package dev.dhun.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dhun.core.DhunResult
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.DataLayer
import dev.dhun.domain.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import dev.dhun.innertube.SearchFilter
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HarnessViewModel(
    private val provider: MusicProvider,
    private val data: DataLayer,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val loading: Boolean = false,
        val tracks: List<Track> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /* ---- Phase 05 hooks (verification of the data layer in-app) ---- */

    val favoriteIds: StateFlow<Set<String>> = data.library.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val recentlyPlayed: StateFlow<List<Track>> = data.history.observeRecentlyPlayed(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSearches: StateFlow<List<String>> = data.search.observeRecentSearches(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { ToggleFavoriteUseCase(data.library)(track) }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query, error = null)
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            data.search.recordSearch(query)
            when (val result = provider.search(query, SearchFilter.SONGS)) {
                is DhunResult.Success -> _state.value = _state.value.copy(
                    loading = false,
                    tracks = result.value.songs,
                    error = if (result.value.songs.isEmpty()) "No songs found for \"$query\"." else null,
                )
                is DhunResult.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    error = result.error.toUserMessage(),
                )
            }
        }
    }
}
