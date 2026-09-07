# Agent 2 — Shared Library UI / Download management (status)

> Scope owner: **shared Library UI + presentation layer only.**
> Owns `shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt`
> and `shared/src/commonMain/kotlin/dev/dhun/presentation/library/LibraryViewModel.kt`
> (+ shared `presentation/library` expect/actual and `shared/jvmTest`).
> Does **not** touch app-android, app-desktop, the download engine
> (`shared/.../download/**`), `ui/components/**`, `ui/shell/**`,
> `ui/player/**`, `presentation/player/**`, `.ai/**`, or `docs/**`.

## Task (from ROADMAP, Phase 14 — ADR-006 polish item 3)

ADR-006 shipped the download foundation/engine + offline-first routing + a
*minimal* Downloads tab (PR #33 → `main` @ `f157245`, CI green). Remaining
UI polish for production: per-track download progress/badge, a
storage-management surface, and download-management actions.

## Decisions (approved before code)

- **Storage management surface:** in-tab manage view. The Downloads tab gets a
  "Storage" action that swaps to a full management view inside the Library
  screen (back button returns). No shell/nav changes.
- **Breakdown:** by download state — Completed (reclaimable bytes + count),
  plus In-progress / Paused / Failed counts.
- **Device total/free:** real values via a context-free `expect fun`
  (`androidMain` `StatFs` on the data partition; `jvmMain` `File.usableSpace`)
  in the shared `presentation/library` package. No app/DI/engine edits.
- Batch delete uses `DownloadManager.remove` (cancels job, deletes files,
  clears row — works for every state); pause/resume/cancel use the existing
  engine API. Live % uses the already-public `DownloadManager.observeProgress`
  (previously unconsumed by UI). No engine changes.

## Constraint notes

- `LibraryViewModel` is constructed by shared **shell** code
  (`ui/shell/DhunAppShell.kt`), which this agent cannot edit. Every new
  dependency is therefore self-contained (top-level `expect/actual` like the
  existing `currentUtcOffsetMs()`), mirroring the already-nullable
  `downloadManager` wiring. No required constructor params are added.
- **No Gradle runs here** — CI is the compile gate.
- **No hardware claims.** Offline playback, install-upgrade and on-device
  storage figures stay HW-open (see `.ai/ROADMAP.md`); green CI only proves
  compilation/shared-JVM tests.
- Branch: this Arena session is pinned to `arena/01a079f5-dhun` (the requested
  `agent/library-downloads` is not a pushable target here). All commits and
  the PR go from `arena/01a079f5-dhun` → `main`.

## Implemented (commit batch)

**Presentation — `LibraryViewModel.kt` + new `StorageSpace.kt` expect/actual**
- `StorageSummary`/`DownloadGroup` models; `storageSummary: StateFlow` built by
  combining `downloads` with a 30s device-capacity ticker (and manual
  `refreshStorage()`).
- Context-free `expect fun deviceStorageSpace()` — `androidMain` `StatFs`
  (`Environment.getDataDirectory()`, no Context), `jvmMain` `File.usableSpace`;
  `null` degrades gracefully. Same pattern/place as `currentUtcOffsetMs()`.
- Actions: `pauseDownload`, `resumeDownload` (resume/retry), `cancelDownload`,
  batch `removeDownloads(ids)`, plus existing single `removeDownload` /
  `clearDownloads`. `progressFor(trackId)` exposes live progress.

**UI — `LibraryScreen.kt` (Downloads tab only)**
- Storage summary card (used by downloads vs device capacity + volume bar).
- In-tab Storage-management view: total/free/used, breakdown by state,
  multi-select + select-all, batch delete (confirm), clear-all (confirm).
- Per-row live progress bar + %, pause/retry/cancel, completed file size.
- Uses existing design components/tokens/icons only; no `components/`,
  `shell/`, engine, app, `.ai/`, or `docs/` files touched.

**Tests — `shared/jvmTest/.../LibraryDownloadsViewModelTest.kt`**
- Storage-summary aggregation by state (counts + bytes), empty summary,
  batch/pause/resume/cancel delegation via a recording `FakeDownloadManager`,
  progress pass-through, and null-manager graceful degradation.

## Verification status

- **CI `build-and-test` GREEN on the PR branch — run `34082028450`** (3m56s):
  Python/packaging checks, PowerShell syntax, **shared JVM unit tests**
  (incl. new `LibraryDownloadsViewModelTest`), Android debug build (compiles
  the `androidMain` `StatFs` actual), probe + Desktop compile (compiles the
  `jvmMain` `File.usableSpace` actual). Green here = commonMain + both actual
  source sets compile and the JVM tests pass.
- NOT compiled locally (no Gradle per task rules) — CI is the compile gate.
- **No hardware claims.** On-device total/free figures, offline playback of
  retained/removed downloads, and the Android/JVM disk-stat path remain
  HW-open per `.ai/ROADMAP.md`; green CI proves compilation + shared JVM tests
  only.

**PR:** #36 (branch `arena/01a079f5-dhun` → `main`). Note: the requested
branch name `agent/library-downloads` is not a pushable target in this
pinned session; work is on `arena/01a079f5-dhun` and also carries Agent-3's
parallel per-track download-badge commits (no file overlap).

## Log

- 2026-09-07: boot sequence complete (MASTER_PROMPT, ROADMAP active task,
  origin/main verified @ `f157245`). Scope + engine API analyzed; design
  approved (in-tab manage view, breakdown by state, include device total/free).
- 2026-09-07: presentation + expect/actual implemented and committed;
  Downloads UI reworked and committed; JVM tests added. Pushing + opening PR.
