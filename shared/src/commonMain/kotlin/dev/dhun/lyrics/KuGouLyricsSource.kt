package dev.dhun.lyrics

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.math.abs

/** Public KuGou lyrics search/download (no credentials); LRC response is base64 UTF-8. */
class KuGouLyricsSource(
    private val client: HttpClient = LrcLibSource.defaultClient(),
    private val baseUrl: String = "https://lyrics.kugou.com",
) : LyricsSource {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun fetch(track: Track): DhunResult<Lyrics> {
        if (track.title.isBlank() || track.artistName.isBlank()) return DhunResult.Success(Lyrics.NotAvailable)
        return try {
            val search = client.get("$baseUrl/search") {
                parameter("ver", 1); parameter("man", "yes"); parameter("client", "pc")
                parameter("keyword", "${track.artistName} - ${track.title}")
                parameter("duration", (track.durationSeconds ?: 0) * 1000)
            }
            if (!search.status.isSuccess()) return DhunResult.Success(Lyrics.NotAvailable)
            val root = Json.parseToJsonElement(search.bodyAsText()) as? JsonObject
            val candidates = root?.get("candidates") as? JsonArray
            val candidate = candidates?.filterIsInstance<JsonObject>()?.minByOrNull {
                abs((it["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) -
                    (track.durationSeconds ?: 0) * 1000L)
            } ?: return DhunResult.Success(Lyrics.NotAvailable)
            val id = candidate["id"]?.jsonPrimitive?.content ?: return DhunResult.Success(Lyrics.NotAvailable)
            val key = candidate["accesskey"]?.jsonPrimitive?.content ?: return DhunResult.Success(Lyrics.NotAvailable)
            val response = client.get("$baseUrl/download") {
                parameter("ver", 1); parameter("client", "pc"); parameter("id", id)
                parameter("accesskey", key); parameter("fmt", "lrc"); parameter("charset", "utf8")
            }
            if (!response.status.isSuccess()) return DhunResult.Success(Lyrics.NotAvailable)
            val body = Json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            val encoded = body?.get("content")?.jsonPrimitive?.content ?: return DhunResult.Success(Lyrics.NotAvailable)
            val text = Base64.decode(encoded).decodeToString()
            val lines = LrcParser.parse(text, allowUnsyncedFallback = false)
            if (lines.isNotEmpty()) DhunResult.Success(Lyrics.Synced(lines))
            else if (text.isNotBlank()) DhunResult.Success(Lyrics.Unsynced(text))
            else DhunResult.Success(Lyrics.NotAvailable)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            DhunResult.Failure(DhunError.Network())
        }
    }
}
