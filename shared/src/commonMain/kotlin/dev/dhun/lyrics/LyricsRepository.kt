package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import dev.dhun.data.LyricsCacheRepository

/**
 * Phase 11 repository — the single lyrics entry point the UI calls.
 *
 * Order per PROMPT_SEQUENCE.md:
 *  1. `cache` (SQLDelight `LyricsCache`) — instant, no network.
 *  2. `YTM` (`YouTubeLyricsSource` — InnerTube browse, unsynced text).
 *  3. `LRCLIB` (`LrcLibSource` — synced LRC, title+artist+duration match).
 *  4. `NotAvailable`.
 *
 * Successful `Synced`/`Unsynced` results are cached (so the second open
 * is instant). `NotAvailable` and `Failure` are not cached — the next
 * open retries the network sources (LRCLIB may have gained the track).
 *
 * This repository is intentionally `Track`-keyed (not `videoId`-only)
 * because LRCLIB matching needs title/artist/album/duration.
 */
class LyricsRepository(
    private val cache: LyricsCacheRepository,
    private val ytm: YouTubeLyricsSource,
    private val lrcLib: LrcLibSource,
) {
    /**
     * @return `DhunResult.Success(Lyrics.Synced/Unsynced/NotAvailable)` — never
     *         `Failure` for the UI's purposes; network failures from the
     *         sources are mapped to `NotAvailable` after both sources are tried.
     *         The UI still shows `LyricsUiState.Error` only when the repository
     *         itself fails to read the cache (disk I/O) — which is rare.
     */
    suspend fun getLyrics(track: Track): DhunResult<Lyrics> {
        // 1. Cache
        try {
            val cached = cache.get(track.id)
            if (cached != null && cached != Lyrics.NotAvailable) {
                return DhunResult.Success(cached)
            }
        } catch (_: Exception) {
            // Cache read failure — fall through to network; don't block lyrics
        }

        // 2. YTM
        when (val ytmResult = ytm.fetch(track)) {
            is DhunResult.Success -> {
                val ytmLyrics = ytmResult.value
                if (ytmLyrics is Lyrics.Synced || ytmLyrics is Lyrics.Unsynced) {
                    // YTM rarely returns Synced, but if it does we cache it
                    runCatching { cache.put(track.id, ytmLyrics) }
                    return DhunResult.Success(ytmLyrics)
                }
                // NotAvailable or empty — try LRCLIB
            }
            is DhunResult.Failure -> {
                // YTM failure (network/parse) — try LRCLIB instead of surfacing error
            }
        }

        // 3. LRCLIB
        when (val lrcResult = lrcLib.fetch(track)) {
            is DhunResult.Success -> {
                val lrcLyrics = lrcResult.value
                when (lrcLyrics) {
                    is Lyrics.Synced, is Lyrics.Unsynced -> {
                        runCatching { cache.put(track.id, lrcLyrics) }
                        return DhunResult.Success(lrcLyrics)
                    }
                    is Lyrics.NotAvailable -> {
                        // fall through to NotAvailable
                    }
                }
            }
            is DhunResult.Failure -> {
                // LRCLIB failure — will return NotAvailable
            }
        }

        // 4. NotAvailable
        return DhunResult.Success(Lyrics.NotAvailable)
    }

    /** For tests and “clear cache” diagnostics. */
    suspend fun clearCache() = cache.clear()

    /** Direct cache read without network (for “second open = instant” verification). */
    suspend fun cached(trackId: String): Lyrics? = cache.get(trackId)
}
