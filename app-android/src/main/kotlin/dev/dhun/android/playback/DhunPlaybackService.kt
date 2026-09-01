package dev.dhun.android.playback

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * The playback service: owns the ExoPlayer and the MediaSession (background
 * audio, lock-screen/notification controls, media keys — all via Media3).
 *
 * Stream resolution: media items carry dhun://track/<videoId> URIs;
 * [ResolvingDataSource] rewrites them to real googlevideo URLs on open via
 * [DhunStreamCache]. On HTTP 403 (URL expired/invalidated mid-playback) the
 * listener invalidates the cache, seeks back to the current position and
 * re-prepares — the playback survives without losing the user's place.
 */
class DhunPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val streamCache: DhunStreamCache by lazy {
        GlobalContext.get().get()
    }

    private val resolvingDataSourceFactory = DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        ResolvingDataSource(http, object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
                // ANY failure must surface as IOException (player error), never
                // as an uncaught exception crashing the service.
                val videoId = try {
                    check(dataSpec.uri.scheme == "dhun") { "unexpected uri: ${dataSpec.uri}" }
                    dataSpec.uri.lastPathSegment ?: error("malformed dhun uri")
                } catch (e: IllegalStateException) {
                    throw java.io.IOException("bad media uri: ${dataSpec.uri}", e)
                }
                val url = try {
                    runBlocking { streamCache.get(videoId) }
                } catch (e: Exception) {
                    throw java.io.IOException("stream resolve failed: ${e.message}", e)
                }
                return androidx.media3.datasource.DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setPosition(dataSpec.position)
                    .setLength(dataSpec.length)
                    .setKey(dataSpec.key)
                    .build()
            }
        })
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this, DefaultMediaSourceFactory(resolvingDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            private val retries = HashMap<String, Int>()
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return
                val id = player.currentMediaItem?.mediaId ?: return
                val count = (retries[id] ?: 0) + 1
                retries[id] = count
                if (count > MAX_403_RETRIES) return
                streamCache.invalidate(id)
                val position = player.currentPosition
                player.seekTo(position)
                player.prepare() // re-resolve and resume where the user was
            }
        })

        val sessionActivity = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, dev.dhun.android.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
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

    companion object {
        private const val MAX_403_RETRIES = 2
    }
}
