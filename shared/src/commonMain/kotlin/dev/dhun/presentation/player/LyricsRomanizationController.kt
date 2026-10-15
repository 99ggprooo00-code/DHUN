package dev.dhun.presentation.player

import dev.dhun.core.Track
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import dev.dhun.lyrics.RomanizationEngine
import dev.dhun.lyrics.RomanizationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Source text and track identity guard against stale results during navigation/provider changes. */
data class RomanizedLyrics(
    val trackId: String? = null,
    val originals: List<String> = emptyList(),
    val secondary: List<String?> = emptyList(),
) {
    fun line(trackId: String, index: Int, original: String): String? =
        secondary.getOrNull(index)?.takeIf { this.trackId == trackId && originals.getOrNull(index) == original }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRomanizationController(
    private val settings: SettingsRepository?,
    private val scope: CoroutineScope,
    track: StateFlow<Track?>,
    lyrics: StateFlow<LyricsUiState>,
    engine: RomanizationEngine = RomanizationEngine(),
) {
    private val localEnabled = MutableStateFlow(false)
    private val localMode = MutableStateFlow(RomanizationMode.AUTO)
    val enabled: StateFlow<Boolean> = settings?.observeString(SettingsKeys.LYRICS_ROMANIZATION_ENABLED)
        ?.map { it?.toBooleanStrictOrNull() ?: false }
        ?.stateIn(scope, SharingStarted.Eagerly, false) ?: localEnabled.asStateFlow()
    val mode: StateFlow<RomanizationMode> = settings?.observeString(SettingsKeys.LYRICS_ROMANIZATION_MODE)
        ?.map { RomanizationMode.fromId(it) }
        ?.stateIn(scope, SharingStarted.Eagerly, RomanizationMode.AUTO) ?: localMode.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private data class Request(val trackId: String?, val source: LyricsUiState, val enabled: Boolean, val mode: RomanizationMode)
    val result: StateFlow<RomanizedLyrics> = combine(track, lyrics, enabled, mode) { t, l, e, m ->
        Request(t?.id, l, e, m)
    }.mapLatest { request ->
        if (!request.enabled || request.trackId == null) return@mapLatest RomanizedLyrics()
        val originals = when (val source = request.source) {
            is LyricsUiState.Synced -> source.lines.map { it.text }
            is LyricsUiState.Unsynced -> source.text.lines()
            else -> emptyList()
        }
        // Bound work for untrusted service responses. Remaining originals are still displayed.
        val secondary = originals.take(2_000).map { text ->
            engine.romanize(text, request.mode).takeUnless { it == text }
        }
        RomanizedLyrics(request.trackId, originals, secondary)
    }.stateIn(scope, SharingStarted.Eagerly, RomanizedLyrics())

    fun setEnabled(value: Boolean) = write {
        if (settings == null) localEnabled.value = value
        else settings.putBoolean(SettingsKeys.LYRICS_ROMANIZATION_ENABLED, value)
    }
    fun setMode(value: RomanizationMode) = write {
        if (settings == null) localMode.value = value
        else settings.putString(SettingsKeys.LYRICS_ROMANIZATION_MODE, value.id)
    }
    private fun write(action: suspend () -> Unit) {
        scope.launch {
            try { action(); mutableError.value = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableError.value = "Couldn't save romanization preferences. Please retry." }
        }
    }
}
