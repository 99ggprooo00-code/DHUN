package dev.dhun.ui.home

import dev.dhun.design.DhunTypographyTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.HomeFeed
import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Playlist
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.AlbumCard
import dev.dhun.design.components.ArtistCard
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.SectionShimmer
import dev.dhun.design.components.TrackCard
import dev.dhun.design.components.TrackRowShimmer
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onTrackOverflow: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                HomeShimmerSkeleton(modifier = Modifier.fillMaxSize())
            }
            is HomeUiState.Error -> {
                ErrorView(
                    message = state.message,
                    title = "Could not load Home",
                    onRetry = { viewModel.load() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is HomeUiState.Empty -> {
                EmptyView(
                    title = "Nothing to display",
                    message = "Could not find any music recommendations right now.",
                    actionLabel = "Retry",
                    onAction = { viewModel.load() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is HomeUiState.Success -> {
                HomeFeedContent(
                    feed = state.feed,
                    recentlyPlayed = recentlyPlayed,
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
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

@Composable
private fun HomeFeedContent(
    feed: HomeFeed,
    recentlyPlayed: List<Track>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DhunSpacing.contentBottomInset),
    ) {
        // Top Header: Greeting + Refresh
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "DHUN",
                        style = MaterialTheme.typography.labelSmall,
                        color = DhunColors.accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = DhunTypographyTokens.brand.letterSpacing,
                    )
                    Text(
                        text = feed.greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = DhunColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                DhunIconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    contentDescription = if (isRefreshing) "Refreshing" else "Refresh home",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.textSecondary,
                    )
                }
            }
        }

        // Quick Picks Section (3x2 grid of tracks)
        if (feed.quickPicks.isNotEmpty()) {
            item(key = "quick_picks_header") {
                SectionHeader(
                    title = "Quick picks",
                    modifier = Modifier.padding(top = DhunSpacing.sm),
                )
            }
            item(key = "quick_picks_grid") {
                QuickPicksGrid(
                    tracks = feed.quickPicks,
                    onTrackClick = { track, index ->
                        onTrackClick(track, feed.quickPicks, index)
                    },
                    onTrackOverflow = onTrackOverflow,
                )
            }
        }

        // Listen Again Section (from Local History)
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "listen_again_header") {
                SectionHeader(
                    title = "Listen again",
                    modifier = Modifier.padding(top = DhunSpacing.lg),
                )
            }
            item(key = "listen_again_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    itemsIndexed(recentlyPlayed, key = { _, track -> "history_${track.id}" }) { index, track ->
                        TrackCard(
                            track = track,
                            onClick = { onTrackClick(track, recentlyPlayed, index) },
                        )
                    }
                }
            }
        }

        // Dynamic Shelves / Sections from InnerTube
        items(feed.sections, key = { it.title }) { section ->
            Column(modifier = Modifier.padding(top = DhunSpacing.lg)) {
                SectionHeader(
                    title = section.title,
                )
                if (!section.subtitle.isNullOrBlank()) {
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.textTertiary,
                        modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs),
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                    modifier = Modifier.padding(top = DhunSpacing.sm),
                ) {
                    itemsIndexed(section.items) { index, item ->
                        when (item) {
                            is HomeItem.TrackItem -> {
                                TrackCard(
                                    track = item.track,
                                    onClick = { onTrackClick(item.track, section.tracks, index) },
                                )
                            }
                            is HomeItem.AlbumItem -> {
                                AlbumCard(
                                    album = item.album,
                                    onClick = { onAlbumClick(item.album) },
                                )
                            }
                            is HomeItem.PlaylistItem -> {
                                PlaylistCard(
                                    playlist = item.playlist,
                                    onClick = { onPlaylistClick(item.playlist) },
                                )
                            }
                            is HomeItem.ArtistItem -> {
                                ArtistCard(
                                    artist = item.artist,
                                    onClick = { onArtistClick(item.artist) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 3x2 Quick Picks grid using compact glassy cards. */
@Composable
private fun QuickPicksGrid(
    tracks: List<Track>,
    onTrackClick: (Track, Int) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    val chunked = tracks.take(6).chunked(2) // 3 columns, 2 rows each
    LazyRow(
        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        items(chunked) { columnTracks ->
            Column(
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                modifier = Modifier.width(DhunSpacing.dialogMinWidth),
            ) {
                columnTracks.forEach { track ->
                    val originalIndex = tracks.indexOf(track)
                    QuickPickItem(
                        track = track,
                        onClick = { onTrackClick(track, originalIndex) },
                        onOverflow = { onTrackOverflow(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPickItem(
    track: Track,
    onClick: () -> Unit,
    onOverflow: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = DhunShapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            ArtworkImage(
                imageUrl = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.size(DhunSpacing.touchTarget),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DhunColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.labelSmall,
                    color = DhunColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DhunIconButton(
                onClick = onOverflow,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "More actions for ${track.title}",
            ) {
                DhunIconView(
                    icon = DhunIcon.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textTertiary,
                )
            }
        }
    }
}

/** Loading skeleton without spinners. */
@Composable
private fun HomeShimmerSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DhunSpacing.screenPadding),
    ) {
        LoadingShimmer(modifier = Modifier.width(DhunSpacing.bottomNavHeight).height(DhunSpacing.lg))
        Spacer(modifier = Modifier.height(DhunSpacing.xs))
        LoadingShimmer(modifier = Modifier.width(DhunSpacing.dialogListHeight).height(DhunSpacing.mediumLarge))
        Spacer(modifier = Modifier.height(DhunSpacing.xl))

        // Quick picks skeleton (2 cards)
        LoadingShimmer(modifier = Modifier.width(DhunSpacing.skeletonTextWidth).height(DhunSpacing.xl))
        Spacer(modifier = Modifier.height(DhunSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
            LoadingShimmer(modifier = Modifier.width(DhunSpacing.quickPickWidth).height(DhunSpacing.skeletonCardHeight))
            LoadingShimmer(modifier = Modifier.width(DhunSpacing.quickPickWidth).height(DhunSpacing.skeletonCardHeight))
        }

        Spacer(modifier = Modifier.height(DhunSpacing.xl))
        SectionShimmer()
    }
}
