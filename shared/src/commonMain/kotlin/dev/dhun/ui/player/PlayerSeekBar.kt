package dev.dhun.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import kotlinx.coroutines.delay
import kotlin.math.abs

/** The caller keys this whole timeline to the current track/queue occurrence. */
@Composable
internal fun PlayerTimeline(
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var scrubPosition by remember(durationMs, enabled) { mutableStateOf<Long?>(null) }
    var pendingPosition by remember(durationMs, enabled) { mutableStateOf<Long?>(null) }
    // Keep the committed preview until the engine's polling catches up, but
    // always fall back to real state if a seek is ignored or fails.
    LaunchedEffect(pendingPosition) {
        if (pendingPosition != null) {
            delay(SEEK_PREVIEW_TIMEOUT_MS)
            pendingPosition = null
        }
    }
    LaunchedEffect(positionMs, pendingPosition) {
        val pending = pendingPosition
        if (pending != null && durationMs > 0 &&
            abs(positionMs.coerceIn(0, durationMs) - pending) <= SEEK_ACK_TOLERANCE_MS
        ) {
            pendingPosition = null
        }
    }
    val displayedPosition = scrubPosition ?: pendingPosition ?: positionMs
    Column(modifier = modifier) {
        DhunSeekBar(
            positionMs = pendingPosition ?: positionMs,
            durationMs = durationMs,
            accent = accent,
            onSeek = { target -> pendingPosition = target; onSeek(target) },
            enabled = enabled,
            onScrubPositionChange = { scrubPosition = it },
        )
        Spacer(modifier = Modifier.height(DhunSpacing.xs))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatMs(if (durationMs > 0) displayedPosition.coerceIn(0, durationMs) else 0, durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (scrubPosition != null) accent else DhunColors.textTertiary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (durationMs > 0) formatMs(durationMs, durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
            )
        }
    }
}

/** 48dp target; single commit on release, cancellation-safe scrubbing, keyboard and range semantics. */
@Composable
internal fun DhunSeekBar(
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onScrubPositionChange: (Long?) -> Unit = {},
) {
    val canSeek = enabled && durationMs > 0
    val progress = playbackProgress(positionMs, durationMs)
    var dragging by remember(durationMs, canSeek) { mutableStateOf(false) }
    var dragFraction by remember(durationMs, canSeek) { mutableFloatStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrubPositionChange)
    val effective = if (dragging) dragFraction else progress
    val effectiveMs = seekPositionAt(effective, durationMs) ?: 0L
    val barHeight by animateDpAsState(
        targetValue = if (dragging || focused) DhunSpacing.progressHeightActive else DhunSpacing.progressHeight,
        animationSpec = DhunAnimations.fastTween(),
        label = "seekBarHeight",
    )
    val fill = if (canSeek) accent else DhunColors.textTertiary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(DhunSpacing.touchTarget)
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(effective, 0f..1f)
                stateDescription = if (durationMs > 0) {
                    "${formatMs(effectiveMs, durationMs)} of ${formatMs(durationMs, durationMs)}"
                } else {
                    "Duration unavailable"
                }
                if (canSeek) {
                    setProgress { fraction ->
                        val target = seekPositionAt(fraction, durationMs)
                        if (target == null || target == effectiveMs) {
                            false
                        } else {
                            currentOnSeek(target)
                            true
                        }
                    }
                } else {
                    disabled()
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val target = if (canSeek && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
                    seekPositionForKey(event.key, effectiveMs, durationMs)
                } else {
                    null
                }
                if (target == null) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) currentOnSeek(target)
                    true
                }
            }
            .focusable(enabled = canSeek)
            // Callback changes never restart an in-flight pointer gesture.
            .pointerInput(durationMs, canSeek) {
                if (!canSeek) return@pointerInput
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        seekPositionAt(offset.x / size.width, durationMs)?.let(currentOnSeek)
                    }
                }
            }
            .pointerInput(durationMs, canSeek) {
                if (!canSeek) return@pointerInput
                try {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0) {
                                dragging = true
                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                currentOnScrub(seekPositionAt(dragFraction, durationMs))
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            if (dragging && size.width > 0) {
                                change.consume()
                                // Absolute pointer position avoids counting the initial
                                // touch-slop delta twice and works outside the bar bounds.
                                dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                currentOnScrub(seekPositionAt(dragFraction, durationMs))
                            }
                        },
                        onDragEnd = {
                            try {
                                if (dragging) seekPositionAt(dragFraction, durationMs)?.let(currentOnSeek)
                            } finally {
                                dragging = false
                                currentOnScrub(null)
                            }
                        },
                        onDragCancel = { dragging = false; currentOnScrub(null) },
                    )
                } finally {
                    // Duration/track changes and disposal cancel without committing.
                    dragging = false
                    currentOnScrub(null)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        Box(
            modifier = Modifier.fillMaxWidth().height(barHeight)
                .clip(DhunShapes.full).background(DhunColors.border),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(effective).fillMaxHeight()
                    .clip(DhunShapes.full).background(fill),
            )
        }
        val thumbSize = if (dragging || focused) DhunSpacing.mdPlus else DhunSpacing.sm
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val thumbWidth = thumbSize.roundToPx()
                    IntOffset(
                        (effective * widthPx - thumbWidth / 2f).toInt()
                            .coerceIn(0, (widthPx - thumbWidth).toInt().coerceAtLeast(0)),
                        0,
                    )
                }
                .size(thumbSize)
                .shadow(DhunSpacing.xs, DhunShapes.full, clip = false)
                .clip(DhunShapes.full)
                .background(fill),
        )
    }
}

internal fun playbackProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f) else 0f

/** Reject non-finite accessibility input and unknown duration before converting to milliseconds. */
internal fun seekPositionAt(fraction: Float, durationMs: Long): Long? {
    if (!fraction.isFinite() || durationMs <= 0) return null
    return (fraction.coerceIn(0f, 1f).toDouble() * durationMs).toLong().coerceIn(0, durationMs)
}

internal fun seekPositionForKey(key: Key, positionMs: Long, durationMs: Long): Long? {
    if (durationMs <= 0) return null
    val position = positionMs.coerceIn(0, durationMs)
    return when (key) {
        Key.DirectionLeft, Key.DirectionDown -> (position - KEY_SEEK_STEP_MS).coerceAtLeast(0)
        Key.DirectionRight, Key.DirectionUp -> position + (durationMs - position).coerceAtMost(KEY_SEEK_STEP_MS)
        Key.MoveHome -> 0L
        Key.MoveEnd -> durationMs
        else -> null
    }
}

internal fun formatMs(ms: Long, referenceMs: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return if (referenceMs >= 3_600_000 || hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
    } else {
        "$minutes:$seconds"
    }
}

private const val KEY_SEEK_STEP_MS = 5_000L
private const val SEEK_ACK_TOLERANCE_MS = 1_000L
private const val SEEK_PREVIEW_TIMEOUT_MS = 2_000L
