package dev.dhun.ui.browse

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.ArtistPage
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
import dev.dhun.design.components.DhunTonalButton
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.SectionShimmer
import dev.dhun.design.components.TrackRow
import dev.dhun.presentation.browse.ArtistUiState
import dev.dhun.presentation.browse.ArtistViewModel

/**
 * Artist page (Phase 09): parallax artwork header that collapses into a
 * glass toolbar on scroll, shuffle/radio actions, top songs (ordered list,
 * plays as queue), albums & singles carousels, related artists, about card.
 */
@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel,
    onBack: () -> Unit,
    onTrackPlay: (track: Track, queue: List<Track>, index: Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val radioLoading by viewModel.radioLoading.collectAsState()
    val listState = rememberLazyListState()
    val toolbarCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DhunColors.background)) {
        when (val s = state) {
            is ArtistUiState.Loading -> ArtistSkeleton()
            is ArtistUiState.Error -> ErrorView(
                title = "Could not load artist",
                message = s.message,
                onRetry = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            )
            is ArtistUiState.Success -> ArtistContent(
                page = s.page,
                listState = listState,
                radioLoading = radioLoading,
                onShuffle = { viewModel.playTopSongsShuffled() },
                onRadio = { viewModel.startRadio() },
                onTrackPlay = onTrackPlay,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick,
                onTrackOverflow = onTrackOverflow,
            )
        }

        // Collapse-on-scroll glass toolbar
        val title = (state as? ArtistUiState.Success)?.page?.artist?.name.orEmpty()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DhunSpacing.huge),
        ) {
            if (toolbarCollapsed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DhunColors.glassStrong),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DhunSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Back",
                ) {
                    DhunIconView(
                        icon = DhunIcon.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (toolbarCollapsed) 1f else 0f),
                )
            }
        }
    }
}

@Composable
private fun ArtistContent(
    page: ArtistPage,
    listState: LazyListState,
    radioLoading: Boolean,
    onShuffle: () -> Unit,
    onRadio: () -> Unit,
    onTrackPlay: (Track, List<Track>, Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Parallax header artwork + name
        item(key = "header") {
            val collapseShift = with(listState) {
                if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset else 0
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
            ) {
                ArtworkImage(
                    imageUrl = page.artist.thumbnailUrl,
                    contentDescription = page.artist.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // parallax: header artwork drifts slower than scroll
                            translationY = collapseShift * 0.35f
                        },
                    shape = RectangleShape,
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    DhunColors.background.copy(alpha = 0.15f),
                                    DhunColors.background.copy(alpha = 0.05f),
                                    DhunColors.background,
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(DhunSpacing.screenPadding),
                ) {
                    Text(
                        text = page.artist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = DhunColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    page.monthlyListeners?.let { listeners ->
                        Text(
                            text = listeners,
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textSecondary,
                        )
                    }
                }
            }
        }

        // Actions: Shuffle / Radio
        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                DhunTonalButton(onClick = onShuffle, enabled = page.topSongs.isNotEmpty()) {
                    DhunIconView(
                        icon = DhunIcon.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    )
                    Spacer(modifier = Modifier.width(DhunSpacing.xs))
                    Text("Shuffle")
                }
                DhunTonalButton(onClick = onRadio, enabled = !radioLoading && page.topSongs.isNotEmpty()) {
                    if (radioLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(DhunSpacing.iconSizeSm), strokeWidth = DhunSpacing.iconStroke)
                    } else {
                        DhunIconView(
                            icon = DhunIcon.Play,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        )
                    }
                    Spacer(modifier = Modifier.width(DhunSpacing.xs))
                    Text("Radio")
                }
            }
        }

        // Top songs
        if (page.topSongs.isNotEmpty()) {
            item(key = "top_songs_header") {
                SectionHeader(title = "Top songs")
            }
            itemsIndexed(page.topSongs.take(5), key = { i, t -> "top_${i}_${t.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackPlay(track, page.topSongs, index) },
                    onOverflowClick = { onTrackOverflow(track) },
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = DhunColors.textHint,
                                modifier = Modifier.padding(end = DhunSpacing.xs),
                            )
                            TrackOverflowIcon(
                                trackTitle = track.title,
                                onClick = { onTrackOverflow(track) },
                            )
                        }
                    },
                )
            }
        }

        // Albums
        if (page.albums.isNotEmpty()) {
            item(key = "albums") {
                Column {
                    SectionHeader(title = "Albums")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                    ) {
                        items(page.albums, key = { "album_${it.id}" }) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }

        // Singles
        if (page.singles.isNotEmpty()) {
            item(key = "singles") {
                Column {
                    SectionHeader(title = "Singles & EPs")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                    ) {
                        items(page.singles, key = { "single_${it.id}" }) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }

        // Featured playlists
        if (page.featuredPlaylists.isNotEmpty()) {
            item(key = "featured") {
                Column {
                    SectionHeader(title = "Featured on")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                    ) {
                        items(page.featuredPlaylists, key = { "feat_${it.id}" }) { playlist ->
                            PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                        }
                    }
                }
            }
        }

        // Related artists
        if (page.relatedArtists.isNotEmpty()) {
            item(key = "related") {
                Column {
                    SectionHeader(title = "Fans might also like")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = DhunSpacing.screenPadding),
                        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                    ) {
                        items(page.relatedArtists, key = { "rel_${it.id}" }) { artist ->
                            ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                        }
                    }
                }
            }
        }

        // About
        if (!page.description.isNullOrBlank()) {
            item(key = "about") {
                Column {
                    SectionHeader(title = "About")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DhunSpacing.screenPadding)
                            .padding(bottom = DhunSpacing.xxxl),
                        shape = DhunShapes.card,
                    ) {
                        Text(
                            text = page.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DhunColors.textSecondary,
                            modifier = Modifier.padding(DhunSpacing.cardPadding),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(DhunSpacing.xxxl))
            }
        }
    }
}

@Composable
private fun TrackOverflowIcon(trackTitle: String, onClick: () -> Unit) {
    DhunIconButton(
        onClick = onClick,
        modifier = Modifier.size(DhunSpacing.touchTarget),
        contentDescription = "More actions for $trackTitle",
    ) {
        DhunIconView(
            icon = DhunIcon.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.iconSize),
            tint = DhunColors.textTertiary,
        )
    }
}

@Composable
private fun ArtistSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        LoadingShimmer(modifier = Modifier.fillMaxWidth().height(DhunSpacing.artistHeaderHeight))
        Spacer(modifier = Modifier.height(DhunSpacing.md))
        LoadingShimmer(modifier = Modifier.width(DhunSpacing.skeletonArtistWidth).height(DhunSpacing.xl).padding(horizontal = DhunSpacing.screenPadding))
        SectionShimmer()
        SectionShimmer()
    }
}
