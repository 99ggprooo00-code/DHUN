package dev.dhun.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.onKeyEvent

import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.Playlist
import dev.dhun.core.SearchResults
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.AlbumCard
import dev.dhun.design.components.ArtistCard
import dev.dhun.design.components.contentRowFill
import dev.dhun.design.components.readingSurfaceFill
import dev.dhun.design.components.DhunAssistChip
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.DhunHorizontalRail
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunTextField
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.TrackRow
import dev.dhun.design.components.TrackRowShimmer
import dev.dhun.design.components.DhunDivider
import dev.dhun.download.DownloadManager
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.search.SearchResultsUiState
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.ui.components.TrackDownloadRowActions

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onTrackOverflow: (Track) -> Unit = {},
    downloadManager: DownloadManager? = null,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val resultsState by viewModel.resultsState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val loadMoreError by viewModel.loadMoreError.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val listState = rememberLazyListState()

    // Detect reaching near the bottom for infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore, isLoadingMore, loadMoreError, resultsState) {
        if (shouldLoadMore && !isLoadingMore && loadMoreError == null) {
            viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md)) {
            Text("DHUN", style = DhunTypographyTokens.brand, color = DhunColors.accent)
            Text("Search", style = MaterialTheme.typography.headlineMedium, color = DhunColors.textPrimary)
        }
        SearchBarSection(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onSearchSubmit = viewModel::onSearchSubmitted,
            onClear = viewModel::clearQuery,
        )

        // Filter Chips Row
        FilterChipsRow(
            selectedFilter = selectedFilter,
            onFilterSelected = viewModel::onFilterSelected,
        )

        // Content Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (suggestions.isNotEmpty() && resultsState !is SearchResultsUiState.Success) {
                // Suggestions Dropdown / Overlay
                SuggestionsList(
                    suggestions = suggestions,
                    onSuggestionClick = viewModel::onSuggestionSelected,
                )
            } else {
                when (val state = resultsState) {
                    is SearchResultsUiState.Idle -> {
                        RecentSearchesSection(
                            recentSearches = recentSearches,
                            onSearchClick = { viewModel.onSuggestionSelected(it) },
                            onDeleteSearch = { viewModel.deleteRecentSearch(it) },
                            onClearAll = { viewModel.clearAllRecentSearches() },
                        )
                    }
                    is SearchResultsUiState.Loading -> {
                        SearchShimmerSkeleton(modifier = Modifier.fillMaxSize())
                    }
                    is SearchResultsUiState.Error -> {
                        ErrorView(
                            message = state.message,
                            title = "Search failed",
                            details = state.detail,
                            onRetry = { viewModel.onSearchSubmitted() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is SearchResultsUiState.Empty -> {
                        EmptyView(
                            title = "No results found",
                            message = "No matches found for '${state.query}'. Try searching for something else.",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is SearchResultsUiState.Success -> {
                        SearchResultsList(
                            results = state.results,
                            filter = selectedFilter,
                            isLoadingMore = isLoadingMore,
                            loadMoreError = loadMoreError,
                            onLoadMore = viewModel::loadMore,
                            onRetryMore = viewModel::retryLoadMore,
                            listState = listState,
                            onTrackClick = onTrackClick,
                            onAlbumClick = onAlbumClick,
                            onPlaylistClick = onPlaylistClick,
                            onArtistClick = onArtistClick,
                            onTrackOverflow = onTrackOverflow,
                            downloadManager = downloadManager,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        // DHUN's own input (translucent field, accent caret, hairline edge) in
        // the pill shape the search bar has always had. It was an M3
        // OutlinedTextField restyled to look like glass — a Material outline and
        // floating placeholder inside a glass app, which is the exact drift
        // docs/design/glass-system.md §2 forbids.
        DhunTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search songs, artists, albums…",
            shape = DhunShapes.extraLarge,
            imeAction = ImeAction.Search,
            onSubmit = onSearchSubmit,
            leading = {
                DhunIconView(
                    icon = DhunIcon.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textTertiary,
                )
            },
            trailing = {
                if (query.isNotEmpty()) {
                    DhunIconButton(onClick = onClear, contentDescription = "Clear search") {
                        DhunIconView(
                            icon = DhunIcon.Close,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            tint = DhunColors.textTertiary,
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .onKeyEvent { event ->
                    // Physical Enter (desktop keyboards, and Android hardware
                    // keyboards) — the IME Search action above is the soft-key
                    // half. The rule lives in SearchInputPolicy so the shipped
                    // predicate is the one its regression test exercises.
                    if (SearchInputPolicy.shouldSubmitOnKeyEvent(event)) {
                        onSearchSubmit()
                        true
                    } else {
                        false
                    }
                },
        )
        if (query.isNotBlank()) {
            DhunTextButton(onClick = onSearchSubmit) {
                Text("Search", color = DhunColors.accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit,
) {
    DhunHorizontalRail(
        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        modifier = Modifier.padding(bottom = DhunSpacing.sm, top = DhunSpacing.xs),
    ) {
        val filters = listOf(
            SearchFilter.SONGS to "Songs",
            SearchFilter.ARTISTS to "Artists",
            SearchFilter.ALBUMS to "Albums",
            SearchFilter.PLAYLISTS to "Playlists",
            SearchFilter.VIDEOS to "Videos",
        )
        items(filters) { (filter, label) ->
            DhunFilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SuggestionsList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DhunSpacing.screenPadding),
    ) {
        items(suggestions) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(vertical = DhunSpacing.md, horizontal = DhunSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                DhunIconView(
                    icon = DhunIcon.Search,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.textTertiary,
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DhunColors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
            DhunDivider()
        }
    }
}

@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteSearch: (String) -> Unit,
    onClearAll: suspend () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (recentSearches.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(DhunSpacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Search YouTube Music for songs, albums, and artists",
                style = MaterialTheme.typography.bodyMedium,
                color = DhunColors.textTertiary,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        ) {
            item(key = "recent_header") {
                SectionHeader(
                    title = "Recent searches",
                    actionLabel = "Clear all",
                    onAction = { showClearConfirm = true },
                )
            }
            items(recentSearches, key = { it }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearchClick(query) }
                        .padding(vertical = DhunSpacing.sm, horizontal = DhunSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    DhunIconView(
                        icon = DhunIcon.History,
                        contentDescription = "Recent search",
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.textTertiary,
                    )
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    DhunIconButton(
                        onClick = { onDeleteSearch(query) },
                        modifier = Modifier.size(DhunSpacing.touchTarget),
                        contentDescription = "Delete recent search",
                    ) {
                        DhunIconView(
                            icon = DhunIcon.Close,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            tint = DhunColors.textTertiary,
                        )
                    }
                }
                DhunDivider()
            }
        }

        if (showClearConfirm) {
            ClearRecentSearchesConfirmDialog(
                onDismiss = { showClearConfirm = false },
                onConfirm = onClearAll,
            )
        }
    }
}

@Composable
private fun ClearRecentSearchesConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isClearing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isClearing) onDismiss() }) {
        GlassCard(
            modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth),
            shape = DhunShapes.large,
            opaqueBase = true,
        ) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text("Clear recent searches?", style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                Text(
                    "This removes all recent search terms from this device. Your history, playlists and favorites stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(DhunSpacing.sm))
                    Text(errorMessage!!, style = MaterialTheme.typography.bodySmall, color = DhunColors.error)
                }
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = { if (!isClearing) onDismiss() }, enabled = !isClearing) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    DhunButton(
                        onClick = {
                            if (!isClearing) {
                                isClearing = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        onConfirm()
                                        onDismiss()
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        errorMessage = "Could not clear recent searches. Please try again."
                                    } finally {
                                        isClearing = false
                                    }
                                }
                            }
                        },
                        enabled = !isClearing,
                    ) {
                        Text(if (isClearing) "Clearing…" else if (errorMessage != null) "Try again" else "Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: SearchResults,
    filter: SearchFilter,
    isLoadingMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    listState: LazyListState,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    downloadManager: DownloadManager?,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DhunSpacing.contentBottomInset),
    ) {
        // Songs / Videos
        val tracks = if (filter == SearchFilter.VIDEOS) results.videos else results.songs
        if (tracks.isNotEmpty()) {
            itemsIndexed(tracks, key = { index, track -> "track_${track.id}_$index" }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(track, tracks, index) },
                    trailing = {
                        TrackDownloadRowActions(
                            track = track,
                            downloadManager = downloadManager,
                            onOverflowClick = { onTrackOverflow(track) },
                            // Icon-only badge: keeps long titles readable on
                            // narrow windows; the progress ring still shows
                            // live percent and state stays announced.
                            showBadgeLabel = false,
                        )
                    },
                )
            }
        }

        // Artists
        if (results.artists.isNotEmpty()) {
            item(key = "artists_header") {
                SectionHeader(title = "Artists")
            }
            item(key = "artists_row") {
                DhunHorizontalRail(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    items(results.artists, key = { it.id }) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { onArtistClick(artist) },
                        )
                    }
                }
            }
        }

        // Albums
        if (results.albums.isNotEmpty()) {
            item(key = "albums_header") {
                SectionHeader(title = "Albums")
            }
            item(key = "albums_row") {
                DhunHorizontalRail(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    items(results.albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
            }
        }

        // Playlists
        if (results.playlists.isNotEmpty()) {
            item(key = "playlists_header") {
                SectionHeader(title = "Playlists")
            }
            item(key = "playlists_row") {
                DhunHorizontalRail(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    items(results.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) },
                        )
                    }
                }
            }
        }

        // Pagination status: failures keep the existing results visible and
        // wait for an explicit retry instead of looping on recomposition.
        if (isLoadingMore || loadMoreError != null || results.continuationToken != null) {
            item(key = "pagination_status") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(DhunSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    when {
                        isLoadingMore -> {
                            LoadingShimmer(
                                modifier = Modifier.width(DhunSpacing.skeletonMetaWidth).height(DhunSpacing.xl),
                            )
                            Text("Loading more results…", color = DhunColors.textSecondary)
                        }
                        loadMoreError != null -> {
                            Text(loadMoreError, color = DhunColors.textSecondary)
                            DhunTextButton(onClick = onRetryMore) { Text("Retry loading more") }
                        }
                        else -> DhunTextButton(onClick = onLoadMore) { Text("Load more results") }
                    }
                }
            }
        }
    }
}

/** Loading skeleton for search results. */
@Composable
private fun SearchShimmerSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DhunSpacing.screenPadding),
    ) {
        repeat(6) {
            TrackRowShimmer()
        }
    }
}
