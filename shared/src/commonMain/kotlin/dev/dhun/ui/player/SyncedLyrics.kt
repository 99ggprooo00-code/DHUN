package dev.dhun.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.dhun.core.LyricsLine
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunTonalButton
import dev.dhun.design.components.EmptyView
import kotlin.math.abs

/** Track-keyed by the caller. User scrolling pauses follow until explicitly resumed. */
@Composable
internal fun SyncedLyricsContent(
    lines: List<LyricsLine>,
    positionMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) {
        EmptyView(
            title = "No lyrics",
            message = "No lyric lines were found for this track.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val listState = rememberLazyListState()
    val activeIndex = activeLyricIndex(lines, positionMs)
    var following by remember { mutableStateOf(true) }
    val manualScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Programmatic centering uses SideEffect; touch/trackpad/wheel
                // scrolling uses UserInput. Never consume the user's movement.
                if (source == NestedScrollSource.UserInput && available.y != 0f) following = false
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = constraints.maxHeight
        LaunchedEffect(activeIndex, following, viewportHeight, lines) {
            if (following) {
                // Includes index zero and the intro before the first timestamp:
                // seeking to the beginning must not leave the last verse on screen.
                listState.centerLyric(activeIndex.coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(manualScroll)
                .onPreviewKeyEvent { event ->
                    if (event.key in lyricScrollKeys) following = false
                    false // Keep the list's native keyboard scrolling.
                },
            // Half a viewport of breathing room lets even the first/last line
            // be centered. Insets come from the actual lyrics pane, not a device size.
            contentPadding = PaddingValues(
                horizontal = DhunSpacing.xxl,
                vertical = maxHeight / 2,
            ),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val active = index == activeIndex
                val color by animateColorAsState(
                    targetValue = if (active) accent else DhunColors.textTertiary,
                    animationSpec = DhunAnimations.mediumTween(),
                    label = "lyricColor$index",
                )
                val emphasis by animateFloatAsState(
                    targetValue = if (active) 1f else 0.94f,
                    // ADR-002 P8: the line pops in on a spring, karaoke-style;
                    // the color keeps a calm tween so hues never bounce.
                    animationSpec = DhunAnimations.springSpec(),
                    label = "lyricEmphasis$index",
                )
                val startMs = line.startTimeMs?.takeIf { it >= 0 }
                Text(
                    text = line.text.ifBlank { " " },
                    // Keep measurement stable while emphasis changes; changing
                    // font size per line made wrapping fight the scroll animation.
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { selected = active }
                        .graphicsLayer { scaleX = emphasis; scaleY = emphasis }
                        .clip(DhunShapes.medium)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    if (active) accent.copy(alpha = 0.12f) else Color.Transparent,
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .then(
                            if (startMs != null) {
                                Modifier.clickable(
                                    role = Role.Button,
                                    onClickLabel = "Seek to ${formatMs(startMs, startMs)}",
                                ) {
                                    onSeek(startMs)
                                    following = true
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = DhunSpacing.md),
                )
            }
        }

        if (!following) {
            DhunTonalButton(
                onClick = { following = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(DhunSpacing.sm),
            ) {
                Text("Follow lyrics")
            }
        }
    }
}

private val lyricScrollKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.PageUp, Key.PageDown, Key.MoveHome, Key.MoveEnd,
)

/** Untimed/invalid lines are not active; ties select the final line at that timestamp. */
internal fun activeLyricIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    var active = -1
    var latestStart = -1L
    lines.forEachIndexed { index, line ->
        val start = line.startTimeMs
        if (start != null && start >= 0 && start <= positionMs && start >= latestStart) {
            active = index
            latestStart = start
        }
    }
    return active
}

/** Offsets below one pixel stay put — the anti-twitch floor for lyric centering. */
internal const val CENTERING_JITTER_PX = 0.5f

internal fun shouldRecenterLyric(deltaPx: Float): Boolean =
    abs(deltaPx) > CENTERING_JITTER_PX

/** Offsets use LazyList coordinates, including its (possibly negative) padded viewport start. */
internal fun lyricCenterScrollDelta(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float = itemOffset.toFloat() + itemSize / 2f - (viewportStart.toFloat() + viewportEnd) / 2f

private suspend fun LazyListState.centerLyric(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        // Also awaits the first layout on initial entry into the tab.
        animateScrollToItem(index)
    }
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val delta = lyricCenterScrollDelta(
        itemOffset = item.offset,
        itemSize = item.size,
        viewportStart = layout.viewportStartOffset,
        viewportEnd = layout.viewportEndOffset,
    )
    // Sub-pixel centering jitter is invisible; animating against it made the
    // list twitch on every position tick. Only a real offset scrolls.
    if (shouldRecenterLyric(delta)) animateScrollBy(delta)
}
