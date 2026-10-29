package dev.dhun.android.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Android SQLite driver, not the JDBC driver used by the shared JVM suite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class) // isolated DB: do not boot DhunApp/Koin
class PlaylistDeletionAndroidTest {
    @Test
    fun abortRollsBackMembershipAndSuccessfulRetrySurvivesReopen(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DatabaseFactory.FILE_NAME)
        var driver = DatabaseDriverFactory(context).createDriver()
        try {
            var data = DataLayer(DatabaseFactory.create(driver))
            val removed = data.playlists.create("Remove")
            val kept = data.playlists.create("Keep")
            val song = Track("retained", "Retained song", "Artist")
            data.playlists.addTrack(removed.id, song)
            data.playlists.addTrack(kept.id, song)
            data.library.addFavorite(song)
            data.nowPlaying.saveQueue(listOf(song), 0, 1_000, RepeatMode.OFF, false)

            // The first SQL statement has removed membership when the second
            // statement aborts. The transaction must roll BOTH back.
            driver.execute(null, "CREATE TRIGGER abort_playlist_delete BEFORE DELETE ON Playlist " +
                "BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END", 0)
            try {
                data.playlists.delete(removed.id)
                throw AssertionError("Android SQLite delete unexpectedly succeeded")
            } catch (_: Exception) {
                // Storage error must propagate, not become a successful delete.
            }
            assertEquals(removed.id, data.playlists.observePlaylist(removed.id).first()?.id)
            assertEquals(listOf(song), data.playlists.observeTracks(removed.id).first())
            driver.execute(null, "DROP TRIGGER abort_playlist_delete", 0)
            data.playlists.delete(removed.id)
            driver.close()
            driver = DatabaseDriverFactory(context).createDriver()
            data = DataLayer(DatabaseFactory.create(driver))
            assertNull(data.playlists.observePlaylist(removed.id).first())
            assertEquals(listOf(song), data.playlists.observeTracks(kept.id).first())
            assertTrue(data.library.isFavorite(song.id))
            assertEquals(listOf(song), data.nowPlaying.load()?.queue)
        } finally {
            driver.close()
            context.deleteDatabase(DatabaseFactory.FILE_NAME)
        }
    }
}
