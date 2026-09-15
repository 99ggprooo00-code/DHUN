package dev.dhun.ui.player

import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunSpacing
import dev.dhun.design.FullPlayerLayoutMode
import dev.dhun.design.fittedPlayerArtworkSize
import dev.dhun.design.fullPlayerLayoutMode
import dev.dhun.design.playerWideControlsWidth
import dev.dhun.design.usesCompactPlayerControls
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Player chrome geometry — the three device-reported bugs, as pure logic:
 *
 * 1. the sharp artwork is sized by [fittedPlayerArtworkSize] and drawn `Fit`,
 *    so nothing is ever cropped;
 * 2. the Queue sheet gets a height that is never starved by a density-blind
 *    inset ([queuePanelMetrics] + [chromeHeightDp]);
 * 3. the blurred backdrop stays clear in the middle (so the artwork glows)
 *    and darkens towards the bottom (so titles/progress/transport read).
 */
class PlayerSheetLayoutTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "expected $expected, got $actual (±$tolerance)",
        )
    }

    /* -------- bug 1: fit-to-card artwork, both axes ---------------------- */

    @Test
    fun heroArtworkUsesThePaddedFieldOnPhoneAndDesktopAndNeverOverflows() {
        // (field width, field height) as measured inside the hero stage.
        val fields = listOf(
            328.dp to 380.dp, // 360dp phone after the 16dp side inset
            361.dp to 520.dp, // 393dp phone: 91.9% of the safe content width
            688.dp to 760.dp, // 720dp content column after the same inset
            1_400.dp to 800.dp, // huge window: the token caps it
            120.dp to 240.dp, // narrow pane
        )
        fields.forEach { (width, height) ->
            val size = fittedPlayerArtworkSize(width, height)
            assertTrue(size <= width, "hero $size must fit a $width field")
            assertTrue(size <= height, "hero $size must fit a $height field")
            assertTrue(size <= DhunSpacing.playerArtworkMaxSize)
            assertEquals(minOf(width, height, DhunSpacing.playerArtworkMaxSize), size)
        }
        assertEquals(0.dp, fittedPlayerArtworkSize(320.dp, 0.dp), "no field, no artwork")
    }

    @Test
    fun portraitHeroKeepsTheCoverInTheRequestedWidthRange() {
        val phoneWidths = listOf(360.dp, 393.dp)
        phoneWidths.forEach { screenWidth ->
            val stageWidth = screenWidth -
                DhunSpacing.playerArtworkHorizontalInset * 2 -
                DhunSpacing.playerArtworkAnimationInset * 2
            val cover = fittedPlayerArtworkSize(stageWidth, 600.dp)
            val fraction = cover.value / screenWidth.value
            assertTrue(
                fraction in 0.80f..0.92f,
                "$cover should be 80–92% of a $screenWidth portrait player, was ${fraction * 100}%",
            )
        }
    }

    @Test
    fun wideViewportsGiveArtworkHeightInsteadOfStarvingItAboveChrome() {
        assertEquals(FullPlayerLayoutMode.Stacked, fullPlayerLayoutMode(393.dp, 823.dp))
        assertEquals(FullPlayerLayoutMode.Wide, fullPlayerLayoutMode(873.dp, 393.dp))
        assertEquals(FullPlayerLayoutMode.Wide, fullPlayerLayoutMode(1_200.dp, 780.dp))
        assertEquals(FullPlayerLayoutMode.Stacked, fullPlayerLayoutMode(479.dp, 320.dp))
        assertEquals(FullPlayerLayoutMode.Stacked, fullPlayerLayoutMode(480.dp, 480.dp))

        // A compact landscape control column leaves a real artwork pane; it
        // never asks the stacked layout to squeeze art above all controls.
        val controls = playerWideControlsWidth(873.dp)
        assertTrue(controls >= DhunSpacing.playerWideControlsMinWidth)
        assertTrue(controls <= DhunSpacing.playerWideControlsMaxWidth)
        assertTrue(controls + DhunSpacing.playerWideLayoutGap < 873.dp)
    }

    @Test
    fun shortViewportsCompactOnlySpacingNotTouchTargets() {
        assertTrue(usesCompactPlayerControls(DhunSpacing.playerCompactControlsHeight))
        assertFalse(usesCompactPlayerControls(DhunSpacing.playerCompactControlsHeight + 1.dp))
        assertFalse(usesCompactPlayerControls(0.dp))
        assertEquals(0.dp, playerWideControlsWidth(0.dp))
    }

    /* -------- bug 3: the queue sheet is never a thin bar ----------------- */

    @Test
    fun phoneSizedSheetKeepsRowsAboveTheChrome() {
        // 393×873dp phone, ~50dp of insets, ~330dp of chrome.
        val metrics = queuePanelMetrics(availableHeight = 823.dp, chromeHeight = 330.dp)
        assertEquals(330.dp, metrics.bottomInset, "the sheet floats exactly above the chrome")
        assertClose(expected = 0.68f * 493f, actual = metrics.height.value)
        assertTrue(metrics.height >= DhunSpacing.queuePanelMinHeight)
        assertTrue(
            metrics.height + metrics.bottomInset <= 823.dp,
            "sheet + chrome must never overflow the safe area",
        )
    }

    @Test
    fun measuredChromePixelsBecomeDpBeforeTheyAreUsedAsAnInset() {
        // Android: 907px of chrome at density 2.75 is 330dp, NOT 907dp.
        val androidChrome = chromeHeightDp(pixelHeight = 907, density = 2.75f)
        assertClose(expected = 329.8f, actual = androidChrome.value, tolerance = 0.05f)

        // Desktop at density 1.0 is unchanged.
        assertClose(expected = 330f, actual = chromeHeightDp(330, 1f).value)

        // The regression: a pixel count used as dp ate the whole sheet, so
        // Android showed an empty translucent bar and desktop a thin sliver.
        val metrics = queuePanelMetrics(availableHeight = 823.dp, chromeHeight = androidChrome)
        assertTrue(metrics.bottomInset < 823.dp - DhunSpacing.queuePanelMinHeight)
        assertTrue(metrics.height >= DhunSpacing.queuePanelMinHeight, "rows must have room")
    }

    @Test
    fun degenerateDensitiesAndHeightsStayInsideTheLayout() {
        assertEquals(0.dp, chromeHeightDp(-10, 2.75f))
        assertEquals(0.dp, chromeHeightDp(0, 2.75f))
        assertClose(expected = 100f, actual = chromeHeightDp(100, 0f).value, tolerance = 0.001f)
        assertClose(expected = 100f, actual = chromeHeightDp(100, Float.NaN).value, tolerance = 0.001f)
        assertClose(expected = 100f, actual = chromeHeightDp(100, Float.POSITIVE_INFINITY).value, tolerance = 0.001f)
    }

    @Test
    fun shortViewportsShrinkTheSheetInsteadOfOverflowingTheScreen() {
        // Landscape phone: the chrome barely leaves room above it.
        val tight = queuePanelMetrics(availableHeight = 400.dp, chromeHeight = 320.dp)
        assertEquals(320.dp, tight.bottomInset)
        assertEquals(80.dp, tight.height, "the sheet takes the room it has")

        // Chrome taller than the viewport: nothing to show above it.
        val impossible = queuePanelMetrics(availableHeight = 300.dp, chromeHeight = 480.dp)
        assertEquals(300.dp, impossible.bottomInset)
        assertEquals(0.dp, impossible.height)

        val negative = queuePanelMetrics(availableHeight = (-40).dp, chromeHeight = 100.dp)
        assertEquals(0.dp, negative.bottomInset)
        assertEquals(0.dp, negative.height)
    }

    @Test
    fun sheetNeverDropsBelowTheReadableFloorWhileRoomExists() {
        val lowFraction = queuePanelMetrics(availableHeight = 823.dp, chromeHeight = 330.dp, fraction = 0.01f)
        assertEquals(DhunSpacing.queuePanelMinHeight, lowFraction.height)

        val highFraction = queuePanelMetrics(availableHeight = 823.dp, chromeHeight = 330.dp, fraction = 4f)
        assertEquals(493.dp, highFraction.height, "an out-of-range fraction clamps to the room available")
    }

    /* -------- bug 2: blurred backdrop, dark at the bottom ---------------- */

    @Test
    fun ambientScrimGlowsThroughTheMiddleAndDarkensTheControlCluster() {
        val stops = playerAmbientScrimStops()

        assertEquals(0f, stops.first().first)
        assertEquals(1f, stops.last().first)
        stops.forEach { (offset, alpha) ->
            assertTrue(offset in 0f..1f, "offset $offset out of range")
            assertTrue(alpha in 0f..1f, "alpha $alpha out of range")
        }
        stops.zipWithNext { (offsetA, _), (offsetB, _) ->
            assertTrue(offsetB > offsetA, "stops must ascend: $offsetA then $offsetB")
        }

        // Middle of the screen: the blurred artwork must read through.
        val middle = stops.first { it.first in 0.30f..0.50f }
        assertEquals(0f, middle.second, "the backdrop stays untouched behind the artwork field")

        // Bottom: dark enough for text/transport, but never a flat black wall.
        val bottom = stops.last().second
        assertTrue(bottom >= 0.85f, "bottom scrim too light for chrome: $bottom")
        assertTrue(bottom < 1f, "bottom scrim kills the artwork: $bottom")

        // And it must darken on the way down, so there is no bright band behind the controls.
        val lowerBand = stops.filter { it.first >= 0.55f }.map { it.second }
        lowerBand.zipWithNext { a, b -> assertTrue(b >= a, "scrim lightens towards the bottom: $a then $b") }
        assertTrue(lowerBand.first() > 0f, "the darkening must start above the very bottom edge")
    }

    /* -------- queue rows ------------------------------------------------- */

    @Test
    fun queueRowsShowADurationOnlyWhenItIsKnown() {
        assertEquals("3:45", queueRowDurationLabel(225))
        assertEquals("1:02:03", queueRowDurationLabel(3_723))
        assertNull(queueRowDurationLabel(null), "unknown length shows no time column")
        assertNull(queueRowDurationLabel(0), "a zero duration is not a real length")
        assertNull(queueRowDurationLabel(-5))
    }
}
