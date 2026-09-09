package dev.dhun.player.equalizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure equalizer controller: presets, per-band gains, preamp, enable flag.
 * No player engine, no platform types. Mutations publish [state] and push
 * the snapshot to [engine].
 *
 * Lives beside [dev.dhun.player.DhunPlayer] rather than on it — the player
 * contract is frozen; platforms that can apply EQ hold a session and call
 * [reapply] after a new media item (libVLC drops the filter on play).
 *
 * [presetId] is derived from the curve: a stock id when preamp + 10 gains
 * match a VLC preset, otherwise [EqualizerPresets.CUSTOM_ID].
 */
class EqualizerSession(
    private val engine: EqualizerEngine = NoOpEqualizerEngine,
    initial: EqualizerState = EqualizerState(),
) {
    private val _state = MutableStateFlow(normalize(initial))
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    val current: EqualizerState get() = _state.value

    init {
        engine.apply(_state.value)
    }

    fun setEnabled(enabled: Boolean) {
        update { it.copy(enabled = enabled) }
    }

    /** No-op when [id] is unknown (including [EqualizerPresets.CUSTOM_ID]). */
    fun selectPreset(id: String) {
        val preset = EqualizerPresets.byId(id) ?: return
        update {
            it.copy(
                presetId = preset.id,
                preampDb = preset.preampDb,
                gainsDb = preset.gainsDb,
            )
        }
    }

    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in 0 until EqualizerBands.COUNT) return
        update { current ->
            val gains = current.gainsDb.toMutableList()
            gains[index] = EqualizerBands.clampGain(gainDb)
            current.copy(gainsDb = gains)
        }
    }

    fun setPreamp(gainDb: Float) {
        update { it.copy(preampDb = EqualizerBands.clampGain(gainDb)) }
    }

    fun setGains(gainsDb: List<Float>) {
        update { it.copy(gainsDb = EqualizerBands.clampGains(gainsDb)) }
    }

    fun resetToFlat() = selectPreset(EqualizerPresets.FLAT.id)

    fun restore(state: EqualizerState) {
        update { state }
    }

    /** Push the current snapshot to [engine] without changing it (new media). */
    fun reapply() {
        engine.apply(_state.value)
    }

    private fun update(transform: (EqualizerState) -> EqualizerState) {
        val next = normalize(transform(_state.value))
        if (next == _state.value) return
        _state.value = next
        engine.apply(next)
    }

    private fun normalize(state: EqualizerState): EqualizerState {
        val gains = EqualizerBands.clampGains(state.gainsDb)
        val preamp = EqualizerBands.clampGain(state.preampDb)
        val match = EqualizerPresets.matching(gains, preamp)
        return EqualizerState(
            enabled = state.enabled,
            presetId = match?.id ?: EqualizerPresets.CUSTOM_ID,
            preampDb = preamp,
            gainsDb = gains,
        )
    }
}
