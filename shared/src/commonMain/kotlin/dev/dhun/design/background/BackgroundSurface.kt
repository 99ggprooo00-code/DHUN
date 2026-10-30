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
 * Which background section the content being composed belongs to.
 *
 * The page backdrop knows its section because the shell computes it; a dialog or
 * a panel that floats over that page does not, and threading the value through
 * every call site would only invite drift. The shell provides it once (Home,
 * Search or Library) and the player provides its own while it is expanded, so
 * `GlassCard`/`GlassDialog` frost with the switches of the surface actually
 * behind them.
 */
val LocalBackgroundSection = staticCompositionLocalOf { BackgroundSection.HOME }

/**
 * Tints a surface with its section's palette.
 *
 * The page backgrounds are painted by `AmbientBackdrop`; the surfaces that sit
 * *on top* of a page (the miniplayer dock, the standalone player card, the
 * player's control chrome) cannot paint a full-screen wash, so they take the
 * same palette at low alpha instead. That is what keeps Home, Search, Library,
 * the miniplayer and the full player reading as one palette rather than five
 * unrelated colour blocks — and it is why the tint is a vertical gradient of
 * the section's two bloom colours rather than a flat fill.
 *
 * Animated on the same curve as the page: a track change never snaps.
 */
@Composable
fun Modifier.backgroundSectionTint(
    section: BackgroundSection,
    shape: Shape = RectangleShape,
    alpha: Float = DEFAULT_TINT_ALPHA,
): Modifier {
    val palette = DhunBackgrounds.paletteFor(section)
    val top by animateColorAsState(
        targetValue = palette.secondary.copy(alpha = palette.secondary.alpha * alpha),
        animationSpec = DhunAnimations.slowTween(),
        label = "sectionTintTop",
    )
    val bottom by animateColorAsState(
        targetValue = palette.tertiary.copy(alpha = palette.tertiary.alpha * alpha),
        animationSpec = DhunAnimations.slowTween(),
        label = "sectionTintBottom",
    )
    return background(Brush.verticalGradient(listOf(top, bottom)), shape)
}

/**
 * How much of the palette survives onto a surface that also carries content.
 * Low: this is a tint over glass, not a second background — text sits on top of
 * it, and the contrast gates were measured against the glass fill underneath.
 */
const val DEFAULT_TINT_ALPHA = 0.22f
