package dev.dhun.design

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.dhun.data.SettingsKeys

/** Test-only UI tuning keys. Retired parts stay keyed so old settings can be ignored/reset. */
enum class VisualPart(val key: String, val label: String, val detail: String) {
    PAGE_BACKDROP(SettingsKeys.TUNE_PAGE_BACKDROP, "Page artwork veil", "Pages and browse backgrounds"),
    LIST_ROWS(SettingsKeys.TUNE_LIST_ROWS, "Music rows and cards", "Track, album and playlist rows"),
    READING(SettingsKeys.TUNE_READING, "Reading panels", "Settings and information cards"),
    CHIPS(SettingsKeys.TUNE_CHIPS, "Filter and action chips", "Filters and compact buttons"),
    INPUTS(SettingsKeys.TUNE_INPUTS, "Text fields", "Search and text input"),
    PLAYER(SettingsKeys.TUNE_PLAYER, "Full player backdrop", "Artwork and playback controls"),
    LYRICS(SettingsKeys.TUNE_LYRICS, "Lyrics panel", "Lyrics backdrop"),
    MINI_PLAYER(SettingsKeys.TUNE_MINI_PLAYER, "Mini-player", "Now-playing bar"),
    NAVIGATION(SettingsKeys.TUNE_NAVIGATION, "Navigation", "Tabs and navigation rail"),
    QUEUE_SHEET(SettingsKeys.TUNE_QUEUE_SHEET, "Queue sheet", "Queue and Related backdrop"),
    QUEUE_ROWS(SettingsKeys.TUNE_QUEUE_ROWS, "Queue rows", "Queue track backgrounds"),
    TONAL_SURFACES(SettingsKeys.TUNE_TONAL_SURFACES, "Tonal surfaces", "Cards and elevated fills"),
}

/**
 * Live snapshot state. Nothing outside the test channel loads saved values or
 * exposes the controls. The 50% midpoint remains the original opacity curve.
 * Test builds default every component to 15% and persist later adjustments;
 * public builds keep the original component settings except for the permanent
 * 15% tonal-surface treatment requested for both release channels.
 */
object DhunVisualTuning {
    const val NEUTRAL = 50 // Old appearance and public-build fallback.
    const val TEST_DEFAULT = 15 // Test-only baseline; does not change the interpolation pivot.
    /** Retired sliders remain keyed for migration, but cannot be edited or restored. */
    val adjustableParts: List<VisualPart> = VisualPart.entries.filterNot {
        it == VisualPart.PAGE_BACKDROP || it == VisualPart.MINI_PLAYER || it == VisualPart.NAVIGATION
    }
    private var testEnabled by mutableStateOf(false)
    private val percentages = mutableStateMapOf<VisualPart, Int>()

    fun value(part: VisualPart): Int = when (part) {
        VisualPart.PAGE_BACKDROP, VisualPart.MINI_PLAYER, VisualPart.NAVIGATION ->
            if (testEnabled) TEST_DEFAULT else NEUTRAL
        else -> percentages[part] ?: if (testEnabled) TEST_DEFAULT else NEUTRAL
    }

    fun set(part: VisualPart, percent: Int) {
        if (part !in adjustableParts) return
        val safe = percent.coerceIn(0, 100)
        if (safe == TEST_DEFAULT) percentages.remove(part) else percentages[part] = safe
    }

    fun restore(values: Map<VisualPart, Int>, enabled: Boolean) {
        testEnabled = enabled
        percentages.clear()
        if (enabled) values.forEach { (part, percent) -> set(part, percent) }
    }

    fun reset() = percentages.clear()

    /** Public tonal surfaces are fixed at 15%; the test slider can override them. */
    fun tonalSurface(color: Color, isLight: Boolean): Color {
        val percent = if (testEnabled) value(VisualPart.TONAL_SURFACES) else TEST_DEFAULT
        val base = if (isLight) 0.82f else 0.76f
        val minimum = if (isLight) 0.65f else 0.52f
        val opacity = if (percent < NEUTRAL) {
            minimum + (base - minimum) * percent / NEUTRAL
        } else {
            base + (1f - base) * (percent - NEUTRAL) / NEUTRAL
        }
        return color.copy(alpha = color.alpha * opacity)
    }

    /** Piecewise interpolation preserves the exact original alpha at 50%. */
    fun alpha(part: VisualPart, base: Float, minimum: Float, maximum: Float): Float {
        require(minimum <= base && base <= maximum && maximum <= 1f)
        val percent = value(part)
        return if (percent == NEUTRAL) base else if (percent < NEUTRAL) {
            minimum + (base - minimum) * percent / NEUTRAL
        } else {
            base + (maximum - base) * (percent - NEUTRAL) / NEUTRAL
        }
    }

    /** Bounded coverage for artwork veils; clear stops remain clear. */
    fun veilAlpha(part: VisualPart, base: Float): Float =
        if (base == 0f) 0f else alpha(part, base, (base - 0.10f).coerceAtLeast(0f), (base + 0.10f).coerceAtMost(0.94f))
}
