package dev.dhun.download

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

/**
 * Downloads an audio stream URL into a [DownloadStorage] `.part` file with
 * HTTP Range resume and progress reporting (ADR-006). It is the only layer
 * that touches the network bytes; the caller owns lifecycle/state.
 *
 * Byte-reading deliberately sends the stream's resolving User-Agent (ADS
 * isolation, see [dev.dhun.core.StreamInfo]) and, on interrupt, leaves the
 * `.part` file in place so a later resume continues from the byte offset via
 * a `Range: bytes=<existingSize>-` request.
 */
interface StreamDownloader {
    /**
     * @param destinationPath the `.part` file to write into (existing bytes
     *   are resumed from their length).
     * @param onProgress invoked as bytes accumulate; total is the final total.
     */
    suspend fun download(
        url: String,
        userAgent: String?,
        destinationPath: String,
        onProgress: (bytesWritten: Long, totalBytes: Long?) -> Unit,
    ): DhunResult<Long>
}

class KtorStreamDownloader(
    private val httpClient: HttpClient,
    private val storage: DownloadStorage,
    private val chunkSize: Int = 128 * 1024,
) : StreamDownloader {

    override suspend fun download(
        url: String,
        userAgent: String?,
        destinationPath: String,
        onProgress: (Long, Long?) -> Unit,
    ): DhunResult<Long> {
        storage.ensureDirExists(destinationPath)
        val resumeFrom = runCatching { storage.size(destinationPath) }.getOrDefault(0L)
        val response = try {
            httpClient.request(url) {
                // The stream URL is bound to the client identity that resolved
                // it; sending a different agent yields 403 (ADR-001).
                userAgent?.let { header(HttpHeaders.UserAgent, it) }
                if (resumeFrom > 0L) {
                    header(HttpHeaders.Range, "bytes=${resumeFrom}-")
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            return DhunResult.Failure(DhunError.Network("download open: ${t.message?.take(200)}"))
        }

        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.PartialContent) {
            return DhunResult.Failure(
                DhunError.Network("download HTTP ${response.status.value} for $url"),
            )
        }

        val total: Long? = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { resumeFrom + it }
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(chunkSize)
        var written = resumeFrom
        try {
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read == -1) break // EOF
                if (read == 0) {
                    yield()
                    continue
                }
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                storage.append(destinationPath, chunk)
                written += read
                onProgress(written, total)
            }
        } catch (t: CancellationException) {
            throw t // pause/cancel: leave the `.part` for resume; rethrow
        } catch (t: Throwable) {
            return DhunResult.Failure(DhunError.Network("download aborted: ${t.message?.take(200)}"))
        }
        return DhunResult.Success(written)
    }
}
