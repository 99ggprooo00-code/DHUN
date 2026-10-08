package dev.dhun.ui.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * The one place that decides which physical keystroke submits the search query.
 *
 * **Why this object exists.** The 2026-10-08 Windows report was "typing a query
 * and pressing Enter does nothing". The first fix wired the key check inline in
 * [SearchScreen]'s text field, which left this policy unused and its test
 * asserting a private copy of the rule — so the shipped predicate was untested
 * and a regression in it could still ship green. [SearchScreen] now calls
 * [shouldSubmitOnKeyEvent], and `SearchInputPolicyTest` builds real [KeyEvent]s
 * against this object, so the rule under test is the rule the UI runs.
 *
 * Two properties are load-bearing:
 * - **KeyDown only**, so holding Enter submits once instead of firing a request
 *   per key repeat.
 * - **Space is not a submit key**, because the same keystroke has to keep
 *   typing spaces into the query. That is the other half of the same keyboard
 *   report, whose window-level half lives in `DesktopShortcutPolicy`.
 */
object SearchInputPolicy {

    /**
     * Keys that submit. The numpad Enter is included so a full-size keyboard
     * behaves like a laptop one — the same pair the transport controls accept
     * (`isTransportActivationKey`).
     */
    fun isSubmitKey(key: Key): Boolean = key == Key.Enter || key == Key.NumPadEnter

    /** Whether [event] is the press (not the release) of a submit key. */
    fun shouldSubmitOnKeyEvent(event: KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown && isSubmitKey(event.key)
}
