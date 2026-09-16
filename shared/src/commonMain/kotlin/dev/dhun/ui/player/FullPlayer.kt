package dev.dhun.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import dev.dhun.design.components.dhunMouseDragScroll
import dev.dhun.design.components.GlassBottomBar
import dev.dhun.design.FullPlayerLayoutMode
import dev.dhun.design.fittedPlayerArtworkSize
import dev.dhun.design.fullPlayerLayoutMode
import dev.dhun.design.playerWideControlsWidth
import dev.dhun.design.supportsRealtimeBlur
import dev.dhun.design.usesCompactPlayerControls
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.player.SkipDirection

/**
 * FullPlayer — the immersive, full-screen Now Playing view.
 *
 * Two artwork layers, one source: a **fit-to-card** sharp artwork in the upper
 * field, over a once-per-track **blurred, darkened bleed** of the same image
 * that carries the whole screen and glows behind the controls at the bottom.
 *
 * Layout:
 *  1. **Stacked / portrait** — collapse strip, dominant artwork hero, then
 *     metadata / progress / transport / queue-shuffle-repeat-lyrics chrome;
 *  2. **Wide / landscape and desktop** — the same hero and control cluster
 *     share a row, so the cover uses the viewport height instead of collapsing
 *     into the strip above a tall control stack;
 *  3. the hero sizes itself from its *measured, safely padded field* through
 *     [dev.dhun.design.fittedPlayerArtworkSize]. It is `ContentScale.Fit`, so
 *     square album art stays square and non-square artwork is never stretched
 *     or unnecessarily cropped. In lyrics-dominant mode the rounded lyrics
 *     card rises into this same reusable stage.
 *
 * In stacked mode the hero is an always-emitted `weight(1f)` field, so the
 * chrome remains bottom-docked instead of drifting below the header. Busy
 * state is an overlay inside that stable field rather than a new chrome row:
 * buffering cannot steal artwork space or collapse its placeholder. The
 * chrome sits over a **blurred, darkened** copy of the art
 * ([playerAmbientScrimStops]), never over the sharp cover.
 *
 * **Queue panel:** the queue glyph opens a glass bottom sheet (Queue |
 * Related) whose height *is* the transition's travel distance
 * ([relatedSheetTravel], derived from the safe-area height and the measured
 * chrome). One interruptible progress then drives the whole screen: the sheet
 * slides up from below the clipped edge while the **entire** player
 * composition — header, cover, overflow/like chips, title, artist, timeline,
 * transport, and desktop volume — rises by that same distance over that same
 * progress, so the two boundaries meet on every frame instead of the panel
 * arriving over a stationary player. Half of the rise is absorbed by the
 * weighted artwork field ([relatedSheetLayoutRiseDp]), so the cover re-fits
 * into a thumbnail rather than being sliced off the top of the screen and the
 * collapse header keeps the swipe gesture that dismisses this screen. The
 * queue / shuffle / repeat / lyrics row the panel replaces is hidden for the
 * whole flight (space preserved, restored only once the close lands), opening
 * and closing are the same function run in opposite directions, and rows stay
 * readable at 48dp artwork + title + artist + duration with an explicit close
 * control.
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
 * never exits the app. Nothing in the sheet transition is a per-piece
 * animation: the panel, the player's rise and the action row's hide are all
 * reads of one [updateTransition] progress value.
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
    //  - panelOpen = the Queue/Related sheet is open. Its transition below is
    //    shared by the sheet and the player foreground, so they never drift
    //    into two independent animations.
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

    // One target, one progress, one travel distance. Every moving piece of the
    // transition — the sheet, the player composition rising away from it, and
    // the action row that has to stay out of the way — is a pure function of
    // that single progress, which is what makes opening the exact reversible
    // counterpart of closing: reversing the target mid-flight keeps animating
    // the same value from wherever it is, with the same geometry, instead of
    // restarting or handing a second animation anything to fight over.
    val relatedSheetTarget = panelOpen && !lyricsDominant
    val relatedTransition = updateTransition(
        targetState = relatedSheetTarget,
        label = "relatedSheetTransition",
    )
    val relatedProgress by relatedTransition.animateFloat(
        transitionSpec = { DhunAnimations.mediumTween<Float>() },
        label = "relatedSheetProgress",
    ) { open -> if (open) 1f else 0f }

    // Chrome height feeds the related sheet geometry (same coordinate space —
    // both live inside the safe-drawing box below).
    val chromeHeightPx = remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DhunColors.background)
            .clipToBounds()
            // Swallow gestures so taps don't fall through to the library below.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
        // ---- artwork backdrop (runs under the status bar) -------------------
        // Blurred once per track, darkened towards the bottom so the control
        // cluster reads over it; the sharp thumbnail never reaches down here.
        ArtworkBackdrop(
            track = current,
            lyricsDominant = lyricsDominant,
            cacheKey = artworkCacheKey,
        )

        // ---- foreground (inside the safe drawing area) --------------------
        // The whole player shares one common layout. Portrait keeps the
        // familiar artwork-above-chrome hierarchy; a short/wide viewport moves
        // the exact same control cluster beside the hero, so landscape never
        // reduces the cover to the sliver left above a vertical control stack.
        // Android needs the safe drawing inset for gesture/status bars. The
        // desktop window has no system-bar inset to reserve; using the exact
        // window bounds there keeps Windows' immersive player genuinely
        // full-window instead of shrinking it to an apparent half-screen.
        val foregroundModifier = if (isDesktop) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().safeDrawingPadding()
        }
        Box(modifier = foregroundModifier) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                // Capture constraints before entering Row/Column scopes, whose
                // own scope markers deliberately hide BoxWithConstraints' axes.
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                val density = LocalDensity.current
                // The transition's travel distance is derived from the geometry
                // this viewport actually has: the safe-area height (Android
                // status bar / cutout / gesture area already removed by
                // safeDrawingPadding, the window bounds on Desktop) minus the
                // *measured* player chrome, of which the sheet claims a share.
                // Nothing here is a guessed screen distance.
                val measuredTravelDp = relatedSheetTravel(
                    availableHeight = availableHeight,
                    chromeHeight = chromeHeightDp(chromeHeightPx.intValue, density.density),
                )
                // Captured before the opening transition begins and held for
                // its whole flight (and for the closing one), so a re-measure
                // or a resize mid-motion cannot move the goal posts under a
                // sheet that has already been mounted at its final size.
                val sheetInFlight = relatedSheetTarget || relatedProgress > 0f
                val travelDp = rememberFrozenSheetTravel(sheetInFlight, measuredTravelDp)
                val travelPx = with(density) { travelDp.toPx() }
                // One progress + one travel → both offsets, the mount window
                // and the action-row visibility. The sheet's own height is the
                // very same [travelDp], which is what makes its top edge meet
                // the rising player's lower boundary on every frame.
                val relatedSheet = relatedSheetTransition(
                    targetOpen = relatedSheetTarget,
                    progress = relatedProgress,
                    travelPx = travelPx,
                )
                val relatedMotion = relatedSheet.motion
                // The player rises by travel · progress — the sheet's own
                // travel, from the same progress. The rise is paid for in two
                // halves on purpose: this layout inset, which the weighted
                // artwork field absorbs, and the offset below, which draws the
                // composition at its new height. Together they move the header,
                // cover, metadata, timeline, transport and volume as one piece
                // while the cover *re-fits* smaller instead of being sliced off
                // the top of the screen, and the collapse header (whose swipe
                // down dismisses the player) stays inside the touch area. The
                // chrome's lower boundary still lands on height − travel ·
                // progress, which is exactly where the sheet's top edge is.
                val playerRiseDp = relatedSheetLayoutRiseDp(travelDp, relatedMotion.progress)
                val layoutMode = fullPlayerLayoutMode(availableWidth, availableHeight)
                val compactControls = usesCompactPlayerControls(availableHeight)

                @Composable
                fun Controls(compact: Boolean) {
                    PlayerControlCluster(
                        viewModel = viewModel,
                        state = state,
                        current = current,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        repeatMode = repeatMode,
                        shuffleEnabled = shuffleEnabled,
                        volume = volume,
                        queueIndex = queueIndex,
                        isDesktop = isDesktop,
                        isFavorite = isFavorite,
                        accent = accent,
                        panelOpen = relatedSheetTarget,
                        lyricsDominant = lyricsDominant,
                        actionRowVisible = relatedSheet.actionRowVisible,
                        compact = compact,
                        onOverflowTrack = onOverflowTrack,
                        onOpenArtist = onOpenArtist,
                        onToggleFavorite = onToggleFavorite,
                        onQueueToggle = onQueueToggle,
                        onLyricsToggle = onLyricsToggle,
                        onShowErrorDetails = { showErrorDetails = true },
                        onHeightChanged = { chromeHeightPx.intValue = it },
                    )
                }

                // The backdrop stays in place for continuity. Everything else —
                // header, cover, overflow/like chips, title and artist,
                // timeline, transport, and desktop volume — is one composition
                // rising by the sheet's travel, so the metadata and controls are
                // never left standing still while a panel slides past them.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = playerRiseDp)
                        .graphicsLayer { translationY = relatedMotion.playerOffsetY },
                ) {
                    when (layoutMode) {
                        FullPlayerLayoutMode.Stacked -> {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = DhunSpacing.playerContentMaxWidth)
                                    .fillMaxSize()
                                    .align(Alignment.TopCenter),
                            ) {
                                PlayerHeader(onCollapse = onCollapse)

                                // Always emitted: this weighted stage is the source
                                // of truth for the room between header and chrome.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    PlayerArtworkStage(
                                        track = current,
                                        skipDirection = skipDirection,
                                        isPlaying = isPlaying,
                                        lyricsDominant = lyricsDominant,
                                        busyLabel = playbackBusyLabel(state),
                                        artworkUrl = ArtworkUrls.nowPlaying(current?.thumbnailUrl),
                                        cacheKey = artworkCacheKey,
                                        viewModel = viewModel,
                                        accent = accent,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(
                                                horizontal = DhunSpacing.playerArtworkHorizontalInset,
                                                vertical = DhunSpacing.playerArtworkVerticalInset,
                                            ),
                                    )
                                }
                                Controls(compact = compactControls)
                            }
                        }

                        FullPlayerLayoutMode.Wide -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // The hero remains a distinct, reusable stage. The
                                // overlay header reserves only its own space; it no
                                // longer steals a whole artwork row from a landscape
                                // player or a desktop window.
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    PlayerArtworkStage(
                                        track = current,
                                        skipDirection = skipDirection,
                                        isPlaying = isPlaying,
                                        lyricsDominant = lyricsDominant,
                                        busyLabel = playbackBusyLabel(state),
                                        artworkUrl = ArtworkUrls.nowPlaying(current?.thumbnailUrl),
                                        cacheKey = artworkCacheKey,
                                        viewModel = viewModel,
                                        accent = accent,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(
                                                start = DhunSpacing.playerArtworkHorizontalInset,
                                                top = DhunSpacing.playerArtworkHeaderInset,
                                                end = DhunSpacing.playerArtworkHorizontalInset,
                                                bottom = DhunSpacing.playerArtworkVerticalInset,
                                            ),
                                    )
                                    PlayerHeader(
                                        onCollapse = onCollapse,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter),
                                    )
                                }
                                Spacer(modifier = Modifier.width(DhunSpacing.playerWideLayoutGap))
                                Box(
                                    modifier = Modifier
                                        .width(playerWideControlsWidth(availableWidth))
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Controls(compact = compactControls)
                                }
                            }
                        }
                    }
                }

                // ---- queue / related sheet ------------------------------------
                // Mounted on the first opening frame at its real, frozen height
                // (never zero-then-resize) and kept mounted until the closing
                // animation lands on zero. Same progress, same travel, opposite
                // direction: at progress 0 the sheet sits exactly one height
                // below the clipped edge, at progress 1 its top is the
                // translated player's bottom — no arbitrary offset, no gap.
                if (relatedSheet.sheetMounted) {
                    QueueSheet(
                        viewModel = viewModel,
                        selectedTab = panelTab,
                        onSelectTab = onPanelTabSelect,
                        onClose = onQueueToggle,
                        accent = accent,
                        sheetHeight = travelDp,
                        motion = relatedMotion,
                    )
                }
            }
        }
    }
}

/**
 * The persistent metadata / timeline / transport cluster. It stays in one
 * reusable composable so portrait places it below the hero while wide
 * viewports dock the exact same accessible controls beside it.
 *
 * The whole cluster rides the player composition upward when the Queue/Related
 * sheet rises — it is never counter-translated into standing still. Its own
 * bottom action row is the exception to *visibility*, not to layout:
 * [actionRowVisible] hides it for the duration of the sheet transition while
 * its measured space stays in the column, because that measurement is what the
 * sheet's travel distance is derived from.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerControlCluster(
    viewModel: PlayerViewModel,
    state: PlaybackState,
    current: Track?,
    positionMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    volume: Float,
    queueIndex: Int,
    isDesktop: Boolean,
    isFavorite: Boolean,
    accent: Color,
    panelOpen: Boolean,
    lyricsDominant: Boolean,
    actionRowVisible: Boolean,
    compact: Boolean,
    onOverflowTrack: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onQueueToggle: () -> Unit,
    onLyricsToggle: () -> Unit,
    onShowErrorDetails: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackError = state as? PlaybackState.Error
    val horizontalPadding = if (compact) DhunSpacing.md else DhunSpacing.xxl
    val bottomPadding = if (compact) DhunSpacing.xs else DhunSpacing.md
    val itemSpacing = if (compact) DhunSpacing.xs else DhunSpacing.sm
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChanged(it.height) }
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = bottomPadding),
    ) {
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
                TextButton(onClick = { onShowErrorDetails() }) {
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

        Spacer(modifier = Modifier.height(itemSpacing))

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

        Spacer(modifier = Modifier.height(itemSpacing))

        // Main transport — plain oversized icons over the blurred
        // backdrop (the reference's sleek look: no disc, no shadow).
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = DhunSpacing.playerTransportMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        ) {
            val metrics = playerTransportMetrics(maxWidth)
            // CASE B, not a carousel: this row is only scrollable when the
            // window is narrower than the transport's minimum width. It stays
            // that way (no page-wide sideways scroll); the desktop-only gain is
            // that a mouse can now hold and slide it, since Compose refuses
            // mouse drags on `scrollable` containers.
            val transportOverflows = metrics.minimumWidth > maxWidth
            val transportScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(transportScrollState, enabled = transportOverflows)
                    .dhunMouseDragScroll(transportScrollState, enabled = transportOverflows)
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
                    .padding(top = itemSpacing, start = horizontalPadding, end = horizontalPadding),
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

        Spacer(modifier = Modifier.height(itemSpacing))

        // Bottom action row — queue / shuffle / repeat / lyrics, the
        // reference's sleek icon strip. The sheet replaces exactly this strip,
        // so it must not be on screen while the panel is opening, open, or
        // closing. It is faded out (alpha only) rather than removed from the
        // column: the row's measured height keeps feeding the travel distance,
        // and dropping it would re-measure the chrome mid-motion — the exact
        // geometry change that makes a panel jump. A transparent tap-swallow
        // keeps the hidden buttons from staying hittable under the sheet, and
        // the row returns on the frame the closing animation lands on zero.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DhunSpacing.sm)
                    .graphicsLayer { alpha = if (actionRowVisible) 1f else 0f },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ImmersiveIconAction(
                    icon = DhunIcon.QueueMusic,
                    contentDescription = if (panelOpen) "Close queue" else "Open queue",
                    active = panelOpen,
                    enabled = current != null && actionRowVisible,
                    accent = accent,
                    onClick = onQueueToggle,
                )
                ImmersiveIconAction(
                    icon = DhunIcon.Shuffle,
                    contentDescription = if (shuffleEnabled) "Disable shuffle" else "Enable shuffle",
                    active = shuffleEnabled,
                    enabled = current != null && actionRowVisible,
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
                    enabled = current != null && actionRowVisible,
                    accent = accent,
                    onClick = { viewModel.cycleRepeatMode() },
                )
                ImmersiveIconAction(
                    icon = DhunIcon.ClosedCaption,
                    contentDescription = if (lyricsDominant) "Exit lyrics view" else "Lyrics view",
                    active = lyricsDominant,
                    enabled = current != null && actionRowVisible,
                    accent = accent,
                    onClick = onLyricsToggle,
                )
            }
            if (!actionRowVisible) {
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            }
        }
    }
}

/**
 * Collapse handle and title strip. In a stacked player it owns the top row;
 * in a wide player it overlays only the artwork pane, preserving vertical room
 * for the hero without changing its swipe-down/back contract.
 */
