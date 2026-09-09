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
   RiMusic, InnerTune and others. Under accepted ADR-001, DHUN currently
   uses its **own sequential InnerTube player-client chain**, with an
   optional **user-provided yt-dlp desktop fallback** while the pinned
   NewPipe engine remains a non-fatal recovery watch, not the production
   primary. Metadata also uses the own client. A **daily CI rot-drill**
   validates resolution and actual audio bytes against live YouTube.
2. **Code-first.** The previous attempt produced great documents and an app
   that never attempted its core mission. Every phase here ships running
   code on real hardware before it is "done."

## License

GPL-3.0 — see THIRD_PARTY.md.

## Screenshots

*Coming soon — home and player from test build.*

## Contributors

Thanks to everyone testing early builds.

## Build

Requires JDK 17 and an Android SDK (`ANDROID_HOME`).

```bash
./gradlew :app-android:assembleDebug   # Android debug APK
./gradlew :app-android:bundleDebug     # Android debug App Bundle (AAB, same signing)
./gradlew :shared:jvmTest              # domain + parser + queue + data-layer unit tests
./gradlew :tools:playback-probe:run    # extraction probe (needs PYTHONPATH w/ yt-dlp for the resolve step)
./gradlew :tools:playback-probe:run -PmainClass=dev.dhun.tools.smoke.SmokeMainKt  # live provider smoke
./gradlew :app-desktop:run             # system libVLC; optional yt-dlp fallback (see below)
./gradlew :app-desktop:compileKotlinJvm  # desktop compile check (what CI should run)

# Live Phase 14 rot-drill (requires network + yt-dlp on PATH or Python module)
./gradlew :tools:playback-probe:run --no-daemon
```

Outputs: APK
`app-android/build/outputs/apk/debug/app-android-debug.apk`, AAB
`app-android/build/outputs/bundle/debug/app-android-debug.aab`. Both are
signed with the committed **public test keystore**
(`app-android/keystores/dhun-test.p12`, see the *Release build* section for
the exact sign/verify/install commands and what that key is — and is not —
for).

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

### Branch candidates without publishing a release

The existing `test-release` workflow also supports a manual **build-only**
run on a session branch. `build_only` defaults to true; non-main refs cannot
run the publish job even if that input is turned off. Builds use read-only
repository permissions. APK/MSI ZIP artifacts are retained for 14 days in
Actions (GitHub sign-in may be required), separate from the single rolling
`test` release. No new branch, PR or release is needed.

PRs to main also run these package checks before merging, without publishing.
Only use an MSI that passed the native sentinel checks. Packaging finalization
in `scripts/stage_msi.ps1` applies the upgrade-data policy before checksums;
raw `:app-desktop:packageMsi` output alone is not a distribution-ready update.

Each ZIP includes the binary, SHA256 and a `*.build-info.json` with its exact
source SHA/run and internal MSI version. The Windows job reads the actual
MSI's version/upgrade identity and checks install-over/data preservation on
a disposable hosted Windows runner; that is not a user-machine/audio test.
Use [the short Windows candidate guide](docs/verification/windows-candidate.md)
when a successful build link is supplied. Do not download the old public
release expecting unmerged branch fixes.

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
  folder). Packaged runtime data is intended to live under
  `<installDir>/userdata` (SQLite + audio cache), not `%APPDATA%\DHUN`,
  and be removed with the program. Clean-target cleanup and in-place
  upgrade data preservation still require hardware verification.
- The MSI is **not Authenticode-signed** — SmartScreen will warn
  ("Windows protected your PC"). That is expected for a test build,
  not a virus. Needs a system VLC/libVLC install for playback; DHUN
  does not install or uninstall VLC.
