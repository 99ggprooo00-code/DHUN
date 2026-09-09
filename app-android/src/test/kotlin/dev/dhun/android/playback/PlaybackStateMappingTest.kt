package dev.dhun.android.playback

import androidx.media3.common.Player
import dev.dhun.core.DhunError
import dev.dhun.core.PlaybackState
import dev.dhun.core.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Branch-ordering contract of the playback-state mapping
 * (playback-diagnostics session, 2026-09-09). The fast-fail branch must
 * sit BELOW engine error, in-flight recovery and actual playback (the
 * offline cache-span replay path resolves-to-failure yet plays), and must
 * respect the buffering grace. Everything else must map exactly as before
 * the extraction — this is the regression gate for
 * "starts but stays buffering / never plays audio".
 */
class PlaybackStateMappingTest {

    private val track = Track(id = "v1", title = "Song", artistName = "Artist")
    private val gated = DhunError.AuthRequired(
        detail = "web_remix=AUTH_REQUIRED(Sign in to confirm you're not a bot)",
    )
    private val wellPastGrace = FAST_FAIL_GRACE_MS + 1
    private val withinGrace = FAST_FAIL_GRACE_MS - 1

    private fun map(
        track: Track? = this.track,
        engineError: Boolean = false,
        engineErrorMessage: String = "SourceError",
        recoveryActive: Boolean = false,
        isPlaying: Boolean = false,
        playbackState: Int = Player.STATE_BUFFERING,
        mediaItemCount: Int = 1,
        terminalResolveError: DhunError? = null,
        bufferingForMs: Long = wellPastGrace,
    ): PlaybackState = mapPlaybackState(
        track = track,
        engineError = engineError,
        engineErrorMessage = engineErrorMessage,
        recoveryActive = recoveryActive,
        isPlaying = isPlaying,
        playbackState = playbackState,
        mediaItemCount = mediaItemCount,
        terminalResolveError = terminalResolveError,
        bufferingForMs = bufferingForMs,
    )

    /* ---------------- pre-existing behavior, unchanged ---------------- */

    @Test
    fun `engine error surfaces as Error with the full chain`() {
        val state = map(engineError = true, engineErrorMessage = "ERROR_CODE_IO_BAD_HTTP_STATUS ← 403")
        assertTrue(state is PlaybackState.Error)
        state as PlaybackState.Error
        assertEquals("ERROR_CODE_IO_BAD_HTTP_STATUS ← 403", state.message)
    }

    @Test
    fun `in-flight recovery outranks everything below it`() {
        val state = map(recoveryActive = true, terminalResolveError = gated)
        assertTrue(state is PlaybackState.Recovering)
    }

    @Test
    fun `engine error defers to active recovery as before`() {
        val state = map(engineError = true, recoveryActive = true)
        assertTrue(state is PlaybackState.Recovering)
    }

    @Test
    fun `playing wins even with a terminal verdict on file`() {
        // Offline cache-span replay: resolve failed (terminal recorded) but
        // PlaybackGraph serves cached bytes and the track plays.
        val state = map(isPlaying = true, terminalResolveError = gated)
        assertTrue(state is PlaybackState.Playing)
    }

    @Test
    fun `plain buffering stays buffering without a terminal verdict`() {
        val state = map(terminalResolveError = null)
        assertTrue(state is PlaybackState.Buffering)
    }

    @Test
    fun `ready maps to paused`() {
        assertTrue(map(playbackState = Player.STATE_READY) is PlaybackState.Paused)
    }

    @Test
    fun `restored not-prepared queue maps to paused not idle`() {
        val state = map(playbackState = Player.STATE_ENDED)
        assertTrue(state is PlaybackState.Paused)
    }

    @Test
    fun `empty idle player maps to idle`() {
        val state = map(track = null, playbackState = Player.STATE_IDLE, mediaItemCount = 0)
        assertTrue(state is PlaybackState.Idle)
    }

    /* ---------------- the new fast-fail branch ---------------- */

    @Test
    fun `terminal verdict past the grace fast-fails buffering into a typed error`() {
        val state = map(terminalResolveError = gated, bufferingForMs = wellPastGrace)
        assertTrue(state is PlaybackState.Error)
        state as PlaybackState.Error
        assertEquals("This content needs a signed-in session.", state.message)
        assertEquals(gated.detail, state.detail)
    }

    @Test
    fun `terminal verdict inside the grace keeps buffering`() {
        // Local cache-span replay reaches READY in ~1-2s — far inside the
        // grace — so it must never flash an error while its resolve verdict
        // lands.
        val state = map(terminalResolveError = gated, bufferingForMs = withinGrace)
        assertTrue(state is PlaybackState.Buffering)
    }

    @Test
    fun `transient verdicts never fast-fail`() {
        val state = map(terminalResolveError = DhunError.Network(detail = "timeout"))
        assertTrue(state is PlaybackState.Buffering)
    }

    @Test
    fun `unavailable fast-fails with its own human message`() {
        val state = map(terminalResolveError = DhunError.Unavailable(detail = "region"))
        assertTrue(state is PlaybackState.Error)
        assertEquals("This track isn't available right now.", (state as PlaybackState.Error).message)
    }

    @Test
    fun `fast-fail without a known track still errors with a null track`() {
        val state = map(track = null, terminalResolveError = gated)
        assertTrue(state is PlaybackState.Error)
        assertEquals(null, (state as PlaybackState.Error).track)
    }

    @Test
    fun `fast-fail only applies while actually buffering`() {
        val state = map(
            terminalResolveError = gated,
            playbackState = Player.STATE_READY,
        )
        assertTrue(state is PlaybackState.Paused)
    }
}
