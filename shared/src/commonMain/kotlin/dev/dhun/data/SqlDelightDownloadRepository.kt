package dev.dhun.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.database.DhunDatabase
import dev.dhun.download.DownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import dev.dhun.database.Downloaded_track as DownloadedTrackRow

/** Serialize the enum to the stable TEXT column value (enum name). */
internal fun DownloadState.toDbValue(): String = name

internal fun String.toDownloadState(): DownloadState =
    runCatching { DownloadState.valueOf(this) }.getOrDefault(DownloadState.FAILED)

internal fun DownloadedTrackRow.toDomain(): DownloadedTrack = DownloadedTrack(
    trackId = trackId,
    title = title,
    artistName = artistName,
    albumName = albumName,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    localAudioPath = localAudioPath,
    localArtworkPath = localArtworkPath,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    bitrateKbps = bitrateKbps,
    downloadState = downloadState.toDownloadState(),
    downloadedAtEpochMs = downloadedAtEpochMs,
)

class SqlDelightDownloadRepository(
    private val db: DhunDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : DownloadRepository {
    override suspend fun upsert(track: DownloadedTrack): Unit = withContext(io) {
        db.downloadedTrackQueries.insertOrReplace(
            trackId = track.trackId,
            title = track.title,
            artistName = track.artistName,
            albumName = track.albumName,
            durationSeconds = track.durationSeconds,
            thumbnailUrl = track.thumbnailUrl,
            localAudioPath = track.localAudioPath,
            localArtworkPath = track.localArtworkPath,
            fileSizeBytes = track.fileSizeBytes,
            mimeType = track.mimeType,
            bitrateKbps = track.bitrateKbps,
            downloadState = track.downloadState.toDbValue(),
            downloadedAtEpochMs = track.downloadedAtEpochMs,
        )
    }

    override suspend fun get(trackId: String): DownloadedTrack? = withContext(io) {
        db.downloadedTrackQueries.selectByTrackId(trackId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getCompleted(trackId: String): DownloadedTrack? = withContext(io) {
        get(trackId)?.takeIf { it.isCompleted }
    }

    override fun observeAll(): Flow<List<DownloadedTrack>> =
        db.downloadedTrackQueries.selectAll().asFlow().mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeCompleted(): Flow<List<DownloadedTrack>> =
        db.downloadedTrackQueries.selectCompleted().asFlow().mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeTrack(trackId: String): Flow<DownloadedTrack?> =
        db.downloadedTrackQueries.selectByTrackId(trackId).asFlow().mapToOneOrNull(io)
            .map { it?.toDomain() }

    override suspend fun updateState(trackId: String, state: DownloadState): Unit = withContext(io) {
        db.downloadedTrackQueries.updateState(state.toDbValue(), trackId)
    }

    override suspend fun delete(trackId: String): Unit = withContext(io) {
        db.downloadedTrackQueries.deleteByTrackId(trackId)
    }

    override suspend fun clearAll(): Unit = withContext(io) {
        db.downloadedTrackQueries.clear()
    }

    override suspend fun isDownloaded(trackId: String): Boolean = withContext(io) {
        db.downloadedTrackQueries.isDownloaded(trackId).executeAsOne() > 0L
    }

    override suspend fun count(): Long = withContext(io) {
        db.downloadedTrackQueries.count().executeAsOne()
    }
}
