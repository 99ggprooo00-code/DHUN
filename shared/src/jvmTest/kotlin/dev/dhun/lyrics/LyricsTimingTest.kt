package dev.dhun.lyrics

import dev.dhun.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTimingTest {
    private class MemorySettings : SettingsRepository {
        val values = mutableMapOf<String, MutableStateFlow<String?>>()
        private fun value(key: String) = values.getOrPut(key) { MutableStateFlow(null) }
        override suspend fun getString(key: String) = value(key).value
        override suspend fun putString(key: String, value: String) { this.value(key).value = value }
        override suspend fun getBoolean(key: String, default: Boolean) = getString(key)?.toBooleanStrictOrNull() ?: default
        override suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
        override suspend fun getInt(key: String, default: Int) = getString(key)?.toIntOrNull() ?: default
        override suspend fun putInt(key: String, value: Int) = putString(key, value.toString())
        override suspend fun remove(key: String) { value(key).value = null }
        override fun observeString(key: String): Flow<String?> = value(key)
    }

    @Test fun timingPersistsPerTrackAndResetDoesNotAffectOtherTracks() = runBlocking {
        val settings = MemorySettings()
        val repo = LyricsTimingRepository(settings)
        repo.adjust("a", 100)
        repo.adjust("a", 100)
        repo.adjust("b", -100)
        val reopened = LyricsTimingRepository(settings)
        assertEquals(LyricsTiming("a", 200), reopened.observe("a").first())
        assertEquals(LyricsTiming("b", -100), reopened.observe("b").first())
        assertEquals(LyricsTiming("new", 0), reopened.observe("new").first())
        reopened.reset("a")
        assertEquals(0, reopened.observe("a").first().offsetMs)
        assertEquals(-100, reopened.observe("b").first().offsetMs)
    }

    @Test fun correctionsAreBoundedAndCorruptSettingsFallBack() = runBlocking {
        val settings = MemorySettings()
        val repo = LyricsTimingRepository(settings)
        repo.adjust("a", Int.MAX_VALUE)
        assertEquals(10_000, repo.observe("a").first().offsetMs)
        repo.adjust("a", Int.MIN_VALUE)
        assertEquals(-10_000, repo.observe("a").first().offsetMs)
        settings.putString(dev.dhun.data.SettingsKeys.lyricsOffsetKey("a"), "invalid")
        assertEquals(0, repo.observe("a").first().offsetMs)
    }

    @Test fun displayAndSeekUseInverseCorrectionWithoutMutatingLyrics() {
        val later = LyricsTiming("a", 200)
        assertEquals(800L, later.lyricPosition(1_000))
        assertEquals(1_200L, later.seekPosition(1_000, 10_000))
        assertEquals(-200L, later.lyricPosition(0)) // timestamp zero must not start early
        assertEquals(0L, LyricsTiming("a", -200).seekPosition(100, 10_000))
        assertEquals(10_000L, later.seekPosition(10_000, 10_000))
        assertEquals(Long.MAX_VALUE, later.seekPosition(Long.MAX_VALUE, 0))
    }
}