@Composable
private fun PlayerHeader(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapseSwipeThresholdPx = with(LocalDensity.current) { DhunSpacing.touchTarget.toPx() }
    var collapseDragPx by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = modifier
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
        // Sheet drag handle — subtle frosted pill, rides the drag as a
        // rubber-band affordance.
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
        // Top bar: collapse / label / (balance slot).
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
}

/**
 * The full artwork boundary shared by normal and lyrics-dominant player modes.
 * It owns the sharp art, the future-facing lyrics replacement, and transient
 * loading/buffering status without letting any of those states change the
 * stage's measured size.
 */
@Composable
private fun PlayerArtworkStage(
    track: Track?,
    skipDirection: SkipDirection,
    isPlaying: Boolean,
    lyricsDominant: Boolean,
    busyLabel: String?,
    artworkUrl: String?,
    cacheKey: String,
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clipToBounds()) {
        ArtworkHeroField(
            track = track,
            skipDirection = skipDirection,
            isPlaying = isPlaying,
            lyricsDominant = lyricsDominant,
            // Reserve real layout headroom for the existing lightweight play
            // scale and slide transition; no transform is used to fake size.
            modifier = Modifier
                .fillMaxSize()
                .padding(DhunSpacing.playerArtworkAnimationInset),
        )
        LyricsCardOverlay(
            visible = lyricsDominant,
            artworkUrl = artworkUrl,
            cacheKey = cacheKey,
            viewModel = viewModel,
            accent = accent,
            modifier = Modifier.fillMaxSize(),
        )
        PlayerStatusPill(
            label = busyLabel,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DhunSpacing.sm),
        )
    }
}

