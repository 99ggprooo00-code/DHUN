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
 * Threading: all public methods are thread-safe — work is marshaled to the
 * EDT (the player flows that drive [setTrack]/[setPlaying] collect on
 * Dispatchers.Default).
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

    /**
     * Registers the tray icon.
     * @return false (degraded, no tray) when the OS/headless env has none —
     * the app keeps working; tray-only features are just absent.
     */
    fun start(): Boolean {
        if (!SystemTray.isSupported()) {
            log("SystemTray unsupported — running without tray")
            return false
        }
        onEdt { buildIcon() }
        return true
    }

    /** Updates the non-selectable track-title menu row. */
    fun setTrack(track: Track?) {
        onEdt {
            val text = if (track != null && track.title.isNotBlank()) {
                val artist = track.artistName.takeIf { it.isNotBlank() }
                if (artist != null) "${track.title} — $artist" else track.title
            } else {
                "DHUN — nothing playing"
            }
            trackItem?.text = text
        }
    }

    /** Swaps the icon (playing = triangle / paused = bars) + menu verb. */
    fun setPlaying(playing: Boolean) {
        onEdt {
            val icon = trayIcon ?: return@onEdt
            icon.image = if (playing) TrayIcons.playing() else TrayIcons.paused()
            playItem?.text = if (playing) "Pause" else "Play"
        }
    }

    fun stop() {
        onEdt {
            trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) }.onFailure { e -> log("tray remove: $e") } }
            trayIcon = null
            trackItem = null
            playItem = null
        }
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

        val icon = TrayIcon(TrayIcons.paused(), "DHUN", menu)
        icon.isImageAutoSize = true
        icon.addActionListener { runCatching { onOpen() } }
        runCatching { SystemTray.getSystemTray().add(icon) }.onFailure {
            log("tray add failed: $it — tray disabled")
            return
        }
        trayIcon = icon
    }

    private inline fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
