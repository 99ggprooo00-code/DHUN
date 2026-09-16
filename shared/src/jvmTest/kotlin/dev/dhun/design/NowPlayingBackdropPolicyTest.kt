package dev.dhun.design

import dev.dhun.design.components.NowPlayingBackdropPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The now-playing backdrop's decisions: which artwork it asks for, and — the
 * part that must never regress — that it asks for **nothing at all** when there
 * is no artwork to show, so the existing DHUN background stays the fallback
 * instead of a hole, a glyph or a grey rectangle.
 */
class NowPlayingBackdropPolicyTest {

    @Test
    fun missingOrBlankArtworkMeansNoBackdropAtAll() {
        assertNull(NowPlayingBackdropPolicy.resolveUrl(null))
        assertNull(NowPlayingBackdropPolicy.resolveUrl(""))
        assertNull(NowPlayingBackdropPolicy.resolveUrl("   "))
    }

    @Test
    fun artworkIsRequestedAtTheListTierNotTheNowPlayingTier() {
        // A background blurred by 64dp cannot resolve 1024px, and three screens
        // should not each pull a megapixel image per track change.
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            NowPlayingBackdropPolicy.resolveUrl("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"),
        )
        assertEquals(ArtworkUrls.LIST_SIZE_PX, NowPlayingBackdropPolicy.artworkSizePx)
        assertTrue(NowPlayingBackdropPolicy.artworkSizePx < ArtworkUrls.NOW_PLAYING_SIZE_PX)
    }

    @Test
    fun nonProxyArtworkIsPassedThroughUnchanged() {
        // i.ytimg.com thumbnails have no size param to rewrite; guessing one
        // risks a 404, which would read as a broken background.
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            NowPlayingBackdropPolicy.resolveUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
    }

    @Test
    fun theScrimIsAWashNeverAPhoto() {
        val stops = NowPlayingBackdropPolicy.scrimStops()
        assertTrue(stops.isNotEmpty())
        // Ordered across the screen, and every stop translucent.
        var previous = -1f
        stops.forEach { (offset, alpha) ->
            assertTrue(offset >= previous, "scrim stops must ascend: $stops")
            previous = offset
            assertTrue(alpha in 0f..0.9f, "stop $offset is opaque enough to hide the artwork")
            assertTrue(alpha > 0f, "stop $offset does nothing")
        }
        assertEquals(0f, stops.first().first, 0.0001f)
        assertEquals(1f, stops.last().first, 0.0001f)
    }

    @Test
    fun theScrimIsHeaviestWhereChromeAndListsSit() {
        val stops = NowPlayingBackdropPolicy.scrimStops()
        val top = stops.first().second
        val bottom = stops.last().second
        val middle = stops.minBy { it.second }.second
        assertTrue(top > middle, "the search field / section headers sit at the top")
        assertTrue(bottom > middle, "the docked mini-player sits at the bottom")
    }

    @Test
    fun theDimKeepsContrastLowEnoughToRead() {
        // Together with the scrim this is what stops a bright album cover from
        // fighting the text on top of it.
        assertTrue(NowPlayingBackdropPolicy.DIM_ALPHA in 0.4f..0.75f)
        assertTrue(NowPlayingBackdropPolicy.BLUR_SCALE >= 3, "a background must be heavily blurred")
        assertTrue(NowPlayingBackdropPolicy.OVERSCAN > 1f, "the blur rim must never be visible")
    }
}
