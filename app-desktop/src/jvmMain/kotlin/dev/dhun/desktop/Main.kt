package dev.dhun.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.design.DhunTheme
import dev.dhun.desktop.player.DesktopDhunPlayer
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.player.NowPlayingPersistence
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.lyrics.LrcLibSource
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.lyrics.YouTubeLyricsSource
import dev.dhun.provider.YouTubeMusicProvider
import dev.dhun.provider.forDesktop
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.DhunAppShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Phase 04 + 07 + 08 Desktop entry point.
 * Window 1200x780, Koin graph, vlcj-backed player, full shared DhunAppShell UI
 * (Home, Search, full Player with volume slider, Catalog).
 */
fun main() = application {
    val koin = startKoin { modules(desktopModule) }.koin
    val appScope: CoroutineScope = koin.get()
    val player: DesktopDhunPlayer = koin.get()
    val homeViewModel: HomeViewModel = koin.get()
    val searchViewModel: SearchViewModel = koin.get()
    val playerViewModel: PlayerViewModel = koin.get()
    val provider: MusicProvider = koin.get()
    val dataLayer: DataLayer = koin.get()
    val persistence: NowPlayingPersistence = koin.get()

    // Restore the last session (paused) then keep persisting.
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
            val nav = remember { AppNavState() }
            DhunAppShell(
                player = player,
                homeViewModel = homeViewModel,
                searchViewModel = searchViewModel,
                playerViewModel = playerViewModel,
                provider = provider,
                dataLayer = dataLayer,
                nav = nav,
                isDesktop = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val desktopModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
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

    single { GetHomeFeedUseCase(get(), get<DataLayer>().history) }

    // Phase 11 lyrics — cache → YTM → LRCLIB → NotAvailable, persisted in SQLDelight
    single { LrcLibSource() }
    single { YouTubeLyricsSource(get()) }
    single {
        val data: DataLayer = get()
        LyricsRepository(cache = data.lyricsCache, ytm = get(), lrcLib = get())
    }

    // Phase 08 player UI model (queue ops, related/lyrics tabs, hold-to-seek) + Phase 11 lyrics repo.
    single {
        PlayerViewModel(
            player = get<DesktopDhunPlayer>(),
            provider = get(),
            scope = get(),
            persistence = get(),
            lyricsRepository = get(),
        )
    }

    single {
        HomeViewModel(
            getHomeFeed = get(),
            historyRepository = get<DataLayer>().history,
            libraryRepository = get<DataLayer>().library,
            scope = get(),
        )
    }

    single {
        SearchViewModel(
            provider = get(),
            searchRepository = get<DataLayer>().search,
            libraryRepository = get<DataLayer>().library,
            playlistRepository = get<DataLayer>().playlists,
            scope = get(),
        )
    }
}
