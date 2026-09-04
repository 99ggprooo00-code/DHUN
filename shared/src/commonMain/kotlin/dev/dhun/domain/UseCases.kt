package dev.dhun.domain

import dev.dhun.core.DhunResult
import dev.dhun.core.HistoryEntry
import dev.dhun.core.HomeFeed
import dev.dhun.core.HomeSection
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.EpochClock
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.NowPlayingRepository
import dev.dhun.data.NowPlayingSnapshot
import dev.dhun.data.PlayContext
import dev.dhun.data.PlaylistRepository
import dev.dhun.data.SearchRepository
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.flow.Flow

/**
 * Phase 05 + Phase 07 use cases: the verbs the application layer (view models) calls.
 * Thin on purpose — business rules live here, storage details never do.
 */

/* ---------------- home feed ---------------- */

class GetHomeFeedUseCase(
    private val provider: MusicProvider,
    private val history: HistoryRepository,
    private val clock: EpochClock = EpochClock.System,
) {
    suspend operator fun invoke(): DhunResult<HomeFeed> {
        val greeting = greetingForCurrentTime(clock)
        return when (val r = provider.homeFeed()) {
            is DhunResult.Success -> {
                val sections = r.value
                val quickPicks = extractQuickPicks(sections)
                DhunResult.Success(
                    HomeFeed(
                        greeting = greeting,
                        quickPicks = quickPicks,
                        sections = sections,
                    )
                )
            }
            is DhunResult.Failure -> {
                DhunResult.Failure(r.error)
            }
        }
    }

    private fun extractQuickPicks(sections: List<HomeSection>): List<Track> {
        val quickSection = sections.firstOrNull { it.title.contains("quick", ignoreCase = true) }
        val tracks = quickSection?.tracks?.takeIf { it.isNotEmpty() }
            ?: sections.flatMap { it.tracks }
        return tracks.distinctBy { it.id }.take(6)
    }

    companion object {
        fun greetingForHour(hour24: Int): String = when (hour24) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }

        fun greetingForCurrentTime(clock: EpochClock = EpochClock.System): String {
            val epochMs = clock.nowMs()
            val totalHours = (epochMs / 3_600_000L)
            val utcHour = ((totalHours % 24) + 24) % 24
            return greetingForHour(utcHour.toInt())
        }
    }
}

/* ---------------- favorites ---------------- */

class ToggleFavoriteUseCase(private val library: LibraryRepository) {
    /** @return the new favorite state. */
    suspend operator fun invoke(track: Track): Boolean {
        return if (library.isFavorite(track.id)) {
            library.removeFavorite(track.id); false
        } else {
            library.addFavorite(track); true
        }
    }
}

class ObserveFavoritesUseCase(private val library: LibraryRepository) {
    operator fun invoke(): Flow<List<Track>> = library.observeFavorites()
    fun ids(): Flow<Set<String>> = library.observeFavoriteIds()
    fun isFavorite(trackId: String): Flow<Boolean> = library.observeIsFavorite(trackId)
}

/* ---------------- playlists ---------------- */

class CreatePlaylistUseCase(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(name: String, description: String? = null): LocalPlaylist =
        playlists.create(name, description)
}

class RenamePlaylistUseCase(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(id: String, newName: String) {
        require(newName.isNotBlank()) { "Playlist name cannot be blank" }
        playlists.rename(id, newName)
    }
}

class DeletePlaylistUseCase(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(id: String) = playlists.delete(id)
}

class AddToPlaylistUseCase(private val playlists: PlaylistRepository) {
    /** @return true if added, false if it was already there. */
    suspend operator fun invoke(playlistId: String, track: Track): Boolean = playlists.addTrack(playlistId, track)

    /** Bulk add (e.g. "add album to playlist"). @return number actually added. */
    suspend operator fun invoke(playlistId: String, tracks: List<Track>): Int =
        tracks.count { playlists.addTrack(playlistId, it) }
}

class RemoveFromPlaylistUseCase(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(playlistId: String, trackId: String) = playlists.removeTrack(playlistId, trackId)
}

class ReorderPlaylistUseCase(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(playlistId: String, from: Int, to: Int) = playlists.move(playlistId, from, to)
}

class ObservePlaylistsUseCase(private val playlists: PlaylistRepository) {
    operator fun invoke(): Flow<List<LocalPlaylist>> = playlists.observePlaylists()
    fun one(id: String): Flow<LocalPlaylist?> = playlists.observePlaylist(id)
    fun tracks(id: String): Flow<List<Track>> = playlists.observeTracks(id)
}

/* ---------------- history ---------------- */

class RecordPlayUseCase(private val history: HistoryRepository) {
    /** Returns a handle the player calls when the track finishes naturally. */
    suspend operator fun invoke(track: Track, context: PlayContext): PlayHandle {
        val playedAt = history.recordPlay(track, context)
        return PlayHandle(track.id, playedAt)
    }

