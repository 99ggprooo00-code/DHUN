# Agent 3 Status — Shared Track Download UI

Updated: 2026-09-07
Branch: `arena/01a079f5-dhun` (Arena-pinned session branch; did not create/switch to `agent/track-download-ui`)
Base observed: `origin/main@f157245` (ADR-006 merged via PR #33)

## Scope

Shared per-track download affordances only.

## Implemented

- Added reusable shared UI components in `shared/src/commonMain/kotlin/dev/dhun/ui/components/DownloadAffordances.kt`:
  - `DownloadBadge`
  - `DownloadProgressIndicator`
  - `TrackDownloadBadge`
  - `TrackDownloadRowActions`
- The badge renders ADR-006 states: `QUEUED`, `DOWNLOADING` with percent when `DownloadProgress.fraction` is available, `COMPLETED`, `FAILED`, and `PAUSED`.
- Live progress is read through `DownloadManager.observeProgress(trackId)`.
- Added a `Pending` vector icon to `shared/src/commonMain/kotlin/dev/dhun/design/DhunIcons.kt` for queued downloads.
- Wired the reusable trailing row composable into:
  - Home quick-pick track rows (`HomeScreen` / `QuickPickItem`)
  - Search song/video result rows (`SearchScreen` / `TrackRow` trailing content)
- Kept the existing `TrackOverflowDialog` "Download for offline" action unchanged.

## Guardrails followed

- Did not modify download engine files under `shared/.../download/**`.
- Did not modify app platform modules.
- Did not modify Library, Player, Shell, `.ai`, or `docs` areas.
- Did not claim hardware verification.

## Verification

- `git diff --check`: PASS.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: PASS.
- Local Gradle compile could not run in this sandbox because Java/JDK is unavailable (`JAVA_HOME` unset and no `java` on `PATH`). GitHub CI remains the compile gate.

## Open handoff note

`HomeScreen` and `SearchScreen` now accept an optional `DownloadManager` and render badges when provided. The app shell pass-through was intentionally left untouched because `shared/.../ui/shell/**` is outside Agent 3 scope.
