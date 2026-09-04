package dev.dhun.presentation.browse

import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunResult
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.player.DhunPlayer
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data class Success(val page: ArtistPage) : ArtistUiState
    data class Error(val message: String) : ArtistUiState
}

/**
 * Artist page screen model (Phase 09). Owns its scope — call [close] when the
 * page is popped off the detail stack.
 */
class ArtistViewModel(
    private val provider: MusicProvider,
    private val player: DhunPlayer,
    private val artistId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    private val _radioLoading = MutableStateFlow(false)
    val radioLoading: StateFlow<Boolean> = _radioLoading.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _state.value = ArtistUiState.Loading
            _state.value = when (val r = provider.artistPage(artistId)) {
                is DhunResult.Success -> ArtistUiState.Success(r.value)
                is DhunResult.Failure -> ArtistUiState.Error(r.error.toUserMessage())
            }
        }
    }

    /** Play the top-songs list from [index] as the queue context. */
    fun playTopSongs(index: Int = 0) {
        val page = (_state.value as? ArtistUiState.Success)?.page ?: return
        val tracks = page.topSongs
        if (tracks.isEmpty()) return
        scope.launch {
            player.prepareQueue(tracks, index.coerceIn(0, tracks.size - 1), playWhenReady = true)
        }
    }

    /** Shuffle-play the top songs. */
    fun playTopSongsShuffled() {
        val page = (_state.value as? ArtistUiState.Success)?.page ?: return
        if (page.topSongs.isEmpty()) return
        scope.launch {
            player.prepareQueue(page.topSongs, 0, playWhenReady = true)
            player.setShuffle(true)
        }
    }

    /** Radio seeded by the artist's most-played track (InnerTube /next). */
    fun startRadio() {
        val page = (_state.value as? ArtistUiState.Success)?.page ?: return
        val seed = page.topSongs.firstOrNull() ?: return
        _radioLoading.value = true
        scope.launch {
            try {
                when (val r = provider.relatedTracks(seed.id)) {
                    is DhunResult.Success ->
                        if (r.value.isNotEmpty()) {
                            player.prepareQueue(listOf(seed) + r.value, 0, playWhenReady = true)
                        }
                    is DhunResult.Failure -> {
                        // Radio failed silently is fine — the tracks list stays.
                    }
                }
            } finally {
                _radioLoading.value = false
            }
        }
    }

    fun close() = scope.cancel()
}
