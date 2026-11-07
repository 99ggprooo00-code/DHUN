package dev.dhun.lyrics

import dev.dhun.core.LyricsLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bilingual lyric separation.
 *
 * The reported defect: "translation is appended to the same line in primary
 * lyrics". Root cause was two-fold and both halves are pinned here:
 *
 *  1. an inline bilingual LRC line (`primary / 译文`) was rendered verbatim, so
 *     the translation shared the lyric's baseline; and
 *  2. a provider that ships a *separate* translation document (kugou's
 *     `contentts`) had no overlay step at all.
 */
class LyricsTranslationTest {

    /* ---------------- inline single-line separation ---------------------- */

    @Test
    fun anInlineTranslationBecomesItsOwnSecondaryText() {
        val (primary, translation) = LrcParser.splitInlineTranslation("Tere bina / तेरे बिना")
        assertEquals("Tere bina", primary)
        assertEquals("तेरे बिना", translation)
    }

    @Test
    fun theDoubleSlashVariantSplitsToo() {
        val (primary, translation) = LrcParser.splitInlineTranslation("Hello world // नमस्ते दुनिया")
        assertEquals("Hello world", primary)
        assertEquals("नमस्ते दुनिया", translation)
    }

    @Test
    fun ordinaryLyricsAreNeverSplit() {
        // No spaced separator.
        assertEquals("Either/Or" to null, LrcParser.splitInlineTranslation("Either/Or"))
        assertEquals("and/or" to null, LrcParser.splitInlineTranslation("and/or"))
        // A URL keeps its "//" because neither side is a separate credit.
        assertEquals("https://example.com/x" to null, LrcParser.splitInlineTranslation("https://example.com/x"))
        // Blank on one side is not a translation.
        assertEquals(" / " to null, LrcParser.splitInlineTranslation(" / "))
        assertEquals("" to null, LrcParser.splitInlineTranslation(""))
    }

    @Test
    fun aRepeatedLineIsNotMistakenForATranslation() {
        assertEquals("la la // la la" to null, LrcParser.splitInlineTranslation("la la // la la"))
    }

    @Test
    fun theSplitSurvivesTheSyncedParser() {
        val lines = LrcParser.parse("[00:10.00]Tere bina / तेरे बिना\n[00:14.00]Plain line")
        assertEquals(2, lines.size)
        assertEquals("Tere bina", lines[0].text)
        assertEquals("तेरे बिना", lines[0].translation)
        assertEquals("Plain line", lines[1].text)
        assertNull(lines[1].translation)
    }

    @Test
    fun theSplitSurvivesTheUnsyncedFallback() {
        val lines = LrcParser.parse("Tere bina / तेरे बिना\n\nNext stanza", allowUnsyncedFallback = true)
        assertEquals("Tere bina", lines.first().text)
        assertEquals("तेरे बिना", lines.first().translation)
    }

    /* ---------------- separate translation document ---------------------- */

    @Test
    fun aTranslationDocumentIsOverlaidByStartTimeOntoItsOwnLines() {
        val primary = listOf(
            LyricsLine(1_000, "first"),
            LyricsLine(5_000, "second"),
        )
        val view = KuGouLyricsSourceView()
        val merged = view.overlay(primary, "[00:01.00]पहला\n[00:05.00]दूसरा")
        assertEquals(listOf("first", "second"), merged.map { it.text })
        assertEquals(listOf("पहला", "दूसरा"), merged.map { it.translation })
    }

    @Test
    fun unmatchedOrIdenticalTranslationLinesAreDroppedNotGuessed() {
        val primary = listOf(
            LyricsLine(1_000, "same"),
            LyricsLine(9_000, "unmatched"),
        )
        val merged = KuGouLyricsSourceView().overlay(
            primary,
            // Identical text at 1s, and a line at a timestamp the primary has not.
            "[00:01.00]same\n[00:03.00]orphan",
        )
        assertNull(merged[0].translation, "an identical line is not a translation")
        assertNull(merged[1].translation, "a translation with no matching onset must be dropped")
    }

    @Test
    fun anEmptyOrUnparsableDocumentLeavesPrimaryLyricsUntouched() {
        val primary = listOf(LyricsLine(1_000, "first"))
        assertEquals(primary, KuGouLyricsSourceView().overlay(primary, null))
        assertEquals(primary, KuGouLyricsSourceView().overlay(primary, "   "))
        assertEquals(primary, KuGouLyricsSourceView().overlay(primary, "not an lrc document"))
        assertTrue(KuGouLyricsSourceView().overlay(emptyList(), "[00:01.00]x").isEmpty())
    }

    /* ---------------- persistence ---------------------------------------- */

    @Test
    fun theTranslationSurvivesACacheRoundTrip() {
        val lines = listOf(
            LyricsLine(1_000, "first", translation = "पहला"),
            LyricsLine(5_000, "second"),
        )
        val decoded = SyncedLyricsCodec.decode(SyncedLyricsCodec.encode(lines))
        assertEquals(lines, decoded)
        assertEquals("पहला", decoded[0].translation)
        assertNull(decoded[1].translation)
    }

    @Test
    fun aPayloadWithoutTranslationsStillDecodes() {
        // Version 1/2 payloads (written before this change) must keep loading.
        val legacy = """{"dhunSyncedLyrics":2,"lines":[{"start":1000,"text":"first","background":false,"words":[]}]}"""
        val decoded = SyncedLyricsCodec.decode(legacy)
        assertEquals(1, decoded.size)
        assertEquals("first", decoded.single().text)
        assertNull(decoded.single().translation)
    }
}

/**
 * The overlay behaviour is a method on [KuGouLyricsSource]; this view just
 * exposes it under the name the tests describe it by.
 */
private class KuGouLyricsSourceView {
    private val source = KuGouLyricsSource()
    fun overlay(primary: List<LyricsLine>, document: String?) =
        source.applyTranslationOverlay(primary, document)
}
