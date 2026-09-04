package dev.dhun.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The DHUN theme — dark-first, glass-capable. Every screen is wrapped in
 * this. The color scheme, typography, and shapes are the single source of
 * truth; no screen defines its own.
 */
@Composable
fun DhunTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DhunColors.scheme,
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
