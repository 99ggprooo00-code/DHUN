package dev.dhun.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * Shimmer — used for loading skeletons (Home/Search). A moving linear
 * gradient sweeps across placeholder bars; no spinner.
 */
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "shimmer-offset",
    )
    val brush = Brush.linearGradient(
        colors = listOf(DhunColors.shimmerBase, DhunColors.shimmerHighlight, DhunColors.shimmerBase),
        start = Offset(offset - 400f, 0f),
        end = Offset(offset, 200f),
    )
    Box(
        modifier = modifier
            .clip(DhunShapes.small)
            .background(brush),
    )
}

/** Row skeleton matching TrackRow dimensions. */
@Composable
fun TrackRowShimmer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingShimmer(modifier = Modifier.size(DhunSpacing.artworkThumb))
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = DhunSpacing.md),
        ) {
            LoadingShimmer(modifier = Modifier.fillMaxWidth(0.6f).height(DhunSpacing.md))
            Spacer(modifier = Modifier.height(DhunSpacing.xs))
            LoadingShimmer(modifier = Modifier.fillMaxWidth(0.4f).height(DhunSpacing.sm))
        }
        LoadingShimmer(modifier = Modifier.size(DhunSpacing.iconSize))
    }
}

@Composable
fun SectionShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = DhunSpacing.sm)) {
        LoadingShimmer(modifier = Modifier.width(120.dp).height(DhunSpacing.lg))
        Spacer(modifier = Modifier.height(DhunSpacing.sm))
        repeat(3) {
            TrackRowShimmer()
        }
    }
}
