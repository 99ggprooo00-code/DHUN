package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes

/** Theme-aware translucent material for in-page content, not navigation chrome. */
object ContentSurfacePolicy {
    const val CONTENT_OPACITY = 0.74f
    const val READING_OPACITY = 0.82f
}

internal fun contentRowFill(): Color = DhunColors.surface.copy(alpha = ContentSurfacePolicy.CONTENT_OPACITY)
internal fun readingSurfaceFill(): Color =
    (if (DhunAppearance.tokens.isLight) DhunColors.surfaceVariant else DhunColors.surface)
        .copy(alpha = ContentSurfacePolicy.READING_OPACITY)

/**
 * Quiet surface for music rows and groups, readable over blurred artwork in
 * both themes. Apply before clickable so the ripple stays within the shape.
 */
fun Modifier.dhunContentSurface(
    shape: Shape = DhunShapes.large,
    reading: Boolean = false,
): Modifier = this.clip(shape).background(if (reading) readingSurfaceFill() else contentRowFill(), shape)

/** One row shape across Home, Search, Library and browse lists. */
fun Modifier.dhunContentRowSurface(): Modifier = dhunContentSurface(shape = DhunShapes.medium)
