package dev.dhun.design

import androidx.compose.runtime.mutableStateMapOf
import dev.dhun.data.SettingsKeys

/** Experimental, independently adjustable components. 50% is the shipped appearance. */
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
}

/**
 * Live snapshot state. Nothing outside the test channel loads saved values or
 * exposes the controls. The 50% midpoint is an identity mapping, so the public
 * UI and fresh test installs render exactly as before.
 */
object DhunVisualTuning {
    const val DEFAULT = 50
    private val percentages = mutableStateMapOf<VisualPart, Int>()

    fun value(part: VisualPart): Int = percentages[part] ?: DEFAULT

    fun set(part: VisualPart, percent: Int) {
        val safe = percent.coerceIn(0, 100)
        if (safe == DEFAULT) percentages.remove(part) else percentages[part] = safe
    }

    fun restore(values: Map<VisualPart, Int>, enabled: Boolean) {
        percentages.clear()
        if (enabled) values.forEach { (part, percent) -> set(part, percent) }
    }

    fun reset() = percentages.clear()

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
