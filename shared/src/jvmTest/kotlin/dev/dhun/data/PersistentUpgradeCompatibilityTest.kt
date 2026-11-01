package dev.dhun.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.Lyrics
import dev.dhun.core.RepeatMode
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * File-backed old-data → current → repeated-restart compatibility fixture.
 *
 * The v1 SQL below is deliberately independent of the current .sq files: it
 * represents an installation that predates lyrics and downloads. Reopening the
 * same file runs the real SQLDelight migrations. Later iterations model app
 * revisions opening the already-migrated database again, not fresh installs.
 */
class PersistentUpgradeCompatibilityTest {

    @Test
    fun oldUserDataSurvivesMigrationAndRepeatedMetadataSaves(): Unit = runBlocking {
        val directory = Files.createTempDirectory("dhun-old-data-upgrade").toFile()
        val file = File(directory, DatabaseFactory.FILE_NAME)
        try {
            createVersionOneFixture(file)
            var downloadCreated = false
            var lyricsCreated = false

            repeat(8) { revision ->
                val driver = DatabaseDriverFactory(file).createDriver()
                try {
                    val data = DataLayer(DatabaseFactory.create(driver))
                    val old = assertNotNull(data.tracks.get("old-track"))
                    assertEquals("https://images.example/old.jpg", old.thumbnailUrl)
                    assertEquals("UC-old", old.artistId)
                    assertEquals("MPRE-old", old.albumId)
                    assertEquals("Old album", old.albumName)
                    assertEquals(201, old.durationSeconds)
                    assertTrue(data.library.isFavorite(old.id))

                    val playlist = assertNotNull(data.playlists.observePlaylist("playlist-old").first())
                    assertEquals("Road trip", playlist.name)
                    assertEquals("keep me", playlist.description)
                    assertEquals(1, playlist.trackCount)
                    assertEquals(listOf("old-track"), data.playlists.observeTracks(playlist.id).first().map { it.id })
                    assertEquals(1, data.history.observeHistory(10).first().size)
                    assertEquals(listOf("old query"), data.search.observeRecentSearches(10).first())
                    assertEquals("NP", data.settings.getString(SettingsKeys.COUNTRY_CODE))
                    assertEquals(37, data.settings.getInt(SettingsKeys.BACKDROP_BRIGHTNESS, -1))

                    val restored = assertNotNull(data.nowPlaying.load())
                    assertEquals(listOf("old-track"), restored.queue.map { it.id })
                    assertEquals(12_345L + revision, restored.positionMs)
                    assertEquals(RepeatMode.ALL, restored.repeatMode)
                    assertTrue(restored.shuffle)

                    // Newer revisions save an older/partial queue model. Missing
                    // optional fields must not erase richer metadata already in
                    // the user's database (especially its artwork).
                    data.nowPlaying.saveQueue(
                        queue = listOf(old.copy(
                            artistId = null,
                            albumName = null,
                            albumId = null,
                            durationSeconds = null,
                            thumbnailUrl = null,
                        )),
                        currentIndex = 0,
                        positionMs = 12_346L + revision,
                        repeatMode = RepeatMode.ALL,
                        shuffle = true,
                    )
                    val afterSave = assertNotNull(data.tracks.get("old-track"))
                    assertEquals("https://images.example/old.jpg", afterSave.thumbnailUrl)
                    assertEquals("UC-old", afterSave.artistId)
                    assertEquals("MPRE-old", afterSave.albumId)
                    assertEquals("Old album", afterSave.albumName)
                    assertEquals(201, afterSave.durationSeconds)
                    assertTrue(data.library.isFavorite("old-track"))
                    assertEquals(1, data.playlists.observeTracks("playlist-old").first().size)

                    if (!downloadCreated) {
                        assertEquals(0L, data.downloads.count())
                        data.downloads.upsert(DownloadedTrack(
                            trackId = "new-download",
                            title = "Downloaded after upgrade",
                            artistName = "Artist",
                            localAudioPath = "/data/downloads/new.webm",
                            localArtworkPath = "/data/downloads/new.jpg",
                            mimeType = "audio/webm",
                            downloadState = DownloadState.COMPLETED,
                        ))
                        downloadCreated = true
                    }
                    assertEquals(1L, data.downloads.count())
                    assertEquals(
                        "/data/downloads/new.jpg",
                        data.downloads.get("new-download")?.localArtworkPath,
                    )

                    if (!lyricsCreated) {
                        assertNull(data.lyricsCache.get("old-track"))
                        data.lyricsCache.put("old-track", Lyrics.Unsynced("kept lyrics"))
                        lyricsCreated = true
                    }
                    assertEquals(Lyrics.Unsynced("kept lyrics"), data.lyricsCache.get("old-track"))
                } finally {
                    driver.close()
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createVersionOneFixture(file: File) {
        val properties = Properties().apply { setProperty("foreign_keys", "true") }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties)
        try {
            val schema = """
                CREATE TABLE Track (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    artistName TEXT NOT NULL,
                    artistId TEXT,
                    albumName TEXT,
                    albumId TEXT,
                    durationSeconds INTEGER,
                    thumbnailUrl TEXT,
                    explicit INTEGER NOT NULL DEFAULT 0,
                    cachedAt INTEGER,
                    updatedAt INTEGER NOT NULL
                );
                CREATE TABLE Favorite (
                    trackId TEXT NOT NULL PRIMARY KEY REFERENCES Track(id) ON DELETE CASCADE,
                    addedAt INTEGER NOT NULL
                );
                CREATE TABLE History (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trackId TEXT NOT NULL REFERENCES Track(id),
                    playedAt INTEGER NOT NULL,
                    playedFromContext TEXT,
                    completedPlayback INTEGER NOT NULL DEFAULT 0
                );
                CREATE INDEX History_playedAt ON History(playedAt DESC);
                CREATE TABLE Playlist (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    thumbnailUrl TEXT,
                    isLocal INTEGER NOT NULL DEFAULT 1,
                    youtubePlaylistId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                );
                CREATE TABLE PlaylistTrack (
                    playlistId TEXT NOT NULL REFERENCES Playlist(id) ON DELETE CASCADE,
                    trackId TEXT NOT NULL REFERENCES Track(id),
                    position INTEGER NOT NULL,
                    addedAt INTEGER NOT NULL,
                    PRIMARY KEY (playlistId, trackId)
                );
                CREATE INDEX PlaylistTrack_playlist_position ON PlaylistTrack(playlistId, position);
                CREATE TABLE RecentSearch (
                    query TEXT NOT NULL PRIMARY KEY,
                    searchedAt INTEGER NOT NULL
                );
                CREATE TABLE Settings (
                    key TEXT NOT NULL PRIMARY KEY,
                    value TEXT NOT NULL
                );
                CREATE TABLE NowPlayingQueue (
                    position INTEGER NOT NULL PRIMARY KEY,
                    trackId TEXT NOT NULL REFERENCES Track(id)
                );
                CREATE TABLE NowPlayingState (
                    id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                    currentIndex INTEGER NOT NULL,
                    positionMs INTEGER NOT NULL,
                    repeatMode TEXT NOT NULL,
                    shuffle INTEGER NOT NULL,
                    savedAt INTEGER NOT NULL
                );
            """.trimIndent()
            schema.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { statement ->
                driver.execute(null, statement, 0)
            }
            val rows = listOf(
                "INSERT INTO Track(id, title, artistName, artistId, albumName, albumId, durationSeconds, thumbnailUrl, explicit, cachedAt, updatedAt) VALUES ('old-track', 'Old title', 'Old artist', 'UC-old', 'Old album', 'MPRE-old', 201, 'https://images.example/old.jpg', 0, 10, 11)",
                "INSERT INTO Favorite(trackId, addedAt) VALUES ('old-track', 20)",
                "INSERT INTO Playlist(id, name, description, thumbnailUrl, isLocal, youtubePlaylistId, createdAt, updatedAt) VALUES ('playlist-old', 'Road trip', 'keep me', NULL, 1, NULL, 30, 31)",
                "INSERT INTO PlaylistTrack(playlistId, trackId, position, addedAt) VALUES ('playlist-old', 'old-track', 0, 32)",
                "INSERT INTO History(trackId, playedAt, playedFromContext, completedPlayback) VALUES ('old-track', 40, 'QUEUE', 1)",
                "INSERT INTO RecentSearch(query, searchedAt) VALUES ('old query', 50)",
                "INSERT INTO Settings(key, value) VALUES ('country_code', 'NP')",
                "INSERT INTO Settings(key, value) VALUES ('backdrop_brightness', 37)",
                "INSERT INTO NowPlayingQueue(position, trackId) VALUES (0, 'old-track')",
                "INSERT INTO NowPlayingState(id, currentIndex, positionMs, repeatMode, shuffle, savedAt) VALUES (1, 0, 12345, 'ALL', 1, 60)",
                "PRAGMA user_version = 1",
            )
            rows.forEach { driver.execute(null, it, 0) }
        } finally {
            driver.close()
        }
    }
}
