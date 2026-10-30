package dev.dhun.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.zIndex
import dev.dhun.core.AlwaysOnlineConnectivityMonitor
import dev.dhun.core.ConnectivityMonitor
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.PlayContext
import dev.dhun.data.SettingsKeys
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.DhunVisualTuning
import dev.dhun.design.VisualPart
import dev.dhun.design.background.BackgroundSection
import dev.dhun.design.background.DhunBackgrounds
import dev.dhun.design.background.LocalBackgroundSection
import dev.dhun.design.background.backgroundSectionTint
import dev.dhun.design.catalog.ComponentCatalogScreen
import dev.dhun.design.components.AmbientBackdrop
import dev.dhun.design.components.DhunNavigationBar
import dev.dhun.design.components.DhunNavigationBarItem
import dev.dhun.design.components.DhunNavigationRail
import dev.dhun.design.components.DhunNavigationRailItem
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.GlassDock
import dev.dhun.design.components.LocalNowPlayingArtwork
import dev.dhun.design.components.LocalTextInputFocusRegistry
import dev.dhun.design.components.NowPlayingBackdrop
import dev.dhun.design.components.TextInputFocusRegistry
import dev.dhun.design.components.acrylicGlass
import dev.dhun.design.components.contentRowFill
import dev.dhun.design.components.lyricsVeilBrush
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.download.DownloadManager
import dev.dhun.legal.DhunAppInfo
import dev.dhun.player.DhunPlayer
import dev.dhun.player.equalizer.EqualizerBands
import dev.dhun.player.equalizer.EqualizerSession
import dev.dhun.player.equalizer.EqualizerState
import dev.dhun.presentation.browse.AlbumViewModel
import dev.dhun.presentation.browse.ArtistViewModel
import dev.dhun.presentation.browse.PlaylistViewModel
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.library.LibraryTab
import dev.dhun.presentation.library.LibraryViewModel
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.presentation.settings.SettingsViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.ui.browse.AlbumScreen
import dev.dhun.ui.browse.ArtistScreen
import dev.dhun.ui.browse.PlaylistScreen
import dev.dhun.ui.components.ActionErrorBanner
import dev.dhun.ui.components.AddToPlaylistDialog
import dev.dhun.ui.components.TrackOverflowDialog
import dev.dhun.ui.home.HomeScreen
import dev.dhun.ui.legal.AboutLegalScreen
import dev.dhun.ui.legal.LegalDocumentScreen
import dev.dhun.ui.library.LibraryScreen
import dev.dhun.ui.player.FullPlayer
import dev.dhun.ui.player.MiniPlayer
import dev.dhun.ui.search.SearchScreen
import dev.dhun.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.drop

// A positive layer keeps the immersive player above the rail, split panes and
// the desktop navigation dock while its enter/exit transition is running.
private const val FULL_PLAYER_LAYER_Z_INDEX = 10f

enum class AppTab(val title: String, val icon: DhunIcon) {
    HOME("Home", DhunIcon.Home),
    SEARCH("Search", DhunIcon.Search),
    LIBRARY("Library", DhunIcon.LibraryMusic),

    /**
     * Legacy internal design-catalogue tab. Kept parseable for old restored
     * state but never offered in normal navigation; the current preview entry
     * is a test-channel-only route from Settings. See [userTabs].
     */
    CATALOG("Catalog", DhunIcon.Palette),
    ;

    companion object {
        /** Tabs the nav bar / rail actually offers. */
        val userTabs: List<AppTab> = listOf(HOME, SEARCH, LIBRARY)

        /**
         * The tab BACK bottoms out at — the app's landing screen. From here the
         * platform default runs (Android parks the app, playback untouched);
         * there is no tab beneath it, so the walk can never loop.
         */
        val root: AppTab = HOME
    }
}

/**
 * The app shell: navigation + docked MiniPlayer + FullPlayer overlay +
 * detail-page stack (artist/album/playlist), in one of two layouts decided by
 * [DhunShellPolicy] from the measured width:
 *
 * - **[DhunShellLayout.SinglePane]** (handsets, narrow windows) — bottom bar,
 *   a floating MiniPlayer, and a detail page that *covers* the tab content.
 *   This branch is what shipped to devices; it is deliberately untouched
 *   apart from moving the same `when` into [ShellMasterPane].
 * - **[DhunShellLayout.TwoPane]** (≥ 840dp, Phase 13's "navigation rail at
 *   width ≥ 840dp; two-pane player where space allows") — the rail, a master
 *   column (list + MiniPlayer docked to its bottom), and a detail column beside
 *   it only while [AppNavState.detailStack] has a route. With no route the
 *   master uses the full content width; the stack survives a tab switch, and
 *   Back pops it page by page before the player sheet.
 *
 * Nav & overlay state live in [nav] (hoisted to the platform shell so its
 * BackHandler can coordinate: player collapses → detail pops → app default).
 * No layout branch is allowed to change that contract — see [DhunShellPolicy.backAction].
 */
