package dev.dhun.lyrics

import dev.dhun.core.LyricsLine
import dev.dhun.core.LyricsWord
import kotlinx.coroutines.CancellationException

/** A deliberately bounded TTML media-time subset, not a general subtitle/layout engine.
 * No DTDs, external resources, frame/tick clocks or sequential/repeated containers.
 * Unsupported timing fails closed, never becoming fabricated karaoke or visible XML.
 */
object TtmlParser {
    const val MAX_INPUT_CHARS = 256_000
    private const val MAX_TIME = 86_400_000L
    private const val NS = "http://www.w3.org/ns/ttml"
    private const val XML = "http://www.w3.org/XML/1998/namespace"
    private const val PARAMETER = "http://www.w3.org/ns/ttml#parameter"
    private val clock = Regex("(\\d{2,}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?")
    private val offset = Regex("(\\d+(?:\\.\\d{1,9})?|\\.\\d{1,9})(ms|s|m|h)")

    fun parse(input: String, checkpoint: () -> Unit = {}): List<LyricsLine> {
        checkpoint()
        if (input.length > MAX_INPUT_CHARS || input.isBlank()) return emptyList()
        return try {
            val stack = mutableListOf<Node>()
            var root: Node? = null
            var count = 0
            var textSize = 0
            readLyricsXml(input.removePrefix("\uFEFF"), object : LyricsXmlSink {
                override fun start(namespace: String, name: String, attributes: List<LyricsXmlAttribute>) {
                    checkpoint()
                    require(++count <= 10_000 && stack.size < 48 && attributes.size <= 32)
                    val node = Node(namespace, name, attributes)
                    if (stack.isEmpty()) { require(root == null); root = node }
                    else stack.last().children.add(node)
                    stack.add(node)
                }
                override fun text(value: String) {
                    checkpoint()
                    textSize += value.length
                    require(textSize <= MAX_INPUT_CHARS)
                    if (stack.isNotEmpty()) stack.last().children.add(value)
                    else require(value.isBlank())
                }
                override fun end() { checkpoint(); require(stack.isNotEmpty()); stack.removeAt(stack.lastIndex) }
            })
            val document = requireNotNull(root)
            require(stack.isEmpty() && document.name == "tt" && document.namespace == NS)
            require(document.attr("timeBase", PARAMETER).let { it == null || it == "media" })
            val lines = mutableListOf<LyricsLine>()
            fun walk(node: Node, parent: Interval, preserve: Boolean) {
                checkpoint()
                if (node.namespace != NS || node.name !in setOf("tt", "body", "div", "p")) return
                val interval = interval(node, parent)
                val keepSpace = space(node, preserve)
                if (node.name == "p") {
                    require(lines.size < 2_000)
                    line(node, interval, keepSpace, checkpoint)?.let(lines::add)
                } else node.children.filterIsInstance<Node>().forEach { walk(it, interval, keepSpace) }
            }
            walk(document, Interval(0, null, false), false)
            lines.sortedBy { it.startTimeMs }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
    }

    private data class Node(val namespace: String, val name: String, val attributes: List<LyricsXmlAttribute>) {
        val children = mutableListOf<Any>()
        fun attr(name: String, namespace: String = "") = attributes.firstOrNull { it.name == name && it.namespace == namespace }?.value
    }
    private data class Interval(val start: Long, val end: Long?, val explicit: Boolean)
    private data class Segment(val start: Int, val end: Int, val time: Interval)

    private fun space(node: Node, inherited: Boolean) = when (node.attr("space", XML)) {
        null -> inherited
        "preserve" -> true
        "default" -> false
        else -> error("Unsupported whitespace mode")
    }

    private fun add(a: Long, b: Long): Long = (a + b).also { require(it in 0..MAX_TIME) }
    private fun interval(node: Node, parent: Interval): Interval {
        require(node.attr("timeContainer").let { it == null || it == "par" })
        require(node.attr("repeatCount") == null && node.attr("repeatDur") == null)
        val begin = node.attr("begin")?.let(::time)
        val end = node.attr("end")?.let(::time)
        val duration = node.attr("dur")?.let(::time)
        val start = add(parent.start, begin ?: 0)
        val ends = listOfNotNull(parent.end, end?.let { add(parent.start, it) }, duration?.let { add(start, it) })
        val until = ends.minOrNull()
        require(until == null || until > start)
        return Interval(start, until, parent.explicit || begin != null || end != null || duration != null)
    }

    private fun line(node: Node, timing: Interval, preserve: Boolean, checkpoint: () -> Unit): LyricsLine? {
        val text = StringBuilder()
        val segments = mutableListOf<Segment>()
        fun append(value: String, keepSpace: Boolean) {
            for (char in value) {
                if (!keepSpace && char in " \t\r\n") {
                    if (text.isNotEmpty() && text.last() != ' ' && text.last() != '\n') text.append(' ')
                } else text.append(char)
            }
            require(text.length <= 16_000)
        }
        fun visit(current: Node, inherited: Interval, keepSpace: Boolean) {
            checkpoint()
            for (child in current.children) when (child) {
                is String -> append(child, keepSpace)
                is Node -> {
                    require(child.namespace == NS && child.name in setOf("span", "br"))
                    val times = interval(child, inherited)
                    val childSpace = space(child, keepSpace)
                    if (child.name == "br") {
                        text.append('\n')
                    } else {
                        val start = text.length
                        val before = segments.size
                        visit(child, times, childSpace)
                        // Leaf timing spans only: no overlapping nested word ranges.
                        if (segments.size == before && child.attr("begin") != null && text.length > start) {
                            segments.add(Segment(start, text.length, times))
                        }
                    }
                }
            }
        }
        visit(node, timing, preserve)
        val raw = text.toString()
        val leading = if (preserve) 0 else raw.length - raw.trimStart(' ', '\n', '\r', '\t').length
        val original = if (preserve) raw else raw.trim(' ', '\n', '\r', '\t')
        if (original.isBlank()) return null
        val words = segments.mapNotNull {
            val start = (it.start - leading).coerceIn(0, original.length)
            val end = (it.end - leading).coerceIn(0, original.length)
            if (end <= start) null else LyricsWord(start, end, it.time.start, it.time.end)
        }
        // Monotonic text/time mappings are required for word seeking. The original
        // paragraph remains usable as line-timed lyrics if word timings conflict.
        val ordered = words.zipWithNext().all { (a, b) -> a.endIndex <= b.startIndex && a.startTimeMs <= b.startTimeMs }
        val start = if (timing.explicit) timing.start else words.minOfOrNull { it.startTimeMs } ?: return null
        return LyricsLine(start, original, if (ordered) words else emptyList(), timing.end)
    }

    /** Decimal arithmetic avoids millisecond loss from binary floating-point rounding. */
    internal fun time(value: String): Long {
        require(value.length <= 64)
        clock.matchEntire(value)?.let {
            val hours = it.groupValues[1].toLong()
            val minutes = it.groupValues[2].toLong()
            val seconds = it.groupValues[3].toLong()
            require(hours <= 24 && minutes <= 59 && seconds <= 59)
            return (hours * 3_600_000 + minutes * 60_000 + seconds * 1_000 +
                it.groupValues[4].padEnd(3, '0').take(3).toLong()).also { ms -> require(ms <= MAX_TIME) }
        }
        val match = requireNotNull(offset.matchEntire(value))
        val scale = when (match.groupValues[2]) { "h" -> 3_600_000L; "m" -> 60_000L; "s" -> 1_000L; else -> 1L }
        val pieces = match.groupValues[1].split('.')
        val whole = pieces[0].ifEmpty { "0" }.toLong()
        require(whole <= MAX_TIME / scale)
        val fraction = pieces.getOrNull(1).orEmpty()
        var denominator = 1L
        repeat(fraction.length) { denominator *= 10 }
        return (whole * scale + (fraction.toLongOrNull() ?: 0) * scale / denominator).also { require(it <= MAX_TIME) }
    }
}

internal data class LyricsXmlAttribute(val namespace: String, val name: String, val value: String)
internal interface LyricsXmlSink {
    fun start(namespace: String, name: String, attributes: List<LyricsXmlAttribute>)
    fun text(value: String)
    fun end()
}

/** Platform XML tokenizer only. No platform rendering, HTTP, entity or file loading. */
internal expect fun readLyricsXml(input: String, sink: LyricsXmlSink)
