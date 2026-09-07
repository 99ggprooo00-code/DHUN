package dev.dhun.download

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.DownloadState
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SqlDelightDownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives ADR-006's [FileDownloadManager] state machine end to end. */
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
    fun enqueueResolvesCompletesAndPersists(): Unit = runTest {
        val storage = TestDownloadStorage()
        val resolver = FakeResolver(DhunResult.Success(stream("v1")))
        val downloader = FakeStreamDownloader(storage)
        val repo = repository()
        val manager = FileDownloadManager(repo, resolver, downloader, storage, backgroundScope)

        manager.enqueue(track("v1"))
        advanceUntilIdle()

        val row = repo.get("v1")!!
        assertEquals(DownloadState.COMPLETED, row.downloadState)
        assertTrue(row.localAudioPath.endsWith("v1.webm"), "path ${row.localAudioPath}")
        assertTrue(storage.exists(row.localAudioPath))
        assertEquals(listOf("v1"), manager.downloads.value.map { it.trackId })
    }

    @Test
    fun resolveFailureMarksFailed(): Unit = runTest {
        val storage = TestDownloadStorage()
        val resolver = FakeResolver(DhunResult.Failure(DhunError.Unavailable("nope")))
        val downloader = FakeStreamDownloader(storage)
        val repo = repository()
        val manager = FileDownloadManager(repo, resolver, downloader, storage, backgroundScope)

        manager.enqueue(track("v2"))
        advanceUntilIdle()

        assertEquals(DownloadState.FAILED, repo.get("v2")?.downloadState)
    }

    @Test
    fun byteFailureMarksFailed(): Unit = runTest {
        val storage = TestDownloadStorage()
        val resolver = FakeResolver(DhunResult.Success(stream("v2b")))
        val downloader = FakeStreamDownloader(storage, fail = true)
        val repo = repository()
        val manager = FileDownloadManager(repo, resolver, downloader, storage, backgroundScope)

        manager.enqueue(track("v2b"))
        advanceUntilIdle()

        assertEquals(DownloadState.FAILED, repo.get("v2b")?.downloadState)
    }

    @Test
    fun removeDeletesMediaAndRow(): Unit = runTest {
        val storage = TestDownloadStorage()
        val resolver = FakeResolver(DhunResult.Success(stream("v3")))
        val downloader = FakeStreamDownloader(storage)
        val repo = repository()
        val manager = FileDownloadManager(repo, resolver, downloader, storage, backgroundScope)

        manager.enqueue(track("v3"))
        advanceUntilIdle()
        val path = repo.get("v3")!!.localAudioPath
        assertTrue(storage.exists(path))

        manager.remove("v3")
        advanceUntilIdle()
        assertNull(repo.get("v3"))
        assertFalse(storage.exists(path))
    }

    @Test
    fun pauseOnCompletedIsNoop(): Unit = runTest {
        val storage = TestDownloadStorage()
        val resolver = FakeResolver(DhunResult.Success(stream("v4")))
        val downloader = FakeStreamDownloader(storage)
        val repo = repository()
        val manager = FileDownloadManager(repo, resolver, downloader, storage, backgroundScope)

        manager.enqueue(track("v4"))
        advanceUntilIdle()
        manager.pause("v4")
        advanceUntilIdle()

        assertEquals(DownloadState.COMPLETED, repo.get("v4")?.downloadState)
    }

    @Test
    fun enqueueCompletedTrackIsSkipped(): Unit = runTest {
        val storage = TestDownloadStorage()
        val resolver = FakeResolver(DhunResult.Success(stream("v5")))
        val downloader = FakeStreamDownloader(storage)
        val repo = repository()
        val manager = FileDownloadManager(repo, resolver, downloader, storage, backgroundScope)

        manager.enqueue(track("v5"))
        advanceUntilIdle()
        val countAfterFirst = repo.count()

        // Re-enqueueing a completed download must not start a second worker.
        manager.enqueue(track("v5"))
        advanceUntilIdle()
        assertEquals(countAfterFirst, repo.count())
    }
}
