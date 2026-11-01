package dev.dhun.design.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.VisualPart

/** Shared geometry and independently tuned dark/light navigation colors. */
object DhunNavigationPolicy {
    const val SELECTION_MS = 160
    const val PILL_TOP_ALPHA_DARK = 0.30f
    const val PILL_TOP_ALPHA_LIGHT = 0.20f
    const val PILL_BOTTOM_ALPHA_DARK = 0.10f
    const val PILL_BOTTOM_ALPHA_LIGHT = 0.06f
    const val RIM_ALPHA_DARK = 0.42f
    const val RIM_ALPHA_LIGHT = 0.30f

    val iconSize = DhunSpacing.iconSize
    val pillWidth = 56.dp
    val pillHeight = 30.dp

    /** Must remain identical to the width reserved by the two-pane shell. */
    val railWidth = DhunSpacing.bottomNavHeight

    fun pillTopAlpha(light: Boolean): Float =
        if (light) PILL_TOP_ALPHA_LIGHT else PILL_TOP_ALPHA_DARK

    fun pillBottomAlpha(light: Boolean): Float =
        if (light) PILL_BOTTOM_ALPHA_LIGHT else PILL_BOTTOM_ALPHA_DARK

    fun rimAlpha(light: Boolean): Float = if (light) RIM_ALPHA_LIGHT else RIM_ALPHA_DARK

    fun pillBrush(accent: Color, light: Boolean, progress: Float = 1f): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to accent.copy(alpha = (pillTopAlpha(light) * progress).coerceIn(0f, 1f)),
            1f to accent.copy(alpha = (pillBottomAlpha(light) * progress).coerceIn(0f, 1f)),
        ),
    )

    fun rimColor(accent: Color, light: Boolean, progress: Float = 1f): Color =
        accent.copy(alpha = (rimAlpha(light) * progress).coerceIn(0f, 1f))

    fun tint(selected: Boolean, progress: Float): Color =
        lerp(DhunColors.textTertiary, DhunColors.accent, if (selected) progress else 0f)
}

/** Bottom tab band with DHUN body paint and full-height tab targets. */
@Composable
fun DhunNavigationBar(
    modifier: Modifier = Modifier,
    fill: Color = contentRowFill(VisualPart.NAVIGATION),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DhunSpacing.navigationBarContent)
            .dhunGlassBody(fill),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One bottom tab; the pill is decoration and the whole cell is clickable. */
@Composable
fun RowScope.DhunNavigationBarItem(
    icon: DhunIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = rememberSelectionProgress(selected)
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = DhunSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavigationPill(icon = icon, selected = selected, progress = progress)
        Spacer(Modifier.height(DhunSpacing.xs))
        NavigationLabel(text = label, selected = selected, progress = progress)
    }
}

/** Desktop rail at exactly the width reserved by the shell's pane arithmetic. */
@Composable
fun DhunNavigationRail(
    modifier: Modifier = Modifier,
    fill: Color = contentRowFill(VisualPart.NAVIGATION),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(DhunNavigationPolicy.railWidth)
            .fillMaxHeight()
            .dhunGlassBody(fill)
            .padding(vertical = DhunSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        content()
    }
}

@Composable
fun DhunNavigationRailItem(
    icon: DhunIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = rememberSelectionProgress(selected)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = DhunSpacing.smPlus),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavigationPill(icon = icon, selected = selected, progress = progress)
        Spacer(Modifier.height(DhunSpacing.xs))
        NavigationLabel(text = label, selected = selected, progress = progress)
    }
}

@Composable
private fun rememberSelectionProgress(selected: Boolean): Float {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(DhunNavigationPolicy.SELECTION_MS, easing = FastOutSlowInEasing),
        label = "navigation-selection",
    )
    return progress
}

@Composable
private fun NavigationPill(icon: DhunIcon, selected: Boolean, progress: Float) {
    val light = DhunAppearance.tokens.isLight
    Box(
        modifier = Modifier
            .width(DhunNavigationPolicy.pillWidth)
            .height(DhunNavigationPolicy.pillHeight)
            .clip(DhunShapes.navIndicator)
            .background(DhunNavigationPolicy.pillBrush(DhunColors.accent, light, progress))
            .border(
                width = DhunSpacing.divider,
                color = DhunNavigationPolicy.rimColor(DhunColors.accent, light, progress),
                shape = DhunShapes.navIndicator,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The visible label names the tab; do not announce the icon twice.
        DhunIconView(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(DhunNavigationPolicy.iconSize),
            tint = DhunNavigationPolicy.tint(selected = selected, progress = progress),
        )
    }
}

@Composable
private fun NavigationLabel(text: String, selected: Boolean, progress: Float) {
    BasicText(
        text = text,
        style = DhunTypographyTokens.labelSmall.copy(
            color = DhunNavigationPolicy.tint(selected = selected, progress = progress),
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
