package dev.dhun.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The recovery budget behind [PlaybackGraph]'s 403 / dropped-stream listener:
 * how many automatic re-resolves a track gets, how long each one waits, and —
 * the reason this class exists — when a track earns its allowance back.
 *
 * The listener wiring (a real ExoPlayer, a main-thread `Handler`, the delayed
 * refund) needs a device or an emulator and stays reviewed-not-executed; what
 * is pinned here is the policy the wiring is required to honour.
 *
 * Plain JUnit: no Android types are involved, so no Robolectric classloader.
 */
class StreamRetryBudgetTest {

    @Test
    fun threeAutomaticRecoveriesThenTheErrorReachesTheUser() {
        val budget = StreamRetryBudget()
        assertEquals(1, budget.recordFailure("v1"))
        assertEquals(2, budget.recordFailure("v1"))
        assertEquals(3, budget.recordFailure("v1"))
        // Exhausted: the error surfaces with its manual Retry button, and
        // stays exhausted however often the same track fails again.
        assertNull(budget.recordFailure("v1"))
        assertNull(budget.recordFailure("v1"))
        assertEquals(0, budget.attemptsLeft("v1"))
    }

    @Test
    fun theFirstRetryIsImmediateAndLaterOnesBackOff() {
        val budget = StreamRetryBudget()
        assertEquals(0L, budget.backoffMillisFor(1))
        assertEquals(StreamRetryBudget.RETRY_BACKOFF_MS, budget.backoffMillisFor(2))
        assertEquals(StreamRetryBudget.RETRY_BACKOFF_MS * 2, budget.backoffMillisFor(3))
        // Defensive: a caller handing back a bogus attempt never gets a
        // negative delay posted to the handler.
        assertEquals(0L, budget.backoffMillisFor(0))
        assertEquals(0L, budget.backoffMillisFor(-1))
    }

    /**
     * The regression this extraction fixes. The listener outlives every track
     * it plays, and the old inline map never forgot: a track that recovered at
     * minute 2 arrived at minute 90 with one retry left, then none — so the
     * engine stopped fixing by itself a fault it had been fixing all session,
     * and the user saw an error instead of "Reconnecting…". Staying audible for
     * the pinned window refunds the full allowance.
     */
    @Test
    fun aTrackThatStayedAudibleGetsItsFullBudgetBack() {
        val budget = StreamRetryBudget()
        repeat(StreamRetryBudget.MAX_RETRIES + 1) { budget.recordFailure("v1") }
        assertNull(budget.recordFailure("v1"))

        budget.clear("v1")

        assertEquals(StreamRetryBudget.MAX_RETRIES, budget.attemptsLeft("v1"))
        assertEquals(1, budget.recordFailure("v1"))
        assertEquals(2, budget.recordFailure("v1"))
        assertEquals(3, budget.recordFailure("v1"))
        assertNull(budget.recordFailure("v1"))
    }

    /**
     * The refund is per track: clearing one id must not hand a second, still
     * failing track a fresh allowance. (The listener captures the id when it
     * schedules the refund rather than reading the player when it fires, so a
     * skip during the window cannot refund the wrong track either.)
     */
    @Test
    fun refundingOneTrackDoesNotRefundAnother() {
        val budget = StreamRetryBudget()
        repeat(StreamRetryBudget.MAX_RETRIES) { budget.recordFailure("v1") }
        repeat(StreamRetryBudget.MAX_RETRIES) { budget.recordFailure("v2") }
        assertNull(budget.recordFailure("v2"))

        budget.clear("v1")

        assertEquals(1, budget.recordFailure("v1"))
        assertNull(budget.recordFailure("v2"))
        assertEquals(0, budget.attemptsLeft("v2"))
    }

    @Test
    fun aTrackNobodyHasFailedYetStartsWithAFullBudget() {
        val budget = StreamRetryBudget()
        repeat(StreamRetryBudget.MAX_RETRIES + 2) { budget.recordFailure("v1") }
        assertEquals(StreamRetryBudget.MAX_RETRIES, budget.attemptsLeft("fresh"))
        assertEquals(1, budget.recordFailure("fresh"))
    }

    /**
     * The shipped numbers, pinned. They are user-visible behaviour — how long
     * a "Reconnecting…" can last before an error, and how much silence a
     * backoff costs — so a change here should be a deliberate decision rather
     * than a silent retune.
     */
    @Test
    fun theBudgetNumbersAreTheShippedNumbers() {
        assertEquals(3, StreamRetryBudget.MAX_RETRIES)
        assertEquals(1_500L, StreamRetryBudget.RETRY_BACKOFF_MS)
        assertEquals(10_000L, StreamRetryBudget.RESET_AFTER_PLAYING_MS)
    }
}
