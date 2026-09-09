package dev.dhun.android.playback

import androidx.media3.common.Player
import dev.dhun.core.DhunError
import dev.dhun.core.PlaybackState
import dev.dhun.core.Track
import dev.dhun.core.detailString
import dev.dhun.core.toUserMessage

/**
 * Pure mapping from a player snapshot to the shared [PlaybackState] —
 * extracted from `AndroidDhunPlayer.refresh()` (playback-diagnostics
 * session, 2026-09-09) so the branch ordering is unit-testable without a
 * device, a looper, or an engine.
 *
 * Branch order is load-bearing; the fast-fail branch sits **after**
 * `isPlaying` and the recovery signal so it can never override:
 * 1. a surfaced engine error (`playerError`),
 * 2. in-flight mid-stream 403 recovery (`Recovering`), or
 * 3. a track that actually started playing — notably the offline
 *    cache-span replay path, where the resolve fails (terminal verdict
 *    recorded!) but `PlaybackGraph` serves cached bytes and playback
 *    succeeds. Only a player genuinely parked in `STATE_BUFFERING` with a
 *    fresh terminal verdict fast-fails.
 *
 * The [bufferingForMs] grace additionally protects the cache-replay case:
 * spans reach `READY` in ~1-2s locally, well inside the grace, so a
 * replaying track never flashes an error while its terminal resolve verdict
 * lands.
 */
internal fun mapPlaybackState(
    track: Track?,
    engineError: Boolean,
    engineErrorMessage: String,
    recoveryActive: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    mediaItemCount: Int,
    terminalResolveError: DhunError?,
    bufferingForMs: Long,
): PlaybackState {
    if (engineError && !recoveryActive) {
        return PlaybackState.Error(track, engineErrorMessage)
    }
    if (recoveryActive && track != null) {
        return PlaybackState.Recovering(track)
    }
    if (isPlaying) {
        return PlaybackState.Playing(track ?: UNKNOWN)
    }
    if (playbackState == Player.STATE_BUFFERING) {
        if (terminalResolveError != null && bufferingForMs >= FAST_FAIL_GRACE_MS) {
            // Terminal resolve verdict (every identity said no — bot-gating /
            // unplayable). The engine's bounded retries will keep churning
            // underneath for minutes; the user must not wait them out in a
            // fake "buffering" state. Human headline + technical evidence.
            return PlaybackState.Error(
                track = track,
                message = terminalResolveError.toUserMessage(),
                detail = terminalResolveError.detailString(),
            )
        }
        return PlaybackState.Buffering(track ?: UNKNOWN)
    }
    if (playbackState == Player.STATE_READY) {
        return PlaybackState.Paused(track ?: UNKNOWN)
    }
    // Restored-but-not-prepared queue (playWhenReady=false before buffering)
    // is still a paused session, not an idle player.
    if (mediaItemCount > 0 && playbackState != Player.STATE_IDLE) {
        return PlaybackState.Paused(track ?: UNKNOWN)
    }
    return PlaybackState.Idle
}

/** How long the player must sit in STATE_BUFFERING before a terminal
 *  resolve verdict fast-fails into Error. Local cache-span replay reaches
 *  READY far faster; remote resolves that fail take far longer. */
internal const val FAST_FAIL_GRACE_MS: Long = 8_000L

private val UNKNOWN = Track(id = "", title = "Unknown", artistName = "")
