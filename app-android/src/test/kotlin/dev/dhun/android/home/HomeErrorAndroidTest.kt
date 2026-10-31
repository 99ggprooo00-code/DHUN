package dev.dhun.android.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.GetRecommendationsUseCase
import dev.dhun.innertube.SearchFilter
import dev.dhun.presentation.home.HomeUiState
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** Runs the shared failure path with Android SQLite and Android's real Log facade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class HomeErrorAndroidTest {
    @Test
    fun parseDetailReachesHomeStateAndSanitizedAndroidLog(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DatabaseFactory.FILE_NAME)
        val driver = DatabaseDriverFactory(context).createDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val data = DataLayer(DatabaseFactory.create(driver))
            val provider = FailingHomeProvider
            val viewModel = HomeViewModel(
                getHomeFeed = GetHomeFeedUseCase(provider, data.history),
                getRecommendations = GetRecommendationsUseCase(provider, data.history, data.library),
                historyRepository = data.history,
                libraryRepository = data.library,
                scope = scope,
                errorLogger = { Log.e("DHUN", it) },
            )

            val state = withTimeout(10_000) {
                viewModel.uiState.first { it is HomeUiState.Error }
            } as HomeUiState.Error
            assertEquals("HTTP 400 from /browse; <url>", state.detail)

            val homeLines = ShadowLog.getLogsForTag("DHUN")
                .map { it.msg }
                .filter { it.startsWith("Home load failed") }
            assertEquals(1, homeLines.size)
            assertTrue(homeLines.single().contains("Home load failed (Parse): HTTP 400 from /browse"))
            assertFalse(homeLines.single().contains("SECRET"))
        } finally {
            scope.cancel()
            driver.close()
            context.deleteDatabase(DatabaseFactory.FILE_NAME)
        }
    }

    private object FailingHomeProvider : MusicProvider {
        override suspend fun homeFeedPage(): DhunResult<HomeFeedPage> = DhunResult.Failure(
            DhunError.Parse("HTTP 400 from /browse; https://example.com/?signature=SECRET"),
        )

        override suspend fun homeFeed(): DhunResult<List<HomeSection>> = error("unused")
        override suspend fun homeFeedContinuation(continuationToken: String) = error("unused")
        override suspend fun search(query: String, filter: SearchFilter) = error("unused")
        override suspend fun searchContinuation(continuationToken: String) = error("unused")
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun relatedTracks(videoId: String) = DhunResult.Success(emptyList<Track>())
        override suspend fun getStreamInfo(videoId: String) = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun getLyrics(videoId: String) = DhunResult.Success(Lyrics.NotAvailable)
        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> = error("unused")
        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> = error("unused")
        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> = error("unused")
    }
}
