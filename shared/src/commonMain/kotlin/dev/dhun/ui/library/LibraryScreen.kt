package dev.dhun.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.HistoryEntry
import dev.dhun.core.Track
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlayContext
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunOutlinedButton
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.domain.HistoryDay
import dev.dhun.presentation.library.DownloadsListUi
import dev.dhun.presentation.library.LibraryTab
import dev.dhun.presentation.library.LibraryViewModel
import dev.dhun.presentation.library.currentUtcOffsetMs
import dev.dhun.presentation.library.toTrack
import dev.dhun.ui.components.DragHandleGrip
import dev.dhun.ui.components.ReorderableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Library screen — Phase 10 (updated).
 *
 * Liked Songs are neatly organized inside a dedicated pinned folder card
 * right under the Playlists section, removing the separated top-level Liked/Favorites tab.
 *
 * Tabs:
 *  1. Playlists (containing the Liked Songs dedicated folder + user playlists)
 *  2. History (grouped by day)
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onTrackOverflow: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val viewingLikedSongs by viewModel.viewingLikedSongs.collectAsState()
    val playlists by viewModel.playlistsFlow.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val groupedHistory by viewModel.groupedHistory.collectAsState()
    val downloadsUi by viewModel.downloadsForUi.collectAsState()
    val storageSummary by viewModel.storageSummary.collectAsState()

    // Keep day grouping fresh on zone changes (cheap ticker)
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshHistoryGrouping(currentUtcOffsetMs())
            delay(60_000)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header — brand wordmark + sans headline (M3 readable type)
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md),
        ) {
            Text(
                text = "DHUN",
                style = DhunTypographyTokens.brand,
                color = DhunColors.accent,
            )
            Text(
                text = "Your library",
                style = MaterialTheme.typography.headlineMedium,
                color = DhunColors.textPrimary,
            )
        }

        LibraryTabRow(
            selectedTab = selectedTab,
            onSelect = viewModel::selectTab,
            modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        )

        Spacer(modifier = Modifier.height(DhunSpacing.sm))

        when (selectedTab) {
            LibraryTab.PLAYLISTS -> {
                if (viewingLikedSongs) {
                    LikedSongsDetailView(
                        favorites = favorites,
                        onBack = { viewModel.closeLikedSongs() },
                        onPlayTrack = viewModel::playFavoritesTrack,
                        onPlayAll = { viewModel.playFavorites(0) },
                        onRemove = viewModel::removeFavorite,
                        onTrackOverflow = onTrackOverflow,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PlaylistsTab(
                        favoritesCount = favorites.size,
                        onOpenLikedSongs = { viewModel.openLikedSongs() },
                        onPlayLikedSongs = { viewModel.playFavorites(0) },
                        playlists = playlists,
                        onPlaylistClick = onPlaylistClick,
                        onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                        onPlayPlaylist = viewModel::playPlaylist,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            LibraryTab.FAVORITES -> {
                LikedSongsDetailView(
                    favorites = favorites,
                    onBack = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                    onPlayTrack = viewModel::playFavoritesTrack,
                    onPlayAll = { viewModel.playFavorites(0) },
                    onRemove = viewModel::removeFavorite,
                    onTrackOverflow = onTrackOverflow,
                    modifier = Modifier.weight(1f),
                )
            }
            LibraryTab.DOWNLOADS -> DownloadsTab(
                downloads = downloadsUi,
                storage = storageSummary,
                onPlay = viewModel::playDownloaded,
                onRemove = viewModel::removeDownload,
                onRemoveBatch = viewModel::removeDownloads,
                onClearAll = viewModel::clearDownloads,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onCancel = viewModel::cancelDownload,
                progressFor = viewModel::progressFor,
                modifier = Modifier.weight(1f),
            )
            LibraryTab.HISTORY -> HistoryTab(
                groupedHistory = groupedHistory,
                onPlayEntry = viewModel::playHistoryEntry,
                onPlayDay = viewModel::playHistoryDay,
                onRemoveEntry = viewModel::removeHistoryEntry,
                onClearAll = viewModel::clearHistory,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LibraryTabRow(
    selectedTab: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleTabs = listOf(LibraryTab.PLAYLISTS, LibraryTab.DOWNLOADS, LibraryTab.HISTORY)
    Row(
        modifier = modifier.fillMaxWidth().height(DhunSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        visibleTabs.forEach { tab ->
            val selected = (tab == selectedTab) || (tab == LibraryTab.PLAYLISTS && selectedTab == LibraryTab.FAVORITES)
            val label = when (tab) {
                LibraryTab.PLAYLISTS -> "Playlists"
                LibraryTab.DOWNLOADS -> "Downloads"
                LibraryTab.HISTORY -> "History"
                LibraryTab.FAVORITES -> "Favorites"
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(DhunShapes.full)
                    .background(
                        if (selected) {
                            DhunColors.accent.copy(alpha = 0.82f)
                        } else {
                            DhunColors.glassHighlight
                        },
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = DhunSpacing.lg, vertical = DhunSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) DhunColors.onAccent else DhunColors.textSecondary,
                )
            }
        }
    }
}

/* ---------------- Playlists tab with Liked Songs Folder --------------------- */

@Composable
private fun PlaylistsTab(
    favoritesCount: Int,
    onOpenLikedSongs: () -> Unit,
    onPlayLikedSongs: () -> Unit,
    playlists: List<LocalPlaylist>,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onCreatePlaylist: suspend (String) -> LocalPlaylist,
    onPlayPlaylist: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = DhunSpacing.xxxl),
        ) {
            // Liked Songs Dedicated Folder Card ------------------------------
            item(key = "liked_songs_folder") {
                LikedSongsFolderCard(
                    trackCount = favoritesCount,
                    onClick = onOpenLikedSongs,
                    onPlay = onPlayLikedSongs,
                    modifier = Modifier.padding(
                        horizontal = DhunSpacing.screenPadding,
                        vertical = DhunSpacing.sm,
                    ),
                )
            }

            // Playlists Section Header ---------------------------------------
            item(key = "playlists_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Your Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        color = DhunColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    DhunOutlinedButton(onClick = { showCreate = true }) {
                        DhunIconView(
                            icon = DhunIcon.Add,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            tint = DhunColors.accent,
                        )
                        Spacer(modifier = Modifier.width(DhunSpacing.xs))
                        Text("New playlist")
                    }
                }
            }

            // Playlist items or empty hint -----------------------------------
            if (playlists.isEmpty()) {
                item(key = "empty_playlists") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No custom playlists yet. Tap 'New playlist' to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textTertiary,
                        )
                    }
                }
            } else {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onPlay = { onPlayPlaylist(playlist.id, 0) },
                    )
                }
            }
        }

        if (showCreate) {
            CreatePlaylistDialog(
                onDismiss = { showCreate = false },
                onConfirm = { showCreate = false },
                onCreate = onCreatePlaylist,
            )
        }
    }
}

