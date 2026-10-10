package dev.dhun.legal

/**
 * A minimal Markdown reader for the legal pages.
 *
 * Deliberately a hand-rolled subset rather than a dependency: the project ships
 * no runtime dependency it does not need, and the corpus is seven documents this
 * repository controls. What it understands is exactly what the Markdown files
 * under `legal` and the spliced-in `LICENSE`, `LICENSES`, `THIRD_PARTY.md` and
 * `SECURITY.md` actually contain — headings, paragraphs, bullet and numbered
 * lists, quotes,
 *
 * (Those globs are spelled out because Kotlin block comments NEST: a literal
 * slash-star inside this KDoc opens a comment that no star-slash in this file
 * closes, and the compiler then reports the whole file as one unclosed comment.)
 *
 * They contain headings, paragraphs, bullet and numbered lists, quotes,
 * GFM tables, thematic breaks and inline bold / code / strikethrough / links.
 *
 * It is pure (no Compose, no I/O) so [dev.dhun.legal.MarkdownParserTest] can pin
 * the parsing rules in CI without an emulator or a display.
 *
 * What it deliberately does **not** do:
 *  - 4-space indented code blocks. The licence texts use significant leading
 *    indentation for prose, so treating it as code would put most of Apache-2.0
 *    in a monospace box. Only fenced ``` blocks are code.
 *  - Nested block constructs, HTML, reference-style links, images, footnotes.
 *    None appear in the corpus; if one is added, the parser renders it as text
 *    rather than guessing.
 */

/** One run of styled text inside a block. */
sealed interface MarkdownSpan {
    val text: String

    data class Plain(
        override val text: String,
        val bold: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
    ) : MarkdownSpan

    /** Text that opens an external URL. Rendered with the platform link handler. */
    data class Link(override val text: String, val url: String) : MarkdownSpan
}

sealed interface MarkdownBlock {
    /** `level` is 1 for `#`. Anything past 3 is clamped by the caller's styles. */
    data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock

    data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock

    /** [indent] is the nesting depth, so a sub-bullet is visibly inset. */
    data class Bullet(val spans: List<MarkdownSpan>, val indent: Int = 0) : MarkdownBlock

    data class Numbered(val number: Int, val spans: List<MarkdownSpan>, val indent: Int = 0) : MarkdownBlock

    /**
     * A `>` quote. The legal pages use these for the DRAFT banners, so they get
     * their own visual treatment rather than merging into the body text.
     */
    data class Quote(val spans: List<MarkdownSpan>) : MarkdownBlock

    data class Code(val text: String) : MarkdownBlock

    /**
     * A GFM table. Held as parsed cells, not as text: the renderer turns it into
     * stacked label/value rows on a phone, because a four-column table on a
     * 360dp screen is either unreadable or horizontally scrollable, and the
     * brief rules out horizontal overflow from long URLs.
     */
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock

    /** A `---` rule. Also how the generator separates the spliced licence texts. */
    data object Rule : MarkdownBlock
}

object MarkdownParser {

