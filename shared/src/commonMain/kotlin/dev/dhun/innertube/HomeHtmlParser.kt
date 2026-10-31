package dev.dhun.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Extract the real Home response embedded in music.youtube.com's HTML.
 *
 * This is a protocol fallback for a rejected `/browse` request, not fabricated
 * content. Only a JSON object with a recognized Home `contents` envelope is
 * returned. YouTube has shipped both a direct `ytInitialData` object and a
 * single-quoted, hex-escaped `data: '…'` script value; accept either without
 * evaluating JavaScript. The scans respect strings and escapes, so nested
 * shelves and a `}` inside a title cannot truncate the response.
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
            homeCandidate(html.substring(start, end + 1))?.let { return it }
        }
    }

    val dataMarker = Regex("""\bdata\s*:\s*'""")
    for (match in dataMarker.findAll(html)) {
        val start = match.range.last + 1
        val end = jsSingleQuotedStringEnd(html, start) ?: continue
        val decoded = decodeJavaScriptString(html.substring(start, end))
        homeCandidate(decoded)?.let { return it }
    }
    return null
}

/** The complete WEB_REMIX request context embedded by the music homepage. */
internal fun parseInnertubeContext(html: String): JsonObject? {
    if (html.isEmpty()) return null
    val marker = Regex("\"INNERTUBE_CONTEXT\"\\s*:\\s*")
    for (match in marker.findAll(html)) {
        val start = match.range.last + 1
        if (start >= html.length || html[start] != '{') continue
        val end = jsonObjectEnd(html, start) ?: continue
        val candidate = runCatching {
            Json.parseToJsonElement(html.substring(start, end + 1)) as? JsonObject
        }.getOrNull() ?: continue
        if (candidate.obj("client")?.str("clientName") == "WEB_REMIX") return candidate
    }
    return null
}

private fun homeCandidate(source: String): JsonObject? {
    val candidate = runCatching {
        Json.parseToJsonElement(source) as? JsonObject
    }.getOrNull() ?: return null
    val contents = candidate.obj("contents")
    return candidate.takeIf {
        contents?.obj("singleColumnBrowseResultsRenderer") != null ||
            contents?.obj("twoColumnBrowseResultsRenderer") != null ||
            contents?.obj("sectionListRenderer") != null
    }
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

/** Closing quote for a JavaScript single-quoted string, respecting backslash parity. */
private fun jsSingleQuotedStringEnd(source: String, start: Int): Int? {
    var escaped = false
    for (index in start until source.length) {
        val char = source[index]
        if (escaped) {
            escaped = false
        } else if (char == '\\') {
            escaped = true
        } else if (char == '\'') {
            return index
        }
    }
    return null
}

/** Decode only string escapes; this parser never executes page script. */
private fun decodeJavaScriptString(source: String): String = buildString(source.length) {
    var index = 0
    while (index < source.length) {
        val char = source[index]
        if (char != '\\' || index + 1 >= source.length) {
            append(char)
            index++
            continue
        }
        when (val escape = source[index + 1]) {
            'x' -> {
                val value = if (index + 3 < source.length) {
                    source.substring(index + 2, index + 4).toIntOrNull(16)
                } else {
                    null
                }
                if (value == null) {
                    append('\\').append('x')
                    index += 2
                } else {
                    append(value.toChar())
                    index += 4
                }
            }
            'u' -> {
                val value = if (index + 5 < source.length) {
                    source.substring(index + 2, index + 6).toIntOrNull(16)
                } else {
                    null
                }
                if (value == null) {
                    append('\\').append('u')
                    index += 2
                } else {
                    append(value.toChar())
                    index += 6
                }
            }
            '\\' -> append('\\').also { index += 2 }
            '/' -> append('/').also { index += 2 }
            '\'' -> append('\'').also { index += 2 }
            '"' -> append('"').also { index += 2 }
            else -> {
                // Preserve JSON escapes such as \n instead of inserting a raw
                // control character into the JSON document.
                append('\\').append(escape)
                index += 2
            }
        }
    }
}
