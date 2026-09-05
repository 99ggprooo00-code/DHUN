package dev.dhun.ui.library

import dev.dhun.design.DhunTypographyTokens
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
import dev.dhun.ui.components.ReorderableList
import dev.dhun.ui.components.DragHandleGrip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Library screen — Phase 10.
 *
 * Three tabs per PROMPT_SEQUENCE.md: Playlists / Favorites / History.
 * Acceptance:
 *  1. Favorites round-trip in UI (tap plays favorites as queue, swipe removes).
 *  2. History grouped by day with relative times; long-press remove; clear-all confirmation.
 *  3. Empty states for all tabs.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onTrackOverflow: (Track) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
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

    Column(modifier = modifier.fillMaxSize().background(DhunColors.background)) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md),
        ) {
            Text(
                text = "DHUN",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = DhunTypographyTokens.brand.letterSpacing,
            )
            Text(
                text = "Your library",
                style = MaterialTheme.typography.headlineMedium,
                color = DhunColors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        LibraryTabRow(
            selectedTab = selectedTab,
            onSelect = viewModel::selectTab,
            modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        )

        Spacer(modifier = Modifier.height(DhunSpacing.sm))

        when (selectedTab) {
            LibraryTab.PLAYLISTS -> PlaylistsTab(
                playlists = playlists,
                onPlaylistClick = onPlaylistClick,
                onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                onPlayPlaylist = viewModel::playPlaylist,
                modifier = Modifier.weight(1f),
            )
            LibraryTab.FAVORITES -> FavoritesTab(
                favorites = favorites,
                onPlayTrack = viewModel::playFavoritesTrack,
                onPlayAll = { viewModel.playFavorites(0) },
                onRemove = viewModel::removeFavorite,
                onTrackOverflow = onTrackOverflow,
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
    Row(
        modifier = modifier.fillMaxWidth().height(DhunSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        LibraryTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val label = when (tab) {
                LibraryTab.PLAYLISTS -> "Playlists"
                LibraryTab.FAVORITES -> "Favorites"
                LibraryTab.HISTORY -> "History"
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(DhunShapes.full)
                    .background(if (selected) DhunColors.accentContainer else DhunColors.surfaceElevated)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = DhunSpacing.lg, vertical = DhunSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) DhunColors.onAccentContainer else DhunColors.textSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/* ---------------- Playlists tab --------------------------------------------- */

@Composable
private fun PlaylistsTab(
    playlists: List<LocalPlaylist>,
    onPlaylistClick: (LocalPlaylist) -> Unit,
    onCreatePlaylist: suspend (String) -> LocalPlaylist,
    onPlayPlaylist: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            EmptyView(
                title = "No playlists",
                message = "Playlists you create will appear here. Create one to start collecting tracks.",
                actionLabel = "New playlist",
                onAction = { showCreate = true },
                modifier = Modifier.fillMaxSize().padding(DhunSpacing.xxl),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = DhunSpacing.xxxl),
            ) {
                item(key = "create") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(DhunSpacing.screenPadding),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DhunOutlinedButton(onClick = { showCreate = true }) {
                            Text("+ New playlist")
                        }
                    }
                }
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
                onConfirm = { name ->
                    // The create suspend is fire-and-forget from the ViewModel
                    // flow; we just close the dialog after launching.
                    showCreate = false
                },
                onCreate = onCreatePlaylist,
            )
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

/* ---------------- Favorites tab --------------------------------------------- */

@Composable
private fun FavoritesTab(
    favorites: List<Track>,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onRemove: (String) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        EmptyView(
            title = "No favorites yet",
            message = "Tap the heart on any track to save it here. It will sync across restarts.",
            modifier = modifier.fillMaxSize().padding(DhunSpacing.xxl),
        )
        return
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${favorites.size} favorite${if (favorites.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = DhunColors.textSecondary)
            DhunButton(onClick = onPlayAll, enabled = favorites.isNotEmpty()) {
                DhunIconView(
                    icon = DhunIcon.Play,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                )
                Spacer(modifier = Modifier.width(DhunSpacing.xs))
                Text("Play all")
            }
        }
        // Swipe-to-remove via ReorderableList (drag disabled by not exposing handle reorder? we keep handle but reorder is no-op grouped by favorites? Actually favorites are ordered by addedAt DESC, reordering not supported for now; we expose drag handle but move is no-op — swipe is the primary action.)
        ReorderableList(
            items = favorites,
            onMove = { _, _ -> /* Favorites reordering not in v1 — keep insertion order. */ },
            onSwipeRemove = { _, track -> onRemove(track.id) },
            onItemClick = { index, track -> onPlayTrack(track) },
            modifier = Modifier.fillMaxSize(),
        ) { _, track, dragHandle, _, _ ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = DhunSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                ArtworkImage(imageUrl = track.thumbnailUrl, contentDescription = track.title, modifier = Modifier.size(DhunSpacing.touchTarget))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.bodyMedium, color = DhunColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artistName, style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
