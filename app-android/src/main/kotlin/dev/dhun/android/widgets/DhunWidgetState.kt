package dev.dhun.android.widgets

/**
 * Pure, testable representation of what a widget shows.
 *
 * Produced by [DhunWidgetUpdater] from a [androidx.media3.session.MediaController]
 * snapshot (or from an idle fallback). All formatting rules live here so they
 * can be unit-tested without Robolectric/RemoteViews.
 *
 * The widget never crashes on empty data — every field has a graceful
 * placeholder (see [isIdle]).
 */
data class DhunWidgetState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasTrack: Boolean,
) {
    val isIdle: Boolean get() = !hasTrack

    companion object {
        const val MAX_TITLE_LEN = 30
        const val MAX_ARTIST_LEN = 24
        const val IDLE_TITLE = "Nothing playing"
        const val IDLE_ARTIST = "Tap to open DHUN"

        /** Truncate with ellipsis to keep RemoteViews TextView single-line. */
        fun ellipsize(text: String, maxLen: Int): String {
            val t = text.trim()
            if (t.length <= maxLen) return t
            return t.take(maxLen - 1) + "…"
        }

        /** Idle state — no track, controls disabled (tinted). */
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
            )
        }
    }
}
