package dev.dhun.android.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.core.content.ContextCompat
import dev.dhun.android.R

/**
 * Renders the frosted-glass card behind widget content.
 *
 * Why a runtime bitmap: XML drawables cannot apply translucency to the
 * dynamic system colors (`@android:color/system_accent1_*` are opaque and
 * there is no alpha-combining mechanism in resources). So the tint is
 * resolved at runtime ([ContextCompat.getColor] follows the wallpaper +
 * light/dark mode) and composited here: tinted fill at [FILL_ALPHA], a soft
 * top sheen, and a hairline edge. True blur-behind is not exposed to app
 * widgets on any API level — translucency over the wallpaper is the
 * platform's glass look, and fully-opaque text/controls on top keep it
 * legible.
 *
 * Bitmaps are aspect-correct per widget instance (from the host's reported
 * dp size) but capped at [MAX_EDGE_PX] — glass is soft gradients, so
 * upscaling in the [android.widget.ImageView] is invisible and keeps the
 * RemoteViews transaction far under the binder limit. Results are cached;
 * a theme change resolves new colors and therefore new cache keys, so the
 * glass follows light/dark switches within one push.
 */
object WidgetGlass {

    /** Fill opacity of the glass card. */
    const val FILL_ALPHA = 0.70f

    /** Peak opacity of the top sheen. */
    const val SHEEN_ALPHA = 0.12f

    /** Fraction of the card height the sheen covers. */
    const val SHEEN_FRACTION = 0.45f

    /** Card corner radius, matching the M3 widget chrome. */
    const val CORNER_RADIUS_DP = 28f

    /** Longest bitmap edge in px — glass gradients upscale invisibly. */
    const val MAX_EDGE_PX = 256

    private const val CACHE_ENTRIES = 12

    private val cache = object : LruCache<String, Bitmap>(CACHE_ENTRIES * MAX_EDGE_PX * MAX_EDGE_PX * 4) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }

    /**
     * Glass bitmap for a widget instance of [widthDp]×[heightDp]. Never
     * throws — returns null when colors cannot be resolved, in which case
     * the caller falls back to the solid `@drawable/widget_background`.
     */
    fun forWidget(context: Context, widthDp: Int, heightDp: Int): Bitmap? = runCatching {
        val dw = widthDp.coerceIn(110, 650)
        val dh = heightDp.coerceIn(40, 320)
        val density = context.resources.displayMetrics.density.coerceIn(1f, 3f)
        val (px, py) = scaledSize((dw * density).toInt(), (dh * density).toInt(), MAX_EDGE_PX)
        val fullW = dw * density
        val scale = if (fullW > 0f) px / fullW else 1f
        val radiusPx = (CORNER_RADIUS_DP * density * scale).coerceAtMost(minOf(px, py) / 2f)
        val fill = ContextCompat.getColor(context, R.color.widget_background)
        val stroke = ContextCompat.getColor(context, R.color.widget_outline)
        val key = "$px,$py,${radiusPx.toInt()},$fill,$stroke"
        cache.get(key) ?: bitmap(px, py, radiusPx, applyAlpha(fill, FILL_ALPHA), stroke)
            .also { runCatching { cache.put(key, it) } }
    }.getOrNull()

    /** Test hook — clears the cache between cases. */
    internal fun evictAll() {
        runCatching { cache.evictAll() }
    }

    /**
     * Composites fill + sheen + edge stroke. Pure drawing — no resources,
     * no cache.
     */
    internal fun bitmap(
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float,
        fillArgb: Int,
        strokeArgb: Int,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = radiusPx.coerceIn(0f, minOf(w, h) / 2f)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillArgb }
        canvas.drawRoundRect(rect, radius, radius, fill)

        // Top sheen: white fading to transparent; CLAMP holds transparency
        // below the sheen band so one draw covers the card.
        val sheenH = (h * SHEEN_FRACTION).coerceAtLeast(1f)
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, sheenH,
                applyAlpha(0xFFFFFF, SHEEN_ALPHA),
                0x00000000,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, radius, radius, sheen)

        val strokeW = (minOf(w, h) / 128f).coerceAtLeast(1f)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = strokeArgb
        }
        val inset = strokeW / 2f
        canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, edge)
        return out
    }

    /** Replaces the alpha channel of [rgb] (which may carry any alpha). */
    internal fun applyAlpha(rgb: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }

    /** Downscales [widthPx]×[heightPx] to fit [maxEdge], preserving aspect. */
    internal fun scaledSize(widthPx: Int, heightPx: Int, maxEdge: Int): Pair<Int, Int> {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val longest = maxOf(w, h)
        if (longest <= maxEdge) return w to h
        val s = maxEdge.toFloat() / longest
        return (w * s + 0.5f).toInt().coerceAtLeast(1) to (h * s + 0.5f).toInt().coerceAtLeast(1)
    }
}
