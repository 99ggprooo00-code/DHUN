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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunOutlinedButton
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.presentation.browse.PlaylistUiState
import dev.dhun.presentation.browse.PlaylistViewModel
import dev.dhun.ui.components.DragHandleGrip
import dev.dhun.ui.components.ReorderableList

/**
 * Playlist page (Phase 09) — remote (YTM) or local (SQLDelight).
 * Remote: header + ordered tracks + play/shuffle.
 * Local: all of that + rename, delete, swipe-remove, drag-reorder.
 */
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onTrackPlay: (track: Track, queue: List<Track>, index: Int) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val deleted by viewModel.deleted.collectAsState()

    LaunchedEffect(deleted) { if (deleted) onDeleted() }

    Box(modifier = modifier.fillMaxSize().background(DhunColors.background)) {
        when (val s = state) {
            is PlaylistUiState.Loading -> PlaylistSkeleton()
            is PlaylistUiState.Error -> ErrorView(
                title = "Could not load playlist",
                message = s.message,
                onRetry = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            )
            is PlaylistUiState.Remote -> RemotePlaylistContent(
                detail = s.detail,
                onPlayAll = { viewModel.play(0) },
                onShuffle = { viewModel.playShuffled() },
                onTrackPlay = onTrackPlay,
                onTrackOverflow = onTrackOverflow,
            )
            is PlaylistUiState.Local -> {
                val playlist = s.playlist
                if (playlist == null) {
                    EmptyView(
                        title = "Playlist deleted",
                        message = "This local playlist no longer exists.",
                        actionLabel = "Back",
                        onAction = onBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LocalPlaylistContent(
                        title = playlist.name,
                        description = playlist.description,
                        tracks = s.tracks,
                        viewModel = viewModel,
                        onPlayAll = { viewModel.play(0) },
                        onShuffle = { viewModel.playShuffled() },
                        onTrackPlay = onTrackPlay,
                    )
                }
            }
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

/* ---------------- remote (YTM) --------------------------------------------- */

@Composable
private fun RemotePlaylistContent(
    detail: PlaylistDetail,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTrackPlay: (Track, List<Track>, Int) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DhunSpacing.xxxl),
    ) {
        item(key = "header") {
            PlaylistHeader(
                title = detail.title,
                subtitle = detail.authorName ?: "YouTube Music",
                meta = detail.trackCountText,
                description = detail.description,
                thumbnailUrl = detail.thumbnailUrl,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                trackCount = detail.tracks.size,
            )
        }
        if (detail.tracks.isEmpty()) {
            item(key = "empty") {
                EmptyView(title = "No tracks", message = "This playlist page did not include tracks.")
            }
        } else {
            itemsIndexed(detail.tracks, key = { i, t -> "pl_${i}_${t.id}" }) { index, track ->
                RemoteTrackRow(
                    index = index,
                    track = track,
                    onClick = { onTrackPlay(track, detail.tracks, index) },
                    onOverflow = { onTrackOverflow(track) },
                )
            }
        }
    }
}

@Composable
private fun RemoteTrackRow(index: Int, track: Track, onClick: () -> Unit, onOverflow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
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

/* ---------------- local (editable) ------------------------------------------ */

@Composable
private fun LocalPlaylistContent(
    title: String,
    description: String?,
    tracks: List<Track>,
    viewModel: PlaylistViewModel,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTrackPlay: (Track, List<Track>, Int) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PlaylistHeader(
            title = title,
            subtitle = "Local playlist",
            meta = "${tracks.size} track${if (tracks.size == 1) "" else "s"}",
            description = description,
            thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl,
            onPlayAll = onPlayAll,
            onShuffle = onShuffle,
            trackCount = tracks.size,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
                    DhunOutlinedButton(onClick = { showRename = true }) { Text("Rename") }
                    DhunOutlinedButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                }
            },
        )

        if (tracks.isEmpty()) {
            EmptyView(
                title = "Empty playlist",
                message = "Add tracks from search or a queue's overflow menu.",
                modifier = Modifier.weight(1f),
            )
        } else {
            ReorderableList(
                items = tracks,
                onMove = { from, to -> viewModel.moveTrack(from, to) },
                onSwipeRemove = { _, track -> viewModel.removeTrack(track) },
                onItemClick = { index, _ -> onTrackPlay(tracks[index], tracks, index) },
                modifier = Modifier.weight(1f),
            ) { _, track, dragHandle, _, _ ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = DhunSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    ArtworkImage(
                        imageUrl = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier.size(DhunSpacing.compactTarget),
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
                            text = track.artistName,
                            style = MaterialTheme.typography.labelSmall,
                            color = DhunColors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(modifier = dragHandle) { DragHandleGrip() }
                }
            }
        }
    }

    if (showRename) {
        RenameDialog(
            current = title,
            onDismiss = { showRename = false },
            onConfirm = { newName ->
                viewModel.rename(newName)
                showRename = false
            },
        )
    }
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            playlistName = title,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { viewModel.delete(); showDeleteConfirm = false },
        )
    }
}

/* ---------------- shared header + dialogs ----------------------------------- */

@Composable
private fun PlaylistHeader(
    title: String,
    subtitle: String,
    meta: String?,
    description: String?,
    thumbnailUrl: String?,
    trackCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DhunSpacing.huge)
            .padding(horizontal = DhunSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            imageUrl = thumbnailUrl,
            contentDescription = title,
            modifier = Modifier.size(DhunSpacing.artworkPlaylist),
            shape = DhunShapes.extraLarge,
        )
        Spacer(modifier = Modifier.height(DhunSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = DhunColors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(subtitle)
                meta?.let { append(" • $it") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = DhunSpacing.xs),
            )
        }
        Spacer(modifier = Modifier.height(DhunSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
            DhunButton(onClick = onPlayAll, enabled = trackCount > 0) {
                DhunIconView(
                    icon = DhunIcon.Play,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.xs))
                Text("Play")
            }
            DhunOutlinedButton(onClick = onShuffle, enabled = trackCount > 0) {
                DhunIconView(
                    icon = DhunIcon.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.xs))
                Text("Shuffle")
            }
        }
        actions?.let {
            Spacer(modifier = Modifier.height(DhunSpacing.sm))
            it()
        }
        Spacer(modifier = Modifier.height(DhunSpacing.md))
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(current) { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth), shape = DhunShapes.large) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text(
                    "Rename playlist",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.weight(1f))
                    DhunButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth), shape = DhunShapes.large) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text(
                    "Delete \"$playlistName\"?",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                Text(
                    "This removes the local playlist. Tracks stay in your favorites/history if saved there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.weight(1f))
                    DhunButton(onClick = onConfirm) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(DhunSpacing.huge))
        LoadingShimmer(modifier = Modifier.size(DhunSpacing.artworkPlaylist))
        Spacer(modifier = Modifier.height(DhunSpacing.lg))
        LoadingShimmer(modifier = Modifier.fillMaxWidth(0.5f).height(DhunSpacing.xxl).padding(horizontal = DhunSpacing.xxl))
        Spacer(modifier = Modifier.height(DhunSpacing.xxl))
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingShimmer(modifier = Modifier.size(DhunSpacing.touchTarget))
                Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                    LoadingShimmer(modifier = Modifier.width(DhunSpacing.artworkPlaylist).height(DhunSpacing.mdPlus))
                    LoadingShimmer(modifier = Modifier.width(DhunSpacing.skeletonTextWidth).height(DhunSpacing.md))
                }
            }
        }
    }
}
