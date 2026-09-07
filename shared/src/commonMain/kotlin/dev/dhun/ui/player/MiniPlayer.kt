package dev.dhun.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * The docked, in-app MiniPlayer (ADR-004): artwork wash, track-local progress,
 * readable metadata and independent transport targets. Click/keyboard activation
 * expands it (or opens error details); a density-aware swipe up always expands.
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
    val progress = playbackProgress(positionMs, durationMs)
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
    val errorState = state as? PlaybackState.Error
    // A dismissed/recovered error must not reopen on a later failure or track.
    var showErrorDialog by remember(track.id, errorState) { mutableStateOf(false) }
    var dragAccumPx by remember(track.id) { mutableFloatStateOf(0f) }
    val currentOnExpand by rememberUpdatedState(onExpand)
    val swipeThresholdPx = with(LocalDensity.current) { DhunSpacing.touchTarget.toPx() }
    val actionLabel = playbackActionLabel(state)

    GlassBottomBar(
        modifier = modifier
            .fillMaxWidth()
            .clip(DhunShapes.large)
            .background(
                Brush.horizontalGradient(
                    listOf(ambientTint, DhunColors.glassHighlight.copy(alpha = 0.15f), ambientTint.copy(alpha = 0.08f)),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // A new track starts at its own position, never a sweep backwards
            // from the previous track. Playback ticks are gently interpolated.
            key(track.id, durationMs) {
                val displayedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = DhunAnimations.fastTween(),
                    label = "miniProgress",
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(DhunSpacing.progressHeight)
                        .background(DhunColors.border.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (displayedProgress > 0f) {
                        Box(
                            modifier = Modifier.fillMaxWidth(displayedProgress)
                                .height(DhunSpacing.progressHeight)
                                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent))),
                        )
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Keep the title usable on narrow phones; Previous remains in
                // FullPlayer and is also shown in the dock whenever space permits.
                val showPrevious = maxWidth >= DhunSpacing.playerTransportMaxWidth
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DhunSpacing.miniPlayerHeight)
                        // Lift feedback while the expansion swipe is in flight:
                        // a bounded quarter-parallax, never enough to detach a
                        // row from its committed threshold. Offset-only, so the
                        // docked layout never re-measures.
                        .graphicsLayer {
                            val lift = (-dragAccumPx / 4f).coerceIn(0f, swipeThresholdPx / 4f)
                            translationY = -lift
                        }
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (errorState != null) "Show playback details" else "Open player",
                        ) {
                            if (errorState != null) showErrorDialog = true else currentOnExpand()
                        }
                        .pointerInput(track.id, swipeThresholdPx) {
                            try {
                                detectVerticalDragGestures(
                                    onDragStart = { dragAccumPx = 0f },
                                    onDragEnd = {
                                        if (shouldExpandMiniPlayer(dragAccumPx, swipeThresholdPx)) currentOnExpand()
                                        dragAccumPx = 0f
                                    },
                                    onDragCancel = { dragAccumPx = 0f },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragAccumPx += dragAmount
                                }
                            } finally {
                                dragAccumPx = 0f
                            }
                        }
                        .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    ArtworkImage(
                        imageUrl = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.touchTarget)
                            .shadow(DhunSpacing.xs, DhunShapes.medium, clip = false),
                        shape = DhunShapes.medium,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
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
                                when {
                                    errorState != null -> append(" • Error — open details")
                                    state is PlaybackState.Paused -> append(" • Paused")
                                    else -> playbackBusyLabel(state)?.let { append(" • $it") }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                errorState != null -> DhunColors.error
                                playbackBusyLabel(state) != null -> accent
                                else -> DhunColors.textTertiary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showPrevious) {
                        DhunIconButton(
                            onClick = viewModel::previous,
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
                    }
                    Box(
                        modifier = Modifier.size(DhunSpacing.touchTarget)
                            .clip(DhunShapes.full)
                            .background(accent.copy(alpha = 0.22f))
                            .semantics { contentDescription = actionLabel }
                            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = viewModel::togglePlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(
                            targetState = playbackActionIcon(state),
                            animationSpec = DhunAnimations.mediumTween(),
                            label = "miniPlayPauseMorph",
                        ) { icon ->
                            DhunIconView(
                                icon = icon,
                                contentDescription = null, // One label, even during the crossfade.
                                modifier = Modifier.size(DhunSpacing.iconSize),
                                tint = accent,
                            )
                        }
                    }
                    DhunIconButton(
                        onClick = viewModel::next,
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
    }

    if (showErrorDialog && errorState != null) {
        PlaybackErrorDialog(errorState, onDismiss = { showErrorDialog = false }, onRetry = viewModel::retry)
    }
}

internal fun shouldExpandMiniPlayer(dragPx: Float, thresholdPx: Float): Boolean =
    dragPx.isFinite() && thresholdPx.isFinite() && thresholdPx > 0f && dragPx <= -thresholdPx
