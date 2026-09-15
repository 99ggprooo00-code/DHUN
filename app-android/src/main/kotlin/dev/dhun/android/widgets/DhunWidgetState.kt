package dev.dhun.android.widgets

/**
 * Pure, testable representation of what a widget shows.
 *
 * Produced by [DhunWidgetUpdater] from a MediaController / Player snapshot
 * (or from an idle fallback). All formatting rules live here so they can be
 * unit-tested on the plain JVM without Robolectric/RemoteViews.
 *
 * The widget never crashes on empty data — every field has a graceful
 * placeholder (see [isIdle]).
 */
data class DhunWidgetState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasTrack: Boolean,
    /** Last known position; 0 when idle/unknown. */
    val positionMs: Long = 0L,
    /** Track duration; 0 when idle/unknown (progress hidden). */
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    /** One of [REPEAT_OFF]/[REPEAT_ALL]/[REPEAT_ONE]. */
    val repeatMode: Int = REPEAT_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /**
     * Cache key for the artwork bitmap (the artwork URI string, or a
     * `bytes:<hash>` synthetic key when only [androidx.media3.common.MediaMetadata.artworkData]
     * is available). Null when the track has no artwork.
     */
    val artworkKey: String? = null,
) {
    val isIdle: Boolean get() = !hasTrack

    /** True when a meaningful progress bar can be drawn. */
    val hasProgress: Boolean get() = hasTrack && durationMs > 0L

    /** 0..1000 for `RemoteViews.setProgressBar(max = 1000)`. */
    val progressPermille: Int get() {
        if (!hasProgress) return 0
        return ((positionMs.coerceAtLeast(0L) * 1000L) / durationMs).coerceIn(0L, 1000L).toInt()
    }

    val positionText: String get() = formatTime(positionMs)
    val durationText: String get() = formatTime(durationMs)

    companion object {
        const val MAX_TITLE_LEN = 30
        const val MAX_ARTIST_LEN = 24
        const val IDLE_TITLE = "Nothing playing"
        const val IDLE_ARTIST = "Tap to open DHUN"

        /** Mirrors `Player.REPEAT_MODE_*` without a Media3 dependency (plain-JVM tests). */
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2

        /** Truncate with ellipsis to keep RemoteViews TextView single-line. */
        fun ellipsize(text: String, maxLen: Int): String {
            val t = text.trim()
            if (t.length <= maxLen) return t
            return t.take(maxLen - 1) + "…"
        }

        /** `m:ss`, or `h:mm:ss` past the hour. Never blank, never negative. */
        fun formatTime(ms: Long): String {
            val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).coerceAtMost(359_999L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0) {
                "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
            } else {
                "$minutes:${seconds.toString().padStart(2, '0')}"
            }
        }

        /** Idle state — no track, transport dimmed, progress empty. */
        fun idle(): DhunWidgetState = DhunWidgetState(
            title = IDLE_TITLE,
            artist = IDLE_ARTIST,
            isPlaying = false,
            hasTrack = false,
        )

        /**
         * Build from raw controller metadata. Never throws — blank fields fall
         * back to placeholders. `rawTitle`/`rawArtist` may be null (controller
         * returns null for metadata when idle).
         */
        fun fromMetadata(
            rawTitle: CharSequence?,
            rawArtist: CharSequence?,
            isPlaying: Boolean,
        ): DhunWidgetState = fromPlayback(
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            isPlaying = isPlaying,
            positionMs = 0L,
            durationMs = 0L,
            shuffleEnabled = false,
            repeatMode = REPEAT_OFF,
            hasNext = false,
            hasPrevious = false,
            artworkKey = null,
        )

        /** Full build from a playback snapshot; same never-throws contract. */
        fun fromPlayback(
            rawTitle: CharSequence?,
            rawArtist: CharSequence?,
            isPlaying: Boolean,
            positionMs: Long,
            durationMs: Long,
            shuffleEnabled: Boolean,
            repeatMode: Int,
            hasNext: Boolean,
            hasPrevious: Boolean,
            artworkKey: String?,
        ): DhunWidgetState {
            val t = rawTitle?.toString()?.trim().orEmpty()
            val a = rawArtist?.toString()?.trim().orEmpty()
            if (t.isEmpty() && a.isEmpty()) return idle()
            val title = if (t.isEmpty()) "Unknown" else ellipsize(t, MAX_TITLE_LEN)
            val artist = if (a.isEmpty()) "" else ellipsize(a, MAX_ARTIST_LEN)
            return DhunWidgetState(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                hasTrack = true,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode.coerceIn(REPEAT_OFF, REPEAT_ONE),
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                artworkKey = artworkKey?.takeIf { it.isNotBlank() },
            )
        }
    }
}
