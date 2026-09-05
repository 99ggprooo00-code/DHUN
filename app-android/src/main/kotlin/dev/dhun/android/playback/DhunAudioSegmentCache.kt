package dev.dhun.android.playback

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dev.dhun.player.AudioCacheBudget
import java.io.File

/**
 * Phase 14 bounded **audio-segment** cache (Media3 [SimpleCache] + LRU).
 *
 * Distinct from [DhunStreamCache] (URL TTL only):
 * - Keys are stable **video ids**, so googlevideo URL rotation / 403 recovery
 *   does not throw away already-downloaded bytes.
 * - Played ranges are written while streaming; LRU evicts oldest when the
 *   budget ([AudioCacheBudget] / `SettingsKeys.CACHE_SIZE_MB`, default 1 GiB)
 *   is exceeded.
 * - Offline replay: if resolve fails but [hasContent] for the id, the player
 *   serves cached spans without hitting the network.
 *
 * **Single instance per process** — [SimpleCache] locks its directory.
 */
class DhunAudioSegmentCache private constructor(
    context: Context,
    maxBytes: Long,
) {
    private val cacheDir: File = File(context.applicationContext.cacheDir, DIR_NAME).also { it.mkdirs() }
    private val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)

    val cache: Cache = SimpleCache(
        cacheDir,
        LeastRecentlyUsedCacheEvictor(maxBytes),
        databaseProvider,
    )

    val maxBytes: Long = maxBytes

    /** True when at least one cached span exists for [videoId]. */
    fun hasContent(videoId: String): Boolean {
        if (videoId.isBlank()) return false
        return try {
            cache.getCachedSpans(videoId).any { it.isCached }
        } catch (t: Throwable) {
            Log.w(TAG, "hasContent($videoId) failed: ${t.message}")
            false
        }
    }

    /** Approximate cached byte count for [videoId] (0 if none). */
    fun cachedBytes(videoId: String): Long {
        if (videoId.isBlank()) return 0L
        return try {
            cache.getCachedBytes(videoId, /* position= */ 0, /* length= */ Long.MAX_VALUE)
        } catch (t: Throwable) {
            Log.w(TAG, "cachedBytes($videoId) failed: ${t.message}")
            0L
        }
    }

    fun release() {
        try {
            (cache as? SimpleCache)?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "release failed: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "DHUN"
        private const val DIR_NAME = "audio-segments"

        @Volatile
        private var instance: DhunAudioSegmentCache? = null

        /**
         * Process-wide singleton. [maxBytes] is applied only on first create;
         * changing the setting requires a process restart (v1 — documented).
         */
        fun get(context: Context, maxBytes: Long = AudioCacheBudget.bytesForMb(AudioCacheBudget.DEFAULT_MB)): DhunAudioSegmentCache {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: DhunAudioSegmentCache(context.applicationContext, maxBytes).also {
                    instance = it
                    Log.i(TAG, "audio-segment cache open dir=${it.cacheDir.absolutePath} maxBytes=$maxBytes")
                }
            }
        }

        /** Test-only: drop the singleton after [release]. */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }
}
