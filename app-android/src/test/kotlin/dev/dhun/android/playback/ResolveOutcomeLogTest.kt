package dev.dhun.android.playback

import dev.dhun.core.DhunError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression gate for the playback-diagnostics fix (2026-09-09): the
 * outcome log must classify terminal verdicts (AuthRequired/Unavailable —
 * the drill-proven "Sign in to confirm you're not a bot" gate) correctly,
 * supersede them on success, expire them, and clear them on retry.
 */
class ResolveOutcomeLogTest {

    private class FakeClock(var now: Long = 1_000_000L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    private val clock = FakeClock()

    private val log = ResolveOutcomeLog(clock = { clock.now }, logger = { })

    @Test
    fun `authRequired is terminal while fresh`() {
        val error = DhunError.AuthRequired(
            detail = "web_remix=AUTH_REQUIRED(Sign in to confirm you're not a bot)",
        )
        log.record("v1", error)
        assertSame(error, log.terminalFor("v1"))
        assertEquals(error, log.last("v1")?.error)
    }

    @Test
    fun `unavailable is terminal while fresh`() {
        log.record("v1", DhunError.Unavailable(detail = "removed"))
        assertNotNull(log.terminalFor("v1"))
    }

    @Test
    fun `success supersedes a terminal failure`() {
        log.record("v1", DhunError.AuthRequired(detail = "gated"))
        log.record("v1", null)
        assertNull(log.terminalFor("v1"))
        assertNull(log.last("v1")?.error)
    }

    @Test
    fun `transient error kinds are never terminal`() {
        log.record("net", DhunError.Network(detail = "timeout"))
        log.record("parse", DhunError.Parse(detail = "shape"))
        log.record("rate", DhunError.RateLimited(retryAfterSeconds = 30))
        log.record("unknown", DhunError.Unknown(causeMessage = "boom"))
        assertNull(log.terminalFor("net"))
        assertNull(log.terminalFor("parse"))
        assertNull(log.terminalFor("rate"))
        assertNull(log.terminalFor("unknown"))
    }

    @Test
    fun `terminal verdicts expire after the freshness window`() {
        log.record("v1", DhunError.AuthRequired(detail = "gated"))
        clock.advance(ResolveOutcomeLog.FRESHNESS_MS)
        assertNotNull("exactly at the boundary the verdict still applies", log.terminalFor("v1"))
        clock.advance(1)
        assertNull("one ms past the window it is stale", log.terminalFor("v1"))
    }

    @Test
    fun `clear drops the verdict`() {
        log.record("v1", DhunError.AuthRequired(detail = "gated"))
        log.clear("v1")
        assertNull(log.terminalFor("v1"))
        assertNull(log.last("v1"))
    }

    @Test
    fun `records are isolated per video id`() {
        log.record("v1", DhunError.AuthRequired(detail = "gated"))
        assertNull(log.terminalFor("v2"))
        log.record("v2", null)
        assertNotNull(log.terminalFor("v1"))
    }

    @Test
    fun `blank ids are ignored`() {
        log.record("", DhunError.AuthRequired(detail = "gated"))
        assertNull(log.last(""))
    }

    @Test
    fun `isTerminal classifies exactly the drill-proven verdict kinds`() {
        assertTrue(ResolveOutcomeLog.isTerminal(DhunError.AuthRequired()))
        assertTrue(ResolveOutcomeLog.isTerminal(DhunError.Unavailable()))
        listOf(
            DhunError.Network(),
            DhunError.Parse(),
            DhunError.RateLimited(),
            DhunError.Unknown(),
        ).forEach { error ->
            assertTrue("!$error", !ResolveOutcomeLog.isTerminal(error))
        }
    }
}
