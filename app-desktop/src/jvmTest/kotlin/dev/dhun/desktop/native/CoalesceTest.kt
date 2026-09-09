package dev.dhun.desktop.native

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-core tests for the jump-list rewrite throttle: the shell commit is
 * rate-limited to one per window and requests inside the window coalesce
 * into a single trailing run.
 */
class CoalesceTest {

    private val interval = JumpList.MIN_INTERVAL_MS

    @Test
    fun `first run after the window elapses is immediate`() {
        assertEquals(0, JumpList.nextDelayMs(nowMs = 10_000, lastRunMs = 10_000 - interval, intervalMs = interval))
        assertEquals(0, JumpList.nextDelayMs(nowMs = 10_000, lastRunMs = 0, intervalMs = interval))
    }

    @Test
    fun `inside the window the remaining time is the delay`() {
        assertEquals(500, JumpList.nextDelayMs(nowMs = 1_000, lastRunMs = 1_000 - (interval - 500), intervalMs = interval))
        assertEquals(1, JumpList.nextDelayMs(nowMs = 1_000, lastRunMs = 1_000 - (interval - 1), intervalMs = interval))
    }

    @Test
    fun `a skewed clock never produces a delay past the interval`() {
        assertEquals(interval, JumpList.nextDelayMs(nowMs = 1_000, lastRunMs = 5_000, intervalMs = interval))
    }

    @Test
    fun `delay is never negative at the boundary`() {
        assertEquals(0, JumpList.nextDelayMs(nowMs = 1_000, lastRunMs = 1_000 - interval - 1, intervalMs = interval))
    }
}
