package dev.dhun.android.shortcuts

import dev.dhun.core.PlaybackState
import dev.dhun.core.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure rules of the dynamic "Now playing" shortcut: label construction and
 * playback-state -> track mapping. No Android framework types, no
 * ShortcutManager shadow — the publishing wrapper stays intentionally thin.
 */
class NowPlayingShortcutLabelsTest {

    private fun track(title: String = "Nightcall", artist: String = "Kavinsky") =
        Track(id = "vid1", title = title, artistName = artist)

    @Test
    fun `combines title and artist with an em dash`() {
        assertEquals("Nightcall — Kavinsky", NowPlayingShortcutLabels.longLabel("Nightcall", "Kavinsky"))
    }

    @Test
    fun `title only when artist is blank`() {
        assertEquals("Nightcall", NowPlayingShortcutLabels.longLabel("Nightcall", " "))
        assertEquals("Nightcall", NowPlayingShortcutLabels.longLabel("Nightcall", null))
    }

    @Test
    fun `falls back to Untitled for blank or null title`() {
        // Artist info is still shown beside the fallback title — losing the
        // title must not also lose the artist.
        assertEquals("Untitled — Kavinsky", NowPlayingShortcutLabels.longLabel(null, "Kavinsky"))
        assertEquals("Untitled — Kavinsky", NowPlayingShortcutLabels.longLabel("   ", "Kavinsky"))
        assertEquals("Untitled", NowPlayingShortcutLabels.longLabel("", ""))
    }

    @Test
    fun `truncates over-long labels to the bound with an ellipsis`() {
        val label = NowPlayingShortcutLabels.longLabel("x".repeat(200), "y".repeat(200))

        assertEquals(NowPlayingShortcutLabels.MAX_LONG_LABEL, label.length)
        assertEquals("…", label.takeLast(1))
    }

    @Test
    fun `whitespace is trimmed before combining`() {
        assertEquals("A — B", NowPlayingShortcutLabels.longLabel(" A ", " B "))
    }

    @Test
    fun `shortcutTrack maps every live state to its track`() {
        val t = track()
        assertEquals(t, PlaybackState.Resolving(t).shortcutTrack())
        assertEquals(t, PlaybackState.Buffering(t).shortcutTrack())
        assertEquals(t, PlaybackState.Recovering(t).shortcutTrack())
        assertEquals(t, PlaybackState.Playing(t).shortcutTrack())
        assertEquals(t, PlaybackState.Paused(t).shortcutTrack())
    }

    @Test
    fun `shortcutTrack keeps idle error and null without a track`() {
        assertNull(PlaybackState.Idle.shortcutTrack())
        assertNull(PlaybackState.Error(track(), "no stream").shortcutTrack())
        assertNull(null.shortcutTrack())
    }
}
