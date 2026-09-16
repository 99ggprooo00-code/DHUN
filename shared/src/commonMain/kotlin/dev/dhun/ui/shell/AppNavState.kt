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
 * ## Back behavior contract (program-level)
 *
 * The platform shell installs its own BackHandler and calls [onBack]; when
 * nothing closes, the platform default runs (Android → `moveTaskToBack`, which
 * parks the app without killing playback). One press closes exactly one layer,
 * in this order:
 *
 * 1. the full player collapses (it is a sheet, not a page),
 * 2. one detail page pops,
 * 3. **the tab returns to the tab it came from** ([popTab]),
 * 4. at [AppTab.root] with nothing open, the platform takes over.
 *
 * Step 3 is what Android was missing (Phase 16): Search and Library are *tabs*,
 * not stack entries, so BACK on them found nothing to close and fell straight
 * through to the platform default — the app appeared to exit from a screen the
 * user had not finished with. Tab switches are now recorded here, so
 * Home → Search → BACK is Home, Home → Library → BACK is Home, and a
 * three-tab walk unwinds in the order it was walked. Root behaviour is
 * unchanged: BACK on Home still parks the app, and it can never loop.
 *
 * ## Layout
 *
 * [detailStack] is the only *page* stack, and it means the same thing in both
 * shell layouts; what differs is *where* its top is rendered. Below the rail
 * breakpoint it replaces the tab content (see the shell's single-pane branch);
 * at [DhunShellLayout.TwoPane] a non-empty stack becomes the detail pane beside
 * the list. An empty stack leaves the large-screen master full-width. Nothing
 * here encodes the layout — [DhunShellPolicy] owns that decision, so a platform
 * caller (or a restored Bundle) keeps working unchanged.
 */
class AppNavState {

    /**
     * Tabs already visited, oldest first; the tail is where BACK returns to.
     * Bounded by [MAX_TAB_HISTORY] — a long browsing session must not grow an
     * unbounded list, and nobody unwinds 40 tab switches one press at a time.
     */
    private val tabHistory = mutableStateListOf<AppTab>()

    private var currentTab by mutableStateOf(AppTab.HOME)

    /**
     * The visible tab.
     *
     * Deliberately a property with a recording setter rather than a plain
     * `mutableStateOf`: tabs are assigned from the nav bar, the nav rail, the
     * "Liked songs"/"Offline" shortcuts, launcher shortcut intents and restored
     * process-death state, and every one of those paths has to land in the back
     * history or BACK silently skips it.
     */
    var selectedTab: AppTab
        get() = currentTab
        set(value) {
            if (value == currentTab) return
            rememberTab(currentTab)
            currentTab = value
        }

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

    /**
     * Whether BACK has somewhere to go before the platform default: i.e. the
     * visible tab is not the root. [tabHistory] may still be non-empty at the
     * root (HOME is recorded like any other tab) — that is not a destination.
     */
    val hasTabHistory: Boolean get() = currentTab != AppTab.root

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

    /**
     * BACK on a tab: return to the tab this one was reached from, ultimately
     * [AppTab.root]. @return false at the root, where the platform default runs.
     *
     * The assignment goes through [currentTab], not the [selectedTab] setter,
     * so going backwards does not push the tab we just left onto the history —
     * that is what makes the walk terminate instead of oscillating.
     */
    fun popTab(): Boolean {
        if (currentTab == AppTab.root) return false
        var target: AppTab? = null
        while (target == null && tabHistory.isNotEmpty()) {
            val candidate = tabHistory.removeAt(tabHistory.lastIndex)
            if (candidate != currentTab) target = candidate
        }
        currentTab = target ?: AppTab.root
        return true
    }

    /**
     * The whole platform-BACK contract in one call: player, then one page, then
     * one tab. @return true if anything was handled; false means the caller
     * should run the platform default. Mirrors [DhunShellPolicy.backAction],
     * which states the same rule as data so it can be unit-tested.
     */
    fun onBack(): Boolean = closeTop() || popTab()

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
     * Either way the switch is recorded in the tab history, so BACK still
     * returns to the tab the user was on.
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

    /** History for `onSaveInstanceState` (oldest first). */
    fun tabHistoryEntries(): List<AppTab> = tabHistory.toList()

    /**
     * Replaces the history — the restore half of [tabHistoryEntries].
     *
     * Replaces rather than appends because restoring a tab through the
     * [selectedTab] setter has already recorded a default entry; keeping that
     * would put a phantom HOME in front of the user's real walk.
     */
    fun setTabHistory(entries: List<AppTab>) {
        tabHistory.clear()
        entries.takeLast(MAX_TAB_HISTORY).forEach { tabHistory.add(it) }
    }

    private fun rememberTab(tab: AppTab) {
        // A consecutive duplicate would make the next BACK look like a no-op.
        if (tabHistory.lastOrNull() == tab) return
        tabHistory.add(tab)
        while (tabHistory.size > MAX_TAB_HISTORY) tabHistory.removeAt(0)
    }

    companion object {
        /** Tabs remembered for BACK. `AppTab.userTabs` is three deep. */
        const val MAX_TAB_HISTORY = 8
    }
}
