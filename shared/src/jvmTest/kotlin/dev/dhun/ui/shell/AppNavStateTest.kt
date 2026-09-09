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

    @Test
    fun catalogIsNeverANavBarTabButSurvivesRestoredState() {
        // Deep links / process-death restore may name CATALOG, so it stays in
        // the enum; the bar must not offer it (it shipped once as a fourth tab
        // full of swatches).
        assertEquals(listOf(AppTab.HOME, AppTab.SEARCH, AppTab.LIBRARY), AppTab.userTabs)
        assertEquals(AppTab.CATALOG, AppTab.valueOf("CATALOG"))
    }
}
