package dev.dhun.design.background

import androidx.compose.ui.graphics.Color
import dev.dhun.design.ArtworkColors
import dev.dhun.design.DhunTokens
import dev.dhun.design.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The five surfaces whose background DHUN paints itself.
 *
 * Every one of them is a *section* rather than a screen id: Home, Search and
 * Library share the page backdrop, but the user asked to be able to colour
 * them differently, so each is its own saved preference set. The miniplayer
 * and the full player are surfaces in their own right.
 */
enum class BackgroundSection(
    /** Stable persistence key. Never rename: it is in the settings table. */
    val id: String,
    val label: String,
    /** One line, plain language, no implementation detail. */
    val description: String,
) {
    HOME("home", "Home", "The Home tab behind the shelves"),
    SEARCH("search", "Search", "The Search tab behind results"),
    LIBRARY("library", "Library", "The Library tab behind playlists and downloads"),
    MINI_PLAYER("mini", "Miniplayer", "The now-playing bar above the tabs"),
    FULL_PLAYER("player", "Full player", "The immersive player behind the artwork"),
    ;

    companion object {
        val default: BackgroundSection = HOME

        fun fromId(id: String?): BackgroundSection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The three layers a section background is made of, in painting order:
 *
 * - **primary** is the base wash the section sits on;
 * - **secondary** is the main bloom (top-left), the layer that carries the
 *   section's colour identity;
 * - **tertiary** is the companion bloom (bottom-right) that stops the
 *   background reading as one flat tint.
 *
 * They are a palette rather than three independent colours: [BackgroundPalettePolicy]
 * derives all three from one source, so a manual pick or an artwork tint keeps
 * the three related to each other instead of producing unrelated blocks.
 */
data class BackgroundPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    /** All three, for the settings preview and for equality-based tests. */
    val layers: List<Color> get() = listOf(primary, secondary, tertiary)

    companion object {
        /** The neutral palette every section starts from. */
        fun neutral(tokens: DhunTokens): BackgroundPalette = BackgroundPalette(
            primary = tokens.background,
            secondary = tokens.accent,
            tertiary = tokens.accentContainer,
        )
    }
}

/**
 * Which palette a section actually paints, and why.
 *
 * Resolution is a three-step precedence and nothing else — the same order for
 * all five sections, so a section can never behave differently from its
 * neighbour for an unrelated reason:
 *
 * 1. **Global Dynamic Colors off** → the section's manual palette, else its
 *    default. Artwork never reaches the screen.
 * 2. **Global on, section off** → the same manual palette. This is what makes
 *    "I want artwork on Home but my own colours in Search" work.
 * 3. **Both on** → the artwork-derived palette, if there is artwork; otherwise
 *    fall back to the manual palette, then to the default.
 *
 * The third fallback matters: a device with nothing playing, a failed image
 * request or a blank seed must still paint a deliberate background rather than
 * a transparent hole.
 */
object BackgroundPalettePolicy {

    /**
     * How far an artwork palette may move a section's base away from the theme
     * background, as a fraction of the 0..1 luminance range.
     *
     * The contrast gates (`DhunThemeContrastTest`) measure the text ladder
     * against rows painted *over* the background. Keep the base inside this
     * band and the measured ratios stay inside the band they were written for;
     * the artwork identity belongs to the two blooms, which sit far below the
     * rows and are already alpha-limited.
     */
    const val BASE_LUMINANCE_DRIFT = 0.05f

    /** Share of the artwork colour left in the base wash. */
    const val BASE_TINT_ALPHA = 0.30f

    /** The main bloom is the artwork hue, pulled toward neutral. */
    const val SECONDARY_DESATURATION = 0.35f

    /** The companion bloom is a related hue, weaker still. */
    const val TERTIARY_DESATURATION = 0.55f

    /** Companion hue offset in degrees — a related colour, not a second random one. */
    const val TERTIARY_HUE_SHIFT = 38f

    /**
     * The palette a section uses when nothing else applies.
     *
     * Sections share the theme's own colours, except the full player, which
     * leans on the accent a little harder because it is the one screen that is
     * *made* of artwork.
     */
    fun defaultPalette(section: BackgroundSection, tokens: DhunTokens): BackgroundPalette =
        when (section) {
            // The player is the one surface that is genuinely made of artwork,
            // so its default leans on the accent and a surface rung.
            BackgroundSection.FULL_PLAYER -> BackgroundPalette(
                primary = tokens.background,
                secondary = tokens.accent,
                tertiary = tokens.surfaceVariant,
            )
            // The dock sits on top of the page: its companion layer is the
            // raised surface, not another accent, so it reads as chrome.
            BackgroundSection.MINI_PLAYER -> BackgroundPalette(
                primary = tokens.background,
                secondary = tokens.accent,
                tertiary = tokens.surfaceElevated,
            )
            else -> BackgroundPalette.neutral(tokens)
        }

    /**
     * The artwork palette, or `null` when there is no artwork to derive one
     * from.
     *
     * One palette serves all five sections: they are coloured by the same cover,
     * so a per-section derivation would only let two surfaces drift apart for a
     * reason the user never asked for. [ArtworkColors] is the *existing*
     * extraction result — this function never decodes or samples an image, it
     * only reshapes a palette the app has already computed. That is the "one
     * image pipeline" rule.
     */
    fun artworkPalette(
        art: ArtworkColors?,
        tokens: DhunTokens,
    ): BackgroundPalette? = art?.let { derive(it.primary, tokens) }

    /**
     * The three layers one source hue produces. Shared by the artwork palette
     * and a manual pick so the two cannot drift into different recipes, and so
     * a picked swatch lands in exactly the palette the cover would have made.
     */
    private fun derive(hue: Color, tokens: DhunTokens): BackgroundPalette = BackgroundPalette(
        // The base keeps the theme's own luminance and only borrows a little of
        // the source colour, so rows over it keep their measured contrast.
        primary = keepLuminance(hue, tokens.background, BASE_TINT_ALPHA),
        secondary = hue.desaturated(SECONDARY_DESATURATION),
        tertiary = hue.shifted(TERTIARY_HUE_SHIFT).desaturated(TERTIARY_DESATURATION),
    )

    /**
     * The palette a manual pick of [primary] produces.
     *
     * Deliberately the same derivation as [artworkPalette]: pick the same hue in
     * each of the three layers in Settings and you get exactly the palette the
     * artwork would have produced for that hue. It is also what keeps the
     * settings swatches related to each other instead of three unrelated
     * colours, and why every offered swatch is already inside the contrast band.
     */
    fun manualFrom(primary: Color, tokens: DhunTokens): BackgroundPalette = derive(primary, tokens)

    /**
     * Resolve what a section paints. Pure function of saved preferences plus
     * the current artwork palette — the single place precedence lives, so the
     * settings preview and the rendered background cannot disagree.
     */
    fun resolve(
        section: BackgroundSection,
        tokens: DhunTokens,
        globalDynamicColors: Boolean,
        sectionDynamicColors: Boolean,
        manual: BackgroundPalette?,
        artwork: BackgroundPalette?,
    ): BackgroundPalette {
        // Sanitised here rather than at the write site: a palette restored from
        // an older build, or set directly in a test, must be just as legible.
        val safeManual = manual?.let { sanitize(it, tokens) }
        val fallback = safeManual ?: defaultPalette(section, tokens)
        if (!globalDynamicColors || !sectionDynamicColors) return fallback
        return artwork ?: fallback
    }

    /**
     * Why a section is showing what it shows — rendered in Settings so the
     * global override is visible instead of mysterious.
     */
    fun source(
        globalDynamicColors: Boolean,
        sectionDynamicColors: Boolean,
        hasManual: Boolean,
        hasArtwork: Boolean,
    ): PaletteSource = when {
        !globalDynamicColors ->
            if (hasManual) PaletteSource.MANUAL_OVERRIDDEN else PaletteSource.DEFAULT_OVERRIDDEN
        !sectionDynamicColors ->
            if (hasManual) PaletteSource.MANUAL else PaletteSource.DEFAULT
        hasArtwork -> PaletteSource.ARTWORK
        else -> if (hasManual) PaletteSource.MANUAL else PaletteSource.DEFAULT
    }

    /**
     * Force user-picked / artwork colours into the legibility band.
     *
     * Applied to every manual pick and every manual palette read back from
     * storage (a value written by an older build, or by hand, is still safe).
     * The blooms are only floored for visibility — they sit under the rows, so
     * they can be as saturated as the artwork, but a fully transparent layer
     * would be an invisible setting the user could not understand.
     */
    fun sanitize(palette: BackgroundPalette, tokens: DhunTokens): BackgroundPalette {
        val base = tokens.background
        return BackgroundPalette(
            primary = clampBase(palette.primary, base),
            secondary = palette.secondary.withMinimumAlpha(MIN_LAYER_ALPHA),
            tertiary = palette.tertiary.withMinimumAlpha(MIN_LAYER_ALPHA),
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
     * artwork base wash: recognisably the song's colour, provably in the
     * contrast band the text ladder was measured against.
     */
    fun keepLuminance(source: Color, base: Color, tint: Float): Color {
        val t = tint.coerceIn(0f, 1f)
        val mixed = base.blend(source, t)
        return clampBase(mixed, base)
    }

    /** A manual layer must never be fully transparent — that reads as "unset". */
    const val MIN_LAYER_ALPHA = 0.05f

    /** Tolerance for "already inside the band", so clamping is idempotent. */
    const val CLAMP_EPSILON = 1e-4f
}

/** Where a section's current palette came from. Drives the Settings caption. */
enum class PaletteSource {
    /** Artwork-derived (both the global and the section toggle are on). */
    ARTWORK,

    /** The user's own palette for this section. */
    MANUAL,

    /** The section default; the user has not picked colours. */
    DEFAULT,

    /** The user's palette, but the global Dynamic Colors switch is off. */
    MANUAL_OVERRIDDEN,

    /** The section default, and the global switch is off. */
    DEFAULT_OVERRIDDEN,
    ;

    val isOverridden: Boolean get() = this == MANUAL_OVERRIDDEN || this == DEFAULT_OVERRIDDEN

    val label: String
        get() = when (this) {
            ARTWORK -> "From the current artwork"
            MANUAL -> "Your colours"
            DEFAULT -> "DHUN default"
            MANUAL_OVERRIDDEN -> "Your colours · Dynamic Colors is off"
            DEFAULT_OVERRIDDEN -> "DHUN default · Dynamic Colors is off"
        }
}

/**
 * Blur is two switches, not one.
 *
 * `Background blur` is the master: off means *no* blur anywhere, and it is the
 * switch a user reaches for when the device is struggling. `Dynamic blur` is
 * the *kind* of blur: on, the radius responds to the artwork; off, every
 * surface uses the same steady radius. Neither touches colour.
 */
object BackgroundBlurPolicy {

    /**
     * The radius used when blur is on but not dynamic. Chosen so a moving
     * phone gets the frosted look without the 100% radius, which costs the
     * most GPU time on low-end devices.
     */
    const val STATIC_PERCENT = 70

    /** Adaptive range. Never zero: "dynamic blur" must not mean "sometimes none". */
    const val DYNAMIC_MIN_PERCENT = 45
    const val DYNAMIC_MAX_PERCENT = 100

    /** How far artwork luminance can push the radius, in percent. */
    const val DYNAMIC_LUMINANCE_SPREAD = 26

    /** Saturated artwork needs more frost to keep text legible over it. */
    const val DYNAMIC_SATURATION_LIFT = 12

    /** Neutral artwork statistics: the radius falls back to [STATIC_PERCENT]. */
    const val NEUTRAL_LUMINANCE = 0.5f
    const val NEUTRAL_SATURATION = 0.5f

    /**
     * The weakest frost an *enabled* blur is allowed to reach.
     *
     * The master switch is the only control that turns blur off. Before this
     * feature the intensity slider was also the off switch, and the version-0
     * migration still maps a stored `backdrop_blur = 0` to "master off" — so a
     * slider that could reach zero would let the master switch say "on" while
     * nothing blurred, which reads as a broken switch rather than a light
     * frost. An enabled blur therefore keeps at least this share of its radius.
     */
    const val MIN_EFFECTIVE_STRENGTH = 20

    /**
     * The blur percentage a section should render, or 0 for "no blur".
     *
     * The master switch wins over everything, then the section's own switch,
     * then the *kind* of blur. Turning the master off suspends dynamic blur
     * without forgetting it — the saved preference is untouched, and the
     * radius comes straight back when the master returns.
     */
    fun percentFor(
        globalBlurEnabled: Boolean,
        sectionBlurEnabled: Boolean,
        dynamicBlur: Boolean,
        sectionDynamicBlur: Boolean,
        artworkLuminance: Float? = null,
        artworkSaturation: Float? = null,
    ): Int {
        if (!globalBlurEnabled || !sectionBlurEnabled) return 0
        if (!dynamicBlur || !sectionDynamicBlur) return STATIC_PERCENT
        return adaptivePercent(artworkLuminance, artworkSaturation)
    }

    /**
     * The artwork-responsive radius. Bright artwork gets more frost (white text
     * over a pale cover is the hard case), saturated artwork gets a little more
     * again; dark, muted artwork gets less so the cover is still recognisable.
     */
    fun adaptivePercent(luminance: Float?, saturation: Float?): Int {
        val lum = (luminance ?: NEUTRAL_LUMINANCE).coerceIn(0f, 1f)
        val sat = (saturation ?: NEUTRAL_SATURATION).coerceIn(0f, 1f)
        val fromLuminance = (lum - NEUTRAL_LUMINANCE) * 2f * DYNAMIC_LUMINANCE_SPREAD
        val fromSaturation = (sat - NEUTRAL_SATURATION) * 2f * DYNAMIC_SATURATION_LIFT
        val percent = STATIC_PERCENT + fromLuminance + fromSaturation
        return percent.toInt().coerceIn(DYNAMIC_MIN_PERCENT, DYNAMIC_MAX_PERCENT)
    }

    /** Whether a section renders any blur at all, for labels and tests. */
    fun isBlurred(percent: Int): Boolean = percent > 0

    /**
     * The user's blur *strength* (the existing slider) applied to the resolved
     * kind. Strength is a preference about intensity; on/off belongs to the
     * master switch, so strength can only reduce a blur — never resurrect a
     * disabled one, and never silently reproduce "off" either (see
     * [MIN_EFFECTIVE_STRENGTH]).
     */
    fun applyStrength(percent: Int, strengthPercent: Int): Int {
        if (percent <= 0) return 0
        // Never zero while the kind asks for frost (rounding a 1% kind against a
        // 20% strength would otherwise floor it to nothing), and never more than
        // the kind itself: strength reduces, it does not amplify.
        val scaled = (percent * effectiveStrength(strengthPercent) / 100f).toInt()
        return scaled.coerceIn(1, percent)
    }

    /**
     * The strength actually used for an enabled blur: the slider, clamped into
     * the documented band. The one place both the per-section surfaces and the
     * single-surface ones (lyrics card, menus) agree on it.
     */
    fun effectiveStrength(strengthPercent: Int): Int =
        strengthPercent.coerceIn(MIN_EFFECTIVE_STRENGTH, 100)
}

/* ---------------------------------------------------------------- colours */

private fun Color.blend(other: Color, t: Float): Color {
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
