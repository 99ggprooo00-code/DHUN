package dev.dhun.player.equalizer

/** One slider in an equalizer strip. */
data class EqualizerBandPresentation(
    val index: Int,
    val frequencyHz: Int,
    val label: String,
    val gainDb: Float,
)

/** One row in the preset picker. */
data class EqualizerPresetPresentation(
    val id: String,
    val displayName: String,
    val selected: Boolean,
)

/**
 * UI-facing snapshot derived from [EqualizerState]. No Compose, no resources
 * — a later screen (outside this slice) can bind it without touching
 * [dev.dhun.player.DhunPlayer].
 */
data class EqualizerUiModel(
    val enabled: Boolean,
    val selectedPresetId: String,
    val isCustom: Boolean,
    val presets: List<EqualizerPresetPresentation>,
    val bands: List<EqualizerBandPresentation>,
    val preampDb: Float,
    val minGainDb: Float = EqualizerBands.MIN_GAIN_DB,
    val maxGainDb: Float = EqualizerBands.MAX_GAIN_DB,
)

fun EqualizerState.toUiModel(): EqualizerUiModel {
    val selected = if (isCustom) EqualizerPresets.CUSTOM_ID else presetId
    val stock = EqualizerPresets.ALL.map { preset ->
        EqualizerPresetPresentation(
            id = preset.id,
            displayName = preset.displayName,
            selected = preset.id == selected,
        )
    }
    val customRow = EqualizerPresetPresentation(
        id = EqualizerPresets.CUSTOM_ID,
        displayName = "Custom",
        selected = isCustom,
    )
    return EqualizerUiModel(
        enabled = enabled,
        selectedPresetId = selected,
        isCustom = isCustom,
        presets = stock + customRow,
        bands = EqualizerBands.FREQUENCIES_HZ.mapIndexed { index, hz ->
            EqualizerBandPresentation(
                index = index,
                frequencyHz = hz,
                label = EqualizerBands.frequencyLabel(hz),
                gainDb = band(index),
            )
        },
        preampDb = preampDb,
    )
}
