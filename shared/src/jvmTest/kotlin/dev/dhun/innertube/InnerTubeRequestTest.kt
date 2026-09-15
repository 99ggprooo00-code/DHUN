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

    @Test
    fun firstBrowseUsesTheDiscoveredVersionInBothHeaderAndBody() = runBlocking {
        var posts = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.20260906.01.00"}""")
                "/youtubei/v1/browse" -> {
                    posts++
                    val body = obj((request.body as TextContent).text)
                    assertEquals("1.20260906.01.00", request.headers["X-YouTube-Client-Version"])
                    assertEquals("1.20260906.01.00", body.obj("context").obj("client").str("clientVersion"))
                    assertEquals("WEB_REMIX", body.obj("context").obj("client").str("clientName"))
                    if (posts == 1) {
                        assertEquals("FEmusic_home", body.str("browseId"))
                        respond("""{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"next-page"}}]}}}}]}}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        assertEquals("next-page", body.str("continuation"))
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
}
