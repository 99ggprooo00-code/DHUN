package dev.dhun.presentation.player

import dev.dhun.core.Track
import dev.dhun.lyrics.LyricsMatch
import dev.dhun.lyrics.LyricsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LyricsMatchState(
    val trackId: String? = null,
    val loading: Boolean = false,
    val matches: List<LyricsMatch> = emptyList(),
    val failedProviders: List<String> = emptyList(),
    val selected: LyricsMatch? = null,
    val message: String? = null,
)

/** Track-keyed state machine, independent of Compose. Every operation has a generation guard
 * as well as cooperative cancellation, including saving a choice during a track transition.
 */
class LyricsMatchController(
    private val repository: LyricsRepository,
    private val scope: CoroutineScope,
    private val onSelectionChanged: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(LyricsMatchState())
    val state = mutableState.asStateFlow()
    private var track: Track? = null
    private var generation = 0L
    private var job: Job? = null

    fun setTrack(track: Track?) {
        if (this.track?.id == track?.id) return
        this.track = track
        job?.cancel()
        val version = ++generation
        mutableState.value = LyricsMatchState(track?.id)
        if (track != null) job = scope.launch {
            val selected = repository.selectedMatch(track.id)
            if (version == generation) mutableState.value = mutableState.value.copy(selected = selected)
        }
    }

    fun search(trackId: String, query: String) {
        val current = track?.takeIf { it.id == trackId } ?: return
        job?.cancel()
        val version = ++generation
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        job = scope.launch {
            try {
                val result = repository.searchMatches(current, query)
                val selected = repository.selectedMatch(trackId)
                currentCoroutineContext().ensureActive()
                if (version == generation) mutableState.value = mutableState.value.copy(
                    selected = selected, loading = false, matches = result.matches, failedProviders = result.failedProviders,
                    message = if (result.matches.isEmpty()) "No matches found. Change the search or retry." else null,
                )
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (version == generation) mutableState.value = mutableState.value.copy(
                    loading = false, message = "Couldn't search lyrics. Please retry.",
                )
            }
        }
    }

    fun choose(trackId: String, match: LyricsMatch?) {
        if (track?.id != trackId || (match != null && match !in mutableState.value.matches)) return
        job?.cancel()
        val version = ++generation
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        job = scope.launch {
            try {
                if (match == null) repository.resetMatch(trackId) else repository.selectMatch(trackId, match)
                currentCoroutineContext().ensureActive()
                if (version == generation) {
                    mutableState.value = mutableState.value.copy(loading = false, selected = match)
                    onSelectionChanged(trackId)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (version == generation) mutableState.value = mutableState.value.copy(
                    loading = false, message = "Couldn't save this lyrics choice. Please retry.",
                )
            }
        }
    }

    fun cancelSearch() {
        job?.cancel()
        ++generation
        mutableState.value = mutableState.value.copy(loading = false)
    }
}
