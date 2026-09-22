package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.dhun.design.BlurredArtworkCache
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.supportsRealtimeBlur

/**
 * The lyrics-card material, as numbers — so the lyrics card, the Related
 * list, the nav band under the mini-player, and small chrome cannot drift.
 *
 * This is still the ADR-002 recipe (blur **once** per track, translucent M3
 * veil, sharp content). It is not Liquid Glass and not a platform Acrylic API.
 * "Acrylic" below is the lighter milky cut of the same recipe used by the
 * mini-player: more of the blur shows through than the old `glassStrong`
 * slab (~72% near-black), which read as a translucent black disc/bar.
 */
object LyricsMaterialPolicy {

    /** Lyrics-card readability veil, top → bottom. Applied to [DhunColors.background]. */
    const val LYRICS_VEIL_TOP = 0.42f

    /** Lyrics-card readability veil, bottom. Heavier so lines stay legible. */
    const val LYRICS_VEIL_BOTTOM = 0.62f

    /**
     * Flat black laid under the acrylic veil only, and only where this surface
     * paints its own artwork. The dock's art is undimmed (unlike the shell
     * backdrop); without this a bright cover blows out the title.
     */
    const val ACRYLIC_ART_DIM = 0.18f

    /** Acrylic veil, top. Lighter than the lyrics veil so the blur reads as glass. */
    const val ACRYLIC_VEIL_TOP = 0.34f

    /** Acrylic veil, bottom. Still well under the old `glassStrong` slab. */
    const val ACRYLIC_VEIL_BOTTOM = 0.48f

    /** Milky highlight on acrylic, top. Applied to white. */
    const val ACRYLIC_FROST_TOP = 0.14f

    /** Milky highlight, mid. */
    const val ACRYLIC_FROST_MID = 0.06f

    /** Milky highlight, bottom — a hint, not a second slab. */
    const val ACRYLIC_FROST_BOTTOM = 0.03f

    /**
     * Blur radius as a multiple of [DhunSpacing.glassBlur]. Same as the lyrics
     * card (`glassBlur * 2`), not the full-screen 64dp bleed.
     */
    const val BLUR_SCALE = 2

    fun lyricsVeilStops(): List<Pair<Float, Float>> = listOf(
        0f to LYRICS_VEIL_TOP,
        1f to LYRICS_VEIL_BOTTOM,
    )

    fun acrylicVeilStops(): List<Pair<Float, Float>> = listOf(
        0f to ACRYLIC_VEIL_TOP,
        1f to ACRYLIC_VEIL_BOTTOM,
    )

    fun acrylicFrostStops(): List<Pair<Float, Float>> = listOf(
        0f to ACRYLIC_FROST_TOP,
        0.42f to ACRYLIC_FROST_MID,
        1f to ACRYLIC_FROST_BOTTOM,
    )

    /**
     * Whether this surface should paint its own blurred artwork. A blank URL
     * or a platform that cannot really blur falls back to the veil alone, so
     * a sharp cover is never stretched under text.
     */
    fun shouldPaintArtwork(artworkUrl: String?, supportsBlur: Boolean): Boolean =
        !artworkUrl.isNullOrBlank() && supportsBlur
}

/** The lyrics-card veil. Call sites that already sit on a blurred backdrop use this alone. */
@Composable
fun lyricsVeilBrush(): Brush = Brush.verticalGradient(
    colorStops = LyricsMaterialPolicy.lyricsVeilStops()
        .map { (offset, alpha) -> offset to DhunColors.background.copy(alpha = alpha) }
        .toTypedArray(),
)

/** Lyrics veil, no extra artwork — the shell or player backdrop is the blur. */
@Composable
fun Modifier.lyricsVeil(shape: Shape = RectangleShape): Modifier =
    background(lyricsVeilBrush(), shape)

/**
 * Mini-player acrylic: a light dim, a veil lighter than the lyrics card, and
 * a white frost so the bar reads as glass rather than a near-black slab.
 */
@Composable
fun Modifier.acrylicGlass(shape: Shape = RectangleShape): Modifier {
    val veil = Brush.verticalGradient(
        colorStops = LyricsMaterialPolicy.acrylicVeilStops()
            .map { (offset, alpha) -> offset to DhunColors.background.copy(alpha = alpha) }
            .toTypedArray(),
    )
    val frost = Brush.verticalGradient(
        colorStops = LyricsMaterialPolicy.acrylicFrostStops()
            .map { (offset, alpha) -> offset to Color.White.copy(alpha = alpha) }
            .toTypedArray(),
    )
    return background(Color.Black.copy(alpha = LyricsMaterialPolicy.ACRYLIC_ART_DIM), shape)
        .background(veil, shape)
        .background(frost, shape)
}

/**
 * Lyrics-card surface for a region that does **not** already sit on blurred
 * artwork (the Related sheet paints an opaque base, so the list has to carry
 * its own blur). Content stays sharp.
 */
@Composable
fun LyricsMaterial(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.large,
    drawBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (drawBorder) {
                    Modifier.border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        LyricsArtworkLayer(artworkUrl = artworkUrl, modifier = Modifier.matchParentSize())
        Box(modifier = Modifier.matchParentSize().lyricsVeil())
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * Floating mini-player card (rail / two-pane). The phone dock paints the
 * artwork once for the whole bar and only veils the mini-player row with
 * [acrylicGlass]; this surface is the same recipe when the row is on its own.
 */
@Composable
fun AcrylicSurface(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.large,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                DhunSpacing.md,
                shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.28f),
            )
            .clip(shape)
            .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), shape),
    ) {
        LyricsArtworkLayer(artworkUrl = artworkUrl, modifier = Modifier.matchParentSize())
        Box(modifier = Modifier.matchParentSize().acrylicGlass())
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * Blurred now-playing artwork for one surface. List tier — a panel blurred
 * by 32dp cannot use the 1024px hero, and the shell backdrop already requests
 * this same URL. Skipped when blur is unsupported so a sharp cover never
 * fills the panel.
 */
@Composable
internal fun LyricsArtworkLayer(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val resolved = remember(artworkUrl) { NowPlayingBackdropPolicy.resolveUrl(artworkUrl) }
    if (!LyricsMaterialPolicy.shouldPaintArtwork(resolved, supportsRealtimeBlur)) return
    val cacheKey = remember(artworkUrl) { BlurredArtworkCache.keyFor(artworkUrl, null) }
    // AsyncImage, not ArtworkImage: a failed load there paints a note glyph
    // and a grey wash, which under a blur reads as a dirty slab. Hide it and
    // let the veil carry the surface — the same fallback as NowPlayingBackdrop.
    var failed by remember(resolved) { mutableStateOf(false) }
    if (failed) return
    val context = LocalPlatformContext.current
    key(cacheKey) {
        LaunchedEffect(cacheKey) {
            if (cacheKey.isNotBlank()) BlurredArtworkCache.markPrepared(cacheKey)
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolved)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier
                .graphicsLayer {
                    // Past the clip so the blur rim never draws a hard edge.
                    scaleX = NowPlayingBackdropPolicy.OVERSCAN
                    scaleY = NowPlayingBackdropPolicy.OVERSCAN
                }
                .blur(DhunSpacing.glassBlur * LyricsMaterialPolicy.BLUR_SCALE),
            contentScale = ContentScale.Crop,
            onError = { failed = true },
        )
    }
}
