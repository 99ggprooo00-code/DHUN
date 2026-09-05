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
import androidx.compose.foundation.layout.size
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
import dev.dhun.core.AlwaysOnlineConnectivityMonitor
import dev.dhun.core.ConnectivityMonitor
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.PlayContext
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
    CATALOG("Catalog", DhunIcon.Palette),
}

/**
 * The app shell: bottom nav + docked MiniPlayer + FullPlayer overlay +
 * detail-page stack (artist/album/playlist).
 *
 * Nav & overlay state live in [nav] (hoisted to the platform shell so its
 * BackHandler can coordinate: player collapses → detail pops → app default).
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
    val libraryVm = libraryViewModel ?: remember(dataLayer, player, scope, playerViewModel) {
        LibraryViewModel(
            dataLayer = dataLayer,
            player = player,
            scope = scope,
            setContext = { ctx -> playerViewModel.setPlayContext(ctx) },
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
        val useNavigationRail = maxWidth >= DhunSpacing.navigationRailBreakpoint
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
                        colorStops = arrayOf(
                            0.0f to ambient.copy(alpha = 0.42f),
                            0.28f to ambient.copy(alpha = 0.14f),
                            0.55f to Color.Transparent,
                            1.0f to Color.Transparent,
                        ),
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ambient.copy(alpha = 0.18f),
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
                {}
            } else {
                {
                    BottomNavigationBar(
                        nav = nav,
                        playerViewModel = playerViewModel,
                    )
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    AppNavigationRail(nav = nav)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val route = nav.detailStack.lastOrNull()) {
                    null -> TabContent(
                        tab = nav.selectedTab,
                        homeViewModel = homeViewModel,
                        searchViewModel = searchViewModel,
                        libraryViewModel = libraryVm,
                        onPlayTrack = onPlayTrack,
                        onNavigate = { nav.push(it) },
                        onTrackOverflow = { overflowTrack = it },
                        onOpenLiked = {
                            libraryVm.selectTab(LibraryTab.FAVORITES)
                            nav.selectedTab = AppTab.LIBRARY
                            nav.detailStack.clear()
                        },
                        onOpenOffline = {
                            // Segment cache lives under playback; Library is the
                            // honest destination until a dedicated Offline page.
                            libraryVm.selectTab(LibraryTab.PLAYLISTS)
                            nav.selectedTab = AppTab.LIBRARY
                            nav.detailStack.clear()
                        },
                        sleepTimerLabel = sleepLabel,
                        onCycleSleepTimer = { playerViewModel.cycleSleepTimer() },
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
                            onTrackOverflow = { overflowTrack = it },
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
                            onTrackOverflow = { overflowTrack = it },
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
                            onTrackOverflow = { overflowTrack = it },
                            onDeleted = { nav.closeTop() },
                        )
                    }
                        }
                    }
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
                isFavorite = track.id in favoriteIds,
                onToggleFavorite = { homeViewModel.toggleFavorite(it) },
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
                AppTab.entries.forEach { tab ->
                    AppBottomNavigationItem(
                        tab = tab,
                        selected = nav.selectedTab == tab && nav.detailStack.isEmpty(),
                        onClick = {
                            nav.selectedTab = tab
                            nav.detailStack.clear()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigationRail(nav: AppNavState) {
    NavigationRail(
        containerColor = DhunColors.glassStrong,
        contentColor = DhunColors.textPrimary,
        modifier = Modifier.fillMaxHeight(),
    ) {
        AppTab.entries.forEach { tab ->
            AppRailNavigationItem(
                tab = tab,
                selected = nav.selectedTab == tab && nav.detailStack.isEmpty(),
                onClick = {
                    nav.selectedTab = tab
                    nav.detailStack.clear()
                },
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
