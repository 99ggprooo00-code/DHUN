package dev.dhun.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Horizontal rails — the *one* implementation behind every sideways shelf in
 * DHUN (Home shelves / quick picks / chip rows, search result rails, artist
 * albums / singles / featured / related, the accent chip row).
 *
 * ## Why this exists (Windows, Phase 16)
 *
 * On Desktop the mouse wheel could not move any of them, and neither could
 * click-hold-and-drag, which left a Windows user with no way to reach the items
 * off the right edge of a rail. Both facts are Compose Multiplatform 1.8.2
 * behaviour, read from the shipped sources rather than assumed:
 *
 * 1. **A vertical wheel never scrolls a horizontal container.** The mouse-wheel
 *    handler asks the container whether it can consume the delta *on its own
 *    axis only* — `ScrollingLogic.canConsumeDelta` projects the delta with
 *    `toFloat()` (`x` for `Orientation.Horizontal`) and bails when it is zero
 *    ("It means that it's for another axis and cannot be consumed",
 *    `MouseWheelScrollable.kt`). The rail declines, so the enclosing vertical
 *    `LazyColumn` scrolls the page instead. **That is left alone on purpose:**
 *    a mouse wheel is a vertical control, and nothing here turns a vertical
 *    wheel into sideways motion.
 * 2. **Mouse drag is deliberately rejected.** `Modifier.scrollable` gates its
 *    drag detector on `CanDragCalculation = { change -> change.type !=
 *    PointerType.Mouse }` (`Scrollable.kt`, beside the upstream TODO "provide
 *    public way to drag by mouse"), and the desktop mediator reports every
 *    mouse event as `PointerType.Mouse`. Touch drags work; mouse drags do not.
 *    [dhunMouseDragScroll] fills exactly that gap, for horizontal containers
 *    only.
 * 3. **Trackpads already work, and keep working.** Windows delivers a
 *    two-finger horizontal pan as `WM_MOUSEHWHEEL`, AWT flags it with
 *    `SHIFT_DOWN_MASK` (`awt_Component.cpp`), and the desktop mediator maps a
 *    shift-held wheel event to an x delta (`ComposeSceneMediator.desktop.kt`),
 *    which the rail's own `scrollable` consumes natively — as it does
 *    Shift+wheel and macOS precise-wheel pans. Nothing here observes
 *    `PointerEventType.Scroll`, so those keep their native path *and* their
 *    native smooth-scroll animation.
 *
 * So the two things a Windows mouse user was missing are added here: hold the
 * pointer and slide the rail, plus a **glassy scrollbar** under it that is
 * draggable and shows how much is off-screen.
 */

/**
 * The horizontal rail used by Home, Search and the browse pages.
 *
 * A thin wrapper over [LazyRow] adding the two desktop affordances above; it
 * changes nothing on Android, where touch drag and fling already work.
 *
 * @param mouseDragScrollEnabled hold-and-slide with a mouse. Turn off only for
 *   a rail whose items own a horizontal drag of their own.
 * @param scrollbarEnabled glassy thumb under the rail. It appears only when the
 *   content actually overflows the viewport, so a rail that fits reserves no
 *   space and looks exactly as it did before.
 */
@Composable
fun DhunHorizontalRail(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(DhunSpacing.zero),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    mouseDragScrollEnabled: Boolean = true,
    scrollbarEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .dhunMouseDragScroll(state, enabled = mouseDragScrollEnabled),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content,
        )
        if (scrollbarEnabled) {
            // Read straight from the list state: layoutInfo is snapshot-backed,
            // so this recomposes when the rail is measured or scrolled and
            // needs no extra observer.
            val metrics = lazyRailMetrics(state)
            val thumb = metrics?.let {
                RailScrollbarGeometry.thumb(
                    contentExtentPx = it.contentExtentPx,
                    viewportPx = it.viewportPx,
                    scrollOffsetPx = it.scrollOffsetPx,
                )
            }
            if (metrics != null && thumb != null) {
                DhunRailScrollbar(
                    scrollable = state,
                    thumb = thumb,
                    metrics = metrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DhunSpacing.xs),
                )
            }
        }
    }
}

