package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunException
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.core.detailString
import dev.dhun.core.diagnosticText
import dev.dhun.core.withDetail
import dev.dhun.innertube.INNERTUBE_USER_AGENT
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.innertube.arr
import dev.dhun.innertube.long
import dev.dhun.innertube.obj
import dev.dhun.innertube.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The own-client resolver (ADR-001 + ADR-003 staged wave parallelisation):
 * InnerTube /player tried under staged concurrent waves of tokenless client
 * identities pinned from yt-dlp master `INNERTUBE_CLIENTS`. Never signs URLs,
 * never deciphers challenges, never uses cookies / PO tokens.
 *
 * Waves (ADR-003 Option C):
 *  Wave 1: WEB_EMBEDDED_PLAYER, VISIONOS
 *  Wave 2: TVHTML5, TVHTML5 downgraded, TVHTML5_SIMPLY
 *  Wave 3: MWEB, WEB_REMIX
 *
 * Each wave races its identities concurrently. The first identity yielding
 * direct audio wins immediately, cancelling remaining requests in that wave.
 * Subsequent waves execute only if preceding waves fail.
 */
class OwnClientStreamResolver(
    private val client: InnerTubeClient,
) : StreamResolver {
    override val name: String = "own-innertube-player"

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
        val outcomes = LinkedHashMap<String, DhunError>()
        for (wave in WAVES) {
            val streamInfo = executeWave(videoId, wave, outcomes)
            if (streamInfo != null) {
                return DhunResult.Success(streamInfo)
            }
            if (outcomes.values.any { it is DhunError.RateLimited }) {
                break // back off, don't hammer
            }
        }
        return DhunResult.Failure(aggregateResolveFailures(outcomes))
    }

    private suspend fun executeWave(
        videoId: String,
        wave: List<Strategy>,
        outcomes: MutableMap<String, DhunError>,
    ): StreamInfo? = coroutineScope {
        if (wave.isEmpty()) return@coroutineScope null
        if (wave.size == 1) {
            val strategy = wave[0]
            val response = try {
                strategy.call(client, videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DhunException) {
                DhunResult.Failure(e.error)
            }
            return@coroutineScope when (response) {
                is DhunResult.Success -> try {
                    parseStreamInfo(videoId, response.value).copy(userAgent = strategy.userAgent)
                } catch (e: DhunException) {
                    outcomes[strategy.label] = e.error
                    null
                }
                is DhunResult.Failure -> {
                    outcomes[strategy.label] = response.error
                    null
                }
            }
        }

        val channel = Channel<Pair<Strategy, DhunResult<JsonObject>>>(wave.size)
        val jobs = wave.map { strategy ->
            launch {
                val res = try {
                    strategy.call(client, videoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DhunException) {
                    DhunResult.Failure(e.error)
                }
                channel.send(strategy to res)
            }
        }

        var winner: StreamInfo? = null
        var received = 0
        while (received < wave.size) {
            val (strategy, result) = channel.receive()
            received++
            when (result) {
                is DhunResult.Success -> {
                    try {
                        val parsed = parseStreamInfo(videoId, result.value).copy(userAgent = strategy.userAgent)
                        winner = parsed
                        break
                    } catch (e: DhunException) {
                        outcomes[strategy.label] = e.error
                    }
                }
                is DhunResult.Failure -> {
                    outcomes[strategy.label] = result.error
                }
            }
        }

        jobs.forEach { it.cancel() }
        winner
    }

    private class Strategy(
        val label: String,
        /**
         * User-Agent this identity presents. Stream URLs resolved through it
         * are only served to the same agent, so it travels with [StreamInfo]
         * all the way to the byte-reading layer.
         */
        val userAgent: String,
        val call: suspend (InnerTubeClient, String) -> DhunResult<JsonObject>,
    )

    companion object {
        private val STRATEGIES = listOf(
            Strategy(
                "web_embedded",
                InnerTubeClient.ALT_CLIENT_WEB_EMBEDDED.userAgent,
            ) { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_WEB_EMBEDDED)
            },
            Strategy("visionos", InnerTubeClient.ALT_CLIENT_VISIONOS.userAgent) { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_VISIONOS)
            },
            Strategy("tv", InnerTubeClient.ALT_CLIENT_TV.userAgent) { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_TV)
            },
            Strategy(
                "tv_downgraded",
                InnerTubeClient.ALT_CLIENT_TV_DOWNGRADED.userAgent,
            ) { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_TV_DOWNGRADED)
            },
            Strategy("tv_simply", InnerTubeClient.ALT_CLIENT_TV_SIMPLY.userAgent) { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_TV_SIMPLY)
            },
            Strategy("mweb", InnerTubeClient.ALT_CLIENT_MWEB.userAgent) { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_MWEB)
            },
            // WEB_REMIX is the primary (non-alt) identity: its /player call
            // goes through browserHeaders(), i.e. INNERTUBE_USER_AGENT.
            Strategy("web_remix", INNERTUBE_USER_AGENT) { c, id -> c.playerResponse(id) },
        )

        private val WAVES: List<List<Strategy>> = listOf(
            listOf(STRATEGIES[1]),
            listOf(STRATEGIES[2], STRATEGIES[3], STRATEGIES[4]),
            listOf(STRATEGIES[5], STRATEGIES[6]),
        )
    }
}

