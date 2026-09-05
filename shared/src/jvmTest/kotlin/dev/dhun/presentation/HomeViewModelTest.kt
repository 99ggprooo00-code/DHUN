package dev.dhun.presentation

import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel
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

class HomeViewModelTest {

    private fun testData() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    private fun sampleTrack(id: String) = Track(id = id, title = "Track $id", artistName = "Artist $id")

    private class FakeMusicProvider(
        var feedResult: DhunResult<List<HomeSection>> = DhunResult.Success(emptyList()),
    ) : MusicProvider {
        override suspend fun search(query: String, filter: SearchFilter) = DhunResult.Success(SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) = DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun homeFeed(): DhunResult<List<HomeSection>> = feedResult
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

    private suspend fun eventually(timeoutMs: Long = 15_000, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!check()) delay(10)
        }
    }

    @Test
    fun greetingCalculation(): Unit {
        assertEquals("Good morning", GetHomeFeedUseCase.greetingForHour(6))
        assertEquals("Good morning", GetHomeFeedUseCase.greetingForHour(11))
        assertEquals("Good afternoon", GetHomeFeedUseCase.greetingForHour(12))
        assertEquals("Good afternoon", GetHomeFeedUseCase.greetingForHour(15))
        assertEquals("Good evening", GetHomeFeedUseCase.greetingForHour(18))
        assertEquals("Good evening", GetHomeFeedUseCase.greetingForHour(20))
        assertEquals("Good night", GetHomeFeedUseCase.greetingForHour(23))
        assertEquals("Good night", GetHomeFeedUseCase.greetingForHour(2))
    }

    @Test
    fun homeViewModelLoadsFeedAndExtractsQuickPicks(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val sections = listOf(
                HomeSection(
                    title = "Quick picks",
                    items = listOf(
                        HomeItem.TrackItem(sampleTrack("1")),
                        HomeItem.TrackItem(sampleTrack("2")),
                        HomeItem.TrackItem(sampleTrack("3")),
                    ),
                ),
                HomeSection(
                    title = "Recommended",
                    items = listOf(
                        HomeItem.TrackItem(sampleTrack("4")),
                    ),
                ),
            )
            val provider = FakeMusicProvider(DhunResult.Success(sections))
            val useCase = GetHomeFeedUseCase(provider, data.history)

            val vm = HomeViewModel(
                getHomeFeed = useCase,
                historyRepository = data.history,
                libraryRepository = data.library,
                scope = testScope,
            )

            eventually { vm.uiState.value is HomeUiState.Success }
            val state = vm.uiState.value as HomeUiState.Success
            val feed = state.feed
            assertEquals(3, feed.quickPicks.size)
            assertEquals(2, feed.sections.size)
            assertEquals("Track 1", feed.quickPicks[0].title)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun homeViewModelHandlesError(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeMusicProvider(DhunResult.Failure(DhunError.Network))
            val useCase = GetHomeFeedUseCase(provider, data.history)

            val vm = HomeViewModel(
                getHomeFeed = useCase,
                historyRepository = data.history,
                libraryRepository = data.library,
                scope = testScope,
            )

            eventually { vm.uiState.value is HomeUiState.Error }
            assertTrue(vm.uiState.value is HomeUiState.Error)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun homeViewModelTogglesFavorites(): Unit = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = testData()
            val provider = FakeMusicProvider()
            val useCase = GetHomeFeedUseCase(provider, data.history)

            val vm = HomeViewModel(
                getHomeFeed = useCase,
                historyRepository = data.history,
                libraryRepository = data.library,
                scope = testScope,
            )

            val track = sampleTrack("fav1")
            vm.toggleFavorite(track)
            eventually { data.library.isFavorite("fav1") }
            eventually { vm.favoriteIds.value.contains("fav1") }
            assertEquals(setOf("fav1"), vm.favoriteIds.value)
        } finally {
            testScope.cancel()
        }
    }
}
