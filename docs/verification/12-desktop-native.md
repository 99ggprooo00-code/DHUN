# Phase 12 verification — Desktop Native Integrations

Status: 🟨 **CODE COMPLETE (spike phase 1)** — tray, mini-player window,
keyboard shortcuts, close-to-tray + window geometry, and the SMTC spike
(activation-only) are implemented in `app-desktop`; CI compiles it;
on-hardware acceptance OPEN (Windows machine required for the SMTC probe
and the tray/media-key checks).

## What was built (code-level, auditable)

| Spec item (PROMPT_SEQUENCE.md Phase 12) | Implementation |
|---|---|
| System tray: icon (playing/paused variants), menu (track title / play-pause / next / prev / open / quit) | `app-desktop/.../desktop/native/DhunTray.kt` — AWT `SystemTray` + `TrayIcon` (JDK standard, no dependency; Win/Linux/macOS, silently degraded on headless); menu exactly per spec — non-selectable track-title row + Play/Pause (verb swaps) + Next + Previous + Open DHUN + Quit; icon swaps `TrayIcons.playing()` (accent triangle) ↔ `TrayIcons.paused()` (accent bars); all mutators EDT-marshaled (thread-safe); `start()` returns false on headless so the app degrades instead of crashing |
| Tray icons (no binary assets yet) | `app-desktop/.../desktop/native/TrayIcons.kt` — 32×32 ARGB `BufferedImage` drawn in code (dark rounded tile + accent glyph, `setAutoSize` for 16/48 DPI). Replaced by a real `.ico` in the jpackage step if a design asset lands |
| Mini-player window: 320×88 always-on-top; artwork, title, transport, progress; draggable; click opens main | `app-desktop/.../desktop/ui/MiniPlayerWindow.kt` — `MiniPlayerContent` (56 dp `ArtworkImage`, title `titleSmall` / artist `labelSmall`, ⏸/▶ + ⏭ `DhunIconButton`s, 2 dp accent progress line) hosted in `Main.kt` as a second Compose `Window` (`alwaysOnTopValue=true`, `resizable=false`, `skipTaskbar=true`); `dragWindow` modifier moves the AWT frame on pointer-drag over the artwork+title region; release **without** drag = click → `showMainWindow()`; window X hides (not disposes — `DO_NOTHING_ON_CLOSE` + `isVisible=false`) so Ctrl+M / tray "Open" always work |
| Keyboard shortcuts: Space, ←/→ seek 5 s, Ctrl+←/→ prev/next, Ctrl+F search, Ctrl+M mini-player, Ctrl+Q quit | `Main.kt` — root `Modifier.onKeyEvent` (NOT preview: fires only for keys the focused node didn't consume, so Space/←/→ typing in the search field stays untouched); `EventType.Press`-only (no auto-repeat); Ctrl+F → `nav.selectedTab = AppTab.SEARCH` (jumps to the Search tab — auto-focus into the field is a follow-up); Ctrl+M → `toggleMiniPlayer()` (AWT show/hide + toFront); Ctrl+Q → `quit()` (the one clean-exit path, shared with tray Quit: save geometry → tray.stop → persistence.stop → player.release → scope.cancel → `System.exit(0)` — no zombies) |
| Close-to-tray setting (default on), remembered window state | `Main.kt` — `closeToTray` read once at startup from `SettingsKeys.CLOSE_TO_TRAY` (default `true`); main window `onCloseRequest` → hide to tray (after saving geometry) or `quit()`; main + mini frames get `WindowConstants.DO_NOTHING_ON_CLOSE`; geometry persisted as `"x,y,w,h"` in `SettingsKeys.WINDOW_GEOMETRY` (Phase 05 DB) on close-to-tray and quit, restored into `rememberWindowState(position=…)` at startup |
| SMTC spike (time-boxed 3 days): now-playing tile, artwork, media keys; if stable → integrate, else documented fallback | `app-desktop/.../desktop/smct/Smct.kt` — **phase 1 (this build)**: passive startup probe (after 2 s, once the main window exists) that proves the hard part of the risk — WinRT activation from an unpackaged JVM via JNA: `RoGetActivationFactory` (combase.dll) → `ISystemMediaTransportControlsInterop` (IID `ddb0472d-c911-4a1f-86d9-dc3d71a95f5a`, verified MS docs + WindowsInteropWrappers) → `GetForWindow(HWND, IID 99FA3FF4-1742-42A6-902E-087D41F965EC)` → live-object check `IsTransportControlsButtonVisible`. Every step logs its HRESULT (`SMTC probe PASS/FAIL — step=ok (…) | …`); nothing throws into the app; `-Ddhun.smct=false` disables. HWND via `FindWindowW("SunAwtFrame", "DHUN")` (AWT doesn't expose HWND publicly; the main window is the only top-level window with that title). **Phase 2 (NOT in this build)**: `UpdateMetadata` (`ISMCSystemMetadata`) + `ButtonPressed` event registration (JNA callback vtable + `EventRegistrationToken`) — needs the two IIDs pulled from the target machine's winmd (procedure below) |
| Packaging: jpackage `.msi` with app icon; clean-VM install test | `app-desktop/build.gradle.kts` `compose.desktop { application { nativeDistributions { targetFormats(Dmg, Msi, Deb) } } }` already active (Phase 04) — the Compose packager drives jpackage; `packageVersion` stays 1.0.x (packager rejects MAJOR 0, documented Phase 04). App icon + clean-VM install test: OPEN (needs Windows machine + a real `.ico`) |

## SMTC spike — phase 2 procedure (on the user's Windows machine)

Spike phase 1 is deliberately **activation-only**: the two remaining
interops (`ISMCSystemMetadata`, `ISystemMediaTransportControlsButtonPressedEventHandler`)
need IIDs + vtable method order. Rather than ship unverified constants,
pull them authoritatively on the target machine:

1. **Verify phase 1 first**: run the desktop app, watch the console for
   `SMTC probe PASS — hwnd=… | abi=ok (…) | activate-factory=ok (…) |
   get-for-window=ok (…) | is-visible=ok (visible=…)`.
   - If any step FAILs: read the HRESULT (0x80004002 = E_NOINTERFACE →
     IID wrong; 0x80070005 = access; NULL factory → class name/OS floor —
     SMTC needs Windows 10 1607+), record it below, and declare the
     fallback (tray + shortcuts ship; KNOWN_LIMITATIONS already says so).
2. **Pull the IIDs + method order** from the system winmd (pick one):
   - PowerShell + `dotnet-ildasm` (if installed):
     `ildasm C:\Windows\System32\WinMetadata\Windows.winmd /text:Windows.Media.Control.SMCSystemMetadata`
   - Or a 20-line C# scratch program:
     `typeof(Windows.Media.Control.SMCSystemMetadata).GUID` with the
     Windows SDK reference (project SDK `Microsoft.NET.Sdk`,
     `<TargetFramework>net8.0-windows10.0.19041.0</TargetFramework>`).
   - Or Python: `pywinrt` metadata dump of `Windows.winmd`.
   Record: `ISMCSystemMetadata` IID, method order (static
   `CreateSystemMetadata` position + `SetMusicTitle`/`SetMusicArtist`
   slots), `ISystemMediaTransportControlsButtonPressedEventHandler` IID +
   its `Invoke` slot, and the full `ISystemMediaTransportControls` method
   list (to confirm slot 6/7/8 = `IsTransportControlsButtonVisible` /
   `UpdateMetadata` / `RegisterSystemMediaTransportControlsButtonPressedEventHandler`).
3. **Implement phase 2** in `Smct.kt` (metadata push on track change +
   play-state, button events → `DesktopDhunPlayer`), re-run the probe
   (extend it to cover metadata + a button round-trip), and record the
   result below. Stable → integrated (media keys work); not stable →
   documented fallback stays (already the default).

## On-hardware checklist (OPEN — Windows machine; Linux/macOS where noted)

- [ ] **Tray**: launch (`:app-desktop:run`) → tray icon present (paused bars); start any track → icon switches to triangle within ~1 s; tray menu shows `"title — artist"`; Play/Pause/Next/Previous from the menu drive the player (position/track change observable in the app); **Open DHUN** brings the main window to front (also single-click on the icon); on Linux/macOS the tray either works or is absent without crash (headless CI = absent, app fine)
- [ ] **Close-to-tray (default on)**: main window X → window hides, app alive (tray still there, playback continues — audio is the proof); tray → Open DHUN → window back with same queue/position; **Quit** from tray → process gone (`tasklist | findstr dhun` / `ps` — no zombie, no dangling libVLC/vlc process); quit path also saves geometry
- [ ] **Close-to-tray off**: clear the setting (`Settings` row for `close_to_tray` = false in `dhun.db`, or via a future settings screen) → restart → main window X now exits the app
- [ ] **Window geometry**: resize/move the main window → close-to-tray → relaunch → window returns at the same size+position (`window_geometry` row in `dhun.db` = "x,y,w,h")
- [ ] **Mini-player window**: starts visible at 320×88, always on top (verified: over an elevated other window); shows current artwork/title/artist; ▶/⏸ and ⏭ buttons work; progress line advances with playback; **drag** the artwork/title region → window follows the cursor; **click** (no drag) the artwork/title region → main window comes to front; its X hides it (mini gone, app alive) → **Ctrl+M** brings it back → **Ctrl+M** hides again
- [ ] **Keyboard shortcuts** (main window focused): Space toggles play/pause; ←/→ seek ±5 s (position bar moves); Ctrl+← / Ctrl+→ = previous/next track; Ctrl+F lands on the Search tab; Ctrl+M toggles the mini-player; Ctrl+Q exits clean (same zombie check as tray Quit). **Negative check**: typing "Bohemian  Rhapsody" (space) in the search field types a space — shortcuts don't steal keys from the text field
- [ ] **SMTC probe**: console shows `SMTC probe PASS — …` (all four steps) on the Windows machine → record the exact line below; then follow the phase-2 procedure above
- [ ] **jpackage**: `./gradlew :app-desktop:createMsi` (Windows) → installer builds with app icon; install on a clean Windows user/VM → launches, plays, tray works → record version/any issues
- [ ] **Soak**: 30-min mixed use (queue skips, tray use, mini-player drag, shortcuts) — zero crashes; tray state never desyncs from the player (icon/verb always match)

## Probe / evidence log (fill on hardware)

- Windows build / machine: ______
- `SMTC probe` console line: ______
- IIDs pulled (phase 2): `ISMCSystemMetadata` ______ · `ButtonPressedEventHandler` ______
- Phase 2 verdict (stable → integrated / not stable → fallback): ______
- Tray: ______ · Close-to-tray: ______ · Geometry: ______ · Mini-player: ______ · Shortcuts: ______ · jpackage MSI: ______

## Known gaps (mirror of KNOWN_LIMITATIONS)

- Media keys don't drive DHUN until SMTC phase 2 ships (or never, if the
  spike fails — fallback is tray + shortcuts; documented, not silent).
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
