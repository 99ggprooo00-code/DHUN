package dev.dhun.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.dhun.core.Lyrics
import dev.dhun.database.DhunDatabase
import dev.dhun.lyrics.LrcParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Persisted lyrics cache (Phase 11) — lives in the Phase 05 DB
 * (`dhun.db`) alongside history/favorites. Keyed by `Track.id`
 * (YouTube videoId). Stores either the raw LRC (synced) or plain
 * text (unsynced). `NotAvailable` is not cached (negative caching
 * would hide future availability).
 *
 * Content for `Synced` is the raw LRC string (e.g. `[00:12.34]Hello`);
 * it is re-parsed on read so the parser stays the single source of truth.
 */
interface LyricsCacheRepository {
    suspend fun get(trackId: String): Lyrics?
    suspend fun put(trackId: String, lyrics: Lyrics)
    suspend fun clear()
    fun observe(trackId: String): Flow<Lyrics?>
}

class SqlDelightLyricsCacheRepository(
    private val db: DhunDatabase,
    private val clock: EpochClock = EpochClock.System,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : LyricsCacheRepository {

    override suspend fun get(trackId: String): Lyrics? = withContext(io) {
        val row = db.lyricsCacheQueries.selectByTrackId(trackId).executeAsOneOrNull() ?: return@withContext null
        rowToLyrics(row)
    }

    override suspend fun put(trackId: String, lyrics: Lyrics): Unit = withContext(io) {
        val now = clock.nowMs()
        when (lyrics) {
            is Lyrics.Synced -> {
                // Retain enhanced word markers and full millisecond precision.
                // Old line-only cache entries remain readable without a migration.
                val lrc = LrcParser.serialize(lyrics.lines)
                db.lyricsCacheQueries.insert(trackId, true, lrc, now)
            }
            is Lyrics.Unsynced -> {
                db.lyricsCacheQueries.insert(trackId, false, lyrics.text, now)
            }
            is Lyrics.NotAvailable -> {
                // Do not cache negative result — future fetches may succeed
                // (LRCLIB may gain the track, YTM may start serving lyrics).
            }
        }
    }

    override suspend fun clear(): Unit = withContext(io) {
        db.lyricsCacheQueries.clear()
    }

    override fun observe(trackId: String): Flow<Lyrics?> =
        db.lyricsCacheQueries.selectByTrackId(trackId).asFlow().mapToOneOrNull(io).map { row ->
            row?.let { rowToLyrics(it) }
        }

    private fun rowToLyrics(row: dev.dhun.database.LyricsCache): Lyrics {
        return if (row.isSynced) {
            val lines = LrcParser.parse(row.content, allowUnsyncedFallback = false)
            if (lines.isEmpty()) Lyrics.Unsynced(row.content) else Lyrics.Synced(lines)
        } else {
            if (row.content.isBlank()) Lyrics.NotAvailable else Lyrics.Unsynced(row.content)
        }
    }

}
