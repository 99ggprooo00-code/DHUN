package dev.dhun.data

import dev.dhun.core.HistoryEntry
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import kotlinx.coroutines.flow.Flow

/**
 * Phase 05 data layer contracts. Flows out, suspend in. The domain and UI
 * layers see ONLY these interfaces — never SQLDelight queries or SQL.
 */

interface TrackRepository {
    suspend fun get(id: String): Track?
    suspend fun save(track: Track)
    suspend fun saveAll(tracks: List<Track>)
    suspend fun delete(id: String)
    fun observe(id: String): Flow<Track?>
}

interface LibraryRepository {
    fun observeFavorites(): Flow<List<Track>>
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeIsFavorite(trackId: String): Flow<Boolean>
    suspend fun isFavorite(trackId: String): Boolean
    /** Saves the track row too, so a favorite never dangles. */
    suspend fun addFavorite(track: Track)
    suspend fun removeFavorite(trackId: String)
}

/** A local (DHUN-owned) playlist. YouTube playlists are browsed live, not stored. */
data class LocalPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<LocalPlaylist>>
    fun observePlaylist(id: String): Flow<LocalPlaylist?>
    fun observeTracks(playlistId: String): Flow<List<Track>>
    suspend fun create(name: String, description: String? = null): LocalPlaylist
    suspend fun rename(id: String, newName: String)
    suspend fun delete(id: String)
    /** Appends; no-op if the track is already in the playlist. Saves the track row. */
    suspend fun addTrack(playlistId: String, track: Track): Boolean
    suspend fun removeTrack(playlistId: String, trackId: String)
    suspend fun move(playlistId: String, fromIndex: Int, toIndex: Int)
    suspend fun contains(playlistId: String, trackId: String): Boolean
}

/** Where a play was started from — analytics-free, purely for "Listen again" UX. */
enum class PlayContext { SEARCH, HOME, ARTIST, ALBUM, PLAYLIST, QUEUE, LIBRARY, HISTORY, RESTORED, UNKNOWN }

interface HistoryRepository {
    /** @return playedAt timestamp used for the row (pass to [markCompleted]). */
    suspend fun recordPlay(track: Track, context: PlayContext): Long
    suspend fun markCompleted(trackId: String, playedAtEpochMs: Long)
    fun observeHistory(limit: Int): Flow<List<HistoryEntry>>
    fun observeRecentlyPlayed(limit: Int): Flow<List<Track>>
    suspend fun playCount(trackId: String): Long
    suspend fun remove(entryId: Long)
    suspend fun clear()
}

interface SettingsRepository {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun getBoolean(key: String, default: Boolean): Boolean
    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun getInt(key: String, default: Int): Int
    suspend fun putInt(key: String, value: Int)
    suspend fun remove(key: String)
    fun observeString(key: String): Flow<String?>
}

interface SearchRepository {
    fun observeRecentSearches(limit: Int = 20): Flow<List<String>>
    suspend fun recordSearch(query: String)
    suspend fun removeSearch(query: String)
    suspend fun clearRecentSearches()
}

/** Snapshot of the player restored on cold start. */
data class NowPlayingSnapshot(
    val queue: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: RepeatMode,
    val shuffle: Boolean,
    val savedAtEpochMs: Long,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
}

interface NowPlayingRepository {
    suspend fun saveQueue(queue: List<Track>, currentIndex: Int, positionMs: Long, repeatMode: RepeatMode, shuffle: Boolean)
    /** Cheap, frequent update (every few seconds) without rewriting the queue. */
    suspend fun updateProgress(currentIndex: Int, positionMs: Long)
    suspend fun load(): NowPlayingSnapshot?
    suspend fun clear()
}
