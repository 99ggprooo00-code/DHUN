package dev.dhun.extraction

import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo

/**
 * Stream-URL resolution abstraction (ADR-001). Implementations:
 *  - OwnClientStreamResolver (common)   — InnerTube /player, no tokens
 *  - YtDlpStreamResolver     (jvm)      — subprocess, desktop
 *  - NewPipeStreamResolver   (jvm)      — drill-watched, currently broken upstream
 */
interface StreamResolver {
    val name: String
    suspend fun resolve(videoId: String): DhunResult<StreamInfo>
}

/**
 * Primary-with-failover resolver. If the primary fails, the fallback gets one
 * shot; on double failure the PRIMARY's error is reported (the more
 * meaningful one — the fallback is an insurance path, not the story).
 */
class ResolvingStreamResolver(
    private val primary: StreamResolver,
    private val fallback: StreamResolver? = null,
) : StreamResolver {
    override val name: String =
        if (fallback != null) "resolving(${primary.name} -> ${fallback.name})" else primary.name

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
        val primaryResult = primary.resolve(videoId)
        if (primaryResult is DhunResult.Success) return primaryResult
        if (fallback == null) return primaryResult
        return when (val fallbackResult = fallback.resolve(videoId)) {
            is DhunResult.Success -> fallbackResult
            // report the primary error: it is the configured engine
            is DhunResult.Failure -> primaryResult
        }
    }
}
