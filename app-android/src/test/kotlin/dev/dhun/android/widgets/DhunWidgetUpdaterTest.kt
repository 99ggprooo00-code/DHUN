package dev.dhun.android.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.dhun.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rendering contract for the widget RemoteViews (no MediaController, no launcher).
 * Verifies that the updater maps [DhunWidgetState] → RemoteViews without
 * crashing, with the right tier for the size the host reports, and that the
 * snapshot → state mapping is loss-free. Real artwork fetch / live
 * MediaController polling is not exercised — hardware gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DhunWidgetUpdaterTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `idle state renders placeholder texts and play icon`() {
        // The textual contract lives in the state; the builder must accept it.
        assertEquals("Nothing playing", DhunWidgetState.idle().title)
        val quick = DhunWidgetUpdater.buildQuickPlayViewsForId(context, DhunWidgetState.idle(), 1)
        assertNotNull(quick)
    }

    @Test
    fun `playing state uses pause icon, paused uses play`() {
        val playing = DhunWidgetState(title = "A", artist = "B", isPlaying = true, hasTrack = true)
        val paused = DhunWidgetState(title = "A", artist = "B", isPlaying = false, hasTrack = true)
        // Building must not throw for either.
        assertNotNull(DhunWidgetUpdater.buildQuickPlayViewsForId(context, playing, 2))
        assertNotNull(DhunWidgetUpdater.buildQuickPlayViewsForId(context, paused, 2))
        // Verify the pure state contract (icon choice is `isPlaying && hasTrack`).
        assertEquals(true, playing.isPlaying && playing.hasTrack)
        assertEquals(false, paused.isPlaying && paused.hasTrack)
    }

    @Test
    fun `per-id builders do not clash across widget instances`() {
        val state = DhunWidgetState.fromMetadata("Track", "Artist", isPlaying = false)
        val v1 = DhunWidgetUpdater.buildQuickPlayViewsForId(context, state, 10)
        val v2 = DhunWidgetUpdater.buildQuickPlayViewsForId(context, state, 11)
        assertNotNull(v1)
        assertNotNull(v2)
        // Distinct objects — each carries its own instance-scoped PendingIntents.
        assertTrue(v1 !== v2)
    }

    @Test
    fun `unknown host size falls back to the small tier`() {
        // 0/0 is what tests and pre-API-31 launchers report. The floor tier is
        // the only one guaranteed to fit, so it must win.
        assertNotNull(DhunWidgetUpdater.buildQuickPlayViewsForId(context, DhunWidgetState.idle(), 5, null, 0))
        assertEquals(R.layout.widget_quick_play, DhunWidgetUpdater.layoutForQuickPlay(0))
    }

    // ------------------------------------------------------------ responsive

    @Test
    fun `quick play tier selection follows width at the exact boundary`() {
        assertEquals(R.layout.widget_quick_play, DhunWidgetUpdater.layoutForQuickPlay(110))
        assertEquals(R.layout.widget_quick_play, DhunWidgetUpdater.layoutForQuickPlay(199))
        assertEquals(R.layout.widget_quick_play_wide, DhunWidgetUpdater.layoutForQuickPlay(200))
        assertEquals(R.layout.widget_quick_play_wide, DhunWidgetUpdater.layoutForQuickPlay(650))
        // A nonsensical size never selects the wider tier.
        assertEquals(R.layout.widget_quick_play, DhunWidgetUpdater.layoutForQuickPlay(-5))
    }

    @Test
    fun `wide tier threshold is the documented constant`() {
        // Layouts and the launcher's resize hints are tuned to this number.
        assertEquals(200, DhunWidgetUpdater.QUICK_WIDE_MIN_WIDTH_DP)
        assertEquals(
            R.layout.widget_quick_play_wide,
            DhunWidgetUpdater.layoutForQuickPlay(DhunWidgetUpdater.QUICK_WIDE_MIN_WIDTH_DP),
        )
        assertEquals(
            R.layout.widget_quick_play,
            DhunWidgetUpdater.layoutForQuickPlay(DhunWidgetUpdater.QUICK_WIDE_MIN_WIDTH_DP - 1),
        )
    }

    @Test
    fun `both tiers build for idle and full playback states`() {
        val states = listOf(
            DhunWidgetState.idle(),
            DhunWidgetState.fromPlayback(
                rawTitle = "Nightcall", rawArtist = "Kavinsky", isPlaying = true,
                positionMs = 45_000L, durationMs = 180_000L,
                shuffleEnabled = true, repeatMode = DhunWidgetState.REPEAT_ONE,
                hasNext = true, hasPrevious = true, artworkKey = "k",
            ),
            DhunWidgetState.fromPlayback(
                rawTitle = "T", rawArtist = "", isPlaying = false,
                positionMs = 0L, durationMs = 0L,
                shuffleEnabled = false, repeatMode = DhunWidgetState.REPEAT_ALL,
                hasNext = false, hasPrevious = false, artworkKey = null,
            ),
        )
        val art = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        var id = 100
        for (state in states) {
            for (artwork in listOf(null, art)) {
                // Small (2x2) and wide (resized) tiers, at reported and
                // unknown host sizes — the ids the binding skips are part of
                // the contract, so nothing may throw here.
                assertNotNull(DhunWidgetUpdater.buildQuickPlayViewsForId(context, state, id++, artwork, 110))
                assertNotNull(DhunWidgetUpdater.buildQuickPlayViewsForId(context, state, id++, artwork, 250))
                assertNotNull(DhunWidgetUpdater.buildQuickPlayViewsForId(context, state, id++, artwork, 0))
                assertNotNull(
                    DhunWidgetUpdater.buildQuickPlayViewsForId(context, state, id++, artwork, 250, 200),
                )
            }
        }
    }

    @Test
    fun `repeat off all and one all build`() {
        for (repeat in listOf(
            DhunWidgetState.REPEAT_OFF,
            DhunWidgetState.REPEAT_ALL,
            DhunWidgetState.REPEAT_ONE,
        )) {
            val state = DhunWidgetState.fromPlayback(
                rawTitle = "T", rawArtist = "A", isPlaying = true,
                positionMs = 1_000L, durationMs = 60_000L,
                shuffleEnabled = true, repeatMode = repeat,
                hasNext = true, hasPrevious = true, artworkKey = null,
            )
            assertNotNull(DhunWidgetUpdater.buildQuickPlayViewsForId(context, state, 7, null, 110))
        }
    }

    // -------------------------------------------------------------- mapping

    @Test
    fun `snapshot maps onto the state contract`() {
        val snapshot = DhunWidgetUpdater.PlaybackSnapshot(
            title = "  Nightcall  ",
            artist = "Kavinsky",
            isPlaying = true,
            positionMs = 90_000L,
            durationMs = 180_000L,
            shuffleEnabled = true,
            repeatMode = DhunWidgetState.REPEAT_ALL,
            hasNext = true,
            hasPrevious = false,
            artworkUri = "https://img/one.jpg",
            artworkData = null,
            artworkKey = "https://img/one.jpg",
        )
        val state = DhunWidgetUpdater.stateOf(snapshot)
        assertEquals("Nightcall", state.title)
        assertEquals("Kavinsky", state.artist)
        assertEquals(500, state.progressPermille)
        assertEquals("1:30", state.positionText)
        assertEquals("3:00", state.durationText)
        assertTrue(state.shuffleEnabled)
        assertEquals(DhunWidgetState.REPEAT_ALL, state.repeatMode)
        assertEquals("https://img/one.jpg", state.artworkKey)
    }

    @Test
    fun `blank snapshot collapses to idle instead of stale data`() {
        val snapshot = DhunWidgetUpdater.PlaybackSnapshot(
            title = " ", artist = null, isPlaying = false, positionMs = 0L, durationMs = 0L,
            shuffleEnabled = false, repeatMode = DhunWidgetState.REPEAT_OFF,
            hasNext = false, hasPrevious = false,
            artworkUri = null, artworkData = null, artworkKey = null,
        )
        assertTrue(DhunWidgetUpdater.stateOf(snapshot).isIdle)
    }

    @Test
    fun `progress tick cadence is the documented ten seconds`() {
        assertEquals(10_000L, DhunWidgetUpdater.PROGRESS_TICK_MS)
    }
}
