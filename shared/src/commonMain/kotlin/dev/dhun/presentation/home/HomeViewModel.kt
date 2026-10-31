package dev.dhun.presentation.home

import dev.dhun.core.DhunResult
import dev.dhun.core.DhunError
import dev.dhun.core.HomeFeed
import dev.dhun.core.detailString
import dev.dhun.core.diagnosticText
import dev.dhun.core.HomeItem
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.domain.HomeMood
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.GetRecommendationsUseCase
import dev.dhun.domain.ToggleFavoriteUseCase
import dev.dhun.presentation.MutationState
import dev.dhun.presentation.MutationTracker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
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
    data class Error(val message: String, val detail: String? = null) : HomeUiState
    data object Empty : HomeUiState
}

class HomeViewModel(
    private val getHomeFeed: GetHomeFeedUseCase,
    private val getRecommendations: GetRecommendationsUseCase,
    historyRepository: HistoryRepository,
    libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
    settings: SettingsRepository? = null,
    /** Receives the same sanitized detail shown by Home; Android routes it to logcat. */
    private val errorLogger: (String) -> Unit = {},
) {
    private val toggleFavoriteUseCase = ToggleFavoriteUseCase(libraryRepository)

    /**
     * The Like control is on every track row, and it used to be a bare
     * `scope.launch { toggleFavoriteUseCase(track) }`: a write failure either
     * vanished or reached the platform's uncaught handler (an app crash on
     * Android) while the heart simply stayed as it was. Failures are now a
     * message the shell renders as a banner.
     */
    private val mutations = MutationTracker(scope)
    val mutation: StateFlow<MutationState> = mutations.state
    fun dismissMutationError() = mutations.dismissError()

    // Each category owns its feed, generation and paging lifecycle. Switching
    // tabs must not destroy another tab's cursor or let its late response win.
    private data class CategoryState(
        val generation: Long = 0,
        val ui: HomeUiState = HomeUiState.Loading,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val pageError: String? = null,
        val completedTokens: Set<String> = emptySet(),
        val emptyPageCount: Int = 0,
    )

    private data class State(
        val mood: HomeMood = HomeMood.FOR_YOU,
        val categories: Map<HomeMood, CategoryState> = emptyMap(),
    ) {
        fun category(mood: HomeMood): CategoryState = categories[mood] ?: CategoryState()
        fun withCategory(mood: HomeMood, category: CategoryState): State =
            copy(categories = categories + (mood to category))
    }

    private val state = MutableStateFlow(State())
    val selectedMood: StateFlow<HomeMood> = state.map { it.mood }
        .stateIn(scope, SharingStarted.Eagerly, HomeMood.FOR_YOU)
    val uiState: StateFlow<HomeUiState> = state.map { it.category(it.mood).ui }
        .stateIn(scope, SharingStarted.Eagerly, HomeUiState.Loading)
    val isRefreshing: StateFlow<Boolean> = state.map { it.category(it.mood).refreshing }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isLoadingMore: StateFlow<Boolean> = state.map { it.category(it.mood).loadingMore }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val loadMoreError: StateFlow<String?> = state.map { it.category(it.mood).pageError }
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val feedJobs = mutableMapOf<HomeMood, Job>()
    private val pageJobs = mutableMapOf<HomeMood, Job>()
    private var recommendJob: Job? = null

    private val allowExplicit = settings?.observeString(SettingsKeys.EXPLICIT_CONTENT)
        ?.map { it?.toBooleanStrictOrNull() ?: true }
        ?.stateIn(scope, SharingStarted.Eagerly, true)
    private val rawRecentlyPlayed = historyRepository.observeRecentlyPlayed(24)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val recentlyPlayed: StateFlow<List<Track>> = allowExplicit?.let { allowed ->
        combine(rawRecentlyPlayed, allowed) { tracks, yes ->
            if (yes) tracks else tracks.filterNot { it.explicit }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    } ?: rawRecentlyPlayed
    val favoriteIds: StateFlow<Set<String>> = libraryRepository.observeFavoriteIds()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Content for the Home "Recommended songs" row. A near-instant local pick
     * set is published first (so the carousel never sits empty waiting on a
     * network hop), then replaced by richer history-seeded picks when those
     * arrive. Empty when the user has neither history nor saved songs.
     */
    private val _recommendedSongs = MutableStateFlow<List<Track>>(emptyList())
    val recommendedSongs: StateFlow<List<Track>> = allowExplicit?.let { allowed ->
        combine(_recommendedSongs, allowed) { tracks, yes ->
            if (yes) tracks else tracks.filterNot { it.explicit }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    } ?: _recommendedSongs.asStateFlow()

    private val _isLoadingRecommended = MutableStateFlow(false)
    val isLoadingRecommended: StateFlow<Boolean> = _isLoadingRecommended.asStateFlow()

    init {
        load()
        observeRecommendationSignals()
    }

    fun load() = requestFeed(refresh = false)
    fun refresh() {
        val active = state.value
        if (active.category(active.mood).refreshing) return
        requestFeed(refresh = true)
        if (active.mood == HomeMood.FOR_YOU) refreshRecommendations()
    }

    fun selectMood(mood: HomeMood) {
        val before = state.value
        if (before.mood == mood) return
        state.update { it.copy(mood = mood) }
        val existing = state.value.categories[mood]
        if (existing == null || (existing.ui is HomeUiState.Loading && feedJobs[mood]?.isActive != true)) {
            requestFeed(refresh = false, mood = mood)
        }
    }

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
        val active = state.value
        val feed = (active.category(active.mood).ui as? HomeUiState.Success)?.feed ?: return ids
        feed.quickPicks.forEach { ids.add(it.id) }
        feed.sections.forEach { section ->
            section.items.forEach { item ->
                if (item is HomeItem.TrackItem) ids.add(item.track.id)
            }
        }
        return ids
    }

    /**
     * Keep the friendly headline and the evidence together. The old Error state
     * dropped [DhunError.detailString], making a shape change, an HTTP rejection
     * and a missing client version indistinguishable on the phone.
     *
     * [diagnosticText] bounds untrusted text and strips URLs before either the
     * UI or logcat sees it. Home's shape summary itself contains key names only,
     * never continuation tokens, tracking values or response bodies.
     */
    private fun homeError(error: DhunError): HomeUiState.Error {
        val detail = error.detailString()
            ?.let { diagnosticText(it, HOME_ERROR_DETAIL_LIMIT) }
            ?.takeIf { it.isNotBlank() }
        errorLogger("Home load failed (${error::class.simpleName ?: "Unknown"}): ${detail ?: "no detail"}")
        return HomeUiState.Error(error.toUserMessage(), detail)
    }

    private fun requestFeed(refresh: Boolean, mood: HomeMood = state.value.mood) {
        val request = state.updateAndGet { current ->
            val previous = current.category(mood)
            val next = CategoryState(
                generation = previous.generation + 1,
                ui = if (refresh && previous.ui is HomeUiState.Success) previous.ui else HomeUiState.Loading,
                refreshing = refresh,
            )
            current.withCategory(mood, next)
        }.category(mood)
        feedJobs[mood]?.cancel()
        pageJobs[mood]?.cancel()
        feedJobs[mood] = scope.launch {
            try {
                val result = getHomeFeed(mood)
                val nextUi: HomeUiState = when (result) {
                    is DhunResult.Success -> {
                        val feed = result.value
                        if (feed.quickPicks.isEmpty() && feed.sections.isEmpty() && feed.continuationToken == null) {
                            HomeUiState.Empty
                        } else {
                            HomeUiState.Success(feed)
                        }
                    }
                    is DhunResult.Failure -> homeError(result.error)
                }
                state.update { current ->
                    val category = current.category(mood)
                    if (category.generation != request.generation) current
                    else current.withCategory(mood, category.copy(ui = nextUi))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = homeError(
                    DhunError.Unknown("${e::class.simpleName ?: "Exception"}: ${e.message.orEmpty()}"),
                )
                state.update { current ->
                    val category = current.category(mood)
                    if (category.generation != request.generation) current
                    else current.withCategory(mood, category.copy(ui = failure))
                }
            } finally {
                state.update { current ->
                    val category = current.category(mood)
                    if (category.generation != request.generation) current
                    else current.withCategory(mood, category.copy(refreshing = false))
                }
            }
        }
    }

    fun loadMore() = requestMore(retry = false)
    fun retryLoadMore() = requestMore(retry = true)

    /** One request at a time; failures wait for an explicit retry, not a recomposition loop. */
    private fun requestMore(retry: Boolean) {
        val before = state.value
        val mood = before.mood
        val beforeCategory = before.category(mood)
        val current = (beforeCategory.ui as? HomeUiState.Success)?.feed ?: return
        val token = current.continuationToken ?: return
        if (beforeCategory.refreshing || beforeCategory.loadingMore ||
            token in beforeCategory.completedTokens || (!retry && beforeCategory.pageError != null)
        ) return
        val requestCategory = beforeCategory.copy(
            loadingMore = true,
            pageError = null,
            emptyPageCount = if (retry) 0 else beforeCategory.emptyPageCount,
        )
        val request = before.withCategory(mood, requestCategory)
        // Claim BEFORE coroutine dispatch. A refresh in the same category (or
        // another rapid scroll event) can invalidate this exact generation.
        if (!state.compareAndSet(before, request)) return
        pageJobs[mood] = scope.launch {
            try {
                val result = getHomeFeed.loadMore(current, mood)
                state.update { latest ->
                    val category = latest.category(mood)
                    if (category.generation != requestCategory.generation) return@update latest
                    when (result) {
                        is DhunResult.Success -> {
                            val used = category.completedTokens + token
                            val feed = result.value.copy(
                                continuationToken = result.value.continuationToken?.takeUnless { it in used },
                            )
                            val emptyPages = if (feed.sections.size == current.sections.size) category.emptyPageCount + 1 else 0
                            latest.withCategory(mood, category.copy(
                                ui = HomeUiState.Success(feed),
                                completedTokens = used,
                                emptyPageCount = emptyPages,
                                pageError = if (feed.continuationToken != null && emptyPages >= MAX_EMPTY_PAGES) {
                                    "No new recommendations arrived. Retry or refresh Home."
                                } else null,
                            ))
                        }
                        is DhunResult.Failure -> latest.withCategory(
                            mood,
                            category.copy(pageError = result.error.toUserMessage()),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.update { latest ->
                    val category = latest.category(mood)
                    if (category.generation != requestCategory.generation) latest
                    else latest.withCategory(
                        mood,
                        category.copy(pageError = DhunError.Unknown().toUserMessage()),
                    )
                }
            } finally {
                state.update { latest ->
                    val category = latest.category(mood)
                    if (category.generation != requestCategory.generation) latest
                    else latest.withCategory(mood, category.copy(loadingMore = false))
                }
            }
        }
    }

    fun toggleFavorite(track: Track) {
        mutations.run("like:${track.id}", FAVORITE_FAILED) { toggleFavoriteUseCase(track) }
    }

    private companion object {
        const val FAVORITE_FAILED = "Could not update Liked Songs. Try again."
        const val HOME_ERROR_DETAIL_LIMIT = 700
        const val MAX_EMPTY_PAGES = 3
        /** Distinct recent plays that form the seed signal for recommendations. */
        const val RECOMMEND_SEED_SIGNAL = 10
    }
}
