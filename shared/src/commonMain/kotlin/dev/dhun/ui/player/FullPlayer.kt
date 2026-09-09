package dev.dhun.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.ArtworkUrls
import dev.dhun.design.BlurredArtworkCache
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.GlassBottomBar
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.player.SkipDirection

/**
 * FullPlayer — the immersive, full-screen Now Playing view.
 *
 * The now-playing artwork **is** the screen: a sharp full-bleed copy of the
 * artwork (slide + fade on track change) sits over a once-per-track blurred,
 * enlarged bleed of the same image, and a smooth bottom scrim fades into the
 * surface so the overlaid chrome — title, artist, progress bar, transport —
 * stays legible without boxing the art into a card.
 *
 * Layout (top → bottom): collapse strip (drag down to dismiss) · open
 * artwork field · bottom overlay: busy/error state · title + artist with
 * overflow & favourite chips · progress · previous/play/next · volume
 * (desktop) · queue / shuffle / repeat / lyrics action row.
 *
 * **Queue panel:** the queue glyph opens a glass bottom sheet
 * (Queue | Related) that docks above the chrome; the artwork stays
 * full-bleed behind it.
 *
 * **Lyrics-dominant mode (ADR-002 P6):** the lyrics glyph (CC) recedes the
 * sharp artwork to a darkened blur and raises a rounded card carrying the
 * synced lyrics — still the same screen, no navigation. Material 3 only:
 * translucent surfaces + the existing [Modifier.blur] pipeline. **No Liquid
 * Glass.**
 *
 * **Blur-once backdrop (ADR-002 P4):** the blurred bleed lives in a subtree
 * keyed by [BlurredArtworkCache.keyFor], so it is prepared once per track —
 * never per frame, and never as two stacked blurred layers during a
 * transition.
 *
 * Choreography: artwork slides in the skip direction with fade, the dim
 * crossfades 500ms, the title fades. BACK / chevron / swipe-down collapses —
 * never exits the app.
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
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: (Track) -> Unit = {},
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
    val queueIndex by viewModel.currentQueueIndex.collectAsState()

    val current = track
    val isFavorite = current?.id?.let { it in favoriteIds } ?: false
    val playbackError = state as? PlaybackState.Error
    var showErrorDetails by remember(playbackError) { mutableStateOf(false) }
    if (showErrorDetails && playbackError != null) {
        PlaybackErrorDialog(playbackError, onDismiss = { showErrorDetails = false }, onRetry = viewModel::retry)
    }
    // Controls use the tamed accent, never the raw artwork colour — a raw
    // hue put a red play disc + red volume slider on screen (device report).
    val colors = remember(current?.thumbnailUrl, current?.id) {
        ArtworkColorExtractor.extractFromSeed(current?.thumbnailUrl ?: current?.id ?: "")
    }
    val accent by animateColorAsState(
        targetValue = colors.controlAccent,
        animationSpec = DhunAnimations.slowTween(),
        label = "accent",
    )

    // Immersive state:
    //  - selectedTab 0 = lyrics-dominant view (ADR-002 P6); 1/2 = plain tabs
    //    that live inside the queue panel;
    //  - panelOpen = the Queue/Related sheet is docked above the chrome.
    var selectedTab by rememberSaveable { mutableIntStateOf(1) } // Queue by default
    // Where the lyrics glyph returns when lyrics-dominant mode closes.
    var lastPlainTab by rememberSaveable { mutableIntStateOf(1) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    val lyricsDominant = selectedTab == LYRICS_TAB_INDEX

    // ADR-002 P4: one stable identity for the backdrop per track/artwork URL.
    val artworkCacheKey = remember(current?.thumbnailUrl, current?.id) {
        BlurredArtworkCache.keyFor(current?.thumbnailUrl, current?.id)
    }

    // The CC glyph (bottom action row) toggles lyrics-dominant mode on this
    // very screen — entering stashes the plain tab, leaving restores it.
    val onLyricsToggle = {
        panelOpen = false
        val (tab, plainTab) = toggleLyricsDominant(selectedTab, lastPlainTab)
        selectedTab = tab
        lastPlainTab = plainTab
    }
    // The queue glyph opens/closes the sheet; opening it closes lyrics first.
    val onQueueToggle = {
        if (panelOpen) {
            panelOpen = false
        } else {
            if (lyricsDominant) selectedTab = lastPlainTab
            panelOpen = true
        }
    }
    val onPanelTabSelect: (Int) -> Unit = { tab ->
        selectedTab = tab
        lastPlainTab = tab
    }
    val panelTab = if (selectedTab == LYRICS_TAB_INDEX) lastPlainTab else selectedTab

    // Chrome height feeds the queue sheet's bottom inset (same coordinate
    // space — both live inside the safe-drawing box below).
    val chromeHeightPx = remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DhunColors.background)
            // Swallow gestures so taps don't fall through to the library below.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        // ---- full-bleed immersive backdrop (runs under the status bar) ------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            ImmersiveBackdrop(
                track = current,
                skipDirection = skipDirection,
                isPlaying = isPlaying,
                lyricsDominant = lyricsDominant,
                cacheKey = artworkCacheKey,
            )
        }
        // Smooth fade into the surface: legible chrome at the bottom while the
        // artwork stays visible across the upper field.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to DhunColors.background.copy(alpha = 0.28f),
                        0.10f to Color.Transparent,
                        0.46f to Color.Transparent,
                        0.70f to DhunColors.background.copy(alpha = 0.55f),
                        0.86f to DhunColors.background.copy(alpha = 0.90f),
                        1.00f to DhunColors.background.copy(alpha = 0.97f),
                    ),
                ),
        )

        // ---- foreground (inside the safe drawing area) ------------------------
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier.widthIn(max = DhunSpacing.playerContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
            ) {
                // Collapse strip: sheet drag handle + top bar. A committed
                // downward drag past the threshold collapses (never exits the
                // app); taps and button clicks are untouched.
                val collapseSwipeThresholdPx = with(LocalDensity.current) { DhunSpacing.touchTarget.toPx() }
                var collapseDragPx by remember { mutableFloatStateOf(0f) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(collapseSwipeThresholdPx) {
                            try {
                                detectVerticalDragGestures(
                                    onDragStart = { collapseDragPx = 0f },
                                    onDragEnd = {
                                        if (shouldCollapseFullPlayer(collapseDragPx, collapseSwipeThresholdPx)) onCollapse()
                                        collapseDragPx = 0f
                                    },
                                    onDragCancel = { collapseDragPx = 0f },
                                ) { change, dragAmount ->
                                    change.consume()
                                    collapseDragPx += dragAmount
                                }
                            } finally {
                                collapseDragPx = 0f
                            }
                        },
                ) {
                    // Sheet drag handle — subtle frosted pill, rides the drag
                    // as a rubber-band affordance.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DhunSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(DhunSpacing.xxxl)
                                .height(DhunSpacing.xsPlus)
                                .graphicsLayer {
                                    translationY = collapseDragPx.coerceIn(0f, collapseSwipeThresholdPx) / 3f
                                }
                                .clip(DhunShapes.full)
                                .background(DhunColors.glassEdge),
                        )
                    }
                    // Top bar: collapse / label / (balance slot)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DhunSpacing.huge)
                            .padding(horizontal = DhunSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerChipButton(
                            icon = DhunIcon.ChevronDown,
                            contentDescription = "Collapse player",
                            tint = DhunColors.textPrimary,
                            onClick = onCollapse,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "NOW PLAYING",
                            style = DhunTypographyTokens.brand,
                            color = DhunColors.textTertiary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Invisible balance slot keeps the label centred.
                        Spacer(modifier = Modifier.size(DhunSpacing.compactTarget))
                    }
                }

                // Open artwork field. In lyrics-dominant mode the rounded
                // lyrics card rises into this space (ADR-002 P6); otherwise the
                // full-bleed artwork behind it is the entire visual.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    AnimatedVisibility(
                        visible = lyricsDominant,
                        enter = fadeIn(DhunAnimations.mediumTween()) +
                            slideInVertically(DhunAnimations.mediumTween()) { it / 4 },
                        exit = fadeOut(DhunAnimations.fastTween()) +
                            slideOutVertically(DhunAnimations.fastTween()) { it / 4 },
                    ) {
                        LyricsCard(
                            artworkUrl = ArtworkUrls.nowPlaying(current?.thumbnailUrl),
                            cacheKey = artworkCacheKey,
                            viewModel = viewModel,
                            accent = accent,
                        )
                    }
                }

                // ---- bottom chrome overlaid on the artwork ---------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { chromeHeightPx.intValue = it.height }
                        .padding(start = DhunSpacing.xxl, end = DhunSpacing.xxl, bottom = DhunSpacing.md),
                ) {
                    // Source-neutral resolving/buffering/recovery status.
                    val busyLabel = playbackBusyLabel(state)
                    if (busyLabel != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = DhunSpacing.xs)
                                .clip(DhunShapes.large)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            DhunColors.glassHighlight,
                                            DhunColors.glass,
                                        ),
                                    ),
                                )
                                .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = busyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                            )
                        }
                    }

                    // Playback failure — message + one-tap recovery inline, so
                    // the player never sits silently dead on an error.
                    if (playbackError != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = DhunSpacing.xs)
                                .clip(DhunShapes.large)
                                .background(DhunColors.errorContainer.copy(alpha = 0.55f))
                                .border(
                                    BorderStroke(DhunSpacing.border, DhunColors.borderError),
                                    DhunShapes.large,
                                )
                                .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = playbackError.message,
                                style = MaterialTheme.typography.labelMedium,
                                color = DhunColors.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { showErrorDetails = true }) {
                                Text("Details", color = DhunColors.textPrimary)
                            }
                            TextButton(onClick = viewModel::retry) {
                                Text("Retry", color = DhunColors.error)
                            }
                        }
                    }

                    // Title / artist + overflow & favourite chips (fade-update
                    // via Crossfade). The chips are the reference's circular
                    // "more / like" affordances beside the track name.
                    Crossfade(
                        targetState = current,
                        animationSpec = DhunAnimations.mediumTween(),
                        label = "titleFade",
                        modifier = Modifier.fillMaxWidth(),
                    ) { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start,
                            ) {
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
                            PlayerChipButton(
                                icon = DhunIcon.MoreVert,
                                contentDescription = "More player actions",
                                enabled = t != null,
                                tint = DhunColors.textSecondary,
                                onClick = { t?.let(onOverflowTrack) },
                            )
                            PlayerChipButton(
                                icon = if (isFavorite) DhunIcon.Favorite else DhunIcon.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                enabled = t != null,
                                tint = if (isFavorite) accent else DhunColors.textPrimary,
                                onClick = { t?.let(onToggleFavorite) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(DhunSpacing.sm))

                    // Cancel scrubbing on a new queue occurrence, even for
                    // equal-duration tracks.
                    key(current?.id, queueIndex) {
                        PlayerTimeline(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            accent = accent,
                            onSeek = { target ->
                                if (viewModel.currentTrack.value?.id == current?.id &&
                                    viewModel.currentQueueIndex.value == queueIndex
                                ) viewModel.seekTo(target)
                            },
                            enabled = current != null && state !is PlaybackState.Resolving && playbackError == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.height(DhunSpacing.sm))

                    // Main transport — plain oversized icons over the artwork
                    // (the reference's sleek look: no disc, no shadow).
                    BoxWithConstraints(
                        modifier = Modifier
                            .widthIn(max = DhunSpacing.playerTransportMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                    ) {
                        val metrics = playerTransportMetrics(maxWidth)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState(), enabled = metrics.minimumWidth > maxWidth)
                                .width(maxOf(maxWidth, metrics.minimumWidth))
                                .padding(horizontal = metrics.horizontalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            key(current?.id, queueIndex, false) {
                                HoldTapTransportButton(
                                    enabled = current != null,
                                    forward = false,
                                    icon = DhunIcon.SkipPrevious,
                                    iconSize = metrics.skipSize,
                                    contentDescription = "Previous track",
                                    onTap = { viewModel.previous() },
                                    onHold = { viewModel.beginHoldSeek(forward = false) },
                                    onRelease = { viewModel.endHoldSeek() },
                                )
                            }
                            ImmersivePlayButton(
                                state = state,
                                enabled = current != null,
                                iconSize = metrics.playSize,
                                onClick = viewModel::togglePlay,
                            )
                            key(current?.id, queueIndex, true) {
                                HoldTapTransportButton(
                                    enabled = current != null,
                                    forward = true,
                                    icon = DhunIcon.SkipNext,
                                    iconSize = metrics.skipSize,
                                    contentDescription = "Next track",
                                    onTap = { viewModel.next() },
                                    onHold = { viewModel.beginHoldSeek(forward = true) },
                                    onRelease = { viewModel.endHoldSeek() },
                                )
                            }
                        }
                    }

                    // Volume (desktop only — Android uses hardware keys)
                    if (isDesktop) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = DhunSpacing.playerVolumeMaxWidth)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally)
                                .padding(top = DhunSpacing.sm, start = DhunSpacing.xxl, end = DhunSpacing.xxl),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DhunIconView(
                                icon = DhunIcon.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                                tint = DhunColors.textSecondary,
                            )
                            Spacer(modifier = Modifier.width(DhunSpacing.sm))
                            // Bounded width: a full-bleed slider at 100% volume
                            // read as a giant coloured error bar across the
                            // window.
                            Slider(
                                value = volume,
                                onValueChange = viewModel::setVolume,
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent.copy(alpha = 0.85f),
                                    inactiveTrackColor = DhunColors.border,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(DhunSpacing.sm))

                    // Bottom action row — queue / shuffle / repeat / lyrics,
                    // the reference's sleek icon strip.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DhunSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ImmersiveIconAction(
                            icon = DhunIcon.QueueMusic,
                            contentDescription = if (panelOpen) "Close queue" else "Open queue",
                            active = panelOpen,
                            enabled = current != null,
                            accent = accent,
                            onClick = onQueueToggle,
                        )
                        ImmersiveIconAction(
                            icon = DhunIcon.Shuffle,
                            contentDescription = if (shuffleEnabled) "Disable shuffle" else "Enable shuffle",
                            active = shuffleEnabled,
                            enabled = current != null,
                            accent = accent,
                            onClick = { viewModel.toggleShuffle() },
                        )
                        ImmersiveIconAction(
                            icon = if (repeatMode == RepeatMode.ONE) DhunIcon.RepeatOne else DhunIcon.Repeat,
                            contentDescription = when (repeatMode) {
                                RepeatMode.OFF -> "Repeat off"
                                RepeatMode.ALL -> "Repeat all"
                                RepeatMode.ONE -> "Repeat one"
                            },
                            active = repeatMode != RepeatMode.OFF,
                            enabled = current != null,
                            accent = accent,
                            onClick = { viewModel.cycleRepeatMode() },
                        )
                        ImmersiveIconAction(
                            icon = DhunIcon.ClosedCaption,
                            contentDescription = if (lyricsDominant) "Exit lyrics view" else "Lyrics view",
                            active = lyricsDominant,
                            enabled = current != null,
                            accent = accent,
                            onClick = onLyricsToggle,
                        )
                    }
                }
            }

            // Queue / Related sheet — docks above the chrome while the
            // artwork stays full-bleed behind it.
            AnimatedVisibility(
                visible = panelOpen && !lyricsDominant,
                enter = slideInVertically(DhunAnimations.mediumTween()) { it } +
                    fadeIn(DhunAnimations.mediumTween()),
                exit = slideOutVertically(DhunAnimations.fastTween()) { it } +
                    fadeOut(DhunAnimations.fastTween()),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    GlassBottomBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(QUEUE_PANEL_HEIGHT_FRACTION)
                            .padding(bottom = Dp(chromeHeightPx.intValue.toFloat())),
                        shape = DhunShapes.bottomSheet,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            PanelTabRow(
                                selectedTab = panelTab,
                                onSelect = onPanelTabSelect,
                                accent = accent,
                                modifier = Modifier.padding(top = DhunSpacing.sm),
                            )
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                PlayerTabContent(
                                    tab = panelTab,
                                    viewModel = viewModel,
                                    accent = accent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lyrics tab index within the player's plain tab set (Lyrics | Queue | Related). */
