package dev.dhun.presentation.player

import dev.dhun.core.Track
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.lyrics.TtmlParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val ttmlDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val parseTtml: (String, () -> Unit) -> List<LyricsLine> = TtmlParser::parse,
    private val onSelectionChanged: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(LyricsMatchState())
    val state = mutableState.asStateFlow()
    private var track: Track? = null
    private var generation = 0L
    private var job: Job? = null
    private var saving = false

    fun setTrack(track: Track?) {
        if (this.track?.id == track?.id) return
        this.track = track
        saving = false
        job?.cancel()
        val version = ++generation
        mutableState.value = LyricsMatchState(track?.id)
        if (track != null) job = scope.launch {
            val selected = repository.selectedMatch(track.id)
            if (version == generation) mutableState.value = mutableState.value.copy(selected = selected)
        }
    }

    fun search(trackId: String, query: String) {
        if (saving) return
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

    /** Local explicit paste -> preview only. It never fetches a provider, auto-saves,
     * or reads the clipboard. Choosing the resulting candidate persists it per song.
     */
    fun previewTtml(trackId: String, input: String) {
        if (saving) return
        val current = track?.takeIf { it.id == trackId } ?: return
        job?.cancel()
        val version = ++generation
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        job = scope.launch {
            try {
                val lines = withContext(ttmlDispatcher) {
                    val context = currentCoroutineContext()
                    parseTtml(input) { context.ensureActive() }
                }
                currentCoroutineContext().ensureActive()
                if (version == generation) {
                    val match = lines.takeIf { it.isNotEmpty() }?.let {
                        LyricsMatch("local-ttml", "local_ttml", current.title, current.artistName,
                            Lyrics.Synced(it), metadataVerified = false)
                    }
                    mutableState.value = mutableState.value.copy(loading = false,
                        matches = if (match == null) mutableState.value.matches else
                            listOf(match) + mutableState.value.matches.filter { it.provider != "local_ttml" },
                        message = if (match == null) "No supported timed lyrics found. Use media-time TTML with a TTML namespace, within 256,000 characters. DTDs, frame/tick clocks and sequential/repeated timing are not supported."
                            else "Local TTML preview ready. Choose this match to save it for this song; nothing was sent to a provider.")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (version == generation) mutableState.value = mutableState.value.copy(
                    loading = false, message = "Couldn't read this TTML. Your saved choice is unchanged.")
            }
        }
    }

    fun choose(trackId: String, match: LyricsMatch?) {
        if (saving) return
        if (track?.id != trackId || (match != null && match !in mutableState.value.matches)) return
        job?.cancel()
        val version = ++generation
        saving = true
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
            } finally {
                if (version == generation) saving = false
            }
        }
    }

    fun cancelSearch() {
        // Dismissing a picker cancels searches/previews, not an explicit save.
        // Otherwise closing immediately after Choose races the durable write.
        if (saving) return
        job?.cancel()
        ++generation
        mutableState.value = mutableState.value.copy(loading = false)
    }
}
