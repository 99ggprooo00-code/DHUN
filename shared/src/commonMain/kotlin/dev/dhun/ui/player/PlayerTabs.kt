package dev.dhun.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.Track
import dev.dhun.data.PlayContext
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunTonalButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.LoadingShimmer
import dev.dhun.presentation.player.LyricsUiState
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.player.RelatedUiState
import dev.dhun.ui.components.DragHandleGrip
import dev.dhun.ui.components.ReorderableList
import kotlinx.coroutines.delay

@Composable
internal fun PlayerTabContent(
    tab: Int,
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    when (tab) {
        LYRICS_TAB_INDEX -> LyricsTabContent(viewModel = viewModel, accent = accent, modifier = modifier)
        2 -> RelatedTabContent(viewModel = viewModel, accent = accent, modifier = modifier)
        else -> QueueTabContent(viewModel = viewModel, accent = accent, modifier = modifier)
    }
}

/* ---------------- queue ---------------------------------------------------- */

/**
 * Queue tab: drag-reorder (long-press the reorder handle), swipe-left remove,
 * tap-to-jump. The playing row is tinted + shows the equalizer animation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QueueTabContent(
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentQueueIndex.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    if (queue.isEmpty()) {
        EmptyView(
            title = "Queue is empty",
            message = "Play something and it will show up here.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (currentIndex in queue.indices) {
                    "${currentIndex + 1} of ${queue.size}"
                } else {
                    "${queue.size} tracks"
                },
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Drag to reorder",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
            )
        }
        ReorderableList(
            items = queue,
            onMove = { from, to -> viewModel.moveQueueItem(from, to, queue) },
            onSwipeRemove = { index, _ -> viewModel.removeQueueItem(index, queue) },
            onItemClick = { index, _ -> viewModel.playQueueAt(index, queue) },
            highlightIndex = currentIndex,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { index, track, dragHandle, isDragging, isHighlighted ->
            val rowBg = if (isHighlighted) {
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.22f), DhunColors.glassHighlight, DhunColors.glassDeep.copy(alpha = 0.4f)),
                )
            } else {
                Brush.verticalGradient(
                    listOf(DhunColors.glassHighlight, DhunColors.glassDeep.copy(alpha = 0.45f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DhunSpacing.sm, vertical = DhunSpacing.xs)
                    .clip(DhunShapes.large)
                    .background(rowBg)
                    .semantics {
                        selected = isHighlighted
                        if (isHighlighted) stateDescription = if (isPlaying) "Playing" else "Current track"
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction("Move up") {
                                viewModel.moveQueueItem(index, index - 1, queue)
                            })
                            if (index < queue.lastIndex) add(CustomAccessibilityAction("Move down") {
                                viewModel.moveQueueItem(index, index + 1, queue)
                            })
                            add(CustomAccessibilityAction("Remove from queue") {
                                viewModel.removeQueueItem(index, queue)
                            })
                        }
                    }
                    .padding(horizontal = DhunSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                Box(modifier = Modifier.size(DhunSpacing.compactTarget).clip(DhunShapes.medium)) {
                    ArtworkImage(
                        imageUrl = track.thumbnailUrl,
                        contentDescription = null, // The adjacent title already labels the row.
                        modifier = Modifier.fillMaxSize(),
                        shape = DhunShapes.medium,
                    )
                    if (isHighlighted) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            EqualizerBars(
                                color = accent,
                                animate = isPlaying,
                                modifier = Modifier.size(DhunSpacing.xl),
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isHighlighted) accent else DhunColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = buildString {
                            append(track.artistName)
                            track.albumName?.let { append(" • $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = DhunColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                QueueRowActions(
                    track = track,
                    index = index,
                    queue = queue,
                    viewModel = viewModel,
                    enabled = !isDragging,
                )
                Box(modifier = dragHandle) {
                    DragHandleGrip()
                }
            }
        }
    }
}

/** Visible keyboard-friendly alternatives to drag and swipe; shared list stays unchanged. */
@Composable
private fun QueueRowActions(
    track: Track,
    index: Int,
    queue: List<Track>,
    viewModel: PlayerViewModel,
    enabled: Boolean,
) {
    var expanded by remember(queue, index) { mutableStateOf(false) }
    Box {
        DhunIconButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            contentDescription = "Queue actions for ${track.title}",
        ) {
            DhunIconView(
                icon = DhunIcon.MoreVert,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = DhunColors.textSecondary,
            )
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Move up") },
                enabled = index > 0,
                onClick = {
                    expanded = false
                    viewModel.moveQueueItem(index, index - 1, queue)
                },
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                enabled = index < queue.lastIndex,
                onClick = {
                    expanded = false
                    viewModel.moveQueueItem(index, index + 1, queue)
                },
            )
            DropdownMenuItem(
                text = { Text("Remove from queue", color = DhunColors.error) },
                onClick = {
                    expanded = false
                    viewModel.removeQueueItem(index, queue)
                },
            )
        }
    }
}

