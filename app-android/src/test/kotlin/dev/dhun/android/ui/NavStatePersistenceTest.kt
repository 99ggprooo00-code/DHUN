package dev.dhun.android.ui

import android.os.Bundle
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DetailRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rotation/process-death contract of the Android nav state (Phase 13):
 * whatever `onSaveInstanceState` writes must restore to an equal
 * [AppNavState]. The serialization format is a stable cross-process-restart
 * contract — older format strings in a restored Bundle must not crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavStatePersistenceTest {

    @Test
    fun `full round trip preserves tab, expanded player and detail stack order`() {
        val original = AppNavState().apply {
            selectedTab = AppTab.LIBRARY
            playerExpanded = true
            detailStack += DetailRoute.ArtistPage("UCartist1")
            detailStack += DetailRoute.AlbumPage("MPREb_album1")
            detailStack += DetailRoute.PlaylistPage("VLplaylist1", isLocal = false)
            detailStack += DetailRoute.PlaylistPage("42", isLocal = true)
        }
        val bundle = Bundle().also { NavStatePersistence.save(original, it) }

        val restored = NavStatePersistence.restore(bundle)

        assertEquals(AppTab.LIBRARY, restored.selectedTab)
        assertTrue(restored.playerExpanded)
        assertEquals(original.detailStack.toList(), restored.detailStack.toList())
    }

    @Test
    fun `collapsed player and empty stack survive a round trip`() {
        val original = AppNavState().apply { selectedTab = AppTab.SEARCH }
        val bundle = Bundle().also { NavStatePersistence.save(original, it) }

        val restored = NavStatePersistence.restore(bundle)

        assertEquals(AppTab.SEARCH, restored.selectedTab)
        assertFalse(restored.playerExpanded)
        assertTrue(restored.detailStack.isEmpty())
    }

    @Test
    fun `null bundle restores fresh-start defaults`() {
        val restored = NavStatePersistence.restore(null)

        assertEquals(AppTab.HOME, restored.selectedTab)
        assertFalse(restored.playerExpanded)
        assertTrue(restored.detailStack.isEmpty())
    }

    @Test
    fun `unknown tab name falls back to HOME instead of crashing`() {
        val bundle = Bundle().apply {
            putString(NavStatePersistence.KEY_NAV_TAB, "SOME_FUTURE_TAB")
        }

        assertEquals(AppTab.HOME, NavStatePersistence.restore(bundle).selectedTab)
    }

    @Test
    fun `corrupt or future route entries are dropped, valid ones survive`() {
        val bundle = Bundle().apply {
            putStringArrayList(
                NavStatePersistence.KEY_DETAIL_ROUTES,
                arrayListOf(
                    "artist:UCok",
                    "bogus",
                    "", // empty entry
                    "playlist:true:7", // local playlist (colon inside payload-safe form)
                    "artist:", // missing id
                    "album:MPREb_ok",
                ),
            )
        }

        val restored = NavStatePersistence.restore(bundle)

        assertEquals(
            listOf(
                DetailRoute.ArtistPage("UCok"),
                DetailRoute.PlaylistPage("7", isLocal = true),
                DetailRoute.AlbumPage("MPREb_ok"),
            ),
            restored.detailStack.toList(),
        )
    }

    @Test
    fun `tab back history survives a round trip so BACK still reaches the previous tab`() {
        // Phase 16: Search/Library are tabs, and their back history is what
        // keeps Android BACK from parking the app. Losing it on rotate would
        // drop the user to Home from the tab they were on.
        val original = AppNavState().apply {
            selectTab(AppTab.SEARCH)
            selectTab(AppTab.LIBRARY)
        }
        assertEquals(listOf(AppTab.HOME, AppTab.SEARCH), original.tabHistoryEntries())

        val bundle = Bundle().also { NavStatePersistence.save(original, it) }
        val restored = NavStatePersistence.restore(bundle)

        assertEquals(AppTab.LIBRARY, restored.selectedTab)
        assertEquals(original.tabHistoryEntries(), restored.tabHistoryEntries())
        assertTrue(restored.onBack())
        assertEquals(AppTab.SEARCH, restored.selectedTab)
        assertTrue(restored.onBack())
        assertEquals(AppTab.HOME, restored.selectedTab)
        // JUnit's assertFalse takes the message first, unlike kotlin.test's.
        assertFalse("the root tab hands BACK to the platform", restored.onBack())
    }

    @Test
    fun `an unknown or absent tab history restores clean instead of inventing tabs`() {
        val fromOldBundle = NavStatePersistence.restore(
            Bundle().apply { putString(NavStatePersistence.KEY_NAV_TAB, AppTab.SEARCH.name) },
        )
        // No saved history (older format string, or a fresh install): BACK from
        // Search still lands on Home rather than doing nothing.
        assertTrue(fromOldBundle.onBack())
        assertEquals(AppTab.HOME, fromOldBundle.selectedTab)

        val corrupt = NavStatePersistence.restore(
            Bundle().apply {
                putString(NavStatePersistence.KEY_NAV_TAB, AppTab.LIBRARY.name)
                putStringArrayList(
                    NavStatePersistence.KEY_TAB_HISTORY,
                    arrayListOf("HOME", "NOT_A_TAB", "SEARCH"),
                )
            },
        )
        assertEquals(listOf(AppTab.HOME, AppTab.SEARCH), corrupt.tabHistoryEntries())
    }

    @Test
    fun `a restored catalog tab lands on home instead of the closeless screen`() {
        // CATALOG has no nav-bar entry and its onClose is a no-op: restoring
        // onto it (old bundle, debug session) would strand the user.
        val restored = NavStatePersistence.restore(
            Bundle().apply { putString(NavStatePersistence.KEY_NAV_TAB, AppTab.CATALOG.name) },
        )
        assertEquals(AppTab.HOME, restored.selectedTab)
        assertFalse("the root tab hands BACK to the platform", restored.onBack())
    }

    @Test
    fun `catalog entries are dropped from restored tab history`() {
        val restored = NavStatePersistence.restore(
            Bundle().apply {
                putString(NavStatePersistence.KEY_NAV_TAB, AppTab.LIBRARY.name)
                putStringArrayList(
                    NavStatePersistence.KEY_TAB_HISTORY,
                    arrayListOf("HOME", "CATALOG", "SEARCH"),
                )
            },
        )
        assertEquals(listOf(AppTab.HOME, AppTab.SEARCH), restored.tabHistoryEntries())
        // BACK walks the sanitized history and never lands on CATALOG.
        assertTrue(restored.onBack())
        assertEquals(AppTab.SEARCH, restored.selectedTab)
        assertTrue(restored.onBack())
        assertEquals(AppTab.HOME, restored.selectedTab)
    }

    @Test
    fun `playlist route keeps the local flag through encode-decode`() {
        val local = NavStatePersistence.encodeRoute(DetailRoute.PlaylistPage("15", isLocal = true))
        val remote = NavStatePersistence.encodeRoute(DetailRoute.PlaylistPage("VL_x", isLocal = false))

        assertEquals(DetailRoute.PlaylistPage("15", isLocal = true), NavStatePersistence.decodeRoute(local))
        assertEquals(DetailRoute.PlaylistPage("VL_x", isLocal = false), NavStatePersistence.decodeRoute(remote))
    }

    @Test
    fun `settings route round-trips as an id-less entry`() {
        // S4: SettingsPage carries no id, so it encodes to a bare tag and
        // decodes even if a future version appends fields (split limit).
        assertEquals("settings", NavStatePersistence.encodeRoute(DetailRoute.SettingsPage))
        assertEquals(DetailRoute.SettingsPage, NavStatePersistence.decodeRoute("settings"))
    }
}
