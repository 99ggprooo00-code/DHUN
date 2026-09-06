package dev.dhun.presentation

import dev.dhun.ui.player.TransportPress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests the same press lifecycle used by the actual Previous/Next pointer handlers. */
class TransportPressTest {
    @Test
    fun releaseBeforeDeadlineSkipsExactlyOnce() {
        val calls = mutableListOf<String>()
        val press = recordingPress(calls)
        press.initialWaitFinished(true)
        press.finish()
        assertEquals(listOf("tap"), calls)
        assertFalse(press.holding)
    }

    @Test
    fun cancelledPressDoesNotSkipOrStartSeeking() {
        val calls = mutableListOf<String>()
        val press = recordingPress(calls)
        press.initialWaitFinished(false)
        press.finish()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun deadlineStartsHoldAndCancellationOrReleaseStopsItOnce() {
        val calls = mutableListOf<String>()
        val press = recordingPress(calls)
        press.initialWaitFinished(null)
        assertTrue(press.holding)
        assertEquals(listOf("hold"), calls)
        press.finish() // same cleanup path for pointer release, cancellation or disposal
        press.finish()
        assertFalse(press.holding)
        assertEquals(listOf("hold", "release"), calls)
    }

    @Test
    fun cancellationBeforeTheInitialWaitFinishesHasNoAction() {
        val calls = mutableListOf<String>()
        recordingPress(calls).finish()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun failedHoldCallbackStillGetsItsMatchingCleanup() {
        val calls = mutableListOf<String>()
        val press = TransportPress(
            onTap = { calls += "tap" },
            onHold = { calls += "hold"; error("hold startup failed") },
            onRelease = { calls += "release" },
        )
        assertFailsWith<IllegalStateException> {
            try { press.initialWaitFinished(null) } finally { press.finish() }
        }
        assertEquals(listOf("hold", "release"), calls)
        assertFalse(press.holding)
    }

    private fun recordingPress(calls: MutableList<String>) = TransportPress(
        onTap = { calls += "tap" },
        onHold = { calls += "hold" },
        onRelease = { calls += "release" },
    )
}
