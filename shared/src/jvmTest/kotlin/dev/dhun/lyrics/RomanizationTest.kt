package dev.dhun.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class RomanizationTest {
    @Test fun detectsScriptsConservativelyAndDoesNotGuessHanOrCyrillicLanguage() {
        assertEquals(RomanizationMode.KOREAN, RomanizationMode.detect("한글, Hello!"))
        assertEquals(RomanizationMode.JAPANESE_KANA, RomanizationMode.detect("世界へ"))
        assertEquals(RomanizationMode.DEVANAGARI, RomanizationMode.detect("नमस्ते!"))
        assertEquals(RomanizationMode.GURMUKHI, RomanizationMode.detect("ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ"))
        for (text in listOf("你好", "Привет", "Київ", "العربية", "Ελληνικά", "Hello!", "한글 नमस्ते")) {
            assertEquals(null, RomanizationMode.detect(text), text)
        }
        assertEquals(RomanizationMode.AUTO, RomanizationMode.fromId("unknown"))
    }

    @Test fun realIcuSupportsEveryAdvertisedModeAndPreservesPunctuationAndOriginals() = runBlocking {
        val engine = RomanizationEngine()
        val cases = listOf(
            RomanizationMode.JAPANESE_KANA to "カナ",
            RomanizationMode.KOREAN to "한글",
            RomanizationMode.CHINESE to "你好",
            RomanizationMode.DEVANAGARI to "नमस्ते",
            RomanizationMode.GURMUKHI to "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ",
            RomanizationMode.RUSSIAN to "Привет",
            RomanizationMode.UKRAINIAN to "Київ",
        )
        for ((mode, original) in cases) {
            val input = "Hello, $original! 123 🎵"
            val result = engine.romanize(input, mode)
            assertNotEquals(input, result, mode.id)
            assertTrue(result.startsWith("Hello, "), mode.id)
            assertTrue(result.endsWith("! 123 🎵"), mode.id)
            assertFalse(result.any { it in original && it.isLetter() }, mode.id)
            assertEquals("Hello, $original! 123 🎵", input)
        }
        assertEquals("kana", engine.romanize("カナ", RomanizationMode.JAPANESE_KANA))
        assertEquals("i", engine.romanize("и", RomanizationMode.RUSSIAN))
        assertEquals("y", engine.romanize("и", RomanizationMode.UKRAINIAN))
        assertTrue(engine.romanize("你好", RomanizationMode.CHINESE).contains("nǐ"))
        assertTrue(engine.romanize("世界へ", RomanizationMode.AUTO).startsWith("世界"))
        assertNotEquals("世界へ", engine.romanize("世界へ", RomanizationMode.AUTO))
        for (text in listOf("你好", "Привет", "العربية", "Unknown! 🎵")) {
            assertEquals(text, engine.romanize(text, RomanizationMode.AUTO))
        }
    }

    @Test fun boundedCacheKeysIncludeSourceAndModeAndEvictLeastRecentlyUsed() = runTest {
        var calls = 0
        val engine = RomanizationEngine(StandardTestDispatcher(testScheduler), { text, mode -> calls++; "$text-$mode" }, 2)
        engine.romanize("one", RomanizationMode.CHINESE)
        engine.romanize("two", RomanizationMode.CHINESE)
        engine.romanize("one", RomanizationMode.CHINESE)
        assertEquals(2, calls)
        engine.romanize("one", RomanizationMode.RUSSIAN) // evicts two, not the recently touched one
        engine.romanize("two", RomanizationMode.CHINESE)
        assertEquals(4, calls)
    }

    @Test fun workRunsOnWorkerRatherThanCallingUiThread() = runBlocking {
        val caller = Thread.currentThread()
        var ranOnWorker = false
        val engine = RomanizationEngine(transform = { _, _ ->
            ranOnWorker = Thread.currentThread() !== caller
            "kana"
        })
        assertEquals("kana", engine.romanize("カナ", RomanizationMode.JAPANESE_KANA))
        assertTrue(ranOnWorker)
    }

    @Test fun failedUnknownBlankAndOversizeTextRemainUnchangedAndCancellationPropagates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = RomanizationEngine(dispatcher, { _, _ -> error("unsupported transform") })
        assertEquals("カナ", engine.romanize("カナ", RomanizationMode.JAPANESE_KANA))
        assertEquals("  ", engine.romanize("  ", RomanizationMode.AUTO))
        val huge = "カ".repeat(4_097)
        assertEquals(huge, engine.romanize(huge, RomanizationMode.JAPANESE_KANA))
        val cancelled = RomanizationEngine(dispatcher, { _, _ -> throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { cancelled.romanize("カナ", RomanizationMode.JAPANESE_KANA) }
    }
}
