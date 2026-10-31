package dev.dhun.download

import dev.dhun.core.DhunResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Exercises the Ktor byte-downloader (Range-resume + progress + errors). */
class StreamDownloaderTest {

    private val content = "0123456789abcdef".encodeToByteArray()

    private fun client(engine: MockEngine): HttpClient = HttpClient(engine)

    @Test
    fun downloadsBytesToStorageAndReportsProgress(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val engine = MockEngine { request ->
            // Always Range — even on a fresh fetch (anti-throttle, InnerTune rule).
            assertEquals("bytes=0-", request.headers[HttpHeaders.Range])
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, content.size.toString()),
            )
        }
        val downloader = KtorStreamDownloader(client(engine), storage)
        val dest = "${storage.root}/audio/x.webm.part"
        var lastProgress: Long? = null

        val result = downloader.download(
            url = "https://example/a",
            userAgent = "Mozilla/5.0",
            destinationPath = dest,
            onProgress = { written, _ -> lastProgress = written },
        )

        assertIs<DhunResult.Success<Long>>(result)
        assertEquals(content.size.toLong(), result.value)
        assertEquals(content.size, storage.files[dest]?.size)
        assertEquals(content.size.toLong(), lastProgress)
    }

    @Test
    fun resumesFromExistingPartWithRangeHeader(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val dest = "${storage.root}/audio/x.webm.part"
        val initial = "01234567".encodeToByteArray()
        storage.append(dest, initial)

        val engine = MockEngine { request ->
            val range = request.headers[HttpHeaders.Range]
            assertEquals("bytes=8-", range, "resume must request from byte 8")
            respond(
                content = content.copyOfRange(8, content.size),
                status = HttpStatusCode.PartialContent,
                headers = headersOf(
                    HttpHeaders.ContentLength to listOf((content.size - 8).toString()),
                    "Content-Range" to listOf("bytes 8-15/16"),
                ),
            )
        }
        val downloader = KtorStreamDownloader(client(engine), storage)
        val result = downloader.download("https://example/a", null, dest) { _, _ -> }

        assertIs<DhunResult.Success<Long>>(result)
        assertEquals(content.size.toLong(), result.value) // existing 8 + resumed 8
        assertEquals(content.size, storage.files[dest]?.size)
    }

    @Test
    fun serverIgnoringRangeRestartsFileInsteadOfCorrupting(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val dest = "${storage.root}/audio/x.webm.part"
        storage.append(dest, "stale-stale".encodeToByteArray())

        val engine = MockEngine { request ->
            assertEquals("bytes=11-", request.headers[HttpHeaders.Range]) // "stale-stale" is 11 bytes
            // Server ignored the Range: full object with 200.
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, content.size.toString()),
            )
        }
        val downloader = KtorStreamDownloader(client(engine), storage)
        var lastTotal: Long? = null
        val result = downloader.download("https://example/a", null, dest) { _, total -> lastTotal = total }

        assertIs<DhunResult.Success<Long>>(result)
        assertEquals(content.size.toLong(), result.value, "byte count restarts from zero")
        assertEquals(content.size, storage.files[dest]?.size, "stale bytes must be gone")
        assertTrue(storage.files[dest]!!.contentEquals(content))
        assertEquals(content.size.toLong(), lastTotal)
    }

    @Test
    fun rangeNotSatisfiableRestartsUnverifiedPartInsteadOfCommittingIt(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val dest = "${storage.root}/audio/x.webm.part"
        storage.append(dest, "stale-bytes-from-another-response".encodeToByteArray())
        var requests = 0

        val engine = MockEngine { request ->
            requests++
            if (requests == 1) {
                respond(content = ByteArray(0), status = HttpStatusCode.RequestedRangeNotSatisfiable)
            } else {
                assertEquals("bytes=0-", request.headers[HttpHeaders.Range])
                respond(
                    content = content,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentLength, content.size.toString()),
                )
            }
        }
        val downloader = KtorStreamDownloader(client(engine), storage)
        val result = downloader.download("https://example/a", null, dest) { _, _ -> }

        assertIs<DhunResult.Success<Long>>(result)
        assertEquals(content.size.toLong(), result.value)
        assertEquals(2, requests)
        assertTrue(storage.files[dest]!!.contentEquals(content))
    }

    @Test
    fun truncatedResponseIsNotReportedAsACompleteDownload(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val dest = "${storage.root}/audio/x.webm.part"
        val engine = MockEngine { _ ->
            respond(
                content = content.copyOfRange(0, 8),
                status = HttpStatusCode.PartialContent,
                // Declare the complete object/range, then provide a short
                // channel. The downloader must retain the bytes it could read
                // and reject the response instead of reporting a full download.
                headers = headersOf("Content-Range", "bytes 0-15/16"),
            )
        }
        val downloader = KtorStreamDownloader(client(engine), storage)
        val result = downloader.download("https://example/a", null, dest) { _, _ -> }

        assertIs<DhunResult.Failure>(result)
        assertEquals(8L, storage.size(dest), "the partial remains available for a later verified resume")
    }

    @Test
    fun nonSuccessStatusReturnsNetworkFailure(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val engine = MockEngine { _ ->
            respond(content = ByteArray(0), status = HttpStatusCode.Forbidden)
        }
        val downloader = KtorStreamDownloader(client(engine), storage)
        val result = downloader.download("https://example/a", null, "/x.part") { _, _ -> }
        assertIs<DhunResult.Failure>(result)
    }
}
