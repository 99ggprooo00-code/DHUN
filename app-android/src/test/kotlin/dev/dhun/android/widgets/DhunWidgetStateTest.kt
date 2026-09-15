package dev.dhun.android.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure formatting rules for the home-screen widgets. No Android framework,
 * no Robolectric — the state → display-text mapping must stay deterministic.
 */
class DhunWidgetStateTest {

    @Test
    fun `idle has no track and stable placeholder texts`() {
        val idle = DhunWidgetState.idle()
        assertTrue(idle.isIdle)
        assertFalse(idle.hasTrack)
        assertEquals(DhunWidgetState.IDLE_TITLE, idle.title)
        assertEquals(DhunWidgetState.IDLE_ARTIST, idle.artist)
        assertFalse(idle.isPlaying)
    }

    @Test
    fun `fromMetadata combines title and artist without truncation when short`() {
        val s = DhunWidgetState.fromMetadata("Nightcall", "Kavinsky", true)
        assertEquals("Nightcall", s.title)
        assertEquals("Kavinsky", s.artist)
        assertTrue(s.hasTrack)
        assertTrue(s.isPlaying)
        assertFalse(s.isIdle)
    }

    @Test
    fun `fromMetadata falls back to idle when both fields blank`() {
        assertTrue(DhunWidgetState.fromMetadata(null, null, false).isIdle)
        assertTrue(DhunWidgetState.fromMetadata("", " ", false).isIdle)
        assertTrue(DhunWidgetState.fromMetadata("   ", null, true).isIdle)
    }

    @Test
    fun `fromMetadata uses Unknown when title is blank but artist present`() {
        val s = DhunWidgetState.fromMetadata("", "Kavinsky", false)
        assertEquals("Unknown", s.title)
        assertEquals("Kavinsky", s.artist)
        assertFalse(s.isIdle)
    }

    @Test
    fun `fromMetadata keeps title when artist is blank`() {
        val s = DhunWidgetState.fromMetadata("Nightcall", "", false)
        assertEquals("Nightcall", s.title)
        assertEquals("", s.artist)
    }

    @Test
    fun `fromMetadata truncates over-long fields with ellipsis`() {
        val longTitle = "x".repeat(100)
        val longArtist = "y".repeat(100)
        val s = DhunWidgetState.fromMetadata(longTitle, longArtist, false)
        assertEquals(DhunWidgetState.MAX_TITLE_LEN, s.title.length)
        assertEquals("…", s.title.takeLast(1))
        assertEquals(DhunWidgetState.MAX_ARTIST_LEN, s.artist.length)
        assertEquals("…", s.artist.takeLast(1))
    }

    @Test
    fun `ellipsize trims whitespace before measuring`() {
        assertEquals("A", DhunWidgetState.ellipsize(" A ", 10))
        assertEquals("A — B", DhunWidgetState.ellipsize(" A — B ", 10))
    }

    @Test
    fun `ellipsize leaves short text untouched`() {
        assertEquals("Hello", DhunWidgetState.ellipsize("Hello", 10))
        assertEquals("Hello", DhunWidgetState.ellipsize("Hello", 5))
    }

    @Test
    fun `max lengths are stable widget contract`() {
        // Changing these silently truncates or overflows RemoteViews TextViews.
        assertEquals(30, DhunWidgetState.MAX_TITLE_LEN)
        assertEquals(24, DhunWidgetState.MAX_ARTIST_LEN)
    }

    // ------------------------------------------------------------- progress

    @Test
    fun `formatTime renders m-ss and h-mm-ss`() {
        assertEquals("0:00", DhunWidgetState.formatTime(0L))
        assertEquals("0:05", DhunWidgetState.formatTime(5_000L))
        assertEquals("1:01", DhunWidgetState.formatTime(61_000L))
        assertEquals("59:59", DhunWidgetState.formatTime(3_599_999L))
        assertEquals("1:00:00", DhunWidgetState.formatTime(3_600_000L))
        assertEquals("2:02:02", DhunWidgetState.formatTime(7_322_000L))
    }

