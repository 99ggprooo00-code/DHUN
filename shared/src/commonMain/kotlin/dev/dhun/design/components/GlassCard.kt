package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * Frosted Material 3 surface — **glass-morphism atmosphere**, not Liquid Glass.
 *
 * Recipe (lightweight, battery-safe):
 * 1. Soft elevation shadow
 * 2. Translucent multi-stop fill (what’s behind peeks through)
 * 3. Top-edge highlight hairline (fake specular)
 * 4. Content stays **sharp** — we never `Modifier.blur` the content layer
 *
 * Backdrop blur lives on layers *behind* this surface (FullPlayer artwork,
 * shell ambient wash). Blurring this box would smear titles/lyrics — banned.
 *
 * API 31+/Skiko still get real blur only on those backdrop layers.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.glass,
    /** Kept for call-site compatibility; ignored — content must stay sharp. */
    blurRadius: Dp = DhunSpacing.glassBlur,
    contentPadding: androidx.compose.foundation.layout.PaddingValues? = null,
    elevated: Boolean = true,
    tint: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredBlur = blurRadius

    val base = if (elevated) {
        modifier.shadow(DhunSpacing.sm, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f))
    } else {
        modifier
    }

    Box(
        modifier = base
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DhunColors.glassHighlight,
                        DhunColors.glass,
                        DhunColors.glassDeep,
                    ),
                ),
                shape,
            )
            .then(
                if (tint.alpha > 0.01f) {
                    Modifier.background(tint.copy(alpha = tint.alpha.coerceIn(0f, 0.28f)), shape)
                } else {
                    Modifier
                },
            )
            .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), shape),
    ) {
        // Specular top sheen — cheap glass cue without a blur pass.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to DhunColors.glassSheen,
                            0.22f to Color.Transparent,
                            1.0f to Color.Transparent,
                        ),
                    ),
                ),
        )
        if (contentPadding != null) {
            Box(modifier = Modifier.padding(contentPadding), content = content)
        } else {
            Box(content = content)
        }
    }
}

/**
 * Docked chrome (MiniPlayer, bottom nav): stronger frosted scrim so transport
 * stays legible over Home/library content and ambient washes.
 */
@Composable
fun GlassBottomBar(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.glass,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(DhunSpacing.md, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DhunColors.glassBarTop,
                        DhunColors.glassStrong,
                    ),
                ),
                shape,
            )
            .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), shape),
        content = content,
    )
}

/**
 * Thin frosted panel for chips / compact chrome — translucent, pill-friendly.
 */
@Composable
fun FrostedChipSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.chip,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = if (selected) {
        Brush.horizontalGradient(
            listOf(
                DhunColors.accent.copy(alpha = 0.88f),
                DhunColors.accent.copy(alpha = 0.72f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(DhunColors.glassHighlight, DhunColors.glass),
        )
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(
                BorderStroke(
                    DhunSpacing.border,
                    if (selected) DhunColors.accent.copy(alpha = 0.5f) else DhunColors.glassEdge,
                ),
                shape,
            ),
        content = content,
    )
}
