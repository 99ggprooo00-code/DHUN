package dev.dhun.android.widgets

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import dev.dhun.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Pins the widget surfaces to a contract:
 *  - the Quick Play info XML exists, parses, and fits the slot it promises,
 *  - required AppWidgetProvider attributes are present,
 *  - the layouts contain the view ids the Kotlin updater binds,
 *  - the manifest registers exactly one widget receiver, and it is Quick Play.
 *
 * Two things this guards against, both learned on device:
 *  - a silent rename of a layout id or a missing receiver breaks the widget on
 *    a real launcher with zero compile error;
 *  - an instance whose declared minimums exceed a launcher cell is refused
 *    outright ("couldn't load") by strict launchers, which is what killed the
 *    Now Playing widget — so the size math is asserted here, not eyeballed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetXmlTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resources get() = context.resources

    @Test
    fun `quick play info exists and declares an initial layout`() {
        val quickPlay = parseWidgetInfo(R.xml.widget_quick_play_info)
        assertEquals(R.layout.widget_quick_play, quickPlay.initialLayout)
        assertTrue("minWidth missing for quick play", !quickPlay.minWidthRaw.isNullOrBlank())
        assertTrue("minHeight missing for quick play", !quickPlay.minHeightRaw.isNullOrBlank())
        // widgetCategory="home_screen" is compiled to int 1 (WIDGET_CATEGORY_HOME_SCREEN)
        assertEquals(1, quickPlay.widgetCategoryInt)
    }

    @Test
    fun `quick play fits the two by two slot it targets`() {
        // The bug that deleted Now Playing: minHeight 140dp > a 4x2 slot (~110dp),
        // and strict launchers error out instead of growing the widget a row.
        // Quick Play must never re-grow its own minimum past its target cells.
        val quick = parseWidgetInfo(R.xml.widget_quick_play_info)
        assertEquals(2, quick.targetCellWidth)
        assertEquals(2, quick.targetCellHeight)
        // Decoding itself is part of the contract: a silently unreadable
        // dimension would let the assertion below pass on a null.
        // The raw text is echoed into the message: if a future aapt2 changes
        // how a compiled dimension is printed, the failure says what it said.
        assertTrue(
            "minHeight must decode to a positive dp value (parser reported '${quick.minHeightRaw}')",
            (quick.minHeightDp ?: -1f) > 0f,
        )
        assertTrue(
            "minWidth must decode to a positive dp value (parser reported '${quick.minWidthRaw}')",
            (quick.minWidthDp ?: -1f) > 0f,
        )
        assertTrue("minHeight ${quick.minHeightDp}dp exceeds a 2x2 slot (~110dp)", quick.minHeightDp!! <= 110f)
        assertTrue("minWidth ${quick.minWidthDp}dp exceeds a 2x2 slot (~110dp)", quick.minWidthDp!! <= 110f)
        // Resizing may only shrink down to the same floor; it grows the card,
        // never the minimum.
        val minResizeH = quick.minResizeHeightDp
        if (minResizeH != null) assertTrue("minResizeHeight exceeds the slot", minResizeH <= 110f)
    }

    @Test
    fun `quick play info declares target cells resize bounds and a live preview`() {
        val quick = parseWidgetInfo(R.xml.widget_quick_play_info)
        assertEquals(R.layout.widget_preview_quick_play, quick.previewLayout)
        assertTrue("minResizeWidth missing", !quick.minResizeWidthRaw.isNullOrBlank())
        assertTrue("maxResizeWidth missing", !quick.maxResizeWidthRaw.isNullOrBlank())
        assertTrue("maxResizeHeight missing", !quick.maxResizeHeightRaw.isNullOrBlank())
        assertTrue("resizeMode missing", !quick.resizeModeRaw.isNullOrBlank())
        // Self-refresh safety net on top of the service push path.
        assertEquals(1_800_000, quick.updatePeriodMillis)
        // Picker description must exist — a missing string kills the entry.
        assertTrue(resources.getString(R.string.widget_quick_play_desc).isNotBlank())
    }

    @Test
    fun `quick play small tier contains the ids the updater binds`() {
        val ids = layoutIds(R.layout.widget_quick_play)
        assertTrue(R.id.widget_root in ids)
        assertTrue(R.id.widget_glass in ids)
        assertTrue(R.id.widget_title in ids)
        assertTrue(R.id.widget_artist in ids)
        assertTrue(R.id.widget_play_pause in ids)
        assertTrue(R.id.widget_play_pause_icon in ids)
        assertTrue(R.id.widget_progress in ids)
    }

    @Test
    fun `quick play wide tier adds artwork and next`() {
        val ids = layoutIds(R.layout.widget_quick_play_wide)
        assertTrue(R.id.widget_root in ids)
        assertTrue(R.id.widget_glass in ids)
        assertTrue(R.id.widget_artwork in ids)
        assertTrue(R.id.widget_title in ids)
        assertTrue(R.id.widget_artist in ids)
        assertTrue(R.id.widget_play_pause in ids)
        assertTrue(R.id.widget_play_pause_icon in ids)
        assertTrue(R.id.widget_next in ids)
        assertTrue(R.id.widget_progress in ids)
        // The small tier must NOT carry the wide-only extras: the updater only
        // wires the next-intent when the wide layout is chosen, and RemoteViews
        // would silently drop the binding otherwise.
        val small = layoutIds(R.layout.widget_quick_play)
        assertTrue(R.id.widget_artwork !in small)
        assertTrue(R.id.widget_next !in small)
    }

    @Test
    fun `preview layout is a static mockup with core ids`() {
        val quick = layoutIds(R.layout.widget_preview_quick_play)
        assertTrue(R.id.widget_root in quick)
        assertTrue(R.id.widget_title in quick)
        assertTrue(R.id.widget_play_pause in quick)
        assertTrue(R.id.widget_play_pause_icon in quick)
        assertTrue(R.id.widget_progress in quick)
    }

    @Test
    fun `manifest registers exactly one widget receiver and it is quick play`() {
        val pm = context.packageManager
        val receivers = pm.getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS).receivers
            ?: emptyArray()
        val widgetReceivers = receivers.filter { it.name.endsWith("WidgetProvider") }
        // Exactly one widget ships. Re-adding a provider is a deliberate act
        // (with slot sizing re-checked), not something that happens quietly.
        assertEquals(
            "expected exactly one widget receiver, got ${widgetReceivers.map { it.name }}",
            1,
            widgetReceivers.size,
        )
        val quick = widgetReceivers.single()
        assertTrue("Quick Play receiver renamed", quick.name.endsWith("DhunQuickPlayWidgetProvider"))
        // Exported is required for AppWidgetManager to deliver broadcasts.
        assertTrue("quick play widget must be exported", quick.exported)
    }

    @Test
    fun `background drawables and icon drawables exist`() {
        assertNotNull(resources.getDrawable(R.drawable.widget_background, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_play_circle, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_artwork_bg, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_progress, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_play, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_pause, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_next, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_prev, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_shuffle, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_repeat, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_repeat_one, null))
        assertNotNull(resources.getDrawable(R.drawable.widget_ic_music_note, null))
    }

    @Test
    fun `widget palette talkback strings and radius dimen resolve`() {
        // Layouts reference these — a missing color/string/dimen breaks
        // inflation on device.
        val palette = listOf(
            R.color.widget_background,
            R.color.widget_on_background,
            R.color.widget_secondary,
            R.color.widget_accent,
            R.color.widget_on_accent,
            R.color.widget_track,
            R.color.widget_outline,
            R.color.widget_artwork_scrim,
        )
        for (res in palette) {
            resources.getColor(res, null)
        }
        val labels = listOf(
            R.string.widget_cd_play,
            R.string.widget_cd_pause,
            R.string.widget_cd_next,
            R.string.widget_cd_previous,
            R.string.widget_cd_artwork,
            R.string.widget_cd_shuffle_on,
            R.string.widget_cd_shuffle_off,
            R.string.widget_cd_repeat_off,
            R.string.widget_cd_repeat_all,
            R.string.widget_cd_repeat_one,
        )
        for (res in labels) {
            assertTrue(resources.getString(res).isNotBlank())
        }
        // Both renderers (static chrome + runtime glass) read this one dimen.
        // On API 31+ it aliases a *platform* dimen, which a Robolectric table
        // may or may not carry, so only positivity is pinned — a miss is
        // tolerated in production by WidgetGlass' own fallback.
        val radius = runCatching { resources.getDimension(R.dimen.widget_corner_radius) }.getOrNull()
        assertTrue("corner radius dimen must be positive when resolvable", radius == null || radius > 0f)
    }

    // -- helpers

    private data class WidgetInfo(
        val initialLayout: Int,
        val minWidthRaw: String?,
        val minHeightRaw: String?,
        val minWidthDp: Float?,
        val minHeightDp: Float?,
        val minResizeHeightDp: Float?,
        val widgetCategoryInt: Int,
        val targetCellWidth: Int,
        val targetCellHeight: Int,
        val previewLayout: Int,
        val minResizeWidthRaw: String?,
        val maxResizeWidthRaw: String?,
        val maxResizeHeightRaw: String?,
        val resizeModeRaw: String?,
        val updatePeriodMillis: Int,
    )

    private fun parseWidgetInfo(resId: Int): WidgetInfo {
        val parser = resources.getXml(resId)
        var layout = 0
        var minW: String? = null
        var minH: String? = null
        var minWDp: Float? = null
        var minHDp: Float? = null
        var minResizeHDp: Float? = null
        var catInt = 0
        var cellW = -1
        var cellH = -1
        var preview = 0
        var minResizeW: String? = null
        var maxResizeW: String? = null
        var maxResizeH: String? = null
        var resizeMode: String? = null
        var period = -1
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                    layout = parser.getAttributeResourceValue(ANDROID_NS, "initialLayout", 0)
                    // minWidth/minHeight: the raw text is only good for a
                    // "declared at all" check — aapt2 stores dimensions as a
                    // TypedValue, so the magnitude comes from readDimensionDp.
                    minW = parser.getAttributeValue(ANDROID_NS, "minWidth")
                    minH = parser.getAttributeValue(ANDROID_NS, "minHeight")
                    minWDp = readDimensionDp(parser, "minWidth")
                    minHDp = readDimensionDp(parser, "minHeight")
                    minResizeHDp = readDimensionDp(parser, "minResizeHeight")
                    // widgetCategory="home_screen" is compiled to 1; read as int.
                    catInt = parser.getAttributeIntValue(ANDROID_NS, "widgetCategory", 0)
                    // targetCellWidth/Height are integers — getAttributeValue returns
                    // null for non-string compiled values, so read as int.
                    cellW = parser.getAttributeIntValue(ANDROID_NS, "targetCellWidth", -1)
                    cellH = parser.getAttributeIntValue(ANDROID_NS, "targetCellHeight", -1)
                    preview = parser.getAttributeResourceValue(ANDROID_NS, "previewLayout", 0)
                    minResizeW = parser.getAttributeValue(ANDROID_NS, "minResizeWidth")
                    maxResizeW = parser.getAttributeValue(ANDROID_NS, "maxResizeWidth")
                    maxResizeH = parser.getAttributeValue(ANDROID_NS, "maxResizeHeight")
                    resizeMode = parser.getAttributeValue(ANDROID_NS, "resizeMode")
                    period = parser.getAttributeIntValue(ANDROID_NS, "updatePeriodMillis", -1)
                }
            }
        } finally {
            parser.close()
        }
        return WidgetInfo(
            layout, minW, minH, minWDp, minHDp, minResizeHDp,
            catInt, cellW, cellH, preview,
            minResizeW, maxResizeW, maxResizeH, resizeMode, period,
        )
    }

    private fun layoutIds(layoutRes: Int): Set<Int> {
        val parser = resources.getLayout(layoutRes)
        val ids = mutableSetOf<Int>()
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    val id = parser.getAttributeResourceValue(ANDROID_NS, "id", 0)
                    if (id != 0) ids.add(id)
                }
            }
        } finally {
            parser.close()
        }
        return ids
    }

    /**
     * Attribute magnitude in dp.
     *
     * A compiled dimension attribute does NOT come back as the source text:
     * `110dp` is stored as a packed complex value and printed by the parser as
     * `110d`, so an `endsWith("dp")` test finds nothing (this cost a CI cycle).
     * Match the leading number instead and accept the unit spellings a host
     * may report — `dp`, `dip`, or `d`. Any other unit (sp/px/mm/%) is
     * rejected: a slot-size contract is meaningless unless it is dp. A
     * `@dimen/...` reference is resolved the way the launcher would.
     *
     * Typed as [XmlResourceParser], not [XmlPullParser]: only the former (via
     * AttributeSet) exposes the resource-id and typed accessors this needs.
     */
    private fun readDimensionDp(parser: XmlResourceParser, attr: String): Float? {
        fromText(parser.getAttributeValue(ANDROID_NS, attr))?.let { return it }
        val id = parser.getAttributeResourceValue(ANDROID_NS, attr, 0)
        if (id == 0) return null
        return runCatching {
            val px = resources.getDimension(id)
            val density = resources.displayMetrics.density
            if (px > 0f && density > 0f) px / density else null
        }.getOrNull()
    }

    /** Numeric magnitude of a compiled dimension string, when it is dp-based. */
    private fun fromText(raw: String?): Float? {
        val text = raw?.trim() ?: return null
        val match = DIMENSION_MAGNITUDE.find(text) ?: return null
        val magnitude = match.value.toFloatOrNull() ?: return null
        val unit = text.substring(match.range.last + 1).trim().lowercase()
        return if (unit.isEmpty() || unit == "dp" || unit == "dip" || unit == "d") magnitude else null
    }

    companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        /** Leading numeric magnitude of a dimension literal: `110dp`, `110d`, `-2.5`. */
        val DIMENSION_MAGNITUDE: Regex = Regex("^-?\\d+(?:\\.\\d+)?")
    }
}
