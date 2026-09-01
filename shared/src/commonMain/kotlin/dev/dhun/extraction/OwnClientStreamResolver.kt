package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.innertube.arr
import dev.dhun.innertube.long
import dev.dhun.innertube.obj
import dev.dhun.innertube.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The own-client resolver (ADR-001): InnerTube /player as WEB_REMIX, parse
 * audio-only adaptive formats. Works where YouTube does not demand a PO
 * token (residential networks, many mobile carriers); returns typed errors
 * (AuthRequired / Unavailable) where it does not — never guesses, never
 * hangs. This is the Android in-JVM path until a maintained engine is
 * drill-green there.
 */
class OwnClientStreamResolver(
    private val client: InnerTubeClient,
) : StreamResolver {
    override val name: String = "own-innertube-player"

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> =
        when (val result = client.playerResponse(videoId)) {
            is DhunResult.Failure -> result
            is DhunResult.Success -> DhunResult.Success(parseStreamInfo(videoId, result.value))
        }
}

internal fun parseStreamInfo(videoId: String, root: JsonObject): StreamInfo {
    val formats = root.obj("streamingData").arr("adaptiveFormats")
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()

    val audio = formats
        .filter { f -> f.str("mimeType")?.startsWith("audio") == true }
        .filter { f -> (f["url"] as? JsonPrimitive)?.contentOrNull != null }

    require(audio.isNotEmpty()) { "no audio-only formats with direct URLs in player response" }

    val best = audio.maxByOrNull { it.long("bitrate") ?: 0L }
        ?: error("unreachable: audio list non-empty")

    val mimeType = best.str("mimeType") ?: "audio/unknown"
    // mimeType looks like: audio/webm; codecs="opus"  (quotes included in real responses)
    val codec = mimeType.substringAfter("codecs=", "")
        .replace("\"", "")
        .trim()
        .takeIf { it.isNotEmpty() }
    return StreamInfo(
        videoId = videoId,
        audioUrl = (best["url"] as? JsonPrimitive)?.contentOrNull ?: "",
        mimeType = mimeType.substringBefore(';').trim(),
        bitrateKbps = ((best.long("bitrate") ?: 0L) / 1000L).toInt().takeIf { it > 0 },
        codec = codec,
        contentLengthBytes = best.long("contentLength"),
    )
}
