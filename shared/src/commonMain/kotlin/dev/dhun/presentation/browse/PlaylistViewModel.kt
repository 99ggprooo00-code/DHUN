package dev.dhun.presentation.browse

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlaylistRepository
import dev.dhun.player.DhunPlayer
import dev.dhun.player.resetModesForNewQueue
import dev.dhun.presentation.MutationState
import dev.dhun.presentation.MutationTracker
import dev.dhun.data.SettingsRepository
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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

    private data class ViewState(
        val ui: PlaylistUiState = PlaylistUiState.Loading,
        val generation: Long = 0,
        val loadingMore: Boolean = false,
        val pageError: String? = null,
        val completedTokens: Set<String> = emptySet(),
        val emptyPageCount: Int = 0,
    )

    private val viewState = MutableStateFlow(ViewState())
    val state: StateFlow<PlaylistUiState> = viewState.map { it.ui }
        .stateIn(scope, SharingStarted.Eagerly, PlaylistUiState.Loading)
    val isLoadingMore: StateFlow<Boolean> = viewState.map { it.loadingMore }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val loadMoreError: StateFlow<String?> = viewState.map { it.pageError }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private var loadJob: Job? = null
    private var pageJob: Job? = null

    /** True once a local playlist under view has been deleted (UI pops). */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()
    private val _deletion = MutableStateFlow(PlaylistDeletionState())
    val deletion: StateFlow<PlaylistDeletionState> = _deletion.asStateFlow()

    /**
     * Pending/failure state for the row edits on a local playlist (remove,
     * reorder). Both used to be `scope.launch { playlists.… }` with no
     * handling, so a failed swipe silently left the row in place and a failed
     * drag snapped the list back with no explanation.
     */
    private val mutations = MutationTracker(scope)
    val mutation: StateFlow<MutationState> = mutations.state
    fun dismissMutationError() = mutations.dismissError()

    init {
        if (isLocal) {
            scope.launch {
                combine(
                    playlists.observePlaylist(playlistId),
                    playlists.observeTracks(playlistId),
                ) { playlist, tracks -> PlaylistUiState.Local(playlist, tracks) }
                    .collect { local -> viewState.update { current -> current.copy(ui = local) } }
            }
        } else {
            load()
        }
    }

    fun load() {
        if (isLocal) return
        val request = viewState.updateAndGet {
            ViewState(ui = PlaylistUiState.Loading, generation = it.generation + 1)
        }
        loadJob?.cancel()
        pageJob?.cancel()
        loadJob = scope.launch {
            try {
                val next = when (val result = provider.playlistPage(playlistId)) {
                    is DhunResult.Success -> PlaylistUiState.Remote(result.value)
                    is DhunResult.Failure -> PlaylistUiState.Error(result.error.toUserMessage())
                }
                viewState.update { current ->
                    if (current.generation != request.generation) current else current.copy(ui = next)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = PlaylistUiState.Error(
                    DhunError.Unknown("${error::class.simpleName ?: "Exception"}: ${error.message.orEmpty()}")
                        .toUserMessage(),
                )
                viewState.update { current ->
                    if (current.generation != request.generation) current else current.copy(ui = failure)
                }
            }
        }
    }

    fun loadMore() = requestMore(retry = false)
    fun retryLoadMore() = requestMore(retry = true)

    private fun requestMore(retry: Boolean) {
        if (isLocal) return
        val before = viewState.value
        val detail = (before.ui as? PlaylistUiState.Remote)?.detail ?: return
        val token = detail.continuationToken ?: return
        if (before.loadingMore || token in before.completedTokens || (!retry && before.pageError != null)) return
        val request = before.copy(
            loadingMore = true,
            pageError = null,
            emptyPageCount = if (retry) 0 else before.emptyPageCount,
        )
        if (!viewState.compareAndSet(before, request)) return
        pageJob = scope.launch {
            try {
                val result = provider.playlistContinuation(token)
                viewState.update { latest ->
                    if (latest.generation != request.generation) return@update latest
                    val current = (latest.ui as? PlaylistUiState.Remote)?.detail ?: return@update latest
                    when (result) {
                        is DhunResult.Success -> {
                            val seen = current.tracks.mapTo(HashSet()) { it.id }
                            val tracks = current.tracks + result.value.tracks.filter { seen.add(it.id) }
                            val used = latest.completedTokens + token
                            val nextToken = result.value.continuationToken
                                ?.takeIf { it.isNotBlank() && it !in used }
                            val emptyPages = if (tracks.size == current.tracks.size) latest.emptyPageCount + 1 else 0
                            latest.copy(
                                ui = PlaylistUiState.Remote(current.copy(
                                    tracks = tracks,
                                    continuationToken = nextToken,
                                )),
                                completedTokens = used,
                                emptyPageCount = emptyPages,
                                pageError = if (nextToken != null && emptyPages >= MAX_EMPTY_PAGES) {
                                    "No new tracks arrived. Retry or reload the playlist."
                                } else null,
                            )
                        }
                        is DhunResult.Failure -> latest.copy(pageError = result.error.toUserMessage())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = DhunError.Unknown("${error::class.simpleName ?: "Exception"}: ${error.message.orEmpty()}")
                viewState.update { latest ->
                    if (latest.generation != request.generation) latest
                    else latest.copy(pageError = failure.toUserMessage())
                }
            } finally {
                viewState.update { latest ->
                    if (latest.generation != request.generation) latest else latest.copy(loadingMore = false)
                }
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

    private fun currentTracks(): List<Track>? = when (val s = viewState.value.ui) {
        is PlaylistUiState.Remote -> s.detail.tracks
        is PlaylistUiState.Local -> s.tracks
        else -> null
    }

    /* ---------------- local CRUD ---------------- */

    /**
     * Renames the local playlist. Suspends and propagates failure so the dialog
     * can keep itself open with a retry — closing it on the tap made a failed
     * rename look identical to a successful one.
     */
    suspend fun rename(newName: String) {
        if (!isLocal) return
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == viewState.value.ui.let { (it as? PlaylistUiState.Local)?.playlist?.name }) return
        playlists.rename(playlistId, trimmed)
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
        while (true) {
            val current = _deletion.value
            if (current.isDeleting) return
            if (_deletion.compareAndSet(current, PlaylistDeletionState())) return
        }
    }

    fun removeTrack(track: Track) {
        if (!isLocal) return
        mutations.run(
            key = "playlist-remove:${track.id}",
            failureMessage = "Could not remove \"${track.title}\" from this playlist. Try again.",
        ) { playlists.removeTrack(playlistId, track.id) }
    }

    fun moveTrack(from: Int, to: Int) {
        if (!isLocal) return
        mutations.run(
            key = "playlist-move",
            failureMessage = "Could not save the new order. The list was put back; try again.",
        ) { playlists.move(playlistId, from, to) }
    }

    fun close() = scope.cancel()

    private companion object {
        const val MAX_EMPTY_PAGES = 2
    }
}
