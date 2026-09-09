package dev.dhun.player.equalizer

/**
 * Numbers a vlcj actual feeds `uk.co.caprica.vlcj.player.base.Equalizer`.
 *
 * Kept in commonMain so the mapping is unit-testable without libVLC. The
 * desktop player is the only consumer today; Android AudioEffect is a
 * separate later stream and must not be inferred from this type.
 *
 * When [enabled] is false the actual **clears** the native equalizer
 * (`setEqualizer(null)`); [preampDb]/[ampsDb] are still populated so a
 * later enable has a curve to restore.
 */
data class VlcEqualizerCommand(
    val enabled: Boolean,
    val preampDb: Float,
    val ampsDb: List<Float>,
) {
    companion object {
        fun from(state: EqualizerState): VlcEqualizerCommand = VlcEqualizerCommand(
            enabled = state.enabled,
            preampDb = EqualizerBands.clampGain(state.preampDb),
            ampsDb = EqualizerBands.clampGains(state.gainsDb),
        )
    }
}
