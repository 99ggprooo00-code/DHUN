package dev.dhun.lyrics

import dev.dhun.core.LyricsLine
import dev.dhun.core.LyricsWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Versioned cache payload: unlike LRC, retains independent word ends/gaps and
 * explicit line ends. Old line/enhanced LRC cache and saved choices remain readable.
 * No DB schema change. Never reinterpret broken structured data as lyric text.
 */
internal object SyncedLyricsCodec {
    const val MAX_CHARS = 512_000

    fun encode(lines: List<LyricsLine>): String {
        require(valid(lines)) { "Invalid or oversized timed lyrics" }
        val encoded = buildJsonObject {
            put("dhunSyncedLyrics", 1)
            put("lines", buildJsonArray {
                lines.forEach { line -> add(buildJsonObject {
                    put("start", line.startTimeMs)
                    put("text", line.text)
                    line.endTimeMs?.let { put("end", it) }
                    put("words", buildJsonArray {
                        line.words.forEach { word -> add(buildJsonObject {
                            put("from", word.startIndex); put("to", word.endIndex); put("start", word.startTimeMs)
                            word.endTimeMs?.let { put("end", it) }
                        }) }
                    })
                }) }
            })
        }.toString()
        require(encoded.length <= MAX_CHARS) { "Timed lyrics are too large to save" }
        return encoded
    }

    fun decode(payload: String): List<LyricsLine> = try {
        if (payload.length > MAX_CHARS) emptyList()
        else if (!payload.trimStart().startsWith("{")) LrcParser.parse(payload, false)
        else {
            fun JsonObject.value(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
            fun JsonObject.number(key: String) = value(key)?.toLongOrNull() ?: error("Missing number")
            fun JsonObject.end(): Long? = if ("end" in this) number("end") else null
            require(boundedLyricsJson(payload))
            val root = Json.parseToJsonElement(payload) as? JsonObject ?: error("Not an object")
            require(root.value("dhunSyncedLyrics") == "1")
            val array = root["lines"] as? JsonArray ?: error("Missing lines")
            require(array.size <= 2_000)
            val lines = array.map { item ->
                val line = item as? JsonObject ?: error("Invalid line")
                val words = line["words"] as? JsonArray ?: error("Missing words")
                require(words.size <= 10_000)
                LyricsLine(line.number("start"), line.value("text") ?: error("Missing text"), words.map {
                    val word = it as? JsonObject ?: error("Invalid word")
                    val from = word.number("from"); val to = word.number("to")
                    require(from in 0..Int.MAX_VALUE.toLong() && to in 0..Int.MAX_VALUE.toLong())
                    LyricsWord(from.toInt(), to.toInt(), word.number("start"), word.end())
                }, line.end())
            }
            require(valid(lines))
            lines
        }
    } catch (_: Exception) { emptyList() }

    private fun valid(lines: List<LyricsLine>): Boolean {
        if (lines.isEmpty() || lines.size > 2_000 || lines.sumOf { it.words.size } > 10_000) return false
        return lines.all { line ->
            val start = line.startTimeMs
            start != null && start >= 0 && line.text.length <= 256_000 &&
                (line.endTimeMs == null || line.endTimeMs > start) &&
                line.words.all { word ->
                    word.startIndex >= 0 && word.endIndex <= line.text.length && word.startIndex < word.endIndex &&
                        word.startTimeMs >= start && (word.endTimeMs == null || word.endTimeMs >= word.startTimeMs) &&
                        (line.endTimeMs == null || word.startTimeMs < line.endTimeMs && (word.endTimeMs == null || word.endTimeMs <= line.endTimeMs))
                } && line.words.zipWithNext().all { (a, b) -> a.endIndex <= b.startIndex && a.startTimeMs <= b.startTimeMs }
        }
    }
}

/** Bound structured parsing depth before the JSON library allocates a nested tree.
 * Braces inside lyric strings (including escaped quotes) aren't nesting markers.
 */
internal fun boundedLyricsJson(value: String): Boolean {
    var depth = 0
    var quoted = false
    var escaped = false
    for (char in value) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '{', '[' -> if (++depth > 16) return false
            '}', ']' -> if (--depth < 0) return false
        }
    }
    return depth == 0 && !quoted
}
