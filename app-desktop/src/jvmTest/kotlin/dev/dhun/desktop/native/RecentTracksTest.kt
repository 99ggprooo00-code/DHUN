package dev.dhun.desktop.native

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-core tests for the recent-tracks persistence (the tab-separated file
 * under the app data dir): record policy (dedupe, move-to-front, cap),
 * tolerant parsing, sanitizing, and the write-temp-then-rename save.
 */
class RecentTracksTest {

    private fun track(id: String, title: String = "T $id", artist: String = "A") =
        RecentTrack(id = id, title = title, artistName = artist)

    // ---- record policy --------------------------------------------------

    @Test
    fun `record moves an existing id to the front`() {
        val updated = RecentTracksStore.record(track("b"), listOf(track("a"), track("b"), track("c")))
        assertEquals(listOf("b", "a", "c"), updated.map { it.id })
    }

    @Test
    fun `record prepends a new id and caps the list`() {
        var list = listOf<RecentTrack>()
        for (i in 1..8) list = RecentTracksStore.record(track("id$i"), list)
        assertEquals((8 downTo 4).map { "id$it" }, list.map { it.id })
        assertEquals(JumpListModel.MAX_RECENT, list.size)
    }

    @Test
    fun `record of an already-first track changes nothing`() {
        val list = listOf(track("a"), track("b"))
        assertEquals(list, RecentTracksStore.record(track("a"), list))
    }

    // ---- line format ----------------------------------------------------

    @Test
    fun `format and parse round-trip`() {
        val line = RecentTracksStore.formatLine(RecentTrack("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley"))
        assertEquals("dQw4w9WgXcQ\tNever Gonna Give You Up\tRick Astley", line)
        assertEquals(RecentTrack("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley"), RecentTracksStore.parseLine(line))
    }

    @Test
    fun `format sanitizes control characters so fields cannot eat the separators`() {
        val fields = RecentTracksStore
            .formatLine(RecentTrack("id", "Evil\nTitle", "Bad\tArtist"))
            .split('\t')
        assertEquals(3, fields.size)
        assertEquals("id", fields[0])
        assertEquals("Evil Title", fields[1])
        assertEquals("Bad Artist", fields[2])
    }

    @Test
    fun `parse skips corrupt lines`() {
        assertNull(RecentTracksStore.parseLine(""))
        assertNull(RecentTracksStore.parseLine("onlyid"))
        assertNull(RecentTracksStore.parseLine("id\t"))
        assertNull(RecentTracksStore.parseLine("\ttitle\tartist"))
        assertEquals(RecentTrack("id", "title", ""), RecentTracksStore.parseLine("id\ttitle\t"))
        assertNull(RecentTracksStore.parseLine("id\ttitle\tartist\textra"), "wrong field count = corrupt")
    }

    // ---- store (real file) ----------------------------------------------

    @Test
    fun `save then load round-trips through a real file`() {
        val dir = newTempDir()
        try {
            val store = RecentTracksStore(File(dir, RecentTracksStore.FILE_NAME))
            val list = listOf(track("b"), track("a"))
            assertTrue(store.save(list))
            assertEquals(list, store.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `save creates missing parent dirs and overwrites stale content`() {
        val dir = newTempDir()
        try {
            val file = File(dir, "nested/${RecentTracksStore.FILE_NAME}")
            val store = RecentTracksStore(file)
            assertTrue(store.save(listOf(track("x1"))))
            assertTrue(store.save(listOf(track("x2"), track("x1"))))
            assertEquals(listOf("x2", "x1"), store.load().map { it.id })
            assertFalse(File(dir, "nested/${RecentTracksStore.FILE_NAME}.tmp").exists(), "no tmp leftovers")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `load of a missing or corrupt file is empty, never throws`() {
        val dir = newTempDir()
        try {
            val file = File(dir, RecentTracksStore.FILE_NAME)
            assertTrue(RecentTracksStore(File(dir, "missing.txt")).load().isEmpty())
            file.writeText("garbage line\n\nid\tok\t\n \t \t \n")
            assertEquals(
                listOf(RecentTrack("id", "ok", "")),
                RecentTracksStore(File(dir, RecentTracksStore.FILE_NAME)).load(),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `store without a file is an inert in-memory store`() {
        val store = RecentTracksStore(null)
        assertFalse(store.save(listOf(track("a"))))
        assertTrue(store.load().isEmpty())
    }

    /** junit @TempDir needs junit5; keep the JDK-only temp dir helper. */
    private fun newTempDir(): File = File.createTempFile("dhun-jumplist-test", "").let { f ->
        f.delete()
        f.mkdirs()
        f
    }
}