/**
 * Resolving/buffering/recovery is an overlay on the stable hero stage rather
 * than a row in the bottom cluster. That keeps the artwork at its allocated
 * size while the player is busy and announces state changes without hiding
 * transport or the progress timeline.
 */
@Composable
private fun PlayerStatusPill(
    label: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (label == null) return
    Box(
        modifier = modifier
            .clip(DhunShapes.full)
            .background(
                Brush.horizontalGradient(
                    listOf(DhunColors.glassHighlight, DhunColors.glassStrong),
                ),
            )
            .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), DhunShapes.full)
            .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Lyrics tab index within the player's plain tab set (Lyrics | Queue | Related). */
internal const val LYRICS_TAB_INDEX = 0

/**
 * Share of the room above the measured chrome that the Queue/Related sheet
 * claims. Because that share is *also* how far the player composition gives way,
 * it is deliberately modest: the metadata, timeline and transport have to stay
 * readable above the panel, and the cover must keep enough of itself to still
 * read as the album art rather than as a cropped rectangle.
 */
private const val QUEUE_PANEL_HEIGHT_FRACTION = 0.6f

/**
 * The Queue/Related sheet's height, in dp — which, because the sheet is flush
 * with the bottom of the safe area and the player rises by exactly as far as
 * the sheet does, is simultaneously this transition's **single travel
 * distance**.
 *
 * Both halves of that sentence matter. The previous design measured the chrome
 * too, but used the measurement to park the sheet *above* the control cluster
 * as a footer, so the panel rose over a player whose metadata and transport
 * never moved. Making the sheet's height the travel removes the choice: one
 * number sizes the panel, lifts the player, and is the only number the seam
 * between them can be computed from.
 *
 * [availableHeight] is the safe-area height actually handed to the player
 * (Android status bar / cutout / gesture inset already subtracted by
 * `safeDrawingPadding`; the whole window on Desktop) and [chromeHeight] is the
 * measured control cluster, already density-corrected by [chromeHeightDp]. The
 * sheet never grows past the room above the chrome — that would bury the
 * transport under the panel meant to sit beside it — and stays inside
 * [DhunSpacing.queuePanelPlayerBandFloor] of player band, so a short viewport
 * shrinks the panel instead of shoving the whole composition off the top. It
 * never drops below [DhunSpacing.queuePanelMinHeight] while there is room for
 * that much; a smaller viewport takes what it has rather than overflowing.
 */
