package dev.dhun.android.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure mapping tests for the launcher-shortcut extra contract. No Robolectric
 * needed — [ShortcutIntents.actionFromExtra] must stay a pure function.
 */
class ShortcutIntentsTest {

    @Test
    fun `maps every known extra value to its action`() {
        assertEquals(ShortcutAction.SEARCH, ShortcutIntents.actionFromExtra("search"))
        assertEquals(ShortcutAction.RESUME, ShortcutIntents.actionFromExtra("resume"))
        assertEquals(ShortcutAction.LIBRARY, ShortcutIntents.actionFromExtra("library"))
    }

    @Test
    fun `null and absent extras map to null`() {
        assertNull(ShortcutIntents.actionFromExtra(null))
        assertNull(ShortcutIntents.actionFromExtra(""))
    }

    @Test
    fun `unknown or hostile values are ignored, not crashed on`() {
        // A misbehaving launcher can put anything in the extra; the app must
        // still launch normally (action = null → normal cold start).
        assertNull(ShortcutIntents.actionFromExtra("boom"))
        assertNull(ShortcutIntents.actionFromExtra("SEARCH")) // case-sensitive on purpose
        assertNull(ShortcutIntents.actionFromExtra("search ")) // trailing space
        assertNull(ShortcutIntents.actionFromExtra("dev.dhun.android.extra.SHORTCUT_ACTION"))
    }

    @Test
    fun `extra key is stable - it is a public launcher contract`() {
        // Launchers and Android backup tooling may persist Intents that carry
        // this extra; changing the key silently breaks deep shortcuts.
        assertEquals("dev.dhun.android.extra.SHORTCUT_ACTION", ShortcutIntents.EXTRA_SHORTCUT_ACTION)
    }
}
