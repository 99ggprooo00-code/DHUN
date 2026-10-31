package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.VisualPart

/**
 * Shared DHUN glass for cards, reading groups and high-opacity dialogs.
 *
 * Artwork and any supported blur stay behind this component; its body gradient,
 * tint and sheen remain sharp with the content. [opaqueBase] is retained for
 * call-site compatibility, but means the policy's high-opacity dialog glass,
 * not a flat opaque rectangle. With no artwork or no real blur, the same body,
 * sheen and edge still provide a complete fallback.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.glass,
    /** Kept for call-site compatibility; blur belongs behind the card. */
    blurRadius: Dp = DhunSpacing.glassBlur,
    contentPadding: PaddingValues? = null,
    elevated: Boolean = true,
    tint: Color = Color.Transparent,
    borderColor: Color = DhunColors.glassEdge,
    opaqueBase: Boolean = false,
    /** Use on an existing reading surface to avoid stacking two tonal fills. */
    subtle: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredBlur = blurRadius
    val light = DhunAppearance.tokens.isLight
    val body = when {
        opaqueBase -> GlassMaterialPolicy.dialogBody(light, DhunAppearance.tokens.surfaceVariant)
        subtle -> DhunColors.overlayFocus
        else -> GlassMaterialPolicy.contentBody(readingSurfaceFill())
    }
    val bodyBrush = remember(body, light) { glassBodyBrush(body, light) }
    val sheenBrush = remember(light, opaqueBase) { glassSheenBrush(light, content = !opaqueBase) }
    val edgeBrush = remember(borderColor, light) { glassEdgeBrush(borderColor, light) }
    val outer = if (elevated) {
        modifier.shadow(
            DhunSpacing.sm,
            shape,
            clip = false,
            ambientColor = GlassMaterialPolicy.cardShadowColor(light),
        )
    } else {
        modifier
    }

    Box(modifier = outer.clip(shape).border(DhunSpacing.border, edgeBrush, shape)) {
        Box(modifier = Modifier.matchParentSize().background(bodyBrush, shape))
        if (tint.alpha > 0.01f) {
            Box(
                modifier = Modifier.matchParentSize().background(
                    tint.copy(alpha = tint.alpha.coerceAtMost(0.20f)),
                    shape,
                ),
            )
        }
        Box(modifier = Modifier.matchParentSize().background(sheenBrush, shape))
        if (contentPadding != null) {
            Box(modifier = Modifier.padding(contentPadding), content = content)
        } else {
            Box(content = content)
        }
    }
}

/**
 * Paint-only glass body for floating chrome which already owns its edge.
 * Use selectively; ordinary music rows keep the quieter content-row surface.
 */
@Composable
fun Modifier.dhunGlassBody(fill: Color, shape: Shape = RectangleShape): Modifier {
    val light = DhunAppearance.tokens.isLight
    val body = GlassMaterialPolicy.contentBody(fill)
    val bodyBrush = remember(body, light) { glassBodyBrush(body, light) }
    val sheenBrush = remember(light) { glassSheenBrush(light, content = true) }
    return background(bodyBrush, shape).background(sheenBrush, shape)
}

/**
 * Continuous bottom dock. One artwork layer serves its child bands; the dock
 * adds no second body fill beneath their own materials.
 */
@Composable
fun GlassDock(
    /** The raw current-track thumbnail URL; resolved/tiered by the artwork policy. */
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.bottomSheet,
    content: @Composable ColumnScope.() -> Unit,
) {
    val light = DhunAppearance.tokens.isLight
    val edgeColor = DhunColors.glassEdge
    val edgeBrush = remember(edgeColor, light) { glassEdgeBrush(edgeColor, light) }
    Box(
        modifier = modifier
            .shadow(
                DhunSpacing.md,
                shape,
                clip = false,
                ambientColor = GlassMaterialPolicy.dockShadowColor(light),
            )
            .clip(shape)
            .border(DhunSpacing.border, edgeBrush, shape),
    ) {
        LyricsArtworkLayer(artworkUrl = artworkUrl, modifier = Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Compact chrome shares the neutral/selected fills as filter chips. Selected
 * chips stay a clear accent fill; unselected chips use the quiet glass body.
 * Their small size does not justify another artwork blur or specular layer.
 */
@Composable
fun FrostedChipSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.chip,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val light = DhunAppearance.tokens.isLight
    val fill = if (selected) DhunColors.accent else contentRowFill(VisualPart.CHIPS)
    val bodyBrush = remember(fill, light) { glassBodyBrush(fill, light) }
    val edgeColor = if (selected) DhunColors.accent.copy(alpha = 0.5f) else DhunColors.glassEdge
    val edgeBrush = remember(edgeColor, light) { glassEdgeBrush(edgeColor, light) }
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(fill, shape).border(DhunSpacing.border, edgeColor, shape)
                } else {
                    Modifier.background(bodyBrush, shape).border(DhunSpacing.border, edgeBrush, shape)
                },
            ),
        content = content,
    )
}

private fun glassBodyBrush(fill: Color, light: Boolean): Brush =
    Brush.verticalGradient(*GlassMaterialPolicy.bodyStops(fill, light).toTypedArray())

private fun glassSheenBrush(light: Boolean, content: Boolean): Brush {
    val stops = if (content) {
        GlassMaterialPolicy.contentSheenStops(light)
    } else {
        GlassMaterialPolicy.sheenStops(light)
    }
    return Brush.verticalGradient(
        *stops.map { (offset, alpha) -> offset to Color.White.copy(alpha = alpha) }.toTypedArray(),
    )
}

private fun glassEdgeBrush(edge: Color, light: Boolean): Brush = Brush.verticalGradient(
    *GlassMaterialPolicy.edgeStops(light)
        .map { (offset, factor) ->
            offset to edge.copy(alpha = (edge.alpha * factor).coerceIn(0f, 1f))
        }
        .toTypedArray(),
)
