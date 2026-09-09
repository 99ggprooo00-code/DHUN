package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunThemeMode

/**
 * The theme toggle — candidate 28's user-facing surface.
 *
 * **Stateless by design** (the Compose idiom): it renders [mode]/[accent] and
 * reports intents, holding nothing itself. That is what lets it be dropped
 * straight into a real Settings screen later without carrying a second source
 * of truth; today it is mounted by
 * `dev.dhun.design.catalog.ComponentCatalogScreen`, wired to
 * `dev.dhun.design.DhunAppearance`.
 *
 * Two controls:
 * - **Mode** — Dark / Light. Dark is the shipped default.
 * - **Accent** — one chip per [DhunAccent], each showing the ramp it will
 *   actually apply *in the selected mode* (an accent is a dark ramp and a
 *   light ramp, not one colour), so the swatch never lies about the result.
 *
 * Selection is expressed with [DhunFilterChip], which already carries the
 * correct toggle semantics — the accent chips read as
 * "Violet, selected" to TalkBack without this file adding any.
 *
 * **Persistence is not this component's job.** It holds no state, so whoever
 * mounts it owns saving the choice. The store already exists
 * (`SettingsRepository` + `SettingsKeys.THEME`, whose documented values are
 * `"dark" | "light" | "system"` and match [DhunThemeMode.id] exactly); nothing
 * reads it yet, and an accent-hue key would have to be added in the data layer.
 * See [dev.dhun.design.DhunAppearance] for the full picture.
 */
@Composable
fun DhunAppearanceControls(
    mode: DhunThemeMode,
    accent: DhunAccent,
    onModeChange: (DhunThemeMode) -> Unit,
    onAccentChange: (DhunAccent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = DhunSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.labelMedium,
            color = DhunColors.textTertiary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DhunThemeMode.entries.forEach { entry ->
                DhunFilterChip(
                    selected = entry == mode,
                    onClick = { onModeChange(entry) },
                    label = { Text(entry.label) },
                )
            }
        }

        Text(
            text = "Accent",
            style = MaterialTheme.typography.labelMedium,
            color = DhunColors.textTertiary,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DhunAccent.entries.forEach { entry ->
                DhunFilterChip(
                    selected = entry == accent,
                    onClick = { onAccentChange(entry) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(DhunSpacing.lg)
                                .clip(CircleShape)
                                .background(rampFor(entry, mode).accent),
                        )
                    },
                    label = { Text(entry.label) },
                )
            }
        }
    }
}

/** The ramp [accent] applies under [mode] — what the swatch must preview. */
private fun rampFor(accent: DhunAccent, mode: DhunThemeMode) =
    if (mode == DhunThemeMode.LIGHT) accent.light else accent.dark
