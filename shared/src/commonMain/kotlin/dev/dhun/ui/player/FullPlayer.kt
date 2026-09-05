package dev.dhun.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunIconButton
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.player.SkipDirection
import kotlinx.coroutines.withTimeoutOrNull

/**
 * FullPlayer — the Phase 08 showstopper.
 *
 * Layout (top → bottom): collapse bar · sliding artwork (blurred-artwork
 * background + scrim behind everything) · title/artist · custom seek bar
 * (4dp→8dp drag, thumb on touch only) · transport (shuffle / prev|hold-seek /
 * play-pause morph / next|hold-seek / repeat-cycle) · volume (desktop) ·
 * Lyrics | Queue | Related tabs.
 *
 * Choreography: artwork slides in skip direction with fade, background color
 * crossfades 500ms, title fades. BACK/⌄ collapses — never exits the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayer(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    isDesktop: Boolean = false,
    onOverflowTrack: (Track) -> Unit = {},
    onOpenArtist: (Track) -> Unit = {},
    onOpenAlbum: (Track) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val skipDirection by viewModel.skipDirection.collectAsState()

    val current = track
    val colors = remember(current?.thumbnailUrl, current?.id) {
        ArtworkColorExtractor.extractFromSeed(current?.thumbnailUrl ?: current?.id ?: "")
    }
    val bgTintTop by animateColorAsState(
        targetValue = colors.primary.copy(alpha = 0.28f),
        animationSpec = DhunAnimations.slowTween(),
        label = "bgTintTop",
    )
    val bgTintMid by animateColorAsState(
        targetValue = colors.primary.copy(alpha = 0.10f),
        animationSpec = DhunAnimations.slowTween(),
        label = "bgTintMid",
    )
    val accent by animateColorAsState(
        targetValue = colors.primary,
        animationSpec = DhunAnimations.slowTween(),
        label = "accent",
    )

    var selectedTab by remember { mutableIntStateOf(1) } // Queue by default

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DhunColors.background)
            // Swallow gestures so taps don't fall through to the library below.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .safeDrawingPadding(),
    ) {
        // ---- background: real blurred artwork -----------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(DhunSpacing.glassBlur * 3),
        ) {
            Crossfade(
                targetState = current?.thumbnailUrl,
                animationSpec = DhunAnimations.slowTween(),
                label = "bgArtwork",
            ) { url ->
                ArtworkImage(
                    imageUrl = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // Artwork-driven tint + dark scrim so controls stay legible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(bgTintTop, bgTintMid, DhunColors.background),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(DhunColors.scrim, DhunColors.scrim, DhunColors.background),
                    ),
                ),
        )

        // ---- foreground -------------------------------------------------------------
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: collapse / label / overflow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DhunSpacing.huge)
                    .padding(horizontal = DhunSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunIconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "Collapse player",
                ) {
                    DhunIconView(
                        icon = DhunIcon.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = DhunColors.textTertiary,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                DhunIconButton(
                    onClick = { current?.let(onOverflowTrack) },
                    enabled = current != null,
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = "More player actions",
                ) {
                    DhunIconView(
                        icon = DhunIcon.MoreVert,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = DhunColors.textSecondary,
                    )
                }
            }

            // Artwork with skip-direction slide + playing scale spring
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
                    .padding(horizontal = DhunSpacing.xxxl),
                contentAlignment = Alignment.Center,
            ) {
                val artworkScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0.84f,
                    animationSpec = DhunAnimations.springSpec(),
                    label = "artworkScale",
                )
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        if (skipDirection == SkipDirection.BACKWARD) {
                            (slideInHorizontally(DhunAnimations.mediumTween()) { -it / 3 } + fadeIn(DhunAnimations.mediumTween()))
                                .togetherWith(
                                    slideOutHorizontally(DhunAnimations.mediumTween()) { it / 3 } +
                                        fadeOut(DhunAnimations.mediumTween()),
                                )
                        } else {
                            (slideInHorizontally(DhunAnimations.mediumTween()) { it / 3 } + fadeIn(DhunAnimations.mediumTween()))
                                .togetherWith(
                                    slideOutHorizontally(DhunAnimations.mediumTween()) { -it / 3 } +
                                        fadeOut(DhunAnimations.mediumTween()),
                                )
                        }
                    },
                    label = "artworkChange",
                ) { t ->
                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        ArtworkImage(
                            imageUrl = t?.thumbnailUrl,
                            contentDescription = t?.title,
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .aspectRatio(1f)
                                .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale },
                            shape = DhunShapes.extraLarge,
                        )
                    }
                }
            }

            // Title / artist (fade-update via Crossfade)
            Crossfade(
                targetState = current,
                animationSpec = DhunAnimations.mediumTween(),
                label = "titleFade",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.xxl),
            ) { t ->
                Column {
                    Text(
                        text = t?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = DhunColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = t?.artistName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            enabled = t != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { t?.let(onOpenArtist) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(DhunSpacing.md))

            // Seek bar + time labels
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.xxl),
            ) {
                DhunSeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accent = accent,
                    onSeek = viewModel::seekTo,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.xs))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatMs(positionMs, durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = DhunColors.textTertiary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (durationMs > 0) formatMs(durationMs, durationMs) else "--:--",
                        style = MaterialTheme.typography.labelSmall,
                        color = DhunColors.textTertiary,
                    )
                }
            }

            // Transport row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .padding(horizontal = DhunSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Shuffle
                DhunIconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier
                        .size(DhunSpacing.touchTarget)
                        .clip(DhunShapes.full)
                        .background(if (shuffleEnabled) accent.copy(alpha = 0.22f) else Color.Transparent),
                    contentDescription = if (shuffleEnabled) "Disable shuffle" else "Enable shuffle",
                ) {
                    DhunIconView(
                        icon = DhunIcon.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = if (shuffleEnabled) accent else DhunColors.textPrimary,
                    )
                }

                HoldTapTransportButton(
                    forward = false,
                    icon = DhunIcon.SkipPrevious,
                    contentDescription = "Previous track",
                    onTap = { viewModel.previous() },
                    onHold = { viewModel.beginHoldSeek(forward = false) },
                    onRelease = { viewModel.endHoldSeek() },
                )

                // Play / pause — animated morph inside the accent disc
                val glowAlpha by animateFloatAsState(
                    targetValue = if (isPlaying) 0.35f else 0f,
                    animationSpec = DhunAnimations.mediumTween(),
                    label = "playGlow",
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(DhunSpacing.lg, DhunShapes.full, clip = false)
                        .clip(DhunShapes.full)
                        .background(accent)
                        .clickable { viewModel.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (glowAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = glowAlpha }
                                .background(Color.White),
                        )
                    }
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = DhunAnimations.mediumTween(),
                        label = "playPauseMorph",
                    ) { playing ->
                        DhunIconView(
                            icon = if (playing) DhunIcon.Pause else DhunIcon.Play,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(DhunSpacing.iconSizeLg),
                            tint = DhunColors.onAccent,
                        )
                    }
                }

                HoldTapTransportButton(
                    forward = true,
                    icon = DhunIcon.SkipNext,
                    contentDescription = "Next track",
                    onTap = { viewModel.next() },
                    onHold = { viewModel.beginHoldSeek(forward = true) },
                    onRelease = { viewModel.endHoldSeek() },
                )

                // Repeat cycle: OFF → ALL → ONE
                DhunIconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier.size(DhunSpacing.touchTarget),
                    contentDescription = when (repeatMode) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                ) {
                    DhunIconView(
                        icon = if (repeatMode == RepeatMode.ONE) DhunIcon.RepeatOne else DhunIcon.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSize),
                        tint = if (repeatMode != RepeatMode.OFF) accent else DhunColors.textTertiary,
                    )
                }
            }

            // Volume (desktop only — Android uses hardware keys)
            if (isDesktop) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhunSpacing.xxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DhunIconView(
                        icon = DhunIcon.VolumeUp,
                        contentDescription = "Volume",
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.textSecondary,
                    )
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    Slider(
                        value = volume,
                        onValueChange = viewModel::setVolume,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = DhunColors.border,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(DhunSpacing.xs))

            // Bottom tabs: Lyrics | Queue | Related
            PlayerTabRow(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
                accent = accent,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f),
            ) {
                PlayerTabContent(
                    tab = selectedTab,
                    viewModel = viewModel,
                    accent = accent,
                )
            }
        }
    }
}

/* ---------------- seek bar ------------------------------------------------ */

