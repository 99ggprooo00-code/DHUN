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
- Forwarded `downloadManager` through `DhunAppShell` → `TabContent` → `HomeScreen` / `SearchScreen`, so those row affordances receive the same manager already used by Library and `TrackOverflowDialog`.
- Kept the existing `TrackOverflowDialog` "Download for offline" action unchanged.

## Guardrails followed

- Did not modify download engine files under `shared/.../download/**`.
- Did not modify app platform modules.
- Did not modify Library, Player, `.ai`, or `docs` areas.
- Modified `shared/.../ui/shell/DhunAppShell.kt` only after explicit follow-up approval to complete the `downloadManager` pass-through.
- Did not claim hardware verification.

## Verification

- `git diff --check`: PASS.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: PASS.
- Local Gradle compile could not run in this sandbox because Java/JDK is unavailable (`JAVA_HOME` unset and no `java` on `PATH`). GitHub CI remains the compile gate.

## Open handoff note

`HomeScreen` and `SearchScreen` now accept an optional `DownloadManager`, and `DhunAppShell` forwards its existing manager into both screens. When a track has a row in `downloadManager.downloads`, Home quick picks and Search song/video rows render `TrackDownloadRowActions`; those render `TrackDownloadBadge`, which observes `observeProgress(trackId)` for live percentages.
