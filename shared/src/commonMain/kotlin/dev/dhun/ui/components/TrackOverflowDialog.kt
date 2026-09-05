package dev.dhun.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassCard
import dev.dhun.player.DhunPlayer

@Composable
fun TrackOverflowDialog(
    track: Track,
    player: DhunPlayer,
    isFavorite: Boolean,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onNavigateToArtist: ((track: Track) -> Unit)? = null,
    onNavigateToAlbum: ((track: Track) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 380.dp)
                .padding(DhunSpacing.md),
            shape = DhunShapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DhunSpacing.lg),
            ) {
                // Header: Artwork + Title + Artist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    ArtworkImage(
                        imageUrl = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = DhunColors.textPrimary,
                            maxLines = 1,
                        )
                        Text(
                            text = track.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textTertiary,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(DhunSpacing.md))
                HorizontalDivider(color = DhunColors.border)
                Spacer(modifier = Modifier.height(DhunSpacing.xs))

                // Action Items
                OverflowActionRow(
                    icon = DhunIcon.SkipNext,
                    label = "Play next",
                    onClick = {
                        player.addNext(track)
                        onDismiss()
                    },
                )
                OverflowActionRow(
                    icon = DhunIcon.Add,
                    label = "Add to queue",
                    onClick = {
                        player.addToQueue(track)
                        onDismiss()
                    },
                )
                OverflowActionRow(
                    icon = DhunIcon.QueueMusic,
                    label = "Add to playlist…",
                    onClick = {
                        onDismiss()
                        onAddToPlaylist(track)
                    },
                )
                OverflowActionRow(
                    icon = if (isFavorite) DhunIcon.Favorite else DhunIcon.FavoriteBorder,
                    label = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    iconColor = if (isFavorite) DhunColors.accent else DhunColors.textSecondary,
                    onClick = {
                        onToggleFavorite(track)
                        onDismiss()
                    },
                )
                if (track.artistName.isNotBlank() && onNavigateToArtist != null) {
                    OverflowActionRow(
                        icon = DhunIcon.Person,
                        label = "Go to artist (${track.artistName})",
                        onClick = {
                            onDismiss()
                            onNavigateToArtist(track)
                        },
                    )
                }
                if ((!track.albumName.isNullOrBlank() || track.albumId != null) && onNavigateToAlbum != null) {
                    OverflowActionRow(
                        icon = DhunIcon.Album,
                        label = "Go to album (${track.albumName ?: "Album"})",
                        onClick = {
                            onDismiss()
                            onNavigateToAlbum(track)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DhunTextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowActionRow(
    icon: DhunIcon,
    label: String,
    onClick: () -> Unit,
    iconColor: androidx.compose.ui.graphics.Color = DhunColors.textSecondary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = DhunSpacing.sm, horizontal = DhunSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        DhunIconView(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.iconSize),
            tint = iconColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
        )
    }
}