/**
 * Hold the pointer and slide, horizontally, for any container whose scroll
 * state is a [ScrollableState] — a `LazyRow` through [DhunHorizontalRail], or
 * the `Row` + `horizontalScroll` pairs in the appearance panel and the player's
 * transport row.
 *
 * Only `PointerType.Mouse` pointers are taken over: touch and pen keep the
 * framework's own drag + fling, so Android behaviour is untouched by
 * construction. The gesture waits for touch slop before consuming anything, so
 * a click on a card inside the rail stays a click — only a real sideways drag
 * cancels it.
 */
fun Modifier.dhunMouseDragScroll(
    state: ScrollableState,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                change.consume()
                // Content follows the pointer, exactly like the touch path.
                // dispatchRawDelta, not scrollBy: it is synchronous, so the
                // rail tracks the cursor on the same frame, and neither
                // PointerInputScope nor AwaitPointerEventScope is a
                // CoroutineScope in Compose 1.8 (a `launch` here would bind to
                // the deprecated scope-less global and not compile).
                state.dispatchRawDelta(-overSlop)
            } ?: return@awaitEachGesture
            horizontalDrag(slopChange.id) { change ->
                val dragged = change.positionChange().x
                if (dragged != 0f) {
                    change.consume()
                    state.dispatchRawDelta(-dragged)
                }
            }
        }
    }
}

/**
 * Glassy scrollbar for a horizontal rail: a hairline glass track with a pill
 * thumb that fades in while the rail moves, is dragged or is hovered, and fades
 * back out shortly after. Dragging the thumb scrolls the rail.
 */
@Composable
private fun DhunRailScrollbar(
    scrollable: ScrollableState,
    thumb: RailThumb,
    metrics: RailMetrics,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = dragging || hovered || scrollable.isScrollInProgress
    var shown by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active) {
            shown = true
        } else {
            delay(SCROLLBAR_LINGER_MS)
            shown = false
        }
    }
    val thumbAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = DhunAnimations.mediumTween(),
        label = "railScrollbar",
    )
    BoxWithConstraints(
        modifier = modifier.height(DhunSpacing.railScrollbar),
    ) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        // Track: the faintest glass fill, so an idle rail reads as "scrollable"
        // without drawing a chrome bar under the artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(thumbAlpha)
                .clip(DhunShapes.full)
                .background(DhunColors.glassHighlight),
        )
        val thumbWidthPx = (trackWidthPx * thumb.fraction).coerceAtLeast(1f)
        val thumbOffsetPx = (trackWidthPx - thumbWidthPx) * thumb.offsetFraction
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { thumbWidthPx.toDp() })
                .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .alpha(thumbAlpha)
                .clip(DhunShapes.full)
                .background(if (dragging || hovered) DhunColors.accent else DhunColors.glassStrong)
                .hoverable(interactionSource)
                .draggable(
                    state = rememberDraggableState { delta ->
                        // Same synchronous path as the rail drag above.
                        scrollable.dispatchRawDelta(
                            RailScrollbarGeometry.scrollDeltaFor(
                                thumbDragPx = delta,
                                contentExtentPx = metrics.contentExtentPx,
                                trackWidthPx = trackWidthPx,
                            ),
                        )
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragging = true },
                    onDragStopped = { dragging = false },
                ),
        )
    }
}

/** Where a rail's thumb sits: [fraction] of the track it covers, [offsetFraction] along it. */
data class RailThumb(val fraction: Float, val offsetFraction: Float)

/** A rail's scroll geometry in pixels, as measured by [lazyRailMetrics]. */
data class RailMetrics(
    val contentExtentPx: Float,
    val viewportPx: Float,
    val scrollOffsetPx: Float,
)

