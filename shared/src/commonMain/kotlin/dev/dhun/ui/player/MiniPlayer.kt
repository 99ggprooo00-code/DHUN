package dev.dhun.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.PlaybackState
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.GlassBottomBar
import dev.dhun.presentation.player.PlayerViewModel

/**
 * MiniPlayer — frosted M3 glass bar docked above the bottom nav (Android) or at
 * the window bottom (desktop). Translucent multi-stop fill + hairline edge
 * (glass-morphism atmosphere — not Liquid Glass / continuous reblur).
 *
 * - 1dp accent progress line pinned to the top edge
 * - artwork (crossfades via Coil), marquee title, artist line with live
 *   Resolving/Buffering status
 * - play/pause + next transport
 * - tap OR swipe-up expands the FullPlayer
 *
 * Hidden entirely when nothing is loaded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    val track = currentTrack ?: return
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var dragAccumPx by remember { mutableFloatStateOf(0f) }
    // "Error — tap to see": an error tap opens the diagnosis dialog (with
    // Retry) instead of just expanding to a FullPlayer that showed nothing.
    var showErrorDialog by remember { mutableStateOf(false) }
    val errorState = state as? PlaybackState.Error

    GlassBottomBar(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1dp accent progress line -------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DhunSpacing.divider)
                    .background(DhunColors.glassEdge.copy(alpha = 0.35f)),
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(DhunSpacing.divider)
                            .background(DhunColors.accent),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DhunSpacing.miniPlayerHeight)
                    .pointerInput(errorState) {
                        detectTapGestures(
                            onTap = {
                                if (errorState != null) showErrorDialog = true else onExpand()
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragAccumPx < -SWIPE_UP_THRESHOLD_PX) onExpand()
                                dragAccumPx = 0f
                            },
                            onDragCancel = { dragAccumPx = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumPx += dragAmount
                        }
                    }
                    .padding(horizontal = DhunSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                ArtworkImage(
                    imageUrl = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    shape = DhunShapes.medium,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = buildString {
                            append(track.artistName)
                            when (state) {
                                is PlaybackState.Buffering -> append(" • Buffering…")
                                is PlaybackState.Resolving -> append(" • Resolving…")
                                is PlaybackState.Recovering -> append(" • Reconnecting…")
                                is PlaybackState.Error -> append(" • Error — tap to see")
                                else -> {}
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (state) {
                            is PlaybackState.Error -> DhunColors.error
                            is PlaybackState.Recovering -> DhunColors.accent
                            else -> DhunColors.textTertiary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.textSecondary,
                    )
                }
            }
        }
    }

    if (showErrorDialog && errorState != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            // Stock AlertDialog paints an opaque grey slab that ignored the
            // design system entirely (visible in the device screenshots).
            // Match the frosted M3 chrome the rest of the app uses.
            containerColor = DhunColors.glassStrong,
            titleContentColor = DhunColors.textPrimary,
            textContentColor = DhunColors.textSecondary,
            iconContentColor = DhunColors.accent,
            tonalElevation = DhunSpacing.zero,
            shape = DhunShapes.extraLarge,
            title = {
                Text(
                    text = "Playback error",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                )
            },
            text = {
                Column {
                    Text(
                        text = errorState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textSecondary,
                    )
                    // Diagnostics chain, when the resolver supplied one. The
                    // FullPlayer already surfaces this; the mini-player dialog
                    // is where most users actually hit the error first.
                    val detail = errorState.detail
                    if (!detail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(DhunSpacing.sm))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = DhunColors.textTertiary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showErrorDialog = false
                        viewModel.retry()
                    },
                ) {
                    Text("Retry", color = DhunColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("Close", color = DhunColors.textSecondary)
                }
            },
        )
    }
}

private const val SWIPE_UP_THRESHOLD_PX = 80f
