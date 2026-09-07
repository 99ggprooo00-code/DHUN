package dev.dhun.download

import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import kotlinx.coroutines.flow.Flow

/**
 * Persistent store for downloaded tracks (ADR-006).
 *
 * Contract mirrors the rest of the data layer: flows out, suspend in. The
 * domain/UI layers only ever see this interface, never SQLDelight queries.
 * Implementations must persist the metadata snapshot so downloaded tracks
 * stay renderable offline.
 */
interface DownloadRepository {
    /** Insert or replace the full row (used on completion / metadata change). */
    suspend fun upsert(track: DownloadedTrack)

    suspend fun get(trackId: String): DownloadedTrack?
    suspend fun getCompleted(trackId: String): DownloadedTrack?

    /** All downloads, newest first. */
    fun observeAll(): Flow<List<DownloadedTrack>>

    /** One-shot snapshot of all downloads, newest first (for cleanup). */
    suspend fun getAll(): List<DownloadedTrack>

    /** Only COMPLETED downloads, newest first. */
    fun observeCompleted(): Flow<List<DownloadedTrack>>

    fun observeTrack(trackId: String): Flow<DownloadedTrack?>

    /** Cheap state update without rewriting the row's metadata. */
    suspend fun updateState(trackId: String, state: DownloadState)

    suspend fun delete(trackId: String)
    suspend fun clearAll()
    suspend fun isDownloaded(trackId: String): Boolean
    suspend fun count(): Long
}
