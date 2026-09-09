package dev.dhun.android.playback

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.player.DhunPlayer
import dev.dhun.player.StreamRecoverySignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * DhunPlayer implementation over a connected Media3 [MediaController].
 * Resolution lives service-side (see [DhunPlaybackService]); this class only
 * translates between the shared DhunPlayer API and the controller, and
 * projects controller events into StateFlows for Compose.
 *
 * THREADING (fatal if violated): the [player] is normally a
 * [androidx.media3.session.MediaController], which enforces main-thread
 * access on EVERY method (verifyApplicationThread — setters, getters and
 * events alike). Calling it from a background dispatcher throws
 * `IllegalStateException: MediaController method is called from a wrong
 * thread` and crashes the app (observed: artist shuffle-play from a
 * Dispatchers.Default ViewModel — full entry in `.ai/DEBUG_LOG.md`).
 * Every controller call in this class is therefore marshalled to the main
 * looper ([onMain] / [Dispatchers.Main]); the session-less ExoPlayer
 * fallback path stays correct through the same route.
 */
class AndroidDhunPlayer(
    private val player: Player,
    private val scope: CoroutineScope,
    private val streamCache: DhunStreamCache? = null,
    /**
     * Resolve-outcome record (playback-diagnostics session, 2026-09-09).
     * Default = the process-wide log the DI-wrapped MusicProvider writes
     * (`ResolveObservingMusicProvider` in AppModule); inject an isolated one
     * in tests. Lets a terminal resolve verdict (AuthRequired/Unavailable)
     * fast-fail out of STATE_BUFFERING instead of riding out the engine's
     * multi-minute retry cascade ("stuck buffering").
     */
    private val resolveOutcomes: ResolveOutcomeLog = ResolveOutcomeLog.global,
    private val clock: () -> Long = System::currentTimeMillis,
) : DhunPlayer {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Run on the main thread; inline when already there (keeps FIFO order). */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private val trackMap = ConcurrentHashMap<String, Track>()
    private var prefetchJob: Job? = null

    /**
     * Epoch ms of the first refresh that observed STATE_BUFFERING in the
     * current bout; 0 when not buffering. Feeds the fast-fail grace so a
     * terminal resolve verdict only cuts in once the player has genuinely
     * been stuck (offline cache-span replay reaches READY far faster and
     * must never flash an error).
     */
    private var bufferingSinceMs = 0L

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

    // Main-pinned: the getters below hit the MediaController, which requires
    // the application thread regardless of the caller's scope.
    private val pollJob: Job = scope.launch(Dispatchers.Main) {
        while (isActive) {
            if (player.isPlaying) {
                _positionMs.value = player.currentPosition.coerceAtLeast(0)
                val d = player.duration
                if (d > 0) _durationMs.value = d
            }
            delay(500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
        override fun onPlayerError(error: PlaybackException) = refresh()
        override fun onRepeatModeChanged(repeatMode: Int) = refresh()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refresh()
        override fun onVolumeChanged(volume: Float) = refresh()
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = refresh()
    }

    init {
        player.addListener(listener)
        refresh()
    }

    override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
        tracks.forEach { trackMap[it.id] = it }
        // Suspend-aware: runs on main AND waits, so callers that chain
        // calls (e.g. restore() → seekTo) keep their order.
        withContext(Dispatchers.Main) {
            player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
            player.playWhenReady = playWhenReady
            player.prepare()
        }
        refresh()
    }

    override fun addNext(track: Track) {
        trackMap[track.id] = track
        onMain {
            val nextIndex = if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1
            player.addMediaItem(nextIndex, track.toMediaItem())
        }
        refresh()
    }

    override fun addToQueue(track: Track) {
        trackMap[track.id] = track
        onMain { player.addMediaItem(track.toMediaItem()) }
        refresh()
    }

    override fun playAt(index: Int) {
        onMain {
            if (index !in 0 until player.mediaItemCount) return@onMain
            player.seekTo(index, androidx.media3.common.C.TIME_UNSET)
            player.play()
        }
        refresh()
    }

    override fun removeFromQueue(index: Int) {
        onMain {
            if (index !in 0 until player.mediaItemCount) return@onMain
            player.removeMediaItem(index)
        }
        refresh()
    }

    override fun moveInQueue(from: Int, to: Int) {
        onMain {
            if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount || from == to) return@onMain
            player.moveMediaItem(from, to)
        }
        refresh()
    }

    override fun playPause() {
        // The isPlaying read must happen on main too (controller getter).
        onMain { if (player.isPlaying) player.pause() else player.play() }
    }

    override fun next() {
        onMain { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    }

    override fun previous() {
        onMain { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
    }

    override fun seekTo(positionMs: Long) {
        onMain { player.seekTo(positionMs) }
        _positionMs.value = positionMs
    }

    override fun setRepeatMode(mode: RepeatMode) {
        onMain {
            player.repeatMode = when (mode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
        }
        refresh()
    }

    override fun setShuffle(enabled: Boolean) {
        onMain { player.shuffleModeEnabled = enabled }
        refresh()
    }

    override fun setVolume(volume: Float) {
        onMain { player.volume = volume.coerceIn(0f, 1f) }
        refresh()
    }

    override fun stop() {
        onMain { player.stop() }
        refresh()
    }

    /**
     * Manual recovery from [PlaybackState.Error]: re-resolves the current
     * item at its last position and resumes. After a failure ExoPlayer sits
     * in error-idle where play() alone is a no-op — only prepare() clears
     * the parked error, which is why the old UI's play button appeared dead.
     */
    override fun retry() {
        onMain {
            if (player.mediaItemCount == 0) return@onMain
            // Drop this track's terminal resolve verdict so the fresh attempt
            // is judged on its own outcome, not instantly fast-failed by the
            // stale one.
            player.currentMediaItem?.mediaId?.let { resolveOutcomes.clear(it) }
            StreamRecoverySignal.end()
            player.seekTo(player.currentPosition.coerceAtLeast(0))
            player.playWhenReady = true
            player.prepare()
        }
        refresh()
    }

    fun release() {
        pollJob.cancel()
        prefetchJob?.cancel()
        onMain {
            player.removeListener(listener)
            player.release()
        }
    }

    /* ---------------- internals ---------------- */

    private fun schedulePrefetchNextTrack() {
        val cache = streamCache ?: return
        if (!player.isPlaying) return
        val currentIdx = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (count <= 1) return
        val nextIdx = when {
            currentIdx + 1 < count -> currentIdx + 1
            player.repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> -1
        }
        if (nextIdx < 0) return
        val nextItem = runCatching { player.getMediaItemAt(nextIdx) }.getOrNull() ?: return
        val nextId = nextItem.mediaId
        if (nextId.isBlank()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            cache.prefetch(nextId)
        }
    }

    /** Listener callbacks already arrive on main; init may run anywhere. */
    private fun refresh() {
        onMain {
            val track = trackOf(player.currentMediaItem)
            _currentTrack.value = track
            _queue.value = (0 until player.mediaItemCount)
                .mapNotNull { trackOf(player.getMediaItemAt(it)) }
            _currentQueueIndex.value = player.currentMediaItemIndex
            _repeatMode.value = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
            _shuffleEnabled.value = player.shuffleModeEnabled
            _volume.value = player.volume.coerceIn(0f, 1f)

            val now = clock()
            if (player.playbackState == Player.STATE_BUFFERING) {
                if (bufferingSinceMs == 0L) bufferingSinceMs = now
            } else {
                bufferingSinceMs = 0L
            }
            val bufferingForMs =
                if (bufferingSinceMs == 0L) 0L else (now - bufferingSinceMs).coerceAtLeast(0L)

            // Fresh terminal verdict for the CURRENT track, if any
            // (AuthRequired/Unavailable — every resolve identity said no).
            val currentId = player.currentMediaItem?.mediaId
            val terminal = currentId?.let { resolveOutcomes.terminalFor(it, now) }

            val engineErrorMessage =
                player.playerError?.let { describeErrorChain(it) } ?: "Playback error"
            val mapped = mapPlaybackState(
                track = track,
                engineError = player.playerError != null,
                engineErrorMessage = engineErrorMessage,
                recoveryActive = StreamRecoverySignal.active.value,
                isPlaying = player.isPlaying,
                playbackState = player.playbackState,
                mediaItemCount = player.mediaItemCount,
                terminalResolveError = terminal,
                bufferingForMs = bufferingForMs,
            )
            if (mapped is PlaybackState.Playing) {
                StreamRecoverySignal.end()
                schedulePrefetchNextTrack()
            }
            if (mapped is PlaybackState.Error && player.playerError != null) {
                android.util.Log.e("DHUN", "playback error: ${mapped.message}")
            }
            if (mapped is PlaybackState.Error && player.playerError == null && terminal != null) {
                // Fast-fail: the engine's bounded retry cascade would keep
                // this player in Buffering/Recovering for minutes while every
                // re-resolve hits the same gate. Surface the typed verdict now.
                android.util.Log.w(
                    "DHUN",
                    "fast-fail: resolve for $currentId is terminal " +
                        "(${terminal::class.simpleName}) after ${bufferingForMs}ms buffering — " +
                        "surfacing typed error instead of the retry cascade",
                )
            }
            _state.value = mapped
        }
    }

    /**
     * Diagnostics harness helper: renders the FULL error chain, not just
     * ExoPlayer's generic "Source error". The resolver deliberately puts
     * per-client evidence (web_remix=…; android=…) into its exception, and
     * this is what surfaces it on screen when playback fails on a device.
     */
    private fun describeErrorChain(error: PlaybackException): String {
        val parts = mutableListOf(
            (if (error.errorCodeName.isNotBlank()) error.errorCodeName + " " else "") +
                error.javaClass.simpleName,
        )
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 5) {
            // 500, not 160: DhunResolveException's message carries the whole
            // per-identity resolve-chain verdict (up to 400 chars on its own),
            // and truncating it is what made device reports inconclusive.
            val msg = cause.message?.take(500)?.trim()
            if (!msg.isNullOrEmpty()) parts.add(msg)
            cause = cause.cause
            depth++
        }
        return parts.joinToString(" ← ").take(1_000)
    }

    private fun trackOf(item: MediaItem?): Track? {
        val id = item?.mediaId ?: return null
        return trackMap[id] ?: Track(id = id, title = item.mediaMetadata.title?.toString() ?: id, artistName = "")
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri("dhun://track/$id") // rewritten by ResolvingDataSource in the service
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistName)
                .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                .build()
        )
        .build()
}
