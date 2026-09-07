package dev.dhun.desktop

import dev.dhun.design.DhunSpacing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import dev.dhun.data.DhunUserDirs
import dev.dhun.data.SettingsKeys
import dev.dhun.design.DhunTheme
import dev.dhun.download.DownloadManager
import dev.dhun.download.FileDownloadManager
import dev.dhun.download.JvmDownloadStorage
import dev.dhun.download.KtorStreamDownloader
import dev.dhun.download.createDownloadHttpClient
import dev.dhun.extraction.OfflineFirstStreamResolver
import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.ResolvingStreamResolver
import dev.dhun.extraction.StreamResolver
import dev.dhun.extraction.YtDlpStreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.desktop.native.DhunTray
import dev.dhun.desktop.player.DesktopDhunPlayer
import dev.dhun.desktop.smct.Smct
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.lyrics.LrcLibSource
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.lyrics.YouTubeLyricsSource
import dev.dhun.player.AudioCacheBudget
import dev.dhun.player.AudioFileCache
import dev.dhun.player.NowPlayingPersistence
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
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
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * Phase 04 + 07 + 08 + 12 Desktop entry point.
 *
 * One window: the main app window (1200×780) with the in-app docked
 * MiniPlayer above the bottom nav. The separate Phase 12 mini-player window
 * (320×88, always on top) was REMOVED on 2026-09-06 per user decision —
 * ADR-004: the docked mini-player is the product's mini-player, so a second
 * window is redundant (and showed in the taskbar, which Compose Desktop
 * 1.8.2 cannot suppress). The removed window lived in
 * `desktop/ui/MiniPlayerWindow.kt` (deleted); its Ctrl+M toggle and the
 * `Smct.moveWindow` helper went with it.
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
 *    Ctrl+←/→ prev/next, Ctrl+F search, Ctrl+Q quit
 *
 * Phase 14 ruggedization — \"Failed to launch JVM\" investigation:
 *  - jpackage bundles a jlink-minimized runtime; missing JDK modules (notably
 *    java.sql for sqlite-jdbc) made the *launcher* fail with \"Failed to
 *    launch JVM\" before any app code ran. Fixed in build.gradle.kts via
 *    `modules(...)` + `includeAllModules`.
 *  - VLc initialization is now fault-tolerant (see DesktopDhunPlayer): a
 *    missing/incompatible VLC no longer crashes startup; the player enters
 *    an Error state with install instructions.
 *  - Startup now captures every exception to a log file
 *    (<installDir>/userdata/dhun-startup.log or %TEMP%/dhun-startup.log)
 *    and shows an AWT dialog so a Windows MSI user without a console sees
 *    the actual cause instead of the generic launcher message.
 *
 * Compose Desktop 1.8.2 API notes (verified against
 * JetBrains/compose-multiplatform-core v1.8.2 sources):
 *  - `Window` content is `FrameWindowScope.() -> Unit`; `window` is a
 *    [ComposeWindow] which extends `javax.swing.JFrame` — all window control
 *    (show/hide/toFront/requestFocus) is plain public AWT on it.
 *  - `WindowPosition` (Dp-based) for position.
 *  - Arrows are `Key.DirectionLeft/DirectionRight`; space is `Key.Spacebar`.
 *  - `alwaysOnTop` and the missing `skipTaskbar` parameter mattered only for
 *    the removed mini-player window (ADR-004).
 */

// ---------------------------------------------------------------------------
// Startup diagnostics — log file + dialog so MSI users see the real cause.
// ---------------------------------------------------------------------------

private fun startupLogFile(): File {
    return try {
        File(DhunUserDirs.dataDir(), "dhun-startup.log")
    } catch (_: Throwable) {
        File(System.getProperty("java.io.tmpdir") ?: ".", "dhun-startup.log")
    }
}

private fun logStartup(msg: String) {
    try {
        val f = startupLogFile()
        f.parentFile?.mkdirs()
        f.appendText("[${Instant.now()}] $msg\n")
    } catch (_: Throwable) { /* log failure must never crash startup */ }
    println(msg)
}