/**
 * Dedicated Liked Songs Folder Card — pinned at the top of the Playlists tab.
 */
@Composable
private fun LikedSongsFolderCard(
    trackCount: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = DhunShapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            DhunColors.accentContainer.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(DhunSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(DhunSpacing.artworkThumb)
                    .clip(DhunShapes.medium)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                DhunColors.accent,
                                DhunColors.accentContainer,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                DhunIconView(
                    icon = DhunIcon.Favorite,
                    contentDescription = "Liked Songs",
                    modifier = Modifier.size(DhunSpacing.iconSizeLg),
                    tint = DhunColors.onAccent,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$trackCount song${if (trackCount == 1) "" else "s"} • Auto-playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                )
            }

            if (trackCount > 0) {
                DhunIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Play Liked Songs",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Play,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.accent,
                    )
                }
            }
        }
    }
}

/**
 * Detailed Liked Songs playlist view inside the Playlists tab.
 */
@Composable
private fun LikedSongsDetailView(
    favorites: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onRemove: (String) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunIconButton(
                    onClick = onBack,
                    contentDescription = "Back to Playlists",
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
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                )
            }
            EmptyView(
                title = "No liked songs yet",
                message = "Tap the heart on any track to save it to your Liked Songs.",
                modifier = Modifier.weight(1f).padding(DhunSpacing.xxl),
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DhunIconButton(
                onClick = onBack,
                contentDescription = "Back to Playlists",
            ) {
                DhunIconView(
                    icon = DhunIcon.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${favorites.size} song${if (favorites.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DhunColors.textSecondary,
                )
            }
            DhunButton(onClick = onPlayAll) {
                DhunIconView(
                    icon = DhunIcon.Play,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.xs))
                Text("Play all")
            }
        }

        ReorderableList(
            items = favorites,
            onMove = { _, _ -> /* Favorites ordered by addedAt DESC */ },
            onSwipeRemove = { _, track -> onRemove(track.id) },
            onItemClick = { _, track -> onPlayTrack(track) },
            modifier = Modifier.fillMaxSize(),
        ) { _, track, dragHandle, _, _ ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = DhunSpacing.md),
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
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
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
                DhunIconButton(
                    onClick = { onTrackOverflow(track) },
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
                Box(modifier = dragHandle) { DragHandleGrip() }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: LocalPlaylist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs)
            .clickable(onClick = onClick),
        shape = DhunShapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(DhunSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
        ) {
            Box(
                modifier = Modifier.size(DhunSpacing.artworkThumb)
                    .clip(DhunShapes.medium)
                    .background(DhunColors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                DhunIconView(
                    icon = DhunIcon.LibraryMusic,
                    contentDescription = "Playlist",
                    modifier = Modifier.size(DhunSpacing.iconSizeLg),
                    tint = DhunColors.textTertiary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DhunColors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"} • updated ${relativeBrief(playlist.updatedAtEpochMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DhunColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DhunIconButton(
                onClick = onPlay,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "Play ${playlist.name}",
            ) {
                DhunIconView(
                    icon = DhunIcon.Play,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.accent,
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onCreate: suspend (String) -> LocalPlaylist,
) {
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth), shape = DhunShapes.large) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text("New playlist", style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("My playlist") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    DhunButton(
                        onClick = {
                            if (name.isNotBlank() && !creating) {
                                creating = true
                                scope.launch {
                                    try { onCreate(name.trim()) } catch (_: Exception) {}
                                    creating = false
                                    onConfirm(name)
                                }
                            }
                        },
                        enabled = name.isNotBlank() && !creating,
                    ) { Text(if (creating) "Creating…" else "Create") }
                }
            }
        }
    }
}

