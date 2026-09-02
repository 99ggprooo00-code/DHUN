package dev.dhun.desktop.player

import dev.dhun.core.DhunResult
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.player.DhunPlayer
import dev.dhun.player.QueueManager
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * Phase 04 desktop player: vlcj (libVLC) audio engine behind the shared
 * [DhunPlayer] contract. Queue decisions come from the shared
 * [QueueManager] — the single source of truth on desktop (Android keeps
 * Media3's native queue; that divergence is documented in the Phase 03
 * verification log). Streams resolve via the desktop resolver chain
 * (own-client primary, yt-dlp failover — ADR-001).
 */
class DesktopDhunPlayer(
    private val provider: MusicProvider,
    private val scope: CoroutineScope,
) : DhunPlayer {

    private val factory = MediaPlayerFactory("--no-video", "--quiet")
    private val mediaPlayer = factory.mediaPlayers().newMediaPlayer()
    private val queueManager = QueueManager()
    private val opMutex = Mutex()

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    override val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var pollJob: Job? = null

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Playing(_currentTrack.value ?: UNKNOWN)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Paused(_currentTrack.value ?: UNKNOWN)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _state.value = PlaybackState.Error(
                    _currentTrack.value,
                    "Playback failed (stream URL or network).",
                )
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                // Never call back into libVLC on the native callback thread
                // (vlcj 4 tutorial, "exit() is called from submit()") —
                // advance on a coroutine instead.
                scope.launch { advanceOnEnded() }
            }
        })
    }

    override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int) {
        opMutex.withLock {
            queueManager.setQueue(tracks, startIndex)
            _queue.value = queueManager.snapshot
            playCurrentLocked()
        }
    }

    override fun playPause() {
        when (_state.value) {
            is PlaybackState.Playing -> mediaPlayer.controls().pause()
            is PlaybackState.Paused, is PlaybackState.Buffering -> mediaPlayer.controls().play()
            else -> Unit
        }
    }

    override fun next() {
        scope.launch {
            opMutex.withLock {
                val nextTrack = queueManager.next(trackEnded = false)
                if (nextTrack == null) stopLocked() else playCurrentLocked()
            }
        }
    }

    override fun previous() {
        scope.launch {
            opMutex.withLock {
                val wasPlaying = _state.value is PlaybackState.Playing
                val before = queueManager.current
                val prev = queueManager.previous()
                if (prev == null) return@withLock
                if (prev.id == before?.id) {
                    // Already at the first track of the play order: restart it.
                    mediaPlayer.controls().setTime(0)
                    _positionMs.value = 0
                    if (wasPlaying) mediaPlayer.controls().play()
                } else {
                    playCurrentLocked()
                }
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val safe = positionMs.coerceAtLeast(0)
        mediaPlayer.controls().setTime(safe)
        _positionMs.value = safe
    }

    override fun setRepeatMode(mode: RepeatMode) {
        queueManager.setRepeatMode(mode)
    }

    override fun setShuffle(enabled: Boolean) {
        if (queueManager.shuffleEnabled != enabled) {
            queueManager.toggleShuffle()
            _queue.value = queueManager.snapshot
        }
    }

    override fun stop() {
        scope.launch { opMutex.withLock { stopLocked() } }
    }

    /** Tears down libVLC resources. Call once when the app exits. */
    fun release() {
        pollJob?.cancel()
        pollJob = null
        mediaPlayer.release()
        factory.release()
    }

    /* ---------------- internals ---------------- */

    private suspend fun advanceOnEnded() {
        opMutex.withLock {
            val nextTrack = queueManager.next(trackEnded = true)
            if (nextTrack == null) stopLocked() else playCurrentLocked()
        }
    }

    private suspend fun playCurrentLocked() {
        val track = queueManager.current ?: run {
            _state.value = PlaybackState.Idle
            return
        }
        _currentTrack.value = track
        _state.value = PlaybackState.Resolving(track)
        when (val result = provider.getStreamInfo(track.id)) {
            is DhunResult.Success -> {
                _durationMs.value = (track.durationSeconds ?: 0) * 1000L
                mediaPlayer.media().play(result.value.audioUrl)
                startPolling()
                _state.value = PlaybackState.Buffering(track)
            }
            is DhunResult.Failure -> {
                _state.value = PlaybackState.Error(track, result.error.toUserMessage())
            }
        }
    }

    private fun stopLocked() {
        pollJob?.cancel()
        pollJob = null
        mediaPlayer.controls().stop()
        _positionMs.value = 0
        _state.value = PlaybackState.Idle
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val time = runCatching { mediaPlayer.status().time() }.getOrDefault(_positionMs.value)
                val length = runCatching { mediaPlayer.status().length() }.getOrDefault(_durationMs.value)
                if (length > 0) _durationMs.value = length
                if (time > 0) _positionMs.value = time
                delay(POLL_MS)
            }
        }
    }

    companion object {
        private const val POLL_MS = 500L
        private val UNKNOWN = Track(id = "", title = "Unknown", artistName = "")
    }
}
