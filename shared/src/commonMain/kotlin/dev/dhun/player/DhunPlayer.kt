package dev.dhun.player

import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform player abstraction (shared). Each platform implements it around
 * its playback engine: Android = Media3 MediaController, Desktop = vlcj
 * (Phase 04). Resolution happens inside the platform implementation —
 * callers only ever see [Track]s in and flows out.
 */
interface DhunPlayer {
    val state: StateFlow<PlaybackState>
    val currentTrack: StateFlow<Track?>
    val queue: StateFlow<List<Track>>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    /** Loads [tracks] as the queue and starts playing at [startIndex]. */
    suspend fun prepareQueue(tracks: List<Track>, startIndex: Int = 0)

    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)
    fun stop()
}
