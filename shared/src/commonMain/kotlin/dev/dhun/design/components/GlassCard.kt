package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.VisualPart

/**
 * Shared content material. In-page cards use the same quiet translucent fill
 * as music rows; dialogs have an opaque base so the page beneath cannot show
 * through input labels. Blur stays on the artwork layer, never on text.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.glass,
    /** Kept for call-site compatibility; blur belongs behind the card. */
    blurRadius: Dp = DhunSpacing.glassBlur,
    contentPadding: androidx.compose.foundation.layout.PaddingValues? = null,
    elevated: Boolean = true,
    tint: Color = Color.Transparent,
    borderColor: Color = DhunColors.glassEdge,
    opaqueBase: Boolean = false,
    /** Use on an existing reading surface to avoid stacking two dark/light fills. */
    subtle: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredBlur = blurRadius
    val base = if (elevated) {
        modifier.shadow(DhunSpacing.sm, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.15f))
    } else modifier
    Box(
        modifier = base
            .clip(shape)
            .background(when {
                // Dialogs stay opaque for reading, but not stark black/white.
                opaqueBase -> DhunAppearance.tokens.surfaceVariant
                subtle -> DhunColors.overlayFocus
                else -> readingSurfaceFill()
            }, shape)
            .then(if (tint.alpha > 0.01f) Modifier.background(tint.copy(alpha = tint.alpha.coerceAtMost(0.20f)), shape) else Modifier)
            .border(BorderStroke(DhunSpacing.border, borderColor), shape),
    ) {
        if (contentPadding != null) {
            Box(modifier = Modifier.padding(contentPadding), content = content)
        } else {
            Box(content = content)
        }
    }
}

/**
 * Continuous bottom dock: its own artwork layer and controlled veils provide
 * navigation chrome; ordinary content panels above it stay neutral.
 */
@Composable
fun GlassDock(
    /** The **raw** current-track thumbnail URL; resolved/tiered by the policy. */
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.bottomSheet,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(DhunSpacing.md, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.28f))
            .clip(shape)
            .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), shape),
    ) {
        // One blur for the whole dock. Each band veils itself so the
        // mini-player can be acrylic and the tabs below it can be the
        // lyrics material, instead of one near-black slab over both.
        LyricsArtworkLayer(artworkUrl = artworkUrl, modifier = Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** Compact chrome shares the same neutral/selected fills as filter chips. */
@Composable
fun FrostedChipSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.chip,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = if (selected) DhunColors.accent else contentRowFill(VisualPart.CHIPS)
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
