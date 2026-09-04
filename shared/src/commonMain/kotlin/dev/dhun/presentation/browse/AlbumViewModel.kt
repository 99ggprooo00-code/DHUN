package dev.dhun.presentation.browse

import dev.dhun.core.AlbumDetail
import dev.dhun.core.DhunResult
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

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Success(val detail: AlbumDetail) : AlbumUiState
    data class Error(val message: String) : AlbumUiState
}

/** Album page screen model (Phase 09). [close] when the page is popped. */
class AlbumViewModel(
    private val provider: MusicProvider,
    private val player: DhunPlayer,
    private val albumId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        scope.launch {
            _state.value = AlbumUiState.Loading
            _state.value = when (val r = provider.albumPage(albumId)) {
                is DhunResult.Success -> AlbumUiState.Success(r.value)
                is DhunResult.Failure -> AlbumUiState.Error(r.error.toUserMessage())
            }
        }
    }

    /** Play the album in track order from [index]. */
    fun play(index: Int = 0) {
        val detail = (_state.value as? AlbumUiState.Success)?.detail ?: return
        if (detail.tracks.isEmpty()) return
        scope.launch {
            player.prepareQueue(detail.tracks, index.coerceIn(0, detail.tracks.size - 1), playWhenReady = true)
        }
    }

    /** Shuffle-play the whole album. */
    fun playShuffled() {
        val detail = (_state.value as? AlbumUiState.Success)?.detail ?: return
        if (detail.tracks.isEmpty()) return
        scope.launch {
            player.prepareQueue(detail.tracks, 0, playWhenReady = true)
            player.setShuffle(true)
        }
    }

    fun close() = scope.cancel()
}
