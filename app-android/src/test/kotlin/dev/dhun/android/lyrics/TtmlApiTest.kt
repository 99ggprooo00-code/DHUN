package dev.dhun.android.lyrics

import dev.dhun.lyrics.TtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android uses its tokenizing XML pull parser, not JVM-only JAXP configuration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class TtmlApiTest {
    private val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="5s"><span begin="0s" end="1s">A &amp; </span><span begin="2s" end="3s">&#x1F3B5;</span></p></div></body></tt>"""

    @Test fun realPullParserRetainsTextRangesEntitiesAndRelativeTimingAtApiFloor() {
        val line = TtmlParser.parse(xml).single()
        assertEquals("A & 🎵", line.text)
        assertEquals(1_000L, line.startTimeMs)
        assertEquals(5_000L, line.endTimeMs)
        assertEquals(listOf(1_000L, 3_000L), line.words.map { it.startTimeMs })
        assertEquals(listOf(2_000L, 4_000L), line.words.map { it.endTimeMs })
        assertEquals("🎵", line.text.substring(line.words.last().startIndex, line.words.last().endIndex))
        assertEquals(listOf(line), TtmlParser.parse("<?xml version=\"1.0\"?>" + xml))
    }

    @Test fun dtdEntitiesMalformedAndOversizedInputFailClosedOnAndroid() {
        val prefix = """<!DOCTYPE tt [<!ENTITY secret SYSTEM "file:///etc/passwd">]>"""
        assertTrue(TtmlParser.parse(prefix + xml).isEmpty())
        assertTrue(TtmlParser.parse(prefix + xml.replace("A &amp; ", "&secret;")).isEmpty())
        assertTrue(TtmlParser.parse("""<!DOCTYPE tt SYSTEM "http://127.0.0.1:9/never-request">""" + xml).isEmpty())
        assertTrue(TtmlParser.parse(xml.replace("&amp;", "&unknown;")).isEmpty())
        assertTrue(TtmlParser.parse("<broken>").isEmpty())
        assertTrue(TtmlParser.parse(" ".repeat(TtmlParser.MAX_INPUT_CHARS + 1)).isEmpty())
        assertTrue(TtmlParser.parse("<?external href=\"file:///never-read\"?>" + xml).isEmpty())
    }
}
