package dev.dhun.innertube

import dev.dhun.core.DhunError
import dev.dhun.core.DhunException
import dev.dhun.core.DhunResult
import dev.dhun.core.detailString
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InnerTubeRequestTest {
    private fun obj(json: String) = Json.parseToJsonElement(json) as JsonObject

    private fun homeFixture(): String = javaClass.classLoader
        .getResourceAsStream("fixtures/home-pagination/initial-feed-and-shelf-token.json")!!
        .bufferedReader().use { it.readText() }

    @Test
    fun homeContinuationUsesTheBrowseQueryWireContract() = runBlocking {
        var posts = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.20260906.01.00","VISITOR_DATA":"visitor-fixture"}""")
                "/youtubei/v1/browse" -> {
                    posts++
                    val body = obj((request.body as TextContent).text)
                    assertEquals("1.20260906.01.00", request.headers["X-YouTube-Client-Version"])
                    assertEquals("visitor-fixture", request.headers["X-Goog-Visitor-Id"])
                    assertEquals("1.20260906.01.00", body.obj("context").obj("client").str("clientVersion"))
                    assertEquals("WEB_REMIX", body.obj("context").obj("client").str("clientName"))
                    if (posts == 1) {
                        assertEquals("FEmusic_home", body.str("browseId"))
                        assertEquals("json", request.url.parameters["alt"])
                        assertNull(request.url.parameters["ctoken"])
                        assertNull(request.url.parameters["continuation"])
                        respond("""{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"next-page"}}]}}}}]}}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        assertEquals("FEmusic_home", body.str("browseId"))
                        assertNull(body["continuation"])
                        assertEquals("next-page", request.url.parameters["ctoken"])
                        assertEquals("next-page", request.url.parameters["continuation"])
                        assertTrue(body.obj("context").obj("user") != null)
                        respond("""{"continuationContents":{"sectionListContinuation":{"contents":[]}}}""")
                    }
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val client = InnerTubeClient(http)
            val first = client.homeFeedPage() as DhunResult.Success
            assertEquals("next-page", first.value.continuationToken)
            assertTrue(client.homeFeedContinuation("next-page") is DhunResult.Success)
            assertEquals(2, posts)
        } finally {
            http.close()
        }
    }

    @Test
    fun homeClientVersionConfigAllowsJsonWhitespace() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("""{"INNERTUBE_CLIENT_VERSION" : "1.20261027.10.00"}""")
                "/youtubei/v1/browse" -> {
                    assertEquals("1.20261027.10.00", request.headers["X-YouTube-Client-Version"])
                    respond(
                        """{"contents":{"sectionListRenderer":{"contents":[]}}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            assertTrue(InnerTubeClient(http).homeFeedPage() is DhunResult.Success)
        } finally {
            http.close()
        }
    }

    @Test
    fun malformedOrNonObjectHomeJsonIsParseErrorWithoutDeterministicRetries() = runBlocking {
        for (payload in listOf("{not-json", "[]")) {
            var browseCalls = 0
            val engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.20261027.10.00"}""")
                    "/youtubei/v1/browse" -> {
                        browseCalls++
                        respond(payload, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    else -> error("Unexpected request: ${request.url.encodedPath}")
                }
            }
            val http = HttpClient(engine) { install(HttpTimeout) }
            try {
                val failure = InnerTubeClient(http).homeFeedPage() as DhunResult.Failure
                assertTrue(failure.error is DhunError.Parse, "payload=$payload error=${failure.error}")
                assertTrue(failure.error.detailString().orEmpty().contains("/browse"))
                assertEquals(1, browseCalls, "a malformed body cannot be fixed by repeating the same request")
            } finally {
                http.close()
            }
        }
    }

    @Test
    fun homeBrowseHttpErrorKeepsStatusAndEndpoint() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.20261027.10.00"}""")
                "/youtubei/v1/browse" -> respond("{}", status = HttpStatusCode.BadRequest)
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val failure = InnerTubeClient(http).homeFeedPage() as DhunResult.Failure
            assertTrue(failure.error is DhunError.Parse)
            assertEquals("HTTP 400 from /browse", failure.error.detailString())
        } finally {
            http.close()
        }
    }

    @Test
    fun rejectedHomeBrowseUsesFreshRealHomepageInitialData() = runBlocking {
        var homepageRequests = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homepageRequests++
                    val config = """{"INNERTUBE_CLIENT_VERSION":"1.20261027.10.00"}"""
                    if (homepageRequests == 1) respond(config)
                    else respond("$config<script>var ytInitialData = ${homeFixture()};</script>")
                }
                "/youtubei/v1/browse" -> respond(
                    """{"error":{"status":"INVALID_ARGUMENT","message":"bad request"}}""",
                    status = HttpStatusCode.BadRequest,
                )
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = InnerTubeClient(http).homeFeedPage() as DhunResult.Success
            assertEquals(listOf("a"), result.value.sections.single().tracks.map { it.id })
            assertEquals("page-two", result.value.continuationToken)
            assertEquals(2, homepageRequests, "fallback must fetch fresh content rather than replay a cached success")
        } finally {
            http.close()
        }
    }

    @Test
    fun missingHomeClientVersionReportsHomepageHttpStatusNotJustRegexFailure() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("request denied", status = HttpStatusCode.Forbidden)
                else -> error("Browse must not run without a client version")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val failure = InnerTubeClient(http).homeFeedPage() as DhunResult.Failure
            assertTrue(failure.error is DhunError.Parse)
            val detail = failure.error.detailString().orEmpty()
            assertTrue(detail.contains("HTTP 403"), detail)
            assertTrue(detail.contains("client version missing"), detail)
            assertTrue(!detail.contains("request denied"), "response text must not leak into diagnostics")
        } finally {
            http.close()
        }
    }

    @Test
    fun unplayableAndErrorRetainStatusAndServiceReason() {
        for (status in listOf("UNPLAYABLE", "ERROR")) {
            val error = assertFailsWith<DhunException> {
                checkPlayability(obj("""{"playabilityStatus":{"status":"$status","reason":"This client cannot play the video"}}"""))
            }.error
            assertTrue(error is DhunError.Unavailable)
            assertTrue(error.detailString().orEmpty().contains("status=$status"))
            assertTrue(error.detailString().orEmpty().contains("This client cannot play"))
        }
    }

    /**
     * Drives one alt-identity `/player` call through [MockEngine] and hands back
     * what actually went on the wire: the JSON body and the visitor header.
     */
    private fun altPlayerRequest(visitorData: String?, signatureTimestamp: String?): Pair<JsonObject, String?> {
        var capturedBody: JsonObject? = null
        var capturedVisitorHeader: String? = null
        val engine = MockEngine { request ->
            capturedBody = obj((request.body as TextContent).text)
            capturedVisitorHeader = request.headers["X-Goog-Visitor-Id"]
            respond(
                """{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"vid1"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = runBlocking {
                InnerTubeClient(http).altPlayerResponse(
                    "vid1",
                    InnerTubeClient.ALT_CLIENT_VISIONOS,
                    visitorData = visitorData,
                    signatureTimestamp = signatureTimestamp,
                )
            }
            assertTrue(result is DhunResult.Success)
        } finally {
            http.close()
        }
        return (capturedBody ?: error("the alt /player request never reached the engine")) to capturedVisitorHeader
    }

    /**
     * PR #55 made visitorData/signatureTimestamp optional on the alt identities.
     * The resolver supplies them when sourcing succeeds — but when it cannot
     * (fail-open nulls), "absent from the request" is the behavior every
     * resolve wave depends on: no `visitorData`, no top-level
     * `playbackContext`, no visitor header. Pinned here, because `put(key) { … }`
     * inside `buildJsonObject` (instead of `putJsonObject`) compiled as a
     * lambda-typed JsonElement and left `main` red at `073083c` for four jobs.
     */
    @Test
    fun altPlayerSendsNoSessionFieldsWhenNoneWereCaptured() {
        val (body, visitorHeader) = altPlayerRequest(visitorData = null, signatureTimestamp = null)
        val context = body.obj("context") ?: error("no context in the alt /player body")
        assertEquals("VISIONOS", context.obj("client").str("clientName"))
        assertNull(context.obj("client")?.get("visitorData"))
        assertNull(context["playbackContext"])
        assertNull(body["playbackContext"])
        assertNull(visitorHeader)
    }

    /**
     * When a session IS known, it must appear in all three places the server
     * reads it from — `context.client.visitorData`, the mirrored
     * `X-Goog-Visitor-Id` header, and `signatureTimestamp` inside the
     * TOP-LEVEL `playbackContext.contentPlaybackContext` (a sibling of
     * `context`/`videoId` per the InnerTube schema — yt-dlp sends it there,
     * not nested inside `context`).
     */
    @Test
    fun altPlayerCarriesCapturedSessionFieldsOnTheWire() {
        val (body, visitorHeader) =
            altPlayerRequest(visitorData = "CgtFaK3zEXAMPLE", signatureTimestamp = "24061")
        val context = body.obj("context") ?: error("no context in the alt /player body")
        assertEquals("CgtFaK3zEXAMPLE", context.obj("client").str("visitorData"))
        assertEquals(
            "24061",
            body.obj("playbackContext").obj("contentPlaybackContext").str("signatureTimestamp"),
        )
        assertEquals("CgtFaK3zEXAMPLE", visitorHeader)
    }

    @Test
    fun httpErrorSummaryUsesAllowlistedServiceFieldsOnly() {
        val detail = innerTubeHttpErrorDetail(
            400,
            "browse",
            """{"error":{"status":"INVALID_ARGUMENT","message":"Request contains an invalid argument.","reason":"badRequest","debugInfo":"secret-debug","context":{"visitorData":"secret-visitor"}},"request":{"visitorData":"also-secret"}}""",
        )!!
        assertTrue(detail.contains("HTTP 400 from /browse"))
        assertTrue(detail.contains("status=INVALID_ARGUMENT"))
        assertTrue(detail.contains("reason=badRequest"))
        assertTrue(detail.contains("Request contains an invalid argument"))
        for (secret in listOf("debugInfo", "secret-debug", "visitorData", "secret-visitor", "also-secret", "request")) {
            assertTrue(!detail.contains(secret), "HTTP detail leaked $secret: $detail")
        }
        assertEquals("HTTP 400 from /browse", innerTubeHttpErrorDetail(400, "browse", "not-json"))
    }

    @Test
    fun nestedErrorReasonSurvivesAndSignedUrlsAreRedacted() {
        val error = assertFailsWith<DhunException> {
            checkPlayability(obj("""{"playabilityStatus":{"status":"UNPLAYABLE","errorScreen":{"playerErrorMessageRenderer":{
              "reason":{"simpleText":"Playback on other websites disabled"},
              "subreason":{"runs":[{"text":"Try https://example.com/v?signature=SECRET"}]}
            }}}}"""))
        }.error
        val detail = error.detailString().orEmpty()
        assertTrue(detail.contains("Playback on other websites disabled"))
        assertTrue(detail.contains("<url>"))
        assertTrue(!detail.contains("SECRET"))
    }

    /**
     * `LIVE_STREAM_OFFLINE` is a **content** verdict, not a parsing problem:
     * the service says the broadcast is off and ships its own reason. Passing
     * it through as playable used to make `parseStreamInfo` die later with
     * `Parse("no formats in player response (streamingData empty)")`, so the
     * wave aggregate reported "the music service sent something DHUN couldn't
     * read" for a track that is simply not on — reason lost, and Parse reads
     * as *our* bug (and as something worth retrying).
     */
    @Test
    fun liveStreamOfflineWithoutFormatsReportsUnavailableWithTheServiceReason() {
        val error = assertFailsWith<DhunException> {
            checkPlayability(obj("""{"playabilityStatus":{"status":"LIVE_STREAM_OFFLINE",
              "reason":"This live stream is offline",
              "errorScreen":{"playerErrorMessageRenderer":{
                "subreason":{"simpleText":"It will be back at 8:00 PM"}}}}}"""))
        }.error
        assertTrue(error is DhunError.Unavailable, "was ${error::class.simpleName}")
        val detail = error.detailString().orEmpty()
        assertTrue(detail.contains("status=LIVE_STREAM_OFFLINE"), "detail was: $detail")
        assertTrue(detail.contains("This live stream is offline"), "detail was: $detail")
        assertTrue(detail.contains("It will be back at 8:00 PM"), "detail was: $detail")
    }

    /**
     * The pass-through half of the same rule: a finished broadcast that still
     * serves formats is playable, and playability is not the blocker — so the
     * response must reach the parser untouched rather than being rejected on
     * its status string alone.
     */
    @Test
    fun liveStreamOfflineThatStillCarriesFormatsIsPassedThrough() {
        val response = obj(
            """{"playabilityStatus":{"status":"LIVE_STREAM_OFFLINE","reason":"This live stream is offline"},
               "streamingData":{"adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4",
                 "bitrate":128000,"url":"https://gvs.example/stream?itag=140"}]}}""",
        )
        val passed = checkPlayability(response)
        assertTrue(passed["streamingData"] != null)
    }
}
