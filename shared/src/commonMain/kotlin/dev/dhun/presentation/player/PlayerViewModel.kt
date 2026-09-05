package dev.dhun.presentation.player

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.PlayContext
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.player.DhunPlayer
import dev.dhun.player.NowPlayingPersistence
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* ---------------- tab states ---------------- */

sealed interface RelatedUiState {
    data object Loading : RelatedUiState
    data object Empty : RelatedUiState
    data class Success(val tracks: List<Track>) : RelatedUiState
    data class Error(val message: String) : RelatedUiState
}

sealed interface LyricsUiState {
    data object Loading : LyricsUiState
    data class Unsynced(val text: String) : LyricsUiState
    data class Synced(val lines: List<LyricsLine>) : LyricsUiState
    data object Unavailable : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}

/** Which way the artwork slides on a track change (+1 next, -1 previous). */
enum class SkipDirection { FORWARD, BACKWARD }

/**
 * PlayerViewModel — the application-layer snapshot + actions behind
 * MiniPlayer and FullPlayer (Phase 08).
 *
 * Owns: tab content loading for the FullPlayer (Related wired to InnerTube
 * `/next`, Lyrics via provider's YTM lyrics — LRCLIB synced sources land in
 * Phase 11), repeat/shuffle cycling, hold-to-seek, queue mutations and the
 * track-change skip direction used for choreography.
 */
