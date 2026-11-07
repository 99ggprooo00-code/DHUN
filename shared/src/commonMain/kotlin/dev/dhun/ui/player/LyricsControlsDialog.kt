package dev.dhun.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunSlider
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.DhunTonalButton
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

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            val availableSurfaceHeight =
                (maxHeight - DhunSpacing.xxl * 2).coerceAtLeast(DhunSpacing.zero)
            val availableBodyHeight =
                (availableSurfaceHeight - DhunSpacing.smPlus * 2).coerceAtLeast(DhunSpacing.zero)
            val bodyMaxHeight = minOf(DhunSpacing.dialogBodyMaxHeight, availableBodyHeight)
            TrackMenuSurface(
                artworkUrl = track.thumbnailUrl,
                modifier = Modifier.fillMaxWidth()
                    .widthIn(max = DhunSpacing.lyricsControlsDialogMaxWidth)
                    .heightIn(max = availableSurfaceHeight),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DhunSpacing.mdPlus, vertical = DhunSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                        Text(
                            "Lyrics controls",
                            style = MaterialTheme.typography.titleLarge,
                            color = DhunColors.textPrimary,
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = DhunColors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.semantics {
                                contentDescription = "Current song title: ${track.title}"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "From ${track.artistName.ifBlank { "Unknown artist" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = DhunColors.border)
                    LyricsRomanizationControls(
                        viewModel.lyricsRomanization,
                        showModeWhenDisabled = true,
                    )
                    LyricsProviderPicker(
                        selectedProvider = preferredProvider,
                        onSelect = onPreferredProviderChange,
                        accent = DhunColors.accent,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                    ) {
                        LyricsControlActionButton(
                            label = "Edit",
                            icon = DhunIcon.Edit,
                            enabled = onEdit != null,
                            onClick = { onEdit?.invoke() },
                        )
                        LyricsControlActionButton(
                            label = "Refetch",
                            icon = DhunIcon.Refresh,
                            enabled = viewModel.currentTrack.value?.id == track.id,
                            onClick = {
                                if (viewModel.currentTrack.value?.id == track.id) viewModel.refreshLyrics()
                            },
                        )
                        LyricsControlActionButton(
                            label = "Search",
                            icon = DhunIcon.Search,
                            enabled = onSearch != null,
                            onClick = { onSearch?.invoke() },
                        )
                    }
                    if (lyricsState is LyricsUiState.Synced) {
                        HorizontalDivider(color = DhunColors.border)
                        LyricsOffsetControls(viewModel = viewModel, track = track, timing = timing)
                    }
                    if (onFindMatches != null) {
                        DhunTonalButton(
                            onClick = onFindMatches,
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "Find lyrics matches"
                            },
                        ) {
                            DhunIconView(
                                icon = DhunIcon.Search,
                                contentDescription = null,
                                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            )
                            Spacer(modifier = Modifier.width(DhunSpacing.xs))
                            Text("Find lyrics matches", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun RowScope.LyricsControlActionButton(
    label: String,
    icon: DhunIcon,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) DhunColors.onAccentContainer else DhunColors.textDisabled
    val actionDescription = when (label) {
        "Edit" -> "Edit lyrics using local TTML"
        "Refetch" -> "Refetch current lyrics"
        else -> "Search lyrics matches"
    }
    DhunTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).semantics {
            contentDescription = actionDescription
        },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = DhunSpacing.xs, vertical = DhunSpacing.sm),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
        ) {
            DhunIconView(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = contentColor,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
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
                    color = DhunColors.accent,
                    modifier = Modifier.padding(horizontal = DhunSpacing.sm, vertical = DhunSpacing.xs),
                )
            }
            Spacer(modifier = Modifier.width(DhunSpacing.xs))
            DhunTextButton(
                enabled = timing.offsetMs != 0 && !dragging,
                onClick = {
                    if (viewModel.currentTrack.value?.id == track.id) {
                        viewModel.resetLyricsTiming(track.id)
                        draftOffsetMs = 0
                    }
                },
            ) {
                Text("Reset")
            }
        }
        DhunSlider(
            value = draftOffsetMs.toFloat(),
            onValueChange = { value ->
                dragging = true
                draftOffsetMs = LyricsOffsetSliderPolicy.offsetFromSlider(value)
            },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Lyrics offset"
            },
            valueRange = -LyricsTiming.MAX_OFFSET_MS.toFloat()..LyricsTiming.MAX_OFFSET_MS.toFloat(),
            steps = LyricsOffsetSliderPolicy.SLIDER_STEPS,
            onValueChangeFinished = {
                if (viewModel.currentTrack.value?.id == track.id) {
                    val deltaMs = draftOffsetMs - timing.offsetMs
                    if (deltaMs != 0) viewModel.adjustLyricsTiming(track.id, deltaMs)
                }
                dragging = false
            },
        )
        LyricsOffsetMarkers()
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
