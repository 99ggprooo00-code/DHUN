package dev.dhun.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.PlaybackState
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.DhunAnimations
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
 * MiniPlayer — polished M3 glass bar docked above the bottom nav (Android) or at
 * the window bottom (desktop).
 *
 * Features:
 * - Ambient gradient wash derived from artwork seed
 * - 2dp smoothed top progress line with glowing active indicator
 * - Rounded artwork thumbnail with subtle shadow
 * - Marquee title and live playback status label
 * - Refined circular transport buttons with animated play/pause morph
 * - Tap to expand FullPlayer (or tap to view error details on failure)
 * - Swipe-up gesture to open full-screen player
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

    val colors = remember(track.thumbnailUrl, track.id) {
        ArtworkColorExtractor.extractFromSeed(track.thumbnailUrl ?: track.id)
    }
    val ambientTint by animateColorAsState(
        targetValue = colors.backgroundTint.copy(alpha = 0.20f),
        animationSpec = DhunAnimations.slowTween(),
        label = "miniPlayerAmbient",
    )
    val accent by animateColorAsState(
        targetValue = colors.controlAccent,
        animationSpec = DhunAnimations.slowTween(),
        label = "miniPlayerAccent",
    )

    var dragAccumPx by remember { mutableFloatStateOf(0f) }
    var showErrorDialog by remember { mutableStateOf(false) }
    val errorState = state as? PlaybackState.Error
    val isPlaying = state is PlaybackState.Playing

    GlassBottomBar(
        modifier = modifier
            .fillMaxWidth()
            .clip(DhunShapes.large)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ambientTint,
                        DhunColors.glassHighlight.copy(alpha = 0.15f),
                        ambientTint.copy(alpha = 0.08f),
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Refined progress line -------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DhunSpacing.progressHeight)
                    .background(DhunColors.border.copy(alpha = 0.35f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(DhunSpacing.progressHeight)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(alpha = 0.7f), accent),
                                ),
                            ),
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
                    .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                ArtworkImage(
                    imageUrl = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(DhunSpacing.touchTarget)
                        .shadow(DhunSpacing.xs, DhunShapes.medium, clip = false),
                    shape = DhunShapes.medium,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = when (state) {
                            is PlaybackState.Error -> DhunColors.error
                            is PlaybackState.Recovering -> accent
                            is PlaybackState.Buffering, is PlaybackState.Resolving -> DhunColors.accent
                            else -> DhunColors.textTertiary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Previous button
                DhunIconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Previous track",
                ) {
                    DhunIconView(
                        icon = DhunIcon.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.textSecondary,
                    )
                }

                // Play / Pause circular button
                Box(
                    modifier = Modifier
                        .size(DhunSpacing.touchTarget)
                        .clip(DhunShapes.full)
                        .background(accent.copy(alpha = 0.22f))
                        .clickable { viewModel.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = DhunAnimations.mediumTween(),
                        label = "miniPlayPauseMorph",
                    ) { playing ->
                        DhunIconView(
                            icon = if (playing) DhunIcon.Pause else DhunIcon.Play,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(DhunSpacing.iconSize),
                            tint = accent,
                        )
                    }
                }

                // Next track button
                DhunIconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Next track",
                ) {
                    DhunIconView(
                        icon = DhunIcon.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.textPrimary,
                    )
                }
            }
        }
    }

    if (showErrorDialog && errorState != null) {
        PlaybackErrorDialog(errorState, onDismiss = { showErrorDialog = false }, onRetry = viewModel::retry)
    }
}

private const val SWIPE_UP_THRESHOLD_PX = 80f
