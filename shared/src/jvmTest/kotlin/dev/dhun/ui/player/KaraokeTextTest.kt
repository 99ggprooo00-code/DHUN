package dev.dhun.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import dev.dhun.lyrics.LrcParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KaraokeTextTest {
    private val line = LrcParser.parse("[00:01]<00:01>Hello <00:02>world<00:03>").single()

    @Test fun highlightsOnlyWordsWhoseTimeHasArrivedAndSupportsBackwardSeek() {
        val before = karaokeText(line, 999, Color.Red, Color.Gray)
        val during = karaokeText(line, 1_500, Color.Red, Color.Gray)
        val after = karaokeText(line, 2_500, Color.Red, Color.Gray)
        assertEquals(line.text, during.text)
        assertTrue(before.spanStyles.none { it.item.color == Color.Red })
        assertEquals(1, during.spanStyles.count { it.item.color == Color.Red })
        assertEquals(TextDecoration.Underline, during.spanStyles[0].item.textDecoration)
        assertEquals(TextDecoration.None, after.spanStyles[0].item.textDecoration)
        assertEquals(TextDecoration.Underline, after.spanStyles[1].item.textDecoration)
        assertEquals(2, after.spanStyles.count { it.item.color == Color.Red })
        assertEquals(before, karaokeText(line, 999, Color.Red, Color.Gray))
    }

    @Test fun wordSeekingUsesTextRangesWithLineFallback() {
        assertEquals(1_000L, wordSeekTime(line, 2))
        assertEquals(2_000L, wordSeekTime(line, 7))
        assertEquals(1_000L, wordSeekTime(line, null))
        assertEquals(1_000L, wordSeekTime(line, 999))
        val rtl = LrcParser.parse("[00:01]<00:01>مرحبا <00:02>بالعالم<00:03>").single()
        assertEquals(2_000L, wordSeekTime(rtl, rtl.words[1].startIndex))
    }

    @Test fun plainAndInactiveLinesKeepLineStyling() {
        assertTrue(karaokeText(line, 2_000, Color.Red, Color.Gray, active = false).spanStyles.isEmpty())
        val plain = LrcParser.parse("[00:01]مرحبا بالعالم!").single()
        assertEquals(plain.text, karaokeText(plain, 2_000, Color.Red, Color.Gray).text)
        assertTrue(karaokeText(plain, 2_000, Color.Red, Color.Gray).spanStyles.isEmpty())
    }

    @Test fun explicitEndTimesAllowOverlappingVoices() {
        val lines = LrcParser.parse("[00:01]<00:01>Main<00:05>\n[00:02]<00:02>Backing<00:03>")
        assertEquals(setOf(0, 1), activeLyricIndices(lines, 2_500))
        assertEquals(setOf(0), activeLyricIndices(lines, 3_500))
        assertEquals(emptySet(), activeLyricIndices(lines, 5_000))
        assertEquals(emptySet(), activeLyricIndices(lines, 500))
    }
}
