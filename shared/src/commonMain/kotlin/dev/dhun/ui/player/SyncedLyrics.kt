package dev.dhun.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import dev.dhun.core.LyricsLine
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.DhunTonalButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.fadeVerticalEdges
import dev.dhun.design.components.lyricsActiveHighlightBrush
import dev.dhun.design.components.lyricsActiveHighlightBase
import kotlin.math.abs

/**
 * Synced lyrics, immersive presentation (2026-11 lyrics pass).
 *
 * Hierarchy instead of a wall of equal lines: the sung line is set in
 * [DhunTypographyTokens.lyricActiveStyle] — bold, centred, wrapped in a soft
 * frosted highlight ([lyricsActiveHighlightBrush]) that *frames* the words
 * rather than drawing another card — while every other line recedes to
 * [DhunTypographyTokens.lyricLineStyle] at reduced opacity. Translations and
 * transliterations hang under their primary line in
 * [DhunTypographyTokens.lyricSecondaryStyle], so a line and its secondary
 * text read as one group, and [DhunSpacing.lyricsGroupSpacing] separates the
 * groups from each other.
 *
 * The list feathers into the player backdrop at both ends
 * ([fadeVerticalEdges]) instead of clipping at a card edge, and keeps its
 * half-viewport content padding so the followed line centres in the *lyrics
 * region* (header and chrome excluded by construction — the stage measures
 * only the room between them).
 *
 * Behaviour preserved: follow/unfollow on manual scroll, tap-a-line and
 * tap-a-word seeking, karaoke word tinting, simultaneous backing vocals, and
 * "no unnecessary scrolling" (recentering is keyed on the active index and
 * ignores sub-pixel jitter).
 *
 * Track-keyed by the caller. User scrolling pauses follow until explicitly resumed.
 */
