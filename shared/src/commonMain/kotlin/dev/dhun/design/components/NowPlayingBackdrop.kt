package dev.dhun.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.dhun.design.ArtworkUrls
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTokens
import dev.dhun.design.DhunVisualTuning
import dev.dhun.design.VisualPart
import dev.dhun.design.background.BackgroundBlurPolicy
import dev.dhun.design.background.BackgroundComponent
import dev.dhun.design.background.DhunBackgrounds
import dev.dhun.design.background.LocalBackgroundComponent
import dev.dhun.design.supportsRealtimeBlur

/**
 * The atmospheric backdrop behind Home, Search and Library: the artwork of the
 * **currently playing** song, edge to edge, under everything else.
 *
 * Only painted while Background blur is on for this surface: "blur off" means
 * no thumbnail behind the app, not a sharp or dimmed one.
 *
 * On API 31+ / desktop the cover is heavily blurred (user-controlled). On
 * Android below API 31 `Modifier.blur` is a no-op, so the thumbnail is
 * drawn sharp. A single theme-aware translucent veil follows the brightness
 * setting. When the thumbnail is absent, loading or fails, the veil fades with
 * it so only the DHUN fallback background remains.
 */
@Composable
fun NowPlayingBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    component: BackgroundComponent? = null,
) {
    if (!NowPlayingBackdropPolicy.shouldRenderBackdrop(artworkUrl)) return
    val resolvedComponent = component ?: LocalBackgroundComponent.current
    val url = remember(artworkUrl) { NowPlayingBackdropPolicy.resolveUrl(artworkUrl) } ?: return

    // Blur is resolved per surface and per the switch: with Background blur off
    // the artwork is not painted *at all* (not sharp, not dimmed) and the
    // surface is the component's own colour. That early return is the whole
    // difference between "blur off" and "blur replaced by a dark picture".
    val blurPercent = DhunBackgrounds.blurPercentFor(
        component = resolvedComponent,
        strengthPercent = DhunAppearance.backdropBlurPercent,
    )
    if (!BackgroundBlurPolicy.paintsArtwork(blurPercent)) return
    val brightnessPercent = DhunAppearance.backdropBrightnessPercent
    val canBlur = supportsRealtimeBlur
    val tokens = DhunAppearance.tokens
    val veilStops = NowPlayingBackdropPolicy.veilStops(tokens.isLight, brightnessPercent)
        .map { (offset, alpha) -> offset to DhunVisualTuning.veilAlpha(VisualPart.PAGE_BACKDROP, alpha) }
    // The veil carries the surface's colour: with Dynamic colours on that is the
    // artwork's, otherwise the user's pick for this surface, so a frosted page
    // is tinted either way (and never just a theme grey). Animated on the same
    // curve as the palette, so a track change crossfades the tint instead of
    // snapping it a beat before the blooms catch up.
    val veilTint by animateColorAsState(
        targetValue = DhunBackgrounds.paletteFor(resolvedComponent).base,
        animationSpec = DhunAnimations.slowTween(),
        label = "pageVeilTint",
    )

    val context = LocalPlatformContext.current
    var phase by remember(artworkUrl) { mutableStateOf(NowPlayingBackdropPhase.Idle) }
    val artworkAlpha by animateFloatAsState(
        targetValue = if (phase == NowPlayingBackdropPhase.Loaded) 1f else 0f,
        animationSpec = DhunAnimations.slowTween(),
        label = "nowPlayingBackdrop",
    )

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = NowPlayingBackdropPolicy.OVERSCAN
                    scaleY = NowPlayingBackdropPolicy.OVERSCAN
                    alpha = artworkAlpha
                }
                .then(
                    if (canBlur && blurPercent > 0) {
                        Modifier.blur(
                            DhunSpacing.glassBlur * NowPlayingBackdropPolicy.BLUR_SCALE *
                                NowPlayingBackdropPolicy.blurMultiplier(blurPercent),
                        )
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.Crop,
            onLoading = { phase = NowPlayingBackdropPhase.Loading },
            onSuccess = {
                phase = NowPlayingBackdropPhase.Loaded
            },
            onError = { phase = NowPlayingBackdropPhase.Failed },
        )
        // One theme-aware veil, faded with the image. No permanent overlay on
        // the fallback background while a request is loading or has failed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = artworkAlpha }
                .background(
                    Brush.verticalGradient(
                        colorStops = veilStops.map { (offset, alpha) ->
                            offset to veilTint.copy(alpha = alpha)
                        }.toTypedArray(),
                    ),
                ),
        )
    }
}

