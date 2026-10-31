package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunVisualTuning
import dev.dhun.design.VisualPart

/**
 * In-page glass, not the Queue/Related sheet material. The old near-black
 * surface / pure-white surface fills made every row a dark or white tile on
 * top of the blurred artwork. Use the middle of each theme's tonal ladder
 * instead, with enough opacity to keep text readable even over bright art.
 */
object ContentSurfacePolicy {
    const val CONTENT_OPACITY_DARK = 0.82f
    const val CONTENT_OPACITY_LIGHT = 0.83f
    const val READING_OPACITY_DARK = 0.88f
    const val READING_OPACITY_LIGHT = 0.89f
}

internal fun contentRowFill(part: VisualPart = VisualPart.LIST_ROWS): Color {
    val light = DhunAppearance.tokens.isLight
    val base = if (light) ContentSurfacePolicy.CONTENT_OPACITY_LIGHT else ContentSurfacePolicy.CONTENT_OPACITY_DARK
    val alpha = DhunVisualTuning.alpha(part, base, if (light) 0.78f else 0.75f, 0.98f)
    val tint = if (light) DhunColors.surfaceContainerHigh else DhunColors.surfaceElevated
    return tint.copy(alpha = tint.alpha * alpha)
}

internal fun readingSurfaceFill(part: VisualPart = VisualPart.READING): Color {
    val light = DhunAppearance.tokens.isLight
    val base = if (light) ContentSurfacePolicy.READING_OPACITY_LIGHT else ContentSurfacePolicy.READING_OPACITY_DARK
    val alpha = DhunVisualTuning.alpha(part, base, 0.82f, 0.99f)
    val tint = if (light) DhunColors.surfaceContainerHighest else DhunColors.surfaceElevated
    return tint.copy(alpha = tint.alpha * alpha)
}

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
