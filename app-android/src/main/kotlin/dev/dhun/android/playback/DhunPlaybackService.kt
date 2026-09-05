package dev.dhun.android.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import dev.dhun.android.R
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

    /** Keeps the notification's title/artwork in step with the session. */
    private val notificationUpdater = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateNotification()
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateNotification()
        override fun onPlaybackStateChanged(playbackState: Int) = updateNotification()
    }

    override fun onCreate() {
        super.onCreate()
        val player = PlaybackGraph.buildExoPlayer(this, streamCache, audioCache)
        player.addListener(notificationUpdater)
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
        mediaSession?.run {
            player.removeListener(notificationUpdater)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "dhun_playback"
    }
}
