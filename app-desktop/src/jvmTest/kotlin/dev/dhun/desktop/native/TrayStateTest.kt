package dev.dhun.desktop.native

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-core tests for the tray polish: the (track, playing) → state mapping
 * and the tooltip strings, including the ellipsis cap that keeps DHUN under
 * the shell's ~128-char tooltip elision.
 */
class TrayStateTest {

    // ---- state mapping ---------------------------------------------------

    @Test
    fun `no track is idle regardless of the playing flag`() {
        assertEquals(TrayStateKind.IDLE, TrayState.resolve(hasTrack = false, playing = true))
        assertEquals(TrayStateKind.IDLE, TrayState.resolve(hasTrack = false, playing = false))
    }

    @Test
    fun `track plus playing flag maps to playing or paused`() {
        assertEquals(TrayStateKind.PLAYING, TrayState.resolve(hasTrack = true, playing = true))
        assertEquals(TrayStateKind.PAUSED, TrayState.resolve(hasTrack = true, playing = false))
    }

    // ---- tooltips ---------------------------------------------------------

    @Test
    fun `idle tooltip matches the historical menu-row text`() {
        assertEquals("DHUN — nothing playing", TrayState.tooltip(TrayStateKind.IDLE, null, null))
        assertEquals("DHUN — nothing playing", TrayState.tooltip(TrayStateKind.IDLE, "Song", "Artist"))
    }

    @Test
    fun `playing and paused tooltips show title and artist`() {
        assertEquals(
            "DHUN — playing: Song — Artist",
            TrayState.tooltip(TrayStateKind.PLAYING, "Song", "Artist"),
        )
        assertEquals(
            "DHUN — paused: Song",
            TrayState.tooltip(TrayStateKind.PAUSED, "Song", "  "),
        )
        assertEquals("DHUN — playing", TrayState.tooltip(TrayStateKind.PLAYING, "", null))
    }

    @Test
    fun `long tooltips truncate with an ellipsis inside the shell-safe cap`() {
        val long = "y".repeat(500)
        val tip = TrayState.tooltip(TrayStateKind.PLAYING, long, null)
        assertEquals(TrayState.TOOLTIP_MAX_CHARS, tip.length)
        assertTrue(tip.endsWith("…"))
        assertTrue(tip.length < 128, "must stay under the shell elision point")
    }

    @Test
    fun `tooltip cap boundary is exact`() {
        val exact = "z".repeat(TrayState.TOOLTIP_MAX_CHARS - "DHUN — playing: ".length)
        assertEquals(
            "DHUN — playing: $exact",
            TrayState.tooltip(TrayStateKind.PLAYING, exact, null),
            "a tooltip exactly at the cap is not truncated",
        )
    }
}
