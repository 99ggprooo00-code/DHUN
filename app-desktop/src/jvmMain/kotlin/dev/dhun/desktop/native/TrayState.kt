package dev.dhun.desktop.native

/**
 * Candidate 27 — pure tray presentation state. Maps the (track, playing)
 * pair the tray collectors deliver onto the icon variant, tooltip text and
 * menu verb, so the polish rules are unit-testable without AWT. The old
 * behavior (paused icon from process start) is preserved exactly for the
 * has-a-track cases and gains only a distinct idle look.
 */
enum class TrayStateKind { IDLE, PLAYING, PAUSED }

object TrayState {

    /** Windows elides tooltips past ~128 chars; stay safely under. */
    const val TOOLTIP_MAX_CHARS = 100

    fun resolve(hasTrack: Boolean, playing: Boolean): TrayStateKind = when {
        !hasTrack -> TrayStateKind.IDLE
        playing -> TrayStateKind.PLAYING
        else -> TrayStateKind.PAUSED
    }

    /**
     * "DHUN — nothing playing" (idle, matching the menu row's historical
     * text) / "DHUN — playing: Title — Artist" / "DHUN — paused: …",
     * truncated with an ellipsis rather than by Windows mid-word.
     */
    fun tooltip(kind: TrayStateKind, title: String?, artistName: String?): String {
        val base = when (kind) {
            TrayStateKind.IDLE -> "DHUN — nothing playing"
            TrayStateKind.PLAYING -> "DHUN — playing"
            TrayStateKind.PAUSED -> "DHUN — paused"
        }
        if (kind == TrayStateKind.IDLE) return base
        val parts = listOfNotNull(
            title?.trim()?.takeIf { it.isNotEmpty() },
            artistName?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" — ")
        if (parts.isEmpty()) return base
        val full = "$base: $parts"
        return if (full.length <= TOOLTIP_MAX_CHARS) full
        else full.take(TOOLTIP_MAX_CHARS - 1) + "…"
    }
}
