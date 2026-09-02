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

    private data class Entry(val url: String, val resolvedAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    suspend fun get(videoId: String): String {
        val hit = cache[videoId]
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.resolvedAtMs < TTL_MS) return hit.url
        return when (val result = provider.getStreamInfo(videoId)) {
            is DhunResult.Success -> {
                cache[videoId] = Entry(result.value.audioUrl, now)
                Log.i(
                    TAG,
                    "resolved $videoId: ${result.value.mimeType} " +
                        "${result.value.bitrateKbps ?: "?"}kbps " +
                        "host=${Uri.parse(result.value.audioUrl).host}",
                )
                result.value.audioUrl
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
