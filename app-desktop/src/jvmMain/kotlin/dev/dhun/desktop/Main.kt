package dev.dhun.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.px
import androidx.compose.ui.window.ComposeWindow
import androidx.compose.ui.window.LocalWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.dhun.core.PlaybackState
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SettingsKeys
import dev.dhun.design.DhunTheme
import dev.dhun.desktop.native.DhunTray
import dev.dhun.desktop.player.DesktopDhunPlayer
import dev.dhun.desktop.smct.Smct
import dev.dhun.desktop.ui.MiniPlayerContent
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.lyrics.LrcLibSource
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.lyrics.YouTubeLyricsSource
import dev.dhun.player.NowPlayingPersistence
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
import dev.dhun.provider.forDesktop
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DhunAppShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 04 + 07 + 08 + 12 Desktop entry point.
 *
 * Two windows: the main app window (1200×780) and the Phase 12 mini-player
 * window (320×88, always on top, draggable, click → main).
 *
 * Phase 12 additions (this file):
 *  - system tray (AWT): track title + play/pause/next/prev/open/quit menu,
 *    playing/paused icon variants — the documented SMTC fallback path
 *  - close-to-tray (setting [SettingsKeys.CLOSE_TO_TRAY], default on): the
 *    main window's X hides to tray; tray "Quit" exits clean
 *  - window geometry persisted to [SettingsKeys.WINDOW_GEOMETRY] ("x,y,w,h",
 *    px; read via JNA GetWindowRect on Windows — the primary desktop OS)
 *  - keyboard shortcuts (window-scope [Window.onKeyEvent], which receives
 *    only keys the focused node didn't consume): Space play/pause,
 *    ←/→ seek ±5 s, Ctrl+←/→ prev/next, Ctrl+F search, Ctrl+M mini-player,
 *    Ctrl+Q quit
 *
 * NOTE (1.8.2 API): `Window`'s content lambda takes NO receiver
 * (WindowScope landed later) — the AWT/Compose window handle comes from
 * [LocalWindow] (`ComposeWindow.show/hide/requestFocus`).
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
    val settings = dataLayer.settings

    // Phase 12: persisted window geometry + close-to-tray (Phase 05 DB).
    val initialGeometry: WindowGeometry? = runBlocking {
        runCatching { settings.getString(SettingsKeys.WINDOW_GEOMETRY) }
            .getOrNull()
            ?.split(',')
            ?.takeIf { it.size == 4 }
            ?.let { (x, y, w, h) ->
                WindowGeometry(
                    x = x.toLongOrNull() ?: 0L,
                    y = y.toLongOrNull() ?: 0L,
                    w = w.toLongOrNull() ?: 1200L,
                    h = h.toLongOrNull() ?: 780L,
                )
            }
    }
    val closeToTray = runBlocking {
        runCatching {
            settings.getBoolean(SettingsKeys.CLOSE_TO_TRAY, SettingsKeys.CLOSE_TO_TRAY_DEFAULT)
        }.getOrDefault(true)
    }

    val mainWindowRef = AtomicReference<ComposeWindow>()
    val miniWindowRef = AtomicReference<ComposeWindow>()
    var miniVisible = true

    fun showMainWindow() {
        val w = mainWindowRef.get() ?: return
        w.show()
        w.requestFocus()
    }

    fun toggleMiniPlayer() {
        val w = miniWindowRef.get() ?: return
        miniVisible = !miniVisible
        if (miniVisible) {
            w.show()
            w.requestFocus()
        } else {
            w.hide()
        }
    }

    /** Windows px rect of the main window (null off-Windows / not found). */
    fun saveGeometry() {
        val rect = Smct.windowRect("DHUN") ?: return
        val geo = "${rect[0]},${rect[1]},${rect[2]},${rect[3]}"
        appScope.launch { runCatching { settings.putString(SettingsKeys.WINDOW_GEOMETRY, geo) } }
    }

    val quitRef = AtomicReference<() -> Unit>({ System.exit(0) })

    val tray = DhunTray(
        onPlayPause = { player.playPause() },
        onNext = { player.next() },
        onPrevious = { player.previous() },
        onOpen = { showMainWindow() },
        onQuit = { quitRef.get().invoke() },
    )

    /** Tray "Quit" and Ctrl+Q converge here — one clean exit, no zombies. */
    fun quit() {
        saveGeometry()
        runCatching { tray.stop() }
        runCatching { persistence.stop() }
        runCatching { player.release() }
        appScope.cancel()
        System.exit(0)
    }
    quitRef.set(::quit)

    tray.start()

    // Tray mirrors the player (collectors run on Dispatchers.Default; the
    // tray marshals to the EDT internally).
    appScope.launch { player.currentTrack.collect { tray.setTrack(it) } }
    appScope.launch { player.state.collect { tray.setPlaying(it is PlaybackState.Playing) } }

    // Phase 12 SMTC spike (phase 1): passive probe after the main window is
    // up. Logs "SMTC probe PASS/FAIL — …" to the console; disable with
    // -Ddhun.smct=false. No integration yet (metadata + button events are
    // spike phase 2, see docs/verification/12-desktop-native.md).
    appScope.launch {
        kotlinx.coroutines.delay(2_000)
        runCatching { Smct.probe("DHUN") }
            .onFailure { System.err.println("DHUN SMTC probe error: $it") }
    }

    // Restore the last session (paused) then keep persisting.
    LaunchedEffect(Unit) {
        runCatching { persistence.restore() }.onFailure { System.err.println("DHUN restore failed: $it") }
        persistence.start()
    }

    val nav = remember { AppNavState() }

    // ---- Phase 12 mini-player window (declared first so the main window   //
    // ---- owns the startup focus) ------------------------------------------ //
    Window(
        onCloseRequest = {
            // X hides (not disposes) — Ctrl+M / tray "Open" always work.
            miniVisible = false
            miniWindowRef.get()?.hide()
        },
        state = rememberWindowState(
            width = 320.dp,
            height = 88.dp,
            position = Offset(96f, 72f),
            alwaysOnTopValue = true,
        ),
        title = "DHUN mini-player",
        resizable = false,
        skipTaskbar = true,
    ) {
        val miniWindow = LocalWindow.current
        LaunchedEffect(miniWindow) {
            miniWindowRef.set(miniWindow)
        }
        DhunTheme {
            MiniPlayerContent(
                viewModel = playerViewModel,
                onOpenMain = { showMainWindow() },
            )
        }
    }

    // ---- main window ------------------------------------------------------- //
    Window(
        onCloseRequest = {
            if (closeToTray) {
                // Phase 12: close → tray (default on). Tray "Quit" exits.
                saveGeometry()
                mainWindowRef.get()?.hide()
            } else {
                quit()
            }
        },
        state = rememberWindowState(
            width = initialGeometry?.let { it.w.toFloat().px } ?: 1200.dp,
            height = initialGeometry?.let { it.h.toFloat().px } ?: 780.dp,
            position = initialGeometry?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
                ?: Offset.Unspecified,
        ),
        title = "DHUN",
        // Window-scope shortcuts. onKeyEvent (NOT onPreviewKeyEvent) receives
        // only keys the focused node didn't consume — so typing Space /
        // arrows in the search field stays untouched, while Ctrl-combos
        // (not consumed by the field) always reach us.
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            when {
                event.isCtrlPressed && event.key == Key.Q -> {
                    quit()
                    true
                }
                event.isCtrlPressed && event.key == Key.M -> {
                    toggleMiniPlayer()
                    true
                }
                event.isCtrlPressed && event.key == Key.F -> {
                    nav.selectedTab = AppTab.SEARCH
                    true
                }
                event.isCtrlPressed && event.key == Key.LEFT -> {
                    playerViewModel.previous()
                    true
                }
                event.isCtrlPressed && event.key == Key.RIGHT -> {
                    playerViewModel.next()
                    true
                }
                event.key == Key.LEFT -> {
                    playerViewModel.seekTo(
                        (playerViewModel.positionMs.value - SEEK_STEP_MS).coerceAtLeast(0L),
                    )
                    true
                }
                event.key == Key.RIGHT -> {
                    val duration = playerViewModel.durationMs.value
                    if (duration > 0) {
                        playerViewModel.seekTo(
                            (playerViewModel.positionMs.value + SEEK_STEP_MS).coerceAtMost(duration),
                        )
                        true
                    } else {
                        false
                    }
                }
                event.key == Key.Space && !event.isCtrlPressed -> {
                    playerViewModel.togglePlay()
                    true
                }
                else -> false
            }
        },
    ) {
        val mainWindow = LocalWindow.current
        LaunchedEffect(mainWindow) {
            mainWindowRef.set(mainWindow)
        }
        DhunTheme {
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

private const val SEEK_STEP_MS = 5_000L

/** Restored "x,y,w,h" (px) from [SettingsKeys.WINDOW_GEOMETRY]. */
private data class WindowGeometry(val x: Long, val y: Long, val w: Long, val h: Long)

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
