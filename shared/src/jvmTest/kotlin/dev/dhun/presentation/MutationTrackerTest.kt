package dev.dhun.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The mutation contract the native audit found missing: a duplicate tap is
 * ignored, pending state is published before the call returns, a failure is
 * captured as a message instead of escaping the coroutine, and the key is
 * released so the user can retry.
 *
 * Every assertion here is about a defect that shipped: `scope.launch {
 * runCatching { … } }` gave no feedback at all, and the un-guarded variants
 * (`createPlaylist`, `addToPlaylist`) let the exception reach the thread's
 * uncaught-exception handler — an app crash on Android.
 */
class MutationTrackerTest {

    /** Captures anything that escapes a coroutine in this test's scope. */
    private class Harness {
        val escaped = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined +
                CoroutineExceptionHandler { _, error -> escaped += error },
        )
        val tracker = MutationTracker(scope)

        fun close() = scope.cancel()
    }

    private suspend fun await(timeoutMs: Long = 2_000, check: () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(5) }
    }

    @Test
    fun pendingIsPublishedBeforeTheCallReturns() {
        val h = Harness()
        try {
            val gate = CompletableDeferred<Unit>()
            val started = h.tracker.run(key = "k", failureMessage = "failed") { gate.await() }
            assertTrue(started, "the first submission starts")
            assertTrue(h.tracker.state.value.isRunning("k"), "pending is visible without waiting for a frame")
            assertTrue(h.tracker.state.value.isBusy)
            gate.complete(Unit)
            runBlocking { await { !h.tracker.state.value.isBusy } }
        } finally {
            h.close()
        }
    }

    @Test
    fun aSecondSubmissionWhileRunningIsIgnoredNotQueued() {
        val h = Harness()
        try {
            val gate = CompletableDeferred<Unit>()
            var runs = 0
            assertTrue(h.tracker.run("delete", "failed") { runs++; gate.await() })
            assertFalse(h.tracker.run("delete", "failed") { runs++; gate.await() }, "duplicate rejected")
            assertFalse(h.tracker.run("delete", "failed") { runs++ }, "still rejected while pending")
            assertEquals(1, runs)
            gate.complete(Unit)
            runBlocking { await { !h.tracker.state.value.isBusy } }
            // Released after completion, so a deliberate retry is allowed.
            assertTrue(h.tracker.run("delete", "failed") { runs++ })
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertEquals(2, runs)
        } finally {
            h.close()
        }
    }

    @Test
    fun successRunsTheCallbackOnceAndLeavesNoError() {
        val h = Harness()
        try {
            var successes = 0
            h.tracker.run("k", "failed", onSuccess = { successes++ }) { }
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertEquals(1, successes, "a completed write confirms exactly once")
            assertNull(h.tracker.state.value.errorMessage)
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailureBecomesAMessageAndNeverEscapesTheCoroutine() {
        val h = Harness()
        try {
            var successes = 0
            h.tracker.run("k", "Could not save. Try again.", onSuccess = { successes++ }) {
                error("disk full")
            }
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertEquals("Could not save. Try again.", h.tracker.state.value.errorMessage)
            assertEquals(0, successes, "a failed write must not report success")
            assertTrue(h.escaped.isEmpty(), "the exception was captured, not thrown out: ${h.escaped}")
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailureKeepsTheControlAvailableForRetry() {
        val h = Harness()
        try {
            var attempt = 0
            h.tracker.run("k", "failed") { attempt++; error("first attempt fails") }
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertEquals("failed", h.tracker.state.value.errorMessage)

            // The same key can run again: the failure released it.
            h.tracker.run("k", "failed") { attempt++; }
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertEquals(2, attempt)
            assertNull(h.tracker.state.value.errorMessage, "a successful retry clears the failure")
            assertTrue(h.escaped.isEmpty())
        } finally {
            h.close()
        }
    }

    @Test
    fun cancellationIsRethrownAndReleasesTheKey() {
        val h = Harness()
        try {
            val gate = CompletableDeferred<Unit>()
            h.tracker.run("k", "failed") { gate.await() }
            assertTrue(h.tracker.state.value.isRunning("k"))
            gate.completeExceptionally(CancellationException("screen closed"))
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertNull(h.tracker.state.value.errorMessage, "cancellation is not a user-facing failure")
            assertTrue(h.escaped.isEmpty(), "cancellation must not be reported as an uncaught error")
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailureIsScopedToTheActionThatFailed() {
        val h = Harness()
        try {
            h.tracker.run("audio-cache", "audio failed") { error("boom") }
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertEquals("audio failed", h.tracker.state.value.errorFor("audio-cache"))
            assertNull(h.tracker.state.value.errorFor("lyrics-cache"), "another dialog must not show it")
            assertNull(h.tracker.state.value.screenErrorMessage, "a dialog owns the message, not the banner")

            // A preference write has no owner dialog, so the screen shows it.
            h.tracker.report("Could not save that setting.")
            assertEquals("Could not save that setting.", h.tracker.state.value.screenErrorMessage)
            assertNull(h.tracker.state.value.errorFor("audio-cache"), "the dialog's own message was replaced")
        } finally {
            h.close()
        }
    }

    @Test
    fun reportedFailuresCanBeDismissed() {
        val h = Harness()
        try {
            h.tracker.report("Could not save that setting.")
            assertEquals("Could not save that setting.", h.tracker.state.value.errorMessage)
            h.tracker.dismissError()
            assertNull(h.tracker.state.value.errorMessage)
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedActionRollsBackBeforeTheFailureIsPublished() {
        val h = Harness()
        try {
            var rolledBack = false
            var messageWhileRollingBack: String? = "unset"
            h.tracker.run(
                "reset-settings",
                "reset failed",
                onFailure = {
                    rolledBack = true
                    // The screen must be put back to the stored state *before*
                    // it announces the failure, so the user reads the message
                    // next to values that are actually true.
                    messageWhileRollingBack = h.tracker.state.value.errorMessage
                },
            ) { error("boom") }

            assertTrue(rolledBack, "the rollback runs for a failed action")
            assertNull(messageWhileRollingBack, "the rollback runs before the message is published")
            assertEquals("reset failed", h.tracker.state.value.errorFor("reset-settings"))
            assertFalse(h.tracker.state.value.isRunning("reset-settings"), "the key is released for a retry")
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailingRollbackStillPublishesTheRealFailure() {
        val h = Harness()
        try {
            // The rollback reads the same store that just failed; if that read
            // throws too the user must still be told what happened.
            h.tracker.run("reset-settings", "reset failed", onFailure = { error("read back failed") }) {
                error("boom")
            }
            assertEquals("reset failed", h.tracker.state.value.errorFor("reset-settings"))
            assertFalse(h.tracker.state.value.isRunning("reset-settings"))
            assertTrue(h.escaped.isEmpty(), "neither failure may escape the coroutine: ${h.escaped}")
        } finally {
            h.close()
        }
    }

    @Test
    fun aSuccessfulActionNeverRollsBack() {
        val h = Harness()
        try {
            var rolledBack = false
            h.tracker.run("reset-settings", "reset failed", onFailure = { rolledBack = true }) { }
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertFalse(rolledBack, "success must not undo the stored change")
            assertNull(h.tracker.state.value.errorMessage)
        } finally {
            h.close()
        }
    }

    @Test
    fun aCancelledActionDoesNotRollBack() {
        val h = Harness()
        try {
            var rolledBack = false
            val gate = CompletableDeferred<Unit>()
            h.tracker.run("k", "failed", onFailure = { rolledBack = true }) { gate.await() }
            gate.completeExceptionally(CancellationException("screen closed"))
            runBlocking { await { !h.tracker.state.value.isBusy } }
            assertFalse(rolledBack, "cancellation is not a failed write")
        } finally {
            h.close()
        }
    }

    @Test
    fun independentKeysRunConcurrently() {
        val h = Harness()
        try {
            val a = CompletableDeferred<Unit>()
            val b = CompletableDeferred<Unit>()
            assertTrue(h.tracker.run("a", "a failed") { a.await() })
            assertTrue(h.tracker.run("b", "b failed") { b.await() })
            assertEquals(setOf("a", "b"), h.tracker.state.value.runningKeys)
            b.complete(Unit)
            runBlocking { await { !h.tracker.state.value.isRunning("b") } }
            assertTrue(h.tracker.state.value.isRunning("a"), "one key finishing must not release another")
            a.complete(Unit)
            runBlocking { await { !h.tracker.state.value.isBusy } }
        } catch (unexpected: Throwable) {
            fail("unexpected failure: $unexpected")
        } finally {
            h.close()
        }
    }
}
