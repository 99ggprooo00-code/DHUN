package dev.dhun.design

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import dev.dhun.data.SettingsKeys

/** Test-only UI tuning keys. Retired parts stay keyed so old settings can be ignored/reset. */
enum class VisualPart(val key: String, val label: String, val detail: String) {
    PAGE_BACKDROP(SettingsKeys.TUNE_PAGE_BACKDROP, "Page artwork veil", "Home, Search, Library and browse backgrounds; separate from artwork blur and brightness"),
    LIST_ROWS(SettingsKeys.TUNE_LIST_ROWS, "Music rows and cards", "Track, album and playlist rows on pages; not the queue"),
    READING(SettingsKeys.TUNE_READING, "Reading panels", "Settings, About & Legal and other content cards"),
    CHIPS(SettingsKeys.TUNE_CHIPS, "Filter and action chips", "Category filters and compact unselected buttons"),
    INPUTS(SettingsKeys.TUNE_INPUTS, "Text fields", "Search and short text-entry backgrounds"),
    PLAYER(SettingsKeys.TUNE_PLAYER, "Full player backdrop", "The blurred player behind artwork and playback controls"),
    LYRICS(SettingsKeys.TUNE_LYRICS, "Lyrics panel", "The CC lyrics backdrop; not queue lyrics"),
    MINI_PLAYER(SettingsKeys.TUNE_MINI_PLAYER, "Mini-player", "The frosted now-playing bar"),
    NAVIGATION(SettingsKeys.TUNE_NAVIGATION, "Navigation", "Bottom tab bar and desktop navigation rail"),
    QUEUE_SHEET(SettingsKeys.TUNE_QUEUE_SHEET, "Queue sheet", "The artwork veil of the Queue/Related panel"),
    QUEUE_ROWS(SettingsKeys.TUNE_QUEUE_ROWS, "Queue rows", "Individual queue track backgrounds"),
    TONAL_SURFACES(SettingsKeys.TUNE_TONAL_SURFACES, "Tonal surfaces", "Surface, variant and elevated fills (including Settings cards); lower = more artwork visible"),
}

/**
 * Live snapshot state. Nothing outside the test channel loads saved values or
 * exposes the controls. The 50% midpoint is an identity mapping, so the public
 * UI remains unchanged. Fresh test installs use fixed lighter mini/navigation
 * veils and a softer tonal surface baseline.
 */
object DhunVisualTuning {
    const val DEFAULT = 50
    /** Retired sliders remain keyed for migration, but cannot be edited or restored. */
    val adjustableParts: List<VisualPart> = VisualPart.entries.filterNot {
        it == VisualPart.PAGE_BACKDROP || it == VisualPart.MINI_PLAYER || it == VisualPart.NAVIGATION
    }
    private var testEnabled by mutableStateOf(false)
    private val percentages = mutableStateMapOf<VisualPart, Int>()

    fun value(part: VisualPart): Int = when (part) {
        VisualPart.PAGE_BACKDROP -> DEFAULT
        VisualPart.MINI_PLAYER, VisualPart.NAVIGATION -> if (testEnabled) 0 else DEFAULT
        else -> percentages[part] ?: DEFAULT
    }

    fun set(part: VisualPart, percent: Int) {
        if (part !in adjustableParts) return
        val safe = percent.coerceIn(0, 100)
        if (safe == DEFAULT) percentages.remove(part) else percentages[part] = safe
    }

    fun restore(values: Map<VisualPart, Int>, enabled: Boolean) {
        testEnabled = enabled
        percentages.clear()
        if (enabled) values.forEach { (part, percent) -> set(part, percent) }
    }

    fun reset() = percentages.clear()

    /** Test-only token translucency; 50% is a calmer baseline, not the old opaque tile. */
    fun tonalSurface(color: Color, isLight: Boolean): Color {
        if (!testEnabled) return color
        val percent = value(VisualPart.TONAL_SURFACES)
        val base = if (isLight) 0.82f else 0.76f
        val minimum = if (isLight) 0.65f else 0.52f
        val opacity = if (percent < DEFAULT) {
            minimum + (base - minimum) * percent / DEFAULT
        } else {
            base + (1f - base) * (percent - DEFAULT) / DEFAULT
        }
        return color.copy(alpha = color.alpha * opacity)
    }

    /** Piecewise interpolation preserves the exact original alpha at 50%. */
    fun alpha(part: VisualPart, base: Float, minimum: Float, maximum: Float): Float {
        require(minimum <= base && base <= maximum && maximum <= 1f)
        val percent = value(part)
        return if (percent == DEFAULT) base else if (percent < DEFAULT) {
            minimum + (base - minimum) * percent / DEFAULT
        } else {
            base + (maximum - base) * (percent - DEFAULT) / DEFAULT
        }
    }

    /** Bounded coverage for artwork veils; clear stops remain clear. */
    fun veilAlpha(part: VisualPart, base: Float): Float =
        if (base == 0f) 0f else alpha(part, base, (base - 0.10f).coerceAtLeast(0f), (base + 0.10f).coerceAtMost(0.94f))
}
