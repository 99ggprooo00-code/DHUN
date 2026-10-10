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
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.supportsRealtimeBlur

/**
 * The atmospheric backdrop behind Home, Search and Library: the artwork of the
 * **currently playing** song, edge to edge, under everything else.
 *
 * On API 31+ / desktop the cover is heavily blurred (user-controlled). On
 * Android below API 31 `Modifier.blur` is a no-op, so the same thumbnail is
 * drawn sharp and dimmed instead — brightness is user-controlled. Nothing is
 * drawn when the current song has no thumbnail, the URL is blank, or the load
 * fails; the default DHUN background shows through.
 */
@Composable
fun NowPlayingBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    dimColor: Color = Color.Black.copy(alpha = NowPlayingBackdropPolicy.DIM_ALPHA),
) {
    if (!NowPlayingBackdropPolicy.shouldRenderBackdrop(artworkUrl)) return
    val url = remember(artworkUrl) { NowPlayingBackdropPolicy.resolveUrl(artworkUrl) } ?: return

    val blurPercent = DhunAppearance.backdropBlurPercent
    val brightnessPercent = DhunAppearance.backdropBrightnessPercent
    val canBlur = supportsRealtimeBlur
    val overlay = if (canBlur) {
        dimColor
    } else {
        Color.Black.copy(alpha = NowPlayingBackdropPolicy.dimAlphaForBrightness(brightnessPercent))
    }

    val context = LocalPlatformContext.current
    var phase by remember(artworkUrl) { mutableStateOf(NowPlayingBackdropPhase.Idle) }
    var loadedOnce by remember { mutableStateOf(false) }
    val artworkAlpha by animateFloatAsState(
        targetValue = if (loadedOnce && phase != NowPlayingBackdropPhase.Failed) 1f else 0f,
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
                loadedOnce = true
                phase = NowPlayingBackdropPhase.Loaded
            },
            onError = { phase = NowPlayingBackdropPhase.Failed },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlay),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = NowPlayingBackdropPolicy.scrimStops()
                            .map { (offset, alpha) ->
                                offset to DhunColors.background.copy(alpha = alpha)
                            }
                            .toTypedArray(),
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

    const val DIM_ALPHA = 0.40f

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
     * Black overlay on the un-blurred thumbnail (API < 31). 0 = almost black,
     * 100 = the cover is clearly visible. Default 55 → ~0.48.
     */
    fun dimAlphaForBrightness(percent: Int): Float {
        val t = percent.coerceIn(0, 100) / 100f
        return 0.82f - 0.62f * t
    }

    fun scrimStops(): List<Pair<Float, Float>> = listOf(
        0.00f to 0.50f,
        0.35f to 0.32f,
        0.72f to 0.44f,
        1.00f to 0.62f,
    )
}
