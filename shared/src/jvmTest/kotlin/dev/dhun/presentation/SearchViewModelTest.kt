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
import kotlin.test.assertTrue

class SearchViewModelTest {

    private fun testData() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    private fun sampleTrack(id: String) = Track(id = id, title = "Song $id", artistName = "Artist $id")

    private class FakeSearchMusicProvider : MusicProvider {
        @Volatile var lastFilter: SearchFilter? = null
        @Volatile var lastQuery: String? = null

        override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> {
            lastQuery = query
            lastFilter = filter
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
            return DhunResult.Success(
                SearchResults(
                    query = "",
                    songs = listOf(Track(id = "s2", title = "Song 2", artistName = "Artist 2")),
                    continuationToken = null,
                )
            )
        }

        override suspend fun searchSuggestions(query: String): DhunResult<List<String>> {
            return DhunResult.Success(listOf("$query one", "$query two"))
        }

        override suspend fun homeFeed(): DhunResult<List<HomeSection>> = DhunResult.Success(emptyList())
        override suspend fun relatedTracks(videoId: String) = DhunResult.Success(emptyList<Track>())
        override suspend fun getStreamInfo(videoId: String) = DhunResult.Failure(DhunError.Unavailable)
        override suspend fun getLyrics(videoId: String) = DhunResult.Success(Lyrics.NotAvailable)

        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> =
            DhunResult.Failure(DhunError.Unavailable)
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> =
            DhunResult.Failure(DhunError.Unavailable)
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> =
            DhunResult.Failure(DhunError.Unavailable)
    }

    private suspend fun eventually(timeoutMs: Long = 5_000, check: suspend () -> Boolean) {
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
