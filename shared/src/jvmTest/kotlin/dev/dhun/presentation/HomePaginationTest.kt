package dev.dhun.presentation

import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HistoryEntry
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeFeed
import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.data.PlayContext
import dev.dhun.domain.HomeMood
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.GetRecommendationsUseCase
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.ui.home.quickPickShelfAlreadyShown
import dev.dhun.ui.home.remainingHomeSections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomePaginationTest {
    private fun page(id: String, token: String?) = HomeFeedPage(
        sections = listOf(HomeSection("Recommended", items = listOf(HomeItem.TrackItem(Track(id, "Track $id", "Artist"))))),
        continuationToken = token,
    )
    private fun model(provider: PagingProvider, scope: CoroutineScope) =
        HomeViewModel(
            getHomeFeed = GetHomeFeedUseCase(provider, History),
            getRecommendations = GetRecommendationsUseCase(provider, History, Library),
            historyRepository = History,
            libraryRepository = Library,
            scope = scope,
        )
    private fun HomeViewModel.feed() = (uiState.value as HomeUiState.Success).feed

    @Test
    fun rapidLoadMoreEventsMakeOneRequestAndAppendThePage() = runTest {
        val gate = CompletableDeferred<HomeFeedPage>()
        val provider = PagingProvider(DhunResult.Success(page("a", "p2"))).apply {
            more = { DhunResult.Success(gate.await()) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.loadMore()
        vm.loadMore()
        runCurrent()
        assertTrue(vm.isLoadingMore.value)
        assertEquals(listOf("p2"), provider.requests)
        gate.complete(page("b", null))
        runCurrent()
        assertEquals(listOf("a", "b"), vm.feed().sections.flatMap { it.tracks }.map { it.id })
        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun continuationFailureKeepsTheFeedAndWaitsForExplicitRetry() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("a", "p2"))).apply {
            more = { DhunResult.Failure(DhunError.Network()) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.loadMore()
        runCurrent()
        assertNotNull(vm.loadMoreError.value)
        assertEquals("p2", vm.feed().continuationToken)
        assertEquals("a", vm.feed().sections.single().tracks.single().id)
        repeat(5) { vm.loadMore(); runCurrent() }
        assertEquals(1, provider.requests.size, "a persistent footer error must not auto-retry")
        provider.more = { DhunResult.Success(page("b", null)) }
        vm.retryLoadMore()
        runCurrent()
        assertEquals(2, provider.requests.size)
        assertNull(vm.loadMoreError.value)
        assertEquals(2, vm.feed().sections.size)
    }

    @Test
    fun refreshCannotBeOverwrittenByAnOldPage() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = PagingProvider(DhunResult.Success(page("old", "old-next"))).apply {
            more = {
                withContext(NonCancellable) { gate.await() }
                DhunResult.Success(page("stale", "stale-next"))
            }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.loadMore()
        runCurrent()
        provider.first = DhunResult.Success(page("fresh", "fresh-next"))
        vm.refresh()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("fresh", vm.feed().sections.single().tracks.single().id)
        assertEquals("fresh-next", vm.feed().continuationToken)
        assertFalse(vm.isRefreshing.value)
        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun continuationCyclesStopInsteadOfRefetchingForever() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("a", "A"))).apply {
            more = { token -> DhunResult.Success(if (token == "A") page("b", "B") else page("c", "A")) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        repeat(3) { vm.loadMore(); runCurrent() }
        assertEquals(listOf("A", "B"), provider.requests)
        assertEquals(3, vm.feed().sections.size)
        assertNull(vm.feed().continuationToken)
    }

    @Test
    fun repeatedEmptyPagesPauseAutomaticLoadingButCanBeRetried() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("a", "1"))).apply {
            more = { token -> DhunResult.Success(HomeFeedPage(continuationToken = "${token.toInt() + 1}")) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        repeat(6) { vm.loadMore(); runCurrent() }
        assertEquals(3, provider.requests.size)
        assertNotNull(vm.loadMoreError.value)
        vm.retryLoadMore()
        runCurrent()
        assertEquals(4, provider.requests.size)
        assertNull(vm.loadMoreError.value)
    }

    @Test
    fun laterQuickPicksWithFreshMusicAreNotHiddenByTheirTitle() {
        val first = page("a", null).sections.single().copy(title = "Quick picks")
        val second = page("b", null).sections.single().copy(title = "Quick picks")
        assertTrue(quickPickShelfAlreadyShown(first, first.tracks))
        assertFalse(quickPickShelfAlreadyShown(second, first.tracks))
        val feed = HomeFeed("Hello", quickPicks = first.tracks, sections = listOf(first, second))
        assertEquals(listOf(second), remainingHomeSections(feed), "the screen's category filter must not discard fresh Quick picks")
    }

    @Test
    fun emptyFirstPageWithATokenCanStillLoadMore() = runTest {
        val provider = PagingProvider(DhunResult.Success(HomeFeedPage(continuationToken = "next")))
        val vm = model(provider, backgroundScope)
        runCurrent()
        assertTrue(vm.uiState.value is HomeUiState.Success)
        provider.more = { DhunResult.Success(page("a", null)) }
        vm.loadMore()
        runCurrent()
        assertEquals("a", vm.feed().sections.single().tracks.single().id)
    }

    @Test
    fun eachMoodRequestsDifferentSongsAndForYouRestoresHome() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("home", "home-next")))
        val vm = model(provider, backgroundScope)
        runCurrent()
        for (mood in HomeMood.entries.filterNot { it == HomeMood.FOR_YOU }) {
            vm.selectMood(mood)
            runCurrent()
            assertEquals(mood, vm.selectedMood.value)
            assertEquals(listOf(mood.query), vm.feed().sections.flatMap { it.tracks }.map { it.id })
            assertTrue(vm.feed().quickPicks.isEmpty(), "old home quick picks must not survive a mood switch")
        }
        assertEquals(HomeMood.entries.mapNotNull { it.query }, provider.searches)
        vm.selectMood(HomeMood.FOR_YOU)
        runCurrent()
        assertEquals("home", vm.feed().sections.single().tracks.single().id)
        assertEquals("home-next", vm.feed().continuationToken)
    }

    @Test
    fun selectingTheAlreadySelectedMoodDoesNotIssueAnotherRequest() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("home", null)))
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.selectMood(HomeMood.CHILL)
        runCurrent()
        repeat(5) { vm.selectMood(HomeMood.CHILL) }
        runCurrent()
        assertEquals(listOf(HomeMood.CHILL.query), provider.searches)
    }

    @Test
    fun refreshingKeepsSelectedMoodAndCoalescesRepeatedGestures() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("home", null)))
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.selectMood(HomeMood.FOCUS)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        provider.searchPage = { query ->
            gate.await()
            DhunResult.Success(SearchResults(query, songs = listOf(Track("fresh", "Fresh", "Artist"))))
        }
        repeat(5) { vm.refresh() }
        runCurrent()
        assertTrue(vm.isRefreshing.value)
        assertEquals(listOf(HomeMood.FOCUS.query, HomeMood.FOCUS.query), provider.searches)
        gate.complete(Unit)
        runCurrent()
        assertEquals(HomeMood.FOCUS, vm.selectedMood.value)
        assertEquals("fresh", vm.feed().sections.single().tracks.single().id)
        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun staleHomeContinuationCannotOverwriteMoodSongs() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = PagingProvider(DhunResult.Success(page("home", "home-next"))).apply {
            more = {
                withContext(NonCancellable) { gate.await() }
                DhunResult.Success(page("stale", "old-next"))
            }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.loadMore()
        runCurrent()
        vm.selectMood(HomeMood.CHILL)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(HomeMood.CHILL.query), vm.feed().sections.flatMap { it.tracks }.map { it.id })
        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun rapidMoodSwitchRejectsLateSearchAndKeepsNewestSelection() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = PagingProvider(DhunResult.Success(page("home", null))).apply {
            searchPage = { query ->
                if (query == HomeMood.FOCUS.query) withContext(NonCancellable) { gate.await() }
                DhunResult.Success(SearchResults(query, songs = listOf(Track(query, query, "Artist"))))
            }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.selectMood(HomeMood.FOCUS)
        runCurrent()
        vm.selectMood(HomeMood.PARTY)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(HomeMood.PARTY, vm.selectedMood.value)
        assertEquals(HomeMood.PARTY.query, vm.feed().sections.single().tracks.single().id)
    }

    @Test
    fun moodContinuationUsesSearchAndDeduplicatesSongs() = runTest {
        val song = Track("a", "A", "Artist")
        val provider = PagingProvider(DhunResult.Success(page("home", "home-next"))).apply {
            searchPage = { DhunResult.Success(SearchResults(it, songs = listOf(song), continuationToken = "search-next")) }
            searchMore = { DhunResult.Success(SearchResults("", songs = listOf(song, song.copy(id = "b")))) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.selectMood(HomeMood.WORKOUT)
        runCurrent()
        vm.loadMore()
        runCurrent()
        assertEquals(listOf("search-next"), provider.searchTokens)
        assertTrue(provider.requests.isEmpty(), "search tokens must never reach browse continuation")
        assertEquals(listOf("a", "b"), vm.feed().sections.flatMap { it.tracks }.map { it.id })
        assertNull(vm.feed().continuationToken)
    }

    @Test
    fun failedOrEmptyMoodNeverFallsBackToUnrelatedHomeSongsAndRetryKeepsMood() = runTest {
        val provider = PagingProvider(DhunResult.Success(page("home", null))).apply {
            searchPage = { DhunResult.Failure(DhunError.Network()) }
        }
        val vm = model(provider, backgroundScope)
        runCurrent()
        vm.selectMood(HomeMood.PARTY)
        runCurrent()
        assertTrue(vm.uiState.value is HomeUiState.Error)
        assertEquals(HomeMood.PARTY, vm.selectedMood.value)
        provider.searchPage = { DhunResult.Success(SearchResults(it)) }
        vm.load()
        runCurrent()
        assertTrue(vm.uiState.value is HomeUiState.Empty)
        assertEquals(listOf(HomeMood.PARTY.query, HomeMood.PARTY.query), provider.searches)
        vm.selectMood(HomeMood.FOR_YOU)
        runCurrent()
        assertEquals("home", vm.feed().sections.single().tracks.single().id)
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
        val searches = mutableListOf<String>()
        val searchTokens = mutableListOf<String>()
        var searchPage: suspend (String) -> DhunResult<SearchResults> = { query ->
            DhunResult.Success(SearchResults(query, songs = listOf(Track(query, query, "Artist"))))
        }
        var searchMore: suspend (String) -> DhunResult<SearchResults> = { DhunResult.Success(SearchResults("")) }
        override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> {
            assertEquals(SearchFilter.SONGS, filter)
            searches += query
            return searchPage(query)
        }
        override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> {
            searchTokens += continuationToken
            return searchMore(continuationToken)
        }
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
        override suspend fun recordPlay(track: Track, context: PlayContext): Long = error("unused")
        override suspend fun markCompleted(trackId: String, playedAtEpochMs: Long): Unit = error("unused")
        override suspend fun playCount(trackId: String): Long = error("unused")
        override suspend fun remove(entryId: Long): Unit = error("unused")
        override suspend fun clear(): Unit = error("unused")
    }

    private object Library : LibraryRepository {
        override fun observeFavoriteIds() = flowOf(emptySet<String>())
        override fun observeFavorites() = flowOf(emptyList<Track>())
        override fun observeIsFavorite(trackId: String) = flowOf(false)
        override suspend fun isFavorite(trackId: String): Boolean = error("unused")
        override suspend fun addFavorite(track: Track): Unit = error("unused")
        override suspend fun removeFavorite(trackId: String): Unit = error("unused")
    }
}
