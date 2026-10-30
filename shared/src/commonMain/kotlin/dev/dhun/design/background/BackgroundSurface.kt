package dev.dhun.design.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import dev.dhun.design.DhunAnimations

/**
 * Which background component the content being composed belongs to.
 *
 * The page knows because the shell sets it; a dialog or a panel that floats
 * over that page does not, and threading the value through every call site
 * would only invite drift. The shell provides [BackgroundComponent.PAGES] (its
 * only page component) and the player provides its own while it is expanded,
 * so `GlassCard`/`GlassDialog` use the colour and switches of the surface
 * actually behind them.
 */
val LocalBackgroundComponent = staticCompositionLocalOf { BackgroundComponent.PAGES }

/**
 * The share of a palette used to tint a surface that sits *over* a page or the
 * artwork: the dock, the navigation band, the player's chrome.
 */
const val DEFAULT_TINT_ALPHA = 0.22f

/**
 * The tint share when there is **no artwork** behind the surface.
 *
 * Higher, because this tint is then the only thing carrying the component's
 * colour: at 0.22 over near-black a picked colour is invisible, and "I set
 * green and nothing changed" is the exact complaint this feature exists to fix.
 */
const val SOLID_TINT_ALPHA = 0.46f

/**
 * Tints a surface with its component's palette.
 *
 * Full-screen backgrounds are painted by `AmbientBackdrop`; the surfaces that
 * sit *on top* of one (the miniplayer dock, the navigation band, the player's
 * control chrome) cannot paint a full-screen wash, so they take the same
 * palette at low alpha instead. That is what makes the four surfaces read as
 * one colour scheme rather than four unrelated blocks — and it is why the tint
 * is a vertical gradient of the two glows rather than a flat fill.
 *
 * Animated on the same curve as the page: a track change never snaps.
 */
@Composable
fun Modifier.backgroundComponentTint(
    component: BackgroundComponent,
    shape: Shape = RectangleShape,
    alpha: Float? = null,
): Modifier {
    val palette = DhunBackgrounds.paletteFor(component)
    // No artwork behind this surface → the tint is the colour, not a wash.
    val resolvedAlpha = alpha
        ?: if (DhunBackgrounds.showsArtwork(component)) DEFAULT_TINT_ALPHA else SOLID_TINT_ALPHA
    val top by animateColorAsState(
        targetValue = palette.mainGlow.copy(alpha = palette.mainGlow.alpha * resolvedAlpha),
        animationSpec = DhunAnimations.slowTween(),
        label = "componentTintTop",
    )
    val bottom by animateColorAsState(
        targetValue = palette.companionGlow.copy(alpha = palette.companionGlow.alpha * resolvedAlpha),
        animationSpec = DhunAnimations.slowTween(),
        label = "componentTintBottom",
    )
    return background(Brush.verticalGradient(listOf(top, bottom)), shape)
}