@Composable
fun DhunAppShell(
    player: DhunPlayer,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    playerViewModel: PlayerViewModel,
    provider: MusicProvider,
    dataLayer: DataLayer,
    nav: AppNavState,
    modifier: Modifier = Modifier,
    isDesktop: Boolean = false,
    libraryViewModel: LibraryViewModel? = null,
    connectivity: ConnectivityMonitor = AlwaysOnlineConnectivityMonitor,
    downloadManager: DownloadManager? = null,
    equalizerSession: EqualizerSession? = null,
    /**
     * This build's version/channel, read from build metadata by the platform
     * shell. About & Legal reports it, and the test channel gates the internal
     * Theme Catalogue entry. It defaults to the honest "unknown" sentinel so
     * an unwired caller shows that rather than a plausible-looking wrong version.
     */
    appInfo: DhunAppInfo = DhunAppInfo.Unknown,
    clearAudioCache: (suspend () -> Long)? = null,
    onCountryCodeChange: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var overflowTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    val favoriteIds by homeViewModel.favoriteIds.collectAsState()
    // Like/Unlike is offered from Home, Search, the queue and the full player,
    // and all of them route through one of these two models. A failure is shown
    // once, above whatever is on screen, instead of failing silently.
    val homeMutation by homeViewModel.mutation.collectAsState()
    val searchMutation by searchViewModel.mutation.collectAsState()
    val currentTrack by playerViewModel.currentTrack.collectAsState()
    // The background palette follows the current artwork, through the *same*
    // extraction the player tint already uses (cached per seed, one pass per
    // track). Nothing here decodes an image again or touches blur.
    val artworkSeed = currentTrack?.thumbnailUrl ?: currentTrack?.id
    LaunchedEffect(artworkSeed) { DhunBackgrounds.setArtworkSeed(artworkSeed) }
    // The base wash is theme-relative, so a theme/accent change re-derives it
    // instead of leaving the previous theme's layers behind.
    val appearanceRevision = DhunAppearance.appearanceRevision
    LaunchedEffect(appearanceRevision) { DhunBackgrounds.refreshArtworkPalette() }
    // Phase 10: library owns Playlists / Favorites / History tabs.
    // If the host doesn't supply a ViewModel, create one from DataLayer.
    // Wire play-context via PlayerViewModel so history rows are labeled
    // LIBRARY/HISTORY/PLAYLIST instead of UNKNOWN.
    val libraryVm = libraryViewModel ?: remember(dataLayer, player, scope, playerViewModel, downloadManager) {
        LibraryViewModel(
            dataLayer = dataLayer,
            player = player,
            scope = scope,
            setContext = { ctx -> playerViewModel.setPlayContext(ctx) },
            downloadManager = downloadManager,
        )
    }
    // S4: settings model lives at shell scope (one instance, shared by both
    // panes) so a change made in the detail pane is visible if the window is
    // resized down to the single-pane path, and vice versa.
    // Reset settings also restores the live EQ engine: its rows are ordinary
    // settings, but its state lives in a platform session that would keep the
    // old preset and write it back on the next change. The session is stable
    // for the app's lifetime (Koin singleton / player equalizer).
    val resetEqualizer: (suspend () -> Unit)? = equalizerSession?.let { session ->
        { session.restore(EqualizerState()) }
    }
    val settingsVm = remember(dataLayer, scope) {
        SettingsViewModel(
            GetSettingUseCase(dataLayer.settings),
            UpdateSettingUseCase(dataLayer.settings),
            scope,
            isTestRelease = appInfo.isTestRelease,
            clearLyricsCacheAction = { playerViewModel.clearLyricsCache() },
            clearSearchHistoryAction = { dataLayer.search.clearRecentSearches() },
            clearPlayHistoryAction = { dataLayer.history.clear() },
            clearAudioCacheAction = clearAudioCache,
            resetEqualizerAction = resetEqualizer,
            onCountryCodeChange = onCountryCodeChange,
        )
    }

    // The EQ is a platform engine session, but its controls live in Settings.
    // Restore once before observing writes; the DB remains the source of truth
    // across restarts, and reset-to-defaults also restores the live engine.
    LaunchedEffect(dataLayer, equalizerSession) {
        if (equalizerSession != null) {
            val prefs = dataLayer.settings
            val defaults = EqualizerState()
            val initial = EqualizerState(
                enabled = prefs.getBoolean(SettingsKeys.EQUALIZER_ENABLED, defaults.enabled),
                presetId = prefs.getString(SettingsKeys.EQUALIZER_PRESET) ?: defaults.presetId,
                preampDb = prefs.getString(SettingsKeys.EQUALIZER_PREAMP_DB)?.toFloatOrNull() ?: defaults.preampDb,
                gainsDb = prefs.getString(SettingsKeys.EQUALIZER_GAINS_DB)
                    ?.split(',')?.mapNotNull { it.toFloatOrNull() }
                    ?.takeIf { it.size == EqualizerBands.COUNT }
                    ?: defaults.gainsDb,
            )
            equalizerSession.restore(initial)
            equalizerSession.state.drop(1).collect { state ->
                prefs.putBoolean(SettingsKeys.EQUALIZER_ENABLED, state.enabled)
                prefs.putString(SettingsKeys.EQUALIZER_PRESET, state.presetId)
                prefs.putString(SettingsKeys.EQUALIZER_PREAMP_DB, state.preampDb.toString())
                prefs.putString(SettingsKeys.EQUALIZER_GAINS_DB, state.gainsDb.joinToString(","))
            }
        }
    }

    val countryCode by settingsVm.countryCode.collectAsState()
    val explicitContent by settingsVm.explicitContent.collectAsState()
    val preferredLyricsProvider by settingsVm.preferredProvider.collectAsState()
    val settingsLoaded by settingsVm.loaded.collectAsState()
    // Stored experiments are applied only in the test channel. Public builds
    // render the original 50% appearance even after installing over a test APK.
    LaunchedEffect(settingsLoaded, appInfo.isTestRelease) {
        if (settingsLoaded) DhunVisualTuning.restore(settingsVm.visualTuning.value, appInfo.isTestRelease)
    }
    LaunchedEffect(countryCode, explicitContent, settingsLoaded) {
        if (settingsLoaded) {
            homeViewModel.refresh()
            if (searchViewModel.query.value.isNotBlank()) searchViewModel.onSearchSubmitted()
        }
    }

    // Phase 10: RecordPlay contexts — every queue handoff labels the history row.
    val onPlayTrack: (Track, List<Track>, Int) -> Unit = { _, queue, index ->
        val ctx = when (nav.selectedTab) {
            AppTab.HOME -> PlayContext.HOME
            AppTab.SEARCH -> PlayContext.SEARCH
            AppTab.LIBRARY -> PlayContext.LIBRARY
            else -> PlayContext.UNKNOWN
        }
        playerViewModel.playQueue(queue, index, ctx)
    }
    val onPlayArtist: (Track, List<Track>, Int) -> Unit = { _, q, i -> playerViewModel.playQueue(q, i, PlayContext.ARTIST) }
    val onPlayAlbum: (Track, List<Track>, Int) -> Unit = { _, q, i -> playerViewModel.playQueue(q, i, PlayContext.ALBUM) }
    val onPlayPlaylist: (Track, List<Track>, Int) -> Unit = { _, q, i -> playerViewModel.playQueue(q, i, PlayContext.PLAYLIST) }

    val openArtist: (Track) -> Unit = { track ->
        track.artistId?.let { nav.push(DetailRoute.ArtistPage(it)) }
    }
    val openAlbum: (Track) -> Unit = { track ->
        track.albumId?.let { nav.push(DetailRoute.AlbumPage(it)) }
    }

    val textInputFocusRegistry = remember { TextInputFocusRegistry() }
    // Every dialog and frost-cut surface (GlassCard/GlassDialog) paints the
    // song that is playing, blurred once and cached, so the panel is visibly
    // glass over the app's own artwork instead of a flat plate over a scrim.
    // Null when nothing plays: the documented fallback (gradient + sheen).
    val nowPlayingArtworkForGlass = currentTrack?.thumbnailUrl
    // Home, Search and Library each have their own saved background
    // preferences: the page backdrop is painted for the section the user is
    // actually on, and every panel or dialog floating above it frosts with that
    // same section's switches (LocalBackgroundSection).
    val pageSection = when (nav.selectedTab) {
        AppTab.SEARCH -> BackgroundSection.SEARCH
        AppTab.LIBRARY -> BackgroundSection.LIBRARY
        else -> BackgroundSection.HOME
    }
    CompositionLocalProvider(
        LocalTextInputFocusRegistry provides textInputFocusRegistry,
        LocalNowPlayingArtwork provides nowPlayingArtworkForGlass,
        LocalBackgroundSection provides pageSection,
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (DesktopShortcutPolicy.shouldTogglePlaybackOnSpace(
                            isDesktop = isDesktop,
                            isKeyDown = event.type == KeyEventType.KeyDown,
                            isSpace = event.key == Key.Spacebar,
                            controlPressed = event.isCtrlPressed,
                            textInputFocused = textInputFocusRegistry.hasFocusedTextInput,
                        )
                    ) {
                        playerViewModel.togglePlay()
                        true
                    } else {
                        false
                    }
                },
        ) {
        // ONE decision drives both large-screen affordances: [DhunShellLayout.of]
        // reuses the rail breakpoint token, so the rail and the intent to split
        // can never drift apart. Below it the shell is exactly what shipped to
        // phones. At and above it, a detail route opts into a real second pane;
        // an empty stack keeps the master full-width instead of reserving a
        // permanent "Nothing open" half-screen placeholder.
        val shellMaxWidth = maxWidth
        val layout = DhunShellPolicy.layoutAt(shellMaxWidth)
        val useNavigationRail = layout == DhunShellLayout.TwoPane
        // Phase 14 error taxonomy: offline banner. Rendered in the Scaffold
        // topBar slot so innerPadding pushes content down while it shows.
        val isOnline by connectivity.isOnline.collectAsState()
        val playerOverlayVisible = nav.playerExpanded && currentTrack != null
        val offlineBannerPlacement = DhunShellPolicy.offlineBannerPlacement(
            isOnline = isOnline,
            fullPlayerVisible = playerOverlayVisible,
        )
        val sleepRemaining by playerViewModel.sleepTimerRemainingMs.collectAsState()
        val sleepLabel = sleepRemaining?.let { ms ->
            val mins = ((ms + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            "Sleep · ${mins}m"
        }
        val accentMode = DhunAppearance.accentMode
        val selectedAccentColor = DhunAppearance.selectedAccentColor
        // Publish the active track tint into the shared appearance tokens so
        // artwork mode is visible throughout the app, not only on player chrome.
        LaunchedEffect(
            currentTrack?.id,
            currentTrack?.thumbnailUrl,
            accentMode,
            selectedAccentColor,
            DhunAppearance.appearanceRevision,
        ) {
            val artworkAccent = if (accentMode == "static") {
                null
            } else {
                currentTrack?.let { track ->
                    ArtworkColorExtractor.extractFromSeed(track.thumbnailUrl ?: track.id).controlAccent
                }
            }
            DhunAppearance.setArtworkAccent(artworkAccent)
        }
        // Only one theme-aware artwork veil lives beneath the screens. Accent
        // extraction above still colors controls, but no second full-screen wash
        // is painted over the image (or over the fallback when art is absent).
        // The page base is never one flat fill: the ambient artwork-coloured
        // blooms sit under the blurred cover, so a screen that is not backed by
        // artwork (first run, Library, Settings, offline) still has depth and
        // colour instead of a plain Material background
        // (docs/design/glass-system.md §1).
        // The page backdrop for the section the user is on (see pageSection
        // above); it re-resolves when the tab changes.
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackdrop(modifier = Modifier.fillMaxSize(), section = pageSection)
            NowPlayingBackdrop(
                artworkUrl = currentTrack?.thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                section = pageSection,
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                AppNavigationRail(nav = nav, layout = layout)
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Material's Scaffold is only the slot layout here: its own
                // container is transparent so the glass backdrop shows through.
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
            topBar = {
                TopLevelAnimatedVisibility(
                    visible = offlineBannerPlacement == OfflineBannerPlacement.ScaffoldTopBar,
                    enter = slideInVertically { -it } + fadeIn(DhunAnimations.mediumTween()),
                    exit = slideOutVertically { -it } + fadeOut(DhunAnimations.fastTween()),
                ) {
                    OfflineStatusBanner()
                }
            },
            bottomBar = if (useNavigationRail) {
                // The rail owns navigation here; the dock that used to carry the
                // bottom bar instead (MiniPlayer) moves into the master pane.
                {}
            } else {
                {
                    BottomNavigationBar(
                        nav = nav,
                        playerViewModel = playerViewModel,
                        layout = layout,
                    )
                }
            },
        ) { innerPadding ->
            // The split is computed from what the Scaffold actually handed back,
            // minus the rail — see [DhunShellPolicy.panes]. It is deliberately
            // requested only for an open route: an empty detail stack is the
            // normal landing state, not a reason to surrender half the window.
            val direction = LocalLayoutDirection.current
            val horizontalInsets = innerPadding.calculateLeftPadding(direction) +
                innerPadding.calculateRightPadding(direction)
            val detailRoute = nav.detailStack.lastOrNull()
            val showDetailPane = DhunShellPolicy.detailPaneVisible(
                layout = layout,
                detailDepth = nav.detailStack.size,
            )
            val panes = if (showDetailPane) {
                DhunShellPolicy.panes(
                    shellWidth = shellMaxWidth,
                    contentWidth = shellMaxWidth - horizontalInsets,
                    hasRail = useNavigationRail,
                )
            } else {
                null
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (panes == null || detailRoute == null) {
                    // Phone / narrow window, or a large screen with no route:
                    // unchanged content wiring, with the master using all of the
                    // available space. A Column (not a Box) so the rail layout's
                    // MiniPlayer is a DOCKED bottom bar that reserves its own
                    // height instead of floating over the tab list. Android
                    // landscape and Windows fullscreen cross the 840dp rail
                    // breakpoint into this branch; the old floating card sat on
                    // top of Home/Search/Library and, on the short landscape
                    // height, read as "covering the whole screen". The rail owns
                    // navigation here, so this bar IS the dock.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        // `panes == null` is either the phone path, a large
                        // screen with no detail route, or a constrained window that
                        // cannot fit two usable columns. Only a non-empty route is
                        // rendered here; the normal empty state stays on the tab.
                        // Weighted Box so the docked MiniPlayer below always keeps
                        // its own strip and can never overlay the list.
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ShellMasterPane(
                            tab = nav.selectedTab,
                            detailRoute = detailRoute,
                            homeViewModel = homeViewModel,
                            searchViewModel = searchViewModel,
                            libraryViewModel = libraryVm,
                            provider = provider,
                            dataLayer = dataLayer,
                            player = player,
                            radioSession = playerViewModel.radioSession,
                            nav = nav,
                            onPlayTrack = onPlayTrack,
                            onPlayArtist = onPlayArtist,
                            onPlayAlbum = onPlayAlbum,
                            onPlayPlaylist = onPlayPlaylist,
                            onTrackOverflow = { overflowTrack = it },
                            downloadManager = downloadManager,
                            sleepTimerLabel = sleepLabel,
                            onCycleSleepTimer = { playerViewModel.cycleSleepTimer() },
                            onOpenLiked = {
                                libraryVm.openLikedSongs()
                                nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = false)
                            },
                            onOpenOffline = {
                                // Segment cache lives under playback; Library is the
                                // honest destination until a dedicated Offline page.
                                libraryVm.selectTab(LibraryTab.PLAYLISTS)
                                nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = false)
                            },
                            settingsVm = settingsVm,
                            lyricsRomanization = playerViewModel.lyricsRomanization,
                            isDesktop = isDesktop,
                            equalizerSession = equalizerSession,
                            onOpenSettings = { nav.push(DetailRoute.SettingsPage) },
                            appInfo = appInfo,
                        )
                        }
                        if (useNavigationRail && !nav.playerExpanded) {
                            MiniPlayer(
                                viewModel = playerViewModel,
                                onExpand = { nav.playerExpanded = true },
                                modifier = Modifier
                                    .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
                            )
                        }
                    }
                } else {
                    // Large screen with an open route: a master column (tab list
                    // + docked MiniPlayer) and a detail column. The stack survives
                    // tab switches here — it is not covering anything any more.
                    ShellTwoPane(
                        panes = panes,
                        master = {
                            ShellMasterPane(
                                tab = nav.selectedTab,
                                // The detail pane beside this column already
                                // renders the route — the master must show the
                                // TAB list, not the same page twice (which also
                                // double-loads its data). Passing the route here
                                // used to hide the tab content behind a duplicate.
                                detailRoute = null,
                                homeViewModel = homeViewModel,
                                searchViewModel = searchViewModel,
                                libraryViewModel = libraryVm,
                                provider = provider,
                                dataLayer = dataLayer,
                                player = player,
                                radioSession = playerViewModel.radioSession,
                                nav = nav,
                                onPlayTrack = onPlayTrack,
                                onPlayArtist = onPlayArtist,
                                onPlayAlbum = onPlayAlbum,
                                onPlayPlaylist = onPlayPlaylist,
                                onTrackOverflow = { overflowTrack = it },
                                downloadManager = downloadManager,
                                sleepTimerLabel = sleepLabel,
                                onCycleSleepTimer = { playerViewModel.cycleSleepTimer() },
                                onOpenLiked = {
                                    libraryVm.openLikedSongs()
                                    // This branch *is* the two-pane case, so the rule is
                                    // unconditional here rather than re-derived from `layout`.
                                    nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = true)
                                },
                                onOpenOffline = {
                                    libraryVm.selectTab(LibraryTab.PLAYLISTS)
                                    nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = true)
                                },
                                settingsVm = settingsVm,
                                lyricsRomanization = playerViewModel.lyricsRomanization,
                                isDesktop = isDesktop,
                                equalizerSession = equalizerSession,
                                onOpenSettings = { nav.push(DetailRoute.SettingsPage) },
                                appInfo = appInfo,
                            )
                        },
                        detail = {
                            ShellDetailPane(
                                route = detailRoute,
                                provider = provider,
                                dataLayer = dataLayer,
                                player = player,
                                radioSession = playerViewModel.radioSession,
                                nav = nav,
                                onPlayArtist = onPlayArtist,
                                onPlayAlbum = onPlayAlbum,
                                onPlayPlaylist = onPlayPlaylist,
                                onTrackOverflow = { overflowTrack = it },
                                settingsVm = settingsVm,
                                lyricsRomanization = playerViewModel.lyricsRomanization,
                                isDesktop = isDesktop,
                                equalizerSession = equalizerSession,
                                appInfo = appInfo,
                            )
                        },
                        miniPlayer = if (!nav.playerExpanded) {
                            {
                                MiniPlayer(
                                    viewModel = playerViewModel,
                                    onExpand = { nav.playerExpanded = true },
                                    modifier = Modifier.padding(
                                        horizontal = DhunSpacing.md,
                                        vertical = DhunSpacing.sm,
                                    ),
                                )
                            }
                        } else {
                            {}
                        },
                    )
                }
            }
        }

        // ---------------- FullPlayer overlay (covers nav + content) --------------
        // Keep this as an explicit top layer rather than relying on sibling draw
        // order. On Compose Desktop the rail/detail split can otherwise remain
        // visually above a transitioning sheet during the first frame, which
        // makes the Windows player look like it opened in only one pane.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(FULL_PLAYER_LAYER_Z_INDEX),
        ) {
            TopLevelAnimatedVisibility(
                visible = nav.playerExpanded && currentTrack != null,
                enter = slideInVertically(DhunAnimations.mediumTween()) { it } +
                    fadeIn(DhunAnimations.mediumTween()),
                exit = slideOutVertically(DhunAnimations.mediumTween()) { it } +
                    fadeOut(DhunAnimations.fastTween()),
                modifier = Modifier.fillMaxSize(),
            ) {
                // The expanded player is its own background section; providing it
                // here (not inside FullPlayer) means the player's own dialogs and
                // sheets frost with the player's switches too.
                CompositionLocalProvider(
                    LocalBackgroundSection provides BackgroundSection.FULL_PLAYER,
                ) {
                    FullPlayer(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = playerViewModel,
                        isDesktop = isDesktop,
                        onCollapse = { nav.playerExpanded = false },
                        preferredLyricsProvider = preferredLyricsProvider,
                        onPreferredLyricsProviderChange = { id ->
                            settingsVm.setPreferredProvider(id) {
                                playerViewModel.refreshLyrics()
                            }
                        },
                        onOverflowTrack = { overflowTrack = it },
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { homeViewModel.toggleFavorite(it) },
                        onOpenArtist = { track ->
                            nav.playerExpanded = false
                            nav.detailStack.clear()
                            openArtist(track)
                        },
                        onOpenAlbum = { track ->
                            nav.playerExpanded = false
                            nav.detailStack.clear()
                            openAlbum(track)
                        },
                    )
                }
            }
        }
        // The Scaffold's top bar is behind the immersive player. Re-layer the
        // same offline status above FullPlayer so buffering cannot look silent.
        TopLevelAnimatedVisibility(
            visible = offlineBannerPlacement == OfflineBannerPlacement.AboveFullPlayer,
            enter = slideInVertically { -it } + fadeIn(DhunAnimations.mediumTween()),
            exit = slideOutVertically { -it } + fadeOut(DhunAnimations.fastTween()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(FULL_PLAYER_LAYER_Z_INDEX + 1f),
        ) {
            OfflineStatusBanner(modifier = Modifier.safeDrawingPadding())
        }

        // A failed Like/Unlike or download request has no dialog of its own.
        // Reported above the page (and above the expanded player, where the
        // same heart is available) as a polite live region.
        val actionError = homeMutation.errorMessage ?: searchMutation.errorMessage
        TopLevelAnimatedVisibility(
            visible = actionError != null,
            enter = slideInVertically { -it } + fadeIn(DhunAnimations.mediumTween()),
            exit = slideOutVertically { -it } + fadeOut(DhunAnimations.fastTween()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(FULL_PLAYER_LAYER_Z_INDEX + 2f),
        ) {
            ActionErrorBanner(
                message = actionError.orEmpty(),
                onDismiss = {
                    homeViewModel.dismissMutationError()
                    searchViewModel.dismissMutationError()
                },
                modifier = Modifier.safeDrawingPadding(),
            )
        }

            } // Close Box(weight=1f)
        } // Close Row
        
        // ---------------- dialogs (topmost) ---------------------------------------
        overflowTrack?.let { track ->
            TrackOverflowDialog(
                track = track,
                player = player,
                onDownload = if (downloadManager != null) { t -> libraryVm.download(t) } else null,
                onAddToPlaylist = { addToPlaylistTrack = it },
                onNavigateToArtist = {
                    val id = track.artistId
                    if (id != null) {
                        nav.push(DetailRoute.ArtistPage(id))
                    } else {
                        // The fallback search must be visible even when this
                        // action came from above the expanded player.
                        searchViewModel.onQueryChange(track.artistName)
                        searchViewModel.performSearch(track.artistName, dev.dhun.innertube.SearchFilter.ARTISTS)
                        nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = false)
                    }
                },
                onNavigateToAlbum = {
                    val albumId = track.albumId
                    val albumName = track.albumName
                    if (albumId != null) {
                        nav.push(DetailRoute.AlbumPage(albumId))
                    } else if (!albumName.isNullOrBlank()) {
                        searchViewModel.onQueryChange(albumName)
                        searchViewModel.performSearch(albumName, dev.dhun.innertube.SearchFilter.ALBUMS)
                        nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = false)
                    }
                },
                onDismiss = { overflowTrack = null },
            )
        }

        addToPlaylistTrack?.let { track ->
            AddToPlaylistDialog(
                track = track,
                playlistRepository = dataLayer.playlists,
                onDismiss = { addToPlaylistTrack = null },
                onAdded = { /* confirmation handled in dialog */ },
                onOpenPlaylist = { playlistId ->
                    addToPlaylistTrack = null
                    nav.push(DetailRoute.PlaylistPage(playlistId, isLocal = true))
                },
            )
        }
        }
    }
}

