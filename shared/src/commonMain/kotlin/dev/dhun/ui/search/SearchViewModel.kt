package dev.dhun.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.DhunResult
import dev.dhun.core.Playlist
import dev.dhun.core.SearchResults
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.DataLayer
import dev.dhun.data.LocalPlaylist
import dev.dhun.domain.AddToPlaylistUseCase
import dev.dhun.domain.RecentlySearchesUseCase
import dev.dhun.domain.ToggleFavoriteUseCase
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.innertube.SearchFilter
import dev.dhun.innertube.parseSearchContinuation
import dev.dhun.innertube.parseSearchResults
import dev.dhun.player.DhunPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 07 — Search screen ViewModel.
 * Debounced suggestions (300ms) → filter chips → paginated results → overflow.
 */
class SearchViewModel(
    private val innerTubeClient: InnerTubeClient,
    private val player: DhunPlayer,
    private val data: DataLayer,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val suggestions: List<String> = emptyList(),
        val showSuggestions: Boolean = false,
        val selectedFilter: SearchFilter = SearchFilter.SONGS,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val tracks: List<Track> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val albums: List<Album> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val error: String? = null,
        val hasMore: Boolean = false,
        val continuationToken: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Overflow menu — null = closed. */
    private val _overflowTrack = MutableStateFlow<Track?>(null)
    val overflowTrack: StateFlow<Track?> = _overflowTrack.asStateFlow()

    val recentSearches: StateFlow<List<String>> = data.search.observeRecentSearches(8)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = data.library.observeFavoriteIds()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val playlists: StateFlow<List<LocalPlaylist>> = data.playlists.observePlaylists()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var suggestionJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(
            query = query,
            showSuggestions = query.isNotEmpty(),
            error = null,
        )
        suggestionJob?.cancel()
        if (query.isNotEmpty()) {
            suggestionJob = viewModelScope.launch {
                delay(SUGGESTION_DEBOUNCE_MS)
                loadSuggestions(query)
            }
        } else {
            _state.value = _state.value.copy(suggestions = emptyList())
        }
    }

    fun onQuerySubmit(query: String) {
        if (query.isBlank()) return
        _state.value = _state.value.copy(query = query.trim(), showSuggestions = false)
        suggestionJob?.cancel()
        searchJob?.cancel()
        viewModelScope.launch {
            data.search.recordSearch(query.trim())
        }
        search(query.trim())
    }

    fun selectSuggestion(suggestion: String) {
        _state.value = _state.value.copy(
            query = suggestion,
            showSuggestions = false,
        )
        suggestionJob?.cancel()
        viewModelScope.launch {
            data.search.recordSearch(suggestion.trim())
        }
        search(suggestion)
    }

    fun onFilterChange(filter: SearchFilter) {
        if (filter == _state.value.selectedFilter) return
        _state.value = _state.value.copy(selectedFilter = filter)
        val query = _state.value.query
        if (query.isNotBlank()) search(query)
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                tracks = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                playlists = emptyList(),
                continuationToken = null,
                hasMore = false,
            )
            when (val result = innerTubeClient.search(query, _state.value.selectedFilter)) {
                is DhunResult.Success -> {
                    val parsed = result.value
                    _state.value = _state.value.copy(
                        loading = false,
                        tracks = parsed.songs,
                        artists = parsed.artists,
                        albums = parsed.albums,
                        playlists = parsed.playlists,
                    )
                }
                is DhunResult.Failure -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun loadMore() {
        val token = _state.value.continuationToken ?: return
        if (_state.value.loadingMore) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            when (val result = innerTubeClient.searchContinuation(token)) {
                is DhunResult.Success -> {
                    val parsed = parseSearchContinuation(result.value)
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        tracks = _state.value.tracks + parsed.songs,
                        artists = _state.value.artists + parsed.artists,
                        albums = _state.value.albums + parsed.albums,
                        playlists = _state.value.playlists + parsed.playlists,
                        continuationToken = parsed.continuation,
                        hasMore = parsed.continuation != null,
                    )
                }
                is DhunResult.Failure -> {
                    _state.value = _state.value.copy(loadingMore = false)
                }
            }
        }
    }

    fun dismissSuggestions() {
        _state.value = _state.value.copy(showSuggestions = false)
    }

    fun clearQuery() {
        _state.value = UiState()
    }

    fun removeRecentSearch(query: String) {
        scope.launch { data.search.removeSearch(query) }
    }

    fun clearRecentSearches() {
        scope.launch { data.search.clearRecentSearches() }
    }

    /** Tap a search result → play it with its category as queue context. */
    fun onTrackClick(track: Track) {
        val tracks = _state.value.tracks
        val index = tracks.indexOf(track).coerceAtLeast(0)
        scope.launch {
            player.prepareQueue(tracks, index)
            data.history.recordPlay(track, dev.dhun.data.PlayContext.SEARCH)
        }
    }

    fun toggleFavorite(track: Track) {
        scope.launch { ToggleFavoriteUseCase(data.library)(track) }
    }

    fun showOverflow(track: Track) { _overflowTrack.value = track }
    fun hideOverflow() { _overflowTrack.value = null }

    fun addToQueue(track: Track) {
        scope.launch {
            player.prepareQueue(player.queue.value + track, player.queue.value.size)
        }
        hideOverflow()
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        scope.launch { AddToPlaylistUseCase(data.playlists)(playlistId, track) }
        hideOverflow()
    }

    private suspend fun loadSuggestions(query: String) {
        when (val result = innerTubeClient.searchSuggestions(query)) {
            is DhunResult.Success -> {
                _state.value = _state.value.copy(suggestions = result.value)
            }
            is DhunResult.Failure -> {
                // Suggestions failing silently — don't surface errors
                _state.value = _state.value.copy(suggestions = emptyList())
            }
        }
    }

    private companion object {
        const val SUGGESTION_DEBOUNCE_MS = 300L
    }
}
