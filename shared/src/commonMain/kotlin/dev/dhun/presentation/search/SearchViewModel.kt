package dev.dhun.presentation.search

import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.DhunResult
import dev.dhun.core.DhunError
import dev.dhun.core.detailString
import dev.dhun.core.diagnosticText
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
import dev.dhun.presentation.MutationState
import dev.dhun.presentation.MutationTracker
import dev.dhun.innertube.SearchFilter
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

sealed interface SearchResultsUiState {
    data object Idle : SearchResultsUiState
    data object Loading : SearchResultsUiState
    data class Success(val results: SearchResults) : SearchResultsUiState
    data class Error(val message: String, val detail: String? = null) : SearchResultsUiState
    data class Empty(val query: String) : SearchResultsUiState
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val provider: MusicProvider,
    private val searchRepository: SearchRepository,
    private val libraryRepository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val scope: CoroutineScope,
    /** Receives the same sanitized detail shown by Search; Android routes it to logcat. */
    private val errorLogger: (String) -> Unit = {},
) {
    private val recentSearchesUseCase = RecentSearchesUseCase(searchRepository)
    private val toggleFavoriteUseCase = ToggleFavoriteUseCase(libraryRepository)

    /**
     * Same contract as [dev.dhun.presentation.home.HomeViewModel]: a Like that
     * cannot be saved reports itself instead of disappearing (or crashing the
     * app through an unguarded coroutine).
     */
    private val mutations = MutationTracker(scope)
    val mutation: StateFlow<MutationState> = mutations.state
    fun dismissMutationError() = mutations.dismissError()
    private val addToPlaylistUseCase = AddToPlaylistUseCase(playlistRepository)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SearchFilter.SONGS)
    val selectedFilter: StateFlow<SearchFilter> = _selectedFilter.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private data class State(
        val generation: Long = 0,
        val results: SearchResultsUiState = SearchResultsUiState.Idle,
        val loadingMore: Boolean = false,
        val pageError: String? = null,
        val completedTokens: Set<String> = emptySet(),
        val emptyPageCount: Int = 0,
    )

    private val state = MutableStateFlow(State())
    val resultsState: StateFlow<SearchResultsUiState> = state.map { it.results }
        .stateIn(scope, SharingStarted.Eagerly, SearchResultsUiState.Idle)
    val isLoadingMore: StateFlow<Boolean> = state.map { it.loadingMore }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val loadMoreError: StateFlow<String?> = state.map { it.pageError }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val recentSearches: StateFlow<List<String>> = recentSearchesUseCase.observe(15)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<Set<String>> = libraryRepository.observeFavoriteIds()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private var searchJob: Job? = null
    private var pageJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        // Debounce search suggestions by 300ms
        scope.launch {
            _query
                .debounce(300L)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.trim().length >= 2 && state.value.results !is SearchResultsUiState.Success) {
                        fetchSuggestions(q.trim())
                    } else {
                        _suggestions.value = emptyList()
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        if (newQuery == _query.value) return
        _query.value = newQuery
        _suggestions.value = emptyList()
        invalidateResults()
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
        invalidateResults()
    }

    private fun invalidateResults() {
        searchJob?.cancel()
        pageJob?.cancel()
        state.update { State(generation = it.generation + 1) }
    }

    fun deleteRecentSearch(query: String) {
        scope.launch {
            recentSearchesUseCase.remove(query)
        }
    }

    suspend fun clearAllRecentSearches() {
        recentSearchesUseCase.clear()
    }

    private fun fetchSuggestions(q: String) {
        suggestionJob?.cancel()
        suggestionJob = scope.launch {
            when (val r = provider.searchSuggestions(q)) {
                is DhunResult.Success -> {
                    if (_query.value.trim() == q && state.value.results is SearchResultsUiState.Idle) {
                        _suggestions.value = r.value
                    }
                }
                is DhunResult.Failure -> {
                    logSearchError("suggestions", r.error)
                    if (_query.value.trim() == q) _suggestions.value = emptyList()
                }
            }
        }
    }

    fun performSearch(query: String, filter: SearchFilter = _selectedFilter.value) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        val request = state.updateAndGet {
            State(generation = it.generation + 1, results = SearchResultsUiState.Loading)
        }
        searchJob?.cancel()
        pageJob?.cancel()
        _query.value = cleanQuery
        _selectedFilter.value = filter
        _suggestions.value = emptyList()
        searchJob = scope.launch {
            try {
                try {
                    recentSearchesUseCase.record(cleanQuery)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A history write must not prevent the actual search, but
                    // it is still visible to developers rather than swallowed.
                    errorLogger("Search history save failed (${error::class.simpleName ?: "Exception"})")
                }
                val next = when (val result = provider.search(cleanQuery, filter)) {
                    is DhunResult.Success -> {
                        if (result.value.isEmpty()) SearchResultsUiState.Empty(cleanQuery)
                        else SearchResultsUiState.Success(result.value)
                    }
                    is DhunResult.Failure -> searchError("request", result.error)
                }
                state.update { current ->
                    if (current.generation != request.generation) current
                    else current.copy(results = next)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = searchError(
                    "request",
                    DhunError.Unknown("${error::class.simpleName ?: "Exception"}: ${error.message.orEmpty()}"),
                )
                state.update { current ->
                    if (current.generation != request.generation) current
                    else current.copy(results = failure)
                }
            }
        }
    }

    fun loadMore() = requestMore(retry = false)
    fun retryLoadMore() = requestMore(retry = true)

    /** One page in flight; stale generations and repeated cursors cannot append. */
    private fun requestMore(retry: Boolean) {
        val before = state.value
        val currentSuccess = before.results as? SearchResultsUiState.Success ?: return
        val token = currentSuccess.results.continuationToken ?: return
        if (before.loadingMore || token in before.completedTokens || (!retry && before.pageError != null)) return
        val request = before.copy(
            loadingMore = true,
            pageError = null,
            emptyPageCount = if (retry) 0 else before.emptyPageCount,
        )
        if (!state.compareAndSet(before, request)) return
        pageJob = scope.launch {
            try {
                val result = provider.searchContinuation(token)
                state.update { latest ->
                    if (latest.generation != request.generation) return@update latest
                    val current = (latest.results as? SearchResultsUiState.Success)?.results
                        ?: return@update latest
                    when (result) {
                        is DhunResult.Success -> {
                            val more = result.value
                            val songs = appendDistinct(current.songs, more.songs) { it.id }
                            val videos = appendDistinct(current.videos, more.videos) { it.id }
                            val artists = appendDistinct(current.artists, more.artists) { it.id }
                            val albums = appendDistinct(current.albums, more.albums) { it.id }
                            val playlists = appendDistinct(current.playlists, more.playlists) { it.id }
                            val added = songs.size - current.songs.size + videos.size - current.videos.size +
                                artists.size - current.artists.size + albums.size - current.albums.size +
                                playlists.size - current.playlists.size
                            val used = latest.completedTokens + token
                            val nextToken = more.continuationToken
                                ?.takeIf { it.isNotBlank() && it !in used }
                            val emptyPages = if (added == 0) latest.emptyPageCount + 1 else 0
                            latest.copy(
                                results = SearchResultsUiState.Success(current.copy(
                                    songs = songs,
                                    videos = videos,
                                    artists = artists,
                                    albums = albums,
                                    playlists = playlists,
                                    continuationToken = nextToken,
                                )),
                                completedTokens = used,
                                emptyPageCount = emptyPages,
                                pageError = if (nextToken != null && emptyPages >= MAX_EMPTY_PAGES) {
                                    "No new results arrived. Retry or search again."
                                } else null,
                            )
                        }
                        is DhunResult.Failure -> {
                            logSearchError("continuation", result.error)
                            latest.copy(pageError = result.error.toUserMessage())
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = DhunError.Unknown(
                    "${error::class.simpleName ?: "Exception"}: ${error.message.orEmpty()}",
                )
                logSearchError("continuation", failure)
                state.update { latest ->
                    if (latest.generation != request.generation) latest
                    else latest.copy(pageError = failure.toUserMessage())
                }
            } finally {
                state.update { latest ->
                    if (latest.generation != request.generation) latest
                    else latest.copy(loadingMore = false)
                }
            }
        }
    }

    private fun searchError(operation: String, error: DhunError): SearchResultsUiState.Error {
        logSearchError(operation, error)
        val detail = error.detailString()
            ?.let { diagnosticText(it, SEARCH_ERROR_DETAIL_LIMIT) }
            ?.takeIf { it.isNotBlank() }
        return SearchResultsUiState.Error(error.toUserMessage(), detail)
    }

    private fun logSearchError(operation: String, error: DhunError) {
        val detail = error.detailString()
            ?.let { diagnosticText(it, SEARCH_ERROR_DETAIL_LIMIT) }
            ?.takeIf { it.isNotBlank() }
        errorLogger(
            "Search $operation failed (${error::class.simpleName ?: "Unknown"}): ${detail ?: "no detail"}",
        )
    }

    private fun <T> appendDistinct(current: List<T>, more: List<T>, id: (T) -> String): List<T> {
        if (more.isEmpty()) return current
        val seen = current.mapTo(HashSet(), id)
        return current + more.filter { seen.add(id(it)) }
    }

    fun toggleFavorite(track: Track) {
        mutations.run("like:${track.id}", "Could not update Liked Songs. Try again.") {
            toggleFavoriteUseCase(track)
        }
    }

    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean {
        return addToPlaylistUseCase(playlistId, track)
    }

    private fun SearchResults.isEmpty(): Boolean =
        songs.isEmpty() && videos.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()

    private companion object {
        const val MAX_EMPTY_PAGES = 2
        const val SEARCH_ERROR_DETAIL_LIMIT = 700
    }
}
