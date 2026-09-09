package dev.dhun.design

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * DHUN color tokens — dark-first, glassy, artwork-driven.
 * Every screen uses these; no raw hex values exist outside `design/`.
 *
 * **These are accessors, not constants.** Each one resolves against
 * [DhunAppearance.tokens], the active [DhunTokens] set, so a theme flip
 * reaches every screen that reads `DhunColors.x` without that screen being
 * touched — the `shared/ui` screens read these tokens 274 times and never read
 * `MaterialTheme.colorScheme` at all.
 *
 * Reading outside a composition also works and returns the current values
 * (dark by default), which is what `app-desktop`'s `TrayIcons`
 * object-initialiser and the JVM tests depend on.
 *
 * With the default appearance — [DhunThemeMode.DARK] + [DhunAccent.BRAND] —
 * every value below is exactly the palette that shipped before candidate 28:
 * warm near-black surfaces (0A → 2A) so artwork pops, translucent glass with a
 * 10%-white hairline border, 4-step alpha text, and the static `#BB86FC`
 * accent fallback. The dynamic artwork-derived accent still arrives via
 * [ArtworkColors].
 *
 * The hex values themselves live in [DhunTokens] — this file only names them.
 */
object DhunColors {

    // Surfaces (near-black stack, warm)
    val background: Color get() = DhunAppearance.tokens.background
    val surface: Color get() = DhunAppearance.tokens.surface
    val surfaceVariant: Color get() = DhunAppearance.tokens.surfaceVariant
    val surfaceElevated: Color get() = DhunAppearance.tokens.surfaceElevated
    val surfaceHighest: Color get() = DhunAppearance.tokens.surfaceHighest
    val surfaceCard: Color get() = DhunAppearance.tokens.surfaceCard

    // Glass-morphism tokens (M3 translucent atmosphere — not Liquid Glass).
    // Stack: sheen → highlight → body → deep so backdrops read through.
    /** Upper glass fill (~55% near-black with cool lift; translucent white in light). */
    val glassHighlight: Color get() = DhunAppearance.tokens.glassHighlight
    /** Mid glass body (~62%). */
    val glass: Color get() = DhunAppearance.tokens.glass
    /** Lower glass depth (~72%). */
    val glassDeep: Color get() = DhunAppearance.tokens.glassDeep
    /** Stronger glass for bottom bars / mini-player (~82%). */
    val glassStrong: Color get() = DhunAppearance.tokens.glassStrong
    /** Top edge of docked bars — slightly lighter for separation. */
    val glassBarTop: Color get() = DhunAppearance.tokens.glassBarTop
    /** Specular sheen painted on glass tops (cheap “frost” cue). */
    val glassSheen: Color get() = DhunAppearance.tokens.glassSheen
    /** Hairline edge for frosted chrome. */
    val glassEdge: Color get() = DhunAppearance.tokens.glassEdge

    /** Subtle scrim behind full-bleed artwork (50% black). */
    val scrim: Color get() = DhunAppearance.tokens.scrim
    val scrimStrong: Color get() = DhunAppearance.tokens.scrimStrong

    // Borders
    /** 10% white — hairline glass border (8% black in light). */
    val border: Color get() = DhunAppearance.tokens.border
    val borderStrong: Color get() = DhunAppearance.tokens.borderStrong

    // Text (alpha ladder; white in dark, warm near-black in light)
    val textPrimary: Color get() = DhunAppearance.tokens.textPrimary      // 100%
    val textSecondary: Color get() = DhunAppearance.tokens.textSecondary  // 80% / 75%
    val textTertiary: Color get() = DhunAppearance.tokens.textTertiary    // 60% / 55%
    val textDisabled: Color get() = DhunAppearance.tokens.textDisabled    // 38% / 36%
    val textHint: Color get() = DhunAppearance.tokens.textHint            // 30% / 28%

    // Accent (static fallback; dynamic accent via ArtworkColors)
    val accent: Color get() = DhunAppearance.tokens.accent
    val onAccent: Color get() = DhunAppearance.tokens.onAccent
    val accentContainer: Color get() = DhunAppearance.tokens.accentContainer
    val onAccentContainer: Color get() = DhunAppearance.tokens.onAccentContainer

    // Semantic
    val error: Color get() = DhunAppearance.tokens.error
    val onError: Color get() = DhunAppearance.tokens.onError
    val errorContainer: Color get() = DhunAppearance.tokens.errorContainer
    val success: Color get() = DhunAppearance.tokens.success
    val warning: Color get() = DhunAppearance.tokens.warning
    val borderError: Color get() = DhunAppearance.tokens.borderError

    // Overlays
    val overlayHover: Color get() = DhunAppearance.tokens.overlayHover     // 6%
    val overlayPressed: Color get() = DhunAppearance.tokens.overlayPressed // 8%
    val overlayFocus: Color get() = DhunAppearance.tokens.overlayFocus     // 12%

    // Artwork placeholders
    val placeholderStart: Color get() = DhunAppearance.tokens.placeholderStart
    val placeholderEnd: Color get() = DhunAppearance.tokens.placeholderEnd
    val placeholderPulse: Color get() = DhunAppearance.tokens.placeholderPulse

    // Shimmer
    val shimmerBase: Color get() = DhunAppearance.tokens.shimmerBase
    val shimmerHighlight: Color get() = DhunAppearance.tokens.shimmerHighlight

    /**
     * Material 3 tonal surface ladder. Prefer these over raw hex in
     * new UI — keeps elevation readable without heavy shadows.
     */
    val surfaceContainerLowest: Color get() = DhunAppearance.tokens.surfaceContainerLowest
    val surfaceContainerLow: Color get() = DhunAppearance.tokens.surfaceContainerLow
    val surfaceContainer: Color get() = DhunAppearance.tokens.surfaceContainer
    val surfaceContainerHigh: Color get() = DhunAppearance.tokens.surfaceContainerHigh
    val surfaceContainerHighest: Color get() = DhunAppearance.tokens.surfaceContainerHighest

    /** Material3 scheme derived from the active tokens (single source of truth). */
    val scheme: ColorScheme get() = DhunAppearance.tokens.materialScheme
}
