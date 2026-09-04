package dev.dhun.lyrics

import dev.dhun.core.LyricsLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LrcParserTest {

    // 1) [mm:ss.xx] two-digit centiseconds
    @Test fun parseMmSsXx() {
        val lrc = "[00:12.34]Hello\n[01:05.67]World"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].startTimeMs)
        assertEquals("Hello", lines[0].text)
        assertEquals(65_670L, lines[1].startTimeMs) // 1*60000+5*1000+670
        assertEquals("World", lines[1].text)
    }

    // 2) [mm:ss.xxx] three-digit milliseconds
    @Test fun parseMmSsXxx() {
        val lrc = "[00:00.500]Intro\n[00:12.345]Verse"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(500L, lines[0].startTimeMs)
        assertEquals(12_345L, lines[1].startTimeMs)
    }

    // 3) [mm:ss] no fraction
    @Test fun parseMmSsNoFraction() {
        val lrc = "[01:02]No fraction\n[02:03]Also"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(62_000L, lines[0].startTimeMs) // 1*60000+2*1000
        assertEquals(123_000L, lines[1].startTimeMs)
    }

    // 4) Multiple timestamps on one line
    @Test fun parseMultiTimestamp() {
        val lrc = "[00:10.00][00:12.00][00:14.00]Repeat"
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        // sorted
        assertEquals(10_000L, lines[0].startTimeMs)
        assertEquals(12_000L, lines[1].startTimeMs)
        assertEquals(14_000L, lines[2].startTimeMs)
        assertTrue(lines.all { it.text == "Repeat" })
    }

    // 5) Enhanced LRC word timings <mm:ss.xx> tolerated / stripped
    @Test fun parseEnhancedStripped() {
        val lrc = "[00:05.00]Hello <00:05.20>world <00:05.40>!\n[00:10.00]Next line"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(5_000L, lines[0].startTimeMs)
        // word timings stripped, remaining text collapsed
        assertTrue(lines[0].text.contains("Hello"))
        assertTrue(lines[0].text.contains("world"))
        assertFalse(lines[0].text.contains("<"))
        assertEquals("Next line", lines[1].text)
    }

    // Diverse: metadata tags skipped
    @Test fun parseMetadataSkipped() {
        val lrc = "[ti:Title]\n[ar:Artist]\n[al:Album]\n[by:Author]\n[00:10.00]Real line"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(10_000L, lines[0].startTimeMs)
        assertEquals("Real line", lines[0].text)
    }

    // Unsynced fallback
    @Test fun parseUnsyncedFallback() {
        val lrc = "Just plain lyrics\nSecond line\nThird"
        val lines = LrcParser.parse(lrc, allowUnsyncedFallback = true)
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.startTimeMs == null })
        assertEquals("Just plain lyrics", lines[0].text)
        // with fallback disabled, unsynced lines are ignored
        val none = LrcParser.parse(lrc, allowUnsyncedFallback = false)
        assertEquals(0, none.size)
    }

    // Sorting
    @Test fun parseSorted() {
        val lrc = "[00:20.00]Second\n[00:10.00]First\n[00:15.00]Middle"
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(10_000L, lines[0].startTimeMs)
        assertEquals(15_000L, lines[1].startTimeMs)
        assertEquals(20_000L, lines[2].startTimeMs)
    }

    // isSynced heuristic
    @Test fun isSyncedHeuristic() {
        assertTrue(LrcParser.isSynced("[00:10.00]Hello"))
        assertFalse(LrcParser.isSynced("Just plain text"))
        assertFalse(LrcParser.isSynced("[ti:Title]"))
    }

    // Blank timestamp-only lines → empty text still has timing
    @Test fun parseBlankLines() {
        val lrc = "[00:10.00]\n[00:12.00]After"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text) // placeholder handled by UI as ♪
        assertEquals("After", lines[1].text)
    }
}
