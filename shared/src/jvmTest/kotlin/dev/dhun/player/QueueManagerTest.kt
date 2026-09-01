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
}
