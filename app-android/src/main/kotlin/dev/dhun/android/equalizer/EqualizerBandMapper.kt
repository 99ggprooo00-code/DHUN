package dev.dhun.android.equalizer

import dev.dhun.player.equalizer.EqualizerBands
import dev.dhun.player.equalizer.EqualizerState
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Pure shared-curve → device-band mapping for the Android EQ engine (S4.3).
 *
 * The shared model is always 10 bands at fixed centers
 * ([EqualizerBands.FREQUENCIES_HZ], ±[EqualizerBands.MIN_GAIN_DB]…MAX dB);
 * an `AudioEffect.Equalizer` exposes whatever the DSP has (usually 5 bands)
 * at device-chosen centers in **millihertz**, with a device level range in
 * **millibels**. This object bridges the two with no Android imports, so it
 * is unit-testable on the JVM:
 *
 * - each device band reads the shared curve at its center frequency,
 *   interpolated in **log-frequency space** (gains are perceptual; linear
 *   interpolation would overweight the top octave), clamped to the edge
 *   values outside the shared range;
 * - the shared preamp (which `AudioEffect` has no knob for) is folded into
 *   every band, then the total is clamped to the shared ±20 dB range before
 *   device clamping, so a +20 preamp + +20 band cannot demand +40 dB;
 * - the result is rounded to millibels and clamped to the device range, so
 *   the engine can pass it straight to `setBandLevel` without further checks.
 */
object EqualizerBandMapper {

    /**
     * @param deviceCenterFreqMhz device band centers in millihertz, as
     *   returned by `Equalizer.getCenterFreq` (ascending).
     * @param minLevelMb/maxLevelMb the device range in millibels, as returned
     *   by `Equalizer.getBandLevelRange` ([min, max]).
     * @return one millibel level per device band, in order.
     */
    fun map(
        state: EqualizerState,
        deviceCenterFreqMhz: List<Int>,
        minLevelMb: Short,
        maxLevelMb: Short,
    ): List<Short> {
        if (deviceCenterFreqMhz.isEmpty()) return emptyList()
        val centers = EqualizerBands.FREQUENCIES_HZ
        val gains = state.gainsDb
        val lo = minOf(minLevelMb.toInt(), maxLevelMb.toInt())
        val hi = maxOf(minLevelMb.toInt(), maxLevelMb.toInt())
        return deviceCenterFreqMhz.map { mhz ->
            val hz = (mhz / 1000.0).coerceAtLeast(1.0)
            val curve = curveGainDb(hz, centers, gains)
            val preamp = state.preampDb.takeUnless { it.isNaN() } ?: 0f
            val total = (curve + preamp).coerceIn(
                EqualizerBands.MIN_GAIN_DB,
                EqualizerBands.MAX_GAIN_DB,
            )
            (total * 100).roundToInt().coerceIn(lo, hi).toShort()
        }
    }

    private fun curveGainDb(hz: Double, centers: List<Int>, gains: List<Float>): Double {
        val first = centers.first().toDouble()
        val last = centers.last().toDouble()
        val g0 = gains.getOrElse(0) { 0f }.safe()
        val gLast = gains.getOrElse(centers.lastIndex) { 0f }.safe()
        if (hz <= first) return g0
        if (hz >= last) return gLast
        val upper = centers.indexOfFirst { it.toDouble() >= hz }.coerceAtLeast(1)
        val f0 = centers[upper - 1].toDouble()
        val f1 = centers[upper].toDouble()
        val t = (ln(hz) - ln(f0)) / (ln(f1) - ln(f0))
        val a = gains.getOrElse(upper - 1) { 0f }.safe()
        val b = gains.getOrElse(upper) { 0f }.safe()
        return a + t * (b - a)
    }

    private fun Float.safe(): Double = if (isNaN() || isInfinite()) 0.0 else toDouble()
}
