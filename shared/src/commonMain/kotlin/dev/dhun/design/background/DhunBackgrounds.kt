package dev.dhun.design.background

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.ArtworkColors
import dev.dhun.design.DhunAppearance
import dev.dhun.design.luminance

/**
 * DHUN's background preferences, live for the whole process.
 *
 * Two independent features and one customisation:
 *
 * - **Background blur** — is the current track's artwork painted, blurred,
 *   behind the interface? Off means it is not painted at all.
 * - **Dynamic colours** — do the surfaces take their colour from that artwork?
 *   Off means they use the colour the user picked for each surface.
 * - **Per-component colour** — one colour per surface (pages, navigation bar,
 *   miniplayer, full player), used when the colour is not coming from artwork.
 *
 * Every combination is valid, including "no artwork, artwork colours" and
 * "artwork, my colours". Nothing here decodes an image: the artwork colour comes
 * from the *existing* extraction pass the player tint already uses (one image
 * pipeline, cached per seed), so changing a colour preference costs nothing and
 * changing the track costs one extraction that was already happening.
 */
object DhunBackgrounds {

    private val state = mutableStateOf(BackgroundState())

    /** The current snapshot. Reading this in composition subscribes to changes. */
    val current: BackgroundState get() = state.value

    /* ------------------------------------------------------------ globals */

    /** The artwork-as-background switch. Off suspends every surface's blur. */
    fun setBlurEnabled(enabled: Boolean) = update { it.copy(blurEnabled = enabled) }

    /** Whether an enabled frost adapts to the artwork. Meaningless with blur off. */
    fun setAdaptiveBlur(enabled: Boolean) = update { it.copy(adaptiveBlur = enabled) }

    /** Artwork-derived colours. Off restores the per-surface picks. Independent of blur. */
    fun setDynamicColors(enabled: Boolean) = update { it.copy(dynamicColors = enabled) }

    /* --------------------------------------------------------- components */

    fun setComponentBlurEnabled(component: BackgroundComponent, enabled: Boolean) =
        updateComponent(component) { it.copy(blurEnabled = enabled) }

    fun setComponentFollowArtwork(component: BackgroundComponent, enabled: Boolean) =
        updateComponent(component) { it.copy(followArtwork = enabled) }

    /**
     * A colour for one surface, or `null` to go back to the DHUN default.
     *
     * Sanitised on the way in when it is painted: a colour that would push the
     * base outside the contrast band is clamped by the palette derivation
     * rather than rejected, so any pick stays usable and stays the colour the
     * user chose.
     */
    fun setComponentColor(component: BackgroundComponent, color: Color?) =
        updateComponent(component) { it.copy(color = color) }

    /** Forget one surface's choices: blur, artwork colours and picked colour. */
    fun resetComponent(component: BackgroundComponent) =
        updateComponent(component) { ComponentBackgrounds.default }

    /** Forget every surface's choices. The global switches are untouched. */
    fun resetComponents() = update {
        it.copy(components = emptyMap(), revision = it.revision + 1)
    }

    /* ------------------------------------------------------------ artwork */

    /**
     * Point the backgrounds at the current artwork.
     *
     * [seed] is the track's artwork URL (or id). The same seed twice is a no-op,
     * so a recomposition or a position tick never re-extracts. `null`/blank
     * clears it and every surface falls back to its own colour.
     */
    fun setArtworkSeed(seed: String?) {
        val key = seed?.takeIf { it.isNotBlank() }
        if (key == lastSeed) return
        lastSeed = key
        if (key == null) {
            update {
                it.copy(
                    artworkPrimary = null,
                    artworkLuminance = null,
                    artworkSaturation = null,
                    revision = it.revision + 1,
                )
            }
            return
        }
        // The one extraction pass, reusing the palette the app already computes.
        val colors = ArtworkColorExtractor.extractFromSeed(key)
        // An "artwork" that extracts to the brand fallback is not artwork
        // colour: treat it as absent so each surface keeps its own colour
        // instead of silently painting the accent twice.
        val primary = if (colors == ArtworkColors.fallback) null else colors.primary
        update {
            it.copy(
                artworkPrimary = primary,
                artworkLuminance = colors.primary.luminance(),
                artworkSaturation = colors.primary.saturation(),
                revision = it.revision + 1,
            )
        }
    }

    private var lastSeed: String? = null

    /** Re-derive after a theme change (the base is theme-relative). */
    fun refreshArtworkPalette() {
        val seed = lastSeed ?: return
        lastSeed = null
        setArtworkSeed(seed)
    }

    /* ----------------------------------------------------------- resolve */