internal fun relatedSheetTravel(
    availableHeight: Dp,
    chromeHeight: Dp,
    fraction: Float = QUEUE_PANEL_HEIGHT_FRACTION,
): Dp {
    // An unbounded or unmeasured axis means there is nothing to derive from:
    // answer "no travel", which keeps the panel unmounted and the player in its
    // untouched layout rather than animating against a fictional number.
    if (!availableHeight.value.isFinite() || !chromeHeight.value.isFinite()) return DhunSpacing.zero
    val available = finitePlayerDimensionDp(availableHeight)
    val chrome = finitePlayerDimensionDp(chromeHeight).coerceIn(DhunSpacing.zero, available)
    // The room the player can vacate without its own footer being buried: the
    // chrome rides the rise, so everything below this line disappears under
    // the panel.
    val room = available - chrome
    if (room <= DhunSpacing.zero) return DhunSpacing.zero
    // …and a viewport that short must not lose the cover entirely to the sheet
    // floor, so the rise is additionally capped by the band the composition
    // needs to stay legible. The readable-panel floor still wins when the two
    // cannot both be satisfied: a queue you can scroll beats a cover you can
    // only see three rows of.
    val bandCap = (available - chrome - DhunSpacing.queuePanelPlayerBandFloor).coerceAtLeast(DhunSpacing.zero)
    val floor = DhunSpacing.queuePanelMinHeight.coerceAtMost(room)
    val desired = room * fraction.coerceIn(0f, 1f)
    return desired.coerceAtLeast(floor).coerceAtMost(maxOf(bandCap, floor))
}

