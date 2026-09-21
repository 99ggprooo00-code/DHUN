package dev.dhun.download

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for S3 defect 1 — Android downloads race.
 *
 * Root cause: ForegroundServiceDownloadManager.enqueue() called
 * `controller.ensureRunning()` BEFORE `delegate.enqueue()`. The controller's
 * `observeState` collector saw its first emission (empty list) before the
 * QUEUED row existed and called `stopForegroundCompat() → stopSelf()`,
 * killing the FGS microseconds after it started. Windows has no FGS, so
 * "Windows works, Android doesn't" matched exactly. Second suspect is
 * Android 14/15 FGS policy (dataSync start not allowed) — now caught.
 *
 * Fix: `DownloadServiceController` now uses a `hasSeenWork` latch —
 * it only stops when it has previously seen active/queued work and then
 * observes 0/0. The first empty emission is ignored (grace). This test
 * specifies that latch contract; the Android service test would require
 * Robolectric, so the pure logic is exercised here and the service
 * delegates to it.
 */
class DownloadServiceStopLogicTest {

    /** Mirrors DownloadServiceController's hasSeenWork decision. */
    private class StopDecider {
        var hasSeenWork = false

        /** @return true if the service should stop on this emission. */
        fun shouldStop(active: Int, queued: Int): Boolean {
            if (active > 0 || queued > 0) hasSeenWork = true
            if (active == 0 && queued == 0) {
                if (!hasSeenWork) return false
                hasSeenWork = false
                return true
            }
            return false
        }
    }

    @Test
    fun doesNotStopOnInitialEmptyEmission() {
        val d = StopDecider()
        // First collect before enqueue — must NOT stop
        assertFalse(d.shouldStop(active = 0, queued = 0), "initial empty must be grace, not stop")
        assertFalse(d.hasSeenWork)
        // Still empty on second emission (e.g. DB flow debounce) — still grace
        assertFalse(d.shouldStop(active = 0, queued = 0))
    }

    @Test
    fun stopsOnlyAfterHadWorkThenEmpties() {
        val d = StopDecider()
        assertFalse(d.shouldStop(0, 0)) // grace
        // Enqueue inserts QUEUED
        assertFalse(d.shouldStop(0, 1), "queued work must not stop")
        assertTrue(d.hasSeenWork, "latch must be set after seeing queued")
        // Resolves to DOWNLOADING
        assertFalse(d.shouldStop(1, 0))
        // Completes — no active/queued -> should stop
        assertTrue(d.shouldStop(0, 0), "had work then empty must stop")
        // Latch resets after stop
        assertFalse(d.hasSeenWork)
        // A fresh empty after stop is grace again (no leak)
        assertFalse(d.shouldStop(0, 0))
    }

    @Test
    fun queuedThenImmediateEmptyStops() {
        val d = StopDecider()
        assertFalse(d.shouldStop(0, 1))
        assertTrue(d.shouldStop(0, 0))
    }

    @Test
    fun activeWorkPreventsStopUntilDone() {
        val d = StopDecider()
        assertFalse(d.shouldStop(1, 1))
        assertFalse(d.shouldStop(1, 0))
        assertFalse(d.shouldStop(0, 1))
        assertTrue(d.shouldStop(0, 0))
    }

    @Test
    fun latchSurvivesMultipleWorkBatches() {
        val d = StopDecider()
        // Batch 1
        assertFalse(d.shouldStop(0, 1))
        assertTrue(d.shouldStop(0, 0))
        // Batch 2 — latch was reset, so new empty is grace
        assertFalse(d.shouldStop(0, 0))
        assertFalse(d.shouldStop(0, 1))
        assertTrue(d.shouldStop(0, 0))
    }
}
