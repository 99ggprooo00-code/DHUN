package dev.dhun.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Detail destinations pushed on top of the tab content (Phase 09).
 * The app shell renders the top of [AppNavState.detailStack] when non-empty.
 */
sealed interface DetailRoute {
    /** YTM artist page (browse id UC…). */
    data class ArtistPage(val id: String) : DetailRoute

    /** YTM album page (browse id MPREb…). */
    data class AlbumPage(val id: String) : DetailRoute

    /** Playlist page — YTM (id VL…) or local ([isLocal] = true, SQLDelight id). */
    data class PlaylistPage(val id: String, val isLocal: Boolean = false) : DetailRoute
}

/**
 * Small shared navigator state (the "desktop navigator" of the locked stack,
 * also used on Android before Navigation-Compose deep-link work).
 *
 * Back behavior contract (program-level): the platform shell installs its
 * own BackHandler and calls [closeTop]; when nothing closes, the platform
 * default runs (Android → moveTaskToBack). FullPlayer collapses first, then
 * detail pages pop — BACK never exits the app while either is open.
 *
 * [detailStack] is the only navigation stack, and it means the same thing in
 * both shell layouts; what differs is *where* its top is rendered. Below the
 * rail breakpoint it replaces the tab content (see the shell's single-pane
 * branch); at [DhunShellLayout.TwoPane] it is the detail pane beside the list.
 * Nothing here encodes the layout — [DhunShellPolicy] owns that decision, so a
 * platform caller (or a restored Bundle) keeps working unchanged.
 */
class AppNavState {
    var selectedTab by mutableStateOf(AppTab.HOME)

    var playerExpanded by mutableStateOf(false)

    val detailStack = mutableStateListOf<DetailRoute>()

    /** Something is covering the tab content (full player or a detail page). */
    val hasOverlay: Boolean get() = playerExpanded || detailStack.isNotEmpty()

    /**
     * A detail page is open. Unlike [hasOverlay] this ignores the player, which
     * is the distinction the large-screen panes need: in two-pane mode a detail
     * route does not overlay the master at all, so "overlay" and "a page is
     * open" are no longer the same question.
     */
    val hasDetail: Boolean get() = detailStack.isNotEmpty()

    /** Closes the topmost overlay. @return true if anything closed. */
    fun closeTop(): Boolean = when {
        playerExpanded -> {
            playerExpanded = false
            true
        }
        detailStack.isNotEmpty() -> {
            detailStack.removeAt(detailStack.lastIndex)
            true
        }
        else -> false
    }

    /**
     * Pops exactly one [DetailRoute], leaving [playerExpanded] alone — the
     * detail pane's own up/back affordance. @return true if a page was popped.
     */
    fun popDetail(): Boolean =
        if (detailStack.isNotEmpty()) {
            detailStack.removeAt(detailStack.lastIndex)
            true
        } else {
            false
        }

    /** Push a detail page; also collapses the player so navigation is visible. */
    fun push(route: DetailRoute) {
        detailStack += route
    }

    /**
     * Nav-bar / nav-rail tap.
     *
     * [keepDetailOnTabChange] is the large-screen rule: with a separate detail
     * pane the page stays open while the master switches tabs, and re-tapping
     * the already-selected tab is the "up" affordance that pops one page. A
     * single-pane shell passes `false`, which reproduces the shipped behavior
     * exactly: switching tabs drops the whole stack, because a stack that is
     * merely hidden is a stack the user cannot get back to.
     *
     * @return true if the tap changed anything (no current caller reads it; it
     *   exists so the rule is testable rather than implicit in the click lambda).
     */
    fun selectTab(tab: AppTab, keepDetailOnTabChange: Boolean = false): Boolean {
        val tabChanged = tab != selectedTab
        selectedTab = tab
        if (!keepDetailOnTabChange) {
            val hadDetail = detailStack.isNotEmpty()
            detailStack.clear()
            return tabChanged || hadDetail
        }
        // Two-pane: a *different* tab switches the master under the page; a
        // re-tap on the selected tab is the up affordance and pops one entry.
        return if (tabChanged) true else popDetail()
    }
}
