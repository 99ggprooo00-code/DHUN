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
    fun clearAllDeletesMediaAndRows(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("v6"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            manager.enqueue(track("v6"))
            assertEquals(DownloadState.COMPLETED, awaitTerminal(repo, "v6"))
            val path = repo.get("v6")!!.localAudioPath
            assertTrue(storage.exists(path))

            manager.clearAll()

            assertNull(repo.get("v6"))
            assertFalse(storage.exists(path))
            withTimeout(5_000) {
                while (manager.downloads.value.isNotEmpty()) delay(10)
            }
            assertTrue(manager.downloads.value.isEmpty())
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

    @Test
    fun updateRestartPreservesAudioRepairsArtworkAndPausesOrphanedWork(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val audio = storage.audioPath("old", "audio/webm")
        val audioBytes = "already-downloaded-audio".encodeToByteArray()
        storage.append(audio, audioBytes)
        // An older build left an unversioned artwork fragment behind. It must
        // not be prefixed to the new response after the update.
        storage.append(storage.artPath("old") + ".part", "stale-image-fragment".encodeToByteArray())
        repo.upsert(DownloadedTrack(
            trackId = "old",
            title = "Old song",
            artistName = "Artist",
            albumName = "Album",
            durationSeconds = 200,
            thumbnailUrl = "https://images.example/old.jpg",
            localAudioPath = audio,
            localArtworkPath = null,
            fileSizeBytes = audioBytes.size.toLong(),
            mimeType = "audio/webm",
            bitrateKbps = 160,
            downloadState = DownloadState.COMPLETED,
            downloadedAtEpochMs = 1,
        ))
        repo.upsert(DownloadedTrack(
            trackId = "interrupted",
            title = "Interrupted song",
            artistName = "Artist",
            albumName = null,
            durationSeconds = 200,
            thumbnailUrl = null,
            localAudioPath = storage.audioPath("interrupted", "audio/webm") + ".part",
            localArtworkPath = null,
            fileSizeBytes = 0,
            mimeType = "audio/webm",
            bitrateKbps = 160,
            downloadState = DownloadState.DOWNLOADING,
            downloadedAtEpochMs = 2,
        ))
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        // Resolving audio would fail: this restart must need only the artwork
        // request, not a redownload of the healthy completed song.
        FileDownloadManager(
            repo, FakeResolver(DhunResult.Failure(DhunError.Unavailable("must not resolve"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            withTimeout(15_000) {
                while (repo.get("old")?.localArtworkPath == null ||
                    repo.get("interrupted")?.downloadState != DownloadState.PAUSED
                ) delay(10)
            }
            val repaired = repo.get("old") ?: error("old row vanished")
            assertEquals(DownloadState.COMPLETED, repaired.downloadState)
            assertEquals(audio, repaired.localAudioPath)
            assertTrue(storage.files[audio]!!.contentEquals(audioBytes))
            val artwork = repaired.localArtworkPath ?: error("artwork was not repaired")
            assertTrue(storage.files[artwork]!!.contentEquals("audio-bytes".encodeToByteArray()))
            assertEquals(DownloadState.PAUSED, repo.get("interrupted")?.downloadState)
        } finally { managerScope.cancel() }
    }

    @Test
    fun legacyArtworkMovesToTheVersionedFileOnlyAfterSuccessfulRefresh(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val audio = storage.audioPath("legacy-art", "audio/webm")
        storage.append(audio, "audio".encodeToByteArray())
        val oldArt = "${storage.root}/art/legacy-art.jpg"
        storage.append(oldArt, "old-possibly-damaged-cover".encodeToByteArray())
        val versionedArt = storage.artPath("legacy-art")
        assertTrue(oldArt != versionedArt)
        repo.upsert(DownloadedTrack(
            trackId = "legacy-art",
            title = "Legacy cover",
            artistName = "Artist",
            durationSeconds = 200,
            thumbnailUrl = "https://images.example/legacy.jpg",
            localAudioPath = audio,
            localArtworkPath = oldArt,
            fileSizeBytes = 5,
            mimeType = "audio/webm",
            downloadState = DownloadState.COMPLETED,
        ))
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        FileDownloadManager(
            repo, FakeResolver(DhunResult.Failure(DhunError.Unavailable("must not resolve"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            withTimeout(15_000) {
                while (repo.get("legacy-art")?.localArtworkPath != versionedArt) delay(10)
            }
            assertTrue(storage.exists(versionedArt))
            assertTrue(storage.files[versionedArt]!!.contentEquals("audio-bytes".encodeToByteArray()))
            assertFalse(storage.exists(oldArt), "old file is removed only after the new path is persisted")
            assertEquals(DownloadState.COMPLETED, repo.get("legacy-art")?.downloadState)
        } finally { managerScope.cancel() }
    }

    @Test
    fun failedLegacyArtworkRefreshKeepsThePreviousRowAndFile(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        val audio = storage.audioPath("offline-art", "audio/webm")
        storage.append(audio, "audio".encodeToByteArray())
        val oldArt = "${storage.root}/art/offline-art.jpg"
        val oldBytes = "previous-cover".encodeToByteArray()
        storage.append(oldArt, oldBytes)
        repo.upsert(DownloadedTrack(
            trackId = "offline-art",
            title = "Offline cover",
            artistName = "Artist",
            durationSeconds = 200,
            thumbnailUrl = "https://images.example/offline.jpg",
            localAudioPath = audio,
            localArtworkPath = oldArt,
            fileSizeBytes = 5,
            mimeType = "audio/webm",
            downloadState = DownloadState.COMPLETED,
        ))
        val attempts = mutableListOf<String>()
        val offlineDownloader = object : StreamDownloader {
            override suspend fun download(
                url: String,
                userAgent: String?,
                destinationPath: String,
                onProgress: (Long, Long?) -> Unit,
            ): DhunResult<Long> {
                attempts += url
                return DhunResult.Failure(DhunError.Network("offline"))
            }
        }
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        FileDownloadManager(
            repo, FakeResolver(DhunResult.Failure(DhunError.Unavailable("must not resolve"))),
            offlineDownloader, storage, managerScope,
        )
        try {
            withTimeout(15_000) { while (attempts.size < 2) delay(10) }
            assertEquals(oldArt, repo.get("offline-art")?.localArtworkPath)
            assertTrue(storage.files[oldArt]!!.contentEquals(oldBytes))
            assertFalse(storage.exists(storage.artPath("offline-art")))
            assertEquals(DownloadState.COMPLETED, repo.get("offline-art")?.downloadState)
        } finally { managerScope.cancel() }
    }

    @Test
    fun updateRestartMarksMissingAudioRetryableWithoutDeletingItsMetadata(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val repo = repository()
        repo.upsert(DownloadedTrack(
            trackId = "missing",
            title = "Keep this metadata",
            artistName = "Artist",
            albumName = "Album",
            durationSeconds = 200,
            thumbnailUrl = "https://images.example/missing.jpg",
            localAudioPath = storage.audioPath("missing", "audio/webm"),
            localArtworkPath = null,
            fileSizeBytes = 123,
            mimeType = "audio/webm",
            bitrateKbps = 160,
            downloadState = DownloadState.COMPLETED,
            downloadedAtEpochMs = 1,
        ))
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = FileDownloadManager(
            repo, FakeResolver(DhunResult.Success(stream("missing"))),
            FakeStreamDownloader(storage), storage, managerScope,
        )
        try {
            withTimeout(15_000) {
                while (repo.get("missing")?.downloadState != DownloadState.FAILED) delay(10)
            }
            assertEquals("Keep this metadata", repo.get("missing")?.title)
            manager.resume("missing")
            assertEquals(DownloadState.COMPLETED, awaitTerminal(repo, "missing"))
            assertEquals("Keep this metadata", repo.get("missing")?.title)
            assertTrue(storage.exists(repo.get("missing")!!.localAudioPath))
        } finally { managerScope.cancel() }
    }
}
