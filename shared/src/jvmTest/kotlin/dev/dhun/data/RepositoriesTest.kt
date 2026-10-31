package dev.dhun.data

import dev.dhun.core.RepeatMode
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.DownloadState
import dev.dhun.core.Track
import dev.dhun.database.DhunDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
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
    fun trackUpsertRoundTripsEveryField(): Unit = runBlocking {
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
    fun sparseTrackUpdateDoesNotEraseKnownArtwork(): Unit = runBlocking {
        val repo = SqlDelightTrackRepository(newDb(), FakeClock(), Dispatchers.Unconfined)
        val full = track("artwork")
        repo.save(full)
        repo.save(full.copy(title = "Sparse replay", thumbnailUrl = null))
        assertEquals(full.thumbnailUrl, repo.get(full.id)?.thumbnailUrl)
        assertEquals("Sparse replay", repo.get(full.id)?.title)

        repo.save(full.copy(thumbnailUrl = "https://i/new.jpg"))
        assertEquals("https://i/new.jpg", repo.get(full.id)?.thumbnailUrl)
    }

    @Test
    fun trackObserveEmitsOnChange(): Unit = runBlocking {
        val repo = SqlDelightTrackRepository(newDb(), FakeClock(), Dispatchers.Unconfined)
        assertNull(repo.observe("z").first())
        repo.save(track("z"))
        assertEquals("Song z", repo.observe("z").first()?.title)
    }

    /* ---------------- Favorites ---------------- */

    @Test
    fun favoriteAddObserveRemove(): Unit = runBlocking {
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
    fun playlistCreateAddReorderRemoveDelete(): Unit = runBlocking {
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
    fun playlistCoverFollowsOrderedTrackArtworkWithoutAMigration(): Unit = runBlocking {
        val db = newDb()
        val clock = FakeClock()
        val repo = SqlDelightPlaylistRepository(db, clock, Dispatchers.Unconfined, idGenerator = { "cover" })
        val tracks = SqlDelightTrackRepository(db, clock, Dispatchers.Unconfined)
        repo.create("Covers")
        val sparse = track("a").copy(thumbnailUrl = null)
        repo.addTrack("cover", sparse)
        repo.addTrack("cover", track("b"))
        assertEquals("https://i/b.jpg", repo.observePlaylist("cover").first()?.thumbnailUrl)

        tracks.save(track("a"))
        assertEquals("https://i/a.jpg", repo.observePlaylist("cover").first()?.thumbnailUrl)
        repo.move("cover", 0, 1)
        assertEquals(listOf("b", "a"), repo.observeTracks("cover").first().map { it.id })
        assertEquals("https://i/b.jpg", repo.observePlaylist("cover").first()?.thumbnailUrl)
        repo.removeTrack("cover", "b")
        assertEquals("https://i/a.jpg", repo.observePlaylist("cover").first()?.thumbnailUrl)
        repo.removeTrack("cover", "a")
        assertNull(repo.observePlaylist("cover").first()?.thumbnailUrl)
    }

    @Test
    fun deletingPlaylistPreservesRelatedDataAcrossDatabaseReopen(): Unit = runBlocking {
        val dir = Files.createTempDirectory("dhun-playlist-delete").toFile()
        val dbFile = dir.resolve("library.db")
        val audio = dir.resolve("track.webm").apply { writeText("synthetic audio") }
        var driver = DatabaseDriverFactory(dbFile).createDriver()
        try {
            var data = DataLayer(DatabaseFactory.create(driver))
            val first = data.playlists.create("Delete")
            val second = data.playlists.create("Keep")
            val song = track("shared")
            data.playlists.addTrack(first.id, song)
            data.playlists.addTrack(second.id, song)
            data.library.addFavorite(song)
            data.history.recordPlay(song, PlayContext.PLAYLIST)
            data.downloads.upsert(DownloadedTrack(
                trackId = song.id, title = song.title, artistName = song.artistName,
                localAudioPath = audio.absolutePath, mimeType = "audio/webm",
                downloadState = DownloadState.COMPLETED,
            ))
            data.nowPlaying.saveQueue(listOf(song), 0, 12_000L, RepeatMode.ONE, false)
            data.playlists.delete(first.id)
            data.playlists.delete("missing") // idempotent, cannot affect other rows
            driver.close()
            driver = DatabaseDriverFactory(dbFile).createDriver()
            data = DataLayer(DatabaseFactory.create(driver))
            assertNull(data.playlists.observePlaylist(first.id).first())
            assertEquals(listOf(song.id), data.playlists.observeTracks(second.id).first().map { it.id })
            assertNotNull(data.tracks.get(song.id))
            assertTrue(data.library.isFavorite(song.id))
            assertEquals(listOf(song.id), data.history.observeHistory(10).first().map { it.track.id })
            assertTrue(data.downloads.isDownloaded(song.id))
            assertEquals(listOf(song), data.nowPlaying.load()?.queue)
            assertEquals(12_000L, data.nowPlaying.load()?.positionMs)
            assertEquals("synthetic audio", audio.readText())
        } finally {
            driver.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun playlistDeletionRollsBackMembershipWhenRowDeleteFails(): Unit = runBlocking {
        // A BEFORE DELETE trigger aborts the second statement of the transaction,
        // after deleteAllTracks has run. Without a transaction, membership is lost.
        val driver = DatabaseDriverFactory.inMemory().createDriver()
        try {
            val data = DataLayer(DatabaseFactory.create(driver))
            val playlist = data.playlists.create("Keep on failure")
            val other = data.playlists.create("Another playlist")
            val song = track("rollback")
            data.playlists.addTrack(playlist.id, song)
            data.playlists.addTrack(other.id, song)
            data.library.addFavorite(song)
            driver.execute(null,
                "CREATE TRIGGER abort_playlist_delete BEFORE DELETE ON Playlist " +
                    "BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END", 0)
            kotlin.test.assertFailsWith<Exception> { data.playlists.delete(playlist.id) }
            assertEquals(playlist.id, data.playlists.observePlaylist(playlist.id).first()?.id)
            assertEquals(listOf(song.id), data.playlists.observeTracks(playlist.id).first().map { it.id })
            assertEquals(listOf(song.id), data.playlists.observeTracks(other.id).first().map { it.id })
            assertTrue(data.library.isFavorite(song.id))
            driver.execute(null, "DROP TRIGGER abort_playlist_delete", 0)
            data.playlists.delete(playlist.id)
            assertNull(data.playlists.observePlaylist(playlist.id).first())
            assertEquals(listOf(song.id), data.playlists.observeTracks(other.id).first().map { it.id })
        } finally { driver.close() }
    }

    @Test
    fun playlistsOrderedByMostRecentlyUpdated(): Unit = runBlocking {
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
    fun historyRecordsObservesAndClears(): Unit = runBlocking {
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
    fun historyLimitIsRespected(): Unit = runBlocking {
        val clock = FakeClock()
        val repo = SqlDelightHistoryRepository(newDb(), clock, Dispatchers.Unconfined)
        repeat(30) { repo.recordPlay(track("t$it"), PlayContext.UNKNOWN); clock.tick() }
        assertEquals(5, repo.observeHistory(5).first().size)
        assertEquals("t29", repo.observeHistory(5).first().first().track.id)
    }

    /* ---------------- Settings ---------------- */

    @Test
    fun settingsAllTypesRoundTrip(): Unit = runBlocking {
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
    fun recentSearchesDedupeOrderAndTrim(): Unit = runBlocking {
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
    fun nowPlayingQueueRoundTrip(): Unit = runBlocking {
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

    /* ---------------- Lyrics cache ---------------- */

    @Test
    fun songMatchPersistsThroughSqlDelightRepositoryRecreation(): Unit = runBlocking {
        val db = newDb()
        val source = object : dev.dhun.lyrics.LyricsSource {
            override suspend fun fetch(track: dev.dhun.core.Track) = dev.dhun.core.DhunResult.Success(dev.dhun.core.Lyrics.NotAvailable)
        }
        val match = dev.dhun.lyrics.LyricsMatch("record", "lrclib", "Song", "Artist",
            dev.dhun.core.Lyrics.Synced(dev.dhun.lyrics.LrcParser.parse("[00:01]<00:01>Word<00:02>")))
        val cache = SqlDelightLyricsCacheRepository(db, FakeClock(), Dispatchers.Unconfined)
        dev.dhun.lyrics.LyricsRepository(cache, source, source,
            SqlDelightSettingsRepository(db, Dispatchers.Unconfined)).selectMatch("song", match)
        cache.clear()
        val reopened = dev.dhun.lyrics.LyricsRepository(cache, source, source,
            SqlDelightSettingsRepository(db, Dispatchers.Unconfined))
        assertEquals(match, reopened.selectedMatch("song"))
        assertEquals(dev.dhun.core.DhunResult.Success(match.lyrics), reopened.getLyrics(dev.dhun.core.Track("song", "Song", "Artist")))
    }

    @Test
    fun vocalRolesSurviveCacheAndSavedChoiceRecreation(): Unit = runBlocking {
        val db = newDb()
        val lines = dev.dhun.lyrics.TtmlParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:m="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="1s" end="5s" m:agent="v1">Main<span begin="1s" end="2s" m:role="x-bg" m:agent="v2">Backing</span></p></div></body></tt>""")
        val lyrics = dev.dhun.core.Lyrics.Synced(lines)
        val cache = SqlDelightLyricsCacheRepository(db, FakeClock(), Dispatchers.Unconfined)
        cache.put("roles", lyrics)
        assertEquals(lyrics, SqlDelightLyricsCacheRepository(db, FakeClock(), Dispatchers.Unconfined).get("roles"))
        val source = object : dev.dhun.lyrics.LyricsSource {
            override suspend fun fetch(track: dev.dhun.core.Track) = dev.dhun.core.DhunResult.Success(dev.dhun.core.Lyrics.NotAvailable)
        }
        val selected = dev.dhun.lyrics.LyricsMatch("local-ttml", "local_ttml", "Song", "Artist", lyrics, metadataVerified = false)
        dev.dhun.lyrics.LyricsRepository(cache, source, source, SqlDelightSettingsRepository(db, Dispatchers.Unconfined)).selectMatch("roles", selected)
        cache.clear()
        val restored = dev.dhun.lyrics.LyricsRepository(cache, source, source, SqlDelightSettingsRepository(db, Dispatchers.Unconfined))
        assertEquals(selected, restored.selectedMatch("roles"))
    }

    @Test
    fun ttmlLineEndsAndWordGapsSurviveCacheAndChoiceRecreation(): Unit = runBlocking {
        val db = newDb()
        val lines = dev.dhun.lyrics.TtmlParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="6s"><span begin="0s" end="1s">A </span><span begin="3s" end="4s">B</span></p></div></body></tt>""")
        val lyrics = dev.dhun.core.Lyrics.Synced(lines)
        val cache = SqlDelightLyricsCacheRepository(db, FakeClock(), Dispatchers.Unconfined)
        cache.put("ttml", lyrics)
        val reopened = SqlDelightLyricsCacheRepository(db, FakeClock(), Dispatchers.Unconfined)
        assertEquals(lyrics, reopened.get("ttml"))
        assertEquals(lyrics, reopened.observe("ttml").first())
        // Historical line/enhanced LRC cache rows need no migration.
        db.lyricsCacheQueries.insert("old", true, "[00:01]<00:01>old<00:02>", 1L)
        assertEquals(dev.dhun.core.Lyrics.Synced(dev.dhun.lyrics.LrcParser.parse("[00:01]<00:01>old<00:02>")), reopened.get("old"))
        db.lyricsCacheQueries.insert("corrupt", true, "{broken-json", 1L)
        assertEquals(dev.dhun.core.Lyrics.NotAvailable, reopened.get("corrupt"))
        val source = object : dev.dhun.lyrics.LyricsSource {
            override suspend fun fetch(track: dev.dhun.core.Track) = dev.dhun.core.DhunResult.Success(dev.dhun.core.Lyrics.NotAvailable)
        }
        val settings = SqlDelightSettingsRepository(db, Dispatchers.Unconfined)
        val chosen = dev.dhun.lyrics.LyricsMatch("local-ttml", "local_ttml", "Song", "Artist", lyrics, metadataVerified = false)
        dev.dhun.lyrics.LyricsRepository(cache, source, source, settings).selectMatch("ttml", chosen)
        reopened.clear()
        val repo = dev.dhun.lyrics.LyricsRepository(reopened, source, source, SqlDelightSettingsRepository(db, Dispatchers.Unconfined))
        assertEquals(chosen, repo.selectedMatch("ttml"))
    }

    @Test
    fun enhancedLyricsSurviveRepositoryRecreation(): Unit = runBlocking {
        val db = newDb()
        val original = dev.dhun.core.Lyrics.Synced(dev.dhun.lyrics.LrcParser.parse(
            "[00:01.123]<00:01.123>Hello <00:01.987>世界<00:03.456>"
        ))
        SqlDelightLyricsCacheRepository(db, FakeClock(), Dispatchers.Unconfined).put("word-track", original)
        val reopened = SqlDelightLyricsCacheRepository(db, FakeClock(), Dispatchers.Unconfined)
        assertEquals(original, reopened.get("word-track"))
        assertEquals(original, reopened.observe("word-track").first())
    }

    @Test
    fun lyricsCacheSaveGetAndClear(): Unit = runBlocking {
        val clock = FakeClock()
        val repo = SqlDelightLyricsCacheRepository(newDb(), clock, Dispatchers.Unconfined)
        assertNull(repo.get("t1"))

        val synced = dev.dhun.core.Lyrics.Synced(
            listOf(
                dev.dhun.core.LyricsLine(12_340L, "Hello"),
                dev.dhun.core.LyricsLine(65_670L, "World"),
            ),
        )
        repo.put("t1", synced)
        val fetchedSynced = repo.get("t1")
        assertNotNull(fetchedSynced)
        assertTrue(fetchedSynced is dev.dhun.core.Lyrics.Synced)
        assertEquals(2, fetchedSynced.lines.size)
        assertEquals(12_340L, fetchedSynced.lines[0].startTimeMs)
        assertEquals("Hello", fetchedSynced.lines[0].text)
        assertEquals(65_670L, fetchedSynced.lines[1].startTimeMs)
        assertEquals("World", fetchedSynced.lines[1].text)
        assertEquals(fetchedSynced, repo.observe("t1").first())

        val unsynced = dev.dhun.core.Lyrics.Unsynced("Plain lyrics text")
        repo.put("t2", unsynced)
        val fetchedUnsynced = repo.get("t2")
        assertNotNull(fetchedUnsynced)
        assertTrue(fetchedUnsynced is dev.dhun.core.Lyrics.Unsynced)
        assertEquals("Plain lyrics text", fetchedUnsynced.text)

        // NotAvailable is never cached
        repo.put("t3", dev.dhun.core.Lyrics.NotAvailable)
        assertNull(repo.get("t3"))

        repo.clear()
        assertNull(repo.get("t1"))
        assertNull(repo.get("t2"))
    }

    /* ---------------- Schema ---------------- */

    @Test
    fun schemaVersionIsThree() {
        // v1 base + 1.sqm (LyricsCache) → 2 + 2.sqm (DownloadedTrack) → 3.
        assertEquals(3L, DhunDatabase.Schema.version)
    }
}