private fun relativeBrief(epochMs: Long): String {
    val nowMs = dev.dhun.data.EpochClock.System.nowMs()
    return LibraryViewModel.relativeTimeLabel(epochMs, nowMs)
}

/* ---------------- Downloads tab (ADR-006) ----------------------------------- */

private enum class DownloadsView { LIST, MANAGE }

@Composable
private fun DownloadsTab(
    downloads: DownloadsListUi,
    storage: dev.dhun.presentation.library.StorageSummary,
    onPlay: (Track) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveBatch: (Collection<String>) -> Unit,
    onClearAll: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    progressFor: (String) -> kotlinx.coroutines.flow.Flow<dev.dhun.download.DownloadProgress?>,
    modifier: Modifier = Modifier,
) {
    if (downloads.totalCount == 0) {
        EmptyView(
            title = "No downloads yet",
            message = "Use the download action on a track to save it for offline listening.",
            modifier = modifier.fillMaxSize().padding(DhunSpacing.xxl),
        )
        return
    }

    var view by remember { mutableStateOf(DownloadsView.LIST) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showBatchConfirm by remember { mutableStateOf(false) }
    // Selection lives across LIST/MANAGE so batch delete targets the same set.
    val selected = remember { mutableStateOf(setOf<String>()) }
    fun toggle(id: String) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab toolbar: count/size on the left, manage + clear on the right.
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${downloads.totalCount} item${if (downloads.totalCount == 1) "" else "s"} • ${formatBytes(storage.usedByDownloadsBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = DhunColors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
                DhunOutlinedButton(onClick = {
                    view = if (view == DownloadsView.MANAGE) DownloadsView.LIST else DownloadsView.MANAGE
                }) { Text(if (view == DownloadsView.MANAGE) "Done" else "Storage") }
                DhunTextButton(onClick = { showClearConfirm = true }) { Text("Clear all", color = DhunColors.error) }
            }
        }

        if (view == DownloadsView.MANAGE) {
            StorageManageView(
                storage = storage,
                downloads = downloads.all,
                selected = selected.value,
                onToggle = ::toggle,
                onSelectAll = {
                    selected.value = if (selected.value.size == downloads.totalCount) emptySet()
                    else downloads.all.map { it.trackId }.toSet()
                },
                onDeleteSelected = { showBatchConfirm = true },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = DhunSpacing.xxxl),
            ) {
                item(key = "storage_card") {
                    StorageSummaryCard(
                        storage = storage,
                        modifier = Modifier.padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs),
                    )
                }
                if (downloads.active.isNotEmpty()) {
                    item(key = "active_header") {
                        SectionHeader(
                            title = "Active downloads",
                            modifier = Modifier.padding(top = DhunSpacing.xs),
                        )
                    }
                    itemsIndexed(downloads.active, key = { _, d -> d.trackId }) { _, item ->
                        DownloadRow(
                            download = item,
                            progressFlow = progressFor(item.trackId),
                            onPlay = { onPlay(item.toTrack()) },
                            onRemove = { onRemove(item.trackId) },
                            onPause = { onPause(item.trackId) },
                            onResume = { onResume(item.trackId) },
                            onCancel = { onCancel(item.trackId) },
                        )
                    }
                }
                if (downloads.completed.isNotEmpty()) {
                    item(key = "completed_header") {
                        SectionHeader(
                            title = "Downloaded",
                            modifier = Modifier.padding(top = DhunSpacing.xs),
                        )
                    }
                    itemsIndexed(downloads.completed, key = { _, d -> d.trackId }) { _, item ->
                        DownloadRow(
                            download = item,
                            progressFlow = progressFor(item.trackId),
                            onPlay = { onPlay(item.toTrack()) },
                            onRemove = { onRemove(item.trackId) },
                            onPause = { onPause(item.trackId) },
                            onResume = { onResume(item.trackId) },
                            onCancel = { onCancel(item.trackId) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ClearDownloadsConfirmDialog(
            count = downloads.totalCount,
            onDismiss = { showClearConfirm = false },
            onConfirm = { onClearAll(); selected.value = emptySet(); showClearConfirm = false },
        )
    }
    if (showBatchConfirm) {
        DeleteSelectedConfirmDialog(
            count = selected.value.size,
            onDismiss = { showBatchConfirm = false },
            onConfirm = {
                onRemoveBatch(selected.value.toList())
                selected.value = emptySet()
                showBatchConfirm = false
            },
        )
    }
}

/**
 * Storage card: used-by-downloads with the device capacity it sits in.
 * The bar shows the share of the whole volume that downloads occupy; when
 * capacity is unknown we show a used-only figure and no bar.
 */
@Composable
private fun StorageSummaryCard(
    storage: dev.dhun.presentation.library.StorageSummary,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), shape = DhunShapes.large) {
        Column(modifier = Modifier.padding(DhunSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunIconView(
                    icon = DhunIcon.Offline,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeLg),
                    tint = DhunColors.accent,
                )
                Spacer(modifier = Modifier.width(DhunSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "On this device",
                        style = MaterialTheme.typography.titleSmall,
                        color = DhunColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (storage.device != null) {
                            "${formatBytes(storage.usedByDownloadsBytes)} of downloads • ${formatBytes(storage.device.freeBytes)} free"
                        } else {
                            "${formatBytes(storage.usedByDownloadsBytes)} of downloads"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = DhunColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (storage.device != null) {
                    Text(
                        "${formatBytes(storage.device.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DhunColors.textTertiary,
                    )
                }
            }
            if (storage.device != null && storage.device.totalBytes > 0) {
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                // Track: total volume used (everything); accent: share that is downloads.
                val volumeUsed = storage.deviceUsedFraction ?: 0f
                val downloadsShare = storage.downloadsFractionOfDevice ?: 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DhunSpacing.progressStroke * 2)
                        .clip(DhunShapes.full)
                        .background(DhunColors.surfaceElevated),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(volumeUsed)
                            .clip(DhunShapes.full)
                            .background(DhunColors.glassHighlight),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(downloadsShare)
                            .clip(DhunShapes.full)
                            .background(DhunColors.accent),
                    )
                }
            }
        }
    }
}

/**
 * Storage-management view: device capacity, a per-state breakdown, and a
 * checklist for batch delete. Kept inside the Library tab (no shell/nav
 * touchpoints).
 */
@Composable
private fun StorageManageView(
    storage: dev.dhun.presentation.library.StorageSummary,
    downloads: List<DownloadedTrack>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Capacity block -----------------------------------------------------
        GlassCard(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs),
            shape = DhunShapes.large,
        ) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text(
                    "Storage",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                if (storage.device != null) {
                    StorageStatRow("Total space", formatBytes(storage.device.totalBytes))
                    StorageStatRow("Free space", formatBytes(storage.device.freeBytes))
                    val volumeUsed = (storage.device.totalBytes - storage.device.freeBytes).coerceAtLeast(0L)
                    StorageStatRow("Used by apps & system", formatBytes(volumeUsed))
                    Spacer(modifier = Modifier.height(DhunSpacing.sm))
                    LinearProgressIndicator(
                        progress = { storage.deviceUsedFraction ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(DhunSpacing.progressStroke * 2).clip(DhunShapes.full),
                        color = DhunColors.accent,
                        trackColor = DhunColors.surfaceElevated,
                    )
                } else {
                    Text(
                        "Device capacity isn't reported on this platform.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                StorageStatRow("Downloaded audio", formatBytes(storage.usedByDownloadsBytes), emphasize = true)
            }
        }

        // Breakdown by state -------------------------------------------------
        GlassCard(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.xs),
            shape = DhunShapes.large,
        ) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text(
                    "Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    color = DhunColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                storage.groups.forEach { g ->
                    if (g.count > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stateLabel(g.state),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (g.state == DownloadState.FAILED) DhunColors.error else DhunColors.textSecondary,
                            )
                            Text(
                                if (g.bytes > 0) "${g.count} • ${formatBytes(g.bytes)}" else "${g.count}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DhunColors.textTertiary,
                            )
                        }
                    }
                }
            }
        }

        // Batch-select action bar --------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            Row(
                modifier = Modifier.clickable(onClick = onSelectAll)
                    .padding(vertical = DhunSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected.size == downloads.size && downloads.isNotEmpty(),
                    onCheckedChange = { onSelectAll() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = DhunColors.accent,
                        uncheckedColor = DhunColors.textTertiary,
                    ),
                )
                Text(
                    if (selected.isEmpty()) "Select all" else "Selected ${selected.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = DhunColors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            DhunButton(
                onClick = onDeleteSelected,
                enabled = selected.isNotEmpty(),
            ) {
                DhunIconView(
                    icon = DhunIcon.Close,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.xs))
                Text(if (selected.isEmpty()) "Delete" else "Delete (${selected.size})")
            }
        }

        // Selectable rows -----------------------------------------------------
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = DhunSpacing.xxxl),
        ) {
            itemsIndexed(downloads, key = { _, d -> d.trackId }) { _, item ->
                ManageableDownloadRow(
                    download = item,
                    selected = item.trackId in selected,
                    onToggle = { onToggle(item.trackId) },
                )
            }
        }
    }
}

