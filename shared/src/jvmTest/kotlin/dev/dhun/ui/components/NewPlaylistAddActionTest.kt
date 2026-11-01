package dev.dhun.ui.components

import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlaylistRepository
import dev.dhun.presentation.MutationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewPlaylistAddActionTest {

    private suspend fun await(check: () -> Boolean) {
        withTimeout(5_000) { while (!check()) delay(5) }
    }

    @Test
    fun addFailureAfterCreateRetriesMembershipOnTheSamePlaylist(): Unit = runBlocking {
        val driver = DatabaseDriverFactory.inMemory().createDriver()
        val data = DataLayer(DatabaseFactory.create(driver))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var creates = 0
        var additions = 0
        val repository = object : PlaylistRepository by data.playlists {
            override suspend fun create(name: String, description: String?): LocalPlaylist {
                creates++
                return data.playlists.create(name, description)
            }

            override suspend fun addTrack(playlistId: String, track: Track): Boolean {
                additions++
                if (additions == 1) throw IllegalStateException("injected membership failure")
                return data.playlists.addTrack(playlistId, track)
            }
        }
        val tracker = MutationTracker(scope)
        val action = NewPlaylistAddAction(repository, tracker)
        val song = Track(id = "song", title = "Song", artistName = "Artist")
        val successes = mutableListOf<String>()
        try {
            assertTrue(action.run(song, "Only one") { successes += it })
            await { tracker.state.value.errorMessage != null }
            assertEquals(1, creates)
            assertEquals(1, additions)
            assertEquals(1, data.playlists.observePlaylists().first().size)
            assertTrue(successes.isEmpty(), "a failed add cannot report a successful add")
            assertTrue(action.hasPendingCreation)
            val created = data.playlists.observePlaylists().first().single()
            assertFalse(data.playlists.contains(created.id, song.id))

            assertTrue(action.run(song, "Only one") { successes += it })
            await { successes.isNotEmpty() }
            assertEquals(1, creates, "retry must not create another playlist")
            assertEquals(2, additions)
            assertEquals(listOf("Only one"), successes)
            assertEquals(1, data.playlists.observePlaylists().first().size)
            assertTrue(data.playlists.contains(created.id, song.id))
            assertFalse(action.hasPendingCreation)
            assertNull(tracker.state.value.errorMessage)
        } finally {
            scope.cancel()
            driver.close()
        }
    }
}
