package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import dev.dhun.data.LyricsCacheRepository
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository

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
    private val settings: SettingsRepository? = null,
    private val search: LyricsSource? = null,
    private val kugou: LyricsSource? = null,
) {
    /**
     * @return `DhunResult.Success(Lyrics.Synced/Unsynced/NotAvailable)` — never
     *         `Failure` for the UI's purposes; network failures from the
     *         sources are mapped to `NotAvailable` after both sources are tried.
     *         The UI still shows `LyricsUiState.Error` only when the repository
     *         itself fails to read the cache (disk I/O) — which is rare.
     */
    suspend fun getLyrics(track: Track): DhunResult<Lyrics> {
        if (settings?.getBoolean(SettingsKeys.LYRICS_ENABLED, true) == false)
            return DhunResult.Success(Lyrics.NotAvailable)
        val preferred = settings?.getString(SettingsKeys.LYRICS_PREFERRED_PROVIDER)
            ?: SettingsKeys.LYRICS_PREFERRED_PROVIDER_DEFAULT
        val syncedFirst = settings?.getBoolean(SettingsKeys.LYRICS_PREFER_SYNCED, true) ?: false
        try {
            val cached = cache.get(track.id)
            if (cached != null && cached != Lyrics.NotAvailable) return DhunResult.Success(cached)
        } catch (_: Exception) { /* cache failure must not block network */ }

        val sources = mapOf("lrclib" to lrcLib, "ytm" to ytm, "lrclib_search" to search, "kugou" to kugou)
        val enabled = (settings?.getString(SettingsKeys.LYRICS_PROVIDER_ORDER)
            ?: if (settings == null) "ytm,lrclib" else SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT)
            .split(',').filter { sources[it] != null }.distinct()
        val order = if (preferred == "auto") enabled else {
            // An explicitly preferred source is the primary even when its
            // fallback toggle is off; the toggle controls whether it can be
            // retried as a later fallback for other selections.
            val selected = preferred.takeIf { sources[it] != null }
            (listOfNotNull(selected) + enabled).distinct()
        }
        var plain: Lyrics.Unsynced? = null
        for (id in order) {
            val result = try { sources[id]?.fetch(track) } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) { null }
            val lyrics = (result as? DhunResult.Success)?.value
            if (lyrics is Lyrics.Synced) {
                runCatching { cache.put(track.id, lyrics) }
                return DhunResult.Success(lyrics)
            }
            if (lyrics is Lyrics.Unsynced) {
                if (!syncedFirst || preferred != "auto") {
                    runCatching { cache.put(track.id, lyrics) }
                    return DhunResult.Success(lyrics)
                }
                if (plain == null) plain = lyrics
            }
        }
        plain?.let {
            runCatching { cache.put(track.id, it) }
            return DhunResult.Success(it)
        }
        return DhunResult.Success(Lyrics.NotAvailable)
    }

    /** For tests and “clear cache” diagnostics. */
    suspend fun clearCache() = cache.clear()

    /** Direct cache read without network (for “second open = instant” verification). */
    suspend fun cached(trackId: String): Lyrics? = cache.get(trackId)
}
