package dev.dhun.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.use
import androidx.compose.ui.unit.Density
import dev.dhun.core.Album
import dev.dhun.core.AlbumDetail
import dev.dhun.core.Artist
import dev.dhun.core.ArtistPage
import dev.dhun.core.AlwaysOnlineConnectivityMonitor
import dev.dhun.core.ConnectivityMonitor
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.core.PlaybackState
import dev.dhun.core.Playlist
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.RepeatMode
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlayContext
import dev.dhun.data.PlaylistRepository
import dev.dhun.data.SettingsKeys
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunTheme
import dev.dhun.design.DhunThemeMode
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.GetRecommendationsUseCase
import dev.dhun.innertube.SearchFilter
import dev.dhun.legal.DhunAppInfo
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.library.LibraryViewModel
import dev.dhun.presentation.player.LyricsUiState
import dev.dhun.presentation.player.PlayerViewModel
import dev.dhun.presentation.player.RelatedUiState
import dev.dhun.presentation.search.SearchResultsUiState
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.presentation.settings.SettingsCategory
import dev.dhun.provider.MusicProvider
import dev.dhun.ui.components.AddToPlaylistDialog
import dev.dhun.ui.components.TrackOverflowDialog
import dev.dhun.ui.home.HomeScreen
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DetailRoute
import dev.dhun.ui.shell.DhunAppShell
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Application-shell visual evidence rendered by the real Compose/Skia runtime.
 * Recorded metadata comes from the existing anonymous YouTube parser fixtures;
 * the database and player are deterministic test doubles. No shipped screen,
 * navigation route, callback or test-only product flag is changed.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class, InternalTestApi::class, InternalComposeUiApi::class)
class VisualAppEvidenceTest {
    private val output = File("build/outputs/visual-evidence").apply { mkdirs() }

