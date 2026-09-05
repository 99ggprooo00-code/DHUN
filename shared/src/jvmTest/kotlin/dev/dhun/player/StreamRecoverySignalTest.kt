package dev.dhun.player

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 14: 403 recovery flag for Recovering → Reconnecting… UI. */
class StreamRecoverySignalTest {

    @BeforeTest
    fun reset() {
        StreamRecoverySignal.end()
    }

    @Test
    fun beginEndTogglesActive() {
        assertFalse(StreamRecoverySignal.active.value)
        StreamRecoverySignal.begin()
        assertTrue(StreamRecoverySignal.active.value)
        StreamRecoverySignal.end()
        assertFalse(StreamRecoverySignal.active.value)
    }

    @Test
    fun endIsIdempotent() {
        StreamRecoverySignal.begin()
        StreamRecoverySignal.end()
        StreamRecoverySignal.end()
        assertFalse(StreamRecoverySignal.active.value)
    }
}
