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
 * Owns the user-facing settings and a legacy close-to-tray value:
 *
 * - [themeId] / [accentId] — persisted ids; the screen maps them onto
 *   `DhunThemeMode` / `DhunAccent` and applies them live through
 *   `DhunAppearance`. This VM deliberately stays in plain strings so
 *   `presentation` keeps its clean-room separation from `design`.
 * - [cacheSizeMb] — the audio-segment cache budget, honoured by the platform
 *   players on the next process start (the running cache cannot be resized).
 * - [resumeOnLaunch] — honoured by `RestoreNowPlayingUseCase` on cold start.
 * - [closeToTray] — legacy storage compatibility only; no UI or host consumer.
 *   Desktop X always quits, regardless of this stored value.
 * - [backdropBlurPercent] / [backdropBrightnessPercent] — live now-playing
 *   backdrop. Blur is used where `Modifier.blur` is real (API 31+ / desktop);
 *   brightness dims the sharp thumbnail on older Android.
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
    private val clearLyricsCacheAction: (suspend () -> Unit)? = null,
    private val clearSearchHistoryAction: (suspend () -> Unit)? = null,
    private val clearPlayHistoryAction: (suspend () -> Unit)? = null,
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

    private val _backdropBlurPercent = MutableStateFlow(SettingsKeys.BACKDROP_BLUR_DEFAULT)
    val backdropBlurPercent: StateFlow<Int> = _backdropBlurPercent.asStateFlow()

    private val _backdropBrightnessPercent = MutableStateFlow(SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT)
    val backdropBrightnessPercent: StateFlow<Int> = _backdropBrightnessPercent.asStateFlow()

    private val _accentMode = MutableStateFlow(SettingsKeys.ACCENT_MODE_DEFAULT)
    val accentMode: StateFlow<String> = _accentMode.asStateFlow()
    private val _audioQuality = MutableStateFlow(SettingsKeys.AUDIO_QUALITY_DEFAULT)
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()
    private val _seekStepSeconds = MutableStateFlow(SettingsKeys.SEEK_STEP_SECONDS_DEFAULT)
    val seekStepSeconds: StateFlow<Int> = _seekStepSeconds.asStateFlow()
    private val _autoplayRadio = MutableStateFlow(SettingsKeys.AUTOPLAY_RADIO_ENABLED_DEFAULT)
    val autoplayRadio: StateFlow<Boolean> = _autoplayRadio.asStateFlow()
    private val _lyricsEnabled = MutableStateFlow(SettingsKeys.LYRICS_ENABLED_DEFAULT)
    val lyricsEnabled: StateFlow<Boolean> = _lyricsEnabled.asStateFlow()
    private val _lyricsPreferSynced = MutableStateFlow(SettingsKeys.LYRICS_PREFER_SYNCED_DEFAULT)
    val lyricsPreferSynced: StateFlow<Boolean> = _lyricsPreferSynced.asStateFlow()
    private val _lyricsAutoScroll = MutableStateFlow(SettingsKeys.LYRICS_AUTO_SCROLL_DEFAULT)
    val lyricsAutoScroll: StateFlow<Boolean> = _lyricsAutoScroll.asStateFlow()
    private val _lyricsTextSize = MutableStateFlow(SettingsKeys.LYRICS_TEXT_SIZE_DEFAULT)
    val lyricsTextSize: StateFlow<String> = _lyricsTextSize.asStateFlow()
    private val _providerOrder = MutableStateFlow(SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT)
    val providerOrder: StateFlow<String> = _providerOrder.asStateFlow()
    private val _preferredProvider = MutableStateFlow(SettingsKeys.LYRICS_PREFERRED_PROVIDER_DEFAULT)
    val preferredProvider: StateFlow<String> = _preferredProvider.asStateFlow()
    private val _countryCode = MutableStateFlow(SettingsKeys.COUNTRY_CODE_DEFAULT)
    val countryCode: StateFlow<String> = _countryCode.asStateFlow()
    private val _explicitContent = MutableStateFlow(SettingsKeys.EXPLICIT_CONTENT_DEFAULT)
    val explicitContent: StateFlow<Boolean> = _explicitContent.asStateFlow()
    private val _persistShuffleRepeat = MutableStateFlow(SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT)
    val persistShuffleRepeat: StateFlow<Boolean> = _persistShuffleRepeat.asStateFlow()

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
            _backdropBlurPercent.value = get.int(
                SettingsKeys.BACKDROP_BLUR, SettingsKeys.BACKDROP_BLUR_DEFAULT,
            ).coerceIn(0, 100)
            _backdropBrightnessPercent.value = get.int(
                SettingsKeys.BACKDROP_BRIGHTNESS, SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT,
            ).coerceIn(0, 100)
            _accentMode.value = get.string(SettingsKeys.ACCENT_MODE, SettingsKeys.ACCENT_MODE_DEFAULT)
            _audioQuality.value = get.string(SettingsKeys.AUDIO_QUALITY, SettingsKeys.AUDIO_QUALITY_DEFAULT)
            _seekStepSeconds.value = get.int(SettingsKeys.SEEK_STEP_SECONDS, SettingsKeys.SEEK_STEP_SECONDS_DEFAULT)
            _autoplayRadio.value = get.boolean(SettingsKeys.AUTOPLAY_RADIO_ENABLED, SettingsKeys.AUTOPLAY_RADIO_ENABLED_DEFAULT)
            _lyricsEnabled.value = get.boolean(SettingsKeys.LYRICS_ENABLED, SettingsKeys.LYRICS_ENABLED_DEFAULT)
            _lyricsPreferSynced.value = get.boolean(SettingsKeys.LYRICS_PREFER_SYNCED, SettingsKeys.LYRICS_PREFER_SYNCED_DEFAULT)
            _lyricsAutoScroll.value = get.boolean(SettingsKeys.LYRICS_AUTO_SCROLL, SettingsKeys.LYRICS_AUTO_SCROLL_DEFAULT)
            _lyricsTextSize.value = get.string(SettingsKeys.LYRICS_TEXT_SIZE, SettingsKeys.LYRICS_TEXT_SIZE_DEFAULT)
            _providerOrder.value = get.string(SettingsKeys.LYRICS_PROVIDER_ORDER, SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT)
            _preferredProvider.value = get.string(SettingsKeys.LYRICS_PREFERRED_PROVIDER, SettingsKeys.LYRICS_PREFERRED_PROVIDER_DEFAULT)
            _countryCode.value = get.string(SettingsKeys.COUNTRY_CODE, SettingsKeys.COUNTRY_CODE_DEFAULT)
            _explicitContent.value = get.boolean(SettingsKeys.EXPLICIT_CONTENT, SettingsKeys.EXPLICIT_CONTENT_DEFAULT)
            _persistShuffleRepeat.value = get.boolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT)
            _loaded.value = true
        }
    }

    /**
     * Only ids the data layer documents are persisted; anything else is
     * ignored so a corrupt write path can never poison the DB.
     */
    fun setThemeId(id: String) {
        if (id !in listOf("dark", "light", "system")) return
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

    fun setBackdropBlurPercent(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _backdropBlurPercent.value = value
        scope.launch { update.int(SettingsKeys.BACKDROP_BLUR, value) }
    }

    fun setBackdropBrightnessPercent(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _backdropBrightnessPercent.value = value
        scope.launch { update.int(SettingsKeys.BACKDROP_BRIGHTNESS, value) }
    }

    fun setAccentMode(id: String) {
        if (id !in listOf("artwork", "static")) return
        _accentMode.value = id
        scope.launch { update.string(SettingsKeys.ACCENT_MODE, id) }
    }
    fun setAudioQuality(id: String) {
        if (id !in listOf("low", "medium", "high")) return
        _audioQuality.value = id
        scope.launch { update.string(SettingsKeys.AUDIO_QUALITY, id) }
    }
    fun setSeekStepSeconds(seconds: Int) {
        if (seconds !in listOf(5, 10, 15, 30)) return
        _seekStepSeconds.value = seconds
        scope.launch { update.int(SettingsKeys.SEEK_STEP_SECONDS, seconds) }
    }
    fun setAutoplayRadio(enabled: Boolean) {
        _autoplayRadio.value = enabled
        scope.launch { update.boolean(SettingsKeys.AUTOPLAY_RADIO_ENABLED, enabled) }
    }
    fun setLyricsEnabled(enabled: Boolean) {
        _lyricsEnabled.value = enabled
        scope.launch { update.boolean(SettingsKeys.LYRICS_ENABLED, enabled) }
    }
    fun setLyricsPreferSynced(enabled: Boolean) {
        _lyricsPreferSynced.value = enabled
        scope.launch { update.boolean(SettingsKeys.LYRICS_PREFER_SYNCED, enabled) }
    }
    fun setLyricsAutoScroll(enabled: Boolean) {
        _lyricsAutoScroll.value = enabled
        scope.launch { update.boolean(SettingsKeys.LYRICS_AUTO_SCROLL, enabled) }
    }
    fun setLyricsTextSize(id: String) {
        if (id !in listOf("small", "default", "large")) return
        _lyricsTextSize.value = id
        scope.launch { update.string(SettingsKeys.LYRICS_TEXT_SIZE, id) }
    }
    fun setProviderEnabled(id: String, enabled: Boolean) {
        if (id !in listOf("lrclib", "ytm", "lrclib_search", "kugou")) return
        val current = _providerOrder.value.split(',').filter { it.isNotBlank() }
        val next = if (enabled) (current + id).distinct() else current.filterNot { it == id }
        if (next.isEmpty()) return
        _providerOrder.value = next.joinToString(",")
        scope.launch { update.string(SettingsKeys.LYRICS_PROVIDER_ORDER, _providerOrder.value) }
    }

    fun clearLyricsCache() {
        val action = clearLyricsCacheAction ?: return
        scope.launch { action() }
    }

    fun clearSearchHistory() {
        clearSearchHistoryAction?.let { action -> scope.launch { action() } }
    }
    fun clearPlayHistory() {
        clearPlayHistoryAction?.let { action -> scope.launch { action() } }
    }

    fun setPreferredProvider(id: String) {
        if (id !in listOf("auto", "lrclib", "ytm", "lrclib_search", "kugou")) return
        _preferredProvider.value = id
        scope.launch { update.string(SettingsKeys.LYRICS_PREFERRED_PROVIDER, id) }
    }
    fun setCountryCode(id: String) {
        if (id !in listOf("US", "NP", "IN", "GB", "CA", "AU", "DE", "FR", "JP", "KR", "BR")) return
        _countryCode.value = id
        scope.launch { update.string(SettingsKeys.COUNTRY_CODE, id) }
    }
    fun setExplicitContent(enabled: Boolean) {
        _explicitContent.value = enabled
        scope.launch { update.boolean(SettingsKeys.EXPLICIT_CONTENT, enabled) }
    }
    fun setPersistShuffleRepeat(enabled: Boolean) {
        _persistShuffleRepeat.value = enabled
        scope.launch { update.boolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, enabled) }
    }
    fun reset() {
        _themeId.value = SettingsKeys.THEME_DEFAULT
        _accentId.value = SettingsKeys.ACCENT_DEFAULT
        _accentMode.value = SettingsKeys.ACCENT_MODE_DEFAULT
        _backdropBlurPercent.value = SettingsKeys.BACKDROP_BLUR_DEFAULT
        _backdropBrightnessPercent.value = SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT
        _cacheSizeMb.value = SettingsKeys.CACHE_SIZE_MB_DEFAULT
        _resumeOnLaunch.value = SettingsKeys.RESUME_ON_LAUNCH_DEFAULT
        _closeToTray.value = SettingsKeys.CLOSE_TO_TRAY_DEFAULT
        _audioQuality.value = SettingsKeys.AUDIO_QUALITY_DEFAULT
        _seekStepSeconds.value = SettingsKeys.SEEK_STEP_SECONDS_DEFAULT
        _autoplayRadio.value = SettingsKeys.AUTOPLAY_RADIO_ENABLED_DEFAULT
        _lyricsEnabled.value = SettingsKeys.LYRICS_ENABLED_DEFAULT
        _lyricsPreferSynced.value = SettingsKeys.LYRICS_PREFER_SYNCED_DEFAULT
        _lyricsAutoScroll.value = SettingsKeys.LYRICS_AUTO_SCROLL_DEFAULT
        _lyricsTextSize.value = SettingsKeys.LYRICS_TEXT_SIZE_DEFAULT
        _preferredProvider.value = SettingsKeys.LYRICS_PREFERRED_PROVIDER_DEFAULT
        _providerOrder.value = SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT
        _countryCode.value = SettingsKeys.COUNTRY_CODE_DEFAULT
        _explicitContent.value = SettingsKeys.EXPLICIT_CONTENT_DEFAULT
        _persistShuffleRepeat.value = SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT
        scope.launch { SettingsKeys.all.forEach { update.remove(it) } }
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
