package dev.dhun.presentation.player

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.PlayContext
import dev.dhun.domain.RadioSession
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
import kotlinx.coroutines.flow.combine
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
    /**
     * Shared endless-radio bookkeeping (Koin `single` in production).
     * Exposed so other screens that start radios (ArtistScreen) can hand
     * their session to the SAME instance the refill monitor watches.
     */
    val radioSession: RadioSession = RadioSession(),
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

    /* ---------------- endless radio ---------------- */

    /**
     * Next-page token of the LAST related list [loadRelated] fetched — the
     * chain [startRadio]/[playRelatedAt] hand to [radioSession] so refills
     * continue the SAME station instead of re-seeding.
     */
    private var lastRelatedPageToken: String? = null
    private var radioRefillInFlight = false

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
        // Endless radio: while a radio plays and only a few songs remain,
        // fetch the next /next page and swap the queue tail around the
        // sounding item (same song, same position, no gap). Failures are
        // fail-open — the queue is never touched.
        scope.launch {
            combine(player.queue, player.currentQueueIndex, player.state) { q, i, s ->
                RefillProbe(q, i, s)
            }.collect { probe -> maybeRefillRadio(probe) }
        }
    }

    /** One conflated (queue, index, state) snapshot for the refill gate. */
    private data class RefillProbe(val queue: List<Track>, val index: Int, val state: PlaybackState)

    /**
     * Gate for the endless-radio refill. Fires at most one in-flight refill:
     * radio active + queue loaded + currently PLAYING + ≤
     * [RADIO_REFILL_THRESHOLD_SONGS] songs after the current one.
     */
    private fun maybeRefillRadio(probe: RefillProbe) {
        if (!radioSession.isActive) return
        val queue = probe.queue
        if (probe.index !in 0 until queue.size) return
        if (probe.state !is PlaybackState.Playing) return
        val remaining = queue.size - probe.index - 1
        if (remaining > RADIO_REFILL_THRESHOLD_SONGS) return
        if (radioRefillInFlight) return
        scope.launch { refillRadio(queue, probe.index) }
    }

    /**
     * Fetches the next page of the station and swaps the queue tail around
     * the sounding track via [DhunPlayer.replaceQueueKeepingCurrent] — the
     * same seamless primitive "Play radio" uses, so the current song keeps
     * sounding from the same position with no gap.
     *
     * Fail-open contract: a failed OR duplicate page leaves the queue
     * exactly as it was (the next advance re-triggers the check).
     */
    private suspend fun refillRadio(expectedQueue: List<Track>, expectedIndex: Int) {
        val current = player.currentTrack.value ?: return
        // The probe is one state emission old by the time this runs: re-check
        // the sounding track still matches, so a late refill can never
        // clobber a newer queue.
        if (expectedQueue.getOrNull(expectedIndex)?.id != current.id) return
        radioRefillInFlight = true
        try {
            val page = when (val token = radioSession.continuationToken) {
                null -> provider.radioQueuePage(current.id)
                else -> provider.radioQueueContinuation(token)
            }
            when (page) {
                is DhunResult.Success -> {
                    val tail = page.value.tracks.filter { it.id != current.id }
                    val currentTailIds =
                        expectedQueue.subList(expectedIndex + 1, expectedQueue.size).map { it.id }.toSet()
                    val tailIsAllNew = tail.isNotEmpty() &&
                        tail.any { it.id !in currentTailIds }
                    if (tailIsAllNew) {
                        player.replaceQueueKeepingCurrent(tail)
                    }
                    // Consume the token whether or not we swapped: a page that
                    // duplicates the visible tail still advances the server's
                    // paging state (and a null next-token marks the chain
                    // exhausted → re-seed on the next trigger).
                    radioSession.tokenConsumed(page.value.continuationToken)
                }
                is DhunResult.Failure -> {
                    // Leave the queue alone; the next track advance retries.
                }
            }
        } finally {
            radioRefillInFlight = false
        }
    }

    /* ---------------- transport ---------------- */

    fun togglePlay() {
        // After a failure the engine sits in error-idle where play() is a
        // no-op — route the press through recovery so the button never
        // appears dead on an error row.
        if (state.value is PlaybackState.Error) retry() else player.playPause()
    }

    /** One-tap recovery from [PlaybackState.Error] (mini/full-player Retry). */
    fun retry() = player.retry()

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
        // A fresh non-radio queue takes over: the old station stops refilling.
        radioSession.stop()
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

    /** Append without replacing playback; used by the Related tab's explicit queue action. */
    fun addToQueue(track: Track) = player.addToQueue(track)

    /**
     * Snapshot-aware overloads for delayed row menus / swipe completion. Queue
     * indices alone can refer to a different occurrence after a replacement or
     * reorder, including queues containing the same track more than once.
     * Existing one-argument / two-argument actions remain unchanged for callers.
     */
    fun playQueueAt(index: Int, expectedQueue: List<Track>): Boolean {
        if (index !in expectedQueue.indices || queue.value != expectedQueue) return false
        playQueueAt(index)
        return true
    }

    fun removeQueueItem(index: Int, expectedQueue: List<Track>): Boolean {
        if (index !in expectedQueue.indices || queue.value != expectedQueue) return false
        removeQueueItem(index)
        return true
    }

    fun moveQueueItem(from: Int, to: Int, expectedQueue: List<Track>): Boolean {
        if (from !in expectedQueue.indices || to !in expectedQueue.indices || from == to) return false
        if (queue.value != expectedQueue) return false
        moveQueueItem(from, to)
        return true
    }

    /* ---------------- tab actions ---------------- */

    /** Plays the related list (radio queue) from [index]. Suspends: caller launches. */
    suspend fun playRelatedAt(index: Int, context: PlayContext = PlayContext.QUEUE) {
        val tracks = (relatedState.value as? RelatedUiState.Success)?.tracks ?: return
        val track = tracks.getOrNull(index) ?: return
        persistence?.setPlayContext(context)
        // The queue IS the station: the refill monitor keeps it going.
        radioSession.start(track.id, lastRelatedPageToken)
        player.prepareQueue(tracks, index, playWhenReady = true)
        _skipDirection.value = SkipDirection.FORWARD
    }

    /**
     * "Start radio" / "Play radio": keeps the CURRENT song playing from its
     * current position (no restart, no pause) and replaces the rest of the
     * queue with the Related (radio) list. Semantics match InnerTune's
     * `startRadioSeamlessly` / ViMusic's seamless radio: the head never
     * moves, the tail becomes the station.
     *
     * No-op when nothing is playing or the radio list isn't loaded yet.
     * Deliberately does NOT touch [_skipDirection]: the head track didn't
     * change, so the artwork must not slide.
     */
    fun startRadio(context: PlayContext = PlayContext.QUEUE) {
        val current = currentTrack.value ?: return
        val tracks = (relatedState.value as? RelatedUiState.Success)?.tracks ?: return
        if (tracks.isEmpty()) return
        // Defensive: the Related loader already filters the head, but a
        // stale emission must never duplicate it into the queue.
        val rest = tracks.filter { it.id != current.id }
        if (rest.isEmpty()) return
        persistence?.setPlayContext(context)
        // The queue becomes the station: the refill monitor keeps it going.
        radioSession.start(current.id, lastRelatedPageToken)
        scope.launch { player.replaceQueueKeepingCurrent(rest) }
    }

    /** Loads an arbitrary track list as the queue (album/playlist/artist actions). */
    suspend fun playTracks(tracks: List<Track>, startIndex: Int = 0, context: PlayContext = PlayContext.UNKNOWN) {
        if (tracks.isEmpty()) return
        // A fresh non-radio queue takes over: any running station stops refilling.
        radioSession.stop()
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
            when (val r = provider.radioQueuePage(track.id)) {
                is DhunResult.Success -> {
                    // Remember the station's paging chain: startRadio /
                    // playRelatedAt hand it to the refill monitor so endless
                    // radio continues the SAME /next list.
                    lastRelatedPageToken = r.value.continuationToken
                    // /next playlistPanelVideoRenderer includes the
                    // currently-playing video as its first entry. Filter the
                    // current track out so the Related tab never lists the
                    // playing song as a row, and the seamless radio tail
                    // never duplicates the head.
                    val filtered = r.value.tracks.filter { it.id != track.id }
                    _relatedState.value =
                        if (filtered.isEmpty()) RelatedUiState.Empty else RelatedUiState.Success(filtered)
                }
                is DhunResult.Failure -> {
                    lastRelatedPageToken = null
                    _relatedState.value = RelatedUiState.Error(r.error.toUserMessage())
                }
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

        /**
         * Endless radio: fetch the next /next page when at most this many
         * songs remain after the current one. 3 = the queue still has a
         * short buffer while the fetch is in flight, so playback never
         * dead-ends on a slow network.
         */
        const val RADIO_REFILL_THRESHOLD_SONGS = 3
    }
}
