package dev.dhun.presentation.settings

import dev.dhun.data.SettingsKeys
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.player.AudioCacheBudget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persisted preferences for the grouped Settings screen. Writes remain in
 * [UpdateSettingUseCase]; UI-facing flows update optimistically while runtime
 * consumers observe the same SQLDelight keys on their own dispatchers.
 */
class SettingsViewModel(
    private val get: GetSettingUseCase,
    private val update: UpdateSettingUseCase,
    private val scope: CoroutineScope,
    private val clearLyricsCacheAction: (suspend () -> Unit)? = null,
    private val clearSearchHistoryAction: (suspend () -> Unit)? = null,
    private val clearPlayHistoryAction: (suspend () -> Unit)? = null,
    private val clearAudioCacheAction: (suspend () -> Long)? = null,
    private val onCountryCodeChange: (String) -> Unit = {},
) {
    // Settings controls write asynchronously, but rapid changes (especially
    // Reset right after a slider move) must reach the DB in tap order. Start
    // undispatched so each write claims the mutex before returning to the UI.
    private val writeMutex = Mutex()
    private fun enqueueWrite(action: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeMutex.withLock { action() }
        }
    }

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
    private val _pauseOnMute = MutableStateFlow(SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME_DEFAULT)
    val pauseOnMute: StateFlow<Boolean> = _pauseOnMute.asStateFlow()
    private val _persistShuffleRepeat = MutableStateFlow(SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT)
    val persistShuffleRepeat: StateFlow<Boolean> = _persistShuffleRepeat.asStateFlow()

    private val _recordListeningHistory = MutableStateFlow(SettingsKeys.RECORD_LISTENING_HISTORY_DEFAULT)
    val recordListeningHistory: StateFlow<Boolean> = _recordListeningHistory.asStateFlow()

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
                .uppercase()
                .takeIf(CountryRegions::isSupportedCode)
                ?: SettingsKeys.COUNTRY_CODE_DEFAULT
            _explicitContent.value = get.boolean(SettingsKeys.EXPLICIT_CONTENT, SettingsKeys.EXPLICIT_CONTENT_DEFAULT)
            _pauseOnMute.value = get.boolean(SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME, SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME_DEFAULT)
            _persistShuffleRepeat.value = get.boolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT)
            _recordListeningHistory.value = get.boolean(SettingsKeys.RECORD_LISTENING_HISTORY, SettingsKeys.RECORD_LISTENING_HISTORY_DEFAULT)
            _loaded.value = true
        }
    }

    /**
     * Only ids the data layer documents are persisted; anything else is
     * ignored so a corrupt write path can never poison the DB.
     */
    fun setThemeId(id: String) {
        if (id !in listOf("dark", "light", "black", "system")) return
        _themeId.value = id
        enqueueWrite { update.string(SettingsKeys.THEME, id) }
    }

    fun setAccentId(id: String) {
        if (id.isBlank()) return
        _accentId.value = id
        enqueueWrite { update.string(SettingsKeys.ACCENT, id) }
    }

    /** No-op unless [mb] is one of [CACHE_OPTIONS_MB]. */
    fun setCacheSizeMb(mb: Int) {
        if (mb !in CACHE_OPTIONS_MB) return
        _cacheSizeMb.value = mb
        enqueueWrite { update.int(SettingsKeys.CACHE_SIZE_MB, mb) }
    }

    fun setResumeOnLaunch(enabled: Boolean) {
        _resumeOnLaunch.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.RESUME_ON_LAUNCH, enabled) }
    }

    fun resetWindowGeometry() {
        enqueueWrite { update.remove(SettingsKeys.WINDOW_GEOMETRY) }
    }

    fun setCloseToTray(enabled: Boolean) {
        _closeToTray.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.CLOSE_TO_TRAY, enabled) }
    }

    fun setBackdropBlurPercent(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _backdropBlurPercent.value = value
        enqueueWrite { update.int(SettingsKeys.BACKDROP_BLUR, value) }
    }

    fun setBackdropBrightnessPercent(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _backdropBrightnessPercent.value = value
        enqueueWrite { update.int(SettingsKeys.BACKDROP_BRIGHTNESS, value) }
    }

    fun setAccentMode(id: String) {
        if (id !in listOf("artwork", "static")) return
        _accentMode.value = id
        enqueueWrite { update.string(SettingsKeys.ACCENT_MODE, id) }
    }
    fun setAudioQuality(id: String) {
        if (id !in listOf("low", "medium", "high")) return
        _audioQuality.value = id
        enqueueWrite { update.string(SettingsKeys.AUDIO_QUALITY, id) }
    }
    fun setSeekStepSeconds(seconds: Int) {
        if (seconds !in listOf(5, 10, 15, 30)) return
        _seekStepSeconds.value = seconds
        enqueueWrite { update.int(SettingsKeys.SEEK_STEP_SECONDS, seconds) }
    }
    fun setAutoplayRadio(enabled: Boolean) {
        _autoplayRadio.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.AUTOPLAY_RADIO_ENABLED, enabled) }
    }
    fun setLyricsEnabled(enabled: Boolean) {
        _lyricsEnabled.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.LYRICS_ENABLED, enabled) }
    }
    fun setLyricsPreferSynced(enabled: Boolean) {
        _lyricsPreferSynced.value = enabled
        enqueueWrite {
            update.boolean(SettingsKeys.LYRICS_PREFER_SYNCED, enabled)
            clearLyricsCacheAction?.invoke()
        }
    }
    fun setLyricsAutoScroll(enabled: Boolean) {
        _lyricsAutoScroll.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.LYRICS_AUTO_SCROLL, enabled) }
    }
    fun setLyricsTextSize(id: String) {
        if (id !in listOf("small", "default", "large")) return
        _lyricsTextSize.value = id
        enqueueWrite { update.string(SettingsKeys.LYRICS_TEXT_SIZE, id) }
    }
    fun setProviderEnabled(id: String, enabled: Boolean) {
        if (id !in listOf("lrclib", "ytm", "lrclib_search", "kugou")) return
        val current = _providerOrder.value.split(',').filter { it.isNotBlank() }
        val next = if (enabled) (current + id).distinct() else current.filterNot { it == id }
        if (next.isEmpty()) return
        _providerOrder.value = next.joinToString(",")
        val order = _providerOrder.value
        enqueueWrite {
            update.string(SettingsKeys.LYRICS_PROVIDER_ORDER, order)
            clearLyricsCacheAction?.invoke()
        }
    }

    fun clearLyricsCache() {
        val action = clearLyricsCacheAction ?: return
        scope.launch { action() }
    }

    val canClearAudioCache: Boolean get() = clearAudioCacheAction != null
    private val _freedAudioBytes = MutableStateFlow<Long?>(null)
    val freedAudioBytes: StateFlow<Long?> = _freedAudioBytes.asStateFlow()
    private val _audioCacheError = MutableStateFlow(false)
    val audioCacheError: StateFlow<Boolean> = _audioCacheError.asStateFlow()
    fun clearAudioCache() {
        clearAudioCacheAction?.let { action ->
            scope.launch {
                _audioCacheError.value = false
                runCatching { withContext(Dispatchers.Default) { action() } }
                    .onSuccess { _freedAudioBytes.value = it }
                    .onFailure { _audioCacheError.value = true }
            }
        }
    }

    fun clearSearchHistory() {
        clearSearchHistoryAction?.let { action -> scope.launch { action() } }
    }
    fun clearPlayHistory() {
        clearPlayHistoryAction?.let { action -> scope.launch { action() } }
    }

    fun setPreferredProvider(id: String) = setPreferredProvider(id, afterPersist = {})

    /**
     * Selects a provider through the same persisted preference as Settings.
     * [afterPersist] runs only after the new value is stored and the lyrics
     * cache is cleared, so a caller can safely reload the current track.
     */
    fun setPreferredProvider(id: String, afterPersist: () -> Unit) {
        if (id !in listOf("auto", "lrclib", "ytm", "lrclib_search", "kugou")) return
        _preferredProvider.value = id
        enqueueWrite {
            update.string(SettingsKeys.LYRICS_PREFERRED_PROVIDER, id)
            clearLyricsCacheAction?.invoke()
            afterPersist()
        }
    }
    fun setCountryCode(id: String) {
        val code = id.trim().uppercase()
        if (!CountryRegions.isSupportedCode(code)) return
        onCountryCodeChange(code)
        _countryCode.value = code
        enqueueWrite { update.string(SettingsKeys.COUNTRY_CODE, code) }
    }
    fun setExplicitContent(enabled: Boolean) {
        // Publish the new UI value only after the provider's SQLDelight read
        // can see it; a Home/Search refresh is keyed to this flow.
        enqueueWrite {
            update.boolean(SettingsKeys.EXPLICIT_CONTENT, enabled)
            _explicitContent.value = enabled
        }
    }
    fun setPauseOnMute(enabled: Boolean) {
        _pauseOnMute.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME, enabled) }
    }

    fun setPersistShuffleRepeat(enabled: Boolean) {
        _persistShuffleRepeat.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, enabled) }
    }
    fun setRecordListeningHistory(enabled: Boolean) {
        _recordListeningHistory.value = enabled
        enqueueWrite { update.boolean(SettingsKeys.RECORD_LISTENING_HISTORY, enabled) }
    }

    fun reset() {
        _recordListeningHistory.value = SettingsKeys.RECORD_LISTENING_HISTORY_DEFAULT
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
        onCountryCodeChange(SettingsKeys.COUNTRY_CODE_DEFAULT)
        _countryCode.value = SettingsKeys.COUNTRY_CODE_DEFAULT
        _explicitContent.value = SettingsKeys.EXPLICIT_CONTENT_DEFAULT
        _pauseOnMute.value = SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME_DEFAULT
        _persistShuffleRepeat.value = SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT
        enqueueWrite { SettingsKeys.all.forEach { update.remove(it) } }
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
