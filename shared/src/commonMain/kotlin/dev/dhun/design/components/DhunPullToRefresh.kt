package dev.dhun.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import kotlin.math.roundToInt

/** Pure reveal/progress rules for the DHUN pull indicator. */
object DhunRefreshPolicy {
    const val FULL_ALPHA_AT = 0.35f

    fun reveal(distanceFraction: Float, isRefreshing: Boolean): Float =
        if (isRefreshing) 1f else (distanceFraction / FULL_ALPHA_AT).coerceIn(0f, 1f)

    fun sweepFraction(distanceFraction: Float): Float = distanceFraction.coerceIn(0f, 1f)
}

/** Keeps the platform gesture mechanics but replaces the visible indicator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhunPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = { DhunRefreshIndicator(state, isRefreshing) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.DhunRefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean) {
    val distance = DhunRefreshPolicy.sweepFraction(state.distanceFraction)
    val reveal = DhunRefreshPolicy.reveal(state.distanceFraction, isRefreshing)
    if (reveal <= 0f) return

    GlassCard(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = DhunSpacing.sm)
            .offset { IntOffset(0, (distance * PULL_TRAVEL.toPx()).roundToInt()) }
            .size(INDICATOR_SIZE)
            .graphicsLayer { alpha = reveal },
        shape = CircleShape,
        elevated = false,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isRefreshing) {
                DhunSpinner(size = ARC_SIZE, strokeWidth = DhunSpacing.iconStroke)
            } else {
                Canvas(modifier = Modifier.size(ARC_SIZE)) {
                    val stroke = DhunSpacing.iconStroke.toPx()
                    drawArc(
                        color = DhunColors.accent.copy(alpha = DhunColors.accent.alpha * reveal),
                        startAngle = -90f,
                        sweepAngle = DhunSpinnerPolicy.SWEEP_DEGREES * distance,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

private val INDICATOR_SIZE = 36.dp
private val ARC_SIZE = 20.dp
private val PULL_TRAVEL = 28.dp