internal const val LYRICS_TAB_INDEX = 0

/** Share of the screen the Queue/Related sheet occupies above the chrome. */
private const val QUEUE_PANEL_HEIGHT_FRACTION = 0.52f

/**
 * ADR-002 rule 5: the lyrics (CC) control toggles lyrics-dominant mode on the
 * same screen. From a plain tab it enters lyrics and remembers where to
 * return; from lyrics it restores that tab. Result: (newSelectedTab,
 * newLastPlainTab).
 */
internal fun toggleLyricsDominant(selectedTab: Int, lastPlainTab: Int): Pair<Int, Int> =
    if (selectedTab == LYRICS_TAB_INDEX) {
        lastPlainTab.coerceIn(1, 2) to lastPlainTab
    } else {
        LYRICS_TAB_INDEX to selectedTab.coerceIn(1, 2)
    }

/** Density-aware downward swipe commits a collapse; upward/short drags never do (P9). */
internal fun shouldCollapseFullPlayer(dragPx: Float, thresholdPx: Float): Boolean =
    dragPx.isFinite() && thresholdPx.isFinite() && thresholdPx > 0f && dragPx >= thresholdPx

/**
 * The immersive backdrop — full-bleed artwork behind the whole screen.
 *
 * Three layers, back to front:
 *  1. [PlayerBackdrop] — the blurred, enlarged bleed (blur once, ADR-002 P4),
 *     scaled past the screen edges so the blur never shows a hard rim;
 *  2. the sharp full-bleed artwork, which animates (slide + fade) on track
 *     changes. It lives OUTSIDE the keyed backdrop subtree so a track change
 *     crossfades instead of replacing the layer mid-transition;
 *  3. a dim that deepens while lyrics-dominant so the lyrics card reads.
 */