/** Three bouncing bars — animates while playing, static when paused. */
@Composable
internal fun EqualizerBars(
    color: Color,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    if (animate) {
        val transition = rememberInfiniteTransition(label = "eq")
        val heights = listOf(420, 640, 530).mapIndexed { i, duration ->
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eq$i",
            )
        }
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight(h.value)
                        .width(DhunSpacing.progressStroke)
                        .clip(DhunShapes.full)
                        .background(color),
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(0.5f, 0.8f, 0.4f).forEach { h ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight(h)
                        .width(DhunSpacing.progressStroke)
                        .clip(DhunShapes.full)
                        .background(color),
                )
            }
        }
    }
}

/* ---------------- lyrics ---------------------------------------------------- */

@Composable
internal fun LyricsTabContent(
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val lyricsState by viewModel.lyricsState.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val track = currentTrack
    if (track == null) {
        EmptyView(
            title = "Nothing playing",
            message = "Play a track to see its lyrics.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    // Scroll position and manual-follow preference belong to this track, not
    // the tab slot. This also resets plain-text lyrics on a track change.
    key(track.id) {
        when (val state = lyricsState) {
            is LyricsUiState.Loading -> {
                Column(
                    modifier = modifier.fillMaxSize().padding(DhunSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                ) {
                    repeat(7) {
                        LoadingShimmer(
                            modifier = Modifier
                                .fillMaxWidth(if (it % 2 == 0) 0.9f else 0.6f)
                                .height(DhunSpacing.lg),
                        )
                    }
                }
            }
            is LyricsUiState.Unavailable -> {
                EmptyView(
                    title = "No lyrics",
                    message = "Lyrics aren't available for this track yet.",
                    modifier = modifier.fillMaxSize(),
                    actionLabel = "Check again",
                    onAction = viewModel::refreshLyrics,
                )
            }
            is LyricsUiState.Error -> {
                ErrorView(
                    title = "Lyrics failed to load",
                    message = state.message,
                    onRetry = viewModel::refreshLyrics,
                    modifier = modifier.fillMaxSize(),
                )
            }
            is LyricsUiState.Unsynced -> {
                if (state.text.isBlank()) {
                    EmptyView(
                        title = "No lyrics",
                        message = "No lyric text was found for this track.",
                        modifier = modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                    ) {
                        Text(
                            text = "Not time-synced",
                            style = MaterialTheme.typography.labelMedium,
                            color = DhunColors.textTertiary,
                        )
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DhunColors.textSecondary,
                            lineHeight = DhunTypographyTokens.bodyRelaxed.lineHeight,
                        )
                        Spacer(modifier = Modifier.height(DhunSpacing.huge))
                    }
                }
            }
            is LyricsUiState.Synced -> SyncedLyricsContent(
                lines = state.lines,
                positionMs = positionMs,
                accent = accent,
                onSeek = { position ->
                    // An outgoing tab/track must never seek its successor.
                    if (viewModel.currentTrack.value?.id == track.id) viewModel.seekTo(position)
                },
                modifier = modifier,
            )
        }
    }
}

/* ---------------- related --------------------------------------------------- */

/** Related tab: `/next` radio list — tap plays with the radio as the queue. */
@Composable
internal fun RelatedTabContent(
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val relatedState by viewModel.relatedState.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val current = currentTrack
    if (current == null) {
        EmptyView(
            title = "Nothing playing",
            message = "Play a track to discover related music.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    key(current.id) {
        when (val state = relatedState) {
            is RelatedUiState.Loading -> {
                Column(modifier = modifier.fillMaxSize().padding(DhunSpacing.md)) {
                    repeat(5) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                            modifier = Modifier.padding(vertical = DhunSpacing.sm),
                        ) {
                            LoadingShimmer(modifier = Modifier.size(DhunSpacing.compactTarget))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                            ) {
                                LoadingShimmer(modifier = Modifier.fillMaxWidth(0.85f).height(DhunSpacing.mdPlus))
                                LoadingShimmer(modifier = Modifier.fillMaxWidth(0.6f).height(DhunSpacing.md))
                            }
                        }
                    }
                }
            }
            is RelatedUiState.Empty -> {
                EmptyView(
                    title = "No related tracks",
                    message = "No recommendations were found for this track. Try another song or check again.",
                    actionLabel = "Check again",
                    onAction = viewModel::refreshRelated,
                    modifier = modifier.fillMaxSize(),
                )
            }
            is RelatedUiState.Error -> {
                ErrorView(
                    title = "Related tracks unavailable",
                    message = state.message,
                    onRetry = viewModel::refreshRelated,
                    modifier = modifier.fillMaxSize(),
                )
            }
            is RelatedUiState.Success -> {
                val tracks = state.tracks
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = DhunSpacing.xs),
                ) {
                    item(key = "start_radio") {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                        ) {
                            Text(
                                text = "Based on ${current.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = DhunColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            DhunTonalButton(
                                enabled = tracks.isNotEmpty(),
                                // Capture the displayed list; a provider refresh must
                                // not make this index play a different recommendation.
                                // The VM owns the job, so switching tabs won't cancel it.
                                onClick = { viewModel.playQueue(tracks, 0, PlayContext.QUEUE) },
                            ) {
                                DhunIconView(
                                    icon = DhunIcon.Play,
                                    contentDescription = null,
                                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                                )
                                Spacer(modifier = Modifier.width(DhunSpacing.xs))
                                Text("Play radio (${tracks.size})")
                            }
                            Text(
                                text = "Replaces your queue • Use + to add a song instead",
                                style = MaterialTheme.typography.labelSmall,
                                color = DhunColors.textTertiary,
                            )
                        }
                    }
                    itemsIndexed(tracks, key = { i, t -> "related_${i}_${t.id}" }) { index, track ->
                        RelatedRow(
                            track = track,
                            accent = accent,
                            onClick = { viewModel.playQueue(tracks, index, PlayContext.QUEUE) },
                            onAddToQueue = { viewModel.addToQueue(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedRow(track: Track, accent: Color, onClick: () -> Unit, onAddToQueue: () -> Unit) {
    var queued by remember(track.id) { mutableStateOf(false) }
    LaunchedEffect(queued) {
        if (queued) {
            delay(QUEUE_FEEDBACK_MS)
            queued = false
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.xs)
            .clip(DhunShapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(DhunColors.glassHighlight, DhunColors.glassDeep.copy(alpha = 0.45f)),
                ),
            )
            .clickable(role = Role.Button, onClickLabel = "Play ${track.title}", onClick = onClick)
            .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
    ) {
        ArtworkImage(
            imageUrl = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            shape = DhunShapes.medium,
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
                text = if (queued) "Added to queue" else buildString {
                    append(track.artistName)
                    track.albumName?.let { append(" • $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (queued) accent else DhunColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { if (queued) liveRegion = LiveRegionMode.Polite },
            )
        }
        DhunIconButton(
            onClick = onClick,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            contentDescription = "Play ${track.title}",
        ) {
            DhunIconView(
                icon = DhunIcon.Play,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = DhunColors.textSecondary,
            )
        }
        DhunIconButton(
            onClick = { onAddToQueue(); queued = true },
            enabled = !queued,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            contentDescription = if (queued) "Added to queue" else "Add ${track.title} to queue",
        ) {
            DhunIconView(
                icon = if (queued) DhunIcon.QueueMusic else DhunIcon.Add,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = if (queued) accent else DhunColors.textSecondary,
            )
        }
    }
}

private const val QUEUE_FEEDBACK_MS = 2_000L
