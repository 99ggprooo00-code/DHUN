package dev.dhun.ui.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import dev.dhun.core.PlaybackState
import dev.dhun.core.Track
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunSpacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerControlStateTest {
    private val track = Track("track", "A song", "An artist")

    @Test
    fun failedPlaybackIsLabeledAndDrawnAsRetryRatherThanPlay() {
        val failed = PlaybackState.Error(track, "Could not play")
        assertEquals("Retry playback", playbackActionLabel(failed))
        assertEquals(DhunIcon.Refresh, playbackActionIcon(failed))
        assertEquals("Pause", playbackActionLabel(PlaybackState.Playing(track)))
        assertEquals(DhunIcon.Pause, playbackActionIcon(PlaybackState.Playing(track)))
        assertEquals("Play", playbackActionLabel(PlaybackState.Paused(track)))
        assertEquals(DhunIcon.Play, playbackActionIcon(PlaybackState.Paused(track)))
    }

    @Test
    fun busyLabelsStayDistinctFromErrorsAndNormalPlayback() {
        assertEquals("Resolving…", playbackBusyLabel(PlaybackState.Resolving(track)))
        assertEquals("Buffering…", playbackBusyLabel(PlaybackState.Buffering(track)))
        assertEquals("Reconnecting…", playbackBusyLabel(PlaybackState.Recovering(track)))
        assertNull(playbackBusyLabel(PlaybackState.Playing(track)))
        assertNull(playbackBusyLabel(PlaybackState.Paused(track)))
        assertNull(playbackBusyLabel(PlaybackState.Error(track, "Failed")))
        assertNull(playbackBusyLabel(PlaybackState.Idle))
    }

    @Test
    fun keyboardActivationKeysExcludeNavigationAndEscape() {
        listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter).forEach {
            assertTrue(isTransportActivationKey(it))
        }
        listOf(Key.DirectionLeft, Key.DirectionRight, Key.Tab, Key.Escape).forEach {
            assertFalse(isTransportActivationKey(it))
        }
    }

    @Test
    fun miniPlayerOnlyExpandsForACompletedUpwardThreshold() {
        assertTrue(shouldExpandMiniPlayer(-48f, 48f))
        assertTrue(shouldExpandMiniPlayer(-120f, 48f))
        assertFalse(shouldExpandMiniPlayer(-47f, 48f))
        assertFalse(shouldExpandMiniPlayer(120f, 48f))
        assertFalse(shouldExpandMiniPlayer(0f, 48f))
        assertFalse(shouldExpandMiniPlayer(-120f, 0f))
        assertFalse(shouldExpandMiniPlayer(Float.NaN, 48f))
        assertFalse(shouldExpandMiniPlayer(-120f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun allSixTransportTargetsFitNormalPhoneAndDesktopWidths() {
        listOf(300.dp, 320.dp, 360.dp, 400.dp).forEach { width ->
            val metrics = playerTransportMetrics(width)
            assertTrue(metrics.minimumWidth <= width, "Transport must fit $width")
            assertTrue(metrics.playSize >= DhunSpacing.touchTarget)
            assertEquals(
                DhunSpacing.touchTarget * 5 + metrics.playSize + metrics.horizontalPadding * 2,
                metrics.minimumWidth,
            )
        }
        assertEquals(DhunSpacing.transportTarget, playerTransportMetrics(300.dp).playSize)
        assertEquals(DhunSpacing.miniPlayerHeight, playerTransportMetrics(400.dp).playSize)
    }

    @Test
    fun exceptionallyNarrowTransportScrollsInsteadOfShrinkingFavoriteOrRepeat() {
        val metrics = playerTransportMetrics(240.dp)
        assertTrue(metrics.minimumWidth > 240.dp)
        assertTrue(metrics.playSize >= DhunSpacing.touchTarget)
        assertEquals(DhunSpacing.touchTarget * 5, metrics.minimumWidth - metrics.playSize - metrics.horizontalPadding * 2)
    }
}
