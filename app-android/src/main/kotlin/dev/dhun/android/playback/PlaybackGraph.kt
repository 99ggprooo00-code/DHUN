package dev.dhun.android.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.dhun.player.StreamRecoverySignal
import java.util.concurrent.atomic.AtomicReference
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
    /**
     * @param audioCache segment cache, or null to stream direct with no
     * caching. The null path exists so a corrupt/unopenable cache dir
     * degrades playback instead of killing the whole engine (see
     * [buildExoPlayer]'s fallback — SimpleCache throws on corrupt state).
     */
    fun resolvingDataSourceFactory(
        streamCache: DhunStreamCache,
        audioCache: DhunAudioSegmentCache?,
    ): DataSource.Factory {
        // The agent googlevideo expects for the URL about to be opened. The
        // resolver writes it, [UserAgentDataSource] reads it on open.
        val userAgentForNextOpen = AtomicReference<String?>(null)

        // Configured once; the agent is restamped on it per resolve (see
        // UserAgentDataSource) and each open builds a source from it.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(FALLBACK_USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)

        val userAgentHttpFactory = DataSource.Factory {
            UserAgentDataSource(httpFactory, userAgentForNextOpen)
        }

        // Outer data source: segment cache when available, plain HTTP when
        // the cache dir is unusable (no offline replay in that mode).
        val outerFactory: DataSource.Factory = if (audioCache != null) {
            CacheDataSource.Factory()
                .setCache(audioCache.cache)
                .setUpstreamDataSourceFactory(userAgentHttpFactory)
                // Prefer cache; on cache read errors fall through to network once.
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                // Key already set on DataSpec in the resolver — do not let the
                // default URI-based key fragment the cache across URL rotations.
                .setCacheKeyFactory { dataSpec ->
                    dataSpec.key?.takeIf { it.isNotBlank() } ?: dataSpec.uri.toString()
                }
        } else {
            userAgentHttpFactory
        }

        return ResolvingDataSource.Factory(
            outerFactory,
            ResolvingDataSource.Resolver { dataSpec ->
                val videoId = try {
                    check(dataSpec.uri.scheme == "dhun") { "unexpected uri: ${dataSpec.uri}" }
                    dataSpec.uri.lastPathSegment ?: error("malformed dhun uri")
                } catch (e: IllegalStateException) {
                    throw java.io.IOException("bad media uri: ${dataSpec.uri}", e)
                }
                try {
                    val resolved = runBlocking { streamCache.get(videoId) }
                    // googlevideo binds a signed stream URL to the InnerTube
                    // identity that resolved it and answers a byte read from
                    // any other User-Agent with 403 — which ExoPlayer reports
                    // as a playback error, i.e. "no audio". Hand the resolving
                    // identity to the data source before the open it feeds.
                    userAgentForNextOpen.set(resolved.userAgent ?: FALLBACK_USER_AGENT)
                    dataSpec
                        .buildUpon()
                        .setUri(Uri.parse(resolved.url))
                        .setKey(videoId)
                        .build()
                } catch (e: Exception) {
                    userAgentForNextOpen.set(FALLBACK_USER_AGENT)
                    if (audioCache != null && audioCache.hasContent(videoId)) {
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

    /**
     * Pass `audioCache = null` only as a last resort (corrupt cache dir):
     * playback streams direct with no segment caching or offline replay.
     * Callers must try the cached build first and fall back here on throw.
     */
    fun buildExoPlayer(
        context: Context,
        streamCache: DhunStreamCache,
        audioCache: DhunAudioSegmentCache? = DhunAudioSegmentCache.get(context),
    ): ExoPlayer {
        // Stall-heavy mobile carriers need more per-segment retries than the
        // default before a track is declared dead — throttled/shaped reads
        // otherwise surface as instant errors. Anything still failing
        // after these reaches onPlayerError, where the recovery listener
        // runs invalidate → re-resolve.
        val mediaSourceFactory = DefaultMediaSourceFactory(
            resolvingDataSourceFactory(streamCache, audioCache),
        ).setLoadErrorHandlingPolicy(
            androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(
                /* minimumLoadableRetryCount = */ SEGMENT_RETRY_COUNT,
            ),
        )
        val player = ExoPlayer.Builder(
            context,
            mediaSourceFactory,
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

    /** Only used when a resolver reports no identity (non-InnerTube engines). */
    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private const val MAX_RETRIES = 3
    private const val RETRY_BACKOFF_MS = 1_500L
    /** Per-segment load retries before the error reaches onPlayerError. */
    private const val SEGMENT_RETRY_COUNT = 5
}

/**
 * Opens each request through an HTTP source built with the User-Agent that
 * resolved the URL.
 *
 * Two constraints force this shape. (1) [ResolvingDataSource] constructs its
 * upstream data source once, in its own constructor, so the agent cannot be
 * chosen later through the factory alone. (2) In media3 1.5.1
 * `DefaultHttpDataSource` has no instance-level `setUserAgent` — the agent
 * is fixed at construction and only `DefaultHttpDataSource.Factory` accepts
 * one. So the resolver publishes the agent, and every [open] restamps the
 * factory and builds a fresh source for that one request.
 *
 * [userAgent] is written by the resolver immediately before the open it
 * belongs to (same loader thread, and opens on one player are serialised).
 *
 * `DataSource` declares `addTransferListener` and `getUri` as abstract
 * (only `getResponseHeaders` has a default), so all of them are implemented
 * here — omitting the listener registration is what an earlier attempt got
 * wrong.
 */
private class UserAgentDataSource(
    private val httpFactory: DefaultHttpDataSource.Factory,
    private val userAgent: AtomicReference<String?>,
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        current?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        userAgent.get()?.let { httpFactory.setUserAgent(it) }
        val source = httpFactory.createDataSource()
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): android.net.Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        current?.responseHeaders ?: emptyMap()

    override fun close() {
        current?.close()
        current = null
    }
}