/**
 * Keep every client's evidence, including UNPLAYABLE/ERROR reasons. A rejected
 * embedded client is not proof that the track is removed: rate limiting,
 * authentication and connectivity take precedence over generic unavailability.
 * Bound each reason, not the whole chain, so the last clients are not lost.
 */
internal fun aggregateResolveFailures(outcomes: Map<String, DhunError>): DhunError {
    val summary = outcomes.entries.joinToString("; ") { (label, error) ->
        val category = when (error) {
            is DhunError.AuthRequired -> "AUTH_REQUIRED"
            is DhunError.Parse -> "PARSE"
            is DhunError.RateLimited -> "RATE_LIMITED"
            is DhunError.Network -> "NETWORK"
            is DhunError.Unavailable -> "UNAVAILABLE"
            is DhunError.Unknown -> "UNKNOWN"
        }
        val detail = error.detailString()?.let { "(${diagnosticText(it, 180)})" }.orEmpty()
        "$label=$category$detail"
    }
    val errors = outcomes.values
    val preferred = errors.firstOrNull { it is DhunError.RateLimited }
        ?: errors.firstOrNull { it is DhunError.AuthRequired }
        ?: errors.firstOrNull { it is DhunError.Network }
        ?: errors.firstOrNull { it !is DhunError.Parse }
        ?: errors.firstOrNull()
        ?: return DhunError.Parse("no attempts made")
    return preferred.withDetail(summary)
}

internal fun parseStreamInfo(videoId: String, root: JsonObject): StreamInfo {
    val streaming = root.obj("streamingData")
    val adaptive = streaming?.arr("adaptiveFormats")?.mapNotNull { it as? JsonObject }.orEmpty()
    val progressive = streaming?.arr("formats")?.mapNotNull { it as? JsonObject }.orEmpty()

    fun urlOf(f: JsonObject): String? =
        (f["url"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
    fun mimeOf(f: JsonObject): String = f.str("mimeType") ?: ""

    // audio-only adaptive with a direct URL — the preferred pick
    val audio = adaptive.filter { mimeOf(it).startsWith("audio") && urlOf(it) != null }
    // progressive muxed (video container that carries an audio codec) —
    // heavier but guaranteed playable when adaptive URLs are withheld
    val muxed = progressive.filter {
        urlOf(it) != null && run { val m = mimeOf(it); m.contains("mp4a") || m.contains("opus") }
    }

    val best = audio.maxByOrNull { it.long("bitrate") ?: 0L }
        ?: muxed.maxByOrNull { it.long("bitrate") ?: 0L }
        ?: throw DhunException(
            when {
                adaptive.isEmpty() && progressive.isEmpty() ->
                    DhunError.Parse("no formats in player response (streamingData empty)")
                adaptive.none { urlOf(it) != null } && progressive.none { urlOf(it) != null } ->
                    DhunError.Parse(
                        "all ${adaptive.size + progressive.size} formats lack direct urls " +
                            "(ciphered/protected response)",
                    )
                else -> DhunError.Parse("no audio-capable format with a direct url")
            },
        )

    val mimeType = mimeOf(best)
    // mimeType looks like: audio/webm; codecs="opus"  (quotes included in real responses)
    val codec = mimeType.substringAfter("codecs=", "")
        .replace("\"", "")
        .trim()
        .takeIf { it.isNotEmpty() }
    return StreamInfo(
        videoId = videoId,
        audioUrl = urlOf(best) ?: "",
        mimeType = mimeType.substringBefore(';').trim(),
        bitrateKbps = ((best.long("bitrate") ?: 0L) / 1000L).toInt().takeIf { it > 0 },
        codec = codec,
        contentLengthBytes = best.long("contentLength"),
    )
}
