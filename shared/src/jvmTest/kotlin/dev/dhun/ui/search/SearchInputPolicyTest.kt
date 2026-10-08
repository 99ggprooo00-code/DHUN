package dev.dhun.ui.search

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the 2026-10-08 Windows report: *"typing a search query
 * and pressing Enter does nothing."*
 *
 * These build real [KeyEvent]s and call the shipped [SearchInputPolicy] — the
 * same object `SearchScreen`'s `onKeyEvent` calls — so the rule under test is
 * the rule the UI runs. The previous version of this file asserted a private
 * copy of the predicate instead, which stayed green whatever the policy or its
 * caller did; it proved nothing and is the reason the fix shipped unwired.
 *
 * The desktop [KeyEvent] factory is Compose-internal API (the platform key
 * event type is `Any` on JVM, so there is no public constructor). Opting in is
 * the price of testing the real object; it is pinned to the same Compose
 * Multiplatform version as the rest of the build (1.8.2).
 */
@OptIn(InternalComposeUiApi::class)
class SearchInputPolicyTest {

    @Test
    fun enterSubmitsTheQueryOnKeyDownAndNotOnKeyUp() {
        assertTrue(
            SearchInputPolicy.shouldSubmitOnKeyEvent(keyDown(Key.Enter)),
            "a physical Enter press must submit the query",
        )
        assertFalse(
            SearchInputPolicy.shouldSubmitOnKeyEvent(keyUp(Key.Enter)),
            "the release must not submit a second time, and a held Enter must not repeat",
        )
    }

    @Test
    fun numpadEnterSubmitsLikeTheMainEnterKey() {
        assertTrue(
            SearchInputPolicy.shouldSubmitOnKeyEvent(keyDown(Key.NumPadEnter)),
            "a full-size keyboard's numpad Enter is the same intent",
        )
    }

    @Test
    fun typingKeysNeverSubmitSoTheSpaceBarKeepsTypingSpaces() {
        // The other half of the same keyboard report: the window-level Space
        // play/pause shortcut (DesktopShortcutPolicy) must not steal the space
        // bar from the query, and no ordinary key may hijack a keystroke the
        // text field needs.
        listOf(
            Key.Spacebar,
            Key.A,
            Key.Z,
            Key.Zero,
            Key.DirectionDown,
            Key.Escape,
        ).forEach { key ->
            assertFalse(
                SearchInputPolicy.shouldSubmitOnKeyEvent(keyDown(key)),
                "$key must reach the text field instead of firing a search",
            )
            assertFalse(
                SearchInputPolicy.shouldSubmitOnKeyEvent(keyUp(key)),
                "$key release must be ignored too",
            )
        }
    }

    @Test
    fun submitKeysAreNamedExplicitlySoTheMappingCannotDrift() {
        assertTrue(SearchInputPolicy.isSubmitKey(Key.Enter))
        assertTrue(SearchInputPolicy.isSubmitKey(Key.NumPadEnter))
        assertFalse(SearchInputPolicy.isSubmitKey(Key.Spacebar))
        assertFalse(SearchInputPolicy.isSubmitKey(Key.Unknown))
    }

    private fun keyDown(key: Key): KeyEvent =
        KeyEvent(key = key, type = KeyEventType.KeyDown)

    private fun keyUp(key: Key): KeyEvent =
        KeyEvent(key = key, type = KeyEventType.KeyUp)
}
