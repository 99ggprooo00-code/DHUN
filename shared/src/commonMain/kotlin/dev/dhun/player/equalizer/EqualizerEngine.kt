package dev.dhun.player.equalizer

/**
 * Platform sink for an [EqualizerState]. Desktop wires this to vlcj's
 * `MediaPlayer.audio().setEqualizer`; Android [android.media.audiofx.AudioEffect]
 * is a later stream and is not implemented here. A no-op engine is valid —
 * the session still holds state the UI can render.
 *
 * Implementations must not throw. Missing native EQ (no libVLC, no audio
 * device) is a silent no-op, not a crash.
 */
fun interface EqualizerEngine {
    fun apply(state: EqualizerState)
}

object NoOpEqualizerEngine : EqualizerEngine {
    override fun apply(state: EqualizerState) = Unit
}
