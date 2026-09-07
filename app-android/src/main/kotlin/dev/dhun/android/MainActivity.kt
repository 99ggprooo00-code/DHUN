package dev.dhun.android

import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTypographyTokens
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.dhun.android.playback.AndroidDhunPlayer
import dev.dhun.android.playback.DhunPlaybackService
import dev.dhun.android.playback.PlaybackGraph
import dev.dhun.android.shortcuts.ShortcutAction
import dev.dhun.android.shortcuts.ShortcutIntents
import dev.dhun.android.ui.NavStatePersistence
import dev.dhun.core.PlaybackState
import dev.dhun.data.DataLayer
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunTheme
import dev.dhun.download.DownloadManager
import dev.dhun.player.NowPlayingPersistence
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.provider.MusicProvider
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DhunAppShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: AndroidDhunPlayer? = null
    private var persistence: NowPlayingPersistence? = null
    private var currentNav: AppNavState? = null
    // Handed to NavStatePersistence lazily by the first composition (the
    // original per-field restore was extracted into that object for tests).
    private var lastSavedState: Bundle? = null

    private val pendingShortcut = MutableStateFlow<ShortcutAction?>(null)
    private val batteryRationaleVisible = MutableStateFlow(false)

    private sealed interface ConnectUi {
        data object Connecting : ConnectUi
        data class Ready(val reason: String?) : ConnectUi // reason != null = fallback used
        data class Failed(val message: String) : ConnectUi
    }

    private val connectState = MutableStateFlow<ConnectUi>(ConnectUi.Connecting)
    private val connectLog = MutableStateFlow<List<String>>(emptyList())

    private fun logLine(line: String) {
        connectLog.value = connectLog.value + line
        Log.i(TAG, line)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastSavedState = savedInstanceState
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        handleShortcutIntent(intent)
        requestNotificationPermissionIfNeeded()
        connectWithFallback()
        setContent {
            DhunTheme {
                // Music-app back behavior: FullPlayer collapses first, then
                // detail pages pop; only when nothing overlays do we park the
                // app — BACK never kills the player.
                val nav = androidx.compose.runtime.remember { NavStatePersistence.restore(lastSavedState) }
                currentNav = nav
                BackHandler { if (!nav.closeTop()) moveTaskToBack(true) }

                val ui by connectState.collectAsState()
                val shortcut by pendingShortcut.collectAsState()
                val showBatteryRationale by batteryRationaleVisible.collectAsState()
                val koin = GlobalContext.get()

                LaunchedEffect(ui, shortcut) {
                    val action = shortcut ?: return@LaunchedEffect
                    if (ui !is ConnectUi.Ready) return@LaunchedEffect
                    when (action) {
                        ShortcutAction.SEARCH -> {
                            nav.selectedTab = AppTab.SEARCH
                            nav.detailStack.clear()
                        }
                        ShortcutAction.LIBRARY -> {
                            nav.selectedTab = AppTab.LIBRARY
                            nav.detailStack.clear()
                        }
                        ShortcutAction.RESUME -> {
                            // Restore is launched during attach; give it a
                            // short main-scope window before resuming the
                            // paused queue. If there is no saved queue this is
                            // a harmless no-op in the player implementation.
                            delay(500L)
                            player?.let { p ->
                                if (p.state.value !is PlaybackState.Playing) p.playPause()
                            }
                        }
                    }
                    pendingShortcut.value = null
                }

                when (val s = ui) {
                    is ConnectUi.Connecting -> ConnectingScreen(
                        log = connectLog.collectAsState().value,
                        version = appVersionName(),
                    )
                    is ConnectUi.Ready -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        player?.let { p ->
                            val homeViewModel: HomeViewModel = koin.get()
                            val searchViewModel: SearchViewModel = koin.get()
                            val dataLayer: DataLayer = koin.get()
                            val provider: MusicProvider = koin.get()
                            val lyricsRepository: LyricsRepository = koin.get()
                            val playerViewModel = androidx.compose.runtime.remember(p) {
                                PlayerViewModel(
                                    player = p,
                                    provider = provider,
                                    scope = activityScope,
                                    persistence = persistence,
                                    lyricsRepository = lyricsRepository,
                                )
                            }

                            DhunAppShell(
                                player = p,
                                homeViewModel = homeViewModel,
                                searchViewModel = searchViewModel,
                                playerViewModel = playerViewModel,
                                provider = provider,
                                dataLayer = dataLayer,
                                nav = nav,
                                isDesktop = false,
                                connectivity = koin.get(),
                                downloadManager = koin.get(),
                            )
                        }
                        s.reason?.let { reason ->
                            Surface(
                                color = DhunColors.errorContainer,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(),
                            ) {
                                Text(
                                    reason,
                                    fontSize = DhunTypographyTokens.labelSmall.fontSize,
                                    color = DhunColors.warning,
                                    modifier = Modifier.padding(DhunSpacing.xsPlus),
                                )
                            }
                        }
                    }
                    is ConnectUi.Failed -> FailureScreen(
                        message = s.message,
                        onRetry = {
                            connectState.value = ConnectUi.Connecting
                            connectWithFallback()
                        },
                    )
                }

                if (showBatteryRationale) {
                    AlertDialog(
                        onDismissRequest = { batteryRationaleVisible.value = false },
                        title = { Text("Keep playback reliable") },
                        text = {
                            Text(
                                "Android battery optimization can stop background music " +
                                    "on some phones. Allow DHUN to keep the playback service " +
                                    "available when the screen is off?",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    batteryRationaleVisible.value = false
                                    openBatteryOptimizationSettings()
                                },
                            ) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { batteryRationaleVisible.value = false }) {
                                Text("Not now")
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentNav?.let { nav -> NavStatePersistence.save(nav, outState) }
        super.onSaveInstanceState(outState)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        pendingShortcut.value = ShortcutIntents.actionFrom(intent)
    }

    override fun onDestroy() {
        persistence?.stop()
        player?.release()
        player = null
        activityScope.cancel()
        super.onDestroy()
    }

    /* ---------------- connection strategy ---------------- */

    private fun connectWithFallback() {
        activityScope.launch { attemptControllerConnect(1) }
    }

    private suspend fun attemptControllerConnect(attempt: Int) {
        logLine("attempt $attempt: connecting to playback service…")
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        try {
            val token = SessionToken(this, ComponentName(this, DhunPlaybackService::class.java))
            future = MediaController.Builder(this, token).buildAsync()
            val pending = future
            val controller = kotlinx.coroutines.withTimeout(10_000L) {
                pending!!.await()
            }
            val cache = GlobalContext.get().get<dev.dhun.android.playback.DhunStreamCache>()
            attach(AndroidDhunPlayer(controller, activityScope, cache))
            connectState.value = ConnectUi.Ready(reason = null)
            logLine("connected to playback service")
            return
        } catch (t: Throwable) {
            runCatching { future?.let { MediaController.releaseFuture(it) } }
            logLine(
                "attempt $attempt failed: " + (t.javaClass.simpleName) +
                    ((t.message?.take(90))?.let { ": $it" } ?: "")
            )
            Log.w(TAG, "controller connect attempt $attempt failed", t)
            if (attempt < MAX_CONNECT_ATTEMPTS) {
                delay(1_500L * attempt)
                attemptControllerConnect(attempt + 1)
                return
            }
            // Final fallback: session-less local player (audio works,
            // lock-screen controls degraded).
            try {
                logLine("starting LOCAL fallback player…")
                val cache = GlobalContext.get().get<dev.dhun.android.playback.DhunStreamCache>()
                // Same corrupt-cache guard as the service: a dead cache dir
                // must not take down the fallback engine too.
                val segments = try {
                    GlobalContext.get().get<dev.dhun.android.playback.DhunAudioSegmentCache>()
                } catch (t: Throwable) {
                    Log.w(TAG, "segment cache unusable — streaming without cache", t)
                    null
                }
                // ADR-006: the session-less fallback also honors offline-first
                // playback (a COMPLETED download plays from disk).
                val downloads = try {
                    GlobalContext.get().get<dev.dhun.data.DataLayer>().downloads
                } catch (t: Throwable) {
                    Log.w(TAG, "download repo unavailable — offline playback disabled", t)
                    null
                }
                val local = PlaybackGraph.buildExoPlayer(applicationContext, cache, segments, downloads)
                attach(AndroidDhunPlayer(local, activityScope, cache))
                logLine("local player ready — audio will play; session controls degraded")
                connectState.value = ConnectUi.Ready(
                    reason = "Background/media-session controls unavailable on this device " +
                        "(${t.javaClass.simpleName}); playing in local mode.",
                )
                Log.w(TAG, "session-less fallback active")
            } catch (t2: Throwable) {
                logLine("LOCAL player also failed: " + t2.javaClass.simpleName + ": " + (t2.message?.take(90) ?: ""))
                Log.e(TAG, "all playback paths failed", t2)
                connectState.value = ConnectUi.Failed(
                    t2.toDhunStyleMessage().ifBlank { t2.javaClass.simpleName },
                )
            }
        }
    }

    private fun attach(p: AndroidDhunPlayer) {
        player?.release()
        persistence?.stop()
        player = p
        // Media apps are expected to ask; without the exemption OEM battery
        // savers (MIUI/HyperOS/OneUI) kill the playback service in the
        // background. Asked at most once per process, only while the app is
        // actually in use (a system dialog from the background would be
        // blocked anyway). MIUI "auto-start" still has to be toggled by hand
        // (no programmatic request exists) — see docs/verification/03.
        requestBatteryOptimizationExemptionIfNeeded()
        // Persist queue/position/history, restore the last session
        // when the engine is idle (cold start). Restored = paused, never autoplay.
        val koin = GlobalContext.get()
        val pers = NowPlayingPersistence(
            player = p,
            save = koin.get(),
            restore = koin.get(),
            recordPlay = koin.get(),
            scope = activityScope,
            log = { Log.i(TAG, "persistence: $it") },
        )
        persistence = pers
        activityScope.launch {
            runCatching { pers.restore() }
                .onFailure { Log.w(TAG, "queue restore failed", it) }
                .onSuccess { snap -> if (snap != null) logLine("restored ${snap.queue.size} tracks (paused)") }
            pers.start()
        }
    }

    private fun Throwable.toDhunStyleMessage(): String =
        message?.take(200) ?: ""

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (batteryExemptionRequested) return
        batteryExemptionRequested = true
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            batteryRationaleVisible.value = true
        } catch (_: Exception) {
            // OEMs with no such settings page / dialog denied — playback
            // still works, just less resilient to aggressive savers.
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            // OEMs with no such settings page / dialog denied — playback
            // still works, just less resilient to aggressive savers.
        }
    }

    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    /* ---------------- permissions ---------------- */

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val TAG = "DHUN"
        private const val MAX_CONNECT_ATTEMPTS = 3
        private var batteryExemptionRequested = false
    }
}

