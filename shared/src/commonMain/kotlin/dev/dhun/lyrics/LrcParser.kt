package dev.dhun.lyrics

import dev.dhun.core.LyricsLine

/**
 * LRC parser — Phase 11.
 *
 * Tolerates:
 *  - `[mm:ss.xx]` , `[mm:ss.xxx]` , `[mm:ss]` (fraction optional)
 *  - Multiple timestamps per line: `[00:12.34][00:15.10]Repeat`
 *  - Empty lines and metadata tags like `[ti:...]` / `[ar:...]` / `[by:...]` (skipped)
 *  - Enhanced LRC word timing `<mm:ss.xx>` tolerated: e.g. `Hello <00:01.20>world`
 *    → the `<...>` tokens are stripped and the remaining line’s leading timestamp is used.
 *  - Unsynced fallback: lines without timestamps become `LyricsLine(null, text)` when
 *    `allowUnsyncedFallback` is true.
 *
 * Returns lines sorted by `startTimeMs`. Enhanced word timings are *not* expanded
 * into word-level lines in v1 — they are collapsed to a single line entry per
 * timestamped line (still scrolls in sync at line granularity).
 */
object LrcParser {

    // [mm:ss.xx] where xx is 2 or 3 digits, or [mm:ss]
    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimingRegex = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    /** Parse raw LRC text. @return sorted synced lines, or unsynced fallback. */
    fun parse(lrcText: String, allowUnsyncedFallback: Boolean = true): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val rawLines = lrcText.lines()
        for (raw in rawLines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            // Skip metadata tags with no numeric timestamp content, e.g. [ti:Song]
            // But [mm:ss.xx] lines will match timestampRegex
            val stamps = timestampRegex.findAll(line).toList()
            if (stamps.isEmpty()) {
                // Potential unsynced line (no timestamps) — keep if fallback allowed and not a metadata tag
                if (allowUnsyncedFallback && !isMetadataTag(line)) {
                    // Strip any word timings that slipped in without a leading stamp
                    val text = wordTimingRegex.replace(line, "").trim()
                    if (text.isNotEmpty()) lines.add(LyricsLine(null, text))
                }
                continue
            }
            // Text after the last timestamp in this line
            val last = stamps.last()
            val textStart = last.range.last + 1
            var text = line.substring(textStart).trim()
            // Strip enhanced word timings: <00:01.20>
            text = wordTimingRegex.replace(text, "").trim()
            // Some LRC stores timestamp-only separator lines — skip if text empty
            // but we still want to keep the timestamps? For that case we treat
            // it as blank lyric line with that time (preserves spacing).
            // Keep blank as “♪” placeholder will be handled by UI.
            // If text empty, keep as empty string — UI renders as “♪”
            // but we keep the timing.
            for (m in stamps) {
                val minutes = m.groupValues[1].toLongOrNull() ?: continue
                val seconds = m.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = m.groupValues[3]
                val millis = when {
                    fracRaw.isEmpty() -> 0L
                    fracRaw.length == 1 -> fracRaw.toLong() * 100L // [mm:ss.x] -> 100ms
                    fracRaw.length == 2 -> fracRaw.toLong() * 10L  // [mm:ss.xx] -> 10ms
                    else -> fracRaw.take(3).toLong()               // [mm:ss.xxx]
                }
                val totalMs = minutes * 60_000L + seconds * 1_000L + millis
                lines.add(LyricsLine(startTimeMs = totalMs, text = text))
            }
        }
        // Sort by time; stable for duplicate timestamps
        return lines.sortedBy { it.startTimeMs ?: Long.MAX_VALUE }
    }

    private fun isMetadataTag(line: String): Boolean {
        // e.g. [ti:...] [ar:...] [al:...] [by:...] [offset:...]
        // Those never contain a numeric timestamp with colon
        return line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") ||
            line.startsWith("[by:") || line.startsWith("[offset:") || line.startsWith("[length:")
    }

    /** Quick heuristic: does this text look like synced LRC? */
    fun isSynced(lrcText: String): Boolean = timestampRegex.containsMatchIn(lrcText)
}
