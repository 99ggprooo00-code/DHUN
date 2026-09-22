package dev.dhun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.LyricsMaterial
import dev.dhun.player.DhunPlayer

/**
 * The one ⋮ menu of the app.
 *
 * Every overflow affordance — full player, playlist / album / artist pages,
 * Home, Search and Library — is mounted once from `DhunAppShell`, so all of
 * them open *this*. The queue row's ⋮ is an anchored Material 3 `DropdownMenu`
 * (a popup, not a dialog) and keeps its own actions, but it draws into the same
 * [TrackMenuSurface] and [MenuActionRow] below rather than being a second menu
 * system.
 *
 * It used to be a centered Material **sheet**: the old opaque GlassCard at
 * 280–380dp, double padding, a 52dp header, a divider, ~48dp action rows and
 * its own Close button — a long black slab on both Android and Windows. What
 * ships now is a compact menu:
 *
 * - **frosted artwork material** — the track's own cover, blurred once, under
 *   the lyrics-card veil ([LyricsMaterial]), on an opaque `surface` base so the
 *   dimmed page cannot read through the labels;
 * - **sized to its actions** — Material's 280dp menu ceiling, 44dp rows, one
 *   line of padding, no divider and no Close button (tap outside or Back
 *   dismisses, which is what [onDismiss] is for);
 * - **the same actions and behaviour as before**, in the same order, with the
 *   same visibility rules — see `TrackMenuPolicy`. Only the labels got short:
 *   "Download" and "Go to artist" no longer carry the parenthetical target
 *   name, because the menu header already says which track and which artist
 *   this is, and a long label is what stretched the old sheet.
 */
@Composable
fun TrackOverflowDialog(
    track: Track,
    player: DhunPlayer,
    onAddToPlaylist: (Track) -> Unit,
    onNavigateToArtist: ((track: Track) -> Unit)? = null,
    onNavigateToAlbum: ((track: Track) -> Unit)? = null,
    onDownload: ((Track) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        TrackMenuSurface(
            artworkUrl = track.thumbnailUrl,
            modifier = Modifier
                // A dialog window clips at its content bounds, so the surface
                // keeps a margin for its own shadow. The width constraint comes
                // after it: the menu is Material's 280dp ceiling, not the
                // window's width.
                .padding(DhunSpacing.sm)
                .widthIn(
                    min = DhunSpacing.menuMinWidth,
                    max = DhunSpacing.menuMaxWidth,
                ),
        ) {
            TrackMenuHeader(track = track)
            val actions = TrackMenuPolicy.actionsFor(
                track = track,
                canDownload = onDownload != null,
                canNavigateToArtist = onNavigateToArtist != null,
                canNavigateToAlbum = onNavigateToAlbum != null,
            )
            val run: (TrackMenuAction) -> Unit = { action ->
                // Order preserved from the sheet this replaced: queue actions
                // dismiss after mutating, navigation/dialog actions dismiss
                // first so the next surface never animates in under this one.
                when (action) {
                    TrackMenuAction.PLAY_NEXT -> {
                        player.addNext(track)
                        onDismiss()
                    }
                    TrackMenuAction.ADD_TO_QUEUE -> {
                        player.addToQueue(track)
                        onDismiss()
                    }
                    TrackMenuAction.ADD_TO_PLAYLIST -> {
                        onDismiss()
                        onAddToPlaylist(track)
                    }
                    TrackMenuAction.DOWNLOAD -> {
                        onDismiss()
                        onDownload?.invoke(track)
                    }
                    TrackMenuAction.GO_TO_ARTIST -> {
                        onDismiss()
                        onNavigateToArtist?.invoke(track)
                    }
                    TrackMenuAction.GO_TO_ALBUM -> {
                        onDismiss()
                        onNavigateToAlbum?.invoke(track)
                    }
                }
            }
            actions.forEach { action ->
                MenuActionRow(
                    icon = action.icon,
                    label = action.label,
                    onClick = { run(action) },
                )
            }
        }
    }
}

/* ---------------- the menu itself ------------------------------------------- */

/**
 * Every action the ⋮ menu can offer, in display order, with the label and
 * glyph it renders. Pure data, so `TrackMenuPolicyTest` pins the menu instead
 * of leaving it to a screenshot.
 */
