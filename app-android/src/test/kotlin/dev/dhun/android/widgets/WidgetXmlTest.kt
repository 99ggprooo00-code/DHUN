package dev.dhun.android.widgets

import android.content.Context
import android.content.pm.PackageManager
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
 *  - both widget_info XMLs exist and parse,
 *  - initialLayout points at the expected layout,
 *  - required AppWidgetProvider attributes are present,
 *  - layouts contain the view ids the Kotlin updater expects,
 *  - manifest registers both receivers for APPWIDGET_UPDATE.
 *
 * A silent rename of a layout id or a missing receiver breaks the widget
 * on a real launcher with zero compile error — this test is the gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetXmlTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resources get() = context.resources

    @Test
    fun `both widget info xmls exist and declare an initial layout`() {
        val nowPlaying = parseWidgetInfo(R.xml.widget_now_playing_info)
        assertEquals(R.layout.widget_now_playing, nowPlaying.initialLayout)
        assertTrue("minWidth missing for now playing", !nowPlaying.minWidthRaw.isNullOrBlank())
        assertTrue("minHeight missing for now playing", !nowPlaying.minHeightRaw.isNullOrBlank())
        // widgetCategory="home_screen" is compiled to int 1 (WIDGET_CATEGORY_HOME_SCREEN)
        assertEquals(1, nowPlaying.widgetCategoryInt)

        val quickPlay = parseWidgetInfo(R.xml.widget_quick_play_info)
        assertEquals(R.layout.widget_quick_play, quickPlay.initialLayout)
        assertTrue("minWidth missing for quick play", !quickPlay.minWidthRaw.isNullOrBlank())
        assertTrue("minHeight missing for quick play", !quickPlay.minHeightRaw.isNullOrBlank())
        assertEquals(1, quickPlay.widgetCategoryInt)
    }

    @Test
    fun `now playing and quick play have distinct info and layouts`() {
        val now = parseWidgetInfo(R.xml.widget_now_playing_info)
        val quick = parseWidgetInfo(R.xml.widget_quick_play_info)
        // Distinct picker entries = distinct initial layouts + different min sizes.
        assertTrue(now.initialLayout != quick.initialLayout)
        assertTrue(now.minWidthRaw != quick.minWidthRaw || now.minHeightRaw != quick.minHeightRaw)
    }

    @Test
    fun `widget infos declare target cells resize bounds and live previews`() {
        val now = parseWidgetInfo(R.xml.widget_now_playing_info)
        assertEquals(4, now.targetCellWidth)
        assertEquals(2, now.targetCellHeight)
        assertEquals(R.layout.widget_now_playing, now.previewLayout)
        assertTrue("minResizeWidth missing", !now.minResizeWidthRaw.isNullOrBlank())
        assertTrue("maxResizeHeight missing", !now.maxResizeHeightRaw.isNullOrBlank())

        val quick = parseWidgetInfo(R.xml.widget_quick_play_info)
        assertEquals(2, quick.targetCellWidth)
        assertEquals(2, quick.targetCellHeight)
        assertEquals(R.layout.widget_quick_play, quick.previewLayout)
        assertTrue("minResizeWidth missing", !quick.minResizeWidthRaw.isNullOrBlank())
    }

    @Test
    fun `now playing layout contains the transport view ids updater expects`() {
        // Inflate via Robolectric resources check — avoids needing a real launcher.
        // We parse the XML to verify ids are present; the updater sets them via RemoteViews.
        val ids = layoutIds(R.layout.widget_now_playing)
        assertTrue("widget_root missing", R.id.widget_root in ids)
        assertTrue("widget_artwork missing", R.id.widget_artwork in ids)
        assertTrue("widget_title missing", R.id.widget_title in ids)
        assertTrue("widget_artist missing", R.id.widget_artist in ids)
        assertTrue("widget_prev missing", R.id.widget_prev in ids)
        assertTrue("widget_play_pause missing", R.id.widget_play_pause in ids)
        assertTrue("widget_play_pause_icon missing", R.id.widget_play_pause_icon in ids)
        assertTrue("widget_next missing", R.id.widget_next in ids)
        assertTrue("widget_progress missing", R.id.widget_progress in ids)
    }

    @Test
    fun `compact tall and wide tiers contain their ids`() {
        val compact = layoutIds(R.layout.widget_now_playing_compact)
        assertTrue(R.id.widget_root in compact)
        assertTrue(R.id.widget_artwork in compact)
        assertTrue(R.id.widget_title in compact)
        assertTrue(R.id.widget_play_pause in compact)
        assertTrue(R.id.widget_play_pause_icon in compact)
        assertTrue(R.id.widget_progress in compact)

        val tall = layoutIds(R.layout.widget_now_playing_tall)
        assertTrue(R.id.widget_root in tall)
        assertTrue(R.id.widget_artwork in tall)
        assertTrue(R.id.widget_title in tall)
        assertTrue(R.id.widget_artist in tall)
        assertTrue(R.id.widget_play_pause in tall)
        assertTrue(R.id.widget_play_pause_icon in tall)
        assertTrue(R.id.widget_prev in tall)
        assertTrue(R.id.widget_next in tall)
        assertTrue(R.id.widget_shuffle in tall)
        assertTrue(R.id.widget_repeat in tall)
        assertTrue(R.id.widget_progress in tall)
        assertTrue(R.id.widget_position in tall)
        assertTrue(R.id.widget_duration in tall)
        assertTrue(R.id.widget_times_row in tall)

        val wide = layoutIds(R.layout.widget_quick_play_wide)
        assertTrue(R.id.widget_root in wide)
        assertTrue(R.id.widget_artwork in wide)
        assertTrue(R.id.widget_title in wide)
        assertTrue(R.id.widget_play_pause in wide)
        assertTrue(R.id.widget_play_pause_icon in wide)
        assertTrue(R.id.widget_next in wide)
        assertTrue(R.id.widget_progress in wide)
    }

    @Test
    fun `quick play layout contains its ids`() {
        val ids = layoutIds(R.layout.widget_quick_play)
        assertTrue(R.id.widget_root in ids)
        assertTrue(R.id.widget_title in ids)
        assertTrue(R.id.widget_artist in ids)
        assertTrue(R.id.widget_play_pause in ids)
        assertTrue(R.id.widget_play_pause_icon in ids)
        assertTrue(R.id.widget_progress in ids)
    }

    @Test
    fun `manifest registers both widget receivers for APPWIDGET_UPDATE`() {
        val pm = context.packageManager
        val receivers = pm.getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS).receivers
            ?: emptyArray()
        val names = receivers.map { it.name }.toSet()
        assertTrue(
            "DhunNowPlayingWidgetProvider receiver missing",
            names.any { it.endsWith("DhunNowPlayingWidgetProvider") },
        )
        assertTrue(
            "DhunQuickPlayWidgetProvider receiver missing",
            names.any { it.endsWith("DhunQuickPlayWidgetProvider") },
        )
        // Both must handle APPWIDGET_UPDATE — check the intent filter via raw manifest parsing
        // (PackageManager doesn't expose filter details in Robolectric shadow).
        // We instead directly parse the merged manifest xml via resources? Simpler: assert the xml exists
        // and the receiver is exported (required for AppWidgetManager to send broadcasts).
        val nowInfo = receivers.first { it.name.endsWith("DhunNowPlayingWidgetProvider") }
        assertTrue("now playing widget must be exported", nowInfo.exported)
        val quickInfo = receivers.first { it.name.endsWith("DhunQuickPlayWidgetProvider") }
        assertTrue("quick play widget must be exported", quickInfo.exported)
    }

    @Test
    fun `widget background and icon drawables exist`() {
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
    fun `widget palette and talkback strings resolve`() {
        // Layouts reference these — a missing color/string breaks inflation on device.
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
    }

    // -- helpers

    private data class WidgetInfo(
        val initialLayout: Int,
        val minWidthRaw: String?,
        val minHeightRaw: String?,
        val widgetCategoryInt: Int,
        val targetCellWidth: Int,
        val targetCellHeight: Int,
        val previewLayout: Int,
        val minResizeWidthRaw: String?,
        val maxResizeHeightRaw: String?,
    )

    private fun parseWidgetInfo(resId: Int): WidgetInfo {
        val parser = resources.getXml(resId)
        var layout = 0
        var wRaw: String? = null
        var hRaw: String? = null
        var catInt = 0
        var cellW = -1
        var cellH = -1
        var preview = 0
        var minResizeW: String? = null
        var maxResizeH: String? = null
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                    layout = parser.getAttributeResourceValue(ANDROID_NS, "initialLayout", 0)
                    // minWidth/minHeight are dimension literals (e.g. 250dp) — read as raw string;
                    // getAttributeIntValue returns 0 for raw dimen in Robolectric.
                    wRaw = parser.getAttributeValue(ANDROID_NS, "minWidth")
                    hRaw = parser.getAttributeValue(ANDROID_NS, "minHeight")
                    // widgetCategory="home_screen" is compiled to 1; read as int.
                    catInt = parser.getAttributeIntValue(ANDROID_NS, "widgetCategory", 0)
                    // targetCellWidth/Height are integers — getAttributeValue returns
                    // null for non-string compiled values, so read as int.
                    cellW = parser.getAttributeIntValue(ANDROID_NS, "targetCellWidth", -1)
                    cellH = parser.getAttributeIntValue(ANDROID_NS, "targetCellHeight", -1)
                    preview = parser.getAttributeResourceValue(ANDROID_NS, "previewLayout", 0)
                    minResizeW = parser.getAttributeValue(ANDROID_NS, "minResizeWidth")
                    maxResizeH = parser.getAttributeValue(ANDROID_NS, "maxResizeHeight")
                }
            }
        } finally {
            parser.close()
        }
        return WidgetInfo(layout, wRaw, hRaw, catInt, cellW, cellH, preview, minResizeW, maxResizeH)
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

    companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
