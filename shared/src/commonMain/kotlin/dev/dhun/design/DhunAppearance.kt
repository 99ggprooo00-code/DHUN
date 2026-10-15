package dev.dhun.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Candidate 28 — themes beyond dark-first.
 *
 * DHUN is dark-first and stays that way: [DhunThemeMode.DARK] + [DhunAccent.BRAND]
 * is the default. It was byte-for-byte the palette that shipped before this
 * file existed until the 2026-09-21 UI-polish pass lifted the dark *surface*
 * ladder one step (each rung +0x0C) so the shell screens read less near-black;
 * the glass, text, scrim and accent tokens are untouched. What this file adds
 * is (a) a **light** scheme built from the same
 * token names, and (b) an **accent selector**, both reachable through one
 * observable holder, [DhunAppearance].
 *
 * ## Why the tokens had to move, not just the `ColorScheme`
 *
 * Screens read `DhunColors.textPrimary` and friends as **plain object
 * properties** — 416 reads repo-wide, 274 of them under the `shared/ui`
 * screens — and `MaterialTheme.colorScheme` is read **nowhere** in them. A
 * light
 * `ColorScheme` alone would therefore have compiled green and changed nothing
 * on screen. So [DhunColors] became a set of *accessors* over the active
 * [DhunTokens], and [DhunAppearance] holds that active set in Compose
 * snapshot state:
 *
 * - a read **inside** composition subscribes to the state, so flipping the
 *   toggle recomposes every token reader without touching a single screen;
 * - a read **outside** composition still works and returns the current
 *   values, which is what `app-desktop`'s `TrayIcons` object-initialiser and
 *   the JVM tests rely on.
 *
 * ## Known limits (honest, not aspirational)
 *
 * 1. **One appearance per process, not per window.** [DhunAppearance] is a
 *    process-wide holder, so desktop's startup-error window and main window
 *    share a theme. That is the intended behaviour for a toggle; it is stated
 *    here because it is a real constraint, not an omission.
 * 2. **Persistence is host-owned, via [applyPersistedAppearance].** The store is
 *    `dev.dhun.data.SettingsRepository.getString/putString` (SQLDelight-backed):
 *    `SettingsKeys.THEME` and `SettingsKeys.ACCENT`. `design` must not depend
 *    on `data` — that would invert the layers — so the platform entry points
 *    read the two keys at startup and call [applyPersistedAppearance], and the
 *    Settings screen persists every change. Unknown or corrupt ids fall back
 *    to the defaults instead of crashing.
 * 3. **`TrayIcons` keeps the dark palette**: it reads tokens at object-init,
 *    before any composition exists. Fixing that means deferring the read until
 *    first tray paint; cosmetic, deferred past v0.1.0 with the rest of UI polish.
 */

/**
 * Light or dark. `DARK` is the default — the palette DHUN shipped with.
 *
 * System is a preference rather than a token palette: [DhunTheme]
 * resolves it through the platform's [androidx.compose.foundation.isSystemInDarkTheme].
 * [fromId] intentionally accepts only concrete palette ids.
 */
