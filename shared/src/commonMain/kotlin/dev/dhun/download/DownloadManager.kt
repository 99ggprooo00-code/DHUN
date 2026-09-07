package dev.dhun.download

import dev.dhun.core.DownloadedTrack
import dev.dhun.core.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Live progress for one in-flight download (ADR-006). */
data class DownloadProgress(
    val trackId: String,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
}

/**
 * Orchestrates persistent offline downloads (ADR-006).
 *
 * Implementations own the download queue, run the [StreamDownloader], persist
 * progress/state via [DownloadRepository], and expose reactive state for UI
 * (Library "Downloaded" section, track badging, storage management). A
 * download that is cancelled or interrupted stays PAUSED with its `.part`
 * file intact so it can resume — it is never deleted until [remove] /
 * [clearAll].
 */
interface DownloadManager {
    /** All downloads (any state), newest first; seeds from the repository. */
    val downloads: StateFlow<List<DownloadedTrack>>

    /** Per-track progress; null when no download is in flight for the track. */
    fun observeProgress(trackId: String): Flow<DownloadProgress?>

    /** Queues the track and starts it when a download slot is free. */
    suspend fun enqueue(track: Track)

    suspend fun enqueueAll(tracks: List<Track>)

    /** Pauses an in-flight or queued download (keeps its `.part`). */
    suspend fun pause(trackId: String)

    /** Resumes a paused/failed download from its byte offset. */
    suspend fun resume(trackId: String)

    /** Cancels + removes an in-flight/queued download and its `.part`. */
    suspend fun cancel(trackId: String)

    /** Deletes a completed download's media + artwork + DB row. */
    suspend fun remove(trackId: String)

    suspend fun clearAll()
}
