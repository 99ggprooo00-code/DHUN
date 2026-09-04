package dev.dhun.ui.search

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.Playlist
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.AlbumCard
import dev.dhun.design.components.ArtistCard
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.TrackRow
import dev.dhun.innertube.SearchFilter

/**
 * Phase 07 — shared SearchScreen.
 * Search bar → suggestions (inline) → filter chips → results.
 * Overflow menu (play next / add to queue / add to playlist) via ViewModel.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    onNavigateBack: () -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Infinite scroll detection
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = state.tracks.size + state.artists.size + state.albums.size + state.playlists.size
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.loading && !state.loadingMore) {
            viewModel.loadMore()
        }
    }

    // Pre-fill query if passed in
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty() && state.query.isEmpty()) {
            viewModel.onQueryChange(initialQuery)
            viewModel.onQuerySubmit(initialQuery)
        }
    }

    // Auto-focus search bar on first composition
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DhunColors.surface)
            .imePadding(),
    ) {
        // Search bar
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = {
                focusManager.clearFocus()
                viewModel.onQuerySubmit(state.query)
            },
            onClear = viewModel::clearQuery,
            onBack = {
                focusManager.clearFocus()
                onNavigateBack()
            },
            focusRequester = focusRequester,
        )

        // Filter chips (visible once user has searched)
        if (state.query.isNotEmpty()) {
            FilterChipsRow(
                selected = state.selectedFilter,
                onSelect = viewModel::onFilterChange,
            )
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // Suggestions overlay
                state.showSuggestions && state.suggestions.isNotEmpty() -> {
                    SuggestionsList(
                        suggestions = state.suggestions,
                        recentSearches = recentSearches,
                        onSelectSuggestion = { suggestion ->
                            viewModel.selectSuggestion(suggestion)
                            focusManager.clearFocus()
                        },
                        onRemoveRecent = viewModel::removeRecentSearch,
                        onClearRecent = viewModel::clearRecentSearches,
                    )
                }
                // Loading
                state.loading -> SearchLoadingContent()
                // Error
                state.error != null -> ErrorView(
                    message = state.error!!,
                    onRetry = { viewModel.search(state.query) },
                    modifier = Modifier.fillMaxSize(),
                )
                // Empty results
                state.tracks.isEmpty() && state.artists.isEmpty() &&
                    state.albums.isEmpty() && state.playlists.isEmpty() &&
                    state.query.isNotEmpty() -> EmptyView(
                    title = "No results for \"${state.query}\"",
                    message = "Try a different search or filter",
                    modifier = Modifier.fillMaxSize(),
                )
                // Results
                state.query.isNotEmpty() -> SearchResultsList(
                    state = state,
                    favorites = favorites,
                    viewModel = viewModel,
                    listState = listState,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                )
                // Empty state (no query)
                else -> SearchStartContent()
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.sm, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            IconArrowLeft(DhunColors.textPrimary)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .background(DhunColors.surfaceElevated, DhunShapes.large)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconSearch(DhunColors.textTertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(DhunSpacing.sm))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = DhunColors.textPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(DhunColors.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "Search songs, artists, albums…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DhunColors.textTertiary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(20.dp),
                    ) {
                        IconClose(DhunColors.textTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        modifier = Modifier.padding(vertical = DhunSpacing.sm),
    ) {
        items(SearchFilter.entries) { filter ->
            DhunFilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.displayName()) },
            )
        }
    }
}

@Composable
private fun SuggestionsList(
    suggestions: List<String>,
    recentSearches: List<String>,
    onSelectSuggestion: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = DhunSpacing.sm),
    ) {
        // Recent searches
        if (recentSearches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        color = DhunColors.textTertiary,
                    )
                    TextButton(onClick = onClearRecent) {
                        Text("Clear all", color = DhunColors.accent, fontSize = 12.sp)
                    }
                }
            }
            items(recentSearches) { query ->
                SuggestionRow(
                    icon = { IconHistory(DhunColors.textTertiary, modifier = Modifier.size(20.dp)) },
                    text = query,
                    trailing = {
                        IconButton(onClick = { onRemoveRecent(query) }, modifier = Modifier.size(20.dp)) {
                            IconClose(DhunColors.textTertiary, modifier = Modifier.size(14.dp))
                        }
                    },
                    onClick = { onSelectSuggestion(query) },
                )
            }
            item {
                HorizontalDivider(
                    color = DhunColors.border,
                    modifier = Modifier.padding(vertical = DhunSpacing.sm),
                )
            }
        }

        // Suggestions
        items(suggestions) { suggestion ->
            SuggestionRow(
                icon = { IconSearch(DhunColors.textTertiary, modifier = Modifier.size(20.dp)) },
                text = suggestion,
                onClick = { onSelectSuggestion(suggestion) },
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: @Composable () -> Unit,
    text: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(DhunSpacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = DhunColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

@Composable
private fun SearchResultsList(
    state: SearchViewModel.UiState,
    favorites: Set<String>,
    viewModel: SearchViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = DhunSpacing.bottomNavHeight + DhunSpacing.huge,
        ),
    ) {
        // Tracks section
        if (state.tracks.isNotEmpty()) {
            item {
                SectionHeader(title = "Songs (${state.tracks.size})")
            }
            itemsIndexed(state.tracks, key = { _, t -> t.id }) { _, track ->
                TrackRow(
                    track = track,
                    onClick = { viewModel.onTrackClick(track) },
                    trailing = {
                        TrackOverflowButton(
                            track = track,
                            isFavorite = track.id in favorites,
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            onShowOverflow = { viewModel.showOverflow(track) },
                        )
                    },
                )
            }
            if (state.selectedFilter == SearchFilter.ALL) {
                item { Spacer(Modifier.height(DhunSpacing.lg)) }
            }
        }

        // Artists section
        if (state.artists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    items(state.artists, key = { it.id }) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = { onArtistClick(artist) },
                        )
                    }
                }
                Spacer(Modifier.height(DhunSpacing.lg))
            }
        }

        // Albums section
        if (state.albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Albums")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    items(state.albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
                Spacer(Modifier.height(DhunSpacing.lg))
            }
        }

        // Playlists section
        if (state.playlists.isNotEmpty()) {
            item {
                SectionHeader(title = "Playlists")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) },
                        )
                    }
                }
                Spacer(Modifier.height(DhunSpacing.lg))
            }
        }

        // Load more indicator
        if (state.loadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DhunSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = DhunColors.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackOverflowButton(
    track: Track,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShowOverflow: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Favorite button
        IconButton(onClick = onToggleFavorite) {
            Text(
                text = if (isFavorite) "♥" else "♡",
                color = if (isFavorite) DhunColors.accent else DhunColors.textTertiary,
                fontSize = 18.sp,
            )
        }
        // Overflow: three dots drawn with Canvas
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onShowOverflow),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val dotRadius = 2.dp.toPx()
                val spacing = 6.dp.toPx()
                val centerX = size.width / 2
                val centerY = size.height / 2
                val dotColor = DhunColors.textTertiary
                drawCircle(dotColor, centerX - spacing, centerY - dotRadius, dotRadius)
                drawCircle(dotColor, centerX, centerY - dotRadius, dotRadius)
                drawCircle(dotColor, centerX + spacing, centerY - dotRadius, dotRadius)
            }
        }
    }
}

@Composable
private fun SearchLoadingContent() {
    LazyColumn(
        contentPadding = PaddingValues(top = DhunSpacing.lg),
    ) {
        items(8) {
            LoadingShimmer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs),
                shape = DhunShapes.small,
            )
        }
    }
}

@Composable
private fun SearchStartContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconSearch(
            tint = DhunColors.textTertiary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(DhunSpacing.lg))
        Text(
            "Search YouTube Music",
            style = MaterialTheme.typography.titleMedium,
            color = DhunColors.textSecondary,
        )
        Text(
            "Songs, artists, albums, playlists",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textTertiary,
        )
    }
}

/* ---- platform-safe vector icons (no material-icons-extended dependency) ---- */

