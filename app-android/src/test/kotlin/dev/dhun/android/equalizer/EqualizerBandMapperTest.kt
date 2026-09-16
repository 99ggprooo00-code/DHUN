package dev.dhun.android.equalizer

import dev.dhun.player.equalizer.EqualizerBands
import dev.dhun.player.equalizer.EqualizerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4.3: the shared-curve → DSP-band mapping is pure Kotlin (no `AudioEffect`
 * imports), so it is tested on the JVM without Robolectric. The thin engine
 * around it is hardware-gated (S3): no emulator faithfully reproduces a DSP
 * equalizer, so the engine's binder calls are reviewed, not executed.
 */
class EqualizerBandMapperTest {

    private fun state(preampDb: Float = 0f, gainsDb: List<Float> = EqualizerBands.zeros()) =
        EqualizerState(enabled = true, preampDb = preampDb, gainsDb = gainsDb)

    @Test
    fun `flat state maps to silence on a typical five-band dsp`() {
        val levels = EqualizerBandMapper.map(
            state(),
            deviceCenterFreqMhz = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000),
            minLevelMb = -1500,
            maxLevelMb = 1500,
        )
        assertEquals(listOf<Short>(0, 0, 0, 0, 0), levels)
    }

    @Test
    fun `device centers on shared centers pass gains through exactly`() {
        val gains = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)
        val levels = EqualizerBandMapper.map(
            state(gainsDb = gains),
            // 60 Hz, 310 Hz, 1000 Hz, 16000 Hz — all shared centers, in mHz.
            deviceCenterFreqMhz = listOf(60_000, 310_000, 1_000_000, 16_000_000),
            minLevelMb = -2000,
            maxLevelMb = 2000,
        )
        assertEquals(listOf<Short>(100, 300, 500, 1000), levels)
    }

    @Test
    fun `geometric midpoint interpolates halfway in log space`() {
        // sqrt(60 * 170) Hz ≈ 100.995 Hz is the log-midpoint of the first
        // shared interval; gains 0 → 10 dB must read 5 dB there.
        val gains = listOf(0f, 10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val levels = EqualizerBandMapper.map(
            state(gainsDb = gains),
            deviceCenterFreqMhz = listOf(100_995),
            minLevelMb = -2000,
            maxLevelMb = 2000,
        )
        assertEquals(listOf<Short>(500), levels)
    }

    @Test
    fun `preamp folds into every band`() {
        val levels = EqualizerBandMapper.map(
            state(preampDb = -4.5f),
            deviceCenterFreqMhz = listOf(60_000, 1_000_000, 16_000_000),
            minLevelMb = -2000,
            maxLevelMb = 2000,
        )
        assertEquals(listOf<Short>(-450, -450, -450), levels)
    }

    @Test
    fun `shared range clamps before device range`() {
        // +20 preamp + +20 band would ask +40 dB: the shared ±20 dB clamp
        // applies first (→ +20 dB = 2000 mB), then the tighter device max.
        val levels = EqualizerBandMapper.map(
            state(preampDb = 20f, gainsDb = List(10) { 20f }),
            deviceCenterFreqMhz = listOf(1_000_000),
            minLevelMb = -1500,
            maxLevelMb = 1200,
        )
        assertEquals(listOf<Short>(1200), levels)
    }

    @Test
    fun `centers outside the shared range clamp to the edge bands`() {
        val gains = listOf(-10f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 8f)
        val levels = EqualizerBandMapper.map(
            state(gainsDb = gains),
            deviceCenterFreqMhz = listOf(20_000, 30_000_000), // 20 Hz, 30 kHz
            minLevelMb = -2000,
            maxLevelMb = 2000,
        )
        assertEquals(listOf<Short>(-1000, 800), levels)
    }

    @Test
    fun `empty device bands and corrupt inputs degrade cleanly`() {
        assertTrue(
            EqualizerBandMapper.map(state(), emptyList(), -1500, 1500).isEmpty(),
        )
        // NaN/∞ gains (should never escape the session's clampGain, but the
        // mapper must not propagate them to the DSP) read as 0 dB.
        val levels = EqualizerBandMapper.map(
            state(preampDb = Float.NaN, gainsDb = List(10) { Float.POSITIVE_INFINITY }),
            deviceCenterFreqMhz = listOf(1_000_000),
            minLevelMb = -1500,
            maxLevelMb = 1500,
        )
        assertEquals(listOf<Short>(0), levels)
        // A swapped device range is normalized, not trusted blindly.
        val swapped = EqualizerBandMapper.map(
            state(preampDb = 10f),
            deviceCenterFreqMhz = listOf(1_000_000),
            minLevelMb = 1500,
            maxLevelMb = -1500,
        )
        assertEquals(listOf<Short>(1000), swapped)
    }
}
