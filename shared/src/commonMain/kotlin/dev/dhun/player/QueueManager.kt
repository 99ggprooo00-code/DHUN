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
        rebuildOrder()
        orderCursor = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        currentIndexInItems = if (items.isEmpty()) -1 else orderCursor
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
        items.add(currentIndexInItems + 1, track)
        rebuildOrder()
        // keep cursor on current; find the inserted track's order position
        orderCursor = playOrder.indexOf(currentIndexInItems)
    }

    fun addToQueue(track: Track) {
        val wasEmpty = items.isEmpty()
        items += track
        rebuildOrder()
        if (wasEmpty) {
            orderCursor = 0
            currentIndexInItems = 0
        } else {
            orderCursor = playOrder.indexOf(currentIndexInItems)
        }
    }

    fun removeAt(index: Int): Boolean {
        if (index !in items.indices) return false
        val removingCurrent = index == currentIndexInItems
        items.removeAt(index)
        when {
            items.isEmpty() -> {
                currentIndexInItems = -1
                orderCursor = -1
            }
            else -> {
                // shift: current moved left if we removed before it
                if (index < currentIndexInItems) currentIndexInItems--
                if (removingCurrent) {
                    currentIndexInItems = currentIndexInItems.coerceAtMost(items.size - 1)
                }
                rebuildOrder()
                orderCursor = playOrder.indexOf(currentIndexInItems).coerceAtLeast(0)
            }
        }
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

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    /* ---------------- navigation ---------------- */

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
