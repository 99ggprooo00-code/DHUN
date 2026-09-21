package dev.dhun.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/**
 * Android download transport (ADR-006).
 *
 * OkHttp — not CIO. Every maintained InnerTube-music app downloads over
 * OkHttp on Android (InnerTune's `OkHttpDataSource`, OuterTune, RiMusic,
 * ViMusic); CIO's long streaming reads are unproven on ART, and the shared
 * CIO client is the prime suspect behind downloads stalling on Android
 * while desktop (HotSpot) works. Desktop keeps CIO
 * ([createDownloadHttpClient]).
 *
 * Timeouts are connect + socket-idle only — never a request timeout, which
 * would abort large files mid-transfer. Without the idle timeout a
 * throttled stream hangs forever with no visible failure.
 */
fun createAndroidDownloadHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
}
