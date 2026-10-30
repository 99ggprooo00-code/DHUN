package dev.dhun.design.background

import androidx.compose.ui.graphics.Color
import dev.dhun.design.DhunTokens
import dev.dhun.design.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The interface areas a user can give their own colour.
 *
 * This is the *customisation unit*, not a screen: Home, Search and Library are
 * the same surface physically (one page backdrop, one set of panels) and share
 * [PAGES]. The dock and the player are surfaces in their own right, and the tab
 * bar/rail is a separate piece of chrome that sits over whichever page is open.
 *
 * Deliberately small. The previous model exposed five "sections" and three
 * palette layers per section, which asked the user to reason about the
 * implementation (`primary`, `secondary`, `tertiary`) instead of about the app
 * they were looking at.
 */
enum class BackgroundComponent(
    /** Stable persistence key. Never rename: it is in the settings table. */
    val id: String,
    val label: String,
    /** One line, plain language, no implementation detail. */
    val description: String,
) {
    PAGES("page", "Main pages", "Home, Search, Library and the pages you open from them"),
    NAVIGATION("nav", "Navigation bar", "The tab bar, or the side rail on a wide window"),
    MINI_PLAYER("mini", "Miniplayer", "The now-playing bar above the tabs"),
    FULL_PLAYER("player", "Full player", "The immersive player behind the artwork"),
    ;

    companion object {
        val default: BackgroundComponent = PAGES

        fun fromId(id: String?): BackgroundComponent? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One component's saved preferences.
 *
 * Everything here survives a global switch: turning *Dynamic colours* off
 * suspends [followArtwork] rather than clearing it, and turning *Background
 * blur* off suspends [blurEnabled] rather than clearing it, so re-enabling
 * either global restores exactly this configuration.
 */
data class ComponentBackgrounds(
    /** This surface frosts the artwork at all (only meaningful while the global is on). */
    val blurEnabled: Boolean = true,
    /** This surface may use artwork-derived colours (while *Dynamic colours* is on). */
    val followArtwork: Boolean = true,
    /**
     * The colour the user picked for this surface, or `null` for the DHUN
     * default. One colour, not three layers: the three-layer palette the
     * background is painted from is *derived* from it, so the user picks a
     * colour and sees a related set of layers rather than being asked to
     * assemble a palette.
     */
    val color: Color? = null,
) {
    companion object {
        val default = ComponentBackgrounds()
    }
}

/**
 * A complete, immutable snapshot of every background preference plus the
 * artwork colour currently in play.
 *
 * A snapshot type (rather than a dozen mutable fields) is what makes the
 * precedence testable without a composition, lets the host restore the whole
 * state in one assignment before the first frame, and gives the settings screen
 * one object to preview.
 */
data class BackgroundState(
    /** Background blur: the current track's artwork as a blurred background. */
    val blurEnabled: Boolean = true,
    /**
     * Whether the frost adapts to the artwork. A sub-choice of [blurEnabled],
     * not a third feature: it only changes *how much* an enabled blur frosts.
     */
    val adaptiveBlur: Boolean = true,
    /** Dynamic colours: artwork-derived colours, independent of both blur choices. */
    val dynamicColors: Boolean = true,
    val components: Map<BackgroundComponent, ComponentBackgrounds> = emptyMap(),
    /** The seed colour extracted from the current artwork, if any. */
    val artworkPrimary: Color? = null,
    val artworkLuminance: Float? = null,
    val artworkSaturation: Float? = null,
    /** Bumped on every change so derived values are re-read. */
    val revision: Int = 0,
) {
    fun component(component: BackgroundComponent): ComponentBackgrounds =
        components[component] ?: ComponentBackgrounds.default

    /** Every component, with the defaults filled in. */
    fun resolvedComponents(): Map<BackgroundComponent, ComponentBackgrounds> =
        BackgroundComponent.entries.associateWith { component(it) }

    /**
     * Carry the *runtime* artwork half over from another snapshot.
     *
     * The settings layer owns preferences; the artwork colour belongs to the
     * live holder (it is not a preference). Publishing a settings-only snapshot
     * must not drop the playing track's colour, which is what this restores.
     */
    fun withArtworkFrom(live: BackgroundState): BackgroundState = copy(
        artworkPrimary = live.artworkPrimary,
        artworkLuminance = live.artworkLuminance,
        artworkSaturation = live.artworkSaturation,
    )

    companion object {
        val default = BackgroundState()
    }
}

/**
 * The three layers a component background is painted from, in painting order:
 *
 * - **base** is the wash the surface sits on;
 * - **main glow** is the strongest bloom (top-left), the layer that carries the
 *   surface's colour identity;
 * - **companion glow** is the related hue (bottom-right) that stops the
 *   background reading as one flat tint.
 *
 * These are never chosen by the user directly: they are derived from one source
 * colour (the artwork's, or the user's pick) so that a surface always looks
 * like one deliberate colour scheme.
 */
data class BackgroundPalette(
    val base: Color,
    val mainGlow: Color,
    val companionGlow: Color,
) {
    val layers: List<Color> get() = listOf(base, mainGlow, companionGlow)

    companion object {
        /** The neutral palette every component starts from. */
        fun neutral(tokens: DhunTokens): BackgroundPalette = BackgroundPalette(
            base = tokens.background,
            mainGlow = tokens.accent,
            companionGlow = tokens.accentContainer,
        )
    }
}

/**
 * Which palette a component paints, and why.
 *
 * Resolution is a two-step precedence and nothing else, identical for all four
 * components, so no surface can behave differently from its neighbour for an
 * unrelated reason:
 *
 * 1. **Dynamic colours off**, or this surface opted out of artwork colours →
 *    the user's colour for this surface, else the DHUN default. Artwork colour
 *    never reaches the screen.
 * 2. **Both on** → the artwork palette; no artwork (nothing playing, a failed
 *    extraction, a blank seed) falls back to the user's colour, then to the
 *    default, so a device with no cover still paints a deliberate background
 *    instead of a hole.
 *
 * Blur is **not** an input here, and this function is not an input to any blur
 * decision: the two features are independent by construction. The only thing
 * they share is [solid]: whether the surface has artwork under it. A solid
 * surface needs the picked colour to *be* the background rather than a wash
 * over a cover, which is a rendering difference, not a precedence one.
 */
object BackgroundPalettePolicy {

    /**
     * How far a palette may move a surface's base away from the theme
     * background, as a fraction of the 0..1 luminance range.
     *
     * The contrast gates (`DhunThemeContrastTest`) measure the text ladder
     * against rows painted *over* the background. Keeping the base inside this
     * band keeps those ratios valid while the hue still reads: a dark theme
     * gains a deep coloured surface, not a bright one.
     */
    const val BASE_LUMINANCE_DRIFT = 0.05f

    /** Share of the source colour in the base when artwork is blurred behind it. */
    const val BASE_TINT_ALPHA = 0.30f

    /**
     * Share of the source colour in the base when there is **no** artwork
     * behind it (blur off).
     *
     * Higher on purpose: with nothing behind it, a 30% wash over near-black is
     * indistinguishable from "no colour set", which is the complaint that
     * started this. The luminance clamp below still holds the surface inside
     * the measured band, so this buys chroma, not brightness.
     */
    const val SOLID_BASE_TINT_ALPHA = 0.55f

    /** The main glow is the source hue, pulled toward neutral. */
    const val MAIN_GLOW_DESATURATION = 0.35f

    /** The companion glow is a related hue, weaker still. */
    const val COMPANION_GLOW_DESATURATION = 0.55f

    /** Companion hue offset in degrees — a related colour, not a second random one. */
    const val COMPANION_HUE_SHIFT = 38f

    /** A glow layer must never be fully transparent — that reads as "unset". */
    const val MIN_GLOW_ALPHA = 0.05f

    /** Tolerance for "already inside the band", so clamping is idempotent. */
    const val CLAMP_EPSILON = 1e-4f

    /**
     * The palette a component uses when nothing else applies.
     *
     * Every component starts from the theme, except that the player leans on
     * the accent (it is the one surface made of artwork) and the dock's
     * companion layer is a raised surface so it reads as chrome.
     */
    fun defaultPalette(component: BackgroundComponent, tokens: DhunTokens): BackgroundPalette =
        when (component) {
            BackgroundComponent.FULL_PLAYER -> BackgroundPalette(
                base = tokens.background,
                mainGlow = tokens.accent,
                companionGlow = tokens.surfaceVariant,
            )
            BackgroundComponent.MINI_PLAYER -> BackgroundPalette(
                base = tokens.background,
                mainGlow = tokens.accent,
                companionGlow = tokens.surfaceElevated,
            )
            BackgroundComponent.NAVIGATION -> BackgroundPalette(
                base = tokens.background,
                mainGlow = tokens.accent,
                companionGlow = tokens.surfaceElevated,
            )
            BackgroundComponent.PAGES -> BackgroundPalette.neutral(tokens)
        }

    /**
     * The palette one source colour produces. Used for the artwork palette and
     * for a user's pick, so the two cannot drift into different recipes: pick
     * the colour a cover happens to have and the surface looks the way the
     * cover made it look.
     *
     * [solid] is `true` when no artwork is painted behind the surface (blur
     * off), which is exactly when the colour has to carry the surface on its
     * own.
     */
    fun paletteFrom(source: Color, tokens: DhunTokens, solid: Boolean): BackgroundPalette =
        BackgroundPalette(
            // The base keeps the theme's own luminance and borrows the hue, so
            // rows over it keep their measured contrast.
            base = keepLuminance(
                source = source,
                base = tokens.background,
                tint = if (solid) SOLID_BASE_TINT_ALPHA else BASE_TINT_ALPHA,
            ),
            mainGlow = source.desaturated(MAIN_GLOW_DESATURATION).withMinimumAlpha(MIN_GLOW_ALPHA),
            companionGlow = source.shifted(COMPANION_HUE_SHIFT)
                .desaturated(COMPANION_GLOW_DESATURATION)
                .withMinimumAlpha(MIN_GLOW_ALPHA),
        )

    /**
     * Resolve what a component paints. Pure function of saved preferences plus
     * the current artwork colour — the single place precedence lives, so the
     * settings preview and the rendered surface cannot disagree.
     */
    fun resolve(
        component: BackgroundComponent,
        tokens: DhunTokens,
        dynamicColors: Boolean,
        followArtwork: Boolean,
        manualColor: Color?,
        artworkColor: Color?,
        solid: Boolean,
    ): BackgroundPalette {
        val fallback = manualColor?.let { paletteFrom(it, tokens, solid) }
            ?: defaultPalette(component, tokens)
        if (!dynamicColors || !followArtwork) return fallback
        return artworkColor?.let { paletteFrom(it, tokens, solid) } ?: fallback
    }

    /**
     * Why a component is showing what it shows — rendered in Settings so a
     * global switch never looks like a broken per-surface setting.
     */
    fun source(
        dynamicColors: Boolean,
        followArtwork: Boolean,
        hasManual: Boolean,
        hasArtwork: Boolean,
    ): PaletteSource = when {
        !dynamicColors ->
            if (hasManual) PaletteSource.MANUAL_OVERRIDDEN else PaletteSource.DEFAULT_OVERRIDDEN
        !followArtwork -> if (hasManual) PaletteSource.MANUAL else PaletteSource.DEFAULT
        hasArtwork -> PaletteSource.ARTWORK
        else -> if (hasManual) PaletteSource.MANUAL else PaletteSource.DEFAULT
    }

    /**
     * Force a stored/derived palette back into the legibility band.
     *
     * Applied on read as well as on write: a value written by an older build,
     * or by hand, must be just as safe as one picked here. The glows are only
     * floored for visibility — they sit under the rows, so they can be as
     * saturated as the artwork, but a fully transparent layer would be an
     * invisible setting the user could not understand.
     */
    fun sanitize(palette: BackgroundPalette, tokens: DhunTokens): BackgroundPalette {
        val base = tokens.background
        return BackgroundPalette(
            base = clampBase(palette.base, base),
            mainGlow = palette.mainGlow.withMinimumAlpha(MIN_GLOW_ALPHA),
            companionGlow = palette.companionGlow.withMinimumAlpha(MIN_GLOW_ALPHA),
        )
    }

    /**
     * The base may only drift [BASE_LUMINANCE_DRIFT] from the theme background.
     *
     * The mix factor is solved against the *distance left in the direction of
     * travel*, not a fixed denominator: pulling a bright colour down by
     * `excess / lum` of its own value and a dark colour up by `excess / (1-lum)`
     * of the distance to white both land exactly on the band edge. Getting that
     * wrong in one direction is how a "clamped" base quietly ends up twice as
     * far from the theme as documented.
     */
    fun clampBase(color: Color, background: Color): Color {
        val target = background.luminance()
        val lum = color.luminance()
        val drift = lum - target
        // The epsilon matters: clamping lands the colour exactly on the band
        // edge, and without it a second pass (sanitise runs on the way in *and*
        // on the way out of storage) would nudge an already-safe palette again,
        // drifting a little further every read.
        if (abs(drift) <= BASE_LUMINANCE_DRIFT + CLAMP_EPSILON) return color
        val excess = abs(drift) - BASE_LUMINANCE_DRIFT
        val towardBlack = drift > 0f
        val span = if (towardBlack) lum else 1f - lum
        val mix = if (span <= 0.0001f) 1f else (excess / span).coerceIn(0f, 1f)
        return color.blend(if (towardBlack) Color.Black else Color.White, mix)
    }

    /**
     * A colour that carries [source]'s hue and [base]'s luminance. Used for the
     * base wash: recognisably the chosen colour, provably inside the contrast
     * band the text ladder was measured against.
     */
    fun keepLuminance(source: Color, base: Color, tint: Float): Color {
        val t = tint.coerceIn(0f, 1f)
        val mixed = base.blend(source, t)
        return clampBase(mixed, base)
    }
}

/** Where a component's current palette came from. Drives the Settings caption. */
enum class PaletteSource {
    /** Artwork-derived (dynamic colours on, and this surface follows the artwork). */
    ARTWORK,

    /** The user's colour for this surface. */
    MANUAL,

    /** The DHUN default; the user has not picked a colour. */
    DEFAULT,

    /** The user's colour, but the global Dynamic colours switch is off. */
    MANUAL_OVERRIDDEN,

    /** The DHUN default, and the global switch is off. */
    DEFAULT_OVERRIDDEN,
    ;

    val isOverridden: Boolean get() = this == MANUAL_OVERRIDDEN || this == DEFAULT_OVERRIDDEN

    val label: String
        get() = when (this) {
            ARTWORK -> "From the current artwork"
            MANUAL -> "Your colour"
            DEFAULT -> "DHUN default"
            MANUAL_OVERRIDDEN -> "Your colour · Dynamic colours is off"
            DEFAULT_OVERRIDDEN -> "DHUN default · Dynamic colours is off"
        }
}

/**
 * Blur: on or off, plus *how much*.
 *
 * The switch is the feature ("use the track's artwork as a blurred
 * background"); [MIN_EFFECTIVE_STRENGTH] only stops the strength slider from
 * impersonating that switch. Nothing here reads a colour setting, and the
 * colour policy never reads this: a user can have blur with no artwork colour,
 * artwork colour with no blur, both, or neither.
 */
object BackgroundBlurPolicy {

    /**
     * The radius used when the frost does not adapt: the shipped frost, i.e.
     * the full radius of the user's strength setting.
     *
     * "Adaptive off" means *the normal blur* — not a different, weaker one.
     * Anything less here would make choosing the steady frost a quiet downgrade
     * of the app's own look.
     */
    const val STATIC_PERCENT = 100

    /**
     * Adaptive range, in percent of the strength radius.
     *
     * The adaptive radius starts at [ADAPTIVE_BASE_PERCENT] (the shipped frost,
     * so a neutral cover looks exactly like it did) and only ever moves *down*
     * for artwork that does not need that much: a dark, muted cover at the full
     * radius is just a grey smear, and letting it stay recognisable is the whole
     * point of following the artwork. It never reaches zero — "adaptive" must
     * not mean "sometimes none".
     */
    const val ADAPTIVE_MIN_PERCENT = 70
    const val ADAPTIVE_MAX_PERCENT = 100
    const val ADAPTIVE_BASE_PERCENT = 100

    /** How far artwork luminance can pull the radius, in percent. */
    const val ADAPTIVE_LUMINANCE_SPREAD = 18

    /** Saturated artwork keeps more frost; muted artwork gives some back. */
    const val ADAPTIVE_SATURATION_SPREAD = 12

    /** Neutral artwork statistics: the radius is [ADAPTIVE_BASE_PERCENT]. */
    const val NEUTRAL_LUMINANCE = 0.5f
    const val NEUTRAL_SATURATION = 0.5f

    /**
     * The weakest frost an *enabled* blur is allowed to reach.
     *
     * The switch is the only control that removes blur. Before this feature the
     * intensity slider was also the off switch, and the version-0 migration
     * still maps a stored `backdrop_blur = 0` to blur off — so a slider that
     * could reach zero would let the switch say "on" while nothing blurred,
     * which reads as a broken switch rather than a light frost.
     */
    const val MIN_EFFECTIVE_STRENGTH = 20

    /**
     * The blur percentage a component should render, or 0 for "no artwork
     * behind this surface at all".
     *
     * The global switch wins, then the surface's own switch, then the kind of
     * blur. Turning the global off suspends both per-surface choices without
     * forgetting them.
     */
    fun percentFor(
        blurEnabled: Boolean,
        componentBlurEnabled: Boolean,
        adaptive: Boolean,
        artworkLuminance: Float? = null,
        artworkSaturation: Float? = null,
    ): Int {
        if (!blurEnabled || !componentBlurEnabled) return 0
        if (!adaptive) return STATIC_PERCENT
        return adaptivePercent(artworkLuminance, artworkSaturation)
    }

    /**
     * The artwork-responsive radius. Bright, saturated artwork keeps the full
     * frost (pale, busy covers are the hard case for white text, and the full
     * radius is already the ceiling); dark, muted artwork gives some back so the
     * cover is still recognisable instead of dissolving into grey.
     */
    fun adaptivePercent(luminance: Float?, saturation: Float?): Int {
        val lum = (luminance ?: NEUTRAL_LUMINANCE).coerceIn(0f, 1f)
        val sat = (saturation ?: NEUTRAL_SATURATION).coerceIn(0f, 1f)
        val fromLuminance = (lum - NEUTRAL_LUMINANCE) * 2f * ADAPTIVE_LUMINANCE_SPREAD
        val fromSaturation = (sat - NEUTRAL_SATURATION) * 2f * ADAPTIVE_SATURATION_SPREAD
        val percent = ADAPTIVE_BASE_PERCENT + fromLuminance + fromSaturation
        return percent.toInt().coerceIn(ADAPTIVE_MIN_PERCENT, ADAPTIVE_MAX_PERCENT)
    }

    /**
     * Whether the artwork is painted behind a surface at this radius.
     *
     * This is the one answer every image layer in the app asks for, and the
     * reason "blur off" can mean *no thumbnail anywhere* instead of "a sharp,
     * dimmed thumbnail": with no radius there is no artwork layer at all, only
     * the component's colour.
     */
    fun paintsArtwork(percent: Int): Boolean = percent > 0

    /**
     * The user's blur *strength* (the existing slider) applied to the resolved
     * kind. Strength is a preference about intensity; on/off belongs to the
     * switch, so strength can only reduce a blur — never resurrect a disabled
     * one, and never silently reproduce "off" either (see
     * [MIN_EFFECTIVE_STRENGTH]).
     */
    fun applyStrength(percent: Int, strengthPercent: Int): Int {
        if (percent <= 0) return 0
        val scaled = (percent * effectiveStrength(strengthPercent) / 100f).toInt()
        return scaled.coerceIn(1, percent)
    }

    /**
     * The strength actually used for an enabled blur: the slider, clamped into
     * the documented band. The one place both the surface layers and the
     * single-surface ones (lyrics card, menus) agree on it.
     */
    fun effectiveStrength(strengthPercent: Int): Int =
        strengthPercent.coerceIn(MIN_EFFECTIVE_STRENGTH, 100)
}

/* ---------------------------------------------------------------- colours */

internal fun Color.blend(other: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * k,
        green = green + (other.green - green) * k,
        blue = blue + (other.blue - blue) * k,
        alpha = alpha,
    )
}

private fun Color.desaturated(amount: Float): Color {
    val gray = luminance()
    return blend(Color(gray, gray, gray, alpha), amount)
}

private fun Color.withMinimumAlpha(minimum: Float): Color =
    if (alpha >= minimum) this else copy(alpha = minimum)

/** The companion layer is the same hue family, rotated: related, not random. */
internal fun Color.shifted(degrees: Float): Color {
    val h = hueDegrees()
    val s = saturation()
    val v = value()
    return hsv(h + degrees, s, v, alpha)
}

internal fun Color.hueDegrees(): Float {
    val r = red; val g = green; val b = blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    val d = mx - mn
    if (d <= 0.0001f) return 0f
    val h = when (mx) {
        r -> ((g - b) / d).let { if (it < 0f) it + 6f else it }
        g -> ((b - r) / d) + 2f
        else -> ((r - g) / d) + 4f
    }
    return h * 60f
}

internal fun Color.saturation(): Float {
    val mx = max(red, max(green, blue)); val mn = min(red, min(green, blue))
    return if (mx <= 0f) 0f else (mx - mn) / mx
}

internal fun Color.value(): Float = max(red, max(green, blue))

internal fun hsv(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, alpha)
}
