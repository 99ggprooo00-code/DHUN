package dev.dhun.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The DHUN theme — dark-first, glass-capable. Every screen is wrapped in
 * this. The color scheme, typography, and shapes are the single source of
 * truth; no screen defines its own.
 *
 * **Appearance (candidate 28).** [mode] and [accent] default to the active
 * [DhunAppearance], which is dark + brand violet unless someone changes it —
 * so every existing `DhunTheme { ... }` call site compiles and renders exactly
 * as it did before, and follows the toggle automatically once something sets
 * it. Passing them explicitly applies that appearance **process-wide** (it is
 * written back to [DhunAppearance]), because DHUN has one theme, not a
 * per-window one; that is deliberate, and it keeps `MaterialTheme` and the
 * [DhunColors] token reads from ever disagreeing — the screens read tokens,
 * not the scheme.
 *
 * The write is a no-op when the requested appearance is already active, so
 * recomposition never churns state.
 */
@Composable
fun DhunTheme(
    mode: DhunThemeMode = DhunAppearance.mode,
    accent: DhunAccent = DhunAppearance.accent,
    content: @Composable () -> Unit,
) {
    DhunAppearance.setAppearance(mode, accent)
    val tokens = remember(mode, accent) { DhunTokens.forAppearance(mode, accent) }
    MaterialTheme(
        colorScheme = tokens.materialScheme,
        typography = DhunTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = DhunShapes.extraSmall,
            small = DhunShapes.small,
            medium = DhunShapes.medium,
            large = DhunShapes.large,
            extraLarge = DhunShapes.extraLarge,
        ),
        content = content,
    )
}