@Composable
private fun StorageStatRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.xs / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = if (emphasize) DhunColors.textPrimary else DhunColors.textTertiary,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ManageableDownloadRow(
    download: DownloadedTrack,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = DhunColors.accent,
                uncheckedColor = DhunColors.textTertiary,
            ),
        )
        ArtworkImage(
            imageUrl = download.thumbnailUrl,
            contentDescription = download.title,
            modifier = Modifier.size(DhunSpacing.touchTarget),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                download.title,
                style = MaterialTheme.typography.bodyMedium,
                color = DhunColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(stateLabel(download.downloadState))
                    if (download.isCompleted && download.fileSizeBytes > 0) {
                        append(" • "); append(formatBytes(download.fileSizeBytes))
                    }
                    append(" • "); append(download.artistName)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (download.isCompleted) DhunColors.textSecondary else DhunColors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadedTrack,
    progressFlow: kotlinx.coroutines.flow.Flow<dev.dhun.download.DownloadProgress?>,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    // Remember the per-track flow across recompositions (re-created only if
    // the track id changes) so the progress collection isn't torn down each frame.
    val flow = remember(download.trackId) { progressFlow }
    val progress by flow.collectAsState(initial = null)
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
            ArtworkImage(
                imageUrl = download.thumbnailUrl,
                contentDescription = download.title,
                modifier = Modifier.size(DhunSpacing.touchTarget),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DhunColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val status = when (download.downloadState) {
                    DownloadState.COMPLETED -> buildString {
                        append("Downloaded")
                        if (download.fileSizeBytes > 0) append(" • ").append(formatBytes(download.fileSizeBytes))
                        append(" • ").append(download.artistName)
                    }
                    DownloadState.DOWNLOADING -> buildString {
                        val pct = progress?.fraction
                        if (pct != null) append("${(pct * 100).toInt()}% • ")
                        else append("Downloading • ")
                        append(download.artistName)
                    }
                    DownloadState.QUEUED -> "Queued • ${download.artistName}"
                    DownloadState.PAUSED -> "Paused • ${download.artistName}"
                    DownloadState.FAILED -> "Failed • tap retry • ${download.artistName}"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (download.downloadState) {
                        DownloadState.COMPLETED -> DhunColors.textSecondary
                        DownloadState.FAILED -> DhunColors.error
                        else -> DhunColors.accent
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // State-specific primary action -----------------------------------
            when (download.downloadState) {
                DownloadState.COMPLETED -> DhunIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Play ${download.title}",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Play,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.accent,
                    )
                }
                DownloadState.DOWNLOADING -> DhunIconButton(
                    onClick = onPause,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Pause download of ${download.title}",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.accent,
                    )
                }
                DownloadState.PAUSED, DownloadState.FAILED -> DhunIconButton(
                    onClick = onResume,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = if (download.downloadState == DownloadState.FAILED) "Retry ${download.title}" else "Resume ${download.title}",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.accent,
                    )
                }
                DownloadState.QUEUED -> DhunIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Cancel download of ${download.title}",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Close,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.textTertiary,
                    )
                }
            }

            // Remove works for every state (cancel + delete files + row). ----
            DhunIconButton(
                onClick = onRemove,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "Remove ${download.title}",
            ) {
                DhunIconView(
                    icon = DhunIcon.Close,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.textTertiary,
                )
            }
        }

        // Live progress bar while downloading.
        if (download.downloadState == DownloadState.DOWNLOADING) {
            val fraction = progress?.fraction
            Spacer(modifier = Modifier.height(DhunSpacing.xs))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(DhunSpacing.progressStroke).clip(DhunShapes.full),
                    color = DhunColors.accent,
                    trackColor = DhunColors.surfaceElevated,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(DhunSpacing.progressStroke).clip(DhunShapes.full),
                    color = DhunColors.accent,
                    trackColor = DhunColors.surfaceElevated,
                )
            }
        }
    }
}

