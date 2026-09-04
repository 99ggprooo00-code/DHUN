package dev.dhun.presentation.home

import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeed
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.ToggleFavoriteUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String) : HomeUiState
    data object Empty : HomeUiState
}

class HomeViewModel(
    private val getHomeFeed: GetHomeFeedUseCase,
    private val historyRepository: HistoryRepository,
    private val libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val toggleFavoriteUseCase = ToggleFavoriteUseCase(libraryRepository)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val recentlyPlayed: StateFlow<List<Track>> = historyRepository.observeRecentlyPlayed(12)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<Set<String>> = libraryRepository.observeFavoriteIds()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    init {
        load()
    }

    fun load() {
        scope.launch {
            _uiState.value = HomeUiState.Loading
            fetchFeed()
        }
    }

    fun refresh() {
        scope.launch {
            _isRefreshing.value = true
            fetchFeed()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchFeed() {
        when (val result = getHomeFeed()) {
            is DhunResult.Success -> {
                val feed = result.value
                if (feed.quickPicks.isEmpty() && feed.sections.isEmpty()) {
                    _uiState.value = HomeUiState.Empty
                } else {
                    _uiState.value = HomeUiState.Success(feed)
                }
            }
            is DhunResult.Failure -> {
                _uiState.value = HomeUiState.Error(result.error.toUserMessage())
            }
        }
    }

    fun toggleFavorite(track: Track) {
        scope.launch {
            toggleFavoriteUseCase(track)
        }
    }
}
