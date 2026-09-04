package dev.dhun.presentation

import dev.dhun.core.AlbumDetail
import dev.dhun.core.Artist
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaybackState
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.RepeatMode
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.innertube.SearchFilter
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.browse.AlbumUiState
import dev.dhun.presentation.browse.AlbumViewModel
import dev.dhun.presentation.browse.ArtistUiState
import dev.dhun.presentation.browse.ArtistViewModel
import dev.dhun.presentation.browse.PlaylistUiState
import dev.dhun.presentation.browse.PlaylistViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseViewModelTest {

    private fun track(id: String) = Track(id = id, title = "Song $id", artistName = "Artist $id", durationSeconds = 100)

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

        var preparedStartIndex = -1

        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            queue.value = tracks
            preparedStartIndex = startIndex
            currentTrack.value = tracks.getOrNull(startIndex)
            currentQueueIndex.value = if (tracks.isEmpty()) -1 else startIndex
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

    private class FakeBrowseProvider(
        var artist: DhunResult<ArtistPage> = DhunResult.Failure(DhunError.Network),
        var album: DhunResult<AlbumDetail> = DhunResult.Failure(DhunError.Network),
        var playlist: DhunResult<PlaylistDetail> = DhunResult.Failure(DhunError.Network),
        var radio: List<Track> = emptyList(),
    ) : MusicProvider {
        override suspend fun search(query: String, filter: SearchFilter) = DhunResult.Success(SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) = DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun homeFeed() = DhunResult.Success(emptyList<HomeSection>())
        override suspend fun relatedTracks(videoId: String) = DhunResult.Success(radio)
        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> = DhunResult.Failure(DhunError.Unavailable)
        override suspend fun getLyrics(videoId: String) = DhunResult.Success(Lyrics.NotAvailable)
        override suspend fun artistPage(browseId: String) = artist
        override suspend fun albumPage(browseId: String) = album
        override suspend fun playlistPage(browseId: String) = playlist
    }

    private suspend fun eventually(timeoutMs: Long = 5_000, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(10) }
    }

    @Test
    fun artistPageLoadsAndPlaysTopSongs(): Unit = runBlocking {
        val player = FakePlayer()
        val provider = FakeBrowseProvider(
            artist = DhunResult.Success(
                ArtistPage(
                    artist = Artist(id = "UC1", name = "Queen"),
                    topSongs = listOf(track("t1"), track("t2"), track("t3")),
                ),
            ),
            radio = listOf(track("r1")),
        )
        val vm = ArtistViewModel(provider, player, "UC1")
        try {
            eventually { vm.state.value is ArtistUiState.Success }
            val page = (vm.state.value as ArtistUiState.Success).page
            assertEquals("Queen", page.artist.name)
            assertEquals(3, page.topSongs.size)

            vm.playTopSongs(1)
            eventually { player.queue.value.size == 3 }
            assertEquals(1, player.preparedStartIndex)

            vm.startRadio()
            eventually { player.queue.value.size == 2 && player.queue.value[0].id == "t1" }
        } finally {
            vm.close()
        }
    }

    @Test
    fun artistPageErrorMaps(): Unit = runBlocking {
        val vm = ArtistViewModel(FakeBrowseProvider(), FakePlayer(), "UCbad")
        try {
            eventually { vm.state.value is ArtistUiState.Error }
        } finally {
            vm.close()
        }
    }

    @Test
    fun albumLoadsAndPlaysInOrder(): Unit = runBlocking {
        val player = FakePlayer()
        val provider = FakeBrowseProvider(
            album = DhunResult.Success(
                AlbumDetail(
                    id = "MPREb_x",
                    title = "Album X",
                    artistName = "Artist X",
                    tracks = listOf(track("a1"), track("a2")),
                ),
            ),
        )
        val vm = AlbumViewModel(provider, player, "MPREb_x")
        try {
            eventually { vm.state.value is AlbumUiState.Success }
            vm.play(1)
            eventually { player.queue.value.map { it.id } == listOf("a1", "a2") }
            assertEquals(1, player.preparedStartIndex)

            vm.playShuffled()
            eventually { player.shuffleEnabled.value }
        } finally {
            vm.close()
        }
    }

    @Test
    fun remotePlaylistLoadsAndPlays(): Unit = runBlocking {
        val player = FakePlayer()
        val provider = FakeBrowseProvider(
            playlist = DhunResult.Success(
                PlaylistDetail(
                    id = "VLx",
                    title = "Hits",
                    tracks = listOf(track("p1"), track("p2")),
                ),
            ),
        )
        val data = DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))
        val vm = PlaylistViewModel(provider, data.playlists, player, "VLx", isLocal = false)
        try {
            eventually { vm.state.value is PlaylistUiState.Remote }
            vm.play(0)
            eventually { player.queue.value.size == 2 }
        } finally {
            vm.close()
        }
    }

    @Test
    fun localPlaylistCrud(): Unit = runBlocking {
        val player = FakePlayer()
        val data = DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))
        val created = data.playlists.create("Workout")
        data.playlists.addTrack(created.id, track("w1"))
        data.playlists.addTrack(created.id, track("w2"))
        data.playlists.addTrack(created.id, track("w3"))

        val vm = PlaylistViewModel(FakeBrowseProvider(), data.playlists, player, created.id, isLocal = true)
        try {
            eventually { (vm.state.value as? PlaylistUiState.Local)?.tracks?.size == 3 }
            val local = vm.state.value as PlaylistUiState.Local
            assertEquals("Workout", local.playlist?.name)

            // reorder
            vm.moveTrack(0, 2)
            eventually { (vm.state.value as? PlaylistUiState.Local)?.tracks?.map { it.id } == listOf("w2", "w3", "w1") }

            // remove
            vm.removeTrack(track("w3"))
            eventually { (vm.state.value as? PlaylistUiState.Local)?.tracks?.size == 2 }

            // rename
            vm.rename("Evening")
            eventually { (vm.state.value as? PlaylistUiState.Local)?.playlist?.name == "Evening" }

            // play from local
            vm.play(0)
            eventually { player.queue.value.size == 2 }

            // delete → deleted flag flips
            vm.delete()
            eventually { vm.deleted.value }
            eventually { (vm.state.value as? PlaylistUiState.Local)?.playlist == null }
            assertNull((vm.state.value as PlaylistUiState.Local).playlist)
        } finally {
            vm.close()
        }
    }
}
