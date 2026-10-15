package dev.dhun.lyrics

import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Positive offset delays lyrics. Stored per track, independently of cache/provider choice. */
data class LyricsTiming(val trackId: String?, val offsetMs: Int = 0) {
    init { require(offsetMs in -MAX_OFFSET_MS..MAX_OFFSET_MS) }
    fun lyricPosition(positionMs: Long): Long = shift(positionMs, -offsetMs)
    fun seekPosition(lyricTimeMs: Long, durationMs: Long): Long =
        shift(lyricTimeMs, offsetMs).coerceIn(0, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)

    private fun shift(timeMs: Long, deltaMs: Int): Long {
        val time = timeMs.coerceAtLeast(0)
        return if (deltaMs > 0 && time > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE else time + deltaMs
    }

    companion object { const val MAX_OFFSET_MS = 10_000 }
}

class LyricsTimingRepository(private val settings: SettingsRepository) {
    private val writes = Mutex()
    fun observe(trackId: String): Flow<LyricsTiming> =
        settings.observeString(SettingsKeys.lyricsOffsetKey(trackId)).map {
            LyricsTiming(trackId, (it?.toIntOrNull() ?: 0).coerceIn(-LyricsTiming.MAX_OFFSET_MS, LyricsTiming.MAX_OFFSET_MS))
        }

    suspend fun adjust(trackId: String, deltaMs: Int) = writes.withLock {
        val key = SettingsKeys.lyricsOffsetKey(trackId)
        val current = settings.getInt(key, 0).coerceIn(-LyricsTiming.MAX_OFFSET_MS, LyricsTiming.MAX_OFFSET_MS)
        settings.putInt(key, (current.toLong() + deltaMs).coerceIn(-10_000L, 10_000L).toInt())
    }

    suspend fun reset(trackId: String) = writes.withLock { settings.remove(SettingsKeys.lyricsOffsetKey(trackId)) }
}
