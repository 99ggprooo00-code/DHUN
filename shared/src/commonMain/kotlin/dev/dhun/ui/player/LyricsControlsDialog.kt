package dev.dhun.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.DhunDivider
import dev.dhun.lyrics.LyricsTiming
import dev.dhun.presentation.player.LyricsUiState
import dev.dhun.presentation.player.PlayerViewModel

/** The CC-mode ⋮: lyrics controls only, never the normal track actions. */
@Composable
internal fun LyricsControlsDialog(
    viewModel: PlayerViewModel,
    track: Track,
    preferredProvider: String,
    onPreferredProviderChange: (String) -> Unit,
    onFindMatches: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val lyricsState by viewModel.lyricsState.collectAsState()
    val savedTiming by viewModel.lyricsTiming.collectAsState()
    val timing = savedTiming.takeIf { it.trackId == track.id } ?: LyricsTiming(track.id)

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = DhunSpacing.dialogMaxWidth),
            opaqueBase = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = DhunSpacing.playerCompactControlsHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(DhunSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                Text("Lyrics controls", style = MaterialTheme.typography.titleLarge, color = DhunColors.textPrimary)
                Text(track.title, style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                DhunDivider()
                LyricsRomanizationControls(viewModel.lyricsRomanization)
                LyricsProviderPicker(
                    selectedProvider = preferredProvider,
                    onSelect = onPreferredProviderChange,
                    accent = DhunColors.accent,
                )
                if (lyricsState is LyricsUiState.Synced) {
                    DhunDivider()
                    Text("Timing offset: ${timing.offsetMs} ms",
                        style = MaterialTheme.typography.labelLarge, color = DhunColors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                        DhunTextButton(onClick = {
                            if (viewModel.currentTrack.value?.id == track.id) viewModel.adjustLyricsTiming(track.id, -100)
                        }) { Text("Earlier") }
                        DhunTextButton(onClick = {
                            if (viewModel.currentTrack.value?.id == track.id) viewModel.adjustLyricsTiming(track.id, 100)
                        }) { Text("Later") }
                        DhunTextButton(onClick = {
                            if (viewModel.currentTrack.value?.id == track.id) viewModel.resetLyricsTiming(track.id)
                        }) { Text("Reset") }
                    }
                }
                if (onFindMatches != null) {
                    DhunDivider()
                    DhunTextButton(onClick = onFindMatches) { Text("Find lyrics matches for this song") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
