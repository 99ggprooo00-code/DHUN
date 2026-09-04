package dev.dhun.android.di

import dev.dhun.android.playback.DhunStreamCache
import dev.dhun.android.ui.MainActivity
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.domain.RecordPlayUseCase
import dev.dhun.domain.RestoreNowPlayingUseCase
import dev.dhun.domain.SaveNowPlayingUseCase
import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.StreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.player.DhunPlayer
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
import dev.dhun.ui.home.HomeViewModel
import dev.dhun.ui.home.HomeViewModelFactory
import dev.dhun.ui.search.SearchViewModel
import dev.dhun.ui.search.SearchViewModelFactory
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Android DI graph (Koin). Per ADR-001 (+ 2026-09-02 addendum) the Android
 * stream chain is the own-client resolver only — WEB_REMIX /player, then
 * VISIONOS, then TVHTML5 — no yt-dlp on Android. The desktop factory is the
 * failover variant; when every client identity is gated on a user's network
 * the player surfaces a typed, human message carrying per-client evidence.
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

    // Phase 07: ViewModels
    factoryOf(::HomeViewModelFactory)
    factoryOf(::SearchViewModelFactory)

    viewModel { dev.dhun.android.ui.HarnessViewModel(get(), get()) }
}