internal enum class TrackMenuAction(val label: String, val icon: DhunIcon) {
    PLAY_NEXT("Play next", DhunIcon.SkipNext),
    ADD_TO_QUEUE("Add to queue", DhunIcon.Add),
    ADD_TO_PLAYLIST("Add to playlist", DhunIcon.QueueMusic),
    DOWNLOAD("Download", DhunIcon.Download),
    GO_TO_ARTIST("Go to artist", DhunIcon.Person),
    GO_TO_ALBUM("Go to album", DhunIcon.Album),
}

/**
 * Which actions a given track's menu shows. Unchanged from the sheet this
 * replaced — the same three always-on queue actions, then the optional ones:
 *
 * - **Download** only exists where the host supplied a download manager;
 * - **Go to artist** needs a non-blank artist name (the shell searches for it
 *   when the row carried no `artistId`, so a name is enough);
 * - **Go to album** needs an album id *or* a name, for the same reason.
 *
 * (Favorite/like is deliberately absent: it lives as a dedicated button beside
 * Shuffle in the FullPlayer transport.)
 */
internal object TrackMenuPolicy {

    fun actionsFor(
        track: Track,
        canDownload: Boolean,
        canNavigateToArtist: Boolean,
        canNavigateToAlbum: Boolean,
    ): List<TrackMenuAction> = buildList {
        add(TrackMenuAction.PLAY_NEXT)
        add(TrackMenuAction.ADD_TO_QUEUE)
        add(TrackMenuAction.ADD_TO_PLAYLIST)
        if (canDownload) add(TrackMenuAction.DOWNLOAD)
        if (canNavigateToArtist && track.artistName.isNotBlank()) add(TrackMenuAction.GO_TO_ARTIST)
        if (canNavigateToAlbum && (!track.albumName.isNullOrBlank() || track.albumId != null)) {
            add(TrackMenuAction.GO_TO_ALBUM)
        }
    }
}

/**
 * The frosted panel every ⋮ menu draws into — the shared surface for the
 * track overflow menu and the queue row's anchored Material 3 `DropdownMenu`,
 * so there is one menu look rather than two.
 *
 * Recipe: the track's own artwork blurred once under the lyrics-card veil
 * ([LyricsMaterial]), on an opaque [DhunColors.surface] base with a soft
 * shadow. The base matters: a menu floats over a dimmed scrim (or over an
 * anchored popup), and the veil alone — or nothing at all, when the track has
 * no artwork or the platform cannot really blur — would let the page read
 * straight through the labels. Same rule as `GlassCard(opaqueBase = true)`.
 *
 * Callers bound the width; the surface wraps its height to the rows it is
 * given, which is what keeps a menu a menu instead of a sheet.
 */
@Composable
internal fun TrackMenuSurface(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    LyricsMaterial(
        artworkUrl = artworkUrl,
        modifier = modifier
            .shadow(
                DhunSpacing.md,
                shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
            )
            .background(DhunColors.surface, shape),
        shape = shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DhunSpacing.smPlus),
            content = content,
        )
    }
}

/**
 * Compact menu header: which track this menu is for. A thumbnail, one line of
 * title, one line of artist — not the 52dp banner with a divider under it that
 * made the old sheet read as a page.
 */
@Composable
internal fun TrackMenuHeader(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.mdPlus, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        ArtworkImage(
            imageUrl = track.thumbnailUrl,
            // The title and artist beside it already label this artwork.
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.menuArtwork),
            shape = DhunShapes.small,
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
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One compact menu row: glyph + label, [DhunSpacing.menuRowHeight] tall, full
 * width so the whole row is the touch target.
 *
 * [destructive] is the queue's "Remove from queue"; [enabled] greys out a row
 * that cannot run (the first row's "Move up", the last row's "Move down").
 */
@Composable
internal fun MenuActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DhunIcon? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val labelColor = when {
        !enabled -> DhunColors.textDisabled
        destructive -> DhunColors.error
        else -> DhunColors.textPrimary
    }
    val iconColor = when {
        !enabled -> DhunColors.textDisabled
        destructive -> DhunColors.error
        else -> DhunColors.textSecondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DhunSpacing.menuRowHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = DhunSpacing.mdPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        if (icon != null) {
            DhunIconView(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = iconColor,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
