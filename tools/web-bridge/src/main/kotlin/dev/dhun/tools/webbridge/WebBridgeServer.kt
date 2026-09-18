package dev.dhun.tools.webbridge

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.provider.YouTubeMusicProvider
import dev.dhun.provider.forDesktop
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/* ===========================================================================
   DHUN Bridge — the server the DHUN Web client talks to.

   Why the web client cannot do this itself
   ----------------------------------------
   1. googlevideo binds a signed stream URL to the User-Agent that requested
      it (see dev.dhun.core.StreamInfo.userAgent): "fetching the bytes with a
      different User-Agent is rejected (403 at open, or mid-stream)". A
      browser's <audio> element can only ever send the browser's own agent,
      so the bytes must be proxied by something that replays StreamInfo's.
      That is what `/audio/{videoId}` below does.
   2. Extraction is a cross-origin POST to music.youtube.com/youtubei/v1 with
      PO tokens and visitor ids (dev.dhun.innertube.InnerTubeClient). GitHub
      Pages is static-only, so there is no runtime to perform it.

   This module therefore reuses `:shared` unchanged — the same
   YouTubeMusicProvider.forDesktop() chain the desktop app uses (ADR-001: own
   InnerTube client primary, yt-dlp failover). No extraction logic is
   duplicated here.

   Deliberately dependency-free: com.sun.net.httpserver for serving and
   java.net.http for the byte proxy are both in the JDK.

   Run:
       ./gradlew :tools:web-bridge:run --args="--port 8787"

   Endpoints (JSON, mirroring dev.dhun.core types):
       GET /health                  -> {ok, engine, version}
       GET /search?q=               -> SearchResults
       GET /suggestions?q=          -> List<String>
       GET /stream/{videoId}        -> StreamInfo
       GET /audio/{videoId}         -> proxied bytes (Range supported)
       GET /lyrics/{videoId}        -> Lyrics
   =========================================================================== */

private const val BRIDGE_VERSION = "0.1.0"

private val json = Json { encodeDefaults = true }

/* --------------------------------------------------------------- models -- */

private fun Track.toJson(): JsonElement = buildJsonObject {
    put("id", id)
    put("title", title)
    put("artistName", artistName)
    artistId?.let { put("artistId", it) }
    albumName?.let { put("albumName", it) }
    albumId?.let { put("albumId", it) }
    durationSeconds?.let { put("durationSeconds", it) }
    thumbnailUrl?.let { put("thumbnailUrl", it) }
    put("explicit", explicit)
}

private fun DhunError.toJson(): JsonElement = buildJsonObject {
    put("type", this@toJson::class.simpleName ?: "Unknown")
    put("message", toUserMessage())
}

/**
 * Lyrics is a sealed interface (Entities.kt): Synced carries timed lines,
 * Unsynced carries a plain block, NotAvailable is a data object. Serialised
 * with an explicit `kind` so the client never has to guess.
 */
private fun Lyrics.toJson(): JsonElement = when (this) {
    is Lyrics.Synced -> buildJsonObject {
        put("kind", "synced")
        put("lines", buildJsonArray {
            for (line in lines) {
                add(buildJsonObject {
                    put("text", line.text)
                    line.startTimeMs?.let { put("startTimeMs", it) }
                })
            }
        })
    }
    is Lyrics.Unsynced -> buildJsonObject {
        put("kind", "unsynced")
        put("text", text)
    }
    is Lyrics.NotAvailable -> buildJsonObject { put("kind", "unavailable") }
}

/* ------------------------------------------------------------ utilities -- */

private class BridgeConfig(
    val host: String,
    val port: Int,
    val allowedOrigin: String,
    val country: String,
)

private fun parseArgs(args: Array<String>): BridgeConfig {
    var host = "0.0.0.0"
    var port = 8787
    var allowedOrigin = "*"
    var country = "US"
    var index = 0
    while (index < args.size) {
        val value = args.getOrNull(index + 1)
        when (args[index]) {
            "--host" -> value?.let { host = it }
            "--port" -> value?.let { port = it.toIntOrNull() ?: port }
            "--allowed-origin" -> value?.let { allowedOrigin = it }
            "--country" -> value?.let { country = it }
            "--help" -> {
                println(USAGE)
                kotlin.system.exitProcess(0)
            }
        }
        index += 2
    }
    return BridgeConfig(host, port, allowedOrigin, country)
}

