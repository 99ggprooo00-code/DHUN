package dev.dhun.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Extract the real Home response embedded in music.youtube.com's HTML.
 *
 * This is a protocol fallback for a rejected `/browse` request, not fabricated
 * content. Only a JSON object with a recognized Home `contents` envelope is
 * returned. The balanced scan respects JSON strings and escapes; a regex that
 * stops at the first `}` cannot safely parse nested shelves.
 */
internal fun parseHomeInitialData(html: String): JsonObject? {
    if (html.isEmpty()) return null
    val markers = listOf(
        Regex("""(?:\bvar\s+)?\bytInitialData\s*=\s*"""),
        Regex("""\bwindow\s*\[\s*[\"']ytInitialData[\"']\s*\]\s*=\s*"""),
    )
    for (marker in markers) {
        for (match in marker.findAll(html)) {
            val start = match.range.last + 1
            if (start >= html.length || html[start] != '{') continue
            val end = jsonObjectEnd(html, start) ?: continue
            val candidate = runCatching {
                Json.parseToJsonElement(html.substring(start, end + 1)) as? JsonObject
            }.getOrNull() ?: continue
            val contents = candidate.obj("contents")
            if (contents?.obj("singleColumnBrowseResultsRenderer") != null ||
                contents?.obj("twoColumnBrowseResultsRenderer") != null ||
                contents?.obj("sectionListRenderer") != null
            ) return candidate
        }
    }
    return null
}

/** End offset of the JSON object starting at [start], or null for an incomplete value. */
private fun jsonObjectEnd(source: String, start: Int): Int? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until source.length) {
        val char = source[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return index
                if (depth < 0) return null
            }
        }
    }
    return null
}
