package dev.dhun.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ReorderableList — drag-reorder (long-press on the handle) + optional
 * swipe-left-to-remove. Built on compose.foundation only (no third-party
 * reorderable dependency) so it behaves identically on Android and Desktop.
 *
 * Used by the FullPlayer queue tab and the local playlist editor.
 *
 * Flow contract: this widget keeps a local visual copy of [items] while the
 * user drags; on drag end it calls [onMove] exactly once with the queue-space
 * indices. The caller is expected to apply the mutation through the owning
 * player/repository, which then emits the new [items] — the widget resyncs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableList(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    onSwipeRemove: ((index: Int, item: T) -> Unit)? = null,
    onItemClick: ((index: Int, item: T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 68.dp,
    reorderEnabled: Boolean = true,
    swipeRemoveEnabled: Boolean = true,
    highlightIndex: Int = -1,
    contentPadding: PaddingValues = PaddingValues(vertical = DhunSpacing.sm),
    itemContent: @Composable (
        index: Int,
        item: T,
        dragHandle: Modifier,
        isDragging: Boolean,
        isHighlighted: Boolean,
    ) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }

    // Local visual copy with stable keys so LazyColumn item state (and the
    // in-flight press/drag gesture) survives reorders.
    class Row(val key: Long, val item: T)
    // Snapshot list so rebuilds trigger recomposition; stable keys let the
    // in-flight drag gesture survive reorders.
    val rows = remember { androidx.compose.runtime.mutableStateListOf<Row>() }
    var nextKey by remember { mutableLongStateOf(0L) }
    // Index-sensitive signature: rebuild visual copy whenever order/content changes.
    val signature = remember(items) { items.fold(items.size) { acc, e -> 31 * acc + (e?.hashCode() ?: 0) } }
    LaunchedEffect(signature) {
        rows.clear()
        items.forEach { rows += Row(nextKey++, it) }
    }

    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var dragGrabIndex by remember { mutableIntStateOf(-1) }   // where the item was grabbed
    var dragCurrentIndex by remember { mutableIntStateOf(-1) } // visual target as the drag moves
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }  // pointer offset from grab point

    fun finishDrag(commit: Boolean) {
        val key = draggingKey
        if (key != null) {
            if (commit) {
                val from = dragGrabIndex
                val to = rows.indexOfFirst { it.key == key }
                if (from in items.indices && to in rows.indices && from != to) {
                    onMove(from, to)
                }
            }
        }
        draggingKey = null
        dragGrabIndex = -1
        dragCurrentIndex = -1
        dragOffsetPx = 0f
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            val isDragging = row.key == draggingKey
            // The row already moved inside `rows`; cancel its list displacement
            // so it visually follows the pointer instead.
            val visualOffset =
                if (isDragging) dragOffsetPx - (dragCurrentIndex - dragGrabIndex) * rowHeightPx else 0f

            val liveIndex by rememberUpdatedState(index)
            val liveItem by rememberUpdatedState(row.item)

            val dragHandle = if (reorderEnabled) {
                Modifier.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            val grabbed = rows.indexOfFirst { it.key == row.key }
                            if (grabbed >= 0) {
                                draggingKey = row.key
                                dragGrabIndex = grabbed
                                dragCurrentIndex = grabbed
                                dragOffsetPx = 0f
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingKey != row.key) return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragOffsetPx += dragAmount.y
                            val shift = (dragOffsetPx / rowHeightPx).roundToInt()
                            val target = (dragGrabIndex + shift).coerceIn(0, rows.lastIndex)
                            val currentOfRow = rows.indexOfFirst { it.key == row.key }
                            if (target != currentOfRow && currentOfRow >= 0) {
                                rows.add(target, rows.removeAt(currentOfRow))
                                dragCurrentIndex = target
                            }
                        },
                        onDragEnd = { finishDrag(commit = true) },
                        onDragCancel = { finishDrag(commit = false) },
                    )
                }
            } else {
                Modifier
            }

            // ---- swipe-to-remove layer ------------------------------------
            Box(modifier = Modifier.fillMaxWidth()) {
                var rowWidthPx by remember { mutableFloatStateOf(0f) }
                val swipeOffset = remember(row.key) { Animatable(0f) }
                val removing by rememberUpdatedState(onSwipeRemove)

                if (onSwipeRemove != null && swipeOffset.value < -rowHeightPx / 4f) {
                    Row(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(dev.dhun.design.DhunShapes.small)
                            .background(DhunColors.errorContainer)
                            .padding(horizontal = DhunSpacing.lg),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Remove",
                            color = DhunColors.error,
                            fontSize = 13.sp,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .onSizeChanged { rowWidthPx = it.width.toFloat() }
                        .zIndex(if (isDragging) 1f else 0f)
                        .animateItem()
                        .graphicsLayer {
                            translationX = swipeOffset.value
                            translationY = visualOffset
                        }
                        .then(if (isDragging) Modifier.shadow(DhunSpacing.md) else Modifier)
                        .background(if (isDragging) DhunColors.surfaceElevated else androidx.compose.ui.graphics.Color.Transparent)
                        .then(
                            if (onItemClick != null) {
                                Modifier.clickable { onItemClick.invoke(liveIndex, liveItem) }
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (onSwipeRemove != null && swipeRemoveEnabled) {
                                Modifier.pointerInput(row.key) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, dx ->
                                            change.consume()
                                            scope.launch {
                                                swipeOffset.snapTo(
                                                    (swipeOffset.value + dx)
                                                        .coerceIn(-rowWidthPx, 0f),
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            scope.launch {
                                                val threshold = rowWidthPx * 0.35f
                                                if (rowWidthPx > 0 && swipeOffset.value < -threshold) {
                                                    swipeOffset.animateTo(
                                                        -rowWidthPx,
                                                        tween(DhunAnimations.fast),
                                                    )
                                                    removing?.invoke(liveIndex, liveItem)
                                                } else {
                                                    swipeOffset.animateTo(0f, tween(DhunAnimations.fast))
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch { swipeOffset.animateTo(0f, tween(DhunAnimations.fast)) }
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    itemContent(index, row.item, dragHandle, isDragging, index == highlightIndex)
                }
            }
        }
    }
}

/** Standard drag handle grip ("≡"-ish) used by both queue and playlist rows. */
@Composable
fun DragHandleGrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(DhunSpacing.xs)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "≡", color = DhunColors.textTertiary, fontSize = 20.sp)
    }
}