enum class DhunThemeMode(
    /** Stable persistence key — survives a display-name change. */
    val id: String,
    /** Short label for the toggle UI. */
    val label: String,
) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light"),
    ;

    companion object {
        val default: DhunThemeMode = DARK

        /** `null` for an unknown/absent key, so a corrupt pref falls back rather than crashing. */
        fun fromId(id: String?): DhunThemeMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The four accent roles a scheme needs, tuned per [DhunThemeMode]: the same
 * hue reads very differently on near-black and on paper white, so each accent
 * carries its own dark and light ramp instead of one colour being reused.
 */
class AccentRamp(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Selectable accents. [BRAND] is the default and its dark ramp is exactly the
 * historical `#BB86FC` set, so choosing nothing changes nothing.
 *
 * Every ramp satisfies, in both modes (pinned by `DhunAppearanceTest`):
 * `onAccent` vs `accent` ≥ 4.5:1 and `onContainer` vs `container` ≥ 4.5:1
 * (WCAG AA for text), and `accent` vs the mode's surface and background
 * ≥ 3:1 (WCAG 1.4.11 for non-text UI — the play disc, progress bar, active
 * tab indicator).
 */
enum class DhunAccent(
    /** Stable persistence key — survives a display-name change. */
    val id: String,
    /** Label shown in the accent picker. */
    val label: String,
    val dark: AccentRamp,
    val light: AccentRamp,
) {
    BRAND(
        id = "brand",
        label = "Violet",
        dark = AccentRamp(
            accent = Color(0xFFBB86FC),
            onAccent = Color(0xFF000000),
            container = Color(0xFF3A2A5A),
            onContainer = Color(0xFFE8D5FF),
        ),
        light = AccentRamp(
            accent = Color(0xFF6750A4),
            onAccent = Color(0xFFFFFFFF),
            container = Color(0xFFEADDFF),
            onContainer = Color(0xFF21005D),
        ),
    ),
    AZURE(
        id = "azure",
        label = "Azure",
        dark = AccentRamp(
            accent = Color(0xFF7FB3FF),
            onAccent = Color(0xFF000000),
            container = Color(0xFF12314F),
            onContainer = Color(0xFFD6E6FF),
        ),
        light = AccentRamp(
            accent = Color(0xFF0B57D0),
            onAccent = Color(0xFFFFFFFF),
            container = Color(0xFFD3E3FD),
            onContainer = Color(0xFF041E49),
        ),
    ),
    JADE(
        id = "jade",
        label = "Jade",
        dark = AccentRamp(
            accent = Color(0xFF79E0B4),
            onAccent = Color(0xFF000000),
            container = Color(0xFF0E3B2C),
            onContainer = Color(0xFFCFF5E4),
        ),
        light = AccentRamp(
            accent = Color(0xFF00694A),
            onAccent = Color(0xFFFFFFFF),
            container = Color(0xFFB8F2D6),
            onContainer = Color(0xFF00211A),
        ),
    ),
    AMBER(
        id = "amber",
        label = "Amber",
        dark = AccentRamp(
            accent = Color(0xFFFFC46B),
            onAccent = Color(0xFF000000),
            container = Color(0xFF4E3311),
            onContainer = Color(0xFFFFE8C7),
        ),
        light = AccentRamp(
            accent = Color(0xFF8A5300),
            onAccent = Color(0xFFFFFFFF),
            container = Color(0xFFFFDCBE),
            onContainer = Color(0xFF2E1500),
        ),
    ),
    ROSE(
        id = "rose",
        label = "Rose",
        dark = AccentRamp(
            accent = Color(0xFFFF9EC0),
            onAccent = Color(0xFF000000),
            container = Color(0xFF4E1C31),
            onContainer = Color(0xFFFFD9E6),
        ),
        light = AccentRamp(
            accent = Color(0xFFA63A63),
            onAccent = Color(0xFFFFFFFF),
            container = Color(0xFFFFD9E3),
            onContainer = Color(0xFF3E001D),
        ),
    ),
    CYAN(
        id = "cyan",
        label = "Cyan",
        dark = AccentRamp(
            accent = Color(0xFF6FE3F0),
            onAccent = Color(0xFF000000),
            container = Color(0xFF0D3F4A),
            onContainer = Color(0xFFCFF6FF),
        ),
        light = AccentRamp(
            accent = Color(0xFF00668B),
            onAccent = Color(0xFFFFFFFF),
            container = Color(0xFFC2E8FF),
            onContainer = Color(0xFF001E2C),
        ),
    ),
    ;

    companion object {
        val default: DhunAccent = BRAND

        /** `null` for an unknown/absent key, so a corrupt pref falls back rather than crashing. */
        fun fromId(id: String?): DhunAccent? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One complete, immutable colour token set. Every member maps 1:1 onto a
 * [DhunColors] accessor — this is the only file in the repo allowed to hold
 * raw hex values.
 *
 * The default constructor is the dark palette as of the 2026-09-21 UI-polish
 * lift (surfaces +0x0C per rung; glass/text/accents as shipped), so
 * `DhunTokens()` remains the regression baseline; the light set is
 * [DhunTokens.light], written out in full rather than patched onto the dark
 * one so no dark value can leak through by omission.
 */
data class DhunTokens(
    /** Selects `lightColorScheme()` as the Material3 base and flips a few token-derived rules. */
    val isLight: Boolean = false,

    // Surfaces (dark: warm lifted stack — the 2026-09-21 UI-polish pass moved
    // every rung up 0x0C from the shipped 0A→2A ladder so Home/Search/Library
    // sit closer to the full player's backdrop brightness; WCAG gates in
    // DhunThemeContrastTest still pass, and the control-accent floor in
    // ArtworkColors was retuned 0.42→0.45 to match).
    // Do not lift another rung: artwork-derived controls are 3.12:1 on
    // #1E1E1E, and #222222 drops the worst case to 2.97:1 (under WCAG 1.4.11).
    // Further brightness belongs on the artwork scrims, not these hexes.
    val background: Color = Color(0xFF161616),
    val surface: Color = Color(0xFF1E1E1E),
    val surfaceVariant: Color = Color(0xFF262626),
    val surfaceElevated: Color = Color(0xFF303030),
    val surfaceHighest: Color = Color(0xFF363636),
    val surfaceCard: Color = Color(0xFF2A2A2A),

    // Glass-morphism (M3 translucent atmosphere — not Liquid Glass).
    val glassHighlight: Color = Color(0x5E20202A),
    val glass: Color = Color(0x7016161E),
    val glassDeep: Color = Color(0x8A0E0E14),
    val glassStrong: Color = Color(0xB8121218),
    val glassBarTop: Color = Color(0xC81C1C26),
    val glassSheen: Color = Color(0x1FFFFFFF),
    val glassEdge: Color = Color(0x38FFFFFF),

    val scrim: Color = Color(0x80000000),
    val scrimStrong: Color = Color(0xCC000000),

    // Borders
    val border: Color = Color(0x1AFFFFFF),
    val borderStrong: Color = Color(0x33FFFFFF),

    // Text — a 5-step alpha ladder
    val textPrimary: Color = Color(0xFFFFFFFF),
    val textSecondary: Color = Color(0xCCFFFFFF),
    val textTertiary: Color = Color(0x99FFFFFF),
    val textDisabled: Color = Color(0x61FFFFFF),
    val textHint: Color = Color(0x4DFFFFFF),

    // Accent
    val accent: Color = Color(0xFFBB86FC),
    val onAccent: Color = Color(0xFF000000),
    val accentContainer: Color = Color(0xFF3A2A5A),
    val onAccentContainer: Color = Color(0xFFE8D5FF),

    // Semantic
    // S5: #D5798A (was #CF6679) — the old error on this container measured
    // 3.92:1, below WCAG AA; the retune measures 4.66:1 with the same hue.
    val error: Color = Color(0xFFD5798A),
    val onError: Color = Color(0xFF000000),
    val errorContainer: Color = Color(0xFF4D1A24),
    val success: Color = Color(0xFF4CAF50),
    val warning: Color = Color(0xFFFFB74D),
    val borderError: Color = Color(0x40D5798A),

    // Overlays
    val overlayHover: Color = Color(0x0FFFFFFF),
    val overlayPressed: Color = Color(0x14FFFFFF),
    val overlayFocus: Color = Color(0x1FFFFFFF),

    // Artwork placeholders
    val placeholderStart: Color = Color(0xFF262626),
    val placeholderEnd: Color = Color(0xFF363636),
    val placeholderPulse: Color = Color(0xFF3F3F3F),

    // Shimmer
    val shimmerBase: Color = Color(0xFF2A2A2A),
    val shimmerHighlight: Color = Color(0xFF3A3A3A),

    // Material 3 tonal surface ladder
    val surfaceContainerLowest: Color = Color(0xFF161616),
    val surfaceContainerLow: Color = Color(0xFF1E1E1E),
    val surfaceContainer: Color = Color(0xFF303030),
    val surfaceContainerHigh: Color = Color(0xFF363636),
    val surfaceContainerHighest: Color = Color(0xFF3C3C3C),

    /** Tertiary role — scheme-only in dark (teal); kept as a token so light can retune it. */
    val tertiary: Color = Color(0xFF80CBC4),
    val onTertiary: Color = Color(0xFF003732),
) {

    /**
     * The Material3 scheme for this token set, derived from the tokens so
     * there is exactly one source of truth. Built on the matching M3 base and
     * then `.copy()`-ed, so every role not set here keeps its M3 default —
     * identical to the previous `darkColorScheme(...)` construction.
     */
    // A token set is immutable; scheme creation is synchronized so a first
    // read from Skiko and a background observer cannot initialize it twice.
    val materialScheme: ColorScheme by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val base = if (isLight) lightColorScheme() else darkColorScheme()
        base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = accent.copy(alpha = 0.85f),
            onSecondary = onAccent,
            secondaryContainer = accentContainer.copy(alpha = 0.7f),
            onSecondaryContainer = onAccentContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            outline = borderStrong,
            outlineVariant = border,
            scrim = scrim,
        )
    }

    /**
     * The accent roles swapped for [ramp]; everything else is untouched.
     *
     * Returns `this` when [ramp] already matches, so the shipped default
     * (dark + brand) never allocates and identity survives a round trip —
     * which is what makes "nothing changed" observable instead of merely
     * equal-by-value.
     */
    fun withAccent(ramp: AccentRamp): DhunTokens = if (
        ramp.accent == accent &&
        ramp.onAccent == onAccent &&
        ramp.container == accentContainer &&
        ramp.onContainer == onAccentContainer
    ) {
        this
    } else {
        copy(
            accent = ramp.accent,
            onAccent = ramp.onAccent,
            accentContainer = ramp.container,
            onAccentContainer = ramp.onContainer,
        )
    }

    companion object {
        /** The dark palette as of the 2026-09-21 UI-polish lift — still the default. */
        val dark: DhunTokens = DhunTokens()

        /**
         * Warm paper-white light scheme. Surfaces are warm (not pure white)
         * so artwork still drives the room; glass flips to translucent white
         * with a black hairline; the text ladder is a dark alpha ladder with
         * the same five steps, so every screen keeps its hierarchy.
         */
        val light: DhunTokens = DhunTokens(
            isLight = true,
            background = Color(0xFFF6F4F1),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEFECE8),
            surfaceElevated = Color(0xFFFFFFFF),
            surfaceHighest = Color(0xFFE9E5E0),
            surfaceCard = Color(0xFFFFFFFF),
            glassHighlight = Color(0x66FFFFFF),
            glass = Color(0x7AFFFFFF),
            glassDeep = Color(0x8CF7F5F2),
            glassStrong = Color(0xC4FFFFFF),
            glassBarTop = Color(0xD6FFFFFF),
            glassSheen = Color(0x33FFFFFF),
            glassEdge = Color(0x29000000),
            scrim = Color(0x66000000),
            scrimStrong = Color(0xB3000000),
            border = Color(0x14000000),
            borderStrong = Color(0x2E000000),
            textPrimary = Color(0xF21A1714),
            textSecondary = Color(0xBF1A1714),
            textTertiary = Color(0x8C1A1714),
            textDisabled = Color(0x5C1A1714),
            textHint = Color(0x471A1714),
            error = Color(0xFFB00020),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            success = Color(0xFF1B7A3D),
            warning = Color(0xFF8A5300),
            borderError = Color(0x40B00020),
            overlayHover = Color(0x0A000000),
            overlayPressed = Color(0x14000000),
            overlayFocus = Color(0x1F000000),
            placeholderStart = Color(0xFFEFECE8),
            placeholderEnd = Color(0xFFE4E0DB),
            placeholderPulse = Color(0xFFDAD5CF),
            shimmerBase = Color(0xFFEDEAE6),
            shimmerHighlight = Color(0xFFFBFAF8),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFBFAF8),
            surfaceContainer = Color(0xFFF3F1EE),
            surfaceContainerHigh = Color(0xFFEDEAE6),
            surfaceContainerHighest = Color(0xFFE7E3DE),
            tertiary = Color(0xFF00695F),
            onTertiary = Color(0xFFFFFFFF),
        )

        /** The token set for [mode] + [accent]. Dark + [DhunAccent.BRAND] is [dark] by value. */
        fun forAppearance(mode: DhunThemeMode, accent: DhunAccent): DhunTokens = when (mode) {
            DhunThemeMode.DARK -> dark.withAccent(accent.dark)
            DhunThemeMode.LIGHT -> light.withAccent(accent.light)
        }
    }
}

