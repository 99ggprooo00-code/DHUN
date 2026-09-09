package dev.dhun.player.equalizer

/**
 * Snapshot of the equalizer the UI and engines consume. Disabled by default
 * so constructing a session is acoustically a no-op until the user turns it
 * on. Band count is always [EqualizerBands.COUNT].
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val presetId: String = EqualizerPresets.FLAT.id,
    val preampDb: Float = EqualizerPresets.FLAT.preampDb,
    val gainsDb: List<Float> = EqualizerBands.zeros(),
) {
    val isCustom: Boolean get() = presetId == EqualizerPresets.CUSTOM_ID

    fun band(index: Int): Float = gainsDb.getOrElse(index) { 0f }
}