/* ---------------- screens ---------------- */

@Composable
private fun ConnectingScreen(log: List<String>, version: String) {
    // Consumer splash: brand + indeterminate indicator + one static status
    // line. The raw attempt/log lines stay in Logcat (via logLine) — they
    // read as a terminal on screen and never ship to users.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "DHUN",
                fontSize = DhunTypographyTokens.hero.fontSize,
                fontWeight = FontWeight.Bold,
                color = DhunColors.textPrimary,
            )
            Spacer(modifier = Modifier.height(DhunSpacing.xl))
            CircularProgressIndicator(
                color = DhunColors.accent,
                strokeWidth = DhunSpacing.progressStroke,
            )
            Spacer(modifier = Modifier.height(DhunSpacing.lg))
            Text(
                "Initializing audio engine…",
                fontSize = DhunTypographyTokens.bodySmall.fontSize,
                color = DhunColors.textTertiary,
            )
        }
        Text(
            text = "v$version",
            fontSize = DhunTypographyTokens.labelSmall.fontSize,
            color = DhunColors.textHint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DhunSpacing.lg),
        )
    }
}

@Composable
private fun FailureScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(DhunSpacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("DHUN", fontSize = DhunTypographyTokens.hero.fontSize, fontWeight = FontWeight.Bold)
        Text(
            "Playback failed to start",
            color = DhunColors.error,
            modifier = Modifier.padding(top = DhunSpacing.md),
        )
        Text(
            message,
            fontSize = DhunTypographyTokens.bodySmall.fontSize,
            color = DhunColors.textTertiary,
            modifier = Modifier.padding(top = DhunSpacing.sm),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = DhunSpacing.xl)) {
            Text("Retry")
        }
    }
}
