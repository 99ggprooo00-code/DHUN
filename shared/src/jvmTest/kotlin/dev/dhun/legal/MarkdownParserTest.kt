package dev.dhun.legal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the Markdown subset the legal pages render from.
 *
 * These are the rules that decide whether a 48 KB licence text reads as prose or
 * as a wall of code, whether the DRAFT banner is a banner or a paragraph, and
 * whether a link in a policy opens the right URL. Runs in CI via
 * `:shared:jvmTest`; no emulator, no display.
 */
class MarkdownParserTest {

    private fun blocks(markdown: String) = MarkdownParser.parse(markdown)

    private fun first(markdown: String): MarkdownBlock = blocks(markdown).first()

    private fun MarkdownBlock.text(): String = when (this) {
        is MarkdownBlock.Heading -> spans.joinToString("") { it.text }
        is MarkdownBlock.Paragraph -> spans.joinToString("") { it.text }
        is MarkdownBlock.Quote -> spans.joinToString("") { it.text }
        is MarkdownBlock.Bullet -> spans.joinToString("") { it.text }
        is MarkdownBlock.Numbered -> spans.joinToString("") { it.text }
        is MarkdownBlock.Code -> text
        else -> error("not a text block")
    }

    // ------------------------------------------------------------- structure

    @Test
    fun blankInputParsesToNothing() {
        assertEquals(emptyList(), blocks(""))
        assertEquals(emptyList(), blocks("\n\n   \n"))
    }

    @Test
    fun headingLevelsComeFromTheHashCount() {
        assertEquals(1, (first("# Top") as MarkdownBlock.Heading).level)
        assertEquals(3, (first("### Sub") as MarkdownBlock.Heading).level)
        assertEquals("Top", first("# Top").text())
    }

    @Test
    fun softWrappedLinesReflowIntoOneParagraph() {
        // The canonical legal/*.md is hard-wrapped near column 80, so this is the
        // single most common shape in the corpus.
        val block = first(
            """
            The GNU General Public License is a free, copyleft license for
            software and other kinds of works.
            """.trimIndent(),
        )
        assertIs<MarkdownBlock.Paragraph>(block)
        assertEquals(
            "The GNU General Public License is a free, copyleft license for software and other kinds of works.",
            block.text(),
        )
    }

    @Test
    fun aBlankLineSeparatesTwoParagraphs() {
        val parsed = blocks("one\n\ntwo")
        assertEquals(2, parsed.size)
        assertEquals("one", parsed[0].text())
        assertEquals("two", parsed[1].text())
    }

    @Test
    fun quoteLinesJoinIntoOneBanner() {
        val quote = first(
            """
            > **DRAFT — not yet in force.** This page describes what the code in
            > this build actually does.
            """.trimIndent(),
        )
        assertIs<MarkdownBlock.Quote>(quote)
        assertTrue("DRAFT — not yet in force." in quote.text())
        assertTrue("this build actually does." in quote.text())
    }

    @Test
    fun bulletsCarryTheirNestingDepth() {
        val parsed = blocks("- top\n  - nested")
        assertEquals(0, (parsed[0] as MarkdownBlock.Bullet).indent)
        assertEquals(1, (parsed[1] as MarkdownBlock.Bullet).indent)
    }

    @Test
    fun numberedListItemsKeepTheirOwnNumber() {
        val parsed = blocks("1. first\n2. second")
        assertEquals(1, (parsed[0] as MarkdownBlock.Numbered).number)
        assertEquals(2, (parsed[1] as MarkdownBlock.Numbered).number)
    }

    @Test
    fun aRuleBecomesARuleNotAParagraph() {
        assertIs<MarkdownBlock.Rule>(first("---"))
        assertIs<MarkdownBlock.Rule>(first("***"))
    }

    @Test
    fun fencedBlocksStayVerbatimAndDoNotReflow() {
        val parsed = blocks("```\nline one\nline two\n```")
        assertEquals(1, parsed.size)
        assertEquals("line one\nline two", (parsed[0] as MarkdownBlock.Code).text)
    }

