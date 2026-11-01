package dev.dhun.design.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Edge-feather masks — the shared way DHUN lets content melt into whatever is
 * behind it instead of sitting on it as a framed card.
 *
 * Both modifiers rasterise the node into an offscreen layer
 * ([CompositingStrategy.Offscreen]) and then multiply the layer's alpha by a
 * gradient mask with [BlendMode.DstIn]: alpha 0 at the edge, 1 past the
 * feather distance. Because only the *alpha* of the node itself is touched,
 * what shows through the faded rim is exactly what is behind it — for the
 * now-playing hero that is the once-blurred artwork bleed, so the sharp cover
 * and the background become one continuous surface; for the lyrics list the
 * first and last lines dissolve into the player backdrop instead of stopping
 * at a clipped edge.
 *
 * Cost: one offscreen buffer for the node, no per-frame recomposition (the
 * mask is drawn, not composed). That is the same order of expense as the
 * blur the player already runs once per track, and it replaces the rounded
 * border + scrim "card" treatment the hero used to wear.
 *
 * The mask is clamped to half of the smaller axis so a degenerate or tiny
 * node fades to nothing gracefully instead of producing overlapping gradients
 * that would invert the alpha ramp.
 */
fun Modifier.featherEdges(feather: Dp): Modifier = featherMask(feather, horizontal = true, vertical = true)

/** Top/bottom-only fade, for scrolling columns (lyrics) over a backdrop. */
fun Modifier.fadeVerticalEdges(feather: Dp): Modifier = featherMask(feather, horizontal = false, vertical = true)

private fun Modifier.featherMask(feather: Dp, horizontal: Boolean, vertical: Boolean): Modifier =
    if (feather <= Dp.Hairline) this else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val featherPx = feather.toPx()
                .coerceIn(0f, minOf(size.width, size.height) / 2f)
            if (featherPx <= 0f || size.width <= 0f || size.height <= 0f) return@drawWithContent
            if (vertical) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = maskRamp,
                        startY = 0f,
                        endY = featherPx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = maskRampReversed,
                        startY = size.height - featherPx,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (horizontal) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = maskRamp,
                        startX = 0f,
                        endX = featherPx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = maskRampReversed,
                        startX = size.width - featherPx,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/** DstIn mask ramps: only the alpha channel is significant. */
private val maskRamp = listOf(Color.Transparent, Color.Black)
private val maskRampReversed = listOf(Color.Black, Color.Transparent)