/**
 * The one observable appearance holder — theme mode, selected fallback accent,
 * and the active [DhunTokens]. In artwork mode, the shell supplies a
 * contrast-safe current-track tint that updates accent tokens app-wide; static
 * mode restores the selected fallback ramp. Snapshot state means token readers
 * recompose without screen-specific color plumbing.
 *
 * Default is dark + brand, i.e. the palette from before this file existed.
 */
object DhunAppearance {

    const val BACKDROP_BLUR_DEFAULT = 100
    const val BACKDROP_BRIGHTNESS_DEFAULT = 55

    // Keep the selected theme/accent and its static tokens together. The
    // derived active-token state is refreshed whenever this palette or the
    // artwork tint changes.
    private data class Palette(val mode: DhunThemeMode, val accent: DhunAccent, val tokens: DhunTokens)
    private val paletteState = mutableStateOf(Palette(DhunThemeMode.default, DhunAccent.default, DhunTokens.dark))
    private val themeIdState = mutableStateOf("dark")
    private val accentModeState = mutableStateOf("artwork")
    private val artworkAccentState = mutableStateOf<Color?>(null)
    private val appearanceRevisionState = mutableStateOf(0)
    private val activeTokensState = mutableStateOf(DhunTokens.dark)
    private val backdropBlurPercentState = mutableStateOf(BACKDROP_BLUR_DEFAULT)
    private val backdropBrightnessPercentState = mutableStateOf(BACKDROP_BRIGHTNESS_DEFAULT)

