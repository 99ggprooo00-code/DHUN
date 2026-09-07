package dev.dhun.data

import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.database.DhunDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR-006 persistent downloads: the SQLDelight repository against a real
 * in-memory SQLite database (schema v3, migration 2.sqm). Confirms the
 * metadata snapshot round-trips, state transitions work, and only COMPLETED
 * rows count as downloaded — the data contract every download-sync layer
 * relies on.
 */
class DownloadRepositoryTest {

    private fun downloaded(
        id: String,
        state: DownloadState = DownloadState.COMPLETED,
        size: Long = 1_024L,
        downloadedAtMs: Long = id.length.toLong(),
    ) = DownloadedTrack(
        trackId = id,
        title = "Song $id",
        artistName = "Artist $id",
        albumName = "Album",
        durationSeconds = 200,
        thumbnailUrl = "https://i/$id.jpg",
        localAudioPath = "/downloads/audio/$id.webm",
        localArtworkPath = "/downloads/art/$id.jpg",
        fileSizeBytes = size,
        mimeType = "audio/webm",
        bitrateKbps = 160,
        downloadState = state,
        downloadedAtEpochMs = downloadedAtMs,
    )

    @Test
    fun upsertAndGetRoundTripsEveryField(): Unit = runBlocking {
        val repo = newRepo()
        val dt = downloaded("a1")
        repo.upsert(dt)
        assertEquals(dt, repo.get("a1"))
        assertEquals(dt, repo.getCompleted("a1"))
        assertTrue(repo.isDownloaded("a1"))
    }

    @Test
    fun completedOnlyCountsAsDownloaded(): Unit = runBlocking {
        val repo = newRepo()
        repo.upsert(downloaded("done", DownloadState.COMPLETED))
        repo.upsert(downloaded("queued", DownloadState.QUEUED))
        repo.upsert(downloaded("failed", DownloadState.FAILED))
        assertTrue(repo.isDownloaded("done"))
        assertFalse(repo.isDownloaded("queued"))
        assertFalse(repo.isDownloaded("failed"))
        assertEquals(3L, repo.count())
        assertEquals(listOf("done"), repo.observeCompleted().first().map { it.trackId })
    }

    @Test
    fun updateStateTransitionsRowWithoutFullRewrite(): Unit = runBlocking {
        val repo = newRepo()
        repo.upsert(downloaded("b1", DownloadState.QUEUED, size = 42L))
        repo.updateState("b1", DownloadState.DOWNLOADING)
        assertEquals(DownloadState.DOWNLOADING, repo.get("b1")?.downloadState)
        // State write must not clobber the pre-existing metadata fields.
        assertEquals(42L, repo.get("b1")?.fileSizeBytes)
        repo.updateState("b1", DownloadState.COMPLETED)
        assertEquals(DownloadState.COMPLETED, repo.get("b1")?.downloadState)
    }

    @Test
    fun observeAllNewestFirstAndDeleteAndClear(): Unit = runBlocking {
        val repo = newRepo()
        repo.upsert(downloaded("x1", downloadedAtMs = 3L))
        repo.upsert(downloaded("x2", downloadedAtMs = 5L))
        assertEquals(listOf("x2", "x1"), repo.observeAll().first().map { it.trackId })
        repo.delete("x2")
        assertNull(repo.get("x2"))
        assertNull(repo.observeTrack("x2").first())
        repo.clearAll()
        assertEquals(0L, repo.count())
        assertEquals(emptyList(), repo.observeAll().first())
    }

    private fun newRepo(): SqlDelightDownloadRepository = SqlDelightDownloadRepository(
        DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()),
        Dispatchers.Unconfined,
    )
}