@Composable
private fun OfflineStatusBanner(modifier: Modifier = Modifier) {
    // Glass with an error tint, not a flat Material Surface strip: the shell's
    // chrome is the app's material (see docs/design/glass-system.md §2).
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tint = DhunColors.errorContainer,
        elevated = false,
    ) {
        Text(
            "You're offline. Search and streaming are unavailable; downloaded tracks remain available.",
            fontSize = DhunTypographyTokens.labelSmall.fontSize,
            color = DhunColors.warning,
            modifier = Modifier.padding(DhunSpacing.xsPlus),
        )
    }
}

@Composable
private fun BottomNavigationBar(
    nav: AppNavState,
    playerViewModel: PlayerViewModel,
    layout: DhunShellLayout,
) {
    // One clipped dock, two materials (the lyrics-card artwork is painted once
    // by [GlassDock]):
    //  - the MiniPlayer row is translucent acrylic — a milky veil, not the old
    //    near-black `glassStrong` slab;
    //  - Home / Search / Library, the tabs that sit *below* the mini-player,
    //    use the lyrics-card veil so that band matches the lyrics background.
    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val showMini = !nav.playerExpanded && currentTrack != null
    GlassDock(
        artworkUrl = currentTrack?.thumbnailUrl,
        modifier = Modifier.fillMaxWidth(),
        shape = DhunShapes.bottomSheet,
    ) {
        if (showMini) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .acrylicGlass()
                    // The dock is the miniplayer section: it takes that section's
                    // palette (artwork or manual) as a soft tint over its acrylic.
                    .backgroundSectionTint(BackgroundSection.MINI_PLAYER),
            ) {
                MiniPlayer(
                    viewModel = playerViewModel,
                    onExpand = { nav.playerExpanded = true },
                    embedded = true,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DhunSpacing.border)
                    .background(DhunColors.glassEdge),
            )
        }
        // The band is the DHUN glass body (gradient + sheen over the dock's
        // frosted artwork), not Material's NavigationBar: its selection pill and
        // item metrics were the last stock-Material look in the shell.
        // `lyricsVeil` still sets the tone so the tabs match the lyrics panel.
        DhunNavigationBar(
            modifier = Modifier.background(lyricsVeilBrush(part = VisualPart.NAVIGATION)),
            fill = contentRowFill(VisualPart.NAVIGATION),
        ) {
            AppTab.userTabs.forEach { tab ->
                DhunNavigationBarItem(
                    icon = tab.icon,
                    label = tab.title,
                    selected = DhunShellPolicy.isTabSelected(
                        layout = layout,
                        selectedTab = nav.selectedTab,
                        tab = tab,
                        detailDepth = nav.detailStack.size,
                    ),
                    // A bottom bar only exists in the single-pane layout, so
                    // `false` here is the phone rule by construction: switching
                    // tabs drops the stack that was covering the tab.
                    onClick = { nav.selectTab(tab, keepDetailOnTabChange = false) },
                )
            }
        }
    }
}

