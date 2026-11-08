package dev.dhun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.dhun.core.Track
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlaylistRepository
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunOutlinedButton
import dev.dhun.design.components.DhunTextField
import dev.dhun.design.components.DhunTextButton
import dev.dhun.presentation.MutationTracker

/**
 * Destination picker for adding one track to a local playlist.
 *
 * It shares the artwork-aware track header/material with the compact overflow
 * menu so the song remains identifiable, but the body is a destination list,
 * not another list of track actions: each playlist is its own add target,
 * opening a playlist is a distinct secondary action, and creation has a
 * separate full-width affordance/form. The repository flow and all mutation,
 * duplicate, retry, and dismissal rules below remain result-driven.
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
    // This is a cold SQLDelight flow. Remember it across animated dialog
    // recompositions; otherwise each recomposition can restart collection at
    // the empty initial value and the picker never shows existing playlists.
    val playlistFlow = remember(playlistRepository) { playlistRepository.observePlaylists() }
    // Read in this parent composition, not only inside Dialog's separate
    // popup composition. An emission then recomposes the parent and passes a
    // fresh snapshot to the popup instead of leaving its initial empty list.
    val playlists = playlistFlow.collectAsState(initial = emptyList()).value
    val scope = rememberCoroutineScope()
    // Result-driven, like every other mutation in the app: `onAdded` and
    // `onDismiss` run only after the write returned normally, a failure is
    // shown inside this sheet (it used to escape the coroutine as a crash), and
    // a second tap while the write is in flight is ignored.
    val tracker = remember { MutationTracker(scope) }
    val newPlaylistAction = remember { NewPlaylistAddAction(playlistRepository, tracker) }
    val mutation by tracker.state.collectAsState()
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val failureText = mutation.errorMessage ?: errorText

    val addToPlaylist: (String, String) -> Unit = add@{ playlistId, name ->
        // Read the tracker directly as well as its Compose snapshot: two
        // different rows tapped in one frame must not run concurrently.
        if (tracker.state.value.isBusy) return@add
        errorText = null
        // The repository tells us WHICH thing happened. Reporting "added" for a
        // track that was already there is the same class of false confirmation
        // this sheet used to give on a failed write: say so, keep the sheet open
        // so the user can pick another playlist, and only close on a real add.
        var alreadyThere = false
        tracker.run(
            key = "add:$playlistId",
            failureMessage = "Could not add this song to \"$name\". Try again.",
            onSuccess = {
                if (alreadyThere) {
                    errorText = "Already in \"$name\"."
                } else {
                    onAdded(name)
                    onDismiss()
                }
            },
        ) {
            alreadyThere = !playlistRepository.addTrack(playlistId, track)
        }
    }
    val createAndAdd: () -> Unit = create@{
        if (tracker.state.value.isBusy) return@create
        val name = newPlaylistName.trim()
        if (name.isBlank()) {
            errorText = "Name cannot be empty"
        } else {
            errorText = null
            newPlaylistAction.run(track, name) { createdName ->
                onAdded(createdName)
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!tracker.state.value.isBusy) {
                newPlaylistAction.abandonPendingCreation()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            val surfaceMaxWidth = minOf(DhunSpacing.dialogWideMaxWidth, maxWidth)
            val surfaceMaxHeight = (maxHeight - DhunSpacing.md).coerceAtLeast(DhunSpacing.zero)
            val trackHeaderHeight = DhunSpacing.menuArtwork + DhunSpacing.sm * 3 + DhunSpacing.smPlus * 2
            TrackMenuSurface(
                artworkUrl = track.thumbnailUrl,
                modifier = Modifier
                    .widthIn(min = minOf(DhunSpacing.menuMinWidth, surfaceMaxWidth), max = surfaceMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = surfaceMaxHeight),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    TrackMenuHeader(track = track)
                    val formScrollModifier = if (isCreatingNew) {
                        Modifier
                            .heightIn(
                                max = (surfaceMaxHeight - trackHeaderHeight).coerceAtLeast(DhunSpacing.zero),
                            )
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.mdPlus)
                            .then(formScrollModifier),
                        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                    ) {
                        Text(
                            text = if (isCreatingNew) "New playlist" else "Add to playlist",
                            style = MaterialTheme.typography.titleMedium,
                            color = DhunColors.textPrimary,
                        )
                        Text(
                            text = if (isCreatingNew) {
                                "This song will be added after the playlist is created."
                            } else {
                                "Choose a destination for this song."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textSecondary,
                        )

                        if (isCreatingNew) {
                            Text(
                                text = "Playlist name",
                                style = MaterialTheme.typography.labelMedium,
                                color = DhunColors.textSecondary,
                            )
                            DhunTextField(
                                value = newPlaylistName,
                                onValueChange = {
                                    // Once the playlist row exists, a retry keeps its name
                                    // and ID; editing must not turn that retry into a create.
                                    if (!newPlaylistAction.hasPendingCreation && !mutation.isBusy) {
                                        newPlaylistName = it
                                        errorText = null
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = "New playlist name" },
                                placeholder = "Playlist name",
                                isError = failureText != null,
                                enabled = !newPlaylistAction.hasPendingCreation && !mutation.isBusy,
                                onSubmit = createAndAdd,
                            )
                            if (newPlaylistAction.hasPendingCreation) {
                                Text(
                                    "Playlist created. Retrying will add this song to that same playlist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DhunColors.textSecondary,
                                )
                            }
                            failureText?.let {
                                PlaylistPickerError(text = it)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.xs, bottom = DhunSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                DhunTextButton(
                                    onClick = {
                                        newPlaylistAction.abandonPendingCreation()
                                        tracker.dismissError()
                                        isCreatingNew = false
                                        newPlaylistName = ""
                                        errorText = null
                                    },
                                    enabled = !mutation.isBusy,
                                ) {
                                    Text("Back")
                                }
                                Spacer(modifier = Modifier.size(DhunSpacing.sm))
                                DhunButton(
                                    onClick = createAndAdd,
                                    enabled = newPlaylistName.isNotBlank() && !mutation.isBusy,
                                    loading = mutation.isRunning(NewPlaylistAddAction.KEY),
                                ) {
                                    Text(if (failureText != null) "Try again" else "Create & add")
                                }
                            }
                        } else {
                            failureText?.let { PlaylistPickerError(text = it) }
                            if (playlists.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(DhunShapes.medium)
                                        .background(DhunColors.surfaceContainerLow)
                                        .padding(DhunSpacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    DhunIconView(
                                        icon = DhunIcon.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                                        tint = DhunColors.textTertiary,
                                    )
                                    Text(
                                        text = "No playlists yet — create one below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DhunColors.textSecondary,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(
                                            max = AddToPlaylistPolicy.listMaxHeight(
                                                rowCount = playlists.size,
                                                rowHeight = DhunSpacing.playlistDestinationRowHeight,
                                                ceiling = DhunSpacing.playlistDestinationListMaxHeight,
                                            ),
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                                ) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        val openPlaylist = onOpenPlaylist
                                        PlaylistDestinationRow(
                                            playlist = playlist,
                                            enabled = !mutation.isBusy,
                                            onAdd = { addToPlaylist(playlist.id, playlist.name) },
                                            onOpen = openPlaylist?.let { open -> { open(playlist.id) } },
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = DhunColors.border)
                            DhunOutlinedButton(
                                onClick = {
                                    errorText = null
                                    isCreatingNew = true
                                },
                                enabled = !mutation.isBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DhunIconView(
                                    icon = DhunIcon.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                                    tint = DhunColors.accent,
                                )
                                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                                Text("Create new playlist")
                            }
                            Spacer(modifier = Modifier.size(DhunSpacing.xs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDestinationRow(
    playlist: LocalPlaylist,
    enabled: Boolean,
    onAdd: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = DhunSpacing.playlistDestinationRowMinHeight)
            .clip(DhunShapes.medium)
            .background(DhunColors.surfaceContainerLow)
            .padding(horizontal = DhunSpacing.smPlus, vertical = DhunSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f)
                .heightIn(min = DhunSpacing.playlistDestinationActionMinHeight)
                .clip(DhunShapes.small)
                .clickable(
                    enabled = enabled,
                    onClickLabel = "Add to ${playlist.name}",
                    role = Role.Button,
                    onClick = onAdd,
                )
                .semantics { contentDescription = "Add this song to ${playlist.name}" }
                .padding(horizontal = DhunSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(DhunSpacing.menuArtwork)
                    .clip(DhunShapes.small)
                    .background(DhunColors.accentContainer),
                contentAlignment = Alignment.Center,
            ) {
                DhunIconView(
                    icon = DhunIcon.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.accent,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DhunColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DhunColors.textTertiary,
                    maxLines = 1,
                )
            }
        }
        onOpen?.let { open ->
            DhunTextButton(
                onClick = open,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Open ${playlist.name}" },
            ) {
                Text("Open", maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlaylistPickerError(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(DhunShapes.medium)
            .background(DhunColors.errorContainer)
            .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DhunIconView(
            icon = DhunIcon.Error,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.iconSizeSm),
            tint = DhunColors.error,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.error,
        )
    }
}

/**
 * Operation-specific state for Create & add. If playlist creation succeeds but
 * the membership write fails, a retry reuses that row instead of creating a
 * duplicate. Leaving the form abandons only this retry state; the visible
 * playlist is preserved and can still be chosen from the picker.
 */
