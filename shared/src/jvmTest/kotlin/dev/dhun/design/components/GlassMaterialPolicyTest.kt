package dev.dhun.design.components

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins the DHUN material recipe without pretending a JVM test is a screenshot. */
class GlassMaterialPolicyTest {

    private val tolerance = 0.01f

    @Test
    fun bodyHasDepthAndPreservesTheReadableAlphaInBothModes() {
        for (light in listOf(false, true)) {
            val base = Color(0xBFE7E3DE)
            val stops = GlassMaterialPolicy.bodyStops(base, light)
            assertEquals(listOf(0f, GlassMaterialPolicy.BODY_MID_STOP, 1f), stops.map { it.first })
            stops.zipWithNext().forEach { (first, second) ->
                assertTrue(second.first > first.first, "body offsets must ascend")
            }
            val top = stops.first().second
            val middle = stops[1].second
            val bottom = stops.last().second
            assertTrue(luminance(top) > luminance(middle), "light=$light top must be lifted")
            assertTrue(luminance(bottom) < luminance(middle), "light=$light bottom must deepen")
            stops.forEach { (_, color) ->
                assertEquals(base.alpha, color.alpha, tolerance, "body must not choose a new opacity")
                assertTrue(
                    abs(luminance(color) - luminance(middle)) < 0.12f,
                    "light=$light depth must not become a wash",
                )
            }
        }
    }

    @Test
    fun sheenIsCappedDirectionalAndIndependentlyTunedForLight() {
        val dark = GlassMaterialPolicy.sheenStops(false)
        val light = GlassMaterialPolicy.sheenStops(true)
        for (stops in listOf(dark, light)) {
            assertEquals(0f, stops.first().first)
            assertEquals(GlassMaterialPolicy.SHEEN_EXTENT, stops.last().first)
            assertEquals(0f, stops.last().second)
            assertTrue(stops.first().second > 0f)
            assertTrue(stops.first().second <= GlassMaterialPolicy.SHEEN_TOP)
            stops.zipWithNext().forEach { (first, second) ->
                assertTrue(second.first > first.first)
                assertTrue(second.second <= first.second, "sheen must fade downwards")
            }
        }
        assertTrue(light.first().second < dark.first().second, "white-on-white needs a quieter sheen")
    }

    @Test
    fun contentCardsUseTheContrastSafeSheenCut() {
        val sheer = Color(0x52303030)
        val strengthened = GlassMaterialPolicy.contentBody(sheer)
        assertEquals(GlassMaterialPolicy.CONTENT_BODY_MIN_ALPHA, strengthened.alpha, tolerance)
        assertEquals(sheer.red, strengthened.red, tolerance)
        val alreadyReadable = Color(0xE6303030)
        assertEquals(alreadyReadable, GlassMaterialPolicy.contentBody(alreadyReadable))

        val dark = GlassMaterialPolicy.contentSheenStops(false)
        assertEquals(GlassMaterialPolicy.CONTENT_SHEEN_TOP_DARK, dark.first().second, tolerance)
        assertEquals(0f, dark.last().second)
        assertTrue(dark.first().second < GlassMaterialPolicy.sheenStops(false).first().second)
        assertEquals(
            GlassMaterialPolicy.sheenStops(true),
            GlassMaterialPolicy.contentSheenStops(true),
            "the independently tuned light sheen already stays within its contrast band",
        )
    }

    @Test
    fun edgeCatchesLightAtTheTopAndFadesAtTheBottom() {
        for (light in listOf(false, true)) {
            val stops = GlassMaterialPolicy.edgeStops(light)
            assertEquals(listOf(0f, 1f), stops.map { it.first })
            assertTrue(stops.first().second > stops.last().second)
            assertTrue(stops.last().second > 0f)
        }
        assertTrue(
            GlassMaterialPolicy.edgeStops(true).first().second <
                GlassMaterialPolicy.edgeStops(false).first().second,
            "light mode has its own edge strength",
        )
    }

    @Test
    fun dialogGlassIsReadableButNotAnOpaquePlate() {
        for (light in listOf(false, true)) {
            val body = GlassMaterialPolicy.dialogBody(light, Color(0xFF262626))
            val expected = if (light) {
                GlassMaterialPolicy.DIALOG_BODY_ALPHA_LIGHT
            } else {
                GlassMaterialPolicy.DIALOG_BODY_ALPHA_DARK
            }
            assertEquals(expected, body.alpha, tolerance)
            assertTrue(body.alpha > 0.85f)
            assertTrue(body.alpha < 1f)
        }
        assertTrue(
            GlassMaterialPolicy.DIALOG_BODY_ALPHA_LIGHT >
                GlassMaterialPolicy.DIALOG_BODY_ALPHA_DARK,
        )
    }

    @Test
    fun lightElevationIsQuieterThanDarkElevation() {
        val darkCard = GlassMaterialPolicy.cardShadowColor(false)
        val lightCard = GlassMaterialPolicy.cardShadowColor(true)
        val darkDock = GlassMaterialPolicy.dockShadowColor(false)
        val lightDock = GlassMaterialPolicy.dockShadowColor(true)
        assertEquals(GlassMaterialPolicy.CARD_SHADOW_ALPHA_DARK, darkCard.alpha, tolerance)
        assertEquals(GlassMaterialPolicy.CARD_SHADOW_ALPHA_LIGHT, lightCard.alpha, tolerance)
        assertEquals(GlassMaterialPolicy.DOCK_SHADOW_ALPHA_DARK, darkDock.alpha, tolerance)
        assertEquals(GlassMaterialPolicy.DOCK_SHADOW_ALPHA_LIGHT, lightDock.alpha, tolerance)
        assertTrue(lightCard.alpha < darkCard.alpha)
        assertTrue(lightDock.alpha < darkDock.alpha)
        assertTrue(lightCard.alpha > 0f && lightDock.alpha > 0f)
    }

    @Test
    fun unsupportedOrDisabledBlurUsesTheNonBlurFallback() {
        assertFalse(GlassMaterialPolicy.shouldBlurArtwork(false, 100))
        assertFalse(GlassMaterialPolicy.shouldBlurArtwork(true, 0))
        assertFalse(GlassMaterialPolicy.shouldBlurArtwork(true, -20))
        assertFalse(GlassMaterialPolicy.shouldBlurArtwork(false, 0))
        assertTrue(GlassMaterialPolicy.shouldBlurArtwork(true, 1))
        assertTrue(GlassMaterialPolicy.shouldBlurArtwork(true, 100))
    }

    @Test
    fun blendIsBoundedAndNeverChangesAlpha() {
        val base = Color(0.2f, 0.3f, 0.4f, 0.5f)
        val lifted = GlassMaterialPolicy.blend(base, Color.White, 0.5f)
        assertEquals(0.6f, lifted.red, tolerance)
        assertEquals(0.65f, lifted.green, tolerance)
        assertEquals(0.7f, lifted.blue, tolerance)
        assertEquals(base.alpha, lifted.alpha, tolerance)
        val unchanged = GlassMaterialPolicy.blend(base, Color.White, -1f)
        assertEquals(base.red, unchanged.red, tolerance)
        assertEquals(base.green, unchanged.green, tolerance)
        assertEquals(base.blue, unchanged.blue, tolerance)
        val bounded = GlassMaterialPolicy.blend(base, Color.Black, 2f)
        assertEquals(0f, bounded.red, tolerance)
        assertEquals(base.alpha, bounded.alpha, tolerance)
    }

    private fun luminance(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
}
