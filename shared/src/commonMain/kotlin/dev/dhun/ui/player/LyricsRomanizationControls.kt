package dev.dhun.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunSwitch
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.resolveGlassArtwork
import dev.dhun.lyrics.RomanizationMode
import dev.dhun.presentation.player.LyricsRomanizationController
import dev.dhun.ui.components.MenuActionRow
import dev.dhun.ui.components.TrackMenuSurface

@Composable
internal fun LyricsRomanizationControls(controller: LyricsRomanizationController) {
    val enabled by controller.enabled.collectAsState()
    val mode by controller.mode.collectAsState()
    val error by controller.error.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Romanized line", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f))
            // DHUN switch: glass off-track, accent on-track (glass-system.md §2).
            DhunSwitch(checked = enabled, onCheckedChange = controller::setEnabled,
                modifier = Modifier.semantics { contentDescription = "Show romanized lyrics below original" })
        }
        if (enabled) {
            Box {
                DhunTextButton(onClick = { menu = true }, modifier = Modifier.semantics {
                    contentDescription = "Romanization mode: ${mode.label}"
                }) { Text("Mode: ${mode.label}") }
                // Same pattern as every other anchored menu in the app: the
                // Material popup is only an anchor, its container is transparent,
                // and the visible surface is the frosted DHUN menu
                // (docs/design/glass-system.md §2).
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = DhunShapes.large,
                    containerColor = Color.Transparent,
                    tonalElevation = DhunSpacing.zero,
                    shadowElevation = DhunSpacing.zero,
                ) {
                    TrackMenuSurface(
                        artworkUrl = resolveGlassArtwork(null),
                        modifier = Modifier.width(DhunSpacing.menuMinWidth),
                        shape = DhunShapes.large,
                    ) {
                        Text(
                            "On-device transliteration, not translation. Han/Cyrillic need an override; Japanese Kanji stays unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textTertiary,
                            modifier = Modifier.padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.xs),
                        )
                        RomanizationMode.entries.forEach { value ->
                            MenuActionRow(
                                label = value.label,
                                icon = if (value == mode) DhunIcon.Check else null,
                                modifier = Modifier.semantics { selected = value == mode },
                                onClick = { controller.setMode(value); menu = false },
                            )
                        }
                    }
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
