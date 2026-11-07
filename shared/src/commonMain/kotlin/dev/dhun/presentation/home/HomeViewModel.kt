package dev.dhun.presentation.home

import dev.dhun.core.DhunResult
import dev.dhun.core.DhunError
import dev.dhun.core.HomeFeed
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
    data class Error(val message: String) : HomeUiState
    data object Empty : HomeUiState
}

private fun DhunError.isTransientForAutoRetry(): Boolean = when (this) {
    is DhunError.Network, is DhunError.RateLimited, is DhunError.Unknown -> true
    is DhunError.Parse, is DhunError.Unavailable, is DhunError.AuthRequired -> false
}

class HomeViewModel(
    private val getHomeFeed: GetHomeFeedUseCase,
    private val getRecommendations: GetRecommendationsUseCase,
    historyRepository: HistoryRepository,
    libraryRepository: LibraryRepository,
    private val scope: CoroutineScope,
    settings: SettingsRepository? = null,
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

    // Generation, feed and request flags change atomically. A separate generation
    // check followed by a UI write could still overwrite a concurrent refresh on
    // the desktop's Default dispatcher (check-then-write race).
    private data class State(
        val generation: Long = 0,
        val mood: HomeMood = HomeMood.FOR_YOU,
        val ui: HomeUiState = HomeUiState.Loading,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val pageError: String? = null,
        /** Whether the last continuation error is transient enough for ambient retry. */
        val autoRetryEligible: Boolean = false,
        val completedTokens: Set<String> = emptySet(),
        val emptyPageCount: Int = 0,
        /** Consecutive scroll-driven transient continuation failures absorbed automatically. */
        val autoRetries: Int = 0,
    )

    private val state = MutableStateFlow(State())
    val selectedMood: StateFlow<HomeMood> = state.map { it.mood }
        .stateIn(scope, SharingStarted.Eagerly, HomeMood.FOR_YOU)
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
        if (state.value.refreshing) return
        requestFeed(refresh = true)
        if (state.value.mood == HomeMood.FOR_YOU) refreshRecommendations()
    }

    fun selectMood(mood: HomeMood) {
        if (state.value.mood == mood) return
        requestFeed(refresh = false, mood = mood)
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
        val feed = (state.value.ui as? HomeUiState.Success)?.feed ?: return ids
        feed.quickPicks.forEach { ids.add(it.id) }
        feed.sections.forEach { section ->
            section.items.forEach { item ->
                if (item is HomeItem.TrackItem) ids.add(item.track.id)
            }
        }
        return ids
    }

    private fun requestFeed(refresh: Boolean, mood: HomeMood = state.value.mood) {
        val request = state.updateAndGet {
            State(
                generation = it.generation + 1,
                mood = mood,
                ui = if (refresh && it.ui is HomeUiState.Success) it.ui else HomeUiState.Loading,
                refreshing = refresh,
            )
        }
        feedJob?.cancel()
        pageJob?.cancel()
        feedJob = scope.launch {
            try {
                val result = getHomeFeed(request.mood)
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

    /** The explicit "Load more" affordance and the footer's Retry. */
    fun loadMore() = requestMore(retry = false)
    fun retryLoadMore() = requestMore(retry = true)

    /**
     * Scroll-driven trigger, called when the user reaches the end of the list.
     *
     * This is deliberately NOT the same entry point as [loadMore], because the
     * two have opposite obligations:
     *
     *  * [loadMore] is a **user command**. After a failure it must stay put —
     *    hammering the service because a finger is on the screen is worse than
     *    showing an honest footer, so it waits for [retryLoadMore].
     *  * [onScrollNearEnd] is an **ambient signal**. It fires repeatedly while
     *    the list sits at the bottom, so it has to be the thing that keeps the
     *    feed alive. Before this existed, a single transient continuation
     *    failure (a dropped mobile connection, one 5xx, a rate-limit blip) set
     *    `pageError`, and every later scroll event was rejected by the
     *    `pageError != null` guard *until the user found the footer button*.
     *    On For You — whose feed is continuation-only, unlike the mood
     *    categories that re-query Search — that is indistinguishable from
     *    "endless scrolling does not work".
     *
     * The budget is what keeps it honest: after [MAX_AUTO_RETRIES] consecutive
     * failures the footer error is left in place and the trigger stops, so a
     * genuinely dead service produces one message instead of a request loop.
     */
    fun onScrollNearEnd() {
        val before = state.value
        if (before.ui !is HomeUiState.Success) return
        if (before.refreshing || before.loadingMore) return
        // Nothing to load and no failure to heal: the feed is simply finished.
        if (before.pageError == null && (before.ui as HomeUiState.Success).feed.continuationToken == null) return
        // Only transient failures are candidates for ambient retry. Parse,
        // unavailable and auth errors cannot be repaired by replaying the same
        // continuation; keep their footer action explicit.
        if (before.pageError != null && !before.autoRetryEligible) return
        // For a retryable failure, the prior error belongs to the previous
        // request; this attempt is itself counted, so the budget is spent by the
        // attempt — not checked before it.
        requestMore(retry = before.pageError != null, fromScroll = true)
    }

    /** One continuation at a time; scroll-triggered recovery is separately bounded. */
    private fun requestMore(retry: Boolean, fromScroll: Boolean = false) {
        val before = state.value
        val current = (before.ui as? HomeUiState.Success)?.feed ?: return
        val token = current.continuationToken ?: return
        if (before.refreshing || before.loadingMore || (!retry && before.pageError != null)) return
        // The automatic budget: a scroll-driven retry stops once the feed has
        // already failed this many times since it last grew. Non-transient
        // errors stay explicit. The gate lives here, not only in
        // [onScrollNearEnd], so EVERY scroll path is bounded — including the
        // empty-page case, which fails to make progress without a request error.
        if (fromScroll && before.pageError != null &&
            (!before.autoRetryEligible || before.autoRetries > MAX_AUTO_RETRIES)
        ) return
        val request = before.copy(
            loadingMore = true,
            pageError = null,
            autoRetryEligible = false,
            emptyPageCount = if (retry) 0 else before.emptyPageCount,
            autoRetries = if (fromScroll) before.autoRetries else 0,
        )
        // Claim BEFORE coroutine dispatch. If refresh/another request won the
        // CAS, let its updated state trigger the next UI event instead.
        if (!state.compareAndSet(before, request)) return
        pageJob = scope.launch {
            try {
                val result = getHomeFeed.loadMore(current, request.mood)
                state.update { latest ->
                    if (latest.generation != request.generation) return@update latest
                    when (result) {
                        is DhunResult.Success -> {
                            val used = latest.completedTokens + token
                            val feed = result.value.copy(
                                continuationToken = result.value.continuationToken?.takeUnless { it in used },
                            )
                            val emptyPages = if (feed.sections.size == current.sections.size) latest.emptyPageCount + 1 else 0
                            // A page that actually grew the feed is proof the
                            // scroll path is healthy again: clear the automatic
                            // retry budget so the NEXT transient failure later in
                            // the same session can still self-heal.
                            val grew = feed.sections.size > current.sections.size
                            latest.copy(
                                ui = HomeUiState.Success(feed),
                                completedTokens = used,
                                emptyPageCount = emptyPages,
                                autoRetryEligible = false,
                                // A page that grew the feed proves the scroll
                                // path is healthy: reset the budget so a later
                                // transient failure can still self-heal. A page
                                // that returned nothing new spends budget too,
                                // otherwise "no new recommendations" would let a
                                // scroll retry run forever.
                                autoRetries = if (grew) 0 else latest.autoRetries + 1,
                                pageError = if (feed.continuationToken != null && emptyPages >= MAX_EMPTY_PAGES) {
                                    "No new recommendations arrived. Retry or refresh Home."
                                } else null,
                            )
                        }
                        is DhunResult.Failure -> latest.copy(
                            pageError = result.error.toUserMessage(),
                            autoRetryEligible = fromScroll && result.error.isTransientForAutoRetry(),
                            // Count one failure per request, not per wrapping
                            // request: a retry that also fails moves the budget
                            // forward exactly one step.
                            autoRetries = latest.autoRetries + 1,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.update {
                    if (it.generation != request.generation) it else it.copy(
                        pageError = DhunError.Unknown().toUserMessage(),
                        autoRetryEligible = fromScroll,
                        autoRetries = it.autoRetries + 1,
                    )
                }
            } finally {
                state.update {
                    if (it.generation != request.generation) it else it.copy(loadingMore = false)
                }
            }
        }
    }

    fun toggleFavorite(track: Track) {
        mutations.run("like:${track.id}", FAVORITE_FAILED) { toggleFavoriteUseCase(track) }
    }

    private companion object {
        const val FAVORITE_FAILED = "Could not update Liked Songs. Try again."
        const val MAX_EMPTY_PAGES = 3

        /**
         * Upper-layer retry budget after the initial continuation request:
         * at most three page requests total for a transient failure streak.
         * This is a bounded default, not a field-measured optimum; Network and
         * RateLimited errors already receive transport-level backoff, while
         * Parse/Auth/Unavailable errors remain explicit rather than replaying.
         */
        const val MAX_AUTO_RETRIES = 2
        /** Distinct recent plays that form the seed signal for recommendations. */
        const val RECOMMEND_SEED_SIGNAL = 10
    }
}
