# Phase 12 verification — Desktop Native Integrations

Status: 🟨 **NATIVE CODE MERGED; UPGRADE / PLAYBACK / NATIVE HARDWARE GATES OPEN.**
Baseline `main` / `test` is `0920148`; Desktop compilation and MSI publishing
are green (`34018809911` / `34018809913`, published 2026-09-06T07:22:29Z).
The user now reports **one-window startup after manual uninstall/reinstall**.
The separate mini-player removal (ADR-004 / PR #28) is not work to repeat;
the docked in-app MiniPlayer remains.

The same report says **install-over fails with “Another version of this
product is already installed…” and audio still fails**. A local candidate
adds increasing MSI ProductVersions, Windows-aware extraction, diagnostics,
Home and player repairs. **Commit/push for branch CI is approved; tests are
pending and no PR, merge or release is authorised**;
see [Phase 14's fresh report and validation record](14-release.md).
Tray/SMTC/shortcuts, data-preserving upgrade and clean-target hygiene are
not proven by a visible window or a green packaging job.

## What was built (code-level, auditable)

| Spec item (PROMPT_SEQUENCE.md Phase 12) | Implementation |
|---|---|
| System tray: icon (playing/paused variants), menu (track title / play-pause / next / prev / open / quit) | `app-desktop/.../desktop/native/DhunTray.kt` — AWT `SystemTray` + `TrayIcon` (JDK standard, no dependency; Win/Linux/macOS, silently degraded on headless); menu exactly per spec — non-selectable track-title row + Play/Pause (verb swaps) + Next + Previous + Open DHUN + Quit; icon swaps `TrayIcons.playing()` (accent triangle) ↔ `TrayIcons.paused()` (accent bars); all mutators EDT-marshaled (thread-safe); `start()` returns false on headless so the app degrades instead of crashing |
| Tray icons (no binary assets yet) | `app-desktop/.../desktop/native/TrayIcons.kt` — 32×32 ARGB `BufferedImage` drawn in code (dark rounded tile + accent glyph, `setAutoSize` for 16/48 DPI). Replaced by a real `.ico` in the jpackage step if a design asset lands |
| Mini-player window: ~~320×88 always-on-top; artwork, title, transport, progress; draggable; click opens main~~ **REMOVED 2026-09-06 (ADR-004, user decision)** | Was `app-desktop/.../desktop/ui/MiniPlayerWindow.kt` — `MiniPlayerContent` (56 dp `ArtworkImage`, title/artist, ⏸/▶ + ⏭, 2 dp accent progress line) hosted in `Main.kt` as a second Compose `Window` (`alwaysOnTop=true`, `resizable=false`), draggable via JNA `SetWindowPos`, click → `showMainWindow()`, Ctrl+M toggle. The user judged it redundant next to the docked in-app MiniPlayer (Phase 08) and it always showed in the taskbar (no `skipTaskbar` in Compose Desktop 1.8.2). The window, its toggle, and the `Smct.moveWindow` helper were deleted; `Main.kt` now opens exactly one window |
| Keyboard shortcuts: Space, ←/→ seek 5 s, Ctrl+←/→ prev/next, Ctrl+F search, Ctrl+Q quit | `Main.kt` — root `Modifier.onKeyEvent` (NOT preview: fires only for keys the focused node didn't consume, so Space/←/→ typing in the search field stays untouched); `EventType.Press`-only (no auto-repeat); Ctrl+F → `nav.selectedTab = AppTab.SEARCH` (jumps to the Search tab — auto-focus into the field is a follow-up); Ctrl+Q → `quit()` (the one clean-exit path, shared with tray Quit: save geometry → tray.stop → persistence.stop → player.release → scope.cancel → `System.exit(0)` — no zombies). Ctrl+M was removed with the mini-player window (ADR-004) |
| Close-to-tray setting (default on), remembered window state | `Main.kt` — `closeToTray` read once at startup from `SettingsKeys.CLOSE_TO_TRAY` (default `true`); main window `onCloseRequest` → hide to tray (after saving geometry) or `quit()`; geometry persisted as `"x,y,w,h"` in `SettingsKeys.WINDOW_GEOMETRY` (Phase 05 DB) on close-to-tray and quit, restored into `rememberWindowState(position=…)` at startup. The mini frame reference went away with the removed mini-player window (ADR-004) |
| SMTC spike (time-boxed 3 days): now-playing tile, artwork, media keys; if stable → integrate, else documented fallback | `app-desktop/.../desktop/smct/Smct.kt` — **phase 2 code**: startup activation after the AWT window exists, `GetForWindow(HWND, IID 99FA3FF4-1742-42A6-902E-087D41F965EC)`, `DisplayUpdater` → `MusicProperties` title/artist/album, remote `RandomAccessStreamReference` thumbnail, playback-state and previous/next state updates, and `ButtonPressed` registration through a retained JNA COM callback (`0557e996-7b23-5bae-aa81-ea0d671143a4`). The exact Windows.Media vtable order is encoded from the Windows SDK/windows-rs ABI; `IsEnabled` is the liveness check at slot 10. Native failures are HRESULT-logged and leave the AWT tray/keyboard fallback active; `-Ddhun.smct=false` disables. Hardware round-trip is still OPEN. |
| Packaging: jpackage `.msi` with app icon; clean-VM install test | `app-desktop/build.gradle.kts` `compose.desktop { application { nativeDistributions { targetFormats(Dmg, Msi, Deb) } } }` already active (Phase 04) — the Compose packager drives jpackage; published baseline uses 1.0.5; the LOCAL candidate uses a positive-major, increasing internal MSI sequence (`scripts/installer_version.py`), separate from app semver. App icon + clean-VM install test: OPEN (needs Windows machine + a real `.ico`) |

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
- [x] **Single-window startup after manual reinstall** — user-confirmed 2026-09-06 following the latest `test` recommendation; ADR-004 removal is on GitHub in PR #28. This closes only the reported one-window check, not installer upgrade, native controls, playback or checksum identity.
- [ ] **Remaining window/native checks** — no unwanted taskbar entry; docked MiniPlayer expands/controls playback; tray hide/restore works; no removed Ctrl+M window behavior. Do not reintroduce the separate always-on-top mini-player.
- [ ] **Keyboard shortcuts** (main window focused): Space toggles play/pause; ←/→ seek ±5 s (position bar moves); Ctrl+← / Ctrl+→ = previous/next track; Ctrl+F lands on the Search tab; Ctrl+Q exits clean (same zombie check as tray Quit). **Negative check**: typing "Bohemian  Rhapsody" (space) in the search field types a space — shortcuts don't steal keys from the text field
- [ ] **SMTC probe + phase 2**: console shows `SMTC probe PASS — …` with `hwnd`, `abi`, `activate-factory`, `get-for-window`, `is-enabled`, and `phase2=ok`; use the Windows tile to verify title/artist/artwork and press Play/Pause/Next/Previous media keys → record the exact line and round-trip below
- [ ] **jpackage**: `./gradlew :app-desktop:packageMsi` (Windows) → installer builds with app icon; install on a clean Windows user/VM → launches, plays, tray works → record version/any issues
- [ ] **Soak**: 30-min mixed use (queue skips, tray use, shortcuts) — zero crashes; tray state never desyncs from the player (icon/verb always match)

## Probe / evidence log (fill on hardware)

- Windows build / machine: ______
- `SMTC probe` console line: ______
- IIDs used (source-verified): `ISystemMediaTransportControlsInterop` `ddb0472d-c911-4a1f-86d9-dc3d71a95f5a` · `ButtonPressedEventHandler` `0557e996-7b23-5bae-aa81-ea0d671143a4`
- Phase 2 verdict (stable → integrated / not stable → fallback): ______
- One-window launch: **user-reported PASS after manual reinstall, 2026-09-06**. Install-over: **FAIL, “Another version…”**. Audio: **FAIL, generic unavailable**.
- Tray: ______ · Close-to-tray: ______ · Geometry: ______ · Docked MiniPlayer controls: ______ · Shortcuts: ______ · Clean-target MSI: ______

## Phase 14 Windows JVM launch fix — 2026-09-06 (main@e90dba6, PR #22)

**Symptom:** `dhun-test.msi` installed after SmartScreen **Run anyway**, but opening the installed app displayed `Failed to launch JVM`. Compilation and MSI packaging had succeeded — install ≠ launch.

**Investigation (per handoff):** checked `app-desktop/build.gradle.kts` bundled modules and `DesktopDhunPlayer`/`Main.kt` startup. Confirmed: (1) no explicit `java.sql` module despite SQLDelight/JDBC, (2) VLC eager init before window.

**Fixes applied:**

- **Bundled runtime modules (`app-desktop/build.gradle.kts`):** added `modules("java.sql","java.sql.rowset","java.naming","jdk.unsupported","java.management","java.instrument","java.desktop","java.logging","java.net.http")` plus `includeAllModules = true` (fallback; size 112 MB at `34011563630`) and bumped `packageVersion` 1.0.4 → **1.0.5**. The Compose plugin's `jlink` does NOT auto-detect modules — missing `java.sql` was the exact cause (same as StackOverflow 77675565/78374398 for Compose+sqlite/H2).

- **VLC fault tolerance (`DesktopDhunPlayer.kt`):** `MediaPlayerFactory` now constructed inside `try/catch`; `vlcAvailable` gates every op; init failure writes `dhun-vlc-error.log` and sets `PlaybackState.Error` with `install VLC (https://www.videolan.org/vlc/)` guidance; the app continues (window + tray + SMTC remain usable).

- **Startup diagnostics (`Main.kt`):** `Thread.setDefaultUncaughtExceptionHandler`, probes for `java.sql.Driver`/`org.sqlite.JDBC`/`vlcj` availability, logs to `<installDir>/userdata/dhun-startup.log` (fallback `%TEMP%`/`dhun-startup.log`) with OS/Java/jpackage.app-path/stacktrace, shows AWT `JOptionPane` dialog on failure, and can open a minimal error `Window` if Koin/DataLayer fails before the main window (previously the launcher's generic message was the only signal). `DataLayer` creation now tries file DB then in-memory fallback and logs both.

**CI evidence:** PR #22 `34011326728` passed shared tests + Android + probe + Desktop compiles; `test-release` `34011563630` on `main@e90dba6` (merge commit) built and published `dhun-test.msi` (5m13s) + `dhun-test.apk` (4m33s) — the MSI that previously would have launched with the generic error now bundles the required modules. Post-fix docs merge PR #23 (`main@9294520`) re-ran both green — CI `34012157207`, test-release `34012157287` — and republished the identical `1.0.5` binaries (MSI 112,001,488 B, APK 17,467,038 B) at `2026-09-06T04:45:40Z`; the `test` tag pointed at `9294520` at that historical checkpoint (superseded by `0920148` / 07:22:29Z).

**Remaining verification (OPEN — requires Windows hardware):** install `dhun-test.msi` from the rolling `test` pre-release (last verified tag `0920148`, 07:22:29Z; record the current identity, because `test` is rolling) on a clean Windows user/VM (accept SmartScreen), launch DHUN, confirm:

- No `Failed to launch JVM` — window opens, tray icon appears.
- `dhun-startup.log` (in `<installDir>/userdata` or `%TEMP%` if dataDir not yet created) contains `java.sql.Driver available` + `org.sqlite.JDBC available` + `VLC initialized` (or `VLC init failed` → graceful Error state with VLC install hint, not a crash).
- If VLC installed: play a track → audio audible; if VLC missing: player shows `VLC not found` Error but window remains responsive and tray works.
- Record OS/Windows build, VLC version, MSI size (112 MB), and log excerpts here.

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
- (Removed with the mini-player window, ADR-004: the two-window
  close-interaction note — DHUN now has a single window, so close-to-tray +
  tray Quit is the only window pattern.)
- Tray on Wayland/X11 without a system tray (e.g. GNOME default without
  an extension) = `SystemTray.isSupported()` false → no tray, app works.

## Screenshots (to capture on hardware)

- Tray: paused vs playing icon + open menu (track title row visible)
- One main window with docked MiniPlayer and taskbar (no separate mini-player); restored main window after tray Open
- Main window restored to previous geometry (before/after relaunch)
- Console: `SMTC probe …` line (PASS or the exact FAIL step)
