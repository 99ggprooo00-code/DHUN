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
        /** Next-page token served with the related (radio) page. */
        var relatedToken: String? = null,
        var lyrics: DhunResult<Lyrics> = DhunResult.Success(Lyrics.NotAvailable),
    ) : MusicProvider {
        /* Endless-radio scripts. Per-videoId pages answer BOTH the related
           load and a re-seed (same /next list — no call-order races);
           continuations consume [continuationResults] in order (last
           repeats). Untyped ids fall back to the [related] script. */
        val radioPageCalls = mutableListOf<String>()
        val continuationCalls = mutableListOf<String>()
        val radioPagesByVideo = mutableMapOf<String, DhunResult<dev.dhun.core.RadioQueuePage>>()
        val continuationResults = mutableListOf<DhunResult<dev.dhun.core.RadioQueuePage>>()
        private var continuationIndex = 0

        override suspend fun search(query: String, filter: SearchFilter) = DhunResult.Success(SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) = DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun homeFeed() = DhunResult.Success(emptyList<HomeSection>())
        override suspend fun homeFeedPage() =
            DhunResult.Success(dev.dhun.core.HomeFeedPage())
        override suspend fun homeFeedContinuation(continuationToken: String) =
            DhunResult.Success(dev.dhun.core.HomeFeedPage())
        override suspend fun relatedTracks(videoId: String) = related
        override suspend fun radioQueuePage(videoId: String): DhunResult<dev.dhun.core.RadioQueuePage> {
            radioPageCalls += videoId
            radioPagesByVideo[videoId]?.let { return it }
            return when (related) {
                is DhunResult.Success ->
                    DhunResult.Success(dev.dhun.core.RadioQueuePage(related.value, relatedToken))
                is DhunResult.Failure -> related
            }
        }
        override suspend fun radioQueueContinuation(continuationToken: String): DhunResult<dev.dhun.core.RadioQueuePage> {
            continuationCalls += continuationToken
            if (continuationIndex < continuationResults.size) {
                return continuationResults[continuationIndex++]
            }
            return continuationResults.lastOrNull()
                ?: DhunResult.Success(dev.dhun.core.RadioQueuePage())
        }
        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun getLyrics(videoId: String) = lyrics
        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> = DhunResult.Failure(DhunError.Unavailable())
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

            // startRadio is seamless: head stays, radio becomes the tail
            vm.startRadio()
            eventually { player.queue.value.map { it.id } == listOf("a", "r1", "r2") }
            assertEquals("a", player.currentTrack.value?.id)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun relatedErrorMapsToErrorState(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            val provider = FakeProvider(related = DhunResult.Failure(DhunError.Network()))
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

    @Test
    fun startRadioKeepsHeadPlayingAndReplacesTail(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            // Provider returns related list where first is the current track
            val provider = FakeProvider(
                related = DhunResult.Success(listOf(track("a"), track("b"), track("c"))),
            )
            val vm = newVm(player, provider, scope)
            player.prepareQueue(listOf(track("a"), track("old")), 0)
            player.positionMs.value = 42_000 // a few seconds in
            eventually { vm.relatedState.value is RelatedUiState.Success }
            val related = (vm.relatedState.value as RelatedUiState.Success).tracks
            // Related tab must not show the playing song as a row
            assertFalse(related.any { it.id == "a" }, "related must filter current track")
            assertEquals(listOf("b", "c"), related.map { it.id })
            // startRadio keeps a sounding and swaps the tail to the radio
            vm.startRadio()
            eventually { player.queue.value.map { it.id } == listOf("a", "b", "c") }
            assertEquals("a", player.currentTrack.value?.id, "startRadio must not move off the head")
            assertEquals(42_000, player.positionMs.value, "position must be untouched")
            assertTrue(player.seeks.isEmpty(), "no seek may happen — never restart")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startRadioIsNoopWithoutPlaybackOrRadio(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // Nothing playing at all.
            val idle = FakePlayer()
            val vmIdle = newVm(idle, FakeProvider(related = DhunResult.Success(listOf(track("b")))), scope)
            vmIdle.startRadio()
            delay(200)
            assertTrue(idle.queue.value.isEmpty())

            // Playing, but the radio list is empty.
            val player = FakePlayer()
            val vm = newVm(player, FakeProvider(related = DhunResult.Success(emptyList())), scope)
            player.prepareQueue(listOf(track("a"), track("old")), 0)
            eventually { vm.relatedState.value is RelatedUiState.Empty }
            vm.startRadio()
            delay(200)
            assertEquals(listOf("a", "old"), player.queue.value.map { it.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun relatedFiltersCurrentTrackToEmptyWhenOnlyCurrent(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val player = FakePlayer()
            val provider = FakeProvider(
                related = DhunResult.Success(listOf(track("solo"))),
            )
            val vm = newVm(player, provider, scope)
            player.prepareQueue(listOf(track("solo")), 0)
            eventually { vm.relatedState.value is RelatedUiState.Empty }
        } finally {
            scope.cancel()
        }
    }

/* ---------------- endless radio (auto-refill at ≤3 songs left) ----------- */

    private class RadioFixture(val player: FakePlayer, val provider: FakeProvider, val vm: PlayerViewModel)

    /**
     * Starts a radio on "a" with a 5-song tail (remaining = 5, above the
     * refill threshold) so tests can run the station down deterministically.
     */
    private suspend fun startRadioWithTail(scope: CoroutineScope, token: String?): RadioFixture {
        val player = FakePlayer()
        val provider = FakeProvider(
            // RDAMVM lists the seed video itself first — same shape as the
            // live fixture (#99). The Related tab filters it out.
            related = DhunResult.Success(
                listOf(track("a"), track("r1"), track("r2"), track("r3"), track("r4"), track("r5")),
            ),
            relatedToken = token,
        )
        val vm = newVm(player, provider, scope)
        player.prepareQueue(listOf(track("a")), 0)
        player.positionMs.value = 42_000
        eventually { vm.relatedState.value is RelatedUiState.Success }
        vm.startRadio()
        eventually { player.queue.value.map { it.id } == listOf("a", "r1", "r2", "r3", "r4", "r5") }
        return RadioFixture(player, provider, vm)
    }

    /** Simulates natural playback advancing the engine to [index]. */
    private fun FakePlayer.advanceTo(index: Int) {
        currentQueueIndex.value = index
        val t = queue.value[index]
        currentTrack.value = t
        state.value = PlaybackState.Playing(t)
    }

    @Test
    fun endlessRadioReplacesTailWhenSongsRunLow(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = startRadioWithTail(scope, token = "tok-a")
            // 5 songs remain at start — above the threshold, so no fetch yet.
            delay(200)
            assertTrue(fixture.provider.continuationCalls.isEmpty(), "no refill above the threshold")
            assertTrue(fixture.provider.continuationResults.isEmpty())

            // The station runs down: now on r3, 2 songs remain (≤ 3).
            fixture.provider.continuationResults += DhunResult.Success(
                dev.dhun.core.RadioQueuePage(
                    tracks = listOf(track("r6"), track("r7"), track("r8"), track("r9"), track("r10")),
                    continuationToken = "tok-2",
                ),
            )
            fixture.player.advanceTo(3)
            eventually {
                fixture.player.queue.value.map { it.id } ==
                    listOf("r3", "r6", "r7", "r8", "r9", "r10")
            }
            // Same song, same position, no gap: the head never moves and no
            // seek may be issued — the swap goes around the sounding item.
            assertEquals("r3", fixture.player.currentTrack.value?.id)
            assertEquals(0, fixture.player.currentQueueIndex.value)
            assertEquals(42_000, fixture.player.positionMs.value, "position must be untouched")
            assertTrue(fixture.player.seeks.isEmpty(), "no seek may happen — never restart")
            // The refill walked the CONTINUATION chain (not a re-seed) and
            // consumed the page's next token.
            assertEquals(listOf("tok-a"), fixture.provider.continuationCalls)
            assertEquals("tok-2", fixture.vm.radioSession.continuationToken)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun endlessRadioReseedsWhenTheTokenIsGone(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = startRadioWithTail(scope, token = null)
            // r4's own /next page (a fresh /next lists the seeding video
            // first, #99 shape) — the related load and the re-seed both read
            // this, so call order cannot race the assertion.
            fixture.provider.radioPagesByVideo["r4"] = DhunResult.Success(
                dev.dhun.core.RadioQueuePage(
                    tracks = listOf(track("r4"), track("s1"), track("s2"), track("s3")),
                    continuationToken = "tok-s",
                ),
            )
            // 1 song remains (now on r4) and the token is null: the refill
            // must RE-SEED a fresh /next from the playing track.
            fixture.player.advanceTo(4)
            eventually { fixture.player.queue.value.map { it.id } == listOf("r4", "s1", "s2", "s3") }
            assertEquals("r4", fixture.player.currentTrack.value?.id)
            // The re-seed targeted the CURRENT track (the head is filtered
            // out of the refilled tail — no self-replay), and the page's
            // token now chains the station.
            assertTrue("r4" in fixture.provider.radioPageCalls, "re-seed must target the playing track")
            assertEquals("tok-s", fixture.vm.radioSession.continuationToken)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun endlessRadioLeavesQueueAloneWhenRefillFails(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = startRadioWithTail(scope, token = "tok-a")
            // First refill fails: the queue must survive untouched (fail-open
            // — never wipe on a failed fetch) and the token is kept, so the
            // next advance retries the SAME chain.
            fixture.provider.continuationResults += DhunResult.Failure(DhunError.Network())
            fixture.player.advanceTo(3)
            delay(300)
            assertEquals(listOf("a", "r1", "r2", "r3", "r4", "r5"), fixture.player.queue.value.map { it.id })
            assertEquals(3, fixture.player.currentQueueIndex.value)
            assertEquals("r3", fixture.player.currentTrack.value?.id)
            assertEquals(1, fixture.provider.continuationCalls.size)

            // Next advance: the chain is retried and a good page lands.
            fixture.provider.continuationResults += DhunResult.Success(
                dev.dhun.core.RadioQueuePage(
                    tracks = listOf(track("r6"), track("r7"), track("r8")),
                    continuationToken = "tok-2",
                ),
            )
            fixture.player.advanceTo(4)
            eventually { fixture.player.queue.value.map { it.id } == listOf("r4", "r6", "r7", "r8") }
            assertEquals("r4", fixture.player.currentTrack.value?.id)
            assertEquals(listOf("tok-a", "tok-a"), fixture.provider.continuationCalls)
            assertEquals("tok-2", fixture.vm.radioSession.continuationToken)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun endlessRadioSkipsDuplicatePagesWithoutLooping(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = startRadioWithTail(scope, token = "tok-a")
            // The "next page" is exactly what is already visible behind the
            // head: swapping it would change nothing, so the queue must stay
            // put — and no hot loop may spin (one fetch per trigger).
            fixture.provider.continuationResults += DhunResult.Success(
                dev.dhun.core.RadioQueuePage(
                    tracks = listOf(track("r4"), track("r5")),
                    continuationToken = "tok-a",
                ),
            )
            fixture.player.advanceTo(3)
            eventually { fixture.provider.continuationCalls.size >= 1 }
            delay(300) // let a would-be loop have time to fire
            assertEquals(1, fixture.provider.continuationCalls.size, "duplicate page must not spin")
            assertEquals(listOf("a", "r1", "r2", "r3", "r4", "r5"), fixture.player.queue.value.map { it.id })
            assertEquals(3, fixture.player.currentQueueIndex.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun endlessRadioIgnoresNonRadioQueues(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = startRadioWithTail(scope, token = "tok-a")
            // A fresh non-radio queue takes over: the station stops
            // refilling even though the new queue also runs low.
            fixture.vm.playQueue(listOf(track("x"), track("y"), track("z")), 1)
            eventually { fixture.player.queue.value.map { it.id } == listOf("x", "y", "z") }
            fixture.player.advanceTo(1)
            delay(300)
            // The refill would have swapped the tail around "y" — the queue
            // must be exactly the user's 3 tracks, and no continuation may
            // have been walked (the related tab's own fetch is unrelated).
            assertEquals(listOf("x", "y", "z"), fixture.player.queue.value.map { it.id })
            assertEquals(1, fixture.player.currentQueueIndex.value)
            assertTrue(fixture.provider.continuationCalls.isEmpty(), "non-radio queue must not refill")
            assertFalse(fixture.vm.radioSession.isActive)
        } finally {
            scope.cancel()
        }
    }
}