@Composable
private fun ImmersiveBackdrop(
    track: Track?,
    skipDirection: SkipDirection,
    isPlaying: Boolean,
    lyricsDominant: Boolean,
    cacheKey: String,
) {
    val dimColor by animateColorAsState(
        targetValue = Color.Black.copy(alpha = if (lyricsDominant) 0.52f else 0.30f),
        animationSpec = DhunAnimations.slowTween(),
        label = "backdropDim",
    )
    val sharpAlpha by animateFloatAsState(
        targetValue = if (lyricsDominant) 0f else 1f,
        animationSpec = DhunAnimations.mediumTween(),
        label = "sharpArtAlpha",
    )
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.03f else 1f,
        animationSpec = DhunAnimations.springSpec(),
        label = "backdropPlayScale",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Hi-res tier for the backdrop — the same Coil key as the sharp layer
        // below, so the bytes are fetched once, not twice. Keyed by the
        // track's BlurredArtworkCache key: unrelated recompositions (position
        // ticks, tab switches, dim crossfades) never restart it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            key(cacheKey) {
                PlayerBackdrop(
                    artworkUrl = ArtworkUrls.nowPlaying(track?.thumbnailUrl),
                    cacheKey = cacheKey,
                )
            }
        }
        AnimatedContent(
            targetState = track,
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
            label = "immersiveArtworkChange",
        ) { t ->
            ArtworkImage(
                imageUrl = ArtworkUrls.nowPlaying(t?.thumbnailUrl),
                contentDescription = t?.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = sharpAlpha
                        scaleX = playScale
                        scaleY = playScale
                    },
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
            )
        }
        // Depth: deepens behind the lyrics card, stays subtle in main mode.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dimColor),
        )
    }
}

