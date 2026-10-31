package dev.dhun.design.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
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
            NowPlayingBackdropPolicy.loadedLayerAlpha(blurArtwork)
        } else {
            0f
        },
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

    fun loadedLayerAlpha(blurActive: Boolean): Float = if (blurActive) 1f else NON_BLUR_LAYER_ALPHA

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
