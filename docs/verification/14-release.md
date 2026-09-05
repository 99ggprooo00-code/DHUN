# Phase 14 verification — Robustness, Rot-Drill, Release

Status: 🟨 **ROT-DRILL WORKFLOW WIRED; ALL HARDWARE AND RELEASE ACCEPTANCE
OPEN**. This document is an evidence log, not a claim that Phase 14 is done.
CI compilation is not a substitute for the live extraction, Android soak,
Desktop soak, clean-install, or release checks below.

## Phase 14 implementation status

| Step | Current status | Evidence / remaining gate |
|---|---|---|
| Typed error taxonomy and actionable user messages | 🟨 Existing `DhunResult`/`DhunError` paths and playback recovery are in place; complete network/db/playback sweep and offline-banner review remain | `shared/src/commonMain/kotlin/dev/dhun/core/DhunResult.kt`, `app-android/.../PlaybackGraph.kt`; manual error-path review OPEN |
| Bounded audio cache and offline replay | ⬜ Not implemented | Android currently has a 5-hour stream-URL cache with 403 invalidation; played audio segment caching and offline replay are still required |
| Daily live rot-drill | 🟨 Workflow wired in `.github/workflows/rot-drill.yml` | Requires a successful scheduled or manual live run; CI compile does not exercise YouTube/network extraction |
| Android 30-minute soak | ⬜ Open | Requires a physical device with unrestricted battery mode, lock-screen playback, and zero-crash/leak evidence |
| Desktop 30-minute soak | ⬜ Open | Requires a desktop with libVLC and tray/SMTC-capable runtime |
| Release v0.1.0 artifacts | ⬜ Open | Rolling `test` APK/MSI is not the signed/stable v0.1.0 release; clean-target installation and release evidence are required |

## Rot-drill procedure

The scheduled workflow runs daily at `04:17 UTC` and can be started manually
from GitHub Actions. It performs the real Phase 01 path:

1. Install JDK 17 and `yt-dlp` on a fresh Ubuntu runner.
2. Run `:tools:playback-probe:run` using the shared InnerTube client.
3. Search for a known song, resolve an audio URL, fetch and validate audio
   bytes, and fetch related tracks.
4. Record the full log as a 14-day workflow artifact.
5. Open or update one `[rot-drill]` issue when the probe fails; automatically
   close that issue after a later green run.

The NewPipe Extractor watch line is intentionally non-fatal. The production
Desktop path uses the ADR-001 yt-dlp fallback while NewPipe remains monitored
for upstream recovery.

## Live evidence log

### Rot-drill

- [ ] Manual run from the Phase 14 branch completes with `PROBE|verdict|PASS`.
- [ ] First scheduled run completes on the default branch.
- [ ] Failure path creates or updates one `[rot-drill]` issue and uploads the
      log artifact.
- [ ] Recovery path comments on and closes the open issue.
- Run URL / date: ____________________
- Verdict line: ____________________
- Issue number (if exercised): ____________________

### Android soak

- Device / Android version: ____________________
- Battery mode / OEM settings: ____________________
- APK / commit: ____________________
- Start and end timestamps (30 minutes): ____________________
- Lock-screen and notification controls: ____________________
- Rotation/back stack/shortcut result: ____________________
- Crash / leak result: ____________________
- Screenshots or logcat location: ____________________

### Desktop soak and clean install

- OS / version / libVLC version: ____________________
- MSI / commit: ____________________
- Start and end timestamps (30 minutes): ____________________
- Tray / mini-player / keyboard / SMTC result: ____________________
- Clean-install result: ____________________
- Crash / zombie-process result: ____________________
- Screenshots or logs: ____________________

### v0.1.0 release gate

- [ ] Rot-drill is scheduled and has a green live run.
- [ ] Android APK and AAB build and install on a clean target.
- [ ] Windows MSI installs and launches on a clean Windows VM/user.
- [ ] Android and Desktop soak evidence is attached above.
- [ ] `KNOWN_LIMITATIONS.md`, `THIRD_PARTY.md`, `RISK_REGISTER.md`, README,
      and CHANGELOG are current.
- [ ] Release is tagged `v0.1.0` only after all required evidence is real.
