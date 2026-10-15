package dev.dhun.presentation.player

import dev.dhun.core.LyricsLine
import dev.dhun.core.Track
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import dev.dhun.lyrics.RomanizationEngine
import dev.dhun.lyrics.RomanizationMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRomanizationControllerTest {
    private class Store : SettingsRepository {
        private val entries = mutableMapOf<String, MutableStateFlow<String?>>()
        private fun entry(key: String) = entries.getOrPut(key) { MutableStateFlow(null) }
        var failWrites = false
        override suspend fun getString(key: String) = entry(key).value
        override suspend fun putString(key: String, value: String) { if (failWrites) error("disk error"); entry(key).value = value }
        override suspend fun getBoolean(key: String, default: Boolean) = getString(key)?.toBooleanStrictOrNull() ?: default
        override suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
        override suspend fun getInt(key: String, default: Int) = getString(key)?.toIntOrNull() ?: default
        override suspend fun putInt(key: String, value: Int) = putString(key, value.toString())
        override suspend fun remove(key: String) { entry(key).value = null }
        override fun observeString(key: String): Flow<String?> = entry(key)
    }

    @Test fun toggleAndOverridePersistAndNeverMutateOriginalLyrics() = runTest {
        val store = Store()
        val track = MutableStateFlow<Track?>(Track("a", "Song", "Artist"))
        val original = LyricsUiState.Synced(listOf(LyricsLine(1_000, "你好")))
        val lyrics = MutableStateFlow<LyricsUiState>(original)
        val engine = RomanizationEngine(StandardTestDispatcher(testScheduler), { text, _ -> "roman:$text" })
        val controller = LyricsRomanizationController(store, backgroundScope, track, lyrics, engine)
        runCurrent()
        assertFalse(controller.enabled.value)
        controller.setEnabled(true)
        runCurrent()
        assertNull(controller.result.value.line("a", 0, "你好")) // Auto does not guess Mandarin.
        controller.setMode(RomanizationMode.CHINESE)
        runCurrent()
        assertEquals("roman:你好", controller.result.value.line("a", 0, "你好"))
        assertEquals(original, lyrics.value)
        val reopened = LyricsRomanizationController(store, backgroundScope, track, lyrics, engine)
        runCurrent()
        assertTrue(reopened.enabled.value)
        assertEquals(RomanizationMode.CHINESE, reopened.mode.value)
        assertEquals("roman:你好", reopened.result.value.line("a", 0, "你好"))
        assertNull(reopened.result.value.line("other", 0, "你好"))
        assertNull(reopened.result.value.line("a", 0, "different source"))
        controller.setEnabled(false)
        runCurrent()
        assertTrue(controller.result.value.secondary.isEmpty())
        assertEquals(original, lyrics.value)
    }

    @Test fun trackSourceChangesAndSettingFailuresDoNotShowWrongSecondaryText() = runTest {
        val store = Store()
        val track = MutableStateFlow<Track?>(Track("a", "Song", "Artist"))
        val lyrics = MutableStateFlow<LyricsUiState>(LyricsUiState.Unsynced("한글\nEnglish"))
        val engine = RomanizationEngine(StandardTestDispatcher(testScheduler), { _, _ -> "hangeul" })
        val controller = LyricsRomanizationController(store, backgroundScope, track, lyrics, engine)
        controller.setEnabled(true)
        runCurrent()
        val previous = controller.result.value
        assertEquals("hangeul", previous.line("a", 0, "한글"))
        assertNull(previous.line("a", 1, "English"))
        track.value = Track("b", "Other song", "Artist")
        lyrics.value = LyricsUiState.Unsynced("English")
        runCurrent()
        assertNull(controller.result.value.line("b", 0, "English"))
        assertNull(previous.line("b", 0, "한글"))
        track.value = null
        runCurrent()
        assertTrue(controller.result.value.secondary.isEmpty())
        store.failWrites = true
        controller.setEnabled(false)
        runCurrent()
        assertTrue(controller.enabled.value)
        assertTrue(controller.error.value != null)
        store.failWrites = false
        controller.setEnabled(false)
        runCurrent()
        assertFalse(controller.enabled.value)
        assertNull(controller.error.value)
        store.putString(SettingsKeys.LYRICS_ROMANIZATION_MODE, "not-a-mode")
        runCurrent()
        assertEquals(RomanizationMode.AUTO, controller.mode.value)
    }
}
