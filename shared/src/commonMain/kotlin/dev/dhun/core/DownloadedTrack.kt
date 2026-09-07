package dev.dhun.core

/**
 * Lifecycle of a persistent download (ADR-006).
 *
 * QUEUED → DOWNLOADING → (COMPLETED | FAILED). PAUSED is an explicit
 * user-requested stop; resume returns to DOWNLOADING. A download interrupted
 * by the network is kept as PAUSED with its `.part` file intact so a later
 * resume continues from the byte offset rather than from zero.
 */
enum class DownloadState { QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED }

/**
 * A persistently-downloaded track (ADR-006). Unlike the LRU stream cache
 * (ADR-005), a row here survives until the user deletes it. The row carries
 * its own metadata snapshot so it stays fully renderable offline even when
 * the streaming metadata (or network) is unavailable.
 */
data class DownloadedTrack(
    val trackId: String,
    val title: String,
    val artistName: String,
    val albumName: String? = null,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    /** Absolute path of the locally stored audio file. */
    val localAudioPath: String,
    /** Absolute path of the locally stored artwork, if fetched. */
    val localArtworkPath: String? = null,
    val fileSizeBytes: Long = 0,
    val mimeType: String,
    val bitrateKbps: Int? = null,
    val downloadState: DownloadState = DownloadState.QUEUED,
    val downloadedAtEpochMs: Long = 0,
) {
    val isCompleted: Boolean get() = downloadState == DownloadState.COMPLETED
}
