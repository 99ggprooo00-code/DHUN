package dev.dhun.lyrics

import dev.dhun.core.LyricsLine
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TtmlParserTest {
    private fun document(body: String, attrs: String = "") =
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttp="http://www.w3.org/ns/ttml#parameter" $attrs><body><div>$body</div></body></tt>"""

    @Test fun relativeMediaTimingRetainsIndependentWordEndsAndSilenceGaps() {
        val line = TtmlParser.parse(document("""<p begin="10s" end="15s"><span begin=".123s" end="1.001s">Hello </span><span begin="2.456s" dur="1s">世界!</span></p>""")).single()
        assertEquals(10_000L, line.startTimeMs)
        assertEquals(15_000L, line.endTimeMs)
        assertEquals("Hello 世界!", line.text)
        assertEquals(listOf("Hello ", "世界!"), line.words.map { line.text.substring(it.startIndex, it.endIndex) })
        assertEquals(listOf(10_123L, 12_456L), line.words.map { it.startTimeMs })
        assertEquals(listOf(11_001L, 13_456L), line.words.map { it.endTimeMs })
        assertEquals(listOf(line), SyncedLyricsCodec.decode(SyncedLyricsCodec.encode(listOf(line))))
    }

    @Test fun nestedParallelContainersUseParentRelativeClocksAndClipToTheirEnd() {
        val xml = """<t:tt xmlns:t="http://www.w3.org/ns/ttml"><t:body begin="1s" dur="10s"><t:div begin="2s"><t:p begin="00:00:03.001" dur="8s">नमस्ते<t:br/>مرحبا 🎵</t:p></t:div></t:body></t:tt>"""
        val line = TtmlParser.parse(xml).single()
        assertEquals(6_001L, line.startTimeMs)
        assertEquals(11_000L, line.endTimeMs)
        assertEquals("नमस्ते\nمرحبا 🎵", line.text)
        assertTrue(line.words.isEmpty())
    }

    @Test fun clockAndOffsetParsingUsesExactMillisecondsAndNoGuessedFrames() {
        val times = mapOf("00:00:01.001" to 1_001L, "01:02:03.456789" to 3_723_456L,
            "1.001s" to 1_001L, ".125s" to 125L, "123.9ms" to 123L, "1.5m" to 90_000L, "1h" to 3_600_000L)
        times.forEach { (input, expected) -> assertEquals(expected, TtmlParser.time(input)) }
        listOf("1f", "10t", "00:01:00:12", "00:99:01", "00:01:99", "-1s", "NaNs", "1e3s", "25h", "999999999999999999999999s").forEach {
            assertFailsWith<IllegalArgumentException>(it) { TtmlParser.time(it) }
        }
    }

    @Test fun originalLogicalTextEntitiesWhitespaceAndCdataAreNotSplitIntoCharacters() {
        val text = "  مرحبا & नमस्ते 🎵  "
        val line = TtmlParser.parse(document("""<p begin="1s" xml:space="preserve"><span begin="0s" end="2s">  مرحبا &amp; नमस्ते &#x1F3B5;  </span></p>""")).single()
        assertEquals(text, line.text)
        assertEquals(text, line.text.substring(line.words.single().startIndex, line.words.single().endIndex))
        assertEquals("A B\nC < D", TtmlParser.parse(document("""<p begin="1s">  A
             B<br/><![CDATA[C < D]]> </p>""")).single().text)
    }

    @Test fun preservedSpanBoundarySpacesAndExplicitBreaksSurviveDefaultParagraphWhitespace() {
        val xml = document("""<p begin="1s"><span begin="0s" xml:space="preserve">  A  </span></p>""")
        val line = TtmlParser.parse("\uFEFF" + xml).single()
        assertEquals("  A  ", line.text)
        assertEquals(0, line.words.single().startIndex)
        assertEquals(5, line.words.single().endIndex)
        assertEquals("\nA\n", TtmlParser.parse(document("""<p begin="1s"><br/>A<br/></p>""")).single().text)
    }

    @Test fun nestedWordRangesAndNonMonotonicTimingNeverManufactureKaraoke() {
        val nested = TtmlParser.parse(document("""<p begin="1s"><span begin="1s"><span begin=".5s" dur="1s">A</span></span></p>""")).single()
        assertEquals(2_500L, nested.words.single().startTimeMs)
        val reversed = TtmlParser.parse(document("""<p begin="1s" end="5s"><span begin="2s">A</span><span begin="1s">B</span></p>""")).single()
        assertEquals("AB", reversed.text)
        assertTrue(reversed.words.isEmpty())
        assertEquals(1_000L, reversed.startTimeMs)
    }

    @Test fun absentTimingNeverBecomesWordSyncAndSpanOnlyTimingCanStartALine() {
        assertTrue(TtmlParser.parse(document("<p>Plain only</p>")).isEmpty())
        assertEquals(LyricsLine(1_000, "Line only", endTimeMs = 2_000),
            TtmlParser.parse(document("""<p begin="1s" end="2s">Line only</p>""")).single())
        val span = TtmlParser.parse(document("""<p><span begin="2s" end="3s">Only span</span></p>""")).single()
        assertEquals(2_000L, span.startTimeMs)
        assertEquals(2_000L, span.words.single().startTimeMs)
    }

    @Test fun malformedUnsupportedAndOversizedDocumentsFailClosed() {
        val bad = listOf("<tt>", "<not-tt/>", "<tt><body><p begin=\"1s\">No namespace</p></body></tt>",
            document("""<p begin="2s" end="1s">Backwards</p>"""),
            document("""<p begin="1s" timeContainer="seq">Sequential</p>"""),
            document("""<p begin="1s" repeatCount="2">Repeated</p>"""),
            document("""<p begin="30f">Frames</p>"""),
            document("""<p begin="1s">Clock</p>""", """ttp:timeBase="clock""""),
            document("""<p begin="1s">Unknown <image src="file:///tmp/secret"/></p>"""),
            document("""<p begin="1s">${"x".repeat(16_001)}</p>"""),
            document("<div>".repeat(50) + "<p begin=\"1s\">Deep</p>" + "</div>".repeat(50)),
            document((1..2_001).joinToString("") { "<p begin=\"1s\">Many</p>" }),
            " ".repeat(TtmlParser.MAX_INPUT_CHARS + 1))
        bad.forEachIndexed { i, xml -> assertTrue(TtmlParser.parse(xml).isEmpty(), "bad input $i") }
    }

    @Test fun xmlDtdExternalEntitiesExpansionAndInstructionsAreRejected() {
        val payloads = listOf(
            """<!DOCTYPE tt SYSTEM "http://127.0.0.1:9/never-request">""",
            """<!DOCTYPE tt [<!ENTITY secret SYSTEM "file:///etc/passwd">]>""",
            """<!DOCTYPE tt [<!ENTITY a "expanded"><!ENTITY secret "&a;&a;&a;&a;">]>""",
            """<?external href="https://example.invalid/never-request"?>""",
        )
        payloads.forEach { prefix ->
            assertTrue(TtmlParser.parse(prefix + document("""<p begin="1s">&secret;</p>""")).isEmpty())
            assertTrue(TtmlParser.parse(prefix + document("""<p begin="1s">Even without entity use</p>""")).isEmpty())
        }
        assertTrue(TtmlParser.parse(document("""<p begin="1s">&unknown;</p>""")).isEmpty())
    }

    @Test fun workerCancellationIsNotConvertedToUnavailableLyrics() {
        var calls = 0
        assertFailsWith<CancellationException> {
            TtmlParser.parse(document("""<p begin="1s">Cancel</p>""")) {
                if (++calls > 3) throw CancellationException("track changed")
            }
        }
    }
}
