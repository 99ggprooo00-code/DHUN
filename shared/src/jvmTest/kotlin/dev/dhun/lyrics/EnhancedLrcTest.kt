package dev.dhun.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedLrcTest {
    @Test fun preservesRangesAndTerminalEnd() {
        val line = LrcParser.parse("[00:01.123]<00:01.123>Hello <00:02.456>world!<00:03.789>").single()
        assertEquals("Hello world!", line.text)
        assertEquals(listOf("Hello ", "world!"), line.words.map { line.text.substring(it.startIndex, it.endIndex) })
        assertEquals(listOf(1_123L, 2_456L), line.words.map { it.startTimeMs })
        assertEquals(listOf(2_456L, 3_789L), line.words.map { it.endTimeMs })
        assertEquals(listOf(line), LrcParser.parse(LrcParser.serialize(listOf(line))))
    }

    @Test fun repeatedLinesShiftWords() {
        val lines = LrcParser.parse("[00:01][00:11]<00:01>A <00:02>B<00:03>")
        assertEquals(11_000L, lines[1].words[0].startTimeMs)
        assertEquals(13_000L, lines[1].words[1].endTimeMs)
        assertEquals(lines, LrcParser.parse(LrcParser.serialize(lines)))
    }

    @Test fun incompleteTimingDoesNotInventWordBoundaries() {
        val line = LrcParser.parse("[00:01]Hello <00:02>world").single()
        assertEquals("Hello world", line.text)
        assertEquals(1, line.words.size)
        assertEquals(6, line.words.single().startIndex)
        assertEquals(null, line.words.single().endTimeMs)
        assertEquals(emptyList(), LrcParser.parse("[00:01]Plain").single().words)
    }

    @Test fun malformedTimingFallsBackWithoutDroppingText() {
        for (body in listOf("<00:02>A<00:01>B", "<00:99>A", "<00:00>A")) {
            val line = LrcParser.parse("[00:01]$body").single()
            assertTrue(line.text.isNotEmpty())
            assertTrue(line.words.isEmpty())
        }
        assertTrue(LrcParser.parse("[00:99]Invalid", false).isEmpty())
    }

    @Test fun joinedScriptsPunctuationAndEmojiRetainOriginalText() {
        for (text in listOf("مرحبا بالعالم!", "नमस्ते संसार", "你好世界", "한글 가사", "🎵 Hello!")) {
            val line = LrcParser.parse("[00:01]<00:01>$text<00:03>").single()
            assertEquals(text, line.text)
            assertEquals(text, line.text.substring(line.words.single().startIndex, line.words.single().endIndex))
            assertEquals(listOf(line), LrcParser.parse(LrcParser.serialize(listOf(line))))
        }
    }

    @Test fun duplicateMarkersAndWhitespaceDoNotProduceEmptyWords() {
        val line = LrcParser.parse("[00:01]  <00:01><00:01.5>A <00:02>B  <00:03>").single()
        assertEquals("A B", line.text)
        assertEquals(2, line.words.size)
        assertEquals(1_500L, line.words.first().startTimeMs)
        assertTrue(line.words.all { it.endIndex > it.startIndex })
    }
}
