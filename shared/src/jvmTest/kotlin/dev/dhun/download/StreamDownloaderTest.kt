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
                headers = headersOf(HttpHeaders.ContentLength, (content.size - 8).toString()),
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
            assertEquals("bytes=10-", request.headers[HttpHeaders.Range])
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
    fun rangeNotSatisfiableOnCompletePartIsSuccess(): Unit = runBlocking {
        val storage = TestDownloadStorage()
        val dest = "${storage.root}/audio/x.webm.part"
        storage.append(dest, content) // previous run wrote everything, died before commit

        val engine = MockEngine { _ ->
            respond(content = ByteArray(0), status = HttpStatusCode.RequestedRangeNotSatisfiable)
        }
        val downloader = KtorStreamDownloader(client(engine), storage)
        var progressCalls = 0
        val result = downloader.download("https://example/a", null, dest) { _, _ -> progressCalls++ }

        assertIs<DhunResult.Success<Long>>(result)
        assertEquals(content.size.toLong(), result.value)
        assertEquals(0, progressCalls, "no bytes flow on a 416")
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
