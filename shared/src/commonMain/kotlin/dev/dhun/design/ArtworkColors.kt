package dev.dhun.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Palette extracted from artwork — used to tint the FullPlayer background
 * and accent the progress bar. Falls back to [DhunColors.accent] when
 * extraction is impossible (no bitmap, network error, solid-color art).
 *
 * The extractor runs in commonMain by sampling [ImageBitmap] pixels; a
 * lightweight heuristic, not a Palette library — but deterministic and
 * GPL-clean.
 */
data class ArtworkColors(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val backgroundTint: Color,
) {
    /**
     * Control accent — what the play disc, seek bar, slider and active tab
     * are allowed to use.
     *
     * **Why this exists.** [primary] is derived from artwork (or, before the
     * bitmap lands, from a hash of the artwork URL). Feeding it straight into
     * transport controls produced the hardware-reported "terrible UI": a
     * blood-red play disc and a full-width red volume slider that read as a
     * permanent error state, an orange disc on the next track, and so on —
     * the app looked broken and different every song.
     *
     * So the control accent is *tamed*: blended toward the brand accent and
     * floored for lightness, keeping a hint of the artwork without ever
     * landing on alarm-red or muddy brown. Chrome stays recognisably DHUN;
     * only the ambient backdrop gets the full artwork hue.
     */
    val controlAccent: Color get() = primary.tamedForControls()

    companion object {
        val fallback = ArtworkColors(
            primary = DhunColors.accent,
            onPrimary = DhunColors.onAccent,
            container = DhunColors.accentContainer,
            backgroundTint = DhunColors.accent.copy(alpha = AMBIENT_ALPHA),
        )

        fun fromPrimary(primary: Color) = ArtworkColors(
            primary = primary,
            onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White,
            container = primary.copy(alpha = 0.28f),
            backgroundTint = primary.desaturate(AMBIENT_DESATURATION).copy(alpha = AMBIENT_ALPHA),
        )

        /**
         * How much artwork colour survives into the ambient wash. Was 0.22 at
         * full saturation, which painted whole screens brown/maroon/green.
         */
        const val AMBIENT_ALPHA = 0.10f

        /** Ambient washes are pulled most of the way to neutral. */
        const val AMBIENT_DESATURATION = 0.55f

    }
}

/** Share of brand accent mixed into every artwork-derived control colour. */
private const val BRAND_MIX_DEFAULT = 0.45f

/** Linear mix, [t] = 0 keeps the receiver, 1 gives [other]. */
internal fun Color.mix(other: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * k,
        green = green + (other.green - green) * k,
        blue = blue + (other.blue - blue) * k,
        alpha = alpha,
    )
}

/**
 * Makes an artwork colour safe to put on interactive chrome: mixes in the
 * brand accent, then lifts it until it is legible on the near-black surface.
 */
internal fun Color.tamedForControls(): Color {
    val blended = mix(DhunColors.accent, BRAND_MIX_DEFAULT)
    // Floor the lightness so dark artwork can't yield a near-invisible disc.
    val lum = blended.luminance()
    return if (lum < 0.42f) blended.mix(Color.White, (0.42f - lum) * 1.1f) else blended
}

internal fun Color.luminance(): Float {
    val r = red; val g = green; val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

internal fun Color.desaturate(amount: Float): Color {
    val gray = luminance()
    return Color(
        red = red + (gray - red) * amount,
        green = green + (gray - green) * amount,
        blue = blue + (gray - blue) * amount,
        alpha = alpha,
    )
}

/**
 * Extracts a palette from a decoded artwork [ImageBitmap]. The algorithm
 * is intentionally simple: sample ~1k pixels on a grid, bucket by hue,
 * pick the most saturated bucket's average, then derive tints.
 *
 * On platforms where bitmap read fails, returns [ArtworkColors.fallback].
 * Call from a `LaunchedEffect` after Coil loads the bitmap.
 */
object ArtworkColorExtractor {

    fun extract(bitmap: ImageBitmap): ArtworkColors = runCatching { extractInternal(bitmap) }
        .getOrElse { ArtworkColors.fallback }

    /**
     * Fallback when only a URL / id is available (before bitmap loads).
     * Uses a stable hash so the same artwork always yields the same tint
     * — useful for placeholders and unit tests.
     */
    fun extractFromSeed(seed: String): ArtworkColors {
        if (seed.isBlank()) return ArtworkColors.fallback
        val hash = seed.hashCode()
        // Hue is nudged around the brand hue rather than spun freely around
        // the wheel. A free hue meant a hash could land on fire-engine red or
        // sludge brown and then paint the entire app with it — which is what
        // the device screenshots showed. ±40° keeps every track recognisably
        // DHUN while still feeling different song to song.
        val brandHue = 265f
        // −40°..+30°: stops short of 300° so the hue can never cross into
        // the magenta/red side of the wheel (that is the error colour).
        val offset = (((hash % 71) + 71) % 71) - 40
        val hue = (((brandHue + offset) % 360f) + 360f) % 360f / 360f
        // Muted, mid-light: enough colour to tint a backdrop, never enough to
        // shout. (Was sat .62–.92 / value .78–.98.)
        val sat = 0.34f + ((hash ushr 8) % 14) / 100f
        val v = 0.62f + ((hash ushr 16) % 12) / 100f
        val primary = hsvToColor(hue, sat.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        return ArtworkColors.fromPrimary(primary)
    }

    private fun extractInternal(bitmap: ImageBitmap): ArtworkColors {
        val w = bitmap.width; val h = bitmap.height
        if (w <= 0 || h <= 0) return ArtworkColors.fallback
        val pixels = IntArray(w * h)
        try {
            // Compose 1.8+ common overload: readPixels(buffer, startX, startY, width, height)
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
            bitmap.readPixels(pixels, 0, 0, w, h)
        } catch (_: Throwable) {
            return ArtworkColors.fallback
        }
        val step = max(1, (pixels.size / 1024).coerceAtLeast(1))
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
        var bestSaturation = -1f
        var bestR = 0; var bestG = 0; var bestB = 0
        for (i in pixels.indices step step) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            if (a < 128) continue
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0f else (mx - mn).toFloat() / mx
            val v = mx / 255f
            if (sat < 0.22f || v < 0.18f || v > 0.98f && sat < 0.5f) continue
            rSum += r; gSum += g; bSum += b; count++
            if (sat > bestSaturation) {
                bestSaturation = sat; bestR = r; bestG = g; bestB = b
            }
        }
        val primary = if (count > 0 && bestSaturation >= 0) {
            val avgR = (rSum / count).toInt(); val avgG = (gSum / count).toInt(); val avgB = (bSum / count).toInt()
            Color(
                red = ((avgR * 0.45f + bestR * 0.55f) / 255f).coerceIn(0f, 1f),
                green = ((avgG * 0.45f + bestG * 0.55f) / 255f).coerceIn(0f, 1f),
                blue = ((avgB * 0.45f + bestB * 0.55f) / 255f).coerceIn(0f, 1f),
            )
        } else {
            DhunColors.accent
        }
        return ArtworkColors.fromPrimary(primary)
    }

    private fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val i = (h * 6).toInt()
        val f = h * 6 - i
        val p = v * (1 - s)
        val q = v * (1 - f * s)
        val t = v * (1 - (1 - f) * s)
        val (r, g, b) = when (i % 6) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return Color(r, g, b, 1f)
    }
}