    val mode: DhunThemeMode get() = paletteState.value.mode
    val accent: DhunAccent get() = paletteState.value.accent
    /** The user-selected fallback ramp, before any current-artwork tint. */
    val selectedAccentColor: Color get() = paletteState.value.tokens.accent
    val selectedOnAccentColor: Color get() = paletteState.value.tokens.onAccent
    val selectedAccentContainerColor: Color get() = paletteState.value.tokens.accentContainer
    val themeId: String get() = themeIdState.value
    val accentMode: String get() = accentModeState.value
    /** Changes on reset so the shell can reapply the current track tint. */
    val appearanceRevision: Int get() = appearanceRevisionState.value
    val backdropBlurPercent: Int get() = backdropBlurPercentState.value
    val backdropBrightnessPercent: Int get() = backdropBrightnessPercentState.value
    val tokens: DhunTokens get() = activeTokensState.value

    fun setAppearance(nextMode: DhunThemeMode = mode, nextAccent: DhunAccent = accent) {
        val current = paletteState.value
        if (nextMode == current.mode && nextAccent == current.accent) return
        paletteState.value = Palette(nextMode, nextAccent, DhunTokens.forAppearance(nextMode, nextAccent))
        refreshTokens()
    }

    /** Called from event handlers/startup, never during composition. */
    fun setThemePreference(id: String, systemDark: Boolean) {
        if (id !in listOf("dark", "light", "system")) return
        themeIdState.value = id
        setAppearance(nextMode = when (id) {
            "light" -> DhunThemeMode.LIGHT
            "system" -> if (systemDark) DhunThemeMode.DARK else DhunThemeMode.LIGHT
            else -> DhunThemeMode.DARK
        })
    }

