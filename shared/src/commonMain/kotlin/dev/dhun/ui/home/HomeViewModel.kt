package dev.dhun.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Track
import dev.dhun.core.toUserMessage
import dev.dhun.data.DataLayer
import dev.dhun.domain.AddToPlaylistUseCase
import dev.dhun.domain.ToggleFavoriteUseCase
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.innertube.parseHomeSections
import dev.dhun.player.DhunPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Phase 07 — Home screen ViewModel.
 * Loads YTM home sections and "listen again" from local history.
 * Handles track tap → plays; long-press → overflow menu.
 */
class HomeViewModel(
    private val innerTubeClient: InnerTubeClient,
    private val player: DhunPlayer,
    private val data: DataLayer,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val sections: List<HomeSection> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        val recentlyPlayed: List<Track> = emptyList(),
        val greeting: String = "Good evening",
        val error: String? = null,
        val isRefreshing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Overflow menu state — null = menu closed. */
    private val _overflowTrack = MutableStateFlow<Track?>(null)
    val overflowTrack: StateFlow<Track?> = _overflowTrack.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = data.library.observeFavoriteIds()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val playlists: StateFlow<List<dev.dhun.data.LocalPlaylist>> = data.playlists.observePlaylists()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        updateGreeting()
        load()
        observeDataLayer()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = innerTubeClient.homeFeed()) {
                is DhunResult.Success -> {
                    val sections = parseHomeSections(result.value)
                    _state.value = _state.value.copy(
                        loading = false,
                        sections = sections,
                        error = if (sections.isEmpty()) "No content returned." else null,
                    )
                }
                is DhunResult.Failure -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            // Force client version refresh on pull-to-refresh
            innerTubeClient.clientVersion(forceRefresh = true)
            when (val result = innerTubeClient.homeFeed()) {
                is DhunResult.Success -> {
                    val sections = parseHomeSections(result.value)
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        sections = sections,
                        error = null,
                    )
                }
                is DhunResult.Failure -> {
                    _state.value = _state.value.copy(isRefreshing = false)
                }
            }
        }
    }

    private fun observeDataLayer() {
        scope.launch {
            data.search.observeRecentSearches(6).collect { searches ->
                _state.value = _state.value.copy(recentSearches = searches)
            }
        }
        scope.launch {
            data.history.observeRecentlyPlayed(6).collect { tracks ->
                _state.value = _state.value.copy(recentlyPlayed = tracks)
            }
        }
    }

    /** All tracks from all home sections, flattened. Used for quick-picks. */
    fun allTracks(): List<Track> = buildList {
        for (section in _state.value.sections) {
            for (item in section.items) {
                if (item is HomeItem.TrackItem) add(item.track)
            }
        }
    }

    /** Tracks for a specific section at [sectionIndex]. */
    fun sectionTracks(sectionIndex: Int): List<Track> {
        val section = _state.value.sections.getOrNull(sectionIndex) ?: return emptyList()
        return section.items.mapNotNull { it as? HomeItem.TrackItem }.map { it.track }
    }

    fun onTrackClick(track: Track, sectionTracks: List<Track> = listOf(track)) {
        val all = if (sectionTracks.size > 1) sectionTracks else allTracks()
        val index = all.indexOf(track).coerceAtLeast(0)
        scope.launch {
            player.prepareQueue(all, index)
            data.history.recordPlay(track, dev.dhun.data.PlayContext.HOME)
        }
    }

    fun onQuickPickClick(track: Track) {
        onTrackClick(track, allTracks())
    }

    fun onHomeItemClick(item: HomeItem) {
        when (item) {
            is HomeItem.TrackItem -> onTrackClick(item.track)
            is HomeItem.AlbumItem -> { /* Phase 09 — navigate to album */ }
            is HomeItem.ArtistItem -> { /* Phase 09 — navigate to artist */ }
            is HomeItem.PlaylistItem -> { /* Phase 09 — navigate to playlist */ }
        }
    }

    fun toggleFavorite(track: Track) {
        scope.launch { ToggleFavoriteUseCase(data.library)(track) }
    }

    fun showOverflow(track: Track) { _overflowTrack.value = track }
    fun hideOverflow() { _overflowTrack.value = null }

    fun playNext(track: Track) {
        scope.launch {
            player.next()
            // current queue is the list of all tracks
        }
        hideOverflow()
    }

    fun addToQueue(track: Track) {
        scope.launch {
            player.prepareQueue(player.queue.value + track, player.queue.value.size)
        }
        hideOverflow()
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        scope.launch { AddToPlaylistUseCase(data.playlists)(playlistId, track) }
        hideOverflow()
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        _state.value = _state.value.copy(greeting = greeting)
    }
}
