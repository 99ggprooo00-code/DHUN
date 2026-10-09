# Dhun — Implementation Plan

Dhun is in development. This is a proposed sequence; reconcile it with the actual code and existing roadmap before assigning completion status.

## Phase 0 — Baseline and decisions
Inventory modules, build workflows, tests, and current behavior. Read existing roadmap/planning docs. Decide native Android/Kotlin versus shared React architecture; verify upstream client, playback constraints, terms and licenses.
Exit: recorded architecture decision, clean build baseline, risk list.

## Phase 1 — Product/design foundation
Review PRD and flows; define tokens, navigation, accessibility, domain models and error taxonomy.
Exit: approved docs and screen acceptance criteria.

## Phase 2 — Platform skeleton
Confirm routing and app shell. Configure types/lint/tests for React if selected; keep Android build/test pipeline healthy; define platform capability interfaces and least-privilege Tauri permissions.
Exit: minimal buildable shell on every claimed target.

## Phase 3 — Catalog/search
Build provider adapter, normalize DTOs, add cancellation, bounded retries, and loading/empty/offline/error states. Test with mocked responses.
Exit: search journey passes on supported targets.

## Phase 4 — Playback foundation
Implement platform playback adapters and play/pause/seek/skip/queue/buffering/error behavior. Validate Android audio focus/media session/lifecycle, web autoplay restrictions, and Tauri native integration.
Exit: playback smoke tests and queue consistency checks pass.

## Phase 5 — Player/lyrics UX
Build mini-player and full-screen player; add lyrics provider interface and all states; implement lightweight glass transition and reduced-motion fallback.
Exit: state consistency and readability checks pass on phone and desktop.

## Phase 6 — Local library/settings
Add favorites/playlists/history only when behavior is defined. Persist local data with versioned migrations and clear-cache/privacy controls.
Exit: persistence, migration and deletion tests pass.

## Phase 7 — Hardening
Run accessibility, responsive, performance, offline, security, license and compliance checks.
Exit: no known release blockers or clearly documented limitations.

## Phase 8 — Release readiness
Build Android artifacts and Windows package; deploy website; test supported browsers; document install, upgrade, troubleshooting and release notes.
Exit: reproducible builds and release checklist complete.

## Work rules
- Keep tasks small and avoid multiple agents editing the same modules.
- Independent streams may cover docs/design, provider contract tests, Android audit, and web shell after interfaces are agreed.
- Every task defines scope, files, acceptance criteria, tests, risks and rollback.
- Status labels require evidence: complete, partial, missing, architectural risk.
- Generated code is not complete until the relevant tests/build run.

## Definition of done
Works on every platform claimed as supported; loading/empty/error/offline states exist; controls are accessible; tests pass; docs match reality; no secrets or prohibited media are committed.
