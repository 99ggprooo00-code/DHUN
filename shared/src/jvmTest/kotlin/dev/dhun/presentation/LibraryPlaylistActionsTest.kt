package dev.dhun.presentation

import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlaylistRepository
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.library.LibraryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Rename and delete of a saved playlist **from the Library list**.
 *
 * The Library's playlist list had no management affordances at all: the two
 * ViewModel entry points existed but nothing called them, so a playlist could
 * only be renamed or deleted after opening its detail page. The row menu now
 * drives them through model state ([LibraryViewModel.renamingPlaylist],
 * [LibraryViewModel.deletingPlaylist]) instead of local `visible` flags, which
 * is what makes every step below assertable without a rendered frame:
 *
 *  - opening the action targets the playlist that was tapped,
 *  - the list reflects the new name / the removed row without a restart,
 *  - a failed write leaves the stored name and the row in place and keeps the
 *    dialog open with its own error,
 *  - cancelling deletes nothing, and a second tap while a write runs is ignored.
 *
 * The Compose rendering of the two dialogs and the ⋮ menu is shared with the
 * playlist detail page (`ui/components/PlaylistDialogs.kt`, the row's anchored
 * `TrackMenuSurface` menu) and is not rendered in these JVM tests.
 */
class LibraryPlaylistActionsTest {

