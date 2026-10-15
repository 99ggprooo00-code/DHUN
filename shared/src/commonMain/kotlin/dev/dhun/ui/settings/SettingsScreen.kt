package dev.dhun.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.dhunContentSurface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunThemeMode
import dev.dhun.design.DhunVisualTuning
import dev.dhun.design.VisualPart
import dev.dhun.design.supportsRealtimeBlur
import dev.dhun.design.components.DhunAppearanceControls
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunTonalButton
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.dhunMouseDragScroll
import dev.dhun.player.equalizer.EqualizerPresets
import dev.dhun.player.equalizer.EqualizerSession
import dev.dhun.player.equalizer.toUiModel
import dev.dhun.presentation.settings.SettingsCategory
import dev.dhun.presentation.player.LyricsRomanizationController
import dev.dhun.ui.player.LyricsRomanizationControls
import dev.dhun.presentation.settings.CountryRegions
import dev.dhun.presentation.settings.SettingsViewModel
import kotlin.math.round

/**
 * Settings page (S4) — the only screen that writes user preferences.
 *
 * Controls appear once, in frosted cards grouped by runtime concern.
 * Unsupported platform-only controls are hidden, not displayed as dead toggles.
 * The cache budget applies on the next launch; backdrop controls apply live.
 *
 * @param equalizerSession the platform EQ session, or null when the platform
 *   cannot apply EQ — the whole section hides rather than showing dead
 *   sliders. Desktop passes `player.equalizer`; Android passes the Koin
 *   session once the AudioEffect engine exists (S4 slice 3).
 * @param onOpenAboutLegal pushes the About & Legal index onto the detail stack.
 *   Passed by the shell, so this screen does not know about navigation and both
 *   the single-pane and two-pane layouts get the same behaviour for free.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    isDesktop: Boolean,
    equalizerSession: EqualizerSession? = null,
    modifier: Modifier = Modifier,
    onOpenAboutLegal: () -> Unit = {},
    showThemeCatalogue: Boolean = false,
    onOpenThemeCatalogue: () -> Unit = {},
    category: SettingsCategory? = null,
    onOpenCategory: (SettingsCategory) -> Unit = {},
    romanization: LyricsRomanizationController? = null,
) {
    val themeId by viewModel.themeId.collectAsState()
    val accentId by viewModel.accentId.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.sm, vertical = DhunSpacing.sm)
                .dhunContentSurface(reading = true)
                .padding(horizontal = DhunSpacing.md),
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
                text = category?.title ?: "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = DhunColors.textPrimary,
            )
        }

        val systemDark = isSystemInDarkTheme()
        var confirmReset by remember { mutableStateOf(false) }
        var confirmLyricsClear by remember { mutableStateOf(false) }
        var confirmAudioClear by remember { mutableStateOf(false) }
        var confirmHistory by remember { mutableStateOf("") }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(remember(category) { ScrollState(0) })
                .padding(horizontal = DhunSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.md),
        ) {
            if (category == null) SettingsHub(
                isDesktop = isDesktop,
                themeId = themeId,
                accentMode = viewModel.accentMode.collectAsState().value,
                onOpenCategory = onOpenCategory,
                showThemeCatalogue = showThemeCatalogue,
                onOpenThemeCatalogue = onOpenThemeCatalogue,
            )
            if (category == SettingsCategory.APPEARANCE) SettingsGroup("Appearance & Backdrop") {
                DhunAppearanceControls(
                    mode = DhunThemeMode.fromId(themeId) ?: DhunAppearance.mode,
                    accent = DhunAccent.fromId(accentId) ?: DhunAccent.default,
                    selectedThemeId = themeId,
                    onModeChange = { mode ->
                        viewModel.setThemeId(mode.id)
                        DhunAppearance.setThemePreference(mode.id, systemDark)
                    },
                    onSystemChange = {
                        viewModel.setThemeId("system")
                        DhunAppearance.setThemePreference("system", systemDark)
                    },
                    onAccentChange = { accent ->
                        viewModel.setAccentId(accent.id)
                        DhunAppearance.setAppearance(nextAccent = accent)
                    },
                )
                OptionRow("Accent color mode", listOf("artwork" to "Dynamic (from artwork)", "static" to "Static (selected accent)"),
                    viewModel.accentMode.collectAsState().value) {
                    viewModel.setAccentMode(it)
                    DhunAppearance.setAccentMode(it)
                }
                Text(
                    text = "Dynamic tints accent-colored controls throughout the app from the current track; static keeps the selected color.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                    modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding),
                )
                BackdropControls(viewModel)
            }
            if (category == SettingsCategory.APPEARANCE && showThemeCatalogue) SettingsGroup("Test build · UI tuning") {
                VisualTuningControls(viewModel)
            }
            if (category == SettingsCategory.PLAYBACK) SettingsGroup("Playback & Audio") {
                OptionRow("Streaming & download quality", listOf("low" to "Low (~48–64 kbps)",
                    "medium" to "Medium (~128 kbps)", "high" to "High (best available)"),
                    viewModel.audioQuality.collectAsState().value, viewModel::setAudioQuality)
                OptionRow("Keyboard seek step", listOf(5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s"),
                    viewModel.seekStepSeconds.collectAsState().value, viewModel::setSeekStepSeconds)
                SettingsSwitchRow("Resume on launch", "Restore the last queue and position when the app starts",
                    viewModel.resumeOnLaunch.collectAsState().value, viewModel::setResumeOnLaunch)
                SettingsSwitchRow("Endless radio", "Automatically queue similar songs near the end",
                    viewModel.autoplayRadio.collectAsState().value, viewModel::setAutoplayRadio)
                SettingsSwitchRow("Keep shuffle & repeat", "Keep modes when starting a new queue or restarting",
                    viewModel.persistShuffleRepeat.collectAsState().value, viewModel::setPersistShuffleRepeat)
                SettingsSwitchRow("Pause on mute", "Pause when the in-app volume reaches zero",
                    viewModel.pauseOnMute.collectAsState().value, viewModel::setPauseOnMute)
            }
            if (category == SettingsCategory.LYRICS) SettingsGroup("Lyrics & Providers") {
                if (romanization != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding)) {
                        LyricsRomanizationControls(romanization)
                    }
                }
                SettingsSwitchRow("Enable lyrics", "Fetch lyrics for the current song",
                    viewModel.lyricsEnabled.collectAsState().value, viewModel::setLyricsEnabled)
                OptionRow("Preferred provider", listOf("auto" to "Auto (synced first)", "lrclib" to "LRCLIB",
                    "ytm" to "YouTube Music", "lrclib_search" to "LRCLIB search", "kugou" to "KuGou"),
                    viewModel.preferredProvider.collectAsState().value, { viewModel.setPreferredProvider(it) })
                listOf("lrclib" to "LRCLIB", "ytm" to "YouTube Music",
                    "lrclib_search" to "LRCLIB search", "kugou" to "KuGou").forEach { (id, label) ->
                    SettingsSwitchRow(label, "Include as a fallback after the preferred provider",
                        id in viewModel.providerOrder.collectAsState().value.split(',')) {
                        viewModel.setProviderEnabled(id, it)
                    }
                }
                SettingsNavigationRow("Clear lyrics cache", "Refresh automatic lyrics; keep song choices", { confirmLyricsClear = true })
                SettingsSwitchRow("Prefer synced lyrics", "Use timestamped lyrics when available",
                    viewModel.lyricsPreferSynced.collectAsState().value, viewModel::setLyricsPreferSynced)
                SettingsSwitchRow("Auto-scroll synced lyrics", "Follow the current line until you scroll manually",
                    viewModel.lyricsAutoScroll.collectAsState().value, viewModel::setLyricsAutoScroll)
                OptionRow("Lyrics text size", listOf("small" to "Small", "default" to "Default", "large" to "Large"),
                    viewModel.lyricsTextSize.collectAsState().value, viewModel::setLyricsTextSize)
            }
            if (category == SettingsCategory.CONTENT) SettingsGroup("Content, Region & Discovery") {
                CountryRegionPicker(viewModel)
                SettingsSwitchRow("Explicit content", "Allow explicit tracks in search, home and radio",
                    viewModel.explicitContent.collectAsState().value, viewModel::setExplicitContent)
            }
            if (category == SettingsCategory.PLAYBACK && equalizerSession != null) SettingsGroup("Equalizer & Sound Processing") {
                EqualizerSection(equalizerSession)
            }
            if (category == SettingsCategory.PRIVACY) SettingsGroup("Privacy & History") {
                SettingsSwitchRow("Record listening history", "Save new plays locally; turning off keeps existing history and queue restore",
                    viewModel.recordListeningHistory.collectAsState().value, viewModel::setRecordListeningHistory)
                Text("This is not incognito: queue restore, caches and provider requests still work. Plays skipped while off are not added later.",
                    modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding),
                    style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                SettingsNavigationRow("Clear search history", "Remove recent searches", { confirmHistory = "search" })
                SettingsNavigationRow("Clear play history", "Remove listening history", { confirmHistory = "play" })
                SettingsNavigationRow("Privacy & Legal", "Review provider requests and on-device data", onOpenAboutLegal)
            }
            if (category == SettingsCategory.STORAGE) SettingsGroup("Storage, Cache & Data") {
                CacheBudgetRow(viewModel)
                if (viewModel.canClearAudioCache) {
                    SettingsNavigationRow("Clear audio cache", "Remove streamed audio files", { confirmAudioClear = true })
                    viewModel.freedAudioBytes.collectAsState().value?.let { freed ->
                        Text("Freed ${freed / (1024 * 1024)} MB", modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding))
                    }
                    if (viewModel.audioCacheError.collectAsState().value) {
                        Text("Could not clear audio in use. Try again after playback stops.",
                            modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding))
                    }
                }
            }
            if (category == SettingsCategory.DESKTOP && isDesktop) SettingsGroup("Desktop & Window Behavior") {
                SettingsSwitchRow("Close to tray", "Keep music playing when closing the window",
                    viewModel.closeToTray.collectAsState().value, viewModel::setCloseToTray)
                SettingsNavigationRow("Reset window geometry", "Center at 1200 × 780", viewModel::resetWindowGeometry)
            }
            if (category == SettingsCategory.ABOUT) SettingsGroup("About, Privacy & Legal") {
                SettingsNavigationRow("About & Legal", "Privacy, terms, licenses and support", onOpenAboutLegal)
                SettingsNavigationRow("Reset settings to defaults", "Restore all preferences", { confirmReset = true })
            }
            Spacer(modifier = Modifier.height(DhunSpacing.xl))
        }
        if (confirmAudioClear) SettingsConfirmDialog(
            onDismissRequest = { confirmAudioClear = false },
            title = { Text("Clear audio cache?") },
            text = { Text("Streamed tracks may need to load again.") },
            confirmButton = { TextButton(onClick = {
                viewModel.clearAudioCache()
                confirmAudioClear = false
            }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmAudioClear = false }) { Text("Cancel") } },
        )
        if (confirmHistory.isNotEmpty()) SettingsConfirmDialog(
            onDismissRequest = { confirmHistory = "" },
            title = { Text("Clear ${confirmHistory} history?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = {
                if (confirmHistory == "search") viewModel.clearSearchHistory() else viewModel.clearPlayHistory()
                confirmHistory = ""
            }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmHistory = "" }) { Text("Cancel") } },
        )
        if (confirmLyricsClear) SettingsConfirmDialog(
            onDismissRequest = { confirmLyricsClear = false },
            title = { Text("Clear lyrics cache?") },
            text = { Text("Automatic lyrics will be fetched again. Saved per-song choices remain; use Use automatic in the lyrics match picker to reset one.") },
            confirmButton = { TextButton(onClick = {
                viewModel.clearLyricsCache()
                confirmLyricsClear = false
            }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmLyricsClear = false }) { Text("Cancel") } },
        )
        if (confirmReset) SettingsConfirmDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("All preferences will return to their defaults.") },
            confirmButton = { TextButton(onClick = {
                viewModel.reset()
                equalizerSession?.restore(dev.dhun.player.equalizer.EqualizerState())
                DhunAppearance.reset()
                confirmReset = false
            }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsConfirmDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        GlassCard(modifier = Modifier.fillMaxWidth(), opaqueBase = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(DhunSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
                title()
                text()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), elevated = false) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.md)) {
            SectionHeader(title = title)
            content()
        }
    }
}

@Composable
private fun <T> OptionRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = DhunColors.textPrimary)
        val scroll = rememberScrollState()
        Row(modifier = Modifier.horizontalScroll(scroll).dhunMouseDragScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
            options.forEach { (value, label) ->
                DhunFilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun CountryRegionPicker(viewModel: SettingsViewModel) {
    val countryCode by viewModel.countryCode.collectAsState()
    val selected = CountryRegions.find(countryCode) ?: CountryRegions.default
    var showPicker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { CountryRegions.search(query) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        Text("Content country/region", style = MaterialTheme.typography.bodyLarge, color = DhunColors.textPrimary)
        DhunTonalButton(
            onClick = {
                query = ""
                showPicker = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "${selected.name} (${selected.code})",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DhunIconView(
                icon = DhunIcon.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = DhunColors.textPrimary,
            )
        }
        Text(
            text = "Filters the music catalogue where that region is available.",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )
    }

    if (showPicker) Dialog(onDismissRequest = { showPicker = false }) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).heightIn(max = 560.dp),
            opaqueBase = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(DhunSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                Text("Choose a country or region", style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search by name or code") },
                    singleLine = true,
                )
                if (matches.isEmpty()) {
                    Text(
                        text = "No matching country or region.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textSecondary,
                        modifier = Modifier.padding(vertical = DhunSpacing.md),
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(matches, key = { it.code }) { region ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(role = Role.Button) {
                                        viewModel.setCountryCode(region.code)
                                        showPicker = false
                                    }
                                    .padding(horizontal = DhunSpacing.sm, vertical = DhunSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = region.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (region.code == selected.code) DhunColors.accent else DhunColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.width(DhunSpacing.md))
                                Text(
                                    text = region.code,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DhunColors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackdropControls(viewModel: SettingsViewModel) {
    val blur by viewModel.backdropBlurPercent.collectAsState()
    val brightness by viewModel.backdropBrightnessPercent.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        if (supportsRealtimeBlur) {
            Text(
                text = "Background blur",
                style = MaterialTheme.typography.bodyLarge,
                color = DhunColors.textPrimary,
            )
            Text(
                text = "How heavily the now-playing cover is frosted behind the app.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = blur.toFloat(),
                    onValueChange = { value ->
                        val percent = value.toInt().coerceIn(0, 100)
                        viewModel.setBackdropBlurPercent(percent)
                        DhunAppearance.setBackdropPrefs(blurPercent = percent)
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                Text(
                    text = "$blur%",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                )
            }
        }
        Text(
                text = "Background brightness",
                style = MaterialTheme.typography.bodyLarge,
                color = DhunColors.textPrimary,
            )
            Text(
                text = if (supportsRealtimeBlur) "Dim the blurred artwork independently of blur." else "Hardware blur is unavailable on this Android version; dim the sharp cover instead.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = brightness.toFloat(),
                    onValueChange = { value ->
                        val percent = value.toInt().coerceIn(0, 100)
                        viewModel.setBackdropBrightnessPercent(percent)
                        DhunAppearance.setBackdropPrefs(brightnessPercent = percent)
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                Text(
                    text = "$brightness%",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                )
            }
    }
}

/** Test-channel controls, named so a tester can report exact values per component. */
@Composable
private fun VisualTuningControls(viewModel: SettingsViewModel) {
    val values by viewModel.visualTuning.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        Text(
            "Live preview · 50% is the current design. Changes are saved on release. " +
                "Use the labels and percentages when reporting what looks too dark or too white.",
            style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary,
        )
        VisualPart.entries.forEach { part ->
            val percent = values[part] ?: DhunVisualTuning.DEFAULT
            Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                Text(part.label, style = MaterialTheme.typography.bodyLarge, color = DhunColors.textPrimary)
                Text(part.detail, style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = percent.toFloat(),
                        onValueChange = { value ->
                            val next = value.toInt().coerceIn(0, 100)
                            viewModel.previewVisualTuning(part, next)
                            DhunVisualTuning.set(part, next)
                        },
                        onValueChangeFinished = { viewModel.saveVisualTuning(part) },
                        valueRange = 0f..100f,
                        enabled = loaded,
                        modifier = Modifier.weight(1f).semantics { contentDescription = part.label },
                    )
                    Spacer(Modifier.width(DhunSpacing.sm))
                    Text("$percent%", style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                }
            }
        }
        TextButton(onClick = viewModel::resetVisualTuning, enabled = loaded) { Text("Reset UI tuning to 50%") }
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

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
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
