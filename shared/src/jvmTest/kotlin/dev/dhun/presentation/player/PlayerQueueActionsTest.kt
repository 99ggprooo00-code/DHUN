package dev.dhun.presentation.player

import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaybackState
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.RepeatMode
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.PlayContext
import dev.dhun.innertube.SearchFilter
import dev.dhun.player.DhunPlayer
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerQueueActionsTest {
    private fun track(id: String) = Track(id, "Song $id", "Artist", durationSeconds = 120)

    @Test
    fun currentSnapshotDelegatesCorrectOccurrenceIncludingDuplicates() = runTest {
        val player = RecordingPlayer()
        val snapshot = listOf(track("a"), track("b"), track("a"))
        player.queue.value = snapshot
        val vm = PlayerViewModel(player, MetadataProvider(), backgroundScope)

        assertTrue(vm.playQueueAt(2, snapshot))
        assertTrue(vm.removeQueueItem(2, snapshot))
        assertTrue(vm.moveQueueItem(2, 0, snapshot))
        assertEquals(listOf(2), player.playedIndices)
        assertEquals(listOf(2), player.removedIndices)
        assertEquals(listOf(2 to 0), player.moves)
    }

    @Test
    fun staleMenuCannotMutateAReplacementQueue() = runTest {
        val player = RecordingPlayer()
        val snapshot = listOf(track("a"), track("b"))
        player.queue.value = snapshot
        val vm = PlayerViewModel(player, MetadataProvider(), backgroundScope)
        player.queue.value = listOf(track("c"), track("a"))

        assertFalse(vm.playQueueAt(0, snapshot))
        assertFalse(vm.removeQueueItem(0, snapshot))
        assertFalse(vm.moveQueueItem(0, 1, snapshot))
        assertTrue(player.playedIndices.isEmpty())
        assertTrue(player.removedIndices.isEmpty())
        assertTrue(player.moves.isEmpty())
    }

    @Test
    fun sameIdAtIndexDoesNotMakeAReorderedSnapshotSafe() = runTest {
        val player = RecordingPlayer()
        val snapshot = listOf(track("a"), track("b"), track("a"))
        val vm = PlayerViewModel(player, MetadataProvider(), backgroundScope)
        player.queue.value = listOf(track("a"), track("a"), track("b"))

        assertFalse(vm.removeQueueItem(0, snapshot))
        assertFalse(vm.moveQueueItem(0, 2, snapshot))
        assertTrue(player.removedIndices.isEmpty())
        assertTrue(player.moves.isEmpty())
    }

    @Test
    fun invalidIndicesAndNoOpMovesAreRejected() = runTest {
        val player = RecordingPlayer()
        val snapshot = listOf(track("a"), track("b"))
        player.queue.value = snapshot
        val vm = PlayerViewModel(player, MetadataProvider(), backgroundScope)

        assertFalse(vm.playQueueAt(-1, snapshot))
        assertFalse(vm.removeQueueItem(snapshot.size, snapshot))
        assertFalse(vm.moveQueueItem(-1, 0, snapshot))
        assertFalse(vm.moveQueueItem(0, snapshot.size, snapshot))
        assertFalse(vm.moveQueueItem(0, 0, snapshot))
        assertTrue(player.playedIndices.isEmpty())
        assertTrue(player.removedIndices.isEmpty())
        assertTrue(player.moves.isEmpty())
    }

    @Test
    fun appendLeavesCurrentPlaybackAndQueueOrderIntact() = runTest {
        val player = RecordingPlayer()
        val snapshot = listOf(track("a"), track("b"))
        player.queue.value = snapshot
        player.currentTrack.value = snapshot[1]
        player.currentQueueIndex.value = 1
        val vm = PlayerViewModel(player, MetadataProvider(), backgroundScope)
        val related = track("c")

        vm.addToQueue(related)

        assertEquals(snapshot + related, player.queue.value)
        assertEquals(snapshot[1], player.currentTrack.value)
        assertEquals(1, player.currentQueueIndex.value)
        assertTrue(player.preparedQueues.isEmpty())
    }

    @Test
    fun relatedPlaybackUsesDisplayedSnapshotNotNewProviderResult() = runTest {
        val player = RecordingPlayer()
        val displayed = listOf(track("r1"), track("r2"))
        val refreshed = listOf(track("new"))
        val vm = PlayerViewModel(player, MetadataProvider(refreshed), backgroundScope)
        player.currentTrack.value = track("source")
        runCurrent()
        assertEquals(refreshed, (vm.relatedState.value as RelatedUiState.Success).tracks)

        // This is the same scope-owned action used by the Related tab.
        vm.playQueue(displayed, 1, PlayContext.QUEUE)
        runCurrent()

        assertEquals(listOf(displayed to 1), player.preparedQueues)
        assertEquals(displayed[1], player.currentTrack.value)
    }

    private class RecordingPlayer : DhunPlayer {
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val currentTrack = MutableStateFlow<Track?>(null)
        override val queue = MutableStateFlow<List<Track>>(emptyList())
        override val currentQueueIndex = MutableStateFlow(-1)
        override val positionMs = MutableStateFlow(0L)
        override val durationMs = MutableStateFlow(0L)
        override val repeatMode = MutableStateFlow(RepeatMode.OFF)
        override val shuffleEnabled = MutableStateFlow(false)
        override val volume = MutableStateFlow(1f)
        val playedIndices = mutableListOf<Int>()
        val removedIndices = mutableListOf<Int>()
        val moves = mutableListOf<Pair<Int, Int>>()
        val preparedQueues = mutableListOf<Pair<List<Track>, Int>>()

        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            preparedQueues += tracks to startIndex
            queue.value = tracks
            currentQueueIndex.value = startIndex
            currentTrack.value = tracks.getOrNull(startIndex)
        }
        override fun addNext(track: Track) { queue.value = queue.value + track }
        override fun addToQueue(track: Track) { queue.value = queue.value + track }
        override fun playAt(index: Int) { playedIndices += index }
        override fun removeFromQueue(index: Int) { removedIndices += index }
        override fun moveInQueue(from: Int, to: Int) { moves += from to to }
        override fun playPause() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) { this.positionMs.value = positionMs }
        override fun setRepeatMode(mode: RepeatMode) { repeatMode.value = mode }
        override fun setShuffle(enabled: Boolean) { shuffleEnabled.value = enabled }
        override fun setVolume(volume: Float) { this.volume.value = volume }
        override fun stop() {}
    }

    private class MetadataProvider(private val related: List<Track> = emptyList()) : MusicProvider {
        override suspend fun search(query: String, filter: SearchFilter) = DhunResult.Success(SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) = DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun homeFeed() = DhunResult.Success(emptyList<HomeSection>())
        override suspend fun homeFeedPage() = DhunResult.Success(HomeFeedPage())
        override suspend fun homeFeedContinuation(continuationToken: String) = DhunResult.Success(HomeFeedPage())
        override suspend fun relatedTracks(videoId: String) = DhunResult.Success(related)
        override suspend fun getLyrics(videoId: String) = DhunResult.Success(Lyrics.NotAvailable)
        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> = DhunResult.Failure(DhunError.Unavailable())
    }
}
