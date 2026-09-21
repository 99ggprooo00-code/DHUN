package dev.dhun.download

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

/**
 * Default Ktor client for DHUN's downloader (desktop/JVM). Shared so
 * platform modules don't need the CIO engine on their own classpath
 * (shared already depends on it). Android uses
 * [createAndroidDownloadHttpClient] (OkHttp) instead — see that factory.
 *
 * Timeouts are connect + socket-idle only — never a request timeout, which
 * would abort large files mid-transfer. Without the idle timeout a
 * throttled stream hangs forever with no visible failure.
 */
fun createDownloadHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
}

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
                // ALWAYS Range — even `bytes=0-` on a fresh fetch. YouTube
                // throttles non-Range progressive requests (InnerTune appends
                // `&range=0-N` to every download URL for exactly this reason);
                // the old Range-only-on-resume code downloaded at a trickle.
                header(HttpHeaders.Range, "bytes=${resumeFrom}-")
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            return DhunResult.Failure(DhunError.Network("download open: ${t.message?.take(200)}"))
        }

        // 416 with a non-empty part: our offset is already past EOF, i.e. the
        // part holds the whole object (previous run wrote the last byte then
        // died before commit). The existing bytes ARE the download.
        if (response.status == HttpStatusCode.RequestedRangeNotSatisfiable && resumeFrom > 0L) {
            return DhunResult.Success(resumeFrom)
        }

        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.PartialContent) {
            return DhunResult.Failure(
                DhunError.Network("download HTTP ${response.status.value} for $url"),
            )
        }

        var written = resumeFrom
        if (response.status == HttpStatusCode.OK && resumeFrom > 0L) {
            // The server ignored our Range and is sending the full object —
            // restart the file, or the bytes corrupt (Exo's DownloadManager
            // does the same on an unexpected 200).
            storage.delete(destinationPath)
            written = 0L
        }

        // 206 Content-Length is the REMAINING bytes; 200 is the full object.
        val remaining = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val total: Long? = when (response.status) {
            HttpStatusCode.PartialContent -> remaining?.let { written + it }
            else -> remaining
        }
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(chunkSize)
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
