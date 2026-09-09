package dev.dhun.android.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
