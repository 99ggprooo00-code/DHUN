package dev.dhun.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import dev.dhun.core.PlaybackState
import dev.dhun.core.diagnosticText
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/** Same complete, selectable diagnostics from either player; never a six-line ellipsis. */
@Composable
internal fun PlaybackErrorDialog(error: PlaybackState.Error, onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DhunColors.glassStrong.compositeOver(DhunColors.surface),
        titleContentColor = DhunColors.textPrimary,
        textContentColor = DhunColors.textSecondary,
        tonalElevation = DhunSpacing.zero,
        shape = DhunShapes.extraLarge,
        title = { Text("Playback details", style = MaterialTheme.typography.titleMedium) },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier.heightIn(max = DhunSpacing.playerDiagnosticsMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    Text(error.message, style = MaterialTheme.typography.bodyMedium)
                    error.track?.let { Text("${it.title}\nTrack ID: ${it.id}", style = MaterialTheme.typography.bodySmall) }
                    error.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(diagnosticText(it, 4_000), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onRetry() }) { Text("Retry", color = DhunColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = DhunColors.textSecondary) }
        },
    )
}
