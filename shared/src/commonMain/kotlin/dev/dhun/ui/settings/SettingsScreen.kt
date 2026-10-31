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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.dhunContentSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.background.BackgroundBlurPolicy
import dev.dhun.design.DhunThemeMode
import dev.dhun.design.DhunVisualTuning
import dev.dhun.design.VisualPart
import dev.dhun.design.supportsRealtimeBlur
import dev.dhun.design.components.DhunAppearanceControls
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunSlider
import dev.dhun.design.components.DhunSwitch
import dev.dhun.design.components.DhunTextField
import dev.dhun.design.components.DhunTextButton
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
        // Pending/failure state for every destructive action here. The dialogs
        // below are result-driven: they stay open while the clear runs and show
        // a retryable message if it fails (the same contract the library's
        // clear-history and the playlist delete dialogs use).
        val maintenance by viewModel.maintenanceState.collectAsState()
        var confirmReset by remember { mutableStateOf(false) }
        var confirmLyricsClear by remember { mutableStateOf(false) }
        var confirmAudioClear by remember { mutableStateOf(false) }
        var confirmHistory by remember { mutableStateOf("") }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(remember(category) { ScrollState(0) })
                .padding(horizontal = DhunSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.md),
        ) {
            maintenance.screenErrorMessage?.let { message ->
                SettingsFailureNotice(
                    message = message,
                    onDismiss = viewModel::dismissMaintenanceError,
                )
            }
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
                    text = "Dynamic follows artwork; Static uses your selected accent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                    modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding),
                )
            }
            if (category == SettingsCategory.APPEARANCE) SettingsGroup("Backgrounds") {
                BackgroundSettingsSection(viewModel)
            }
            if (category == SettingsCategory.APPEARANCE) SettingsGroup("Backdrop detail") {
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
                SettingsSwitchRow("Resume on launch", "Restore your last queue and position",
                    viewModel.resumeOnLaunch.collectAsState().value, viewModel::setResumeOnLaunch)
                SettingsSwitchRow("Endless radio", "Add similar songs when the queue ends",
                    viewModel.autoplayRadio.collectAsState().value, viewModel::setAutoplayRadio)
                SettingsSwitchRow("Keep shuffle & repeat", "Keep modes for new queues and restarts",
                    viewModel.persistShuffleRepeat.collectAsState().value, viewModel::setPersistShuffleRepeat)
                SettingsSwitchRow("Pause on mute", "Pause at zero volume; resume if mute paused playback",
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
                    SettingsSwitchRow(label, "Use after the preferred provider",
                        id in viewModel.providerOrder.collectAsState().value.split(',')) {
                        viewModel.setProviderEnabled(id, it)
                    }
                }
                SettingsNavigationRow("Clear lyrics cache", "Refresh automatic lyrics; keep song choices", { confirmLyricsClear = true })
                SettingsSwitchRow("Prefer synced lyrics", "Use timestamped lyrics when available",
                    viewModel.lyricsPreferSynced.collectAsState().value, viewModel::setLyricsPreferSynced)
                SettingsSwitchRow("Auto-scroll synced lyrics", "Follow the current line until you scroll",
                    viewModel.lyricsAutoScroll.collectAsState().value, viewModel::setLyricsAutoScroll)
                OptionRow("Lyrics text size", listOf("small" to "Small", "default" to "Default", "large" to "Large"),
                    viewModel.lyricsTextSize.collectAsState().value, viewModel::setLyricsTextSize)
            }
            if (category == SettingsCategory.CONTENT) SettingsGroup("Content, Region & Discovery") {
                CountryRegionPicker(viewModel)
                SettingsSwitchRow("Explicit content", "Allow explicit tracks",
                    viewModel.explicitContent.collectAsState().value, viewModel::setExplicitContent)
            }
            if (category == SettingsCategory.PLAYBACK && equalizerSession != null) SettingsGroup("Equalizer & Sound Processing") {
                EqualizerSection(equalizerSession)
            }
            if (category == SettingsCategory.PRIVACY) SettingsGroup("Privacy & History") {
                SettingsSwitchRow("Record listening history", "Save new plays on this device",
                    viewModel.recordListeningHistory.collectAsState().value, viewModel::setRecordListeningHistory)
                Text("Not incognito: other data still works. Plays made while off aren’t added later.",
                    modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding),
                    style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                SettingsNavigationRow("Clear search history", "Remove recent searches", { confirmHistory = "search" })
                SettingsNavigationRow("Clear play history", "Remove listening history", { confirmHistory = "play" })
                SettingsNavigationRow("Privacy & Legal", "Provider requests and on-device data", onOpenAboutLegal)
            }
            if (category == SettingsCategory.STORAGE) SettingsGroup("Storage, Cache & Data") {
                CacheBudgetRow(viewModel)
                if (viewModel.canClearAudioCache) {
                    SettingsNavigationRow(
                        "Clear audio cache",
                        if (maintenance.isRunning(SettingsViewModel.KEY_CLEAR_AUDIO)) "Clearing streamed audio files…" else "Remove streamed audio files",
                        { confirmAudioClear = true },
                    )
                    viewModel.freedAudioBytes.collectAsState().value?.let { freed ->
                        Text(
                            if (freed > 0) "Freed ${freed / (1024 * 1024)} MB" else "Audio cache was already empty",
                            modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding),
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textSecondary,
                        )
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
        // A confirmation closes only after ITS action completed successfully.
        // (Previously every dialog closed the instant the button was tapped, so
        // a failed clear looked exactly like a successful one.)
        val completedKey by viewModel.lastCompletedKey.collectAsState()
        LaunchedEffect(completedKey) {
            when (completedKey) {
                null -> {}
                SettingsViewModel.KEY_CLEAR_AUDIO -> {
                    confirmAudioClear = false
                    viewModel.consumeCompletedAction()
                }
                SettingsViewModel.KEY_CLEAR_LYRICS -> {
                    confirmLyricsClear = false
                    viewModel.consumeCompletedAction()
                }
                SettingsViewModel.KEY_CLEAR_SEARCH, SettingsViewModel.KEY_CLEAR_PLAY -> {
                    confirmHistory = ""
                    viewModel.consumeCompletedAction()
                }
                SettingsViewModel.KEY_RESET -> {
                    confirmReset = false
                    equalizerSession?.restore(dev.dhun.player.equalizer.EqualizerState())
                    DhunAppearance.reset()
                    viewModel.consumeCompletedAction()
                }
            }
        }
        if (confirmAudioClear) SettingsConfirmDialog(
            onDismissRequest = { confirmAudioClear = false },
            title = { Text("Clear audio cache?") },
            text = { Text("Streamed tracks may need to load again.") },
            isPending = maintenance.isRunning(SettingsViewModel.KEY_CLEAR_AUDIO),
            errorMessage = maintenance.errorFor(SettingsViewModel.KEY_CLEAR_AUDIO),
            confirmLabel = "Clear",
            pendingLabel = "Clearing…",
            onConfirm = { viewModel.clearAudioCache() },
        )
        if (confirmHistory.isNotEmpty()) {
            val historyKey = if (confirmHistory == "search") {
                SettingsViewModel.KEY_CLEAR_SEARCH
            } else {
                SettingsViewModel.KEY_CLEAR_PLAY
            }
            SettingsConfirmDialog(
                onDismissRequest = { confirmHistory = "" },
                title = { Text("Clear ${confirmHistory} history?") },
                text = { Text("This cannot be undone.") },
                isPending = maintenance.isRunning(historyKey),
                errorMessage = maintenance.errorFor(historyKey),
                confirmLabel = "Clear",
                pendingLabel = "Clearing…",
                onConfirm = {
                    if (confirmHistory == "search") viewModel.clearSearchHistory() else viewModel.clearPlayHistory()
                },
            )
        }
        if (confirmLyricsClear) SettingsConfirmDialog(
            onDismissRequest = { confirmLyricsClear = false },
            title = { Text("Clear lyrics cache?") },
            text = { Text("Automatic lyrics will be fetched again. Saved per-song choices remain; use Use automatic in the lyrics match picker to reset one.") },
            isPending = maintenance.isRunning(SettingsViewModel.KEY_CLEAR_LYRICS),
            errorMessage = maintenance.errorFor(SettingsViewModel.KEY_CLEAR_LYRICS),
            confirmLabel = "Clear",
            pendingLabel = "Clearing…",
            onConfirm = { viewModel.clearLyricsCache() },
        )
        if (confirmReset) SettingsConfirmDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = { Text("All preferences will return to their defaults.") },
            isPending = maintenance.isRunning(SettingsViewModel.KEY_RESET),
            errorMessage = maintenance.errorFor(SettingsViewModel.KEY_RESET),
            confirmLabel = "Reset",
            pendingLabel = "Resetting…",
            // Live appearance/EQ state is reset in the LaunchedEffect above,
            // only after the persisted reset reports success.
            onConfirm = { viewModel.reset() },
        )
    }
}

