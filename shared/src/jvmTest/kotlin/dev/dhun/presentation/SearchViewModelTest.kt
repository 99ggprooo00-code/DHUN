package dev.dhun.presentation

import dev.dhun.core.Album
import dev.dhun.core.AlbumDetail
import dev.dhun.core.Artist
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.Playlist
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.search.SearchResultsUiState
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchViewModelTest {

    private fun testData() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    private fun sampleTrack(id: String) = Track(id = id, title = "Song $id", artistName = "Artist $id")

    private class FakeSearchMusicProvider : MusicProvider {
        @Volatile var lastFilter: SearchFilter? = null
        @Volatile var lastQuery: String? = null
        @Volatile var searchHandler: (suspend (String, SearchFilter) -> DhunResult<SearchResults>)? = null
        @Volatile var continuationHandler: (suspend (String) -> DhunResult<SearchResults>)? = null
        val continuationCalls = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val continuationFilters = java.util.concurrent.ConcurrentLinkedQueue<SearchFilter>()

        override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> {
            lastQuery = query
            lastFilter = filter
            searchHandler?.let { return it(query, filter) }
            return DhunResult.Success(
                SearchResults(
                    query = query,
                    songs = listOf(Track(id = "s1", title = "Song 1", artistName = "Artist 1")),
                    artists = listOf(Artist(id = "a1", name = "Artist 1")),
                    albums = listOf(Album(id = "al1", title = "Album 1")),
                    playlists = listOf(Playlist(id = "p1", title = "Playlist 1")),
                    continuationToken = "token_123",
                )
            )
        }

        override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> {
            continuationCalls += continuationToken
            continuationHandler?.let { return it(continuationToken) }
            return DhunResult.Success(
                SearchResults(
                    query = "",
                    songs = listOf(Track(id = "s2", title = "Song 2", artistName = "Artist 2")),
                    continuationToken = null,
                )
            )
        }

        override suspend fun searchContinuation(
            continuationToken: String,
            filter: SearchFilter,
        ): DhunResult<SearchResults> {
            continuationFilters += filter
            return searchContinuation(continuationToken)
        }

        override suspend fun searchSuggestions(query: String): DhunResult<List<String>> {
            return DhunResult.Success(listOf("$query one", "$query two"))
        }

        override suspend fun homeFeed(): DhunResult<List<HomeSection>> = DhunResult.Success(emptyList())
        override suspend fun homeFeedPage(): DhunResult<dev.dhun.core.HomeFeedPage> =
            DhunResult.Success(dev.dhun.core.HomeFeedPage())
        override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<dev.dhun.core.HomeFeedPage> =
            DhunResult.Success(dev.dhun.core.HomeFeedPage())
        override suspend fun relatedTracks(videoId: String) = DhunResult.Success(emptyList<Track>())
        override suspend fun getStreamInfo(videoId: String) = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun getLyrics(videoId: String) = DhunResult.Success(Lyrics.NotAvailable)

        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> =
            DhunResult.Failure(DhunError.Unavailable())
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> =
            DhunResult.Failure(DhunError.Unavailable())
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> =
            DhunResult.Failure(DhunError.Unavailable())
    }

    private suspend fun eventually(timeoutMs: Long = 15_000, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!check()) delay(10)
        }
    }

    @Test
    fun searchExecutionAndResults(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            val vm = SearchViewModel(
                provider = provider,
                searchRepository = data.search,
                libraryRepository = data.library,
                playlistRepository = data.playlists,
                scope = testScope,
            )

            vm.performSearch("bohemian", SearchFilter.SONGS)
            eventually { vm.resultsState.value is SearchResultsUiState.Success }
            val state = vm.resultsState.value as SearchResultsUiState.Success
            val results = state.results
            assertEquals(1, results.songs.size)
            assertEquals("s1", results.songs[0].id)
            assertEquals("token_123", results.continuationToken)

            // Recent searches recorded
            eventually { vm.recentSearches.value.contains("bohemian") }
            assertTrue("bohemian" in vm.recentSearches.value)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun searchContinuationInfiniteScroll(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            val vm = SearchViewModel(
                provider = provider,
                searchRepository = data.search,
                libraryRepository = data.library,
                playlistRepository = data.playlists,
                scope = testScope,
            )

            vm.performSearch("queen")
            eventually { vm.resultsState.value is SearchResultsUiState.Success }
            vm.loadMore()
            eventually { (vm.resultsState.value as? SearchResultsUiState.Success)?.results?.songs?.size == 2 }

            val state = vm.resultsState.value as SearchResultsUiState.Success
            val results = state.results
            assertEquals(2, results.songs.size)
            assertEquals("s1", results.songs[0].id)
            assertEquals("s2", results.songs[1].id)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun videoFilterIsPreservedForContinuation(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            provider.searchHandler = { query, _ ->
                DhunResult.Success(SearchResults(
                    query = query,
                    videos = listOf(sampleTrack("v1")),
                    continuationToken = "token_123",
                ))
            }
            provider.continuationHandler = {
                DhunResult.Success(SearchResults(query = "", videos = listOf(sampleTrack("v2"))))
            }
            val vm = SearchViewModel(provider, data.search, data.library, data.playlists, testScope)
            vm.performSearch("live", SearchFilter.VIDEOS)
            eventually { vm.resultsState.value is SearchResultsUiState.Success }
            vm.loadMore()
            eventually { provider.continuationFilters.size == 1 }
            assertEquals(SearchFilter.VIDEOS, provider.continuationFilters.single())
            eventually {
                (vm.resultsState.value as? SearchResultsUiState.Success)?.results?.videos?.size == 2
            }
            val results = (vm.resultsState.value as SearchResultsUiState.Success).results
            assertEquals(listOf("v1", "v2"), results.videos.map { it.id })
            assertEquals(0, results.songs.size)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun filterSelectionUpdatesSearch(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            val vm = SearchViewModel(
                provider = provider,
                searchRepository = data.search,
                libraryRepository = data.library,
                playlistRepository = data.playlists,
                scope = testScope,
            )

            vm.onQueryChange("coldplay")
            vm.onFilterSelected(SearchFilter.ALBUMS)
            assertEquals(SearchFilter.ALBUMS, vm.selectedFilter.value)
            eventually { provider.lastFilter == SearchFilter.ALBUMS }
            assertEquals(SearchFilter.ALBUMS, provider.lastFilter)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun suggestionsDebounce(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            val vm = SearchViewModel(
                provider = provider,
                searchRepository = data.search,
                libraryRepository = data.library,
                playlistRepository = data.playlists,
                scope = testScope,
            )

            vm.onQueryChange("cold")
            eventually(timeoutMs = 3000) { vm.suggestions.value.isNotEmpty() }
            assertEquals(listOf("cold one", "cold two"), vm.suggestions.value)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun recentSearchesManagement(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            val vm = SearchViewModel(
                provider = provider,
                searchRepository = data.search,
                libraryRepository = data.library,
                playlistRepository = data.playlists,
                scope = testScope,
            )

            vm.performSearch("query1")
            eventually { vm.recentSearches.value.contains("query1") }
            vm.performSearch("query2")
            eventually { vm.recentSearches.value.contains("query2") }

            vm.deleteRecentSearch("query1")
            eventually { !vm.recentSearches.value.contains("query1") }
            vm.clearAllRecentSearches()
            eventually { vm.recentSearches.value.isEmpty() }
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun continuationFailureIsRetryableAndAppendsAreDistinct(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            provider.continuationHandler = {
                if (provider.continuationCalls.size == 1) {
                    DhunResult.Failure(DhunError.Network("HTTP 400 from /search"))
                } else {
                    DhunResult.Success(SearchResults(
                        query = "",
                        songs = listOf(sampleTrack("s1"), sampleTrack("s2"), sampleTrack("s2")),
                        continuationToken = "token_123",
                    ))
                }
            }
            val vm = SearchViewModel(provider, data.search, data.library, data.playlists, testScope)
            vm.performSearch("queen")
            eventually { vm.resultsState.value is SearchResultsUiState.Success }

            vm.loadMore()
            eventually { vm.loadMoreError.value != null }
            assertEquals(1, (vm.resultsState.value as SearchResultsUiState.Success).results.songs.size)
            vm.loadMore()
            delay(100)
            assertEquals(1, provider.continuationCalls.size, "a failure must not auto-retry")

            vm.retryLoadMore()
            eventually {
                (vm.resultsState.value as? SearchResultsUiState.Success)?.results?.songs?.size == 2
            }
            val results = (vm.resultsState.value as SearchResultsUiState.Success).results
            assertEquals(listOf("s1", "s2"), results.songs.map { it.id })
            assertNull(results.continuationToken, "a completed cursor must not be requested again")
            assertEquals(2, provider.continuationCalls.size)
            vm.loadMore()
            delay(100)
            assertEquals(2, provider.continuationCalls.size)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun staleContinuationCannotOverwriteANewerSearch(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            val release = CompletableDeferred<Unit>()
            provider.continuationHandler = {
                try {
                    release.await()
                } catch (_: CancellationException) {
                    // Deliberately emulate a provider that answers after its
                    // caller was cancelled; the ViewModel must still drop it.
                }
                DhunResult.Success(SearchResults(query = "", songs = listOf(sampleTrack("stale"))))
            }
            val vm = SearchViewModel(provider, data.search, data.library, data.playlists, testScope)
            vm.performSearch("old")
            eventually { vm.resultsState.value is SearchResultsUiState.Success }
            vm.loadMore()
            eventually { provider.continuationCalls.size == 1 }

            provider.searchHandler = { query, _ ->
                DhunResult.Success(SearchResults(query = query, songs = listOf(sampleTrack("new"))))
            }
            vm.performSearch("new")
            eventually {
                (vm.resultsState.value as? SearchResultsUiState.Success)?.results?.songs?.singleOrNull()?.id == "new"
            }
            release.complete(Unit)
            delay(100)
            val results = (vm.resultsState.value as SearchResultsUiState.Success).results
            assertEquals("new", results.query)
            assertEquals(listOf("new"), results.songs.map { it.id })
            assertNull(vm.loadMoreError.value)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun searchFailureKeepsSanitizedEndpointDetail(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            provider.searchHandler = { _, _ ->
                DhunResult.Failure(DhunError.Parse("HTTP 400 from /search; status=INVALID_ARGUMENT"))
            }
            val vm = SearchViewModel(provider, data.search, data.library, data.playlists, testScope)
            vm.performSearch("focus")
            eventually { vm.resultsState.value is SearchResultsUiState.Error }
            val error = vm.resultsState.value as SearchResultsUiState.Error
            assertTrue(error.detail?.contains("HTTP 400 from /search") == true)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun addToPlaylistAction(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeSearchMusicProvider()
            val vm = SearchViewModel(
                provider = provider,
                searchRepository = data.search,
                libraryRepository = data.library,
                playlistRepository = data.playlists,
                scope = testScope,
            )

            val pl = data.playlists.create("Test Playlist")
            val track = sampleTrack("track_xyz")
            val added = vm.addToPlaylist(pl.id, track)
            assertTrue(added)

            val tracks = data.playlists.observeTracks(pl.id).first()
            assertEquals(1, tracks.size)
            assertEquals("track_xyz", tracks[0].id)
        } finally {
            testScope.cancel()
        }
    }
}
