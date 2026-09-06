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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.dhun.player.StreamRecoverySignal
import kotlinx.coroutines.runBlocking

/**
 * Single construction point for DHUN's playback engine: ExoPlayer wired to
 * the resolving data source (dhun://track/<id> -> real stream URL) with the
 * 403 mid-stream recovery listener **and** Phase 14 bounded audio-segment
 * cache (Media3 SimpleCache, stable video-id keys, offline replay of
 * cached spans). Used by BOTH the MediaSessionService and the activity's
 * session-less fallback path — identical behavior either way.
 */
object PlaybackGraph {

    /**
     * Pipeline (outer → inner):
     * 1. [ResolvingDataSource] — `dhun://track/<id>` → googlevideo URL;
     *    cache **key** = video id (stable across URL TTL / 403).
     * 2. [CacheDataSource] — LRU segment store ([DhunAudioSegmentCache]).
     * 3. HTTP — real network read when a span is missing.
     *
     * Offline: if resolve fails but the segment cache has bytes for the id,
     * we keep a synthetic URI + the same key so CacheDataSource serves
     * local spans without a network round-trip.
     */
    fun resolvingDataSourceFactory(
        streamCache: DhunStreamCache,
        audioCache: DhunAudioSegmentCache,
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            )
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(audioCache.cache)
            .setUpstreamDataSourceFactory(httpFactory)
            // Prefer cache; on cache read errors fall through to network once.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            // Key already set on DataSpec in the resolver — do not let the
            // default URI-based key fragment the cache across URL rotations.
            .setCacheKeyFactory { dataSpec ->
                dataSpec.key?.takeIf { it.isNotBlank() } ?: dataSpec.uri.toString()
            }

        return ResolvingDataSource.Factory(
            cacheFactory,
            ResolvingDataSource.Resolver { dataSpec ->
                val videoId = try {
                    check(dataSpec.uri.scheme == "dhun") { "unexpected uri: ${dataSpec.uri}" }
                    dataSpec.uri.lastPathSegment ?: error("malformed dhun uri")
                } catch (e: IllegalStateException) {
                    throw java.io.IOException("bad media uri: ${dataSpec.uri}", e)
                }
                try {
                    val url = runBlocking { streamCache.get(videoId) }
                    dataSpec
                        .buildUpon()
                        .setUri(Uri.parse(url))
                        .setKey(videoId)
                        .build()
                } catch (e: Exception) {
                    if (audioCache.hasContent(videoId)) {
                        android.util.Log.i(
                            "DHUN",
                            "offline/cached replay for $videoId " +
                                "(${audioCache.cachedBytes(videoId)} bytes on disk)",
                        )
                        // Synthetic host — CacheDataSource serves by key; any
                        // uncached hole will fail the upstream open (expected
                        // offline). Fully/mostly played tracks replay cleanly.
                        dataSpec
                            .buildUpon()
                            .setUri(Uri.parse("https://dhun.local/cached/$videoId"))
                            .setKey(videoId)
                            .build()
                    } else {
                        throw java.io.IOException("stream resolve failed: ${e.message}", e)
                    }
                }
            },
        )
    }

    fun buildExoPlayer(
        context: Context,
        streamCache: DhunStreamCache,
        audioCache: DhunAudioSegmentCache = DhunAudioSegmentCache.get(context),
    ): ExoPlayer {
        val player = ExoPlayer.Builder(
            context,
            DefaultMediaSourceFactory(resolvingDataSourceFactory(streamCache, audioCache)),
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

        // One main-thread handler for every recovery re-prepare: listener
        // callbacks may arrive on an ExoPlayer internal thread, and every
        // controller/player call must be marshalled to main (see
        // AndroidDhunPlayer's threading contract).
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        player.addListener(object : Player.Listener {
            private val retries = HashMap<String, Int>()
            private var recoveryPosted = false
            override fun onPlayerError(error: PlaybackException) {
                if (!isRecoverable(error)) {
                    StreamRecoverySignal.end()
                    return
                }
                val id = player.currentMediaItem?.mediaId ?: return
                val count = ((retries[id] ?: 0) + 1).also { retries[id] = it }
                if (count > MAX_RETRIES) {
                    StreamRecoverySignal.end()
                    return
                }
                // Surface Recovering → "Reconnecting…" while we invalidate
                // the stale/failed URL and re-resolve at the same position.
                // Segment cache is NOT cleared — already-played bytes stay.
                // First retry is immediate; later ones back off so a gated
                // endpoint is not hammered. playWhenReady is restored —
                // after an error ExoPlayer parks it, and without this the
                // re-prepare would land paused (read as "still broken").
                StreamRecoverySignal.begin()
                streamCache.invalidate(id)
                recoveryPosted = true
                val backoffMs = RETRY_BACKOFF_MS * (count - 1)
                mainHandler.post {
                    recoveryPosted = false
                    // The user may have skipped while this was queued —
                    // never yank a different track to the failed position.
                    if (player.currentMediaItem?.mediaId != id) return@post
                    player.seekTo(player.currentPosition.coerceAtLeast(0))
                    player.playWhenReady = true
                    if (backoffMs <= 0) {
                        player.prepare()
                    } else {
                        mainHandler.postDelayed({
                            if (player.currentMediaItem?.mediaId == id) player.prepare()
                        }, backoffMs)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) StreamRecoverySignal.end()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    StreamRecoverySignal.end()
                } else if (playbackState == Player.STATE_IDLE && !recoveryPosted) {
                    // IDLE fires right after onPlayerError too — only stand
                    // down when no recovery re-prepare is still queued.
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

    /**
     * Transient failures worth one automatic re-resolve: HTTP errors
     * (403-expired / 429 / 5xx at open or mid-stream), dropped/timeout
     * connections, missing-file opens (rotated URL), and our own
     * "stream resolve failed" wrapper from the ResolvingDataSource.
     * Parse/decoder/drm errors are NOT recoverable by re-resolving — those
     * go straight to the Error state with its manual Retry button.
     */
    private fun isRecoverable(error: PlaybackException): Boolean {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> return true
        }
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is java.io.IOException &&
                cause.message?.contains("stream resolve failed") == true
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private const val MAX_RETRIES = 3
    private const val RETRY_BACKOFF_MS = 1_500L
}
