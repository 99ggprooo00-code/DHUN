package dev.dhun.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing

/**
 * The numbers behind [DhunSpinner], kept pure so the look can be asserted
 * without a rendered frame.
 */
object DhunSpinnerPolicy {

    /** One full turn; slow enough to read as motion, fast enough to feel alive. */
    const val ROTATION_MS = 900

    /** The visible arc. Less than a circle, so the spin is legible. */
    const val SWEEP_DEGREES = 270f

    /**
     * Faint full-circle track under the arc. This is the "glass groove": the
     * spinner sits *in* a surface instead of being a solid Material ring.
     */
    const val TRACK_ALPHA = 0.22f
}

/**
 * DHUN's spinner — a thin accent arc rotating inside a faint glass track.
 *
 * Material's `CircularProgressIndicator` is a thick, solid ring with Material's
 * own colour and no track: inside a DHUN button or a glass row it is the most
 * recognisably "stock Material 3" element on the screen. This keeps the same
 * semantics (an indeterminate progress range, so screen readers still announce
 * it) with the app's own geometry.
 */
@Composable
fun DhunSpinner(
    modifier: Modifier = Modifier,
    color: Color = DhunColors.accent,
    size: Dp = DhunSpacing.iconSizeSm,
    strokeWidth: Dp = DhunSpacing.iconStroke,
) {
    val transition = rememberInfiniteTransition(label = "dhun-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = DhunSpinnerPolicy.ROTATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dhun-spinner-rotation",
    )
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(width = this.size.width - stroke, height = this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = color.copy(alpha = DhunSpinnerPolicy.TRACK_ALPHA),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = DhunSpinnerPolicy.SWEEP_DEGREES,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