/**
 * Blurred-artwork bleed (ADR-002 P4). [ImmersiveBackdrop] mounts this inside
 * `key(BlurredArtworkCache.keyFor(...))`, so every track change starts one
 * fresh layer and nothing else recomposes it — the blur radius animates in
 * exactly once per track, then the layer is static. Re-entering the player
 * for a track whose key is already prepared skips the fade-up: the backdrop
 * arrives pre-blurred, honouring the cache's once-per-track contract.
 * Scaled 1.2× past the screen edges so the blurred rim is never visible.
 */
@Composable
private fun PlayerBackdrop(
    artworkUrl: String?,
    cacheKey: String,
) {
    var prepared by remember {
        mutableStateOf(cacheKey.isNotBlank() && BlurredArtworkCache.isPrepared(cacheKey))
    }
    LaunchedEffect(cacheKey) {
        if (cacheKey.isNotBlank()) {
            BlurredArtworkCache.markPrepared(cacheKey)
            prepared = true
        }
    }
    var shown by remember { mutableStateOf(prepared) }
    LaunchedEffect(Unit) { shown = true }
    val backdropAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = DhunAnimations.slowTween(),
        label = "backdropFade",
    )
    val blurRadius by animateDpAsState(
        targetValue = if (prepared) DhunSpacing.glassBlur * 4 else DhunSpacing.zero,
        animationSpec = DhunAnimations.mediumTween(),
        label = "backdropBlurOnce",
    )
    ArtworkImage(
        imageUrl = artworkUrl,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1.2f
                scaleY = 1.2f
                alpha = backdropAlpha
            }
            .blur(blurRadius),
        shape = RectangleShape,
        contentScale = ContentScale.Crop,
    )
}

