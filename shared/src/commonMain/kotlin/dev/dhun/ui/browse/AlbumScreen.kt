package dev.dhun.ui.browse

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.AlbumDetail
import dev.dhun.core.Artist
import dev.dhun.core.Track
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Brush
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunOutlinedButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.TrackRow
import dev.dhun.presentation.browse.AlbumUiState
import dev.dhun.presentation.browse.AlbumViewModel

/**
 * Album page (Phase 09): artwork-tinted header, play/shuffle actions,
 * ordered numbered track list, "More by artist" navigation.
 */
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onBack: () -> Unit,
    onTrackPlay: (track: Track, queue: List<Track>, index: Int) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(DhunColors.background)) {
        when (val s = state) {
            is AlbumUiState.Loading -> AlbumSkeleton()
            is AlbumUiState.Error -> ErrorView(
                title = "Could not load album",
                message = s.message,
                onRetry = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            )
            is AlbumUiState.Success -> AlbumContent(
                detail = s.detail,
                onPlayAll = { viewModel.play(0) },
                onShuffle = { viewModel.playShuffled() },
                onTrackPlay = onTrackPlay,
                onArtistClick = onArtistClick,
                onTrackOverflow = onTrackOverflow,
            )
        }

        // Floating back
        Box(modifier = Modifier.padding(DhunSpacing.xs)) {
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
        }
    }
}

@Composable
private fun AlbumContent(
    detail: AlbumDetail,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTrackPlay: (Track, List<Track>, Int) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    val colors = remember(detail.thumbnailUrl, detail.id) {
        ArtworkColorExtractor.extractFromSeed(detail.thumbnailUrl ?: detail.id)
    }
    val headerTint by animateColorAsState(
        targetValue = colors.primary.copy(alpha = 0.30f),
        animationSpec = DhunAnimations.slowTween(),
        label = "albumHeaderTint",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DhunSpacing.xxxl),
    ) {
        // Header: big artwork + meta over the artwork-tinted gradient
        item(key = "header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(headerTint, DhunColors.background),
                        ),
                    )
                    .padding(top = DhunSpacing.huge, bottom = DhunSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtworkImage(
                        imageUrl = detail.thumbnailUrl,
                        contentDescription = detail.title,
                        modifier = Modifier.size(DhunSpacing.artworkAlbum),
                        shape = DhunShapes.extraLarge,
                    )
                    Spacer(modifier = Modifier.height(DhunSpacing.lg))
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = DhunColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = DhunSpacing.xxl),
                    )
                    Text(
                        text = buildString {
                            append(detail.artistName ?: "")
                            detail.year?.let { append(" • $it") }
                        }.trim().ifBlank { "Album" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textSecondary,
                    )
                    val metaLine = listOfNotNull(detail.trackCountText, detail.durationText)
                        .joinToString(" • ")
                    if (metaLine.isNotBlank()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = DhunColors.textTertiary,
                            modifier = Modifier.padding(top = DhunSpacing.xs),
                        )
                    }
                    Spacer(modifier = Modifier.height(DhunSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
                        DhunButton(onClick = onPlayAll, enabled = detail.tracks.isNotEmpty()) {
                            DhunIconView(
                                icon = DhunIcon.Play,
                                contentDescription = null,
                                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            )
                            Spacer(modifier = Modifier.width(DhunSpacing.xs))
                            Text("Play")
                        }
                        DhunOutlinedButton(onClick = onShuffle, enabled = detail.tracks.isNotEmpty()) {
                            DhunIconView(
                                icon = DhunIcon.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            )
                            Spacer(modifier = Modifier.width(DhunSpacing.xs))
                            Text("Shuffle")
                        }
                    }
                }
            }
        }

        // Ordered numbered track list
        if (detail.tracks.isEmpty()) {
            item(key = "empty") {
                EmptyView(
                    title = "No tracks found",
                    message = "This album page did not include a track list.",
                )
            }
        } else {
            itemsIndexed(detail.tracks, key = { i, t -> "album_${i}_${t.id}" }) { index, track ->
                AlbumTrackRow(
                    number = index + 1,
                    track = track,
                    onClick = { onTrackPlay(track, detail.tracks, index) },
                    onOverflow = { onTrackOverflow(track) },
                )
            }
        }

        // More by artist
        if (!detail.artistName.isNullOrBlank() || detail.moreByArtistBrowseId != null) {
            item(key = "more_by") {
                Column {
                    SectionHeader(title = "More by ${detail.artistName ?: "artist"}")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DhunSpacing.screenPadding),
                    ) {
                        DhunOutlinedButton(
                            onClick = {
                                detail.artistId?.let { id ->
                                    onArtistClick(
                                        Artist(
                                            id = id,
                                            name = detail.artistName ?: "",
                                        ),
                                    )
                                }
                            },
                            enabled = detail.artistId != null,
                        ) {
                            Text("Artist page")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    number: Int,
    track: Track,
    onClick: () -> Unit,
    onOverflow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DhunShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%2d".format(number),
            style = MaterialTheme.typography.labelMedium,
            color = DhunColors.textHint,
            modifier = Modifier.width(DhunSpacing.xxxl),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = DhunColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(track.artistName)
                    track.durationSeconds?.let { append(" • %d:%02d".format(it / 60, it % 60)) }
                },
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

@Composable
private fun AlbumSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(DhunSpacing.huge))
        LoadingShimmer(modifier = Modifier.size(DhunSpacing.artworkAlbum))
        Spacer(modifier = Modifier.height(DhunSpacing.lg))
        LoadingShimmer(modifier = Modifier.width(DhunSpacing.artworkPlaylist).height(DhunSpacing.xxl))
        Spacer(modifier = Modifier.height(DhunSpacing.sm))
        LoadingShimmer(modifier = Modifier.width(DhunSpacing.skeletonMetaWidth).height(DhunSpacing.mdPlus))
        Spacer(modifier = Modifier.height(DhunSpacing.xxl))
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingShimmer(modifier = Modifier.width(DhunSpacing.xxxl).height(DhunSpacing.mdPlus))
                Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                    LoadingShimmer(modifier = Modifier.width(DhunSpacing.artworkAlbum).height(DhunSpacing.mdPlus))
                    LoadingShimmer(modifier = Modifier.width(DhunSpacing.skeletonTextWidth).height(DhunSpacing.md))
                }
            }
        }
    }
}
