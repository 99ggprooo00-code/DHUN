package dev.dhun.design

import androidx.compose.ui.unit.dp

/**
 * Spatial scale — every layout uses these; no raw `dp` literals outside
 * `shared/design/`.
 */
object DhunSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    // Semantic aliases
    val screenPadding = lg
    val cardPadding = md
    val sectionSpacing = xxl
    val itemSpacing = sm
    val iconSize = 24.dp
    val iconSizeSm = 18.dp
    val iconSizeLg = 32.dp
    val artworkThumb = 56.dp
    val artworkCard = 160.dp
    val artworkLarge = 280.dp
    val miniPlayerHeight = 72.dp
    val bottomNavHeight = 80.dp
    val glassBlur = 16.dp
    val progressHeight = 4.dp
    val progressHeightActive = 8.dp
    val divider = 1.dp
    val border = 1.dp
    val shimmerCorner = 8.dp
}
