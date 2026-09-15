package dev.dhun.android.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Glass-card renderer. Pixel assertions are deliberately absent — Robolectric
 * shadows Canvas as a no-op, so drawing is verified by geometry/config/
 * caching contracts instead. Visuals are a hardware gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetGlassTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun clearCache() {
        WidgetGlass.evictAll()
    }

    @Test
    fun `fill stays a little transparent — google search widget style`() {
        // Pinned so the card can only drift deliberately: dense widget content
        // needs more opacity than a one-glyph search bar.
        assertEquals(0.80f, WidgetGlass.FILL_ALPHA, 0.001f)
    }

    @Test
    fun `edge cap is 320px and still fits one binder transaction`() {
        // Raised from 256 on device feedback: at 2x2 the corners read soft
        // when a downscaled flat fill is stretched back up. 320² ARGB is
        // 400 KB, and artwork rides along at 256² = 256 KB — 656 KB total,
        // still comfortably under the ~1 MB RemoteViews/binder limit.
        assertEquals(320, WidgetGlass.MAX_EDGE_PX)
        val glassBytes = WidgetGlass.MAX_EDGE_PX * WidgetGlass.MAX_EDGE_PX * 4
        val artworkBytes = WidgetArtworkLoader.MAX_BITMAP_PX * WidgetArtworkLoader.MAX_BITMAP_PX * 4
        assertEquals(400 * 1024, glassBytes)
        assertTrue(
            "glass + artwork must stay under the binder budget",
            glassBytes + artworkBytes < 1_000_000,
        )
    }

    @Test
    fun `forWidget never exceeds the cap and respects aspect`() {
        val glass = WidgetGlass.forWidget(context, 650, 110)
        assertNotNull(glass)
        glass!!
        assertEquals(WidgetGlass.MAX_EDGE_PX, glass.width)
        assertTrue(glass.height < glass.width)
        assertTrue(glass.height > 0)
        assertEquals(Bitmap.Config.ARGB_8888, glass.config)
    }

    @Test
    fun `scaledSize preserves aspect under the cap`() {
        assertEquals(320 to 128, WidgetGlass.scaledSize(500, 200, 320))
        assertEquals(100 to 100, WidgetGlass.scaledSize(100, 100, 320))
        assertEquals(320 to 320, WidgetGlass.scaledSize(512, 512, 320))
        assertEquals(1 to 1, WidgetGlass.scaledSize(0, 0, 320))
    }

    // ----------------------------------------------------------------- radius

    @Test
    fun `fallback radius is 28dp of density and clamps absurd densities`() {
        // Pre-S hosts expose no system widget radius; the documented fallback
        // is 28dp, resolved with the same density clamp the card itself uses.
        assertEquals(28f, WidgetGlass.CORNER_RADIUS_DP, 0.0001f)
        assertEquals(56f, WidgetGlass.fallbackRadiusPx(2f), 0.001f)
        assertEquals(84f, WidgetGlass.fallbackRadiusPx(3f), 0.001f)
        // Below 1x and above 3x the clamp holds, so corners never explode.
        assertEquals(28f, WidgetGlass.fallbackRadiusPx(0.5f), 0.001f)
        assertEquals(84f, WidgetGlass.fallbackRadiusPx(6f), 0.001f)
    }

    @Test
    fun `corner radius resolves to a sane launcher value on s plus`() {
        val density = context.resources.displayMetrics.density
        val px = WidgetGlass.cornerRadiusPx(context)
        assertTrue("radius must be positive", px > 0f)
        // Either the platform dimen (16dp class on AOSP) or the 28dp fallback;
        // anything outside 0..48dp means a bad unit conversion.
        assertTrue("radius ${px / density}dp out of range", px / density in 4f..48f)
    }

    @Test
    fun `radius is clamped to half the shorter edge in bitmap space`() {
        // A tiny card cannot carry a full radius; bitmap() must clamp rather
        // than draw a lozenge.
        val out = WidgetGlass.bitmap(40, 20, 500f, 0xCC000000.toInt())
        assertNotNull(out)
        assertEquals(40, out.width)
        assertEquals(20, out.height)
    }

    @Test
    fun `applyAlpha replaces the channel and clamps`() {
        assertEquals(0xCC000000.toInt(), WidgetGlass.applyAlpha(0xFF000000.toInt(), 0.80f))
        assertEquals(0xFF123456.toInt(), WidgetGlass.applyAlpha(0x80123456.toInt(), 1f))
        assertEquals(0x00123456, WidgetGlass.applyAlpha(0xFF123456.toInt(), -1f))
        assertEquals(0xFF123456.toInt(), WidgetGlass.applyAlpha(0xFF123456.toInt(), 2f))
    }

    @Test
    fun `bitmap composites without throwing and keeps geometry`() {
        val out = WidgetGlass.bitmap(200, 100, 20f, 0xCC000000.toInt())
        assertNotNull(out)
        assertEquals(200, out.width)
        assertEquals(100, out.height)
        assertEquals(Bitmap.Config.ARGB_8888, out.config)
    }

    @Test
    fun `forWidget caches per size and clamps garbage`() {
        val first = WidgetGlass.forWidget(context, 110, 110)
        val second = WidgetGlass.forWidget(context, 110, 110)
        assertNotNull(first)
        assertSame(first, second)

        val other = WidgetGlass.forWidget(context, 250, 110)
        assertNotNull(other)
        assertNotSame(first, other)

        // Absurd / unknown sizes never throw and still return glass.
        assertNotNull(WidgetGlass.forWidget(context, 0, 0))
        assertNotNull(WidgetGlass.forWidget(context, 9999, 9999))
    }
}
