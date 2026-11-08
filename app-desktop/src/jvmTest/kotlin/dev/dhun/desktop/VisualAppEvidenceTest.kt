package dev.dhun.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
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
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
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
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadProgress
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTheme
import dev.dhun.design.DhunThemeMode
import dev.dhun.design.catalog.ComponentCatalogScreen
import dev.dhun.design.components.DhunConfirmDialog
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassDialog
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.GetRecommendationsUseCase
import dev.dhun.innertube.SearchFilter
import dev.dhun.legal.DhunAppInfo
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.lyrics.LyricsSource
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.library.LibraryTab
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
import dev.dhun.ui.settings.BatteryPlaybackRationaleDialog
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DetailRoute
import dev.dhun.ui.shell.DhunAppShell
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    fun `playlist create and rename popups retain labels submit and persistence`() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            withFixture(mode) { fixture ->
                val nav = AppNavState().apply { selectedTab = AppTab.LIBRARY }
                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent { Shell(fixture, nav, mode, isDesktop = false) }
                    mainClock.advanceTimeBy(600)

                    onNodeWithText("New playlist").performClick()
                    onNodeWithContentDescription("Playlist name").assertIsDisplayed()
                    onNodeWithText("Save a personal collection in your local library.").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playlist-create-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithContentDescription("Playlist name").performTextInput("Keyboard Created")
                    onNodeWithContentDescription("Playlist name").performImeAction()
                    val createDeadline = System.nanoTime() + 5_000_000_000L
                    while (fixture.library.playlistsFlow.value.none { it.name == "Keyboard Created" } &&
                        System.nanoTime() < createDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertTrue(
                        fixture.library.playlistsFlow.value.any { it.name == "Keyboard Created" },
                        "Done must use the existing playlist-create callback and persist the name",
                    )
                    onAllNodesWithContentDescription("Playlist name").assertCountEquals(0)

                    onNodeWithContentDescription("Manage Visual QA").performScrollTo().performClick()
                    onNodeWithText("Rename").performClick()
                    onNodeWithText("Rename playlist").assertIsDisplayed()
                    onNodeWithText("Current name").assertIsDisplayed()
                    onNodeWithContentDescription("New playlist name").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playlist-rename-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithContentDescription("New playlist name").performTextClearance()
                    onNodeWithContentDescription("New playlist name").performTextInput("Renamed QA")
                    onNodeWithContentDescription("New playlist name").performImeAction()
                    val renameDeadline = System.nanoTime() + 5_000_000_000L
                    while (fixture.library.playlistsFlow.value.none { it.name == "Renamed QA" } &&
                        System.nanoTime() < renameDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertTrue(
                        fixture.library.playlistsFlow.value.any { it.name == "Renamed QA" },
                        "Save must preserve the existing rename callback and persisted result",
                    )
                }

                runSkikoComposeUiTest(size = Size(360f, 760f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                            Shell(fixture, nav, mode, isDesktop = false)
                        }
                    }
                    mainClock.advanceTimeBy(600)
                    onNodeWithText("New playlist").performClick()
                    onNodeWithContentDescription("Playlist name").assertIsDisplayed()
                    onNodeWithText("Create").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playlist-create-narrow-large-text-${mode.id}.png",
                        renderCurrentFrame(this, 360, 760),
                    )
                    onNodeWithText("Cancel").performClick()
                    onNodeWithContentDescription("Manage Renamed QA").performScrollTo().performClick()
                    onNodeWithText("Rename").performClick()
                    onNodeWithContentDescription("New playlist name").assertIsDisplayed()
                    onNodeWithText("Save").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playlist-rename-narrow-large-text-${mode.id}.png",
                        renderCurrentFrame(this, 360, 760),
                    )
                }
            }
        }
    }

    @Test
    fun `capture pre-redesign playlist and playback error popup baselines in the app shell`() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            withFixture(mode) { fixture ->
                fixture.player.useArtwork(
                    extremeArtwork("popup-baseline-${mode.id}", if (mode == DhunThemeMode.DARK) 0x775A86 else 0x3F7881),
                )
                val nav = AppNavState().apply { selectedTab = AppTab.LIBRARY }
                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent { Shell(fixture, nav, mode, isDesktop = false) }
                    mainClock.advanceTimeBy(700)

                    onNodeWithText("New playlist").performClick()
                    mainClock.advanceTimeBy(400)
                    onNodeWithText("My playlist").assertIsDisplayed()
                    captureUiImage(
                        "popup-before-playlist-create-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Cancel").performClick()
                    mainClock.advanceTimeBy(300)

                    onNodeWithContentDescription("Manage Visual QA").performScrollTo().performClick()
                    mainClock.advanceTimeBy(250)
                    onNodeWithText("Rename").performClick()
                    mainClock.advanceTimeBy(400)
                    onNodeWithText("Rename playlist").assertIsDisplayed()
                    captureUiImage(
                        "popup-before-playlist-rename-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Cancel").performClick()
                    mainClock.advanceTimeBy(300)

                    val track = requireNotNull(fixture.player.currentTrack.value)
                    fixture.player.state.value = PlaybackState.Error(
                        track = track,
                        message = "Could not start playback. Check the connection and try again.",
                        detail = "web_embedded=AUTH_REQUIRED(provider returned no playable stream); " +
                            "resolver=UNAVAILABLE(network request timed out)",
                    )
                    mainClock.advanceTimeBy(300)
                    onNodeWithContentDescription("Show playback details").performClick()
                    mainClock.advanceTimeBy(400)
                    onNodeWithText("Playback details").assertIsDisplayed()
                    captureUiImage(
                        "popup-before-playback-error-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Close").performClick()
                }
            }
        }
    }

    @Test
    fun `capture pre-redesign background playback rationale baseline`() {
        DhunAppearance.reset()
        try {
            for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
                DhunAppearance.setAppearance(nextMode = mode)
                val dismissals = AtomicInteger()
                val allows = AtomicInteger()
                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        DhunTheme(mode = mode) {
                            Box(Modifier.fillMaxSize().background(DhunColors.background))
                            PreRedesignBatteryPlaybackRationale(
                                onDismiss = { dismissals.incrementAndGet() },
                                onAllow = { allows.incrementAndGet() },
                            )
                        }
                    }
                    mainClock.advanceTimeBy(500)
                    onNodeWithText("Keep playback reliable").assertIsDisplayed()
                    onNodeWithText("Not now").assertIsDisplayed()
                    onNodeWithText("Allow").assertIsDisplayed()
                    captureUiImage(
                        "popup-before-battery-rationale-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Allow").performClick()
                    assertEquals(1, allows.get(), "The baseline Allow action must remain an explicit callback")
                    assertEquals(0, dismissals.get(), "Allow is not the Not now dismissal action")
                }
            }
        } finally {
            DhunAppearance.reset()
        }
    }

    @Test
    fun `battery playback rationale has an accessible primary allow action in both themes`() {
        DhunAppearance.reset()
        try {
            for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
                DhunAppearance.setAppearance(nextMode = mode)
                val dismissals = AtomicInteger()
                val allows = AtomicInteger()
                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        DhunTheme(mode = mode) {
                            Box(Modifier.fillMaxSize().background(DhunColors.background))
                            BatteryPlaybackRationaleDialog(
                                onDismiss = { dismissals.incrementAndGet() },
                                onAllow = { allows.incrementAndGet() },
                            )
                        }
                    }
                    mainClock.advanceTimeBy(500)
                    onNodeWithText("Background playback").assertIsDisplayed()
                    onNodeWithText("Keep playback reliable").assertIsDisplayed()
                    onNodeWithText("Not now").assertIsDisplayed()
                    onNodeWithText("Allow").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-battery-rationale-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Not now").performClick()
                    onNodeWithText("Allow").performClick()
                    assertEquals(1, dismissals.get(), "Not now remains a dismiss-only callback")
                    assertEquals(1, allows.get(), "Allow remains a separate settings handoff callback")
                }

                runSkikoComposeUiTest(size = Size(360f, 760f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                            DhunTheme(mode = mode) {
                                Box(Modifier.fillMaxSize().background(DhunColors.background))
                                BatteryPlaybackRationaleDialog(onDismiss = {}, onAllow = {})
                            }
                        }
                    }
                    mainClock.advanceTimeBy(500)
                    onNodeWithText("Allow").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-battery-rationale-narrow-large-text-${mode.id}.png",
                        renderCurrentFrame(this, 360, 760),
                    )
                }
            }
        } finally {
            DhunAppearance.reset()
        }
    }

    @Test
    fun `lyrics match remains usable at narrow large text and local import expands`() {
        withFixture(DhunThemeMode.DARK, withLyricsMatches = true) { fixture ->
            val nav = AppNavState().apply { playerExpanded = true }
            runSkikoComposeUiTest(size = Size(360f, 760f)) {
                mainClock.autoAdvance = false
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                        Shell(fixture, nav, DhunThemeMode.DARK, isDesktop = false)
                    }
                }
                mainClock.advanceTimeBy(600)
                onNodeWithContentDescription("Lyrics view").performClick()
                mainClock.advanceTimeBy(500)
                onNodeWithContentDescription("Lyrics controls").performClick()
                mainClock.advanceTimeBy(500)
                onNodeWithContentDescription("Find lyrics matches").performScrollTo().performClick()
                mainClock.advanceTimeBy(500)
                onNodeWithText("Lyrics matches for this song").assertIsDisplayed()
                onNodeWithContentDescription("Search lyrics matches").assertIsDisplayed()
                onNodeWithText("Close").assertIsDisplayed()
                captureUiImage(
                    "popup-after-lyrics-match-narrow-large-text-dark.png",
                    renderCurrentFrame(this, 360, 760),
                )
                onNodeWithText("Paste local TTML lyrics").performClick()
                onNodeWithText("TTML document").assertIsDisplayed()
                onNodeWithContentDescription("TTML document").assertIsDisplayed()
                onNodeWithText("Preview local TTML").assertIsDisplayed()
                val localTtml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="5s">Local preview line</p></div></body></tt>"""
                onNodeWithContentDescription("TTML document").performScrollTo().performTextInput(localTtml)
                onNodeWithText("Preview local TTML").performScrollTo().performClick()
                val previewDeadline = System.nanoTime() + 5_000_000_000L
                while (fixture.playerVm.lyricsMatches?.state?.value?.matches?.none { it.provider == "local_ttml" } != false &&
                    System.nanoTime() < previewDeadline
                ) {
                    mainClock.advanceTimeBy(50)
                    Thread.sleep(20)
                }
                val previewState = requireNotNull(fixture.playerVm.lyricsMatches).state.value
                assertTrue(previewState.matches.any { it.provider == "local_ttml" })
                assertEquals(
                    "Local TTML preview ready. Choose this match to save it for this song; nothing was sent to a provider.",
                    previewState.message,
                )
                onNodeWithText("Choose this match").performScrollTo().assertIsDisplayed()
                captureUiImage(
                    "popup-after-lyrics-local-import-narrow-large-text-dark.png",
                    renderCurrentFrame(this, 360, 760),
                )
                onNodeWithText("Choose this match").performScrollTo().performClick()
                val saveDeadline = System.nanoTime() + 5_000_000_000L
                while (fixture.playerVm.lyricsMatches?.state?.value?.loading == true &&
                    System.nanoTime() < saveDeadline
                ) {
                    mainClock.advanceTimeBy(50)
                    Thread.sleep(20)
                }
                assertEquals("local_ttml", fixture.playerVm.lyricsMatches?.state?.value?.selected?.provider)
                val savedChoice = runBlocking {
                    fixture.data.settings.getString(
                        SettingsKeys.lyricsMatchKey(requireNotNull(fixture.player.currentTrack.value).id),
                    )
                }
                assertTrue(
                    savedChoice?.contains("\"provider\":\"local_ttml\"") == true,
                    "Choosing the preview must persist the per-track local-TTML choice",
                )
                val requestsBeforeImeSearch = fixture.provider.lyricsFetchRequests.get()
                val lyricsSearchField = onNodeWithContentDescription("Search lyrics matches")
                lyricsSearchField.performScrollTo()
                lyricsSearchField.performTextClearance()
                lyricsSearchField.performTextInput("manual query")
                lyricsSearchField.performImeAction()
                val imeSearchDeadline = System.nanoTime() + 5_000_000_000L
                while (fixture.provider.lyricsFetchRequests.get() <= requestsBeforeImeSearch &&
                    System.nanoTime() < imeSearchDeadline
                ) {
                    mainClock.advanceTimeBy(50)
                    Thread.sleep(20)
                }
                assertTrue(
                    fixture.provider.lyricsFetchRequests.get() > requestsBeforeImeSearch,
                    "The Search IME action must invoke the same lyrics-match search callback",
                )
                val imeSearchCompleteDeadline = System.nanoTime() + 5_000_000_000L
                while (fixture.playerVm.lyricsMatches?.state?.value?.loading == true &&
                    System.nanoTime() < imeSearchCompleteDeadline
                ) {
                    mainClock.advanceTimeBy(50)
                    Thread.sleep(20)
                }
                assertFalse(fixture.playerVm.lyricsMatches?.state?.value?.loading == true)
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
    fun `playlist destination actions preserve add duplicate open and retry behavior`() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            withFixture(mode) { fixture ->
                val repository = fixture.data.playlists
                val createdId = AtomicReference<String?>()
                val addedName = AtomicReference<String?>()
                val openedId = AtomicReference<String?>()
                val dismissals = AtomicInteger()
                val createCalls = AtomicInteger()
                val addAttempts = AtomicInteger()
                val firstMembershipStarted = CompletableDeferred<Unit>()
                val releaseFirstMembership = CompletableDeferred<Unit>()
                val diagnosticRepository = object : PlaylistRepository by repository {
                    override suspend fun create(name: String, description: String?): LocalPlaylist {
                        createCalls.incrementAndGet()
                        return repository.create(name, description).also { created ->
                            if (name == "Retry QA") createdId.set(created.id)
                        }
                    }

                    override suspend fun addTrack(playlistId: String, track: Track): Boolean {
                        if (playlistId == createdId.get()) {
                            if (addAttempts.incrementAndGet() == 1) {
                                firstMembershipStarted.complete(Unit)
                                releaseFirstMembership.await()
                                throw IllegalStateException("injected membership failure")
                            }
                        }
                        return repository.addTrack(playlistId, track)
                    }
                }

                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        DhunTheme(mode = mode) {
                            Box(Modifier.fillMaxSize().background(DhunColors.background))
                            AddToPlaylistDialog(
                                track = RECORDED_TRACKS.first(),
                                playlistRepository = diagnosticRepository,
                                onDismiss = { dismissals.incrementAndGet() },
                                onAdded = { addedName.set(it) },
                                onOpenPlaylist = { openedId.set(it) },
                            )
                        }
                    }
                    mainClock.advanceTimeBy(600)
                    val rowsDeadline = System.nanoTime() + 5_000_000_000L
                    while (onAllNodes(hasText("Visual QA"), useUnmergedTree = true)
                            .fetchSemanticsNodes().isEmpty() && System.nanoTime() < rowsDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    onNodeWithContentDescription("Open Visual QA").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playlist-destinations-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )

                    onNodeWithContentDescription("Open Visual QA").performClick()
                    assertEquals(fixture.localPlaylistId, openedId.get(), "Open stays a separate navigation action")
                    onNodeWithContentDescription("Add this song to Visual QA").performClick()
                    val duplicateDeadline = System.nanoTime() + 5_000_000_000L
                    while (onAllNodes(hasText("Already in \"Visual QA\"."), useUnmergedTree = true)
                            .fetchSemanticsNodes().isEmpty() && System.nanoTime() < duplicateDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    onNodeWithText("Already in \"Visual QA\".").assertIsDisplayed()
                    assertNull(addedName.get(), "A duplicate is not reported as a successful add")
                    assertEquals(0, dismissals.get(), "Duplicate selection keeps the picker open")
                    captureUiImage(
                        "popup-after-playlist-duplicate-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )

                    onNodeWithText("Create new playlist").performClick()
                    onNodeWithContentDescription("New playlist name").performTextInput("Retry QA")
                    captureUiImage(
                        "popup-after-playlist-create-form-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Create & add").performClick()
                    val pendingDeadline = System.nanoTime() + 5_000_000_000L
                    while (!firstMembershipStarted.isCompleted && System.nanoTime() < pendingDeadline) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    val enteredPending = firstMembershipStarted.isCompleted
                    if (!enteredPending) releaseFirstMembership.complete(Unit)
                    assertTrue(enteredPending, "The membership write should enter its pending state")
                    onNodeWithText("Create & add").assertIsNotEnabled()
                    onNodeWithText("Back").assertIsNotEnabled()
                    onNodeWithContentDescription("New playlist name").assertIsNotEnabled()
                    releaseFirstMembership.complete(Unit)

                    val failureDeadline = System.nanoTime() + 5_000_000_000L
                    while (onAllNodes(hasText("Could not finish the new playlist and add this song. Try again."))
                            .fetchSemanticsNodes().isEmpty() && System.nanoTime() < failureDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    onNodeWithText("Could not finish the new playlist and add this song. Try again.").assertIsDisplayed()
                    onNodeWithText("Playlist created. Retrying will add this song to that same playlist.").assertIsDisplayed()
                    onNodeWithContentDescription("New playlist name").assertIsNotEnabled()
                    assertNull(addedName.get(), "The failed membership write cannot report success")
                    assertEquals(0, dismissals.get(), "Failure keeps the form open for retry")
                    val failedPlaylistId = requireNotNull(createdId.get())
                    assertEquals(1, createCalls.get(), "The failed first attempt creates exactly one playlist")
                    assertFalse(runBlocking { repository.contains(failedPlaylistId, RECORDED_TRACKS.first().id) })
                    captureUiImage(
                        "popup-after-playlist-add-failure-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )

                    onNodeWithText("Try again").performClick()
                    val successDeadline = System.nanoTime() + 5_000_000_000L
                    while (addedName.get() == null && System.nanoTime() < successDeadline) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertEquals("Retry QA", addedName.get())
                    assertEquals(1, dismissals.get(), "A committed add preserves the original close callback")
                    assertEquals(1, createCalls.get(), "Retry reuses the pending playlist instead of duplicating it")
                    assertEquals(2, addAttempts.get(), "Retry repeats membership only")
                    assertTrue(runBlocking { repository.contains(failedPlaylistId, RECORDED_TRACKS.first().id) })
                    val matchingPlaylists = runBlocking {
                        repository.observePlaylists().first().filter { it.name == "Retry QA" }
                    }
                    assertEquals(1, matchingPlaylists.size)
                }
            }
        }
    }

    @Test
    fun `playback error prioritizes retry and keeps complete selectable diagnostics`() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            withFixture(mode) { fixture ->
                val track = requireNotNull(fixture.player.currentTrack.value)
                val diagnostic = "web_embedded=AUTH_REQUIRED(provider returned no playable stream); " +
                    "resolver=UNAVAILABLE(network request timed out); " +
                    "requestId=visual-evidence-2026-11-07; retryable=true"
                fixture.player.state.value = PlaybackState.Error(
                    track = track,
                    message = "Could not start playback. Check the connection and try again.",
                    detail = diagnostic,
                )
                val nav = AppNavState().apply { selectedTab = AppTab.LIBRARY }
                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent { Shell(fixture, nav, mode, isDesktop = false) }
                    mainClock.advanceTimeBy(600)
                    onNodeWithContentDescription("Show playback details").performClick()
                    onNodeWithText("Playback details").assertIsDisplayed()
                    onNodeWithText("Show diagnostics").assertIsDisplayed()
                    onNodeWithText("Could not start playback. Check the connection and try again.")
                        .assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playback-error-collapsed-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Show diagnostics").performClick()
                    onNodeWithText("Hide diagnostics").assertIsDisplayed()
                    onNodeWithText("Track ID: ${track.id}").assertIsDisplayed()
                    onNodeWithText("web_embedded=AUTH_REQUIRED", substring = true).assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playback-error-expanded-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Close").performClick()
                    assertEquals(0, fixture.player.retryRequests.get(), "Close only dismisses")
                    assertEquals(0, onAllNodes(hasText("Playback details")).fetchSemanticsNodes().size)

                    onNodeWithContentDescription("Show playback details").performClick()
                    onNodeWithText("Retry").performClick()
                    assertEquals(1, fixture.player.retryRequests.get(), "Retry retains the original player callback")
                    assertEquals(0, onAllNodes(hasText("Playback details")).fetchSemanticsNodes().size)
                }

                runSkikoComposeUiTest(size = Size(360f, 760f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                            Shell(fixture, nav, mode, isDesktop = false)
                        }
                    }
                    mainClock.advanceTimeBy(600)
                    onNodeWithContentDescription("Show playback details").performClick()
                    captureUiImage(
                        "popup-after-playback-error-narrow-large-text-collapsed-${mode.id}.png",
                        renderCurrentFrame(this, 360, 760),
                    )
                    onNodeWithText("Show diagnostics").performClick()
                    onNodeWithText("Retry").performScrollTo()
                    onNodeWithText("Retry").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-playback-error-narrow-large-text-${mode.id}.png",
                        renderCurrentFrame(this, 360, 760),
                    )
                }
            }
        }
    }

    @Test
    fun `country picker search selection and persistence work in both themes`() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            withFixture(mode) { fixture ->
                val nav = AppNavState().apply {
                    detailStack.clear()
                    push(DetailRoute.SettingsCategoryPage(SettingsCategory.CONTENT))
                }
                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent { Shell(fixture, nav, mode, isDesktop = false) }
                    mainClock.advanceTimeBy(600)
                    onNodeWithText("United States (US)").performClick()
                    onNodeWithText("Choose a country or region").assertIsDisplayed()
                    onNodeWithText("Current selection").assertIsDisplayed()
                    onNodeWithText("United States · US").assertIsDisplayed()
                    onNodeWithContentDescription("Search country or region by name or code")
                        .performTextInput("Nepal")
                    onNodeWithContentDescription("Nepal (NP)").assertIsDisplayed()
                    onNodeWithText("United States · US").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-country-picker-search-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithContentDescription("Clear country search").performClick()
                    onNodeWithContentDescription("Search country or region by name or code")
                        .performTextInput("Nepal")
                    onNodeWithContentDescription("Nepal (NP)").performClick()
                    onNodeWithText("Nepal (NP)").assertIsDisplayed()
                    val persistenceDeadline = System.nanoTime() + 5_000_000_000L
                    var persistedCountry: String? = null
                    while (persistedCountry != "NP" && System.nanoTime() < persistenceDeadline) {
                        mainClock.advanceTimeBy(50)
                        persistedCountry = runBlocking {
                            fixture.data.settings.getString(SettingsKeys.COUNTRY_CODE)
                        }
                        if (persistedCountry != "NP") Thread.sleep(20)
                    }
                    assertEquals("NP", persistedCountry, "Selection must persist through the existing settings repository")
                    onNodeWithText("Nepal (NP)").performClick()
                    onNodeWithText("Nepal · NP").assertIsDisplayed()
                    onNodeWithContentDescription("Nepal (NP)").performClick()
                }

                runSkikoComposeUiTest(size = Size(360f, 760f)) {
                    mainClock.autoAdvance = false
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                            Shell(fixture, nav, mode, isDesktop = false)
                        }
                    }
                    mainClock.advanceTimeBy(600)
                    onNodeWithText("Nepal (NP)").performClick()
                    onNodeWithText("Choose a country or region").assertIsDisplayed()
                    onNodeWithText("Nepal · NP").assertIsDisplayed()
                    onNodeWithContentDescription("Search country or region by name or code").assertIsDisplayed()
                    captureUiImage(
                        "popup-after-country-picker-narrow-large-text-${mode.id}.png",
                        renderCurrentFrame(this, 360, 760),
                    )
                }
            }
        }
    }

    @Test
    fun renderLyricsControlsDialogInBothThemesAndExerciseItsActions() {
        for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
            val provider = RecordedProvider()
            withFixture(mode, provider = provider, withLyricsMatches = true) { fixture ->
                fixture.player.useArtwork(
                    extremeArtwork(
                        "lyrics-controls-${mode.id}",
                        if (mode == DhunThemeMode.DARK) 0x775A86 else 0x3F7881,
                    ),
                )
                if (mode == DhunThemeMode.LIGHT) {
                    fixture.playerVm.lyricsRomanization.setEnabled(true)
                    val romanizationDeadline = System.nanoTime() + 5_000_000_000L
                    while (!fixture.playerVm.lyricsRomanization.enabled.value && System.nanoTime() < romanizationDeadline) {
                        Thread.sleep(20)
                    }
                    assertTrue(
                        fixture.playerVm.lyricsRomanization.enabled.value,
                        "Romanization setting should persist before capture",
                    )
                }
                // Keep lyrics cache state deterministic for the provider-selection
                // and Refetch callback checks below.
                runBlocking { fixture.data.lyricsCache.clear() }
                val nav = AppNavState().apply { playerExpanded = true }

                runSkikoComposeUiTest(size = Size(412f, 915f)) {
                    mainClock.autoAdvance = false
                    setContent { Shell(fixture, nav, mode, isDesktop = false) }
                    mainClock.advanceTimeBy(600)
                    onNodeWithContentDescription("Lyrics view").performClick()
                    mainClock.advanceTimeBy(500)
                    onNodeWithContentDescription("Lyrics controls").assertIsDisplayed()
                    onNodeWithContentDescription("Lyrics controls").performClick()
                    mainClock.advanceTimeBy(500)

                    onNodeWithText("Lyrics controls").assertIsDisplayed()
                    onNodeWithContentDescription("Current song title: Bohemian Rhapsody. From Queen").assertIsDisplayed()
                    onNodeWithText("Bohemian Rhapsody (From Queen)").assertIsDisplayed()
                    onNodeWithText("Lyrics provider").assertIsDisplayed()
                    onNodeWithContentDescription("Lyrics provider: Auto (synced first)").assertIsDisplayed()
                    onNodeWithText("Auto (synced first)").assertIsDisplayed()
                    onNodeWithContentDescription("Romanization mode: Auto (conservative)").assertIsDisplayed()
                    onNodeWithText("Lyrics offset").assertIsDisplayed()
                    onNodeWithText("0 ms").assertIsDisplayed()
                    onAllNodesWithContentDescription("Reset lyrics offset").assertCountEquals(0)
                    onNodeWithContentDescription("Offset marker: minus five seconds").assertIsDisplayed()
                    onNodeWithContentDescription("Offset marker: zero seconds").assertIsDisplayed()
                    onNodeWithContentDescription("Offset marker: plus five seconds").assertIsDisplayed()
                    onNodeWithContentDescription("Find lyrics matches").assertIsDisplayed()
                    onNodeWithText("Find lyrics matches for this song").assertIsDisplayed()
                    onNodeWithText("Close").assertIsDisplayed()
                    onNodeWithContentDescription("Edit lyrics using local TTML").assertIsDisplayed()
                    onNodeWithContentDescription("Refetch current lyrics").assertIsDisplayed()
                    onNodeWithContentDescription("Search lyrics matches").assertIsDisplayed()

                    // Capture the real anchored choice menus with the selected row
                    // treatment visible, then activate the selected rows to dismiss.
                    onNodeWithContentDescription("Lyrics provider: Auto (synced first)").performClick()
                    mainClock.advanceTimeBy(300)
                    onNode(isSelected() and hasText("Auto (synced first)")).assertIsDisplayed()
                    captureUiImage(
                        "popup-after-lyrics-provider-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNode(isSelected() and hasText("Auto (synced first)")).performClick()
                    mainClock.advanceTimeBy(300)

                    onNodeWithContentDescription("Romanization mode: Auto (conservative)").performClick()
                    mainClock.advanceTimeBy(300)
                    onNode(isSelected() and hasText("Auto (conservative)")).assertIsDisplayed()
                    captureUiImage(
                        "popup-after-romanization-mode-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNode(isSelected() and hasText("Auto (conservative)")).performClick()
                    mainClock.advanceTimeBy(300)

                    captureUiImage(
                        "popup-after-lyrics-controls-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )

                    // Timing persistence and the conditional reset affordance
                    // remain tied to the existing repository callbacks.
                    onNodeWithContentDescription("Lyrics offset")
                        .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                            assertTrue(setProgress(700f), "Lyrics offset must retain its accessible SetProgress action")
                        }
                    val offsetDeadline = System.nanoTime() + 5_000_000_000L
                    while (fixture.playerVm.lyricsTiming.value.offsetMs != 700 &&
                        System.nanoTime() < offsetDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertEquals(
                        700,
                        fixture.playerVm.lyricsTiming.value.offsetMs,
                        "The accessible slider action must persist the selected offset",
                    )
                    mainClock.advanceTimeBy(50)
                    onNodeWithText("+700 ms").assertIsDisplayed()
                    onNodeWithContentDescription("Reset lyrics offset").performClick()
                    val resetDeadline = System.nanoTime() + 5_000_000_000L
                    while (fixture.playerVm.lyricsTiming.value.offsetMs != 0 &&
                        System.nanoTime() < resetDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertEquals(
                        0,
                        fixture.playerVm.lyricsTiming.value.offsetMs,
                        "Reset must persist the zero offset",
                    )
                    mainClock.advanceTimeBy(50)
                    onNodeWithText("0 ms").assertIsDisplayed()
                    onAllNodesWithContentDescription("Reset lyrics offset").assertCountEquals(0)

                    // The compact provider chip still opens the full existing
                    // choice list and writes the selected preference.
                    val beforeProviderSelection = provider.lyricsFetchRequests.get()
                    onNodeWithContentDescription("Lyrics provider: Auto (synced first)").performClick()
                    mainClock.advanceTimeBy(400)
                    onNodeWithText("LRCLIB").performClick()
                    mainClock.advanceTimeBy(200)
                    onNodeWithContentDescription("Lyrics provider: LRCLIB").assertIsDisplayed()
                    val providerSelectionDeadline = System.nanoTime() + 5_000_000_000L
                    while (provider.lyricsFetchRequests.get() <= beforeProviderSelection &&
                        System.nanoTime() < providerSelectionDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertTrue(
                        provider.lyricsFetchRequests.get() > beforeProviderSelection,
                        "Changing the provider should reach the configured provider",
                    )
                    val selectionResultDeadline = System.nanoTime() + 5_000_000_000L
                    while (fixture.playerVm.lyricsState.value !is LyricsUiState.Synced &&
                        System.nanoTime() < selectionResultDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }

                    // Refetch retains its existing callback. Clear the cache so
                    // this interaction proves that callback reaches the provider;
                    // it does not claim that the current repository bypasses a hit.
                    runBlocking { fixture.data.lyricsCache.clear() }
                    val beforeRefetch = provider.lyricsFetchRequests.get()
                    onNodeWithContentDescription("Refetch current lyrics").performClick()
                    val refetchDeadline = System.nanoTime() + 5_000_000_000L
                    while (provider.lyricsFetchRequests.get() <= beforeRefetch &&
                        System.nanoTime() < refetchDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertTrue(
                        provider.lyricsFetchRequests.get() > beforeRefetch,
                        "Refetch should invoke the existing lyrics callback and reach the configured provider",
                    )
                    val refetchResultDeadline = System.nanoTime() + 5_000_000_000L
                    while (fixture.playerVm.lyricsState.value !is LyricsUiState.Synced &&
                        System.nanoTime() < refetchResultDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    seedSavedLyricsChoice(fixture)

                    onNodeWithContentDescription("Edit lyrics using local TTML").performClick()
                    mainClock.advanceTimeBy(500)
                    onNodeWithText("Lyrics matches for this song").assertIsDisplayed()
                    onNodeWithText("Saved choice: lrclib · Bohemian Rhapsody").assertIsDisplayed()
                    onNodeWithText("Paste local TTML lyrics").assertIsDisplayed()
                    onNodeWithText("Preview local TTML").assertIsDisplayed()
                    captureUiImage(
                        "lyrics-match-picker-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    onNodeWithText("Close").performClick()
                    mainClock.advanceTimeBy(400)

                    onNodeWithContentDescription("Lyrics controls").performClick()
                    mainClock.advanceTimeBy(400)
                    val beforeSearch = provider.lyricsFetchRequests.get()
                    val searchGate = CompletableDeferred<Unit>()
                    provider.lyricsGate = searchGate
                    onNodeWithContentDescription("Search lyrics matches").performClick()
                    val searchDeadline = System.nanoTime() + 5_000_000_000L
                    while (provider.lyricsFetchRequests.get() < beforeSearch + 2 && System.nanoTime() < searchDeadline) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    assertTrue(
                        provider.lyricsFetchRequests.get() >= beforeSearch + 2,
                        "Search should run the existing match controller against its enabled sources",
                    )
                    mainClock.advanceTimeBy(100)
                    onNodeWithText("Searching / parsing / saving…").assertIsDisplayed()
                    onNodeWithText("Saved choice: lrclib · Bohemian Rhapsody").assertIsDisplayed()
                    captureUiImage(
                        "lyrics-match-searching-${mode.id}.png",
                        renderCurrentFrame(this, 412, 915),
                    )
                    searchGate.complete(Unit)
                    provider.lyricsGate = null
                    val searchCompleteDeadline = System.nanoTime() + 5_000_000_000L
                    while (fixture.playerVm.lyricsMatches?.state?.value?.loading == true &&
                        System.nanoTime() < searchCompleteDeadline
                    ) {
                        mainClock.advanceTimeBy(50)
                        Thread.sleep(20)
                    }
                    onNodeWithText("Lyrics matches for this song").assertIsDisplayed()
                    onNodeWithText("Search / Retry").assertIsDisplayed()
                    onNodeWithText("Close").performClick()
                    mainClock.advanceTimeBy(400)

                    onNodeWithContentDescription("Lyrics controls").performClick()
                    mainClock.advanceTimeBy(400)
                    val beforeFindMatches = provider.lyricsFetchRequests.get()
                    onNodeWithContentDescription("Find lyrics matches").performClick()
                    mainClock.advanceTimeBy(400)
                    Thread.sleep(100)
                    assertEquals(
                        beforeFindMatches,
                        provider.lyricsFetchRequests.get(),
                        "Find lyrics matches should open the existing picker without auto-searching",
                    )
                    onNodeWithText("Lyrics matches for this song").assertIsDisplayed()
                    onNodeWithText("Close").performClick()
                    mainClock.advanceTimeBy(400)

                    onNodeWithContentDescription("Lyrics controls").performClick()
                    mainClock.advanceTimeBy(400)
                    onNodeWithText("Close").performClick()
                    mainClock.advanceTimeBy(400)
                    onNodeWithContentDescription("Lyrics controls").performClick()
                    mainClock.advanceTimeBy(400)
                    onNodeWithText("Close").assertIsDisplayed()
                    onNodeWithText("Close").performClick()
                    mainClock.advanceTimeBy(400)
                }
            }
        }
    }

    @Test
    fun `capture full player track action overlay over artwork`() {
        withFixture(DhunThemeMode.DARK, includeDownloads = true) { fixture ->
            fixture.player.useArtwork(extremeArtwork("audit-player-actions", 0x6D4C82))
            val nav = AppNavState().apply { playerExpanded = true }
            runSkikoComposeUiTest(size = Size(412f, 915f)) {
                mainClock.autoAdvance = false
                setContent { Shell(fixture, nav, DhunThemeMode.DARK, isDesktop = false) }
                mainClock.advanceTimeBy(600)
                onNodeWithContentDescription("More player actions").performClick()
                mainClock.advanceTimeBy(100)
                captureUiImage(
                    "audit-21-03-01-full-player-track-actions-dark.png",
                    renderCurrentFrame(this, 412, 915),
                )
                mainClock.advanceTimeBy(500)
                onNodeWithText("Play next").assertIsDisplayed()
                onNodeWithText("Add to queue").assertIsDisplayed()
                onNodeWithText("Add to playlist").assertIsDisplayed()
                onNodeWithText("Download").assertIsDisplayed()
                onNodeWithText("Go to artist").assertIsDisplayed()
                onNodeWithText("Go to album").assertIsDisplayed()
                captureUiImage(
                    "audit-21-03-04-track-menu-settled-dark.png",
                    renderCurrentFrame(this, 412, 915),
                )
            }
        }
    }

    @Test
    fun `capture one-to-one screenshot audit library and settings states`() {
        withFixture(
            DhunThemeMode.DARK,
            withLyricsMatches = true,
            includeDownloads = true,
            includeHistoryAuditData = true,
        ) { fixture ->
            val downloadDeadline = System.nanoTime() + 5_000_000_000L
            while (fixture.library.downloadsForUi.value.totalCount != 1 &&
                System.nanoTime() < downloadDeadline
            ) {
                Thread.sleep(20)
            }
            assertEquals(1, fixture.library.downloadsForUi.value.totalCount)
            val historyDeadline = System.nanoTime() + 5_000_000_000L
            while (fixture.library.groupedHistory.value.sumOf { it.entries.size } != 8 &&
                System.nanoTime() < historyDeadline
            ) {
                Thread.sleep(20)
            }
            assertEquals(8, fixture.library.groupedHistory.value.sumOf { it.entries.size })
            val nav = AppNavState()
            runSkikoComposeUiTest(size = Size(360f, 800f)) {
                mainClock.autoAdvance = false
                setContent { Shell(fixture, nav, DhunThemeMode.DARK, isDesktop = false) }
                mainClock.advanceTimeBy(600)

                fun captureAudit(name: String) {
                    mainClock.advanceTimeBy(500)
                    captureUiImage("audit-$name.png", renderCurrentFrame(this, 360, 800))
                }
                fun openSettingsCategory(category: SettingsCategory) {
                    runOnIdle {
                        nav.detailStack.clear()
                        nav.push(DetailRoute.SettingsCategoryPage(category))
                    }
                    mainClock.advanceTimeBy(600)
                }

                runOnIdle {
                    nav.selectTab(AppTab.LIBRARY)
                    fixture.library.openLikedSongs()
                }
                mainClock.advanceTimeBy(600)
                onNodeWithText("Liked Songs").assertIsDisplayed()
                captureAudit("21-03-51-liked-songs")

                runOnIdle { fixture.library.selectTab(LibraryTab.DOWNLOADS) }
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear all").assertIsDisplayed()
                onNodeWithText("On this device").assertIsDisplayed()
                if (fixture.library.storageSummary.value.device != null) {
                    onNodeWithContentDescription("Total device storage", substring = true).assertIsDisplayed()
                }
                captureAudit("21-04-04-downloads")
                onNodeWithText("Clear all").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear all downloads?").assertIsDisplayed()
                onNodeWithText(
                    "This deletes the only downloaded track from this device. Offline playback will no longer work for it.",
                ).assertIsDisplayed()
                captureAudit("21-04-32-clear-downloads-confirm")
                onNodeWithText("Cancel").performClick()
                mainClock.advanceTimeBy(300)

                runOnIdle { fixture.library.selectTab(LibraryTab.HISTORY) }
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear all").assertIsDisplayed()
                captureAudit("21-04-02-history")
                onNodeWithText("Clear all").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear history?").assertIsDisplayed()
                captureAudit("21-04-38-clear-library-history-confirm")
                onNodeWithText("Cancel").performClick()
                mainClock.advanceTimeBy(300)

                openSettingsCategory(SettingsCategory.ABOUT)
                onNodeWithText("Reset settings to defaults").performScrollTo()
                onNodeWithText("Reset settings to defaults").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Reset settings?").assertIsDisplayed()
                captureAudit("21-04-23-reset-settings-confirm")
                onNodeWithText("Cancel").performClick()

                openSettingsCategory(SettingsCategory.LYRICS)
                onNodeWithText("Clear lyrics cache").performScrollTo()
                onNodeWithText("Clear lyrics cache").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear lyrics cache?").assertIsDisplayed()
                captureAudit("21-05-23-clear-lyrics-cache-confirm")
                onNodeWithText("Cancel").performClick()

                openSettingsCategory(SettingsCategory.PRIVACY)
                onNodeWithText("Clear search history").performScrollTo()
                onNodeWithText("Clear search history").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear search history?").assertIsDisplayed()
                captureAudit("21-05-44-clear-search-history-confirm")
                onNodeWithText("Cancel").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear play history").performScrollTo()
                onNodeWithText("Clear play history").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear play history?").assertIsDisplayed()
                captureAudit("21-05-47-clear-play-history-confirm")
                onNodeWithText("Cancel").performClick()

                openSettingsCategory(SettingsCategory.STORAGE)
                onNodeWithText("Clear audio cache").performScrollTo()
                onNodeWithText("Clear audio cache").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Clear audio cache?").assertIsDisplayed()
                captureAudit("21-06-07-clear-audio-cache-confirm")
                onNodeWithText("Cancel").performClick()
                mainClock.advanceTimeBy(300)

                // Leave the non-destructive country picker open for its capture;
                // the fixture is disposed immediately after this final state.
                openSettingsCategory(SettingsCategory.CONTENT)
                onNodeWithText("United States (US)").performClick()
                mainClock.advanceTimeBy(300)
                onNodeWithText("Choose a country or region").assertIsDisplayed()
                captureAudit("21-05-35-country-region-picker")
            }
        }
    }

    @Test
    fun `lyrics controls dialog remains usable at narrow large text size`() {
        withFixture(DhunThemeMode.DARK, withLyricsMatches = true) { fixture ->
            val nav = AppNavState().apply { playerExpanded = true }
            runSkikoComposeUiTest(size = Size(360f, 640f)) {
                mainClock.autoAdvance = false
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.25f)) {
                        Shell(fixture, nav, DhunThemeMode.DARK, isDesktop = false)
                    }
                }
                mainClock.advanceTimeBy(600)
                onNodeWithContentDescription("Lyrics view").performClick()
                mainClock.advanceTimeBy(500)
                onNodeWithContentDescription("Lyrics controls").performClick()
                mainClock.advanceTimeBy(500)

                onNodeWithText("Lyrics controls").assertIsDisplayed()
                onNodeWithContentDescription("Lyrics provider: Auto (synced first)").assertIsDisplayed()
                onNodeWithContentDescription("Lyrics offset").assertIsDisplayed()
                onNodeWithText("Close").performScrollTo()
                onNodeWithText("Close").assertIsDisplayed()
                captureUiImage(
                    "lyrics-controls-compact-large-text-dark.png",
                    renderCurrentFrame(this, 360, 640),
                )
            }
        }
    }

    @Test
    fun `confirmation dialog guards pending actions and preserves retry callbacks`() {
        val pending = mutableStateOf(true)
        val confirms = AtomicInteger()
        val dismissals = AtomicInteger()

        runSkikoComposeUiTest(size = Size(412f, 915f)) {
            mainClock.autoAdvance = false
            setContent {
                DhunTheme(mode = DhunThemeMode.DARK) {
                    Box(Modifier.fillMaxSize().background(DhunColors.background))
                    DhunConfirmDialog(
                        title = "Delete playlist?",
                        message = "This removes the playlist from the local library.",
                        confirmLabel = "Delete",
                        retryLabel = "Retry deletion",
                        pendingLabel = "Deleting…",
                        isPending = pending.value,
                        errorMessage = "Could not delete the playlist. Try again.",
                        onConfirm = { confirms.incrementAndGet() },
                        onDismissRequest = { dismissals.incrementAndGet() },
                    )
                }
            }
            mainClock.advanceTimeBy(500)
            onNodeWithText("Cancel").assertIsNotEnabled()
            onNodeWithText("Deleting…").assertIsNotEnabled()

            runOnIdle { pending.value = false }
            mainClock.advanceTimeBy(100)
            onNodeWithText("Cancel").performClick()
            onNodeWithText("Retry deletion").performClick()
            assertEquals(1, dismissals.get(), "Cancel should keep its original dismissal callback")
            assertEquals(1, confirms.get(), "Retry should call the original mutation callback once")
        }
    }

    @Test
    fun `render confirmations at large text scale in both themes`() {
        DhunAppearance.reset()
        try {
            for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
                DhunAppearance.setAppearance(nextMode = mode)
                scene(width = 360, height = 800, fontScale = 1.3f) {
                    DhunTheme(mode = mode) {
                        Box(Modifier.fillMaxSize().background(DhunColors.background))
                        DhunConfirmDialog(
                            title = "Clear lyrics cache?",
                            message = "Automatic lyrics will be fetched again. Saved per-song choices remain; use Use automatic in the lyrics match picker to reset one.",
                            confirmLabel = "Clear",
                            pendingLabel = "Clearing…",
                            onConfirm = {},
                            onDismissRequest = {},
                        )
                    }
                }.use { composeScene ->
                    SceneDriver(composeScene).capture("confirm-dialog-large-text-${mode.id}.png", settleFrames = 24)
                }
            }
        } finally {
            DhunAppearance.reset()
        }
    }

    @Test
    fun `component catalog title yields room for its close action at phone width`() {
        DhunAppearance.reset()
        val closeCount = AtomicInteger()
        try {
            runSkikoComposeUiTest(size = Size(360f, 800f)) {
                mainClock.autoAdvance = false
                setContent {
                    ComponentCatalogScreen(onClose = { closeCount.incrementAndGet() })
                }
                mainClock.advanceTimeBy(500)
                onNodeWithText("Close").assertIsDisplayed()
                val closeLabelBounds = onNodeWithText("Close", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    closeLabelBounds.left >= 0f && closeLabelBounds.right <= 360f,
                    "The catalogue's Close label must stay inside the phone viewport: $closeLabelBounds",
                )
                assertTrue(
                    closeLabelBounds.height <= 24f,
                    "The catalogue's Close label must remain on one line at phone width: $closeLabelBounds",
                )
                captureUiImage(
                    "component-catalog-header-phone-dark.png",
                    renderCurrentFrame(this, 360, 800),
                )
                onNodeWithText("Close").performClick()
                assertEquals(1, closeCount.get())
            }
        } finally {
            DhunAppearance.reset()
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
        // A no-art reference makes the controlled black/white artwork frames
        // deterministic even when CI has no access to the remote thumbnail host.
        captureSingleState("app-home-no-art-light.png", DhunThemeMode.LIGHT, blurPercent = 0, artworkUrl = "")
    }

    @Test
    fun `home scroll trigger re-arms after a failed continuation without leaving the end`() {
        var attempts = 0
        val provider = RecordedProvider(
            firstContinuationToken = "next-page",
            continuationResult = {
                attempts++
                if (attempts <= 2) {
                    DhunResult.Failure(DhunError.Network("recorded transient continuation failure $attempts"))
                } else {
                    DhunResult.Success(
                        HomeFeedPage(
                            sections = listOf(
                                HomeSection(
                                    "Recovered after retry",
                                    items = listOf(HomeItem.TrackItem(RECORDED_TRACKS.last())),
                                ),
                            ),
                            continuationToken = null,
                        ),
                    )
                }
            },
        )
        withFixture(DhunThemeMode.DARK, provider) { fixture ->
            scene(width = 412, height = 915) {
                DhunTheme(mode = DhunThemeMode.DARK) {
                    Box(Modifier.fillMaxSize().background(DhunColors.background))
                    HomeScreen(viewModel = fixture.home, onTrackClick = { _, _, _ -> })
                }
            }.use { composeScene ->
                val driver = SceneDriver(composeScene)
                driver.settle(32)
                // Drive the real LazyColumn to its end. Further input while it
                // remains there must not be needed to re-arm pagination after
                // either of the first two continuation attempts returns an error.
                var scrolls = 0
                while (scrolls < 8 && provider.continuationRequests.size < 3) {
                    driver.scroll(200f, 800f, 500f)
                    scrolls++
                }
                val deadline = System.nanoTime() + 8_000_000_000L
                while (provider.continuationRequests.size < 3 && System.nanoTime() < deadline) {
                    driver.settle(2)
                }
                driver.capture("app-home-pagination-recovered-dark.png", settleFrames = 12)
                assertEquals(
                    listOf("next-page", "next-page", "next-page"),
                    provider.continuationRequests.toList(),
                    "Two transient errors should consume the bounded self-heal budget, then recover on the third page request",
                )
                assertNull(fixture.home.loadMoreError.value, "the recovered continuation must clear the footer error")
                val feed = (fixture.home.uiState.value as? HomeUiState.Success)?.feed
                assertTrue(feed?.sections?.any { it.title == "Recovered after retry" } == true)
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
        fontScale: Float = 1f,
        destination: (AppNavState) -> Unit = {},
    ) {
        withFixture(mode, provider, online) { fixture ->
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
            scene(width = 412, height = 915, fontScale = fontScale) {
                Shell(fixture, nav, mode, isDesktop = false)
            }.use { composeScene ->
                SceneDriver(composeScene).capture(name, settleFrames = 24)
            }
        }
    }

    @Composable
    private fun PreRedesignBatteryPlaybackRationale(onDismiss: () -> Unit, onAllow: () -> Unit) {
        GlassDialog(onDismissRequest = onDismiss) {
            Text(
                "Keep playback reliable",
                style = MaterialTheme.typography.titleMedium,
                color = DhunColors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(DhunSpacing.sm))
            Text(
                "Android battery optimization can stop background music " +
                    "on some phones. Allow DHUN to keep the playback service " +
                    "available when the screen is off?",
                style = MaterialTheme.typography.bodyMedium,
                color = DhunColors.textSecondary,
            )
            Spacer(modifier = Modifier.height(DhunSpacing.lg))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DhunTextButton(onClick = onDismiss) { Text("Not now") }
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                DhunTextButton(onClick = onAllow) { Text("Allow") }
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
                downloadManager = fixture.downloadManager,
                appInfo = DhunAppInfo.Unknown,
                clearAudioCache = { 2_700_000L },
            )
        }
    }

    private fun seedSavedLyricsChoice(fixture: AppFixture) {
        val track = requireNotNull(fixture.playerVm.currentTrack.value)
        val controller = requireNotNull(fixture.playerVm.lyricsMatches)
        controller.search(track.id, "${track.artistName} ${track.title}")
        val searchDeadline = System.nanoTime() + 5_000_000_000L
        while (controller.state.value.loading && System.nanoTime() < searchDeadline) Thread.sleep(20)
        val matches = controller.state.value.matches
        assertTrue(matches.isNotEmpty(), "The deterministic lyrics fixture should return a saved-choice candidate")
        val selected = matches.firstOrNull { it.provider == "lrclib" } ?: matches.first()
        controller.choose(track.id, selected)
        val saveDeadline = System.nanoTime() + 5_000_000_000L
        while (controller.state.value.loading && System.nanoTime() < saveDeadline) Thread.sleep(20)
        assertEquals(selected, controller.state.value.selected, "The in-memory saved choice should be durable before capture")
    }

    private fun withFixture(
        mode: DhunThemeMode,
        provider: RecordedProvider = RecordedProvider(),
        online: Boolean = true,
        withLyricsMatches: Boolean = false,
        includeDownloads: Boolean = false,
        includeHistoryAuditData: Boolean = false,
        block: (AppFixture) -> Unit,
    ) {
        DhunAppearance.reset()
        DhunAppearance.setAppearance(nextMode = mode)
        val fixture = AppFixture(provider, online, withLyricsMatches, includeDownloads, includeHistoryAuditData)
        try {
            runBlocking { fixture.data.settings.putString(SettingsKeys.THEME, mode.id) }
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
        withLyricsMatches: Boolean = false,
        includeDownloads: Boolean = false,
        includeHistoryAuditData: Boolean = false,
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
        val downloadManager: DownloadManager? = if (includeDownloads) {
            EvidenceDownloadManager(
                listOf(
                    DownloadedTrack(
                        trackId = RECORDED_TRACKS.first().id,
                        title = RECORDED_TRACKS.first().title,
                        artistName = RECORDED_TRACKS.first().artistName,
                        albumName = RECORDED_TRACKS.first().albumName,
                        durationSeconds = RECORDED_TRACKS.first().durationSeconds ?: 0,
                        thumbnailUrl = RECORDED_TRACKS.first().thumbnailUrl,
                        localAudioPath = "/tmp/visual-evidence/download.webm",
                        fileSizeBytes = 2_700_000L,
                        mimeType = "audio/webm",
                        bitrateKbps = 160,
                        downloadState = DownloadState.COMPLETED,
                        downloadedAtEpochMs = System.currentTimeMillis(),
                    ),
                ),
            )
        } else {
            null
        }

        init {
            localPlaylistId = runBlocking {
                data.library.addFavorite(RECORDED_TRACKS[1])
                data.history.recordPlay(RECORDED_TRACKS[0], PlayContext.HOME)
                data.history.recordPlay(RECORDED_TRACKS[1], PlayContext.SEARCH)
                val playlist = data.playlists.create("Visual QA", "In-memory acceptance fixture")
                RECORDED_TRACKS.take(4).forEach { data.playlists.addTrack(playlist.id, it) }
                playlist.id
            }
            if (includeHistoryAuditData) {
                runBlocking {
                    data.history.clear()
                    repeat(8) { index ->
                        data.history.recordPlay(RECORDED_TRACKS[index % RECORDED_TRACKS.size], PlayContext.QUEUE)
                        Thread.sleep(3)
                    }
                }
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
            val lyricsRepository = if (withLyricsMatches) {
                val source = object : LyricsSource {
                    override suspend fun fetch(track: Track): DhunResult<Lyrics> = provider.getLyrics(track.id)
                }
                LyricsRepository(
                    cache = data.lyricsCache,
                    ytm = source,
                    lrcLib = source,
                    settings = data.settings,
                )
            } else {
                null
            }
            playerVm = PlayerViewModel(
                player,
                provider,
                scope,
                lyricsRepository = lyricsRepository,
                settings = data.settings,
            )
            library = LibraryViewModel(data, player, scope, downloadManager = downloadManager)
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
            await("Lyrics populated") { playerVm.lyricsState.value is LyricsUiState.Synced }
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

    private class EvidenceDownloadManager(initial: List<DownloadedTrack>) : DownloadManager {
        private val rows = MutableStateFlow(initial)
        override val downloads: StateFlow<List<DownloadedTrack>> = rows

        override fun observeProgress(trackId: String): Flow<DownloadProgress?> = flowOf(null)
        override suspend fun enqueue(track: Track) = Unit
        override suspend fun enqueueAll(tracks: List<Track>) = Unit
        override suspend fun pause(trackId: String) = Unit
        override suspend fun resume(trackId: String) = Unit
        override suspend fun cancel(trackId: String) = Unit
        override suspend fun remove(trackId: String) {
            rows.value = rows.value.filterNot { it.trackId == trackId }
        }
        override suspend fun clearAll() {
            rows.value = emptyList()
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
        val retryRequests = AtomicInteger()

        override fun retry() {
            retryRequests.incrementAndGet()
        }

        fun useArtwork(url: String) {
            val updated = currentTrack.value!!.copy(thumbnailUrl = url)
            currentTrack.value = updated
            queue.value = queue.value.map { it.copy(thumbnailUrl = url) }
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
        private val firstContinuationToken: String? = null,
        private val continuationResult: suspend (String) -> DhunResult<HomeFeedPage> = {
            DhunResult.Success(HomeFeedPage())
        },
    ) : MusicProvider {
        val continuationRequests = CopyOnWriteArrayList<String>()
        val lyricsFetchRequests = AtomicInteger()
        @Volatile var lyricsGate: CompletableDeferred<Unit>? = null
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
                    continuationToken = firstContinuationToken,
                ),
            )
            StateMode.EMPTY -> DhunResult.Success(HomeFeedPage())
            StateMode.ERROR -> DhunResult.Failure(DhunError.Network("Recorded Home failure"))
            StateMode.LOADING -> {
                CompletableDeferred<Unit>().await()
                error("Unreachable loading result")
            }
        }

        override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<HomeFeedPage> {
            continuationRequests += continuationToken
            return continuationResult(continuationToken)
        }

        override suspend fun homeFeed(): DhunResult<List<HomeSection>> =
            when (val result = homeFeedPage()) {
                is DhunResult.Success -> DhunResult.Success(result.value.sections)
                is DhunResult.Failure -> result
            }

        override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> =
            DhunResult.Success(RECORDED_TRACKS.drop(1))

        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> =
            DhunResult.Failure(DhunError.Unavailable("Visual evidence does not resolve audio"))

        override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> {
            lyricsFetchRequests.incrementAndGet()
            lyricsGate?.await()
            return DhunResult.Success(
                Lyrics.Synced(
                    listOf(
                        LyricsLine(0, "First line"),
                        LyricsLine(30_000, "Second line"),
                        LyricsLine(60_000, "Third line"),
                        LyricsLine(90_000, "Last line"),
                    ),
                ),
            )
        }

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
