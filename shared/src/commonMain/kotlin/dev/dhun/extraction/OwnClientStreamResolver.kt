package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunException
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.innertube.arr
import dev.dhun.innertube.long
import dev.dhun.innertube.obj
import dev.dhun.innertube.str
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The own-client resolver (ADR-001 + addenda 2026-09-02 / 2026-09-05):
 * InnerTube /player tried under a chain of tokenless client identities
 * pinned from yt-dlp master `INNERTUBE_CLIENTS`. Never signs URLs, never
 * deciphers challenges, never uses cookies / PO tokens.
 *
 * Chain order (evidence-driven, drill-reorderable):
 *  1. WEB_EMBEDDED_PLAYER — no GVS PO policy in yt-dlp; embed thirdParty
 *  2. VISIONOS            — spike R5 (was tokenless even from datacenter)
 *  3. TVHTML5             — spike R3 / yt-dlp `tv`
 *  4. TVHTML5 downgraded  — yt-dlp `tv_downgraded` (older Cobalt)
 *  5. TVHTML5_SIMPLY      — yt-dlp `tv_simply`
 *  6. MWEB                — mobile web; try for progressive/direct URLs
 *  7. WEB_REMIX           — music context; often gated, last cheap try
 *
 * ANDROID/IOS deliberately omitted (yt-dlp marks GVS PO required).
 * Rot-drill 33968950214 proved web_remix+visionos+tv all AuthRequired from
 * Actions IPs — this expanded chain is the proper fix attempt, not a probe
 * weaken. Parses audio-only adaptive formats with direct URLs; falls back
 * to progressive muxed formats when adaptive URLs are withheld.
 */
class OwnClientStreamResolver(
    private val client: InnerTubeClient,
) : StreamResolver {
    override val name: String = "own-innertube-player"

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
        val outcomes = LinkedHashMap<String, DhunError>()
        for (strategy in STRATEGIES) {
            val response = try {
                strategy.call(client, videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DhunException) {
                DhunResult.Failure(e.error)
            }
            when (response) {
                is DhunResult.Success -> try {
                    return DhunResult.Success(parseStreamInfo(videoId, response.value))
                } catch (e: DhunException) {
                    outcomes[strategy.label] = e.error
                }
                is DhunResult.Failure -> {
                    outcomes[strategy.label] = response.error
                    if (response.error is DhunError.RateLimited) break // back off, don't hammer
                }
            }
        }
        return DhunResult.Failure(aggregateResolveFailures(outcomes))
    }

    private class Strategy(
        val label: String,
        val call: suspend (InnerTubeClient, String) -> DhunResult<JsonObject>,
    )

    companion object {
        private val STRATEGIES = listOf(
            Strategy("web_embedded") { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_WEB_EMBEDDED)
            },
            Strategy("visionos") { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_VISIONOS)
            },
            Strategy("tv") { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_TV)
            },
            Strategy("tv_downgraded") { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_TV_DOWNGRADED)
            },
            Strategy("tv_simply") { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_TV_SIMPLY)
            },
            Strategy("mweb") { c, id ->
                c.altPlayerResponse(id, InnerTubeClient.ALT_CLIENT_MWEB)
            },
            Strategy("web_remix") { c, id -> c.playerResponse(id) },
        )
    }
}

/**
 * Folds per-client failures into one typed error. The first non-Parse error
 * wins the type (AuthRequired/Network/… are more actionable than parser
 * noise); the summary naming every client's outcome rides along as `detail`
 * for logs and the diagnostics harness screen.
 */
internal fun aggregateResolveFailures(outcomes: Map<String, DhunError>): DhunError {
    val summary = outcomes.entries.joinToString("; ") { (label, error) ->
        val text = when (error) {
            is DhunError.AuthRequired -> "AUTH_REQUIRED" + (error.detail?.let { "($it)" } ?: "")
            is DhunError.Parse -> "PARSE(${error.detail ?: "response shape"})"
            is DhunError.RateLimited -> "RATE_LIMITED"
            DhunError.Network -> "NETWORK"
            DhunError.Unavailable -> "UNAVAILABLE"
            is DhunError.Unknown -> "UNKNOWN(${error.causeMessage ?: ""})"
        }
        "$label=$text"
    }.take(400)
    val preferred = outcomes.values.firstOrNull { it !is DhunError.Parse }
        ?: outcomes.values.firstOrNull()
        ?: DhunError.Parse("no attempts made")
    return when (preferred) {
        is DhunError.AuthRequired -> DhunError.AuthRequired(summary)
        is DhunError.Parse -> DhunError.Parse(summary)
        else -> preferred
    }
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
