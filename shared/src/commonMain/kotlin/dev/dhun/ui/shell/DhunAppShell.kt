package dev.dhun.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.catalog.ComponentCatalogScreen
import dev.dhun.design.components.GlassBottomBar
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.browse.AlbumViewModel
import dev.dhun.presentation.browse.ArtistViewModel
import dev.dhun.presentation.browse.PlaylistViewModel
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.ui.browse.AlbumScreen
import dev.dhun.ui.browse.ArtistScreen
import dev.dhun.ui.browse.PlaylistScreen
import dev.dhun.ui.components.AddToPlaylistDialog
import dev.dhun.ui.components.TrackOverflowDialog
import dev.dhun.ui.home.HomeScreen
import dev.dhun.ui.player.FullPlayer
import dev.dhun.ui.player.MiniPlayer
import dev.dhun.ui.search.SearchScreen
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: String) {
    HOME("Home", "🏠"),
    SEARCH("Search", "🔍"),
    CATALOG("Catalog", "🎨"),
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
) {
    val scope = rememberCoroutineScope()
    var overflowTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    val favoriteIds by homeViewModel.favoriteIds.collectAsState()
    val currentTrack by playerViewModel.currentTrack.collectAsState()

    val onPlayTrack: (Track, List<Track>, Int) -> Unit = { track, contextQueue, index ->
        scope.launch {
            player.prepareQueue(contextQueue, index, playWhenReady = true)
        }
    }

    val openArtist: (Track) -> Unit = { track ->
        track.artistId?.let { nav.push(DetailRoute.ArtistPage(it)) }
    }
    val openAlbum: (Track) -> Unit = { track ->
        track.albumId?.let { nav.push(DetailRoute.AlbumPage(it)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DhunColors.background,
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Docked MiniPlayer (Phase 08) — hidden while the FullPlayer covers.
                    if (!nav.playerExpanded) {
                        MiniPlayer(
                            viewModel = playerViewModel,
                            onExpand = { nav.playerExpanded = true },
                        )
                    }

                    GlassBottomBar(modifier = Modifier.fillMaxWidth()) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            contentColor = DhunColors.textPrimary,
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        ) {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = nav.selectedTab == tab && nav.detailStack.isEmpty(),
                                    onClick = {
                                        nav.selectedTab = tab
                                        nav.detailStack.clear()
                                    },
                                    icon = { Text(text = tab.icon, fontSize = 18.sp) },
                                    label = {
                                        Text(text = tab.title, style = MaterialTheme.typography.labelSmall)
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DhunColors.accent,
                                        selectedTextColor = DhunColors.accent,
                                        unselectedIconColor = DhunColors.textTertiary,
                                        unselectedTextColor = DhunColors.textTertiary,
                                        indicatorColor = DhunColors.accentContainer,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (val route = nav.detailStack.lastOrNull()) {
                    null -> TabContent(
                        tab = nav.selectedTab,
                        homeViewModel = homeViewModel,
                        searchViewModel = searchViewModel,
                        onPlayTrack = onPlayTrack,
                        onNavigate = { nav.push(it) },
                        onTrackOverflow = { overflowTrack = it },
                    )
                    is DetailRoute.ArtistPage -> {
                        val vm = remember(route.id) { ArtistViewModel(provider, player, route.id) }
                        DisposableEffect(vm) { onDispose { vm.close() } }
                        ArtistScreen(
                            viewModel = vm,
                            onBack = { nav.closeTop() },
                            onTrackPlay = onPlayTrack,
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
                            onTrackPlay = onTrackPlay,
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
                            onTrackPlay = onPlayTrack,
                            onTrackOverflow = { overflowTrack = it },
                            onDeleted = { nav.closeTop() },
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
private fun TabContent(
    tab: AppTab,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    onPlayTrack: (Track, List<Track>, Int) -> Unit,
    onNavigate: (DetailRoute) -> Unit,
    onTrackOverflow: (Track) -> Unit,
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
        AppTab.CATALOG -> {
            ComponentCatalogScreen(
                onClose = { },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
