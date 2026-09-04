package dev.dhun.presentation

import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.PlayContext
import dev.dhun.player.DhunPlayer
import dev.dhun.player.NowPlayingPersistence
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.presentation.library.LibraryTab
import dev.dhun.presentation.library.LibraryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LibraryViewModelTest {

    private fun track(id: String, title: String = "Song $id") =
        Track(id = id, title = title, artistName = "Artist $id", durationSeconds = 200, thumbnailUrl = null)

    private class FakePlayer : DhunPlayer {
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val currentTrack = MutableStateFlow<Track?>(null)
        override val queue = MutableStateFlow<List<Track>>(emptyList())
        override val positionMs = MutableStateFlow(0L)
        override val durationMs = MutableStateFlow(0L)
        override val currentQueueIndex = MutableStateFlow(-1)
        override val repeatMode = MutableStateFlow(RepeatMode.OFF)
        override val shuffleEnabled = MutableStateFlow(false)
        override val volume = MutableStateFlow(1f)

        var lastPrepared: List<Track> = emptyList()
        var lastIndex: Int = -1
        var prepareCalls: Int = 0
        var lastPlayContext: PlayContext? = null

        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            lastPrepared = tracks
            lastIndex = startIndex
            prepareCalls++
            queue.value = tracks
            currentTrack.value = tracks.getOrNull(startIndex)
            currentQueueIndex.value = startIndex
        }
        override fun addNext(track: Track) { queue.value = queue.value + track }
        override fun addToQueue(track: Track) { queue.value = queue.value + track }
        override fun playAt(index: Int) {}
        override fun removeFromQueue(index: Int) {}
        override fun moveInQueue(from: Int, to: Int) {}
        override fun playPause() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) { this.positionMs.value = positionMs }
        override fun setRepeatMode(mode: RepeatMode) { repeatMode.value = mode }
        override fun setShuffle(enabled: Boolean) { shuffleEnabled.value = enabled }
        override fun setVolume(volume: Float) { this.volume.value = volume }
        override fun stop() {}
    }

    private fun dataLayer() = DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    private suspend fun eventually(timeoutMs: Long = 5_000, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(20) }
    }

    @Test
    fun libraryTabsAndPlaylistsObserved(): Unit = runBlocking {
        val data = dataLayer()
        val player = FakePlayer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = LibraryViewModel(dataLayer = data, player = player, scope = scope)
            // playlists empty at start
            eventually { vm.playlistsFlow.value.isEmpty() }
            assertEquals(LibraryTab.PLAYLISTS, vm.selectedTab.value)
            vm.selectTab(LibraryTab.FAVORITES)
            assertEquals(LibraryTab.FAVORITES, vm.selectedTab.value)
            vm.selectTab(LibraryTab.HISTORY)
            assertEquals(LibraryTab.HISTORY, vm.selectedTab.value)

            // create playlist
            val pl = data.playlists.create("Gym")
            eventually { vm.playlistsFlow.value.any { it.id == pl.id } }
            assertEquals(1, vm.playlistsFlow.value.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun favoritesRoundTripAndSwipeRemove(): Unit = runBlocking {
        val data = dataLayer()
        val player = FakePlayer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = LibraryViewModel(data, player, scope)
            eventually { vm.favorites.value.isEmpty() }

            // add via repository (as Search/Home overflow would)
            data.library.addFavorite(track("f1"))
            data.library.addFavorite(track("f2"))
            data.library.addFavorite(track("f3"))
            eventually { vm.favorites.value.size == 3 }
            // favorites are ordered by addedAt DESC per SQL (newest first)
            // Our vm just mirrors that order.
            assertTrue(vm.favorites.value.map { it.id }.containsAll(listOf("f1","f2","f3")))

            // play all favorites — should queue with context LIBRARY
            // We don't have persistence here, but queue should be set.
            vm.playFavorites(0)
            eventually { player.prepareCalls == 1 }
            assertEquals(3, player.lastPrepared.size)

            // swipe-remove one
            vm.removeFavorite("f2")
            eventually { vm.favorites.value.size == 2 }
            assertFalse(vm.favorites.value.any { it.id == "f2" })

            // toggleFavorite adds back
            vm.toggleFavorite(track("f2"))
            eventually { vm.favorites.value.size == 3 }

            vm.toggleFavorite(track("f2"))
            eventually { vm.favorites.value.size == 2 }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun historyGroupedByDayAndClearWorks(): Unit = runBlocking {
        val data = dataLayer()
        val player = FakePlayer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = LibraryViewModel(data, player, scope)
            // Force UTC grouping for determinism
            vm.refreshHistoryGrouping(0)

            // Record plays at two different days (UTC)
            val dayMs = 86_400_000L
            val base = 1_700_000_000_000L // arbitrary epoch (approx 2023-11)
            // day 0 and day 1 entries
            data.history.recordPlay(track("h1"), PlayContext.SEARCH) // we will adjust timestamp via repo? But recordPlay uses clock nowMs
            // For deterministic history we directly insert via repository's clock? Easier: use DataLayer with fake clock?
            // Instead we override by manually inserting via SqlDelight? Simpler: use the real history repo and
            // wait, then manipulate: we can use the test's ability to insert with controlled timestamps
            // by using a fake EpochClock.
            // For this test we shortcut: use the domain grouping helper directly with synthetic HistoryEntry list.
            val synthetic = listOf(
                dev.dhun.core.HistoryEntry(track("h1"), playedAtEpochMs = 10 * dayMs + 1_000, entryId = 1),
                dev.dhun.core.HistoryEntry(track("h2"), playedAtEpochMs = 10 * dayMs + 2_000, entryId = 2),
                dev.dhun.core.HistoryEntry(track("h3"), playedAtEpochMs = 9 * dayMs + 500, entryId = 3),
            )
            // Verify grouping helper directly (also used by VM)
            val uc = dev.dhun.domain.GetHistoryUseCase(data.history)
            val grouped = uc.groupByDay(synthetic, utcOffsetMs = 0)
            assertEquals(2, grouped.size)
            assertEquals(10 * dayMs, grouped[0].dayStartEpochMs)
            assertEquals(2, grouped[0].entries.size)
            assertEquals(9 * dayMs, grouped[1].dayStartEpochMs)

            // Now test VM handles real recorded plays — we rely on the clock
            // For real repo, just verify that observeHistory flows show entries after recording
            data.history.recordPlay(track("real1"), PlayContext.HOME)
            data.history.recordPlay(track("real2"), PlayContext.SEARCH)
            eventually { vm.historyEntries.value.size >= 2 }
            // Grouped should also reflect (with offset 0, they are same day)
            vm.refreshHistoryGrouping(0)
            eventually { vm.groupedHistory.value.isNotEmpty() }

            // Remove one entry by id
            val toRemove = vm.historyEntries.value.first().entryId!!
            vm.removeHistoryEntry(toRemove)
            eventually { vm.historyEntries.value.none { it.entryId == toRemove } }

            // Clear all
            vm.clearHistory()
            eventually { vm.historyEntries.value.isEmpty() }
            eventually { vm.groupedHistory.value.isEmpty() }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun historyPlaybackQueuesCorrectly(): Unit = runBlocking {
        val data = dataLayer()
        val player = FakePlayer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = LibraryViewModel(data, player, scope)
            vm.refreshHistoryGrouping(0)
            // Record three tracks to create history entries
            data.history.recordPlay(track("hx1"), PlayContext.SEARCH)
            data.history.recordPlay(track("hx2"), PlayContext.HOME)
            data.history.recordPlay(track("hx3"), PlayContext.PLAYLIST)
            eventually { vm.historyEntries.value.size == 3 }

            // Play a history day — should prepareQueue with HISTORY context (persistance is null so just queue)
            val day = vm.groupedHistory.value.firstOrNull()
            if (day != null) {
                vm.playHistoryDay(day, 0)
                eventually { player.prepareCalls >= 1 }
                assertTrue(player.lastPrepared.isNotEmpty())
            }

            // Play single history entry
            val entry = vm.historyEntries.value.first()
            vm.playHistoryEntry(entry)
            eventually { player.prepareCalls >= 2 }
            assertTrue(player.lastPrepared.any { it.id == entry.track.id })

            // Verify that LibraryViewModel exposes HistoryDay grouping with offset handling
            val plus2Offset = 2 * 3_600_000L
            vm.refreshHistoryGrouping(plus2Offset)
            // After offset change, grouping should be recomputed (still non-empty)
            eventually { vm.groupedHistory.value.isNotEmpty() }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun relativeAndHeaderHelpers(): Unit = runBlocking {
        val now = 1_700_000_000_000L
        // relative
        assertEquals("just now", LibraryViewModel.relativeTimeLabel(now - 30_000, now))
        assertEquals("5m ago", LibraryViewModel.relativeTimeLabel(now - 5 * 60_000, now))
        assertEquals("2h ago", LibraryViewModel.relativeTimeLabel(now - 2 * 3_600_000, now))
        assertEquals("3d ago", LibraryViewModel.relativeTimeLabel(now - 3 * 86_400_000, now))
        // day header Today/Yesterday
        val offset = 0L
        val todayStart = run {
            val dayMs = 86_400_000L
            val localNow = now + offset
            val floored = localNow - ((localNow % dayMs + dayMs) % dayMs)
            floored - offset
        }
        assertEquals("Today", LibraryViewModel.dayHeaderLabel(todayStart, now, offset))
        assertEquals("Yesterday", LibraryViewModel.dayHeaderLabel(todayStart - 86_400_000, now, offset))
        // future fallback ISO
        val iso = LibraryViewModel.dayHeaderLabel(todayStart - 5 * 86_400_000, now, offset)
        assertTrue(iso.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun playlistPlayFires(): Unit = runBlocking {
        val data = dataLayer()
        val player = FakePlayer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = LibraryViewModel(data, player, scope)
            val pl = data.playlists.create("Roadtrip")
            data.playlists.addTrack(pl.id, track("p1"))
            data.playlists.addTrack(pl.id, track("p2"))
            eventually { vm.playlistsFlow.value.any { it.id == pl.id } }

            vm.playPlaylist(pl.id, 1)
            // playPlaylist does a flow.first() fetch; wait for queue
            eventually { player.prepareCalls == 1 }
            assertEquals(listOf("p1","p2"), player.lastPrepared.map { it.id })
            assertEquals(1, player.lastIndex)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun emptyStatesStillExposeFlows(): Unit = runBlocking {
        val data = dataLayer()
        val player = FakePlayer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = LibraryViewModel(data, player, scope)
            eventually { vm.playlistsFlow.value.isEmpty() }
            assertTrue(vm.favorites.value.isEmpty())
            assertTrue(vm.historyEntries.value.isEmpty())
            assertTrue(vm.groupedHistory.value.isEmpty())
            // Play on empty should be no-op
            vm.playFavorites(0)
            vm.playHistoryDay(dev.dhun.domain.HistoryDay(0, emptyList()), 0)
            delay(100)
            assertEquals(0, player.prepareCalls)
        } finally {
            scope.cancel()
        }
    }
}
