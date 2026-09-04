package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.DhunError
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.core.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LRCLIB synced-lyrics source (Phase 11).
 *
 * API: `GET https://lrclib.net/api/get?artist_name=&track_name=&album_name=&duration=`
 * Keyless, CORS-friendly. Returns JSON with `syncedLyrics` (LRC string) and `plainLyrics`.
 *
 * Matching strategy (mirrors the spike's approach):
 *  - Title + artist + album + duration (seconds) are sent; LRCLIB does the fuzzy match.
 *  - If `syncedLyrics` is present → parse via [LrcParser] → `Lyrics.Synced`.
 *  - Else if `plainLyrics` is present → `Lyrics.Unsynced`.
 *  - Else → `Lyrics.NotAvailable` (404 or empty body).
 *
 * This source is **metadata-dependent** (not videoId) — it works on the
 * `Track` title/artist/album/duration, so it can rescue tracks where
 * YTM lyrics are absent or gated.
 */
class LrcLibSource(
    private val httpClient: HttpClient = defaultClient(),
    private val baseUrl: String = "https://lrclib.net",
) : LyricsSource {

    override suspend fun fetch(track: Track): DhunResult<Lyrics> {
        // Quick bail: LRCLIB needs at least title+artist
        if (track.title.isBlank() || track.artistName.isBlank()) {
            return DhunResult.Success(Lyrics.NotAvailable)
        }
        return try {
            val response = httpClient.get("$baseUrl/api/get") {
                parameter("artist_name", track.artistName)
                parameter("track_name", track.title)
                track.albumName?.takeIf { it.isNotBlank() }?.let { parameter("album_name", it) }
                track.durationSeconds?.let { parameter("duration", it) }
            }
            if (response.status.value == 404) {
                return DhunResult.Success(Lyrics.NotAvailable)
            }
            if (!response.status.isSuccess()) {
                return DhunResult.Failure(mapHttpError(response.status.value))
            }
            val body = response.bodyAsText()
            if (body.isBlank()) return DhunResult.Success(Lyrics.NotAvailable)

            val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(body) as? JsonObject
                ?: return DhunResult.Success(Lyrics.NotAvailable)

            val synced = json["syncedLyrics"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            if (!synced.isNullOrBlank()) {
                val lines: List<LyricsLine> = LrcParser.parse(synced, allowUnsyncedFallback = false)
                if (lines.isNotEmpty()) {
                    return DhunResult.Success(Lyrics.Synced(lines))
                }
                // LRCLIB sent empty LRC after parsing → fall through to plain
            }

            val plain = json["plainLyrics"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            if (!plain.isNullOrBlank()) {
                return DhunResult.Success(Lyrics.Unsynced(plain))
            }

            DhunResult.Success(Lyrics.NotAvailable)
        } catch (e: Exception) {
            // Network/timeout → map to typed error but the repository will
            // still fall through to NotAvailable (lyrics are non-fatal)
            DhunResult.Failure(DhunError.Network)
        }
    }

    private fun mapHttpError(code: Int): DhunError = when (code) {
        429 -> DhunError.RateLimited()
        in 500..599 -> DhunError.Network
        else -> DhunError.Unknown("LRCLIB HTTP $code")
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 12_000
            }
        }
    }
}