    fun setAccentMode(id: String) {
        if (id != "artwork" && id != "static") return
        val changed = accentModeState.value != id
        accentModeState.value = id
        if (id == "static") artworkAccentState.value = null
        if (changed || id == "static") refreshTokens()
    }

    /**
     * Installs the active track's contrast-safe artwork tint in the global
     * token palette. In artwork mode this affects the app accent tokens, not
     * only the player; static mode continues to use the selected accent.
     */
    fun setArtworkAccent(color: Color?) {
        if (artworkAccentState.value == color) return
        artworkAccentState.value = color
        refreshTokens()
    }

    private fun refreshTokens() {
        val palette = paletteState.value
        val artwork = artworkAccentState.value.takeIf { accentModeState.value == "artwork" }
        activeTokensState.value = if (artwork == null) {
            palette.tokens
        } else {
            palette.tokens.withAccent(artworkAccentRamp(artwork, palette.tokens))
        }
    }

    private fun artworkAccentRamp(color: Color, base: DhunTokens): AccentRamp {
        val container = color.mix(base.surface, 0.84f)
        return AccentRamp(
            accent = color,
            onAccent = bestContrastText(color),
            container = container,
            onContainer = bestContrastText(container),
        )
    }

    private fun bestContrastText(background: Color): Color {
        val luminance = relativeLuminance(background)
        val blackContrast = (luminance + 0.05f) / 0.05f
        val whiteContrast = 1.05f / (luminance + 0.05f)
        return if (blackContrast >= whiteContrast) Color.Black else Color.White
    }