@Composable
private fun AppNavigationRail(
    nav: AppNavState,
    layout: DhunShellLayout,
) {
    // Not Material's NavigationRail: DHUN owns the container, the pill and the
    // labels. Its width is DhunNavigationPolicy.railWidth, the same number
    // DhunShellPolicy.railAllowance reserves for the pane split.
    DhunNavigationRail {
        AppTab.userTabs.forEach { tab ->
            DhunNavigationRailItem(
                icon = tab.icon,
                label = tab.title,
                selected = DhunShellPolicy.isTabSelected(
                    layout = layout,
                    selectedTab = nav.selectedTab,
                    tab = tab,
                    detailDepth = nav.detailStack.size,
                ),
                onClick = {
                    // The rail only exists at the two-pane breakpoint, so a tab
                    // tap there switches the master and keeps the detail pane; the
                    // re-tap-on-the-selected-tab case pops one page (see
                    // [AppNavState.selectTab]).
                    nav.selectTab(tab, keepDetailOnTabChange = layout.showsDetailPane)
                },
            )
        }
    }
}

/**
 * Master pane of the large-screen shell: the tab list, the detail pane beside
 * it, and the MiniPlayer docked to the bottom of the **master** — never over
 * the page the user is reading. The dock is a Column child here (no floating
 * overlay), which is what keeps it clear of the detail column.
 */
