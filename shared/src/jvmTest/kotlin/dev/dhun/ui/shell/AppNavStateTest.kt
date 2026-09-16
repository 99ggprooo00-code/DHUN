package dev.dhun.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for the shared navigator state that the two-pane shell now
 * depends on: the detail stack is the *only* navigation stack, so its
 * ordering and its back semantics are what decide whether a tablet Back pops a
 * page, collapses the player, or hands the gesture to the platform.
 *
 * `NavStatePersistenceTest` (app-android) pins the wire format; these pin the
 * behaviour behind it, and they are layout-aware in the sense that a two-pane
 * Back must be able to empty the stack route by route without ever taking the
 * master pane with it.
 */
class AppNavStateTest {

    @Test
    fun routesPopInReversePushOrderAndStopAtEmpty() {
        val nav = AppNavState()
        val artist = DetailRoute.ArtistPage("UCartist")
        val album = DetailRoute.AlbumPage("MPREalbum")
        val playlist = DetailRoute.PlaylistPage("VLplaylist", isLocal = false)

        nav.push(artist)
        nav.push(album)
        nav.push(playlist)
        assertEquals(3, nav.detailStack.size)
        assertEquals(playlist, nav.detailStack.last())

        assertTrue(nav.popDetail())
        assertEquals(album, nav.detailStack.last())
        assertTrue(nav.popDetail())
        assertEquals(artist, nav.detailStack.last())
        assertTrue(nav.popDetail())
        assertTrue(nav.detailStack.isEmpty())
        // Popping an empty stack reports "nothing happened" rather than lying.
        assertFalse(nav.popDetail())
        assertFalse(nav.hasDetail)
    }

    @Test
    fun pushingTheSameRouteTwiceIsTwoEntriesBecauseBackShouldGoBackOnce() {
        // Album → artist → same album can legitimately happen from related
        // lists; collapsing duplicates would drop the user two pages deep.
        val nav = AppNavState()
        val album = DetailRoute.AlbumPage("MPREx")
        val artist = DetailRoute.ArtistPage("UCy")
        nav.push(album)
        nav.push(artist)
        nav.push(album)
        assertEquals(3, nav.detailStack.size)
        nav.popDetail()
        assertEquals(artist, nav.detailStack.last())
    }

    @Test
    fun hasOverlayAndHasDetailTrackExactlyWhatCoversTheTabContent() {
        val nav = AppNavState()
        assertFalse(nav.hasOverlay)
        assertFalse(nav.hasDetail)

        nav.playerExpanded = true
        assertTrue(nav.hasOverlay)
        assertFalse(nav.hasDetail, "an expanded player is not a detail route")

        nav.playerExpanded = false
        nav.push(DetailRoute.ArtistPage("UCz"))
        assertTrue(nav.hasOverlay)
        assertTrue(nav.hasDetail)
    }

    @Test
    fun closeTopCollapsesThePlayerFirstThenPopsOneRouteAtATime() {
        // Program-level contract: BACK never exits the app while something the
        // shell owns is open, and one press never destroys more than one layer.
        val nav = AppNavState()
        nav.push(DetailRoute.ArtistPage("UCa"))
        nav.push(DetailRoute.AlbumPage("MPREb"))
        nav.playerExpanded = true

        assertTrue(nav.closeTop())
        assertTrue(nav.playerExpanded.not(), "collapse must not also pop the stack")
        assertEquals(2, nav.detailStack.size)

        assertTrue(nav.closeTop())
        assertEquals(DetailRoute.ArtistPage("UCa"), nav.detailStack.last())

        assertTrue(nav.closeTop())
        assertTrue(nav.detailStack.isEmpty())

        assertFalse(nav.closeTop(), "nothing open → the platform default takes over")
    }

    @Test
    fun tabSwitchLeavesTheStackAloneAndReTappingTheTabPopsOnePage() {
        val nav = AppNavState()
        nav.selectedTab = AppTab.HOME
        nav.push(DetailRoute.ArtistPage("UCkeep"))

        // Two-pane (keepDetailOnTabChange = true): moving to another tab keeps
        // the detail page visible, because it is not covering anything.
        assertTrue(nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = true))
        assertEquals(AppTab.SEARCH, nav.selectedTab)
        assertEquals(1, nav.detailStack.size)

