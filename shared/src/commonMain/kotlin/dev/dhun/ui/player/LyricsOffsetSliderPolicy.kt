package dev.dhun.ui.player

import dev.dhun.lyrics.LyricsTiming
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure slider mapping for the lyrics controls; timing storage remains the source of truth. */
internal object LyricsOffsetSliderPolicy {
    const val STEP_MS = 100
    const val SLIDER_STEPS = (LyricsTiming.MAX_OFFSET_MS * 2 / STEP_MS) - 1

    /** Snap pointer values to the same 100 ms precision as the former Earlier/Later controls. */
    fun offsetFromSlider(value: Float): Int {
        if (!value.isFinite()) return 0
        val bounded = value.coerceIn(-LyricsTiming.MAX_OFFSET_MS.toFloat(), LyricsTiming.MAX_OFFSET_MS.toFloat())
        val magnitude = (abs(bounded) / STEP_MS).roundToInt() * STEP_MS
        return if (bounded < 0f) -magnitude else magnitude
    }

    /** Fraction along the preserved -10s..+10s slider range. */
    fun markerFraction(offsetMs: Int): Float =
        (offsetMs.coerceIn(-LyricsTiming.MAX_OFFSET_MS, LyricsTiming.MAX_OFFSET_MS) + LyricsTiming.MAX_OFFSET_MS)
            .toFloat() / (LyricsTiming.MAX_OFFSET_MS * 2f)

    fun valueLabel(offsetMs: Int): String = when {
        offsetMs > 0 -> "+$offsetMs ms"
        else -> "$offsetMs ms"
    }
}
