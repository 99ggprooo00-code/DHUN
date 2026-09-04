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
 */
class AppNavState {
    var selectedTab by mutableStateOf(AppTab.HOME)

    var playerExpanded by mutableStateOf(false)

    val detailStack = mutableStateListOf<DetailRoute>()

    /** Something is covering the tab content (full player or a detail page). */
    val hasOverlay: Boolean get() = playerExpanded || detailStack.isNotEmpty()

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

    /** Push a detail page; also collapses the player so navigation is visible. */
    fun push(route: DetailRoute) {
        detailStack += route
    }
}
