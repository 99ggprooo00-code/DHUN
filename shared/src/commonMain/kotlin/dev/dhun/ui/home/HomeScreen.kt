package dev.dhun.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
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
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.AlbumCard
import dev.dhun.design.components.ArtistCard
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunAssistChip
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.SectionShimmer
import dev.dhun.design.components.TrackCard
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.HomeShelfKind
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel

/**
 * Home — Material 3, deep scroll (not a 3-row stub).
 *
 * Layout top → bottom:
 * 1. Brand + greeting
 * 2. Quick-action chips (Liked / Offline / Sleep timer)
 * 3. Mood & genre filter chips (from feed shelf titles + defaults)
 * 4. Quick Picks responsive grid
 * 5. Listen again (history)
 * 6. Rediscover / mix shelf (when present)
 * 7. Charts & trending shelves
 * 8. Recommended albums & EPs
 * 9. Remaining InnerTube shelves
 *
 * Typography is clean sans only; brand wordmark uses [DhunTypographyTokens.brand].
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onTrackOverflow: (Track) -> Unit = {},
    onOpenLiked: () -> Unit = {},
    onOpenOffline: () -> Unit = {},
    sleepTimerLabel: String? = null,
    onCycleSleepTimer: () -> Unit = {},
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
                    onOpenLiked = onOpenLiked,
                    onOpenOffline = onOpenOffline,
                    sleepTimerLabel = sleepTimerLabel,
                    onCycleSleepTimer = onCycleSleepTimer,
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
    onOpenLiked: () -> Unit,
    onOpenOffline: () -> Unit,
    sleepTimerLabel: String?,
    onCycleSleepTimer: () -> Unit,
) {
    val classified = remember(feed.sections) {
        feed.sections.map { it to GetHomeFeedUseCase.classifySection(it.title) }
    }
    val moodTitles = remember(classified) {
        classified.filter { it.second == HomeShelfKind.MOOD }.map { it.first.title }
    }
    val moodChips = remember(moodTitles) {
        (listOf("For you") + moodTitles.take(6) + listOf("Focus", "Chill", "Workout", "Party"))
            .distinct()
            .take(10)
    }
    var selectedMood by remember { mutableStateOf("For you") }

    val mixSections = classified.filter { it.second == HomeShelfKind.MIX }.map { it.first }
    val chartSections = classified.filter { it.second == HomeShelfKind.CHARTS }.map { it.first }
    val albumSections = classified.filter { it.second == HomeShelfKind.ALBUMS }.map { it.first }
    val otherSections = classified
        .filter {
            it.second == HomeShelfKind.OTHER || it.second == HomeShelfKind.MOOD
        }
        .map { it.first }
        // Drop pure "quick picks" shelf if already shown as grid
        .filterNot { GetHomeFeedUseCase.classifySection(it.title) == HomeShelfKind.QUICK_PICKS }

    // Mood filter: when a named mood chip matches a shelf title, pin that shelf first.
    val orderedOther = remember(selectedMood, otherSections) {
        if (selectedMood == "For you") {
            otherSections
        } else {
            val match = otherSections.filter {
                it.title.contains(selectedMood, ignoreCase = true)
            }
            (match + otherSections.filterNot { it in match }).distinctBy { it.title }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DhunSpacing.contentBottomInset),
    ) {
        // ---- Brand + greeting -------------------------------------------------
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
                        style = DhunTypographyTokens.brand,
                        color = DhunColors.accent,
                    )
                    Text(
                        text = feed.greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = DhunColors.textPrimary,
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

        // ---- Quick-action chips -----------------------------------------------
        item(key = "quick_actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DhunSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                DhunAssistChip(
                    onClick = onOpenLiked,
                    label = {
                        Text("Liked songs", style = MaterialTheme.typography.labelLarge)
                    },
                    leadingIcon = {
                        DhunIconView(
                            icon = DhunIcon.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            tint = DhunColors.accent,
                        )
                    },
                )
                DhunAssistChip(
                    onClick = onOpenOffline,
                    label = {
                        Text("Offline", style = MaterialTheme.typography.labelLarge)
                    },
                    leadingIcon = {
                        DhunIconView(
                            icon = DhunIcon.Offline,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            tint = DhunColors.textSecondary,
                        )
                    },
                )
                DhunAssistChip(
                    onClick = onCycleSleepTimer,
                    label = {
                        Text(
                            sleepTimerLabel ?: "Sleep timer",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    leadingIcon = {
                        DhunIconView(
                            icon = DhunIcon.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            tint = if (sleepTimerLabel != null) DhunColors.accent else DhunColors.textSecondary,
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(DhunSpacing.md))
        }

        // ---- Mood & genre chips -----------------------------------------------
        item(key = "mood_chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                items(moodChips, key = { it }) { chip ->
                    DhunFilterChip(
                        selected = selectedMood == chip,
                        onClick = { selectedMood = chip },
                        label = {
                            Text(chip, style = MaterialTheme.typography.labelLarge)
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(DhunSpacing.sm))
        }

        // ---- Quick picks (responsive grid, deeper) ----------------------------
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

        // ---- Listen again -----------------------------------------------------
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "listen_again_header") {
                SectionHeader(
                    title = "Listen again",
                    modifier = Modifier.padding(top = DhunSpacing.lg),
                )
            }
            item(key = "listen_again_row") {
                HorizontalShelf {
                    itemsIndexed(recentlyPlayed, key = { _, t -> "hist_${t.id}" }) { index, track ->
                        TrackCard(
                            track = track,
                            onClick = { onTrackClick(track, recentlyPlayed, index) },
                        )
                    }
                }
            }
        }

        // ---- Rediscover / mixes -----------------------------------------------
        mixSections.forEach { section ->
            item(key = "mix_${section.title}") {
                HomeSectionBlock(
                    section = section,
                    onTrackClick = onTrackClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    fallbackTitle = "Rediscover your mix",
                )
            }
        }

        // ---- Charts & trending ------------------------------------------------
        chartSections.forEach { section ->
            item(key = "chart_${section.title}") {
                HomeSectionBlock(
                    section = section,
                    onTrackClick = onTrackClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                )
            }
        }

        // ---- Albums & EPs -----------------------------------------------------
        albumSections.forEach { section ->
            item(key = "album_${section.title}") {
                HomeSectionBlock(
                    section = section,
                    onTrackClick = onTrackClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    fallbackTitle = "Recommended albums & EPs",
                )
            }
        }

        // ---- Remaining dynamic shelves (mood-filtered order) ------------------
        orderedOther.forEach { section ->
            // Skip if already rendered under mix/chart/album
            val kind = GetHomeFeedUseCase.classifySection(section.title)
            if (kind == HomeShelfKind.MIX || kind == HomeShelfKind.CHARTS || kind == HomeShelfKind.ALBUMS) {
                return@forEach
            }
            item(key = "other_${section.title}") {
                HomeSectionBlock(
                    section = section,
                    onTrackClick = onTrackClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                )
            }
        }
    }
}

@Composable
private fun HomeSectionBlock(
    section: HomeSection,
    onTrackClick: (track: Track, contextQueue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    fallbackTitle: String? = null,
) {
    Column(modifier = Modifier.padding(top = DhunSpacing.lg)) {
        SectionHeader(title = fallbackTitle?.takeIf { section.title.isBlank() } ?: section.title)
        if (!section.subtitle.isNullOrBlank()) {
            Text(
                text = section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textTertiary,
                modifier = Modifier.padding(
                    horizontal = DhunSpacing.screenPadding,
                    vertical = DhunSpacing.xs,
                ),
            )
        }
        HorizontalShelf {
            itemsIndexed(section.items) { index, item ->
                when (item) {
                    is HomeItem.TrackItem -> TrackCard(
                        track = item.track,
                        onClick = { onTrackClick(item.track, section.tracks, index) },
                    )
                    is HomeItem.AlbumItem -> AlbumCard(
                        album = item.album,
                        onClick = { onAlbumClick(item.album) },
                    )
                    is HomeItem.PlaylistItem -> PlaylistCard(
                        playlist = item.playlist,
                        onClick = { onPlaylistClick(item.playlist) },
                    )
                    is HomeItem.ArtistItem -> ArtistCard(
                        artist = item.artist,
                        onClick = { onArtistClick(item.artist) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalShelf(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
        modifier = Modifier.padding(top = DhunSpacing.sm),
        content = content,
    )
}

/** Responsive Quick Picks: 2-row columns, up to 12 tracks. */
@Composable
private fun QuickPicksGrid(
    tracks: List<Track>,
    onTrackClick: (Track, Int) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    val chunked = tracks.take(12).chunked(2)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DhunShapes.large)
            .background(DhunColors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        ArtworkImage(
            imageUrl = track.thumbnailUrl,
            contentDescription = track.title,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            shape = DhunShapes.medium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = DhunColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
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
        LoadingShimmer(modifier = Modifier.width(DhunSpacing.skeletonTextWidth).height(DhunSpacing.xl))
        Spacer(modifier = Modifier.height(DhunSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
            LoadingShimmer(modifier = Modifier.width(DhunSpacing.quickPickWidth).height(DhunSpacing.skeletonCardHeight))
            LoadingShimmer(modifier = Modifier.width(DhunSpacing.quickPickWidth).height(DhunSpacing.skeletonCardHeight))
        }
        Spacer(modifier = Modifier.height(DhunSpacing.xl))
        SectionShimmer()
        Spacer(modifier = Modifier.height(DhunSpacing.xl))
        SectionShimmer()
    }
}
