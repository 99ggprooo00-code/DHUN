package dev.dhun.android.playback

import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.provider.MusicProvider

/**
 * Transparent [MusicProvider] decorator that mirrors every `getStreamInfo`
 * outcome into a [ResolveOutcomeLog] — the playback-diagnostics seam
 * (2026-09-09 "stuck buffering" investigation).
 *
 * Wrapping happens in `AppModule.kt` (the single allowed DI edit), so BOTH
 * playback paths (MediaSessionService and the session-less fallback, which
 * share `DhunStreamCache` → this provider) and ADR-005 prefetch all record
 * outcomes with zero engine-contract changes: `PlaybackGraph`,
 * `DhunStreamCache`, `DhunPlaybackService` and `DhunAudioSegmentCache` are
 * untouched. Everything except `getStreamInfo` is forwarded verbatim by
 * Kotlin class delegation.
 */
class ResolveObservingMusicProvider(
    private val delegate: MusicProvider,
    private val outcomes: ResolveOutcomeLog = ResolveOutcomeLog.global,
) : MusicProvider by delegate {

    override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> {
        val result = delegate.getStreamInfo(videoId)
        outcomes.record(
            videoId,
            (result as? DhunResult.Failure)?.error,
        )
        return result
    }
}