/**
 * The ambient page base: DHUN's own artwork-inspired colour, painted even when
 * nothing is playing.
 *
 * Without this, every screen that is not backed by a cover (first run, Library,
 * Settings, an airplaned device) sat on one flat `background` colour with a
 * grid of translucent rectangles on top — the exact "ordinary Material 3"
 * reading the owner keeps reporting. Two soft blooms (the artwork-derived
 * accent plus a cool secondary) give those pages depth and colour while staying
 * far inside the luminance range the contrast gates already measure, because
 * they are painted under the rows and under the artwork veil.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    component: BackgroundComponent? = null,
) {
    val resolvedComponent = component ?: LocalBackgroundComponent.current
    val tokens = DhunAppearance.tokens
    // The resolved palette is the whole colour feature in one call: artwork
    // colours when Dynamic colours is on and this surface follows it, otherwise
    // the user's colour for this surface, otherwise the DHUN default.
    val palette = DhunBackgrounds.paletteFor(resolvedComponent)
    // With no artwork behind it, this *is* the background rather than a wash
    // under one: the glows get a little more presence so the surface reads as a
    // deliberate colour instead of a flat dark page.
    val solid = !DhunBackgrounds.showsArtwork(resolvedComponent)
    // Track changes crossfade instead of snapping: the palette is derived from
    // the artwork, so it changes exactly when the song does.
    val base by animateColorAsState(palette.base, DhunAnimations.slowTween(), label = "bgBase")
    val secondary by animateColorAsState(palette.mainGlow, DhunAnimations.slowTween(), label = "bgMainGlow")
    val tertiary by animateColorAsState(palette.companionGlow, DhunAnimations.slowTween(), label = "bgCompanionGlow")
    val bloom = AmbientBackdropPolicy.bloomAlpha(tokens.isLight, solid)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = base)
        AmbientBackdropPolicy.layers(this.size, secondary, tertiary, bloom).forEach { layer ->
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(layer.color, Color.Transparent),
                    center = layer.center,
                    radius = layer.radius,
                ),
            )
        }
    }
}

/** One radial bloom: where it sits, how far it reaches, what it is made of. */
data class AmbientBloom(
    val center: Offset,
    val radius: Float,
    val color: Color,
)

object AmbientBackdropPolicy {

    /** The main bloom's alpha in the dark theme. Subtle on purpose. */
    const val ACCENT_BLOOM_ALPHA_DARK = 0.16f

    /** Light glass needs less of it; white-on-white turns into a smear. */
    const val ACCENT_BLOOM_ALPHA_LIGHT = 0.11f

    /** The companion bloom is weaker than the main one. */
    const val SECONDARY_SCALE = 0.55f

    /** A solid surface's glows are stronger than a wash over artwork. */
    const val SOLID_BLOOM_SCALE = 1.45f

    /** Bloom radius as a fraction of the shorter viewport edge. */
    const val BLOOM_RADIUS = 0.85f

    /** Where the main bloom sits, as fractions of width/height. */
    const val ACCENT_CENTER_X = 0.18f
    const val ACCENT_CENTER_Y = 0.06f

    /** Where the companion bloom sits. */
    const val SECONDARY_CENTER_X = 0.92f
    const val SECONDARY_CENTER_Y = 0.78f

