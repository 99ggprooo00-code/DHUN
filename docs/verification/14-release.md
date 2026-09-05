# Phase 14 verification — Robustness, Rot-Drill, Release

Status: 🟨 **ROT-DRILL WORKFLOW WIRED; ALL HARDWARE AND RELEASE ACCEPTANCE
OPEN**. This document is an evidence log, not a claim that Phase 14 is done.
CI compilation is not a substitute for the live extraction, Android soak,
Desktop soak, clean-install, or release checks below.

## Phase 14 implementation status

| Step | Current status | Evidence / remaining gate |
|---|---|---|
| Typed error taxonomy and actionable user messages | 🟨 Typed `DhunResult`/`DhunError` + `toUserMessage` paths, per-request retry, 429 global backoff gate (`2932d57`, with unit tests), and offline banner (`fed1d54`) are implemented; CI verdict pending on the current push; 403 "Reconnecting…" UX, offline-banner hardware check, and the db-path review pass remain | `shared/.../core/RateLimitGate.kt`, `shared/.../core/ConnectivityMonitor.kt`, `DhunAppShell.kt`, hosts' Koin modules |
| Bounded audio cache and offline replay | 🟨 Android code | Media3 `SimpleCache` LRU via `DhunAudioSegmentCache` + `CacheDataSource` (stable video-id keys); budget `SettingsKeys.CACHE_SIZE_MB` default 1024 MB (`AudioCacheBudget`); offline serve when resolve fails but spans exist. URL TTL cache still `DhunStreamCache`. Hardware offline-replay check OPEN. Desktop segment cache ⬜ (vlcj) |
| Daily live rot-drill | 🔴 Workflow + probe fixes LIVE on correct branch (run 33968950214 @ `10ad025`): both engines CI-IP bot-gated; metadata PASS; kill switch OK. No green verdict; residential verify OPEN | issue #14, artifact `rot-drill-33968950214` |
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

- [ ] Manual/scheduled run completes with `PROBE|verdict|PASS` (CI-IP may stay red under category-8 gating; residential green is the user-impact gate — see KNOWN_LIMITATIONS).
      Fixes for run 33961533965 are now on session branch `arena/01a07170-dhun`
      (cherry-picks ending at `39cc924`; equivalent to PR #15 head `5dabdfa`).
      CI run **`33967339900` GREEN** @ `60e5631`. Live rerun requires UI/manual dispatch when the
      agent token lacks `actions:write`: Actions → rot-drill → Run workflow →
      ref `arena/01a07170-dhun`. Do not mark green from CI alone.

- [ ] First scheduled run completes on the default branch.
- [x] Failure path creates or updates one `[rot-drill]` issue and uploads the
      log artifact. (done in 33961533965 — though the artifact name was
      mangled in the issue text by the quoting bug; fix in flight)
- [ ] Recovery path comments on and closes the open issue.
- Run URL / date: https://github.com/99ggprooo00-code/DHUN/actions/runs/33961533965 · 2026-09-05
- Verdict line: `PROBE|verdict|FAIL|extraction-pipeline-broken`
- Issue number (if exercised): #14 (OPEN — auto-close awaits a green run)

**CI-network vs residential (kill-switch policy, do not weaken):** a red
drill caused by `AuthRequired`/bot-gate text on a GitHub runner is
CI-network evidence only. It must NOT be converted to a pass by removing
stream validation, skipping the byte check, or making the probe tolerate
resolve failures. Distinguish: metadata PASS + resolve gated ⇒ datacenter
gating (verify on residential hardware); metadata ALSO failing ⇒ real rot
(pin last-good, patch, release ≤72h per RISK_REGISTER).

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

## PR #16 merge (2026-09-05)

Merged to `main` (session `arena/01a07170-dhun`). Code + CI complete for:
taxonomy, Recovering UX, audio-segment cache (Android), M3 glass UI, ADR-002 player polish.

**Still OPEN:** residential rot-drill/stream, Android/Desktop soaks, v0.1.0 artifacts.
