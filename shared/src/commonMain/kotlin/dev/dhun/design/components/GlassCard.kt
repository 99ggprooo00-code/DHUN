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
 * GlassCard — DHUN translucent Material 3 surface (atmosphere layer).
 *
 * **Not Liquid Glass** (user lock 2026-09-05 / ADR-002). Tokens only:
 * translucent fill + hairline border + optional one-shot `Modifier.blur`.
 * Real blur where the platform supports it (Android 12+ RenderEffect,
 * Desktop Skiko); older runtimes degrade to translucent scrim + border.
 *
 * Prefer this for chrome over artwork. Prefer plain `surfaceElevated`
 * (no blur) when the *content* must stay razor-sharp (lyrics, chips).
 * Fallback notes: KNOWN_LIMITATIONS.md.
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
