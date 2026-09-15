package dev.dhun.android.widgets

import android.graphics.Bitmap
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Artwork pipeline without the network: cache probes, decode guards and the
 * bitmap transform. Async fetch paths are intentionally untested — they need
 * HTTP + timing control and are failure-silent by contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetArtworkLoaderTest {

    @After
    fun clearCache() {
        WidgetArtworkLoader.evictAll()
    }

    @Test
    fun `cached returns null for blank keys and misses`() {
        assertNull(WidgetArtworkLoader.cached(null))
        assertNull(WidgetArtworkLoader.cached(""))
        assertNull(WidgetArtworkLoader.cached("  "))
        assertNull(WidgetArtworkLoader.cached("https://img/missing.jpg"))
    }

    @Test
    fun `decodeSampled rejects empty input and never throws on garbage`() {
        assertNull(WidgetArtworkLoader.decodeSampled(ByteArray(0), 256))
        // Must not throw — the result itself depends on the platform decoder
        // (Robolectric's shadow may synthesize a bitmap where a device
        // returns null), so only the no-throw contract is pinned here.
        WidgetArtworkLoader.decodeSampled("not-an-image".toByteArray(), 256)
    }

    @Test
    fun `transform center-crops and rounds to 256 square`() {
        val src = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        src.eraseColor(0xFFFF0000.toInt())
        val out = WidgetArtworkLoader.transform(src)
        assertNotNull(out)
        assertEquals(WidgetArtworkLoader.MAX_BITMAP_PX, out.width)
        assertEquals(WidgetArtworkLoader.MAX_BITMAP_PX, out.height)
        assertEquals(Bitmap.Config.ARGB_8888, out.config)
    }

    @Test
    fun `loadBytes with empty input delivers null`() {
        var delivered: Bitmap? = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        var called = false
        WidgetArtworkLoader.loadBytes(null, "k-null") { bitmap ->
            called = true
            delivered = bitmap
        }
        // Inline when already on main; otherwise drain the posted callback.
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, called)
        assertNull(delivered)
    }

    @Test
    fun `bitmap budget fits the binder limit`() {
        // 256² ARGB_8888 ≈ 256 KB — a quarter of the ~1 MB transaction cap.
        val bytes = WidgetArtworkLoader.MAX_BITMAP_PX * WidgetArtworkLoader.MAX_BITMAP_PX * 4
        assertEquals(256 * 1024, bytes)
    }
}
