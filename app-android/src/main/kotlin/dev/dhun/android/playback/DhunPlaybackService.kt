package dev.dhun.android.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import dev.dhun.android.R
import dev.dhun.android.widgets.DhunWidgetUpdater
import dev.dhun.data.DataLayer
import dev.dhun.download.DownloadRepository
import org.koin.core.context.GlobalContext

/**
 * The MediaSession service: background audio + lock-screen/notification
 * controls + media keys, via Media3. The player engine construction is
 * shared with the session-less fallback path (see [PlaybackGraph]).
 *
 * Runs as a FOREGROUND service (mediaPlayback) — required to survive OEM
 * battery savers (MIUI/HyperOS/OneUI): a background-only service gets
 * killed on idle, which reads to the user as "music stops in the
 * background". The media notification is styled with
 * [MediaStyleNotificationHelper.MediaStyle] bound to the session, so the
 * system drives the transport state, and a player listener keeps the
 * title/artwork current. See `.ai/DEBUG_LOG.md` →
 * "Background playback killed by OEM battery savers".
 */
class DhunPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val streamCache: DhunStreamCache by lazy {
        GlobalContext.get().get()
    }

    private val audioCache: DhunAudioSegmentCache by lazy {
        GlobalContext.get().get()
    }

    private val downloads: DownloadRepository by lazy {
        GlobalContext.get().get<DataLayer>().downloads
    }

    /** Keeps the notification's title/artwork in step with the session. */
    private val notificationUpdater = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateNotification()
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateNotification()
        override fun onPlaybackStateChanged(playbackState: Int) = updateNotification()
    }

    /**
     * Pushes widget updates on every meaningful player event, so the
     * home-screen widgets track the session live instead of polling.
     * Debounced — bursts (e.g. prepare → ready → playing) collapse into one
     * push. Widget failures are swallowed: a launcher hiccup must never
     * affect playback.
     */
    private val widgetHandler = Handler(Looper.getMainLooper())
    private var lastWidgetPushMs = 0L
    private val widgetSyncListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaSession?.let { pushWidgets(it.player, force = true) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mediaSession?.let { pushWidgets(it.player, force = true) }
            if (isPlaying) scheduleWidgetTick() else widgetHandler.removeCallbacks(widgetTick)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            mediaSession?.let { pushWidgets(it.player) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            mediaSession?.let { pushWidgets(it.player, force = true) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            mediaSession?.let { pushWidgets(it.player, force = true) }
        }
    }

    /** Re-pushes position while playing so widget progress bars advance. */
    private val widgetTick = object : Runnable {
        override fun run() {
            widgetHandler.removeCallbacks(this)
            val player = mediaSession?.player ?: return
            if (!player.isPlaying) return
            pushWidgets(player)
            widgetHandler.postDelayed(this, DhunWidgetUpdater.PROGRESS_TICK_MS)
        }
    }

    private fun pushWidgets(player: Player, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastWidgetPushMs < WIDGET_PUSH_DEBOUNCE_MS) return
        lastWidgetPushMs = now
        runCatching { DhunWidgetUpdater.pushFromPlayer(this, player) }
    }

    private fun scheduleWidgetTick() {
        widgetHandler.removeCallbacks(widgetTick)
        widgetHandler.postDelayed(widgetTick, DhunWidgetUpdater.PROGRESS_TICK_MS)
    }

    override fun onCreate() {
        super.onCreate()
        // A corrupt cache dir makes SimpleCache throw — degrade to direct
        // streaming instead of killing the service (and with it all audio).
        val player = try {
            PlaybackGraph.buildExoPlayer(this, streamCache, audioCache, downloads)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "segment cache unusable — streaming without cache", t)
            PlaybackGraph.buildExoPlayer(this, streamCache, null, downloads)
        }
        player.addListener(notificationUpdater)
        player.addListener(widgetSyncListener)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(PlaybackGraph.sessionActivityIntent(this))
            .build()
        startMediaForeground()
    }

    /**
     * Promote to foreground with the live media notification. Android 14
     * (and targetSdk 34 builds) requires the foreground-service TYPE to be
     * passed to startForeground when the manifest declares one — hence the
     * version-branched calls.
     */
    private fun startMediaForeground() {
        val session = mediaSession ?: return
        ensureChannel()
        val notification = buildMediaNotification(session)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Media playback", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "DHUN now-playing controls" },
        )
    }

    private fun buildMediaNotification(session: MediaSession): Notification {
        val metadata = session.player.currentMediaItem?.mediaMetadata
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(metadata?.title?.toString() ?: "DHUN")
            .setContentText(metadata?.artist?.toString())
            .setLargeIcon(
                metadata?.artworkData?.let { data ->
                    runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
                },
            )
            .setContentIntent(PlaybackGraph.sessionActivityIntent(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // No setCategory: the framework has no CATEGORY_MEDIA constant
            // (verified by the compiler); MediaStyle + the mediaPlayback
            // FGS type carry the media semantics.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun updateNotification() {
        val session = mediaSession ?: return
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildMediaNotification(session))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        widgetHandler.removeCallbacks(widgetTick)
        mediaSession?.run {
            player.removeListener(notificationUpdater)
            player.removeListener(widgetSyncListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "dhun_playback"
        const val TAG = "DHUN"
        /** Collapses event bursts into a single widget push. */
        const val WIDGET_PUSH_DEBOUNCE_MS = 800L
    }
}
