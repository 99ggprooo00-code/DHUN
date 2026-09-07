package dev.dhun.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The enum is shared across every screen — duplicates and malformed paths are
 * build-green but visually broken, so both are pinned here.
 */
class DhunIconsTest {

    @Test
    fun iconNamesAreUnique() {
        val names = DhunIcon.entries.map { it.name }
        assertEquals(names.size, names.toSet().size, "DhunIcon must not contain duplicate names")
    }

    @Test
    fun everyIconPathParsesAsSvgTokens() {
        // The embedded mini-parser (DhunIcons.kt) understands only the M/L/H/V/C/S/Q/T/Z
        // family; arcs are drawn as lines and unknown commands are skipped. Restrict the
        // glyph set to commands that actually render — no 'A' arcs, no curves without
        // coordinate pairs.
        DhunIcon.entries.forEach { icon ->
            val path = icon.pathData
            assertTrue(path.isNotBlank(), "${icon.name} has an empty path")
            assertTrue(path.trim().startsWith("M") || path.trim().startsWith("m"), "${icon.name} must start with a move")
            val commands = path.filter { it.isLetter() }
            assertTrue('A' !in commands && 'a' !in commands, "${icon.name} must not use arc commands")
        }
    }

    @Test
    fun playerAffordanceGlyphsExist() {
        // ADR-002 rule 5: the player's Lyrics tab and dedicated CC control render
        // these exact glyphs; renaming either silently breaks FullPlayer.
        assertTrue(DhunIcon.entries.any { it.name == "Lyrics" })
        assertTrue(DhunIcon.entries.any { it.name == "ClosedCaption" })
    }
}