@Composable
private fun ShellTwoPane(
    panes: ShellPanes,
    master: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    miniPlayer: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.requiredWidth(panes.masterWidth).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                master()
            }
            miniPlayer()
        }
        // Pane seam. `DhunColors.border` is the same hairline the glass cards
        // use, so the split does not introduce a new stroke into the design.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(DhunSpacing.border)
                .background(DhunColors.border),
        )
        Box(modifier = Modifier.weight(panes.detailWeight).fillMaxHeight()) {
            detail()
        }
    }
}

/**
 * The tab content, plus — in the single-pane layout only — the detail page that
 * covers it. Keeping both in one function is deliberate: the phone path renders
 * the exact same `when`, so the two layouts cannot fork the browse wiring.
 *
 * [detailRoute] must be a plain local/parameter (`val`) for the sealed
 * smart-casts below to hold — that is why the caller hoists it instead of
 * reading `nav.detailStack.lastOrNull()` inline.
 */
@Composable
private fun ShellMasterPane(
    tab: AppTab,
    detailRoute: DetailRoute?,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    provider: MusicProvider,
    dataLayer: DataLayer,
    player: DhunPlayer,
    /** Shared endless-radio session (same instance PlayerViewModel refills on). */
    radioSession: dev.dhun.domain.RadioSession,
    nav: AppNavState,
    onPlayTrack: (Track, List<Track>, Int) -> Unit,
    onPlayArtist: (Track, List<Track>, Int) -> Unit,
    onPlayAlbum: (Track, List<Track>, Int) -> Unit,
    onPlayPlaylist: (Track, List<Track>, Int) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    downloadManager: DownloadManager?,
    sleepTimerLabel: String?,
    onCycleSleepTimer: () -> Unit,
    onOpenLiked: () -> Unit,
    onOpenOffline: () -> Unit,
    settingsVm: SettingsViewModel,
    lyricsRomanization: dev.dhun.presentation.player.LyricsRomanizationController,
    isDesktop: Boolean,
    equalizerSession: EqualizerSession?,
    onOpenSettings: () -> Unit,
    appInfo: DhunAppInfo,
) {
    val route: DetailRoute? = detailRoute
    when (route) {
        null -> TabContent(
            tab = tab,
            homeViewModel = homeViewModel,
            searchViewModel = searchViewModel,
            libraryViewModel = libraryViewModel,
            onPlayTrack = onPlayTrack,
            onNavigate = { nav.push(it) },
            onTrackOverflow = onTrackOverflow,
            downloadManager = downloadManager,
            onOpenLiked = onOpenLiked,
            onOpenOffline = onOpenOffline,
            sleepTimerLabel = sleepTimerLabel,
            onCycleSleepTimer = onCycleSleepTimer,
            onOpenSettings = onOpenSettings,
        )
        is DetailRoute.ArtistPage -> {
            val vm = remember(route.id) { ArtistViewModel(provider, player, route.id, radioSession, dataLayer.settings) }
            DisposableEffect(vm) { onDispose { vm.close() } }
            ArtistScreen(
                viewModel = vm,
                onBack = { nav.closeTop() },
                onTrackPlay = onPlayArtist,
                onAlbumClick = { nav.push(DetailRoute.AlbumPage(it.id)) },
                onArtistClick = { nav.push(DetailRoute.ArtistPage(it.id)) },
                onPlaylistClick = { nav.push(DetailRoute.PlaylistPage(it.id)) },
                onTrackOverflow = onTrackOverflow,
            )
        }
        is DetailRoute.AlbumPage -> {
            val vm = remember(route.id) { AlbumViewModel(provider, player, route.id, dataLayer.settings) }
            DisposableEffect(vm) { onDispose { vm.close() } }
            AlbumScreen(
                viewModel = vm,
                onBack = { nav.closeTop() },
                onTrackPlay = onPlayAlbum,
                onArtistClick = { nav.push(DetailRoute.ArtistPage(it.id)) },
                onTrackOverflow = onTrackOverflow,
            )
        }
        is DetailRoute.PlaylistPage -> {
            val vm = remember(route.id, route.isLocal) {
                PlaylistViewModel(provider, dataLayer.playlists, player, route.id, route.isLocal, dataLayer.settings)
            }
            DisposableEffect(vm) { onDispose { vm.close() } }
            PlaylistScreen(
                viewModel = vm,
                onBack = { nav.closeTop() },
                onTrackPlay = onPlayPlaylist,
                onTrackOverflow = onTrackOverflow,
                onDeleted = { nav.popDetail() },
            )
        }
        is DetailRoute.SettingsPage, is DetailRoute.SettingsCategoryPage -> {
            SettingsScreen(
                viewModel = settingsVm,
                category = (route as? DetailRoute.SettingsCategoryPage)?.category,
                romanization = lyricsRomanization,
                onOpenCategory = { nav.push(DetailRoute.SettingsCategoryPage(it)) },
                onBack = { nav.closeTop() },
                isDesktop = isDesktop,
                equalizerSession = equalizerSession,
                onOpenAboutLegal = { nav.push(DetailRoute.AboutLegalPage) },
                showThemeCatalogue = appInfo.isTestRelease,
                onOpenThemeCatalogue = { nav.push(DetailRoute.ThemeCatalogPage) },
            )
        }
        is DetailRoute.ThemeCatalogPage -> {
            if (appInfo.isTestRelease) {
                SettingsThemeCatalogue(
                    viewModel = settingsVm,
                    onClose = { nav.popDetail() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // A stale route from an older test install must not expose a
                // test-only destination (or even its placeholder) in public.
                LaunchedEffect(nav, appInfo.isTestRelease) {
                    nav.detailStack.removeAll { it == DetailRoute.ThemeCatalogPage }
                }
            }
        }
        is DetailRoute.AboutLegalPage -> {
            AboutLegalScreen(
                onBack = { nav.closeTop() },
                onOpenDocument = { id -> nav.push(DetailRoute.LegalDocumentPage(id)) },
            )
        }
        is DetailRoute.LegalDocumentPage -> {
            LegalDocumentScreen(
                documentId = route.id,
                appInfo = appInfo,
                onBack = { nav.closeTop() },
            )
        }
    }
}

/**
 * The large-screen detail column. The page owns its own back affordance, and
 * [AppNavState.popDetail] is the right affordance here (one page, not
 * [AppNavState.closeTop], which would also try to collapse the player).
 *
 * This composable is only mounted while the stack is non-empty. The shell
 * removes the pane entirely when the last route closes, so an empty stack
 * returns the tab to full width instead of showing an idle prompt.
 */
@Composable
private fun ShellDetailPane(
    route: DetailRoute,
    provider: MusicProvider,
    dataLayer: DataLayer,
    player: DhunPlayer,
    /** Shared endless-radio session (same instance PlayerViewModel refills on). */
    radioSession: dev.dhun.domain.RadioSession,
    nav: AppNavState,
    onPlayArtist: (Track, List<Track>, Int) -> Unit,
    onPlayAlbum: (Track, List<Track>, Int) -> Unit,
    onPlayPlaylist: (Track, List<Track>, Int) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    settingsVm: SettingsViewModel,
    lyricsRomanization: dev.dhun.presentation.player.LyricsRomanizationController,
    isDesktop: Boolean,
    equalizerSession: EqualizerSession?,
    appInfo: DhunAppInfo,
) {
    when (route) {
        is DetailRoute.ArtistPage -> {
            val vm = remember(route.id) { ArtistViewModel(provider, player, route.id, radioSession, dataLayer.settings) }
            DisposableEffect(vm) { onDispose { vm.close() } }
            ArtistScreen(
                viewModel = vm,
                onBack = { nav.popDetail() },
                onTrackPlay = onPlayArtist,
                onAlbumClick = { nav.push(DetailRoute.AlbumPage(it.id)) },
                onArtistClick = { nav.push(DetailRoute.ArtistPage(it.id)) },
                onPlaylistClick = { nav.push(DetailRoute.PlaylistPage(it.id)) },
                onTrackOverflow = onTrackOverflow,
            )
        }
        is DetailRoute.AlbumPage -> {
            val vm = remember(route.id) { AlbumViewModel(provider, player, route.id, dataLayer.settings) }
            DisposableEffect(vm) { onDispose { vm.close() } }
            AlbumScreen(
                viewModel = vm,
                onBack = { nav.popDetail() },
                onTrackPlay = onPlayAlbum,
                onArtistClick = { nav.push(DetailRoute.ArtistPage(it.id)) },
                onTrackOverflow = onTrackOverflow,
            )
        }
        is DetailRoute.PlaylistPage -> {
            val vm = remember(route.id, route.isLocal) {
                PlaylistViewModel(provider, dataLayer.playlists, player, route.id, route.isLocal, dataLayer.settings)
            }
            DisposableEffect(vm) { onDispose { vm.close() } }
            PlaylistScreen(
                viewModel = vm,
                onBack = { nav.popDetail() },
                onTrackPlay = onPlayPlaylist,
                onTrackOverflow = onTrackOverflow,
                onDeleted = { nav.popDetail() },
            )
        }
        is DetailRoute.SettingsPage, is DetailRoute.SettingsCategoryPage -> {
            SettingsScreen(
                viewModel = settingsVm,
                category = (route as? DetailRoute.SettingsCategoryPage)?.category,
                romanization = lyricsRomanization,
                onOpenCategory = { nav.push(DetailRoute.SettingsCategoryPage(it)) },
                onBack = { nav.popDetail() },
                isDesktop = isDesktop,
                equalizerSession = equalizerSession,
                onOpenAboutLegal = { nav.push(DetailRoute.AboutLegalPage) },
                showThemeCatalogue = appInfo.isTestRelease,
                onOpenThemeCatalogue = { nav.push(DetailRoute.ThemeCatalogPage) },
            )
        }
        is DetailRoute.ThemeCatalogPage -> {
            if (appInfo.isTestRelease) {
                SettingsThemeCatalogue(
                    viewModel = settingsVm,
                    onClose = { nav.popDetail() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // A stale route from an older test install must not expose a
                // test-only destination (or even its placeholder) in public.
                LaunchedEffect(nav, appInfo.isTestRelease) {
                    nav.detailStack.removeAll { it == DetailRoute.ThemeCatalogPage }
                }
            }
        }
        is DetailRoute.AboutLegalPage -> {
            AboutLegalScreen(
                onBack = { nav.popDetail() },
                onOpenDocument = { id -> nav.push(DetailRoute.LegalDocumentPage(id)) },
            )
        }
        is DetailRoute.LegalDocumentPage -> {
            LegalDocumentScreen(
                documentId = route.id,
                appInfo = appInfo,
                onBack = { nav.popDetail() },
            )
        }
    }
}

@Composable
private fun SettingsThemeCatalogue(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemDark = isSystemInDarkTheme()
    val selectedThemeId by viewModel.themeId.collectAsState()
    ComponentCatalogScreen(
        onClose = onClose,
        modifier = modifier,
        selectedThemeId = selectedThemeId,
        onModeChange = { mode ->
            viewModel.setThemeId(mode.id)
            DhunAppearance.setThemePreference(mode.id, systemDark)
        },
        onSystemChange = {
            viewModel.setThemeId("system")
            DhunAppearance.setThemePreference("system", systemDark)
        },
        onAccentChange = { accent ->
            viewModel.setAccentId(accent.id)
            DhunAppearance.setAppearance(nextAccent = accent)
        },
    )
}

@Composable
private fun TabContent(
    tab: AppTab,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    onPlayTrack: (Track, List<Track>, Int) -> Unit,
    onNavigate: (DetailRoute) -> Unit,
    onTrackOverflow: (Track) -> Unit,
    downloadManager: DownloadManager? = null,
    onOpenLiked: () -> Unit = {},
    onOpenOffline: () -> Unit = {},
    sleepTimerLabel: String? = null,
    onCycleSleepTimer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    when (tab) {
        // Old saved CATALOG tabs now land on Home. The only preview entry is
        // the explicitly test-channelled Settings route with a working Back.
        AppTab.HOME, AppTab.CATALOG -> {
            HomeScreen(
                viewModel = homeViewModel,
                onTrackClick = onPlayTrack,
                onAlbumClick = { onNavigate(DetailRoute.AlbumPage(it.id)) },
                onPlaylistClick = { onNavigate(DetailRoute.PlaylistPage(it.id)) },
                onArtistClick = { onNavigate(DetailRoute.ArtistPage(it.id)) },
                onTrackOverflow = onTrackOverflow,
                downloadManager = downloadManager,
                onOpenLiked = onOpenLiked,
                onOpenOffline = onOpenOffline,
                sleepTimerLabel = sleepTimerLabel,
                onCycleSleepTimer = onCycleSleepTimer,
            )
        }
        AppTab.SEARCH -> {
            SearchScreen(
                viewModel = searchViewModel,
                onTrackClick = onPlayTrack,
                onAlbumClick = { onNavigate(DetailRoute.AlbumPage(it.id)) },
                onPlaylistClick = { onNavigate(DetailRoute.PlaylistPage(it.id)) },
                onArtistClick = { onNavigate(DetailRoute.ArtistPage(it.id)) },
                onTrackOverflow = onTrackOverflow,
                downloadManager = downloadManager,
            )
        }
        AppTab.LIBRARY -> {
            LibraryScreen(
                viewModel = libraryViewModel,
                onPlaylistClick = { onNavigate(DetailRoute.PlaylistPage(it.id, isLocal = true)) },
                onTrackOverflow = onTrackOverflow,
                modifier = Modifier.fillMaxSize(),
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun TopLevelAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: androidx.compose.animation.EnterTransition = androidx.compose.animation.fadeIn(),
    exit: androidx.compose.animation.ExitTransition = androidx.compose.animation.fadeOut(),
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content
    )
}
