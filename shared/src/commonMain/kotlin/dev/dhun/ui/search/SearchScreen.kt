package dev.dhun.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.dhun.design.components.AlbumCard
import dev.dhun.design.components.ArtistCard
import dev.dhun.design.components.DhunAssistChip
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.TrackRow
import dev.dhun.design.components.TrackRowShimmer
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.search.SearchResultsUiState
import dev.dhun.presentation.search.SearchViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onTrackOverflow: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val resultsState by viewModel.resultsState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
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

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore) {
            viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Search Bar
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
                            listState = listState,
                            onTrackClick = onTrackClick,
                            onAlbumClick = onAlbumClick,
                            onPlaylistClick = onPlaylistClick,
                            onArtistClick = onArtistClick,
                            onTrackOverflow = onTrackOverflow,
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
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search songs, artists, albums…",
                    color = DhunColors.textHint,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                DhunIconView(
                    icon = DhunIcon.Search,
                    contentDescription = "Search",
                    modifier = Modifier
                        .padding(start = DhunSpacing.xs)
                        .size(DhunSpacing.iconSize),
                    tint = DhunColors.textTertiary,
                )
            },
            trailingIcon = {
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
            singleLine = true,
            shape = DhunShapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DhunColors.surfaceElevated,
                unfocusedContainerColor = DhunColors.surface,
                focusedBorderColor = DhunColors.accent,
                unfocusedBorderColor = DhunColors.border,
                focusedTextColor = DhunColors.textPrimary,
                unfocusedTextColor = DhunColors.textPrimary,
            ),
            modifier = Modifier.weight(1f),
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        modifier = Modifier.padding(bottom = DhunSpacing.xs),
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
            HorizontalDivider(color = DhunColors.border)
        }
    }
}

@Composable
private fun RecentSearchesSection(
    recentSearches: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteSearch: (String) -> Unit,
    onClearAll: () -> Unit,
) {
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
                    onAction = onClearAll,
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
                HorizontalDivider(color = DhunColors.border)
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: SearchResults,
    filter: SearchFilter,
    isLoadingMore: Boolean,
    listState: LazyListState,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        // Songs / Videos
        val tracks = if (filter == SearchFilter.VIDEOS) results.videos else results.songs
        if (tracks.isNotEmpty()) {
            itemsIndexed(tracks, key = { index, track -> "track_${track.id}_$index" }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(track, tracks, index) },
                    onOverflowClick = { onTrackOverflow(track) },
                )
                HorizontalDivider(color = DhunColors.border)
            }
        }

        // Artists
        if (results.artists.isNotEmpty()) {
            item(key = "artists_header") {
                SectionHeader(title = "Artists")
            }
            item(key = "artists_row") {
                LazyRow(
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
                LazyRow(
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
                LazyRow(
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

        // Pagination Loader Indicator
        if (isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DhunSpacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingShimmer(modifier = Modifier.width(140.dp).height(20.dp))
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
            HorizontalDivider(color = DhunColors.border)
        }
    }
}
