package dev.dhun.ui.player

import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.LyricsMaterialPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 2026-11 immersive lyrics presentation, as numbers.
 *
 * The defect this pins: every lyric line used to render in the same style
 * inside a rounded card, so nothing on screen told the eye which line was
 * being sung. The contract now is a three-role ladder (sung line » timed
 * line » secondary text), stanza grouping for unsynced text, honest source
 * attribution, and a highlight that stays a *frame* around the sung words
 * rather than becoming another opaque panel.
 */
class LyricsPresentationTest {

    @Test
    fun theSungLineOutranksEveryOtherRoleAtEveryTextSize() {
        listOf("small", "default", "large").forEach { size ->
            val active = DhunTypographyTokens.lyricActiveStyle(size)
            val line = DhunTypographyTokens.lyricLineStyle(size)
            val secondary = DhunTypographyTokens.lyricSecondaryStyle(size)
            assertTrue(
                active.fontSize > line.fontSize,
                "$size: active ${active.fontSize} must outrank line ${line.fontSize}",
            )
            assertTrue(
                line.fontSize > secondary.fontSize,
                "$size: line ${line.fontSize} must outrank secondary ${secondary.fontSize}",
            )
            // Bold sung line, regular secondary: weight is part of the ladder.
            assertTrue(active.fontWeight!! > line.fontWeight!!, "$size: active must be bolder")
            assertTrue(line.fontWeight!! > secondary.fontWeight!!, "$size: secondary must be the lightest")
            // Tall scripts need leading: lineHeight always above fontSize.
            assertTrue(active.lineHeight > active.fontSize, "$size: active needs leading")
            assertTrue(line.lineHeight > line.fontSize, "$size: line needs leading")
        }
        // The setting shifts the ladder, it never collapses it.
        assertTrue(
            DhunTypographyTokens.lyricActiveStyle("small").fontSize >
                DhunTypographyTokens.lyricLineStyle("large").fontSize,
            "even the smallest sung line must outrank the largest inactive one",
        )
    }

    @Test
    fun inactiveLinesRecedeButStayReadable() {
        assertTrue(
            INACTIVE_LYRIC_ALPHA in 0.45f..0.8f,
            "inactive lyric alpha $INACTIVE_LYRIC_ALPHA is either a wall or a ghost",
        )
    }

    @Test
    fun groupsBreatheMoreThanLineAndTranslationDo() {
        assertTrue(
            DhunSpacing.lyricsGroupSpacing > DhunSpacing.lyricsSecondarySpacing,
            "a stanza gap must read as a gap; line→translation must hug",
        )
        assertTrue(
            DhunSpacing.lyricsEdgeFeather >= DhunSpacing.xxl,
            "the list fade needs room to dissolve the first/last line",
        )
    }

    @Test
    fun unsyncedTextGroupsIntoStanzasByBlankLinesWithoutLosingIndices() {
        val text = "a\nb\n\nc\n\n\nd\n"
        assertEquals(
            listOf(listOf(0, 1), listOf(3), listOf(6)),
            lyricStanzaIndices(text.lines()),
        )
        // No blank lines: one stanza, every index kept.
        assertEquals(listOf(listOf(0, 1, 2)), lyricStanzaIndices(listOf("x", "y", "z")))
        // Blank-only text yields no stanzas (the caller shows an empty view).
        assertEquals(emptyList<List<Int>>(), lyricStanzaIndices(listOf("", "  ")))
    }

    @Test
    fun attributionNamesOnlyKnownSources() {
        assertEquals("Lyrics from YouTube Music", lyricsProviderCaption("ytm"))
        assertEquals("Lyrics from LRCLIB", lyricsProviderCaption("lrclib"))
        assertEquals("Lyrics from LRCLIB search", lyricsProviderCaption("lrclib_search"))
        assertEquals("Lyrics from KuGou", lyricsProviderCaption("kugou"))
        // Unknown or pre-attribution cache rows must not invent a source.
        assertEquals("Lyrics", lyricsProviderCaption(null))
        assertEquals("Lyrics", lyricsProviderCaption("mystery"))
    }

    @Test
    fun theActiveHighlightStaysAFrameNotAPanel() {
        // A frosted pill: visible on dark artwork, never an opaque slab.
        assertTrue(
            LyricsMaterialPolicy.ACTIVE_HIGHLIGHT_FROST in 0.05f..0.18f,
            "frost ${LyricsMaterialPolicy.ACTIVE_HIGHLIGHT_FROST} must whisper, not shout",
        )
        assertTrue(
            LyricsMaterialPolicy.ACTIVE_HIGHLIGHT_BASE in 0.10f..0.35f,
            "contrast base ${LyricsMaterialPolicy.ACTIVE_HIGHLIGHT_BASE} must stay translucent",
        )
    }

    @Test
    fun theOpenLyricsVeilIsLighterThanEveryCardedSurface() {
        // The card is gone; the veil only tops up contrast over the backdrop.
        val open = LyricsMaterialPolicy.openLyricsVeilStops().map { it.second }
        assertTrue(open.all { it <= LyricsMaterialPolicy.LYRICS_VEIL_TOP }, "open veil $open vs carded top")
        assertTrue(open.all { it < LyricsMaterialPolicy.QUEUE_VEIL_TOP }, "open veil must stay under the queue sheet")
        assertTrue(open.maxOrNull()!! >= 0.15f, "the centred column still needs a readability top-up")
    }

    @Test
    fun theSecondaryLadderIsPrimaryThenTranslationThenTransliteration() {
        // The reported defect was a translation sharing the primary line's
        // baseline. Once separated, the two secondary roles must not tie either:
        // a line carrying both has to read primary → translation →
        // transliteration, top to bottom and largest to smallest.
        for (size in listOf("small", "default", "large")) {
            val active = DhunTypographyTokens.lyricActiveStyle(size)
            val line = DhunTypographyTokens.lyricLineStyle(size)
            val translation = DhunTypographyTokens.lyricSecondaryStyle(size)
            val transliteration = DhunTypographyTokens.lyricTransliterationStyle(size)
            assertTrue(
                active.fontSize.value > line.fontSize.value &&
                    line.fontSize.value > translation.fontSize.value &&
                    translation.fontSize.value > transliteration.fontSize.value,
                "the lyric type ladder must strictly descend at size $size " +
                    "(${active.fontSize} > ${line.fontSize} > ${translation.fontSize} > ${transliteration.fontSize})",
            )
        }
        // Transliteration never grows to compete with the translation.
        assertTrue(
            DhunTypographyTokens.lyricTransliterationStyle("large").fontSize <=
                DhunTypographyTokens.lyricSecondaryStyle("small").fontSize,
            "even the largest transliteration must stay at or under the smallest translation",
        )
    }
}
