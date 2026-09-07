package dev.dhun.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.dhun.design.fittedPlayerArtworkSize
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.player.SkipDirection

/**
 * FullPlayer — the Phase 08 showstopper + ADR-002 polish.
 *
 * Layout (top → bottom): collapse bar · sliding artwork (blurred-artwork
 * background + Material 3 scrim behind everything) · title/artist · custom
 * seek bar · transport · volume (desktop) · Lyrics | Queue | Related tabs.
 *
 * **Lyrics-dominant mode (ADR-002 P6):** selecting the Lyrics tab (or tapping
 * the CC control) recedes the centered artwork (scale/fade) and gives the
 * lyrics surface most of the vertical weight. Still the same screen — no
 * navigation. Material 3 only: translucent surfaces + existing
 * [Modifier.blur] pipeline. **No Liquid Glass.**
 *
 * **Blur-once backdrop (ADR-002 P4):** the full-screen blurred artwork lives
 * in a [PlayerBackdrop] subtree keyed by [BlurredArtworkCache.keyFor], so it
 * is prepared once per track — never per frame, and never as two stacked
 * blurred layers during a transition.
 *
 * **Reconnecting chip:** when [PlaybackState.Recovering], a small M3
 * surface chip appears under the title (403 mid-stream recovery).
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
    val colors = remember(current?.thumbnailUrl, current?.id) {
        ArtworkColorExtractor.extractFromSeed(current?.thumbnailUrl ?: current?.id ?: "")
    }
    val bgTintTop by animateColorAsState(
        targetValue = colors.backgroundTint,
        animationSpec = DhunAnimations.slowTween(),
        label = "bgTintTop",
    )
    val bgTintMid by animateColorAsState(
        targetValue = colors.backgroundTint.copy(alpha = 0.05f),
        animationSpec = DhunAnimations.slowTween(),
        label = "bgTintMid",
    )
    // Controls use the tamed accent, never the raw artwork colour — a raw
    // hue put a red play disc + red volume slider on screen (device report).
    val accent by animateColorAsState(
        targetValue = colors.controlAccent,
        animationSpec = DhunAnimations.slowTween(),
        label = "accent",
    )

    var selectedTab by rememberSaveable { mutableIntStateOf(1) } // Queue by default
    // ADR-002 P6: Lyrics tab = lyrics-dominant layout (artwork recedes).
    val lyricsDominant = selectedTab == LYRICS_TAB_INDEX

    // ADR-002 P4: one stable identity for the backdrop per track/artwork URL.
    val artworkCacheKey = remember(current?.thumbnailUrl, current?.id) {
        BlurredArtworkCache.keyFor(current?.thumbnailUrl, current?.id)
    }
    // Where the CC control returns when lyrics-dominant mode closes.
    var lastPlainTab by rememberSaveable { mutableIntStateOf(1) }

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
        // ---- background: Material 3 blurred artwork (blur once, ADR-002 P4) ----
        // Keyed by the track's BlurredArtworkCache key: unrelated recompositions
        // (position ticks, tab switches, stage-weight animation) never restart
        // it, and a track change *replaces* the layer instead of crossfading two
        // full-screen blurs. The radius animates in once when the backdrop marks
        // itself prepared, then the layer is static. No Liquid Glass.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            // Hi-res tier for the backdrop — the same Coil key as the main
            // artwork below, so the bytes are fetched once, not twice.
            key(artworkCacheKey) {
                PlayerBackdrop(
                    artworkUrl = ArtworkUrls.nowPlaying(current?.thumbnailUrl),
                    cacheKey = artworkCacheKey,
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
        Column(
            modifier = Modifier.widthIn(max = DhunSpacing.playerContentMaxWidth)
                .fillMaxSize().align(Alignment.TopCenter),
        ) {
            // ADR-002 P9: swipe-down on the sheet's top strip collapses the
            // player (never exits the app), mirroring a bottom sheet. Taps and
            // button clicks in the strip are untouched — only a committed
            // vertical drag beyond the MiniPlayer's own threshold collapses.
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
                // Sheet drag handle — immersive bottom-sheet cue (M3 frosted
                // pill). Rides the drag as a rubber-band affordance.
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
                        style = DhunTypographyTokens.brand,
                        color = DhunColors.textTertiary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // ADR-002 rule 5: dedicated CC control toggles lyrics-dominant
                    // mode on this very screen — entering stashes the plain tab,
                    // leaving restores it. It never navigates away.
                    DhunIconButton(
                        onClick = {
                            val (tab, plainTab) = toggleLyricsDominant(selectedTab, lastPlainTab)
                            selectedTab = tab
                            lastPlainTab = plainTab
                        },
                        enabled = current != null,
                        modifier = Modifier
                            .size(DhunSpacing.touchTarget)
                            .semantics { selected = lyricsDominant },
                        contentDescription = if (lyricsDominant) "Exit lyrics view" else "Lyrics view",
                    ) {
                        DhunIconView(
                            icon = DhunIcon.ClosedCaption,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSize),
                            tint = if (lyricsDominant) accent else DhunColors.textSecondary,
                        )
                    }
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
            }

            // Artwork stage — recedes in lyrics-dominant mode (ADR-002 P6).
            val stageWeight by animateFloatAsState(
                targetValue = if (lyricsDominant) 0.28f else 1.05f,
                animationSpec = DhunAnimations.mediumTween(),
                label = "artworkStageWeight",
            )
            val stageAlpha by animateFloatAsState(
                targetValue = if (lyricsDominant) 0.55f else 1f,
                animationSpec = DhunAnimations.mediumTween(),
                label = "artworkStageAlpha",
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(stageWeight.coerceAtLeast(0.2f))
                    .padding(horizontal = if (lyricsDominant) DhunSpacing.huge else DhunSpacing.xxxl)
                    .clipToBounds()
                    .graphicsLayer { alpha = stageAlpha },
                contentAlignment = Alignment.Center,
            ) {
                val artworkSize = fittedPlayerArtworkSize(maxWidth, maxHeight)
                val playScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0.84f,
                    animationSpec = DhunAnimations.springSpec(),
                    label = "artworkScale",
                )
                val dominantScale by animateFloatAsState(
                    targetValue = if (lyricsDominant) 0.42f else 1f,
                    animationSpec = DhunAnimations.mediumTween(),
                    label = "lyricsDominantScale",
                )
                val artworkScale = playScale * dominantScale
                AnimatedContent(
                    targetState = current,
                    modifier = Modifier.size(artworkSize),
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
                    ArtworkImage(
                        imageUrl = ArtworkUrls.nowPlaying(t?.thumbnailUrl),
                        contentDescription = t?.title,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale },
                        shape = DhunShapes.extraLarge,
                    )
                }
            }

            // Source-neutral resolving/buffering/recovery status, with sharp text.
            val busyLabel = playbackBusyLabel(state)
            if (busyLabel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.xs)
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

            // Playback failure — message + one-tap recovery inline, so the
            // player never sits silently dead on an error (previously this
            // screen showed nothing and its play button was a no-op while
            // the engine sat in error-idle).
            if (playbackError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhunSpacing.xxl, vertical = DhunSpacing.xs)
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

            Spacer(modifier = Modifier.height(DhunSpacing.sm))

            // Cancel scrubbing on a new queue occurrence, even for equal-duration tracks.
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.xxl),
                )
            }

            // Keep the same six controls in order. Adapt padding/play size
            // before allowing horizontal scrolling at exceptionally narrow widths;
            // never squeeze a secondary action below its 48dp target.
            Spacer(modifier = Modifier.height(DhunSpacing.xs))
            BoxWithConstraints(
                modifier = Modifier
                    .widthIn(max = DhunSpacing.playerTransportMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .height(DhunSpacing.playerTransportHeight),
            ) {
                val metrics = playerTransportMetrics(maxWidth)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState(), enabled = metrics.minimumWidth > maxWidth)
                        .width(maxOf(maxWidth, metrics.minimumWidth))
                        .fillMaxHeight()
                        .padding(horizontal = metrics.horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Like / favorite — sits beside (before) shuffle in the same
                    // transport cluster, so an inline change doesn't disturb the
                    // art/seek/tabs vertical layout.
                    DhunIconButton(
                        onClick = { current?.let(onToggleFavorite) },
                        modifier = Modifier
                            .size(DhunSpacing.touchTarget)
                            .clip(DhunShapes.full)
                            .background(if (isFavorite) accent.copy(alpha = 0.22f) else Color.Transparent),
                        enabled = current != null,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    ) {
                        DhunIconView(
                            icon = if (isFavorite) DhunIcon.Favorite else DhunIcon.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSize),
                            tint = if (isFavorite) accent else DhunColors.textPrimary,
                        )
                    }

                    // Shuffle
                    DhunIconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier
                            .size(DhunSpacing.touchTarget)
                            .semantics { selected = shuffleEnabled }
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

                    key(current?.id, queueIndex, false) {
                        HoldTapTransportButton(
                            enabled = current != null,
                            forward = false,
                            icon = DhunIcon.SkipPrevious,
                            contentDescription = "Previous track",
                            onTap = { viewModel.previous() },
                            onHold = { viewModel.beginHoldSeek(forward = false) },
                            onRelease = { viewModel.endHoldSeek() },
                        )
                    }

                    // Play / pause — animated morph inside the accent disc
                    val glowAlpha by animateFloatAsState(
                        targetValue = if (isPlaying) 0.35f else 0f,
                        animationSpec = DhunAnimations.mediumTween(),
                        label = "playGlow",
                    )
                    Box(
                        modifier = Modifier
                            .size(metrics.playSize)
                            .shadow(DhunSpacing.lg, DhunShapes.full, clip = false)
                            .clip(DhunShapes.full)
                            .background(accent)
                            .semantics { contentDescription = playbackActionLabel(state) }
                            .clickable(
                                enabled = current != null,
                                role = Role.Button,
                                onClickLabel = playbackActionLabel(state),
                                onClick = viewModel::togglePlay,
                            ),
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
                            targetState = playbackActionIcon(state),
                            animationSpec = DhunAnimations.mediumTween(),
                            label = "playPauseMorph",
                        ) { icon ->
                            DhunIconView(
                                icon = icon,
                                contentDescription = null,
                                modifier = Modifier.size(DhunSpacing.iconSizeLg),
                                tint = DhunColors.onAccent,
                            )
                        }
                    }

                    key(current?.id, queueIndex, true) {
                        HoldTapTransportButton(
                            enabled = current != null,
                            forward = true,
                            icon = DhunIcon.SkipNext,
                            contentDescription = "Next track",
                            onTap = { viewModel.next() },
                            onHold = { viewModel.beginHoldSeek(forward = true) },
                            onRelease = { viewModel.endHoldSeek() },
                        )
                    }

                    // Repeat cycle: OFF → ALL → ONE
                    DhunIconButton(
                        onClick = { viewModel.cycleRepeatMode() },
                        modifier = Modifier.size(DhunSpacing.touchTarget)
                            .semantics { selected = repeatMode != RepeatMode.OFF }
                            .clip(DhunShapes.full)
                            .background(if (repeatMode != RepeatMode.OFF) accent.copy(alpha = 0.22f) else Color.Transparent),
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
                            tint = if (repeatMode != RepeatMode.OFF) accent else DhunColors.textPrimary,
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
                        .padding(horizontal = DhunSpacing.xxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DhunIconView(
                        icon = DhunIcon.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.textSecondary,
                    )
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    // Bounded width: a full-bleed slider at 100% volume read
                    // as a giant coloured error bar across the window.
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

            Spacer(modifier = Modifier.height(DhunSpacing.xs))

            // Bottom tabs: Lyrics | Queue | Related
            // Lyrics-dominant (ADR-002): lyrics surface takes most vertical room;
            // still Material 3 translucent fill — not Liquid Glass.
            PlayerTabRow(
                selectedTab = selectedTab,
                onSelect = {
                    if (it != LYRICS_TAB_INDEX) lastPlainTab = it
                    selectedTab = it
                },
                accent = accent,
            )
            val tabsWeight by animateFloatAsState(
                targetValue = if (lyricsDominant) 1.55f else 0.9f,
                animationSpec = DhunAnimations.mediumTween(),
                label = "tabsWeight",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(tabsWeight.coerceAtLeast(0.5f))
                    .then(
                        if (lyricsDominant) {
                            Modifier
                                .padding(horizontal = DhunSpacing.sm)
                                .clip(DhunShapes.extraLarge)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            DhunColors.glassHighlight,
                                            DhunColors.glassDeep,
                                        ),
                                    ),
                                )
                        } else {
                            Modifier
                        },
                    ),
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

/** Lyrics tab index within PlayerTabRow (Lyrics | Queue | Related). */
internal const val LYRICS_TAB_INDEX = 0

/**
 * ADR-002 rule 5: the CC control toggles lyrics-dominant mode on the same
 * screen. From a plain tab it enters lyrics and remembers where to return;
 * from lyrics it restores that tab. Result: (newSelectedTab, newLastPlainTab).
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
 * Blurred-artwork backdrop (ADR-002 P4). [FullPlayer] mounts this inside
 * `key(BlurredArtworkCache.keyFor(...))`, so every track change starts one
 * fresh layer and nothing else recomposes it — the blur radius animates in
 * exactly once per track, then the layer is static. Re-entering the player
 * for a track whose key is already prepared skips the fade-up: the backdrop
 * arrives pre-blurred, honouring the cache's once-per-track contract.
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = backdropAlpha },
    ) {
        ArtworkImage(
            imageUrl = artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius),
            shape = RectangleShape,
            contentScale = ContentScale.Crop,
        )
    }
}
