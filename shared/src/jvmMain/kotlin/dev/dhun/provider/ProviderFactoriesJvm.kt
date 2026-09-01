package dev.dhun.provider

import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.ResolvingStreamResolver
import dev.dhun.extraction.YtDlpStreamResolver
import dev.dhun.innertube.InnerTubeClient

/**
 * Desktop wiring (ADR-001): own client primary (cheap, works when YouTube
 * is not gating), yt-dlp failover (the proven engine from hostile networks).
 * The rot drill measures each engine independently.
 */
fun YouTubeMusicProvider.Companion.forDesktop(country: String = "US"): YouTubeMusicProvider {
    val client = InnerTubeClient(country = country)
    val resolver = ResolvingStreamResolver(
        primary = OwnClientStreamResolver(client),
        fallback = YtDlpStreamResolver(),
    )
    return YouTubeMusicProvider(client, resolver)
}
