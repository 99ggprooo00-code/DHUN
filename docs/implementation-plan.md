# DHUN — Implementation and Verification Plan

**Status:** Proposed execution order based on repository inspection at 2026-10-09. This plan does not reopen completed build phases or replace the current `.ai/ROADMAP.md` S1–S6 release-hardening sequence. Reconcile task status with current code, CI and hardware evidence before marking work done.

## 0. Rules of execution

- One coherent branch/PR per focused workstream; no parallel agents unless the user explicitly changes the current single-agent decision.
- Before editing, read `.ai/README.md`, the CURRENT ACTIVE TASK at the top of `.ai/ROADMAP.md`, `.ai/DEBUG_LOG.md`, `.ai/KNOWN_LIMITATIONS.md`, `.ai/MASTER_PROMPT.md`, relevant accepted ADRs and tests.
- Code + current CI + device/browser evidence outrank stale prose. Do not infer success from compilation or a DOM stub.
- Do not change native stack, add a backend, or promote the browser mirror to a product without an accepted ADR.
- Every fix includes a regression test that fails on the old behavior; update verification and known-limitations records.
- Keep documentation claims scoped: “implemented in source,” “unit-tested,” “verified on hardware,” and “verified on deployed site” are different statuses.

## P0 — Correct destructive-action state and result handling (highest priority)

**Scope:** Downloads, history, cache, playlists and other bulk/destructive actions.

**Confirmed defect:** `LibraryScreen.kt` calls `onClearAll()`, clears selection and dismisses the dialog immediately. `LibraryViewModel.clearDownloads()` launches `dm.clearAll()` and wraps it in `runCatching` without exposing the outcome. The user can see the dialog close even if deletion fails; the exception is hidden. Treat this as a real UX/data-trust bug, not a cosmetic issue.

Tasks:
1. Introduce observable operation state/result for clear operations (idle/pending/success/failure with typed error); do not swallow the error.
2. Define the exact scope: completed files, active jobs, queued/paused/failed jobs, partial files and DB rows.
3. Prevent duplicate submissions and race conditions with new downloads while clearing.
4. Keep the confirmation visible or replace it with a progress state until completion.
5. On success, refresh the list and storage summary, clear selection, remove temporary files and show the empty state.
6. On failure, keep remaining items visible and show an actionable error/retry.
7. Apply the same pattern to clear history, batch deletion, remove-download, playlist deletion, cache clear and preference reset.
8. Add tests for cancel/no mutation, confirm once, repeated tap, active job, partial file, filesystem/DB failure, stale row, process restart and retry.

**Exit gate:** no user-triggered destructive operation silently fails; all success/failure paths have test evidence.

## P1 — Fix website internal routing and deployment-base links

**Source-level risk found:** the site is a GitHub Pages project site at `/DHUN/`, while templates contain root-absolute internal destinations such as `/`, `/features/` and `/ui/`. Unless the build demonstrably prefixes them, these escape the repository path and can lead visitors away from the site. The source has a shared header/footer, so the issue is not necessarily “missing nav”; it can be links resolving to the wrong origin path or the public origin serving a different artifact.

Tasks:
1. Add one base-path-aware internal URL helper/filter driven by Eleventy config/site data; use it for wordmark, header, footer, CTAs, 404 links, sitemap and canonical paths as appropriate.
2. Distinguish internal URLs from external repo/release URLs; do not prefix external links.
3. Test generated HTML for every route at the expected `/DHUN/` prefix and reject links that escape to `/` or malformed doubled prefixes.
4. Build `website/` and crawl every generated page: every internal link must resolve to a generated route or valid in-page anchor.
5. Verify route direct-load, refresh, back/forward and current-page marker on deployed origin.
6. Verify Pages workflow configuration and actual served bytes/commit; do not trust stale README or historical `.ai/WEBSITE_PLAN.md` status alone.
7. Ensure Home, Features and Interface link to each other; 404 has recovery links; CTAs land at the intended repository/issues/changelog/test-release destinations.
8. Keep the marketing site separate from the experimental `app-web/`; no unsupported browser-playback claims.

**Exit gate:** deployed-origin link walk passes from Home and every secondary route; exact deployed commit and URL recorded.

## P2 — Dialog, sheet and action interaction audit

Audit all dialogs and overflow sheets, not just Clear downloads:
- create/rename playlist; add to playlist; delete selected items; clear history; clear cache; reset preferences; lyrics unavailable/error; queue sheet; settings; track overflow.
- Verify unique action names for “open” vs “confirm/choose”; no nested click propagation reopens the same sheet.
- Verify focus entry, focus containment where modal, focus restoration, Escape/back, outside dismissal policy, button order, pending/disabled states, validation and errors.
- All form fields and dialog GlassCards use the shared Dhun components and opaque-base rule where required; do not mix default OutlinedTextField styling into custom glass dialogs.
- Add interaction tests for event dispatch and state transitions; static markup presence alone is insufficient.

