package dev.dhun.desktop.ui

import dev.dhun.core.DhunResult
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.DataLayer
import dev.dhun.domain.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import dev.dhun.innertube.SearchFilter
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Desktop twin of the Android Phase-03 harness view model (no androidx
 * lifecycle on the JVM target — scope is injected and owned by the app).
 * Throwaway; replaced by the real UI in later phases.
 */
class DesktopHarnessViewModel(
    private val provider: MusicProvider,
    private val data: DataLayer,
    private val scope: CoroutineScope,
) {

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
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val recentlyPlayed: StateFlow<List<Track>> = data.history.observeRecentlyPlayed(10)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val recentSearches: StateFlow<List<String>> = data.search.observeRecentSearches(8)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun toggleFavorite(track: Track) {
        scope.launch { ToggleFavoriteUseCase(data.library)(track) }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query, error = null)
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
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
