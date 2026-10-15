package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Provider record metadata, not a claim that a fuzzy result is the correct song. */
data class LyricsMatch(
    val id: String,
    val provider: String,
    val title: String,
    val artist: String,
    val lyrics: Lyrics,
    val language: String? = null,
    val durationSeconds: Double? = null,
    val metadataVerified: Boolean = true,
) {
    val synced: Boolean get() = lyrics is Lyrics.Synced
    val preview: String get() = when (val value = lyrics) {
        is Lyrics.Synced -> value.lines.take(3).joinToString(" / ") { it.text }
        is Lyrics.Unsynced -> value.text.lineSequence().take(3).joinToString(" / ")
        Lyrics.NotAvailable -> ""
    }.take(240)
}

interface LyricsCandidateSource : LyricsSource {
    suspend fun candidates(track: Track, query: String): DhunResult<List<LyricsMatch>>
}

data class LyricsMatchSearch(val matches: List<LyricsMatch>, val failedProviders: List<String> = emptyList())

/** Versioned, bounded per-song choice, independent of the evictable lyrics cache.
 * Version 2 retains independent TTML word/line ends; version 1 LRC remains readable.
 */
internal object LyricsMatchCodec {
    const val MAX_CHARS = 512_000
    fun encode(match: LyricsMatch): String {
        require(match.lyrics != Lyrics.NotAvailable)
        require(match.id.length <= 512 && match.provider.length <= 64 &&
            match.title.length <= 1_024 && match.artist.length <= 1_024 && (match.language?.length ?: 0) <= 64)
        val content = when (val lyrics = match.lyrics) {
            is Lyrics.Synced -> SyncedLyricsCodec.encode(lyrics.lines)
            is Lyrics.Unsynced -> lyrics.text.also { require(it.length <= MAX_CHARS) }
            Lyrics.NotAvailable -> error("Unavailable match")
        }
        val encoded = buildJsonObject {
            put("version", 2)
            put("id", match.id); put("provider", match.provider)
            put("title", match.title); put("artist", match.artist)
            put("synced", match.synced); put("verified", match.metadataVerified)
            match.language?.let { put("language", it) }
            match.durationSeconds?.let { put("duration", it) }
            put("content", content)
        }.toString()
        require(encoded.length <= MAX_CHARS) { "Lyrics are too large to save" }
        return encoded
    }

    fun decode(encoded: String?): LyricsMatch? = runCatching {
        if (encoded == null || encoded.length > MAX_CHARS || !boundedLyricsJson(encoded)) return null
        val obj = Json.parseToJsonElement(encoded) as? JsonObject ?: return null
        fun value(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
        if (value("version") !in setOf("1", "2")) return null
        val content = value("content") ?: return null
        val lyrics = if (value("synced") == "true") {
            val lines = if (value("version") == "1") LrcParser.parse(content, false) else SyncedLyricsCodec.decode(content)
            if (lines.isEmpty()) return null
            Lyrics.Synced(lines)
        } else {
            if (content.isBlank()) return null
            Lyrics.Unsynced(content)
        }
        LyricsMatch(
            value("id") ?: return null, value("provider") ?: return null,
            value("title").orEmpty(), value("artist").orEmpty(), lyrics,
            value("language"), value("duration")?.toDoubleOrNull(), value("verified") != "false",
        )
    }.getOrNull()
}
