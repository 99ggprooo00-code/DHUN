package dev.dhun.ui.navigation

/**
 * Phase 07 navigation routes — shared across Android + Desktop.
 * Simple stack-based navigator (no library dependency).
 */
sealed class Screen {
    data object Home : Screen()
    data object Search : Screen()
    data object Library : Screen()
    data object Player : Screen()

    // Phase 09 — detail pages (stubbed for future)
    data class Artist(val browseId: String) : Screen()
    data class Album(val browseId: String) : Screen()
    data class Playlist(val browseId: String) : Screen()
}

/** Nav arguments carried through the stack. */
data class NavArgs(
    val searchQuery: String? = null,
    val initialTrack: dev.dhun.core.Track? = null,
    val playlistId: String? = null,
)
