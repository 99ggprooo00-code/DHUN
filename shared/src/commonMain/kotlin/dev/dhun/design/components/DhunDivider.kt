package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing

/**
 * DHUN's separator: a hairline that fades out at both ends.
 *
 * `HorizontalDivider(color = DhunColors.border)` was doing the job, and it was
 * never *wrong* — the colour is DHUN's. It was flat, though: an evenly weighted
 * line touching both edges of the panel, which is the one stroke that reads as
 * a Material outline sitting inside otherwise layered glass. The same line,
 * tapered, reads as light falling off a glass edge instead
 * (`docs/design/glass-system.md` §1, the gradient-hairline cue).
 *
 * There is no vertical variant on purpose: the app has no vertical separators,
 * and an unused API is one more thing a future screen can misuse.
 */
@Composable
fun DhunDivider(modifier: Modifier = Modifier) {
    // The middle is the glass edge token, so the line has the same weight as
    // every panel border in the app.
    val edge = DhunColors.glassEdge
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DhunSpacing.divider)
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        DIVIDER_SOLID_START to edge,
                        DIVIDER_SOLID_END to edge,
                        1f to Color.Transparent,
                    ),
                ),
            ),
    )
}

/**
 * Where the hairline reaches full strength. Kept short of the edges but not at
 * the very ends: a line that is at full strength in the middle third and gone
 * at the corners is what separates "tapered" from "broken in two".
 */
private const val DIVIDER_SOLID_START = 0.18f
private const val DIVIDER_SOLID_END = 0.82f
