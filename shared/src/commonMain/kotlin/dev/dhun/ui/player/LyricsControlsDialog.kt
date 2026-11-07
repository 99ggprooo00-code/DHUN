package dev.dhun.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.dhun.core.Track
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunSlider
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.FrostedChipSurface
import dev.dhun.lyrics.LyricsTiming
import dev.dhun.presentation.player.LyricsUiState
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.ui.components.TrackMenuSurface

/** The CC-mode ⋮: lyrics controls only, never the normal track actions. */
@Composable
internal fun LyricsControlsDialog(
    viewModel: PlayerViewModel,
    track: Track,
    preferredProvider: String,
    onPreferredProviderChange: (String) -> Unit,
    onEdit: (() -> Unit)?,
    onSearch: (() -> Unit)?,
    onFindMatches: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val lyricsState by viewModel.lyricsState.collectAsState()
    val savedTiming by viewModel.lyricsTiming.collectAsState()
    val timing = savedTiming.takeIf { it.trackId == track.id } ?: LyricsTiming(track.id)
    val artist = track.artistName.ifBlank { "Unknown artist" }

    // This is a compact, artwork-backed control card, not a platform-width
    // confirmation dialog. The explicit inset keeps it balanced on phones while
    // the width ceiling stops it growing into a sheet on desktop.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.smPlus),
            contentAlignment = Alignment.Center,
        ) {
            val availableSurfaceHeight =
                (maxHeight - DhunSpacing.xxl * 2).coerceAtLeast(DhunSpacing.zero)
            val availableBodyHeight =
                (availableSurfaceHeight - DhunSpacing.smPlus * 2).coerceAtLeast(DhunSpacing.zero)
            val bodyMaxHeight = minOf(DhunSpacing.dialogBodyMaxHeight, availableBodyHeight)
            TrackMenuSurface(
                artworkUrl = track.thumbnailUrl,
                modifier = Modifier
                    .widthIn(max = minOf(DhunSpacing.lyricsControlsDialogMaxWidth, maxWidth))
                    .fillMaxWidth()
                    .heightIn(max = availableSurfaceHeight),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DhunSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                        Text(
                            "Lyrics controls",
                            style = MaterialTheme.typography.titleMedium,
                            color = DhunColors.textPrimary,
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = DhunColors.textPrimary, fontWeight = FontWeight.Medium)) {
                                    append(track.title)
                                }
                                withStyle(SpanStyle(color = DhunColors.textSecondary)) {
                                    append(" (From $artist)")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics {
                                contentDescription = "Current song title: ${track.title}. From $artist"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = DhunColors.border)
                    LyricsRomanizationControls(
                        controller = viewModel.lyricsRomanization,
                        showModeWhenDisabled = true,
                        compact = true,
                    )
                    LyricsProviderPicker(
                        selectedProvider = preferredProvider,
                        onSelect = onPreferredProviderChange,
                        accent = DhunColors.accent,
                    )
                    LyricsControlActionRail(
                        onEdit = onEdit,
                        onRefetch = {
                            if (viewModel.currentTrack.value?.id == track.id) viewModel.refreshLyrics()
                        },
                        onSearch = onSearch,
                        refetchEnabled = viewModel.currentTrack.value?.id == track.id,
                    )
                    if (lyricsState is LyricsUiState.Synced) {
                        HorizontalDivider(color = DhunColors.border)
                        LyricsOffsetControls(viewModel = viewModel, track = track, timing = timing)
                    }
                    if (onFindMatches != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = DhunSpacing.compactTarget)
                                .clickable(role = Role.Button, onClick = onFindMatches)
                                .semantics { contentDescription = "Find lyrics matches" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Find lyrics matches for this song",
                                style = MaterialTheme.typography.bodySmall,
                                color = DhunColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        DhunTextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsControlActionRail(
    onEdit: (() -> Unit)?,
    onRefetch: () -> Unit,
    onSearch: (() -> Unit)?,
    refetchEnabled: Boolean,
) {
    FrostedChipSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = DhunShapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LyricsControlAction(
                label = "Edit",
                icon = DhunIcon.Edit,
                description = "Edit lyrics using local TTML",
                enabled = onEdit != null,
                onClick = { onEdit?.invoke() },
            )
            LyricsControlAction(
                label = "Refetch",
                icon = DhunIcon.Refresh,
                description = "Refetch current lyrics",
                enabled = refetchEnabled,
                onClick = onRefetch,
            )
            LyricsControlAction(
                label = "Search",
                icon = DhunIcon.Search,
                description = "Search lyrics matches",
                enabled = onSearch != null,
                onClick = { onSearch?.invoke() },
            )
        }
    }
}

@Composable
private fun RowScope.LyricsControlAction(
    label: String,
    icon: DhunIcon,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val actionColor = if (enabled) DhunColors.textPrimary else DhunColors.textDisabled
    val iconColor = if (enabled) DhunColors.accent else DhunColors.textDisabled
    Column(
        modifier = Modifier.weight(1f)
            .heightIn(min = DhunSpacing.compactTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
        ) {
            DhunIconView(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = iconColor,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = actionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricsOffsetControls(
    viewModel: PlayerViewModel,
    track: Track,
    timing: LyricsTiming,
) {
    var dragging by remember(track.id) { mutableStateOf(false) }
    var draftOffsetMs by remember(track.id) { mutableIntStateOf(timing.offsetMs) }
    LaunchedEffect(track.id, timing.offsetMs) {
        if (!dragging) draftOffsetMs = timing.offsetMs
    }

    Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Lyrics offset",
                style = MaterialTheme.typography.labelLarge,
                color = DhunColors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            FrostedChipSurface(shape = DhunShapes.chip) {
                Text(
                    text = LyricsOffsetSliderPolicy.valueLabel(draftOffsetMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = DhunColors.textPrimary,
                    modifier = Modifier.padding(horizontal = DhunSpacing.sm, vertical = DhunSpacing.xs),
                )
            }
            if (timing.offsetMs != 0 && !dragging) {
                DhunIconButton(
                    onClick = {
                        if (viewModel.currentTrack.value?.id == track.id) {
                            viewModel.resetLyricsTiming(track.id)
                            draftOffsetMs = 0
                        }
                    },
                    enabled = !dragging,
                    contentDescription = "Reset lyrics offset",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.textSecondary,
                    )
                }
            }
        }
        LyricsOffsetSlider(
            value = draftOffsetMs.toFloat(),
            onValueChange = { value ->
                dragging = true
                draftOffsetMs = LyricsOffsetSliderPolicy.offsetFromSlider(value)
            },
            onValueChangeFinished = {
                if (viewModel.currentTrack.value?.id == track.id) {
                    val deltaMs = draftOffsetMs - timing.offsetMs
                    if (deltaMs != 0) viewModel.adjustLyricsTiming(track.id, deltaMs)
                }
                dragging = false
            },
            valueRange = -LyricsTiming.MAX_OFFSET_MS.toFloat()..LyricsTiming.MAX_OFFSET_MS.toFloat(),
            steps = LyricsOffsetSliderPolicy.SLIDER_STEPS,
        )
        LyricsOffsetMarkers()
    }
}

/** Keep Material's drag/semantics behavior, but draw the round DHUN thumb and quiet scale ticks. */
@Composable
private fun LyricsOffsetSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val thumbFill = if (DhunAppearance.tokens.isLight) DhunColors.surfaceElevated else DhunColors.textPrimary
    val thumbEdge = DhunColors.borderStrong
    val tickColor = DhunColors.textTertiary.copy(alpha = 0.62f)

    Box(modifier = Modifier.fillMaxWidth()) {
        DhunSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Lyrics offset"
            },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            thumbColor = Color.Transparent,
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val trackInset = (DhunSpacing.xs / 2).toPx()
            val trackWidth = (size.width - trackInset * 2).coerceAtLeast(0f)
            val centerY = size.height / 2f
            val tickStartY = centerY + DhunSpacing.smPlus.toPx()
            val tickEndY = tickStartY + DhunSpacing.xs.toPx()
            for (tick in 0..10) {
                val x = trackInset + trackWidth * tick / 10f
                drawLine(
                    color = tickColor,
                    start = Offset(x, tickStartY),
                    end = Offset(x, tickEndY),
                    strokeWidth = DhunSpacing.divider.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val thumbCenterX = trackInset + trackWidth * fraction
            drawCircle(
                color = thumbEdge,
                radius = DhunSpacing.smPlus.toPx(),
                center = Offset(thumbCenterX, centerY),
            )
            drawCircle(
                color = thumbFill,
                radius = DhunSpacing.sm.toPx(),
                center = Offset(thumbCenterX, centerY),
            )
        }
    }
}

/** Labels sit at their true positions on the preserved -10s..+10s range. */
@Composable
private fun LyricsOffsetMarkers() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val halfWidth = maxWidth / 2
        Box(
            modifier = Modifier.width(halfWidth).align(Alignment.CenterStart),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "-5s",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textSecondary,
                modifier = Modifier.semantics { contentDescription = "Offset marker: minus five seconds" },
            )
        }
        Box(
            modifier = Modifier.width(halfWidth).align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "0",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textSecondary,
                modifier = Modifier.semantics { contentDescription = "Offset marker: zero seconds" },
            )
        }
        Box(
            modifier = Modifier.width(halfWidth).align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+5s",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textSecondary,
                modifier = Modifier.semantics { contentDescription = "Offset marker: plus five seconds" },
            )
        }
    }
}
