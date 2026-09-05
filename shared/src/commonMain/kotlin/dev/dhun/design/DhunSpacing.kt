package dev.dhun.design

import androidx.compose.ui.unit.dp

/**
 * Spatial scale — every layout uses these; no raw `dp` literals outside
 * `shared/design/`.
 */
object DhunSpacing {
    val zero = 0.dp
    val divider = 1.dp
    val iconStroke = 2.dp
    val progressStroke = 3.dp
    val xs = 4.dp
    val xsPlus = 6.dp
    val sm = 8.dp
    val smPlus = 10.dp
    val md = 12.dp
    val mdPlus = 14.dp
    val mediumLarge = 28.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    // Semantic aliases
    val screenPadding = xl  // airier than Phase 07 (was 16dp)
    val cardPadding = md
    val sectionSpacing = xxxl
    val itemSpacing = sm
    val touchTarget = 48.dp
    val compactTarget = 44.dp
    val transportTarget = 52.dp
    val artworkMini = 56.dp
    val navigationBarContent = 64.dp
    val navigationRailBreakpoint = 840.dp
    val listRowHeight = 72.dp
    val miniPlayerHeight = 72.dp
    val bottomNavHeight = 80.dp
    val transportRowHeight = 88.dp
    val contentBottomInset = 112.dp  // room above frosted mini+nav dock
    val skeletonCardHeight = 110.dp
    val skeletonTextWidth = 120.dp
    val skeletonMetaWidth = 140.dp
    val skeletonArtistWidth = 150.dp
    val dialogListHeight = 180.dp
    val artworkPlaylist = 200.dp
    val artworkAlbum = 220.dp
    val artistHeaderHeight = 240.dp
    val quickPickWidth = 260.dp
    val dialogMinWidth = 280.dp
    val miniPlayerWindowWidth = 320.dp
    val dialogMaxWidth = 380.dp
    val windowDefaultWidth = 1200.dp
    val windowDefaultHeight = 780.dp
    val dialogWideMaxWidth = 400.dp
    val iconSize = 24.dp
    val iconSizeSm = 18.dp
    val iconSizeLg = 32.dp
    val artworkThumb = 56.dp
    val artworkCard = 160.dp
    val artworkLarge = 280.dp
    val glassBlur = 16.dp
    val progressHeight = 4.dp
    val progressHeightActive = 8.dp
    val border = 1.dp
    val shimmerCorner = 8.dp
}
