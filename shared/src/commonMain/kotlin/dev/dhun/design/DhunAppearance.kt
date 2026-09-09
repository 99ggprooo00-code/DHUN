package dev.dhun.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Candidate 28 — themes beyond dark-first.
 *
 * DHUN is dark-first and stays that way: [DhunThemeMode.DARK] + [DhunAccent.BRAND]
 * is the default and is byte-for-byte the palette that shipped before this file
 * existed. What this file adds is (a) a **light** scheme built from the same
 * token names, and (b) an **accent selector**, both reachable through one
 * observable holder, [DhunAppearance].
 *
 * ## Why the tokens had to move, not just the `ColorScheme`
 *
 * Screens read `DhunColors.textPrimary` and friends as **plain object
 * properties** — 416 reads repo-wide, 274 of them in `shared/ui/**` — and
 * `MaterialTheme.colorScheme` is read **nowhere** in `shared/ui/**`. A light
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
 * 2. **Nothing persists it yet.** A persistence path *does* exist —
 *    `dev.dhun.data.SettingsRepository.getString/putString/observeString`
 *    (SQLDelight-backed) — and `SettingsKeys.THEME` already reserves
 *    `"dark" | "light" | "system"` with `"dark"` as the default, so
 *    [DhunThemeMode.id] deliberately uses exactly those strings. But **no app
 *    code reads that key today** (only `RepositoriesTest` touches it), there
 *    is no Settings screen to host the choice, and reading it at startup means
 *    touching the frozen platform entry points. `design` also must not depend
 *    on `data` — that would invert the layers. So: a restart returns to the
 *    dark default until a host wires `SettingsKeys.THEME` to
 *    [DhunAppearance.setAppearance].
 * 3. **`TrayIcons` keeps the dark palette**: it reads tokens at object-init,
 *    before any composition exists. Fixing that means touching a frozen file.
 */

/**
 * Light or dark. `DARK` is the default — the palette DHUN shipped with.
 *
 * There is no `SYSTEM` entry on purpose: following the platform setting needs
 * an `expect/actual` hook into Android configuration and the desktop OS
 * appearance, and neither exists in this module. Adding a value that cannot
 * be honoured would be a stub. Note that `SettingsKeys.THEME` documents
 * `"system"` as a storable value; [fromId] maps it to `null`, so a persisted
 * "system" falls back to the dark default instead of pretending to work.
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
 * The default constructor **is** the dark palette DHUN shipped with, so
 * `DhunTokens()` is the regression baseline; the light set is
 * [DhunTokens.light], written out in full rather than patched onto the dark
 * one so no dark value can leak through by omission.
 */
data class DhunTokens(
    /** Selects `lightColorScheme()` as the Material3 base and flips a few token-derived rules. */
    val isLight: Boolean = false,

    // Surfaces (dark: warm near-black stack so artwork pops)
    val background: Color = Color(0xFF0A0A0A),
    val surface: Color = Color(0xFF121212),
    val surfaceVariant: Color = Color(0xFF1A1A1A),
    val surfaceElevated: Color = Color(0xFF242424),
    val surfaceHighest: Color = Color(0xFF2A2A2A),
    val surfaceCard: Color = Color(0xFF1E1E1E),

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
    val error: Color = Color(0xFFCF6679),
    val onError: Color = Color(0xFF000000),
    val errorContainer: Color = Color(0xFF4D1A24),
    val success: Color = Color(0xFF4CAF50),
    val warning: Color = Color(0xFFFFB74D),
    val borderError: Color = Color(0x40CF6679),

    // Overlays
    val overlayHover: Color = Color(0x0FFFFFFF),
    val overlayPressed: Color = Color(0x14FFFFFF),
    val overlayFocus: Color = Color(0x1FFFFFFF),

    // Artwork placeholders
    val placeholderStart: Color = Color(0xFF1A1A1A),
    val placeholderEnd: Color = Color(0xFF2A2A2A),
    val placeholderPulse: Color = Color(0xFF333333),

    // Shimmer
    val shimmerBase: Color = Color(0xFF1E1E1E),
    val shimmerHighlight: Color = Color(0xFF2E2E2E),

    // Material 3 tonal surface ladder
    val surfaceContainerLowest: Color = Color(0xFF0A0A0A),
    val surfaceContainerLow: Color = Color(0xFF121212),
    val surfaceContainer: Color = Color(0xFF242424),
    val surfaceContainerHigh: Color = Color(0xFF2A2A2A),
    val surfaceContainerHighest: Color = Color(0xFF303030),

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
    val materialScheme: ColorScheme by lazy {
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
        /** The palette DHUN shipped with — and still the default. */
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
 * The one observable appearance holder — mode, accent, and the derived
 * [DhunTokens]. Snapshot state, so a change recomposes every token reader
 * without any screen being rewritten.
 *
 * Default is dark + brand, i.e. the palette from before this file existed.
 */
object DhunAppearance {

    private val modeState = mutableStateOf(DhunThemeMode.default)
    private val accentState = mutableStateOf(DhunAccent.default)
    private val tokensState = mutableStateOf(
        DhunTokens.forAppearance(DhunThemeMode.default, DhunAccent.default),
    )

    val mode: DhunThemeMode get() = modeState.value
    val accent: DhunAccent get() = accentState.value

    /** The active token set — what every [DhunColors] accessor resolves to. */
    val tokens: DhunTokens get() = tokensState.value

    /**
     * Applies [nextMode]/[nextAccent]. A no-op when nothing changed (the
     * structural-equality snapshot policy makes the write free), which is why
     * [DhunTheme] can call it on every composition without churning state.
     */
    fun setAppearance(
        nextMode: DhunThemeMode = mode,
        nextAccent: DhunAccent = accent,
    ) {
        if (nextMode == modeState.value && nextAccent == accentState.value) return
        modeState.value = nextMode
        accentState.value = nextAccent
        tokensState.value = DhunTokens.forAppearance(nextMode, nextAccent)
    }

    /** Back to the shipped default. Used by tests and any future "reset" action. */
    fun reset() = setAppearance(DhunThemeMode.default, DhunAccent.default)
}