    private fun data() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    /** Smallest possible player: these tests never play anything. */
    private class FakePlayer : DhunPlayer {
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val currentTrack = MutableStateFlow<Track?>(null)
        override val queue = MutableStateFlow<List<Track>>(emptyList())
        override val positionMs = MutableStateFlow(0L)
        override val durationMs = MutableStateFlow(0L)
        override val currentQueueIndex = MutableStateFlow(-1)
        override val repeatMode = MutableStateFlow(RepeatMode.OFF)
        override val shuffleEnabled = MutableStateFlow(false)
        override val volume = MutableStateFlow(1f)

        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            queue.value = tracks
            currentTrack.value = tracks.getOrNull(startIndex)
            currentQueueIndex.value = startIndex
        }
        override fun addNext(track: Track) { queue.value = queue.value + track }
        override fun addToQueue(track: Track) { queue.value = queue.value + track }
        override fun playAt(index: Int) {}
        override fun removeFromQueue(index: Int) {}
        override fun moveInQueue(from: Int, to: Int) {}
        override fun playPause() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) { this.positionMs.value = positionMs }
        override fun setRepeatMode(mode: RepeatMode) { repeatMode.value = mode }
        override fun setShuffle(enabled: Boolean) { shuffleEnabled.value = enabled }
        override fun setVolume(volume: Float) { this.volume.value = volume }
        override fun stop() {}
    }

    private class Harness {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, error -> uncaught += error },
        )

        fun close() = scope.cancel()
    }

    private fun viewModel(
        data: DataLayer,
        scope: CoroutineScope,
        playlists: PlaylistRepository = data.playlists,
    ) = LibraryViewModel(playlists, data.library, data.history, FakePlayer(), scope)

    /**
     * Async assertion deadline with the observed state in the failure message —
     * a bare timeout names neither the wait nor what it was stuck on, which is
     * all CI can show from this sandbox.
     */
    private suspend fun eventually(
        timeoutMs: Long = 15_000,
        label: () -> String = { "unlabelled wait" },
        check: suspend () -> Boolean,
    ) {
        val satisfied = withTimeoutOrNull(timeoutMs) {
            while (!check()) delay(10)
            true
        } ?: false
        if (!satisfied) fail("eventually(${timeoutMs}ms) timed out: ${label()}")
    }

    @Test
    fun openingRenameTargetsThePlaylistThatWasTapped(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        try {
            val gym = d.playlists.create("Gym")
            val chill = d.playlists.create("Chill")
            val vm = viewModel(d, h.scope)
            eventually { vm.playlistsFlow.value.size == 2 }

            vm.beginRenamePlaylist(chill)
            assertEquals(chill.id, vm.renamingPlaylist.value?.id, "the tapped row owns the dialog")
            assertEquals("Chill", vm.renamingPlaylist.value?.name)
            // Opening rename must not open the delete confirmation or delete.
            assertNull(vm.deletingPlaylist.value)
            assertEquals(2, d.playlists.observePlaylistsOnce().size)
            assertTrue(d.playlists.observePlaylistsOnce().any { it.id == gym.id })
        } finally {
            h.close()
        }
    }

    @Test
    fun aSuccessfulRenameUpdatesTheLibraryList(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginRenamePlaylist(playlist)
            vm.confirmRenamePlaylist("  Workout  ")
            eventually(label = { "list still shows ${vm.playlistsFlow.value.map { it.name }}" }) {
                vm.renamingPlaylist.value == null &&
                    vm.playlistsFlow.value.firstOrNull { it.id == playlist.id }?.name == "Workout"
            }
            assertNull(vm.renamingPlaylist.value, "the dialog closes only after the write returned")
            assertNull(vm.mutation.value.errorMessage)
            assertEquals("Workout", d.playlists.observePlaylistOnce(playlist.id)?.name)
        } finally {
            h.close()
        }
    }

    @Test
    fun reSavingTheSameNameClosesWithoutAWrite(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var writes = 0
        val counting = object : PlaylistRepository by d.playlists {
            override suspend fun rename(id: String, newName: String) {
                writes++
                d.playlists.rename(id, newName)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, counting)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginRenamePlaylist(playlist)
            vm.confirmRenamePlaylist("  Gym  ")
            assertNull(vm.renamingPlaylist.value, "an unchanged name is not an error; the dialog closes")
            assertEquals(0, writes, "nothing to save means no write")
            assertEquals("Gym", d.playlists.observePlaylistOnce(playlist.id)?.name)
        } finally {
            h.close()
        }
    }

    @Test
    fun aBlankNameIsRefusedAndTheDialogStaysOpen(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var writes = 0
        val counting = object : PlaylistRepository by d.playlists {
            override suspend fun rename(id: String, newName: String) {
                writes++
                d.playlists.rename(id, newName)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, counting)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginRenamePlaylist(playlist)
            vm.confirmRenamePlaylist("   ")
            assertEquals(playlist.id, vm.renamingPlaylist.value?.id, "the dialog is still open for a real name")
            assertEquals(0, writes, "a blank name never reaches the store")
            assertNull(vm.mutation.value.errorMessage)
            assertEquals("Gym", d.playlists.observePlaylistOnce(playlist.id)?.name)
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedRenameKeepsTheStoredNameAndTheDialog(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var attempts = 0
        val failsOnce = object : PlaylistRepository by d.playlists {
            override suspend fun rename(id: String, newName: String) {
                attempts++
                if (attempts == 1) error("disk full")
                d.playlists.rename(id, newName)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, failsOnce)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginRenamePlaylist(playlist)
            vm.confirmRenamePlaylist("Workout")
            val key = vm.renameActionKey(playlist.id)
            eventually(label = { "mutation=${vm.mutation.value}" }) { vm.mutation.value.errorFor(key) != null }

            // The list is observed from the store, so the old name is still what
            // is shown — the screen never claims a name that was not saved.
            assertEquals("Gym", vm.playlistsFlow.value.first { it.id == playlist.id }.name)
            assertEquals("Gym", d.playlists.observePlaylistOnce(playlist.id)?.name)
            assertEquals(
                playlist.id,
                vm.renamingPlaylist.value?.id,
                "a failed save keeps the dialog open with its inline error for a retry",
            )
            assertTrue(vm.playlistDialogOwnsError(vm.mutation.value), "the dialog shows this failure, not the banner")
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")

            vm.confirmRenamePlaylist("Workout")
            eventually(label = { "rename retry: mutation=${vm.mutation.value}" }) {
                vm.renamingPlaylist.value == null &&
                    vm.playlistsFlow.value.firstOrNull { it.id == playlist.id }?.name == "Workout"
            }
            assertEquals(2, attempts, "the retry performs exactly one new write")
            assertNull(vm.mutation.value.errorMessage)
            assertEquals("Workout", d.playlists.observePlaylistOnce(playlist.id)?.name)
        } finally {
            h.close()
        }
    }

    @Test
    fun openingDeleteAsksForConfirmationAndDeletesNothing(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var deletes = 0
        val counting = object : PlaylistRepository by d.playlists {
            override suspend fun delete(id: String) {
                deletes++
                d.playlists.delete(id)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, counting)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginDeletePlaylist(playlist)
            assertEquals(playlist.id, vm.deletingPlaylist.value?.id, "the confirmation names the tapped playlist")
            assertEquals("Gym", vm.deletingPlaylist.value?.name)
            assertEquals(0, deletes, "opening the confirmation deletes nothing")
            assertEquals(1, vm.playlistsFlow.value.size)
        } finally {
            h.close()
        }
    }

    @Test
    fun cancellingTheConfirmationDeletesNothing(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var deletes = 0
        val counting = object : PlaylistRepository by d.playlists {
            override suspend fun delete(id: String) {
                deletes++
                d.playlists.delete(id)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, counting)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginDeletePlaylist(playlist)
            vm.cancelDeletePlaylist()
            assertNull(vm.deletingPlaylist.value)
            assertEquals(0, deletes, "cancel must not reach the store")
            assertEquals(1, vm.playlistsFlow.value.size, "the playlist is still there")
            assertTrue(d.playlists.observePlaylistsOnce().any { it.id == playlist.id })
        } finally {
            h.close()
        }
    }

    @Test
    fun confirmingTheDeletionRemovesThePlaylistFromTheLibrary(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        try {
            val keep = d.playlists.create("Chill")
            val doomed = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope)
            eventually { vm.playlistsFlow.value.size == 2 }

            vm.beginDeletePlaylist(doomed)
            vm.confirmDeletePlaylist()
            eventually(label = { "list still shows ${vm.playlistsFlow.value.map { it.name }}" }) {
                vm.deletingPlaylist.value == null && vm.playlistsFlow.value.none { it.id == doomed.id }
            }
            assertNull(vm.deletingPlaylist.value, "the dialog closes only after the delete returned")
            assertNull(vm.mutation.value.errorMessage)
            assertEquals(listOf(keep.id), vm.playlistsFlow.value.map { it.id })
            assertNull(d.playlists.observePlaylistOnce(doomed.id))
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedDeletionKeepsThePlaylistAndReportsItInItsDialog(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var attempts = 0
        val failsOnce = object : PlaylistRepository by d.playlists {
            override suspend fun delete(id: String) {
                attempts++
                if (attempts == 1) error("database is locked")
                d.playlists.delete(id)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, failsOnce)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginDeletePlaylist(playlist)
            vm.confirmDeletePlaylist()
            val key = vm.deleteActionKey(playlist.id)
            eventually(label = { "mutation=${vm.mutation.value}" }) { vm.mutation.value.errorFor(key) != null }

            assertEquals(1, vm.playlistsFlow.value.size, "a failed delete must not remove the row")
            assertEquals("Gym", d.playlists.observePlaylistOnce(playlist.id)?.name)
            assertEquals(playlist.id, vm.deletingPlaylist.value?.id, "the confirmation stays open with its error")
            assertTrue(vm.playlistDialogOwnsError(vm.mutation.value))
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")

            vm.confirmDeletePlaylist()
            eventually(label = { "delete retry: mutation=${vm.mutation.value}" }) {
                vm.deletingPlaylist.value == null && vm.playlistsFlow.value.none { it.id == playlist.id }
            }
            assertEquals(2, attempts, "the retry performs exactly one new delete")
            assertNull(vm.mutation.value.errorMessage)
            assertNull(d.playlists.observePlaylistOnce(playlist.id))
        } finally {
            h.close()
        }
    }

    @Test
    fun aSecondSaveWhileTheRenameIsStillWritingIsIgnored(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var writes = 0
        val gate = CompletableDeferred<Unit>()
        val gated = object : PlaylistRepository by d.playlists {
            override suspend fun rename(id: String, newName: String) {
                writes++
                gate.await()
                d.playlists.rename(id, newName)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, gated)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginRenamePlaylist(playlist)
            vm.confirmRenamePlaylist("Workout")
            assertTrue(
                vm.mutation.value.isRunning(vm.renameActionKey(playlist.id)),
                "the write is pending before the second tap can land",
            )
            // A second tap on Save (or a stray recomposition) must not queue a
            // second write, and the dialog cannot be dismissed mid-write.
            vm.confirmRenamePlaylist("Workout")
            vm.cancelRenamePlaylist()
            assertEquals(playlist.id, vm.renamingPlaylist.value?.id, "a pending write cannot be dismissed")
            assertEquals(1, writes)

            gate.complete(Unit)
            eventually { vm.renamingPlaylist.value == null }
            eventually { vm.playlistsFlow.value.first { it.id == playlist.id }.name == "Workout" }
            assertEquals(1, writes)
        } finally {
            h.close()
        }
    }

    @Test
    fun aSecondDeleteWhileTheFirstIsStillWritingIsIgnored(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var deletes = 0
        val gate = CompletableDeferred<Unit>()
        val gated = object : PlaylistRepository by d.playlists {
            override suspend fun delete(id: String) {
                deletes++
                gate.await()
                d.playlists.delete(id)
            }
        }
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope, gated)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            vm.beginDeletePlaylist(playlist)
            vm.confirmDeletePlaylist()
            val key = vm.deleteActionKey(playlist.id)
            assertTrue(vm.mutation.value.isRunning(key), "the delete is pending before a second tap")
            vm.confirmDeletePlaylist()
            vm.cancelDeletePlaylist()
            assertEquals(playlist.id, vm.deletingPlaylist.value?.id, "a pending delete cannot be dismissed")
            assertEquals(1, deletes, "a second tap must not queue another delete")
            assertTrue(vm.playlistsFlow.value.any { it.id == playlist.id }, "nothing is removed before the store commits")

            gate.complete(Unit)
            eventually {
                vm.deletingPlaylist.value == null && vm.playlistsFlow.value.none { it.id == playlist.id }
            }
            assertEquals(1, deletes)
            assertNull(vm.mutation.value.errorFor(key))
        } finally {
            h.close()
        }
    }

    @Test
    fun onlyPlaylistDialogsSuppressTheScreenBanner(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        try {
            val playlist = d.playlists.create("Gym")
            val vm = viewModel(d, h.scope)
            eventually { vm.playlistsFlow.value.any { it.id == playlist.id } }

            // Every other single-row failure still belongs to the banner.
            assertFalse(vm.playlistDialogOwnsError(MutationState(errorMessage = "x", errorKey = "like:t1")))
            assertFalse(vm.playlistDialogOwnsError(MutationState(errorMessage = "x")))
            val renameError = MutationState(errorMessage = "x", errorKey = vm.renameActionKey(playlist.id))
            val deleteError = MutationState(errorMessage = "x", errorKey = vm.deleteActionKey(playlist.id))
            // A closed dialog cannot suppress the only remaining visible error.
            assertFalse(vm.playlistDialogOwnsError(renameError))
            assertFalse(vm.playlistDialogOwnsError(deleteError))
            vm.beginRenamePlaylist(playlist)
            assertTrue(vm.playlistDialogOwnsError(renameError))
            assertFalse(vm.playlistDialogOwnsError(deleteError))
            vm.cancelRenamePlaylist()
            vm.beginDeletePlaylist(playlist)
            assertTrue(vm.playlistDialogOwnsError(deleteError))
            assertFalse(vm.playlistDialogOwnsError(renameError))
        } finally {
            h.close()
        }
    }
}

/* One-shot reads for assertions; the repository itself only exposes observers. */
private suspend fun PlaylistRepository.observePlaylistsOnce(): List<LocalPlaylist> = observePlaylists().first()

private suspend fun PlaylistRepository.observePlaylistOnce(id: String): LocalPlaylist? = observePlaylist(id).first()
