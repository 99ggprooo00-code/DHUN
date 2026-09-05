# Phase 14 verification — Robustness, Rot-Drill, Release

Status: 🟨 **ROT-DRILL WORKFLOW WIRED; ALL HARDWARE AND RELEASE ACCEPTANCE
OPEN**. This document is an evidence log, not a claim that Phase 14 is done.
CI compilation is not a substitute for the live extraction, Android soak,
Desktop soak, clean-install, or release checks below.

## Phase 14 implementation status

| Step | Current status | Evidence / remaining gate |
|---|---|---|
| Typed error taxonomy and actionable user messages | 🟨 Typed `DhunResult`/`DhunError` + `toUserMessage` paths, per-request retry, 429 global backoff gate (`2932d57`, with unit tests), and offline banner (`fed1d54`) are implemented; CI verdict pending on the current push; 403 "Reconnecting…" UX, offline-banner hardware check, and the db-path review pass remain | `shared/.../core/RateLimitGate.kt`, `shared/.../core/ConnectivityMonitor.kt`, `DhunAppShell.kt`, hosts' Koin modules |
| Bounded audio cache and offline replay | ⬜ Not implemented | Android currently has a 5-hour stream-URL cache with 403 invalidation; played audio segment caching and offline replay are still required |
| Daily live rot-drill | 🔴 Workflow is wired and its FAILURE path is proven (run 33961533965, issue #14, artifact, kill switch) — but the first live run FAILED: yt-dlp bot-gated from the runner IP and the probe tested only the fallback engine; no green verdict exists | `.github/workflows/rot-drill.yml`; rerun required after the probe-alignment/diagnostics fixes; a real `PROBE|verdict|PASS` gates this row |
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
- [ ] Manual run from the Phase 14 branch completes with `PROBE|verdict|PASS`.
      Fixes for run 33961533965 are now on session branch `arena/01a07170-dhun`
      (cherry-picks ending at `39cc924`; equivalent to PR #15 head `5dabdfa`).
      This-push CI pending. Live rerun requires UI/manual dispatch when the
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
