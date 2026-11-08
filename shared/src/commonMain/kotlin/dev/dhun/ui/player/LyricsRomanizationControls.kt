package dev.dhun.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunSwitch
import dev.dhun.design.components.DhunTextButton
import dev.dhun.lyrics.RomanizationMode
import dev.dhun.presentation.player.LyricsRomanizationController
import dev.dhun.ui.components.MenuActionRow
import dev.dhun.ui.components.TrackMenuSurface

@Composable
internal fun LyricsRomanizationControls(
    controller: LyricsRomanizationController,
    showModeWhenDisabled: Boolean = false,
    compact: Boolean = false,
) {
    val enabled by controller.enabled.collectAsState()
    val mode by controller.mode.collectAsState()
    val error by controller.error.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Romanized line",
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = DhunColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            DhunSwitch(
                checked = enabled,
                onCheckedChange = controller::setEnabled,
                modifier = Modifier.semantics { contentDescription = "Show romanized lyrics below original" },
            )
        }
        if (enabled || showModeWhenDisabled) {
            Box {
                val modeDescription = "Romanization mode: ${mode.label}"
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = DhunSpacing.compactTarget)
                            .clickable(role = Role.Button, onClick = { menu = true })
                            .semantics { contentDescription = modeDescription },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Mode: ${mode.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textSecondary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        DhunIconView(
                            icon = DhunIcon.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            tint = DhunColors.textSecondary,
                        )
                    }
                } else {
                    DhunTextButton(onClick = { menu = true }, modifier = Modifier.semantics {
                        contentDescription = modeDescription
                    }) { Text("Mode: ${mode.label}") }
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = DhunShapes.large,
                    containerColor = Color.Transparent,
                    tonalElevation = DhunSpacing.zero,
                    shadowElevation = DhunSpacing.zero,
                ) {
                    TrackMenuSurface(
                        artworkUrl = null,
                        modifier = Modifier.widthIn(
                            min = DhunSpacing.menuMinWidth,
                            max = DhunSpacing.dialogMaxWidth,
                        ),
                    ) {
                        Text(
                            "On-device transliteration, not translation. Han/Cyrillic need an override; Japanese Kanji stays unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textSecondary,
                            modifier = Modifier.padding(
                                horizontal = DhunSpacing.mdPlus,
                                vertical = DhunSpacing.sm,
                            ),
                        )
                        RomanizationMode.entries.forEach { value ->
                            MenuActionRow(
                                label = value.label,
                                selected = value == mode,
                                modifier = Modifier.semantics { selected = value == mode },
                                onClick = { controller.setMode(value); menu = false },
                            )
                        }
                    }
                }
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
        }
    }
}
