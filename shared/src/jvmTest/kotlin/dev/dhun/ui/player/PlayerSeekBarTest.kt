package dev.dhun.ui.player

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerSeekBarTest {
    @Test
    fun progressClampsAtBothEdgesAndHandlesUnknownDuration() {
        assertEquals(0f, playbackProgress(50_000, 0))
        assertEquals(0f, playbackProgress(50_000, -1))
        assertEquals(0f, playbackProgress(-1, 100_000))
        assertEquals(0.5f, playbackProgress(50_000, 100_000))
        assertEquals(1f, playbackProgress(150_000, 100_000))
    }

    @Test
    fun tapDragAndAccessibilityFractionsShareTheSameClamp() {
        assertEquals(0L, seekPositionAt(-0.5f, 100_000))
        assertEquals(25_000L, seekPositionAt(0.25f, 100_000))
        assertEquals(100_000L, seekPositionAt(1.5f, 100_000))
    }

    @Test
    fun unknownDurationAndNonFiniteInputNeverSeek() {
        assertNull(seekPositionAt(0.5f, 0))
        assertNull(seekPositionAt(0.5f, -1))
        assertNull(seekPositionAt(Float.NaN, 100_000))
        assertNull(seekPositionAt(Float.POSITIVE_INFINITY, 100_000))
        assertNull(seekPositionAt(Float.NEGATIVE_INFINITY, 100_000))
    }

    @Test
    fun largeDurationConversionStaysWithinBounds() {
        assertEquals(0L, seekPositionAt(0f, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, seekPositionAt(1f, Long.MAX_VALUE))
        assertEquals(1f, playbackProgress(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun arrowKeysStepFiveSecondsAndClampAtEnds() {
        assertEquals(15_000L, seekPositionForKey(Key.DirectionRight, 10_000, 20_000))
        assertEquals(5_000L, seekPositionForKey(Key.DirectionLeft, 10_000, 20_000))
        assertEquals(20_000L, seekPositionForKey(Key.DirectionUp, 19_000, 20_000))
        assertEquals(0L, seekPositionForKey(Key.DirectionDown, 1_000, 20_000))
    }

    @Test
    fun keyboardSeekingDoesNotOverflowAndLeavesOtherShortcutsAlone() {
        assertEquals(Long.MAX_VALUE, seekPositionForKey(Key.DirectionRight, Long.MAX_VALUE - 1, Long.MAX_VALUE))
        assertEquals(0L, seekPositionForKey(Key.MoveHome, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(20_000L, seekPositionForKey(Key.MoveEnd, 0, 20_000))
        assertNull(seekPositionForKey(Key.Spacebar, 10_000, 20_000))
        assertNull(seekPositionForKey(Key.Enter, 10_000, 20_000))
        assertNull(seekPositionForKey(Key.DirectionRight, 10_000, 0))
    }

    @Test
    fun timestampFormattingIsClampedAndHourAwareAtZero() {
        assertEquals("0:00", formatMs(-500, 120_000))
        assertEquals("2:03", formatMs(123_000, 200_000))
        assertEquals("0:00:00", formatMs(0, 3_600_000))
        assertEquals("0:00:05", formatMs(5_000, 3_600_000))
        assertEquals("1:02:03", formatMs(3_723_000, 3_723_000))
        assertEquals("1:00:00", formatMs(3_600_000, 0))
    }
}