private const val USAGE = """
DHUN Bridge — serves the DHUN Web client.

  --port <n>              listen port (default 8787)
  --host <addr>           bind address (default 0.0.0.0)
  --allowed-origin <o>    CORS Access-Control-Allow-Origin (default *)
  --country <cc>          InnerTube country context (default US)

Point the web client's Bridge field at http://<host>:<port>.
"""

private fun applyCors(exchange: HttpExchange, allowedOrigin: String) {
    val headers = exchange.responseHeaders
    headers.add("Access-Control-Allow-Origin", allowedOrigin)
    headers.add("Access-Control-Allow-Methods", "GET, OPTIONS")
    headers.add("Access-Control-Allow-Headers", "Content-Type, Range")
    headers.add("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges")
    headers.add("Vary", "Origin")
}

private fun sendJson(exchange: HttpExchange, status: Int, body: JsonElement, config: BridgeConfig) {
    val bytes = json.encodeToString(JsonElement.serializer(), body).toByteArray(Charsets.UTF_8)
    applyCors(exchange, config.allowedOrigin)
    exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    exchange.responseHeaders.add("Cache-Control", "no-store")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun sendFailure(exchange: HttpExchange, error: DhunError, config: BridgeConfig) {
    // 502 for upstream/extraction problems; the client shows `message`.
    val status = when (error) {
        is DhunError.RateLimited -> 429
        is DhunError.Unavailable -> 404
        else -> 502
    }
    sendJson(exchange, status, buildJsonObject { put("error", error.toJson()) }, config)
}

private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)

/* ------------------------------------------------------------ byte proxy - */

/**
 * Proxy the resolved googlevideo URL back to the browser, replaying the exact
 * User-Agent that produced it and forwarding Range so <audio> can seek.
 */
private fun proxyAudio(
    exchange: HttpExchange,
    audioUrl: String,
    userAgent: String?,
    mimeType: String,
    config: BridgeConfig,
) {
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    val builder = HttpRequest.newBuilder(URI.create(audioUrl)).GET()
    // The identity that resolved the URL must be the identity that reads it.
    userAgent?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it) }
    exchange.requestHeaders.getFirst("Range")?.let { builder.header("Range", it) }

    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    val status = response.statusCode()
    if (status >= 400) {
        response.body().close()
        sendJson(
            exchange,
            502,
            buildJsonObject {
                put("error", buildJsonObject {
                    put("type", "Upstream")
                    put("message", "googlevideo returned HTTP $status for the resolved stream")
                })
            },
            config,
        )
        return
    }

    applyCors(exchange, config.allowedOrigin)
    val headers = exchange.responseHeaders
    headers.add("Content-Type", mimeType)
    headers.add("Accept-Ranges", "bytes")
    headers.add("Cache-Control", "no-store")
    response.headers().firstValue("Content-Range")
        .ifPresent { headers.add("Content-Range", it) }

    val body: InputStream = response.body()
    val length = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
    // 0 means "no body"; -1 means unknown, which HttpServer spells as chunked.
    exchange.sendResponseHeaders(status, if (length > 0) length else -1)
    exchange.responseBody.use { out -> body.use { it.copyTo(out, bufferSize = 64 * 1024) } }
}

/* ------------------------------------------------------------------ main - */

