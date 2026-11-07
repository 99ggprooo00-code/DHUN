package dev.dhun.ui.player

import dev.dhun.lyrics.LyricsTiming
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsOffsetSliderPolicyTest {

    @Test
    fun sliderRetainsTheStoredRangeAndFormerHundredMillisecondPrecision() {
        assertEquals(10_000, LyricsTiming.MAX_OFFSET_MS)
        assertEquals(199, LyricsOffsetSliderPolicy.SLIDER_STEPS)
        assertEquals(-10_000, LyricsOffsetSliderPolicy.offsetFromSlider(-12_000f))
        assertEquals(10_000, LyricsOffsetSliderPolicy.offsetFromSlider(12_000f))
        assertEquals(-100, LyricsOffsetSliderPolicy.offsetFromSlider(-51f))
        assertEquals(-100, LyricsOffsetSliderPolicy.offsetFromSlider(-50f))
        assertEquals(0, LyricsOffsetSliderPolicy.offsetFromSlider(-49f))
        assertEquals(0, LyricsOffsetSliderPolicy.offsetFromSlider(49f))
        assertEquals(100, LyricsOffsetSliderPolicy.offsetFromSlider(50f))
        assertEquals(100, LyricsOffsetSliderPolicy.offsetFromSlider(51f))
    }

    @Test
    fun requestedFiveSecondMarkersStayAtTheirTruePositionsOnTheTenSecondRange() {
        assertEquals(0f, LyricsOffsetSliderPolicy.markerFraction(-10_000))
        assertEquals(0.25f, LyricsOffsetSliderPolicy.markerFraction(-5_000))
        assertEquals(0.5f, LyricsOffsetSliderPolicy.markerFraction(0))
        assertEquals(0.75f, LyricsOffsetSliderPolicy.markerFraction(5_000))
        assertEquals(1f, LyricsOffsetSliderPolicy.markerFraction(10_000))
    }

    @Test
    fun offsetValueLabelMakesPositiveAndNegativeTimingExplicit() {
        assertEquals("0 ms", LyricsOffsetSliderPolicy.valueLabel(0))
        assertEquals("+1200 ms", LyricsOffsetSliderPolicy.valueLabel(1200))
        assertEquals("-1200 ms", LyricsOffsetSliderPolicy.valueLabel(-1200))
    }
}
