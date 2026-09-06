package dev.dhun.domain

import dev.dhun.core.HistoryEntry
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.EpochClock
import dev.dhun.data.PlayContext
import dev.dhun.data.SettingsKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 05 use cases, exercised end-to-end through the real repositories on
 * an in-memory DB (the repositories have their own unit tests; here we check
 * the business rules the use cases add on top).
 */
class UseCasesTest {

    private class FakeClock(var now: Long = 1_700_000_000_000L) : EpochClock {
        override fun nowMs(): Long = now
    }

    private fun data(clock: EpochClock = FakeClock()) =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()), clock)

    private fun track(id: String) = Track(id = id, title = "T$id", artistName = "A$id")

    @Test
    fun toggleFavoriteFlipsAndReports(): Unit = runBlocking {
        val d = data()
        val toggle = ToggleFavoriteUseCase(d.library)
        val observe = ObserveFavoritesUseCase(d.library)
        assertTrue(toggle(track("a")))
        assertTrue(observe.isFavorite("a").first())
        assertFalse(toggle(track("a")))
        assertFalse(observe.isFavorite("a").first())
        assertTrue(observe().first().isEmpty())
    }

    @Test
    fun playlistUseCasesEnforceRules(): Unit = runBlocking {
        val d = data()
        val pl = CreatePlaylistUseCase(d.playlists)("Mix")
        val add = AddToPlaylistUseCase(d.playlists)
        assertEquals(2, add(pl.id, listOf(track("x"), track("y"))))
        assertEquals(1, add(pl.id, listOf(track("y"), track("z")))) // one duplicate
        assertEquals(listOf("x", "y", "z"), ObservePlaylistsUseCase(d.playlists).tracks(pl.id).first().map { it.id })

        ReorderPlaylistUseCase(d.playlists)(pl.id, 2, 0)
        assertEquals(listOf("z", "x", "y"), ObservePlaylistsUseCase(d.playlists).tracks(pl.id).first().map { it.id })

        RemoveFromPlaylistUseCase(d.playlists)(pl.id, "x")
        assertEquals(listOf("z", "y"), ObservePlaylistsUseCase(d.playlists).tracks(pl.id).first().map { it.id })

        assertFailsWith<IllegalArgumentException> { RenamePlaylistUseCase(d.playlists)(pl.id, "   ") }
        RenamePlaylistUseCase(d.playlists)(pl.id, "Mix 2")
        assertEquals("Mix 2", ObservePlaylistsUseCase(d.playlists).one(pl.id).first()?.name)

        DeletePlaylistUseCase(d.playlists)(pl.id)
        assertNull(ObservePlaylistsUseCase(d.playlists).one(pl.id).first())
    }

    @Test
    fun recordPlayThenCompleteMarksOnlyThatRow(): Unit = runBlocking {
        val clock = FakeClock()
        val d = data(clock)
        val record = RecordPlayUseCase(d.history)
        val h1 = record(track("s"), PlayContext.SEARCH)
        clock.now += 1000
        record(track("s"), PlayContext.QUEUE)
        record.complete(h1)
        val entries = GetHistoryUseCase(d.history)().first()
        assertEquals(2, entries.size)
        assertTrue(entries.last().completedPlayback) // the first play
        assertFalse(entries.first().completedPlayback)
        assertEquals(listOf("s"), GetRecentlyPlayedUseCase(d.history)().first().map { it.id })
    }

    @Test
    fun historyGroupsByLocalDay() {
        val uc = GetHistoryUseCase(FakeHistoryRepo)
        val day = 86_400_000L
        val t = track("g")
        val entries = listOf(
            HistoryEntry(t, playedAtEpochMs = 10 * day + 3_600_000), // day 10, 01:00 UTC
            HistoryEntry(t, playedAtEpochMs = 10 * day + 82_800_000), // day 10, 23:00 UTC
            HistoryEntry(t, playedAtEpochMs = 9 * day + 1),
        )
        val utc = uc.groupByDay(entries, utcOffsetMs = 0)
        assertEquals(listOf(10 * day, 9 * day), utc.map { it.dayStartEpochMs })
        assertEquals(2, utc[0].entries.size)

        // At UTC+2, the 23:00 entry belongs to the NEXT local day.
        val plus2 = uc.groupByDay(entries, utcOffsetMs = 2 * 3_600_000)
        assertEquals(3, plus2.size)
        assertEquals(11 * day - 2 * 3_600_000, plus2[0].dayStartEpochMs)
    }

    @Test
    fun settingsUseCasesValidateKeysAndApplyDefaults(): Unit = runBlocking {
        val d = data()
        val get = GetSettingUseCase(d.settings)
        val set = UpdateSettingUseCase(d.settings)
        assertEquals("high", get.audioQuality())
        assertEquals("US", get.countryCode())
        assertTrue(get.resumeOnLaunch())
        set.string(SettingsKeys.COUNTRY_CODE, "IN")
        assertEquals("IN", get.countryCode())
        set.boolean(SettingsKeys.RESUME_ON_LAUNCH, false)
        assertFalse(get.resumeOnLaunch())
        assertFailsWith<IllegalArgumentException> { set.string("not_a_key", "x") }
    }

    @Test
    fun recentSearchesUseCase(): Unit = runBlocking {
        val clock = FakeClock()
        val d = data(clock)
        val uc = RecentSearchesUseCase(d.search)
        uc.record("queen"); clock.now += 1000; uc.record("abba")
        assertEquals(listOf("abba", "queen"), uc.observe().first())
        uc.remove("abba")
        assertEquals(listOf("queen"), uc.observe().first())
        uc.clear()
        assertTrue(uc.observe().first().isEmpty())
    }

    @Test
    fun nowPlayingSaveAndRestoreHonoursResumeSetting(): Unit = runBlocking {
        val d = data()
        val save = SaveNowPlayingUseCase(d.nowPlaying)
        val restore = RestoreNowPlayingUseCase(d.nowPlaying, d.settings)
        assertNull(restore())
        save(listOf(track("1"), track("2")), currentIndex = 1, positionMs = 9_000, repeatMode = RepeatMode.ONE)
        val snap = assertNotNull(restore())
        assertEquals("2", snap.currentTrack?.id)
        assertEquals(RepeatMode.ONE, snap.repeatMode)
        save.progress(0, 1_234)
        assertEquals(1_234, restore()!!.positionMs)

        UpdateSettingUseCase(d.settings).boolean(SettingsKeys.RESUME_ON_LAUNCH, false)
        assertNull(restore()) // user opted out: nothing restored, data kept
        UpdateSettingUseCase(d.settings).boolean(SettingsKeys.RESUME_ON_LAUNCH, true)
        assertNotNull(restore())
        save.clear()
        assertNull(restore())
    }

    /* ---------------- home feed pagination (endless scroll) ---------------- */

    private class PagingProvider(
        private val first: dev.dhun.core.HomeFeedPage,
        private val pages: Map<String, dev.dhun.core.HomeFeedPage>,
    ) : dev.dhun.provider.MusicProvider {
        var continuationCalls = 0
        override suspend fun homeFeedPage(): DhunResult<dev.dhun.core.HomeFeedPage> =
            DhunResult.Success(first)

        override suspend fun homeFeedContinuation(
            continuationToken: String,
        ): DhunResult<dev.dhun.core.HomeFeedPage> {
            continuationCalls++
            return DhunResult.Success(
                pages[continuationToken] ?: dev.dhun.core.HomeFeedPage(),
            )
        }

        // Unused by GetHomeFeedUseCase — MusicProvider's other members.
        override suspend fun search(query: String, filter: dev.dhun.innertube.SearchFilter) =
            DhunResult.Success(dev.dhun.core.SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) =
            DhunResult.Success(dev.dhun.core.SearchResults(""))
        override suspend fun searchSuggestions(query: String) =
            DhunResult.Success(emptyList<String>())
        override suspend fun relatedTracks(videoId: String) =
            DhunResult.Success(emptyList<Track>())
        override suspend fun getStreamInfo(videoId: String) =
            DhunResult.Failure(dev.dhun.core.DhunError.Unavailable)
        override suspend fun getLyrics(videoId: String) =
            DhunResult.Success<dev.dhun.core.Lyrics>(dev.dhun.core.Lyrics.NotAvailable)
        override suspend fun artistPage(browseId: String) =
            DhunResult.Failure(dev.dhun.core.DhunError.Unavailable)
        override suspend fun albumPage(browseId: String) =
            DhunResult.Failure(dev.dhun.core.DhunError.Unavailable)
        override suspend fun playlistPage(browseId: String) =
            DhunResult.Failure(dev.dhun.core.DhunError.Unavailable)
    }

    private fun shelf(title: String, vararg trackIds: String) = dev.dhun.core.HomeSection(
        title = title,
        items = trackIds.map { dev.dhun.core.HomeItem.TrackItem(track(it)) },
    )

    @Test
    fun homeFeedCarriesTheContinuationTokenAndLoadMoreAppendsTheNextPage() = runBlocking {
        val provider = PagingProvider(
            first = dev.dhun.core.HomeFeedPage(
                sections = listOf(shelf("Made for you", "a1", "a2")),
                continuationToken = "page2",
            ),
            pages = mapOf(
                "page2" to dev.dhun.core.HomeFeedPage(
                    sections = listOf(shelf("Charts", "b1")),
                    continuationToken = "page3",
                ),
            ),
        )
        val useCase = GetHomeFeedUseCase(provider, FakeHistoryRepo)

        val first = (useCase() as DhunResult.Success).value
        assertEquals("page2", first.continuationToken)
        assertEquals(listOf("Made for you"), first.sections.map { it.title })

        val second = (useCase.loadMore(first) as DhunResult.Success).value
        assertEquals(
            listOf("Made for you", "Charts"),
            second.sections.map { it.title },
            "the next page must be appended, not replace the shelves on screen",
        )
        assertEquals("page3", second.continuationToken)
    }

    @Test
    fun homePaginationStopsWhenTheServerRunsOutOfShelves() = runBlocking {
        val provider = PagingProvider(
            first = dev.dhun.core.HomeFeedPage(
                sections = listOf(shelf("Made for you", "a1")),
                continuationToken = "last",
            ),
            pages = mapOf(
                "last" to dev.dhun.core.HomeFeedPage(
                    sections = listOf(shelf("Charts", "b1")),
                    continuationToken = null,
                ),
            ),
        )
        val useCase = GetHomeFeedUseCase(provider, FakeHistoryRepo)

        val feed = (useCase() as DhunResult.Success).value
        val exhausted = (useCase.loadMore(feed) as DhunResult.Success).value
        assertNull(exhausted.continuationToken, "no token left means the feed is finished")

        val after = (useCase.loadMore(exhausted) as DhunResult.Success).value
        assertEquals(0, provider.continuationCalls - 1, "an exhausted feed must not hit the network")
        assertEquals(exhausted.sections.size, after.sections.size)
    }

    @Test
    fun homePaginationDropsDuplicateShelvesAndStopsOnAnEmptyPage() = runBlocking {
        val provider = PagingProvider(
            first = dev.dhun.core.HomeFeedPage(
                sections = listOf(shelf("Made for you", "a1")),
                continuationToken = "dup",
            ),
            pages = mapOf(
                // Same shelf title again: LazyColumn keys are title-based, so a
                // duplicate would crash the list — and an empty page means the
                // server is just repeating itself.
                "dup" to dev.dhun.core.HomeFeedPage(
                    sections = listOf(shelf("Made for you", "a9")),
                    continuationToken = "forever",
                ),
            ),
        )
        val useCase = GetHomeFeedUseCase(provider, FakeHistoryRepo)

        val feed = (useCase() as DhunResult.Success).value
        val after = (useCase.loadMore(feed) as DhunResult.Success).value
        assertEquals(1, after.sections.size, "a repeated shelf must not be appended")
        assertEquals("a1", (after.sections[0].items[0] as dev.dhun.core.HomeItem.TrackItem).track.id)
        assertNull(after.continuationToken, "a page that adds nothing ends pagination")
    }

    /** Minimal fake for the pure grouping test (no DB needed). */
    private object FakeHistoryRepo : dev.dhun.data.HistoryRepository {
        override suspend fun recordPlay(track: Track, context: PlayContext): Long = 0
        override suspend fun markCompleted(trackId: String, playedAtEpochMs: Long) = Unit
        override fun observeHistory(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<HistoryEntry>())
        override fun observeRecentlyPlayed(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<Track>())
        override suspend fun playCount(trackId: String): Long = 0
        override suspend fun remove(entryId: Long) = Unit
        override suspend fun clear() = Unit
    }
}
