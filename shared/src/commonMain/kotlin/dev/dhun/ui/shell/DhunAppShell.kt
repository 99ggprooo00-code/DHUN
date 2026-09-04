package dev.dhun.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.PlaybackState
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.catalog.ComponentCatalogScreen
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.GlassBottomBar
import dev.dhun.innertube.SearchFilter
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.ui.components.AddToPlaylistDialog
import dev.dhun.ui.components.TrackOverflowDialog
import dev.dhun.ui.home.HomeScreen
import dev.dhun.ui.search.SearchScreen
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: String) {
    HOME("Home", "🏠"),
    SEARCH("Search", "🔍"),
    CATALOG("Catalog", "🎨"),
}

@Composable
fun DhunAppShell(
    player: DhunPlayer,
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    dataLayer: DataLayer,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var overflowTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    val favoriteIds by homeViewModel.favoriteIds.collectAsState()

    val onPlayTrack: (Track, List<Track>, Int) -> Unit = { track, contextQueue, index ->
        scope.launch {
            player.prepareQueue(contextQueue, index, playWhenReady = true)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DhunColors.background,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Docked MiniPlayer
                MiniPlayerBar(player = player)

                // Bottom Navigation
                GlassBottomBar(modifier = Modifier.fillMaxWidth()) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = DhunColors.textPrimary,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Text(
                                        text = tab.icon,
                                        fontSize = 18.sp,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
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
            when (selectedTab) {
                AppTab.HOME -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onTrackClick = onPlayTrack,
                        onAlbumClick = { album ->
                            searchViewModel.onQueryChange(album.title)
                            searchViewModel.performSearch(album.title, SearchFilter.ALBUMS)
                            selectedTab = AppTab.SEARCH
                        },
                        onPlaylistClick = { playlist ->
                            searchViewModel.onQueryChange(playlist.title)
                            searchViewModel.performSearch(playlist.title, SearchFilter.PLAYLISTS)
                            selectedTab = AppTab.SEARCH
                        },
                        onArtistClick = { artist ->
                            searchViewModel.onQueryChange(artist.name)
                            searchViewModel.performSearch(artist.name, SearchFilter.ARTISTS)
                            selectedTab = AppTab.SEARCH
                        },
                        onTrackOverflow = { overflowTrack = it },
                    )
                }
                AppTab.SEARCH -> {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onTrackClick = onPlayTrack,
                        onAlbumClick = { album ->
                            searchViewModel.onQueryChange(album.title)
                            searchViewModel.performSearch(album.title, SearchFilter.ALBUMS)
                        },
                        onPlaylistClick = { playlist ->
                            searchViewModel.onQueryChange(playlist.title)
                            searchViewModel.performSearch(playlist.title, SearchFilter.PLAYLISTS)
                        },
                        onArtistClick = { artist ->
                            searchViewModel.onQueryChange(artist.name)
                            searchViewModel.performSearch(artist.name, SearchFilter.ARTISTS)
                        },
                        onTrackOverflow = { overflowTrack = it },
                    )
                }
                AppTab.CATALOG -> {
                    ComponentCatalogScreen(
                        onClose = { selectedTab = AppTab.HOME },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Track Overflow Dialog
        overflowTrack?.let { track ->
            val isFav = track.id in favoriteIds
            TrackOverflowDialog(
                track = track,
                player = player,
                isFavorite = isFav,
                onToggleFavorite = { homeViewModel.toggleFavorite(it) },
                onAddToPlaylist = { addToPlaylistTrack = it },
                onNavigateToArtist = { artistName ->
                    searchViewModel.onQueryChange(artistName)
                    searchViewModel.performSearch(artistName, SearchFilter.ARTISTS)
                    selectedTab = AppTab.SEARCH
                },
                onNavigateToAlbum = { albumName ->
                    searchViewModel.onQueryChange(albumName)
                    searchViewModel.performSearch(albumName, SearchFilter.ALBUMS)
                    selectedTab = AppTab.SEARCH
                },
                onDismiss = { overflowTrack = null },
            )
        }

        // Add To Playlist Dialog
        addToPlaylistTrack?.let { track ->
            AddToPlaylistDialog(
                track = track,
                playlistRepository = dataLayer.playlists,
                onDismiss = { addToPlaylistTrack = null },
                onAdded = { /* Toast / confirmation handled in dialog */ },
            )
        }
    }
}

/**
 * Docked MiniPlayer showing current playback status and quick transport controls.
 */
@Composable
private fun MiniPlayerBar(player: DhunPlayer) {
    val state by player.state.collectAsState()
    val currentTrack by player.currentTrack.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()

    if (currentTrack != null) {
        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

        GlassBottomBar(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1dp progress indicator line
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = DhunColors.accent,
                    trackColor = DhunColors.surfaceVariant,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    ArtworkImage(
                        imageUrl = currentTrack?.thumbnailUrl,
                        contentDescription = currentTrack?.title,
                        modifier = Modifier.size(44.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DhunColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = buildString {
                                append(currentTrack?.artistName ?: "")
                                if (state is PlaybackState.Buffering) append(" • Buffering…")
                                else if (state is PlaybackState.Resolving) append(" • Resolving…")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = DhunColors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DhunIconButton(
                        onClick = player::playPause,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = if (state is PlaybackState.Playing) "⏸" else "▶",
                            fontSize = 20.sp,
                            color = DhunColors.textPrimary,
                        )
                    }
                    DhunIconButton(
                        onClick = player::next,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = "⏭",
                            fontSize = 18.sp,
                            color = DhunColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
