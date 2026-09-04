package dev.dhun.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.Playlist
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing

@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DhunSpacing.artworkCard + DhunSpacing.lg)
            .clickable(onClick = onClick)
            .padding(DhunSpacing.xs),
    ) {
        ArtworkImage(
            imageUrl = track.thumbnailUrl,
            contentDescription = track.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = DhunSpacing.sm),
        )
        Text(
            track.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick)
            .padding(DhunSpacing.xs),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        ArtistArtworkImage(
            imageUrl = artist.thumbnailUrl,
            contentDescription = artist.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = DhunSpacing.sm),
        )
        artist.subscriberCountText?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DhunSpacing.artworkCard + DhunSpacing.lg)
            .clickable(onClick = onClick)
            .padding(DhunSpacing.xs),
    ) {
        ArtworkImage(
            imageUrl = album.thumbnailUrl,
            contentDescription = album.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            album.title,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = DhunSpacing.sm),
        )
        Text(
            album.artistName ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textTertiary,
            maxLines = 1,
        )
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(DhunSpacing.artworkCard + DhunSpacing.lg)
            .clickable(onClick = onClick)
            .padding(DhunSpacing.xs),
    ) {
        ArtworkImage(
            imageUrl = playlist.thumbnailUrl,
            contentDescription = playlist.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            playlist.title,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = DhunSpacing.sm),
        )
        Text(
            playlist.trackCountText ?: playlist.authorName ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textTertiary,
            maxLines = 1,
        )
    }
}