/** Non-finite or negative constraints (an unmeasured pass) read as zero. */
private fun finitePlayerDimensionDp(value: Dp): Dp =
    if (value.value.isFinite()) value.coerceAtLeast(DhunSpacing.zero) else DhunSpacing.zero

/**
 * The travel to use on this frame: the value captured while the sheet was at
 * rest, for as long as a transition is in flight, and the live responsive
 * measurement otherwise.
 *
 * Freezing is what keeps an opening panel honest. Without it the sheet mounts
 * at one height, the chrome re-measures on the next frame, and the panel is
 * animated against a distance that changed underneath it — the visible symptom
 * being a player that snaps ahead of its own sheet, or a seam that opens into a
 * gap. A reversal (open → close mid-flight) deliberately keeps the same frozen
 * distance rather than re-deriving one, so the return trip is the exact reverse
 * of the way in.
 *
 * A frozen value of zero means "never captured yet" (first composition, chrome
 * not measured), and falls back to the live measurement rather than to a
 * transition with no motion at all.
 */
internal fun relatedSheetTravelDp(inFlight: Boolean, liveDp: Dp, frozenDp: Dp): Dp {
    val live = finitePlayerDimensionDp(liveDp)
    val frozen = finitePlayerDimensionDp(frozenDp)
    return if (inFlight && frozen > DhunSpacing.zero) frozen else live
}

/**
 * Captures [liveDp] while the sheet is at rest and hands back that frozen value
 * for the whole flight. Idle frames keep tracking responsive geometry —
 * rotation, a dragged Desktop window, the chrome re-measuring after a track
 * change — so the *next* transition starts from an up-to-date distance.
 */
