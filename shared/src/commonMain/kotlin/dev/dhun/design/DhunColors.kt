package dev.dhun.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * DHUN color tokens — dark-first, glassy, artwork-driven.
 * Every screen uses these; no raw hex values exist outside this file.
 *
 * Surfaces are warm near-black (0A → 2A) so artwork pops. Glass is
 * translucent (60% #111111) with a 10%-white hairline border. Text is
 * 4-step alpha. Accent is a static #BB86FC fallback — the dynamic
 * artwork-derived accent arrives via ArtworkColors.
 */
object DhunColors {
    // Surfaces (near-black stack, warm)
    val background = Color(0xFF0A0A0A)
    val surface = Color(0xFF121212)
    val surfaceVariant = Color(0xFF1A1A1A)
    val surfaceElevated = Color(0xFF242424)
    val surfaceHighest = Color(0xFF2A2A2A)
    val surfaceCard = Color(0xFF1E1E1E)

    // Glass (frosted surfaces)
    /** 60% #111111 — the primary glass fill (see GlassCard). */
    val glass = Color(0x99111111)
    /** Stronger glass for bottom bars / mini-player (80% #1A1A1A). */
    val glassStrong = Color(0xCC1A1A1A)
    /** Subtle scrim behind full-bleed artwork (50% black). */
    val scrim = Color(0x80000000)
    val scrimStrong = Color(0xCC000000)

    // Borders
    /** 10% white — hairline glass border. */
    val border = Color(0x1AFFFFFF)
    val borderStrong = Color(0x33FFFFFF)

    // Text (4-step alpha on white)
    val textPrimary = Color(0xFFFFFFFF)      // 100%
    val textSecondary = Color(0xCCFFFFFF)    // 80%
    val textTertiary = Color(0x99FFFFFF)     // 60%
    val textDisabled = Color(0x61FFFFFF)     // 38%
    val textHint = Color(0x4DFFFFFF)         // 30%

    // Accent (static fallback; dynamic accent via ArtworkColors)
    val accent = Color(0xFFBB86FC)
    val onAccent = Color(0xFF000000)
    val accentContainer = Color(0xFF3A2A5A)
    val onAccentContainer = Color(0xFFE8D5FF)

    // Semantic
    val error = Color(0xFFCF6679)
    val onError = Color(0xFF000000)
    val errorContainer = Color(0xFF4D1A24)
    val success = Color(0xFF4CAF50)
    val warning = Color(0xFFFFB74D)
    val borderError = Color(0x40CF6679)

    // Overlays
    val overlayHover = Color(0x0FFFFFFF) // 6% white
    val overlayPressed = Color(0x14FFFFFF) // 8%
    val overlayFocus = Color(0x1FFFFFFF) // 12%

    // Artwork placeholders
    val placeholderStart = Color(0xFF1A1A1A)
    val placeholderEnd = Color(0xFF2A2A2A)
    val placeholderPulse = Color(0xFF333333)

    // Shimmer
    val shimmerBase = Color(0xFF1E1E1E)
    val shimmerHighlight = Color(0xFF2E2E2E)

    /** Material3 dark scheme derived from the tokens (single source of truth). */
    val scheme: ColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surfaceElevated,
        surfaceContainerHigh = surfaceHighest,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        outline = borderStrong,
        outlineVariant = border,
        scrim = scrim,
    )
}