    @Test
    fun `formatTime never goes negative or blank`() {
        assertEquals("0:00", DhunWidgetState.formatTime(-1L))
        assertEquals("0:00", DhunWidgetState.formatTime(Long.MIN_VALUE))
    }

    @Test
    fun `progressPermille scales position over duration and clamps`() {
        val half = playing(positionMs = 30_000L, durationMs = 60_000L)
        assertEquals(500, half.progressPermille)
        assertTrue(half.hasProgress)
        assertEquals("0:30", half.positionText)
        assertEquals("1:00", half.durationText)

        val over = playing(positionMs = 90_000L, durationMs = 60_000L)
        assertEquals(1000, over.progressPermille)

        val none = playing(positionMs = 10_000L, durationMs = 0L)
        assertEquals(0, none.progressPermille)
        assertFalse(none.hasProgress)
    }

    @Test
    fun `idle has no progress`() {
        val idle = DhunWidgetState.idle()
        assertEquals(0, idle.progressPermille)
        assertFalse(idle.hasProgress)
    }

    // ------------------------------------------------------------- playback

    @Test
    fun `fromPlayback carries shuffle repeat edges and artwork key`() {
        val s = DhunWidgetState.fromPlayback(
            rawTitle = "Nightcall",
            rawArtist = "Kavinsky",
            isPlaying = true,
            positionMs = 12_000L,
            durationMs = 240_000L,
            shuffleEnabled = true,
            repeatMode = DhunWidgetState.REPEAT_ONE,
            hasNext = true,
            hasPrevious = false,
            artworkKey = "https://img/x.jpg",
        )
        assertTrue(s.shuffleEnabled)
        assertEquals(DhunWidgetState.REPEAT_ONE, s.repeatMode)
        assertTrue(s.hasNext)
        assertFalse(s.hasPrevious)
        assertEquals("https://img/x.jpg", s.artworkKey)
        assertEquals(50, s.progressPermille)
    }

    @Test
    fun `fromPlayback clamps repeat and drops blank artwork keys`() {
        val high = playing(repeatMode = 99)
        assertEquals(DhunWidgetState.REPEAT_ONE, high.repeatMode)
        val low = playing(repeatMode = -5)
        assertEquals(DhunWidgetState.REPEAT_OFF, low.repeatMode)

        val blankArt = playing(artworkKey = "  ")
        assertNull(blankArt.artworkKey)
    }

    @Test
    fun `fromPlayback falls back to idle when both fields blank`() {
        val s = DhunWidgetState.fromPlayback(
            rawTitle = null, rawArtist = " ",
            isPlaying = true, positionMs = 5_000L, durationMs = 60_000L,
            shuffleEnabled = true, repeatMode = DhunWidgetState.REPEAT_ALL,
            hasNext = true, hasPrevious = true, artworkKey = "k",
        )
        assertTrue(s.isIdle)
        assertEquals(0, s.progressPermille)
    }

    @Test
    fun `fromMetadata defaults to no progress and repeat off`() {
        val s = DhunWidgetState.fromMetadata("A", "B", false)
        assertEquals(0L, s.positionMs)
        assertEquals(0L, s.durationMs)
        assertFalse(s.hasProgress)
        assertFalse(s.shuffleEnabled)
        assertEquals(DhunWidgetState.REPEAT_OFF, s.repeatMode)
        assertNull(s.artworkKey)
    }

    private fun playing(
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        repeatMode: Int = DhunWidgetState.REPEAT_OFF,
        artworkKey: String? = null,
    ) = DhunWidgetState.fromPlayback(
        rawTitle = "T", rawArtist = "A", isPlaying = true,
        positionMs = positionMs, durationMs = durationMs,
        shuffleEnabled = false, repeatMode = repeatMode,
        hasNext = false, hasPrevious = false, artworkKey = artworkKey,
    )
}