private fun logStartupFailure(e: Throwable) {
    try {
        val f = startupLogFile()
        f.parentFile?.mkdirs()
        val sw = java.io.StringWriter()
        e.printStackTrace(java.io.PrintWriter(sw))
        f.appendText(
            "[${Instant.now()}] FAILURE: ${e::class.qualifiedName}: ${e.message}\n" +
                "${sw}\n" +
                "OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} arch=${System.getProperty("os.arch")}\n" +
                "Java: ${System.getProperty("java.version")} runtime=${System.getProperty("java.runtime.version")} vendor=${System.getProperty("java.vendor")}\n" +
                "jpackage.app-path: ${System.getProperty("jpackage.app-path")}\n" +
                "user.home: ${System.getProperty("user.home")} user.dir: ${System.getProperty("user.dir")}\n---\n",
        )
    } catch (_: Throwable) { }
}

private fun showStartupErrorDialog(e: Throwable) {
    try {
        val logPath = startupLogFile().absolutePath
        val message = buildString {
            appendLine("DHUN failed to start:")
            appendLine("${e::class.simpleName}: ${e.message}")
            appendLine()
            appendLine("Log: $logPath")
            appendLine()
            appendLine("If this mentions VLC / libvlc:")
            appendLine("  Install VLC from https://www.videolan.org/vlc/ and restart.")
            appendLine()
            appendLine("If this mentions java.sql / JDBC / sqlite:")
            appendLine("  This build is missing Java modules — please report the log.")
            appendLine()
            appendLine("Try uninstalling + reinstalling the MSI.")
        }
        // Show on EDT; if we're already on EDT this still works.
        SwingUtilities.invokeLater {
            try {
                JOptionPane.showMessageDialog(
                    null,
                    message,
                    "DHUN — Startup Error",
                    JOptionPane.ERROR_MESSAGE,
                )
            } catch (_: Throwable) { }
        }
        // Fallback: block EDT for 0.5s so dialog has time to appear when called from non-EDT startup path
        // (not strictly needed, but keeps the error visible if the app exits immediately after).
    } catch (_: Throwable) { }
}

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
        System.err.println("DHUN uncaught on ${thread.name}: $ex")
        ex.printStackTrace()
        logStartupFailure(ex)
    }
    logStartup(
        "DHUN main starting — installer=${System.getProperty("dhun.installer.version", "development")} " +
            "java=${System.getProperty("java.version")} runtime=${System.getProperty("java.runtime.version")} " +
            "os=${System.getProperty("os.name")} jpackage.app-path=${System.getProperty("jpackage.app-path")}",
    )
    // Early module probes — if the bundled jlink image is missing java.sql,
    // the JVM would already have failed to launch before reaching here.
    // Probing here documents the bundled modules for the log and catches
    // any drift before DataLayer construction.
    runCatching { Class.forName("java.sql.Driver") }
        .onSuccess { logStartup("DHUN probe: java.sql.Driver available") }
        .onFailure { e -> logStartup("DHUN probe FAILED: java.sql.Driver missing: $e") }
    runCatching { Class.forName("org.sqlite.JDBC") }
        .onSuccess { logStartup("DHUN probe: org.sqlite.JDBC available") }
        .onFailure { e -> logStartup("DHUN probe FAILED: org.sqlite.JDBC missing: $e") }
    runCatching { Class.forName("uk.co.caprica.vlcj.factory.MediaPlayerFactory") }
        .onSuccess { logStartup("DHUN probe: vlcj MediaPlayerFactory available") }
        .onFailure { e -> logStartup("DHUN probe FAILED: vlcj missing: $e") }
    try {
        application {
            // Eagerly capture any initialization failure so the Windows MSI
            // user sees a dialog + log instead of a silent exit or the
            // generic \"Failed to launch JVM\" from the launcher.
            var initError: Throwable? = null
            var koinInstance: org.koin.core.Koin? = null

            // Attempt to boot Koin + critical singletons. DataLayer and
            // DesktopDhunPlayer are the two that can throw before the window
            // opens (DB driver / VLC). The try/catch ensures we log them and
            // can still open a minimal error window.
            try {
                val koinApp = startKoin { modules(desktopModule) }
                koinInstance = koinApp.koin
                // Probe the most failure-prone singletons eagerly so their
                // exceptions are captured here, not later on a collector thread.
                runCatching { koinInstance.get<DataLayer>() }.onFailure { e ->
                    logStartupFailure(e)
                    System.err.println("DHUN DataLayer probe failed: $e")
                    throw e
                }
                runCatching { koinInstance.get<DesktopDhunPlayer>() }.onFailure { e ->
                    // VLC failure is already degraded inside the player; log but don't abort.
                    logStartupFailure(e)
                    System.err.println("DHUN DesktopDhunPlayer probe failed (degraded mode): $e")
                }
                logStartup("DHUN Koin initialized successfully")
            } catch (e: Throwable) {
                initError = e
                logStartupFailure(e)
                System.err.println("DHUN initialization failed before window: $e")
                e.printStackTrace()
                showStartupErrorDialog(e)
            }

            if (initError != null && koinInstance == null) {
                // Cannot boot at all — show a minimal error window so the user
                // at least sees that the app *did* launch but failed to init
                // (the launcher's \"Failed to launch JVM\" would otherwise be
                // the only signal, with no log location).
                logStartup("DHUN opening minimal error window due to init failure")
                Window(
                    onCloseRequest = ::exitApplication,
                    title = "DHUN — Startup Error",
                ) {
                    DhunTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val logPath = startupLogFile().absolutePath
                            Text(
                                "DHUN failed to start.\n\n" +
                                    "${initError!!::class.simpleName}: ${initError!!.message}\n\n" +
                                    "See log:\n$logPath\n\n" +
                                    "If the log mentions VLC, install VLC and restart.\n" +
                                    "If it mentions java.sql/JDBC, please report this log.",
                            )
                        }
                    }
                }
                return@application
            }

            // Normal startup path — Koin is available.
            val koin = koinInstance!!
            val appScope: CoroutineScope = koin.get()
            val player: DesktopDhunPlayer = koin.get()
            val homeViewModel: HomeViewModel = koin.get()
            val searchViewModel: SearchViewModel = koin.get()
            val playerViewModel: PlayerViewModel = koin.get()
            val provider: MusicProvider = koin.get()
            val dataLayer: DataLayer = koin.get()
            val persistence: NowPlayingPersistence = koin.get()
            val settings = dataLayer.settings

            if (initError != null) {
                // We had a non-fatal init error (e.g. VLC): surface it once via dialog.
                showStartupErrorDialog(initError!!)
            }

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

            val mainWindowRef = AtomicReference<ComposeWindow>()
            val smctSessionRef = AtomicReference<Smct.Session?>()

            fun showMainWindow() {
                val w = mainWindowRef.get() ?: return
                w.isVisible = true
                w.toFront()
                w.requestFocus()
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

            /** Tray \"Quit\" and Ctrl+Q converge here — one clean exit, no zombies. */
            fun quit() {
                saveGeometry()
                runCatching { tray.stop() }
                runCatching { smctSessionRef.getAndSet(null)?.close() }
                runCatching { persistence.stop() }
                runCatching { player.release() }
                appScope.cancel()
                exitApplication()
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
                                is PlaybackState.Recovering,
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
                runCatching { persistence.restore() }.onFailure {
                    System.err.println("DHUN restore failed: $it")
                    logStartupFailure(it)
                }
                persistence.start()
            }

            val nav = remember { AppNavState() }

            // ---- main window ------------------------------------------------------- //
            Window(
                onCloseRequest = {
                    if (closeToTray) {
                        // Phase 12: close → tray (default on). Tray \"Quit\" exits.
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
                    logStartup("DHUN main window opened successfully")
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
                        downloadManager = koin.get(),
                    )
                }
            }
        }
    } catch (e: Throwable) {
        logStartupFailure(e)
        System.err.println("DHUN outer main failed: $e")
        e.printStackTrace()
        showStartupErrorDialog(e)
        // Give the dialog a moment to appear before forcing exit (JOptionPane is modal on EDT).
        try { Thread.sleep(500) } catch (_: Throwable) { }
        try { JOptionPane.showMessageDialog(null, "DHUN failed to launch:\n${e::class.simpleName}: ${e.message}\n\nLog: ${startupLogFile().absolutePath}", "DHUN Fatal", JOptionPane.ERROR_MESSAGE) } catch (_: Throwable) { }
        System.exit(1)
    }
}

