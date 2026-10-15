package dev.dhun.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.dhun.lyrics.RomanizationMode
import dev.dhun.presentation.player.LyricsRomanizationController

@Composable
internal fun LyricsRomanizationControls(controller: LyricsRomanizationController) {
    val enabled by controller.enabled.collectAsState()
    val mode by controller.mode.collectAsState()
    val error by controller.error.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Romanized line", style = MaterialTheme.typography.labelLarge)
            Switch(checked = enabled, onCheckedChange = controller::setEnabled,
                modifier = Modifier.semantics { contentDescription = "Show romanized lyrics below original" })
        }
        if (enabled) {
            Text("On-device transliteration, not translation. Han and Cyrillic need a language override; Japanese Kanji stays unchanged.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { menu = true }) { Text("Mode: ${mode.label}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                RomanizationMode.entries.forEach { value ->
                    DropdownMenuItem(text = { Text(value.label) }, onClick = { controller.setMode(value); menu = false })
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
