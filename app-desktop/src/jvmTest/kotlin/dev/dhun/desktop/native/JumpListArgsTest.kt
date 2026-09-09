package dev.dhun.desktop.native

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-core tests for the jump-list launch arguments — these guard the exact
 * strings that will one day be parsed by the desktop entry point's arg hook,
 * and the id filter that keeps shell metacharacters out of `IShellLink`
 * arguments.
 */
class JumpListArgsTest {

    @Test
    fun `constants are stable launch arguments`() {
        assertEquals("--dhun-open", JumpListArgs.OPEN)
        assertEquals("--dhun-play-pause", JumpListArgs.PLAY_PAUSE)
        assertEquals("--dhun-play=", JumpListArgs.PLAY_PREFIX)
    }

    @Test
    fun `parse recognizes open and play-pause`() {
        assertEquals(JumpListArgs.Command.Open, JumpListArgs.parse("--dhun-open"))
        assertEquals(JumpListArgs.Command.PlayPause, JumpListArgs.parse("--dhun-play-pause"))
    }

    @Test
    fun `parse extracts valid track ids and rejects malformed ones`() {
        assertEquals(JumpListArgs.Command.Play("dQw4w9WgXcQ"), JumpListArgs.parse("--dhun-play=dQw4w9WgXcQ"))
        assertEquals(JumpListArgs.Command.Play("abc-DEF_123"), JumpListArgs.parse("--dhun-play=abc-DEF_123"))
        // Empty id, id with shell metacharacters, or overlong → Unknown, never an injection surface.
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse("--dhun-play="))
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse("--dhun-play=a;b"))
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse("--dhun-play=a b"))
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse("--dhun-play=" + "x".repeat(65)))
    }

    @Test
    fun `parse of unrelated text is unknown`() {
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse(""))
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse("--help"))
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse("--dhun-playfoo"))
        assertEquals(JumpListArgs.Command.Unknown, JumpListArgs.parse("--DHUN-OPEN"))
    }

    @Test
    fun `id validation allows only plain id characters`() {
        assertTrue(JumpListArgs.isValidTrackId("dQw4w9WgXcQ"))
        assertTrue(JumpListArgs.isValidTrackId("-"))
        assertTrue(JumpListArgs.isValidTrackId("_"))
        assertFalse(JumpListArgs.isValidTrackId(""))
        assertFalse(JumpListArgs.isValidTrackId("a\tb"))
        assertFalse(JumpListArgs.isValidTrackId("a\"b"))
        assertFalse(JumpListArgs.isValidTrackId("ä"))
        assertFalse(JumpListArgs.isValidTrackId("x".repeat(65)))
        assertTrue(JumpListArgs.isValidTrackId("x".repeat(64)))
    }

    @Test
    fun `play formats the prefixed argument`() {
        assertEquals("--dhun-play=dQw4w9WgXcQ", JumpListArgs.play("dQw4w9WgXcQ"))
    }
}
