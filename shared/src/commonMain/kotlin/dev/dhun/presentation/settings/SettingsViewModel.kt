package dev.dhun.presentation.settings

import dev.dhun.data.SettingsKeys
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.player.AudioCacheBudget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings screen model (S4).
 *
 * Owns the five user-facing settings that are actually wired to behaviour:
 *
 * - [themeId] / [accentId] — persisted ids; the screen maps them onto
 *   `DhunThemeMode` / `DhunAccent` and applies them live through
 *   `DhunAppearance`. This VM deliberately stays in plain strings so
 *   `presentation` keeps its clean-room separation from `design`.
 * - [cacheSizeMb] — the audio-segment cache budget, honoured by the platform
 *   players on the next process start (the running cache cannot be resized).
 * - [resumeOnLaunch] — honoured by `RestoreNowPlayingUseCase` on cold start.
 * - [closeToTray] — read by the desktop host at startup (hidden on Android).
 *
 * Settings whose keys exist but have no behaviour behind them yet
 * (`AUDIO_QUALITY`, `COUNTRY_CODE`, `LYRICS_ENABLED`, `ACCENT_MODE`,
 * `EXPLICIT_CONTENT` — see `.ai/KNOWN_LIMITATIONS.md`) are deliberately *not*
 * exposed: a toggle that changes nothing is worse than no toggle.
 */
class SettingsViewModel(
    private val get: GetSettingUseCase,
    private val update: UpdateSettingUseCase,
    private val scope: CoroutineScope,
) {
    private val _themeId = MutableStateFlow(SettingsKeys.THEME_DEFAULT)
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    private val _accentId = MutableStateFlow(SettingsKeys.ACCENT_DEFAULT)
    val accentId: StateFlow<String> = _accentId.asStateFlow()

    private val _cacheSizeMb = MutableStateFlow(SettingsKeys.CACHE_SIZE_MB_DEFAULT)
    val cacheSizeMb: StateFlow<Int> = _cacheSizeMb.asStateFlow()

    private val _resumeOnLaunch = MutableStateFlow(SettingsKeys.RESUME_ON_LAUNCH_DEFAULT)
    val resumeOnLaunch: StateFlow<Boolean> = _resumeOnLaunch.asStateFlow()

    private val _closeToTray = MutableStateFlow(SettingsKeys.CLOSE_TO_TRAY_DEFAULT)
    val closeToTray: StateFlow<Boolean> = _closeToTray.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** True once the persisted values have been read (flows hold defaults until then). */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            _themeId.value = get.string(SettingsKeys.THEME, SettingsKeys.THEME_DEFAULT)
            _accentId.value = get.string(SettingsKeys.ACCENT, SettingsKeys.ACCENT_DEFAULT)
            _cacheSizeMb.value = AudioCacheBudget.clampMb(
                get.int(SettingsKeys.CACHE_SIZE_MB, SettingsKeys.CACHE_SIZE_MB_DEFAULT),
            )
            _resumeOnLaunch.value = get.boolean(
                SettingsKeys.RESUME_ON_LAUNCH, SettingsKeys.RESUME_ON_LAUNCH_DEFAULT,
            )
            _closeToTray.value = get.boolean(
                SettingsKeys.CLOSE_TO_TRAY, SettingsKeys.CLOSE_TO_TRAY_DEFAULT,
            )
            _loaded.value = true
        }
    }

    /**
     * Only ids the data layer documents are persisted; anything else is
     * ignored so a corrupt write path can never poison the DB.
     */
    fun setThemeId(id: String) {
        if (id != "dark" && id != "light") return
        _themeId.value = id
        scope.launch { update.string(SettingsKeys.THEME, id) }
    }

    fun setAccentId(id: String) {
        if (id.isBlank()) return
        _accentId.value = id
        scope.launch { update.string(SettingsKeys.ACCENT, id) }
    }

    /** No-op unless [mb] is one of [CACHE_OPTIONS_MB]. */
    fun setCacheSizeMb(mb: Int) {
        if (mb !in CACHE_OPTIONS_MB) return
        _cacheSizeMb.value = mb
        scope.launch { update.int(SettingsKeys.CACHE_SIZE_MB, mb) }
    }

    fun setResumeOnLaunch(enabled: Boolean) {
        _resumeOnLaunch.value = enabled
        scope.launch { update.boolean(SettingsKeys.RESUME_ON_LAUNCH, enabled) }
    }

    fun setCloseToTray(enabled: Boolean) {
        _closeToTray.value = enabled
        scope.launch { update.boolean(SettingsKeys.CLOSE_TO_TRAY, enabled) }
    }

    companion object {
        /**
         * The budget ladder offered by the UI. `0` is the documented
         * "unlimited" sentinel (see [AudioCacheBudget]); every entry survives
         * [AudioCacheBudget.clampMb] unchanged.
         */
        val CACHE_OPTIONS_MB: List<Int> = listOf(256, 512, 1024, 2048, 4096, 0)

        fun cacheOptionLabel(mb: Int): String = when (mb) {
            0 -> "Unlimited"
            in 1..1023 -> "${mb} MB"
            else -> "${mb / 1024} GB"
        }
    }
}
