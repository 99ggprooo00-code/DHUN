package dev.dhun.desktop.native

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-core tests for the jump-list task model: entry order (recents →
 * separator → Play/Pause → Open), argument wiring, label sanitizing and
 * truncation.
 */
class JumpListModelTest {

    private fun track(id: String, title: String = "Title $id", artist: String = "Artist") =
        RecentTrack(id = id, title = title, artistName = artist)

    @Test
    fun `empty recents produce verbs only — no dangling separator`() {
        val tasks = JumpListModel.buildTasks(emptyList())
        assertEquals(
            listOf(
                JumpListEntry("Play / Pause", JumpListArgs.PLAY_PAUSE),
                JumpListEntry("Open DHUN", JumpListArgs.OPEN),
            ),
            tasks,
        )
    }

    @Test
    fun `recents come first newest-first, then separator, then the verbs`() {
        val tasks = JumpListModel.buildTasks(listOf(track("id1"), track("id2")))
        assertEquals(5, tasks.size)
        assertEquals("Title id1 — Artist", tasks[0].title)
        assertEquals("--dhun-play=id1", tasks[0].arguments)
        assertFalse(tasks[0].isSeparator)
        assertEquals("Title id2 — Artist", tasks[1].title)
        assertTrue(tasks[2].isSeparator)
        assertEquals("Play / Pause", tasks[3].title)
        assertFalse(tasks[3].isSeparator)
        assertEquals("Open DHUN", tasks[4].title)
        assertFalse(tasks[4].isSeparator)
    }

    @Test
    fun `entries with invalid track ids are dropped, others survive`() {
        val tasks = JumpListModel.buildTasks(listOf(track("bad id"), track("ok")))
        assertEquals(3, tasks.size)
        assertEquals("--dhun-play=ok", tasks[0].arguments)
    }

    @Test
    fun `more than MAX_RECENT recents are capped`() {
        val many = (1..20).map { track("id$it") }
        val tasks = JumpListModel.buildTasks(many)
        // 5 recents + separator + 2 verbs
        assertEquals(JumpListModel.MAX_RECENT + 2, tasks.size)
        assertEquals("--dhun-play=id1", tasks[0].arguments)
    }

    @Test
    fun `task titles show title and artist, sanitized to one line`() {
        assertEquals(
            "Song — Duo",
            JumpListModel.taskTitle(RecentTrack("id", "Song", "Duo")),
        )
        assertEquals("Instrumental", JumpListModel.taskTitle(RecentTrack("id", "Instrumental", "")))
        assertEquals("Untitled", JumpListModel.taskTitle(RecentTrack("id", "\t\n", "")))
        assertEquals(
            "A B",
            JumpListModel.taskTitle(RecentTrack("id", "A\nB", "")),
            "control characters collapse to single spaces",
        )
    }

    @Test
    fun `task titles truncate with an ellipsis at the cap`() {
        val long = "x".repeat(JumpListModel.MAX_TITLE_CHARS + 10)
        val label = JumpListModel.taskTitle(RecentTrack("id", long, ""))
        assertEquals(JumpListModel.MAX_TITLE_CHARS, label.length)
        assertTrue(label.endsWith("…"))
        assertEquals("x".repeat(JumpListModel.MAX_TITLE_CHARS - 1) + "…", label)
    }
}
