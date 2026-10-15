package dev.dhun.lyrics

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.Track
import dev.dhun.data.EpochClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LrcLibAccessTest {
    private val track = Track("audit", "Synthetic title", "Synthetic artist", durationSeconds = 4_000)
    private fun client(engine: MockEngine) = HttpClient(engine) { install(HttpTimeout); expectSuccess = false }

    @Test fun identifiesBothEndpointsAndSharesSequentialSpacingAcrossAdapters() = runTest {
        var now = 10_000L
        var calls = 0
        val waits = mutableListOf<Long>()
        val lane = LrcLibAccess("1.2.3", EpochClock { now }, monotonicMs = { now }) { waits += it; now += it }
        val http = client(MockEngine { request ->
            calls++
            assertEquals("DHUN/1.2.3 (https://github.com/99ggprooo00-code/DHUN)", request.headers["User-Agent"])
            assertEquals(null, request.url.parameters["duration"]) // out of documented range
            respond(if (request.url.encodedPath == "/api/get") "{}" else "[]")
        })
        try {
            LrcLibSource(http, access = lane).fetch(track)
            LrcLibSearchSource(http, access = lane).candidates(track, "synthetic")
            assertEquals(2, calls)
            assertEquals(listOf(250L), waits)
        } finally { http.close() }
    }

    @Test fun nonJson429BacksOffBothAdaptersAndDoesNotSleepOrRetryAutomatically() = runTest {
        var now = 10_000L
        var calls = 0
        val lane = LrcLibAccess(clock = EpochClock { now }, monotonicMs = { now }) { now += it }
        val http = client(MockEngine {
            calls++
            if (calls == 1) respond("edge rate limit, not JSON", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "120"))
            else respond("[]")
        })
        try {
            val first = LrcLibSource(http, access = lane).fetch(track)
            assertTrue((first as DhunResult.Failure).error is DhunError.RateLimited)
            val blocked = LrcLibSearchSource(http, access = lane).candidates(track, "retry")
            assertTrue((blocked as DhunResult.Failure).error is DhunError.RateLimited)
            assertEquals(1, calls)
            now += 120_000
            assertTrue(LrcLibSearchSource(http, access = lane).candidates(track, "retry") is DhunResult.Success)
            assertEquals(2, calls)
        } finally { http.close() }
    }

    @Test fun canceledQueuedRequestNeverReachesHttpAndTheLaneRemainsUsable() = runTest {
        var now = 10_000L
        var calls = 0
        val held = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val lane = LrcLibAccess(clock = EpochClock { now }, monotonicMs = { now }) { now += it }
        val http = client(MockEngine {
            calls++
            if (calls == 1) { entered.complete(Unit); held.await() }
            respond("[]")
        })
        try {
            val source = LrcLibSearchSource(http, access = lane)
            val first = async { source.candidates(track, "one") }
            entered.await()
            val queued = async { source.candidates(track, "two") }
            runCurrent()
            assertEquals(1, calls)
            queued.cancel()
            held.complete(Unit)
            first.await(); queued.join()
            assertTrue(source.candidates(track, "three") is DhunResult.Success)
            assertEquals(2, calls)
        } finally { http.close() }
    }

    @Test fun honorsSecondsHttpDatesAndFailClosedForUnsupportedOrOverflowingDelays() {
        assertEquals(122_000L, retryAfterDeadline("120", 2_000))
        assertEquals(60_000L, retryAfterDeadline(null, 0))
        assertEquals(0L, retryAfterDeadline("Thu, 01 Jan 1970 00:00:00 GMT", 0))
        assertEquals(86_400_000L, retryAfterDeadline("Fri, 02 Jan 1970 00:00:00 GMT", 0))
        assertEquals(784_111_777_000L, retryAfterDeadline("Sun, 06 Nov 1994 08:49:37 GMT", 0))
        assertEquals(Long.MAX_VALUE, retryAfterDeadline("Fri, 31 Feb 2026 00:00:00 GMT", 0))
        assertEquals(Long.MAX_VALUE, retryAfterDeadline("future-unsupported-format", 0))
        assertEquals(Long.MAX_VALUE, retryAfterDeadline("9999999999999999999999", 0))
    }

    @Test fun deeplyNestedSuccessIsRejectedBeforeRecursiveJsonParsing() = runTest {
        val http = client(MockEngine { respond("[".repeat(10_000) + "0" + "]".repeat(10_000)) })
        try { assertTrue((LrcLibSource(http).fetch(track) as DhunResult.Failure).error is DhunError.Parse) }
        finally { http.close() }
    }

    @Test fun wallClockJumpCannotExpireAProvidersRequestedCooldownEarly() = runTest {
        var wall = 10_000L
        var elapsed = 0L
        var calls = 0
        val lane = LrcLibAccess(clock = EpochClock { wall }, monotonicMs = { elapsed }) { elapsed += it }
        val http = client(MockEngine {
            calls++
            if (calls == 1) respond("edge backoff", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "120"))
            else respond("[]")
        })
        try {
            LrcLibSource(http, access = lane).fetch(track)
            wall += 3_600_000
            val blocked = LrcLibSearchSource(http, access = lane).candidates(track, "retry")
            assertTrue((blocked as DhunResult.Failure).error is DhunError.RateLimited)
            assertEquals(1, calls)
            elapsed += 120_000
            assertTrue(LrcLibSearchSource(http, access = lane).candidates(track, "retry") is DhunResult.Success)
            assertEquals(2, calls)
        } finally { http.close() }
    }

    @Test fun wallClockJumpCannotBypassTheRelativeRequestGap() = runTest {
        var now = 10_000L
        val waits = mutableListOf<Long>()
        val lane = LrcLibAccess(clock = EpochClock { now }, monotonicMs = { now }) { waits += it }
        val http = client(MockEngine { respond("{}") })
        try {
            val source = LrcLibSource(http, access = lane)
            source.fetch(track)
            now += 3_600_000
            source.fetch(track)
            assertEquals(listOf(250L), waits)
        } finally { http.close() }
    }

    @Test fun oversizedSuccessBodyFailsSoftlyInsteadOfParsingAnUnboundedTree() = runTest {
        val http = client(MockEngine { respond("x".repeat(2_000_001)) })
        try { assertTrue((LrcLibSource(http).fetch(track) as DhunResult.Failure).error is DhunError.Parse) }
        finally { http.close() }
    }
}
