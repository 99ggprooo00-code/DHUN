package dev.dhun.android.playback

import dev.dhun.core.DhunResult
import dev.dhun.core.toUserMessage
import dev.dhun.provider.MusicProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Stream-URL cache on the playback (service) side. Avoids re-resolving on
 * every seek/prepare, honors YouTube's URL TTL (≈6h, we use 5h), and gets
 * invalidated on 403 — the trigger for the recovery path.
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
                result.value.audioUrl
            }
            is DhunResult.Failure -> throw DhunResolveException(result.error)
        }
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    companion object {
        private const val TTL_MS = 5L * 60 * 60 * 1000 // 5 hours (URLs expire ~6h)
    }
}

class DhunResolveException(val error: dev.dhun.core.DhunError) :
    RuntimeException(error.toUserMessage())
