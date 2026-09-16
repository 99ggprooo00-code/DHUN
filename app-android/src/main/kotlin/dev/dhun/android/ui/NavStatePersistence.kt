package dev.dhun.android.ui

import android.os.Bundle
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DetailRoute

/**
 * Saves/restores the shared [AppNavState] through the Android `Bundle`
 * (rotation / process-death survival — Phase 13).
 *
 * Extracted verbatim from `MainActivity` so the serialization format can be
 * unit-tested: the format is a stable process-death contract, and a bug here
 * silently loses the user's navigation on rotate. `NavStatePersistenceTest`
 * pins the round trip.
 */
object NavStatePersistence {

    const val KEY_NAV_TAB = "dhun.nav.tab"
    const val KEY_PLAYER_EXPANDED = "dhun.player.expanded"
    const val KEY_DETAIL_ROUTES = "dhun.nav.routes"
    const val KEY_TAB_HISTORY = "dhun.nav.tabHistory"

    fun save(state: AppNavState, out: Bundle) {
        out.putString(KEY_NAV_TAB, state.selectedTab.name)
        out.putBoolean(KEY_PLAYER_EXPANDED, state.playerExpanded)
        out.putStringArrayList(KEY_DETAIL_ROUTES, ArrayList(state.detailStack.map(::encodeRoute)))
        // The BACK history for tabs (Phase 16): losing it on rotate would send
        // the user straight to Home from the tab they were on.
        out.putStringArrayList(
            KEY_TAB_HISTORY,
            ArrayList(state.tabHistoryEntries().map { it.name }),
        )
    }

    /**
     * @param savedInstanceState the Bundle handed to `onCreate`; null or
     *   partial bundles restore defaults (fresh start).
     */
    fun restore(savedInstanceState: Bundle?): AppNavState = AppNavState().apply {
        savedInstanceState?.getString(KEY_NAV_TAB)?.let { name ->
            selectedTab = runCatching { AppTab.valueOf(name) }.getOrDefault(AppTab.root)
        }
        playerExpanded = savedInstanceState?.getBoolean(KEY_PLAYER_EXPANDED) ?: false
        savedInstanceState?.getStringArrayList(KEY_DETAIL_ROUTES).orEmpty()
            .mapNotNull(::decodeRoute)
            .forEach { route -> detailStack.add(route) }
        // Last, and a replace rather than an append: setting selectedTab above
        // has already recorded a default entry, and an unknown name is dropped
        // instead of inventing a tab the user never visited.
        setTabHistory(
            savedInstanceState?.getStringArrayList(KEY_TAB_HISTORY).orEmpty()
                .mapNotNull { name -> runCatching { AppTab.valueOf(name) }.getOrNull() },
        )
    }

    fun encodeRoute(route: DetailRoute): String = when (route) {
        is DetailRoute.ArtistPage -> "artist:${route.id}"
        is DetailRoute.AlbumPage -> "album:${route.id}"
        is DetailRoute.PlaylistPage -> "playlist:${route.isLocal}:${route.id}"
    }

    fun decodeRoute(value: String): DetailRoute? {
        val parts = value.split(':', limit = 3)
        // A blank id is corrupt, not an empty page: every route id here is a YTM
        // browse id (UC…/MPREb…/VL…) or a SQLDelight row id, so restoring one with
        // an empty id would put an unrecoverable entry on the nav back stack.
        return when (parts.firstOrNull()) {
            "artist" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(DetailRoute::ArtistPage)
            "album" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(DetailRoute::AlbumPage)
            "playlist" -> parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { id ->
                DetailRoute.PlaylistPage(id, parts.getOrNull(1) == "true")
            }
            else -> null
        }
    }
}
