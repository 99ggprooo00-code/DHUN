package dev.dhun.presentation.home

import dev.dhun.core.DhunResult
import dev.dhun.core.DhunError
import dev.dhun.core.HomeFeed
import dev.dhun.core.HomeItem
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.GetRecommendationsUseCase
import dev.dhun.domain.ToggleFavoriteUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String) : HomeUiState
    data object Empty : HomeUiState
}

class HomeViewModel(
    private val getHomeFeed: GetHomeFeedUseCase,
    private val getRecommendations: GetRecommendationsUseCase,
    historyRepository: HistoryRepository,
    libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val toggleFavoriteUseCase = ToggleFavoriteUseCase(libraryRepository)

    // Generation, feed and request flags change atomically. A separate generation
    // check followed by a UI write could still overwrite a concurrent refresh on
    // the desktop's Default dispatcher (check-then-write race).
    private data class State(
        val generation: Long = 0,
        val ui: HomeUiState = HomeUiState.Loading,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val pageError: String? = null,
        val completedTokens: Set<String> = emptySet(),
        val emptyPageCount: Int = 0,
    )

    private val state = MutableStateFlow(State())
    val uiState: StateFlow<HomeUiState> = state.map { it.ui }
        .stateIn(scope, SharingStarted.Eagerly, HomeUiState.Loading)
    val isRefreshing: StateFlow<Boolean> = state.map { it.refreshing }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isLoadingMore: StateFlow<Boolean> = state.map { it.loadingMore }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val loadMoreError: StateFlow<String?> = state.map { it.pageError }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private var feedJob: Job? = null
    private var pageJob: Job? = null
    private var recommendJob: Job? = null

    val recentlyPlayed: StateFlow<List<Track>> = historyRepository.observeRecentlyPlayed(24)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val favoriteIds: StateFlow<Set<String>> = libraryRepository.observeFavoriteIds()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Content for the Home "Recommended songs" row. A near-instant local pick
     * set is published first (so the carousel never sits empty waiting on a
     * network hop), then replaced by richer history-seeded picks when those
     * arrive. Empty when the user has neither history nor saved songs.
     */
    private val _recommendedSongs = MutableStateFlow<List<Track>>(emptyList())
    val recommendedSongs: StateFlow<List<Track>> = _recommendedSongs.asStateFlow()

    private val _isLoadingRecommended = MutableStateFlow(false)
    val isLoadingRecommended: StateFlow<Boolean> = _isLoadingRecommended.asStateFlow()

    init {
        load()
        observeRecommendationSignals()
    }

    fun load() = requestFeed(refresh = false)
    fun refresh() = requestFeed(refresh = true)

    /**
     * Rebuild the recommended row whenever the seed signal — the ordered set
     * of the user's most recent distinct plays — changes (a new listen lands,
     * history is cleared, etc.). Keyed on ids so unrelated history edits do
     * not trigger a redundant network pass.
     */
    private fun observeRecommendationSignals() {
        scope.launch {
            recentlyPlayed
                .map { recent -> recent.distinctBy { it.id }.take(RECOMMEND_SEED_SIGNAL).map { it.id } }
                .distinctUntilChanged()
                .collect { refreshRecommendations() }
        }
    }

    private fun refreshRecommendations() {
        recommendJob?.cancel()
        val job = scope.launch {
            _isLoadingRecommended.value = true
            try {
                val excludeIds = currentVisibleTrackIds()
                // 1) Instant, offline-safe content from the user's own library.
                val local = try {
                    getRecommendations.localPicks(excludeIds)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                if (local.isNotEmpty()) _recommendedSongs.value = local

                // 2) Best-effort network enrichment seeded from recent plays.
                // Never blocks: on failure it returns empty and [local] stays.
                val remote = try {
                    getRecommendations.historySeeded(excludeIds)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                if (remote.isNotEmpty()) _recommendedSongs.value = remote
            } finally {
                _isLoadingRecommended.value = false
            }
        }
        recommendJob = job
    }

    /**
     * All track ids already rendered somewhere on the visible Home (quick
     * picks + feed shelves), so recommendations never duplicate them. The
     * Listen-again history row is deliberately NOT excluded here: the seed
     * signal for network enrichment comes from that same history.
     */
    private fun currentVisibleTrackIds(): Set<String> {
        val ids = LinkedHashSet<String>()
        val feed = (state.value.ui as? HomeUiState.Success)?.feed ?: return ids
        feed.quickPicks.forEach { ids.add(it.id) }
        feed.sections.forEach { section ->
            section.items.forEach { item ->
                if (item is HomeItem.TrackItem) ids.add(item.track.id)
            }
        }
        return ids
    }

    private fun requestFeed(refresh: Boolean) {
        val request = state.updateAndGet {
            State(
                generation = it.generation + 1,
                ui = if (refresh && it.ui is HomeUiState.Success) it.ui else HomeUiState.Loading,
                refreshing = refresh,
            )
        }
        feedJob?.cancel()
        pageJob?.cancel()
        feedJob = scope.launch {
            try {
                val result = getHomeFeed()
                state.update { current ->
                    if (current.generation != request.generation) current else current.copy(
                        ui = when (result) {
                            is DhunResult.Success -> {
                                val feed = result.value
                                if (feed.quickPicks.isEmpty() && feed.sections.isEmpty() && feed.continuationToken == null) {
                                    HomeUiState.Empty
                                } else {
                                    HomeUiState.Success(feed)
                                }
                            }
                            is DhunResult.Failure -> HomeUiState.Error(result.error.toUserMessage())
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.update {
                    if (it.generation != request.generation) it else it.copy(ui = HomeUiState.Error(DhunError.Unknown().toUserMessage()))
                }
            } finally {
                state.update {
                    if (it.generation != request.generation) it else it.copy(refreshing = false)
                }
            }
        }
    }

    fun loadMore() = requestMore(retry = false)
    fun retryLoadMore() = requestMore(retry = true)

    /** One request at a time; failures wait for an explicit retry, not a recomposition loop. */
    private fun requestMore(retry: Boolean) {
        val before = state.value
        val current = (before.ui as? HomeUiState.Success)?.feed ?: return
        val token = current.continuationToken ?: return
        if (before.refreshing || before.loadingMore || (!retry && before.pageError != null)) return
        val request = before.copy(
            loadingMore = true,
            pageError = null,
            emptyPageCount = if (retry) 0 else before.emptyPageCount,
        )
        // Claim BEFORE coroutine dispatch. If refresh/another request won the
        // CAS, let its updated state trigger the next UI event instead.
        if (!state.compareAndSet(before, request)) return
        pageJob = scope.launch {
            try {
                val result = getHomeFeed.loadMore(current)
                state.update { latest ->
                    if (latest.generation != request.generation) return@update latest
                    when (result) {
                        is DhunResult.Success -> {
                            val used = latest.completedTokens + token
                            val feed = result.value.copy(
                                continuationToken = result.value.continuationToken?.takeUnless { it in used },
                            )
                            val emptyPages = if (feed.sections.size == current.sections.size) latest.emptyPageCount + 1 else 0
                            latest.copy(
                                ui = HomeUiState.Success(feed),
                                completedTokens = used,
                                emptyPageCount = emptyPages,
                                pageError = if (feed.continuationToken != null && emptyPages >= MAX_EMPTY_PAGES) {
                                    "No new recommendations arrived. Retry or refresh Home."
                                } else null,
                            )
                        }
                        is DhunResult.Failure -> latest.copy(pageError = result.error.toUserMessage())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.update {
                    if (it.generation != request.generation) it else it.copy(pageError = DhunError.Unknown().toUserMessage())
                }
            } finally {
                state.update {
                    if (it.generation != request.generation) it else it.copy(loadingMore = false)
                }
            }
        }
    }

    fun toggleFavorite(track: Track) {
        scope.launch { toggleFavoriteUseCase(track) }
    }

    private companion object {
        const val MAX_EMPTY_PAGES = 3
        /** Distinct recent plays that form the seed signal for recommendations. */
        const val RECOMMEND_SEED_SIGNAL = 10
    }
}