@Composable
private fun rememberFrozenSheetTravel(inFlight: Boolean, liveDp: Dp): Dp {
    val frozenDpValue = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(inFlight, liveDp) {
        if (!inFlight) frozenDpValue.floatValue = liveDp.value
    }
    return relatedSheetTravelDp(
        inFlight = inFlight,
        liveDp = liveDp,
        frozenDp = frozenDpValue.floatValue.dp,
    )
}

/**
 * The layout half of the player's rise: the inset the weighted artwork field
 * absorbs while [RelatedSheetMotion.playerOffsetY] draws the composition at its
 * new height. Same progress, same travel, no second animation — the field
 * shrinking is how the cover becomes a thumbnail instead of a cropped rectangle,
 * and how the pinned header keeps its swipe-down gesture reachable.
 *
 * Bounded by the room above the chrome by construction ([relatedSheetTravel]),
 * which is the amount the field can give up before the transport would be
 * pushed out of the composition.
 */
internal fun relatedSheetLayoutRiseDp(travelDp: Dp, progress: Float): Dp {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val safeTravel = if (travelDp.value.isFinite()) travelDp.coerceAtLeast(DhunSpacing.zero) else DhunSpacing.zero
    return safeTravel * safeProgress
}

/** Shared geometry for the two halves of the Related/Queue transition. */
internal data class RelatedSheetMotion(
    val playerOffsetY: Float,
    val sheetOffsetY: Float,
    val progress: Float,
)

/**
 * Derives both translations from one normalized progress and one measured
 * travel distance. This is intentionally pure so Android and Desktop share
 * the same reversible motion contract.
 *
 * The two halves are locked together by construction: the player's lower
 * boundary sits at `height - travel · progress` and the sheet's top edge at
 * `height - travel + travel · (1 - progress)` — the same expression, so the two
 * meet on every frame of an opening, a closing, or a reversed transition, with
 * no gap to fill and no overlap to interpolate away.
 */
internal fun relatedSheetMotion(progress: Float, travelPx: Float): RelatedSheetMotion {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val safeTravel = if (travelPx.isFinite() && travelPx > 0f) travelPx else 0f
    return RelatedSheetMotion(
        // Normalize zero so equality-based geometry tests and any downstream
        // interpolation never observe a signed negative zero.
        playerOffsetY = if (safeTravel == 0f || safeProgress == 0f) {
            0f
        } else {
            -safeTravel * safeProgress
        },
        sheetOffsetY = safeTravel * (1f - safeProgress),
        progress = safeProgress,
    )
}

/**
 * Everything else the transition needs beyond the two offsets, from the same
 * single progress.
 *
 * [sheetMounted] spans the whole motion in both directions — from the frame the
 * target opens to the frame a closing animation reaches exactly zero — so a
 * reversal finds the panel already in the tree, at its real height, instead of
 * remounting and restarting. [actionRowVisible] is the mirror of that window:
 * the queue / shuffle / repeat / lyrics strip stays out of the way for the
 * entire flight and returns once, after it lands, which is what stops it
 * flickering back on during the last frames of a close.
 */
internal data class RelatedSheetTransition(
    val motion: RelatedSheetMotion,
    val sheetMounted: Boolean,
    val actionRowVisible: Boolean,
)

internal fun relatedSheetTransition(
    targetOpen: Boolean,
    progress: Float,
    travelPx: Float,
): RelatedSheetTransition {
    val motion = relatedSheetMotion(progress, travelPx)
    val inFlight = targetOpen || motion.progress > 0f
    // A transition with no room to travel in has no panel to show; the mount
    // gate is the same answer for the action row, which stays visible then.
    val mounted = inFlight && travelPx.isFinite() && travelPx > 0f
    return RelatedSheetTransition(
        motion = motion,
        sheetMounted = mounted,
        actionRowVisible = !mounted,
    )
}

/**
 * Converts a measured pixel height (`onSizeChanged`) into dp.
 *
 * Density is the only correct divisor: `Dp(px)` treats a *pixel* count as dp,
 * which is 2–3.5× too many on a phone screen. A non-finite/non-positive
 * density degrades to 1:1 instead of inf/NaN.
 */
internal fun chromeHeightDp(pixelHeight: Int, density: Float): Dp {
    val safeDensity = if (density.isFinite() && density > 0f) density else 1f
    return (pixelHeight.coerceAtLeast(0) / safeDensity).dp
}

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
 * The blurred-artwork backdrop that carries the whole screen.
 *
 * Three layers, back to front:
 *  1. [PlayerBackdrop] — the blurred, enlarged bleed (blur once, ADR-002 P4),
 *     scaled past the screen edges so the blur never shows a hard rim;
 *  2. the ambient scrim ([playerAmbientScrimStops]) — a vertical gradient in
 *     the surface colour, still fully transparent across the middle so the
 *     blur *glows* there and heavy towards the bottom so the control cluster
 *     (title, progress, transport) is legible over it;
 *  3. a dim that deepens while lyrics-dominant so the lyrics card reads.
 *
 * The sharp artwork is **not** here: it lives in its own fit-to-card hero
 * ([ArtworkHeroField]) so a portrait screen never has to crop a 16:9 cover to
 * fill. Nothing in this composable animates per frame.
 */
