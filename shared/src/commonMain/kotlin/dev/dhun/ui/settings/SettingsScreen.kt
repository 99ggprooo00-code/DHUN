package dev.dhun.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunThemeMode
import dev.dhun.design.components.DhunAppearanceControls
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.dhunMouseDragScroll
import dev.dhun.player.equalizer.EqualizerPresets
import dev.dhun.player.equalizer.EqualizerSession
import dev.dhun.player.equalizer.toUiModel
import dev.dhun.presentation.settings.SettingsViewModel
import kotlin.math.round

/**
 * Settings page (S4) — the only screen that writes user preferences.
 *
 * Every control here is wired to behaviour (see [SettingsViewModel]); keys
 * with no behaviour behind them are deliberately absent. Appearance applies
 * live; the cache budget and close-to-tray apply on the next launch, which
 * the section notes say outright.
 *
 * @param equalizerSession the platform EQ session, or null when the platform
 *   cannot apply EQ — the whole section hides rather than showing dead
 *   sliders. Desktop passes `player.equalizer`; Android passes the Koin
 *   session once the AudioEffect engine exists (S4 slice 3).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    isDesktop: Boolean,
    equalizerSession: EqualizerSession? = null,
    modifier: Modifier = Modifier,
) {
    val themeId by viewModel.themeId.collectAsState()
    val accentId by viewModel.accentId.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DhunIconButton(
                onClick = onBack,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "Back",
            ) {
                DhunIconView(
                    icon = DhunIcon.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = DhunColors.textPrimary,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(title = "Appearance")
            DhunAppearanceControls(
                mode = DhunThemeMode.fromId(themeId) ?: DhunThemeMode.default,
                accent = DhunAccent.fromId(accentId) ?: DhunAccent.default,
                onModeChange = { mode ->
                    viewModel.setThemeId(mode.id)
                    DhunAppearance.setAppearance(nextMode = mode)
                },
                onAccentChange = { accent ->
                    viewModel.setAccentId(accent.id)
                    DhunAppearance.setAppearance(nextAccent = accent)
                },
            )

            SectionHeader(title = "Playback & storage")
            CacheBudgetRow(viewModel = viewModel)
            SettingsSwitchRow(
                title = "Resume on launch",
                subtitle = "Restore the last queue when the app starts",
                checked = viewModel.resumeOnLaunch.collectAsState().value,
                onCheckedChange = viewModel::setResumeOnLaunch,
            )

            if (isDesktop) {
                SectionHeader(title = "Desktop")
                SettingsSwitchRow(
                    title = "Close to tray",
                    subtitle = "Closing the window hides the app instead of quitting. Applies on next launch.",
                    checked = viewModel.closeToTray.collectAsState().value,
                    onCheckedChange = viewModel::setCloseToTray,
                )
            }

            if (equalizerSession != null) {
                SectionHeader(title = "Equalizer")
                EqualizerSection(session = equalizerSession)
            }

            Spacer(modifier = Modifier.height(DhunSpacing.xl))
        }
    }
}

@Composable
private fun CacheBudgetRow(viewModel: SettingsViewModel) {
    val selected by viewModel.cacheSizeMb.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        Text(
            text = "Audio cache size",
            style = MaterialTheme.typography.labelMedium,
            color = DhunColors.textTertiary,
        )
        val rowState = rememberScrollState()
        Row(
            modifier = Modifier.horizontalScroll(rowState).dhunMouseDragScroll(rowState),
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsViewModel.CACHE_OPTIONS_MB.forEach { mb ->
                DhunFilterChip(
                    selected = mb == selected,
                    onClick = { viewModel.setCacheSizeMb(mb) },
                    label = { Text(SettingsViewModel.cacheOptionLabel(mb)) },
                )
            }
        }
        Text(
            text = "Space reserved for streamed audio. Applies on next launch.",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = DhunColors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.width(DhunSpacing.md))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The EQ surface candidate 22 deferred ("a later screen (outside this
 * slice)"). Binds [EqualizerSession] through `toUiModel()` — presets,
 * preamp, and one slider per band — pushing every change straight back into
 * the session, which forwards it to the platform engine.
 */
@Composable
private fun EqualizerSection(session: EqualizerSession) {
    val live by session.state.collectAsState()
    val model = live.toUiModel()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Enabled",
                style = MaterialTheme.typography.bodyLarge,
                color = DhunColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = model.enabled,
                onCheckedChange = session::setEnabled,
            )
        }

        Text(
            text = "Preset",
            style = MaterialTheme.typography.labelMedium,
            color = DhunColors.textTertiary,
        )
        val presetRowState = rememberScrollState()
        Row(
            modifier = Modifier.horizontalScroll(presetRowState).dhunMouseDragScroll(presetRowState),
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.presets.forEach { preset ->
                // The Custom row reflects slider edits; tapping it is a no-op
                // by session contract (selectPreset ignores CUSTOM_ID).
                DhunFilterChip(
                    selected = preset.selected,
                    enabled = model.enabled && preset.id != EqualizerPresets.CUSTOM_ID,
                    onClick = { session.selectPreset(preset.id) },
                    label = { Text(preset.displayName) },
                )
            }
        }

        EqualizerSliderRow(
            label = "Preamp",
            valueLabel = formatDb(model.preampDb),
            value = model.preampDb,
            min = model.minGainDb,
            max = model.maxGainDb,
            enabled = model.enabled,
            onChange = session::setPreamp,
        )
        model.bands.forEach { band ->
            EqualizerSliderRow(
                label = band.label,
                valueLabel = formatDb(band.gainDb),
                value = band.gainDb,
                min = model.minGainDb,
                max = model.maxGainDb,
                enabled = model.enabled,
                onChange = { session.setBandGain(band.index, it) },
            )
        }
    }
}

@Composable
private fun EqualizerSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    min: Float,
    max: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            modifier = Modifier.width(DhunSpacing.touchTarget),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(DhunSpacing.sm))
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )
    }
}

private fun formatDb(db: Float): String {
    val rounded = round(db * 10) / 10f
    return if (rounded > 0) "+$rounded dB" else "$rounded dB"
}
