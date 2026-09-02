package dev.dhun.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.exitApplication
import androidx.compose.ui.window.rememberWindowState
import dev.dhun.desktop.player.DesktopDhunPlayer
import dev.dhun.desktop.ui.DesktopHarnessScreen
import dev.dhun.desktop.ui.DesktopHarnessViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
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

    Window(
        onCloseRequest = {
            player.release()
            appScope.cancel()
            exitApplication()
        },
        state = rememberWindowState(width = 1200.dp, height = 780.dp),
        title = "DHUN",
    ) {
        DesktopHarnessScreen(player = player, viewModel = viewModel)
    }
}

private val desktopModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<MusicProvider> { YouTubeMusicProvider.forDesktop() }
    single { DesktopDhunPlayer(provider = get(), scope = get()) }
    single { DesktopHarnessViewModel(provider = get(), scope = get()) }
}
