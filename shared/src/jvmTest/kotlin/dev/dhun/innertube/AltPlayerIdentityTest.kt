package dev.dhun.innertube

import dev.dhun.core.DhunResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Anonymous /player identity (visitorData + signatureTimestamp): parser
 * coverage over YouTube-shaped fixtures (no network) plus MockEngine
 * wire-format and fail-open coverage for the sourcing providers.
 *
 * Shapes pinned from yt-dlp's extraction: ytcfg `VISITOR_DATA` /
 * `visitorData`, watch-page `/s/player/…/base.js`, base.js
 * `signatureTimestamp:<digits>`.
 */
class AltPlayerIdentityTest {
    private fun obj(json: String) = Json.parseToJsonElement(json) as JsonObject

    private val homepageHtml = """
        <html><head><title>YouTube</title></head><body>
        <script>ytcfg.set({"VISITOR_DATA":"CgtTeDJ6TWFVdCjIiEhIKBghTZWVkTW9kdWxlEgJpEBoSCgi0sYa8oKCpkQ==","SESSION_INDEX":0});</script>
        </body></html>
    """.trimIndent()

    private val homepageVisitorDataSpelling = """
        {"responseContext":{"visitorData":"Cgtsb21lVmlzaXRvckRhdGE="},"other":1}
    """.trimIndent()

    private val watchHtml = """
        <html><body><script>var ytInitialPlayerResponse={};</script>
        <script>,"jsUrl":"/s/player/5c9f3a2b/player_ias.vflset/en_US/base.js","sts":0,</script>
        </body></html>
    """.trimIndent()

    private val watchHtmlAbsoluteJs = """
        <html><body>"https://www.youtube.com/s/player/9d8e7f6a/player_ias.vflset/en_US/base.js"</body></html>
    """.trimIndent()

    private val playerJs = """
        ;var cfg={signatureTimestamp:19912,jsUrl:"/s/player/5c9f3a2b/player_ias.vflset/en_US/base.js"};
    """.trimIndent()

    // ---- pure parsers (no client, no network) ----

    @Test
    fun visitorDataParsedFromYtcfgSpelling() {
        assertEquals(
            "CgtTeDJ6TWFVdCjIiEhIKBghTZWVkTW9kdWxlEgJpEBoSCgi0sYa8oKCpkQ==",
            parseVisitorDataFromHomepage(homepageHtml),
        )
    }

    @Test
    fun visitorDataParsedFromContextSpelling() {
        assertEquals("Cgtsb21lVmlzaXRvckRhdGE=", parseVisitorDataFromHomepage(homepageVisitorDataSpelling))
    }

    @Test
    fun visitorDataAbsentReturnsNull() {
        assertNull(parseVisitorDataFromHomepage(""))
        assertNull(parseVisitorDataFromHomepage("<html><body>no identity here</body></html>"))
        assertNull(parseVisitorDataFromHomepage("""{"VISITOR_DATA":""}"""))
    }

    @Test
    fun playerJsUrlAbsolutizesRelativePath() {
        assertEquals(
            "https://www.youtube.com/s/player/5c9f3a2b/player_ias.vflset/en_US/base.js",
            parsePlayerJsUrl(watchHtml),
        )
    }

    @Test
    fun playerJsUrlPassesThroughAbsoluteUrl() {
        assertEquals(
            "https://www.youtube.com/s/player/9d8e7f6a/player_ias.vflset/en_US/base.js",
            parsePlayerJsUrl(watchHtmlAbsoluteJs),
        )
    }

    @Test
    fun playerJsUrlAbsentReturnsNull() {
        assertNull(parsePlayerJsUrl(""))
        assertNull(parsePlayerJsUrl("<html><body>no player here</body></html>"))
    }

    @Test
    fun signatureTimestampParsedFromPlayerJs() {
        assertEquals("19912", parseSignatureTimestampFromPlayerJs(playerJs))
    }

    @Test
    fun signatureTimestampAbsentReturnsNull() {
        assertNull(parseSignatureTimestampFromPlayerJs(""))
        assertNull(parseSignatureTimestampFromPlayerJs("var x = 1;"))
    }

    // ---- wire format: identity attached when provided ----

