package dev.dhun.data

import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.database.DhunDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 05: every repository against a real in-memory SQLite database. */
class RepositoriesTest {

    private class FakeClock(var now: Long = 1_000_000L) : EpochClock {
        override fun nowMs(): Long = now
        fun tick(ms: Long = 1_000) { now += ms }
    }

    private fun newDb(): DhunDatabase = DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver())

    private fun track(id: String, title: String = "Song $id") = Track(
        id = id, title = title, artistName = "Artist $id", artistId = "UC$id", albumName = "Album",
        albumId = "MPREb$id", durationSeconds = 200, thumbnailUrl = "https://i/$id.jpg", explicit = id.endsWith("x"),
    )

    /* ---------------- Track ---------------- */

    @Test
    fun trackUpsertRoundTripsEveryField() = runBlocking {
        val repo = SqlDelightTrackRepository(newDb(), FakeClock(), Dispatchers.Unconfined)
        val t = track("a1x")
        repo.save(t)
        assertEquals(t, repo.get("a1x"))
        repo.save(t.copy(title = "Renamed"))
        assertEquals("Renamed", repo.get("a1x")?.title)
        repo.delete("a1x")
        assertNull(repo.get("a1x"))
    }

    @Test
    fun trackObserveEmitsOnChange() = runBlocking {
        val repo = SqlDelightTrackRepository(newDb(), FakeClock(), Dispatchers.Unconfined)
        assertNull(repo.observe("z").first())
        repo.save(track("z"))
        assertEquals("Song z", repo.observe("z").first()?.title)
    }

    /* ---------------- Favorites ---------------- */

    @Test
    fun favoriteAddObserveRemove() = runBlocking {
        val db = newDb()
        val clock = FakeClock()
        val lib = SqlDelightLibraryRepository(db, clock, Dispatchers.Unconfined)
        assertFalse(lib.isFavorite("f1"))
        lib.addFavorite(track("f1")); clock.tick()
        lib.addFavorite(track("f2")); clock.tick()
        lib.addFavorite(track("f1")) // idempotent
        assertTrue(lib.isFavorite("f1"))
        assertTrue(lib.observeIsFavorite("f1").first())
        assertEquals(listOf("f2", "f1"), lib.observeFavorites().first().map { it.id }) // newest first
        assertEquals(setOf("f1", "f2"), lib.observeFavoriteIds().first())
        lib.removeFavorite("f1")
        assertFalse(lib.isFavorite("f1"))
        assertEquals(listOf("f2"), lib.observeFavorites().first().map { it.id })
        // track row survives un-favoriting (may be referenced by history/playlists)
        assertNotNull(SqlDelightTrackRepository(db, clock, Dispatchers.Unconfined).get("f1"))
    }

    /* ---------------- Playlists ---------------- */

    @Test
    fun playlistCreateAddReorderRemoveDelete() = runBlocking {
        val clock = FakeClock()
        val repo = SqlDelightPlaylistRepository(newDb(), clock, Dispatchers.Unconfined, idGenerator = { "pl1" })
        val pl = repo.create("  Road trip  ")
        assertEquals("pl1", pl.id)
        assertEquals("Road trip", pl.name)
        assertEquals(listOf("pl1"), repo.observePlaylists().first().map { it.id })

        assertTrue(repo.addTrack("pl1", track("a")))
        assertTrue(repo.addTrack("pl1", track("b")))
        assertTrue(repo.addTrack("pl1", track("c")))
        assertFalse(repo.addTrack("pl1", track("b"))) // duplicate ignored
        assertEquals(listOf("a", "b", "c"), repo.observeTracks("pl1").first().map { it.id })
        assertEquals(3, repo.observePlaylist("pl1").first()?.trackCount)

        repo.move("pl1", 0, 2)
        assertEquals(listOf("b", "c", "a"), repo.observeTracks("pl1").first().map { it.id })
        repo.move("pl1", 2, 0)
        assertEquals(listOf("a", "b", "c"), repo.observeTracks("pl1").first().map { it.id })
        repo.move("pl1", 5, 0) // out of range: no-op, no crash
        assertEquals(listOf("a", "b", "c"), repo.observeTracks("pl1").first().map { it.id })

        repo.removeTrack("pl1", "b")
        assertEquals(listOf("a", "c"), repo.observeTracks("pl1").first().map { it.id })
        // positions renumbered densely: appending lands at index 2
        repo.addTrack("pl1", track("d"))
        assertEquals(listOf("a", "c", "d"), repo.observeTracks("pl1").first().map { it.id })

        repo.rename("pl1", "Long drive")
        assertEquals("Long drive", repo.observePlaylist("pl1").first()?.name)

        repo.delete("pl1")
        assertNull(repo.observePlaylist("pl1").first())
        assertTrue(repo.observeTracks("pl1").first().isEmpty())
    }

    @Test
    fun playlistsOrderedByMostRecentlyUpdated() = runBlocking {
        val clock = FakeClock()
        var n = 0
        val repo = SqlDelightPlaylistRepository(newDb(), clock, Dispatchers.Unconfined, idGenerator = { "p${n++}" })
        repo.create("first"); clock.tick()
        repo.create("second"); clock.tick()
        assertEquals(listOf("second", "first"), repo.observePlaylists().first().map { it.name })
        repo.addTrack("p0", track("t")) // touching "first" bumps it to the top
        assertEquals(listOf("first", "second"), repo.observePlaylists().first().map { it.name })
    }

    /* ---------------- History ---------------- */

    @Test
    fun historyRecordsObservesAndClears() = runBlocking {
        val clock = FakeClock()
        val repo = SqlDelightHistoryRepository(newDb(), clock, Dispatchers.Unconfined)
        val at1 = repo.recordPlay(track("h1"), PlayContext.SEARCH); clock.tick()
        repo.recordPlay(track("h2"), PlayContext.HOME); clock.tick()
        repo.recordPlay(track("h1"), PlayContext.QUEUE); clock.tick()

        val entries = repo.observeHistory(10).first()
        assertEquals(listOf("h1", "h2", "h1"), entries.map { it.track.id })
        assertEquals("QUEUE", entries.first().playedFromContext)
        assertNotNull(entries.first().entryId)
        assertFalse(entries.last().completedPlayback)

        repo.markCompleted("h1", at1)
        assertTrue(repo.observeHistory(10).first().last().completedPlayback)

        // "Listen again": distinct tracks, most recent first
        assertEquals(listOf("h1", "h2"), repo.observeRecentlyPlayed(10).first().map { it.id })
        assertEquals(2L, repo.playCount("h1"))

        repo.remove(entries.first().entryId!!)
        assertEquals(2, repo.observeHistory(10).first().size)
        repo.clear()
        assertTrue(repo.observeHistory(10).first().isEmpty())
    }

    @Test
    fun historyLimitIsRespected() = runBlocking {
        val clock = FakeClock()
        val repo = SqlDelightHistoryRepository(newDb(), clock, Dispatchers.Unconfined)
        repeat(30) { repo.recordPlay(track("t$it"), PlayContext.UNKNOWN); clock.tick() }
        assertEquals(5, repo.observeHistory(5).first().size)
        assertEquals("t29", repo.observeHistory(5).first().first().track.id)
    }

    /* ---------------- Settings ---------------- */

    @Test
    fun settingsAllTypesRoundTrip() = runBlocking {
        val repo = SqlDelightSettingsRepository(newDb(), Dispatchers.Unconfined)
        assertNull(repo.getString(SettingsKeys.THEME))
        assertEquals("dark", repo.getString(SettingsKeys.THEME) ?: SettingsKeys.THEME_DEFAULT)
        repo.putString(SettingsKeys.THEME, "light")
        assertEquals("light", repo.getString(SettingsKeys.THEME))
        assertEquals("light", repo.observeString(SettingsKeys.THEME).first())

        assertTrue(repo.getBoolean(SettingsKeys.LYRICS_ENABLED, true))
        repo.putBoolean(SettingsKeys.LYRICS_ENABLED, false)
        assertFalse(repo.getBoolean(SettingsKeys.LYRICS_ENABLED, true))

        assertEquals(1024, repo.getInt(SettingsKeys.CACHE_SIZE_MB, 1024))
        repo.putInt(SettingsKeys.CACHE_SIZE_MB, 2048)
        assertEquals(2048, repo.getInt(SettingsKeys.CACHE_SIZE_MB, 1024))

        repo.putString(SettingsKeys.CACHE_SIZE_MB, "garbage")
        assertEquals(7, repo.getInt(SettingsKeys.CACHE_SIZE_MB, 7)) // corrupt value → default

        repo.remove(SettingsKeys.THEME)
        assertNull(repo.getString(SettingsKeys.THEME))
    }

    /* ---------------- Recent searches ---------------- */

    @Test
    fun recentSearchesDedupeOrderAndTrim() = runBlocking {
        val clock = FakeClock()
        val repo = SqlDelightSearchRepository(newDb(), clock, Dispatchers.Unconfined, maxEntries = 3)
        repo.recordSearch("queen"); clock.tick()
        repo.recordSearch("  "); clock.tick() // ignored
        repo.recordSearch("beatles"); clock.tick()
        repo.recordSearch("queen "); clock.tick() // same query, re-bumped
        assertEquals(listOf("queen", "beatles"), repo.observeRecentSearches().first())
        repo.recordSearch("coldplay"); clock.tick()
        repo.recordSearch("adele"); clock.tick() // exceeds max 3 → oldest ("beatles") trimmed
        assertEquals(listOf("adele", "coldplay", "queen"), repo.observeRecentSearches().first())
        repo.removeSearch("coldplay")
        assertEquals(listOf("adele", "queen"), repo.observeRecentSearches().first())
        repo.clearRecentSearches()
        assertTrue(repo.observeRecentSearches().first().isEmpty())
    }

    /* ---------------- Now playing ---------------- */

    @Test
    fun nowPlayingQueueRoundTrip() = runBlocking {
        val clock = FakeClock()
        val repo = SqlDelightNowPlayingRepository(newDb(), clock, Dispatchers.Unconfined)
        assertNull(repo.load())

        val q = listOf(track("q1"), track("q2"), track("q3"))
        repo.saveQueue(q, currentIndex = 1, positionMs = 42_000, repeatMode = RepeatMode.ALL, shuffle = true)
        val snap = repo.load()!!
        assertEquals(q, snap.queue)
        assertEquals(1, snap.currentIndex)
        assertEquals("q2", snap.currentTrack?.id)
        assertEquals(42_000, snap.positionMs)
        assertEquals(RepeatMode.ALL, snap.repeatMode)
        assertTrue(snap.shuffle)

        repo.updateProgress(currentIndex = 2, positionMs = 5_000)
        val snap2 = repo.load()!!
        assertEquals(2, snap2.currentIndex)
        assertEquals(5_000, snap2.positionMs)
        assertEquals(q, snap2.queue) // queue untouched by progress updates

        // replacing the queue drops old items (no leftovers at stale positions)
        repo.saveQueue(listOf(track("n1")), 0, 0, RepeatMode.OFF, false)
        assertEquals(listOf("n1"), repo.load()!!.queue.map { it.id })

        // out-of-range index is clamped, never crashes restore
        repo.saveQueue(listOf(track("n1")), 9, -5, RepeatMode.OFF, false)
        assertEquals(0, repo.load()!!.currentIndex)
        assertEquals(0, repo.load()!!.positionMs)

        repo.clear()
        assertNull(repo.load())
        // saving an empty queue == clear
        repo.saveQueue(emptyList(), 0, 0, RepeatMode.OFF, false)
        assertNull(repo.load())
    }

    /* ---------------- Schema ---------------- */

    @Test
    fun schemaVersionIsOne() {
        assertEquals(1L, DhunDatabase.Schema.version)
    }
}
