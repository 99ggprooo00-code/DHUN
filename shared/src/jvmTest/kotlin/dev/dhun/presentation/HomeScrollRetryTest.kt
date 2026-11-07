package dev.dhun.presentation

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HistoryEntry
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeItem
import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.data.PlayContext
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.GetRecommendationsUseCase
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scroll-driven endless scrolling on the Home feed.
 *
 * The reported defect: "no endless scrolling on Home > For You" — while the
 * footer's explicit *Load more* button still worked.
 *
 * That asymmetry is the fingerprint of the bug fixed here. The scroll path and
 * the button path shared a single guard (`pageError != null` → refuse), so ONE
 * transient continuation failure stopped every later scroll event until the
 * user found the button. For You is the feed where this bites hardest: its
 * titles are continuation-only, so there is nothing else to scroll into.
 *
 * These tests drive the *scroll* entry point specifically, because the old
 * suite only drove `loadMore()` — which is why a green suite coexisted with a
 * broken scroll.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScrollRetryTest {

    private fun page(id: String, token: String?) = HomeFeedPage(
        sections = listOf(
            HomeSection("Mix $id", items = listOf(HomeItem.TrackItem(Track(id, "Track $id", "Artist")))),
        ),
        continuationToken = token,
    )

    private fun model(provider: PagingProvider, scope: CoroutineScope) = HomeViewModel(
        getHomeFeed = GetHomeFeedUseCase(provider, History),
        getRecommendations = GetRecommendationsUseCase(provider, History, Library),
        historyRepository = History,
        libraryRepository = Library,
        scope = scope,
    )

    private fun HomeViewModel.feed() = (uiState.value as HomeUiState.Success).feed

    @Test
    fun oneTransientFailureDoesNotStopTheScrollLoaderForever() = runTest {
        var attempts = 0
        val provider = PagingProvider(DhunResult.Success(page("a", "p2"))).apply {
            more = {
                attempts++
                if (attempts == 1) DhunResult.Failure(DhunError.Network()) else DhunResult.Success(page("b", "p3"))
            }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()

        // First scroll event hits the wire and fails.
        vm.onScrollNearEnd()
        runCurrent()
        assertNotNull(vm.loadMoreError.value, "the failure must still be surfaced")
        assertEquals(listOf("p2"), provider.requests)

        // The finger is still at the bottom: the next scroll event must retry
        // on its own instead of waiting for a button the user cannot see.
        vm.onScrollNearEnd()
        runCurrent()
        assertEquals(listOf("p2", "p2"), provider.requests, "the same page is retried")
        assertNull(vm.loadMoreError.value)
        assertEquals(listOf("a", "b"), vm.feed().sections.flatMap { it.tracks }.map { it.id })
    }

    @Test
    fun theAutomaticRetryBudgetIsBounded() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("a", "1"))).apply {
            more = { DhunResult.Failure(DhunError.Network()) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()

        // Keep scrolling through a service that never recovers.
        repeat(20) { vm.onScrollNearEnd(); runCurrent() }

        // One initial request + exactly MAX_AUTO_RETRIES (2) automatic
        // retries, then silence. The scroll trigger fires on every frame while
        // the list sits at the bottom, so this is the assertion that a dead
        // service is not hammered, while a merely blippy one still recovers.
        assertEquals(3, provider.requests.size, "a dead service must not be hammered")
        assertNotNull(vm.loadMoreError.value, "and the failure must remain visible")
    }

    @Test
    fun aRecoveredFeedCanSelfHealAgainLaterInTheSameSession() = runTest {
        var failures = 1
        val provider = PagingProvider(DhunResult.Success(page("a", "p1"))).apply {
            more = { token ->
                if (failures > 0) {
                    failures--
                    DhunResult.Failure(DhunError.Network())
                } else {
                    DhunResult.Success(page("page-${token.length}", token + "x"))
                }
            }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()

        vm.onScrollNearEnd(); runCurrent()   // fails, budget 1
        vm.onScrollNearEnd(); runCurrent()   // succeeds, budget resets
        assertNull(vm.loadMoreError.value)

        // A brand-new transient failure later must get its own retry budget,
        // otherwise a long session silently loses endless scrolling after N
        // unrelated network blips.
        failures = 1
        vm.onScrollNearEnd(); runCurrent()   // fails again
        val before = provider.requests.size
        vm.onScrollNearEnd(); runCurrent()   // retried automatically
        assertEquals(before + 1, provider.requests.size)
    }

    @Test
    fun theExplicitButtonStillWaitsForAnExplicitRetry() = runTest {
        // The old, deliberate behaviour is preserved for the user command: a
        // tap on "Load more" must not loop on its own.
        val provider = PagingProvider(DhunResult.Success(page("a", "p2"))).apply {
            more = { DhunResult.Failure(DhunError.Network()) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.loadMore(); runCurrent()
        repeat(5) { vm.loadMore(); runCurrent() }
        assertEquals(1, provider.requests.size)
        assertNotNull(vm.loadMoreError.value)
    }

    @Test
    fun scrollEventsAreIgnoredWhileAPageIsInFlight() = runTest {
        // A finger resting at the bottom fires the trigger every frame; the
        // in-flight guard must collapse those into one request.
        val gate = kotlinx.coroutines.CompletableDeferred<DhunResult<HomeFeedPage>>()
        val provider = PagingProvider(DhunResult.Success(page("a", "p2"))).apply {
            more = { gate.await() }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        repeat(10) { vm.onScrollNearEnd(); runCurrent() }
        assertTrue(vm.isLoadingMore.value)
        assertEquals(1, provider.requests.size, "one page request at a time")
        gate.complete(DhunResult.Success(page("b", null)))
        runCurrent()
        assertEquals(listOf("a", "b"), vm.feed().sections.flatMap { it.tracks }.map { it.id })
    }

    @Test
    fun anExhaustedFeedNeverRequestsAnything() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("a", null)))
        val vm = model(provider, backgroundScope)
        runCurrent()
        repeat(10) { vm.onScrollNearEnd(); runCurrent() }
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun scrollingBeforeTheFirstPageLoadsDoesNothing() = runTest {
        val provider = PagingProvider(DhunResult.Failure(DhunError.Network()))
        val vm = model(provider, backgroundScope)
        // No runCurrent(): the first page is still loading.
        vm.onScrollNearEnd()
        runCurrent()
        assertTrue(provider.requests.isEmpty(), "there is no token to continue yet")
    }

    private class PagingProvider(var first: DhunResult<HomeFeedPage>) : MusicProvider {
        val requests = mutableListOf<String>()
        var more: suspend (String) -> DhunResult<HomeFeedPage> = { DhunResult.Success(HomeFeedPage()) }
        override suspend fun homeFeedPage() = first
        override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<HomeFeedPage> {
            requests += continuationToken
            return more(continuationToken)
        }
        override suspend fun homeFeed(): DhunResult<List<HomeSection>> = error("unused")
        override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> {
            assertEquals(SearchFilter.SONGS, filter)
            return DhunResult.Success(SearchResults(query, songs = listOf(Track(query, query, "Artist"))))
        }
        override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> =
            DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String): DhunResult<List<String>> = error("unused")
        override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> = error("unused")
        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> = error("unused")
        override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> = error("unused")
        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> = error("unused")
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> = error("unused")
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> = error("unused")
    }

    private object History : HistoryRepository {
        override fun observeRecentlyPlayed(limit: Int) = flowOf(emptyList<Track>())
        override fun observeHistory(limit: Int) = flowOf(emptyList<HistoryEntry>())
        override suspend fun recordPlay(track: Track, context: PlayContext): Long = 0L
        override suspend fun markCompleted(trackId: String, playedAtEpochMs: Long): Unit = Unit
        override suspend fun playCount(trackId: String): Long = 0L
        override suspend fun remove(entryId: Long): Unit = Unit
        override suspend fun clear(): Unit = Unit
    }

    private object Library : LibraryRepository {
        override fun observeFavoriteIds() = flowOf(emptySet<String>())
        override fun observeFavorites() = flowOf(emptyList<Track>())
        override fun observeIsFavorite(trackId: String) = flowOf(false)
        override suspend fun isFavorite(trackId: String): Boolean = false
        override suspend fun addFavorite(track: Track): Unit = Unit
        override suspend fun removeFavorite(trackId: String): Unit = Unit
    }
}
