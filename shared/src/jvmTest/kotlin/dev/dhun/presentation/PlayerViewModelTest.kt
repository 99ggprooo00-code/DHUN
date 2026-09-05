package dev.dhun.presentation

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.SearchResults
import dev.dhun.core.Track
import dev.dhun.player.DhunPlayer
import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.StreamInfo
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.player.LyricsUiState
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.player.RelatedUiState
import dev.dhun.provider.MusicProvider
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerViewModelTest {

    private fun track(id: String, title: String = "Song $id") =
        Track(id = id, title = title, artistName = "Artist $id", durationSeconds = 200)

    /** Scripted DhunPlayer: state flows writable, calls counted. */
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

        var nextCalls = 0
        var previousCalls = 0
        var playPauseCalls = 0
        var playAtCalls = mutableListOf<Int>()
        var removedAt = mutableListOf<Int>()
        var moves = mutableListOf<Pair<Int, Int>>()
        var seeks = mutableListOf<Long>()

        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            queue.value = tracks
            currentQueueIndex.value =
                if (tracks.isEmpty()) -1 else startIndex.coerceIn(0, tracks.size - 1)
            currentTrack.value = tracks.getOrNull(startIndex)
            state.value = if (playWhenReady && tracks.isNotEmpty()) {
                PlaybackState.Playing(tracks[startIndex])
            } else {
                tracks.getOrNull(startIndex)?.let { PlaybackState.Paused(it) } ?: PlaybackState.Idle
            }
        }

        override fun addNext(track: Track) { queue.value = queue.value + track }
        override fun addToQueue(track: Track) { queue.value = queue.value + track }
        override fun playAt(index: Int) {
            playAtCalls += index
            queue.value.getOrNull(index)?.let { t ->
                currentQueueIndex.value = index
                currentTrack.value = t
                state.value = PlaybackState.Playing(t)
            }
        }
        override fun removeFromQueue(index: Int) { removedAt += index }
        override fun moveInQueue(from: Int, to: Int) { moves += from to to }
        override fun playPause() { playPauseCalls++ }
        override fun next() { nextCalls++ }
        override fun previous() { previousCalls++ }
        override fun seekTo(positionMs: Long) { seeks += positionMs; this.positionMs.value = positionMs }
        override fun setRepeatMode(mode: RepeatMode) { repeatMode.value = mode }
        override fun setShuffle(enabled: Boolean) { shuffleEnabled.value = enabled }
        override fun setVolume(volume: Float) { this.volume.value = volume.coerceIn(0f, 1f) }
        override fun stop() { state.value = PlaybackState.Idle }
    }

    private class FakeProvider(
        var related: DhunResult<List<Track>> = DhunResult.Success(emptyList()),
        var lyrics: DhunResult<Lyrics> = DhunResult.Success(Lyrics.NotAvailable),
    ) : MusicProvider {
        override suspend fun search(query: String, filter: SearchFilter) = DhunResult.Success(SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) = DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun homeFeed() = DhunResult.Success(emptyList<HomeSection>())
        override suspend fun relatedTracks(videoId: String) = related
        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> = DhunResult.Failure(DhunError.Unavailable)
        override suspend fun getLyrics(videoId: String) = lyrics
        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> = DhunResult.Failure(DhunError.Unavailable)
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> = DhunResult.Failure(DhunError.Unavailable)
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> = DhunResult.Failure(DhunError.Unavailable)
    }

    private suspend fun eventually(timeoutMs: Long = 15_000, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(10) }
    }

    private fun newVm(
        player: FakePlayer,
        provider: FakeProvider,
        scope: CoroutineScope,
    ) = PlayerViewModel(player, provider, scope)

    @Test
    fun repeatModeCyclesOffAllOneOff(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = newVm(FakePlayer(), FakeProvider(), scope)
            assertEquals(RepeatMode.OFF, vm.repeatMode.value)
            assertEquals(RepeatMode.ALL, vm.cycleRepeatMode())
            assertEquals(RepeatMode.ALL, vm.repeatMode.value)
            assertEquals(RepeatMode.ONE, vm.cycleRepeatMode())
            assertEquals(RepeatMode.OFF, vm.cycleRepeatMode())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun toggleShuffleAndVolumeFlows(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            val vm = newVm(player, FakeProvider(), scope)
            assertFalse(vm.shuffleEnabled.value)
            assertTrue(vm.toggleShuffle())
            assertTrue(player.shuffleEnabled.value)
            assertFalse(vm.toggleShuffle())

            vm.setVolume(0.4f)
            assertEquals(0.4f, player.volume.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun previousRestartsTrackWhenPastThreeSeconds(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            val vm = newVm(player, FakeProvider(), scope)
            player.positionMs.value = 10_000
            vm.previous()
            assertEquals(0, player.previousCalls)
            assertEquals(listOf(0L), player.seeks)

            player.positionMs.value = 1_000
            vm.previous()
            assertEquals(1, player.previousCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun queueOpsDelegateToPlayer(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            player.prepareQueue(listOf(track("1"), track("2"), track("3")), 0)
            val vm = newVm(player, FakeProvider(), scope)

            vm.playQueueAt(2)
            assertEquals(listOf(2), player.playAtCalls)
            vm.removeQueueItem(1)
            assertEquals(listOf(1), player.removedAt)
            vm.moveQueueItem(0, 2)
            assertEquals(listOf(0 to 2), player.moves)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun relatedAndLyricsLoadOnTrackChange(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            val provider = FakeProvider(
                related = DhunResult.Success(listOf(track("r1"), track("r2"))),
                lyrics = DhunResult.Success(
                    Lyrics.Synced(listOf(LyricsLine(0, "hello"), LyricsLine(1000, "world"))),
                ),
            )
            val vm = newVm(player, provider, scope)

            player.prepareQueue(listOf(track("a")), 0)
            eventually { vm.relatedState.value is RelatedUiState.Success }
            assertEquals(2, (vm.relatedState.value as RelatedUiState.Success).tracks.size)
            eventually { vm.lyricsState.value is LyricsUiState.Synced }
            assertEquals(2, (vm.lyricsState.value as LyricsUiState.Synced).lines.size)

            // startRadio plays the related list from the top
            vm.startRadio()
            eventually { player.queue.value.map { it.id } == listOf("r1", "r2") }
            assertEquals("r1", player.currentTrack.value?.id)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun relatedErrorMapsToErrorState(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            val provider = FakeProvider(related = DhunResult.Failure(DhunError.Network))
            val vm = newVm(player, provider, scope)
            player.prepareQueue(listOf(track("a")), 0)
            eventually { vm.relatedState.value is RelatedUiState.Error }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun holdSeekStepsUntilReleased(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            player.prepareQueue(listOf(track("a")), 0)
            player.durationMs.value = 200_000 // 200s → step = max(1666, 1000)=1666ms
            val vm = newVm(player, FakeProvider(), scope)
            vm.beginHoldSeek(forward = true)
            eventually { player.seeks.size >= 2 }
            vm.endHoldSeek()
            val countAtRelease = player.seeks.size
            delay(400)
            // no more seeks after release
            assertEquals(countAtRelease, player.seeks.size)
            // forward steps are positive and increasing
            assertTrue(player.seeks.last() > player.seeks.first())
        } finally {
            scope.cancel()
        }
    }
}
