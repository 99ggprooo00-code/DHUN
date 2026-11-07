package dev.dhun.innertube

import dev.dhun.core.DhunResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stale served-session recovery.
 *
 * `clientVersion` and `visitorData` are scraped from the homepage once and then
 * reused for the life of the process. YouTube rotates the WEB_REMIX build every
 * few weeks; a stale version is answered with a bare HTTP 400, which on the Home
 * feed surfaces as "continuation failed" — endless scrolling stops dead while the
 * first page (fetched while the session was fresh) still renders. The only cure
 * used to be restarting the app.
 *
 * This pins the bounded fix: one re-discovery, then one replay. A request that is
 * still rejected must fail — silently retrying forever would hide a real outage.
 */
class StaleSessionRetryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun aStaleSessionIsRefreshedOnceAndTheRequestIsReplayed() = runBlocking {
        var homeGet = 0
        var browsePosts = 0
        val versions = mutableListOf<String>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> {
                    homeGet++
                    // The served build rotates between the two fetches.
                    respond(
                        """{"INNERTUBE_CLIENT_VERSION":"1.${if (homeGet == 1) "OLD" else "NEW"}.0"}""",
                        headers = jsonHeaders,
                    )
                }
                "/youtubei/v1/browse" -> {
                    browsePosts++
                    versions += request.headers["X-YouTube-Client-Version"].orEmpty()
                    if (browsePosts == 1) {
                        respond("""{"error":{"code":400}}""", HttpStatusCode.BadRequest, jsonHeaders)
                    } else {
                        respond(
                            """{"continuationContents":{"sectionListContinuation":{"contents":[]}}}""",
                            headers = jsonHeaders,
                        )
                    }
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val client = InnerTubeClient(http)
            val result = client.homeFeedContinuation("token")
            assertTrue(result is DhunResult.Success, "a refreshed session must recover the page: $result")
            assertEquals(2, browsePosts, "exactly one replay")
            assertEquals(listOf("1.OLD.0", "1.NEW.0"), versions, "the replay carries the NEW session")
            assertEquals(2, homeGet, "exactly one re-discovery, not one per attempt")
        } finally {
            http.close()
        }
    }

    @Test
    fun aStillRejectedRequestFailsInsteadOfLooping() = runBlocking {
        var browsePosts = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.0.0"}""", headers = jsonHeaders)
                "/youtubei/v1/browse" -> {
                    browsePosts++
                    respond("""{"error":{"code":400}}""", HttpStatusCode.BadRequest, jsonHeaders)
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            val result = InnerTubeClient(http).homeFeedContinuation("token")
            assertTrue(result is DhunResult.Failure, "a genuine rejection must be visible")
            assertEquals(2, browsePosts, "one initial attempt + one replay, then stop")
        } finally {
            http.close()
        }
    }

    @Test
    fun aSuccessfulFirstRequestNeverRevalidates() = runBlocking {
        var browsePosts = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.0.0"}""", headers = jsonHeaders)
                "/youtubei/v1/browse" -> {
                    browsePosts++
                    respond(
                        """{"continuationContents":{"sectionListContinuation":{"contents":[]}}}""",
                        headers = jsonHeaders,
                    )
                }
                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val http = HttpClient(engine) { install(HttpTimeout) }
        try {
            assertTrue(InnerTubeClient(http).homeFeedContinuation("token") is DhunResult.Success)
            assertEquals(1, browsePosts)
        } finally {
            http.close()
        }
    }

    @Test
    fun theReplayStillCarriesTheCursorInTheQueryAndNotTheBody() {
        // Moving the cursor into the JSON body on the recovery path would make
        // YouTube answer with a tab-navigation shell instead of the page — a
        // silent "no more content" with no error to show.
        runBlocking {
            val queryTokens = mutableListOf<String?>()
            val bodyHasContinuation = mutableListOf<Boolean>()
            var posts = 0
            val engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/" -> respond("""{"INNERTUBE_CLIENT_VERSION":"1.0.0"}""", headers = jsonHeaders)
                    "/youtubei/v1/browse" -> {
                        posts++
                        queryTokens += request.url.parameters["ctoken"]
                        val body = Json.parseToJsonElement(
                            (request.body as io.ktor.http.content.TextContent).text,
                        ) as JsonObject
                        bodyHasContinuation += body["continuation"] != null
                        if (posts == 1) {
                            respond("""{"error":{"code":400}}""", HttpStatusCode.BadRequest, jsonHeaders)
                        } else {
                            respond(
                                """{"continuationContents":{"sectionListContinuation":{"contents":[]}}}""",
                                headers = jsonHeaders,
                            )
                        }
                    }
                    else -> error("Unexpected request: ${request.url.encodedPath}")
                }
            }
            val http = HttpClient(engine) { install(HttpTimeout) }
            try {
                InnerTubeClient(http).homeFeedContinuation("cursor-1")
            } finally {
                http.close()
            }
            assertEquals(listOf<String?>("cursor-1", "cursor-1"), queryTokens)
            assertTrue(bodyHasContinuation.none { it }, "the cursor must never move into the JSON body")
        }
    }
}
