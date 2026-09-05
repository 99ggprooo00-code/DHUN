package dev.dhun.player

import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.NowPlayingSnapshot
import dev.dhun.data.PlayContext
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlinx.coroutines.launch

/**
 * Observes a [DhunPlayer] and persists what it is doing — shared by Android
 * and desktop so restart behaviour is identical:
 *
 *  - queue changes  → full snapshot write
 *  - every [progressIntervalMs] while playing → cheap position update
 *  - track transitions → history row (+ completion mark when the previous
 *    track ended naturally, i.e. position reached ≥ 90% of duration)
 *  - [restore] on cold start → re-queues the saved tracks and seeks (paused).
 *
 * Persistence never throws into the player: every write is wrapped.
 */
class NowPlayingPersistence(
    private val player: DhunPlayer,
    private val save: SaveNowPlayingUseCase,
    private val restore: RestoreNowPlayingUseCase,
    private val recordPlay: RecordPlayUseCase,
    private val scope: CoroutineScope,
    private val progressIntervalMs: Long = 5_000,
    private val log: (String) -> Unit = {},
) {
    private var jobs: List<Job> = emptyList()
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffle: Boolean = false
    private var lastHandle: RecordPlayUseCase.PlayHandle? = null
    @Volatile private var lastProgressFraction = 0f
    private var playContext: PlayContext = PlayContext.UNKNOWN

    /**
     * Serializes every now-playing write. Queue-change and track-change
     * events fire back-to-back (prepareQueue sets both), so without this
     * two saveQueue transactions interleave on the DB — on the in-memory
     * JDBC driver (single shared connection) that corrupts the queue rows
     * (CI flake: NowPlayingPersistenceTest, "expected [T1,T2,T3] but was
     * [T1,T2,…]"). Same latent race existed in production: the snapshot
     * coroutines hop to Dispatchers.Default inside withContext.
     */
    private val writeMutex = Mutex()

    /** Call when the UI starts something — so history knows where from. */
    fun setPlayContext(context: PlayContext) { playContext = context }

    /** Mirror of the player's repeat/shuffle since [DhunPlayer] exposes setters only. */
    fun onRepeatModeChanged(mode: RepeatMode) { repeatMode = mode; scope.launch { snapshot() } }
    fun onShuffleChanged(enabled: Boolean) { shuffle = enabled; scope.launch { snapshot() } }

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch {
                player.queue.collect { snapshot() } // StateFlow is already distinct
            },
            scope.launch {
                player.currentTrack.distinctUntilChanged { a, b -> a?.id == b?.id }.collect { onTrackChanged(it) }
            },
            scope.launch {
                while (isActive) {
                    delay(progressIntervalMs)
                    if (player.state.value is PlaybackState.Playing) progress()
                }
            },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    /**
     * Restores the last queue (paused at the saved position). @return the
     * snapshot when something was restored. Only acts if the player is idle.
     */
    suspend fun restore(): NowPlayingSnapshot? {
        if (player.state.value !is PlaybackState.Idle) return null
        val snap = runCatching { restore.invoke() }.onFailure { log("restore failed: ${it.message}") }.getOrNull()
            ?: return null
        playContext = PlayContext.RESTORED
        player.setRepeatMode(snap.repeatMode)
        player.setShuffle(snap.shuffle)
        repeatMode = snap.repeatMode
        shuffle = snap.shuffle
        // A restored session waits for the user, at the position they left.
        player.prepareQueue(snap.queue, snap.currentIndex, playWhenReady = false)
        if (snap.positionMs > 0) player.seekTo(snap.positionMs)
        log("restored ${snap.queue.size} tracks @ index ${snap.currentIndex}, ${snap.positionMs}ms")
        return snap
    }

    /* ---------------- internals ---------------- */

    private suspend fun snapshot() {
        val queue = player.queue.value
        val current = player.currentTrack.value
        val index = queue.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        runCatching {
            writeMutex.withLock { save(queue, index, player.positionMs.value, repeatMode, shuffle) }
        }.onFailure { log("save queue failed: ${it.message}") }
    }

    private suspend fun progress() {
        val queue = player.queue.value
        val current = player.currentTrack.value ?: return
        val index = queue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        val duration = player.durationMs.value
        if (duration > 0) lastProgressFraction = player.positionMs.value.toFloat() / duration
        runCatching {
            writeMutex.withLock { save.progress(index, player.positionMs.value) }
        }.onFailure { log("save progress failed: ${it.message}") }
    }

    private suspend fun onTrackChanged(track: Track?) {
        // finish the previous entry if it played (almost) to the end
        val previous = lastHandle
        val transitionFraction = player.durationMs.value.takeIf { it > 0L }?.let { duration ->
            player.positionMs.value.toFloat() / duration.toFloat()
        } ?: 0f
        // Read the player once more at the transition. A progress tick can be
        // delayed by a busy dispatcher, so completion must not depend solely
        // on the periodic observer having won the scheduling race.
        if (previous != null && maxOf(lastProgressFraction, transitionFraction) >= COMPLETION_FRACTION) {
            runCatching { recordPlay.complete(previous) }
        }
        lastHandle = null
        lastProgressFraction = 0f
        if (track == null || track.id.isEmpty()) return
        if (playContext == PlayContext.RESTORED) {
            // Restoring is not a "play" — the user has not pressed anything yet.
            playContext = PlayContext.QUEUE
            return
        }
        lastHandle = runCatching { recordPlay(track, playContext) }
            .onFailure { log("record play failed: ${it.message}") }.getOrNull()
        snapshot()
    }

    private companion object {
        const val COMPLETION_FRACTION = 0.9f
    }
}