private const val SEEK_STEP_MS = 5_000L

/** Restored \"x,y,w,h\" (px) from [SettingsKeys.WINDOW_GEOMETRY]. */
private data class WindowGeometry(val x: Long, val y: Long, val w: Long, val h: Long)

private val desktopModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { InnerTubeClient() }
    // Network-only resolution chain (ADR-001): own-client primary, yt-dlp
    // failover. Used by the download engine (must NOT short-circuit to a
    // not-yet-downloaded file) and wrapped by the offline-first resolver for
    // playback.
    single<StreamResolver> {
        ResolvingStreamResolver(
            primary = OwnClientStreamResolver(get()),
            fallback = YtDlpStreamResolver(),
        )
    }
    // ADR-006 offline-first playback: a COMPLETED persistent download plays
    // from its local file; otherwise resolve over the network chain.
    single<MusicProvider> {
        val client = get<InnerTubeClient>()
        val resolver = OfflineFirstStreamResolver(
            downloads = get<DataLayer>().downloads,
            primary = get<StreamResolver>(),
            fileExists = { path -> runCatching { File(path).exists() }.getOrDefault(false) },
        )
        YouTubeMusicProvider(client, resolver)
    }
    // ADR-006 persistent download manager: bounded worker pool over the
    // network chain, writing into <data dir>/downloads (audio/ + art/).
    single<DownloadManager> {
        val data: DataLayer = get()
        val storage = JvmDownloadStorage(File(DhunUserDirs.dataDir(), "downloads").absolutePath)
        FileDownloadManager(
            repository = data.downloads,
            resolver = get<StreamResolver>(),
            downloader = KtorStreamDownloader(createDownloadHttpClient(), storage),
            storage = storage,
            scope = get(),
        )
    }
    // Phase 14 bounded audio cache (desktop): whole-track files under
    // DhunUserDirs (packaged = <installDir>/userdata/cache/audio so MSI
    // uninstall removes them; unpackaged = OS user-data dir). Budget from
    // CACHE_SIZE_MB (applied at start).
    single {
        val settings = get<DataLayer>().settings
        val mb = runBlocking {
            runCatching { settings.getInt(SettingsKeys.CACHE_SIZE_MB, SettingsKeys.CACHE_SIZE_MB_DEFAULT) }
                .getOrDefault(SettingsKeys.CACHE_SIZE_MB_DEFAULT)
        }
        AudioFileCache(
            dir = AudioFileCache.defaultDir(DatabaseDriverFactory.defaultFile().parentFile),
            maxBytes = AudioCacheBudget.bytesForMb(mb),
        ).also { it.evictToBudget() }
    }
    single { DesktopDhunPlayer(provider = get(), scope = get(), audioCache = get()) }
    // Phase 14: connectivity signal for the shared offline banner (5s poll).
    single<dev.dhun.core.ConnectivityMonitor> { dev.dhun.core.DesktopConnectivityMonitor(get()) }

    // Phase 05 data layer — SQLite file in the per-OS user data dir.
    // Robustness: if file DB creation fails (e.g. missing java.sql module in a
    // slim jlink image, or corrupted DB file), log the failure and fall back
    // to an in-memory DB so the window can still open to show the error.
    // Without this, Koin creation fails before the window exists and the MSI
    // user sees only the generic launcher \"Failed to launch JVM\".
    single {
        try {
            DataLayer(DatabaseFactory.create(DatabaseDriverFactory().createDriver()))
        } catch (e: Throwable) {
            System.err.println("DHUN DataLayer file DB failed: $e")
            e.printStackTrace()
            try {
                val logFile = try { File(DhunUserDirs.dataDir(), "dhun-startup.log") } catch (_: Throwable) { File(System.getProperty("java.io.tmpdir") ?: ".", "dhun-startup.log") }
                logFile.parentFile?.mkdirs()
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                logFile.appendText("[${Instant.now()}] DataLayer file DB failed: $e\n${sw}\n")
            } catch (_: Throwable) { }
            // If the failure is due to a missing java.sql module, in-memory
            // will also fail — rethrow with context so Main.kt can show the
            // error window + dialog instead of a silent launcher failure.
            try {
                DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver())).also {
                    System.err.println("DHUN using in-memory DB fallback (file DB unavailable)")
                }
            } catch (e2: Throwable) {
                System.err.println("DHUN in-memory DB also failed: $e2")
                throw e // original cause is more informative
            }
        }
    }
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
