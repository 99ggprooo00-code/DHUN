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
