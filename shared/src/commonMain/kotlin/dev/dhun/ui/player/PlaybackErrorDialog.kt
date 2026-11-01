package dev.dhun.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dhun.core.PlaybackState
import dev.dhun.core.diagnosticText
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassDialog

/** Same complete, selectable diagnostics from either player; never a six-line ellipsis. */
@Composable
internal fun PlaybackErrorDialog(error: PlaybackState.Error, onDismiss: () -> Unit, onRetry: () -> Unit) {
    GlassDialog(onDismissRequest = onDismiss) {
        Text(
            "Playback details",
            style = MaterialTheme.typography.titleMedium,
            color = DhunColors.textPrimary,
        )
        Spacer(modifier = Modifier.height(DhunSpacing.md))
        SelectionContainer {
            Column(
                modifier = Modifier.heightIn(max = DhunSpacing.playerDiagnosticsMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                Text(error.message, style = MaterialTheme.typography.bodyMedium, color = DhunColors.textSecondary)
                error.track?.let {
                    Text(
                        "${it.title}\nTrack ID: ${it.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.textSecondary,
                    )
                }
                error.detail?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        diagnosticText(it, 4_000),
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.textSecondary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(DhunSpacing.lg))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DhunTextButton(onClick = onDismiss) { Text("Close", color = DhunColors.textSecondary) }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            DhunTextButton(onClick = { onDismiss(); onRetry() }) { Text("Retry", color = DhunColors.accent) }
        }
    }
}
