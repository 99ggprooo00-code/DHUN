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
    /** Compact Now Playing transport row: hugs the 48dp targets + 72dp play disc. */
    val playerTransportHeight = 72.dp
    val playerContentMaxWidth = 720.dp
    val playerTransportMaxWidth = 400.dp
    /**
     * Largest sharp cover in Now Playing. The hero first consumes the actual
     * stage width/height, then stops here on generous tablet/desktop windows
     * so it remains a deliberate album-art focal point rather than a wall.
     */
    val playerArtworkMaxSize = 640.dp
    /** The breathing room that makes the hero land at roughly 80–92% of a phone's width. */
    val playerArtworkHorizontalInset = md
    val playerArtworkVerticalInset = sm
    /** Headroom around the animated cover, so its existing play/skip motion cannot clip at the stage edge. */
    val playerArtworkAnimationInset = xs
    /** Space reserved above artwork when the collapse header overlays a wide player. */
    val playerArtworkHeaderInset = huge + md
    /** Wide/landscape layout: artwork receives the remaining room beside this compact chrome column. */
    val playerWideLayoutMinWidth = 480.dp
    val playerWideControlsMinWidth = 240.dp
    val playerWideControlsMaxWidth = 400.dp
    val playerWideLayoutGap = sm
    /** Short viewports use tighter non-touch spacing so the hero and every control still fit. */
    val playerCompactControlsHeight = 640.dp
    /**
     * Shortest Queue/Related sheet that still shows a header and real rows.
     * The sheet asks for a share of the room above the control cluster but
     * never drops below this while that room exists. Because the sheet's height
     * is also how far the Full Player rises for it, this floor is a promise
     * about the panel *and* about the motion, so it is deliberately small
     * enough to fit a short phone's remaining band.
     */
    val queuePanelMinHeight = 280.dp
    /**
     * Band of Full Player that must stay on screen while the Queue/Related
     * sheet is up: enough of the cover, above the metadata and transport that
     * rise with it, that the player still reads as the player rather than as a
     * strip of cropped artwork. Caps the sheet's travel on short viewports.
     */
    val queuePanelPlayerBandFloor = 96.dp
    val playerVolumeMaxWidth = 240.dp
    val playerDiagnosticsMaxHeight = 320.dp
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
    /** Glassy thumb of a horizontal rail's scrollbar (see DhunHorizontalRail). */
    val railScrollbar = 4.dp
    /** Scrub preview bubble: one label line, sized to read at a glance mid-drag. */
    val scrubBubbleHeight = 22.dp
    val border = 1.dp
    val shimmerCorner = 8.dp
}
