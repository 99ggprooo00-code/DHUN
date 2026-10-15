package dev.dhun.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineTranslationTest {
    private class LocalFake : OfflineTranslationProvider {
        override var modelId = "fixture-v1"
        override val languages = setOf("en", "hi", "ja")
        val calls = mutableListOf<List<String>>()
        var action: suspend (List<String>) -> List<String?> = { lines -> lines.map { "translated:$it" } }
        override suspend fun translate(source: String, target: String, lines: List<String>): List<String?> {
            calls += lines.toList()
            return action(lines)
        }
    }
    private val document = TranslationDocument("track", listOf("one", "", "one", "two"))
    private suspend fun OfflineTranslationEngine.request(
        doc: TranslationDocument = document,
        source: String = "en",
        target: String = "hi",
        approved: Boolean = true,
        current: () -> Boolean = { true },
    ) = translate(doc, source, target, approved, current).toList()
    private fun List<OfflineTranslationUpdate>.lastOverlay() = (last() as OfflineTranslationUpdate.Progress).overlay

    @Test fun defaultOffAndUnsupportedTargetsNeverInvokeProvider() = runTest {
        val provider = LocalFake()
        val engine = OfflineTranslationEngine(provider)
        assertEquals(listOf(OfflineTranslationUpdate.NeedsManualApproval), engine.translate(document, "en", "hi", isCurrent = { true }).toList())
        assertEquals(listOf(OfflineTranslationUpdate.UnsupportedLanguage), engine.request(target = "ne"))
        assertEquals(listOf(OfflineTranslationUpdate.UnsupportedLanguage), engine.request(target = "HI"))
        assertTrue(provider.calls.isEmpty())
    }

    @Test fun preservesOriginalSlotsAndSnapshotsCallerInputBeforeCollection() = runTest {
        val provider = LocalFake()
        val original = mutableListOf("one", "", "one", "two")
        val engine = OfflineTranslationEngine(provider, batchSize = 2)
        val flow = engine.translate(TranslationDocument("track", original), "en", "hi", true) { true }
        original[0] = "changed"
        val updates = flow.toList()
        assertEquals(listOf(listOf("one", "one"), listOf("two")), provider.calls)
        val result = updates.lastOverlay()
        assertEquals(document, result.document)
        assertEquals(listOf("translated:one", null, "translated:one", "translated:two"), result.lines)
        assertEquals(listOf(false, false, true), updates.map { (it as OfflineTranslationUpdate.Progress).finished })
        assertEquals("changed", original[0])
    }

    @Test fun cacheKeysIncludeContentTrackLanguagesAndModelAndUseLruEviction() = runTest {
        val provider = LocalFake()
        val engine = OfflineTranslationEngine(provider, cacheCapacity = 2)
        val a = TranslationDocument("a", listOf("a"))
        val b = TranslationDocument("b", listOf("b"))
        engine.request(a); engine.request(b); engine.request(a)
        assertEquals(2, provider.calls.size)
        engine.request(a, target = "ja") // evicts b
        engine.request(b)
        assertEquals(4, provider.calls.size)
        engine.request(b.copy(lines = listOf("changed")))
        engine.request(b.copy(trackId = "other"))
        engine.request(b, source = "ja")
        provider.modelId = "fixture-v2"
        engine.request(b)
        assertEquals(8, provider.calls.size)
        engine.clearCache()
        engine.request(b)
        assertEquals(9, provider.calls.size)
    }

    @Test fun partialErrorsMalformedCountsAndOversizedOutputsAreNotCached() = runTest {
        val provider = LocalFake()
        val engine = OfflineTranslationEngine(provider, batchSize = 1)
        provider.action = { lines -> when (lines.single()) {
            "one" -> throw IllegalStateException("sensitive text must not reach UI")
            else -> listOf("ok")
        } }
        val partial = engine.request().lastOverlay()
        assertEquals(listOf(null, null, null, "ok"), partial.lines)
        engine.request()
        assertEquals(6, provider.calls.size)
        provider.action = { listOf("unexpected", "extra") }
        assertTrue(engine.request().lastOverlay().lines.all { it == null })
        provider.action = { listOf("x".repeat(4_097)) }
        assertTrue(engine.request().lastOverlay().lines.all { it == null })
    }

    @Test fun sourceChangeAndCacheRemovalInvalidateInflightWorkWithoutPublishingOrCaching() = runTest {
        for (clear in listOf(false, true)) {
            val provider = LocalFake()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            provider.action = { entered.complete(Unit); release.await(); it }
            val engine = OfflineTranslationEngine(provider)
            var current = true
            val updates = mutableListOf<OfflineTranslationUpdate>()
            val work = async { engine.translate(document, "en", "hi", true) { current }.toList(updates) }
            entered.await()
            if (clear) engine.clearCache() else current = false
            release.complete(Unit)
            assertFailsWith<CancellationException> { work.await() }
            assertTrue(updates.isEmpty())
            provider.action = { it }
            current = true
            engine.request()
            assertEquals(2, provider.calls.size)
        }
    }

    @Test fun cancellationWhileQueuedNeverInvokesSecondInferenceAndTimeoutIsPartial() = runTest {
        val provider = LocalFake()
        val entered = CompletableDeferred<Unit>()
        provider.action = { entered.complete(Unit); delay(1_000); it }
        val engine = OfflineTranslationEngine(provider, batchTimeoutMs = 100)
        val first = async { engine.request() }
        entered.await()
        val second = async { engine.request(document.copy(trackId = "second")) }
        runCurrent()
        second.cancelAndJoin()
        val result = first.await().lastOverlay()
        assertTrue(result.lines.all { it == null })
        assertEquals(1, provider.calls.size)
        assertTrue(second.isCancelled)
    }

    @Test fun oversizedInputIsRejectedAndSameLanguageDoesNotRunInference() = runTest {
        val provider = LocalFake()
        val engine = OfflineTranslationEngine(provider)
        for (doc in listOf(
            document.copy(lines = List(513) { "x" }),
            document.copy(lines = listOf("x".repeat(2_049))),
            document.copy(lines = List(32) { "x".repeat(1_024) }),
        )) assertEquals(listOf(OfflineTranslationUpdate.InputTooLarge), engine.request(doc))
        val result = engine.request(source = "en", target = "en").lastOverlay()
        assertEquals(listOf("one", null, "one", "two"), result.lines)
        assertFalse(provider.calls.isNotEmpty())
    }
}