/**
 * Lyrics-dominant card (ADR-002 P6): a rounded panel carrying the synced
 * lyrics over a blurred artwork, raised into the open field while the sharp
 * backdrop recedes to a darkened blur.
 */
@Composable
private fun LyricsCard(
    artworkUrl: String?,
    cacheKey: String,
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = DhunSpacing.xl, vertical = DhunSpacing.sm)
            .clip(DhunShapes.artworkHero)
            .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), DhunShapes.artworkHero)
            .clipToBounds(),
    ) {
        // Blurred artwork inside the card — keyed like the backdrop so the
        // blur is prepared once per track, never per frame.
        key(cacheKey) {
            ArtworkImage(
                imageUrl = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(DhunSpacing.glassBlur * 2),
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
            )
        }
        // Readability scrim behind the lyric lines — adaptive: near-black in
        // dark, a light wash in light so the dark lyric text keeps contrast.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            DhunColors.background.copy(alpha = 0.42f),
                            DhunColors.background.copy(alpha = 0.62f),
                        ),
                    ),
                ),
        )
        PlayerTabContent(
            tab = LYRICS_TAB_INDEX,
            viewModel = viewModel,
            accent = accent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Circular glass chip — the reference's "more / like" affordances beside the
 * track name and the collapse control up top.
 */
@Composable
private fun PlayerChipButton(
    icon: DhunIcon,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DhunIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(DhunSpacing.compactTarget)
            .clip(DhunShapes.full)
            .background(DhunColors.glassStrong)
            .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), DhunShapes.full),
        contentDescription = contentDescription,
    ) {
        DhunIconView(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.iconSizeSm),
            tint = tint,
        )
    }
}