@Composable
private fun DeleteSelectedConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, count: Int) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth), shape = DhunShapes.large) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text("Delete $count download${if (count == 1) "" else "s"}?", style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                Text("Selected tracks and their downloaded files will be removed from this device. This can't be undone.", style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    DhunButton(onClick = onConfirm) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun ClearDownloadsConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth), shape = DhunShapes.large) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text("Clear all downloads?", style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                Text("This deletes all $count downloaded track${if (count == 1) "" else "s"} from this device. Offline playback will no longer work for them.", style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    DhunButton(onClick = onConfirm) { Text("Clear") }
                }
            }
        }
    }
}

private fun stateLabel(state: DownloadState): String = when (state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.DOWNLOADING -> "Downloading"
    DownloadState.COMPLETED -> "Downloaded"
    DownloadState.FAILED -> "Failed"
    DownloadState.PAUSED -> "Paused"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = 1024L * 1024 * 1024
    val mb = 1024L * 1024
    val kb = 1024L
    return when {
        bytes >= gb -> "${bytes / gb}.${bytes % gb * 10 / gb} GB"
        bytes >= mb -> "${bytes / mb}.${bytes % mb * 10 / mb} MB"
        bytes >= kb -> "${bytes / kb}.${bytes % kb * 10 / kb} KB"
        else -> "$bytes B"
    }
}

