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
    fun `applyAlpha replaces the channel and clamps`() {
        assertEquals(0xB31C1B22.toInt(), WidgetGlass.applyAlpha(0xFF1C1B22.toInt(), 0.70f))
        assertEquals(0xFF123456.toInt(), WidgetGlass.applyAlpha(0x80123456.toInt(), 1f))
        assertEquals(0x00123456, WidgetGlass.applyAlpha(0xFF123456.toInt(), -1f))
        assertEquals(0xFF123456.toInt(), WidgetGlass.applyAlpha(0xFF123456.toInt(), 2f))
    }

    @Test
    fun `scaledSize preserves aspect under the cap`() {
        assertEquals(256 to 102, WidgetGlass.scaledSize(500, 200, 256))
        assertEquals(100 to 100, WidgetGlass.scaledSize(100, 100, 256))
        assertEquals(256 to 256, WidgetGlass.scaledSize(512, 512, 256))
        assertEquals(1 to 1, WidgetGlass.scaledSize(0, 0, 256))
    }

    @Test
    fun `bitmap composites without throwing and keeps geometry`() {
        val out = WidgetGlass.bitmap(200, 100, 20f, 0xB31C1B22.toInt(), 0x29FFFFFF)
        assertNotNull(out)
        assertEquals(200, out.width)
        assertEquals(100, out.height)
        assertEquals(Bitmap.Config.ARGB_8888, out.config)
    }

    @Test
    fun `forWidget returns a capped opaque-config bitmap`() {
        val glass = WidgetGlass.forWidget(context, 250, 140)
        assertNotNull(glass)
        glass!!
        assertTrue(glass.width <= WidgetGlass.MAX_EDGE_PX)
        assertTrue(glass.height <= WidgetGlass.MAX_EDGE_PX)
        assertEquals(Bitmap.Config.ARGB_8888, glass.config)
    }

    @Test
    fun `forWidget caches per size and clamps garbage`() {
        val first = WidgetGlass.forWidget(context, 250, 140)
        val second = WidgetGlass.forWidget(context, 250, 140)
        assertNotNull(first)
        assertSame(first, second)

        val other = WidgetGlass.forWidget(context, 110, 110)
        assertNotNull(other)
        assertNotSame(first, other)

        // Absurd / unknown sizes never throw and still return glass.
        assertNotNull(WidgetGlass.forWidget(context, 0, 0))
        assertNotNull(WidgetGlass.forWidget(context, 9999, 9999))
    }

    @Test
    fun `glass budget leaves headroom for artwork in one transaction`() {
        // 256² ARGB ≈ 256 KB; +256 KB artwork stays well under ~1 MB.
        val bytes = WidgetGlass.MAX_EDGE_PX * WidgetGlass.MAX_EDGE_PX * 4
        assertEquals(256 * 1024, bytes)
    }
}
