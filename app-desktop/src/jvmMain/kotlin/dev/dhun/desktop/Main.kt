package dev.dhun.desktop

import dev.dhun.design.DhunSpacing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
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
 *    playing/paused icon variants — also the documented SMTC fallback path
 *  - SMTC phase 2: now-playing metadata, remote thumbnail, playback state,
 *    previous/next enablement, and native ButtonPressed dispatch to the
 *    shared player; activation failures degrade to the tray path
 *  - close-to-tray (setting [SettingsKeys.CLOSE_TO_TRAY], default on): the
 *    main window's X hides to tray; tray "Quit" exits clean
 *  - window geometry persisted to [SettingsKeys.WINDOW_GEOMETRY] ("x,y,w,h"
 *    px) — read from the live [WindowState] (position is kept current by the
 *    Compose window's component listener), restored via WindowPosition
 *  - keyboard shortcuts (window-scope [Window.onKeyEvent], receives only keys
 *    the focused node didn't consume): Space play/pause, ←/→ seek ±5 s,
 *    Ctrl+←/→ prev/next, Ctrl+F search, Ctrl+M mini-player, Ctrl+Q quit
 *
 * Compose Desktop 1.8.2 API notes (verified against
 * JetBrains/compose-multiplatform-core v1.8.2 sources):
 *  - `Window` content is `FrameWindowScope.() -> Unit`; `window` is a
 *    [ComposeWindow] which extends `javax.swing.JFrame` — all window control
 *    (show/hide/toFront/requestFocus) is plain public AWT on it.
 *  - `alwaysOnTop` is a top-level `Window` parameter (not in WindowState).
 *  - `WindowPosition` (Dp-based) for position; no `skipTaskbar` parameter
 *    in 1.8.2 (mini-player shows in the taskbar — see KNOWN_LIMITATIONS).
 *  - Arrows are `Key.DirectionLeft/DirectionRight`; space is `Key.Spacebar`.
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
                    x = (x.toFloatOrNull() ?: 0f).toLong(),
                    y = (y.toFloatOrNull() ?: 0f).toLong(),
                    w = (w.toFloatOrNull() ?: 1200f).toLong(),
                    h = (h.toFloatOrNull() ?: 780f).toLong(),
                )
            }
    }
    val closeToTray = runBlocking {
        runCatching {
            settings.getBoolean(SettingsKeys.CLOSE_TO_TRAY, SettingsKeys.CLOSE_TO_TRAY_DEFAULT)
        }.getOrDefault(true)
    }

    // Window states (hoisted so close-to-tray/quit can read the live geometry:
    // Compose keeps position/size current via the AWT component listener).
    val mainState = rememberWindowState(
        width = initialGeometry?.w?.toFloat()?.dp ?: DhunSpacing.windowDefaultWidth,
        height = initialGeometry?.h?.toFloat()?.dp ?: DhunSpacing.windowDefaultHeight,
        position = initialGeometry?.let { WindowPosition(it.x.toFloat().dp, it.y.toFloat().dp) }
            ?: WindowPosition.PlatformDefault,
    )
    val miniState = rememberWindowState(
        width = DhunSpacing.miniPlayerWindowWidth,
        height = DhunSpacing.transportRowHeight,
        position = WindowPosition(DhunSpacing.contentBottomInset, DhunSpacing.miniPlayerHeight),
    )

    val mainWindowRef = AtomicReference<ComposeWindow>()
    val miniWindowRef = AtomicReference<ComposeWindow>()
    val smctSessionRef = AtomicReference<Smct.Session?>()

    fun showMainWindow() {
        val w = mainWindowRef.get() ?: return
        w.isVisible = true
        w.toFront()
        w.requestFocus()
    }

    fun toggleMiniPlayer() {
        val w = miniWindowRef.get() ?: return
        w.isVisible = !w.isVisible
        if (w.isVisible) {
            w.toFront()
            w.requestFocus()
        }
    }

    fun saveGeometry() {
        val p = mainState.position
        if (p !is WindowPosition.Absolute) return
        val s = mainState.size
        val geo = "${p.x.value},${p.y.value},${s.width.value},${s.height.value}"
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
        runCatching { smctSessionRef.getAndSet(null)?.close() }
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

    // Phase 12 SMTC phase 2: connect after the AWT main window exists. A
    // failed activation or event registration leaves the documented tray /
    // keyboard fallback active; no native failure reaches the UI.
    appScope.launch {
        kotlinx.coroutines.delay(2_000)
        val session = Smct.connect(
            windowTitle = "DHUN",
            onButton = { button ->
                // WinRT invokes this callback from a native thread. Keep all
                // player calls on the app scope instead of the callback.
                appScope.launch {
                    when (button) {
                        Smct.Button.Play -> if (player.state.value !is PlaybackState.Playing) player.playPause()
                        Smct.Button.Pause -> if (player.state.value is PlaybackState.Playing) player.playPause()
                        Smct.Button.Stop -> player.stop()
                        Smct.Button.Next -> player.next()
                        Smct.Button.Previous -> player.previous()
                        Smct.Button.FastForward -> player.seekTo(player.positionMs.value + 10_000L)
                        Smct.Button.Rewind -> player.seekTo((player.positionMs.value - 10_000L).coerceAtLeast(0L))
                        Smct.Button.Record,
                        Smct.Button.ChannelUp,
                        Smct.Button.ChannelDown,
                        Smct.Button.Unknown,
                        -> Unit
                    }
                }
            },
        ) ?: return@launch
        smctSessionRef.set(session)

        appScope.launch {
            player.currentTrack.collect { track ->
                session.updateMetadata(
                    title = track?.title,
                    artist = track?.artistName,
                    album = track?.albumName,
                    thumbnailUrl = track?.thumbnailUrl,
                )
            }
        }
        appScope.launch {
            player.state.collect { state ->
                session.setPlaybackState(
                    when (state) {
                        is PlaybackState.Playing -> Smct.PlaybackStatus.Playing
                        is PlaybackState.Resolving,
                        is PlaybackState.Buffering,
                        -> Smct.PlaybackStatus.Changing
                        is PlaybackState.Paused -> Smct.PlaybackStatus.Paused
                        is PlaybackState.Idle,
                        is PlaybackState.Error,
                        -> Smct.PlaybackStatus.Stopped
                    },
                )
            }
        }
        appScope.launch {
            player.queue.collect { queue ->
                val index = player.currentQueueIndex.value
                session.setNavigationButtons(hasPrevious = index > 0, hasNext = index >= 0 && index < queue.lastIndex)
            }
        }
        appScope.launch {
            player.currentQueueIndex.collect { index ->
                val queueSize = player.queue.value.size
                session.setNavigationButtons(hasPrevious = index > 0, hasNext = index >= 0 && index < queueSize - 1)
            }
        }
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
            // X hides (not disposes; 1.8.2 sets DO_NOTHING_ON_CLOSE itself) —
            // Ctrl+M / tray "Open" always work.
            miniWindowRef.get()?.isVisible = false
        },
        state = miniState,
        title = "DHUN mini-player",
        resizable = false,
        alwaysOnTop = true,
    ) {
        // FrameWindowScope: `window` is the ComposeWindow (a JFrame).
        LaunchedEffect(window) {
            miniWindowRef.set(window)
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
                mainWindowRef.get()?.isVisible = false
            } else {
                quit()
            }
        },
        state = mainState,
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
                event.isCtrlPressed && event.key == Key.DirectionLeft -> {
                    playerViewModel.previous()
                    true
                }
                event.isCtrlPressed && event.key == Key.DirectionRight -> {
                    playerViewModel.next()
                    true
                }
                event.key == Key.DirectionLeft -> {
                    playerViewModel.seekTo(
                        (playerViewModel.positionMs.value - SEEK_STEP_MS).coerceAtLeast(0L),
                    )
                    true
                }
                event.key == Key.DirectionRight -> {
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
                event.key == Key.Spacebar && !event.isCtrlPressed -> {
                    playerViewModel.togglePlay()
                    true
                }
                else -> false
            }
        },
    ) {
        // FrameWindowScope: `window` is the ComposeWindow (a JFrame).
        LaunchedEffect(window) {
            mainWindowRef.set(window)
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
                connectivity = koin.get(),
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
    // Phase 14: connectivity signal for the shared offline banner (5s poll).
    single<dev.dhun.core.ConnectivityMonitor> { dev.dhun.core.DesktopConnectivityMonitor(get()) }

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
