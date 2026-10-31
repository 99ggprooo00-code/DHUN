package dev.dhun.design

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The three understandable axes used by the custom-colour editor.
 *
 * This is only a colour conversion for the existing per-surface colour
 * preference; it is not another theme, palette or background pipeline. Values
 * are normalized when converted so a restored or out-of-range draft cannot
 * produce an invalid colour.
 */
data class HslColor(
    /** Degrees around the colour wheel. 0 and 360 are both red. */
    val hue: Float,
    /** 0 = grey, 1 = fully coloured. */
    val saturation: Float,
    /** 0 = black, 1 = white. */
    val lightness: Float,
) {
    fun toColor(): Color {
        val h = (((hue % 360f) + 360f) % 360f) / 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        if (s <= 0.0001f) return Color(l, l, l, 1f)

        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        fun channel(turns: Float): Float {
            var t = turns
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }

        return Color(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f), 1f)
    }
}

/** Convert an sRGB colour to the Hue / Saturation / Lightness editor draft. */
fun Color.toHsl(): HslColor {
    val r = red
    val g = green
    val b = blue
    val maximum = max(r, max(g, b))
    val minimum = min(r, min(g, b))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    if (delta <= 0.0001f) return HslColor(0f, 0f, lightness)

    val denominator = 1f - abs(2f * lightness - 1f)
    val saturation = if (denominator <= 0.0001f) 0f else delta / denominator
    val hue = when (maximum) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return HslColor(
        hue = (hue + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness,
    )
}

/** The six primary/secondary waypoints plus the return to red at 360°. */
internal fun hueSliderColors(): List<Color> =
    (0..6).map { step -> HslColor(step * 60f, 1f, 0.5f).toColor() }

/** The exact colours the Saturation slider will produce at its two ends. */
internal fun saturationSliderColors(color: HslColor): List<Color> = listOf(
    color.copy(saturation = 0f).toColor(),
    color.copy(saturation = 1f).toColor(),
)

/** Black → the current hue at mid-lightness → white. */
internal fun lightnessSliderColors(color: HslColor): List<Color> = listOf(
    Color.Black,
    color.copy(lightness = 0.5f).toColor(),
    Color.White,
)