    /** Whether this surface paints the artwork behind itself right now. */
    fun showsArtwork(component: BackgroundComponent): Boolean =
        BackgroundBlurPolicy.paintsArtwork(blurPercentFor(component))

    /**
     * What this surface paints right now.
     *
     * Reads [showsArtwork] because that is the one difference the colour policy
     * cares about: with no cover behind it, the picked colour has to carry the
     * surface instead of being a wash over a photograph.
     */
    fun paletteFor(component: BackgroundComponent): BackgroundPalette {
        val snapshot = state.value
        val prefs = snapshot.component(component)
        val solid = !showsArtwork(component)
        return BackgroundPalettePolicy.resolve(
            component = component,
            tokens = DhunAppearance.tokens,
            dynamicColors = snapshot.dynamicColors,
            followArtwork = prefs.followArtwork,
            manualColor = prefs.color,
            artworkColor = snapshot.artworkPrimary,
            solid = solid,
        )
    }

    /** Why it paints that — for the Settings caption. */
    fun sourceFor(component: BackgroundComponent): PaletteSource {
        val snapshot = state.value
        val prefs = snapshot.component(component)
        return BackgroundPalettePolicy.source(
            dynamicColors = snapshot.dynamicColors,
            followArtwork = prefs.followArtwork,
            hasManual = prefs.color != null,
            hasArtwork = snapshot.artworkPrimary != null,
        )
    }

    /**
     * The blur percentage this surface renders; 0 means "no artwork here".
     *
     * [strengthPercent] is the user's blur-strength slider: it scales the
     * resolved radius but can never switch a disabled blur back on.
     */
    fun blurPercentFor(
        component: BackgroundComponent,
        strengthPercent: Int = DhunAppearance.BACKDROP_BLUR_DEFAULT,
    ): Int {
        val snapshot = state.value
        val prefs = snapshot.component(component)
        val kind = BackgroundBlurPolicy.percentFor(
            blurEnabled = snapshot.blurEnabled,
            componentBlurEnabled = prefs.blurEnabled,
            adaptive = snapshot.adaptiveBlur,
            artworkLuminance = snapshot.artworkLuminance,
            artworkSaturation = snapshot.artworkSaturation,
        )
        return BackgroundBlurPolicy.applyStrength(kind, strengthPercent)
    }

    /** Effective dynamic-colour availability for a surface (both switches on). */
    fun usesArtworkColors(component: BackgroundComponent): Boolean =
        state.value.dynamicColors && state.value.component(component).followArtwork

    /* ------------------------------------------------------------ restore */

    /**
     * Replace the whole state. Used by the host to apply persisted values
     * before the first frame, and by tests.
     *
     * The artwork half of a snapshot is *runtime* (a cover is not a
     * preference), so it is handled explicitly:
     *
     * - a restored snapshot that already carries an artwork colour keeps it;
     * - otherwise a track that is already playing keeps its colour, because its
     *   seed is re-derived instead of dropped — that is what stops a settings
     *   load from washing the background back to the default mid-song;
     * - and with no artwork and no seed the stale seed is cleared, so "nothing
     *   playing" is a real state rather than a colour that reappears later.
     */
    fun restore(snapshot: BackgroundState) {
        val seed = lastSeed
        val restoredArtwork = snapshot.artworkPrimary
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

    private fun updateComponent(
        component: BackgroundComponent,
        transform: (ComponentBackgrounds) -> ComponentBackgrounds,
    ) = update {
        val next = it.components.toMutableMap()
        next[component] = transform(it.component(component))
        it.copy(components = next, revision = it.revision + 1)
    }
}

/**
 * Colour ↔ settings-table encoding.
 *
 * One row per surface: `#AARRGGBB`, or the empty string for "no colour picked"
 * (never a magic colour, so a black pick and "not picked" can never be
 * confused). Decoding is total: a corrupt or hand-edited row is *unset*, not a
 * crash — the surface falls back to its default.
 */
object BackgroundColorCodec {

    fun encode(color: Color?): String {
        val value = color ?: return ""
        // ARGB as unsigned hex. Kept on Long on purpose: `UInt.toString(radix)`
        // is still experimental API, and a colour row is not worth an opt-in.
        val argb = value.toArgb().toLong() and 0xFFFF_FFFFL
        return "#" + argb.toString(16).uppercase().padStart(8, '0')
    }

    fun decode(raw: String?): Color? {
        val text = raw?.trim()?.removePrefix("#")?.takeIf { it.length == 8 } ?: return null
        val value = text.toLongOrNull(16) ?: return null
        return Color(value.toInt())
    }
}
