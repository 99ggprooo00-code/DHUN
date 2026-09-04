package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import dev.dhun.provider.MusicProvider

/**
 * YTM lyrics via InnerTube `browse` (unsynced text only in practice).
 * Thin wrapper so the repository can treat it as a [LyricsSource].
 * This source is **videoId-dependent** — it calls `provider.getLyrics(track.id)`.
 */
class YouTubeLyricsSource(
    private val provider: MusicProvider,
) : LyricsSource {
    override suspend fun fetch(track: Track): DhunResult<Lyrics> =
        provider.getLyrics(track.id)
}
