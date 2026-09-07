package dev.dhun.download

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.DownloadState
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SqlDelightDownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives ADR-006's [FileDownloadManager] state machine end to end. The
 * manager is given a scope on [Dispatchers.Unconfined] so the download worker
 * runs eagerly on the caller's thread (the fakes have no real suspension),
 * making the assertions deterministic.
 */
class FileDownloadManagerTest {

    private fun repository() = SqlDelightDownloadRepository(
        DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()),
        Dispatchers.Unconfined,
    )

    private fun track(id: String, thumb: String? = null) = Track(
        id = id,
        title = "Title $id",
        artistName = "Artist $id",
        albumName = "Album",
        durationSeconds = 200,
        thumbnailUrl = thumb,
    )

    private fun stream(id: String) = StreamInfo(
        videoId = id,
        audioUrl = "https://cdn/$id",
        mimeType = "audio/webm",
        bitrateKbps = 160,
    )

    @Test
    fun enqueueResolvesCompletesAndPersists(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("v1"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            manager.enqueue(track("v1"))
            val row = repo.get("v1") ?: error("no row")
            assertEquals(DownloadState.COMPLETED, row.downloadState)
            assertTrue(row.localAudioPath.endsWith("v1.webm"), "path ${row.localAudioPath}")
            assertTrue(storage.exists(row.localAudioPath))
        } finally { managerScope.cancel() }
    }

    @Test
    fun resolveFailureMarksFailed(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Failure(DhunError.Unavailable("nope"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            manager.enqueue(track("v2"))
            assertEquals(DownloadState.FAILED, repo.get("v2")?.downloadState)
        } finally { managerScope.cancel() }
    }

    @Test
    fun byteFailureMarksFailed(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("v2b"))),
            FakeStreamDownloader(storage, fail = true), storage, managerScope,
        )
        try {
            manager.enqueue(track("v2b"))
            assertEquals(DownloadState.FAILED, repo.get("v2b")?.downloadState)
        } finally { managerScope.cancel() }
    }

    @Test
    fun removeDeletesMediaAndRow(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("v3"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            manager.enqueue(track("v3"))
            val path = repo.get("v3")!!.localAudioPath
            assertTrue(storage.exists(path))

            manager.remove("v3")
            assertNull(repo.get("v3"))
            assertFalse(storage.exists(path))
        } finally { managerScope.cancel() }
    }

    @Test
    fun pauseOnCompletedIsNoop(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("v4"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            manager.enqueue(track("v4"))
            manager.pause("v4")
            assertEquals(DownloadState.COMPLETED, repo.get("v4")?.downloadState)
        } finally { managerScope.cancel() }
    }

    @Test
    fun enqueueCompletedTrackIsSkipped(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("v5"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            manager.enqueue(track("v5"))
            val countAfterFirst = repo.count()
            manager.enqueue(track("v5"))
            assertEquals(countAfterFirst, repo.count())
        } finally { managerScope.cancel() }
    }
}
