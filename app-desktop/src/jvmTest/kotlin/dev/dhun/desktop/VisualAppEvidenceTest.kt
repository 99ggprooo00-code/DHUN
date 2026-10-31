package dev.dhun.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
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
import dev.dhun.data.PlayContext
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
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DetailRoute
import dev.dhun.ui.shell.DhunAppShell
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
@OptIn(ExperimentalComposeUiApi::class)
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
                    driver.capture("app-home-${mode.id}.png", settleFrames = 40)
                    nav.selectTab(AppTab.SEARCH)
                    driver.capture("app-search-${mode.id}.png")
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
                    driver.capture("app-player-${mode.id}.png", settleFrames = 24)
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
                scene(width = 412, height = 915) {
                    DhunTheme(mode = mode) {
                        Box(Modifier.fillMaxSize().background(DhunColors.background))
                        AddToPlaylistDialog(
                            track = RECORDED_TRACKS.first(),
                            playlistRepository = fixture.data.playlists,
                            onDismiss = {},
                            onAdded = {},
                            onOpenPlaylist = {},
                        )
                    }
                }.use { composeScene ->
                    SceneDriver(composeScene).capture("app-add-playlist-${mode.id}.png", settleFrames = 24)
                }
            }
        }
    }

    @Test
    fun `render wide desktop shell and detail navigation`() {
        withFixture(DhunThemeMode.DARK) { fixture ->
            val nav = AppNavState()
            scene(width = 1200, height = 780) {
                Shell(fixture, nav, DhunThemeMode.DARK, isDesktop = true)
            }.use { composeScene ->
                val driver = SceneDriver(composeScene)
                driver.capture("desktop-home-dark.png", settleFrames = 40)
                nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = true)
                driver.capture("desktop-search-dark.png")
                nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = true)
                driver.capture("desktop-library-dark.png")
                nav.push(DetailRoute.AlbumPage(ALBUM_ID))
                driver.capture("desktop-album-dark.png", settleFrames = 30)
                nav.playerExpanded = true
                driver.capture("desktop-player-dark.png", settleFrames = 30)
                assertEquals(RECORDED_TRACKS.first(), fixture.player.currentTrack.value)
                assertIs<PlaybackState.Playing>(fixture.player.state.value)
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
        captureSingleState("app-home-brightness-zero-light.png", DhunThemeMode.LIGHT, brightnessPercent = 0)
        captureSingleState("app-home-brightness-full-light.png", DhunThemeMode.LIGHT, brightnessPercent = 100)
        captureSingleState("app-home-oled.png", DhunThemeMode.BLACK)
        captureSingleState("app-home-large-text-dark.png", DhunThemeMode.DARK, fontScale = 1.3f)
        captureSingleState("app-settings-large-text-dark.png", DhunThemeMode.DARK, fontScale = 1.3f) {
            it.push(DetailRoute.SettingsPage)
        }
    }

    private fun captureSingleState(
        name: String,
        mode: DhunThemeMode,
        provider: RecordedProvider = RecordedProvider(),
        online: Boolean = true,
        blurPercent: Int? = null,
        brightnessPercent: Int? = null,
        fontScale: Float = 1f,
        destination: (AppNavState) -> Unit = {},
    ) {
        withFixture(mode, provider, online) { fixture ->
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
        block: (AppFixture) -> Unit,
    ) {
        DhunAppearance.reset()
        DhunAppearance.setAppearance(nextMode = mode)
        val fixture = AppFixture(provider, online)
        try {
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

    private inner class SceneDriver(private val scene: ImageComposeScene) {
        private var timeNanos = 0L

        fun settle(frames: Int = 12) {
            repeat(frames) {
                Thread.sleep(20)
                timeNanos += FRAME_NANOS
                scene.render(timeNanos).close()
            }
        }

        fun capture(name: String, settleFrames: Int = 12) {
            settle(settleFrames)
            scene.render(timeNanos).use { image ->
                image.encodeToData()!!.use { encoded ->
                    val target = File(output, name)
                    target.writeBytes(encoded.bytes)
                    assertTrue(target.length() > 1_000, "$name was empty or implausibly small")
                }
            }
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

        override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> = DhunResult.Success(
            Lyrics.Synced(
                listOf(
                    LyricsLine(0, "First line"),
                    LyricsLine(30_000, "Second line"),
                    LyricsLine(60_000, "Third line"),
                    LyricsLine(90_000, "Last line"),
                ),
            ),
        )

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
