package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
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