internal class NewPlaylistAddAction(
    private val playlists: PlaylistRepository,
    private val tracker: MutationTracker,
) {
    private var pendingCreation: LocalPlaylist? = null
    val hasPendingCreation: Boolean get() = pendingCreation != null

    fun run(track: Track, name: String, onAdded: (String) -> Unit): Boolean {
        if (tracker.state.value.isBusy) return false
        var createdName = pendingCreation?.name ?: name
        return tracker.run(
            key = KEY,
            failureMessage = "Could not finish the new playlist and add this song. Try again.",
            onSuccess = {
                pendingCreation = null
                onAdded(createdName)
            },
        ) {
            val playlist = pendingCreation ?: playlists.create(name).also { pendingCreation = it }
            createdName = playlist.name
            playlists.addTrack(playlist.id, track)
        }
    }

    fun abandonPendingCreation() {
        if (!tracker.state.value.isBusy) pendingCreation = null
    }

    companion object {
        const val KEY = "create-and-add"
    }
}

internal object AddToPlaylistPolicy {

    /** Playlists visible before the picker starts scrolling. */
    const val VISIBLE_ROWS = 4

    /**
     * Height budget for the destination rows. Zero playlists means no list at
     * all (`DhunSpacing.zero`) — the empty case draws a sentence, not an empty box. Past
     * [VISIBLE_ROWS] items, the list scrolls within the supplied ceiling.
     */
    fun listMaxHeight(
        rowCount: Int,
        rowHeight: Dp = DhunSpacing.menuRowHeight,
        ceiling: Dp = DhunSpacing.dialogListHeight,
    ): Dp {
        if (rowCount <= 0) return DhunSpacing.zero
        if (!rowHeight.value.isFinite() || rowHeight <= DhunSpacing.zero) return DhunSpacing.zero
        val rows = rowCount.coerceAtMost(VISIBLE_ROWS)
        return (rowHeight * rows).coerceAtMost(if (ceiling > DhunSpacing.zero) ceiling else rowHeight * rows)
    }
}
