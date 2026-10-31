package dev.dhun.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dhun.core.Track
import dev.dhun.design.ArtworkUrls
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * TrackRow — list workhorse (Search, Queue, History, Favorites).
 *
 * Standard neutral cell when [frosted] (default) so artwork and titles, not
 * repeated glass gradients, define the list. The false branch is for rows
 * inside an existing surface. Spacing stays airy on both platforms.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArtist: Boolean = true,
    onOverflowClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    frosted: Boolean = true,
) {
    val rowModifier = if (frosted) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs)
            .dhunContentRowSurface()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.smPlus)
    } else {
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        ArtworkImage(
            imageUrl = track.thumbnailUrl,
            fallbackImageUrl = ArtworkUrls.videoThumbnail(track.id),
            contentDescription = track.title,
            modifier = Modifier.size(DhunSpacing.artworkThumb),
            shape = DhunShapes.medium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) DhunColors.textPrimary else DhunColors.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showArtist) {
                Text(
                    text = buildString {
                        append(track.artistName)
                        track.albumName?.let { append(" • $it") }
                        track.durationSeconds?.let { append(" • ${formatSeconds(it)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) DhunColors.textTertiary else DhunColors.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onOverflowClick != null) {
            DhunIconButton(
                onClick = onOverflowClick,
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

@Composable
fun TrackRowCompact(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dhunContentRowSurface()
            .clickable(onClick = onClick)
            .padding(horizontal = DhunSpacing.sm, vertical = DhunSpacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        ArtworkImage(
            imageUrl = track.thumbnailUrl,
            fallbackImageUrl = ArtworkUrls.videoThumbnail(track.id),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            shape = DhunShapes.small,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistName,
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSeconds(total: Int): String = "%d:%02d".format(total / 60, total % 60)
