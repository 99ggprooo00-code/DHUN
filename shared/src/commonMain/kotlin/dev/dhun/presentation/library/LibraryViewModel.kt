package dev.dhun.presentation.library

import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.HistoryEntry
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlayContext
import dev.dhun.data.PlaylistRepository
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadProgress
import dev.dhun.domain.GetHistoryUseCase
import dev.dhun.domain.HistoryDay
import dev.dhun.player.DhunPlayer
import dev.dhun.player.NowPlayingPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Library & History screen model (Phase 10).
 *
 * Exposes three tabs (Playlists / Favorites / History) as [StateFlow]s
 * derived from the Phase 05 repositories. All persistence flows out,
 * suspend in — the UI never touches SQLDelight directly.
 *
 * History is grouped by calendar day via [GetHistoryUseCase.groupByDay].
 * commonMain has no timezone API, so the caller provides the device
 * zone offset (see [HistoryGroupingHelper.utcOffsetMs]).
 *
 * Playback goes through [DhunPlayer]; before every queue handoff the
 * optional [NowPlayingPersistence] is primed with the correct
 * [PlayContext] so `HistoryRepository.recordPlay` labels rows correctly
 * (SEARCH/HOME/ARTIST/ALBUM/PLAYLIST/LIBRARY/HISTORY).
 */

enum class LibraryTab {
    PLAYLISTS,
    DOWNLOADS,
    HISTORY,
    @Deprecated("Liked songs are now a dedicated folder inside Playlists", ReplaceWith("PLAYLISTS"))
    FAVORITES,
}

/**
 * One state bucket in the storage breakdown: how many downloads are in it and
 * how many bytes they occupy. Bytes are meaningful only for COMPLETED tracks
 * (partial `.part` files aren't row-tracked), so other buckets report 0 bytes
 * and are surfaced by count.
 */
data class DownloadGroup(
    val state: DownloadState,
    val count: Int,
    val bytes: Long,
)

/**
 * Aggregate picture of downloaded media and the volume it sits on, rendered by
 * the Downloads storage-management view.
 *
 * [device] is null when the platform can't report capacity (UI then shows only
 * "used by downloads"). [usedByDownloadsBytes] counts completed tracks;
 * [partialBytes] is the sum the in-progress rows know about so far (best
 * effort, from their recorded size).
 */
data class StorageSummary(
    val totalTracks: Int,
    val completedCount: Int,
    val usedByDownloadsBytes: Long,
    val partialBytes: Long,
    val groups: List<DownloadGroup>,
    val device: StorageSpace?,
) {
    val completedBytes: Long get() = usedByDownloadsBytes
    val freeBytes: Long? get() = device?.freeBytes
    val totalBytes: Long? get() = device?.totalBytes

    /** 0f..1f of the *device* volume used by everything; null when unknown. */
    val deviceUsedFraction: Float?
        get() = device?.let { if (it.totalBytes > 0) (it.usedBytes.toFloat() / it.totalBytes).coerceIn(0f, 1f) else null }

    /** 0f..1f of the *device* volume taken by downloads; null when unknown. */
    val downloadsFractionOfDevice: Float?
        get() = device?.let { if (it.totalBytes > 0) (usedByDownloadsBytes.toFloat() / it.totalBytes).coerceIn(0f, 1f) else null }

    fun group(state: DownloadState): DownloadGroup =
        groups.firstOrNull { it.state == state } ?: DownloadGroup(state, 0, 0L)

    companion object {
        fun empty(device: StorageSpace? = null): StorageSummary =
            StorageSummary(0, 0, 0L, 0L, emptyList(), device)

        fun from(rows: List<DownloadedTrack>, device: StorageSpace?): StorageSummary {
            if (rows.isEmpty()) return empty(device)
            val byState = DownloadState.entries.associateWith { state ->
                val inState = rows.filter { it.downloadState == state }
                DownloadGroup(
                    state = state,
                    count = inState.size,
                    bytes = inState.sumOf { it.fileSizeBytes },
                )
            }
            val completed = byState.getValue(DownloadState.COMPLETED)
            val partial = listOf(DownloadState.DOWNLOADING, DownloadState.QUEUED, DownloadState.PAUSED, DownloadState.FAILED)
                .sumOf { byState.getValue(it).bytes }
            return StorageSummary(
                totalTracks = rows.size,
                completedCount = completed.count,
                usedByDownloadsBytes = completed.bytes,
                partialBytes = partial,
                // Stable, meaningful ordering for the breakdown UI.
                groups = listOf(
                    DownloadState.COMPLETED,
                    DownloadState.DOWNLOADING,
                    DownloadState.QUEUED,
                    DownloadState.PAUSED,
                    DownloadState.FAILED,
                ).map { byState.getValue(it) },
                device = device,
            )
        }
    }
}

