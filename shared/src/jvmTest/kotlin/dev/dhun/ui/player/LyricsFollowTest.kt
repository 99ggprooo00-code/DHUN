package dev.dhun.ui.player

import dev.dhun.core.LyricsLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsFollowTest {
    private val lines = listOf(
        LyricsLine(1_000, "First line"),
        LyricsLine(5_000, "Second line"),
        LyricsLine(9_000, "Last line"),
    )

    @Test
    fun introHasNoActiveLineAndFirstTimestampSelectsIndexZero() {
        assertEquals(-1, activeLyricIndex(lines, -1))
        assertEquals(-1, activeLyricIndex(lines, 999))
        assertEquals(0, activeLyricIndex(lines, 1_000))
        assertEquals(0, activeLyricIndex(lines, 4_999))
    }

    @Test
    fun backwardSeekReturnsToFirstLineAndThenIntro() {
        assertEquals(2, activeLyricIndex(lines, 15_000))
        assertEquals(0, activeLyricIndex(lines, 2_000))
        assertEquals(-1, activeLyricIndex(lines, 0))
    }

    @Test
    fun emptyAndUntimedLyricsHaveNoActiveLine() {
        assertEquals(-1, activeLyricIndex(emptyList(), 100_000))
        assertEquals(-1, activeLyricIndex(listOf(LyricsLine(null, "Untimed")), 100_000))
        assertEquals(-1, activeLyricIndex(listOf(LyricsLine(-100, "Invalid")), 100_000))
    }

    @Test
    fun trailingUntimedLineDoesNotStealHighlight() {
        val mixed = lines + LyricsLine(null, "Credit")
        assertEquals(1, activeLyricIndex(mixed, 5_000))
        assertEquals(2, activeLyricIndex(mixed, 10_000))
    }

    @Test
    fun duplicateTimestampsSelectLastMatchingLine() {
        val duet = listOf(LyricsLine(0, "A"), LyricsLine(0, "B"), LyricsLine(1_000, "C"))
        assertEquals(1, activeLyricIndex(duet, 0))
        assertEquals(2, activeLyricIndex(duet, 1_000))
    }

    @Test
    fun unorderedTimestampDoesNotOverrideMoreRecentLine() {
        val unordered = listOf(LyricsLine(2_000, "Later"), LyricsLine(0, "Earlier"))
        assertEquals(1, activeLyricIndex(unordered, 1_000))
        assertEquals(0, activeLyricIndex(unordered, 3_000))
    }

    @Test
    fun centeringAccountsForPaddingAndWrappedLineHeight() {
        // A 300px pane with 150px top padding has viewport [-150, 150].
        assertEquals(30f, lyricCenterScrollDelta(0, 60, -150, 150))
        assertEquals(0f, lyricCenterScrollDelta(-30, 60, -150, 150))
        assertEquals(-70f, lyricCenterScrollDelta(-100, 60, -150, 150))
        assertEquals(60f, lyricCenterScrollDelta(0, 120, -150, 150))
    }

    @Test
    fun centeringWorksWithoutSymmetricPadding() {
        assertEquals(0f, lyricCenterScrollDelta(130, 40, 0, 300))
        assertEquals(80f, lyricCenterScrollDelta(180, 60, -40, 300))
    }

    @Test
    fun subPixelJitterNeverTriggersARecenterAnimation() {
        // 1px and above moves; anything finer is invisible and only twitches.
        assertTrue(shouldRecenterLyric(1f))
        assertTrue(shouldRecenterLyric(-0.6f))
        assertFalse(shouldRecenterLyric(0.5f))
        assertFalse(shouldRecenterLyric(-0.5f))
        assertFalse(shouldRecenterLyric(0f))
        assertFalse(shouldRecenterLyric(Float.NaN))
    }
}
