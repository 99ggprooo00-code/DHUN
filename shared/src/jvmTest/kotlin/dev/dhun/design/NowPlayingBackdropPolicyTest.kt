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
    fun oneVeilStaysTranslucentInBothThemes() {
        for (isLight in listOf(false, true)) {
            val stops = NowPlayingBackdropPolicy.veilStops(isLight, 55)
            assertEquals(listOf(0f, 0.35f, 0.72f, 1f), stops.map { it.first })
            assertTrue(stops.all { it.second in 0.3f..0.76f })
            val middle = stops.minOf { it.second }
            assertTrue(stops.first().second > middle)
            assertTrue(stops.last().second > middle)
        }
        // Light uses one lighter palette veil, NOT a black dim under a white scrim.
        assertTrue(NowPlayingBackdropPolicy.veilStops(true, 55).first().second <
            NowPlayingBackdropPolicy.veilStops(false, 55).first().second)
        assertTrue(NowPlayingBackdropPolicy.BLUR_SCALE >= 3)
        assertTrue(NowPlayingBackdropPolicy.OVERSCAN > 1f)
    }

    @Test
    fun pageVeilUsesTonalGlassWithoutChangingTheQueueMaterial() {
        val darkTint = NowPlayingBackdropPolicy.veilTint(DhunTokens.dark)
        val lightTint = NowPlayingBackdropPolicy.veilTint(DhunTokens.light)
        assertEquals(DhunTokens.dark.surfaceElevated, darkTint)
        assertEquals(DhunTokens.light.surfaceContainerHighest, lightTint)
        assertTrue(darkTint.red > DhunTokens.dark.background.red,
            "dark page backdrop should lift artwork instead of laying near-black over it")
        assertTrue(lightTint.red < DhunTokens.light.background.red,
            "light page backdrop should be warm rather than chalk white")
        // This policy is used by Home/Search/Library and browse pages. The
        // Queue/Related sheet keeps LyricsMaterialPolicy's separate recipe.
        assertEquals(0.42f, dev.dhun.design.components.LyricsMaterialPolicy.lyricsVeilStops().first().second)
        assertEquals(0.62f, dev.dhun.design.components.LyricsMaterialPolicy.lyricsVeilStops().last().second)
    }

    @Test
    fun backdropIsSuppressedOnlyWhenUrlIsBlank() {
        val validUrl = "https://lh3.googleusercontent.com/abc=w60-h60"
        assertTrue(NowPlayingBackdropPolicy.shouldRenderBackdrop(validUrl))
        kotlin.test.assertFalse(NowPlayingBackdropPolicy.shouldRenderBackdrop(null))
        kotlin.test.assertFalse(NowPlayingBackdropPolicy.shouldRenderBackdrop(""))
        kotlin.test.assertFalse(NowPlayingBackdropPolicy.shouldRenderBackdrop("   "))
    }

    @Test
    fun brightnessChangesTheSameVeilWithoutMakingItOpaque() {
        for (isLight in listOf(false, true)) {
            val dark = NowPlayingBackdropPolicy.veilStops(isLight, 0)
            val mid = NowPlayingBackdropPolicy.veilStops(isLight, 55)
            val bright = NowPlayingBackdropPolicy.veilStops(isLight, 100)
            dark.indices.forEach { i ->
                assertTrue(dark[i].second > mid[i].second)
                assertTrue(mid[i].second > bright[i].second)
                assertTrue(dark[i].second < 0.9f)
                assertTrue(bright[i].second > 0.15f)
            }
        }
        assertEquals(0f, NowPlayingBackdropPolicy.blurMultiplier(0), absoluteTolerance = 0.0001f)
        assertEquals(1f, NowPlayingBackdropPolicy.blurMultiplier(100), absoluteTolerance = 0.0001f)
        assertEquals(1f, NowPlayingBackdropPolicy.blurMultiplier(250), absoluteTolerance = 0.0001f)
    }

    @Test
    fun nonBlurFallbackDimsTheEstablishedLayerWithoutRemovingArtwork() {
        assertEquals(1f, NowPlayingBackdropPolicy.loadedLayerAlpha(blurActive = true))
        val fallback = NowPlayingBackdropPolicy.loadedLayerAlpha(blurActive = false)
        assertEquals(NowPlayingBackdropPolicy.NON_BLUR_LAYER_ALPHA, fallback)
        assertTrue(fallback > 0f, "zero/unsupported blur must retain visible sharp artwork")
        assertTrue(fallback < 1f, "the non-blur layer must be dimmed over the theme base")
    }
}
