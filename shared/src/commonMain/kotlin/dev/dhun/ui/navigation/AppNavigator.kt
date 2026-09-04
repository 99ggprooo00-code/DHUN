package dev.dhun.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.player.DhunPlayer
import dev.dhun.ui.home.HomeScreen
import dev.dhun.ui.home.HomeViewModel
import dev.dhun.ui.search.SearchScreen
import dev.dhun.ui.search.SearchViewModel

/**
 * Phase 07 shared app shell — manages navigation between Home, Search, and
 * optionally Library/Player. Uses a simple stack (no Navigation Compose
 * dependency) so it works on both Android and Desktop.
 */
@Composable
fun AppShell(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    player: DhunPlayer,
    modifier: Modifier = Modifier,
    initialScreen: Screen = Screen.Home,
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var searchInitialQuery by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = DhunColors.background,
        bottomBar = {
            BottomNavBar(
                currentScreen = currentScreen,
                onNavigate = { screen ->
                    currentScreen = screen
                    searchInitialQuery = null
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (currentScreen) {
                Screen.Home -> HomeScreen(
                    viewModel = homeViewModel,
                    onSearchQuery = { query ->
                        searchInitialQuery = query
                        currentScreen = Screen.Search
                    },
                )
                Screen.Search -> SearchScreen(
                    viewModel = searchViewModel,
                    initialQuery = searchInitialQuery ?: "",
                    onNavigateBack = {
                        currentScreen = Screen.Home
                        searchInitialQuery = null
                    },
                )
                Screen.Library -> {
                    EmptyScreenPlaceholder("Library — coming in Phase 10")
                }
                Screen.Player -> {
                    EmptyScreenPlaceholder("Player — coming in Phase 08")
                }
                is Screen.Artist -> {
                    EmptyScreenPlaceholder("Artist — coming in Phase 09")
                }
                is Screen.Album -> {
                    EmptyScreenPlaceholder("Album — coming in Phase 09")
                }
                is Screen.Playlist -> {
                    EmptyScreenPlaceholder("Playlist — coming in Phase 09")
                }
            }
        }
    }
}

@Composable
private fun EmptyScreenPlaceholder(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = DhunColors.textTertiary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun BottomNavBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding)
            .padding(bottom = DhunSpacing.md),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem(label = "Home", emoji = "🏠", selected = currentScreen == Screen.Home) { onNavigate(Screen.Home) }
        NavItem(label = "Search", emoji = "🔍", selected = currentScreen == Screen.Search) { onNavigate(Screen.Search) }
        NavItem(label = "Library", emoji = "📚", selected = currentScreen == Screen.Library) { onNavigate(Screen.Library) }
    }
}

@Composable
private fun NavItem(
    label: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(vertical = DhunSpacing.sm)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) DhunColors.accent else DhunColors.textTertiary,
        )
    }
}
