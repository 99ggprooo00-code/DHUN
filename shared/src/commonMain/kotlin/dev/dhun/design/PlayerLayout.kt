package dev.dhun.design

import androidx.compose.ui.unit.Dp

/** Both axes constrain the square; a wide window must never paint artwork over controls. */
internal fun fittedPlayerArtworkSize(availableWidth: Dp, availableHeight: Dp): Dp =
    minOf(availableWidth, availableHeight, DhunSpacing.playerArtworkMaxSize)
        .coerceAtLeast(DhunSpacing.zero)
