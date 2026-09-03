package dev.dhun.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.dhun.core.HistoryEntry
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.database.DhunDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.random.Random
import dev.dhun.database.Track as TrackRow

/* ---------------- mappers ------------------------------------------------ */

internal fun TrackRow.toDomain(): Track = Track(
    id = id,
    title = title,
    artistName = artistName,
    artistId = artistId,
    albumName = albumName,
    albumId = albumId,
    durationSeconds = durationSeconds?.toInt(),
    thumbnailUrl = thumbnailUrl,
    explicit = explicit,
)

/** Must run inside a transaction (all callers do). */
internal fun DhunDatabase.upsertTrack(track: Track, now: Long) {
    trackQueries.insertIgnore(
        id = track.id,
        title = track.title,
        artistName = track.artistName,
        artistId = track.artistId,
        albumName = track.albumName,
        albumId = track.albumId,
        durationSeconds = track.durationSeconds?.toLong(),
        thumbnailUrl = track.thumbnailUrl,
        explicit = track.explicit,
        updatedAt = now,
    )
    trackQueries.update(
        title = track.title,
        artistName = track.artistName,
        artistId = track.artistId,
        albumName = track.albumName,
        albumId = track.albumId,
        durationSeconds = track.durationSeconds?.toLong(),
        thumbnailUrl = track.thumbnailUrl,
        explicit = track.explicit,
        updatedAt = now,
        id = track.id,
    )
}

/* ---------------- Track --------------------------------------------------- */

