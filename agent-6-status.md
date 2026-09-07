# Agent 6 — Shared player UX status

Updated: 2026-09-07 (UTC)

## State

**Implementation approved; slices 1–2 pushed and CI-green, slice 3 ready to push / CI pending.** Hardware verification has not been performed. Each check below applies only to its recorded commit.

- Session branch: `arena/01a079f7-dhun`. This Arena session is fixed to that branch; implementation, incremental commits, pushes, and the PR must use it rather than the requested `agent/player-ux`.
- Checked-out base and fetched `origin/main`: `f15724547aec4725dfa34293de1011612aeee9dd`.
- Phase 14 remains in progress: robustness, rot-drill, UI/UX polish, and feature enhancements. This agent owns only the shared player UX slice; release and hardware acceptance remain open.

## Scope and constraints

- Own `shared/src/commonMain/kotlin/dev/dhun/ui/player/**`.
- Only additive changes in `shared/src/commonMain/kotlin/dev/dhun/presentation/player/**`; preserve existing public signatures and callers.
- Preserve the existing FullPlayer favorite button, including its position before Shuffle. Do not duplicate it or return it to the menu.
- Use the existing Compose Multiplatform / Material 3 design tokens and source-neutral presentation boundary. No new playback engine, extraction, download, or platform wiring; no Liquid Glass or new mini-player window.
- Do not modify app-android, app-desktop, download, library UI/presentation, shared UI components, shared UI shell, tools, `.ai`, or `docs`. Roadmap reconciliation belongs to the docs agent; this file is the agent-local status record.

## Boot findings and remote evidence

Read `.ai/MASTER_PROMPT.md` and `.ai/ROADMAP.md` CURRENT ACTIVE TASK; reviewed ADR-002, ADR-004, ADR-006, the scoped player implementation, and recent fetched `origin/main` history.

