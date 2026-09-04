package dev.dhun.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.AlbumCard
import dev.dhun.design.components.ArtistCard
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.TrackCard
import dev.dhun.design.components.TrackRow
import dev.dhun.design.components.TrackRowCompact

/**
 * Phase 07 — shared HomeScreen.
 * Shows greeting, quick picks (recently played), and YTM home sections.
 * Uses the design system throughout; shimmer skeletons while loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading && state.sections.isEmpty() -> HomeLoadingContent()
            state.error != null && state.sections.isEmpty() -> HomeErrorContent(
                message = state.error!!,
                onRetry = viewModel::load,
            )
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                HomeContent(
                    state = state,
                    favorites = favorites,
                    viewModel = viewModel,
                    onNavigateToSearch = onNavigateToSearch,
                    onSearchQuery = onSearchQuery,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeViewModel.UiState,
    favorites: Set<String>,
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit,
    onSearchQuery: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DhunSpacing.bottomNavHeight + DhunSpacing.huge),
    ) {
        // Greeting
        item {
            Text(
                text = state.greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = DhunColors.textPrimary,
                modifier = Modifier.padding(
                    start = DhunSpacing.screenPadding,
                    end = DhunSpacing.screenPadding,
                    top = DhunSpacing.xxl,
                    bottom = DhunSpacing.lg,
                ),
            )
        }

        // Quick picks — "Listen again" from history (most important user action)
        if (state.recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Listen again",
                    modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding),
                )
            }
            item {
                QuickPicksRow(
                    tracks = state.recentlyPlayed,
                    onClick = viewModel::onQuickPickClick,
                )
                Spacer(Modifier.height(DhunSpacing.xxl))
            }
        }

        // Recent searches as chips
        if (state.recentSearches.isNotEmpty()) {
            item {
                RecentSearchChips(
                    queries = state.recentSearches,
                    onQuery = onSearchQuery,
                )
                Spacer(Modifier.height(DhunSpacing.lg))
            }
        }

        // Home sections
        itemsIndexed(state.sections) { sectionIndex, section ->
            HomeSectionRow(
                section = section,
                sectionIndex = sectionIndex,
                viewModel = viewModel,
                favorites = favorites,
            )
            Spacer(Modifier.height(DhunSpacing.xxl))
        }
    }
}

@Composable
private fun QuickPicksRow(
    tracks: List<Track>,
    onClick: (Track) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        items(tracks, key = { it.id }) { track ->
            QuickPickCard(track = track, onClick = { onClick(track) })
        }
    }
}

@Composable
private fun QuickPickCard(
    track: Track,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(DhunColors.glass, DhunShapes.large)
            .padding(end = DhunSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.dhun.design.components.ArtworkImage(
            imageUrl = track.thumbnailUrl,
            contentDescription = track.title,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(DhunSpacing.md))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentSearchChips(
    queries: List<String>,
    onQuery: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        items(queries) { query ->
            dev.dhun.design.components.SearchChip(
                label = query,
                onClick = { onQuery(query) },
            )
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    sectionIndex: Int,
    viewModel: HomeViewModel,
    favorites: Set<String>,
) {
    Column {
        SectionHeader(
            title = section.title,
        )
        when {
            // Track rows (single-column, e.g. "Listen again" radio rows)
            section.items.all { it is HomeItem.TrackItem } &&
                section.items.size <= 6 -> {
                // Dense track list
                Column(modifier = Modifier.padding(horizontal = DhunSpacing.sm)) {
                    section.items.forEach { item ->
                        if (item is HomeItem.TrackItem) {
                            TrackRowCompact(
                                track = item.track,
                                onClick = { viewModel.onTrackClick(item.track) },
                            )
                        }
                    }
                }
            }
            // Grid of albums / artists / playlists
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    items(section.items) { item ->
                        when (item) {
                            is HomeItem.TrackItem -> TrackCard(
                                track = item.track,
                                onClick = { viewModel.onTrackClick(item.track) },
                            )
                            is HomeItem.AlbumItem -> AlbumCard(
                                album = item.album,
                                onClick = { viewModel.onHomeItemClick(item) },
                            )
                            is HomeItem.ArtistItem -> ArtistCard(
                                artist = item.artist,
                                onClick = { viewModel.onHomeItemClick(item) },
                            )
                            is HomeItem.PlaylistItem -> PlaylistCard(
                                playlist = item.playlist,
                                onClick = { viewModel.onHomeItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLoadingContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = DhunSpacing.xxl, start = DhunSpacing.screenPadding),
    ) {
        item {
            LoadingShimmer(
                modifier = Modifier.height(32.dp).width(180.dp),
                shape = DhunShapes.small,
            )
            Spacer(Modifier.height(DhunSpacing.xxl))
        }
        items(4) {
            LoadingShimmer(
                modifier = Modifier.height(120.dp).fillMaxWidth(),
                shape = DhunShapes.large,
            )
            Spacer(Modifier.height(DhunSpacing.xxl))
        }
    }
}

@Composable
private fun HomeErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    ErrorView(
        message = message,
        onRetry = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

private val DhunShapes = dev.dhun.design.DhunShapes
