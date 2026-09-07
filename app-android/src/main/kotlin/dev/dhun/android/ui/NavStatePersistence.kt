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

    fun save(state: AppNavState, out: Bundle) {
        out.putString(KEY_NAV_TAB, state.selectedTab.name)
        out.putBoolean(KEY_PLAYER_EXPANDED, state.playerExpanded)
        out.putStringArrayList(KEY_DETAIL_ROUTES, ArrayList(state.detailStack.map(::encodeRoute)))
    }

    /**
     * @param savedInstanceState the Bundle handed to `onCreate`; null or
     *   partial bundles restore defaults (fresh start).
     */
    fun restore(savedInstanceState: Bundle?): AppNavState = AppNavState().apply {
        savedInstanceState?.getString(KEY_NAV_TAB)?.let { name ->
            selectedTab = runCatching { AppTab.valueOf(name) }.getOrDefault(AppTab.HOME)
        }
        playerExpanded = savedInstanceState?.getBoolean(KEY_PLAYER_EXPANDED) ?: false
        savedInstanceState?.getStringArrayList(KEY_DETAIL_ROUTES).orEmpty()
            .mapNotNull(::decodeRoute)
            .forEach { route -> detailStack.add(route) }
    }

    fun encodeRoute(route: DetailRoute): String = when (route) {
        is DetailRoute.ArtistPage -> "artist:${route.id}"
        is DetailRoute.AlbumPage -> "album:${route.id}"
        is DetailRoute.PlaylistPage -> "playlist:${route.isLocal}:${route.id}"
    }

    fun decodeRoute(value: String): DetailRoute? {
        val parts = value.split(':', limit = 3)
        return when (parts.firstOrNull()) {
            "artist" -> parts.getOrNull(1)?.let(DetailRoute::ArtistPage)
            "album" -> parts.getOrNull(1)?.let(DetailRoute::AlbumPage)
            "playlist" -> parts.getOrNull(2)?.let { id ->
                DetailRoute.PlaylistPage(id, parts.getOrNull(1) == "true")
            }
            else -> null
        }
    }
}
