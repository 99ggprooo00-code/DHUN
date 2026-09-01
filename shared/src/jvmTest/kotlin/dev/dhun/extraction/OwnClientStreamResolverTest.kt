package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.getOrNull
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.core.toUserMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Player-response parsing tests (synthetic fixtures in YouTube's shape —
 * no network). The gated/auth-required path is verified live by the smoke
 * run on this bot-flagged sandbox IP.
 *
 * Note on JSON-in-Kotlin escaping: YouTube mimeTypes look like
 *   audio/webm; codecs="opus"
 * so the JSON string value must contain \" — built here with an explicit
 * BSLASH constant to keep every escape unambiguous.
 */
class OwnClientStreamResolverTest {

    private val BSLASH = "\\"

    private fun root(json: String) =
        kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject

    private fun audioFormat(kbps: Int, mimeValue: String, withUrl: Boolean = true): String {
        val urlField = if (withUrl) "\"url\":\"https://gvs.example/stream?itag=140&sig=abc\"," else ""
        return "{\"itag\":140,\"mimeType\":\"" + mimeValue + "\",\"bitrate\":" +
            (kbps * 1000) + ",\"contentLength\":123456789," + urlField + "\"x\":1}"
    }

    private fun formatsJson(vararg entries: String) =
        "{\"streamingData\":{\"adaptiveFormats\":[" + entries.joinToString(",") + "]}}"

    /** YouTube-style mime value WITH embedded quotes: codecs="opus" */
    private fun youtubeMime(container: String, codec: String): String =
        "$container; codecs=" + BSLASH + "\"" + codec + BSLASH + "\""

    @Test
    fun parsesBestAudioFormat() {
        val json = formatsJson(
            audioFormat(48, youtubeMime("audio/mp4", "mp4a.40.2")),
            audioFormat(129, youtubeMime("audio/webm", "opus")),
            audioFormat(256, youtubeMime("audio/mp4", "mp4a.40.5")),
        )
        val info = parseStreamInfo("abc123", root(json))
        assertEquals("abc123", info.videoId)
        assertEquals("audio/mp4", info.mimeType)
        assertEquals(256, info.bitrateKbps)
        assertEquals(123456789L, info.contentLengthBytes)
        assertEquals("mp4a.40.5", info.codec)
        assertTrue(info.audioUrl.startsWith("https://gvs.example/stream"))
    }

    @Test
    fun skipsVideoFormats() {
        val json = formatsJson(
            audioFormat(500, "video/mp4"),
            audioFormat(129, "audio/mp4"),
            audioFormat(160, youtubeMime("audio/webm", "opus")),
        )
        val info = parseStreamInfo("v", root(json))
        assertEquals("audio/webm", info.mimeType)
        assertEquals(160, info.bitrateKbps)
        assertEquals("opus", info.codec)
    }

    @Test
    fun mimeTypeWithoutQuotesStillExtractsCodec() {
        val info = parseStreamInfo("v", root(formatsJson(audioFormat(96, "audio/webm; codecs=opus"))))
        assertEquals("opus", info.codec)
    }

    @Test
    fun plainMimeTypeHasNoCodec() {
        val info = parseStreamInfo("v", root(formatsJson(audioFormat(96, "audio/mp4"))))
        assertEquals("audio/mp4", info.mimeType)
        assertNull(info.codec)
    }

    @Test
    fun urllessAudioFormatsAreIgnored() {
        val json = formatsJson(
            audioFormat(256, "audio/mp4", withUrl = false),
            audioFormat(64, "audio/mp4", withUrl = true),
        )
        val info = parseStreamInfo("v", root(json))
        assertEquals(64, info.bitrateKbps) // the 256k entry had no URL; 64k wins
    }

    @Test
    fun noAudioFormatsFails() {
        val result = runCatching {
            parseStreamInfo("v", root(formatsJson(audioFormat(500, "video/mp4"))))
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun dhunResultTypesRoundTrip() {
        val ok: DhunResult<StreamInfo> = DhunResult.Success(
            StreamInfo("id", "url", "audio/webm", 128)
        )
        assertEquals("url", ok.getOrNull()?.audioUrl)
        val bad: DhunResult<StreamInfo> = DhunResult.Failure(DhunError.AuthRequired)
        assertNull(bad.getOrNull())
        assertEquals(
            "This content needs a signed-in session.",
            (bad as DhunResult.Failure).error.toUserMessage(),
        )
    }
}