/** How often the storage card re-reads device free-space while visible. */
private const val STORAGE_REFRESH_INTERVAL_MS = 30_000L

/**
 * Platform helper to supply the device UTC offset without pulling
 * kotlinx-datetime into commonMain. The UI layer computes it on each
 * recomposition (cheap) and passes it to the ViewModel for grouping.
 */
expect fun currentUtcOffsetMs(): Long

class LibraryViewModel(
    private val playlists: PlaylistRepository,
    private val library: LibraryRepository,
    private val history: HistoryRepository,
    private val player: DhunPlayer,
    private val scope: CoroutineScope,
    private val persistence: NowPlayingPersistence? = null,
    private val setContext: ((PlayContext) -> Unit)? = null,
    private val downloadManager: DownloadManager? = null,
) {
    private fun setPlayContext(ctx: PlayContext) {
        if (setContext != null) { setContext.invoke(ctx); return }
        persistence?.setPlayContext(ctx)
    }
    // Convenience ctor from DataLayer
    constructor(
        dataLayer: DataLayer,
        player: DhunPlayer,
        scope: CoroutineScope,
        persistence: NowPlayingPersistence? = null,
        setContext: ((PlayContext) -> Unit)? = null,
        downloadManager: DownloadManager? = null,
    ) : this(
        playlists = dataLayer.playlists,
        library = dataLayer.library,
        history = dataLayer.history,
        player = player,
        scope = scope,
        persistence = persistence,
        setContext = setContext,
        downloadManager = downloadManager,
    )

    private val historyUseCase = GetHistoryUseCase(history)

    private val _selectedTab = MutableStateFlow(LibraryTab.PLAYLISTS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _viewingLikedSongs = MutableStateFlow(false)
    val viewingLikedSongs: StateFlow<Boolean> = _viewingLikedSongs.asStateFlow()

    fun selectTab(tab: LibraryTab) {
        if (tab == LibraryTab.FAVORITES) {
            _selectedTab.value = LibraryTab.PLAYLISTS
            _viewingLikedSongs.value = true
        } else {
            _selectedTab.value = tab
            _viewingLikedSongs.value = false
        }
    }

    fun openLikedSongs() {
        _selectedTab.value = LibraryTab.PLAYLISTS
        _viewingLikedSongs.value = true
    }

    fun closeLikedSongs() {
        _viewingLikedSongs.value = false
    }

    // ---- Playlists ----
    val playlistsFlow: StateFlow<List<LocalPlaylist>> =
        playlists.observePlaylists()
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ---- Favorites ----
    val favorites: StateFlow<List<Track>> =
        library.observeFavorites()
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<Set<String>> =
        library.observeFavoriteIds()
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    // ---- History (raw + grouped) ----
    val historyEntries: StateFlow<List<HistoryEntry>> =
        history.observeHistory(limit = 300)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Grouped by day — recomputed whenever historyEntries changes or
    // the UI pushes a new utcOffset. In practice the offset is stable
    // for a session; we expose a flow the UI can collect with its
    // current offset.
    private val _groupedHistory = MutableStateFlow<List<HistoryDay>>(emptyList())
    val groupedHistory: StateFlow<List<HistoryDay>> = _groupedHistory.asStateFlow()

    // Keep groupedHistory in sync with historyEntries + currentUtcOffsetMs()
    // We combine historyEntries with a ticker that the UI can trigger
    // by calling refreshHistoryGrouping().
    private val _offsetMs = MutableStateFlow(currentUtcOffsetMs())
    init {
        scope.launch {
            combine(historyEntries, _offsetMs) { entries, offset -> historyUseCase.groupByDay(entries, offset) }
                .collect { _groupedHistory.value = it }
        }
    }

    /** Called on each recomposition (or on zone change) to keep day buckets correct. */
    fun refreshHistoryGrouping(offsetMs: Long = currentUtcOffsetMs()) {
        _offsetMs.value = offsetMs
    }

    // ---- Downloads (ADR-006) ----
    val downloads: StateFlow<List<DownloadedTrack>> =
        downloadManager?.downloads
            ?: MutableStateFlow<List<DownloadedTrack>>(emptyList()).asStateFlow()

    val hasDownloads: Boolean
        get() = downloadManager != null

    // Ticker that re-reads device free-space on an interval and on demand.
    // Combined with [downloads] so the storage card refreshes as files grow
    // / are deleted even without a row change. Cheap (one stat call).
    private val storageTick = MutableStateFlow(0L)
    private val storageSpace: StateFlow<StorageSpace?> =
        flow {
            while (true) {
                emit(deviceStorageSpace())
                delay(STORAGE_REFRESH_INTERVAL_MS)
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Aggregate download + device storage used by the Downloads/storage UI. */
    val storageSummary: StateFlow<StorageSummary> =
        combine(downloads, storageSpace, storageTick) { rows, space, _ ->
            StorageSummary.from(rows, space)
        }.stateIn(scope, SharingStarted.Eagerly, StorageSummary.empty())

    /** Force a re-read of device capacity (e.g. after the user returns to the tab). */
    fun refreshStorage() {
        storageTick.value = storageTick.value + 1
    }

    /** Live per-track download progress (null when idle); empty flow if no engine. */
    fun progressFor(trackId: String): Flow<DownloadProgress?> =
        downloadManager?.observeProgress(trackId)
            ?: MutableStateFlow<DownloadProgress?>(null).asStateFlow()

    fun download(track: Track) {
        val dm = downloadManager ?: return
        scope.launch { runCatching { dm.enqueue(track) } }
    }

    fun pauseDownload(trackId: String) {
        val dm = downloadManager ?: return
        scope.launch { runCatching { dm.pause(trackId) } }
    }

    /** Resume a PAUSED download or retry a FAILED one (both re-queue from state). */
    fun resumeDownload(trackId: String) {
        val dm = downloadManager ?: return
        scope.launch { runCatching { dm.resume(trackId) } }
    }

    /** Cancel an in-flight/queued download and drop its `.part` file. */
    fun cancelDownload(trackId: String) {
        val dm = downloadManager ?: return
        scope.launch { runCatching { dm.cancel(trackId) } }
    }

    fun removeDownload(trackId: String) {
        val dm = downloadManager ?: return
        scope.launch { runCatching { dm.remove(trackId) } }
    }

    /**
     * Batch delete: [DownloadManager.remove] cancels any running job and deletes
     * media + artwork + DB row, so it is valid for every [DownloadState].
     */
    fun removeDownloads(trackIds: Collection<String>) {
        val dm = downloadManager ?: return
        if (trackIds.isEmpty()) return
        scope.launch { trackIds.forEach { id -> runCatching { dm.remove(id) } } }
    }

    fun clearDownloads() {
        val dm = downloadManager ?: return
        scope.launch { runCatching { dm.clearAll() } }
    }

    fun playDownloaded(track: Track) {
        setPlayContext(PlayContext.LIBRARY)
        scope.launch {
            // Play the local file directly; OfflineFirstStreamResolver /
            // PlaybackGraph route it to disk without a network round-trip.
            player.prepareQueue(listOf(track), 0, playWhenReady = true)
        }
    }

    // ---- Playlist actions ----
    suspend fun createPlaylist(name: String, description: String? = null): LocalPlaylist =
        playlists.create(name, description)

    fun renamePlaylist(id: String, newName: String) {
        if (newName.isBlank()) return
        scope.launch { runCatching { playlists.rename(id, newName) } }
    }

    fun deletePlaylist(id: String) {
        scope.launch { runCatching { playlists.delete(id) } }
    }

    // ---- Favorite actions ----
    fun removeFavorite(trackId: String) {
        scope.launch { runCatching { library.removeFavorite(trackId) } }
    }

    fun toggleFavorite(track: Track) {
        scope.launch {
            runCatching {
                if (library.isFavorite(track.id)) library.removeFavorite(track.id)
                else library.addFavorite(track)
            }
        }
    }

    // ---- History actions ----
    fun removeHistoryEntry(entryId: Long) {
        scope.launch { runCatching { history.remove(entryId) } }
    }

    fun clearHistory() {
        scope.launch { runCatching { history.clear() } }
    }

    // ---- Playback ----
    fun playFavorites(startIndex: Int = 0) {
        val list = favorites.value
        if (list.isEmpty()) return
        val safe = startIndex.coerceIn(0, list.size - 1)
        setPlayContext(PlayContext.LIBRARY)
        scope.launch { player.prepareQueue(list, safe, playWhenReady = true) }
    }

    fun playFavoritesTrack(track: Track) {
        val list = favorites.value
        val idx = list.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
        playFavorites(idx)
    }

    fun playHistoryEntry(entry: HistoryEntry) {
        // Play the single track, but queue the visible day's tracks
        // so next/prev work naturally (Phase 10 spec: "tap plays").
        val entries = historyEntries.value
        val tracks = entries.map { it.track }
        val idx = entries.indexOfFirst { it.entryId == entry.entryId }
        val safe = if (idx >= 0) idx else 0
        setPlayContext(PlayContext.HISTORY)
        scope.launch { player.prepareQueue(tracks, safe, playWhenReady = true) }
    }

    fun playHistoryDay(day: HistoryDay, startIndex: Int = 0) {
        val tracks = day.entries.map { it.track }
        if (tracks.isEmpty()) return
        val safe = startIndex.coerceIn(0, tracks.size - 1)
        setPlayContext(PlayContext.HISTORY)
        scope.launch { player.prepareQueue(tracks, safe, playWhenReady = true) }
    }

    fun playPlaylist(playlistId: String, startIndex: Int = 0) {
        scope.launch {
            val tracks = runCatching {
                playlists.observeTracks(playlistId).first()
            }.getOrNull() ?: emptyList()
            if (tracks.isEmpty()) return@launch
            val safe = startIndex.coerceIn(0, tracks.size - 1)
            setPlayContext(PlayContext.PLAYLIST)
            player.prepareQueue(tracks, safe, playWhenReady = true)
        }
    }

    // ---- Helpers for relative time (UI can also format) ----
    companion object {
        /** Formats [epochMs] relative to [nowMs] (both UTC ms) as "just now / 5m / 2h / 3d / Mar 12". */
        fun relativeTimeLabel(playedAtMs: Long, nowMs: Long): String {
            val delta = (nowMs - playedAtMs).coerceAtLeast(0L)
            val minute = 60_000L
            val hour = 3_600_000L
            val day = 86_400_000L
            return when {
                delta < minute -> "just now"
                delta < 60 * minute -> "${delta / minute}m ago"
                delta < 24 * hour -> "${delta / hour}h ago"
                delta < 7 * day -> "${delta / day}d ago"
                else -> {
                    // Simple month/day fallback without date libraries (UTC)
                    // We approximate by dividing into 30-day months — good
                    // enough for a list label; the day-header already
                    // carries the canonical grouping.
                    val days = (delta / day).toInt()
                    if (days < 30) "${days}d ago" else "${days / 30}mo ago"
                }
            }
        }

        /** Day header label — Today / Yesterday / or a UTC date. */
        fun dayHeaderLabel(dayStartMs: Long, nowMs: Long, offsetMs: Long): String {
            val dayMs = 86_400_000L
            val todayStart = run {
                val localNow = nowMs + offsetMs
                val floored = localNow - ((localNow % dayMs + dayMs) % dayMs)
                floored - offsetMs
            }
            return when (dayStartMs) {
                todayStart -> "Today"
                todayStart - dayMs -> "Yesterday"
                else -> {
                    // Fallback: YYYY-MM-DD in UTC (no date lib). We compute
                    // via epoch days since 1970-01-01 and approximate.
                    // This matches the grouping key and is stable.
                    val epochDays = ((dayStartMs + offsetMs) / dayMs).toInt()
                    // For Phase 10 verification we keep it simple: epochDays
                    // label — the UI tests only assert grouping, not the
                    // formatted string. A platform formatter can override
                    // this in a later phase.
                    // To keep the user-facing string reasonable we render
                    // as a UTC ISO date via a tiny algorithm without java.time.
                    utcDayToIso(epochDays)
                }
            }
        }

        private fun utcDayToIso(epochDays: Int): String {
            // Howard Hinnant's civil_from_days, shifted to 1970 epoch.
            var z = epochDays + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            var y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = mp + if (mp < 10) 3 else -9
            y += if (m <= 2) 1 else 0
            // Common-safe zero-padding without java.util.Formatter
            fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"
            fun pad4(n: Int): String = when {
                n < 10 -> "000$n"
                n < 100 -> "00$n"
                n < 1000 -> "0$n"
                else -> "$n"
            }
            return "${pad4(y)}-${pad2(m)}-${pad2(d)}"
        }
    }
}
