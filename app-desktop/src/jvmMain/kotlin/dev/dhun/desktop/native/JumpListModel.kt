package dev.dhun.desktop.native

import dev.dhun.data.DhunUserDirs
import java.io.File

/**
 * Candidate 27 — pure (COM-free, AWT-free) cores behind the Windows taskbar
 * jump list. Everything the jump list *decides* lives here so it can be unit
 * tested on any OS without touching a single Windows API; the COM side that
 * *executes* the decisions lives in [JumpList].
 *
 * Scope decision (recorded in `.ai/ROADMAP.md` row 27): the jump list is one
 * `AddUserTasks` batch — recent-track tasks, a separator, then the Play/Pause
 * and Open tasks. No `SetAppID`, no custom categories, no registry writes:
 * custom categories require registering the category under an AUMID we cannot
 * set from the paths this batch owns, and the task list works on the app's
 * default identity. Honest caveat, also recorded in the ROADMAP: a jump-list
 * click launches `DHUN.exe <args>` and today converges on the single-instance
 * launch-or-refocus path, so Recent/Open genuinely surface the running app,
 * while the Play/Pause verb needs the one-line arg hook in the desktop entry
 * point that a later batch can add using [JumpListArgs.parse].
 */

/** One recently played track as the jump list sees it (no shared types). */
data class RecentTrack(val id: String, val title: String, val artistName: String)

/**
 * Taskbar-launch arguments. Deliberately prefixed `--dhun-` so they can never
 * collide with tool flags (`-D…` system properties are a different namespace;
 * these are process args and are simply unused by `main()` today — see the
 * documented Play/Pause follow-up).
 */
object JumpListArgs {

    /** Refocus/launch the app (the verb the tray "Open DHUN" item serves). */
    const val OPEN = "--dhun-open"

    /** Toggle playback in the running instance (verb lands with the arg hook). */
    const val PLAY_PAUSE = "--dhun-play-pause"

    /** Prefix for "play this specific track" recent entries. */
    const val PLAY_PREFIX = "--dhun-play="

    /** What a launched-with-args process should do. */
    sealed interface Command {
        data object Open : Command
        data object PlayPause : Command
        data class Play(val trackId: String) : Command
        data object Unknown : Command
    }

    /** Parses one launch argument (the future `main()` hook is a `when` over this). */
    fun parse(arg: String): Command = when {
        arg == OPEN -> Command.Open
        arg == PLAY_PAUSE -> Command.PlayPause
        arg.startsWith(PLAY_PREFIX) -> {
            val id = arg.removePrefix(PLAY_PREFIX)
            if (isValidTrackId(id)) Command.Play(id) else Command.Unknown
        }
        else -> Command.Unknown
    }

    /**
     * Track ids end up as process arguments inside an `IShellLink`; allow only
     * plain id characters so no shell metacharacter can ever ride through.
     * YouTube video ids are 11 chars of `[A-Za-z0-9_-]`; the check is looser
     * in length so a provider change degrades to "no jump entry", not a crash.
     */
    fun isValidTrackId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

/** One line of the jump-list task block: a labeled task or a separator. */
data class JumpListEntry(
    val title: String,
    val arguments: String,
    val isSeparator: Boolean = false,
)

/** Builds the task list written to the shell; order here = order on screen. */
object JumpListModel {

    /** Jump lists visually degrade past a handful of tasks; 5 reads well. */
    const val MAX_RECENT = 5

    /** Task labels longer than this truncate the popup line; stay under it. */
    const val MAX_TITLE_CHARS = 64

    fun buildTasks(recents: List<RecentTrack>): List<JumpListEntry> {
        val out = ArrayList<JumpListEntry>()
        for (track in recents.take(MAX_RECENT)) {
            if (!JumpListArgs.isValidTrackId(track.id)) continue
            out += JumpListEntry(title = taskTitle(track), arguments = JumpListArgs.play(track.id))
        }
        if (out.isNotEmpty()) {
            out += JumpListEntry(title = "", arguments = "", isSeparator = true)
        }
        out += JumpListEntry(title = "Play / Pause", arguments = JumpListArgs.PLAY_PAUSE)
        out += JumpListEntry(title = "Open DHUN", arguments = JumpListArgs.OPEN)
        return out
    }

    /** "Title — Artist", sanitized and capped for one taskbar popup line. */
    fun taskTitle(track: RecentTrack): String {
        val label = if (track.artistName.isBlank()) track.title
        else "${track.title} — ${track.artistName}"
        return truncate(sanitize(label, fallback = "Untitled"), MAX_TITLE_CHARS)
    }

    /**
     * Strips control characters (the persistence format is tab-separated, and
     * the shell renders labels raw), collapsing the result to a single line.
     */
    fun sanitize(raw: String, fallback: String): String {
        val cleaned = raw.replace(Regex("[\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim()
        return cleaned.ifEmpty { fallback }
    }

    fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take((max - 1).coerceAtLeast(1)) + "…"
}

/**
 * Session-to-session persistence of the recent list as tab-separated lines
 * (`id\ttitle\tartist`) under the app data dir. Owned entirely by
 * [DhunTray] — the desktop entry point is untouched this batch, so the file
 * (not the DB) is the transport, and a null [file] (unwritable data dir)
 * degrades to in-memory recents for the session.
 */
class RecentTracksStore(private val file: File?) {

    companion object {
        const val FILE_NAME = "jumplist-recent.txt"

        /** Opens the store at the app data dir, or an in-memory store. */
        fun open(): RecentTracksStore = try {
            RecentTracksStore(File(DhunUserDirs.dataDir(), FILE_NAME))
        } catch (_: Throwable) {
            RecentTracksStore(null)
        }

        /** Dedupe by id (newest first) and cap — the whole recents policy. */
        fun record(track: RecentTrack, existing: List<RecentTrack>): List<RecentTrack> =
            (listOf(track) + existing.filter { it.id != track.id }).take(JumpListModel.MAX_RECENT)

        fun formatLine(track: RecentTrack): String =
            listOf(track.id, JumpListModel.sanitize(track.title, "Untitled"),
                JumpListModel.sanitize(track.artistName, ""))
                .joinToString("\t")

        /** Tolerant: corrupt lines are skipped, never thrown. Exactly three
         *  tab-separated fields (what [formatLine] writes) is accepted — a
         *  different count means the file was hand-edited or truncated. */
        fun parseLine(line: String): RecentTrack? {
            val parts = line.split('\t')
            if (parts.size != 3) return null
            val id = parts[0].trim()
            val title = parts[1].trim()
            if (id.isEmpty() || title.isEmpty()) return null
            return RecentTrack(id = id, title = title, artistName = parts[2].trim())
        }
    }

    /** Best effort: a missing/corrupt file just means an empty list. */
    fun load(): List<RecentTrack> {
        val f = file ?: return emptyList()
        return runCatching {
            f.readLines().mapNotNull { parseLine(it) }
        }.getOrDefault(emptyList()).take(JumpListModel.MAX_RECENT)
    }

    /** Write-temp-then-rename so a crash mid-write cannot leave garbage. */
    fun save(tracks: List<RecentTrack>): Boolean {
        val f = file ?: return false
        return runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(tracks.joinToString("\n") { formatLine(it) }.let { if (it.isEmpty()) it else "$it\n" })
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }.getOrDefault(false)
    }
}
