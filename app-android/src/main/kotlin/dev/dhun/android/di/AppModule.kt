package dev.dhun.android.di

import dev.dhun.android.playback.DhunStreamCache
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.StreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.presentation.home.HomeViewModel
import dev.dhun.presentation.search.SearchViewModel
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    single<MusicProvider> { YouTubeMusicProvider(get(), get()) }
    single { DhunStreamCache(get()) }

    // Phase 05 data layer: one SQLite database, repositories + use cases.
    single { DataLayer(DatabaseFactory.create(DatabaseDriverFactory(androidContext()).createDriver())) }
    single { SaveNowPlayingUseCase(get<DataLayer>().nowPlaying) }
    single { RestoreNowPlayingUseCase(get<DataLayer>().nowPlaying, get<DataLayer>().settings) }
    single { RecordPlayUseCase(get<DataLayer>().history) }
    single { GetHomeFeedUseCase(get(), get<DataLayer>().history) }

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
