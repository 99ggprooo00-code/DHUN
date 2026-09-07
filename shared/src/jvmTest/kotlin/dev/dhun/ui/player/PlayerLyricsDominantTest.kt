package dev.dhun.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR-002 rule 5 (CC toggle) and P9 (swipe-down collapse) — pure logic. */
class PlayerLyricsDominantTest {

    @Test
    fun ccToggleEntersLyricsAndRemembersTheReturnTab() {
        val (tab, plain) = toggleLyricsDominant(selectedTab = 1, lastPlainTab = 1)
        assertEquals(LYRICS_TAB_INDEX, tab)
        assertEquals(1, plain)
        val (tab2, plain2) = toggleLyricsDominant(selectedTab = 2, lastPlainTab = 1)
        assertEquals(LYRICS_TAB_INDEX, tab2)
        assertEquals(2, plain2, "the tab being left must become the return target")
    }

    @Test
    fun ccToggleInLyricsRestoresTheStashedTab() {
        val (tab, plain) = toggleLyricsDominant(selectedTab = LYRICS_TAB_INDEX, lastPlainTab = 2)
        assertEquals(2, tab)
        assertEquals(2, plain, "the stash survives the return so a re-toggle goes back to it")
    }

    @Test
    fun corruptReturnStashStillLandsOnAPlainTab() {
        val (tab, _) = toggleLyricsDominant(selectedTab = LYRICS_TAB_INDEX, lastPlainTab = 0)
        assertEquals(1, tab, "a stale stash pointing at lyrics must coerce to Queue")
        val (relatedTab, _) = toggleLyricsDominant(selectedTab = 9, lastPlainTab = 1)
        assertEquals(LYRICS_TAB_INDEX, relatedTab)
        val (_, newPlain) = toggleLyricsDominant(selectedTab = 9, lastPlainTab = 1)
        assertEquals(2, newPlain.coerceIn(1, 2), "out-of-range tabs coerce into the plain range")
    }

    @Test
    fun collapseNeedsACompletedDownwardSwipePastThreshold() {
        assertTrue(shouldCollapseFullPlayer(48f, 48f))
        assertTrue(shouldCollapseFullPlayer(120f, 48f))
        assertFalse(shouldCollapseFullPlayer(47f, 48f))
        assertFalse(shouldCollapseFullPlayer(-120f, 48f), "upward drags never collapse the player")
        assertFalse(shouldCollapseFullPlayer(0f, 48f))
        assertFalse(shouldCollapseFullPlayer(120f, 0f))
        assertFalse(shouldCollapseFullPlayer(Float.NaN, 48f))
        assertFalse(shouldCollapseFullPlayer(120f, Float.POSITIVE_INFINITY))
    }
}
