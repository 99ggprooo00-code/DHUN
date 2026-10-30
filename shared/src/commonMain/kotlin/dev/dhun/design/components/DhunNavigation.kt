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
import dev.dhun.design.background.BackgroundComponent
import dev.dhun.design.background.backgroundComponentTint

/**
 * DHUN navigation chrome: the bottom tab bar and the desktop rail.
 *
 * Material's `NavigationBar`/`NavigationRail` were the last default-Material
 * look left in the shell. Even with `containerColor = Color.Transparent` they
 * still paint Material's own selection pill (`secondaryContainer`), its item
 * paddings and its label metrics — so the one control a user touches on every
 * screen read as stock Material 3 sitting on top of DHUN glass.
 *
 * This pair replaces the whole thing: the containers paint the DHUN glass body
 * ([dhunGlassBody] — gradient and sheen over the dock's frosted artwork) and
 * the items paint their own selection: a soft accent pill with a lit rim,
 * fading in over [SELECTION_MS]. Nothing Material is left in the chrome. What
 * survives is the platform behaviour that must not change: `Role.Tab`,
 * `selectable` semantics (so a screen reader still announces "selected"),
 * one tap per tab, and a full-height hit area.
 *
 * The numbers live here rather than in the shell because the tab bar and the
 * rail have to agree on the pill, and because the rail's width is
 * load-bearing: the two-pane split reserves `DhunShellPolicy.railAllowance`
 * for it, so [railWidth] *is* that value rather than a second opinion.
 */
object DhunNavigationPolicy {

    /** How long the pill takes to come up: fast enough that a tap feels instant. */
    const val SELECTION_MS = 160

    /** Selected pill fill at its lit top edge. Never solid — that is the accent button's job. */
    const val PILL_TOP_ALPHA_DARK = 0.30f
    const val PILL_TOP_ALPHA_LIGHT = 0.20f

    /** …and what it has fallen to at the bottom: the pill is a gradient, not a block. */
    const val PILL_BOTTOM_ALPHA_DARK = 0.10f
    const val PILL_BOTTOM_ALPHA_LIGHT = 0.06f

    /** The hairline that keeps the pill readable over a bright cover. */
    const val RIM_ALPHA_DARK = 0.42f
    const val RIM_ALPHA_LIGHT = 0.30f

    /** The glyph in a tab is the shell's standard icon, not a shrunken one. */
    val iconSize = DhunSpacing.iconSize

    /** The pill behind a tab icon. */
    val pillWidth = 56.dp
    val pillHeight = 30.dp

    /**
     * The rail's width. Equal to [DhunSpacing.bottomNavHeight] because
     * `DhunShellPolicy.railAllowance` reserves exactly that much: a wider rail
     * would eat into the master pane at the two-pane breakpoint.
     */
    val railWidth = DhunSpacing.bottomNavHeight

    fun pillTopAlpha(light: Boolean): Float =
        if (light) PILL_TOP_ALPHA_LIGHT else PILL_TOP_ALPHA_DARK

    fun pillBottomAlpha(light: Boolean): Float =
        if (light) PILL_BOTTOM_ALPHA_LIGHT else PILL_BOTTOM_ALPHA_DARK

    fun rimAlpha(light: Boolean): Float = if (light) RIM_ALPHA_LIGHT else RIM_ALPHA_DARK

    /**
     * The selected pill: the accent, lit at the top and fading down. [progress]
     * fades the whole thing in, so an unselected tab has no pill at all rather
     * than a row of empty outlines.
     */
    fun pillBrush(accent: Color, light: Boolean, progress: Float = 1f): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to accent.copy(alpha = (pillTopAlpha(light) * progress).coerceIn(0f, 1f)),
            1f to accent.copy(alpha = (pillBottomAlpha(light) * progress).coerceIn(0f, 1f)),
        ),
    )

    /** The rim is deliberately stronger than any part of the fill: the edge is what reads. */
    fun rimColor(accent: Color, light: Boolean, progress: Float = 1f): Color =
        accent.copy(alpha = (rimAlpha(light) * progress).coerceIn(0f, 1f))

    /** Unselected chrome is tertiary text; a selected tab promotes it to the accent. */
    fun tint(selected: Boolean, progress: Float): Color =
        lerp(DhunColors.textTertiary, DhunColors.accent, if (selected) progress else 0f)
}

/**
 * The bottom tab bar's glass band. It paints the DHUN body itself so a caller
 * cannot accidentally drop an opaque plate behind the tabs.
 */
@Composable
fun DhunNavigationBar(
    modifier: Modifier = Modifier,
    fill: Color = contentRowFill(VisualPart.NAVIGATION),
    /**
     * The surface whose colour tints this band. The navigation bar is its own
     * customisable surface (Settings → Surface colours), so it carries its
     * colour over the body rather than borrowing the page's or the dock's.
     */
    tintComponent: BackgroundComponent? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val tint = if (tintComponent != null) Modifier.backgroundComponentTint(tintComponent) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DhunSpacing.navigationBarContent)
            .dhunGlassBody(fill)
            .then(tint),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * One bottom tab: icon over label, with the accent pill behind the icon when
 * selected. The whole cell is the target — the pill is decoration.
 */
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

/**
 * The desktop rail: the same material and the same pill as the tab bar,
 * stacked, at the width the pane math already reserves for it.
 */
@Composable
fun DhunNavigationRail(
    modifier: Modifier = Modifier,
    fill: Color = contentRowFill(VisualPart.NAVIGATION),
    /** See [DhunNavigationBar.tintComponent]: the rail is the same surface. */
    tintComponent: BackgroundComponent? = null,
    content: @Composable () -> Unit,
) {
    val tint = if (tintComponent != null) Modifier.backgroundComponentTint(tintComponent) else Modifier
    Column(
        modifier = modifier
            .width(DhunNavigationPolicy.railWidth)
            .fillMaxHeight()
            .dhunGlassBody(fill)
            .then(tint)
            .padding(vertical = DhunSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        content()
    }
}

/** One rail tab — the same treatment as [DhunNavigationBarItem], stacked. */
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

/**
 * The glyph in its pill. Nothing is painted when the tab is unselected, so an
 * unselected tab is an icon on the glass band.
 */
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
        // The label below already names the tab, so the glyph itself is
        // decorative here: reading it again would make every tab announce twice.
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
