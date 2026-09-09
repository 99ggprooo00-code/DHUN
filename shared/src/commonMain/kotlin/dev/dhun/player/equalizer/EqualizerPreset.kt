package dev.dhun.player.equalizer

import kotlin.math.abs

/**
 * A named 10-band curve. [id] matches VLC's `preset_list` so a desktop
 * actual can round-trip against libVLC preset names; [gainsDb] and
 * [preampDb] are copied from VLC `eqz_preset_10b` (tiny ±1e-15 leftovers
 * stored as 0).
 */
data class EqualizerPreset(
    val id: String,
    val displayName: String,
    val preampDb: Float,
    val gainsDb: List<Float>,
) {
    init {
        require(id.isNotBlank()) { "preset id must be non-blank" }
        require(gainsDb.size == EqualizerBands.COUNT) {
            "preset '$id' must have ${EqualizerBands.COUNT} bands, was ${gainsDb.size}"
        }
    }
}

/**
 * The 18 VLC 10-band presets, plus the synthetic [CUSTOM_ID] used when the
 * user moves a slider off every stock curve.
 */
object EqualizerPresets {
    const val CUSTOM_ID: String = "custom"

    val FLAT: EqualizerPreset = preset("flat", "Flat", 12f, z())
    val CLASSICAL: EqualizerPreset = preset(
        "classical", "Classical", 12f,
        g(0f, 0f, 0f, 0f, 0f, 0f, -7.2f, -7.2f, -7.2f, -9.6f),
    )
    val CLUB: EqualizerPreset = preset(
        "club", "Club", 6f,
        g(0f, 0f, 8.0f, 5.6f, 5.6f, 5.6f, 3.2f, 0f, 0f, 0f),
    )
    val DANCE: EqualizerPreset = preset(
        "dance", "Dance", 5f,
        g(9.6f, 7.2f, 2.4f, 0f, 0f, -5.6f, -7.2f, -7.2f, 0f, 0f),
    )
    val FULL_BASS: EqualizerPreset = preset(
        "fullbass", "Full bass", 5f,
        g(-8.0f, 9.6f, 9.6f, 5.6f, 1.6f, -4.0f, -8.0f, -10.4f, -11.2f, -11.2f),
    )
    val FULL_BASS_AND_TREBLE: EqualizerPreset = preset(
        "fullbasstreble", "Full bass and treble", 4f,
        g(7.2f, 5.6f, 0f, -7.2f, -4.8f, 1.6f, 8.0f, 11.2f, 12.0f, 12.0f),
    )
    val FULL_TREBLE: EqualizerPreset = preset(
        "fulltreble", "Full treble", 3f,
        g(-9.6f, -9.6f, -9.6f, -4.0f, 2.4f, 11.2f, 16.0f, 16.0f, 16.0f, 16.8f),
    )
    val HEADPHONES: EqualizerPreset = preset(
        "headphones", "Headphones", 4f,
        g(4.8f, 11.2f, 5.6f, -3.2f, -2.4f, 1.6f, 4.8f, 9.6f, 12.8f, 14.4f),
    )
    val LARGE_HALL: EqualizerPreset = preset(
        "largehall", "Large Hall", 5f,
        g(10.4f, 10.4f, 5.6f, 5.6f, 0f, -4.8f, -4.8f, -4.8f, 0f, 0f),
    )
    val LIVE: EqualizerPreset = preset(
        "live", "Live", 7f,
        g(-4.8f, 0f, 4.0f, 5.6f, 5.6f, 5.6f, 4.0f, 2.4f, 2.4f, 2.4f),
    )
    val PARTY: EqualizerPreset = preset(
        "party", "Party", 6f,
        g(7.2f, 7.2f, 0f, 0f, 0f, 0f, 0f, 0f, 7.2f, 7.2f),
    )
    val POP: EqualizerPreset = preset(
        "pop", "Pop", 6f,
        g(-1.6f, 4.8f, 7.2f, 8.0f, 5.6f, 0f, -2.4f, -2.4f, -1.6f, -1.6f),
    )
    val REGGAE: EqualizerPreset = preset(
        "reggae", "Reggae", 8f,
        g(0f, 0f, 0f, -5.6f, 0f, 6.4f, 6.4f, 0f, 0f, 0f),
    )
    val ROCK: EqualizerPreset = preset(
        "rock", "Rock", 5f,
        g(8.0f, 4.8f, -5.6f, -8.0f, -3.2f, 4.0f, 8.8f, 11.2f, 11.2f, 11.2f),
    )
    val SKA: EqualizerPreset = preset(
        "ska", "Ska", 6f,
        g(-2.4f, -4.8f, -4.0f, 0f, 4.0f, 5.6f, 8.8f, 9.6f, 11.2f, 9.6f),
    )
    val SOFT: EqualizerPreset = preset(
        "soft", "Soft", 5f,
        g(4.8f, 1.6f, 0f, -2.4f, 0f, 4.0f, 8.0f, 9.6f, 11.2f, 12.0f),
    )
    val SOFT_ROCK: EqualizerPreset = preset(
        "softrock", "Soft rock", 7f,
        g(4.0f, 4.0f, 2.4f, 0f, -4.0f, -5.6f, -3.2f, 0f, 2.4f, 8.8f),
    )
    val TECHNO: EqualizerPreset = preset(
        "techno", "Techno", 5f,
        g(8.0f, 5.6f, 0f, -5.6f, -4.8f, 0f, 8.0f, 9.6f, 9.6f, 8.8f),
    )

    val ALL: List<EqualizerPreset> = listOf(
        FLAT, CLASSICAL, CLUB, DANCE, FULL_BASS, FULL_BASS_AND_TREBLE,
        FULL_TREBLE, HEADPHONES, LARGE_HALL, LIVE, PARTY, POP, REGGAE,
        ROCK, SKA, SOFT, SOFT_ROCK, TECHNO,
    )

    fun byId(id: String): EqualizerPreset? = ALL.firstOrNull { it.id == id }

    /**
     * First stock preset whose preamp and 10 gains match [gainsDb]/[preampDb]
     * within [epsilon] dB, or null (caller should treat as custom).
     */
    fun matching(
        gainsDb: List<Float>,
        preampDb: Float,
        epsilon: Float = MATCH_EPSILON_DB,
    ): EqualizerPreset? = ALL.firstOrNull { preset ->
        abs(preset.preampDb - preampDb) <= epsilon &&
            preset.gainsDb.indices.all { i ->
                abs(preset.gainsDb[i] - (gainsDb.getOrElse(i) { 0f })) <= epsilon
            }
    }

    const val MATCH_EPSILON_DB: Float = 0.05f

    private fun preset(
        id: String,
        displayName: String,
        preampDb: Float,
        gainsDb: List<Float>,
    ): EqualizerPreset = EqualizerPreset(
        id = id,
        displayName = displayName,
        preampDb = preampDb,
        gainsDb = gainsDb,
    )

    private fun z(): List<Float> = EqualizerBands.zeros()

    private fun g(vararg gains: Float): List<Float> {
        require(gains.size == EqualizerBands.COUNT)
        return gains.toList()
    }
}