class PlayerViewModel(
    private val player: DhunPlayer,
    private val provider: MusicProvider,
    private val scope: CoroutineScope,
    private val persistence: NowPlayingPersistence? = null,
    private val lyricsRepository: LyricsRepository? = null,
) {
    /* ---------------- pass-through player state ---------------- */

    val state: StateFlow<PlaybackState> = player.state
    val currentTrack: StateFlow<Track?> = player.currentTrack
    val queue: StateFlow<List<Track>> = player.queue
    val currentQueueIndex: StateFlow<Int> = player.currentQueueIndex
    val positionMs: StateFlow<Long> = player.positionMs
    val durationMs: StateFlow<Long> = player.durationMs
    val repeatMode: StateFlow<RepeatMode> = player.repeatMode
    val shuffleEnabled: StateFlow<Boolean> = player.shuffleEnabled
    val volume: StateFlow<Float> = player.volume

    val isPlaying: StateFlow<Boolean> = player.state
        .map { it is PlaybackState.Playing }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _skipDirection = MutableStateFlow(SkipDirection.FORWARD)
    val skipDirection: StateFlow<SkipDirection> = _skipDirection.asStateFlow()

    /* ---------------- FullPlayer tab content ---------------- */

    private val _relatedState = MutableStateFlow<RelatedUiState>(RelatedUiState.Loading)
    val relatedState: StateFlow<RelatedUiState> = _relatedState.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Unavailable)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private var relatedJob: Job? = null
    private var lyricsJob: Job? = null
    private var holdSeekJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastSeenTrackId: String? = null

    /* ---------------- Sleep timer (Home quick-action) ---------------- */

    /** Remaining ms until auto-pause; null when inactive. */
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    /** Preset minutes the Home chip cycles through. 0 = cancel. */
    fun cycleSleepTimer() {
        val current = _sleepTimerRemainingMs.value
        val nextMinutes = when {
            current == null -> 15
            current > 45 * 60_000L -> 0 // was 60 → off
            current > 25 * 60_000L -> 60
            current > 12 * 60_000L -> 30
            else -> 60
        }
        if (nextMinutes == 0) cancelSleepTimer() else startSleepTimer(nextMinutes)
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        val total = minutes * 60_000L
        _sleepTimerRemainingMs.value = total
        sleepTimerJob = scope.launch {
            var left = total
            while (left > 0) {
                delay(1_000L)
                left -= 1_000L
                _sleepTimerRemainingMs.value = left.coerceAtLeast(0L)
            }
            _sleepTimerRemainingMs.value = null
            // Pause if still playing — never stop/clear queue.
            if (player.state.value is PlaybackState.Playing) {
                player.playPause()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    init {
        // Skip direction: forward on natural advance / next, backward on
        // previous. Used only for artwork slide choreography.
        scope.launch {
            var prevIndex = -1
            currentQueueIndex.collect { index ->
                if (index == prevIndex) return@collect
                if (prevIndex >= 0 && index >= 0) {
                    _skipDirection.value =
                        if (index > prevIndex) SkipDirection.FORWARD else SkipDirection.BACKWARD
                }
                prevIndex = index
            }
        }
        // Tab content follows the track.
        scope.launch {
            currentTrack
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collect { track ->
                    loadRelated(track)
                    loadLyrics(track)
                }
        }
    }

    /* ---------------- transport ---------------- */

    fun togglePlay() = player.playPause()

    fun next() {
        _skipDirection.value = SkipDirection.FORWARD
        player.next()
    }

    fun previous() {
        // Spotify/ViMusic semantics: past the first 3 seconds, "previous"
        // restarts the track first.
        if (positionMs.value > RESTART_THRESHOLD_MS) {
            player.seekTo(0)
            return
        }
        _skipDirection.value = SkipDirection.BACKWARD
        player.previous()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    /**
     * Hold-to-seek (prev/next long-press): repeatedly steps the position by
     * a duration-scaled chunk until [endHoldSeek]. Works while paused too
     * — the position flow reflects the clamp.
     */
    fun beginHoldSeek(forward: Boolean) {
        endHoldSeek()
        holdSeekJob = scope.launch {
            while (true) {
                val duration = durationMs.value
                if (duration > 0) {
                    val step = (duration / HOLD_SEEK_FRACTION)
                        .coerceIn(MIN_HOLD_STEP_MS, MAX_HOLD_STEP_MS)
                    val target = if (forward) {
                        (positionMs.value + step).coerceAtMost(duration)
                    } else {
                        (positionMs.value - step).coerceAtLeast(0)
                    }
                    player.seekTo(target)
                }
                delay(HOLD_SEEK_INTERVAL_MS)
            }
        }
    }

    fun endHoldSeek() {
        holdSeekJob?.cancel()
        holdSeekJob = null
    }

    /* ---------------- repeat / shuffle / volume ---------------- */

    /** OFF → ALL → ONE → OFF. @return the new mode (for UI affordances). */
    fun cycleRepeatMode(): RepeatMode {
        val next = when (repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        player.setRepeatMode(next)
        persistence?.onRepeatModeChanged(next)
        return next
    }

    fun toggleShuffle(): Boolean {
        val newValue = !shuffleEnabled.value
        player.setShuffle(newValue)
        persistence?.onShuffleChanged(newValue)
        return newValue
    }

    fun setVolume(volume: Float) = player.setVolume(volume)

    /** Phase 10: caller primes history context before any queue handoff. */
    fun setPlayContext(context: PlayContext) {
        persistence?.setPlayContext(context)
    }

    /** Helper for screens that don't go through [playTracks] (e.g. Home/Search shell). */
    fun playQueue(tracks: List<Track>, index: Int, context: PlayContext = PlayContext.UNKNOWN) {
        if (tracks.isEmpty()) return
        persistence?.setPlayContext(context)
        _skipDirection.value = SkipDirection.FORWARD
        // Fire in scope so caller needn't be suspend.
        scope.launch { player.prepareQueue(tracks, index.coerceIn(0, tracks.size - 1), playWhenReady = true) }
    }

    /* ---------------- queue ---------------- */

    fun playQueueAt(index: Int) {
        val delta = index - currentQueueIndex.value
        if (delta != 0) {
            _skipDirection.value = if (delta > 0) SkipDirection.FORWARD else SkipDirection.BACKWARD
        }
        player.playAt(index)
    }

    fun removeQueueItem(index: Int) = player.removeFromQueue(index)

    fun moveQueueItem(from: Int, to: Int) = player.moveInQueue(from, to)

    /* ---------------- tab actions ---------------- */

    /** Plays the related list (radio queue) from [index]. Suspends: caller launches. */
    suspend fun playRelatedAt(index: Int, context: PlayContext = PlayContext.QUEUE) {
        val tracks = (relatedState.value as? RelatedUiState.Success)?.tracks ?: return
        persistence?.setPlayContext(context)
        player.prepareQueue(tracks, index, playWhenReady = true)
        _skipDirection.value = SkipDirection.FORWARD
    }

    /** "Start radio": the whole related list from the top. */
    suspend fun startRadio(context: PlayContext = PlayContext.QUEUE) = playRelatedAt(0, context)

    /** Loads an arbitrary track list as the queue (album/playlist/artist actions). */
    suspend fun playTracks(tracks: List<Track>, startIndex: Int = 0, context: PlayContext = PlayContext.UNKNOWN) {
        if (tracks.isEmpty()) return
        persistence?.setPlayContext(context)
        player.prepareQueue(tracks, startIndex, playWhenReady = true)
        _skipDirection.value = SkipDirection.FORWARD
    }

    fun refreshRelated() = loadRelated(currentTrack.value, force = true)

    fun refreshLyrics() = loadLyrics(currentTrack.value, force = true)

    /* ---------------- internals ---------------- */

    private fun loadRelated(track: Track?, force: Boolean = false) {
        if (track == null) {
            _relatedState.value = RelatedUiState.Empty
            return
        }
        if (!force && track.id == lastSeenTrackId) return
        lastSeenTrackId = track.id
        relatedJob?.cancel()
        relatedJob = scope.launch {
            _relatedState.value = RelatedUiState.Loading
            when (val r = provider.relatedTracks(track.id)) {
                is DhunResult.Success ->
                    _relatedState.value =
                        if (r.value.isEmpty()) RelatedUiState.Empty else RelatedUiState.Success(r.value)
                is DhunResult.Failure ->
                    _relatedState.value = RelatedUiState.Error(r.error.toUserMessage())
            }
        }
    }

    private fun loadLyrics(track: Track?, force: Boolean = false) {
        if (track == null) {
            _lyricsState.value = LyricsUiState.Unavailable
            return
        }
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            if (_lyricsState.value !is LyricsUiState.Loading || force) {
                _lyricsState.value = LyricsUiState.Loading
            }
            // Phase 11: lyrics via repository (cache → YTM → LRCLIB) if wired, else fallback to provider (YTM-only)
            val result: DhunResult<Lyrics> = if (lyricsRepository != null) {
                lyricsRepository.getLyrics(track)
            } else {
                provider.getLyrics(track.id)
            }
            _lyricsState.value = when (result) {
                is DhunResult.Success -> when (val lyrics = result.value) {
                    is Lyrics.Synced -> LyricsUiState.Synced(lyrics.lines)
                    is Lyrics.Unsynced -> LyricsUiState.Unsynced(lyrics.text)
                    is Lyrics.NotAvailable -> LyricsUiState.Unavailable
                }
                is DhunResult.Failure -> LyricsUiState.Error(result.error.toUserMessage())
            }
        }
    }

    companion object {
        private const val RESTART_THRESHOLD_MS = 3_000L
        private const val HOLD_SEEK_INTERVAL_MS = 140L
        private const val HOLD_SEEK_FRACTION = 120L
        private const val MIN_HOLD_STEP_MS = 1_000L
        private const val MAX_HOLD_STEP_MS = 15_000L
    }
}