fun main(args: Array<String>) {
    val config = parseArgs(args)
    val provider: YouTubeMusicProvider = YouTubeMusicProvider.forDesktop(config.country)

    val server = HttpServer.create(InetSocketAddress(config.host, config.port), 0)

    server.createContext("/health", HttpHandler { exchange ->
        try {
            if (exchange.requestMethod == "OPTIONS") {
                applyCors(exchange, config.allowedOrigin)
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
                return@HttpHandler
            }
            sendJson(
                exchange,
                200,
                buildJsonObject {
                    put("ok", true)
                    put("engine", "dhun-shared")
                    put("version", BRIDGE_VERSION)
                    put("country", config.country)
                },
                config,
            )
        } finally {
            exchange.close()
        }
    })

    server.createContext("/search", HttpHandler { exchange ->
        try {
            val query = exchange.requestURI.query
                ?.split("&")
                ?.firstOrNull { it.startsWith("q=") }
                ?.removePrefix("q=")
                ?.let(::decode)
                .orEmpty()
            if (query.isBlank()) {
                sendJson(exchange, 400, buildJsonObject { put("error", "missing q parameter") }, config)
                return@HttpHandler
            }
            when (val result = runBlocking { provider.search(query) }) {
                is DhunResult.Success -> {
                    val page = result.value
                    sendJson(exchange, 200, buildJsonObject {
                        put("query", page.query)
                        put("songs", JsonArray(page.songs.map { it.toJson() }))
                        put("continuationToken", page.continuationToken)
                    }, config)
                }
                is DhunResult.Failure -> sendFailure(exchange, result.error, config)
            }
        } catch (thrown: Exception) {
            sendJson(exchange, 500, buildJsonObject { put("error", thrown.message ?: "bridge error") }, config)
        } finally {
            exchange.close()
        }
    })

    server.createContext("/suggestions", HttpHandler { exchange ->
        try {
            val query = exchange.requestURI.query
                ?.split("&")
                ?.firstOrNull { it.startsWith("q=") }
                ?.removePrefix("q=")
                ?.let(::decode)
                .orEmpty()
            when (val result = runBlocking { provider.searchSuggestions(query) }) {
                is DhunResult.Success -> sendJson(
                    exchange,
                    200,
                    JsonArray(result.value.map { JsonPrimitive(it) }),
                    config,
                )
                is DhunResult.Failure -> sendFailure(exchange, result.error, config)
            }
        } finally {
            exchange.close()
        }
    })

    server.createContext("/stream", HttpHandler { exchange ->
        try {
            val videoId = decode(exchange.requestURI.path.removePrefix("/stream/").trim('/'))
            when (val result = runBlocking { provider.getStreamInfo(videoId) }) {
                is DhunResult.Success -> {
                    val info = result.value
                    sendJson(exchange, 200, buildJsonObject {
                        put("videoId", info.videoId)
                        // Deliberately NOT the googlevideo URL: the client must
                        // use /audio/{id} so the bound User-Agent is replayed.
                        put("audioUrl", "/audio/${info.videoId}")
                        put("mimeType", info.mimeType)
                        info.bitrateKbps?.let { put("bitrateKbps", it) }
                        info.codec?.let { put("codec", it) }
                        info.contentLengthBytes?.let { put("contentLengthBytes", it) }
                    }, config)
                }
                is DhunResult.Failure -> sendFailure(exchange, result.error, config)
            }
        } finally {
            exchange.close()
        }
    })

    server.createContext("/audio", HttpHandler { exchange ->
        try {
            val videoId = decode(exchange.requestURI.path.removePrefix("/audio/").trim('/'))
            when (val result = runBlocking { provider.getStreamInfo(videoId) }) {
                is DhunResult.Success -> proxyAudio(
                    exchange = exchange,
                    audioUrl = result.value.audioUrl,
                    userAgent = result.value.userAgent,
                    mimeType = result.value.mimeType,
                    config = config,
                )
                is DhunResult.Failure -> sendFailure(exchange, result.error, config)
            }
        } catch (thrown: Exception) {
            sendJson(exchange, 500, buildJsonObject { put("error", thrown.message ?: "proxy error") }, config)
        } finally {
            exchange.close()
        }
    })

    server.createContext("/lyrics", HttpHandler { exchange ->
        try {
            val videoId = decode(exchange.requestURI.path.removePrefix("/lyrics/").trim('/'))
            when (val result = runBlocking { provider.getLyrics(videoId) }) {
                is DhunResult.Success -> sendJson(exchange, 200, buildJsonObject {
                    put("videoId", videoId)
                    put("lyrics", result.value.toJson())
                }, config)
                is DhunResult.Failure -> sendFailure(exchange, result.error, config)
            }
        } finally {
            exchange.close()
        }
    })

    server.executor = java.util.concurrent.Executors.newFixedThreadPool(8)
    server.start()
    println("DHUN Bridge $BRIDGE_VERSION listening on http://${config.host}:${config.port}")
    println("  CORS allowed origin: ${config.allowedOrigin}")
    println("  country: ${config.country}")
    println("Point the web client's Bridge field at this URL.")
}
