package dev.dhun.android.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.dhun.player.StreamRecoverySignal
import kotlinx.coroutines.runBlocking

/**
 * Single construction point for DHUN's playback engine: ExoPlayer wired to
 * the resolving data source (dhun://track/<id> -> real stream URL) with the
 * 403 mid-stream recovery listener. Used by BOTH the MediaSessionService
 * and the activity's session-less fallback path — identical behavior either
 * way.
 */
object PlaybackGraph {

    fun resolvingDataSourceFactory(streamCache: DhunStreamCache): DataSource.Factory = DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        ResolvingDataSource(http, object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
                // ANY failure must surface as IOException (player error), never
                // as an uncaught exception crashing the host.
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

    fun buildExoPlayer(context: Context, streamCache: DhunStreamCache): ExoPlayer {
        val player = ExoPlayer.Builder(
            context,
            DefaultMediaSourceFactory(resolvingDataSourceFactory(streamCache))
        )
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
                if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    StreamRecoverySignal.end()
                    return
                }
                val id = player.currentMediaItem?.mediaId ?: return
                val count = (retries[id] ?: 0) + 1
                retries[id] = count
                if (count > MAX_403_RETRIES) {
                    StreamRecoverySignal.end()
                    return
                }
                // Phase 14: surface Recovering → "Reconnecting…" while we
                // invalidate the stale URL and re-prepare at the same position.
                StreamRecoverySignal.begin()
                streamCache.invalidate(id)
                val position = player.currentPosition
                player.seekTo(position)
                player.prepare()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) StreamRecoverySignal.end()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    StreamRecoverySignal.end()
                }
            }
        })
        return player
    }

    fun sessionActivityIntent(context: Context): android.app.PendingIntent =
        android.app.PendingIntent.getActivity(
            context, 0,
            Intent(context, dev.dhun.android.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    private const val MAX_403_RETRIES = 2
}
