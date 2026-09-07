package dev.dhun.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunIconButton
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadProgress
import kotlin.math.roundToInt

/**
 * Small, reusable status pill for ADR-006 persistent downloads.
 *
 * It intentionally stays in shared UI and consumes only the public download
 * contract: persisted row state plus [DownloadManager.observeProgress] for the
 * live byte fraction. Engine details remain hidden behind the manager.
 */
@Composable
fun DownloadBadge(
    state: DownloadState,
    progress: DownloadProgress? = null,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val palette = downloadBadgePalette(state)
    val label = downloadStatusLabel(state, progress)
    Row(
        modifier = modifier
            .clip(DhunShapes.chip)
            .background(palette.container)
            .border(DhunSpacing.border, palette.border, DhunShapes.chip)
            .semantics { contentDescription = label }
            .padding(horizontal = if (showLabel) DhunSpacing.xsPlus else DhunSpacing.xs, vertical = DhunSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        DownloadProgressIndicator(
            state = state,
            progress = progress,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.iconSizeSm),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Icon/progress ring used by [DownloadBadge] and any future per-track surface.
 */
@Composable
fun DownloadProgressIndicator(
    state: DownloadState,
    progress: DownloadProgress? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = downloadStatusLabel(state, progress),
) {
    val palette = downloadBadgePalette(state)
    val fraction = when (state) {
        DownloadState.DOWNLOADING -> progress?.fraction
        else -> null
    }
    val indicatorModifier = if (contentDescription == null) {
        modifier.size(DhunSpacing.iconSizeSm)
    } else {
        modifier
            .size(DhunSpacing.iconSizeSm)
            .semantics { this.contentDescription = contentDescription }
    }

    if (fraction != null) {
        Canvas(modifier = indicatorModifier) {
            val strokeWidth = DhunSpacing.iconStroke.toPx()
            drawCircle(
                color = palette.content.copy(alpha = DownloadIndicatorTrackAlpha),
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = palette.content,
                startAngle = DownloadIndicatorStartAngle,
                sweepAngle = DownloadIndicatorFullSweep * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    } else {
        DhunIconView(
            icon = downloadStatusIcon(state),
            contentDescription = contentDescription,
            modifier = indicatorModifier,
            tint = palette.content,
        )
    }
}

/**
 * Observes a single track's persistent row and live progress, rendering nothing
 * until the track has an ADR-006 download state.
 */
@Composable
fun TrackDownloadBadge(
    trackId: String,
    downloadManager: DownloadManager?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    if (downloadManager == null) return

    val downloads by downloadManager.downloads.collectAsState()
    val progressFlow = remember(downloadManager, trackId) { downloadManager.observeProgress(trackId) }
    val progress by progressFlow.collectAsState(initial = null)
    val download = remember(downloads, trackId) { downloads.firstOrNull { it.trackId == trackId } }
    val state = remember(download?.downloadState, progress) { effectiveDownloadState(download, progress) } ?: return

    DownloadBadge(
        state = state,
        progress = progress,
        modifier = modifier,
        showLabel = showLabel,
    )
}

/** Common trailing actions: live download badge + existing overflow affordance. */
@Composable
fun TrackDownloadRowActions(
    track: Track,
    downloadManager: DownloadManager?,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadgeLabel: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        TrackDownloadBadge(
            trackId = track.id,
            downloadManager = downloadManager,
            showLabel = showBadgeLabel,
        )
        DhunIconButton(
            onClick = onOverflowClick,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            contentDescription = "More actions for ${track.title}",
        ) {
            DhunIconView(
                icon = DhunIcon.MoreVert,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSize),
                tint = DhunColors.textTertiary,
            )
        }
    }
}

private data class DownloadBadgePalette(
    val content: Color,
    val container: Color,
    val border: Color,
)

private fun effectiveDownloadState(download: DownloadedTrack?, progress: DownloadProgress?): DownloadState? = when {
    download?.downloadState == DownloadState.COMPLETED -> DownloadState.COMPLETED
    progress != null -> DownloadState.DOWNLOADING
    else -> download?.downloadState
}

private fun downloadStatusLabel(state: DownloadState, progress: DownloadProgress?): String = when (state) {
    DownloadState.QUEUED -> "QUEUED"
    DownloadState.DOWNLOADING -> progress.percentOrNull()?.let { "DOWNLOADING $it%" } ?: "DOWNLOADING"
    DownloadState.COMPLETED -> "COMPLETED"
    DownloadState.FAILED -> "FAILED"
    DownloadState.PAUSED -> "PAUSED"
}

private fun DownloadProgress?.percentOrNull(): Int? =
    this?.fraction?.let { (it * DownloadPercentScale).roundToInt().coerceIn(0, DownloadPercentScale.toInt()) }

private fun downloadStatusIcon(state: DownloadState): DhunIcon = when (state) {
    DownloadState.QUEUED -> DhunIcon.Pending
    DownloadState.DOWNLOADING -> DhunIcon.Download
    DownloadState.COMPLETED -> DhunIcon.Offline
    DownloadState.FAILED -> DhunIcon.Error
    DownloadState.PAUSED -> DhunIcon.Pause
}

private fun downloadBadgePalette(state: DownloadState): DownloadBadgePalette = when (state) {
    DownloadState.QUEUED -> DownloadBadgePalette(
        content = DhunColors.textSecondary,
        container = DhunColors.glassHighlight,
        border = DhunColors.glassEdge,
    )
    DownloadState.DOWNLOADING -> DownloadBadgePalette(
        content = DhunColors.accent,
        container = DhunColors.accentContainer.copy(alpha = DownloadBadgeContainerAlpha),
        border = DhunColors.accent.copy(alpha = DownloadBadgeBorderAlpha),
    )
    DownloadState.COMPLETED -> DownloadBadgePalette(
        content = DhunColors.success,
        container = DhunColors.success.copy(alpha = DownloadBadgeContainerAlpha),
        border = DhunColors.success.copy(alpha = DownloadBadgeBorderAlpha),
    )
    DownloadState.FAILED -> DownloadBadgePalette(
        content = DhunColors.error,
        container = DhunColors.errorContainer.copy(alpha = DownloadBadgeContainerAlpha),
        border = DhunColors.borderError,
    )
    DownloadState.PAUSED -> DownloadBadgePalette(
        content = DhunColors.warning,
        container = DhunColors.warning.copy(alpha = DownloadBadgeContainerAlpha),
        border = DhunColors.warning.copy(alpha = DownloadBadgeBorderAlpha),
    )
}

private const val DownloadPercentScale = 100f
private const val DownloadBadgeContainerAlpha = 0.28f
private const val DownloadBadgeBorderAlpha = 0.42f
private const val DownloadIndicatorTrackAlpha = 0.26f
private const val DownloadIndicatorStartAngle = -90f
private const val DownloadIndicatorFullSweep = 360f
