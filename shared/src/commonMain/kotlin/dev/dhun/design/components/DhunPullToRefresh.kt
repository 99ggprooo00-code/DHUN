package dev.dhun.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import kotlin.math.roundToInt

/**
 * Pull-to-refresh, with DHUN's own indicator.
 *
 * The gesture itself is Material's (`PullToRefreshBox` owns the nested-scroll
 * plumbing, the threshold and the platform behaviour on both Android and the
 * desktop window), but its *indicator* — a Material circular progress ring in a
 * Material surface — is exactly the kind of stock look that kept appearing on
 * top of the glass. So the container is used only as mechanics, with a custom
 * `indicator` slot: a small glass disc that trails the pull and an accent arc
 * that fills as the finger travels, then the DHUN spinner once the refresh runs.
 *
 * Screens should call this instead of `PullToRefreshBox(...)` directly.
 */
@Composable
fun DhunPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = { DhunRefreshIndicator(state = state, isRefreshing = isRefreshing) },
        content = content,
    )
}

/**
 * The disc that comes down with the pull. It is deliberately *quiet*: at rest
 * it is invisible, and it only reaches full strength once the gesture has
 * travelled far enough to mean something ([FULL_ALPHA_AT]), so a stray drag
 * does not flash chrome over the page.
 */
@Composable
private fun BoxScope.DhunRefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean) {
    val distance = state.distanceFraction.coerceIn(0f, 1f)
    // Keep the disc on screen while a refresh runs, even though the finger is
    // long gone: the pull distance is 0 by then.
    val reveal = if (isRefreshing) 1f else (distance / FULL_ALPHA_AT).coerceIn(0f, 1f)
    if (reveal <= 0f) return

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = DhunSpacing.sm)
            .offset { IntOffset(0, (distance * PULL_TRAVEL.toPx()).roundToInt()) }
            .size(INDICATOR_SIZE)
            // The disc is the same milky glass as the dock, faded in by the
            // pull: `reveal` scales the token's own alpha rather than replacing
            // it, so the disc is never more opaque than glass anywhere else.
            .background(
                DhunColors.glassStrong.copy(alpha = DhunColors.glassStrong.alpha * reveal),
                CircleShape,
            )
            .border(
                width = DhunSpacing.divider,
                color = DhunColors.glassEdge.copy(alpha = DhunColors.glassEdge.alpha * reveal),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            DhunSpinner(
                size = INDICATOR_SIZE - DhunSpacing.md,
                strokeWidth = DhunSpacing.iconStroke,
                color = DhunColors.accent,
            )
        } else {
            // While pulling, the arc *is* the progress: it fills to a full turn
            // exactly when the release would trigger a refresh.
            Canvas(modifier = Modifier.size(ARC_SIZE)) {
                val stroke = DhunSpacing.iconStroke.toPx()
                // Inset by half a stroke so the round caps stay inside the disc.
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = DhunColors.accent.copy(alpha = reveal),
                    startAngle = -90f,
                    sweepAngle = DhunSpinnerPolicy.SWEEP_DEGREES * distance,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** The disc. Small enough to read as a hint, large enough to hold a glyph. */
private val INDICATOR_SIZE = 36.dp

/** The arc inside it — inset from the disc's rim by the padding above. */
private val ARC_SIZE = 20.dp

/** How far the disc follows the finger, at a full pull. */
private val PULL_TRAVEL = 28.dp

/** A pull shorter than this is a scroll, not a refresh: stay invisible. */
private const val FULL_ALPHA_AT = 0.35f
