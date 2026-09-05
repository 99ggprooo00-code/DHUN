package dev.dhun.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioCacheBudgetTest {

    @Test
    fun defaultIsOneGibInBytes() {
        assertEquals(1024L * 1024L * 1024L, AudioCacheBudget.bytesForMb(AudioCacheBudget.DEFAULT_MB))
        assertEquals(1024L * 1024L * 1024L, AudioCacheBudget.bytesForMb(1024))
    }

    @Test
    fun zeroMeansUnlimitedSentinel() {
        assertEquals(AudioCacheBudget.UNLIMITED_BYTES, AudioCacheBudget.bytesForMb(0))
        assertEquals(0, AudioCacheBudget.clampMb(0))
    }

    @Test
    fun negativeFallsBackToDefault() {
        assertEquals(AudioCacheBudget.DEFAULT_MB, AudioCacheBudget.clampMb(-1))
        assertEquals(AudioCacheBudget.bytesForMb(1024), AudioCacheBudget.bytesForMb(-50))
    }

    @Test
    fun hardCeilingAtMaxMb() {
        assertEquals(AudioCacheBudget.MAX_MB, AudioCacheBudget.clampMb(99_000))
        assertEquals(
            AudioCacheBudget.MAX_MB.toLong() * 1024L * 1024L,
            AudioCacheBudget.bytesForMb(99_000),
        )
    }

    @Test
    fun smallBudgetsPreserveExactBytes() {
        assertEquals(64L * 1024L * 1024L, AudioCacheBudget.bytesForMb(64))
        assertTrue(AudioCacheBudget.bytesForMb(1) > 0)
    }
}