    /**
     * The main glow's alpha. A solid surface (no artwork behind it) gets a
     * little more of it: the glow is then part of the background rather than a
     * tint over a photograph, and the colour the user picked has to be visible.
     */
    fun bloomAlpha(light: Boolean, solid: Boolean = false): Float {
        val base = if (light) ACCENT_BLOOM_ALPHA_LIGHT else ACCENT_BLOOM_ALPHA_DARK
        return if (solid) base * SOLID_BLOOM_SCALE else base
    }

    /**
     * The two blooms for a palette's secondary and tertiary layers, sized from
     * the viewport so they scale with the window (a desktop window gets a
     * window-sized bloom, not a phone-sized dot).
     */
    fun layers(
        size: Size,
        secondary: Color,
        tertiary: Color,
        bloomAlpha: Float,
    ): List<AmbientBloom> {
        val radius = BLOOM_RADIUS * minOf(size.width, size.height)
        return listOf(
            AmbientBloom(
                center = Offset(size.width * ACCENT_CENTER_X, size.height * ACCENT_CENTER_Y),
                radius = radius,
                color = secondary.copy(alpha = bloomAlpha),
            ),
            AmbientBloom(
                center = Offset(size.width * SECONDARY_CENTER_X, size.height * SECONDARY_CENTER_Y),
                radius = radius,
                color = tertiary.copy(alpha = bloomAlpha * SECONDARY_SCALE),
            ),
        )
    }

    /** Kept for callers/tests that name the two source colours. */
    fun blooms(
        size: Size,
        accent: Color,
        secondary: Color,
        bloomAlpha: Float,
    ): List<AmbientBloom> = layers(size, accent, secondary, bloomAlpha)
}

/**
 * A detail page's own artwork backdrop (album, artist, playlist).
 *
 * The component is the one the page is composed *on*: a detail page lives on
 * the main pages, so it takes that surface's colour and blur switch rather than
 * pinning itself to a section (which is how an album opened from Library used to
 * keep Home's settings).
 */
@Composable
fun PageArtworkBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    component: BackgroundComponent? = null,
) {
    NowPlayingBackdrop(
        artworkUrl = artworkUrl,
        modifier = modifier,
        component = component ?: LocalBackgroundComponent.current,
    )
}

enum class NowPlayingBackdropPhase { Idle, Loading, Loaded, Failed }

object NowPlayingBackdropPolicy {

    const val BLUR_SCALE = 4

    const val OVERSCAN = 1.15f

    val artworkSizePx: Int get() = ArtworkUrls.LIST_SIZE_PX

    fun shouldRenderBackdrop(thumbnailUrl: String?): Boolean =
        resolveUrl(thumbnailUrl) != null

    fun resolveUrl(thumbnailUrl: String?): String? {
        if (thumbnailUrl.isNullOrBlank()) return null
        return ArtworkUrls.list(thumbnailUrl, artworkSizePx)
    }

    fun blurMultiplier(percent: Int): Float =
        percent.coerceIn(0, 100) / 100f

    /**
     * Screen artwork (including the full player) gets a tonal, not
     * near-black/pure-white, veil. The queue sheet and its lyrics material
     * deliberately do NOT use this policy.
     */
    fun veilTint(tokens: DhunTokens) =
        if (tokens.isLight) tokens.surfaceContainerHighest else tokens.surfaceElevated


    /**
     * One translucent veil; brightness changes opacity, never the tint.
     * Slightly more coverage on dark artwork in light mode offsets the darker
     * warm tint, while lighter dark-mode tint lets artwork glow through.
     */
    fun veilStops(isLight: Boolean, brightnessPercent: Int): List<Pair<Float, Float>> {
        val adjustment = (55 - brightnessPercent.coerceIn(0, 100)) / 100f * 0.30f
        val base = if (isLight) listOf(
            0f to 0.72f, 0.35f to 0.51f, 0.72f to 0.54f, 1f to 0.62f,
        ) else listOf(
            0f to 0.75f, 0.35f to 0.52f, 0.72f to 0.55f, 1f to 0.65f,
        )
        return base.map { (offset, alpha) -> offset to (alpha + adjustment).coerceIn(0.18f, 0.86f) }
    }
}
