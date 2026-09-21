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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives ADR-006's [FileDownloadManager] state machine end to end. Workers
 * are pinned to [Dispatchers.IO] (never the caller's thread), so every test
 * awaits a terminal row state instead of assuming eager completion.
 */
class FileDownloadManagerTest {

    private suspend fun awaitTerminal(
        repo: SqlDelightDownloadRepository,
        id: String,
        timeoutMs: Long = 15_000,
    ): DownloadState = withTimeout(timeoutMs) {
        var state = repo.get(id)?.downloadState
        while (state != DownloadState.COMPLETED && state != DownloadState.FAILED) {
            delay(10)
            state = repo.get(id)?.downloadState
        }
        state ?: error("row vanished for $id")
    }

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
            assertEquals(DownloadState.COMPLETED, awaitTerminal(repo, "v1"))
            val row = repo.get("v1") ?: error("no row")
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
            assertEquals(DownloadState.FAILED, awaitTerminal(repo, "v2"))
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
            assertEquals(DownloadState.FAILED, awaitTerminal(repo, "v2b"))
        } finally { managerScope.cancel() }
    }

    @Test
    fun workerCrashMarksFailedInsteadOfSticking(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("v2c"))),
            FakeStreamDownloader(storage, throwable = IllegalStateException("disk gone")),
            storage, managerScope,
        )
        try {
            manager.enqueue(track("v2c"))
            assertEquals(DownloadState.FAILED, awaitTerminal(repo, "v2c"))
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
            assertEquals(DownloadState.COMPLETED, awaitTerminal(repo, "v3"))
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
            assertEquals(DownloadState.COMPLETED, awaitTerminal(repo, "v4"))
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
            assertEquals(DownloadState.COMPLETED, awaitTerminal(repo, "v5"))
            val countAfterFirst = repo.count()
            manager.enqueue(track("v5"))
            assertEquals(countAfterFirst, repo.count())
        } finally { managerScope.cancel() }
    }
}