@Composable
private fun ArtworkBackdrop(
    track: Track?,
    lyricsDominant: Boolean,
    cacheKey: String,
) {
    val dimColor by animateColorAsState(
        targetValue = Color.Black.copy(alpha = if (lyricsDominant) 0.52f else 0.16f),
        animationSpec = DhunAnimations.slowTween(),
        label = "backdropDim",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        // Hi-res tier for the backdrop — the same Coil key as the hero card,
        // so the bytes are fetched once, not twice. Keyed by the track's
        // BlurredArtworkCache key: unrelated recompositions (position ticks,
        // tab switches, dim crossfades) never restart it.
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientScrimBrush()),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dimColor),
        )
    }
}

/**
 * Ambient scrim color stops (`offset to alpha`, alphas applied to the surface
 * colour): clear across the middle so the blurred bleed glows, dark towards
 * the bottom so titles, progress and transport stay legible, and light at the
 * very top so the "NOW PLAYING" strip reads under the status bar.
 *
 * Kept as data (not a hard-coded brush) so the legibility contract — clears
 * out in the middle, darkens monotonically towards the bottom, never fully
 * opaque — is unit-tested rather than eyeballed.
 */
internal fun playerAmbientScrimStops(): List<Pair<Float, Float>> = listOf(
    0.00f to 0.30f,
    0.16f to 0.10f,
    0.42f to 0.00f,
    0.58f to 0.24f,
    0.72f to 0.52f,
    0.86f to 0.78f,
    1.00f to 0.92f,
)

/** [playerAmbientScrimStops] as the brush the backdrop actually paints. */
@Composable
private fun ambientScrimBrush(): Brush = Brush.verticalGradient(
    colorStops = playerAmbientScrimStops()
        .map { (offset, alpha) -> offset to DhunColors.background.copy(alpha = alpha) }
        .toTypedArray(),
)

/**
 * The sharp artwork, fit to a square card in the upper field.
 *
 * The card is sized by [fittedPlayerArtworkSize], so it respects the width,
 * the *height* and the max-artwork token, and the image inside is drawn with
 * [ContentScale.Fit]: a 16:9 cover or a square avatar is shown whole — never
 * cropped/zoomed — with the blurred backdrop showing through the letterbox
 * bands instead of black bars. Slide + fade on track change, a gentle
 * play-scale, and a fade-out while lyrics-dominant (ADR-002 P6).
 */
@Composable
private fun ArtworkHeroField(
    track: Track?,
    skipDirection: SkipDirection,
    isPlaying: Boolean,
    lyricsDominant: Boolean,
    modifier: Modifier = Modifier,
) {
    val sharpAlpha by animateFloatAsState(
        targetValue = if (lyricsDominant) 0f else 1f,
        animationSpec = DhunAnimations.mediumTween(),
        label = "sharpArtAlpha",
    )
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.02f else 1f,
        animationSpec = DhunAnimations.springSpec(),
        label = "heroPlayScale",
    )
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val cardSize = fittedPlayerArtworkSize(maxWidth, maxHeight)
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
            contentAlignment = Alignment.Center,
            label = "heroArtworkChange",
        ) { t ->
            Box(
                modifier = Modifier
                    .size(cardSize)
                    .graphicsLayer {
                        alpha = sharpAlpha
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .clip(DhunShapes.artworkHero)
                    .background(DhunColors.scrim.copy(alpha = 0.35f))
                    .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), DhunShapes.artworkHero),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkImage(
                    imageUrl = ArtworkUrls.nowPlaying(t?.thumbnailUrl),
                    contentDescription = t?.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = DhunShapes.artwork,
                    contentScale = ContentScale.Fit,
                    // Let the blurred bleed show in the free bands: the whole
                    // cover stays visible and the card still reads as artwork.
                    placeholderBase = false,
                )
            }
        }
    }
}

/**
 * Raises the lyrics card into the artwork hero field (ADR-002 P6).
 *
 * A standalone composable rather than an inline `AnimatedVisibility` inside
 * the field's `Box`: `BoxScope` carries the layout-scope marker, which hides
 * the surrounding `ColumnScope` and with it the column-scoped
 * `AnimatedVisibility` overload. Here the plain overload resolves, and
 * enter/exit are passed explicitly, so the motion is unchanged.
 */