/**
 * Bare icon action for the bottom strip (queue / shuffle / repeat / lyrics):
 * a 48dp target with no container, tinted with the accent while active.
 */
@Composable
private fun ImmersiveIconAction(
    icon: DhunIcon,
    contentDescription: String,
    active: Boolean,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (active) accent else DhunColors.textSecondary,
        animationSpec = DhunAnimations.fastTween(),
        label = "immersiveActionTint${icon.name}",
    )
    DhunIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(DhunSpacing.touchTarget)
            .semantics { selected = active },
        contentDescription = contentDescription,
    ) {
        DhunIconView(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.iconSize),
            tint = tint,
        )
    }
}

/**
 * The big plain play/pause action — a 52dp target drawing a bare icon (no
 * disc, no shadow), matching the reference's sleek transport. Tap/keyboard/
 * accessibility activate; there is no hold behavior here.
 */
@Composable
private fun ImmersivePlayButton(
    state: PlaybackState,
    enabled: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    var keyboardPressed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed || keyboardPressed) 0.92f else 1f,
        animationSpec = DhunAnimations.fastTween(),
        label = "playScale",
    )
    Box(
        modifier = Modifier
            .size(DhunSpacing.transportTarget)
            .clip(DhunShapes.full)
            .border(
                BorderStroke(DhunSpacing.border, if (focused) DhunColors.accent else Color.Transparent),
                DhunShapes.full,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = playbackActionLabel(state)
                if (enabled) {
                    onClick(label = playbackActionLabel(state)) { currentOnClick(); true }
                } else {
                    disabled()
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) keyboardPressed = false
            }
            .onKeyEvent { event ->
                if (!enabled || !isTransportActivationKey(event.key) ||
                    event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
                ) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> keyboardPressed = true
                        KeyEventType.KeyUp -> {
                            if (keyboardPressed) currentOnClick()
                            keyboardPressed = false
                        }
                        else -> Unit
                    }
                    true // Consume key-up too; the window's Space shortcut must not also run.
                }
            }
            .focusable(enabled = enabled)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    try {
                        val released = waitForUpOrCancellation()?.also { it.consume() } != null
                        if (released) currentOnClick()
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = playbackActionIcon(state),
            animationSpec = DhunAnimations.mediumTween(),
            label = "playPauseMorph",
        ) { icon ->
            DhunIconView(
                icon = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
                tint = if (enabled) DhunColors.textPrimary else DhunColors.textDisabled,
            )
        }
    }
}

/**
 * Two-tab header inside the Queue/Related sheet (Queue | Related) — the same
 * frosted style the player's tab row used to carry, narrowed to the tabs the
 * sheet actually hosts.
 */
@Composable
private fun PanelTabRow(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DhunSpacing.touchTarget)
            .selectableGroup(),
    ) {
        listOf(1 to "Queue", 2 to "Related").forEach { (index, title) ->
            val selected = index == selectedTab
            val color by animateColorAsState(
                targetValue = if (selected) accent else DhunColors.textTertiary,
                animationSpec = DhunAnimations.fastTween(),
                label = "panelTabColor$index",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = DhunSpacing.xs)
                    .clip(DhunShapes.large)
                    .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
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
                        .width(if (selected) DhunSpacing.xxl else DhunSpacing.zero)
                        .clip(DhunShapes.full)
                        .background(if (selected) accent else Color.Transparent),
                )
            }
        }
    }
}
