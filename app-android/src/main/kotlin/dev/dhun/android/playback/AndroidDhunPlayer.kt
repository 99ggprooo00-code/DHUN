package dev.dhun.android.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.player.DhunPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * DhunPlayer implementation over a connected Media3 [MediaController].
 * Resolution lives service-side (see [DhunPlaybackService]); this class only
 * translates between the shared DhunPlayer API and the controller, and
 * projects controller events into StateFlows for Compose.
 */
class AndroidDhunPlayer(
    private val player: Player,
    private val scope: CoroutineScope,
) : DhunPlayer {

    private val trackMap = ConcurrentHashMap<String, Track>()

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

    private val pollJob: Job = scope.launch {
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
        player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        player.playWhenReady = playWhenReady
        player.prepare()
        refresh()
    }

    override fun addNext(track: Track) {
        trackMap[track.id] = track
        val nextIndex = if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1
        player.addMediaItem(nextIndex, track.toMediaItem())
        refresh()
    }

    override fun addToQueue(track: Track) {
        trackMap[track.id] = track
        player.addMediaItem(track.toMediaItem())
        refresh()
    }

    override fun playAt(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, androidx.media3.common.C.TIME_UNSET)
        player.play()
        refresh()
    }

    override fun removeFromQueue(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        refresh()
    }

    override fun moveInQueue(from: Int, to: Int) {
        if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount || from == to) return
        player.moveMediaItem(from, to)
        refresh()
    }

    override fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    override fun previous() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    override fun setRepeatMode(mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        refresh()
    }

    override fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        refresh()
    }

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
        refresh()
    }

    override fun stop() {
        player.stop()
        refresh()
    }

    fun release() {
        pollJob.cancel()
        player.removeListener(listener)
        player.release()
    }

    /* ---------------- internals ---------------- */

    private fun refresh() {
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
        _state.value = when {
            player.playerError != null -> {
                val message = player.playerError?.let { describeErrorChain(it) } ?: "Playback error"
                android.util.Log.e("DHUN", "playback error: $message")
                PlaybackState.Error(track, message)
            }
            player.isPlaying -> PlaybackState.Playing(track ?: UNKNOWN)
            player.playbackState == Player.STATE_BUFFERING ->
                PlaybackState.Buffering(track ?: UNKNOWN)
            player.playbackState == Player.STATE_READY ->
                PlaybackState.Paused(track ?: UNKNOWN)
            // Restored-but-not-prepared queue (playWhenReady=false before
            // buffering) is still a paused session, not an idle player.
            player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE ->
                PlaybackState.Paused(track ?: UNKNOWN)
            else -> PlaybackState.Idle
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
            val msg = cause.message?.take(160)?.trim()
            if (!msg.isNullOrEmpty()) parts.add(msg)
            cause = cause.cause
            depth++
        }
        return parts.joinToString(" ← ").take(400)
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

    companion object {
        private val UNKNOWN = Track(id = "", title = "Unknown", artistName = "")
    }
}
