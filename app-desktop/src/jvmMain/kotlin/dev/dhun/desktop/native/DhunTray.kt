package dev.dhun.desktop.native

import dev.dhun.core.Track
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.swing.SwingUtilities

/**
 * Phase 12 — system tray (JDK AWT, no extra dependency; Windows/Linux/macOS).
 *
 * This is also the documented fallback when the SMTC spike is not stable
 * (RISK_REGISTER: "SMTC via JNA unstable → ship documented fallback
 * (tray + media keys)").
 *
 * Menu (per MASTER_PROMPT Phase 12): track title (non-selectable) /
 * play-pause / next / prev / open / quit. Icon swaps between playing and
 * paused variants.
 *
 * Candidate 27 polish (this file; constructor and public signatures are
 * UNCHANGED — the desktop entry point keeps compiling untouched):
 *  - tri-state icon: idle (nothing loaded) / playing (triangle) / paused
 *    (bars) — previously the process started looking "paused";
 *  - live tooltip mirroring the same state ("DHUN — playing: Title —
 *    Artist"), capped well under the ~128-char shell elision point;
 *  - the Windows taskbar jump list ([JumpList]): recent-track tasks plus
 *    Play/Pause and Open, persisted via [RecentTracksStore] in the app data
 *    dir so the list survives restarts. Wired HERE rather than in the entry
 *    point on purpose: the tray already receives every state change the
 *    jump list needs, so zero new wiring is required in `Main.kt` (frozen
 *    for this batch). Recents are recorded from [setTrack]; writes to the
 *    shell are throttled/coalesced inside [JumpList].
 *
 * Honest limits: jump-list clicks launch `DHUN.exe --dhun-…`, which today
 * converges on the single-instance launch-or-refocus path — Recent/Open
 * surface the running app; the Play/Pause verb needs the documented arg
 * hook in the entry point (see [JumpListArgs]). No Windows taskbar behavior
 * is claimed without hardware verification.
 *
 * Threading: all public methods are thread-safe — work is marshaled to the
 * EDT (the player flows that drive [setTrack]/[setPlaying] collect on
 * Dispatchers.Default); jump-list commits run on [JumpList]'s own worker.
 */