/**
 * Pure scrollbar geometry, kept apart from the composable so the numbers are
 * pinned by `HorizontalRailTest` instead of eyeballed on one window size.
 */
object RailScrollbarGeometry {

    /**
     * Narrowest thumb, as a fraction of the track. A rail with a hundred items
     * would otherwise get a 3px sliver that is impossible to grab.
     */
    const val MIN_THUMB_FRACTION = 0.05f

    /**
     * Thumb geometry, or `null` when the rail does not overflow (nothing to
     * show, and the caller must reserve no space).
     */
    fun thumb(contentExtentPx: Float, viewportPx: Float, scrollOffsetPx: Float): RailThumb? {
        if (!contentExtentPx.isFinite() || contentExtentPx <= 0f) return null
        if (!viewportPx.isFinite() || viewportPx <= 0f) return null
        // 1px of slack: sub-pixel measurement noise must not raise a scrollbar
        // on a rail that exactly fits its window.
        if (contentExtentPx <= viewportPx + 1f) return null
        val fraction = (viewportPx / contentExtentPx).coerceIn(MIN_THUMB_FRACTION, 1f)
        val scrollable = contentExtentPx - viewportPx
        val offsetFraction = if (scrollOffsetPx.isFinite()) {
            (scrollOffsetPx / scrollable).coerceIn(0f, 1f)
        } else {
            0f
        }
        return RailThumb(fraction = fraction, offsetFraction = offsetFraction)
    }

    /**
     * Content scroll for a thumb drag of [thumbDragPx]. The thumb travels the
     * track (≈ the viewport), so one track-width of drag must cover the whole
     * content extent — otherwise crossing a long rail takes several drags.
     */
    fun scrollDeltaFor(thumbDragPx: Float, contentExtentPx: Float, trackWidthPx: Float): Float {
        if (!thumbDragPx.isFinite() || thumbDragPx == 0f) return 0f
        if (!contentExtentPx.isFinite() || contentExtentPx <= 0f) return 0f
        if (!trackWidthPx.isFinite() || trackWidthPx <= 0f) return 0f
        return thumbDragPx * (contentExtentPx / trackWidthPx)
    }
}

/**
 * Scroll geometry for a [LazyListState] rail.
 *
 * A lazy list exposes no total content extent, so it is reconstructed from the
 * mean measured item size plus the layout's own spacing and content padding —
 * exact for the uniform card rails this is used on (albums, artists, playlists,
 * chips) and close for a mixed one, where the thumb is still a fair "how much is
 * left" cue.
 */
internal fun lazyRailMetrics(state: LazyListState): RailMetrics? {
    val info = state.layoutInfo
    val items = info.visibleItemsInfo
    if (items.isEmpty()) return null
    val averageItemPx = items.map { it.size }.average().toFloat()
    if (!averageItemPx.isFinite() || averageItemPx <= 0f) return null
    val count = info.totalItemsCount
    val spacingPx = info.mainAxisItemSpacing.toFloat()
    val paddingPx = (info.beforeContentPadding + info.afterContentPadding).toFloat()
    val viewportPx =
        (info.viewportEndOffset - info.viewportStartOffset).toFloat() - paddingPx
    if (viewportPx <= 0f) return null
    val contentExtentPx = count * averageItemPx +
        spacingPx * (count - 1).coerceAtLeast(0) +
        paddingPx
    val scrollOffsetPx = items.first().index * (averageItemPx + spacingPx) +
        state.firstVisibleItemScrollOffset
    return RailMetrics(
        contentExtentPx = contentExtentPx,
        viewportPx = viewportPx,
        scrollOffsetPx = scrollOffsetPx,
    )
}

/** The thumb lingers this long after the rail stops moving before fading out. */
private const val SCROLLBAR_LINGER_MS = 900L
