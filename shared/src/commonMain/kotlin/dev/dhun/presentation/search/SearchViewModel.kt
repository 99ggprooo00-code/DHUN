package dev.dhun.presentation.search

import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.DhunResult
import dev.dhun.core.Playlist
import dev.dhun.core.SearchResults
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.LibraryRepository
import dev.dhun.data.PlaylistRepository
import dev.dhun.data.SearchRepository
import dev.dhun.domain.AddToPlaylistUseCase
import dev.dhun.domain.RecentSearchesUseCase
import dev.dhun.domain.ToggleFavoriteUseCase
import dev.dhun.innertube.SearchFilter
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchResultsUiState {
    data object Idle : SearchResultsUiState
    data object Loading : SearchResultsUiState
    data class Success(val results: SearchResults) : SearchResultsUiState
    data class Error(val message: String) : SearchResultsUiState
    data class Empty(val query: String) : SearchResultsUiState
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val provider: MusicProvider,
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val scope: CoroutineScope,
) {
    private val recentSearchesUseCase = RecentSearchesUseCase(searchRepository)
    private val toggleFavoriteUseCase = ToggleFavoriteUseCase(libraryRepository)
    private val addToPlaylistUseCase = AddToPlaylistUseCase(playlistRepository)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SearchFilter.SONGS)
    val selectedFilter: StateFlow<SearchFilter> = _selectedFilter.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _resultsState = MutableStateFlow<SearchResultsUiState>(SearchResultsUiState.Idle)
    val resultsState: StateFlow<SearchResultsUiState> = _resultsState.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchesUseCase.observe(15)
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val favoriteIds: StateFlow<Set<String>> = libraryRepository.observeFavoriteIds()
        .stateIn(scope, SharingStarted.Lazily, emptySet())

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        // Debounce search suggestions by 300ms
        scope.launch {
            _query
                .debounce(300L)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.trim().length >= 2 && _resultsState.value !is SearchResultsUiState.Success) {
                        fetchSuggestions(q.trim())
                    } else {
                        _suggestions.value = emptyList()
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            _resultsState.value = SearchResultsUiState.Idle
        }
    }

    fun onFilterSelected(filter: SearchFilter) {
        if (_selectedFilter.value == filter) return
        _selectedFilter.value = filter
        if (_query.value.isNotBlank()) {
            performSearch(_query.value, filter)
        }
    }

    fun onSuggestionSelected(suggestion: String) {
        _query.value = suggestion
        _suggestions.value = emptyList()
        performSearch(suggestion, _selectedFilter.value)
    }

    fun onSearchSubmitted() {
        val q = _query.value.trim()
        if (q.isNotBlank()) {
            _suggestions.value = emptyList()
            performSearch(q, _selectedFilter.value)
        }
    }

    fun clearQuery() {
        _query.value = ""
        _suggestions.value = emptyList()
        _resultsState.value = SearchResultsUiState.Idle
    }

    fun deleteRecentSearch(query: String) {
        scope.launch {
            recentSearchesUseCase.remove(query)
        }
    }

    fun clearAllRecentSearches() {
        scope.launch {
            recentSearchesUseCase.clear()
        }
    }

    private fun fetchSuggestions(q: String) {
        suggestionJob?.cancel()
        suggestionJob = scope.launch {
            when (val r = provider.searchSuggestions(q)) {
                is DhunResult.Success -> {
                    _suggestions.value = r.value
                }
                is DhunResult.Failure -> {
                    _suggestions.value = emptyList()
                }
            }
        }
    }

    fun performSearch(query: String, filter: SearchFilter = _selectedFilter.value) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        searchJob?.cancel()
        searchJob = scope.launch {
            _resultsState.value = SearchResultsUiState.Loading
            recentSearchesUseCase.record(cleanQuery)

            when (val r = provider.search(cleanQuery, filter)) {
                is DhunResult.Success -> {
                    val results = r.value
                    if (results.isEmpty()) {
                        _resultsState.value = SearchResultsUiState.Empty(cleanQuery)
                    } else {
                        _resultsState.value = SearchResultsUiState.Success(results)
                    }
                }
                is DhunResult.Failure -> {
                    _resultsState.value = SearchResultsUiState.Error(r.error.toUserMessage())
                }
            }
        }
    }

    fun loadMore() {
        val currentSuccess = _resultsState.value as? SearchResultsUiState.Success ?: return
        val token = currentSuccess.results.continuationToken ?: return
        if (_isLoadingMore.value) return

        scope.launch {
            _isLoadingMore.value = true
            when (val r = provider.searchContinuation(token)) {
                is DhunResult.Success -> {
                    val more = r.value
                    val combined = currentSuccess.results.copy(
                        songs = currentSuccess.results.songs + more.songs,
                        videos = currentSuccess.results.videos + more.videos,
                        artists = currentSuccess.results.artists + more.artists,
                        albums = currentSuccess.results.albums + more.albums,
                        playlists = currentSuccess.results.playlists + more.playlists,
                        continuationToken = more.continuationToken,
                    )
                    _resultsState.value = SearchResultsUiState.Success(combined)
                }
                is DhunResult.Failure -> {
                    // Non-fatal, keep existing results
                }
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleFavorite(track: Track) {
        scope.launch {
            toggleFavoriteUseCase(track)
        }
    }

    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean {
        return addToPlaylistUseCase(playlistId, track)
    }

    private fun SearchResults.isEmpty(): Boolean =
        songs.isEmpty() && videos.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}