    private val FENCE = Regex("^\\s*(```|~~~)\\s*([A-Za-z0-9]*)\\s*$")
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")
    private val RULE = Regex("^\\s*([-*_])\\s*(?:\\1\\s*){2,}$")
    private val TABLE_ROW = Regex("^\\s*\\|.*\\|\\s*$")
    private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")

    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]

            if (line.isBlank()) {
                index++
                continue
            }

            FENCE.matchEntire(line)?.let { fence ->
                val close = Regex("^\\s*" + Regex.escape(fence.groupValues[1]) + "\\s*$")
                val body = StringBuilder()
                index++
                while (index < lines.size && !close.matches(lines[index])) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[index])
                    index++
                }
                index++ // step over the closing fence (or the end of input)
                blocks += MarkdownBlock.Code(body.toString())
                return@let
            }
            if (FENCE.matches(line)) {
                // Unreachable given the block above closed the fence; kept so the
                // loop can never spin on a stray marker.
                index++
                continue
            }

            RULE.matchEntire(line)?.let { blocks += MarkdownBlock.Rule; index++ }
            if (RULE.matches(line)) continue

            HEADING.matchEntire(line)?.let { match ->
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    spans = inline(match.groupValues[2].trim()),
                )
                index++
            }
            if (HEADING.matches(line)) continue

            if (TABLE_ROW.matches(line) && index + 1 < lines.size && TABLE_SEPARATOR.matches(lines[index + 1])) {
                val headers = cells(line)
                val rows = mutableListOf<List<String>>()
                index += 2
                while (index < lines.size && TABLE_ROW.matches(lines[index])) {
                    rows += cells(lines[index])
                    index++
                }
                blocks += MarkdownBlock.Table(headers = headers, rows = rows)
                continue
            }

            QUOTE.matchEntire(line)?.let {
                val buffer = StringBuilder(it.groupValues[1])
                index++
                while (index < lines.size) {
                    val next = QUOTE.matchEntire(lines[index]) ?: break
                    buffer.append(' ').append(next.groupValues[1])
                    index++
                }
                blocks += MarkdownBlock.Quote(inline(buffer.toString().trim()))
            }
            if (QUOTE.matches(line)) continue

            BULLET.matchEntire(line)?.let { match ->
                blocks += MarkdownBlock.Bullet(
                    spans = inline(match.groupValues[2].trim()),
                    indent = indentDepth(match.groupValues[1]),
                )
                index++
            }
            if (BULLET.matches(line)) continue

            NUMBERED.matchEntire(line)?.let { match ->
                blocks += MarkdownBlock.Numbered(
                    number = match.groupValues[2].toIntOrNull() ?: 1,
                    spans = inline(match.groupValues[3].trim()),
                    indent = indentDepth(match.groupValues[1]),
                )
                index++
            }
            if (NUMBERED.matches(line)) continue

            // Paragraph: join the soft-wrapped lines that follow. The canonical
            // Markdown is hard-wrapped near column 80, so a sentence routinely
            // spans several lines and must be reflowed to read as one.
            val buffer = StringBuilder(line.trim())
            index++
            while (index < lines.size && lines[index].isNotBlank() && !startsNewBlock(lines[index])) {
                buffer.append(' ').append(lines[index].trim())
                index++
            }
            blocks += MarkdownBlock.Paragraph(inline(buffer.toString()))
        }
        return blocks
    }

    private fun startsNewBlock(line: String): Boolean =
        FENCE.matches(line) ||
            HEADING.matches(line) ||
            RULE.matches(line) ||
            BULLET.matches(line) ||
            NUMBERED.matches(line) ||
            QUOTE.matches(line) ||
            TABLE_ROW.matches(line)

    private fun indentDepth(spaces: String): Int = spaces.length / 2

    private fun cells(row: String): List<String> =
        row.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }

    // ------------------------------------------------------------------
    // Inline
    // ------------------------------------------------------------------

    private val LINK = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")
    private val BARE_URL = Regex("(https?://[^\\s<>\"'`]+)")

    /** Trailing sentence punctuation that is not part of the URL. */
    private const val URL_TRAILING = ".,;:!?"

    /**
     * Splits one line into styled runs. Handles `[text](url)`, bare URLs,
     * `**bold**`, `~~strike~~` and `` `code` ``; anything else is plain text.
     *
     * Markers are matched greedily on one line only — the corpus never styles
     * across a line break, and refusing to is better than mis-nesting.
     */
    fun inline(source: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>()
        var rest = source

        while (rest.isNotEmpty()) {
            val link = LINK.find(rest)
            val url = if (link == null) null else BARE_URL.find(rest)
            val next = listOfNotNull(link, url).minByOrNull { it.range.first }

            if (next == null) {
                spans += styled(rest)
                break
            }
            if (next.range.first > 0) {
                spans += styled(rest.substring(0, next.range.first))
            }
            if (next === link) {
                val url_ = link!!.groupValues[2]
                spans += MarkdownSpan.Link(text = link.groupValues[1], url = url_)
            } else {
                val raw = url!!.groupValues[1]
                val trimmed = raw.trimEnd(*URL_TRAILING.toCharArray())
                val dropped = raw.length - trimmed.length
                spans += MarkdownSpan.Link(text = trimmed, url = trimmed)
                if (dropped > 0) spans += MarkdownSpan.Plain(raw.takeLast(dropped))
            }
            rest = rest.substring(next.range.last + 1)
        }
        return spans.filter { it.text.isNotEmpty() }
    }

    /** Applies `**bold**`, `~~strike~~` and `` `code` `` to a marker-free fragment. */
    private fun styled(source: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>()
        var rest = source
        val pattern = Regex("(\\*\\*|~~|`)")

        while (rest.isNotEmpty()) {
            // Written as an explicit null check, not `?: run { ... break }`.
            // `break` inside an inline lambda needs Kotlin 2.2; this project is
            // on 2.1.20 and the compiler rejects it.
            val open = pattern.find(rest)
            if (open == null) {
                spans += MarkdownSpan.Plain(rest)
                break
            }
            val marker = open.groupValues[1]
            val close = rest.indexOf(marker, startIndex = open.range.last + 1)
            if (close < 0) {
                // Unbalanced marker: emit the rest literally rather than eating it.
                spans += MarkdownSpan.Plain(rest)
                break
            }
            if (open.range.first > 0) {
                spans += MarkdownSpan.Plain(rest.substring(0, open.range.first))
            }
            val body = rest.substring(open.range.last + 1, close)
            when (marker) {
                "**" -> spans += MarkdownSpan.Plain(body, bold = true)
                "~~" -> spans += MarkdownSpan.Plain(body, strike = true)
                else -> spans += MarkdownSpan.Plain(body, code = true)
            }
            rest = rest.substring(close + marker.length)
        }
        return spans.filter { it.text.isNotEmpty() }
    }
}