class DhunTray(
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onOpen: () -> Unit,
    private val onQuit: () -> Unit,
    private val log: (String) -> Unit = {},
) {
    private var trayIcon: TrayIcon? = null
    private var trackItem: MenuItem? = null
    private var playItem: MenuItem? = null

    // Candidate 27 — EDT-confined mirror of the player state the collectors
    // deliver; both fields are only touched inside onEdt blocks.
    private var currentTrack: Track? = null
    private var playing: Boolean = false

    // Candidate 27 — jump list + its persistence. Created once in [start];
    // null (and inert) off-Windows, when disabled by flag, or unpackaged.
    private var jumpList: JumpList? = null
    private var recentsStore: RecentTracksStore? = null
    private var recents: List<RecentTrack> = emptyList()

    /**
     * Registers the tray icon.
     * @return false (degraded, no tray) when the OS/headless env has none —
     * the app keeps working; tray-only features are just absent. The jump
     * list is independent of the tray's availability and never changes this
     * return value.
     */
    fun start(): Boolean {
        bootstrapJumpList()
        if (!SystemTray.isSupported()) {
            log("SystemTray unsupported — running without tray")
            return false
        }
        onEdt { buildIcon() }
        return true
    }

    /** Updates the non-selectable track-title menu row (+ icon/tooltip/jump list). */
    fun setTrack(track: Track?) {
        onEdt {
            currentTrack = track
            renderState()
            val text = if (track != null && track.title.isNotBlank()) {
                val artist = track.artistName.takeIf { it.isNotBlank() }
                if (artist != null) "${track.title} — $artist" else track.title
            } else {
                "DHUN — nothing playing"
            }
            trackItem?.label = text
            recordRecent(track)
        }
    }

    /** Swaps the icon (playing = triangle / paused = bars) + menu verb. */
    fun setPlaying(playing: Boolean) {
        onEdt {
            this.playing = playing
            renderState()
            playItem?.label = if (playing) "Pause" else "Play"
        }
    }

    fun stop() {
        onEdt {
            trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) }.onFailure { e -> log("tray remove: $e") } }
            trayIcon = null
            trackItem = null
            playItem = null
        }
        // Deliberately NOT clearing the jump list: it is user-facing shell
        // state by design and must survive the process (the taskbar reads it
        // while the app is closed). See JumpList's KDoc.
    }

    /**
     * One icon+tooltip render for the current (track, playing) pair — the
     * single place state maps to presentation, via the pure [TrayState].
     */
    private fun renderState() {
        val icon = trayIcon ?: return
        val state = TrayState.resolve(hasTrack = currentTrack != null, playing = playing)
        runCatching {
            icon.image = when (state) {
                TrayStateKind.IDLE -> TrayIcons.idle()
                TrayStateKind.PLAYING -> TrayIcons.playing()
                TrayStateKind.PAUSED -> TrayIcons.paused()
            }
            icon.setToolTip(
                TrayState.tooltip(state, currentTrack?.title, currentTrack?.artistName),
            )
        }.onFailure { e -> log("tray render: $e") }
    }

    /** Bootstraps the jump list; failures never affect the tray itself. */
    private fun bootstrapJumpList() {
        runCatching {
            val store = RecentTracksStore.open()
            val loaded = store.load()
            val jl = JumpList(log)
            if (!jl.start()) return
            recentsStore = store
            recents = loaded
            jumpList = jl
            jl.update(JumpListModel.buildTasks(loaded))
        }.onFailure { e -> log("jump list bootstrap: $e") }
    }

    /** Moves [track] to the front of the recents list; persists + rewrites. */
    private fun recordRecent(track: Track?) {
        val jl = jumpList ?: return
        if (track == null || track.id.isBlank() || track.title.isBlank()) return
        val updated = RecentTracksStore.record(
            RecentTrack(id = track.id, title = track.title, artistName = track.artistName),
            recents,
        )
        if (updated == recents) return
        recents = updated
        recentsStore?.let { store ->
            runCatching { store.save(updated) }.onFailure { e -> log("jump list save: $e") }
        }
        jl.update(JumpListModel.buildTasks(updated))
    }

    private fun buildIcon() {
        if (trayIcon != null) return
        val menu = PopupMenu()

        val track = MenuItem("DHUN — nothing playing")
        track.isEnabled = false
        trackItem = track
        menu.add(track)

        menu.addSeparator()
        val play = MenuItem("Play")
        playItem = play
        play.addActionListener { runCatching { onPlayPause() }.onFailure { log("playPause: $it") } }
        menu.add(play)

        val next = MenuItem("Next")
        next.addActionListener { runCatching { onNext() }.onFailure { log("next: $it") } }
        menu.add(next)

        val prev = MenuItem("Previous")
        prev.addActionListener { runCatching { onPrevious() }.onFailure { log("previous: $it") } }
        menu.add(prev)

        menu.addSeparator()
        val open = MenuItem("Open DHUN")
        open.addActionListener { runCatching { onOpen() }.onFailure { log("open: $it") } }
        menu.add(open)

        val quit = MenuItem("Quit")
        quit.addActionListener { runCatching { onQuit() }.onFailure { log("quit: $it") } }
        menu.add(quit)

        // Candidate 27: the process starts with nothing loaded, so the idle
        // icon + matching tooltip are the honest first impression (was:
        // paused glyph from the first frame).
        val icon = TrayIcon(TrayIcons.idle(), "DHUN — nothing playing", menu)
        icon.isImageAutoSize = true
        icon.addActionListener { runCatching { onOpen() } }
        runCatching { SystemTray.getSystemTray().add(icon) }.onFailure {
            log("tray add failed: $it — tray disabled")
            return
        }
        trayIcon = icon
    }

    private inline fun onEdt(noinline block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
