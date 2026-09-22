package dev.dhun.design.components

import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the lyrics-card material so a later polish pass cannot put the
 * near-black `glassStrong` slab back under like-chips, the nav band under
 * the mini-player, Related, or the mini-player and call it glass.
 */
class LyricsMaterialPolicyTest {

    @BeforeTest
    fun resetBefore() {
        DhunAppearance.reset()
    }

    @AfterTest
    fun resetAfter() {
        DhunAppearance.reset()
    }

    @Test
    fun lyricsVeilIsTheLyricsCardContract() {
        // FullPlayer's lyrics card paints exactly these alphas over the
        // blurred artwork. Related, tab cards and small chrome share them.
        assertEquals(
            listOf(0f to 0.42f, 1f to 0.62f),
            LyricsMaterialPolicy.lyricsVeilStops(),
        )
        assertEquals(LyricsMaterialPolicy.LYRICS_VEIL_TOP, LyricsMaterialPolicy.lyricsVeilStops().first().second)
        assertEquals(LyricsMaterialPolicy.LYRICS_VEIL_BOTTOM, LyricsMaterialPolicy.lyricsVeilStops().last().second)
    }

    @Test
    fun acrylicIsLighterThanBothTheLyricsVeilAndTheOldBlackDock() {
        val glassStrong = DhunColors.glassStrong.alpha
        val lyricsMax = LyricsMaterialPolicy.lyricsVeilStops().maxOf { it.second }
        LyricsMaterialPolicy.acrylicVeilStops().forEach { (offset, alpha) ->
            assertTrue(alpha < lyricsMax, "acrylic stop $offset ($alpha) must be lighter than the lyrics veil")
            assertTrue(alpha < glassStrong, "acrylic stop $offset must not be the old glassStrong slab ($glassStrong)")
            assertTrue(alpha > 0.2f, "acrylic stop $offset is too clear to keep a title readable")
        }
        assertTrue(LyricsMaterialPolicy.ACRYLIC_ART_DIM in 0.1f..0.3f)
        assertTrue(LyricsMaterialPolicy.ACRYLIC_ART_DIM + LyricsMaterialPolicy.ACRYLIC_VEIL_BOTTOM < glassStrong)
    }

    @Test
    fun acrylicFrostIsAHighlightNotASecondSlab() {
        val frost = LyricsMaterialPolicy.acrylicFrostStops()
        assertEquals(0f, frost.first().first)
        assertEquals(1f, frost.last().first)
        frost.zipWithNext { (a, _), (b, _) ->
            assertTrue(b > a, "frost stops must ascend")
        }
        frost.forEach { (offset, alpha) ->
            assertTrue(alpha in 0.02f..0.2f, "frost at $offset ($alpha) should read as milk, not paint")
        }
        // The highlight fades down the bar — a uniform white wash is a slab.
        assertTrue(frost.first().second > frost.last().second)
    }

    @Test
    fun lyricsVeilStaysTranslucentAndDarkensDownward() {
        val stops = LyricsMaterialPolicy.lyricsVeilStops()
        assertTrue(stops.zipWithNext { a, b -> b.second >= a.second }.all { it })
        stops.forEach { (_, alpha) ->
            assertTrue(alpha in 0.3f..0.75f, "lyrics veil $alpha left the legibility band")
            assertTrue(alpha < DhunColors.glassStrong.alpha)
        }
    }

    @Test
    fun ownArtworkIsSkippedWhenThereIsNothingToBlur() {
        val url = "https://lh3.googleusercontent.com/abc=w60-h60"
        assertTrue(LyricsMaterialPolicy.shouldPaintArtwork(url, supportsBlur = true))
        assertFalse(LyricsMaterialPolicy.shouldPaintArtwork(url, supportsBlur = false))
        assertFalse(LyricsMaterialPolicy.shouldPaintArtwork(null, supportsBlur = true))
        assertFalse(LyricsMaterialPolicy.shouldPaintArtwork("", supportsBlur = true))
        assertFalse(LyricsMaterialPolicy.shouldPaintArtwork("   ", supportsBlur = true))
        // Same radius the lyrics card uses, not a per-frame reblur.
        assertEquals(2, LyricsMaterialPolicy.BLUR_SCALE)
    }
}
