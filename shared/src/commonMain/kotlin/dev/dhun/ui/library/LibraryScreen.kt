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
import dev.dhun.domain.HistoryDay
import dev.dhun.presentation.library.LibraryTab
import dev.dhun.presentation.library.LibraryViewModel
import dev.dhun.presentation.library.currentUtcOffsetMs
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
    val visibleTabs = listOf(LibraryTab.PLAYLISTS, LibraryTab.HISTORY)
    Row(
        modifier = modifier.fillMaxWidth().height(DhunSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        visibleTabs.forEach { tab ->
            val selected = (tab == selectedTab) || (tab == LibraryTab.PLAYLISTS && selectedTab == LibraryTab.FAVORITES)
            val label = when (tab) {
                LibraryTab.PLAYLISTS -> "Playlists"
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
                                DhunColors.accentGlow,
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
