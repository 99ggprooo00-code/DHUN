package dev.dhun.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

/**
 * Android HTTP transport. OkHttp — not CIO.
 *
 * Every maintained InnerTube-music app uses OkHttp on Android (InnerTune's
 * `OkHttpDataSource`, OuterTune, RiMusic, ViMusic). CIO's NIO selector and
 * long streaming reads are unproven on ART; the shared CIO client is also
 * constructed during playback-service `onCreate` (InnerTube via
 * `DhunStreamCache`), so a CIO init failure on API < 31 is process
 * death — the activity never gets to its MediaController fallback.
 * Desktop keeps CIO (`createDownloadHttpClient` /
 * `InnerTubeClient.defaultHttpClient`).
 */

/**
 * Metadata / InnerTube / LRCLIB. Request timeout matches
 * [InnerTubeClient.defaultHttpClient] so a hung browse cannot stall the
 * UI forever; downloads use [createAndroidDownloadHttpClient] instead.
 */
fun createAndroidMetadataHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 25_000
        socketTimeoutMillis = 25_000
    }
}

/**
 * ADR-006 file downloads. Connect + socket-idle only — never a request
 * timeout, which would abort large files mid-transfer. Without the idle
 * timeout a throttled stream hangs forever with no visible failure.
 */
fun createAndroidDownloadHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 60_000
    }
}