        // Re-tapping the active tab is the "up" affordance, so it pops one page…
        assertTrue(nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = true))
        assertTrue(nav.detailStack.isEmpty())

        // …and with nothing to pop it reports that it did nothing.
        assertFalse(nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = true))
    }

    @Test
    fun singlePaneTabSwitchDropsTheWholeStackLikeItAlwaysHas() {
        val nav = AppNavState()
        nav.push(DetailRoute.ArtistPage("UCa"))
        nav.push(DetailRoute.AlbumPage("MPREb"))

        nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = false)
        assertEquals(AppTab.LIBRARY, nav.selectedTab)
        assertTrue(nav.detailStack.isEmpty(), "a covered stack is a stale stack")
    }

    // ------------------------------------------------- tab back (Phase 16)

    @Test
    fun backFromSearchReturnsToHomeInsteadOfExitingTheApp() {
        // The reported Android bug: Search is a tab, not a stack entry, so
        // BACK used to find nothing to close and handed the gesture straight to
        // the platform — the app appeared to exit from a screen in use.
        val nav = AppNavState()
        nav.selectTab(AppTab.SEARCH)

        assertTrue(nav.hasTabHistory, "a non-root tab must have somewhere to go back to")
        assertTrue(nav.onBack(), "BACK must be handled by the shell, not the platform")
        assertEquals(AppTab.HOME, nav.selectedTab)
    }

    @Test
    fun backFromLibraryReturnsToHomeInsteadOfExitingTheApp() {
        val nav = AppNavState()
        nav.selectTab(AppTab.LIBRARY)

        assertTrue(nav.onBack())
        assertEquals(AppTab.HOME, nav.selectedTab)
    }

    @Test
    fun backWalksTheTabsInReverseOrderAndStopsAtTheRoot() {
        val nav = AppNavState()
        nav.selectTab(AppTab.SEARCH)
        nav.selectTab(AppTab.LIBRARY)

        assertTrue(nav.onBack())
        assertEquals(AppTab.SEARCH, nav.selectedTab)
        assertTrue(nav.onBack())
        assertEquals(AppTab.HOME, nav.selectedTab)

        // At the root there is nowhere left to go: the platform default runs,
        // and repeated presses can never invent a tab or loop.
        assertFalse(nav.hasTabHistory)
        assertFalse(nav.popTab())
        assertFalse(nav.onBack())
        assertEquals(AppTab.HOME, nav.selectedTab)
        assertFalse(nav.onBack())
        assertEquals(AppTab.HOME, nav.selectedTab)
    }

    @Test
    fun aDeeperScreenPopsBeforeTheTabIsLeft() {
        // Home → Search → artist → BACK → Search → BACK → Home. One press moves
        // one layer: the page first, the tab only once no page is open.
        val nav = AppNavState()
        nav.selectTab(AppTab.SEARCH)
        nav.push(DetailRoute.ArtistPage("UCsearchresult"))

        assertTrue(nav.onBack())
        assertEquals(AppTab.SEARCH, nav.selectedTab)
        assertTrue(nav.detailStack.isEmpty())

        assertTrue(nav.onBack())
        assertEquals(AppTab.HOME, nav.selectedTab)
    }

    @Test
    fun thePlayerStillCollapsesBeforePagesAndTabs() {
        val nav = AppNavState()
        nav.selectTab(AppTab.LIBRARY)
        nav.push(DetailRoute.PlaylistPage("42", isLocal = true))
        nav.playerExpanded = true

        assertTrue(nav.onBack())
        assertFalse(nav.playerExpanded)
        assertEquals(AppTab.LIBRARY, nav.selectedTab, "collapsing the player is not navigation")

        assertTrue(nav.onBack())
        assertEquals(AppTab.LIBRARY, nav.selectedTab, "one press pops one page only")
        assertTrue(nav.onBack())
        assertEquals(AppTab.HOME, nav.selectedTab)
    }

    @Test
    fun tabHistoryIgnoresConsecutiveDuplicatesAndStaysBounded() {
        val nav = AppNavState()
        // Re-tapping the current tab records nothing (it is not a move).
        nav.selectTab(AppTab.SEARCH)
        nav.selectTab(AppTab.SEARCH)
        nav.selectTab(AppTab.SEARCH)
        assertEquals(listOf(AppTab.HOME), nav.tabHistoryEntries())

        // A long browsing session cannot grow the list without bound.
        repeat(AppNavState.MAX_TAB_HISTORY * 3) { i ->
            nav.selectTab(if (i % 2 == 0) AppTab.SEARCH else AppTab.LIBRARY)
        }
        assertTrue(nav.tabHistoryEntries().size <= AppNavState.MAX_TAB_HISTORY)
        // …and BACK still lands on a real tab rather than nowhere.
        assertTrue(nav.onBack())
        assertTrue(nav.selectedTab in AppTab.userTabs)
    }

    @Test
    fun directTabAssignmentsAlsoLandInTheBackHistory() {
        // Launcher shortcuts, the "Liked songs"/"Offline" affordances and
        // restored state all write selectedTab directly rather than going
        // through selectTab; none of them may be skipped by BACK.
        val nav = AppNavState()
        nav.selectedTab = AppTab.LIBRARY

        assertTrue(nav.onBack())
        assertEquals(AppTab.HOME, nav.selectedTab)
    }

    @Test
    fun onBackAgreesWithTheShellBackPolicy() {
        // AppNavState.onBack is the executor, DhunShellPolicy.backAction the
        // stated rule: the two must not drift apart.
        val nav = AppNavState()
        assertEquals(
            ShellBackAction.PlatformDefault,
            DhunShellPolicy.backAction(
                DhunShellLayout.SinglePane,
                playerExpanded = nav.playerExpanded,
                detailDepth = nav.detailStack.size,
                hasTabHistory = nav.hasTabHistory,
            ).action,
        )

        nav.selectTab(AppTab.SEARCH)
        assertEquals(
            ShellBackAction.ReturnToPreviousTab,
            DhunShellPolicy.backAction(
                DhunShellLayout.SinglePane,
                playerExpanded = nav.playerExpanded,
                detailDepth = nav.detailStack.size,
                hasTabHistory = nav.hasTabHistory,
            ).action,
        )
    }

    @Test
    fun catalogIsNeverANavBarTabButSurvivesRestoredState() {
        // Deep links / process-death restore may name CATALOG, so it stays in
        // the enum; the bar must not offer it (it shipped once as a fourth tab
        // full of swatches).
        assertEquals(listOf(AppTab.HOME, AppTab.SEARCH, AppTab.LIBRARY), AppTab.userTabs)
        assertEquals(AppTab.CATALOG, AppTab.valueOf("CATALOG"))
    }
}
