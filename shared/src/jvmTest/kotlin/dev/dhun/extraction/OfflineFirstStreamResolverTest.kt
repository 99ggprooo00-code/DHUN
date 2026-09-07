package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.StreamInfo
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SqlDelightDownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** ADR-006 offline-first: a completed download short-circuits to a file:// URI. */
class OfflineFirstStreamResolverTest {

    private fun repo() = SqlDelightDownloadRepository(
        DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()),
        Dispatchers.Unconfined,
    )

    /** Network resolver that records it was called and fails loudly if it isn't. */
    private class NetworkResolver(private var calls: Int = 0) : StreamResolver {
        override val name: String = "network"
        override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
            calls++
            return DhunResult.Failure(DhunError.Unavailable("network should not be hit for $videoId"))
        }
    }

    private fun downloadedTrack(id: String, state: DownloadState = DownloadState.COMPLETED) = DownloadedTrack(
        trackId = id,
        title = "T$id",
        artistName = "A$id",
        durationSeconds = 200,
        localAudioPath = "/downloads/audio/$id.webm",
        fileSizeBytes = 4096,
        mimeType = "audio/webm",
        bitrateKbps = 160,
        downloadState = state,
    )

    @Test
    fun completedDownloadReturnsFileUri(): Unit = runBlocking {
        val repo = repo()
        repo.upsert(downloadedTrack("v1"))
        val network = NetworkResolver()
        val resolver = OfflineFirstStreamResolver(repo, network) { true }

        val result = resolver.resolve("v1")
        assertIs<DhunResult.Success<StreamInfo>>(result)
        assertTrue(result.value.audioUrl.startsWith("file://"), "got ${result.value.audioUrl}")
        assertEquals(0, network.calls) // network must not be touched
    }

    @Test
    fun missingFileOrNotCompletedFallsThroughToNetwork(): Unit = runBlocking {
        val repo = repo()
        repo.upsert(downloadedTrack("v2")) // COMPLETED but file absent
        val network = NetworkResolver()
        val resolver = OfflineFirstStreamResolver(repo, network) { false }

        val result = resolver.resolve("v2")
        assertIs<DhunResult.Failure>(result)
        assertEquals(1, network.calls)
    }

    @Test
    fun queuedDownloadDoesNotShortCircuit(): Unit = runBlocking {
        val repo = repo()
        repo.upsert(downloadedTrack("v3", DownloadState.QUEUED))
        val network = NetworkResolver()
        val resolver = OfflineFirstStreamResolver(repo, network) { true }

        resolver.resolve("v3")
        assertEquals(1, network.calls)
    }
}
