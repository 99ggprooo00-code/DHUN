package dev.dhun.presentation.settings

import dev.dhun.data.SettingsKeys
import dev.dhun.design.DhunVisualTuning
import dev.dhun.design.VisualPart
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.player.AudioCacheBudget
import dev.dhun.presentation.MutationState
import dev.dhun.presentation.MutationTracker
import kotlinx.coroutines.CancellationException
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
    private val isTestRelease: Boolean = false,
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

    /**
     * Runs one preference write under the shared write mutex.
     *
     * @param onFailure optional rollback, run before the failure is reported. A
     *   control publishes its new value immediately (a slider must follow the
     *   finger), so without this a failed write left the UI showing a value the
     *   database never stored — the screen lied until the next launch. The
     *   rollback republishes what is actually persisted.
     *
     * The action is the first argument on purpose, so a call without a rollback
     * must pass it in parentheses (`enqueueWrite({ … })`): a bare trailing
     * lambda binds to [onFailure] instead.
     */
    private fun enqueueWrite(action: suspend () -> Unit, onFailure: (suspend () -> Unit)? = null) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeMutex.withLock {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed preference write used to escape this coroutine
                    // and reach the platform's uncaught-exception handler (an
                    // app crash on Android). It is a user-facing failure now:
                    // the control is put back to the stored value and Settings
                    // says what happened.
                    if (onFailure != null) {
                        try {
                            onFailure()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Reading back can fail too (the database is what
                            // just failed). Keep the message; the next launch
                            // shows the stored value regardless.
                        }
                    }
                    maintenance.report(SAVE_FAILED)
                }
            }
        }
    }

    /* Typed single-key writes: persist, and on failure republish the stored
     * value (or the documented default when the key is absent). */

    private fun persistString(key: String, default: String, publish: (String) -> Unit, write: suspend () -> Unit) =
        enqueueWrite(write, onFailure = { publish(get.string(key, default)) })

    private fun persistInt(key: String, default: Int, publish: (Int) -> Unit, write: suspend () -> Unit) =
        enqueueWrite(write, onFailure = { publish(get.int(key, default)) })

    private fun persistBoolean(key: String, default: Boolean, publish: (Boolean) -> Unit, write: suspend () -> Unit) =
        enqueueWrite(write, onFailure = { publish(get.boolean(key, default)) })

    /**
     * Pending/failure state for the destructive maintenance actions and for
     * preference writes. The dialogs read [maintenanceState] so a confirm
     * button can show "Clearing…" and keep its failure on screen for a retry,
     * the same contract the playlist-delete and clear-history dialogs use.
     */
    private val maintenance = MutationTracker(scope)
    val maintenanceState: StateFlow<MutationState> = maintenance.state
    fun dismissMaintenanceError() = maintenance.dismissError()

    /**
     * The key of the last maintenance action that **completed successfully**.
     * A confirmation dialog closes on this signal, never on the tap — a clear
     * that failed leaves its dialog open with a retry.
     */
    private val _lastCompletedKey = MutableStateFlow<String?>(null)
    val lastCompletedKey: StateFlow<String?> = _lastCompletedKey.asStateFlow()
    fun consumeCompletedAction() {
        _lastCompletedKey.value = null
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

    private val _visualTuning = MutableStateFlow<Map<VisualPart, Int>>(emptyMap())
    val visualTuning: StateFlow<Map<VisualPart, Int>> = _visualTuning.asStateFlow()

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
            // Every control on this screen reads a persisted value, and the
            // whole app waits for [loaded] before it applies appearance and
            // visual tuning. An unhandled read failure killed this coroutine
            // (an uncaught exception on Android) and left `loaded` false
            // forever: Settings never rendered its controls, and the rest of
            // the app never applied stored preferences. A failed read now
            // falls back to the documented defaults and says so.
            try {
                loadPersistedValues()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                maintenance.report(SETTINGS_LOAD_FAILED)
            } finally {
                _loaded.value = true
            }
        }
    }

    /** Slider baseline: the test channel's 15% or the original public 50%. */
    private val tuningBaseline: Int
        get() = if (isTestRelease) DhunVisualTuning.TEST_DEFAULT else DhunVisualTuning.NEUTRAL

    private suspend fun readStoredTuning(): Map<VisualPart, Int> {
        val stored = mutableMapOf<VisualPart, Int>()
        for (part in DhunVisualTuning.adjustableParts) {
            stored[part] = get.int(part.key, tuningBaseline).coerceIn(0, 100)
        }
        return stored
    }

    /** Publishes a tuning snapshot to both the sliders and the live appearance. */
    private fun publishTuning(values: Map<VisualPart, Int>) {
        _visualTuning.value = values
        DhunVisualTuning.restore(values, isTestRelease)
    }

    /**
     * Reads every persisted preference and then publishes the snapshot.
     *
     * The values are read into locals first on purpose: publishing as each key
     * arrives meant a read that failed halfway left earlier keys applied
     * alongside defaults for the rest — a mixed snapshot the user never chose,
     * which then silently reverted on the next launch. Nothing is published
     * until the whole snapshot is known, so a failed load leaves the documented
     * defaults in place.
     */
    private suspend fun loadPersistedValues() {
        // One-shot test-profile migration must run before the values are read,
        // so old saved experiments cannot mask the requested 15% baseline.
        if (isTestRelease && get.int(SettingsKeys.TUNE_PROFILE_VERSION, 0) < 1) {
            writeMutex.withLock {
                for (part in VisualPart.entries) update.remove(part.key)
                update.int(SettingsKeys.TUNE_PROFILE_VERSION, 1)
            }
        }

        val themeId = get.string(SettingsKeys.THEME, SettingsKeys.THEME_DEFAULT)
        val accentId = get.string(SettingsKeys.ACCENT, SettingsKeys.ACCENT_DEFAULT)
        val cacheSizeMb = AudioCacheBudget.clampMb(
            get.int(SettingsKeys.CACHE_SIZE_MB, SettingsKeys.CACHE_SIZE_MB_DEFAULT),
        )
        val resumeOnLaunch = get.boolean(SettingsKeys.RESUME_ON_LAUNCH, SettingsKeys.RESUME_ON_LAUNCH_DEFAULT)
        val closeToTray = get.boolean(SettingsKeys.CLOSE_TO_TRAY, SettingsKeys.CLOSE_TO_TRAY_DEFAULT)
        val blur = get.int(SettingsKeys.BACKDROP_BLUR, SettingsKeys.BACKDROP_BLUR_DEFAULT).coerceIn(0, 100)
        val brightness = get.int(SettingsKeys.BACKDROP_BRIGHTNESS, SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT).coerceIn(0, 100)
        val tuning = readStoredTuning()
        val accentMode = get.string(SettingsKeys.ACCENT_MODE, SettingsKeys.ACCENT_MODE_DEFAULT)
        val audioQuality = get.string(SettingsKeys.AUDIO_QUALITY, SettingsKeys.AUDIO_QUALITY_DEFAULT)
        val seekStepSeconds = get.int(SettingsKeys.SEEK_STEP_SECONDS, SettingsKeys.SEEK_STEP_SECONDS_DEFAULT)
        val autoplayRadio = get.boolean(SettingsKeys.AUTOPLAY_RADIO_ENABLED, SettingsKeys.AUTOPLAY_RADIO_ENABLED_DEFAULT)
        val lyricsEnabled = get.boolean(SettingsKeys.LYRICS_ENABLED, SettingsKeys.LYRICS_ENABLED_DEFAULT)
        val lyricsPreferSynced = get.boolean(SettingsKeys.LYRICS_PREFER_SYNCED, SettingsKeys.LYRICS_PREFER_SYNCED_DEFAULT)
        val lyricsAutoScroll = get.boolean(SettingsKeys.LYRICS_AUTO_SCROLL, SettingsKeys.LYRICS_AUTO_SCROLL_DEFAULT)
        val lyricsTextSize = get.string(SettingsKeys.LYRICS_TEXT_SIZE, SettingsKeys.LYRICS_TEXT_SIZE_DEFAULT)
        val providerOrder = get.string(SettingsKeys.LYRICS_PROVIDER_ORDER, SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT)
        val preferredProvider = get.string(SettingsKeys.LYRICS_PREFERRED_PROVIDER, SettingsKeys.LYRICS_PREFERRED_PROVIDER_DEFAULT)
        val countryCode = get.string(SettingsKeys.COUNTRY_CODE, SettingsKeys.COUNTRY_CODE_DEFAULT)
            .uppercase()
            .takeIf(CountryRegions::isSupportedCode)
            ?: SettingsKeys.COUNTRY_CODE_DEFAULT
        val explicitContent = get.boolean(SettingsKeys.EXPLICIT_CONTENT, SettingsKeys.EXPLICIT_CONTENT_DEFAULT)
        val pauseOnMute = get.boolean(SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME, SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME_DEFAULT)
        val persistShuffleRepeat = get.boolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT)
        val recordListeningHistory = get.boolean(SettingsKeys.RECORD_LISTENING_HISTORY, SettingsKeys.RECORD_LISTENING_HISTORY_DEFAULT)

        // Commit — every read above succeeded, so this is one coherent snapshot.
        _themeId.value = themeId
        _accentId.value = accentId
        _cacheSizeMb.value = cacheSizeMb
        _resumeOnLaunch.value = resumeOnLaunch
        _closeToTray.value = closeToTray
        _backdropBlurPercent.value = blur
        _backdropBrightnessPercent.value = brightness
        publishTuning(tuning)
        _accentMode.value = accentMode
        _audioQuality.value = audioQuality
        _seekStepSeconds.value = seekStepSeconds
        _autoplayRadio.value = autoplayRadio
        _lyricsEnabled.value = lyricsEnabled
        _lyricsPreferSynced.value = lyricsPreferSynced
        _lyricsAutoScroll.value = lyricsAutoScroll
        _lyricsTextSize.value = lyricsTextSize
        _providerOrder.value = providerOrder
        _preferredProvider.value = preferredProvider
        _countryCode.value = countryCode
        _explicitContent.value = explicitContent
        _pauseOnMute.value = pauseOnMute
        _persistShuffleRepeat.value = persistShuffleRepeat
        _recordListeningHistory.value = recordListeningHistory
    }

    /**
     * Only ids the data layer documents are persisted; anything else is
     * ignored so a corrupt write path can never poison the DB.
     */
    fun setThemeId(id: String) {
        if (id !in listOf("dark", "light", "black", "system")) return
        _themeId.value = id
        persistString(SettingsKeys.THEME, SettingsKeys.THEME_DEFAULT, { _themeId.value = it }) {
            update.string(SettingsKeys.THEME, id)
        }
    }

    fun setAccentId(id: String) {
        if (id.isBlank()) return
        _accentId.value = id
        persistString(SettingsKeys.ACCENT, SettingsKeys.ACCENT_DEFAULT, { _accentId.value = it }) {
            update.string(SettingsKeys.ACCENT, id)
        }
    }

    /** No-op unless [mb] is one of [CACHE_OPTIONS_MB]. */
    fun setCacheSizeMb(mb: Int) {
        if (mb !in CACHE_OPTIONS_MB) return
        _cacheSizeMb.value = mb
        persistInt(SettingsKeys.CACHE_SIZE_MB, SettingsKeys.CACHE_SIZE_MB_DEFAULT, { _cacheSizeMb.value = AudioCacheBudget.clampMb(it) }) {
            update.int(SettingsKeys.CACHE_SIZE_MB, mb)
        }
    }

    fun setResumeOnLaunch(enabled: Boolean) {
        _resumeOnLaunch.value = enabled
        persistBoolean(SettingsKeys.RESUME_ON_LAUNCH, SettingsKeys.RESUME_ON_LAUNCH_DEFAULT, { _resumeOnLaunch.value = it }) {
            update.boolean(SettingsKeys.RESUME_ON_LAUNCH, enabled)
        }
    }

    fun resetWindowGeometry() {
        enqueueWrite({ update.remove(SettingsKeys.WINDOW_GEOMETRY) })
    }

    fun setCloseToTray(enabled: Boolean) {
        _closeToTray.value = enabled
        persistBoolean(SettingsKeys.CLOSE_TO_TRAY, SettingsKeys.CLOSE_TO_TRAY_DEFAULT, { _closeToTray.value = it }) {
            update.boolean(SettingsKeys.CLOSE_TO_TRAY, enabled)
        }
    }

    fun setBackdropBlurPercent(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _backdropBlurPercent.value = value
        persistInt(SettingsKeys.BACKDROP_BLUR, SettingsKeys.BACKDROP_BLUR_DEFAULT, { _backdropBlurPercent.value = it }) {
            update.int(SettingsKeys.BACKDROP_BLUR, value)
        }
    }

    fun setBackdropBrightnessPercent(percent: Int) {
        val value = percent.coerceIn(0, 100)
        _backdropBrightnessPercent.value = value
        persistInt(SettingsKeys.BACKDROP_BRIGHTNESS, SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT, { _backdropBrightnessPercent.value = it }) {
            update.int(SettingsKeys.BACKDROP_BRIGHTNESS, value)
        }
    }

    /** Preview continuously, but write once on slider release (not per drag frame). */
    fun previewVisualTuning(part: VisualPart, percent: Int) {
        if (part !in DhunVisualTuning.adjustableParts) return
        _visualTuning.value = _visualTuning.value + (part to percent.coerceIn(0, 100))
    }

    fun saveVisualTuning(part: VisualPart) {
        if (part !in DhunVisualTuning.adjustableParts) return
        val value = _visualTuning.value[part] ?: tuningBaseline
        enqueueWrite({ update.int(part.key, value) }) {
            // The slider already shows the dragged value; put the stored one
            // back rather than leaving an unsaved experiment applied.
            publishTuning(_visualTuning.value + (part to get.int(part.key, tuningBaseline).coerceIn(0, 100)))
        }
    }

    fun resetVisualTuning() {
        publishTuning(emptyMap())
        enqueueWrite({ for (part in VisualPart.entries) update.remove(part.key) }) {
            // A failed reset must not leave the app on a baseline it did not
            // persist: re-read the stored experiments and reapply them.
            publishTuning(readStoredTuning())
        }
    }

    fun setAccentMode(id: String) {
        if (id !in listOf("artwork", "static")) return
        _accentMode.value = id
        persistString(SettingsKeys.ACCENT_MODE, SettingsKeys.ACCENT_MODE_DEFAULT, { _accentMode.value = it }) {
            update.string(SettingsKeys.ACCENT_MODE, id)
        }
    }
    fun setAudioQuality(id: String) {
        if (id !in listOf("low", "medium", "high")) return
        _audioQuality.value = id
        persistString(SettingsKeys.AUDIO_QUALITY, SettingsKeys.AUDIO_QUALITY_DEFAULT, { _audioQuality.value = it }) {
            update.string(SettingsKeys.AUDIO_QUALITY, id)
        }
    }
    fun setSeekStepSeconds(seconds: Int) {
        if (seconds !in listOf(5, 10, 15, 30)) return
        _seekStepSeconds.value = seconds
        persistInt(SettingsKeys.SEEK_STEP_SECONDS, SettingsKeys.SEEK_STEP_SECONDS_DEFAULT, { _seekStepSeconds.value = it }) {
            update.int(SettingsKeys.SEEK_STEP_SECONDS, seconds)
        }
    }
    fun setAutoplayRadio(enabled: Boolean) {
        _autoplayRadio.value = enabled
        persistBoolean(SettingsKeys.AUTOPLAY_RADIO_ENABLED, SettingsKeys.AUTOPLAY_RADIO_ENABLED_DEFAULT, { _autoplayRadio.value = it }) {
            update.boolean(SettingsKeys.AUTOPLAY_RADIO_ENABLED, enabled)
        }
    }
    fun setLyricsEnabled(enabled: Boolean) {
        _lyricsEnabled.value = enabled
        persistBoolean(SettingsKeys.LYRICS_ENABLED, SettingsKeys.LYRICS_ENABLED_DEFAULT, { _lyricsEnabled.value = it }) {
            update.boolean(SettingsKeys.LYRICS_ENABLED, enabled)
        }
    }
    fun setLyricsPreferSynced(enabled: Boolean) {
        _lyricsPreferSynced.value = enabled
        persistBoolean(SettingsKeys.LYRICS_PREFER_SYNCED, SettingsKeys.LYRICS_PREFER_SYNCED_DEFAULT, { _lyricsPreferSynced.value = it }) {
            update.boolean(SettingsKeys.LYRICS_PREFER_SYNCED, enabled)
            clearLyricsCacheAfterPreferenceChange()
        }
    }
    fun setLyricsAutoScroll(enabled: Boolean) {
        _lyricsAutoScroll.value = enabled
        persistBoolean(SettingsKeys.LYRICS_AUTO_SCROLL, SettingsKeys.LYRICS_AUTO_SCROLL_DEFAULT, { _lyricsAutoScroll.value = it }) {
            update.boolean(SettingsKeys.LYRICS_AUTO_SCROLL, enabled)
        }
    }
    fun setLyricsTextSize(id: String) {
        if (id !in listOf("small", "default", "large")) return
        _lyricsTextSize.value = id
        persistString(SettingsKeys.LYRICS_TEXT_SIZE, SettingsKeys.LYRICS_TEXT_SIZE_DEFAULT, { _lyricsTextSize.value = it }) {
            update.string(SettingsKeys.LYRICS_TEXT_SIZE, id)
        }
    }
    fun setProviderEnabled(id: String, enabled: Boolean) {
        if (id !in listOf("lrclib", "ytm", "lrclib_search", "kugou")) return
        val current = _providerOrder.value.split(',').filter { it.isNotBlank() }
        val next = if (enabled) (current + id).distinct() else current.filterNot { it == id }
        if (next.isEmpty()) return
        _providerOrder.value = next.joinToString(",")
        val order = _providerOrder.value
        persistString(SettingsKeys.LYRICS_PROVIDER_ORDER, SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT, { _providerOrder.value = it }) {
            update.string(SettingsKeys.LYRICS_PROVIDER_ORDER, order)
            clearLyricsCacheAfterPreferenceChange()
        }
    }

    /**
     * Clears the automatic lyrics cache. The Settings dialog keeps its confirm
     * button pending on [maintenanceState] and shows [LYRICS_CLEAR_FAILED]
     * instead of closing as if the clear had happened.
     */
    fun clearLyricsCache() {
        val action = clearLyricsCacheAction ?: return
        maintenance.run(KEY_CLEAR_LYRICS, LYRICS_CLEAR_FAILED, onSuccess = { _lastCompletedKey.value = KEY_CLEAR_LYRICS }) { action() }
    }

    /** Cache clear fired by a provider/preference change, after the write. */
    private suspend fun clearLyricsCacheAfterPreferenceChange() {
        val action = clearLyricsCacheAction ?: return
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            maintenance.report(LYRICS_CLEAR_FAILED)
        }
    }

    val canClearAudioCache: Boolean get() = clearAudioCacheAction != null
    private val _freedAudioBytes = MutableStateFlow<Long?>(null)
    val freedAudioBytes: StateFlow<Long?> = _freedAudioBytes.asStateFlow()

    fun clearAudioCache() {
        val action = clearAudioCacheAction ?: return
        _freedAudioBytes.value = null
        maintenance.run(KEY_CLEAR_AUDIO, AUDIO_CLEAR_FAILED, onSuccess = { _lastCompletedKey.value = KEY_CLEAR_AUDIO }) {
            val freed = withContext(Dispatchers.Default) { action() }
            _freedAudioBytes.value = freed
        }
    }

    fun clearSearchHistory() {
        val action = clearSearchHistoryAction ?: return
        maintenance.run(KEY_CLEAR_SEARCH, SEARCH_HISTORY_CLEAR_FAILED, onSuccess = { _lastCompletedKey.value = KEY_CLEAR_SEARCH }) { action() }
    }

    fun clearPlayHistory() {
        val action = clearPlayHistoryAction ?: return
        maintenance.run(KEY_CLEAR_PLAY, PLAY_HISTORY_CLEAR_FAILED, onSuccess = { _lastCompletedKey.value = KEY_CLEAR_PLAY }) { action() }
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
        persistString(SettingsKeys.LYRICS_PREFERRED_PROVIDER, SettingsKeys.LYRICS_PREFERRED_PROVIDER_DEFAULT, { _preferredProvider.value = it }) {
            update.string(SettingsKeys.LYRICS_PREFERRED_PROVIDER, id)
            clearLyricsCacheAfterPreferenceChange()
            afterPersist()
        }
    }
    fun setCountryCode(id: String) {
        val code = id.trim().uppercase()
        if (!CountryRegions.isSupportedCode(code)) return
        onCountryCodeChange(code)
        _countryCode.value = code
        persistString(SettingsKeys.COUNTRY_CODE, SettingsKeys.COUNTRY_CODE_DEFAULT, { stored ->
            // The provider is told the country only for a value it will keep.
            if (stored != _countryCode.value) onCountryCodeChange(stored)
            _countryCode.value = stored
        }) {
            update.string(SettingsKeys.COUNTRY_CODE, code)
        }
    }
    fun setExplicitContent(enabled: Boolean) {
        // Publish the new UI value only after the provider's SQLDelight read
        // can see it; a Home/Search refresh is keyed to this flow.
        enqueueWrite({
            update.boolean(SettingsKeys.EXPLICIT_CONTENT, enabled)
            _explicitContent.value = enabled
        })
    }
    fun setPauseOnMute(enabled: Boolean) {
        _pauseOnMute.value = enabled
        persistBoolean(SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME, SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME_DEFAULT, { _pauseOnMute.value = it }) {
            update.boolean(SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME, enabled)
        }
    }

    fun setPersistShuffleRepeat(enabled: Boolean) {
        _persistShuffleRepeat.value = enabled
        persistBoolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, SettingsKeys.PERSIST_SHUFFLE_REPEAT_DEFAULT, { _persistShuffleRepeat.value = it }) {
            update.boolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, enabled)
        }
    }
    fun setRecordListeningHistory(enabled: Boolean) {
        _recordListeningHistory.value = enabled
        persistBoolean(SettingsKeys.RECORD_LISTENING_HISTORY, SettingsKeys.RECORD_LISTENING_HISTORY_DEFAULT, { _recordListeningHistory.value = it }) {
            update.boolean(SettingsKeys.RECORD_LISTENING_HISTORY, enabled)
        }
    }

    /**
     * The one place that knows every documented default. Used by [reset], so a
     * new setting cannot be added to the load path and forgotten here.
     */
    private fun applyDefaults() {
        _themeId.value = SettingsKeys.THEME_DEFAULT
        _accentId.value = SettingsKeys.ACCENT_DEFAULT
        _accentMode.value = SettingsKeys.ACCENT_MODE_DEFAULT
        _backdropBlurPercent.value = SettingsKeys.BACKDROP_BLUR_DEFAULT
        _backdropBrightnessPercent.value = SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT
        publishTuning(emptyMap())
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
        _recordListeningHistory.value = SettingsKeys.RECORD_LISTENING_HISTORY_DEFAULT
    }

    fun reset() {
        applyDefaults()
        // Same write mutex as every other control, so a slider moved after
        // Reset cannot be erased by it (and vice versa). The tracker adds the
        // pending/failure contract the dialog needs.
        maintenance.run(
            KEY_RESET,
            RESET_FAILED,
            onSuccess = { _lastCompletedKey.value = KEY_RESET },
            onFailure = {
                // Every control already shows its default. If the rows could
                // not be removed they are still stored, so republish what is
                // actually there instead of a state this launch invented.
                loadPersistedValues()
            },
        ) {
            writeMutex.withLock { SettingsKeys.all.forEach { update.remove(it) } }
        }
    }

    companion object {
        /* Mutation keys + user-facing failure messages, one pair per action. */
        const val KEY_CLEAR_LYRICS = "clear-lyrics-cache"
        const val KEY_CLEAR_AUDIO = "clear-audio-cache"
        const val KEY_CLEAR_SEARCH = "clear-search-history"
        const val KEY_CLEAR_PLAY = "clear-play-history"
        const val KEY_RESET = "reset-settings"

        const val SAVE_FAILED = "Could not save that setting. Check free space and try again."
        const val LYRICS_CLEAR_FAILED = "Could not clear the lyrics cache. Please try again."
        const val AUDIO_CLEAR_FAILED = "Could not clear the audio cache. Stop playback and try again."
        const val SEARCH_HISTORY_CLEAR_FAILED = "Could not clear the search history. Please try again."
        const val PLAY_HISTORY_CLEAR_FAILED = "Could not clear the play history. Please try again."
        const val RESET_FAILED = "Could not reset every setting. Please try again."
        const val SETTINGS_LOAD_FAILED = "Could not read your saved settings, so DHUN is using its defaults."

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
