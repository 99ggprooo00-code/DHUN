package dev.dhun.design.background

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.ArtworkColors
import dev.dhun.design.DhunAppearance
import dev.dhun.design.luminance

/**
 * One section's saved preferences.
 *
 * Everything here survives a global override: when Background blur or Dynamic
 * Colors is switched off, these values are *suspended*, not cleared, so the
 * section returns to exactly this configuration when the global switch comes
 * back. Nothing in this class knows about the globals.
 */
data class SectionBackgrounds(
    /** This surface renders blur at all (only meaningful while the master is on). */
    val blurEnabled: Boolean = true,
    /** This surface's blur may follow the artwork. */
    val dynamicBlur: Boolean = true,
    /** This surface may paint artwork-derived colours. */
    val dynamicColors: Boolean = true,
    /** `null` = never picked: the section default applies. */
    val manual: BackgroundPalette? = null,
) {
    companion object {
        val default = SectionBackgrounds()
    }
}

/**
 * A complete, immutable snapshot of every background preference plus the
 * artwork palette currently in play.
 *
 * A snapshot type (rather than a dozen mutable fields) is what makes the
 * precedence testable without a composition, lets the host restore the whole
 * state in one assignment before the first frame, and gives the settings screen
 * one object to preview.
 */
data class BackgroundState(
    /** Master switch: off means no blur anywhere, whatever a section says. */
    val blurEnabled: Boolean = true,
    val dynamicBlur: Boolean = true,
    val dynamicColors: Boolean = true,
    val sections: Map<BackgroundSection, SectionBackgrounds> = emptyMap(),
    /** The palette extracted from the current artwork, if any. */
    val artwork: BackgroundPalette? = null,
    val artworkLuminance: Float? = null,
    val artworkSaturation: Float? = null,
    /** Bumped on every change so the shell can re-read derived values. */
    val revision: Int = 0,
) {
    fun section(section: BackgroundSection): SectionBackgrounds =
        sections[section] ?: SectionBackgrounds.default

    /** Every section, with the defaults filled in. */
    fun resolvedSections(): Map<BackgroundSection, SectionBackgrounds> =
        BackgroundSection.entries.associateWith { section(it) }

    companion object {
        val default = BackgroundState()
    }
}

/**
 * DHUN's background settings, live for the whole process.
 *
 * This mirrors [DhunAppearance]: Compose snapshot state read inside composition,
 * plain reads outside it, and **no dependency on the data layer** — the platform
 * entry points read the persisted keys through `SettingsRepository` and call
 * [restore] before the first frame; the Settings screen writes them back.
 *
 * The artwork palette is derived from the *existing* extraction result
 * (`ArtworkColorExtractor`), cached per seed. Nothing here decodes, samples or
 * re-blurs an image, so changing a colour preference never costs an image
 * pipeline run, and changing the track costs exactly one extraction that the
 * app was already doing for the player tint.
 */
object DhunBackgrounds {

    private val state = mutableStateOf(BackgroundState())

    /** The current snapshot. Reading this in composition subscribes to changes. */
    val current: BackgroundState get() = state.value

    /* ------------------------------------------------------------ globals */

    /** Master blur switch. Off suspends every section's blur but forgets nothing. */
    fun setBlurEnabled(enabled: Boolean) = update { it.copy(blurEnabled = enabled) }

    /** Whether the blur follows the artwork (only meaningful with the master on). */
    fun setDynamicBlur(enabled: Boolean) = update { it.copy(dynamicBlur = enabled) }

    /** Master colour switch. Off restores the saved manual section palettes. */
    fun setDynamicColors(enabled: Boolean) = update { it.copy(dynamicColors = enabled) }

    /* ----------------------------------------------------------- sections */

    fun setSectionBlurEnabled(section: BackgroundSection, enabled: Boolean) =
        updateSection(section) { it.copy(blurEnabled = enabled) }

    fun setSectionDynamicBlur(section: BackgroundSection, enabled: Boolean) =
        updateSection(section) { it.copy(dynamicBlur = enabled) }

