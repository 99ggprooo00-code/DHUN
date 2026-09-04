package dev.dhun.player

import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.PlayContext
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Queue-restore round trip: fake player + real repositories (in-memory DB). */
class NowPlayingPersistenceTest {

    /** Minimal scripted DhunPlayer: flows are public so the test drives them. */
    private class FakePlayer : DhunPlayer {
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        override val currentTrack = MutableStateFlow<Track?>(null)
        override val queue = MutableStateFlow<List<Track>>(emptyList())
        override val positionMs = MutableStateFlow(0L)
        override val durationMs = MutableStateFlow(0L)
        var repeat = RepeatMode.OFF
        var shuffleOn = false
        var seeks = mutableListOf<Long>()
        var playPauseCalls = 0

        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            queue.value = tracks
            currentTrack.value = tracks.getOrNull(startIndex)
            state.value = if (playWhenReady) PlaybackState.Playing(tracks[startIndex]) else PlaybackState.Paused(tracks[startIndex])
        }
        override fun addNext(track: Track) {
            val list = queue.value.toMutableList()
            list.add(track)
            queue.value = list
        }
        override fun addToQueue(track: Track) {
            val list = queue.value.toMutableList()
            list.add(track)
            queue.value = list
        }
        override fun playPause() {
            playPauseCalls++
            val t = currentTrack.value ?: return
            state.value = if (state.value is PlaybackState.Playing) PlaybackState.Paused(t) else PlaybackState.Playing(t)
        }
        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) { seeks += positionMs; this.positionMs.value = positionMs }
        override fun setRepeatMode(mode: RepeatMode) { repeat = mode }
        override fun setShuffle(enabled: Boolean) { shuffleOn = enabled }
        override fun stop() { state.value = PlaybackState.Idle }
    }

    private fun track(id: String) = Track(id = id, title = "T$id", artistName = "A")

    private fun data() = DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    private fun persistence(player: DhunPlayer, d: DataLayer, scope: CoroutineScope, interval: Long = 50) =
        NowPlayingPersistence(
            player = player,
            save = SaveNowPlayingUseCase(d.nowPlaying),
            restore = RestoreNowPlayingUseCase(d.nowPlaying, d.settings),
            recordPlay = RecordPlayUseCase(d.history),
            scope = scope,
            progressIntervalMs = interval,
        )

    private suspend fun eventually(timeoutMs: Long = 10_000, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(10) }
    }

    @Test
    fun queueAndProgressArePersistedThenRestoredPaused(): Unit = runBlocking {
        val d = data()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // --- session 1: user plays a queue ---
            val p1 = FakePlayer()
            val pers1 = persistence(p1, d, scope)
            pers1.setPlayContext(PlayContext.SEARCH)
            pers1.start()
            val q = listOf(track("1"), track("2"), track("3"))
            p1.prepareQueue(q, 1)
            p1.durationMs.value = 100_000
            p1.positionMs.value = 30_000
            eventually { d.nowPlaying.load()?.positionMs == 30_000L }
            val saved = assertNotNull(d.nowPlaying.load())
            assertEquals(q, saved.queue)
            assertEquals(1, saved.currentIndex)
            // history got a row for the track that started
            eventually { d.history.observeHistory(5).first().isNotEmpty() }
            assertEquals("2", d.history.observeHistory(5).first().single().track.id)
            assertEquals("SEARCH", d.history.observeHistory(5).first().single().playedFromContext)
            pers1.stop()

            // --- session 2: cold start restores, paused, at the position ---
            val p2 = FakePlayer()
            val pers2 = persistence(p2, d, scope)
            val snap = assertNotNull(pers2.restore())
            assertEquals("2", snap.currentTrack?.id)
            assertEquals(q, p2.queue.value)
            assertTrue(p2.state.value is PlaybackState.Paused, "restored session must be paused: ${p2.state.value}")
            assertEquals(listOf(30_000L), p2.seeks)

            // restoring is not a play → history unchanged
            pers2.start()
            delay(150)
            assertEquals(1, d.history.observeHistory(5).first().size)
            pers2.stop()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun naturalCompletionMarksHistoryRow(): Unit = runBlocking {
        val d = data()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = FakePlayer()
            val pers = persistence(p, d, scope, interval = 20)
            pers.start()
            p.prepareQueue(listOf(track("a"), track("b")), 0)
            p.durationMs.value = 10_000
            p.positionMs.value = 9_800 // ≥ 90%
            delay(300) // let a progress tick observe the fraction (was 80ms — flaky on slow CI)
            p.currentTrack.value = track("b") // transition
            eventually { d.history.observeHistory(5).first().size == 2 }
            val entries = d.history.observeHistory(5).first()
            assertEquals("b", entries[0].track.id)
            assertEquals("a", entries[1].track.id)
            eventually { d.history.observeHistory(5).first()[1].completedPlayback }
            pers.stop()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun restoreIsNoOpWhenNothingSavedOrPlayerBusy(): Unit = runBlocking {
        val d = data()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = FakePlayer()
            val pers = persistence(p, d, scope)
            assertNull(pers.restore())
            d.nowPlaying.saveQueue(listOf(track("z")), 0, 0, RepeatMode.OFF, false)
            p.state.value = PlaybackState.Playing(track("other"))
            assertNull(pers.restore()) // player not idle → leave it alone
            assertEquals(0, p.playPauseCalls)
        } finally {
            scope.cancel()
        }
    }
}
