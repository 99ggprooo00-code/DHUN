package dev.dhun.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TtmlRolesTest {
    private fun doc(body: String) = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div ttm:agent="v1">$body</div></body></tt>"""

    @Test fun paragraphRolesAndReferencesInheritWithoutInventingSingerIdentities() {
        val lines = TtmlParser.parse(doc("""<p begin="1s" end="5s">Main</p><p begin="1s" end="3s" ttm:role="x-bg" ttm:agent="v2">Backing</p>"""))
        assertEquals(listOf("Main", "Backing"), lines.map { it.text })
        assertEquals(listOf("v1", "v2"), lines.map { it.agent })
        assertEquals(listOf(false, true), lines.map { it.isBackground })
        assertEquals(lines, SyncedLyricsCodec.decode(SyncedLyricsCodec.encode(lines)))
    }

    @Test fun nestedBackgroundIsIndependentAndNotDuplicatedInTheMainText() {
        val lines = TtmlParser.parse(doc("""<p begin="10s" end="15s"><span begin="0s" end="1s">Hello </span><span begin="1s" end="3s" ttm:role="x-bg" ttm:agent="v2"><span begin="0s" end=".5s">नमस्ते </span><span begin="1s" end="2s">🎵</span></span><span begin="3s" end="4s">world</span></p>"""))
        assertEquals(2, lines.size)
        val main = lines.first()
        val background = lines.last()
        assertEquals("Hello world", main.text)
        assertEquals(listOf("Hello ", "world"), main.words.map { main.text.substring(it.startIndex, it.endIndex) })
        assertEquals(listOf(10_000L, 13_000L), main.words.map { it.startTimeMs })
        assertEquals("नमस्ते 🎵", background.text)
        assertEquals(11_000L, background.startTimeMs)
        assertEquals(13_000L, background.endTimeMs)
        assertEquals(listOf(11_000L, 12_000L), background.words.map { it.startTimeMs })
        assertEquals(listOf(11_500L, 13_000L), background.words.map { it.endTimeMs })
        assertTrue(background.isBackground)
        assertEquals("v2", background.agent)
        assertEquals(lines, SyncedLyricsCodec.decode(SyncedLyricsCodec.encode(lines)))
    }

    @Test fun separatelyAttributedSpansRetainSourceTimingAndRangesWithoutAPhantomParentLine() {
        val lines = TtmlParser.parse(doc("""<p begin="1s" end="5s"><span begin="0s" end="1s" ttm:agent="v2">مرحبا</span><span begin="2s" end="3s" ttm:agent="v3">你好</span></p>"""))
        assertEquals(listOf("مرحبا", "你好"), lines.map { it.text })
        assertEquals(listOf("v2", "v3"), lines.map { it.agent })
        assertEquals(listOf(1_000L, 3_000L), lines.map { it.startTimeMs })
        assertEquals(listOf(2_000L, 4_000L), lines.map { it.endTimeMs })
        lines.forEach { assertEquals(it.text, it.text.substring(it.words.single().startIndex, it.words.single().endIndex)) }
        assertTrue(lines.none { it.isBackground })
    }

    @Test fun inheritedBackingRoleDoesNotSplitAgainAndWhitespaceInReferencesIsNormalized() {
        val lines = TtmlParser.parse(doc("""<p begin="1s" end="4s" ttm:role="x-bg" ttm:agent=" v2   v3 "><span begin="0s" ttm:role="x-bg">A </span><span begin="1s">B</span></p>"""))
        assertEquals(1, lines.size)
        assertEquals("v2 v3", lines.single().agent)
        assertEquals("A B", lines.single().text)
        assertTrue(lines.single().isBackground)
        // Unrelated attributes/namespaces are not evidence for a background role.
        assertFalse(TtmlParser.parse(doc("""<p begin="1s" role="x-bg" agent="guess">Original</p>""")).single().isBackground)
    }

    @Test fun emptyOversizedOrControlReferencesAndTooManyPromotedPartsAreRejected() {
        listOf("", "a b c d e", "x".repeat(129), "a&#x7f;b").forEach { id ->
            assertTrue(TtmlParser.parse(doc("""<p begin="1s" ttm:agent="$id">No fake identity</p>""")).isEmpty())
        }
        val parts = (1..2_001).joinToString("") { """<span begin="0s" ttm:agent="v2">Part</span>""" }
        assertTrue(TtmlParser.parse(doc("""<p begin="1s">$parts</p>""")).isEmpty())
    }
}