/**
 * One confirmation surface for every destructive Settings action.
 *
 * It is result-driven, not tap-driven: while [isPending] the confirm button
 * shows [pendingLabel] and cannot be tapped again; when [errorMessage] is set
 * the message stays on screen and the button offers a retry. The caller closes
 * the dialog when its action actually succeeded.
 */
@Composable
private fun SettingsConfirmDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    isPending: Boolean = false,
    errorMessage: String? = null,
    confirmLabel: String = "Clear",
    pendingLabel: String = "Clearing…",
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    Dialog(onDismissRequest = { if (!isPending) onDismissRequest() }) {
        GlassCard(
            modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth),
            opaqueBase = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(DhunSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                title()
                text()
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismissRequest, enabled = !isPending) { Text(dismissLabel) }
                    Spacer(modifier = Modifier.weight(1f))
                    DhunButton(
                        onClick = onConfirm,
                        enabled = !isPending,
                        loading = isPending,
                    ) {
                        Text(
                            when {
                                isPending -> pendingLabel
                                errorMessage != null -> "Try again"
                                else -> confirmLabel
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A screen-level failure that no dialog owns (e.g. a preference write). */
@Composable
private fun SettingsFailureNotice(message: String, onDismiss: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), elevated = false) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DhunSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.error,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            DhunTextButton(onClick = onDismiss) { Text("Dismiss") }
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
                // DHUN's own input, not Material's 56dp outlined box: this
                // dialog is glass, and an OutlinedTextField dropped into it was
                // one of the places this screen still read as stock Material 3.
                DhunTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Search by name or code",
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
    val backgrounds by viewModel.backgrounds.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        if (supportsRealtimeBlur) {
            val blurOn = backgrounds.blurEnabled
            // A detail of blur, not a third global switch: it only changes *how
            // much* an enabled frost blurs, is suspended (not cleared) while
            // Background blur is off, and lives next to the strength it scales.
            // Keeping it out of the two-switch block is what lets the user read
            // that block as exactly two independent decisions.
            BackgroundSwitchRow(
                title = "Adaptive blur",
                subtitle = when {
                    !blurOn -> "Paused while Background blur is off"
                    backgrounds.adaptiveBlur -> "Adjust frost to each cover"
                    else -> "Use the same frost for every cover"
                },
                checked = backgrounds.adaptiveBlur,
                enabled = blurOn,
                onCheckedChange = viewModel::setBackgroundAdaptiveBlur,
            )
            // The master switch is the off switch, so the strength slider has no
            // zero: a slider that could reach it would let "Background blur: on"
            // and "nothing is frosted" both be true, which reads as a bug.
            val floor = BackgroundBlurPolicy.MIN_EFFECTIVE_STRENGTH
            val effective = BackgroundBlurPolicy.effectiveStrength(blur)
            Text(
                text = "Blur strength",
                style = MaterialTheme.typography.bodyLarge,
                color = if (blurOn) DhunColors.textPrimary else DhunColors.textDisabled,
            )
            Text(
                text = if (blurOn) {
                    "Set how strongly artwork is frosted"
                } else {
                    "Saved while Background blur is off"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (blurOn) DhunColors.textSecondary else DhunColors.textTertiary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DhunSlider(
                    value = effective.toFloat(),
                    onValueChange = { value ->
                        val percent = value.toInt().coerceIn(floor, 100)
                        viewModel.previewBackdropBlurPercent(percent)
                        DhunAppearance.setBackdropPrefs(blurPercent = percent)
                    },
                    onValueChangeFinished = { viewModel.saveBackdropBlurPercent() },
                    valueRange = floor.toFloat()..100f,
                    enabled = blurOn,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                Text(
                    text = "$effective%",
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
                text = "Dim the artwork background",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                DhunSlider(
                    value = brightness.toFloat(),
                    onValueChange = { value ->
                        val percent = value.toInt().coerceIn(0, 100)
                        viewModel.previewBackdropBrightnessPercent(percent)
                        DhunAppearance.setBackdropPrefs(brightnessPercent = percent)
                    },
                    onValueChangeFinished = { viewModel.saveBackdropBrightnessPercent() },
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
            "Preview live; values save on release. Include labels and percentages in feedback.",
            style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary,
        )
        DhunVisualTuning.adjustableParts.forEach { part ->
            val percent = values[part] ?: DhunVisualTuning.TEST_DEFAULT
            Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                Text(part.label, style = MaterialTheme.typography.bodyLarge, color = DhunColors.textPrimary)
                Text(part.detail, style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DhunSlider(
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
        DhunTextButton(onClick = viewModel::resetVisualTuning, enabled = loaded) {
            Text("Reset adjustable sliders to 15%")
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
        DhunSwitch(checked = checked, onCheckedChange = onCheckedChange)
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
            DhunSwitch(
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
        DhunSlider(
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
