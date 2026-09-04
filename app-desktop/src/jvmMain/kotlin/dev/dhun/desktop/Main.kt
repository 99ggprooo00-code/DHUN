package dev.dhun.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.design.DhunTheme
import dev.dhun.design.catalog.ComponentCatalogScreen
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.player.NowPlayingPersistence
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.dhun.desktop.player.DesktopDhunPlayer
import dev.dhun.desktop.ui.DesktopHarnessScreen
import dev.dhun.desktop.ui.DesktopHarnessViewModel
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
import dev.dhun.ui.home.HomeViewModel
import dev.dhun.ui.navigation.AppShell
import dev.dhun.ui.search.SearchViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Phase 04 desktop entry point (code-complete; hardware verification is the
 * phase's remaining step — see docs/verification/04-desktop.md). Window
 * 1200x780, Koin graph, vlcj-backed player, throwaway harness screen — the
 * same verification loop as Phase 03 Android.
 */
fun main() = application {
    val koin = startKoin { modules(desktopModule) }.koin
    val appScope: CoroutineScope = koin.get()
    val player: DesktopDhunPlayer = koin.get()
    val viewModel: DesktopHarnessViewModel = koin.get()
    val persistence: NowPlayingPersistence = koin.get()

    // Phase 05: restore the last session (paused) then keep persisting.
    LaunchedEffect(Unit) {
        runCatching { persistence.restore() }.onFailure { System.err.println("DHUN restore failed: $it") }
        persistence.start()
    }

    Window(
        onCloseRequest = {
            persistence.stop()
            player.release()
            appScope.cancel()
            exitApplication()
        },
        state = rememberWindowState(width = 1200.dp, height = 780.dp),
        title = "DHUN",
    ) {
        DhunTheme {
            var showCatalog by remember { mutableStateOf(false) }
            if (showCatalog) {
                ComponentCatalogScreen(onClose = { showCatalog = false }, modifier = Modifier.fillMaxSize())
            } else {
                val homeViewModel = remember {
                    HomeViewModel(
                        innerTubeClient = koin.get(),
                        player = player,
                        data = koin.get(),
                        scope = appScope,
                    )
                }
                val searchViewModel = remember {
                    SearchViewModel(
                        innerTubeClient = koin.get(),
                        player = player,
                        data = koin.get(),
                        scope = appScope,
                    )
                }
                AppShell(
                    homeViewModel = homeViewModel,
                    searchViewModel = searchViewModel,
                    player = player,
                )
            }
        }
    }
}

private val desktopModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { InnerTubeClient() }
    single<MusicProvider> { YouTubeMusicProvider.forDesktop() }
    single { DesktopDhunPlayer(provider = get(), scope = get()) }
    // Phase 05 data layer — SQLite file in the per-OS user data dir.
    single { DataLayer(DatabaseFactory.create(DatabaseDriverFactory().createDriver())) }
    single {
        val data: DataLayer = get()
        NowPlayingPersistence(
            player = get<DesktopDhunPlayer>(),
            save = SaveNowPlayingUseCase(data.nowPlaying),
            restore = RestoreNowPlayingUseCase(data.nowPlaying, data.settings),
            recordPlay = RecordPlayUseCase(data.history),
            scope = get(),
            log = { println("DHUN persistence: $it") },
        )
    }
    single { DesktopHarnessViewModel(provider = get(), data = get(), scope = get()) }
}