    @Test
    fun altPlayerAttachesIdentityToBodyAndHeader() = runBlocking {
        var capturedBody: JsonObject? = null
        var capturedVisitorHeader: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/youtubei/v1/player" -> {
                    capturedBody = obj((request.body as TextContent).text)
                    capturedVisitorHeader = request.headers["X-Goog-Visitor-Id"]
                    respond(
                        """{"playabilityStatus":{"status":"OK"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val client = InnerTubeClient(http)
            val result = client.altPlayerResponse(
                "vid123",
                InnerTubeClient.ALT_CLIENT_VISIONOS,
                visitorData = "VISITOR_ABC",
                signatureTimestamp = "19912",
            )
            assertTrue(result is DhunResult.Success)
            val body = capturedBody ?: error("player request was not captured")
            val contextClient = body.obj("context").obj("client")
            assertEquals("VISITOR_ABC", contextClient?.str("visitorData"))
            assertEquals("VISIONOS", contextClient?.str("clientName"))
            assertEquals(
                "19912",
                body.obj("playbackContext").obj("contentPlaybackContext")?.str("signatureTimestamp"),
            )
            assertEquals("VISITOR_ABC", capturedVisitorHeader)
        } finally {
            http.close()
        }
    }

    @Test
    fun altPlayerWithNullIdentityPreservesLegacyWireFormat() = runBlocking {
        var capturedBody: JsonObject? = null
        var sawVisitorHeader = false
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/youtubei/v1/player" -> {
                    capturedBody = obj((request.body as TextContent).text)
                    sawVisitorHeader = request.headers.contains("X-Goog-Visitor-Id")
                    respond(
                        """{"playabilityStatus":{"status":"OK"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val client = InnerTubeClient(http)
            val result = client.altPlayerResponse("vid123", InnerTubeClient.ALT_CLIENT_TV)
            assertTrue(result is DhunResult.Success)
            val body = capturedBody ?: error("player request was not captured")
            assertNull(body.obj("context").obj("client")?.get("visitorData"))
            assertNull(body["playbackContext"])
            assertTrue(!sawVisitorHeader)
        } finally {
            http.close()
        }
    }

    // ---- sourcing providers: success, caching, fail-open ----

    @Test
    fun visitorDataSourcedFromHomepageAndCached() = runBlocking {
        var homepageFetches = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homepageFetches++
                    respond(homepageHtml, headers = headersOf(HttpHeaders.ContentType, "text/html"))
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val client = InnerTubeClient(http)
            val expected = "CgtTeDJ6TWFVdCjIiEhIKBghTZWVkTW9kdWxlEgJpEBoSCgi0sYa8oKCpkQ=="
            assertEquals(expected, client.visitorDataOrNull())
            assertEquals(expected, client.visitorDataOrNull())
            assertEquals(1, homepageFetches)
        } finally {
            http.close()
        }
    }

    @Test
    fun visitorDataFetchFailureFailsOpenToNull() = runBlocking {
        val engine = MockEngine { error("network down") }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            assertNull(InnerTubeClient(http).visitorDataOrNull())
        } finally {
            http.close()
        }
    }

    @Test
    fun signatureTimestampSourcedThroughWatchPageAndPlayerJs() = runBlocking {
        var watchFetches = 0
        var jsFetches = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> {
                    watchFetches++
                    respond(watchHtml, headers = headersOf(HttpHeaders.ContentType, "text/html"))
                }
                "/s/player/5c9f3a2b/player_ias.vflset/en_US/base.js" -> {
                    jsFetches++
                    respond(playerJs, headers = headersOf(HttpHeaders.ContentType, "application/javascript"))
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val client = InnerTubeClient(http)
            assertEquals("19912", client.signatureTimestampOrNull("vid123"))
            // Second resolve re-checks the watch page (player builds rotate)
            // but does not re-download unchanged player JS.
            assertEquals("19912", client.signatureTimestampOrNull("vid123"))
            assertEquals(2, watchFetches)
            assertEquals(1, jsFetches)
        } finally {
            http.close()
        }
    }

    @Test
    fun signatureTimestampFetchFailureFailsOpenToNull() = runBlocking {
        val engine = MockEngine { error("network down") }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            assertNull(InnerTubeClient(http).signatureTimestampOrNull("vid123"))
        } finally {
            http.close()
        }
    }

    @Test
    fun signatureTimestampMissingPlayerJsFailsOpenToNull() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/watch" -> respond(
                    "<html><body>no player here</body></html>",
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            assertNull(InnerTubeClient(http).signatureTimestampOrNull("vid123"))
        } finally {
            http.close()
        }
    }
}
