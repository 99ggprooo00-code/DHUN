package dev.dhun.player

import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueManagerTest {

    private fun track(id: String) = Track(id = id, title = "t$id", artistName = "a$id")

    private fun queueOf(vararg ids: String): QueueManager = QueueManager().apply {
        setQueue(ids.map(::track))
    }

    /* ---------------- construction ---------------- */

    @Test
    fun setQueueSetsCurrentToStartIndex() {
        val q = queueOf("a", "b", "c")
        assertEquals(3, q.size)
        assertEquals("a", q.current?.id)
        q.setQueue(listOf(track("x"), track("y"), track("z")), startIndex = 2)
        assertEquals("z", q.current?.id)
    }

    @Test
    fun emptyQueueIsSafe() {
        val q = QueueManager()
        assertTrue(q.isEmpty)
        assertNull(q.current)
        assertNull(q.next())
        assertNull(q.previous())
        assertEquals(0, q.upcoming.size)
        q.removeAt(0)
        assertNull(q.playAt(3))
    }

    @Test
    fun singleTrackQueueNextWithRepeatOffReturnsNull() {
        val q = queueOf("only")
        assertEquals("only", q.current?.id)
        assertNull(q.next())
    }

    /* ---------------- linear navigation ---------------- */

    @Test
    fun nextAndPreviousWalkLinearly() {
        val q = queueOf("a", "b", "c")
        assertEquals("b", q.next()?.id)
        assertEquals("c", q.next()?.id)
        assertNull(q.next()) // end, repeat off
        assertEquals("b", q.previous()?.id)
        assertEquals("a", q.previous()?.id)
        assertEquals("a", q.previous()?.id) // already first: restart current
    }

    @Test
    fun repeatAllWrapsBothDirections() {
        val q = queueOf("a", "b", "c")
        q.setRepeatMode(RepeatMode.ALL)
        q.next(); q.next()
        assertEquals("c", q.current?.id)
        assertEquals("a", q.next()?.id) // wrap forward
        assertEquals("c", q.previous()?.id) // wrap backward
    }

    @Test
    fun repeatOneReplaysOnNaturalEndButSkipsOnUserNext() {
        val q = queueOf("a", "b")
        q.setRepeatMode(RepeatMode.ONE)
        assertEquals("a", q.next(trackEnded = true)?.id) // replay
        assertEquals("a", q.next(trackEnded = true)?.id) // still replay
        assertEquals("b", q.next(trackEnded = false)?.id) // user next advances
        assertNull(q.next(trackEnded = false)) // end, repeat ONE does not wrap the queue
    }

    /* ---------------- shuffle ---------------- */

    @Test
    fun shuffleKeepsCurrentFirstAndPreservesAllTracks() {
        val q = QueueManager(Random(42)).apply { setQueue((1..20).map { track("t$it") }) }
        q.next() // move to t2, then shuffle: current must stay first
        q.toggleShuffle()
        assertTrue(q.shuffleEnabled)
        assertEquals(q.current?.id, q.upcoming.let { "t2" }) // current is t2 and not upcoming
        val orderIds = listOf(q.current) + q.upcoming
        assertEquals(20, orderIds.size)
        assertEquals(20, orderIds.map { it?.id }.toSet().size) // all distinct
    }

    @Test
    fun shuffleOffRestoresSequentialOrder() {
        val q = QueueManager(Random(7)).apply { setQueue((1..6).map { track("t$it") }) }
        q.toggleShuffle()
        q.toggleShuffle()
        assertFalse(q.shuffleEnabled)
        q.playAt(0)
        assertEquals("t2", q.next()?.id)
        assertEquals("t3", q.next()?.id)
    }

    @Test
    fun shuffleNextExhaustsThenRepeatAllWraps() {
        val q = QueueManager(Random(42)).apply { setQueue(listOf(track("a"), track("b"), track("c"))) }
        q.toggleShuffle()
        var visited = 0
        while (q.next() != null) visited++
        assertEquals(2, visited) // 3 tracks: next from first visits 2 more, then null (repeat off)
        q.setRepeatMode(RepeatMode.ALL)
        assertTrue(q.next() != null) // wraps instead of ending
    }

    /* ---------------- setShuffle + visible (display) order ---------------- */

    /** Deterministic random: every nextInt(bound) is 0, so stdlib shuffled() always swaps toward index 0. */
    private class ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    /** 8-track queue positioned at index 3 with shuffle on — the shared fixture for display-order tests. */
    private fun shuffledQueueAt3(): QueueManager = QueueManager(ZeroRandom()).apply {
        setQueue((1..8).map { track("t$it") })
        playAt(3)
        setShuffle(true)
    }

    @Test
    fun setShuffleIsIdempotentAndToggleIsInverse() {
        val q = QueueManager(Random(42)).apply { setQueue(listOf(track("a"), track("b"), track("c"))) }
        assertFalse(q.shuffleEnabled)
        assertTrue(q.setShuffle(true))
        assertTrue(q.shuffleEnabled)
        // second call with same value is a no-op
        assertTrue(q.setShuffle(true))
        assertTrue(q.shuffleEnabled)
        assertFalse(q.setShuffle(false))
        assertFalse(q.shuffleEnabled)
        assertFalse(q.setShuffle(false))
        // toggle still flips
        assertTrue(q.toggleShuffle())
        assertFalse(q.toggleShuffle())
    }

    @Test
    fun displayQueueShowsShuffledOrderWithCurrentFirst() {
        val q = QueueManager(ZeroRandom()).apply { setQueue((1..6).map { track("t$it") }); playAt(2) }
        q.setShuffle(true)
        val display = q.displayQueue
        assertEquals(6, display.size)
        assertEquals("t3", display.first().id, "shuffled display must keep current first")
        assertEquals("t3", q.current?.id)
        assertEquals(0, q.displayCurrentIndex)
        // display contains all tracks exactly once
        assertEquals(6, display.map { it.id }.toSet().size)
        // snapshot stays SOURCE order regardless of shuffle
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t6"), q.snapshot.map { it.id })
        // upcoming is the display tail after current
        assertEquals(display.drop(1), q.upcoming)
        // toggling off restores source order
        q.setShuffle(false)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t6"), q.displayQueue.map { it.id })
        assertEquals(2, q.displayCurrentIndex)
        assertEquals("t3", q.displayQueue[q.displayCurrentIndex].id)
    }

    @Test
    fun shuffledDisplayOrderDiffersFromSourceOrder() {
        val q = QueueManager(ZeroRandom()).apply { setQueue(listOf(track("a"), track("b"), track("c"), track("d"), track("e"))) }
        q.setShuffle(true)
        assertTrue(q.displayQueue.map { it.id } != listOf("a", "b", "c", "d", "e"))
        // but first is still the current track ("a" — startIndex 0)
        assertEquals("a", q.displayQueue.first().id)
    }

    @Test
    fun playAtDisplayPlaysExactlyTheVisibleRow() {
        val display = shuffledQueueAt3().displayQueue
        for (i in display.indices) {
            val probe = shuffledQueueAt3()
            probe.playAtDisplay(i)
            assertEquals(display[i].id, probe.current?.id, "tapping visible row $i must play that row")
            assertEquals(i, probe.displayCurrentIndex)
        }
    }

    @Test
    fun removeAtDisplayRemovesTheVisibleRowAndKeepsOrder() {
        val q = shuffledQueueAt3()
        val before = q.displayQueue
        val victim = before[3]
        assertTrue(q.removeAtDisplay(3))
        assertEquals(before.size - 1, q.displayQueue.size)
        assertTrue(victim.id !in q.displayQueue.map { it.id })
        assertEquals(
            before.filterIndexed { i, _ -> i != 3 }.map { it.id },
            q.displayQueue.map { it.id },
            "relative order of the survivors must be preserved",
        )
    }

    @Test
    fun moveInDisplayUnderShufflePermutesExactlyAsDragged() {
        val q = shuffledQueueAt3()
        val before = q.displayQueue
        assertTrue(q.moveInDisplay(4, 1))
        val expected = before.toMutableList().apply { add(1, removeAt(4)) }
        assertEquals(expected.map { it.id }, q.displayQueue.map { it.id }, "no re-shuffle may happen on drag")
        // the playback order follows: peek from current == display after current
        assertEquals(q.displayQueue.drop(1), q.upcoming)
    }

    @Test
    fun moveInDisplayWithoutShuffleReordersSourceLikeDrag() {
        val q = QueueManager(ZeroRandom()).apply { setQueue((1..5).map { track("t$it") }) }
        assertTrue(q.moveInDisplay(0, 3))
        assertEquals(listOf("t2", "t3", "t4", "t1", "t5"), q.displayQueue.map { it.id })
    }

    @Test
    fun addNextUnderShuffleIsNextInPlayback() {
        val q = shuffledQueueAt3()
        q.addNext(track("next"))
        val display = q.displayQueue
        assertEquals("next", display[1].id, "play-next must sit directly after the current row")
        assertEquals("next", q.peekNext()?.id, "and it must be what actually plays next")
    }

    @Test
    fun addToQueueUnderShuffleAppendsAtPlaybackEnd() {
        val q = shuffledQueueAt3()
        q.addToQueue(track("last"))
        assertEquals("last", q.displayQueue.last().id)
        assertEquals("last", q.upcoming.last().id)
    }

    @Test
    fun syncCurrentFollowsEngineAdvanceWithoutDisturbingOrder() {
        val q = shuffledQueueAt3()
        val before = q.displayQueue
        val engineAdvanced = before[1]
        assertTrue(q.syncCurrent(engineAdvanced.id))
        assertEquals(1, q.displayCurrentIndex)
        assertEquals(before.map { it.id }, q.displayQueue.map { it.id }, "order must be untouched by sync")
        assertFalse(q.syncCurrent("no-such-track"))
        assertEquals(1, q.displayCurrentIndex)
    }

    /* ---------------- add / remove / move ---------------- */

    @Test
    fun addNextInsertsRightAfterCurrent() {
        val q = queueOf("a", "b", "c")
        q.addNext(track("x"))
        assertEquals("x", q.next()?.id)
        assertEquals("b", q.next()?.id) // then continues with original next
    }

    @Test
    fun addToQueueAppendsAndPlaysFromEmpty() {
        val q = QueueManager()
        q.addToQueue(track("first"))
        assertEquals("first", q.current?.id)
        q.addToQueue(track("second"))
        assertEquals("second", q.next()?.id)
    }

    @Test
    fun removeBeforeCurrentKeepsCurrentTrack() {
        val q = queueOf("a", "b", "c")
        q.playAt(2) // c
        q.removeAt(0) // remove a
        assertEquals("c", q.current?.id)
        assertEquals(2, q.size)
    }

    @Test
    fun removeCurrentAdvancesToSamePosition() {
        val q = queueOf("a", "b", "c")
        q.playAt(1) // b
        q.removeAt(1) // remove b -> current becomes c (next in slot)
        assertEquals("c", q.current?.id)
        q.removeAt(1) // remove c (last) -> current becomes a
        assertEquals("a", q.current?.id)
        q.removeAt(0)
        assertNull(q.current)
        assertTrue(q.isEmpty)
    }

    @Test
    fun moveReordersAndKeepsCurrentPlaying() {
        val q = queueOf("a", "b", "c")
        q.playAt(0) // a playing
        assertTrue(q.move(2, 1)) // a, c, b
        assertEquals("a", q.current?.id)
        assertEquals("c", q.next()?.id)
        assertEquals("b", q.next()?.id)
        assertFalse(q.move(5, 0)) // out of bounds
        assertFalse(q.move(1, 1)) // no-op
    }

    @Test
    fun playAtJumpsAndContinuesFromThere() {
        val q = queueOf("a", "b", "c", "d")
        assertEquals("c", q.playAt(2)?.id)
        assertEquals("d", q.next()?.id)
        assertNull(q.playAt(99))
    }

    @Test
    fun upcomingReflectsPlayOrder() {
        val q = queueOf("a", "b", "c")
        assertEquals(listOf("b", "c"), q.upcoming.map { it.id })
        q.setRepeatMode(RepeatMode.ALL) // repeat does not change upcoming semantics
        assertEquals(listOf("b", "c"), q.upcoming.map { it.id })
    }

    @Test
    fun repeatOneUserNextToLastTrackThenEndReturnsNull() {
        val q = queueOf("a", "b")
        q.setRepeatMode(RepeatMode.ONE)
        q.next(trackEnded = false)
        assertNull(q.next(trackEnded = true)?.let { if (it.id == "b") null else it }) // natural end on b replays b
        assertEquals("b", q.current?.id)
    }

    /* ---------------- seamless radio (replaceKeepingCurrent) ---------------- */

    @Test
    fun radioKeepsHeadAndReplacesTail() {
        val q = queueOf("a", "b", "c")
        q.playAt(1) // b playing mid-queue
        val headObject = q.current
        q.replaceKeepingCurrent(listOf(track("r1"), track("r2")))
        assertEquals("b", q.current?.id, "head must not move")
        assertTrue(q.current === headObject, "head object must be preserved, not re-created")
        assertEquals(listOf("b", "r1", "r2"), q.displayQueue.map { it.id })
        assertEquals(0, q.displayCurrentIndex)
        assertEquals(listOf("r1", "r2"), q.upcoming.map { it.id })
        // Playback follows the new tail.
        assertEquals("r1", q.next()?.id)
        assertEquals("r2", q.next()?.id)
        assertNull(q.next())
    }

    @Test
    fun radioDropsCurrentFromUpcomingDefensively() {
        val q = queueOf("a", "b")
        q.replaceKeepingCurrent(listOf(track("a"), track("r1"), track("a")))
        assertEquals(listOf("a", "r1"), q.displayQueue.map { it.id })
    }

    @Test
    fun radioOnEmptyQueueAndEmptyUpcomingAreNoops() {
        val empty = QueueManager()
        empty.replaceKeepingCurrent(listOf(track("r1")))
        assertTrue(empty.isEmpty)
        assertNull(empty.current)

        val q = queueOf("a", "b")
        q.replaceKeepingCurrent(emptyList())
        assertEquals(listOf("a", "b"), q.displayQueue.map { it.id })
        assertEquals("a", q.current?.id)
    }

    @Test
    fun radioResetsShuffleButPreservesRepeat() {
        val q = QueueManager(ZeroRandom()).apply { setQueue((1..4).map { track("t$it") }) }
        q.setShuffle(true)
        q.setRepeatMode(RepeatMode.ALL)
        q.replaceKeepingCurrent(listOf(track("r1"), track("r2")))
        assertFalse(q.shuffleEnabled, "fresh radio order is already an order")
        assertEquals(RepeatMode.ALL, q.repeatMode)
        assertEquals(0, q.displayCurrentIndex)
    }

    @Test
    fun peekNextInspectsWithoutAdvancingCursor() {
        val q = queueOf("a", "b", "c")
        assertEquals("b", q.peekNext()?.id)
        assertEquals("a", q.current?.id) // still a
        q.next() // advance to b
        assertEquals("c", q.peekNext()?.id)
        assertEquals("b", q.current?.id)
        q.next() // advance to c
        assertNull(q.peekNext()) // no more tracks with repeat OFF
        q.setRepeatMode(RepeatMode.ALL)
        assertEquals("a", q.peekNext()?.id) // wraps to a with repeat ALL
    }
}
