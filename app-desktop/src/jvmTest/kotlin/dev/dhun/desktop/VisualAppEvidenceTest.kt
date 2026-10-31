package dev.dhun.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import dev.dhun.core.Album
import dev.dhun.core.AlbumDetail
import dev.dhun.core.Artist
import dev.dhun.core.ArtistPage
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
import dev.dhun.ui.shell.AppNavState
import dev.dhun.ui.shell.AppTab
import dev.dhun.ui.shell.DetailRoute
import dev.dhun.ui.shell.DhunAppShell
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
                    driver.capture("app-home-${mode.id}.png", settleFrames = 120)
                    nav.selectTab(AppTab.SEARCH)
                    driver.capture("app-search-${mode.id}.png")
                    nav.selectTab(AppTab.LIBRARY)
                    driver.capture("app-library-${mode.id}.png")
                    nav.push(DetailRoute.ArtistPage(QUEEN_ID))
                    driver.capture("app-artist-${mode.id}.png", settleFrames = 80)
                    nav.popDetail()
                    nav.push(DetailRoute.AlbumPage(ALBUM_ID))
                    driver.capture("app-album-${mode.id}.png", settleFrames = 80)
                    nav.popDetail()
                    nav.push(DetailRoute.PlaylistPage(REMOTE_PLAYLIST_ID))
                    driver.capture("app-playlist-${mode.id}.png", settleFrames = 80)
                    nav.popDetail()
                    nav.push(DetailRoute.SettingsPage)
                    driver.capture("app-settings-${mode.id}.png")
                    nav.push(DetailRoute.SettingsCategoryPage(SettingsCategory.APPEARANCE))
                    driver.capture("app-settings-appearance-${mode.id}.png")
                    nav.detailStack.clear()
                    nav.selectTab(AppTab.HOME)
                    nav.playerExpanded = true
                    driver.capture("app-player-${mode.id}.png", settleFrames = 80)
                    driver.click("Lyrics view")
                    driver.capture("app-player-lyrics-${mode.id}.png", settleFrames = 80)
                    driver.click("Exit lyrics view")
                    driver.click("Open queue")
                    driver.capture("app-player-queue-${mode.id}.png", settleFrames = 80)
                    driver.click("Related")
                    driver.capture("app-player-related-${mode.id}.png", settleFrames = 80)
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
                val nav = AppNavState().apply { selectTab(AppTab.SEARCH) }
                scene(width = 412, height = 915) {
                    Shell(fixture, nav, mode, isDesktop = false)
                }.use { composeScene ->
                    val driver = SceneDriver(composeScene)
                    driver.capture("app-dialog-source-${mode.id}.png", settleFrames = 120)
                    driver.click("More actions for ${RECORDED_TRACKS.first().title}")
                    driver.capture("app-track-menu-${mode.id}.png")
                    driver.click("Add to playlist")
                    driver.capture("app-add-playlist-${mode.id}.png", settleFrames = 80)
                    assertEquals(RECORDED_TRACKS.first(), fixture.player.currentTrack.value)
                    assertIs<PlaybackState.Playing>(fixture.player.state.value)
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
                driver.capture("desktop-home-dark.png", settleFrames = 120)
                nav.selectTab(AppTab.SEARCH, keepDetailOnTabChange = true)
                driver.capture("desktop-search-dark.png")
                nav.selectTab(AppTab.LIBRARY, keepDetailOnTabChange = true)
                driver.capture("desktop-library-dark.png")
                nav.push(DetailRoute.AlbumPage(ALBUM_ID))
                driver.capture("desktop-album-dark.png", settleFrames = 100)
                nav.playerExpanded = true
                driver.capture("desktop-player-dark.png", settleFrames = 100)
                assertEquals(RECORDED_TRACKS.first(), fixture.player.currentTrack.value)
                assertIs<PlaybackState.Playing>(fixture.player.state.value)
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
                appInfo = DhunAppInfo.Unknown,
            )
        }
    }

    private fun withFixture(mode: DhunThemeMode, block: (AppFixture) -> Unit) {
        DhunAppearance.reset()
        DhunAppearance.setAppearance(nextMode = mode)
        val fixture = AppFixture()
        try {
            block(fixture)
        } finally {
            fixture.close()
            DhunAppearance.reset()
        }
    }

    private fun scene(width: Int, height: Int, content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width = width, height = height, content = content)

    private inner class SceneDriver(private val scene: ImageComposeScene) {
        private var timeNanos = 0L

        fun settle(frames: Int = 45) {
            repeat(frames) {
                Thread.sleep(10)
                timeNanos += FRAME_NANOS
                scene.render(timeNanos).close()
            }
        }

        fun capture(name: String, settleFrames: Int = 45) {
            settle(settleFrames)
            scene.render(timeNanos).use { image ->
                image.encodeToData()!!.use { encoded ->
                    val target = File(output, name)
                    target.writeBytes(encoded.bytes)
                    assertTrue(target.length() > 1_000, "$name was empty or implausibly small")
                }
            }
        }

        fun click(label: String) {
            settle(4)
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val node = nodes.firstOrNull { candidate ->
                val text = candidate.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                val descriptions = candidate.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                (label in text || label in descriptions) &&
                    candidate.config.getOrNull(SemanticsActions.OnClick) != null
            } ?: fail(
                "No clickable semantics node '$label'. Available: " +
                    nodes.mapNotNull { candidate ->
                        val descriptions = candidate.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                        val text = candidate.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        (descriptions + text).firstOrNull()
                    }.distinct().take(50).joinToString(),
            )
            val action = assertNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action)
            assertTrue(action.invoke(), "Semantics click '$label' was rejected")
            settle(20)
        }

        private fun flatten(node: SemanticsNode): List<SemanticsNode> =
            listOf(node) + node.children.flatMap(::flatten)
    }

    private class AppFixture {
        private val driver = DatabaseDriverFactory.inMemory().createDriver()
        val data = DataLayer(DatabaseFactory.create(driver))
        val provider = RecordedProvider()
        val player = RecordedPlayer()
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
            await("Home populated") { home.uiState.value is HomeUiState.Success }
            await("Search populated") { search.resultsState.value is SearchResultsUiState.Success }
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

    private class RecordedProvider : MusicProvider {
        override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> =
            DhunResult.Success(
                SearchResults(
                    query = query,
                    songs = RECORDED_TRACKS,
                    artists = listOf(QUEEN),
                    albums = listOf(ALBUM),
                    playlists = listOf(REMOTE_PLAYLIST),
                ),
            )

        override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> =
            DhunResult.Success(SearchResults(""))

        override suspend fun searchSuggestions(query: String): DhunResult<List<String>> =
            DhunResult.Success(emptyList())

        override suspend fun homeFeedPage(): DhunResult<HomeFeedPage> = DhunResult.Success(
            HomeFeedPage(
                sections = listOf(
                    HomeSection("Quick picks", items = RECORDED_TRACKS.take(4).map { HomeItem.TrackItem(it) }),
                    HomeSection("Recommended albums & EPs", items = listOf(HomeItem.AlbumItem(ALBUM))),
                    HomeSection("Classic rock", items = listOf(HomeItem.PlaylistItem(REMOTE_PLAYLIST))),
                    HomeSection("Artists", items = listOf(HomeItem.ArtistItem(QUEEN))),
                ),
            ),
        )

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
        const val FRAME_NANOS = 16_666_667L
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
