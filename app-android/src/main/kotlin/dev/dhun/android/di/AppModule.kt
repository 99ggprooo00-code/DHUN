package dev.dhun.android.di

import dev.dhun.android.download.AndroidDownloadStorage
import dev.dhun.android.playback.DhunAudioSegmentCache
import dev.dhun.android.playback.DhunStreamCache
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SettingsKeys
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadRepository
import dev.dhun.download.DownloadStorage
import dev.dhun.download.FileDownloadManager
import dev.dhun.download.KtorStreamDownloader
import dev.dhun.download.createDownloadHttpClient
import dev.dhun.extraction.OfflineFirstStreamResolver
import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.StreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.player.AudioCacheBudget
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.lyrics.LrcLibSource
import dev.dhun.lyrics.LyricsRepository
import dev.dhun.lyrics.YouTubeLyricsSource
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Android DI graph (Koin). Per ADR-001 (+ 2026-09-02 addendum) the Android
 * stream chain is the own-client resolver only — WEB_REMIX /player, then
 * VISIONOS, then TVHTML5 — no yt-dlp on Android.
 */
val appModule = module {
    single { InnerTubeClient() }
    single<StreamResolver> { OwnClientStreamResolver(get()) }
    // ADR-006: offline-first playback — a COMPLETED persistent download
    // resolves to its local file; otherwise resolve over the network chain.
    single<MusicProvider> {
        YouTubeMusicProvider(
            get(),
            OfflineFirstStreamResolver(
                downloads = get<DownloadRepository>(),
                primary = get<StreamResolver>(),
                fileExists = { path -> runCatching { File(path).exists() }.getOrDefault(false) },
            ),
        )
    }
    single { DhunStreamCache(get()) }
    // Phase 14: connectivity signal for the shared offline banner.
    single<dev.dhun.core.ConnectivityMonitor> {
        dev.dhun.core.AndroidConnectivityMonitor(androidContext())
    }

    // Phase 05 data layer: one SQLite database, repositories + use cases.
    single { DataLayer(DatabaseFactory.create(DatabaseDriverFactory(androidContext()).createDriver())) }
    // ADR-006: persistent offline download store (COMPLETED rows drive
    // offline-first playback in PlaybackGraph and the MusicProvider).
    single<DownloadRepository> { get<DataLayer>().downloads }
    // ADR-006: download manager backed by internal storage; a bounded worker
    // pool over the network chain (never the offline-first wrapper — a
    // not-yet-downloaded file must resolve over the network).
    single<DownloadStorage> { AndroidDownloadStorage(androidContext()) }
    // Concrete FileDownloadManager — preserved as its own singleton so
    // tests and direct callers still see the real engine (the
    // ForegroundServiceDownloadManager below delegates to it). DO NOT
    // remove: the shared engine's contract is this implementation.
    single {
        FileDownloadManager(
            repository = get<DownloadRepository>(),
            resolver = get<StreamResolver>(),
            downloader = KtorStreamDownloader(createDownloadHttpClient(), get()),
            storage = get(),
            scope = get(),
        )
    }
    // Android-side decorator: kicks off DhunDownloadService on every
    // enqueue so downloads survive app backgrounding / OEM killers.
    // All other operations are pure pass-throughs.
    single<DownloadManager> {
        dev.dhun.android.download.ForegroundServiceDownloadManager(
            context = androidContext(),
            delegate = get(),
            controller = get(),
        )
    }
    single { dev.dhun.android.download.DownloadServiceController() }
    single { SaveNowPlayingUseCase(get<DataLayer>().nowPlaying) }
    single { RestoreNowPlayingUseCase(get<DataLayer>().nowPlaying, get<DataLayer>().settings) }
    single { RecordPlayUseCase(get<DataLayer>().history) }
    single { GetHomeFeedUseCase(get(), get<DataLayer>().history) }

    // Phase 14: bounded audio-segment cache (Media3 SimpleCache LRU).
    // Budget from SettingsKeys.CACHE_SIZE_MB (default 1 GiB). Changing the
    // setting takes effect on next process start (SimpleCache locks dir).
    single {
        val mb = try {
            runBlocking {
                get<DataLayer>().settings.getInt(
                    SettingsKeys.CACHE_SIZE_MB,
                    SettingsKeys.CACHE_SIZE_MB_DEFAULT,
                )
            }
        } catch (_: Throwable) {
            SettingsKeys.CACHE_SIZE_MB_DEFAULT
        }
        DhunAudioSegmentCache.get(
            androidContext(),
            AudioCacheBudget.bytesForMb(mb),
        )
    }

    // Phase 11 lyrics — cache → YTM → LRCLIB
    single { LrcLibSource() }
    single { YouTubeLyricsSource(get()) }
    single {
        val data: DataLayer = get()
        LyricsRepository(cache = data.lyricsCache, ytm = get(), lrcLib = get())
    }

    single { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

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

    viewModel { dev.dhun.android.ui.HarnessViewModel(get(), get()) }
}