**Exit gate:** every action has an expected state transition and regression test; no overlapping hit targets or unresponsive controls on Android/desktop.

## P3 — Native app navigation and responsive layout audit

1. Trace Home → Search → result → artist/album/playlist → Play → FullPlayer → Lyrics/Queue/Related → Back.
2. Trace Library → Downloads → Storage view → select all → delete selected → clear all; Library → History → clear; Playlists → rename/delete.
3. Verify shared AppNavState back order: FullPlayer, detail/settings, previous tab, platform.
4. Verify wide two-pane navigation rail remains clickable when FullPlayer is open.
5. Verify small-screen/landscape/short-window layout: no clipped player controls, hidden dismiss button, dialog overflow, text over artwork or row overflow button outside the row.
6. Check loading/empty/offline/failure states and ensure each has a next action.
7. Check theme/accent propagation across shell, dialogs, player and list components.

**Exit gate:** flow checklist passes on real Android and Windows/JVM desktop; unsupported environment checks remain open, not assumed.

## P4 — Frosted-glass design consistency and accessibility

1. Apply the updated design contract: translucent frosted glass, artwork-led accent, blur + tint + scrim; Material 3 components are implementation primitives, not the target visual identity.
2. Remove conflicting “Material 3 only” language from the design ADR and cross-reference the UI/UX spec.
3. Verify sharp artwork is fit-to-card, cached blur stays behind it, and bottom controls remain anchored.
4. Ensure dialogs/sheets have enough opaque base; no transparent text fields, unreadable error text or invisible edges.
5. Verify fallback below Android API 31, no-backdrop-filter browsers, reduced motion and high contrast.
6. Use contrast checks over representative bright/dark artwork; inspect focus ring, pressed, selected, disabled, loading and error states.
7. Compare app-web tokens/icons with native design sources using existing tests; never manually duplicate generated icon data.

**Exit gate:** screenshots/recordings from real target environments plus token/contrast tests; no visual claim based solely on CSS or Compose compilation.

## P5 — Browser mirror contract and honest status

- Keep `app-web/` separate from the marketing site.
- Keep no-audio/sample-catalogue notices visible while the corresponding limitations remain.
- Verify its navigation/state/action semantics mirror native where feasible.
- Test browser actual render, pointer/touch/keyboard and layout when a browser runner is available; DOM stubs prove wiring only.
- Any real browser playback, deployment or supported-web claim must follow the current ADR-008 decision/gates. No proxy or credentials as an improvised fix.

## P6 — Regression, CI and verification records

Required automated checks:
- `./gradlew :shared:allTests` and relevant Android/Desktop compile/test tasks (use repository's current CI task names).
- Existing website Python quality/claims tests and website Node tests.
- `app-web` Node tests and `node tools/build.mjs` where that module is changed.
- Route/link crawler, token/icon mirror tests, destructive-action interaction tests, accessibility checks and browser tests.
- CI results checked on the exact PR head; rerun after final commit.

Required manual/hardware checks:
- Android install/upgrade, playback, lyrics, downloads/offline, orientation, background audio, media controls and dialog touch behavior.
- Windows clean/upgrade install, playback, keyboard focus/back, tray/SMTC/jump lists and file cleanup.
- Website deployed origin: Home ↔ Features ↔ Interface, all footer/header links, refresh, direct route and 404 recovery.

Each verification record includes SHA, date, device/browser/OS, exact steps, expected vs actual, evidence, and limitations.

## P7 — Reconcile docs and release gates

- Update this plan, PRD, TRD, flow, UI/UX and schema together when a shared contract changes.
- Update `.ai/ROADMAP.md` active task and exact next step; update `.ai/KNOWN_LIMITATIONS.md` and `.ai/DEBUG_LOG.md` for significant defects.
- If design/architecture changes, update accepted ADR and link it from the docs.
- Do not claim complete unless CI is green and required hardware/deployed-site checks are done.
- Do not tag v0.1.0 until the repository's S3/S6 checklist and signed hardware gates in `.ai/ROADMAP.md` pass.

## Definition of done

A fix is done only when: root cause documented; implementation and tests land; cancellation/error behavior verified; all relevant routes/screens work; no regressions in shared navigation or platform behavior; CI is green on the exact head; applicable hardware/deployed-origin evidence is recorded; roadmap/limitations reflect reality.
