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

    private fun searchFixture(): String = javaClass.classLoader
        .getResourceAsStream("fixtures/search-songs-bohemian-rhapsody.json")!!
        .bufferedReader().use { it.readText() }

    private fun radioFixture(): String = javaClass.classLoader
        .getResourceAsStream("fixtures/next-radio-utwMHfDZ6SA.json")!!
        .bufferedReader().use { it.readText() }

    private fun homepageWithContext(visitor: String): String =
        """{"INNERTUBE_CLIENT_VERSION":"1.20261027.10.00","INNERTUBE_CONTEXT":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20261027.10.00","visitorData":"$visitor","hl":"en","gl":"US"},"request":{"useSsl":true},"user":{"lockedSafetyMode":false}}}"""

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
    fun homeContinuationRefreshesASRejectedSessionWithoutLosingItsToken() = runBlocking {
        var homepageRequests = 0
        var browseRequests = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homepageRequests++
                    respond(homepageWithContext(if (homepageRequests == 1) "visitor-old" else "visitor-new"))
                }
                "/youtubei/v1/browse" -> {
                    browseRequests++
                    val expectedVisitor = if (browseRequests == 1) "visitor-old" else "visitor-new"
                    val body = obj((request.body as TextContent).text)
                    assertEquals("next-page", request.url.parameters["ctoken"])
                    assertEquals("next-page", request.url.parameters["continuation"])
                    assertEquals("FEmusic_home", body.str("browseId"))
                    assertEquals(expectedVisitor, body.obj("context").obj("client").str("visitorData"))
                    assertEquals(expectedVisitor, request.headers["X-Goog-Visitor-Id"])
                    if (browseRequests == 1) {
                        respond("{}", status = HttpStatusCode.BadRequest)
                    } else {
                        respond("""{"continuationContents":{"sectionListContinuation":{"contents":[]}}}""")
                    }
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            assertTrue(InnerTubeClient(http).homeFeedContinuation("next-page") is DhunResult.Success)
            assertEquals(2, homepageRequests)
            assertEquals(2, browseRequests)
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
    fun unfilteredSearchUsesServedSessionAndRefreshesItOnceAfterBadRequest() = runBlocking {
        var homepageRequests = 0
        var searchRequests = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homepageRequests++
                    respond(homepageWithContext(if (homepageRequests == 1) "visitor-old" else "visitor-new"))
                }
                "/youtubei/v1/search" -> {
                    searchRequests++
                    val expectedVisitor = if (searchRequests == 1) "visitor-old" else "visitor-new"
                    val body = obj((request.body as TextContent).text)
                    val context = body.obj("context")
                    assertEquals(expectedVisitor, context.obj("client").str("visitorData"))
                    assertEquals(expectedVisitor, request.headers["X-Goog-Visitor-Id"])
                    assertEquals("true", context.obj("request").str("useSsl"))
                    assertEquals("false", context.obj("user").str("lockedSafetyMode"))
                    assertEquals("focus instrumental music", body.str("query"))
                    if (searchRequests == 1) {
                        respond("""{"error":{"status":"INVALID_ARGUMENT","message":"bad request"}}""", status = HttpStatusCode.BadRequest)
                    } else {
                        respond("{}")
                    }
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = InnerTubeClient(http).search(
                "focus instrumental music",
                SearchFilter.ALL,
            ) as DhunResult.Success
            assertEquals("focus instrumental music", result.value.query)
            assertEquals(2, homepageRequests)
            assertEquals(2, searchRequests, "only the fresh-session retry is allowed")
        } finally {
            http.close()
        }
    }

    @Test
    fun repeatedUnfilteredBadRequestStopsAfterOneSessionRefresh() = runBlocking {
        var homepageRequests = 0
        var searchRequests = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homepageRequests++
                    respond(homepageWithContext("visitor-$homepageRequests"))
                }
                "/youtubei/v1/search" -> {
                    searchRequests++
                    respond("{}", status = HttpStatusCode.BadRequest)
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = InnerTubeClient(http).search("chill music", SearchFilter.ALL) as DhunResult.Failure
            assertTrue(result.error is DhunError.Parse)
            assertEquals(2, homepageRequests)
            assertEquals(2, searchRequests)
        } finally {
            http.close()
        }
    }

    @Test
    fun rejectedFilterUsesCurrentParamsForTheSameTypeWithoutDroppingTheFilter() = runBlocking {
        var homepageRequests = 0
        val seenParams = mutableListOf<String?>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homepageRequests++
                    respond(homepageWithContext("visitor-fixture"))
                }
                "/youtubei/v1/search" -> {
                    val body = obj((request.body as TextContent).text)
                    seenParams += body.str("params")
                    if (seenParams.size == 1) {
                        respond(
                            """{"error":{"status":"INVALID_ARGUMENT","message":"bad request"}}""",
                            status = HttpStatusCode.BadRequest,
                        )
                    } else {
                        respond(searchFixture())
                    }
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = InnerTubeClient(http).search(
                "focus instrumental music",
                SearchFilter.SONGS,
            ) as DhunResult.Success
            assertTrue(result.value.songs.isNotEmpty(), "the real filtered fixture must be parsed")
            assertEquals(
                listOf(SearchFilter.SONGS.params, SearchFilter.SONGS.compatibilityParams),
                seenParams,
            )
            assertTrue(seenParams.all { it != null }, "a filtered failure must never become unfiltered")
            assertEquals(1, homepageRequests, "the successful params fallback needs no session refresh")
        } finally {
            http.close()
        }
    }

    @Test
    fun rejectedFilterAndCompatibilityParamsRemainBounded() = runBlocking {
        var homepageRequests = 0
        val seenParams = mutableListOf<String?>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homepageRequests++
                    respond(homepageWithContext("visitor-$homepageRequests"))
                }
                "/youtubei/v1/search" -> {
                    seenParams += obj((request.body as TextContent).text).str("params")
                    respond("{}", status = HttpStatusCode.BadRequest)
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = InnerTubeClient(http).search("chill music") as DhunResult.Failure
            assertTrue(result.error is DhunError.Parse)
            assertEquals(
                listOf(
                    SearchFilter.SONGS.params,
                    SearchFilter.SONGS.compatibilityParams,
                    SearchFilter.SONGS.compatibilityParams,
                ),
                seenParams,
                "old params, current params, then one fresh-session current retry only",
            )
            assertEquals(2, homepageRequests)
        } finally {
            http.close()
        }
    }

    @Test
    fun everyFilteredSearchHasADistinctCurrentCompatibilityValue() {
        for (filter in SearchFilter.entries.filter { it != SearchFilter.ALL }) {
            assertTrue(!filter.compatibilityParams.isNullOrBlank(), filter.name)
            assertTrue(filter.compatibilityParams != filter.params, filter.name)
        }
        assertNull(SearchFilter.ALL.compatibilityParams)
    }

    @Test
    fun radioQueueRequestsThePersistentAudioPanelAndKeepsItsContinuation() = runBlocking {
        var capturedBody: JsonObject? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.20261027.10.00"}""")
                "/youtubei/v1/next" -> {
                    capturedBody = obj((request.body as TextContent).text)
                    respond(radioFixture())
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = InnerTubeClient(http).radioQueuePage("utwMHfDZ6SA") as DhunResult.Success
            assertTrue(result.value.tracks.size > 1)
            assertTrue(!result.value.continuationToken.isNullOrBlank())
            val body = capturedBody ?: error("no /next request")
            assertEquals("utwMHfDZ6SA", body.str("videoId"))
            assertEquals("RDAMVMutwMHfDZ6SA", body.str("playlistId"))
            assertEquals("true", body.str("enablePersistentPlaylistPanel"))
            assertEquals("true", body.str("isAudioOnly"))
            assertEquals("AUTOMIX_SETTING_NORMAL", body.str("tunerSettingValue"))
            val config = body.obj("watchEndpointMusicSupportedConfigs").obj("watchEndpointMusicConfig")
            assertEquals("true", config.str("hasPersistentPlaylistPanel"))
            assertEquals("MUSIC_VIDEO_TYPE_ATV", config.str("musicVideoType"))
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
            """{"error":{"status":"INVALID_ARGUMENT","message":"Request contains an invalid argument.","errors":[{"reason":"badRequest","debugInfo":"cause-secret"}],"debugInfo":"secret-debug","context":{"visitorData":"secret-visitor"}},"request":{"visitorData":"also-secret"}}""",
        )!!
        assertTrue(detail.contains("HTTP 400 from /browse"))
        assertTrue(detail.contains("status=INVALID_ARGUMENT"))
        assertTrue(detail.contains("reason=badRequest"))
        assertTrue(detail.contains("Request contains an invalid argument"))
        for (secret in listOf("debugInfo", "secret-debug", "cause-secret", "visitorData", "secret-visitor", "also-secret", "request")) {
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
