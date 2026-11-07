package dev.dhun.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
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
import dev.dhun.design.supportsRealtimeBlur

/**
 * The atmospheric backdrop behind Home, Search and Library: the artwork of the
 * **currently playing** song, edge to edge, under everything else.
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
) {
    if (!NowPlayingBackdropPolicy.shouldRenderBackdrop(artworkUrl)) return
    val url = remember(artworkUrl) { NowPlayingBackdropPolicy.resolveUrl(artworkUrl) } ?: return

    val blurPercent = DhunAppearance.backdropBlurPercent
    val brightnessPercent = DhunAppearance.backdropBrightnessPercent
    val canBlur = supportsRealtimeBlur
    val blurArtwork = GlassMaterialPolicy.shouldBlurArtwork(canBlur, blurPercent)
    val tokens = DhunAppearance.tokens
    val veilStops = NowPlayingBackdropPolicy.veilStops(tokens.isLight, brightnessPercent)
        .map { (offset, alpha) -> offset to DhunVisualTuning.veilAlpha(VisualPart.PAGE_BACKDROP, alpha) }
    val veilTint = NowPlayingBackdropPolicy.veilTint(tokens)

    val context = LocalPlatformContext.current
    var phase by remember(artworkUrl) { mutableStateOf(NowPlayingBackdropPhase.Idle) }
    val artworkAlpha by animateFloatAsState(
        targetValue = if (phase == NowPlayingBackdropPhase.Loaded) {
            NowPlayingBackdropPolicy.loadedLayerAlpha(isLight = tokens.isLight, blurActive = blurArtwork)
        } else {
            0f
        },
        animationSpec = DhunAnimations.slowTween(),
        label = "nowPlayingBackdrop",
    )

    NowPlayingBackdropLayer(
        modifier = modifier,
        artworkAlpha = artworkAlpha,
        veilTint = veilTint,
        veilStops = veilStops,
    ) {
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
                }
                .then(
                    if (blurArtwork) {
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
    }
}

/**
 * Shared composition layer for the image and its single tonal veil. Exposed to
 * this module's JVM pixel test so it can inject deterministic color artwork
 * without depending on platform-specific Coil file loading.
 */
@Composable
internal fun NowPlayingBackdropLayer(
    artworkAlpha: Float,
    veilTint: Color,
    veilStops: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    artwork: @Composable BoxScope.() -> Unit,
) {
    // Fade the already-composited artwork + veil as ONE layer. Applying the
    // fallback alpha to each child separately would apply it twice to the
    // artwork wherever the translucent veil overlaps it, and would also leave
    // a different amount of theme base visible than the real-blur path.
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { alpha = artworkAlpha },
    ) {
        artwork()
        // One theme-aware veil, faded with the image. No permanent overlay on
        // the fallback background while a request is loading or has failed.
        Box(
            modifier = Modifier
                .fillMaxSize()
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

@Composable
fun PageArtworkBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    NowPlayingBackdrop(artworkUrl = artworkUrl, modifier = modifier)
}

enum class NowPlayingBackdropPhase { Idle, Loading, Loaded, Failed }

object NowPlayingBackdropPolicy {

    const val BLUR_SCALE = 4

    const val OVERSCAN = 1.15f

    /**
     * Without real blur, full-strength bright/dark covers can overpower direct
     * text even after the tonal veil. Fade the established artwork+veil layer
     * over the theme base instead of adding another scrim or a CPU blur.
     */
    const val NON_BLUR_LAYER_ALPHA = 0.40f

    /**
     * Light theme keeps a little **more** of the sharp artwork in the blur-off
     * fallback. [NON_BLUR_LAYER_ALPHA] was tuned against the dark theme, where
     * 40% of a dark cover over the near-black base reads as a dimmed, intentional
     * backdrop. Composited over the warm-white light base the same 40% reads as
     * a pale smear — the artwork is neither visible nor clean, which is the
     * reported "it doesn't work as expected on light mode".
     *
     * Why exactly 0.44: the cover has to stay visible while the tertiary text
     * token — the faintest one drawn directly on the page — keeps its WCAG gate
     * against the worst case (a pure-black cover at 100% brightness, where the
     * veil thins to 0.375). With the light tokens that gate holds up to 0.46 and
     * fails from 0.50 (tertiary drops to 2.92:1 against a 3:1 floor), so the fix
     * is a targeted alpha change, not a heavier veil stacked on a heavier layer.
     * Pinned by
     * DhunThemeContrastTest.nonBlurBackdropProtectsDirectTextOverExtremeArtwork.
     */
    const val LIGHT_NON_BLUR_LAYER_ALPHA = 0.44f

    /** Theme-aware blur-off fallback. */
    fun loadedLayerAlpha(isLight: Boolean, blurActive: Boolean): Float = when {
        blurActive -> 1f
        isLight -> LIGHT_NON_BLUR_LAYER_ALPHA
        else -> NON_BLUR_LAYER_ALPHA
    }

    /** Dark-theme default, kept so existing callers and tests keep their meaning. */
    fun loadedLayerAlpha(blurActive: Boolean): Float = loadedLayerAlpha(isLight = false, blurActive = blurActive)

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
        // Light keeps ONE translucent palette veil, lighter than dark — not a
        // black dim under a white scrim, and never opaque enough to hide the
        // artwork. The light-mode fix lives in the layer alpha
        // ([LIGHT_NON_BLUR_LAYER_ALPHA]), not here: stacking a heavier veil on
        // top of a heavier layer double-counts and buries the cover.
        val base = if (isLight) listOf(
            0f to 0.72f, 0.35f to 0.51f, 0.72f to 0.54f, 1f to 0.62f,
        ) else listOf(
            0f to 0.75f, 0.35f to 0.52f, 0.72f to 0.55f, 1f to 0.65f,
        )
        return base.map { (offset, alpha) -> offset to (alpha + adjustment).coerceIn(0.18f, 0.86f) }
    }
}