    fun setSectionDynamicColors(section: BackgroundSection, enabled: Boolean) =
        updateSection(section) { it.copy(dynamicColors = enabled) }

    /**
     * A manual palette, or `null` to go back to the section default.
     *
     * Sanitised on the way in: a palette that would push the base outside the
     * contrast band is clamped here rather than rendered and regretted.
     */
    fun setSectionManualPalette(section: BackgroundSection, palette: BackgroundPalette?) =
        updateSection(section) {
            it.copy(
                manual = palette?.let { picked ->
                    BackgroundPalettePolicy.sanitize(picked, DhunAppearance.tokens)
                },
            )
        }

    /** Forget one section's choices: blur, dynamic switches and manual colours. */
    fun resetSection(section: BackgroundSection) =
        updateSection(section) { SectionBackgrounds.default }

    /** Forget every section's choices. The three global switches are untouched. */
    fun resetSections() = update {
        it.copy(sections = emptyMap(), revision = it.revision + 1)
    }

    /* ------------------------------------------------------------ artwork */

    /**
     * Point the backgrounds at the current artwork.
     *
     * [seed] is the track's artwork URL (or id). Same seed twice is a no-op, so
     * a recomposition or a position tick never re-extracts. `null`/blank clears
     * the artwork palette and every section falls back to manual-or-default.
     */
    fun setArtworkSeed(seed: String?) {
        val key = seed?.takeIf { it.isNotBlank() }
        if (key == lastSeed) return
        lastSeed = key
        if (key == null) {
            update { it.copy(artwork = null, artworkLuminance = null, artworkSaturation = null, revision = it.revision + 1) }
            return
        }
        // The one extraction pass, using the palette the app already computes.
        val colors = ArtworkColorExtractor.extractFromSeed(key)
        // An "artwork" that extracts to the brand fallback is not artwork
        // colour: treat it as absent so each section keeps its own palette
        // instead of silently painting the accent twice.
        val derived = if (colors == ArtworkColors.fallback) {
            null
        } else {
            BackgroundPalettePolicy.artworkPalette(art = colors, tokens = DhunAppearance.tokens)
        }
        update {
            it.copy(
                artwork = derived,
                artworkLuminance = colors.primary.luminance(),
                artworkSaturation = colors.primary.saturation(),
                revision = it.revision + 1,
            )
        }
    }

    private var lastSeed: String? = null

    /** Re-derive the artwork palette after a theme change (the base is theme-relative). */
    fun refreshArtworkPalette() {
        val seed = lastSeed ?: return
        lastSeed = null
        setArtworkSeed(seed)
    }

    /* ----------------------------------------------------------- resolve */

    /** What this section paints right now. */
    fun paletteFor(section: BackgroundSection): BackgroundPalette {
        val snapshot = state.value
        val tokens = DhunAppearance.tokens
        val prefs = snapshot.section(section)
        return BackgroundPalettePolicy.resolve(
            section = section,
            tokens = tokens,
            globalDynamicColors = snapshot.dynamicColors,
            sectionDynamicColors = prefs.dynamicColors,
            manual = prefs.manual,
            artwork = snapshot.artwork,
        )
    }

    /** Why it paints that — for the Settings caption. */
    fun sourceFor(section: BackgroundSection): PaletteSource {
        val snapshot = state.value
        val prefs = snapshot.section(section)
        return BackgroundPalettePolicy.source(
            globalDynamicColors = snapshot.dynamicColors,
            sectionDynamicColors = prefs.dynamicColors,
            hasManual = prefs.manual != null,
            hasArtwork = snapshot.artwork != null,
        )
    }