/* ---------------- History tab ----------------------------------------------- */

@Composable
private fun HistoryTab(
    groupedHistory: List<HistoryDay>,
    onPlayEntry: (HistoryEntry) -> Unit,
    onPlayDay: (HistoryDay, Int) -> Unit,
    onRemoveEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groupedHistory.isEmpty()) {
        EmptyView(
            title = "No history yet",
            message = "Tracks you play will appear here grouped by day. Clear them anytime.",
            modifier = modifier.fillMaxSize().padding(DhunSpacing.xxl),
        )
        return
    }
    var showClearConfirm by remember { mutableStateOf(false) }
    val nowMs = dev.dhun.data.EpochClock.System.nowMs()
    val offsetMs = currentUtcOffsetMs()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val total = groupedHistory.sumOf { it.entries.size }
            Text("$total play${if (total == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = DhunColors.textSecondary)
            DhunOutlinedButton(onClick = { showClearConfirm = true }) { Text("Clear all") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = DhunSpacing.xxxl),
        ) {
            groupedHistory.forEach { day ->
                val headerLabel = LibraryViewModel.dayHeaderLabel(day.dayStartEpochMs, nowMs, offsetMs)
                item(key = "header_${day.dayStartEpochMs}") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(headerLabel, style = MaterialTheme.typography.titleSmall, color = DhunColors.textPrimary, fontWeight = FontWeight.Bold)
                        DhunTextButton(onClick = { onPlayDay(day, 0) }) { Text("Play day", fontSize = DhunTypographyTokens.bodySmall.fontSize) }
                    }
                }
                itemsIndexed(day.entries, key = { _, e -> "h_${e.entryId}" }) { index, entry ->
                    HistoryRow(
                        entry = entry,
                        nowMs = nowMs,
                        onTap = { onPlayEntry(entry) },
                        onLongPressRemove = { entry.entryId?.let(onRemoveEntry) },
                    )
                }
            }
        }
    }
    if (showClearConfirm) {
        ClearHistoryConfirmDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = { onClearAll(); showClearConfirm = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    nowMs: Long,
    onTap: () -> Unit,
    onLongPressRemove: () -> Unit,
) {
    val relative = LibraryViewModel.relativeTimeLabel(entry.playedAtEpochMs, nowMs)
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPressRemove)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        ArtworkImage(imageUrl = entry.track.thumbnailUrl, contentDescription = entry.track.title, modifier = Modifier.size(DhunSpacing.touchTarget))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.track.title, style = MaterialTheme.typography.bodyMedium, color = DhunColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(entry.track.artistName)
                    append(" • ")
                    append(relative)
                    entry.playedFromContext?.let { append(" • $it") }
                    if (entry.completedPlayback) append(" • completed")
                },
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Long-press hint affordance (desktop fallback)
        DhunIconButton(
            onClick = onLongPressRemove,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            contentDescription = "Remove ${entry.track.title} from history",
        ) {
            DhunIconView(
                icon = DhunIcon.Close,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = DhunColors.textTertiary,
            )
        }
    }
}

@Composable
private fun ClearHistoryConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.widthIn(min = DhunSpacing.dialogMinWidth, max = DhunSpacing.dialogMaxWidth), shape = DhunShapes.large) {
            Column(modifier = Modifier.padding(DhunSpacing.lg)) {
                Text("Clear history?", style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                Spacer(modifier = Modifier.height(DhunSpacing.sm))
                Text("This removes all playback history. Favorites and playlists stay.", style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                Spacer(modifier = Modifier.height(DhunSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DhunTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    DhunButton(onClick = onConfirm) { Text("Clear") }
                }
            }
        }
    }
}