    suspend fun complete(handle: PlayHandle) = history.markCompleted(handle.trackId, handle.playedAtEpochMs)

    data class PlayHandle(val trackId: String, val playedAtEpochMs: Long)
}

class GetRecentlyPlayedUseCase(private val history: HistoryRepository) {
    operator fun invoke(limit: Int = 20): Flow<List<Track>> = history.observeRecentlyPlayed(limit)
}

/** History grouped by local calendar day for the Library → History screen. */
data class HistoryDay(val dayStartEpochMs: Long, val entries: List<HistoryEntry>)

class GetHistoryUseCase(private val history: HistoryRepository) {
    operator fun invoke(limit: Int = 200): Flow<List<HistoryEntry>> = history.observeHistory(limit)

    /**
     * Groups entries by day using [utcOffsetMs] (caller passes the device
     * zone offset — commonMain has no timezone API without kotlinx-datetime).
     */
    fun groupByDay(entries: List<HistoryEntry>, utcOffsetMs: Long): List<HistoryDay> =
        entries.groupBy { dayStart(it.playedAtEpochMs, utcOffsetMs) }
            .map { (day, list) -> HistoryDay(day, list) }
            .sortedByDescending { it.dayStartEpochMs }

    suspend fun remove(entryId: Long) = history.remove(entryId)
    suspend fun clear() = history.clear()

    private fun dayStart(epochMs: Long, utcOffsetMs: Long): Long {
        val local = epochMs + utcOffsetMs
        val dayMs = 86_400_000L
        val floored = local - floorMod(local, dayMs)
        return floored - utcOffsetMs
    }

    private fun floorMod(a: Long, b: Long): Long = ((a % b) + b) % b
}

/* ---------------- settings ---------------- */

class GetSettingUseCase(private val settings: SettingsRepository) {
    suspend fun string(key: String, default: String): String = settings.getString(key) ?: default
    suspend fun boolean(key: String, default: Boolean): Boolean = settings.getBoolean(key, default)
    suspend fun int(key: String, default: Int): Int = settings.getInt(key, default)
    fun observe(key: String): Flow<String?> = settings.observeString(key)

    suspend fun audioQuality(): String = string(SettingsKeys.AUDIO_QUALITY, SettingsKeys.AUDIO_QUALITY_DEFAULT)
    suspend fun countryCode(): String = string(SettingsKeys.COUNTRY_CODE, SettingsKeys.COUNTRY_CODE_DEFAULT)
    suspend fun resumeOnLaunch(): Boolean = boolean(SettingsKeys.RESUME_ON_LAUNCH, SettingsKeys.RESUME_ON_LAUNCH_DEFAULT)
}

class UpdateSettingUseCase(private val settings: SettingsRepository) {
    suspend fun string(key: String, value: String) {
        require(key in SettingsKeys.all) { "Unknown setting key: $key" }
        settings.putString(key, value)
    }
    suspend fun boolean(key: String, value: Boolean) {
        require(key in SettingsKeys.all) { "Unknown setting key: $key" }
        settings.putBoolean(key, value)
    }
    suspend fun int(key: String, value: Int) {
        require(key in SettingsKeys.all) { "Unknown setting key: $key" }
        settings.putInt(key, value)
    }
}

/* ---------------- recent searches ---------------- */

class RecentSearchesUseCase(private val search: SearchRepository) {
    fun observe(limit: Int = 10): Flow<List<String>> = search.observeRecentSearches(limit)
    suspend fun record(query: String) = search.recordSearch(query)
    suspend fun remove(query: String) = search.removeSearch(query)
    suspend fun clear() = search.clearRecentSearches()
}

/* ---------------- now-playing persistence ---------------- */

class SaveNowPlayingUseCase(private val nowPlaying: NowPlayingRepository) {
    suspend operator fun invoke(
        queue: List<Track>, currentIndex: Int, positionMs: Long,
        repeatMode: RepeatMode = RepeatMode.OFF, shuffle: Boolean = false,
    ) = nowPlaying.saveQueue(queue, currentIndex, positionMs, repeatMode, shuffle)

    suspend fun progress(currentIndex: Int, positionMs: Long) = nowPlaying.updateProgress(currentIndex, positionMs)
    suspend fun clear() = nowPlaying.clear()
}

class RestoreNowPlayingUseCase(
    private val nowPlaying: NowPlayingRepository,
    private val settings: SettingsRepository,
) {
    /** null when nothing to restore or the user disabled resume. */
    suspend operator fun invoke(): NowPlayingSnapshot? {
        if (!settings.getBoolean(SettingsKeys.RESUME_ON_LAUNCH, SettingsKeys.RESUME_ON_LAUNCH_DEFAULT)) return null
        return nowPlaying.load()
    }
}
