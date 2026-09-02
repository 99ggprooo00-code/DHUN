package dev.dhun.android.playback

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.koin.core.context.GlobalContext

/**
 * The MediaSession service: background audio + lock-screen/notification
 * controls + media keys, via Media3. The player engine construction is
 * shared with the session-less fallback path (see [PlaybackGraph]).
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
}
