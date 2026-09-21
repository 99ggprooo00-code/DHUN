package dev.dhun.player

import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import kotlin.random.Random

/**
 * Platform-independent queue logic: order, shuffle, repeat, add/remove/
 * reorder, next/previous. Pure domain — no player engine, no coroutines,
 * no platform types. Every platform's DhunPlayer drives THIS for queue
 * decisions, so behavior is identical on Android and Desktop and fully
 * unit-testable.
 */
class QueueManager(private val random: Random = Random.Default) {

    private val items = mutableListOf<Track>()
    private var playOrder = mutableListOf<Int>() // positions in [items] when shuffled
    private var orderCursor = -1                 // index into playOrder
    private var currentIndexInItems = -1         // index into items

    var repeatMode: RepeatMode = RepeatMode.OFF
        private set
    var shuffleEnabled: Boolean = false
        private set

    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    val current: Track? get() = items.getOrNull(currentIndexInItems)
    val currentIndex: Int get() = currentIndexInItems

    /** Tracks after the current play-order position (what "up next" shows). */
    val upcoming: List<Track>
        get() {
            if (isEmpty) return emptyList()
            return playOrder.drop(orderCursor + 1).mapNotNull { items.getOrNull(it) }
        }

    val snapshot: List<Track> get() = items.toList()

