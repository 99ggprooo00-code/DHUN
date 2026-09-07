package dev.dhun.desktop.player

import dev.dhun.core.DhunResult
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.core.detailString
import dev.dhun.core.toUserMessage
import dev.dhun.player.AudioFileCache
import dev.dhun.player.DhunPlayer
import dev.dhun.player.QueueManager
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 04 desktop player: vlcj (libVLC) audio engine behind the shared
 * [DhunPlayer] contract. Queue decisions come from the shared
 * [QueueManager] — the single source of truth on desktop (Android keeps
 * Media3's native queue; that divergence is documented in the Phase 03
 * verification log). Streams resolve via the desktop resolver chain
 * (own-client primary, yt-dlp failover — ADR-001).
 *
 * Phase 14 bounded audio cache ([AudioFileCache], optional): a cached track
 * plays from the local file (no resolve, works offline); an uncached track
 * streams immediately and is downloaded to the cache in the background
 * (one download at a time, cancelled on track change). If resolve fails
 * (offline / cat.8 gating) and the file is cached, playback still works.
 */
class DesktopDhunPlayer(
    private val provider: MusicProvider,
    private val scope: CoroutineScope,
    private val audioCache: AudioFileCache? = null,
) : DhunPlayer {

    // VLC is an external native dependency (system libVLC). The original eager
    // `MediaPlayerFactory(...).newMediaPlayer()` throws UnsatisfiedLinkError /
    // IllegalStateException if VLC is missing or incompatible, which previously
    // crashed the whole desktop startup before the window opened (reported as
    // a generic \"Failed to launch JVM\" style failure). Make it lazy and
    // fault-tolerant: init is caught, `vlcAvailable` gates all player ops,
    // and a user-visible Error state explains the fix (install VLC).
    private var factory: MediaPlayerFactory? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vlcInitError: String? = null
    private val queueManager = QueueManager()
    private val opMutex = Mutex()

    /**
     * Remote MRL currently handed to libVLC, and whether the local-copy
     * fallback already ran for it. libVLC cannot send a custom User-Agent,
     * so a googlevideo URL bound to a specific InnerTube identity may be
     * rejected at the CDN even though resolution succeeded — the fallback
     * replays the same track from a file DHUN downloaded *with* that agent.
     */
    @Volatile private var streamingRemoteUrl: String? = null
    @Volatile private var localFallbackAttempted = false

    private val vlcAvailable: Boolean get() = factory != null && mediaPlayer != null

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

    private val _currentQueueIndex = MutableStateFlow(-1)
    override val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var pollJob: Job? = null
    private var pendingLazyStart = false // restored queue, not yet resolved
    private var pendingSeekMs = 0L
    private var cacheFillJob: Job? = null
    private var cacheFillCancel: AtomicBoolean? = null
    private var prebufferJob: Job? = null
    private var prebufferCancel: AtomicBoolean? = null

    init {
        try {
            val f = MediaPlayerFactory("--no-video", "--quiet")
            val mp = f.mediaPlayers().newMediaPlayer()
            factory = f
            mediaPlayer = mp
            _volume.value =
                runCatching { mp.audio().volume() / 100f }.getOrDefault(1f).coerceIn(0f, 1f)
            mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    _state.value = PlaybackState.Playing(_currentTrack.value ?: UNKNOWN)
                    schedulePrebufferNextTrack()
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    _state.value = PlaybackState.Paused(_currentTrack.value ?: UNKNOWN)
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    handlePlaybackError()
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    // Never call back into libVLC on the native callback thread
                    // (vlcj 4 tutorial, "exit() is called from submit()") —
                    // advance on a coroutine instead.
                    scope.launch { advanceOnEnded() }
                }
            })
            println("DHUN VLC initialized successfully")
        } catch (e: Throwable) {
            vlcInitError = e.message ?: e::class.simpleName ?: "unknown"
            System.err.println("DHUN VLC init failed (playback disabled, app continues): $e")
            e.printStackTrace()
            // Persist a startup log so the MSI user can find the cause without console.
            runCatching {
                val logFile = java.io.File(
                    try { dev.dhun.data.DhunUserDirs.dataDir() } catch (_: Throwable) { java.io.File(System.getProperty("java.io.tmpdir") ?: ".") },
                    "dhun-vlc-error.log",
                )
                logFile.parentFile?.mkdirs()
                logFile.appendText("[${java.time.Instant.now()}] VLC init failed: $e\n${e.stackTrace.take(30).joinToString("\n")}\n")
            }
            _state.value = PlaybackState.Error(
                null,
                "VLC not found — install VLC (https://www.videolan.org/vlc/) and restart DHUN. Detail: $vlcInitError",
            )
        }
    }

    override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
        opMutex.withLock {
            queueManager.setQueue(tracks, startIndex)
            _repeatMode.value = queueManager.repeatMode
            _shuffleEnabled.value = queueManager.shuffleEnabled
            publishQueueLocked()
            playCurrentLocked(playWhenReady)
        }
    }

    override fun addNext(track: Track) {
        scope.launch {
            opMutex.withLock {
                queueManager.addNext(track)
                publishQueueLocked()
            }
        }
    }

    override fun addToQueue(track: Track) {
        scope.launch {
            opMutex.withLock {
                queueManager.addToQueue(track)
                publishQueueLocked()
            }
        }
    }

    override fun playAt(index: Int) {
        scope.launch {
            opMutex.withLock {
                if (queueManager.playAt(index) != null) playCurrentLocked()
            }
        }
    }

    override fun removeFromQueue(index: Int) {
        scope.launch {
            opMutex.withLock {
                val removingCurrent = index == queueManager.currentIndex
                if (!queueManager.removeAt(index)) return@withLock
                publishQueueLocked()
                if (removingCurrent) {
                    // Removing the playing entry advances to whatever now
                    // sits at its position; empty queue stops the player.
                    if (queueManager.isEmpty) stopLocked() else playCurrentLocked()
                }
            }
        }
    }

    override fun moveInQueue(from: Int, to: Int) {
        scope.launch {
            opMutex.withLock {
                if (queueManager.move(from, to)) publishQueueLocked()
            }
        }
    }

    override fun playPause() {
        if (!vlcAvailable) {
            // VLC missing: keep the descriptive Error state; don't crash.
            if (_state.value !is PlaybackState.Error) {
                _state.value = PlaybackState.Error(
                    _currentTrack.value,
                    "VLC not found — install VLC (https://www.videolan.org/vlc/) and restart. Detail: ${vlcInitError ?: "unknown"}",
                )
            }
            return
        }
        if (pendingLazyStart) {
            scope.launch { opMutex.withLock { if (pendingLazyStart) playCurrentLocked(true) } }
            return
        }
        when (_state.value) {
            is PlaybackState.Playing -> mediaPlayer?.controls()?.pause()
            is PlaybackState.Paused,
            is PlaybackState.Buffering,
            is PlaybackState.Recovering,
            -> mediaPlayer?.controls()?.play()
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
                    if (vlcAvailable) {
                        mediaPlayer?.controls()?.setTime(0)
                        _positionMs.value = 0
                        if (wasPlaying) mediaPlayer?.controls()?.play()
                    } else {
                        _positionMs.value = 0
                    }
                } else {
                    playCurrentLocked()
                }
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val safe = positionMs.coerceAtLeast(0)
        if (pendingLazyStart) {
            // Nothing loaded yet: remember it and apply once playback starts.
            pendingSeekMs = safe
            _positionMs.value = safe
            return
        }
        if (vlcAvailable) {
            mediaPlayer?.controls()?.setTime(safe)
        }
        _positionMs.value = safe
    }

    /** libVLC ignores setTime before the media is actually playing. */
    private suspend fun seekWhenPlaying(positionMs: Long) {
        repeat(40) {
            if (_state.value is PlaybackState.Playing) {
                if (vlcAvailable) mediaPlayer?.controls()?.setTime(positionMs)
                _positionMs.value = positionMs
                return
            }
            delay(100)
        }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        queueManager.setRepeatMode(mode)
        _repeatMode.value = queueManager.repeatMode
    }

    override fun setShuffle(enabled: Boolean) {
        if (queueManager.shuffleEnabled != enabled) {
            queueManager.toggleShuffle()
            _shuffleEnabled.value = queueManager.shuffleEnabled
            publishQueueLocked()
        }
    }

    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _volume.value = v
        if (vlcAvailable) runCatching { mediaPlayer?.audio()?.setVolume((v * 100).toInt()) }
    }

    override fun stop() {
        scope.launch { opMutex.withLock { stopLocked() } }
    }

    /**
     * Manual recovery from [PlaybackState.Error]: re-resolves the current
     * track from scratch (fresh stream URL) and resumes. No-op unless the
     * engine is actually in an error state — never clobbers a live queue.
     */
    override fun retry() {
        if (_state.value !is PlaybackState.Error) return
        scope.launch { opMutex.withLock { playCurrentLocked(true) } }
    }

    /** Tears down libVLC resources. Call once when the app exits. */
    fun release() {
        cancelCacheFill()
        cancelPrebuffer()
        pollJob?.cancel()
        pollJob = null
        runCatching { mediaPlayer?.release() }
        runCatching { factory?.release() }
    }

    /* ---------------- internals ---------------- */

    private suspend fun advanceOnEnded() {
        opMutex.withLock {
            val nextTrack = queueManager.next(trackEnded = true)
            if (nextTrack == null) stopLocked() else playCurrentLocked()
        }
    }

    private suspend fun playCurrentLocked(playWhenReady: Boolean = true) {
        val track = queueManager.current ?: run {
            _state.value = PlaybackState.Idle
            return
        }
        _currentTrack.value = track
        publishQueueLocked()
        _durationMs.value = (track.durationSeconds ?: 0) * 1000L
        if (!playWhenReady) {
            // Restored session: do not resolve or touch libVLC until the user
            // presses play (stream URLs expire anyway). playPause() handles it.
            pendingLazyStart = true
            localFallbackAttempted = false
            _state.value = PlaybackState.Paused(track)
            return
        }
        pendingLazyStart = false
        cancelCacheFill()
        cancelPrebuffer()

        // Immediate silence on track switch: prevent old track audio from lingering while resolving
        if (vlcAvailable) {
            runCatching { mediaPlayer?.controls()?.stop() }
        }
        _positionMs.value = 0

        // Check if this track was pre-buffered in temporary cache (ADR-005): promote and play immediately!
        if (audioCache != null && audioCache.hasTemp(track.id)) {
            val promoted = audioCache.promoteTempToPermanent(track.id)
            if (promoted != null) {
                log("pre-buffered hit: promoted temp to permanent ${track.id} (${promoted.length()} bytes) — instant playback")
                audioCache.clearTemp(keepVideoId = null)
                streamingRemoteUrl = null
                localFallbackAttempted = false
                startMedia(track, promoted.absolutePath)
                return
            }
        }

        // Cache hit: permanent local file, no network, no resolve (offline replay).
        val cached = audioCache?.fileFor(track.id)
        if (cached != null) {
            log("cache hit ${track.id} (${cached.length()} bytes) — playing local file")
            audioCache?.clearTemp(keepVideoId = null)
            streamingRemoteUrl = null
            localFallbackAttempted = false
            startMedia(track, cached.absolutePath)
            return
        }

        // Neither permanent nor temp: clear any unplayed stale temp files
        audioCache?.clearTemp(keepVideoId = null)

        _state.value = PlaybackState.Resolving(track)
        val startedAtMs = System.currentTimeMillis()
        log("resolving ${track.id} …")
        when (val result = provider.getStreamInfo(track.id)) {
            is DhunResult.Success -> {
                val info = result.value
                streamingRemoteUrl = info.audioUrl
                localFallbackAttempted = false
                log(
                    "resolved ${track.id} in ${System.currentTimeMillis() - startedAtMs}ms: " +
                        "${info.mimeType} ${info.bitrateKbps ?: "?"}kbps " +
                        "ua=${info.userAgent?.take(40) ?: "<none>"}",
                )
                startMedia(track, info.audioUrl)
                startCacheFill(
                    track.id,
                    info.audioUrl,
                    info.contentLengthBytes,
                    info.userAgent,
                )
            }
            is DhunResult.Failure -> {
                val detail = result.error.detailString()
                log(
                    "resolve FAILED for ${track.id} after " +
                        "${System.currentTimeMillis() - startedAtMs}ms: " +
                        "${result.error::class.simpleName} ${detail ?: ""}",
                )
                _state.value = PlaybackState.Error(
                    track,
                    result.error.toUserMessage(),
                    detail,
                )
            }
        }
    }

    private fun startMedia(track: Track, mrl: String) {
        if (!vlcAvailable) {
            _state.value = PlaybackState.Error(
                track,
                "VLC not available — install VLC (https://www.videolan.org/vlc/) and restart. Detail: ${vlcInitError ?: "unknown"}",
            )
            return
        }
        val mp = mediaPlayer ?: run {
            _state.value = PlaybackState.Error(track, "VLC unavailable")
            return
        }
        mp.media().play(mrl)
        startPolling()
        _state.value = PlaybackState.Buffering(track)
        val resume = pendingSeekMs
        pendingSeekMs = 0
        if (resume > 0) scope.launch { seekWhenPlaying(resume) }
    }

    /**
     * Background fill of the bounded cache while the stream plays. Skipped
     * when the cache is off or the stream is known to exceed the budget.
     * Bandwidth is spent twice for a first play (stream + fill) — accepted
     * v1 trade-off for a URL-only engine; documented in KNOWN_LIMITATIONS.
     */
    private fun startCacheFill(
        videoId: String,
        url: String,
        contentLength: Long?,
        userAgent: String?,
    ) {
        val cache = audioCache ?: return
        if (contentLength != null && contentLength > cache.maxBytes) return
        val cancel = AtomicBoolean(false)
        cacheFillCancel = cancel
        cacheFillJob = scope.launch(Dispatchers.IO) {
            val file = cache.download(
                videoId,
                url,
                expectedBytes = contentLength,
                cancel = cancel,
                userAgent = userAgent,
            )
            if (file != null) {
                log("cached $videoId (${file.length()} bytes, total ${cache.totalBytes()} / ${cache.maxBytes})")
            } else if (!cancel.get()) {
                log("cache fill for $videoId did not complete (miss kept, playback unaffected)")
            }
        }
    }

    private fun cancelCacheFill() {
        cacheFillCancel?.set(true)
        cacheFillCancel = null
        cacheFillJob = null
    }

    /**
     * ADR-005: Pre-buffering the upcoming track into temporary storage.
     * Starts only after the current track is playing to avoid network contention.
     */
    private fun schedulePrebufferNextTrack() {
        val cache = audioCache ?: return
        val nextTrack = queueManager.peekNext(trackEnded = false) ?: return
        // Do not pre-buffer if already in permanent or temp cache
        if (cache.has(nextTrack.id) || cache.hasTemp(nextTrack.id)) return

        cancelPrebuffer()
        val cancel = AtomicBoolean(false)
        prebufferCancel = cancel
        prebufferJob = scope.launch(Dispatchers.IO) {
            log("pre-buffering next track ${nextTrack.id} (${nextTrack.title}) …")
            when (val result = provider.getStreamInfo(nextTrack.id)) {
                is DhunResult.Success -> {
                    if (cancel.get()) return@launch
                    val info = result.value
                    val file = cache.downloadTemp(
                        nextTrack.id,
                        info.audioUrl,
                        expectedBytes = info.contentLengthBytes,
                        cancel = cancel,
                        userAgent = info.userAgent,
                    )
                    if (file != null) {
                        log("pre-buffered next track ${nextTrack.id} (${file.length()} bytes)")
                    }
                }
                is DhunResult.Failure -> {
                    log("pre-buffer resolution failed for ${nextTrack.id}: ${result.error.detailString() ?: ""}")
                }
            }
        }
    }

    private fun cancelPrebuffer() {
        prebufferCancel?.set(true)
        prebufferCancel = null
        prebufferJob?.cancel()
        prebufferJob = null
    }

    /**
     * libVLC reports failure on the MRL it was given. For a remote stream
     * that is usually the CDN refusing libVLC's own User-Agent (it cannot be
     * overridden through vlcj), not a dead URL — so before showing an error,
     * wait for the cache fill — which *does* send the resolving identity —
     * and replay from the local file. One attempt per track.
     */
    private fun handlePlaybackError() {
        val track = _currentTrack.value
        val remoteUrl = streamingRemoteUrl
        if (track == null || remoteUrl == null || localFallbackAttempted) {
            _state.value = PlaybackState.Error(
                track,
                "Playback failed (stream URL or network).",
            )
            return
        }
        localFallbackAttempted = true
        log("libVLC rejected the stream for ${track.id} — retrying from the local copy")
        _state.value = PlaybackState.Recovering(track)
        val fill = cacheFillJob
        scope.launch {
            fill?.join()
            // The user may have skipped or stopped while the copy finished.
            if (_currentTrack.value?.id != track.id) return@launch
            val file = audioCache?.fileFor(track.id)
            if (file != null && file.length() > 0) {
                streamingRemoteUrl = null
                log("playing ${track.id} from the local copy (${file.length()} bytes)")
                startMedia(track, file.absolutePath)
            } else {
                _state.value = PlaybackState.Error(
                    track,
                    "Stream rejected by the CDN and no local copy could be fetched. " +
                        "Check the connection, then press Retry.",
                    "libVLC rejected the stream URL and the cache fill produced no file " +
                        "(VLC cannot send the resolving identity's User-Agent).",
                )
            }
        }
    }

    private fun log(message: String) = println("DHUN cache: $message")

    private fun stopLocked() {
        streamingRemoteUrl = null
        localFallbackAttempted = false
        cancelCacheFill()
        cancelPrebuffer()
        audioCache?.clearTemp(keepVideoId = null)
        pollJob?.cancel()
        pollJob = null
        if (vlcAvailable) runCatching { mediaPlayer?.controls()?.stop() }
        _positionMs.value = 0
        publishQueueLocked()
        _state.value = PlaybackState.Idle
    }

    private fun startPolling() {
        if (!vlcAvailable) return
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val time = runCatching { mediaPlayer?.status()?.time() ?: _positionMs.value }.getOrDefault(_positionMs.value)
                val length = runCatching { mediaPlayer?.status()?.length() ?: _durationMs.value }.getOrDefault(_durationMs.value)
                if (length > 0) _durationMs.value = length
                if (time > 0) _positionMs.value = time
                delay(POLL_MS)
            }
        }
    }

    /** Publishes queue + current-index flows. Call only while holding [opMutex]. */
    private fun publishQueueLocked() {
        _queue.value = queueManager.snapshot
        _currentQueueIndex.value = queueManager.currentIndex
    }

    companion object {
        private const val POLL_MS = 500L
        private val UNKNOWN = Track(id = "", title = "Unknown", artistName = "")
    }
}