- `f157245`: PR #33 merged ADR-006 persistent download data/engine, offline-first routing, and minimal download UI/platform manager wiring. The roadmap header still describes PR #33 as open at `f46c586`; fetched history and GitHub confirm it is merged. No roadmap edit made.
- `23c0d66`: the FullPlayer favorite action was added to the existing transport cluster before Shuffle. Preserve this work.
- `83f16cd` (PR #32): the docked MiniPlayer was restyled and gained Previous; the seek bar hit target was expanded to 48dp. Refine rather than replace these changes.
- `d2a7095`: prior transport cancellation cleanup and bounded player layout already exist; preserve their behavior.
- GitHub main CI at `f157245`: [CI run 34079283259](https://github.com/99ggprooo00-code/DHUN/actions/runs/34079283259) **success**. [Test-release run 34079283231](https://github.com/99ggprooo00-code/DHUN/actions/runs/34079283231) **success**. These are baseline evidence, not verification of future Agent 6 changes or hardware behavior.

## Implementation progress

### Slice 1 — Lyrics and tab selection (`46e46d4`, pushed and CI-green)

- `PlayerTabs.kt`: selected-tab semantics (`Role.Tab` / selection group), track-keyed lyrics state, explicit no-track/blank/error states, and retry for unavailable lyrics.
- `SyncedLyrics.kt`: centered follow including index zero and backwards seeks, manual scroll pause with "Follow lyrics", stable text measurement with animated emphasis, untimed/invalid timestamp handling, and tap-to-seek.
- Player-only regression companion: `shared/src/jvmTest/kotlin/dev/dhun/ui/player/LyricsFollowTest.kt` (8 tests covering intro/first line, backwards seek, malformed/duplicate timestamps, and padded/wrapped-line centering).
- Existing public presentation signatures and the Favorite transport button are unchanged. No protected paths edited.

### Slice 2 — Queue and Related (`ff2ddce`, pushed and CI-green)

- `PlayerTabs.kt`: queue position summary, visible move-up/move-down/remove menu and accessibility actions, existing drag/swipe component reused unchanged; Related source caption, refreshable empty state, responsive placeholders, explicit queue-replacement notice, and append action with feedback.
- Related playback now passes the displayed list to the existing scope-owned `playQueue` action, so an index cannot silently select a refreshed recommendation list and closing the tab does not cancel the preparation job.
- `PlayerViewModel.kt`: additions only — `addToQueue` plus snapshot-aware `playQueueAt`, `removeQueueItem`, and `moveQueueItem` overloads. Original methods, signatures, and implementations unchanged.
- Player-only regression companion: `PlayerQueueActionsTest.kt` (6 tests for duplicate occurrences, stale queues, bounds, append semantics, and displayed-list playback).

### Slice 3 — Docked MiniPlayer, seek and transport (implemented locally; push / CI pending)

- `MiniPlayer.kt`: keyboard/accessibility expansion using a real click target; independent transport actions; current callback and density-aware swipe with cancellation cleanup; track/error-keyed diagnostics; readable narrow layout (Previous remains available in FullPlayer and appears in the dock at wider widths); track-keyed interpolated progress.
- `PlayerSeekBar.kt`: 48dp target retained; live scrub time, bounded pending-seek preview, single commit on release and cancellation cleanup; finite/clamped range input; five-second arrow-key seeking plus Home/End; focused/disabled states; hour-consistent timestamps. Existing internal seek/time helpers remain in the player package.
- `TransportControls.kt` / `FullPlayer.kt`: keyboard and accessibility activation for hold-capable transport, press/release pairing retained, track/queue-occurrence cancellation, one Play/Pause/Retry label during icon animation, busy-state text, labeled desktop volume, saveable tab selection, and six-control sizing without shrinking secondary targets. Favorite code and order are unchanged.
- Regression companions: `PlayerSeekBarTest.kt` (7) and `PlayerControlStateTest.kt` (6); **27 new player-only regression tests total** across all slices.

## Verification and exact next technical step

- Local `git diff --check`: passed. Source assertions also passed for allowed-path ownership, unchanged FullPlayer/MiniPlayer signatures, strictly additive PlayerViewModel diff, the single unchanged Favorite block before Shuffle, and token-based production dimensions.
- Local `./gradlew :shared:jvmTest --no-daemon`: cannot start; `JAVA_HOME is not set and no 'java' command could be found in your PATH.` This is the documented sandbox toolchain limitation, not a Kotlin test result. CI remains the compile/test gate.
- Slice 1 remote CI: [34081198767](https://github.com/99ggprooo00-code/DHUN/actions/runs/34081198767) **success** at `46e46d4`: shared JVM tests (including the 8 lyrics tests), Android debug build, probe and Desktop compilation. Rechecked on GitHub before the slice 2 push. Slice 2 is covered separately below.
- Slice 2 remote CI: [34081470792](https://github.com/99ggprooo00-code/DHUN/actions/runs/34081470792) **success** at `ff2ddce`: shared JVM tests (including the 6 queue/action tests), Android debug build, probe and Desktop compilation. Rechecked before the slice 3 push. No PR yet; slice 3 remains CI-pending.
- **Next:** push slice 3, inspect/fix CI as needed, perform final scope review, then open the PR from `arena/01a079f7-dhun`. Record verified outcomes here without changing `.ai/ROADMAP.md`.
- Open a PR from the session branch once the implementation has passed the compile gate. Do not merge or modify the rolling release.

## Last error / blockers

- Local Gradle cannot start because the sandbox has no JDK. Remote CI is required.
- Hardware acceptance (device/desktop visuals, audible playback, accessibility, gestures, and soaks) remains unverified.

## Integration follow-up outside this agent

- Source review only: `QueueManager.move` reads `current` after mutating its item list when recovering the current index. Current-track preservation during reorder deserves a playback-engine-owner regression check. This existing engine code is outside Agent 6 scope and was not changed; both drag and menu movement delegate through the existing player API.
