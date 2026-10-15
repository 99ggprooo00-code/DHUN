package dev.dhun.lyrics

import dev.dhun.core.LyricsLine
import dev.dhun.core.LyricsWord

/** Line and enhanced LRC. Word markers precede their text; a final marker is an end time.
 * Invalid/non-monotonic word timing degrades to line timing, never hides the original text.
 * Repeated line timestamps shift word timing relative to the first timestamp.
 */
object LrcParser {
    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimingRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val metadataRegex = Regex("""^\[(?:ti|ar|al|by|offset|length):.*]$""", RegexOption.IGNORE_CASE)

    private fun time(match: MatchResult): Long? {
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        if (seconds > 59) return null
        val fraction = match.groupValues[3].padEnd(3, '0').toLongOrNull() ?: 0L
        return match.groupValues[1].toLong() * 60_000 + seconds * 1_000 + fraction
    }

    fun parse(lrcText: String, allowUnsyncedFallback: Boolean = true): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        for (raw in lrcText.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || metadataRegex.matches(line)) continue
            val stamps = timestampRegex.findAll(line).toList()
            if (stamps.isEmpty()) {
                if (allowUnsyncedFallback) {
                    val text = wordTimingRegex.replace(line, "").trim()
                    if (text.isNotEmpty()) lines.add(LyricsLine(null, text))
                }
                continue
            }
            val times = stamps.mapNotNull(::time)
            if (times.isEmpty()) continue
            val body = line.substring(stamps.last().range.last + 1)
            val (text, words) = parseWords(body, times.first())
            for (start in times) {
                val shift = start - times.first()
                lines.add(LyricsLine(start, text, words.map {
                    it.copy(startTimeMs = it.startTimeMs + shift, endTimeMs = it.endTimeMs?.plus(shift))
                }))
            }
        }
        return lines.sortedBy { it.startTimeMs ?: Long.MAX_VALUE }
    }

    private fun parseWords(body: String, lineStart: Long): Pair<String, List<LyricsWord>> {
        val markers = wordTimingRegex.findAll(body).toList()
        if (markers.isEmpty()) return body.trim() to emptyList()
        val clean = wordTimingRegex.replace(body, "")
        val trimStart = clean.length - clean.trimStart().length
        val text = clean.trim()
        val positions = mutableListOf<Pair<Int, Long>>()
        var removed = 0
        for (marker in markers) {
            val start = time(marker) ?: return text to emptyList()
            positions.add((marker.range.first - removed - trimStart).coerceIn(0, text.length) to start)
            removed += marker.value.length
        }
        if (positions.any { it.second < lineStart } || positions.zipWithNext().any { (a, b) -> b.second < a.second }) {
            return text to emptyList()
        }
        val words = positions.mapIndexedNotNull { index, (offset, start) ->
            val next = positions.getOrNull(index + 1)
            val end = next?.first ?: text.length
            if (end <= offset) null else LyricsWord(offset, end, start, next?.second)
        }
        return text to words
    }

    /** Cache round-trip retains milliseconds, whitespace and enhanced timing. */
    fun serialize(lines: List<LyricsLine>): String = lines.joinToString("\n") { line ->
        buildString {
            line.startTimeMs?.let { append('['); append(formatTime(it)); append(']') }
            val words = line.words.filter { it.startIndex >= 0 && it.endIndex <= line.text.length && it.endIndex > it.startIndex }
            if (words.isEmpty()) append(line.text) else {
                var cursor = 0
                for (word in words) {
                    if (word.startIndex < cursor) continue
                    append(line.text.substring(cursor, word.startIndex))
                    append('<'); append(formatTime(word.startTimeMs)); append('>')
                    append(line.text.substring(word.startIndex, word.endIndex))
                    cursor = word.endIndex
                }
                append(line.text.substring(cursor))
                words.last().endTimeMs?.let { append('<'); append(formatTime(it)); append('>') }
            }
        }
    }

    private fun formatTime(ms: Long): String =
        "${(ms / 60_000).toString().padStart(2, '0')}:${(ms % 60_000 / 1_000).toString().padStart(2, '0')}.${(ms % 1_000).toString().padStart(3, '0')}"

    fun isSynced(lrcText: String): Boolean = timestampRegex.findAll(lrcText).any { time(it) != null }
}