@Composable
private fun IconArrowLeft(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.6f, size.height * 0.15f)
            lineTo(size.width * 0.3f, size.height * 0.5f)
            lineTo(size.width * 0.6f, size.height * 0.85f)
            moveTo(size.width * 0.3f, size.height * 0.5f)
            lineTo(size.width * 0.95f, size.height * 0.5f)
        }
        drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f.toPx()))
    }
}

@Composable
private fun IconSearch(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        drawCircle(tint, radius = size.minDimension / 2.8f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f.toPx()))
        drawLine(
            tint,
            Offset(size.width * 0.72f, size.height * 0.72f),
            Offset(size.width * 0.95f, size.height * 0.95f),
            strokeWidth = 2f.toPx(),
        )
    }
}

@Composable
private fun IconClose(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val pad = size.minDimension * 0.2f
        drawLine(tint, Offset(pad, pad), Offset(size.width - pad, size.height - pad), strokeWidth = 2f.toPx())
        drawLine(tint, Offset(size.width - pad, pad), Offset(pad, size.height - pad), strokeWidth = 2f.toPx())
    }
}

@Composable
private fun IconHistory(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.minDimension / 2.4f
        drawCircle(tint, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f.toPx()))
        drawLine(tint, Offset(cx, cy), Offset(cx, cy - radius * 0.55f), strokeWidth = 2f.toPx())
        drawLine(tint, Offset(cx, cy), Offset(cx + radius * 0.4f, cy), strokeWidth = 2f.toPx())
    }
}

private fun SearchFilter.displayName(): String = when (this) {
    SearchFilter.ALL -> "All"
    SearchFilter.SONGS -> "Songs"
    SearchFilter.VIDEOS -> "Videos"
    SearchFilter.ARTISTS -> "Artists"
    SearchFilter.ALBUMS -> "Albums"
    SearchFilter.PLAYLISTS -> "Playlists"
}
