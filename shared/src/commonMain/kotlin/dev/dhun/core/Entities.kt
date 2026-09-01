package dev.dhun.core

/**
 * DHUN core entities (Phase 02). Plain data, zero platform or framework
 * dependencies. IDs are YouTube/YouTube-Music ids.
 */

data class Track(
    val id: String, // YouTube video id
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumName: String? = null,
    val albumId: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
)

data class Artist(
    val id: String, // channel id (UC…)
    val name: String,
    val subscriberCountText: String? = null,
    val thumbnailUrl: String? = null,
)

data class Album(
    val id: String, // YTM album browse id (MPREb…)
    val title: String,
    val artistName: String? = null,
    val year: String? = null,
    val trackCount: Int? = null,
    val thumbnailUrl: String? = null,
)

data class Playlist(
    val id: String, // YTM playlist browse id (VL…)
    val title: String,
    val authorName: String? = null,
    val trackCountText: String? = null,
    val thumbnailUrl: String? = null,
)

data class SearchResults(
    val query: String,
    val songs: List<Track> = emptyList(),
    val videos: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

data class HistoryEntry(
    val track: Track,
    val playedAtEpochMs: Long,
    val playedFromContext: String? = null,
    val completedPlayback: Boolean = false,
)

/** Resolved playback information for one track. */
data class StreamInfo(
    val videoId: String,
    val audioUrl: String,
    val mimeType: String, // e.g. audio/webm
    val bitrateKbps: Int? = null,
    val codec: String? = null,
    val contentLengthBytes: Long? = null,
)

/* ---------------- Lyrics ------------------------------------------------ */

sealed interface Lyrics {
    data class Synced(val lines: List<LyricsLine>) : Lyrics
    data class Unsynced(val text: String) : Lyrics
    data object NotAvailable : Lyrics
}

data class LyricsLine(
    val startTimeMs: Long?, // null = unsynced line
    val text: String,
)

/* ---------------- Player domain ----------------------------------------- */

enum class RepeatMode { OFF, ALL, ONE }

/** Playback state machine states shared by every platform player. */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Resolving(val track: Track) : PlaybackState
    data class Buffering(val track: Track) : PlaybackState
    data class Playing(val track: Track) : PlaybackState
    data class Paused(val track: Track) : PlaybackState
    data class Error(val track: Track?, val message: String) : PlaybackState
}
