package dev.dhun.ui.player

import dev.dhun.design.DhunTypographyTokens
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.Track
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
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
import kotlinx.coroutines.launch

private val tabTitles = listOf("Lyrics", "Queue", "Related")

/* ---------------- tab header ----------------------------------------------- */

@Composable
internal fun PlayerTabRow(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DhunSpacing.touchTarget),
    ) {
        tabTitles.forEachIndexed { index, title ->
            val selected = index == selectedTab
            val color by animateColorAsState(
                targetValue = if (selected) accent else DhunColors.textTertiary,
                animationSpec = DhunAnimations.fastTween(),
                label = "tabColor$index",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(DhunShapes.medium)
                    .background(if (selected) DhunColors.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.xs))
                Box(
                    modifier = Modifier
                        .height(DhunSpacing.iconStroke)
                        .width(if (selected) DhunSpacing.xl else DhunSpacing.zero)
                        .clip(DhunShapes.full)
                        .background(if (selected) accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
internal fun PlayerTabContent(
    tab: Int,
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    when (tab) {
        0 -> LyricsTabContent(viewModel = viewModel, accent = accent, modifier = modifier)
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

    ReorderableList(
        items = queue,
        onMove = { from, to -> viewModel.moveQueueItem(from, to) },
        onSwipeRemove = { index, _ -> viewModel.removeQueueItem(index) },
        onItemClick = { index, _ -> viewModel.playQueueAt(index) },
        highlightIndex = currentIndex,
        modifier = modifier.fillMaxSize(),
    ) { index, track, dragHandle, isDragging, isHighlighted ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DhunSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
        ) {
            Box(modifier = Modifier.size(DhunSpacing.compactTarget)) {
                ArtworkImage(
                    imageUrl = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isHighlighted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(DhunShapes.artwork)
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
                    style = MaterialTheme.typography.bodyMedium,
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
            if (isHighlighted) {
                Text(
                    text = "NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    letterSpacing = DhunTypographyTokens.compactLetterSpacing,
                )
            }
            Box(modifier = dragHandle) {
                DragHandleGrip()
            }
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

    when (val state = lyricsState) {
        is LyricsUiState.Loading -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(DhunSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            ) {
                repeat(7) {
                    LoadingShimmer(modifier = Modifier.fillMaxWidth(if (it % 2 == 0) 0.9f else 0.6f).height(DhunSpacing.lg))
                }
            }
        }
        is LyricsUiState.Unavailable -> {
            EmptyView(
                title = "No lyrics",
                message = "Lyrics aren't available for this track yet.",
                modifier = modifier.fillMaxSize(),
            )
        }
        is LyricsUiState.Error -> {
            ErrorView(
                title = "Lyrics failed to load",
                message = state.message,
                onRetry = { viewModel.refreshLyrics() },
                modifier = modifier.fillMaxSize(),
            )
        }
        is LyricsUiState.Unsynced -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.md),
            ) {
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DhunColors.textSecondary,
                    lineHeight = DhunTypographyTokens.bodyRelaxed.lineHeight,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.huge))
            }
        }
        is LyricsUiState.Synced -> {
            val lines = state.lines
            val activeIndex = lines.indexOfLast { line ->
                val start = line.startTimeMs ?: Long.MIN_VALUE
                start <= positionMs
            }
            val listState: LazyListState = rememberLazyListState()
            LaunchedEffect(activeIndex) {
                if (activeIndex > 0) {
                    listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                }
            }
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = DhunSpacing.lg, horizontal = DhunSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                itemsIndexed(lines) { index, line ->
                    val active = index == activeIndex
                    val color by animateColorAsState(
                        targetValue = if (active) DhunColors.textPrimary else DhunColors.textTertiary,
                        animationSpec = DhunAnimations.fastTween(),
                        label = "lyricColor$index",
                    )
                    Text(
                        text = line.text.ifBlank { " " },
                        style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(DhunShapes.small)
                            .clickable(enabled = line.startTimeMs != null) {
                                line.startTimeMs?.let(viewModel::seekTo)
                            }
                            .padding(vertical = DhunSpacing.xs),
                    )
                }
            }
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
    val scope = rememberCoroutineScope()

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
                        Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                            LoadingShimmer(modifier = Modifier.width(DhunSpacing.dialogListHeight).height(DhunSpacing.mdPlus))
                            LoadingShimmer(modifier = Modifier.width(DhunSpacing.skeletonTextWidth).height(DhunSpacing.md))
                        }
                    }
                }
            }
        }
        is RelatedUiState.Empty -> {
            EmptyView(
                title = "No related tracks",
                message = "Play a track and its radio queue will appear here.",
                modifier = modifier.fillMaxSize(),
            )
        }
        is RelatedUiState.Error -> {
            ErrorView(
                title = "Related tracks unavailable",
                message = state.message,
                onRetry = { viewModel.refreshRelated() },
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.xs),
                    ) {
                        DhunTonalButton(
                            onClick = { scope.launch { viewModel.startRadio() } },
                        ) {
                            DhunIconView(
                                icon = DhunIcon.Play,
                                contentDescription = null,
                                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            )
                            Spacer(modifier = Modifier.width(DhunSpacing.xs))
                            Text("Play radio (${tracks.size})")
                        }
                    }
                }
                itemsIndexed(tracks, key = { i, t -> "related_${i}_${t.id}" }) { index, track ->
                    RelatedRow(
                        track = track,
                        onClick = { scope.launch { viewModel.playRelatedAt(index) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.xs),
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
                    track.albumName?.let { append(" • $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    }
}