    /**
     * The blur percentage this section renders; 0 means none.
     *
     * [strengthPercent] is the user's blur-strength slider: it scales the
     * resolved radius but can never switch a disabled blur back on.
     */
    fun blurPercentFor(
        section: BackgroundSection,
        strengthPercent: Int = DhunAppearance.BACKDROP_BLUR_DEFAULT,
    ): Int {
        val snapshot = state.value
        val prefs = snapshot.section(section)
        val kind = BackgroundBlurPolicy.percentFor(
            globalBlurEnabled = snapshot.blurEnabled,
            sectionBlurEnabled = prefs.blurEnabled,
            dynamicBlur = snapshot.dynamicBlur,
            sectionDynamicBlur = prefs.dynamicBlur,
            artworkLuminance = snapshot.artworkLuminance,
            artworkSaturation = snapshot.artworkSaturation,
        )
        return BackgroundBlurPolicy.applyStrength(kind, strengthPercent)
    }

    /** Effective dynamic-colour availability for a section (both switches on). */
    fun usesArtworkColors(section: BackgroundSection): Boolean =
        state.value.dynamicColors && state.value.section(section).dynamicColors

    /* ------------------------------------------------------------ restore */

    /**
     * Replace the whole state. Used by the host to apply persisted values
     * before the first frame, and by tests.
     *
     * The snapshot is the settings half of the state; the artwork half is
     * *runtime* (a cover is not a preference), so it is handled explicitly:
     *
     * - a restored snapshot that carries an artwork palette keeps it;
     * - otherwise a track that is already playing keeps its colour, because its
     *   seed is re-derived instead of dropped — that is what stops a settings
     *   load from washing the background back to the theme default mid-song;
     * - and with no artwork and no seed the stale seed is cleared, so "nothing
     *   playing" is a real state rather than a palette that reappears on the
     *   next theme change.
     */
    fun restore(snapshot: BackgroundState) {
        val seed = lastSeed
        val restoredArtwork = snapshot.artwork
        state.value = snapshot.copy(revision = state.value.revision + 1)
        if (restoredArtwork != null) return
        if (seed == null) {
            lastSeed = null
            return
        }
        lastSeed = null
        setArtworkSeed(seed)
    }

    /** Resets to documented defaults. Used by "reset all settings". */
    fun clear() {
        lastSeed = null
        state.value = BackgroundState(revision = state.value.revision + 1)
    }

    private fun update(transform: (BackgroundState) -> BackgroundState) {
        state.value = transform(state.value)
    }

    private fun updateSection(
        section: BackgroundSection,
        transform: (SectionBackgrounds) -> SectionBackgrounds,
    ) = update {
        val next = it.sections.toMutableMap()
        next[section] = transform(it.section(section))
        it.copy(sections = next, revision = it.revision + 1)
    }
}

/**
 * Palette ↔ settings-table encoding.
 *
 * Colours live in the settings table as `#AARRGGBB`, and an absent manual layer
 * is the empty string (never a magic colour, so a black pick and "not picked"
 * can never be confused). Decoding is total: a corrupt or hand-edited row is
 * *unset*, not a crash — the section falls back to its default.
 */
object BackgroundPaletteCodec {

    fun encodePalette(palette: BackgroundPalette?): List<String> =
        if (palette == null) listOf("", "", "") else palette.layers.map(::encodeColor)

    fun encodeColor(color: Color): String {
        // ARGB as unsigned hex. Kept on Long on purpose: `UInt.toString(radix)`
        // is still experimental API, and a colour row is not worth an opt-in.
        val argb = color.toArgb().toLong() and 0xFFFF_FFFFL
        return "#" + argb.toString(16).uppercase().padStart(8, '0')
    }

    fun decodeColor(raw: String?): Color? {
        val text = raw?.trim()?.removePrefix("#")?.takeIf { it.length == 8 } ?: return null
        val value = text.toLongOrNull(16) ?: return null
        return Color(value.toInt())
    }

    /**
     * A palette is only decoded when **all three** layers are present: a half
     * written palette would paint two layers of nothing.
     */
    fun decodePalette(primary: String?, secondary: String?, tertiary: String?): BackgroundPalette? {
        val p = decodeColor(primary) ?: return null
        val s = decodeColor(secondary) ?: return null
        val t = decodeColor(tertiary) ?: return null
        return BackgroundPalette(p, s, t)
    }
}