class SqlDelightTrackRepository(
    private val db: DhunDatabase,
    private val clock: EpochClock = EpochClock.System,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : TrackRepository {
    override suspend fun get(id: String): Track? = withContext(io) {
        db.trackQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun save(track: Track): Unit = withContext(io) {
        val now = clock.nowMs()
        db.transaction { db.upsertTrack(track, now) }
    }

    override suspend fun saveAll(tracks: List<Track>): Unit = withContext(io) {
        val now = clock.nowMs()
        db.transaction { tracks.forEach { db.upsertTrack(it, now) } }
    }

    override suspend fun delete(id: String): Unit = withContext(io) { db.trackQueries.deleteById(id) }

    override fun observe(id: String): Flow<Track?> =
        db.trackQueries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.toDomain() }
}

/* ---------------- Library (favorites) ------------------------------------ */

class SqlDelightLibraryRepository(
    private val db: DhunDatabase,
    private val clock: EpochClock = EpochClock.System,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : LibraryRepository {
    override fun observeFavorites(): Flow<List<Track>> =
        db.favoriteQueries.selectAllTracks().asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        db.favoriteQueries.selectIds().asFlow().mapToList(io).map { it.toSet() }

    override fun observeIsFavorite(trackId: String): Flow<Boolean> =
        db.favoriteQueries.isFavorite(trackId).asFlow().mapToOneOrNull(io).map { (it ?: 0L) > 0L }

    override suspend fun isFavorite(trackId: String): Boolean = withContext(io) {
        db.favoriteQueries.isFavorite(trackId).executeAsOne() > 0L
    }

    override suspend fun addFavorite(track: Track): Unit = withContext(io) {
        val now = clock.nowMs()
        db.transaction {
            db.upsertTrack(track, now)
            db.favoriteQueries.insert(track.id, now)
        }
    }

    override suspend fun removeFavorite(trackId: String): Unit = withContext(io) { db.favoriteQueries.delete(trackId) }
}

/* ---------------- Playlists ---------------------------------------------- */

class SqlDelightPlaylistRepository(
    private val db: DhunDatabase,
    private val clock: EpochClock = EpochClock.System,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val idGenerator: () -> String = { generatePlaylistId() },
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<LocalPlaylist>> =
        db.playlistQueries.selectAll().asFlow().mapToList(io).map { rows ->
            rows.map { LocalPlaylist(it.id, it.name, it.description, it.thumbnailUrl, it.trackCount.toInt(), it.createdAt, it.updatedAt) }
        }

    override fun observePlaylist(id: String): Flow<LocalPlaylist?> =
        db.playlistQueries.selectById(id).asFlow().mapToOneOrNull(io).map { row ->
            row?.let { LocalPlaylist(it.id, it.name, it.description, it.thumbnailUrl, it.trackCount.toInt(), it.createdAt, it.updatedAt) }
        }

    override fun observeTracks(playlistId: String): Flow<List<Track>> =
        db.playlistQueries.selectTracks(playlistId).asFlow().mapToList(io).map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(name: String, description: String?): LocalPlaylist = withContext(io) {
        val now = clock.nowMs()
        val id = idGenerator()
        val cleanName = name.trim().ifEmpty { "New playlist" }.take(MAX_NAME)
        db.playlistQueries.insert(
            id = id, name = cleanName, description = description?.trim()?.takeIf { it.isNotEmpty() },
            thumbnailUrl = null, isLocal = true, youtubePlaylistId = null, createdAt = now, updatedAt = now,
        )
        LocalPlaylist(id, cleanName, description, null, 0, now, now)
    }

    override suspend fun rename(id: String, newName: String): Unit = withContext(io) {
        db.playlistQueries.rename(newName.trim().take(MAX_NAME), clock.nowMs(), id)
    }

    override suspend fun delete(id: String): Unit = withContext(io) {
        db.transaction {
            db.playlistQueries.deleteAllTracks(id) // explicit: not every driver enforces FK cascade
            db.playlistQueries.delete(id)
        }
    }

    override suspend fun addTrack(playlistId: String, track: Track): Boolean = withContext(io) {
        val now = clock.nowMs()
        db.transactionWithResult {
            if (db.playlistQueries.containsTrack(playlistId, track.id).executeAsOne() > 0L) {
                false
            } else {
                db.upsertTrack(track, now)
                val next = (db.playlistQueries.maxPosition(playlistId).executeAsOneOrNull()?.MAX ?: -1L) + 1
                db.playlistQueries.insertTrack(playlistId, track.id, next, now)
                db.playlistQueries.touch(now, playlistId)
                true
            }
        }
    }

    override suspend fun removeTrack(playlistId: String, trackId: String): Unit = withContext(io) {
        db.transaction {
            db.playlistQueries.deleteTrack(playlistId, trackId)
            renumberLocked(playlistId)
            db.playlistQueries.touch(clock.nowMs(), playlistId)
        }
    }

    override suspend fun move(playlistId: String, fromIndex: Int, toIndex: Int): Unit = withContext(io) {
        db.transaction {
            val ids = db.playlistQueries.selectTrackIdsOrdered(playlistId).executeAsList().toMutableList()
            if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return@transaction
            val moved = ids.removeAt(fromIndex)
            ids.add(toIndex, moved)
            ids.forEachIndexed { index, id -> db.playlistQueries.updatePosition(index.toLong(), playlistId, id) }
            db.playlistQueries.touch(clock.nowMs(), playlistId)
        }
    }

    override suspend fun contains(playlistId: String, trackId: String): Boolean = withContext(io) {
        db.playlistQueries.containsTrack(playlistId, trackId).executeAsOne() > 0L
    }

    /** Keep positions dense 0..n-1 so [move] index math stays trivial. */
    private fun renumberLocked(playlistId: String) {
        db.playlistQueries.selectTrackIdsOrdered(playlistId).executeAsList()
            .forEachIndexed { index, id -> db.playlistQueries.updatePosition(index.toLong(), playlistId, id) }
    }

    private companion object {
        const val MAX_NAME = 120
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        fun generatePlaylistId(): String =
            "local_" + (1..16).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}

/* ---------------- History ------------------------------------------------ */

class SqlDelightHistoryRepository(
    private val db: DhunDatabase,
    private val clock: EpochClock = EpochClock.System,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : HistoryRepository {

    override suspend fun recordPlay(track: Track, context: PlayContext): Long = withContext(io) {
        val now = clock.nowMs()
        db.transaction {
            db.upsertTrack(track, now)
            db.historyQueries.insert(track.id, now, context.name)
        }
        now
    }

    override suspend fun markCompleted(trackId: String, playedAtEpochMs: Long): Unit = withContext(io) {
        db.historyQueries.markCompleted(trackId, playedAtEpochMs)
    }

    override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> =
        db.historyQueries.selectRecent(limit.toLong()).asFlow().mapToList(io).map { rows ->
            rows.map { r ->
                HistoryEntry(
                    track = Track(
                        id = r.id, title = r.title, artistName = r.artistName, artistId = r.artistId,
                        albumName = r.albumName, albumId = r.albumId, durationSeconds = r.durationSeconds?.toInt(),
                        thumbnailUrl = r.thumbnailUrl, explicit = r.explicit,
                    ),
                    playedAtEpochMs = r.playedAt,
                    playedFromContext = r.playedFromContext,
                    completedPlayback = r.completedPlayback,
                    entryId = r.entryId,
                )
            }
        }

    override fun observeRecentlyPlayed(limit: Int): Flow<List<Track>> =
        db.historyQueries.selectRecentlyPlayedTracks(limit.toLong()).asFlow().mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun playCount(trackId: String): Long = withContext(io) {
        db.historyQueries.playCount(trackId).executeAsOne()
    }

    override suspend fun remove(entryId: Long): Unit = withContext(io) { db.historyQueries.deleteById(entryId) }

    override suspend fun clear(): Unit = withContext(io) { db.historyQueries.deleteAll() }
}

/* ---------------- Settings ----------------------------------------------- */

class SqlDelightSettingsRepository(
    private val db: DhunDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : SettingsRepository {
    override suspend fun getString(key: String): String? = withContext(io) {
        db.settingsQueries.get(key).executeAsOneOrNull()
    }

    override suspend fun putString(key: String, value: String): Unit = withContext(io) { db.settingsQueries.put(key, value) }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    override suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    override suspend fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default

    override suspend fun putInt(key: String, value: Int) = putString(key, value.toString())

    override suspend fun remove(key: String): Unit = withContext(io) { db.settingsQueries.delete(key) }

    override fun observeString(key: String): Flow<String?> =
        db.settingsQueries.get(key).asFlow().mapToOneOrNull(io)
}

/* ---------------- Recent searches --------------------------------------- */

class SqlDelightSearchRepository(
    private val db: DhunDatabase,
    private val clock: EpochClock = EpochClock.System,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val maxEntries: Int = 50,
) : SearchRepository {
    override fun observeRecentSearches(limit: Int): Flow<List<String>> =
        db.recentSearchQueries.selectRecent(limit.toLong()).asFlow().mapToList(io)

    override suspend fun recordSearch(query: String): Unit = withContext(io) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext
        db.transaction {
            db.recentSearchQueries.upsert(q, clock.nowMs())
            db.recentSearchQueries.trimTo(maxEntries.toLong())
        }
    }

    override suspend fun removeSearch(query: String): Unit = withContext(io) { db.recentSearchQueries.delete(query.trim()) }

    override suspend fun clearRecentSearches(): Unit = withContext(io) { db.recentSearchQueries.deleteAll() }
}

/* ---------------- Now playing persistence ------------------------------- */

class SqlDelightNowPlayingRepository(
    private val db: DhunDatabase,
    private val clock: EpochClock = EpochClock.System,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : NowPlayingRepository {

    override suspend fun saveQueue(
        queue: List<Track>, currentIndex: Int, positionMs: Long, repeatMode: RepeatMode, shuffle: Boolean,
    ): Unit = withContext(io) {
        val now = clock.nowMs()
        db.transaction {
            db.nowPlayingQueries.clearQueue()
            if (queue.isEmpty()) {
                db.nowPlayingQueries.clearState()
                return@transaction
            }
            queue.forEachIndexed { index, track ->
                db.upsertTrack(track, now)
                db.nowPlayingQueries.insertQueueItem(index.toLong(), track.id)
            }
            db.nowPlayingQueries.upsertState(
                currentIndex = currentIndex.coerceIn(0, queue.lastIndex).toLong(),
                positionMs = positionMs.coerceAtLeast(0),
                repeatMode = repeatMode.name,
                shuffle = shuffle,
                savedAt = now,
            )
        }
    }

    override suspend fun updateProgress(currentIndex: Int, positionMs: Long): Unit = withContext(io) {
        db.nowPlayingQueries.updatePosition(positionMs.coerceAtLeast(0), currentIndex.toLong(), clock.nowMs())
    }

    override suspend fun load(): NowPlayingSnapshot? = withContext(io) {
        val state = db.nowPlayingQueries.selectState().executeAsOneOrNull() ?: return@withContext null
        val queue = db.nowPlayingQueries.selectQueueTracks().executeAsList().map { it.toDomain() }
        if (queue.isEmpty()) return@withContext null
        NowPlayingSnapshot(
            queue = queue,
            currentIndex = state.currentIndex.toInt().coerceIn(0, queue.lastIndex),
            positionMs = state.positionMs,
            repeatMode = runCatching { RepeatMode.valueOf(state.repeatMode) }.getOrDefault(RepeatMode.OFF),
            shuffle = state.shuffle,
            savedAtEpochMs = state.savedAt,
        )
    }

    override suspend fun clear(): Unit = withContext(io) {
        db.transaction {
            db.nowPlayingQueries.clearQueue()
            db.nowPlayingQueries.clearState()
        }
    }
}
