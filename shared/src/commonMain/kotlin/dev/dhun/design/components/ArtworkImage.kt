package dev.dhun.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes

/**
 * ArtworkImage — Coil 3 powered, Compose Multiplatform.
 *
 * - crossfade when the image arrives (Coil's request.crossfade)
 * - pulsing placeholder while loading (infinite alpha)
 * - error gradient (placeholderStart → placeholderEnd) when load fails or url is null
 * - contentScale = Crop, rounded corners via [shape]
 */
@Composable
fun ArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.artwork,
    cornerRadius: Dp? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val resolvedShape: Shape = if (cornerRadius != null) RoundedCornerShape(cornerRadius) else shape

    if (imageUrl.isNullOrBlank()) {
        ArtworkPlaceholder(modifier = modifier, shape = resolvedShape)
        return
    }

    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .crossfade(true)
        .build()

    // Load phase drives the placeholder: pulse only while the fetch is in
    // flight; a failed load settles to a static gradient instead of pulsing
    // forever (which read as a "skeleton that never resolves").
    var phase by remember(imageUrl) { mutableStateOf(LoadPhase.Loading) }
    val pulse by rememberInfiniteTransition(label = "artwork-pulse")
        .animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha",
        )
    val placeholderAlpha = if (phase == LoadPhase.Loading) pulse else 1f

    Box(
        modifier = modifier.clip(resolvedShape),
    ) {
        // Placeholder layer (shows through with alpha while Coil fades in)
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(
                        DhunColors.placeholderStart.copy(alpha = placeholderAlpha),
                        DhunColors.placeholderEnd.copy(alpha = placeholderAlpha),
                    ),
                ),
            ),
        )
        if (phase == LoadPhase.Failed) {
            // A failed load used to leave a flat grey rectangle — the device
            // screenshots are full of them. Show the same glyph placeholder
            // the null-URL path uses so the grid reads as artwork-shaped.
            ArtworkPlaceholder(modifier = Modifier.fillMaxSize(), shape = resolvedShape)
        }
        if (phase != LoadPhase.Failed) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                // Coil handles crossfade; we keep the placeholder underneath.
                onLoading = { phase = LoadPhase.Loading },
                onSuccess = { phase = LoadPhase.Loaded },
                onError = { phase = LoadPhase.Failed },
            )
        }
    }
}

/**
 * Artwork stand-in: a soft diagonal wash plus a dimmed note glyph. Reads as
 * "no cover art" rather than "this pane failed to render".
 */
@Composable
private fun ArtworkPlaceholder(modifier: Modifier, shape: Shape) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(DhunColors.placeholderStart, DhunColors.placeholderEnd),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        DhunIconView(
            icon = DhunIcon.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.34f).aspectRatio(1f),
            tint = DhunColors.textHint,
        )
    }
}

/** Placeholder lifecycle for [ArtworkImage] (resets per URL). */
private enum class LoadPhase { Loading, Loaded, Failed }

/**
 * Circular variant for artist avatars.
 */
@Composable
fun ArtistArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    ArtworkImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        shape = DhunShapes.full,
    )
}
