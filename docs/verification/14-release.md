# Phase 14 verification — Robustness, Rot-Drill, Release

Status: 🟨 **IN PROGRESS — WINDOWS UPGRADE, PLAYBACK, HOME AND UI BLOCKERS.**
Current GitHub `main` / `test`: **`0920148`**, CI **34018809911** and publishing
**34018809913** green; `test` published **2026-09-06T07:22:29Z** (MSI
112,009,680 B; APK 17,483,422 B; checksum assets present). Those green runs
verify the previously published code, **not** the repair batch below.

The fresh Windows report confirms **one-window startup after manual
uninstall/reinstall**, but rejects install-over upgrade, audio, Home
pagination and parts of the player layout. New source/test/doc repairs on
`arena/01a0759b-dhun` are **pushed and CI-green at `75c4a8b`**, verified by
[run 34025807972](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972). No PR, merge or release is authorised.
No newer installer has been produced. Hardware, live extraction, soaks,
clean-target hygiene and v0.1.0 cannot be inferred from build CI.

## Phase 14 implementation status

| Step | Current status | Evidence / remaining gate |
|---|---|---|
| Typed error taxonomy and actionable user messages | 🟨 Typed `DhunResult`/`DhunError` + `toUserMessage` paths, per-request retry, 429 global backoff gate (`2932d57`, with unit tests), and offline banner (`fed1d54`) are merged with recovery UX; baseline CI `34018809911` is green. Local reason-preserving diagnostics changes await CI; offline/429/403 hardware checks and db-path review remain | `shared/.../core/RateLimitGate.kt`, `shared/.../core/ConnectivityMonitor.kt`, `DhunAppShell.kt`, hosts' Koin modules |
| Bounded audio cache and offline replay | 🟨 Android + Desktop code | Android: Media3 `SimpleCache` LRU via `DhunAudioSegmentCache` + `CacheDataSource` (stable video-id keys). Desktop: `AudioFileCache` whole-track LRU files under `<data dir>/cache/audio`, background fill during first play, local-file playback on hit (no resolve → offline). Both use `SettingsKeys.CACHE_SIZE_MB` default 1024 MB (`AudioCacheBudget`). URL TTL cache still `DhunStreamCache`. Unit tests: `AudioFileCacheTest` (9: hit/LRU victim/over-budget/short-read/cancel/unsafe id/partial sweep/shrink+clear). Hardware offline-replay check OPEN on both |
| Daily live rot-drill | 🔴 Latest verified run **34011539225**, scheduled on `dd1ab31`, failed; metadata PASS; own-client / production aggregate Unavailable; yt-dlp WATCH separately AuthRequired | Schedule, issue #14 alert and artifact `rot-drill-34011539225` proven. No newer live verdict, green byte check or recovery auto-close |
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