    /* ---------------- queue construction ---------------- */

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        items.clear()
        items += tracks
        shuffleEnabled = false
        repeatMode = RepeatMode.OFF
        currentIndexInItems = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.size - 1)
        rebuildOrder()
    }

    fun playAt(index: Int): Track? {
        if (index !in items.indices) return null
        currentIndexInItems = index
        orderCursor = playOrder.indexOf(index).takeIf { it >= 0 } ?: index
        return items[index]
    }

    /** Insert right after the currently playing track ("play next"). */
    fun addNext(track: Track) {
        if (items.isEmpty()) {
            addToQueue(track)
            return
        }
        val insertAt = currentIndexInItems + 1
        items.add(insertAt, track)
        // Order-preserving splice: entries referencing shifted source positions
        // move +1, and the new track plays directly after the current one —
        // "play next" under shuffle too (no rebuild, no re-shuffle).
        for (i in playOrder.indices) if (playOrder[i] >= insertAt) playOrder[i]++
        playOrder.add(orderCursor + 1, insertAt)
        orderCursor = playOrder.indexOf(currentIndexInItems)
    }

    fun addToQueue(track: Track) {
        val wasEmpty = items.isEmpty()
        val appendedAt = items.size
        items += track
        if (wasEmpty) {
            currentIndexInItems = 0
            orderCursor = 0
            rebuildOrder()
        } else {
            // Append = the END of the playback order, shuffled or not.
            playOrder.add(appendedAt)
        }
    }

    fun removeAt(index: Int): Boolean {
        if (index !in items.indices) return false
        val removingCurrent = index == currentIndexInItems
        items.removeAt(index)
        if (items.isEmpty()) {
            currentIndexInItems = -1
            orderCursor = -1
            playOrder.clear()
            return true
        }
        // Order-preserving removal: drop the entry from the play order and
        // close the gap — the rest of the (possibly shuffled) order is kept
        // exactly as the user saw it.
        val orderPos = playOrder.indexOf(index)
        if (orderPos >= 0) playOrder.removeAt(orderPos)
        for (i in playOrder.indices) if (playOrder[i] > index) playOrder[i]--
        when {
            removingCurrent -> {
                // Advance to whatever now occupies the removed slot in PLAY
                // order ("removing the playing entry advances"); identity
                // order reproduces the old source-position semantics.
                currentIndexInItems = (playOrder.getOrNull(orderPos) ?: index)
                    .coerceIn(0, items.size - 1)
            }
            index < currentIndexInItems -> currentIndexInItems--
        }
        orderCursor = playOrder.indexOf(currentIndexInItems).coerceAtLeast(0)
        return true
    }

    /** @return true if the move happened. */
    fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val track = items.removeAt(from)
        items.add(to, track)
        currentIndexInItems = items.indexOfFirst { it.id == current?.id }
        rebuildOrder()
        orderCursor = playOrder.indexOf(currentIndexInItems).coerceAtLeast(0)
        return true
    }

    /* ---------------- shuffle / repeat ---------------- */

    fun toggleShuffle(): Boolean {
        shuffleEnabled = !shuffleEnabled
        rebuildOrder()
        orderCursor = playOrder.indexOf(currentIndexInItems).coerceAtLeast(0)
        return shuffleEnabled
    }

    /** Idempotent counterpart to [toggleShuffle]; safe to call with the current value. */
    fun setShuffle(enabled: Boolean): Boolean {
        if (shuffleEnabled == enabled) return shuffleEnabled
        return toggleShuffle()
    }

    /**
     * The order the user sees (and the queue tab renders): the shuffled play
     * order — current track head — when shuffle is on, source order otherwise.
     * Playback follows this list on every platform, so a row's position here
     * is exactly what the [playAtDisplay]/[removeAtDisplay]/[moveInDisplay]
     * indices mean.
     */
    val displayQueue: List<Track>
        get() = if (shuffleEnabled) {
            playOrder.mapNotNull { items.getOrNull(it) }
        } else {
            items.toList()
        }

    /** Index of the current track inside [displayQueue]; -1 when empty. */
    val displayCurrentIndex: Int
        get() = if (isEmpty) -1 else displayQueue.indexOfFirst { it.id == current?.id }.takeIf { it >= 0 } ?: 0

    /** [playAt] in display space: plays the track the user sees at [index]. */
    fun playAtDisplay(index: Int): Track? {
        val source = displaySourceIndex(index) ?: return null
        return playAt(source)
    }

    /** [removeAt] in display space: removes the row the user sees at [index]. */
    fun removeAtDisplay(index: Int): Boolean {
        val source = displaySourceIndex(index) ?: return false
        return removeAt(source)
    }

    /**
     * Drag-reorder in display space. Shuffle off this reorders the source
     * queue (display == source). Shuffle on this permutes the shuffled play
     * order EXACTLY as dragged — no re-shuffle — so what the user arranged is
     * what will play.
     */
    fun moveInDisplay(from: Int, to: Int): Boolean {
        if (from !in displayQueue.indices || to !in displayQueue.indices || from == to) return false
        if (!shuffleEnabled) return move(from, to)
        playOrder.add(to, playOrder.removeAt(from))
        orderCursor = playOrder.indexOf(currentIndexInItems).coerceAtLeast(0)
        return true
    }

    /**
     * Re-point the current track to [trackId] WITHOUT touching the play
     * order — for engines whose timeline advances itself (Android/Media3):
     * keeps the visible highlight in step with what is actually sounding
     * after a natural advance, engine-side next/previous, or a service-side
     * seek. No-op when the id is unknown or already current.
     *
     * @return true when the id was found in the queue.
     */
    fun syncCurrent(trackId: String): Boolean {
        val idx = items.indexOfFirst { it.id == trackId }
        if (idx < 0) return false
        if (idx == currentIndexInItems) return true
        currentIndexInItems = idx
        orderCursor = playOrder.indexOf(idx).coerceAtLeast(0)
        return true
    }

    /** Map a display position to its source ([items]) index; null out of bounds. */
    private fun displaySourceIndex(displayIndex: Int): Int? {
        if (displayIndex !in displayQueue.indices) return null
        return if (shuffleEnabled) playOrder.getOrNull(displayIndex) else displayIndex
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    /* ---------------- navigation ---------------- */

    /**
     * Inspect the next track without advancing the queue cursor.
     */
    fun peekNext(trackEnded: Boolean = false): Track? {
        if (isEmpty) return null
        if (trackEnded && repeatMode == RepeatMode.ONE) return current
        if (orderCursor + 1 < playOrder.size) {
            val nextIndex = playOrder[orderCursor + 1]
            return items.getOrNull(nextIndex)
        }
        return when (repeatMode) {
            RepeatMode.ALL -> {
                val firstIndex = playOrder.firstOrNull() ?: -1
                items.getOrNull(firstIndex)
            }
            else -> null
        }
    }

    /**
     * Advance. [trackEnded] true when the current track finished naturally
     * (RepeatMode.ONE replays the same track); false when the user pressed
     * next (RepeatMode.ONE skips like normal).
     */
    fun next(trackEnded: Boolean = false): Track? {
        if (isEmpty) return null
        if (trackEnded && repeatMode == RepeatMode.ONE) return current
        if (orderCursor + 1 < playOrder.size) {
            orderCursor++
            currentIndexInItems = playOrder[orderCursor]
            return current
        }
        // end of queue
        return when (repeatMode) {
            RepeatMode.ALL -> {
                orderCursor = 0
                currentIndexInItems = playOrder[orderCursor]
                current
            }
            else -> null
        }
    }

    fun previous(): Track? {
        if (isEmpty) return null
        if (orderCursor > 0) {
            orderCursor--
            currentIndexInItems = playOrder[orderCursor]
            return current
        }
        return when (repeatMode) {
            RepeatMode.ALL -> {
                orderCursor = playOrder.size - 1
                currentIndexInItems = playOrder[orderCursor]
                current
            }
            else -> current // already first: "previous" restarts the track
        }
    }

    /* ---------------- internals ---------------- */

    private fun rebuildOrder() {
        playOrder = if (shuffleEnabled) {
            if (items.isEmpty()) {
                mutableListOf()
            } else {
                val head = currentIndexInItems.takeIf { it in items.indices }
                val rest = items.indices.filter { it != head }.shuffled(random)
                (listOfNotNull(head) + rest).toMutableList()
            }
        } else {
            MutableList(items.size) { it }
        }
        orderCursor = if (playOrder.isEmpty()) -1 else playOrder.indexOf(currentIndexInItems).coerceAtLeast(0)
    }
}
