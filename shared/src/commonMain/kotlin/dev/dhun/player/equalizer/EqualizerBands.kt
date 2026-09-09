package dev.dhun.player.equalizer

/**
 * libVLC 10-band equalizer geometry. Frequencies match
 * `f_vlc_frequency_table_10b` in VLC's `equalizer_presets.h`.
 *
 * Gain range is libVLC's: −20 dB .. +20 dB. Values outside are clamped
 * before they reach an engine. This object is platform-neutral — it does
 * not import vlcj or Android [android.media.audiofx.AudioEffect].
 */
object EqualizerBands {
    const val COUNT: Int = 10
    const val MIN_GAIN_DB: Float = -20f
    const val MAX_GAIN_DB: Float = 20f

    /** Centre frequencies, low → high, matching libVLC band indices 0..9. */
    val FREQUENCIES_HZ: List<Int> = listOf(
        60, 170, 310, 600, 1_000, 3_000, 6_000, 12_000, 14_000, 16_000,
    )

    fun clampGain(gainDb: Float): Float =
        if (gainDb.isNaN() || gainDb.isInfinite()) 0f
        else gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)

    fun clampGains(gains: List<Float>): List<Float> {
        val clamped = gains.take(COUNT).map(::clampGain)
        return if (clamped.size == COUNT) clamped
        else clamped + List(COUNT - clamped.size) { 0f }
    }

    fun zeros(): List<Float> = List(COUNT) { 0f }

    /** UI label: `"60 Hz"`, `"1 kHz"`, `"16 kHz"`. */
    fun frequencyLabel(hz: Int): String =
        if (hz >= 1_000 && hz % 1_000 == 0) "${hz / 1_000} kHz" else "$hz Hz"
}
