package dev.dhun.android.di

import dev.dhun.android.playback.DhunStreamCache
import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.StreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.provider.MusicProvider
import dev.dhun.provider.YouTubeMusicProvider
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Android DI graph (Koin). Per ADR-001 the Android stream chain is the
 * own-client resolver only — no yt-dlp on Android. The desktop factory is
 * the failover variant; if the own client is gated on a user's network the
 * player surfaces a typed, human message (AuthRequired).
 */
val appModule = module {
    single { InnerTubeClient() }
    single<StreamResolver> { OwnClientStreamResolver(get()) }
    single<MusicProvider> { YouTubeMusicProvider(get(), get()) }
    single { DhunStreamCache(get()) }
    viewModel { dev.dhun.android.ui.HarnessViewModel(get()) }
}
