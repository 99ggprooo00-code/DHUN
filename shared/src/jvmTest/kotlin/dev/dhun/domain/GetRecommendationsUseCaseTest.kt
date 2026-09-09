package dev.dhun.domain

import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HistoryEntry
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.data.PlayContext
import dev.dhun.innertube.SearchFilter
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetRecommendationsUseCaseTest {

    private fun track(id: String) = Track(id = id, title = "Track $id", artistName = "Artist $id")

    private class FakeHistory(private var recent: List<Track>) : HistoryRepository {
        override fun observeRecentlyPlayed(limit: Int): Flow<List<Track>> = flowOf(recent.take(limit))
        override suspend fun recordPlay(track: Track, context: PlayContext): Long {
            recent = listOf(track) + recent.filterNot { it.id == track.id }
            return 0L
        }
        override suspend fun markCompleted(trackId: String, playedAtEpochMs: Long) = Unit
        override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> = flowOf(emptyList())
        override suspend fun playCount(trackId: String): Long = 0L
        override suspend fun remove(entryId: Long) = Unit
        override suspend fun clear() = Unit
    }

    private class FakeLibrary(private var favorites: List<Track>) : LibraryRepository {
        override fun observeFavorites(): Flow<List<Track>> = flowOf(favorites)
        override fun observeFavoriteIds(): Flow<Set<String>> = flowOf(favorites.map { it.id }.toSet())
        override fun observeIsFavorite(trackId: String): Flow<Boolean> = flowOf(favorites.any { it.id == trackId })
        override suspend fun isFavorite(trackId: String): Boolean = favorites.any { it.id == trackId }
        override suspend fun addFavorite(track: Track) {
            favorites = favorites.filterNot { it.id == track.id } + track
        }
        override suspend fun removeFavorite(trackId: String) {
            favorites = favorites.filterNot { it.id == trackId }
        }
    }

    private class StubProvider(
        var related: Map<String, List<Track>> = emptyMap(),
        var relatedResult: DhunResult<List<Track>>? = null,
    ) : MusicProvider {
        val requestedSeeds = mutableListOf<String>()

        override suspend fun search(query: String, filter: SearchFilter) = DhunResult.Success(SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) = DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun homeFeed(): DhunResult<List<HomeSection>> = DhunResult.Success(emptyList())
        override suspend fun homeFeedPage(): DhunResult<dev.dhun.core.HomeFeedPage> =
            DhunResult.Success(dev.dhun.core.HomeFeedPage())
        override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<dev.dhun.core.HomeFeedPage> =
            DhunResult.Success(dev.dhun.core.HomeFeedPage())

        override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> {
            requestedSeeds.add(videoId)
            relatedResult?.let { return it }
            return DhunResult.Success(related[videoId].orEmpty())
        }

        override suspend fun getStreamInfo(videoId: String) = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun getLyrics(videoId: String) = DhunResult.Success(Lyrics.NotAvailable)
        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> =
            DhunResult.Failure(DhunError.Unavailable())
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> =
            DhunResult.Failure(DhunError.Unavailable())
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> =
            DhunResult.Failure(DhunError.Unavailable())
    }

    @Test
    fun localPicksReturnsSavedSongsNotJustReplayed() = runTest {
        // Recent history: A (played), B (played). A is also favourited, so as a
        // just-replayed favourite it is NOT a fresh pick; F is saved & not recent.
        val history = FakeHistory(listOf(track("B"), track("A")))
        val library = FakeLibrary(listOf(track("A"), track("F")))
        val useCase = GetRecommendationsUseCase(StubProvider(), history, library)

        // Listen-again shows A and B; only F (saved, not just replayed) survives.
        assertEquals(listOf("F"), useCase.localPicks(excludeIds = emptySet()).map { it.id })
    }

    @Test
    fun localPicksNeverDuplicatesRecentWindow() = runTest {
        // A user who favourites only songs they replay constantly has no local picks.
        val history = FakeHistory(listOf(track("A")))
        val library = FakeLibrary(listOf(track("A")))
        val useCase = GetRecommendationsUseCase(StubProvider(), history, library)
        assertTrue(useCase.localPicks(excludeIds = emptySet()).isEmpty())
    }

    @Test
    fun localPicksExcludesIdsAlreadyOnScreen() = runTest {
        val history = FakeHistory(emptyList())
        val library = FakeLibrary(listOf(track("F")))
        val useCase = GetRecommendationsUseCase(StubProvider(), history, library)

        val picks = useCase.localPicks(excludeIds = setOf("F"))
        assertTrue(picks.isEmpty())
    }

    @Test
    fun localPicksIsEmptyWhenNoFavorites() = runTest {
        val useCase = GetRecommendationsUseCase(StubProvider(), FakeHistory(emptyList()), FakeLibrary(emptyList()))
        assertTrue(useCase.localPicks(excludeIds = emptySet()).isEmpty())
    }

    @Test
    fun historySeededUsesRecentDistinctSeedsAndDedupes() = runTest {
        // Newest first: C, B, A (distinct). A/B played twice collapses to a single seed each.
        val history = FakeHistory(listOf(track("C"), track("B"), track("B"), track("A"), track("A")))
        val library = FakeLibrary(emptyList())
        val provider = StubProvider(
            related = mapOf(
                "C" to listOf(track("r1"), track("r2")),
                "B" to listOf(track("r2"), track("r3")),
                "A" to listOf(track("A")), // the seed itself must be dropped
            ),
        )
        val useCase = GetRecommendationsUseCase(provider, history, library)

        val remote = useCase.historySeeded(excludeIds = emptySet(), seedCount = 3, limit = 10)

        // r2 collides across C and B and must appear once; A (the seed) is dropped.
        assertEquals(listOf("r1", "r2", "r3"), remote.map { it.id })
        assertEquals(listOf("C", "B", "A"), provider.requestedSeeds)
    }

    @Test
    fun historySeededSkipsVisibleAndStopsAtLimit() = runTest {
        val history = FakeHistory(listOf(track("C"), track("B"), track("A")))
        val provider = StubProvider(
            related = mapOf(
                "C" to listOf(track("c1"), track("c2")),
                "B" to listOf(track("b1")),
            ),
        )
        val useCase = GetRecommendationsUseCase(provider, history, FakeLibrary(emptyList()))

        // c1 is already on screen (excluded); limit 2 stops before B is queried.
        val remote = useCase.historySeeded(excludeIds = setOf("c1"), seedCount = 3, limit = 2)
        assertEquals(listOf("c2", "b1"), remote.map { it.id })
        assertEquals(listOf("C", "B"), provider.requestedSeeds)
    }

    @Test
    fun historySeededReturnsEmptyWhenEveryRelatedHopFails() = runTest {
        val history = FakeHistory(listOf(track("A")))
        val provider = StubProvider(relatedResult = DhunResult.Failure(DhunError.Network()))
        val useCase = GetRecommendationsUseCase(provider, history, FakeLibrary(emptyList()))

        assertTrue(useCase.historySeeded(excludeIds = emptySet()).isEmpty())
    }

    @Test
    fun historySeededHandlesEmptyHistory() = runTest {
        val provider = StubProvider()
        val useCase = GetRecommendationsUseCase(provider, FakeHistory(emptyList()), FakeLibrary(emptyList()))
        assertTrue(useCase.historySeeded(excludeIds = emptySet()).isEmpty())
        assertTrue(provider.requestedSeeds.isEmpty())
    }
}
