package dev.dhun.design.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextInputFocusRegistryTest {

    @Test
    fun `shortcut suppression remains active until every focused input releases focus`() {
        val registry = TextInputFocusRegistry()
        val searchField = Any()
        val playlistNameField = Any()

        assertFalse(registry.hasFocusedTextInput)
        registry.setFocused(searchField, true)
        registry.setFocused(playlistNameField, true)
        assertTrue(registry.hasFocusedTextInput)

        // Focus can move between fields in one composition; one field losing
        // focus must not re-enable Space while another is still editable.
        registry.setFocused(searchField, false)
        assertTrue(registry.hasFocusedTextInput)
        registry.setFocused(playlistNameField, false)
        assertFalse(registry.hasFocusedTextInput)
    }

    @Test
    fun `disposing a focused field clears its registration`() {
        val registry = TextInputFocusRegistry()
        val field = Any()

        registry.setFocused(field, true)
        registry.setFocused(field, false)

        assertFalse(registry.hasFocusedTextInput)
    }
}
