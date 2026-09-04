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
 *
 * Phase 08 additions (FullPlayer / Queue UI):
 *  - [currentQueueIndex], [repeatMode], [shuffleEnabled], [volume] flows so
 *    the UI can render live state instead of poking setters blindly.
 *  - Queue mutation by index — [playAt], [removeFromQueue], [moveInQueue]
 *    power tap-to-jump, swipe-remove and drag-reorder. All indices refer to
 *    the *visual* queue ([queue]) order; when shuffle is on the engine maps
 *    them to its internal play order.
 */
interface DhunPlayer {
    val state: StateFlow<PlaybackState>
    val currentTrack: StateFlow<Track?>
    val queue: StateFlow<List<Track>>
    /** Index of the playing track inside [queue]; -1 when the queue is empty. */
    val currentQueueIndex: StateFlow<Int>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val repeatMode: StateFlow<RepeatMode>
    val shuffleEnabled: StateFlow<Boolean>
    /** Audio volume 0f..1f. */
    val volume: StateFlow<Float>

    /**
     * Loads [tracks] as the queue positioned at [startIndex]. With
     * [playWhenReady] = true (the default) playback starts immediately;
     * false loads the queue paused — used when restoring the last session.
     */
    suspend fun prepareQueue(tracks: List<Track>, startIndex: Int = 0, playWhenReady: Boolean = true)

    /** Inserts [track] immediately after the currently playing track. */
    fun addNext(track: Track)

    /** Appends [track] to the end of the current queue. */
    fun addToQueue(track: Track)

    /** Jumps to the queue entry at [index] and plays it. No-op out of bounds. */
    fun playAt(index: Int)

    /** Removes the queue entry at [index]; if it was playing, advances. */
    fun removeFromQueue(index: Int)

    /** Moves the queue entry at [from] to position [to] (drag-reorder). */
    fun moveInQueue(from: Int, to: Int)

    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)
    fun setVolume(volume: Float)
    fun stop()
}
