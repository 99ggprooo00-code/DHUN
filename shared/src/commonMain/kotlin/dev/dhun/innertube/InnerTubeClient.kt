package dev.dhun.innertube

import dev.dhun.core.DhunError
import dev.dhun.core.DhunException
import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.SearchResults
import dev.dhun.core.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Search-result filter params. These are YTM's filter protos, base64-encoded.
 * Values verified live against every filter in Phase 02 (see verification doc).
 */
enum class SearchFilter(internal val params: String?) {
    ALL(null),
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"),
    ARTISTS("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"),
    ALBUMS("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"),
    PLAYLISTS("EgWKAQIOAWoKEAkQChAFEAMQBA%3D%3D"),
}

internal const val INNERTUBE_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

internal fun HttpRequestBuilder.browserHeaders() {
    headers {
        append(HttpHeaders.UserAgent, INNERTUBE_USER_AGENT)
        append(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        append(HttpHeaders.Origin, InnerTubeClient.MUSIC_BASE)
        append(HttpHeaders.Referrer, "${InnerTubeClient.MUSIC_BASE}/")
    }
}

/**
 * DHUN's own InnerTube client — METADATA ONLY (search / suggestions /
 * related / lyrics browse) plus RAW PLAYER RESPONSES for the own-client
 * resolver. Per the extraction doctrine and ADR-001: this client never
 * signs URLs, never deciphers challenges, never spoofs attestation. It
 * speaks a small, drill-watched set of InnerTube client identities
 * (WEB_REMIX for metadata; WEB_REMIX + VISIONOS + TVHTML5 for /player) —
 * the client identity list is maintenance surface, deliberately explicit.
 *
 * The WEB_REMIX client version is scraped fresh from the music.youtube.com
 * homepage HTML — the exact discovery step NewPipeExtractor v0.26.5 got
 * wrong. The alternate identities use pinned shapes copied from yt-dlp
 * master (versions rotate slowly; the rot drill watches them).
 */
class InnerTubeClient(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val country: String = "US",
) {
    @Volatile
    private var cachedClientVersion: String? = null

    suspend fun clientVersion(forceRefresh: Boolean = false): String {
        cachedClientVersion?.takeIf { !forceRefresh }?.let { return it }
        val html = try {
            httpClient.get("${MUSIC_BASE}/") { browserHeaders() }.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw DhunException(classify(t))
        }
        val version = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"")
            .find(html)?.groupValues?.get(1)
            ?: throw DhunException(DhunError.Parse("client version missing from homepage HTML"))
        cachedClientVersion = version
        return version
    }

    /* ---------------- public API ----------------------------------------- */

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS): DhunResult<SearchResults> =
        resultify {
            val body = buildJsonObject {
                put("context", context())
                put("query", query)
                filter.params?.let { put("params", it) }
            }
            parseSearchResults(query, postJson("search", body))
        }

    suspend fun searchSuggestions(query: String): DhunResult<List<String>> =
        resultify {
            val body = buildJsonObject {
                put("context", context())
                put("input", query)
            }
            parseSuggestions(postJson("music/get_search_suggestions", body))
        }

    /** Radio queue for a track (RDAMVM playlist = "start radio from this"). */
    suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> =
        resultify {
            val body = buildJsonObject {
                put("context", context())
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
            }
            parseRelatedTracks(postJson("next", body))
        }

    suspend fun getLyrics(videoId: String): DhunResult<Lyrics> =
        resultify {
            val browseId = parseLyricsBrowseId(
                postJson("next", buildJsonObject {
                    put("context", context())
                    put("videoId", videoId)
                })
            ) ?: return@resultify Lyrics.NotAvailable
            parseLyricsBrowse(
                postJson("browse", buildJsonObject {
                    put("context", context())
                    put("browseId", browseId)
                })
            )
        }

    /** Raw player response as WEB_REMIX (music.youtube.com) for the
     *  own-client resolver. */
    suspend fun playerResponse(videoId: String): DhunResult<JsonObject> =
        resultify {
            val root = postJson("player", buildJsonObject {
                put("context", context())
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            })
            checkPlayability(root)
        }

    /**
     * Raw player response under an ALTERNATE InnerTube client identity
     * (see [AltInnertubeClient]) — VISIONOS / TVHTML5 have different, laxer
     * bot-gating profiles than web clients and no PO-token requirement
     * (per yt-dlp master 2026-08 policies). Fallback strategies of the
     * own-client chain (ADR-001 addendum 2026-09-02).
     */
    suspend fun altPlayerResponse(
        videoId: String,
        alt: AltInnertubeClient,
    ): DhunResult<JsonObject> =
        resultify {
            val root = postAltJson("player", buildJsonObject {
                put("context", altContext(alt))
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }, alt)
            checkPlayability(root)
        }

    /* ---------------- internals ------------------------------------------ */

    /** Classifies playabilityStatus, keeping YouTube's own `reason` text —
     *  it is the single most useful on-device diagnostic ("Sign in to
     *  confirm you're not a bot" etc). */
    private fun checkPlayability(root: JsonObject): JsonObject {
        val status = root.obj("playabilityStatus").str("status")
        val reason = root.obj("playabilityStatus").str("reason")?.take(120)
        return when (status) {
            "OK", "LIVE_STREAM_OFFLINE" -> root
            "LOGIN_REQUIRED" -> throw DhunException(DhunError.AuthRequired(reason))
            "UNPLAYABLE", "ERROR" -> throw DhunException(DhunError.Unavailable)
            else -> throw DhunException(
                DhunError.Parse("playabilityStatus=$status${reason?.let { " ($it)" } ?: ""}")
            )
        }
    }

    private fun altContext(alt: AltInnertubeClient): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", alt.name)
            put("clientVersion", alt.version)
            put("hl", "en")
            put("gl", country)
            alt.contextExtras.forEach { (key, value) -> put(key, value) }
        }
    }

    /** POST under an alternate client identity. No version scrape (pinned
     *  shapes), fewer retries — these are FALLBACK strategies, not primary. */
    private suspend fun postAltJson(
        endpoint: String,
        body: JsonObject,
        alt: AltInnertubeClient,
    ): JsonObject {
        var lastError: DhunError = DhunError.Network
        repeat(ALT_MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(backoffMillis(attempt, lastError))
            try {
                val response = httpClient.post("$WWW_BASE/youtubei/v1/$endpoint?prettyPrint=false") {
                    headers {
                        append(HttpHeaders.UserAgent, alt.userAgent)
                        append(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                        append("X-YouTube-Client-Name", alt.headerId)
                        append("X-YouTube-Client-Version", alt.version)
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    timeout { requestTimeoutMillis = 12_000 }
                    setBody(body.toString())
                }
                val code = response.status.value
                when {
                    code == 429 -> lastError = DhunError.RateLimited()
                    code in 500..599 -> lastError = DhunError.Network
                    code != 200 -> throw DhunException(DhunError.Parse("HTTP $code from ${alt.label} /$endpoint"))
                    else -> return Json.parseToJsonElement(response.bodyAsText()).jsonObject
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DhunException) {
                throw e
            } catch (t: Throwable) {
                lastError = classify(t)
            }
        }
        throw DhunException(lastError)
    }

    private fun context(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", cachedClientVersion ?: CLIENT_VERSION_FALLBACK)
            put("hl", "en")
            put("gl", country)
        }
    }

    private suspend fun postJson(endpoint: String, body: JsonObject): JsonObject {
        val version = clientVersion()
        var lastError: DhunError = DhunError.Unknown()
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(backoffMillis(attempt, lastError))
            try {
                val response = httpClient.post("$MUSIC_BASE/youtubei/v1/$endpoint?prettyPrint=false") {
                    browserHeaders()
                    headers {
                        append("X-YouTube-Client-Name", CLIENT_NAME_WEB_REMIX)
                        append("X-YouTube-Client-Version", version)
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    timeout { requestTimeoutMillis = 20_000 }
                    setBody(body.toString())
                }
                val code = response.status.value
                when {
                    code == 429 -> lastError = DhunError.RateLimited()
                    code in 500..599 -> lastError = DhunError.Network
                    code != 200 -> throw DhunException(DhunError.Parse("HTTP $code from /$endpoint"))
                    else -> return Json.parseToJsonElement(response.bodyAsText()).jsonObject
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DhunException) {
                throw e // Parse/AuthRequired/Unavailable are never retried
            } catch (t: Throwable) {
                lastError = classify(t)
            }
        }
        throw DhunException(lastError)
    }

    private suspend fun <T> resultify(block: suspend () -> T): DhunResult<T> =
        try {
            DhunResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: DhunException) {
            DhunResult.Failure(e.error)
        } catch (t: Throwable) {
            DhunResult.Failure(classify(t))
        }

    private fun backoffMillis(attempt: Int, error: DhunError): Long {
        val base = if (error is DhunError.RateLimited) 2_000L else 600L
        return base shl (attempt - 1).coerceAtMost(4)
    }

    private fun classify(t: Throwable): DhunError = when (t) {
        is DhunException -> t.error
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        is IOException -> DhunError.Network
        else -> DhunError.Unknown(t.message)
    }

    companion object {
        const val MUSIC_BASE = "https://music.youtube.com"
        const val WWW_BASE = "https://www.youtube.com"
        const val CLIENT_NAME_WEB_REMIX = "67"
        const val CLIENT_VERSION_FALLBACK = "1.20250310.01.00"
        private const val MAX_ATTEMPTS = 3
        private const val ALT_MAX_ATTEMPTS = 2

        /**
         * Alternate /player identities, shapes copied verbatim from yt-dlp
         * master (2026-08) `INNERTUBE_CLIENTS`. Both have the DEFAULT (i.e.
         * not-required) GVS PO-token policy there — the property that makes
         * them tokenless-viable. Evidence for the picks: Phase-01 spike R5
         * (VISIONOS tokenless even from a flagged datacenter IP) and R3
         * (TVHTML5 gated from datacenter but fine from residential IPs).
         * Pinned versions rotate slowly; the rot drill watches them.
         */
        val ALT_CLIENT_VISIONOS = AltInnertubeClient(
            label = "visionos",
            name = "VISIONOS",
            version = "1.02",
            headerId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            contextExtras = buildJsonObject {
                put("deviceMake", "Apple")
                put("deviceModel", "RealityDevice17,1")
                put("osName", "visionOS")
                put("osVersion", "26.5.23O471")
                put(
                    "userAgent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                )
            },
        )

        val ALT_CLIENT_TV = AltInnertubeClient(
            label = "tv",
            name = "TVHTML5",
            version = "7.20260707.07.00",
            headerId = "7",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) " +
                "Cobalt/25.lts.30.1034943-gold (unlike Gecko), " +
                "Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
        )

        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 25_000
            }
        }
    }
}

/** An alternate InnerTube client identity for /player calls. */
class AltInnertubeClient(
    val label: String,
    internal val name: String,
    internal val version: String,
    internal val headerId: String,
    internal val userAgent: String,
    internal val contextExtras: JsonObject = JsonObject(emptyMap()),
)
