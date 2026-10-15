package dev.dhun.android.playback

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/** The Android music stream, shared with hardware volume keys and other media apps. */
class AndroidMediaVolume(context: Context) {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun current(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC)) return 0f
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max).coerceIn(0f, 1f)
    }

    fun set(normalized: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val level = (normalized.coerceIn(0f, 1f) * max).roundToInt()
        // Explicit mute can leave the stream at a nonzero stored level. An
        // in-app move above zero should unmute, just like a hardware volume key.
        if (level > 0 && audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    }
}