- The desktop fallback is **not bundled**. If needed, install the official
  [yt-dlp Windows executable](https://github.com/yt-dlp/yt-dlp/releases/latest)
  on PATH, or set `DHUN_YTDLP` to its full executable path, then restart DHUN.
  A standalone `yt-dlp.exe` does not need Python. VLC and yt-dlp perform
  different jobs; installing VLC alone does not install an extractor.
  The candidate resolver ignores user yt-dlp config/cookies. If audio
  fails, open **Playback details** and copy the bounded diagnostic plus
  track ID; do not share cookies, signed stream URLs or credentials.
- Public tag/asset names stay `test` / `dhun-test.msi`. The **internal MSI
  ProductVersion must increase**: `scripts/installer_version.py` maps
  workflow run and attempt to a valid numeric version; CI supplies it via
  `-PdhunInstallerVersion=…`, keeping the upgrade UUID stable. For manual
  packaging, use a version higher than the installed build (the local
  default 1.0.6 is not higher than future CI versions). Do not reset MSI
  ProductVersion to the app's `0.1.0` semver. Quit DHUN/tray before updating.
- **Repair build available (2026-09-06):** PR #30 merged the Windows/Home/
  player repairs. Main CI and rolling test publishing passed, including
  native MSI install-over/data-preservation and uninstall sentinels. The
  first verified release of this repair code was **11:58:17Z / `76c68eb`**,
  internal MSI **1.36.1**; the rolling release can advance with later builds.
  Use the current checksum/build identity. This is **not** proof of actual
  sound, live Home or visual/native behavior on your machine; those checks
  remain open. Quit DHUN/tray and back up userdata before updating. Report
  Playback **Details** if an uncached song still fails.
- Unsigned / SmartScreen + debug APK are why these are not
  daily-driver builds. Source of both artifacts is this repo via
  `.github/workflows/test-release.yml`.

## Release build — v0.1.0 candidate (build / sign / run)

> **Status 2026-09-07:** the v0.1.0 release pipeline is *prepared, not
> published*. Candidate artifacts are **debug/test-grade** (debug-keystore
> APK + AAB, unsigned MSI), and **no `v0.1.0` tag or GitHub Release is
> public**. A private **DRAFT** release may exist; publishing requires the
> Phase 14 gates in `docs/verification/14-release.md` **and** the user's
> explicit go-ahead. Hosted-Windows install-over checks are NOT proof of a
> real upgrade, launch, audio, or hardware behavior.

### Artifacts, and how each is produced

| Asset | Gradle/CI job | Raw output path | Signing |
|---|---|---|---|
| `dhun-v0.1.0.apk` | `:app-android:assembleDebug` (CI `apk`) | `app-android/build/outputs/apk/debug/app-android-debug.apk` | `testBuild` debug keystore (below) |
| `dhun-v0.1.0.aab` | `:app-android:bundleDebug` (CI `aab`) | `app-android/build/outputs/bundle/debug/app-android-debug.aab` | same `testBuild` debug keystore |
| `dhun-v0.1.0.msi` | `:app-desktop:packageMsi` (CI `msi`) | under `app-desktop/build/compose/` | not Authenticode-signed |

**Android signing facts.** With no explicit config AGP would sign each debug
build with a throwaway per-machine `~/.android/debug.keystore`, so every CI
build would get a *different* signature and refuse to update an existing
install. The `debug` build type therefore points at the committed public
throwaway key `app-android/keystores/dhun-test.p12` (store/key password
`android`, alias `androiddebugkey`); `bundleDebug` inherits it, which is why
the candidate AAB is signed too. This key is **not** a store key: the
`release` build type has no signing config yet, and a real Play/Store upload
needs its own keystore — do not mint one with this file. Anyone with the
repo can sign a same-key APK, so only install artifacts from the repo's own
CI URLs.

### Versions and the MSI ProductVersion rule

- There is **no `gradle/libs.versions.toml`** in this repo (no version
  catalog). Versions are pinned in the module build files.
- App semantic version: `versionCode`/`versionName` in
  `app-android/build.gradle.kts` (currently `5` / `0.1.4` — independent of
  the `v0.1.0` tag; aligning them for a store upload is an `app-android`
  change outside this candidate). The release tag is `v0.1.0`.
- **MSI ProductVersion is an installer sequence, NOT the app version.**
  Windows compares it only to decide upgrades. It must strictly increase on
  every build/rerun and never be reset to `0.1.0`. CI allocates it from the
  workflow run counter via `scripts/installer_version.py`
  (`major 1..255.minor 0..255.build 0..65535`); the `upgradeUuid`
  (`31ddb86b-9666-4071-b11c-45f16fa4682d`) stays stable forever.

### Reproduce in CI (recommended, repeatable)

1. GitHub → Actions → **test-release** → **Run workflow**.
2. Ref: `main` for the real candidate (or a session branch for artifacts).
3. Tick `build_release_candidate`; tick `publish_v010_draft` only when you
   want the private DRAFT `v0.1.0` release refreshed with the assets.
   Leave `build_only` at its default (artifact-only).
4. Green jobs: `apk` + `msi` (includes the disposable-Windows install-over
   sentinel check) + `aab`; plus `release_draft` when requested. The rolling
   `test` release is **not** touched by these inputs.
5. Result: 14-day artifact ZIPs, or the DRAFT release `v0.1.0` carrying
   `dhun-v0.1.0.{apk,aab,msi}` + `.sha256` + `.build-info.json` (source
   commit, run URL, MSI ProductVersion).

The draft job re-stages provenance under the release asset names and
verifies every binary came from the same run/commit. It replaces an older
**draft** but refuses to touch an already-**published** `v0.1.0`.

### Publish v0.1.0 — only after the user's go-ahead

1. Close the Phase 14 release gates (`docs/verification/14-release.md`):
   green live rot-drill verdict, Android + Desktop soaks, clean-target
   installs of all three artifacts.
2. Run the workflow on `main` with `publish_v010_draft = true` and verify
   the draft's assets/notes.
3. Publish: `gh release publish v0.1.0` (or the GitHub UI). Publishing
   creates the public `v0.1.0` tag. Then finalize `CHANGELOG.md` (drop the
   DRAFT markers, add the version compare link), review
   `.ai/KNOWN_LIMITATIONS.md`/`THIRD_PARTY.md`, and record verification
   evidence.

### Manual Android build, verify signature, install

```bash
./gradlew :app-android:assembleDebug :app-android:bundleDebug --no-daemon
# verify the APK signature (Android SDK build-tools):
apksigner verify --print-certs app-android/build/outputs/apk/debug/app-android-debug.apk
adb install -r app-android/build/outputs/apk/debug/app-android-debug.apk

# AAB test install via bundletool (key = the committed test keystore):
java -jar bundletool.jar build-apks \
  --bundle=app-android/build/outputs/bundle/debug/app-android-debug.aab \
  --output=dhun-v0.1.0.apks \
  --ks=app-android/keystores/dhun-test.p12 --ks-pass=pass:android \
  --ks-key-alias=androiddebugkey --key-pass=pass:android
java -jar bundletool.jar install-apks --apks=dhun-v0.1.0.apks
```

If a tool rejects the `.p12`, convert it once with `keytool -importkeystore`
into a JKS using the same alias/passwords. The AAB is debug-keystore-signed
and is **not** Play-upload ready.

### Manual Windows MSI (needs a Windows host with WiX; see caveats)

```powershell
# 1. Allocate a ProductVersion strictly higher than anything already installed:
python scripts/installer_version.py $env:GITHUB_RUN_NUMBER $env:GITHUB_RUN_ATTEMPT
# 2. Build:
./gradlew :app-desktop:packageMsi "-PdhunInstallerVersion=1.99.1" --no-daemon
# 3. FINALIZE before distributing (upgrade-data policy + identity check + checksums):
./scripts/stage_msi.ps1 -ExpectedVersion 1.99.1 -BuildOnly true
```

**Manual packaging caveats**

- jpackage produces MSIs via the **WiX Toolset**; GitHub's Windows runner
  has it installed — a local Windows machine needs WiX 3.x installed and on
  PATH or `:app-desktop:packageMsi` fails.
- Raw `:app-desktop:packageMsi` output is **not** distribution-ready:
  `stage_msi.ps1` (via `patch_msi_upgrade.ps1`) applies the installer
  upgrade-data policy and verifies ProductVersion/UpgradeCode/ProductName
  before checksums are written. Never hand out an unfinalized MSI.
- Pick a ProductVersion higher than the installed build (query a candidate
  with `scripts/msi_helpers.ps1` → `Get-DhunMsiProperties`); the local
  default `1.0.6` is not higher than recent CI versions. Do **not** reset
  ProductVersion to `0.1.0`.
- The MSI is per-user and unsigned (SmartScreen warns); quit DHUN/tray
  before installing; back up `<installDir>/userdata` before an in-place
  upgrade; playback needs a system VLC install.

### Verify checksums

```bash
sha256sum dhun-v0.1.0.apk dhun-v0.1.0.aab dhun-v0.1.0.msi   # Linux/macOS
# Windows: Get-FileHash dhun-v0.1.0.msi -Algorithm SHA256
```

Compare against the `.sha256` assets; `*.build-info.json` records the exact
source commit, workflow run URL and MSI ProductVersion, so a checksum match
also pins provenance.

## Repo map

- `CHANGELOG.md` — Keep-a-Changelog; no versioned release yet; the planned
  `[0.1.0]` first-release notes are drafted there (marked DRAFT)

- `.ai/` — agent operating files (moved out of the project root 2026-09-05):
  - `.ai/MASTER_PROMPT.md` — the 14-phase engineering plan (the contract)
  - `.ai/PROMPT_SEQUENCE.md` — audit of the original 30-phase prompt set + rewritten prompts
  - `.ai/PROBLEMS_AND_FIXES.md` — audit of the original plan + evidence
  - `.ai/RISK_REGISTER.md` — what will go wrong and the pre-agreed responses
  - `.ai/ROADMAP.md` — live phase status (CURRENT ACTIVE TASK at the top)
  - `.ai/KNOWN_LIMITATIONS.md` — honest gaps, updated every phase
  - `.ai/DEBUG_LOG.md` — incidents: stack → root cause → fix
  - `.ai/README.md` — boot protocol + permanent maintenance contract
