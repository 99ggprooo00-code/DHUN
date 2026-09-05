# Phase 12 verification — Desktop Native Integrations

Status: 🟨 **SMTC PHASE 2 CODE STAGED; CI + HARDWARE OPEN** — tray,
mini-player window, keyboard shortcuts, close-to-tray + window geometry, and
the SMTC integration are implemented in `app-desktop`; the new JNA/WinRT path
must pass the next CI run. Windows acceptance remains OPEN because this
sandbox has no Windows desktop, system tray, libVLC, or SMTC-capable hardware.

## What was built (code-level, auditable)

| Spec item (PROMPT_SEQUENCE.md Phase 12) | Implementation |
|---|---|
| System tray: icon (playing/paused variants), menu (track title / play-pause / next / prev / open / quit) | `app-desktop/.../desktop/native/DhunTray.kt` — AWT `SystemTray` + `TrayIcon` (JDK standard, no dependency; Win/Linux/macOS, silently degraded on headless); menu exactly per spec — non-selectable track-title row + Play/Pause (verb swaps) + Next + Previous + Open DHUN + Quit; icon swaps `TrayIcons.playing()` (accent triangle) ↔ `TrayIcons.paused()` (accent bars); all mutators EDT-marshaled (thread-safe); `start()` returns false on headless so the app degrades instead of crashing |
| Tray icons (no binary assets yet) | `app-desktop/.../desktop/native/TrayIcons.kt` — 32×32 ARGB `BufferedImage` drawn in code (dark rounded tile + accent glyph, `setAutoSize` for 16/48 DPI). Replaced by a real `.ico` in the jpackage step if a design asset lands |
| Mini-player window: 320×88 always-on-top; artwork, title, transport, progress; draggable; click opens main | `app-desktop/.../desktop/ui/MiniPlayerWindow.kt` — `MiniPlayerContent` (56 dp `ArtworkImage`, title `titleSmall` / artist `labelSmall`, ⏸/▶ + ⏭ `DhunIconButton`s, 2 dp accent progress line) hosted in `Main.kt` as a second Compose `Window` (`alwaysOnTopValue=true`, `resizable=false`, `skipTaskbar=true`); `dragWindow` modifier moves the AWT frame on pointer-drag over the artwork+title region; release **without** drag = click → `showMainWindow()`; window X hides (not disposes — `DO_NOTHING_ON_CLOSE` + `isVisible=false`) so Ctrl+M / tray "Open" always work |
| Keyboard shortcuts: Space, ←/→ seek 5 s, Ctrl+←/→ prev/next, Ctrl+F search, Ctrl+M mini-player, Ctrl+Q quit | `Main.kt` — root `Modifier.onKeyEvent` (NOT preview: fires only for keys the focused node didn't consume, so Space/←/→ typing in the search field stays untouched); `EventType.Press`-only (no auto-repeat); Ctrl+F → `nav.selectedTab = AppTab.SEARCH` (jumps to the Search tab — auto-focus into the field is a follow-up); Ctrl+M → `toggleMiniPlayer()` (AWT show/hide + toFront); Ctrl+Q → `quit()` (the one clean-exit path, shared with tray Quit: save geometry → tray.stop → persistence.stop → player.release → scope.cancel → `System.exit(0)` — no zombies) |
| Close-to-tray setting (default on), remembered window state | `Main.kt` — `closeToTray` read once at startup from `SettingsKeys.CLOSE_TO_TRAY` (default `true`); main window `onCloseRequest` → hide to tray (after saving geometry) or `quit()`; main + mini frames get `WindowConstants.DO_NOTHING_ON_CLOSE`; geometry persisted as `"x,y,w,h"` in `SettingsKeys.WINDOW_GEOMETRY` (Phase 05 DB) on close-to-tray and quit, restored into `rememberWindowState(position=…)` at startup |
| SMTC spike (time-boxed 3 days): now-playing tile, artwork, media keys; if stable → integrate, else documented fallback | `app-desktop/.../desktop/smct/Smct.kt` — **phase 2 code**: startup activation after the AWT window exists, `GetForWindow(HWND, IID 99FA3FF4-1742-42A6-902E-087D41F965EC)`, `DisplayUpdater` → `MusicProperties` title/artist/album, remote `RandomAccessStreamReference` thumbnail, playback-state and previous/next state updates, and `ButtonPressed` registration through a retained JNA COM callback (`0557e996-7b23-5bae-aa81-ea0d671143a4`). The exact Windows.Media vtable order is encoded from the Windows SDK/windows-rs ABI; `IsEnabled` is the liveness check at slot 10. Native failures are HRESULT-logged and leave the AWT tray/keyboard fallback active; `-Ddhun.smct=false` disables. Hardware round-trip is still OPEN. |
| Packaging: jpackage `.msi` with app icon; clean-VM install test | `app-desktop/build.gradle.kts` `compose.desktop { application { nativeDistributions { targetFormats(Dmg, Msi, Deb) } } }` already active (Phase 04) — the Compose packager drives jpackage; `packageVersion` stays 1.0.x (packager rejects MAJOR 0, documented Phase 04). App icon + clean-VM install test: OPEN (needs Windows machine + a real `.ico`) |

## SMTC phase 2 procedure (on the user's Windows machine)

The phase-2 implementation now uses the authoritative Windows.Media ABI
rather than an unverified metadata interface. `Smct.connect("DHUN", ...)`
performs the following guarded sequence after the AWT main window exists:

1. Create the `Windows.Media.SystemMediaTransportControls` HSTRING.
2. Resolve `ISystemMediaTransportControlsInterop`
   (`ddb0472d-c911-4a1f-86d9-dc3d71a95f5a`) through `RoGetActivationFactory`.
3. Call `GetForWindow(HWND, IID 99FA3FF4-1742-42A6-902E-087D41F965EC)`.
4. Read `IsEnabled` at `ISystemMediaTransportControls` slot 10. The prior
   phase-1 slot-6 check was corrected: slot 6 is `PlaybackStatus`, not a
   boolean visibility method.
5. Get `DisplayUpdater` (slot 8), `MusicProperties` (slot 12), and the
   optional `IMusicDisplayProperties2` album interface. Title, album artist,
   artist, and album are written as HSTRINGs, then `Update` (slot 17) is
   called. A valid HTTP(S) thumbnail is supplied through
   `Uri` → `RandomAccessStreamReference` → `SetThumbnail`.
6. Register a retained COM callback for `ButtonPressed` (slot 32) using
   `ISystemMediaTransportControlsButtonPressedEventHandler`
   (`0557e996-7b23-5bae-aa81-ea0d671143a4`). The callback reads the button
   enum at the event-args slot 6 and dispatches Play, Pause, Stop, Next,
   Previous, Fast-forward, and Rewind to the shared player scope. Event
   removal and all COM references happen on clean quit.
7. Run the app with the console visible. A successful startup prints one
   `SMTC probe PASS — ... phase2=ok (...)` line. A failure prints its HRESULT,
   returns to the tray/keyboard path, and does not crash the app.

No extra Windows SDK, WinRT, or icon dependency is introduced. The only
native dependency is the already-present base JNA artifact. The remote
thumbnail is best effort; text metadata and transport buttons are the
phase-2 readiness requirements.

## On-hardware checklist (OPEN — Windows machine; Linux/macOS where noted)

- [ ] **Tray**: launch (`:app-desktop:run`) → tray icon present (paused bars); start any track → icon switches to triangle within ~1 s; tray menu shows `"title — artist"`; Play/Pause/Next/Previous from the menu drive the player (position/track change observable in the app); **Open DHUN** brings the main window to front (also single-click on the icon); on Linux/macOS the tray either works or is absent without crash (headless CI = absent, app fine)
- [ ] **Close-to-tray (default on)**: main window X → window hides, app alive (tray still there, playback continues — audio is the proof); tray → Open DHUN → window back with same queue/position; **Quit** from tray → process gone (`tasklist | findstr dhun` / `ps` — no zombie, no dangling libVLC/vlc process); quit path also saves geometry
- [ ] **Close-to-tray off**: clear the setting (`Settings` row for `close_to_tray` = false in `dhun.db`, or via a future settings screen) → restart → main window X now exits the app
- [ ] **Window geometry**: resize/move the main window → close-to-tray → relaunch → window returns at the same size+position (`window_geometry` row in `dhun.db` = "x,y,w,h")
- [ ] **Mini-player window**: starts visible at 320×88, always on top (verified: over an elevated other window); shows current artwork/title/artist; ▶/⏸ and ⏭ buttons work; progress line advances with playback; **drag** the artwork/title region → window follows the cursor; **click** (no drag) the artwork/title region → main window comes to front; its X hides it (mini gone, app alive) → **Ctrl+M** brings it back → **Ctrl+M** hides again
- [ ] **Keyboard shortcuts** (main window focused): Space toggles play/pause; ←/→ seek ±5 s (position bar moves); Ctrl+← / Ctrl+→ = previous/next track; Ctrl+F lands on the Search tab; Ctrl+M toggles the mini-player; Ctrl+Q exits clean (same zombie check as tray Quit). **Negative check**: typing "Bohemian  Rhapsody" (space) in the search field types a space — shortcuts don't steal keys from the text field
- [ ] **SMTC probe + phase 2**: console shows `SMTC probe PASS — …` with `hwnd`, `abi`, `activate-factory`, `get-for-window`, `is-enabled`, and `phase2=ok`; use the Windows tile to verify title/artist/artwork and press Play/Pause/Next/Previous media keys → record the exact line and round-trip below
- [ ] **jpackage**: `./gradlew :app-desktop:createMsi` (Windows) → installer builds with app icon; install on a clean Windows user/VM → launches, plays, tray works → record version/any issues
- [ ] **Soak**: 30-min mixed use (queue skips, tray use, mini-player drag, shortcuts) — zero crashes; tray state never desyncs from the player (icon/verb always match)

## Probe / evidence log (fill on hardware)

- Windows build / machine: ______
- `SMTC probe` console line: ______
- IIDs used (source-verified): `ISystemMediaTransportControlsInterop` `ddb0472d-c911-4a1f-86d9-dc3d71a95f5a` · `ButtonPressedEventHandler` `0557e996-7b23-5bae-aa81-ea0d671143a4`
- Phase 2 verdict (stable → integrated / not stable → fallback): ______
- Tray: ______ · Close-to-tray: ______ · Geometry: ______ · Mini-player: ______ · Shortcuts: ______ · jpackage MSI: ______

## Known gaps (mirror of KNOWN_LIMITATIONS)

- SMTC phase 2 is source-integrated but not hardware-verified in this
  sandbox. If activation or ButtonPressed registration fails on Windows,
  the documented fallback is tray + keyboard shortcuts; the exact HRESULT
  must be recorded here rather than treated as a silent success.
- No app icon yet (tray uses the in-code glyph; jpackage uses the
  Compose-packager default until a `.ico` lands).
- Ctrl+F jumps to the Search tab but doesn't move focus into the field
  (Compose Desktop focus request on a specific `TextField` is a small
  follow-up; typing works immediately after one click).
- If the user closes the mini-player window and also closes-to-trays the
  main window, then quits via tray, all is well; but closing the **last**
  visible window on some desktops can let the OS reap focus state — the
  documented user pattern is tray Quit, not closing both windows.
- Tray on Wayland/X11 without a system tray (e.g. GNOME default without
  an extension) = `SystemTray.isSupported()` false → no tray, app works.

## Screenshots (to capture on hardware)

- Tray: paused vs playing icon + open menu (track title row visible)
- Mini-player window over another app (always-on-top proof) + dragged position
- Main window restored to previous geometry (before/after relaunch)
- Console: `SMTC probe …` line (PASS or the exact FAIL step)
