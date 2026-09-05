package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * GlassCard — the DHUN signature surface.
 *
 * Real blur where the platform supports it (Android 12+ via RenderEffect,
 * Desktop Skiko). Below that floor we gracefully degrade to a translucent
 * scrim + hairline border — still glassy, never a solid card.
 *
 * Blur is applied to the *background* layer so the content stays sharp
 * while the artwork behind the card appears frosted. The fallback is
 * flagged in KNOWN_LIMITATIONS.md.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.glass,
    blurRadius: androidx.compose.ui.unit.Dp = DhunSpacing.glassBlur,
    contentPadding: androidx.compose.foundation.layout.PaddingValues? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val outer = modifier
        .clip(shape)
        // Glass fill + hairline border + real blur when available.
        // `Modifier.blur` maps to RenderEffect on API 31+ and Skiko; on older
        // runtimes it becomes a no-op — the translucent fill still reads as glass.
        .background(DhunColors.glass, shape)
        .blur(blurRadius)
        .border(BorderStroke(DhunSpacing.border, DhunColors.border), shape)

    if (contentPadding != null) {
        Box(modifier = outer.padding(contentPadding), content = content)
    } else {
        Box(modifier = outer, content = content)
    }
}

/**
 * Glass surface used for bottom bars (MiniPlayer, bottom nav): stronger scrim
 * so controls stay legible over artwork.
 */
@Composable
fun GlassBottomBar(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.glass,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(DhunColors.glassStrong, shape)
            .border(BorderStroke(DhunSpacing.divider, DhunColors.border), shape),
        content = content,
    )
}
