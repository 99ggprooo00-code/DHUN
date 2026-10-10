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
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.WindowCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.dhun.android.playback.AndroidDhunPlayer
import dev.dhun.android.playback.DhunPlaybackService
import dev.dhun.android.playback.PlaybackGraph
import dev.dhun.android.shortcuts.NowPlayingShortcutSync
import dev.dhun.android.shortcuts.ShortcutAction
import dev.dhun.android.shortcuts.ShortcutIntents
import dev.dhun.android.shortcuts.shortcutTrack
import dev.dhun.android.ui.NavStatePersistence
import dev.dhun.core.PlaybackState
import dev.dhun.data.DataLayer
import dev.dhun.data.SettingsKeys
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunTheme
import dev.dhun.legal.DhunAppInfo
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
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
    private var shortcutSyncJob: Job? = null

    private fun logLine(line: String) {
        connectLog.value = connectLog.value + line
        Log.i(TAG, line)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastSavedState = savedInstanceState
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        handleShortcutIntent(intent)
        requestNotificationPermissionIfNeeded()
        // Views first, before Koin/SQLDelight. Compose 1.8's Android
        // GraphicsLayer references android.graphics.RenderEffect (API 31).
        // On API 29/30 that class load during the first Compose frame is
        // process death that looks like "installs, never opens". API < 31
        // cold starts go through LaunchActivity so ART can verify a
        // Compose-free class and paint this layout before MainActivity loads.
        try {
            showConnectingUi()
        } catch (t: Throwable) {
            Log.e(TAG, "connecting UI failed", t)
        }
        // S4: restore the persisted theme/accent before first composition so
        // the launch frame already carries the user's appearance. Best-effort:
        // a corrupt row falls back to dark+brand inside applyPersistedAppearance,
        // and a dead Koin/DB must never block startup — and must never run
        // before the connecting View has been set.
        runCatching {
            val settings = GlobalContext.get().get<DataLayer>().settings
            runBlocking {
                DhunAppearance.applyPersistedAppearance(
                    settings.getString(SettingsKeys.THEME),
                    settings.getString(SettingsKeys.ACCENT),
                    settings.getInt(SettingsKeys.BACKDROP_BLUR, SettingsKeys.BACKDROP_BLUR_DEFAULT),
                    settings.getInt(SettingsKeys.BACKDROP_BRIGHTNESS, SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT),
                )
            }
        }
        activityScope.launch {
            connectState.collect { ui ->
                when (ui) {
                    ConnectUi.Connecting -> showConnectingUi()
                    is ConnectUi.Ready -> showReadyUi(ui)
                    is ConnectUi.Failed -> showFailedUi(ui.message)
                }
            }
        }
        connectWithFallback()
    }

    private fun showConnectingUi() {
        setContentView(R.layout.activity_connecting)
        findViewById<TextView>(R.id.connecting_version)?.text = "v${appVersionName()}"
    }

    private fun showFailedUi(message: String) {
        setContentView(R.layout.activity_launch_failed)
        findViewById<TextView>(R.id.launch_failed_message).text = message
        findViewById<Button>(R.id.launch_retry).setOnClickListener {
            connectState.value = ConnectUi.Connecting
            connectWithFallback()
        }
    }

    private fun showReadyUi(ready: ConnectUi.Ready) {
        try {
        setContent {
            DhunTheme {
                // Music-app back behavior: FullPlayer collapses first, then
                // detail pages pop, then the tab steps back to the tab it came
                // from — Search and Library are *tabs*, not stack entries, so
                // without that step BACK found nothing to close and parked the
                // app from a screen the user was still using. Only at Home with
                // nothing open do we park it. BACK never kills the player.
                val nav = androidx.compose.runtime.remember { NavStatePersistence.restore(lastSavedState) }
                currentNav = nav
                BackHandler { if (!nav.onBack()) moveTaskToBack(true) }

                val shortcut by pendingShortcut.collectAsState()
                val showBatteryRationale by batteryRationaleVisible.collectAsState()
                val koin = GlobalContext.get()

                LaunchedEffect(shortcut) {
                    val action = shortcut ?: return@LaunchedEffect
                    when (action) {
                        ShortcutAction.NOW_PLAYING -> {
                            nav.playerExpanded = true
                        }
                        ShortcutAction.SEARCH -> {
                            nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = false)
                        }
                        ShortcutAction.LIBRARY -> {
                            nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = false)
                        }
                        ShortcutAction.RESUME -> {
                            delay(500L)
                            player?.let { p ->
                                if (p.state.value !is PlaybackState.Playing) p.playPause()
                            }
                        }
                    }
                    pendingShortcut.value = null
                }

                Box(
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
                                radioSession = koin.get(),
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
                            equalizerSession = koin.get(),
                            appInfo = androidx.compose.runtime.remember { dhunAppInfo() },
                        )
                    }
                    ready.reason?.let { reason ->
                        Surface(
                            color = DhunColors.errorContainer,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite },
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

                if (showBatteryRationale) {
                    AlertDialog(
                        onDismissRequest = { batteryRationaleVisible.value = false },
                        title = {
                            Text(
                                "Keep playback reliable",
                                modifier = Modifier.semantics { heading() },
                            )
                        },
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
        } catch (t: Throwable) {
            Log.e(TAG, "Compose ready UI failed", t)
            showFailedUi(t.toDhunStyleMessage().ifBlank { t.javaClass.simpleName })
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
        // Phase 15: dynamic "Now playing" launcher shortcut — the long label
        // follows the current track. Deduped by track id so it republishes
        // only on actual track changes, and cancelled/re-armed on every
        // attach() (the fallback path attaches a second engine).
        shortcutSyncJob?.cancel()
        shortcutSyncJob = activityScope.launch {
            p.state
                .map { it.shortcutTrack()?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { NowPlayingShortcutSync(this@MainActivity).publish(p.state.value) }
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

    /**
     * The About page's build metadata, read from the installed package.
     *
     * Nothing here is hardcoded: the About screen reports what this APK actually
     * is, so rebuilding with a bumped `versionName` shows the new value with no
     * second place to update. Every field falls back to the explicit
     * [DhunAppInfo.UNKNOWN] sentinel — a visible "unknown" is honest, a
     * plausible-looking guess is not.
     */
    private fun dhunAppInfo(): DhunAppInfo = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        val debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        DhunAppInfo(
            versionName = info.versionName ?: DhunAppInfo.UNKNOWN,
            // PackageInfoCompat, not the API-28 `longVersionCode`: minSdk is 24.
            versionCode = PackageInfoCompat.getLongVersionCode(info).toString(),
            releaseChannel = if (debuggable) "debug build" else "release build",
            platform = "Android",
        )
    } catch (_: Exception) {
        DhunAppInfo.Unknown
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