@Composable
internal fun SyncedLyricsContent(
    lines: List<LyricsLine>,
    positionMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    romanizedLines: List<String?> = emptyList(),
    autoScroll: Boolean = true,
    textSize: String = "default",
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
    val activeIndices = activeLyricIndices(lines, positionMs)
    val activeIndex = followedLyricIndex(lines, positionMs, activeIndices)
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
        LaunchedEffect(activeIndex, following, viewportHeight, lines, autoScroll, romanizedLines) {
            if (autoScroll && following) {
                // Includes index zero and the intro before the first timestamp:
                // seeking to the beginning must not leave the last verse on screen.
                listState.centerLyric(activeIndex.coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .fadeVerticalEdges(DhunSpacing.lyricsEdgeFeather)
                .nestedScroll(manualScroll)
                .onPreviewKeyEvent { event ->
                    if (event.key in lyricScrollKeys) following = false
                    false // Keep the list's native keyboard scrolling.
                },
            // Half a viewport of breathing room lets even the first/last line
            // be centered. Insets come from the actual lyrics pane, not a device size.
            contentPadding = PaddingValues(
                horizontal = DhunSpacing.lyricsHorizontalPadding,
                vertical = maxHeight / 2,
            ),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.lyricsGroupSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val active = index in activeIndices
                // Active lines read as primary white; the karaoke tint still
                // paints sung words in the accent inside them.
                val color by animateColorAsState(
                    targetValue = if (active) {
                        DhunColors.textPrimary
                    } else {
                        DhunColors.textSecondary.copy(alpha = INACTIVE_LYRIC_ALPHA)
                    },
                    animationSpec = DhunAnimations.mediumTween(),
                    label = "lyricColor$index",
                )
                val startMs = line.startTimeMs?.takeIf { it >= 0 }
                var textLayout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.lyricsSecondarySpacing),
                ) {
                    lyricRoleLabel(line)?.let { label ->
                        Text(
                            label,
                            style = DhunTypographyTokens.lyricCaption,
                            color = DhunColors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // The frosted highlight wraps the primary line only while
                    // it is the sung one: a pill around the words, never a
                    // panel behind the list.
                    Box(
                        modifier = if (active) {
                            Modifier
                                .clip(DhunShapes.large)
                                .lyricsActiveHighlightBase(DhunShapes.large)
                                .background(lyricsActiveHighlightBrush())
                                .padding(
                                    horizontal = DhunSpacing.md,
                                    vertical = DhunSpacing.xs,
                                )
                        } else {
                            Modifier
                        },
                    ) {
                        Text(
                            text = karaokeText(line, positionMs, accent, color, active),
                            onTextLayout = { textLayout = it },
                            // Size carries the hierarchy now (the old uniform
                            // headline + 1.04/0.92 scale pop made every line
                            // equally loud and fought the scroll animation).
                            style = if (active) {
                                DhunTypographyTokens.lyricActiveStyle(textSize)
                            } else {
                                DhunTypographyTokens.lyricLineStyle(textSize)
                            },
                            fontWeight = if (active) FontWeight.Bold else FontWeight.W500,
                            color = color,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .semantics { selected = active }
                                .then(
                                    if (startMs != null && line.words.isNotEmpty()) {
                                        Modifier.pointerInput(line, onSeek) {
                                            detectTapGestures { point ->
                                                val offset = textLayout?.getOffsetForPosition(point)
                                                onSeek(wordSeekTime(line, offset) ?: startMs)
                                                following = true
                                            }
                                        }.semantics {
                                            onClick("Seek to lyric line") { onSeek(startMs); following = true; true }
                                        }
                                    } else if (startMs != null) {
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
                                ),
                        )
                    }
                    romanizedLines.getOrNull(index)?.let { romanized ->
                        Text(
                            romanized,
                            style = DhunTypographyTokens.lyricSecondaryStyle(textSize),
                            color = DhunColors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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

/**
 * Opacity of timed lines that are not being sung: recessive but still
 * readable ahead of the voice. Lower values made upcoming verses unusable on
 * device; higher values flattened the hierarchy the active line needs.
 */
internal const val INACTIVE_LYRIC_ALPHA = 0.62f

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

/** One shaped text paragraph: spans retain BiDi order, ligatures and punctuation.
 * Timing colours only; incomplete timing keeps the existing line-level emphasis. */
internal fun karaokeText(line: LyricsLine, positionMs: Long, accent: Color, lineColor: Color, active: Boolean = true): AnnotatedString =
    buildAnnotatedString {
        append(line.text.ifBlank { " " })
        if (!active) return@buildAnnotatedString
        for (word in line.words) {
            if (word.startIndex < 0 || word.endIndex > line.text.length || word.endIndex <= word.startIndex) continue
            val sung = positionMs >= word.startTimeMs
            addStyle(
                SpanStyle(
                    color = if (sung) accent else lineColor.copy(alpha = 0.5f),
                    textDecoration = if (sung && (word.endTimeMs == null || positionMs < word.endTimeMs)) TextDecoration.Underline else TextDecoration.None,
                ),
                word.startIndex, word.endIndex,
            )
        }
    }

/** Explicit line/word end times allow simultaneous singers without guessing an overlap.
 * Plain LRC still uses the established last-line fallback. */
internal fun activeLyricIndices(lines: List<LyricsLine>, positionMs: Long): Set<Int> {
    val latest = activeLyricIndex(lines, positionMs)
    return buildSet {
        if (latest >= 0 && (lines[latest].endTimeMs ?: lines[latest].words.lastOrNull()?.endTimeMs) == null) add(latest)
        lines.forEachIndexed { index, line ->
            val start = line.startTimeMs ?: return@forEachIndexed
            val end = line.endTimeMs ?: line.words.lastOrNull()?.endTimeMs ?: return@forEachIndexed
            if (start <= positionMs && positionMs < end) add(index)
        }
    }
}

/** Text hit testing uses logical UTF-16 offsets supplied by Compose's BiDi layout. */
internal fun wordSeekTime(line: LyricsLine, offset: Int?): Long? =
    line.words.firstOrNull { offset != null && offset >= it.startIndex && offset < it.endIndex }
        ?.startTimeMs ?: line.startTimeMs

/** Labels expose source references, never guessed singer names/genders. A real
 * Text label (not color alone) also makes vocal roles available to screen readers. */
internal fun lyricRoleLabel(line: LyricsLine): String? = buildList {
    if (line.isBackground) add("Backing vocals")
    line.agent?.let { add("Voice reference: $it") }
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Keep the foreground in view during a simultaneous backing part, but allow
 * a backing-only passage to be followed. Gaps keep the existing scroll target. */
internal fun followedLyricIndex(lines: List<LyricsLine>, positionMs: Long,
                               active: Set<Int> = activeLyricIndices(lines, positionMs)): Int =
    active.filter { !lines[it].isBackground }.maxOrNull()
        ?: active.maxOrNull() ?: activeLyricIndex(lines, positionMs)

/**
 * Unsynced lyric text as stanzas of **original line indices**: blank lines
 * separate groups, exactly as the source text intends, so the unsynced
 * presentation can space verses the way the synced list spaces timed lines
 * instead of running one undifferentiated column. Indices (not copied
 * strings) keep every downstream index-aligned lookup — romanization and
 * transliteration results are keyed by source line — correct while blank
 * lines still group but never render. Pure, and shared with the tests.
 */
internal fun lyricStanzaIndices(lines: List<String>): List<List<Int>> {
    val stanzas = mutableListOf<MutableList<Int>>()
    var current = mutableListOf<Int>()
    stanzas.add(current)
    lines.forEachIndexed { index, raw ->
        if (raw.isBlank()) {
            if (current.isNotEmpty()) {
                current = mutableListOf()
                stanzas.add(current)
            }
        } else {
            current.add(index)
        }
    }
    return stanzas.filter { it.isNotEmpty() }
}
