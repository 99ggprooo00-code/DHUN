package dev.dhun.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Dismisses the on-screen keyboard and clears text-field focus.
 *
 * ## The defect this exists to fix
 *
 * On Android the Search field kept its focus after the user navigated away:
 * opening a result, opening the Full Player, or switching to Home/Library left
 * the IME attached to a text field that was no longer on screen. The keyboard
 * stayed up (or came straight back), and the field was still "active" on a
 * screen the user had already left — visible as a live caret and a search bar
 * that swallowed the next tap.
 *
 * The render tree alone cannot fix this: the Search screen is not disposed on
 * navigation (it stays in the composition so its query and results survive), so
 * no `DisposableEffect` in Search can notice that it stopped being the visible
 * destination. The dismissal therefore has to be driven by *who is visible*,
 * which is what [SearchKeyboardPolicy] decides and this effect applies.
 *
 * Both handle and focus are cleared on purpose: dismissing the IME alone leaves
 * the field focused (it re-opens on the next unrelated focus move), and clearing
 * focus alone leaves the IME up on some OEM keyboards.
 */
@Composable
fun DismissSearchKeyboard(trigger: Any?) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentFocus = rememberUpdatedState(focusManager)
    val currentKeyboard = rememberUpdatedState(keyboard)
    val key by rememberUpdatedState(trigger)
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        currentKeyboard.value?.hide()
        currentFocus.value.clearFocus(force = true)
    }
}

/**
 * Pure decision: should search focus be released right now?
 *
 * Kept as data so the rule is unit-tested instead of being implicit in a
 * lambda — the bug was precisely that this rule did not exist anywhere.
 *
 * @param searchVisible the Search tab is the visible destination.
 * @param playerExpanded the Full Player overlay is up.
 * @param detailDepth number of pages pushed above the tab content.
 */
object SearchKeyboardPolicy {

    /**
     * A trigger value that changes exactly when the keyboard must be released,
     * or `null` when the keyboard may stay.
     *
     * The returned token is intentionally coarse (a string per reason) so an
     * unrelated recomposition cannot retrigger it and repeatedly steal focus
     * from a user who is legitimately typing.
     */
    fun dismissalTrigger(
        searchVisible: Boolean,
        playerExpanded: Boolean,
        detailDepth: Int,
    ): String? = when {
        playerExpanded -> "full-player"
        !searchVisible -> "left-search"
        detailDepth > 0 -> "detail-page"
        else -> null
    }

    /** Whether the Search field may hold IME focus at all. */
    fun keepsFocus(searchVisible: Boolean, playerExpanded: Boolean, detailDepth: Int): Boolean =
        searchVisible && !playerExpanded && detailDepth == 0
}