    @Test
    fun `render compact application screens in dark and light modes`() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            withFixture(mode) { fixture ->
                val nav = AppNavState()
                scene(width = 412, height = 915) {
                    Shell(fixture, nav, mode, isDesktop = false)
                }.use { composeScene ->
                    val driver = SceneDriver(composeScene)
                    val homeFrame = driver.capture("app-home-${mode.id}.png", settleFrames = 40)
                    if (mode == DhunThemeMode.DARK) {
                        driver.scroll(200f, 430f, 4f)
                        val scrolledFrame = driver.capture("app-home-scrolled-dark.png")
                        assertTrue(
                            !homeFrame.contentEquals(scrolledFrame),
                            "Mouse-wheel input did not visibly change the Home surface",
                        )
                    }
                    nav.selectTab(AppTab.SEARCH)
                    val searchFrame = driver.capture("app-search-${mode.id}.png")
                    driver.tap(355f, 255f)
                    val menuFrame = driver.capture("app-shell-track-menu-${mode.id}.png")
                    assertTrue(!searchFrame.contentEquals(menuFrame), "Search overflow did not visibly open")
                    driver.tap(150f, 485f)
                    val addFrame = driver.capture("app-shell-add-playlist-${mode.id}.png", settleFrames = 60)
                    assertTrue(!menuFrame.contentEquals(addFrame), "Add to playlist did not visibly open")
                    driver.tap(24f, 120f) // outside the dialog: exercise real dismissal
                    nav.selectTab(AppTab.LIBRARY)
                    driver.capture("app-library-${mode.id}.png")
                    nav.push(DetailRoute.ArtistPage(QUEEN_ID))
                    driver.capture("app-artist-${mode.id}.png", settleFrames = 24)
                    nav.popDetail()
                    nav.push(DetailRoute.AlbumPage(ALBUM_ID))
                    driver.capture("app-album-${mode.id}.png", settleFrames = 24)
                    nav.popDetail()
                    if (mode == DhunThemeMode.DARK) {
                        nav.push(DetailRoute.PlaylistPage(fixture.localPlaylistId, isLocal = true))
                    } else {
                        nav.push(DetailRoute.PlaylistPage(REMOTE_PLAYLIST_ID))
                    }
                    driver.capture("app-playlist-${mode.id}.png", settleFrames = 24)
                    nav.popDetail()
                    nav.push(DetailRoute.SettingsPage)
                    driver.capture("app-settings-${mode.id}.png")
                    nav.push(DetailRoute.SettingsCategoryPage(SettingsCategory.APPEARANCE))
                    driver.capture("app-settings-appearance-${mode.id}.png")
                    nav.detailStack.clear()
                    nav.selectTab(AppTab.HOME)
                    nav.playerExpanded = true
                    val playerFrame = driver.capture("app-player-${mode.id}.png", settleFrames = 24)
                    driver.tap(355f, 879f)
                    val lyricsFrame = driver.capture("app-player-lyrics-${mode.id}.png", settleFrames = 24)
                    assertTrue(!playerFrame.contentEquals(lyricsFrame), "Lyrics input did not visibly change the player")
                    driver.tap(355f, 879f)
                    driver.tap(55f, 879f)
                    val queueFrame = driver.capture("app-player-queue-${mode.id}.png", settleFrames = 24)
                    assertTrue(!playerFrame.contentEquals(queueFrame), "Queue input did not visibly change the player")
                    driver.tap(310f, 630f) // Related tab in the raised Queue/Related sheet
                    val relatedFrame = driver.capture("app-player-related-${mode.id}.png", settleFrames = 24)
                    assertTrue(!queueFrame.contentEquals(relatedFrame), "Related input did not visibly change the player")
                    assertEquals(RECORDED_TRACKS.first(), fixture.player.currentTrack.value)
                    assertIs<PlaybackState.Playing>(fixture.player.state.value)
                }
            }
        }
    }

    @Test
    fun `render existing track menus and add-to-playlist dialogs`() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            withFixture(mode) { fixture ->
                runBlocking {
                    assertEquals(fixture.localPlaylistId, fixture.data.playlists.observePlaylists().first().single().id)
                }
                scene(width = 412, height = 915) {
                    DhunTheme(mode = mode) {
                        Box(Modifier.fillMaxSize().background(DhunColors.background))
                        TrackOverflowDialog(
                            track = RECORDED_TRACKS.first(),
                            player = fixture.player,
                            onAddToPlaylist = {},
                            onNavigateToArtist = {},
                            onNavigateToAlbum = {},
                            onDismiss = {},
                        )
                    }
                }.use { composeScene ->
                    SceneDriver(composeScene).capture("app-track-menu-${mode.id}.png", settleFrames = 24)
                }
                val repository = fixture.data.playlists
                val flowRequests = AtomicInteger()
                val flowEmissions = CopyOnWriteArrayList<List<Pair<String, Int>>>()
                val diagnosticRepository = object : PlaylistRepository by repository {
                    override fun observePlaylists(): Flow<List<LocalPlaylist>> {
                        flowRequests.incrementAndGet()
                        return repository.observePlaylists().onEach { rows ->
                            flowEmissions += rows.map { it.name to it.trackCount }
                        }
                    }
                }
                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    // Artwork/loading indicators intentionally animate forever.
                    // Skiko auto-advance idling has no timeout; manual frames
                    // keep semantic queries bounded and captures deterministic.
                    mainClock.autoAdvance = false
                    setContent {
                        DhunTheme(mode = mode) {
                            Box(Modifier.fillMaxSize().background(DhunColors.background))
                            AddToPlaylistDialog(
                                track = RECORDED_TRACKS.first(),
                                playlistRepository = diagnosticRepository,
                                onDismiss = {},
                                onAdded = {},
                                onOpenPlaylist = {},
                            )
                        }
                    }
                    // A repository precondition alone is not UI evidence: wait
                    // for the real flow emission instead of accepting the
                    // initial empty list or assuming a fixed number of frames
                    // is sufficient. Manual frames are needed because the
                    // clock no longer auto-advances; the deadline still fails
                    // if the popup never delivers its row. The row is clickable,
                    // so inspect its unmerged name/count text children.
                    val deadline = System.nanoTime() + 5_000_000_000L
                    var populated = false
                    while (!populated && System.nanoTime() < deadline) {
                        mainClock.advanceTimeBy(50)
                        populated = onAllNodes(hasText("Visual QA"), useUnmergedTree = true)
                            .fetchSemanticsNodes().isNotEmpty() &&
                            onAllNodes(hasText("4 tracks"), useUnmergedTree = true)
                            .fetchSemanticsNodes().isNotEmpty()
                        if (!populated) Thread.sleep(20)
                    }
                    if (!populated) {
                        captureUiImage(
                            "app-add-playlist-${mode.id}-failed.png",
                            renderCurrentFrame(this, 412, 915),
                        )
                    }
                    assertTrue(
                        populated,
                        "The playlist dialog did not expose its populated row; " +
                            "requests=${flowRequests.get()}, emissions=$flowEmissions, " +
                            "library=${fixture.library.playlistsFlow.value.map { it.name to it.trackCount }}",
                    )
                    onNode(hasText("Visual QA"), useUnmergedTree = true).assertIsDisplayed()
                    onNode(hasText("4 tracks"), useUnmergedTree = true).assertIsDisplayed()
                    mainClock.advanceTimeBy(500)
                    captureUiImage("app-add-playlist-${mode.id}.png", renderCurrentFrame(this, 412, 915))
                }
            }
        }
    }

    @Test
    fun `render wide desktop shell and detail navigation`() {
        withFixture(DhunThemeMode.DARK) { fixture ->
            val nav = AppNavState()
            runSkikoComposeUiTest(size = Size(1200f, 780f)) {
                // Album/artwork loading can otherwise keep waitForIdle() in
                // its unbounded auto-advance loop. Advance only the frames we
                // intend to inspect; assertions still use the real semantics.
                mainClock.autoAdvance = false
                setContent {
                    Shell(fixture, nav, DhunThemeMode.DARK, isDesktop = true)
                }
                fun capture(name: String) {
                    mainClock.advanceTimeBy(500)
                    // Do not rely on Skiko's private snapshot surface here:
                    // manual clock mode does not use its implicit idle render.
                    captureUiImage(name, renderCurrentFrame(this, 1200, 780))
                }

                capture("desktop-home-dark.png")
                // Regression for the full-height standalone MiniPlayer: Home
                // was squeezed to zero height even though its data was loaded.
                onNodeWithText("Quick picks").assertIsDisplayed()
                val pauseTop = onNodeWithContentDescription("Pause")
                    .fetchSemanticsNode().boundsInRoot.top
                assertTrue(
                    pauseTop > 650f,
                    "MiniPlayer must dock at the bottom, not cover the page (Pause top=$pauseTop)",
                )
                runOnIdle { nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = true) }
                capture("desktop-search-dark.png")
                runOnIdle { nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = true) }
                capture("desktop-library-dark.png")
                runOnIdle { nav.push(DetailRoute.AlbumPage(ALBUM_ID)) }
                capture("desktop-album-dark.png")
                runOnIdle { nav.playerExpanded = true }
                capture("desktop-player-dark.png")
                assertEquals(RECORDED_TRACKS.first(), fixture.player.currentTrack.value)
                assertIs<PlaybackState.Playing>(fixture.player.state.value)
            }
            scene(width = 1200, height = 780) {
                DhunTheme(mode = DhunThemeMode.DARK) {
                    Box(Modifier.fillMaxSize().background(DhunColors.background)) {
                        HomeScreen(viewModel = fixture.home, onTrackClick = { _, _, _ -> })
                    }
                }
            }.use { composeScene ->
                SceneDriver(composeScene).capture("desktop-home-direct-dark.png", settleFrames = 24)
            }
        }
    }

    @Test
    fun `render loading empty error offline and appearance states`() {
        captureSingleState("app-home-loading-dark.png", DhunThemeMode.DARK, RecordedProvider(homeMode = StateMode.LOADING))
        captureSingleState("app-home-empty-dark.png", DhunThemeMode.DARK, RecordedProvider(homeMode = StateMode.EMPTY))
        captureSingleState("app-home-error-dark.png", DhunThemeMode.DARK, RecordedProvider(homeMode = StateMode.ERROR))
        captureSingleState(
            "app-search-empty-dark.png",
            DhunThemeMode.DARK,
            RecordedProvider(searchMode = StateMode.EMPTY),
        ) { it.selectTab(AppTab.SEARCH) }
        captureSingleState(
            "app-search-error-dark.png",
            DhunThemeMode.DARK,
            RecordedProvider(searchMode = StateMode.ERROR),
        ) { it.selectTab(AppTab.SEARCH) }
        captureSingleState("app-home-offline-dark.png", DhunThemeMode.DARK, online = false)
        captureSingleState("app-home-no-blur-dark.png", DhunThemeMode.DARK, blurPercent = 0)
        captureSingleState("app-home-no-blur-light.png", DhunThemeMode.LIGHT, blurPercent = 0)
        captureSingleState(
            "app-home-no-blur-bright-dark.png",
            DhunThemeMode.DARK,
            blurPercent = 0,
            brightnessPercent = 100,
        )
        captureSingleState(
            "app-home-no-blur-bright-light.png",
            DhunThemeMode.LIGHT,
            blurPercent = 0,
            brightnessPercent = 100,
        )
        captureSingleState("app-home-brightness-zero-light.png", DhunThemeMode.LIGHT, brightnessPercent = 0)
        captureSingleState("app-home-brightness-full-light.png", DhunThemeMode.LIGHT, brightnessPercent = 100)
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            for ((label, rgb) in listOf("black" to 0x000000, "white" to 0xFFFFFF)) {
                captureSingleState(
                    "app-home-no-blur-$label-artwork-${mode.id}.png",
                    mode,
                    blurPercent = 0,
                    artworkUrl = extremeArtwork(label, rgb),
                )
            }
        }
        captureSingleState("app-home-oled.png", DhunThemeMode.BLACK)
        captureSingleState("app-home-large-text-dark.png", DhunThemeMode.DARK, fontScale = 1.3f)
        captureSingleState("app-settings-large-text-dark.png", DhunThemeMode.DARK, fontScale = 1.3f) {
            it.push(DetailRoute.SettingsPage)
        }
    }

    /**
     * Lyrics are the product's centrepiece, so every presentation variant the
     * shell can receive gets its own rendered frame: Devanagari + Latin synced
     * lines with a deliberately over-long wrapping line and romanization
     * secondary lines, provider attribution, unsynced stanzas in both themes,
     * and the honest unavailable/error states.
     */
    @Test
    fun `render lyrics presentation matrix across scripts providers and states`() {
        val syncedDevanagari = Lyrics.Synced(
            listOf(
                LyricsLine(0, "धुन बजती रहे"),
                LyricsLine(15_000, "First line"),
                LyricsLine(
                    30_000,
                    "यह एक जानबूझकर लंबी रखी गई पंक्ति है जो प्लेयर की चौड़ाई पर कई पंक्तियों में लिपट जाएगी ताकि रैपिंग और हाइलाइट दोनों दिखें",
                ),
                LyricsLine(60_000, "संगत और असंगत"),
                LyricsLine(90_000, "Last line"),
            ),
            provider = "ytm",
        )
        val unsyncedStanzas = Lyrics.Unsynced(
            listOf(
                "धुन बजती रहे",
                "संगत और असंगत",
                "यह एक अनसिंक्ड स्टैंज़ा है",
                "",
                "Second stanza begins here",
                "with a Latin companion line",
            ).joinToString("\n"),
            provider = "lrclib",
        )
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            captureSingleState(
                "app-player-lyrics-devanagari-romanized-${mode.id}.png",
                mode,
                provider = RecordedProvider(lyrics = syncedDevanagari),
                romanization = true,
                taps = listOf(355f to 879f),
            ) { it.playerExpanded = true }
            captureSingleState(
                "app-player-lyrics-unsynced-${mode.id}.png",
                mode,
                provider = RecordedProvider(lyrics = unsyncedStanzas),
                taps = listOf(355f to 879f),
            ) { it.playerExpanded = true }
        }
        captureSingleState(
            "app-player-lyrics-unavailable-dark.png",
            DhunThemeMode.DARK,
            provider = RecordedProvider(lyrics = Lyrics.NotAvailable),
            taps = listOf(355f to 879f),
        ) { it.playerExpanded = true }
        captureSingleState(
            "app-player-lyrics-error-dark.png",
            DhunThemeMode.DARK,
            provider = RecordedProvider(lyrics = null),
            taps = listOf(355f to 879f),
        ) { it.playerExpanded = true }
    }

    /**
     * Artwork arrives in arbitrary aspects and can be missing or fail to load;
     * the fitted hero box must letterbox the source without stretching it (the
     * white centre stripe stays vertical and centred) while the backdrop keeps
     * feathering. Backdrop/accent combinations are rendered on the player too,
     * not only on Home.
     */
    @Test
    fun `render player artwork aspect fallback and backdrop matrix`() {
        for ((label, url) in listOf(
            "square" to artworkFixture("square", 600, 600),
            "wide" to artworkFixture("wide", 640, 360),
            "tall" to artworkFixture("tall", 360, 640),
        )) {
            captureSingleState("app-player-art-$label-dark.png", DhunThemeMode.DARK, artworkUrl = url) {
                it.playerExpanded = true
            }
        }
        captureSingleState("app-player-art-missing-dark.png", DhunThemeMode.DARK, noArtwork = true) {
            it.playerExpanded = true
        }
        captureSingleState(
            "app-player-art-failed-dark.png",
            DhunThemeMode.DARK,
            artworkUrl = File("build/tmp/visual-evidence/does-not-exist.png").toURI().toString(),
        ) { it.playerExpanded = true }
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            captureSingleState("app-player-no-blur-${mode.id}.png", mode, blurPercent = 0) {
                it.playerExpanded = true
            }
        }
        captureSingleState(
            "app-player-no-blur-static-accent-dark.png",
            DhunThemeMode.DARK,
            blurPercent = 0,
            accentMode = "static",
        ) { it.playerExpanded = true }
        captureSingleState("app-player-static-accent-dark.png", DhunThemeMode.DARK, accentMode = "static") {
            it.playerExpanded = true
        }
    }

    /** Short and landscape phone viewports: nothing may clip or overlap. */
    @Test
    fun `render responsive player viewports`() {
        captureSingleState("app-player-short-dark.png", DhunThemeMode.DARK, width = 412, height = 640) {
            it.playerExpanded = true
        }
        captureSingleState(
            "app-player-short-lyrics-dark.png",
            DhunThemeMode.DARK,
            width = 412,
            height = 640,
            taps = listOf(355f to 604f),
        ) { it.playerExpanded = true }
        captureSingleState("app-player-landscape-dark.png", DhunThemeMode.DARK, width = 840, height = 393) {
            it.playerExpanded = true
        }
        captureSingleState(
            "app-player-landscape-lyrics-dark.png",
            DhunThemeMode.DARK,
            width = 840,
            height = 393,
            taps = listOf(355f to 357f),
        ) { it.playerExpanded = true }
    }

    /**
     * Behavioural evidence in the same rendered surface: the highlighted line
     * follows the playback position without snapping artifacts, tapping a line
     * seeks the player to that line's start, and switching tracks re-resolves
     * lyrics instead of showing the previous track's lines.
     */
    @Test
    fun `lyrics follow position and tap-seek in the rendered player`() {
        withFixture(DhunThemeMode.DARK) { fixture ->
            val nav = AppNavState().apply { playerExpanded = true }
            runSkikoComposeUiTest(size = Size(412f, 915f)) {
                mainClock.autoAdvance = false
                setContent { Shell(fixture, nav, DhunThemeMode.DARK, isDesktop = false) }
                fun settle(frames: Int) = repeat(frames) { mainClock.advanceTimeBy(16) }
                settle(40)
                onRoot().performTouchInput {
                    down(Offset(355f, 879f))
                    advanceEventTime(50)
                    up()
                }
                settle(40)
                assertEquals(62_000L, fixture.player.positionMs.value)
                val at62 = captureUiImage("app-lyrics-follow-62s-dark.png", renderCurrentFrame(this, 412, 915))
                fixture.player.positionMs.value = 5_000L
                settle(40)
                val at5 = captureUiImage("app-lyrics-follow-5s-dark.png", renderCurrentFrame(this, 412, 915))
                assertTrue(!at62.contentEquals(at5), "Active lyric line did not visibly follow the position")
                onNodeWithText("Last line").assertIsDisplayed()
                onNodeWithText("Last line").performClick()
                settle(24)
                assertEquals(90_000L, fixture.player.positionMs.value, "Tapping a lyric line must seek to its start")
                val afterTap = captureUiImage("app-lyrics-after-tapseek-dark.png", renderCurrentFrame(this, 412, 915))
                assertTrue(!at5.contentEquals(afterTap), "Tap-seek did not visibly move the highlight")
                fixture.player.playAt(1)
                settle(40)
                assertEquals(RECORDED_TRACKS[1], fixture.player.currentTrack.value)
                assertIs<LyricsUiState.Synced>(fixture.playerVm.lyricsState.value)
                val afterSwitch = captureUiImage("app-lyrics-after-track-switch-dark.png", renderCurrentFrame(this, 412, 915))
                assertTrue(!at5.contentEquals(afterSwitch), "Track switch left the previous lyric surface untouched")
            }
        }
    }

    private fun captureSingleState(
        name: String,
        mode: DhunThemeMode,
        provider: RecordedProvider = RecordedProvider(),
        online: Boolean = true,
        blurPercent: Int? = null,
        brightnessPercent: Int? = null,
        artworkUrl: String? = null,
        noArtwork: Boolean = false,
        fontScale: Float = 1f,
        width: Int = 412,
        height: Int = 915,
        romanization: Boolean = false,
        accentMode: String? = null,
        taps: List<Pair<Float, Float>> = emptyList(),
        destination: (AppNavState) -> Unit = {},
    ) {
        withFixture(mode, provider, online, romanization = romanization, accentMode = accentMode) { fixture ->
            if (noArtwork) {
                fixture.player.clearArtwork()
                assertEquals(null, fixture.playerVm.currentTrack.value?.thumbnailUrl)
            }
            if (artworkUrl != null) {
                fixture.player.useArtwork(artworkUrl)
                assertEquals(artworkUrl, fixture.playerVm.currentTrack.value?.thumbnailUrl)
                assertTrue(
                    fixture.playerVm.queue.value.isNotEmpty() &&
                        fixture.playerVm.queue.value.all { it.thumbnailUrl == artworkUrl },
                    "Controlled artwork must reach the shell-facing queue as well as the current track",
                )
            }
            if (blurPercent != null) DhunAppearance.setBackdropPrefs(blurPercent = blurPercent)
            if (brightnessPercent != null) DhunAppearance.setBackdropPrefs(brightnessPercent = brightnessPercent)
            val nav = AppNavState().apply(destination)
            scene(width = width, height = height, fontScale = fontScale) {
                Shell(fixture, nav, mode, isDesktop = false)
            }.use { composeScene ->
                val driver = SceneDriver(composeScene)
                taps.forEach { (x, y) -> driver.tap(x, y) }
                driver.capture(name, settleFrames = 24)
            }
        }
    }

    @Composable
    private fun Shell(fixture: AppFixture, nav: AppNavState, mode: DhunThemeMode, isDesktop: Boolean) {
        DhunTheme(mode = mode) {
            DhunAppShell(
                player = fixture.player,
                homeViewModel = fixture.home,
                searchViewModel = fixture.search,
                playerViewModel = fixture.playerVm,
                provider = fixture.provider,
                dataLayer = fixture.data,
                nav = nav,
                isDesktop = isDesktop,
                libraryViewModel = fixture.library,
                connectivity = fixture.connectivity,
                appInfo = DhunAppInfo.Unknown,
            )
        }
    }

    private fun withFixture(
        mode: DhunThemeMode,
        provider: RecordedProvider = RecordedProvider(),
        online: Boolean = true,
        romanization: Boolean = false,
        accentMode: String? = null,
        block: (AppFixture) -> Unit,
    ) {
        DhunAppearance.reset()
        DhunAppearance.setAppearance(nextMode = mode)
        if (accentMode != null) DhunAppearance.setAccentMode(accentMode)
        val fixture = AppFixture(provider, online)
        try {
            runBlocking {
                fixture.data.settings.putString(SettingsKeys.THEME, mode.id)
                if (romanization) {
                    fixture.data.settings.putString(SettingsKeys.LYRICS_ROMANIZATION_ENABLED, "true")
                }
            }
            block(fixture)
        } finally {
            fixture.close()
            DhunAppearance.reset()
        }
    }

    private fun scene(
        width: Int,
        height: Int,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ): ImageComposeScene = ImageComposeScene(
        width = width,
        height = height,
        density = Density(1f, fontScale),
        content = content,
    )

    /**
     * Deterministic extreme artwork loaded by the production Coil file-URI
     * path. These are test fixtures, not replacement artwork in the app.
     */
    private fun extremeArtwork(label: String, rgb: Int): String {
        val target = File("build/tmp/visual-evidence/$label.png").apply { parentFile.mkdirs() }
        val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = java.awt.Color(rgb)
            graphics.fillRect(0, 0, image.width, image.height)
        } finally {
            graphics.dispose()
        }
        assertTrue(javax.imageio.ImageIO.write(image, "png", target), "Could not write $label artwork fixture")
        return target.toURI().toString()
    }

    /**
     * Aspect fixtures with a white centre stripe and four colour quadrants:
     * any stretch, wrong crop or rotation is visible in the rendered frame
     * because the stripe must stay vertical, centred and unsplit.
     */
    private fun artworkFixture(label: String, width: Int, height: Int): String {
        val target = File("build/tmp/visual-evidence/$label.png").apply { parentFile.mkdirs() }
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            val quadrants = listOf(
                java.awt.Color(0x8A, 0x2A, 0x2A),
                java.awt.Color(0xD9, 0xC6, 0xA5),
                java.awt.Color(0x2A, 0x4A, 0x8A),
                java.awt.Color(0x3E, 0x7A, 0x4E),
            )
            graphics.color = quadrants[0]
            graphics.fillRect(0, 0, width / 2, height / 2)
            graphics.color = quadrants[1]
            graphics.fillRect(width / 2, 0, width - width / 2, height / 2)
            graphics.color = quadrants[2]
            graphics.fillRect(0, height / 2, width / 2, height - height / 2)
            graphics.color = quadrants[3]
            graphics.fillRect(width / 2, height / 2, width - width / 2, height - height / 2)
            graphics.color = java.awt.Color(0xFF, 0xFF, 0xFF)
            graphics.fillRect(width / 2 - 4, 0, 8, height)
        } finally {
            graphics.dispose()
        }
        assertTrue(javax.imageio.ImageIO.write(image, "png", target), "Could not write $label artwork fixture")
        return target.toURI().toString()
    }

    /** Render the live Skiko scene explicitly; never encode an old idle surface. */
    private fun renderCurrentFrame(test: SkikoComposeUiTest, width: Int, height: Int): ImageBitmap {
        val surface = Surface.makeRasterN32Premul(width, height)
        return try {
            test.runOnUiThread {
                test.scene.render(
                    surface.canvas.asComposeCanvas(),
                    test.mainClock.currentTime * 1_000_000L,
                )
            }
            surface.makeImageSnapshot().toComposeImageBitmap()
        } finally {
            surface.close()
        }
    }

    private fun captureUiImage(name: String, image: ImageBitmap): ByteArray =
        Image.makeFromBitmap(image.asSkiaBitmap()).use { skiaImage ->
            skiaImage.encodeToData()!!.use { encoded ->
                val bytes = encoded.bytes
                val target = File(output, name)
                target.writeBytes(bytes)
                assertTrue(target.length() > 1_000, "$name was empty or implausibly small")
                bytes
            }
        }

    private inner class SceneDriver(private val scene: ImageComposeScene) {
        private var timeNanos = 0L

        fun settle(frames: Int = 12) {
            repeat(frames) {
                Thread.sleep(20)
                timeNanos += FRAME_NANOS
                scene.render(timeNanos).close()
            }
        }

        fun capture(name: String, settleFrames: Int = 12): ByteArray {
            settle(settleFrames)
            return scene.render(timeNanos).use { image ->
                image.encodeToData()!!.use { encoded ->
                    val bytes = encoded.bytes
                    val target = File(output, name)
                    target.writeBytes(bytes)
                    assertTrue(target.length() > 1_000, "$name was empty or implausibly small")
                    bytes
                }
            }
        }

        fun scroll(x: Float, y: Float, deltaY: Float) {
            val position = Offset(x, y)
            val startMillis = timeNanos / 1_000_000L
            scene.sendPointerEvent(PointerEventType.Enter, position, timeMillis = startMillis)
            scene.sendPointerEvent(PointerEventType.Move, position, timeMillis = startMillis + 1)
            scene.sendPointerEvent(
                PointerEventType.Scroll,
                position,
                scrollDelta = Offset(0f, deltaY),
                timeMillis = startMillis + 2,
            )
            settle(24)
        }

        fun tap(x: Float, y: Float) {
            val position = Offset(x, y)
            val startMillis = timeNanos / 1_000_000L
            scene.sendPointerEvent(PointerEventType.Enter, position, timeMillis = startMillis)
            scene.sendPointerEvent(PointerEventType.Move, position, timeMillis = startMillis + 1)
            scene.sendPointerEvent(
                PointerEventType.Press,
                position,
                timeMillis = startMillis + 2,
                buttons = PointerButtons(isPrimaryPressed = true),
                button = PointerButton.Primary,
            )
            settle(1)
            scene.sendPointerEvent(
                PointerEventType.Release,
                position,
                timeMillis = timeNanos / 1_000_000L,
                buttons = PointerButtons(),
                button = PointerButton.Primary,
            )
            settle(24)
        }
    }

    private enum class StateMode { POPULATED, EMPTY, ERROR, LOADING }

    private class AppFixture(
        val provider: RecordedProvider,
        online: Boolean,
    ) {
        private val driver = DatabaseDriverFactory.inMemory().createDriver()
        val data = DataLayer(DatabaseFactory.create(driver))
        val player = RecordedPlayer()
        val connectivity: ConnectivityMonitor = if (online) {
            AlwaysOnlineConnectivityMonitor
        } else {
            object : ConnectivityMonitor {
                override val isOnline = MutableStateFlow(false)
            }
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val home: HomeViewModel
        val search: SearchViewModel
        val playerVm: PlayerViewModel
        val library: LibraryViewModel
        val localPlaylistId: String

        init {
            localPlaylistId = runBlocking {
                data.library.addFavorite(RECORDED_TRACKS[1])
                data.history.recordPlay(RECORDED_TRACKS[0], PlayContext.HOME)
                data.history.recordPlay(RECORDED_TRACKS[1], PlayContext.SEARCH)
                val playlist = data.playlists.create("Visual QA", "In-memory acceptance fixture")
                RECORDED_TRACKS.take(4).forEach { data.playlists.addTrack(playlist.id, it) }
                playlist.id
            }
            home = HomeViewModel(
                getHomeFeed = GetHomeFeedUseCase(provider, data.history),
                getRecommendations = GetRecommendationsUseCase(provider, data.history, data.library),
                historyRepository = data.history,
                libraryRepository = data.library,
                scope = scope,
                settings = data.settings,
            )
            search = SearchViewModel(provider, data.search, data.library, data.playlists, scope)
            playerVm = PlayerViewModel(player, provider, scope, settings = data.settings)
            library = LibraryViewModel(data, player, scope)
            search.onQueryChange("Bohemian Rhapsody")
            search.onSearchSubmitted()
            when (provider.homeMode) {
                StateMode.POPULATED -> await("Home populated") { home.uiState.value is HomeUiState.Success }
                StateMode.EMPTY -> await("Home empty") { home.uiState.value is HomeUiState.Empty }
                StateMode.ERROR -> await("Home error") { home.uiState.value is HomeUiState.Error }
                StateMode.LOADING -> await("Home loading") { home.uiState.value is HomeUiState.Loading }
            }
            when (provider.searchMode) {
                StateMode.POPULATED -> await("Search populated") { search.resultsState.value is SearchResultsUiState.Success }
                StateMode.EMPTY -> await("Search empty") { search.resultsState.value is SearchResultsUiState.Empty }
                StateMode.ERROR -> await("Search error") { search.resultsState.value is SearchResultsUiState.Error }
                StateMode.LOADING -> await("Search loading") { search.resultsState.value is SearchResultsUiState.Loading }
            }
            await("Library populated") { library.playlistsFlow.value.isNotEmpty() }
            when (provider.lyrics) {
                is Lyrics.Synced -> await("Lyrics populated") { playerVm.lyricsState.value is LyricsUiState.Synced }
                is Lyrics.Unsynced -> await("Lyrics unsynced") { playerVm.lyricsState.value is LyricsUiState.Unsynced }
                is Lyrics.NotAvailable -> await("Lyrics unavailable") { playerVm.lyricsState.value is LyricsUiState.Unavailable }
                null -> await("Lyrics error") { playerVm.lyricsState.value is LyricsUiState.Error }
            }
            await("Related populated") { playerVm.relatedState.value is RelatedUiState.Success }
        }

        fun close() {
            scope.cancel()
            driver.close()
        }

        private fun await(label: String, predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!predicate()) {
                if (System.nanoTime() > deadline) fail("Timed out waiting for $label")
                Thread.sleep(20)
            }
        }
    }

    private class RecordedPlayer : DhunPlayer {
        override val state = MutableStateFlow<PlaybackState>(PlaybackState.Playing(RECORDED_TRACKS.first()))
        override val currentTrack = MutableStateFlow<Track?>(RECORDED_TRACKS.first())
        override val queue = MutableStateFlow(RECORDED_TRACKS)
        override val currentQueueIndex = MutableStateFlow(0)
        override val positionMs = MutableStateFlow(62_000L)
        override val durationMs = MutableStateFlow(367_000L)
        override val repeatMode = MutableStateFlow(RepeatMode.OFF)
        override val shuffleEnabled = MutableStateFlow(false)
        override val volume = MutableStateFlow(0.75f)

        fun useArtwork(url: String) {
            val updated = currentTrack.value!!.copy(thumbnailUrl = url)
            currentTrack.value = updated
            queue.value = queue.value.map { it.copy(thumbnailUrl = url) }
            state.value = PlaybackState.Playing(updated)
        }

        /** Missing-artwork path: the track carries no URL at all. */
        fun clearArtwork() {
            val updated = currentTrack.value!!.copy(thumbnailUrl = null)
            currentTrack.value = updated
            queue.value = queue.value.map { it.copy(thumbnailUrl = null) }
            state.value = PlaybackState.Playing(updated)
        }

        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            queue.value = tracks
            currentQueueIndex.value = if (tracks.isEmpty()) -1 else startIndex.coerceIn(tracks.indices)
            currentTrack.value = tracks.getOrNull(startIndex)
            state.value = when {
                tracks.isEmpty() -> PlaybackState.Idle
                playWhenReady -> PlaybackState.Playing(tracks[startIndex])
                else -> PlaybackState.Paused(tracks[startIndex])
            }
        }

        override fun addNext(track: Track) {
            val index = currentQueueIndex.value
            queue.value = queue.value.toMutableList().apply { add(index + 1, track) }
        }

        override fun addToQueue(track: Track) {
            queue.value = queue.value + track
        }

        override fun playAt(index: Int) {
            val track = queue.value.getOrNull(index) ?: return
            currentQueueIndex.value = index
            currentTrack.value = track
            state.value = PlaybackState.Playing(track)
        }

        override fun removeFromQueue(index: Int) {
            queue.value = queue.value.toMutableList().apply { if (index in indices) removeAt(index) }
        }

        override fun moveInQueue(from: Int, to: Int) {
            queue.value = queue.value.toMutableList().apply {
                if (from in indices && to in indices) add(to, removeAt(from))
            }
        }

        override fun playPause() {
            state.value = when (val current = state.value) {
                is PlaybackState.Playing -> PlaybackState.Paused(current.track)
                is PlaybackState.Paused -> PlaybackState.Playing(current.track)
                else -> current
            }
        }

        override fun next() = playAt(currentQueueIndex.value + 1)
        override fun previous() = playAt(currentQueueIndex.value - 1)
        override fun seekTo(positionMs: Long) {
            this.positionMs.value = positionMs
        }

        override fun setRepeatMode(mode: RepeatMode) {
            repeatMode.value = mode
        }

        override fun setShuffle(enabled: Boolean) {
            shuffleEnabled.value = enabled
        }

        override fun setVolume(volume: Float) {
            this.volume.value = volume.coerceIn(0f, 1f)
        }

        override fun stop() {
            state.value = PlaybackState.Idle
        }
    }

    private class RecordedProvider(
        val homeMode: StateMode = StateMode.POPULATED,
        val searchMode: StateMode = StateMode.POPULATED,
        /** `null` records a provider failure so the lyrics error state is rendered. */
        val lyrics: Lyrics? = Lyrics.Synced(
            listOf(
                LyricsLine(0, "First line"),
                LyricsLine(30_000, "Second line"),
                LyricsLine(60_000, "Third line"),
                LyricsLine(90_000, "Last line"),
            ),
        ),
    ) : MusicProvider {
        override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> = when (searchMode) {
            StateMode.POPULATED -> DhunResult.Success(
                SearchResults(
                    query = query,
                    songs = RECORDED_TRACKS,
                    artists = listOf(QUEEN),
                    albums = listOf(ALBUM),
                    playlists = listOf(REMOTE_PLAYLIST),
                ),
            )
            StateMode.EMPTY -> DhunResult.Success(SearchResults(query))
            StateMode.ERROR -> DhunResult.Failure(DhunError.Network("Recorded search failure"))
            StateMode.LOADING -> {
                CompletableDeferred<Unit>().await()
                error("Unreachable loading result")
            }
        }

        override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> =
            DhunResult.Success(SearchResults(""))

        override suspend fun searchSuggestions(query: String): DhunResult<List<String>> =
            DhunResult.Success(emptyList())

        override suspend fun homeFeedPage(): DhunResult<HomeFeedPage> = when (homeMode) {
            StateMode.POPULATED -> DhunResult.Success(
                HomeFeedPage(
                    sections = listOf(
                        HomeSection("Quick picks", items = RECORDED_TRACKS.take(4).map { HomeItem.TrackItem(it) }),
                        HomeSection("Recommended albums & EPs", items = listOf(HomeItem.AlbumItem(ALBUM))),
                        HomeSection("Classic rock", items = listOf(HomeItem.PlaylistItem(REMOTE_PLAYLIST))),
                        HomeSection("Artists", items = listOf(HomeItem.ArtistItem(QUEEN))),
                    ),
                ),
            )
            StateMode.EMPTY -> DhunResult.Success(HomeFeedPage())
            StateMode.ERROR -> DhunResult.Failure(DhunError.Network("Recorded Home failure"))
            StateMode.LOADING -> {
                CompletableDeferred<Unit>().await()
                error("Unreachable loading result")
            }
        }

        override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<HomeFeedPage> =
            DhunResult.Success(HomeFeedPage())

        override suspend fun homeFeed(): DhunResult<List<HomeSection>> =
            when (val result = homeFeedPage()) {
                is DhunResult.Success -> DhunResult.Success(result.value.sections)
                is DhunResult.Failure -> result
            }

        override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> =
            DhunResult.Success(RECORDED_TRACKS.drop(1))

        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> =
            DhunResult.Failure(DhunError.Unavailable("Visual evidence does not resolve audio"))

        override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> = lyrics?.let { DhunResult.Success(it) }
            ?: DhunResult.Failure(DhunError.Network("Recorded lyrics failure"))

        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> = DhunResult.Success(
            ArtistPage(
                artist = QUEEN,
                topSongs = RECORDED_TRACKS.take(5),
                albums = listOf(ALBUM),
                featuredPlaylists = listOf(REMOTE_PLAYLIST),
            ),
        )

        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> = DhunResult.Success(
            AlbumDetail(
                id = ALBUM_ID,
                title = ALBUM.title,
                artistName = QUEEN.name,
                artistId = QUEEN_ID,
                year = ALBUM.year,
                trackCountText = "${RECORDED_TRACKS.size} songs",
                thumbnailUrl = ALBUM.thumbnailUrl,
                tracks = RECORDED_TRACKS,
            ),
        )

        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> = DhunResult.Success(
            PlaylistDetail(
                id = REMOTE_PLAYLIST_ID,
                title = REMOTE_PLAYLIST.title,
                authorName = REMOTE_PLAYLIST.authorName,
                trackCountText = "${RECORDED_TRACKS.size} songs",
                thumbnailUrl = REMOTE_PLAYLIST.thumbnailUrl,
                tracks = RECORDED_TRACKS,
            ),
        )
    }

    private companion object {
        const val FRAME_NANOS = 50_000_000L
        const val QUEEN_ID = "UCEPMVbUzImPl4p8k4LkGevA"
        const val ALBUM_ID = "MPREb_mrZGcLOHyHy"
        const val REMOTE_PLAYLIST_ID = "VLRDAMVMutwMHfDZ6SA"

        fun artwork(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        fun track(
            id: String,
            title: String,
            artist: String,
            album: String,
            durationSeconds: Int,
            artistId: String? = null,
            albumId: String? = null,
        ) = Track(
            id = id,
            title = title,
            artistName = artist,
            artistId = artistId,
            albumName = album,
            albumId = albumId,
            durationSeconds = durationSeconds,
            thumbnailUrl = artwork(id),
        )

        val RECORDED_TRACKS = listOf(
            track(
                "utwMHfDZ6SA",
                "Bohemian Rhapsody",
                "Queen",
                "A Night At The Opera (Deluxe Remastered Version)",
                367,
                artistId = QUEEN_ID,
                albumId = ALBUM_ID,
            ),
            track("I7HR7Nd2FqU", "We Will Rock You", "Queen", "News Of The World", 122),
            track("BciS5krYL80", "Hotel California", "Eagles", "Legacy", 391),
            track("rFNrPleo-dA", "Everybody Wants To Rule The World", "Tears For Fears", "Running 100-120 BPM", 251),
            track("9RfVp-GhKfs", "Creep", "Radiohead", "Creep", 239),
            track("BaxnrepwXck", "Under Pressure (feat. David Bowie)", "Queen", "Hot Space", 242),
            track("pcXWq5MCelE", "Don't Stop Believin'", "Journey", "Coronation Party Songs", 231),
            track("yZR4zH9Zy34", "Dream On", "Aerosmith", "Pandora's Box", 267),
        )
        val QUEEN = Artist(id = QUEEN_ID, name = "Queen")
        val ALBUM = Album(
            id = ALBUM_ID,
            title = "A Night At The Opera",
            artistName = "Queen",
            year = "1975",
            trackCount = RECORDED_TRACKS.size,
            thumbnailUrl = artwork(RECORDED_TRACKS.first().id),
        )
        val REMOTE_PLAYLIST = Playlist(
            id = REMOTE_PLAYLIST_ID,
            title = "Classic rock",
            authorName = "YouTube Music",
            trackCountText = "${RECORDED_TRACKS.size} songs",
            thumbnailUrl = artwork(RECORDED_TRACKS[2].id),
        )
    }
}