    private fun relativeLuminance(color: Color): Float {
        fun linearize(channel: Float): Float =
            if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * linearize(color.red) +
            0.7152f * linearize(color.green) +
            0.0722f * linearize(color.blue)
    }

    /** Back to the shipped default. Used by tests and any future "reset" action. */
    fun reset() {
        appearanceRevisionState.value++
        themeIdState.value = "dark"
        accentModeState.value = "artwork"
        artworkAccentState.value = null
        setAppearance(DhunThemeMode.default, DhunAccent.default)
        refreshTokens()
        setBackdropPrefs(BACKDROP_BLUR_DEFAULT, BACKDROP_BRIGHTNESS_DEFAULT)
    }

    fun setBackdropPrefs(blurPercent: Int = backdropBlurPercent, brightnessPercent: Int = backdropBrightnessPercent) {
        backdropBlurPercentState.value = blurPercent.coerceIn(0, 100)
        backdropBrightnessPercentState.value = brightnessPercent.coerceIn(0, 100)
    }

    /**
     * Applies persisted ids read by the host before the first frame.
     * Invalid values fall back to dark/brand. `system` follows the platform
     * supplied dark-mode flag and continues following changes in [DhunTheme].
     */
    fun applyPersistedAppearance(
        themeId: String?,
        accentId: String?,
        blurPercent: Int? = null,
        brightnessPercent: Int? = null,
        accentMode: String? = null,
        systemDark: Boolean = true,
    ) {
        setThemePreference(themeId?.takeIf { it in listOf("dark", "light", "system") } ?: "dark", systemDark)
        setAccentMode(accentMode ?: "artwork")
        setAppearance(
            nextMode = mode,
            nextAccent = DhunAccent.fromId(accentId) ?: DhunAccent.default,
        )
        setBackdropPrefs(
            blurPercent = blurPercent ?: backdropBlurPercent,
            brightnessPercent = brightnessPercent ?: backdropBrightnessPercent,
        )
    }
}