- [x] **Failure path exercised for real — run 33961533965 (2026-09-05,
      workflow_dispatch on `a554594`, job 101295458477): FAILED as
      designed.** Verdict line: `PROBE|verdict|FAIL|extraction-pipeline-broken`.
      Root cause: yt-dlp's default player path was bot-gated
      ("Sign in to confirm" → `AuthRequired(detail=null)`) from the Actions
      runner's datacenter IP, while InnerTube metadata (version/search/
      related) passed in the same run — i.e. YouTube player-endpoint
      datacenter-IP gating, not extractor-shape rot. Issue [#14](https://github.com/99ggprooo00-code/DHUN/issues/14)
      auto-opened with the log tail; artifact `rot-drill-33961533965`
      uploaded; kill-switch step fired. Secondary defects found and logged
      in `.ai/DEBUG_LOG.md`: probe gated the verdict on the desktop-fallback
      engine only (not the production own-client→yt-dlp chain), yt-dlp
      stderr evidence was dropped, and the issue body swallowed the artifact
      name through a bash backtick bug.

- [x] **Second live dispatch on wrong ref — run 33968612285 (2026-09-05,
      workflow_dispatch on `main` @ `a554594`): FAILED, identical pattern.**
      Same `AuthRequired(detail=null)` / no `WATCH|own-client`. Confirms the
      user UI defaulted to main (pre-fix). Kill switch step "Fail the
      workflow after alerting" is intentional. Issue #14 updated with
      diagnosis comment. **Not a regression of PR #16** — that code was not
      checked out.


- [x] **First live run on the FIXED branch — run 33968950214 (2026-09-05,
      workflow_dispatch on `arena/01a07170-dhun` @ `10ad025`): FAILED as
      designed (kill switch).** Production chain + per-engine WATCH lines
      fired. Evidence:
      - `WATCH|own-client|BROKEN|AuthRequired(web_remix/visionos/tv all
        AUTH_REQUIRED Sign in to confirm you're not a bot)`
      - `WATCH|ytdlp|BROKEN|AuthRequired(...Sign in to confirm you're not a
        bot... --cookies...)` with yt-dlp **2026.08.19** in the artifact
      - `PROBE|related|PASS|50` + search/version PASS ⇒ metadata healthy
      - Artifact name `rot-drill-33968950214` correctly present in issue #14
      - Classification: **category 8 CI/datacenter-IP bot gating of BOTH
        production engines** — not shape rot. Residential verification OPEN.
      - Do **not** convert this red into a pass.


- [x] **Expanded-chain live run — 33970045379 (2026-09-05, `d9f4083`):** FAIL.
      `WATCH|ytdlp` still AuthRequired on default messaging, but
      `resolve+stream` reached a **real googlevideo URL (itag 251)** then
      **HTTP 403** on the range byte-fetch from the Actions IP. Own-client
      WATCH reported `Unavailable`. Metadata PASS. Progress: no-URL →
      URL-then-CDN-403. Kill switch OK. Do not drop byte verification.

- [x] **Scheduled default-branch execution verified — run 34011539225,
      2026-09-06, `schedule`, `dd1ab31`: FAILED.** Artifact
      `rot-drill-34011539225` (3,241 B) exists. Issue #14's
      [04:29:14Z comment](https://github.com/99ggprooo00-code/DHUN/issues/14#issuecomment-5556894160)
      preserves the output: version/search/related PASS; own-client WATCH
      Unavailable; yt-dlp WATCH bot-gate AuthRequired; production resolve+
      stream FAIL / Unavailable. Do not relabel the aggregate as AuthRequired.
      Run: https://github.com/99ggprooo00-code/DHUN/actions/runs/34011539225
- [x] Failure path creates/updates one issue and uploads the log artifact.
- [ ] Manual/scheduled run completes with `PROBE|verdict|PASS`, including
      validated audio bytes. No newer live run for `0920148` was verified.
- [ ] Recovery path comments on and closes issue #14 (still OPEN).

**CI vs user-network evidence:** a red Actions run establishes failure on
that runner, not its unique cause or residential success. The user now
also reports failed Windows audio; do not dismiss it as CI-only gating.
Keep byte validation and the kill switch intact. Authentication/cookie or
identity-scheduling changes require the appropriate approved decision;
ADR-003 remains proposed and the own-client chain remains sequential.

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
- Tray / keyboard / SMTC result (separate mini-player window removed — ADR-004): ____________________
- Clean-install result: ____________________
- Crash / zombie-process result: ____________________
- Screenshots or logs: ____________________

### Windows MSI startup — \"Failed to launch JVM\" (2026-09-06)

**History:** `dhun-test.msi` built from `main@8310383` (PR #20, `34001706159`) installed per-user to `%LOCALAPPDATA%\DHUN` but opening the installed app showed `Failed to launch JVM` — a desktop startup failure, not a packaging failure. No startup fix was published before the handoff; the exception was uncaptured.

**Investigation leads from code review (not yet confirmed then):** `app-desktop/build.gradle.kts` omitted `java.sql` (needed by SQLDelight/JDBC), `DesktopDhunPlayer` init before window (VLC), missing exception capture.

**Fix (PR #22, merged to `main@e90dba6`):**

- `app-desktop/build.gradle.kts` — adds explicit `modules("java.sql", "java.sql.rowset", "java.naming", "jdk.unsupported", "java.management", "java.instrument", "java.desktop", "java.logging", "java.net.http")` plus `includeAllModules = true` (112 MB MSI, `test-release` `34011563630` windows-latest `5m13s`). Bumped `packageVersion` 1.0.4 → **1.0.5** (same `upgradeUuid`, per-user, SmartScreen unsigned — unchanged). This is the documented Compose Desktop fix for sqlite/H2 \"Failed to launch JVM\" (docs: `kotlinlang.org/.../compose-native-distribution.html#including-jdk-modules`; StackOverflow 77675565, 78374398).

- `app-desktop/.../player/DesktopDhunPlayer.kt` — `MediaPlayerFactory` now try/caught; `vlcAvailable` gates all ops; missing VLC degrades to `PlaybackState.Error("VLC not found — install VLC…")` instead of crashing before window.

- `app-desktop/.../desktop/Main.kt` — captures every startup exception: `Thread.setDefaultUncaughtExceptionHandler`, early probes for `java.sql.Driver`/`org.sqlite.JDBC`/`vlcj`, log file `<installDir>/userdata/dhun-startup.log` (fallback `%TEMP%`) with OS/Java/jpackage.app-path + stacktrace, AWT `JOptionPane` dialog + minimal error `Window` if Koin/DataLayer fails before main window, `DataLayer` file-DB → in-memory fallback with logging.

**CI evidence (GitHub-verified, not yet hardware-verified):**

- PR CI `34011326728` — **passed** shared JVM tests, Android debug build, probe compile, Desktop compile (`:app-desktop:compileKotlinJvm`).
- Main CI `34011563632` — **passed** (6m10s) on `e90dba6`.
- Rolling test-release `34011563630` — **passed** `msi` `5m13s` + `apk` `4m33s` + `publish` `19s`; published the `1.0.5` JVM-fix binaries to the `test` pre-release at `2026-09-06T04:33:36Z`.
- Rolling test-release `34012157287` on `main@9294520` (PR #23, docs-only) — **passed**; re-published the same `1.0.5` binaries at `2026-09-06T04:45:40Z`: `dhun-test.msi` 112,001,488 bytes + `dhun-test.msi.sha256`, `dhun-test.apk` 17,467,038 bytes + `dhun-test.apk.sha256` (verified with `gh release view test`). Main CI on the same commit: `34012157207` — **passed**.
- **The `test` tag now points at `9294520`, not `e90dba6`** — the binaries are unchanged, so either checksum set matches the JVM-fix build. Verify before testing; any further push to `main` replaces these assets.

**Hardware gate still OPEN — to verify on a Windows machine:**

1. Download the current `dhun-test.msi` + `dhun-test.msi.sha256` from `https://github.com/99ggprooo00-code/DHUN/releases/tag/test`; verify the checksum, record the actual release SHA and published time (last verified `0920148` / `07:22:29Z`; the rolling asset can change).
2. Install per-user (no admin) — accept SmartScreen **Run anyway** / **More info → Run anyway** — confirm install completes without admin UAC.
3. Launch DHUN from Start menu / installed shortcut — **no** `Failed to launch JVM`; exactly one window opens: the main window (1200×780) with the docked mini-player above the bottom nav, plus the tray icon. (The separate mini-player window was removed — ADR-004, 2026-09-06.)
4. Check `dhun-startup.log` (packaged: `<installDir>/userdata/dhun-startup.log`; fallback: `%TEMP%\dhun-startup.log`) —
   - contains `DHUN main starting` + `java.sql.Driver available` + `org.sqlite.JDBC available` + `VLC initialized` (or `VLC init failed` → graceful Error state, not crash).
   - no `ClassNotFoundException: java.sql` or `UnsatisfiedLinkError: libvlc`.
5. If VLC is installed: play an uncached search result → audible audio; tray icon switches; if VLC is **not** installed: player shows `VLC not found — install VLC…` Error but app stays responsive (tray/close-to-tray still work).
6. Clean uninstall: Settings → Apps → DHUN → Uninstall → confirm `<installDir>/userdata` is removed; VLC remains (not ours).

Record here: Windows version/build, VLC version (or \"not installed\"), MSI size/sha256, `dhun-startup.log` excerpts (sanitized), and whether launch succeeded. **Successful CI packaging is not launch verification.**

### Hardware reports — 2026-09-06

**Earlier report:** APK and MSI installed/launched, confirming the previous
JVM-launch recovery on the user's machines; audio failed on both. PR #24
subsequently propagated the resolving User-Agent and added Home continuation,
PR #25 added a resolve budget/diagnostics, and PR #26 restyled the UI. Those
changes are on GitHub and in the 07:22:29Z release, but are **not proof of
successful audio or accepted UI**. The earlier attribution of all no-audio
to User-Agent mismatch was too strong without device stream evidence.

**Fresh Windows report, in response to the 07:22:29Z build recommendation:**

| Check | User-observed result |
|---|---|
| Install over existing DHUN | **FAIL** — “Another version of this product is already installed. Installation of this version cannot continue. To configure or remove the existing version of the product, use Add/Remove Programs on the Control Panel.” |
| Manual uninstall, then reinstall / launch | **PASS as reported** — one window opens. This is not a clean-VM or successful in-place upgrade test |
| Playback | **FAIL** — “This track is not available right now.” Screenshot: **Ko Cha Ra (Official Audio) — John Rai**, **0:00 / 4:49**, Retry, no useful diagnostic detail |
| Home | **FAIL** — endless/further-page scrolling still unavailable |
| Player appearance | **NOT ACCEPTED** — styling somewhat better; glyph positions, shuffle shape and shuffle/next/previous/repeat colours wrong. Artwork/controls appear oversized/spread across the window |

Screenshot evidence was supplied in the conversation (`Screenshot 2026-09-06
132629.png`), not copied into this checkout. No verified installer SHA256,
track ID, Windows/VLC versions or sanitized current logs were supplied. No
fresh Android result accompanied this Windows report. Only the reported
one-window launch check is closed; tray/SMTC/shortcuts remain unverified.

### Branch repair candidate — arena/01a0759b-dhun (CI ONLY, NOT RELEASED)

**First branch CI result:** [34025629231](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025629231),
`f914050`, push event, **FAIL**. JDK setup and Python checks passed; shared
Kotlin compilation failed with four `Unresolved reference 'index_'` errors
in `HomeScreen.kt`. Kotlin tests did not execute; Android/probe/Desktop steps
were skipped. Fix: delimit `${index}` in the shelf keys. Follow-up also wires
the Quick-picks visibility predicate into the actual category projection and
adds a regression, and upgrades CI checkout to its Node-24 v5 runtime after
the run reported the v4 Node-20 warning. The rerun below is green; no release/PR.

**Verified corrected branch run:** [34025807972](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972) at
**`75c4a8b9e6b3a030d24a360b0cb98923a4de5a0f`**, completed **09:57:34Z on
2026-09-06**, job **101466441642** (6m13s), **SUCCESS**. Run, job steps and
check annotations were verified via GitHub REST APIs:

| Check | Actual result |
|---|---|
| JDK 17 / checkout v5 setup | PASS |
| Installer + fixture Python tests | PASS |
| `:shared:jvmTest` | PASS — includes new parser/paging, resolver lifecycle, diagnostics, transport and raster/layout regressions |
| `:app-android:assembleDebug` | PASS — built, not device-tested or published |
| `:tools:playback-probe:compileKotlin` | PASS — compilation only, not a live extraction run |
| `:app-desktop:compileKotlinJvm` | PASS — no MSI/native runtime test |
| Check annotations | 0 — not a separate full-log compiler-warning audit |

This proves the automated **branch code/test checkpoint**, not actual audio,
Windows upgrades or visual acceptance. `main` / `test` remain `0920148`, the
public download remains the 07:22:29Z build, and there are no open PRs.

| Area | Source repair / regression coverage added |
|---|---|
| MSI identity | Replace constant 1.0.5 with `dhunInstallerVersion`; CI uses `(1 + run/256).(run%256).attempt`, bounded to MSI numeric limits; local default 1.0.6. Run 33/attempt 1 would be 1.33.1. Keep upgrade UUID `31ddb86b-9666-4071-b11c-45f16fa4682d` and `dhun-test.msi`; reject superseded-ref publishing; log installer version. A future stable packager must continue the internal sequence, not reset it to app semver |
| Desktop extraction | Locate `yt-dlp.exe` on Windows Path / explicit `DHUN_YTDLP`; discover real Python/`py` fallback without Unix `which` or launching Store aliases. Missing tool gets an actionable diagnostic, not Network. Drain both pipes while waiting, retain bounded output, interrupt waits and dispose child processes on cancellation; ignore user yt-dlp config to preserve anonymous/no-cookie operation |
| Failure evidence | Network/Unavailable/429 can retain details; preserve playability reason/subreason, every completed own-client outcome and both engine failures. Bound/sanitize external text and redact URLs. Timeout names the active engine and any completed primary failure. No scheduling fan-out |
| Home parsing / transport | Select only feed-level section-list continuation tokens; support list continuations and append/reload actions/commands. Keep the request body's client version aligned with its header on the first request too |
| Home data / state / UI | Same title plus fresh item IDs is new content. Follow advancing empty/duplicate pages, but stop token cycles and pause after three no-growth pages. Claim loading before dispatch; refresh invalidates stale pages. Keep failed feed visible with explicit retry; show honest end state. Indexed keys and later quick-picks shelves do not hide fresh tracks |
| Player graphics / diagnostics | Scale SVG paths about the origin; canonical Apache-2.0 Material shuffle/repeat paths. Fit artwork to both available axes; centre bounded transport/volume; consistent inactive tint and active toggle treatment. Same full, scrollable/selectable Playback details dialog from either player; docked MiniPlayer retained |

**Local checks and the original environment blocker (distinct from CI above):**

- **PASS:** `python3 -m unittest discover -s scripts -p 'test_*.py' -v` —
  **10 tests**: five installer tests (legacy 1.0.5 upgrade ordering, reruns,
  run ordering, numeric rollover/limits and invalid inputs) and five strict
  fixture-validator tests (valid/malformed/duplicate-key/non-JSON-number
  handling plus validation of checked-in inputs).
- **PASS:** `git diff --check`; `python3 scripts/validate_fixtures.py` —
  **29 JSON fixture files**, including **17 new synthetic Home response
  cases** consumed by the Kotlin tests. These are static/helper checks,
  not Kotlin parser tests or application execution.
- **BLOCKED before Gradle started:**
  `./gradlew :shared:jvmTest :app-desktop:compileKotlinJvm --no-daemon` →
  `JAVA_HOME is not set and no 'java' command could be found in your PATH.`
  Maven, Gradle distribution and Adoptium requests also failed with
  `SSL_ERROR_SYSCALL`. The follow-up official Temurin JDK-17 download through
  GitHub also failed at `release-assets.githubusercontent.com` with EOF.
  No usable JDK or Gradle dependencies were downloaded/installed.
- **Now PASS in GitHub CI 34025807972, not run locally:** Windows tool lookup/missing module/Store aliases,
  pipe back-pressure and cancellation tests; error aggregation and fallback
  evidence; scoped continuation/response-shape and first-request tests;
  repeated-title/empty-page/cycle/retry/stale-refresh tests; headless
  icon raster bounds at 18–64 px and artwork dimension tests.
- Candidate CI, shared tests and Android/probe/Desktop builds passed as
  recorded above. There is **no new MSI package, published APK/MSI, live
  extraction, Windows install or audible playback proof**. Publication is deferred until the
  user separately authorises it after verified CI. The earlier **Keep
  everything local** decision was superseded by explicit permission for
  **commit/push and CI only**. Do not open a PR, merge or publish a release.

**Local follow-up review:** Home now keeps different action targets separate,
prefers full-page section lists over unrelated actions, accepts same-target
split updates and rejects ambiguous targets. Previous/Next cancellation no
longer routes through a tap; hold cleanup is in `finally`, and scrubbing is
reset on track/duration changes. yt-dlp's own deadline includes pipe EOF,
cleanup runs once, start denial is typed, and a track ID containing `429`
is not a rate limit. Corresponding Kotlin regressions now **PASS in the
verified branch run**. None of this closes playback, UI or installation acceptance.

### Build-only packaging follow-up (pending execution)

The user clarified that ordinary development is allowed; only the one-time
Arena session-ending action must be avoided. No PR/merge is being used.
The existing packaging workflow now supports `build_only=true` dispatches
on this session branch, with read-only build permissions, isolated branch
concurrency and a job-level `publish` guard requiring main and publishing
mode. Branches cannot publish. The same workflow counter supplies versions,
avoiding a separate artifact workflow's larger counter blocking later MSI
upgrades.

New staging adds exact file checksums and source/run manifests (only
whitelisted non-secret metadata), queries the actual MSI ProductVersion and
stable UpgradeCode, and includes a short Windows guide. A Windows-only CI
script is restricted to disposable Actions runners: download/checksum the
existing release, silently install it, seed userdata/cache sentinels,
install over it, verify those files, and check uninstall cleanup. Full logs
are retained; this does not launch the app or validate sound/visuals.

**Local results:** 19 Python tests pass (including publishing-guard truth
table and artifact provenance tests); 29 JSON fixtures pass syntax checks.
**Packaging dispatch:** pushed at `9317050`, then denied by GitHub with
**HTTP 403: Resource not accessible by integration**. The branch has no
packaging run; no MSI or install-over test ran. Automatic CI 34028039448
started separately. A normal-CI PowerShell syntax check is being added;
that cannot substitute for the actual Windows execution.

Owner action: reconnect GitHub in Arena, or manually run `test-release` from
GitHub Actions using **arena/01a0759b-dhun**, not main, with build-only on.
No new artifact or hardware pass may be claimed until an actual run is
recorded here.

**Next Windows acceptance, only with a CI-green candidate artifact:**

1. Record build SHA, published time, internal MSI version and SHA256.
   Quit all DHUN/tray processes. Back up test userdata before the upgrade
   check; test on a disposable user/VM where possible.
2. Install **over** the previous MSI without manual uninstall. Verify
   library/queue preservation, one-window startup, and no “Another version”
   error. Manual uninstall/reinstall does not pass this test.
3. Play an uncached track and verify actual sound plus advancing position.
   If it fails, open **Details**, select/copy the diagnostic (no signed URLs,
   cookies or tokens), and record track ID, elapsed resolving time,
   Windows/VLC/yt-dlp versions and sanitized `dhun-startup.log` excerpts.
4. Scroll Home through multiple server pages, including repeated shelf
   labels with new music; verify manual retry after a network failure and
   refresh while paging. An upstream null continuation is a real end, not
   an excuse to fabricate an infinite feed.
5. Inspect shuffle/previous/play/next/repeat at common Windows display
   scaling and in a short/wide window. Artwork must not cover controls.
   Then re-check Android using the matching candidate APK.

### v0.1.0 release gate

- [ ] Rot-drill is scheduled and has a green live run (scheduled run still red at `34011539225`; re-investigate after next green).
- [ ] Android APK and AAB build and install on a clean target.
- [ ] Windows MSI installs and launches on a clean Windows VM/user — **published baseline `0920148` launches on the user’s machine; install-over failed and clean-target hygiene is still OPEN**.
- [ ] Android and Desktop soak evidence is attached above (both still OPEN; use an identified candidate that first passes real playback).
- [ ] `KNOWN_LIMITATIONS.md`, `THIRD_PARTY.md`, `RISK_REGISTER.md`, README,
      and CHANGELOG are current (current-report reconciliation is local; final hardware/risk/license/release review still OPEN).
- [ ] Release is tagged `v0.1.0` only after all required evidence is real.

## PR #16 merge (2026-09-05)

Merged to `main` (session `arena/01a07170-dhun`). Code + CI complete for:
taxonomy, Recovering UX, audio-segment cache (Android), M3 glass UI, ADR-002 player polish.

**Still OPEN:** residential rot-drill/stream, Android/Desktop soaks, v0.1.0 artifacts.

## Install / uninstall hygiene (2026-09-06, session arena/01a073c3-dhun)

Code-level audit of the rolling `test` artifacts. Not a malware scan of
the bytes (sandbox cannot fetch GitHub release assets); provenance is
`test-release.yml` on `main`.

| Surface | On install | On uninstall |
|---|---|---|
| Android `dev.dhun.android` | Sideload debug APK, public test key, permissions listed in README. Data only in app-private storage. `allowBackup=false`, `hasFragileUserData=false`, no cleartext HTTP. | Settings / launcher Uninstall deletes `/data/data/dev.dhun.android` (DB + `cache/audio-segments` + Coil). No shared-storage writes exist in the code. |
| Windows MSI | Per-user (`%LOCALAPPDATA%\DHUN`), no UAC. Unsigned → SmartScreen. Needs a preinstalled VLC. | Settings → Apps → DHUN removes the install dir including `userdata/` (DB + `cache/audio`). VLC is left installed (it is not ours). |

Hardware confirmation of the Windows row still OPEN (needs a real MSI
install/uninstall). Android uninstall cleanliness is platform-guaranteed
for private storage.

## Desktop audio cache (2026-09-05, session arena/01a07287-dhun)

Code: `shared/src/jvmMain/kotlin/dev/dhun/player/AudioFileCache.kt`,
`DesktopDhunPlayer` (cache-hit → local file; miss → stream + background
fill; fill cancelled on skip/stop), Koin wiring in `Main.kt`. Test:
`shared/src/jvmTest/.../AudioFileCacheTest.kt`. CI gains a
`:app-desktop:compileKotlinJvm` step (previously desktop only compiled on
main's MSI job).

Desktop offline check to run on a machine with libVLC:
- [ ] Play a track fully → log `DHUN cache: cached <id>`; file exists under
      `<data dir>/cache/audio/<id>.audio`.
- [ ] Disconnect network → play the same track → log `cache hit` and audio
      plays; a non-cached track shows the typed network error.
- [ ] Set `cache_size_mb` small, play several tracks → oldest evicted,
      total stays under budget.
