# DHUN

A serious, cross-platform music application streaming from YouTube Music.
Android (primary) · Desktop via Compose Multiplatform (Windows/Linux/macOS).

> **Status:** Phases 01–11 merged; Phases 12–13 have CI-green code with
> hardware gates open; Phase 14 robustness/rot-drill work is in progress —
> live status in [.ai/ROADMAP.md](.ai/ROADMAP.md). Plan:
> [.ai/MASTER_PROMPT.md](.ai/MASTER_PROMPT.md); why it looks like this:
> [.ai/PROBLEMS_AND_FIXES.md](.ai/PROBLEMS_AND_FIXES.md).

## The two facts that define this project

1. **Extraction is maintenance, not implementation.** YouTube enforces PO
   tokens / SABR; hand-rolled InnerTube extraction is what killed ViMusic,
   RiMusic, InnerTune and others. DHUN wraps **NewPipe Extractor**
   (Android + Desktop) with a **yt-dlp** desktop fallback, keeps its own
   InnerTube client for metadata only, and runs a **daily CI rot-drill**
   against live YouTube.
2. **Code-first.** The previous attempt produced great documents and an app
   that never attempted its core mission. Every phase here ships running
   code on real hardware before it is "done."

## License

GPL-3.0 — required for legitimate reuse of the ecosystem's maintained
extractors (NewPipe Extractor is GPL-3.0). See THIRD_PARTY.md.

## Build (real, as of Phase 03)

Requires JDK 17 and an Android SDK (`ANDROID_HOME`).

```bash
./gradlew :app-android:assembleDebug   # Android debug APK
./gradlew :shared:jvmTest              # domain + parser + queue + data-layer unit tests
./gradlew :tools:playback-probe:run    # extraction probe (needs PYTHONPATH w/ yt-dlp for the resolve step)
./gradlew :tools:playback-probe:run -PmainClass=dev.dhun.tools.smoke.SmokeMainKt  # live provider smoke
./gradlew :app-desktop:run             # desktop app (needs libVLC + yt-dlp; see KNOWN_LIMITATIONS.md)
./gradlew :app-desktop:compileKotlinJvm  # desktop compile check (what CI should run)

# Live Phase 14 rot-drill (requires network + yt-dlp on PATH or Python module)
./gradlew :tools:playback-probe:run --no-daemon
```

APK output: `app-android/build/outputs/apk/debug/app-android-debug.apk`

## Test builds policy

ONE rolling test **pre-release** exists — tag `test`, assets
`dhun-test.apk` (Android) and `dhun-test.msi` (Windows, needs libVLC
installed), auto-replaced on every push to main. No versioned releases for
unfinished builds; nothing in Releases is stable or store-ready. Install
test builds only on devices where that is acceptable (not your daily
phone). Stable URLs:
`https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.apk`,
`…/dhun-test.msi`. What is in a build: [`CHANGELOG.md`](CHANGELOG.md)
(`Unreleased` until `v0.1.0` earns its tag).

### Install / uninstall (test builds)

These are **unsigned/debug test artifacts**, not store releases. Sideload
only on a device you are willing to experiment with.

**Android (`dhun-test.apk`, package `dev.dhun.android`)**
- Permissions: Internet, notifications, media-playback foreground
  service, wake lock, and a one-shot battery-optimisation exemption
  dialog (needed so OEM savers don't kill background music). No
  contacts, SMS, location, camera, microphone, storage, overlay, or
  accessibility.
- `allowBackup=false` and `hasFragileUserData=false` — Android does not
  cloud-backup DHUN data and does not offer to keep it on uninstall.
- All files live in app-private storage (`/data/data/dev.dhun.android`:
  `databases/dhun.db`, `cache/audio-segments`, Coil cache). **Uninstall
  from the launcher or Settings → Apps → DHUN deletes that tree.**
  Nothing is written to shared storage.
- Signed with the public throwaway test key in
  `app-android/keystores/dhun-test.p12` (password `android`) so CI
  updates install over each other. Anyone with the repo can mint a
  same-key APK — only install from the GitHub `test` pre-release URL.

**Windows (`dhun-test.msi`)**
- Per-user install (no Administrator prompt) to `%LOCALAPPDATA%\DHUN`.
  Uninstall: Settings → Apps → DHUN → Uninstall (or Start menu → DHUN
  folder). That removes the program **and** `<installDir>/userdata`
  (SQLite + audio cache). No leftover `%APPDATA%\DHUN`.
- The MSI is **not Authenticode-signed** — SmartScreen will warn
  ("Windows protected your PC"). That is expected for a test build,
  not a virus. Needs a system VLC/libVLC install for playback; DHUN
  does not install or uninstall VLC.
- Unsigned / SmartScreen + debug APK are why these are not
  daily-driver builds. Source of both artifacts is this repo via
  `.github/workflows/test-release.yml`.

## Repo map

- `CHANGELOG.md` — Keep-a-Changelog; no versioned release yet

- `.ai/` — agent operating files (moved out of the project root 2026-09-05):
  - `.ai/MASTER_PROMPT.md` — the 14-phase engineering plan (the contract)
  - `.ai/PROMPT_SEQUENCE.md` — audit of the original 30-phase prompt set + rewritten prompts
  - `.ai/PROBLEMS_AND_FIXES.md` — audit of the original plan + evidence
  - `.ai/RISK_REGISTER.md` — what will go wrong and the pre-agreed responses
  - `.ai/ROADMAP.md` — live phase status (CURRENT ACTIVE TASK at the top)
  - `.ai/KNOWN_LIMITATIONS.md` — honest gaps, updated every phase
  - `.ai/DEBUG_LOG.md` — incidents: stack → root cause → fix
  - `.ai/README.md` — boot protocol + permanent maintenance contract