@Composable
private fun LyricsCardOverlay(
    visible: Boolean,
    artworkUrl: String?,
    cacheKey: String,
    viewModel: PlayerViewModel,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(DhunAnimations.mediumTween()) { offset -> offset / 4 } +
            fadeIn(DhunAnimations.mediumTween()),
        exit = slideOutVertically(DhunAnimations.fastTween()) { offset -> offset / 4 } +
            fadeOut(DhunAnimations.fastTween()),
    ) {
        LyricsCard(
            artworkUrl = artworkUrl,
            cacheKey = cacheKey,
            viewModel = viewModel,
            accent = accent,
        )
    }
}

/**
 * Decision whether to render the full-screen blurred backdrop layer.
 * A null/blank URL or a platform that cannot really blur returns false so the
 * screen falls back to the clean dark surface rather than a sharp stretched cover.
 */
internal fun shouldRenderPlayerBackdrop(artworkUrl: String?, supportsBlur: Boolean): Boolean =
    !artworkUrl.isNullOrBlank() && supportsBlur

/**
 * Blurred-artwork bleed (ADR-002 P4). [ArtworkBackdrop] mounts this inside
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
    if (!shouldRenderPlayerBackdrop(artworkUrl, supportsRealtimeBlur)) return

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
        // On platforms without realtime blur (Android < API 31), skip the
        // unblurred artwork so text stays legible over the clean scrim.
        key(cacheKey) {
            if (shouldRenderPlayerBackdrop(artworkUrl, supportsRealtimeBlur)) {
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
 * The Queue / Related sheet.
 *
 * Its height is [sheetHeight] — [relatedSheetTravel], the same number the
 * player's rise is computed from — and it is bottom-aligned in the very box the
 * player fills. Those two facts are the whole seam: whatever the viewport is,
 * the panel's top edge and the rising player's lower boundary are the same
 * y on every frame, so the sheet never floats above a gap and never covers the
 * transport. Because the size is fixed before the first frame is drawn, the
 * panel mounts at its real measured height instead of growing into place, and
 * no enter/exit animation of its own competes with the shared transition.
 *
 * It slides, it does not fade: the offsets are opaque at every progress, so the
 * motion reads as a panel rising and a player getting out of the way rather
 * than a wash of glass appearing over a still player. Its width is capped to
 * the player content budget so a wide window gets readable rows rather than one
 * stretched line, and the glass keeps the opaque base + rounded top corners the
 * rows need to stay legible over artwork.
 *
 * Closing is the same numbers run backwards, driven by the ✕ in the header
 * (the queue glyph that opened this is hidden while the panel is up).
 */
@Composable
private fun QueueSheet(
    viewModel: PlayerViewModel,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onClose: () -> Unit,
    accent: Color,
    sheetHeight: Dp,
    motion: RelatedSheetMotion,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                // Same width budget as the player's own content column, so
                // a wide desktop window gets a centred, readable sheet
                // instead of rows stretched across the whole screen.
                .widthIn(max = DhunSpacing.playerContentMaxWidth)
                .fillMaxWidth()
                // Flush with the bottom of the safe area at the height the
                // player just vacated: the sheet rises from its own top edge,
                // never from an arbitrary screen offset, and the player chrome
                // is no longer left as an uncovered footer under it.
                .height(sheetHeight)
                .graphicsLayer { translationY = motion.sheetOffsetY },
        ) {
            GlassBottomBar(
                modifier = Modifier.fillMaxSize(),
                // This is now a bottom sheet: rounded top corners, flush lower edge.
                shape = DhunShapes.bottomSheet,
                opaqueBase = true,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    QueueSheetHeader(
                        title = if (selectedTab == 2) "Related" else "Up next",
                        onClose = onClose,
                    )
                    PanelTabRow(
                        selectedTab = selectedTab,
                        onSelect = onSelectTab,
                        accent = accent,
                        modifier = Modifier.padding(horizontal = DhunSpacing.sm),
                    )
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        PlayerTabContent(
                            tab = selectedTab,
                            viewModel = viewModel,
                            accent = accent,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sheet header: grab pill, what the sheet holds, and a 48dp close target —
 * the queue glyph below only *toggles*, so the sheet needs its own visible way
 * out on both touch and mouse.
 */
@Composable
private fun QueueSheetHeader(title: String, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.sm)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(DhunSpacing.xxxl)
                    .height(DhunSpacing.xsPlus)
                    .clip(DhunShapes.full)
                    .background(DhunColors.glassEdge),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DhunSpacing.huge)
                .padding(start = DhunSpacing.lg, end = DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = DhunColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DhunIconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(DhunSpacing.touchTarget)
                    .clip(DhunShapes.full)
                    .background(DhunColors.glassStrong)
                    .border(BorderStroke(DhunSpacing.border, DhunColors.glassEdge), DhunShapes.full),
                contentDescription = "Close queue",
            ) {
                DhunIconView(
                    icon = DhunIcon.Close,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.textPrimary,
                )
            }
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
