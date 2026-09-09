package dev.dhun.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.dhun.core.AlwaysOnlineConnectivityMonitor
import dev.dhun.core.ConnectivityMonitor
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.PlayContext
import dev.dhun.download.DownloadManager
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.catalog.ComponentCatalogScreen
import dev.dhun.design.components.GlassBottomBar
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.browse.AlbumViewModel
import dev.dhun.presentation.browse.ArtistViewModel
import dev.dhun.presentation.browse.PlaylistViewModel
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.library.LibraryTab
import dev.dhun.presentation.library.LibraryViewModel
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.ui.browse.AlbumScreen
import dev.dhun.ui.browse.ArtistScreen
import dev.dhun.ui.browse.PlaylistScreen
import dev.dhun.ui.components.AddToPlaylistDialog
import dev.dhun.ui.components.TrackOverflowDialog
import dev.dhun.ui.home.HomeScreen
import dev.dhun.ui.library.LibraryScreen
import dev.dhun.ui.player.FullPlayer
import dev.dhun.ui.player.MiniPlayer
import dev.dhun.ui.search.SearchScreen
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: DhunIcon) {
    HOME("Home", DhunIcon.Home),
    SEARCH("Search", DhunIcon.Search),
    LIBRARY("Library", DhunIcon.LibraryMusic),

    /**
     * Internal design catalogue. Kept in the enum (deep links and restored
     * state may name it) but **not shown in the nav bar** — it shipped to
     * real devices as a fourth user-facing tab full of swatches, which is
     * developer scaffolding, not a product surface. See [userTabs].
     */
    CATALOG("Catalog", DhunIcon.Palette),
    ;

    companion object {
        /** Tabs the nav bar / rail actually offers. */
        val userTabs: List<AppTab> = listOf(HOME, SEARCH, LIBRARY)
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
 *   column (list + MiniPlayer docked to its bottom), and a detail column that
 *   shows the top of [AppNavState.detailStack] beside the list instead of
 *   replacing it. The stack survives a tab switch, and Back pops it page by
 *   page before the player sheet, so a tablet never loses a page it can see.
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
) {
    val scope = rememberCoroutineScope()
    var overflowTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    val favoriteIds by homeViewModel.favoriteIds.collectAsState()
    val currentTrack by playerViewModel.currentTrack.collectAsState()
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // ONE decision drives both large-screen affordances: [DhunShellLayout.of]
        // reuses the rail breakpoint token, so the rail and the intent to split
        // can never drift apart. Below it the shell is exactly what shipped to
        // phones; at and above it the detail stack becomes a real pane — subject
        // to [DhunShellPolicy.panes] finding actual room in the inset content area.
        val layout = DhunShellPolicy.layoutAt(maxWidth)
        val useNavigationRail = layout == DhunShellLayout.TwoPane
        // Phase 14 error taxonomy: offline banner. Rendered in the Scaffold
        // topBar slot so innerPadding pushes content down while it shows.
        val isOnline by connectivity.isOnline.collectAsState()
        val sleepRemaining by playerViewModel.sleepTimerRemainingMs.collectAsState()
        val sleepLabel = sleepRemaining?.let { ms ->
            val mins = ((ms + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            "Sleep · ${mins}m"
        }
        // Lightweight ambient wash from now-playing art (seed hash — no
        // continuous full-res blur; FullPlayer still owns the real blur layer).
        val ambient by animateColorAsState(
            targetValue = currentTrack?.let {
                ArtworkColorExtractor.extractFromSeed(it.thumbnailUrl ?: it.id).backgroundTint
            } ?: Color.Transparent,
            animationSpec = DhunAnimations.slowTween(),
            label = "shellAmbient",
        )
        // Ambient glass wash from now-playing (seed tint — lightweight).
        // FullPlayer still owns the real once-per-track artwork blur layer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DhunColors.background)
                .background(
                    Brush.verticalGradient(
                        // Restrained: the old 0.42 wash tinted whole screens
                        // brown/green and made list art look dirty.
                        colorStops = arrayOf(
                            0.0f to ambient.copy(alpha = 0.30f),
                            0.22f to ambient.copy(alpha = 0.10f),
                            0.45f to Color.Transparent,
                            1.0f to Color.Transparent,
                        ),
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ambient.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                AnimatedVisibility(
                    visible = !isOnline,
                    enter = slideInVertically { -it } + fadeIn(DhunAnimations.mediumTween()),
                    exit = slideOutVertically { -it } + fadeOut(DhunAnimations.fastTween()),
                ) {
                    Surface(color = DhunColors.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "You're offline. Search and streaming are unavailable until the connection returns.",
                            fontSize = DhunTypographyTokens.labelSmall.fontSize,
                            color = DhunColors.warning,
                            modifier = Modifier.padding(DhunSpacing.xsPlus),
                        )
                    }
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
            // minus the rail — see [DhunShellPolicy.panes].
            val direction = LocalLayoutDirection.current
            val horizontalInsets = innerPadding.calculateLeftPadding(direction) +
                innerPadding.calculateRightPadding(direction)
            val panes = DhunShellPolicy.panes(
                shellWidth = maxWidth,
                contentWidth = maxWidth - horizontalInsets,
                hasRail = useNavigationRail,
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    AppNavigationRail(nav = nav, layout = layout)
                }
                if (panes == null) {
                    // Phone / narrow window: unchanged, including the floating
                    // MiniPlayer above where the bottom bar would have been.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        // `panes == null` is the single-pane case: either below the
                        // breakpoint, or (rare) at it with insets so large that two real
                        // columns do not fit. Either way the top of the stack covers the
                        // tab, exactly as it does on a phone.
                        ShellMasterPane(
                            tab = nav.selectedTab,
                            detailRoute = nav.detailStack.lastOrNull(),
                            homeViewModel = homeViewModel,
                            searchViewModel = searchViewModel,
                            libraryViewModel = libraryVm,
                            provider = provider,
                            dataLayer = dataLayer,
                            player = player,
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
                        )
                        if (useNavigationRail && !nav.playerExpanded) {
                            MiniPlayer(
                                viewModel = playerViewModel,
                                onExpand = { nav.playerExpanded = true },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
                            )
                        }
                    }
                } else {
                    // Large screen: a master column (tab list + docked
                    // MiniPlayer) and a detail column. The stack survives tab
                    // switches here — it is not covering anything any more.
                    ShellTwoPane(
                        panes = panes,
                        master = {
                            ShellMasterPane(
                                tab = nav.selectedTab,
                                detailRoute = null,
                                homeViewModel = homeViewModel,
                                searchViewModel = searchViewModel,
                                libraryViewModel = libraryVm,
                                provider = provider,
                                dataLayer = dataLayer,
                                player = player,
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
                                    nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = layout.showsDetailPane)
                                },
                                onOpenOffline = {
                                    libraryVm.selectTab(LibraryTab.PLAYLISTS)
                                    nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = layout.showsDetailPane)
                                },
                            )
                        },
                        detail = {
                            ShellDetailPane(
                                route = nav.detailStack.lastOrNull(),
                                provider = provider,
                                dataLayer = dataLayer,
                                player = player,
                                nav = nav,
                                onPlayArtist = onPlayArtist,
                                onPlayAlbum = onPlayAlbum,
                                onPlayPlaylist = onPlayPlaylist,
                                onTrackOverflow = { overflowTrack = it },
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
        AnimatedVisibility(
            visible = nav.playerExpanded && currentTrack != null,
            enter = slideInVertically(DhunAnimations.mediumTween()) { it } +
                fadeIn(DhunAnimations.mediumTween()),
            exit = slideOutVertically(DhunAnimations.mediumTween()) { it } +
                fadeOut(DhunAnimations.fastTween()),
            modifier = Modifier.fillMaxSize(),
        ) {
            FullPlayer(
                viewModel = playerViewModel,
                isDesktop = isDesktop,
                onCollapse = { nav.playerExpanded = false },
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
                        nav.detailStack.clear()
                        searchViewModel.onQueryChange(track.artistName)
                        searchViewModel.performSearch(track.artistName, dev.dhun.innertube.SearchFilter.ARTISTS)
                        nav.selectedTab = AppTab.SEARCH
                    }
                },
                onNavigateToAlbum = {
                    val albumId = track.albumId
                    val albumName = track.albumName
                    if (albumId != null) {
                        nav.push(DetailRoute.AlbumPage(albumId))
                    } else if (!albumName.isNullOrBlank()) {
                        nav.detailStack.clear()
                        searchViewModel.onQueryChange(albumName)
                        searchViewModel.performSearch(albumName, dev.dhun.innertube.SearchFilter.ALBUMS)
                        nav.selectedTab = AppTab.SEARCH
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

@Composable
private fun BottomNavigationBar(
    nav: AppNavState,
    playerViewModel: PlayerViewModel,
    layout: DhunShellLayout,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!nav.playerExpanded) {
            MiniPlayer(
                viewModel = playerViewModel,
                onExpand = { nav.playerExpanded = true },
            )
        }
        // Frosted M3 bottom bar (glass-morphism dock — not Liquid Glass).
        GlassBottomBar(
            modifier = Modifier.fillMaxWidth(),
            shape = DhunShapes.bottomSheet,
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                contentColor = DhunColors.textPrimary,
                tonalElevation = DhunSpacing.zero,
                modifier = Modifier.fillMaxWidth().height(DhunSpacing.navigationBarContent),
            ) {
                AppTab.userTabs.forEach { tab ->
                    AppBottomNavigationItem(
                        tab = tab,
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
}

@Composable
private fun AppNavigationRail(
    nav: AppNavState,
    layout: DhunShellLayout,
) {
    NavigationRail(
        containerColor = DhunColors.glassStrong,
        contentColor = DhunColors.textPrimary,
        modifier = Modifier.fillMaxHeight(),
    ) {
        AppTab.userTabs.forEach { tab ->
            AppRailNavigationItem(
                tab = tab,
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
        )
        is DetailRoute.ArtistPage -> {
            val vm = remember(route.id) { ArtistViewModel(provider, player, route.id) }
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
            val vm = remember(route.id) { AlbumViewModel(provider, player, route.id) }
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
                PlaylistViewModel(provider, dataLayer.playlists, player, route.id, route.isLocal)
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
    }
}

/**
 * The large-screen detail column. The page owns its own back affordance, and
 * [AppNavState.popDetail] is the right affordance here (one page, not
 * [AppNavState.closeTop], which would also try to collapse the player).
 *
 * An empty stack is **not** an error state — on a tablet the pane is reserved
 * permanently so the layout does not jump every time a page opens or closes;
 * it shows an idle prompt instead of a blank rectangle.
 */
@Composable
private fun ShellDetailPane(
    route: DetailRoute?,
    provider: MusicProvider,
    dataLayer: DataLayer,
    player: DhunPlayer,
    nav: AppNavState,
    onPlayArtist: (Track, List<Track>, Int) -> Unit,
    onPlayAlbum: (Track, List<Track>, Int) -> Unit,
    onPlayPlaylist: (Track, List<Track>, Int) -> Unit,
    onTrackOverflow: (Track) -> Unit,
) {
    when (route) {
        null -> DetailPanePlaceholder()
        is DetailRoute.ArtistPage -> {
            val vm = remember(route.id) { ArtistViewModel(provider, player, route.id) }
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
            val vm = remember(route.id) { AlbumViewModel(provider, player, route.id) }
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
                PlaylistViewModel(provider, dataLayer.playlists, player, route.id, route.isLocal)
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
    }
}

@Composable
private fun DetailPanePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(DhunSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(DhunSpacing.skeletonTextWidth * 2),
        ) {
            DhunIconView(
                icon = DhunIcon.Album,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.artworkThumb),
                tint = DhunColors.textDisabled,
            )
            Text(
                text = "Nothing open",
                color = DhunColors.textSecondary,
                fontSize = DhunTypographyTokens.titleMedium.fontSize,
                modifier = Modifier.padding(top = DhunSpacing.md),
            )
            Text(
                text = "Pick a song, artist, album or playlist on the left and it opens here.",
                color = DhunColors.textTertiary,
                fontSize = DhunTypographyTokens.bodySmall.fontSize,
                modifier = Modifier.padding(top = DhunSpacing.xs),
            )
        }
    }
}

@Composable
private fun AppNavigationIcon(tab: AppTab, selected: Boolean) {
    DhunIconView(
        icon = tab.icon,
        contentDescription = "${tab.title} tab",
        modifier = Modifier.size(DhunSpacing.iconSize),
        tint = if (selected) DhunColors.accent else DhunColors.textTertiary,
    )
}

@Composable
private fun ColumnScope.AppRailNavigationItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { AppNavigationIcon(tab, selected) },
        label = { Text(text = tab.title, style = MaterialTheme.typography.labelSmall) },
        alwaysShowLabel = true,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = DhunColors.accent,
            selectedTextColor = DhunColors.accent,
            unselectedIconColor = DhunColors.textTertiary,
            unselectedTextColor = DhunColors.textTertiary,
            indicatorColor = DhunColors.accentContainer,
        ),
    )
}

@Composable
private fun RowScope.AppBottomNavigationItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { AppNavigationIcon(tab, selected) },
        label = { Text(text = tab.title, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = DhunColors.onAccentContainer,
            selectedTextColor = DhunColors.accent,
            unselectedIconColor = DhunColors.textTertiary,
            unselectedTextColor = DhunColors.textTertiary,
            indicatorColor = DhunColors.accentContainer,
        ),
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
) {
    when (tab) {
        AppTab.HOME -> {
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
            )
        }
        AppTab.CATALOG -> {
            ComponentCatalogScreen(
                onClose = { },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
