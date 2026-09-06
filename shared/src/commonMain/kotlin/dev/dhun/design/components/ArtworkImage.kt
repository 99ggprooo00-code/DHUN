package dev.dhun.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import dev.dhun.design.DhunColors
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
        Box(
            modifier = modifier
                .clip(resolvedShape)
                .background(
                    Brush.linearGradient(
                        listOf(DhunColors.placeholderStart, DhunColors.placeholderEnd),
                    ),
                ),
        )
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
