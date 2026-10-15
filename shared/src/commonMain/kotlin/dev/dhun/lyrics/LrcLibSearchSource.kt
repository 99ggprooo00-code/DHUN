package dev.dhun.lyrics

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.plugins.timeout
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/** Existing documented LRCLIB /api/search adapter. Broad results are only shown on request;
 * automatic fallback retains conservative title/artist/duration matching.
 */
class LrcLibSearchSource(
    private val client: HttpClient = LrcLibSource.defaultClient(),
    private val baseUrl: String = "https://lrclib.net",
) : LyricsCandidateSource {
    override suspend fun fetch(track: Track): DhunResult<Lyrics> {
        if (track.title.isBlank() || track.artistName.isBlank()) return DhunResult.Success(Lyrics.NotAvailable)
        return when (val result = candidates(track, "${track.artistName} ${track.title}")) {
            is DhunResult.Failure -> result
            is DhunResult.Success -> {
                val closest = result.value.filter {
                    it.title.equals(track.title, ignoreCase = true) &&
                        it.artist.contains(track.artistName, ignoreCase = true) &&
                        (track.durationSeconds == null || it.durationSeconds?.let { duration ->
                            abs(duration - track.durationSeconds) <= 10.0
                        } == true)
                }.minByOrNull { abs((it.durationSeconds ?: 0.0) - (track.durationSeconds ?: 0)) }
                DhunResult.Success(closest?.lyrics ?: Lyrics.NotAvailable)
            }
        }
    }

    override suspend fun candidates(track: Track, query: String): DhunResult<List<LyricsMatch>> {
        if (query.isBlank()) return DhunResult.Success(emptyList())
        return try {
            val response = client.get("$baseUrl/api/search") {
                timeout { requestTimeoutMillis = 12_000 }
                parameter("q", query.trim().take(240))
            }
            if (!response.status.isSuccess()) return DhunResult.Failure(
                if (response.status.value == 429) DhunError.RateLimited() else DhunError.Network()
            )
            val body = response.bodyAsText()
            if (body.length > 2_000_000) return DhunResult.Failure(DhunError.Parse())
            val results = Json.parseToJsonElement(body) as? JsonArray
                ?: return DhunResult.Failure(DhunError.Parse())
            val matches = results.take(40).mapIndexedNotNull { index, element ->
                val item = element as? JsonObject ?: return@mapIndexedNotNull null
                fun text(key: String) = (item[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                val title = text("trackName") ?: return@mapIndexedNotNull null
                val artist = text("artistName") ?: return@mapIndexedNotNull null
                val lines = text("syncedLyrics")?.takeIf { it.length <= 256_000 }
                    ?.let { LrcParser.parse(it, false) }.orEmpty()
                val lyrics = if (lines.isNotEmpty()) Lyrics.Synced(lines) else {
                    val plain = text("plainLyrics")?.takeIf { it.isNotBlank() && it.length <= 256_000 }
                        ?: return@mapIndexedNotNull null
                    Lyrics.Unsynced(plain)
                }
                LyricsMatch(
                    text("id") ?: "result-$index", "lrclib_search", title, artist, lyrics,
                    language = text("language"), durationSeconds = text("duration")?.toDoubleOrNull(),
                )
            }
            DhunResult.Success(matches)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { DhunResult.Failure(DhunError.Network()) }
    }
}
