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
import kotlin.math.abs

/** Tokenless fuzzy fallback when LRCLIB's exact /api/get misses. */
class LrcLibSearchSource(
    private val client: HttpClient = LrcLibSource.defaultClient(),
    private val baseUrl: String = "https://lrclib.net",
) : LyricsSource {
    override suspend fun fetch(track: Track): DhunResult<Lyrics> {
        if (track.title.isBlank() || track.artistName.isBlank()) return DhunResult.Success(Lyrics.NotAvailable)
        return try {
            val response = client.get("$baseUrl/api/search") {
                parameter("q", "${track.artistName} ${track.title}")
            }
            if (!response.status.isSuccess()) return DhunResult.Success(Lyrics.NotAvailable)
            val results = Json.parseToJsonElement(response.bodyAsText()) as? JsonArray
                ?: return DhunResult.Success(Lyrics.NotAvailable)
            val matches = results.filterIsInstance<JsonObject>().filter { item ->
                item["trackName"]?.jsonPrimitive?.content?.equals(track.title, ignoreCase = true) == true &&
                    item["artistName"]?.jsonPrimitive?.content?.contains(track.artistName, ignoreCase = true) == true
            }
            val closest = matches.minByOrNull { item ->
                abs((item["duration"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0) -
                    (track.durationSeconds ?: 0).toDouble())
            } ?: return DhunResult.Success(Lyrics.NotAvailable)
            val duration = track.durationSeconds
            if (duration != null && abs((closest["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: 0.0) - duration.toDouble()) > 10.0) return DhunResult.Success(Lyrics.NotAvailable)
            val synced = closest["syncedLyrics"]?.jsonPrimitive?.content
            val lines = synced?.let { LrcParser.parse(it, allowUnsyncedFallback = false) }.orEmpty()
            if (lines.isNotEmpty()) DhunResult.Success(Lyrics.Synced(lines))
            else closest["plainLyrics"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?.let { DhunResult.Success(Lyrics.Unsynced(it)) }
                ?: DhunResult.Success(Lyrics.NotAvailable)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            DhunResult.Failure(DhunError.Network())
        }
    }
}
