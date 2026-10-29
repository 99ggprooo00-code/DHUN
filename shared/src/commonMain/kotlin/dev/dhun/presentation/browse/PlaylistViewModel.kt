package dev.dhun.presentation.browse

import dev.dhun.core.DhunResult
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlaylistRepository
import dev.dhun.player.DhunPlayer
import dev.dhun.player.resetModesForNewQueue
import dev.dhun.data.SettingsRepository
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState
    data class Error(val message: String) : PlaylistUiState

    /** YTM playlist page (remote, read-only in v1 beyond play/queue). */
    data class Remote(val detail: PlaylistDetail) : PlaylistUiState

    /** Local (SQLDelight) playlist — full CRUD. Null playlist = deleted. */
    data class Local(val playlist: LocalPlaylist?, val tracks: List<Track>) : PlaylistUiState
}

data class PlaylistDeletionState(
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Playlist page screen model (Phase 09) — handles a YTM playlist (browse)
 * or a local playlist (SQLDelight, editable: rename/delete/remove/reorder).
 * [close] when the page is popped.
 */
class PlaylistViewModel(
    private val provider: MusicProvider,
    private val playlists: PlaylistRepository,
    private val player: DhunPlayer,
    private val playlistId: String,
    private val isLocal: Boolean,
    private val settings: SettingsRepository? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    /** True once a local playlist under view has been deleted (UI pops). */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()
    private val _deletion = MutableStateFlow(PlaylistDeletionState())
    val deletion: StateFlow<PlaylistDeletionState> = _deletion.asStateFlow()

    init {
        if (isLocal) {
            scope.launch {
                combine(
                    playlists.observePlaylist(playlistId),
                    playlists.observeTracks(playlistId),
                ) { playlist, tracks -> PlaylistUiState.Local(playlist, tracks) }
                    .collect { _state.value = it }
            }
        } else {
            load()
        }
    }

    fun load() {
        if (isLocal) return
        scope.launch {
            _state.value = PlaylistUiState.Loading
            _state.value = when (val r = provider.playlistPage(playlistId)) {
                is DhunResult.Success -> PlaylistUiState.Remote(r.value)
                is DhunResult.Failure -> PlaylistUiState.Error(r.error.toUserMessage())
            }
        }
    }

    /* ---------------- playback ---------------- */

    fun play(index: Int = 0) {
        val tracks = currentTracks() ?: return
        if (tracks.isEmpty()) return
        scope.launch {
            resetModesForNewQueue(player, settings)
            player.prepareQueue(tracks, index.coerceIn(0, tracks.size - 1), playWhenReady = true)
        }
    }

    fun playShuffled() {
        val tracks = currentTracks() ?: return
        if (tracks.isEmpty()) return
        scope.launch {
            resetModesForNewQueue(player, settings)
            player.prepareQueue(tracks, 0, playWhenReady = true)
            player.setShuffle(true)
        }
    }

    private fun currentTracks(): List<Track>? = when (val s = _state.value) {
        is PlaylistUiState.Remote -> s.detail.tracks
        is PlaylistUiState.Local -> s.tracks
        else -> null
    }

    /* ---------------- local CRUD ---------------- */

    fun rename(newName: String) {
        if (!isLocal || newName.isBlank()) return
        scope.launch { playlists.rename(playlistId, newName.trim()) }
    }

    fun delete() {
        if (!isLocal) return
        // Set the gate synchronously, before launch/Compose can process a second tap.
        while (true) {
            val current = _deletion.value
            if (current.isDeleting || _deleted.value) return
            if (_deletion.compareAndSet(current, PlaylistDeletionState(isDeleting = true))) break
        }
        scope.launch {
            try {
                playlists.delete(playlistId)
                _deleted.value = true // only after the repository transaction returns
                _deletion.value = PlaylistDeletionState()
            } catch (cancelled: CancellationException) {
                _deletion.value = PlaylistDeletionState(
                    errorMessage = "Deletion was interrupted. Reopen the library to check whether the playlist remains.",
                )
                throw cancelled
            } catch (_: Exception) {
                // The outcome may be uncertain if the failure happened after a commit.
                _deletion.value = PlaylistDeletionState(
                    errorMessage = "Could not confirm deletion. Check the playlist and try again.",
                )
            }
        }
    }

    fun clearDeletionError() {
        if (!_deletion.value.isDeleting) _deletion.value = PlaylistDeletionState()
    }

    fun removeTrack(track: Track) {
        if (!isLocal) return
        scope.launch { playlists.removeTrack(playlistId, track.id) }
    }

    fun moveTrack(from: Int, to: Int) {
        if (!isLocal) return
        scope.launch { playlists.move(playlistId, from, to) }
    }

    fun close() = scope.cancel()
}
