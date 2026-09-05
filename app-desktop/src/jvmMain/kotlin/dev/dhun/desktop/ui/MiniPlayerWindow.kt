package dev.dhun.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.PlaybackState
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypography
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunIconButton
import dev.dhun.desktop.smct.Smct
import dev.dhun.presentation.player.PlayerViewModel
import kotlin.math.roundToInt

/**
 * Phase 12 — mini-player window content (hosted at 320×88, always on top).
 *
 * The artwork+title region drags the whole window (Windows — JNA
 * SetWindowPos by window title; elsewhere the decorated title bar still
 * provides native drag) and a release WITHOUT drag opens the main window
 * (spec: "click opens main window"). Transport buttons keep their own
 * click targets outside the drag region.
 */
@Composable
fun MiniPlayerContent(
    viewModel: PlayerViewModel,
    onOpenMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val state by viewModel.state.collectAsState()
    val track = currentTrack
    val progress =
        if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(DhunShapes.glass)
            .background(DhunColors.surfaceCard)
            .border(DhunSpacing.divider, DhunColors.border, DhunShapes.glass),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = DhunSpacing.md, top = DhunSpacing.sm, bottom = DhunSpacing.md, end = DhunSpacing.zero),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = DhunSpacing.sm)
                    .dragWindow(onReleasedWithoutDrag = onOpenMain),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                ArtworkImage(
                    imageUrl = track?.thumbnailUrl,
                    contentDescription = track?.title,
                    modifier = Modifier.size(DhunSpacing.artworkThumb),
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = track?.title ?: "Nothing playing",
                        style = DhunTypography.titleSmall,
                        color = DhunColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.artistName ?: "DHUN",
                        style = DhunTypography.labelSmall,
                        color = DhunColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DhunIconButton(
                onClick = { viewModel.togglePlay() },
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = if (state is PlaybackState.Playing) "Pause" else "Play",
            ) {
                DhunIconView(
                    icon = if (state is PlaybackState.Playing) DhunIcon.Pause else DhunIcon.Play,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textPrimary,
                )
            }
            DhunIconButton(
                onClick = { viewModel.next() },
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "Next track",
            ) {
                DhunIconView(
                    icon = DhunIcon.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.textSecondary,
                )
            }
        }
        // 1dp accent progress line along the bottom edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(DhunSpacing.iconStroke)
                .background(DhunColors.surfaceHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(DhunSpacing.iconStroke)
                    .background(DhunColors.accent),
            )
        }
    }
}

/**
 * Drags this window (title "DHUN mini-player") by pointer movement over
 * this region, via JNA SetWindowPos (Windows). A release with no movement
 * is a "click" → [onReleasedWithoutDrag]. Off-Windows the modifier is inert
 * (the decorated title bar still drags natively).
 */
private fun Modifier.dragWindow(onReleasedWithoutDrag: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        if (!Smct.isWindows) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown()
                down.consume()
                var moved = false
                var last = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    val pos = change.position
                    val dx = (pos.x - last.x).roundToInt()
                    val dy = (pos.y - last.y).roundToInt()
                    last = pos
                    if (dx != 0 || dy != 0) {
                        moved = true
                        Smct.moveWindow(MINI_WINDOW_TITLE, dx, dy)
                    }
                }
                if (!moved) onReleasedWithoutDrag()
            }
        }
    }

private const val MINI_WINDOW_TITLE = "DHUN mini-player"
