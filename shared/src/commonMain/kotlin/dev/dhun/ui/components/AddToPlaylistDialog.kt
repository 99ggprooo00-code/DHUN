package dev.dhun.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.dhun.core.Track
import dev.dhun.data.PlaylistRepository
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunTextField
import dev.dhun.design.components.DhunTextButton
import kotlinx.coroutines.launch

/**
 * Local-playlist picker for one track.
 *
 * **This is part of the ⋮ menu family, not a second dialog system.** It used to
 * be the last old-Material surface left in the app: a `GlassCard` at 280–400dp
 * with double padding, an M3 `titleLarge` header, a **fixed 180dp-tall** list box
 * (empty space under two playlists, a scroll box for a two-row list), an M3
 * `OutlinedTextField` for the new-playlist name, and a Close/Cancel button row —
 * none of which matched the compact frosted menus the rest of the app moved to.
 * The device report ("still the old Material 3 interface… an unusually
 * long/oversized box") is that surface.
 *
 * What it draws now, using the shared pieces rather than new ones:
 *
 * - [TrackMenuSurface] — the track's own blurred artwork under the lyrics veil,
 *   on an opaque base, sized to its content (the surface that fixed the same
 *   stretch bug for the ⋮ menu);
 * - [TrackMenuHeader] — the identical "which track is this" header as the ⋮
 *   menu, so the two surfaces read as one family;
 * - one caption line saying what this surface does, then one [MenuActionRow] per
 *   playlist (name + track count + the "Open" breadcrumb) and an "New playlist"
 *   row — no divider, no Close button, no M3 button row;
 * - a **content-sized** picker list ([AddToPlaylistPolicy.listMaxHeight]): it
 *   reserves nothing for rows that do not exist, and scrolls only past four;
 * - a DHUN-styled single-line input instead of M3's outlined field.
 *
 * Behaviour is unchanged: same repository calls, same "create & add" flow, same
 * blank-name error, same `onAdded` / `onOpenPlaylist` callbacks, and tap-outside
 * / Back still dismisses.
 */
@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlistRepository: PlaylistRepository,
    onDismiss: () -> Unit,
    onAdded: (playlistName: String) -> Unit,
    /** Phase 09: breadcrumb into the local playlist page after adding. */
    onOpenPlaylist: ((playlistId: String) -> Unit)? = null,
) {
    val playlists by playlistRepository.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val addToPlaylist: (String, String) -> Unit = { playlistId, name ->
        scope.launch {
            playlistRepository.addTrack(playlistId, track)
            onAdded(name)
            onDismiss()
        }
    }
    val createAndAdd: () -> Unit = {
        val name = newPlaylistName.trim()
        if (name.isBlank()) {
            errorText = "Name cannot be empty"
        } else {
            scope.launch {
                val playlist = playlistRepository.create(name)
                playlistRepository.addTrack(playlist.id, track)
                onAdded(playlist.name)
                onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        TrackMenuSurface(
            artworkUrl = track.thumbnailUrl,
            modifier = Modifier
                // A dialog window clips at its content bounds, so the surface
                // keeps a margin for its own shadow. The width budget comes
                // after it: the same menu ceiling as the ⋮ menu.
                .padding(DhunSpacing.sm)
                .widthIn(
                    min = DhunSpacing.menuMinWidth,
                    max = DhunSpacing.menuMaxWidth,
                ),
        ) {
            TrackMenuHeader(track = track)
            Text(
                text = if (isCreatingNew) "New playlist" else "Add to playlist",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.accent,
                modifier = Modifier.padding(
                    start = DhunSpacing.mdPlus,
                    end = DhunSpacing.mdPlus,
                    bottom = DhunSpacing.xs,
                ),
            )

            if (isCreatingNew) {
                DhunTextField(
                    value = newPlaylistName,
                    onValueChange = {
                        newPlaylistName = it
                        errorText = null
                    },
                    modifier = Modifier.padding(horizontal = DhunSpacing.mdPlus),
                    placeholder = "Playlist name",
                    isError = errorText != null,
                    onSubmit = createAndAdd,
                )
                errorText?.let {
                    Text(
                        text = it,
                        color = DhunColors.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(
                            start = DhunSpacing.mdPlus,
                            top = DhunSpacing.xs,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhunSpacing.mdPlus, vertical = DhunSpacing.smPlus),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    DhunTextButton(onClick = { isCreatingNew = false }) {
                        Text("Back")
                    }
                    Spacer(modifier = Modifier.size(DhunSpacing.sm))
                    DhunButton(onClick = createAndAdd) {
                        Text("Create & add")
                    }
                }
            } else {
                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists yet — create one below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.textTertiary,
                        modifier = Modifier.padding(
                            horizontal = DhunSpacing.mdPlus,
                            vertical = DhunSpacing.sm,
                        ),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Sized to the rows it actually has: the old sheet
                            // reserved a fixed 180dp box whether it held one
                            // playlist or ten.
                            .heightIn(max = AddToPlaylistPolicy.listMaxHeight(playlists.size)),
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            // Local copy: the breadcrumb needs a smart-castable
                            // reference to build its click lambda.
                            val openPlaylist = onOpenPlaylist
                            MenuActionRow(
                                label = playlist.name,
                                icon = DhunIcon.QueueMusic,
                                supportingText = "${playlist.trackCount} tracks",
                                onClick = { addToPlaylist(playlist.id, playlist.name) },
                                trailingLabel = if (openPlaylist != null) "Open" else null,
                                onTrailingClick = openPlaylist?.let { open -> { open(playlist.id) } },
                            )
                        }
                    }
                }
                MenuActionRow(
                    label = "New playlist",
                    icon = DhunIcon.Add,
                    onClick = { isCreatingNew = true },
                )
            }
        }
    }
}

/**
 * The picker's height budget, as numbers — so the "it reserved a box it did not
 * need" defect stays fixed in CI instead of in a screenshot.
 *
 * The list is measured to its rows up to [VISIBLE_ROWS]; past that it scrolls.
 * The row height is the menu row's, because these rows *are* menu rows.
 */
internal object AddToPlaylistPolicy {

    /** Playlists visible before the picker starts scrolling. */
    const val VISIBLE_ROWS = 4

    /**
     * Tallest the picker list may be for [rowCount] playlists.
     *
     * Zero playlists means no list at all (`0.dp`) — the empty case draws a
     * sentence, not an empty box. The result never exceeds
     * [DhunSpacing.dialogListHeight], the fixed box this replaced.
     */
    fun listMaxHeight(
        rowCount: Int,
        rowHeight: Dp = DhunSpacing.menuRowHeight,
        ceiling: Dp = DhunSpacing.dialogListHeight,
    ): Dp {
        if (rowCount <= 0) return 0.dp
        if (!rowHeight.value.isFinite() || rowHeight <= 0.dp) return 0.dp
        val rows = rowCount.coerceAtMost(VISIBLE_ROWS)
        return (rowHeight * rows).coerceAtMost(if (ceiling > 0.dp) ceiling else rowHeight * rows)
    }
}
