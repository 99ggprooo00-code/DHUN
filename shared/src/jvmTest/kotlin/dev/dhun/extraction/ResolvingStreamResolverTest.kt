package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolvingStreamResolverTest {

    private fun ok(id: String) = DhunResult.Success(StreamInfo(id, "https://x/$id", "audio/webm"))
    private fun fail(e: DhunError) = DhunResult.Failure(e)

    private class FakeResolver(override val name: String, private val result: () -> DhunResult<StreamInfo>) :
        StreamResolver {
        var calls = 0
        override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
            calls++
            return result()
        }
    }

    /** Stands in for a chain that is still walking client identities. */
    private class SlowResolver(
        override val name: String,
        private val millis: Long,
        private val outcome: DhunResult<StreamInfo>,
    ) : StreamResolver {
        var completed = false
        override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
            kotlinx.coroutines.delay(millis)
            completed = true
            return outcome
        }
    }

    @Test
    fun resolveBudgetReturnsATypedVerdictInsteadOfResolvingForever() = kotlinx.coroutines.runBlocking {
        // The desktop report of 2026-09-06 was "stuck on Resolving". Nothing
        // hangs — every InnerTube call is bounded by Ktor HttpTimeout — but the
        // worst-case chain is ~4 minutes, which is indistinguishable from a
        // hang in the UI. The budget must produce a verdict instead.
        val slow = SlowResolver("slow", millis = 5_000, outcome = ok("late"))
        val resolver = ResolvingStreamResolver(slow, fallback = null, budgetMs = 100)

        val started = System.currentTimeMillis()
        val r = resolver.resolve("vid")
        val elapsed = System.currentTimeMillis() - started

        assertTrue(r is DhunResult.Failure, "the budget must win over a slow chain")
        val error = (r as DhunResult.Failure).error
        assertTrue(error is DhunError.Parse, "timeout must be typed, got $error")
        assertTrue(
            (error as DhunError.Parse).detail!!.contains("budget"),
            "detail must explain the timeout, got ${error.detail}",
        )
        assertTrue(elapsed < 2_000, "must give up near the budget, took ${elapsed}ms")
        assertFalse(slow.completed, "the slow chain must be cancelled, not left running")
    }

    @Test
    fun resolveBudgetDoesNotInterfereWithAFastChain() = kotlinx.coroutines.runBlocking {
        val fast = SlowResolver("fast", millis = 10, outcome = ok("v1"))
        val resolver = ResolvingStreamResolver(fast, fallback = null, budgetMs = 5_000)
        val r = resolver.resolve("vid")
        assertTrue(r is DhunResult.Success)
        assertEquals("https://x/v1", (r as DhunResult.Success).value.audioUrl)
        assertTrue(fast.completed)
    }

    @Test
    fun primarySuccessSkipsFallback() = kotlinx.coroutines.runBlocking {
        val primary = FakeResolver("primary") { ok("v1") }
        val fallback = FakeResolver("fallback") { ok("v2") }
        val resolver = ResolvingStreamResolver(primary, fallback)
        val r = resolver.resolve("vid")
        assertTrue(r is DhunResult.Success)
        assertEquals("https://x/v1", (r as DhunResult.Success).value.audioUrl)
        assertEquals(1, primary.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun primaryFailureFallsBack() = kotlinx.coroutines.runBlocking {
        val primary = FakeResolver("primary") { fail(DhunError.AuthRequired()) }
        val fallback = FakeResolver("fallback") { ok("v2") }
        val r = ResolvingStreamResolver(primary, fallback).resolve("vid")
        assertTrue(r is DhunResult.Success)
        assertEquals(1, primary.calls)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun doubleFailureReportsPrimaryError() = kotlinx.coroutines.runBlocking {
        val primary = FakeResolver("primary") { fail(DhunError.AuthRequired()) }
        val fallback = FakeResolver("fallback") { fail(DhunError.Unknown("fallback died")) }
        val r = ResolvingStreamResolver(primary, fallback).resolve("vid")
        assertTrue(r is DhunResult.Failure)
        // the primary's typed error is the story, not the fallback's
        assertTrue((r as DhunResult.Failure).error is DhunError.AuthRequired)
    }

    @Test
    fun noFallbackReturnsPrimaryError() = kotlinx.coroutines.runBlocking {
        val primary = FakeResolver("primary") { fail(DhunError.Network) }
        val r = ResolvingStreamResolver(primary, null).resolve("vid")
        assertTrue((r as DhunResult.Failure).error is DhunError.Network)
    }
}
