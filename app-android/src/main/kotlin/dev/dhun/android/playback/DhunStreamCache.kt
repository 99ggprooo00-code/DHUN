package dev.dhun.android.playback

import android.net.Uri
import android.util.Log
import dev.dhun.core.DhunResult
import dev.dhun.core.detailString
import dev.dhun.core.toUserMessage
import dev.dhun.provider.MusicProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Stream-URL cache on the playback (service) side. Avoids re-resolving on
 * every seek/prepare, honors YouTube's URL TTL (≈6h, we use 5h), and gets
 * invalidated on 403 — the trigger for the recovery path.
 *
 * Every resolve outcome is logged (tag DHUN) with its typed reason, so
 * `adb logcat -s DHUN` shows exactly which client identity served the URL
 * or what each one said when resolution fails on a device.
 */
class DhunStreamCache(private val provider: MusicProvider) {

    private data class Entry(val stream: ResolvedStream, val resolvedAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    /**
     * A resolved stream URL **plus the User-Agent it is bound to**.
     * googlevideo 403s a byte read whose agent differs from the one that
     * resolved the URL, so the two must travel together — returning a bare
     * URL is what let the playback layer send its own default agent.
     */
    data class ResolvedStream(val url: String, val userAgent: String?)

    suspend fun get(videoId: String): ResolvedStream {
        val hit = cache[videoId]
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.resolvedAtMs < TTL_MS) return hit.stream
        return when (val result = provider.getStreamInfo(videoId)) {
            is DhunResult.Success -> {
                val stream = ResolvedStream(result.value.audioUrl, result.value.userAgent)
                cache[videoId] = Entry(stream, now)
                Log.i(
                    TAG,
                    "resolved $videoId: ${result.value.mimeType} " +
                        "${result.value.bitrateKbps ?: "?"}kbps " +
                        "host=${Uri.parse(result.value.audioUrl).host} " +
                        "ua=${stream.userAgent?.take(40) ?: "<none>"}",
                )
                stream
            }
            is DhunResult.Failure -> {
                Log.w(
                    TAG,
                    "resolve $videoId failed: ${result.error} " +
                        "detail=${result.error.detailString() ?: "-"}",
                )
                throw DhunResolveException(result.error)
            }
        }
    }

    /**
     * Proactively resolves and caches stream URL for [videoId] in background (ADR-005 prefetch).
     */
    suspend fun prefetch(videoId: String) {
        val hit = cache[videoId]
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.resolvedAtMs < TTL_MS) return
        runCatching { get(videoId) }
    }

    fun invalidate(videoId: String) {
        Log.i(TAG, "invalidating cached stream for $videoId")
        cache.remove(videoId)
    }

    companion object {
        private const val TAG = "DHUN"
        private const val TTL_MS = 5L * 60 * 60 * 1000 // 5 hours (URLs expire ~6h)
    }
}

class DhunResolveException(val error: dev.dhun.core.DhunError) :
    RuntimeException(buildString {
        append(error.toUserMessage())
        error.detailString()?.let { append(" [").append(it).append("]") }
    })