/**
 * Custom seek bar: 4dp resting track, grows to 8dp while dragging; thumb is
 * drawn only while the pointer is down. Tap = seek; horizontal drag = scrub.
 */
@Composable
internal fun DhunSeekBar(
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val effective = if (dragging) dragFraction else progress
    val barHeight by animateDpAsState(
        targetValue = if (dragging) DhunSpacing.progressHeightActive else DhunSpacing.progressHeight,
        animationSpec = DhunAnimations.fastTween(),
        label = "seekBarHeight",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(DhunShapes.full)
                .background(DhunColors.border)
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        if (durationMs > 0 && widthPx > 0) {
                            onSeek(((offset.x / widthPx) * durationMs).toLong().coerceIn(0, durationMs))
                        }
                    }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (durationMs > 0 && widthPx > 0) {
                                dragging = true
                                dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (widthPx > 0) {
                                dragFraction = (dragFraction + dragAmount / widthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            if (dragging) onSeek((dragFraction * durationMs).toLong())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(effective)
                    .fillMaxHeight()
                    .clip(DhunShapes.full)
                    .background(accent),
            )
            // Thumb — only while touching
            if (dragging) {
                val thumbPx = 14.dp
                val thumbXPx = (effective * (widthPx)).toInt()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((thumbXPx - with(this) { thumbPx.roundToPx() } / 2).coerceAtLeast(0), 0) }
                        .size(thumbPx)
                        .clip(DhunShapes.full)
                        .background(accent),
                )
            }
        }
    }
}

/** Prev/next with hold-to-seek: tap = skip, hold ≥350ms = continuous seek. */
@Composable
internal fun HoldTapTransportButton(
    forward: Boolean,
    icon: DhunIcon,
    contentDescription: String,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.25f else 1f,
        animationSpec = DhunAnimations.fastTween(),
        label = "holdScale",
    )
    Box(
        modifier = modifier
            .size(DhunSpacing.touchTarget)
            .clip(DhunShapes.full)
            .pointerInput(forward) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val released = withTimeoutOrNull(HOLD_DELAY_MS) {
                        waitForUpOrCancellation()
                        true
                    } ?: false
                    if (released) {
                        pressed = false
                        onTap()
                    } else {
                        onHold()
                        waitForUpOrCancellation()
                        pressed = false
                        onRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DhunIconView(
            icon = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(DhunSpacing.iconSizeLg)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            tint = DhunColors.textPrimary,
        )
    }
}

private const val HOLD_DELAY_MS = 350L

internal fun formatMs(ms: Long, referenceMs: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (referenceMs / 1000 >= 3600) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
