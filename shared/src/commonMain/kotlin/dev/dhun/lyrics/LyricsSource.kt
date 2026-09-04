package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track

/**
 * Abstraction over a timed-lyrics backend (Phase 11).
 *
 * v1 sources: YTM via InnerTube browse (unsynced text) and LRCLIB
 * (synced LRC). The [LyricsRepository] layers them as
 * `cache → YTM → LRCLIB → NotAvailable`.
 */
interface LyricsSource {
    suspend fun fetch(track: Track): DhunResult<Lyrics>
}
