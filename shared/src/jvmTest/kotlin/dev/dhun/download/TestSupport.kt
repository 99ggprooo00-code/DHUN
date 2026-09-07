package dev.dhun.download

import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.extraction.StreamResolver

/** In-memory [DownloadStorage] for tests; avoids real file IO flakiness. */
class TestDownloadStorage(override val root: String = "/test") : DownloadStorage {
    val files = LinkedHashMap<String, ByteArray>()

    override fun audioPath(trackId: String, mimeType: String): String =
        "${root}/audio/${safeTrackId(trackId)}.${extensionForMimeType(mimeType)}"

    override fun artPath(trackId: String): String = "${root}/art/${safeTrackId(trackId)}.jpg"

    override suspend fun size(path: String): Long = files[path]?.size?.toLong() ?: 0L

    override suspend fun exists(path: String): Boolean = files.containsKey(path)

    override suspend fun append(path: String, bytes: ByteArray): Unit {
        files[path] = (files[path] ?: ByteArray(0)) + bytes
    }

    override suspend fun commit(part: String, final: String): Unit {
        val data = files.remove(part) ?: return
        files[final] = data
    }

    override suspend fun delete(path: String): Unit { files.remove(path) }

    override suspend fun ensureDirExists(path: String): Unit {}
}

class FakeResolver(private val result: DhunResult<StreamInfo>) : StreamResolver {
    override val name: String = "fake"
    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> = result
}

/** Synchronous fake downloader: writes a fixed payload and reports progress. */
class FakeStreamDownloader(
    private val storage: DownloadStorage,
    private val payload: ByteArray = "audio-bytes".encodeToByteArray(),
    private val fail: Boolean = false,
) : StreamDownloader {
    override suspend fun download(
        url: String,
        userAgent: String?,
        destinationPath: String,
        onProgress: (Long, Long?) -> Unit,
    ): DhunResult<Long> {
        if (fail) return DhunResult.Failure(dev.dhun.core.DhunError.Network("simulated"))
        storage.ensureDirExists(destinationPath)
        val existing = storage.size(destinationPath)
        storage.append(destinationPath, payload)
        val written = existing + payload.size
        onProgress(written, written)
        return DhunResult.Success(written)
    }
}
