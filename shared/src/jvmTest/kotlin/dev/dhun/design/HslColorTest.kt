package dev.dhun.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HslColorTest {
    @Test
    fun primaryAndSecondaryHuesHaveTheExpectedWheelPositions() {
        val expected = mapOf(
            Color.Red to 0f,
            Color(0xFFFFFF00) to 60f,
            Color.Green to 120f,
            Color.Cyan to 180f,
            Color.Blue to 240f,
            Color.Magenta to 300f,
        )
        for ((color, hue) in expected) {
            assertEquals(hue, color.toHsl().hue, 0.001f, color.toString())
            assertColorClose(color, HslColor(hue, 1f, 0.5f).toColor())
        }
    }

    @Test
    fun customColoursSurviveAnHslRoundTrip() {
        val colors = listOf(
            Color(0xFFBB86FC),
            Color(0xFF0B57D0),
            Color(0xFF00694A),
            Color(0xFFFFC46B),
            Color(0xFF123456),
            Color(0xFFF2E8FF),
        )
        for (color in colors) assertColorClose(color, color.toHsl().toColor())
    }

    @Test
    fun achromaticColoursDoNotInventSaturationOrDivideByZero() {
        for (color in listOf(Color.Black, Color.White, Color.Gray, Color(0xFF202020))) {
            val hsl = color.toHsl()
            assertEquals(0f, hsl.saturation, 0.0001f)
            assertColorClose(color, hsl.toColor())
        }
    }

    @Test
    fun outOfRangeDraftsAreClampedAndHueWraps() {
        assertColorClose(Color.Red, HslColor(360f, 2f, 0.5f).toColor())
        assertColorClose(Color.Red, HslColor(-360f, 1f, 0.5f).toColor())
        assertColorClose(Color.White, HslColor(45f, 0.5f, 2f).toColor())
        assertColorClose(Color.Black, HslColor(45f, 0.5f, -1f).toColor())
    }

    @Test
    fun hueTrackStartsAndEndsAtRedAndIncludesAllSixWaypoints() {
        val stops = hueSliderColors()
        assertEquals(7, stops.size)
        assertColorClose(Color.Red, stops.first())
        assertColorClose(Color.Red, stops.last())
        assertColorClose(Color.Green, stops[2])
        assertColorClose(Color.Blue, stops[4])
    }

    @Test
    fun saturationAndLightnessTracksPreviewTheCurrentDraft() {
        val draft = HslColor(210f, 0.65f, 0.4f)
        val saturation = saturationSliderColors(draft)
        assertColorClose(draft.copy(saturation = 0f).toColor(), saturation.first())
        assertColorClose(draft.copy(saturation = 1f).toColor(), saturation.last())

        val lightness = lightnessSliderColors(draft)
        assertColorClose(Color.Black, lightness.first())
        assertColorClose(draft.copy(lightness = 0.5f).toColor(), lightness[1])
        assertColorClose(Color.White, lightness.last())
    }

    @Test
    fun everyEditorColourIsOpaqueForTheExistingPreferenceCodec() {
        for (hue in 0 until 360 step 15) {
            val color = HslColor(hue.toFloat(), 0.7f, 0.45f).toColor()
            assertEquals(1f, color.alpha, 0.0001f)
            assertTrue(color.red in 0f..1f && color.green in 0f..1f && color.blue in 0f..1f)
        }
    }

    private fun assertColorClose(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.001f, "red")
        assertEquals(expected.green, actual.green, 0.001f, "green")
        assertEquals(expected.blue, actual.blue, 0.001f, "blue")
        assertEquals(expected.alpha, actual.alpha, 0.001f, "alpha")
    }
}