    // ---------------------------------------------------------------- tables

    @Test
    fun aGfmTableParsesHeadersAndRows() {
        val table = first(
            """
            | Recipient | What is sent |
            | --- | --- |
            | YouTube | Search terms |
            | LRCLIB | Title and artist |
            """.trimIndent(),
        ) as MarkdownBlock.Table
        assertEquals(listOf("Recipient", "What is sent"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("YouTube", "Search terms"), table.rows[0])
        assertEquals(listOf("LRCLIB", "Title and artist"), table.rows[1])
    }

    @Test
    fun aTableWithoutASeparatorRowIsNotATable() {
        // `|` inside ordinary prose must survive as prose, not become a table.
        val parsed = first("| not | a table |")
        assertIs<MarkdownBlock.Paragraph>(parsed)
    }

    @Test
    fun anEmptyHeaderTableStillParses() {
        // about.md uses `| | |` so the version rows read as a two-column grid.
        val table = first("| | |\n| --- | --- |\n| Version | 1.0 |") as MarkdownBlock.Table
        assertEquals(listOf("", ""), table.headers)
        assertEquals(listOf("Version", "1.0"), table.rows.single())
    }

    // ---------------------------------------------------------------- inline

    @Test
    fun markdownLinkKeepsItsLabelAndUrl() {
        val spans = MarkdownParser.inline("see [THIRD_PARTY.md](https://example.test/a.md) now")
        val link = spans.filterIsInstance<MarkdownSpan.Link>().single()
        assertEquals("THIRD_PARTY.md", link.text)
        assertEquals("https://example.test/a.md", link.url)
    }

    @Test
    fun aBareUrlBecomesALink() {
        val link = MarkdownParser.inline("report at https://lrclib.net/api/get today")
            .filterIsInstance<MarkdownSpan.Link>()
            .single()
        assertEquals("https://lrclib.net/api/get", link.url)
    }

    @Test
    fun trailingSentencePunctuationIsNotSwallowedIntoAUrl() {
        val spans = MarkdownParser.inline("see https://example.test/page.")
        val link = spans.filterIsInstance<MarkdownSpan.Link>().single()
        assertEquals("https://example.test/page", link.url)
        // The full stop is still shown — it is the reader's punctuation.
        assertEquals(".", spans.filterIsInstance<MarkdownSpan.Plain>().last().text)
    }

    @Test
    fun anAngleBracketedUrlIsLinkedWithoutTheBrackets() {
        val link = MarkdownParser.inline("<https://fsf.org/>")
            .filterIsInstance<MarkdownSpan.Link>()
            .single()
        assertEquals("https://fsf.org/", link.url)
    }

    @Test
    fun boldAndCodeAndStrikeAreCarriedAsFlags() {
        val spans = MarkdownParser.inline("**bold** and `code` and ~~gone~~")
        val plain = spans.filterIsInstance<MarkdownSpan.Plain>()
        assertTrue(plain.any { it.text == "bold" && it.bold })
        assertTrue(plain.any { it.text == "code" && it.code })
        assertTrue(plain.any { it.text == "gone" && it.strike })
    }

    @Test
    fun anUnbalancedMarkerIsRenderedLiterally() {
        // A stray backtick in a policy must not swallow the rest of the sentence.
        val spans = MarkdownParser.inline("a ` b and the rest")
        assertEquals("a ` b and the rest", spans.joinToString("") { it.text })
        assertTrue(spans.none { (it as? MarkdownSpan.Plain)?.code == true })
    }

    @Test
    fun inlineRoundTripsAllTheText() {
        val source = "**Bold** with a [link](https://example.test) and `code`."
        assertEquals(
            "Bold with a link and code.",
            MarkdownParser.inline(source).joinToString("") { it.text },
        )
    }

    // ------------------------------------------------------- corpus regression

    @Test
    fun theShippedLicenceTextIsProseNotCode() {
        // Apache-2.0 indents its definitions by three spaces. If 4-space code
        // blocks were enabled, most of that licence would render as a monospace
        // slab; this is the regression that keeps it readable.
        val licence = LegalContent.byId("open-source-licenses")!!.markdown
        val parsed = blocks(licence)
        val codeBlocks = parsed.filterIsInstance<MarkdownBlock.Code>()
        assertTrue(
            codeBlocks.isEmpty(),
            "the licence page produced ${codeBlocks.size} code block(s); indented prose must stay prose",
        )
        assertTrue(parsed.filterIsInstance<MarkdownBlock.Paragraph>().size > 50)
    }

    @Test
    fun everyShippedDocumentParsesToRenderableBlocks() {
        for (doc in LegalContent.all) {
            val parsed = blocks(doc.markdown)
            assertTrue(parsed.isNotEmpty(), "${doc.id} parsed to nothing")
            assertTrue(parsed.size > 5, "${doc.id} parsed to only ${parsed.size} block(s)")
            for (block in parsed) {
                when (block) {
                    is MarkdownBlock.Heading -> assertTrue(block.spans.isNotEmpty(), "${doc.id}: empty heading")
                    is MarkdownBlock.Paragraph -> assertTrue(block.spans.isNotEmpty(), "${doc.id}: empty paragraph")
                    is MarkdownBlock.Quote -> assertTrue(block.spans.isNotEmpty(), "${doc.id}: empty quote")
                    is MarkdownBlock.Bullet -> assertTrue(block.spans.isNotEmpty(), "${doc.id}: empty bullet")
                    is MarkdownBlock.Numbered -> assertTrue(block.spans.isNotEmpty(), "${doc.id}: empty item")
                    is MarkdownBlock.Code -> assertTrue(block.text.isNotBlank(), "${doc.id}: empty code block")
                    is MarkdownBlock.Table -> assertTrue(block.rows.isNotEmpty(), "${doc.id}: empty table")
                    MarkdownBlock.Rule -> Unit
                }
            }
        }
    }

    @Test
    fun everyDraftPageOpensWithItsDraftBannerAsAQuote() {
        val drafts = LegalContent.all.filter { it.isDraft }
        assertTrue(drafts.isNotEmpty(), "no draft page — the check would be vacuous")
        for (doc in drafts) {
            val opening = blocks(doc.markdown).first()
            assertIs<MarkdownBlock.Quote>(opening, "${doc.id} does not open with a quote banner")
            assertTrue("DRAFT" in opening.text(), "${doc.id}'s banner does not say DRAFT")
        }
    }

    @Test
    fun theLicenceSectionsActuallySurviveParsing() {
        // The point of the Open-Source Licenses page is the full text, so pin the
        // landmarks at both ends of each licence rather than trusting a length.
        val parsed = blocks(LegalContent.byId("open-source-licenses")!!.markdown)
        val words = parsed.joinToString(" ") { block ->
            when (block) {
                is MarkdownBlock.Heading -> block.spans.joinToString("") { it.text }
                is MarkdownBlock.Paragraph -> block.spans.joinToString("") { it.text }
                is MarkdownBlock.Quote -> block.spans.joinToString("") { it.text }
                else -> ""
            }
        }
        for (landmark in listOf(
            "GNU GENERAL PUBLIC LICENSE",
            "END OF TERMS AND CONDITIONS",
            "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION",
            "APPENDIX: How to apply the Apache License",
        )) {
            assertTrue(landmark in words, "licence page lost: $landmark")
        }
    }

    @Test
    fun theSupportPageDataControlTableIsIntact() {
        val tables = blocks(LegalContent.byId("support")!!.markdown)
            .filterIsInstance<MarkdownBlock.Table>()
        val controls = tables.first { "Control" in it.headers }
        assertEquals(listOf("Control", "Where", "What it removes", "What remains"), controls.headers)
        assertEquals(3, controls.rows.size, "the three real data controls must all be listed")
        for (expected in listOf("Clear downloads", "Clear history", "Clear recent searches")) {
            assertTrue(
                controls.rows.any { it.first() == expected },
                "missing the real control: $expected",
            )
        }
    }
}
