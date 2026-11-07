package dev.dhun.ui.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Search keyboard release rule.
 *
 * Reported defect: "on phone keyboard is not closed properly — the search bar
 * is active even after I navigated to a different page and on the full-screen
 * player."
 *
 * Root cause: nothing in the app ever released Search's focus. The Search
 * screen is not disposed when another destination is shown (that is how its
 * query and results survive), so no disposal hook could fire; and the full
 * player is an overlay drawn above the still-composed Search tab, so the IME
 * stayed attached to an off-screen text field.
 *
 * These assertions pin the *rule*; [DismissSearchKeyboard] is the effect that
 * applies it.
 */
class SearchKeyboardPolicyTest {

    @Test
    fun typingOnTheSearchTabKeepsTheKeyboard() {
        assertNull(SearchKeyboardPolicy.dismissalTrigger(searchVisible = true, playerExpanded = false, detailDepth = 0))
        assertTrue(SearchKeyboardPolicy.keepsFocus(searchVisible = true, playerExpanded = false, detailDepth = 0))
    }

    @Test
    fun switchingToAnotherTabReleasesTheKeyboard() {
        val trigger = SearchKeyboardPolicy.dismissalTrigger(searchVisible = false, playerExpanded = false, detailDepth = 0)
        assertEquals("left-search", trigger)
        assertFalse(SearchKeyboardPolicy.keepsFocus(searchVisible = false, playerExpanded = false, detailDepth = 0))
    }

    @Test
    fun openingTheFullPlayerReleasesTheKeyboard() {
        // The reported case: the player is an overlay above the composed Search tab.
        assertEquals(
            "full-player",
            SearchKeyboardPolicy.dismissalTrigger(searchVisible = true, playerExpanded = true, detailDepth = 0),
        )
        assertFalse(SearchKeyboardPolicy.keepsFocus(searchVisible = true, playerExpanded = true, detailDepth = 0))
    }

    @Test
    fun openingAResultReleasesTheKeyboard() {
        assertEquals(
            "detail-page",
            SearchKeyboardPolicy.dismissalTrigger(searchVisible = true, playerExpanded = false, detailDepth = 1),
        )
        assertFalse(SearchKeyboardPolicy.keepsFocus(searchVisible = true, playerExpanded = false, detailDepth = 2))
    }

    @Test
    fun theTriggerIsStableSoTypingIsNeverInterrupted() {
        // Same situation ⇒ same token. A token that changed on every
        // recomposition would re-run the effect and steal focus mid-keystroke.
        repeat(5) {
            assertEquals(
                "detail-page",
                SearchKeyboardPolicy.dismissalTrigger(searchVisible = true, playerExpanded = false, detailDepth = 3),
            )
        }
        // Returning to a clean Search screen clears the trigger, so the next
        // navigation fires again.
        assertNull(SearchKeyboardPolicy.dismissalTrigger(searchVisible = true, playerExpanded = false, detailDepth = 0))
    }

    @Test
    fun thePlayerWinsOverTheDetailReasonSoOneEffectRuns() {
        // Both conditions true must still yield ONE deterministic reason.
        assertEquals(
            "full-player",
            SearchKeyboardPolicy.dismissalTrigger(searchVisible = false, playerExpanded = true, detailDepth = 2),
        )
    }
}
