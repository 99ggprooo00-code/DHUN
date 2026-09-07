# Agent 6 — Shared player UX status

Updated: 2026-09-07 (UTC)

## State

**Implementation approved; slice 1 ready for its first push, CI pending.** Hardware verification has not been performed. No Agent 6 changes are claimed CI-green until a remote run confirms them.

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

### Slice 1 — Lyrics and tab selection (implemented locally; first push / CI pending)

- `PlayerTabs.kt`: selected-tab semantics (`Role.Tab` / selection group), track-keyed lyrics state, explicit no-track/blank/error states, and retry for unavailable lyrics.
- `SyncedLyrics.kt`: centered follow including index zero and backwards seeks, manual scroll pause with "Follow lyrics", stable text measurement with animated emphasis, untimed/invalid timestamp handling, and tap-to-seek.
- Player-only regression companion: `shared/src/jvmTest/kotlin/dev/dhun/ui/player/LyricsFollowTest.kt` (8 tests covering intro/first line, backwards seek, malformed/duplicate timestamps, and padded/wrapped-line centering).
- Existing public presentation signatures and the Favorite transport button are unchanged. No protected paths edited.

## Verification and exact next technical step

- Local `git diff --check`: passed.
- Local `./gradlew :shared:jvmTest --no-daemon`: cannot start; `JAVA_HOME is not set and no 'java' command could be found in your PATH.` This is the documented sandbox toolchain limitation, not a Kotlin test result. CI remains the compile/test gate.
- Pre-push remote check: main CI `34079283259` is successful at `f157245`; no session-branch CI run or PR exists yet.
- **Next:** push slice 1 to `arena/01a079f7-dhun`, inspect CI, then refine Queue/Related interactions using existing components. Follow with docked MiniPlayer expand/error behavior and seek/transport accessibility. Additive presentation changes only; preserve existing public signatures.
- Open a PR from the session branch once the implementation has passed the compile gate. Do not merge or modify the rolling release.

## Last error / blockers

- Local Gradle cannot start because the sandbox has no JDK. Remote CI is required.
- Hardware acceptance (device/desktop visuals, audible playback, accessibility, gestures, and soaks) remains unverified.
