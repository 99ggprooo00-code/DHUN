package dev.dhun.android.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
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
 * Runs as a FOREGROUND service with the live media notification. This is
 * not cosmetic: a background-only service is a first-class target of OEM
 * battery savers (MIUI/HyperOS/OneUI) and gets killed within minutes of
 * idle, which reads to the user as "music stops in the background".
 * See `.ai/DEBUG_LOG.md` → "Background playback killed by OEM battery savers".
 */
class DhunPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val streamCache: DhunStreamCache by lazy {
        GlobalContext.get().get()
    }

    override fun onCreate() {
        super.onCreate()
        val player = PlaybackGraph.buildExoPlayer(this, streamCache)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(PlaybackGraph.sessionActivityIntent(this))
            .build()
        startMediaForeground()
    }

    /**
     * Promote to foreground with the session's live media notification
     * (title/artwork/transport update as the player changes). The channel
     * id is the session's own [MediaSession.sessionId] — the same id
     * MediaStyleNotificationHelper uses for the notification, so exactly
     * one channel is needed and they always match.
     */
    private fun startMediaForeground() {
        val session = mediaSession ?: return
        ensureChannel(session.sessionId)
        val notification = MediaStyleNotificationHelper.createNotification(
            session,
            R.drawable.ic_notification,
            PlaybackGraph.sessionActivityIntent(this),
        ) ?: fallbackNotification(session)
        session.startForeground(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Media playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "DHUN now-playing controls"
            },
        )
    }

    /** Only used if the Media3 helper unexpectedly returns null. */
    private fun fallbackNotification(session: MediaSession): Notification {
        val metadata = session.player.currentMediaItem?.mediaMetadata
        return NotificationCompat.Builder(this, session.sessionId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(metadata.title?.toString() ?: "DHUN")
            .setContentText(metadata.artist?.toString())
            .setContentIntent(PlaybackGraph.sessionActivityIntent(this))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}
