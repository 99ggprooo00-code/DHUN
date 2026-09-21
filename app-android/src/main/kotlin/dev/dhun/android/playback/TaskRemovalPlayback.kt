package dev.dhun.android.playback

import androidx.media3.common.Player

/**
 * Explicit task dismissal stops even a buffering/play-when-ready engine.
 * Keep the queue intact for session persistence. Stopping the service alone
 * is insufficient while a controller is still bound, so silence it first.
 * Called only from Service.onTaskRemoved, never from Activity lifecycle hooks.
 */
internal fun stopPlaybackOnTaskRemoval(player: Player?, stopService: () -> Unit) {
    try {
        player?.pause()
        player?.stop()
    } finally {
        stopService()
    }
}
