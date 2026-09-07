package dev.dhun.android.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.dhun.android.download.DownloadServiceController
import dev.dhun.android.download.ForegroundServiceDownloadManager
import dev.dhun.android.playback.DhunAudioSegmentCache
import dev.dhun.android.playback.DhunStreamCache
import dev.dhun.android.ui.HarnessViewModel
import dev.dhun.core.ConnectivityMonitor
import dev.dhun.data.DataLayer
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadRepository
import dev.dhun.download.DownloadStorage
import dev.dhun.download.FileDownloadManager
import dev.dhun.extraction.StreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.lyrics.LrcLibSource
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.lyrics.YouTubeLyricsSource
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Resolves EVERY definition of the real Android `appModule` under
 * Robolectric — the "C1" regression class from the ADR-006 integration:
 *
 *   `single<DownloadManager> { ForegroundServiceDownloadManager(
 *       delegate = get() /* inferred DownloadManager = ITSELF */ ) }`
 *
 * recursed at FIRST RESOLUTION (Koin caches singletons after construction).
 * The app crashed at launch while ALL CI checks stayed green, because
 * `:app-android:assembleDebug` is a type-check gate and nothing ever
 * resolved the graph. Constructing the whole graph here turns any
 * self-recursion / missing link into a test failure instead of a launch
 * crash. (The coordinator logged this exact test as a CI follow-up.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppModuleGraphTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        // Robolectric boots the real DhunApp (which starts Koin); leave a
        // clean global registry behind for any test that runs after this one.
        runCatching { GlobalContext.stopKoin() }
    }

    @Test
    fun `every appModule definition resolves without self-recursion or a missing link`() {
        runCatching { GlobalContext.stopKoin() } // DhunApp.onCreate may have started it
        startKoin {
            androidContext(app)
            modules(appModule)
        }
        val koin = GlobalContext.get()

        // Provider / metadata chain
        koin.get<InnerTubeClient>()
        koin.get<StreamResolver>()
        koin.get<MusicProvider>()

        // Playback caches + connectivity
        koin.get<DhunStreamCache>()
        koin.get<ConnectivityMonitor>()
        koin.get<DhunAudioSegmentCache>()

        // Data layer + ADR-006 download stack — the decorated DownloadManager
        // single is the C1 regression itself. If the delegate ever resolves
        // the decorator again, this line overflows instead of the app
        // crashing at launch.
        koin.get<DataLayer>()
        koin.get<DownloadRepository>()
        koin.get<DownloadStorage>()
        koin.get<FileDownloadManager>()
        val downloadManager = koin.get<DownloadManager>()
        assertTrue(
            "DownloadManager must be the FGS decorator, not the bare engine",
            downloadManager is ForegroundServiceDownloadManager,
        )
        koin.get<DownloadServiceController>()

        // Use cases
        koin.get<SaveNowPlayingUseCase>()
        koin.get<RestoreNowPlayingUseCase>()
        koin.get<RecordPlayUseCase>()
        koin.get<GetHomeFeedUseCase>()

        // Lyrics stack
        koin.get<LrcLibSource>()
        koin.get<YouTubeLyricsSource>()
        koin.get<LyricsRepository>()

        // Scope + view models
        koin.get<CoroutineScope>()
        koin.get<HomeViewModel>()
        koin.get<SearchViewModel>()
        koin.get<HarnessViewModel>()
    }
}
