# DHUN (धुन) — Product Requirements Document

**Status:** Draft for review; repo-grounded as of 2026-10-09. DHUN remains in development. This document distinguishes implemented code from verified product behavior. A source file or green unit test is not proof that a user-facing flow works on hardware.

## 1. Product definition and source of truth

DHUN is a GPL-3.0 open-source music player for Android (primary) and desktop JVM (Windows first; Linux/macOS where supported by the JVM build), using YouTube Music as a metadata/stream source. The native application is Kotlin Multiplatform + Compose Multiplatform, with shared domain/presentation code, Android Media3 playback and desktop VLCJ playback. This is the repository's actual architecture; React 19, Tauri, Zustand, and a React-based production web player are **not** the current native-app stack and must not be described as shipped architecture.

There are three distinct surfaces. They must never be conflated:
1. **Native app:** Android + desktop; source of truth for product behavior and visual design.
2. **Marketing website:** static Eleventy site in `website/`, intended to explain DHUN and link to source, features, interface, issues, changelog and the rolling test release. It is not a music player.
3. **`app-web/` engineering mirror:** a separate browser UI experiment. Current limitations documented in `app-web/README.md` and `docs/verification/29-web-app-mirror.md` include no audible playback, sample metadata fallback and no deployed product support. Do not market it as a production web player. Any future browser-player commitment requires the applicable ADR/gate to be satisfied.

Source of truth order when statements conflict: current code + current CI/hardware evidence; accepted ADRs for locked decisions; `.ai/MASTER_PROMPT.md` and `.ai/ROADMAP.md`; verification records; then this document. Historical notes are not active requirements.

## 2. Product goals

- Make discovery and playback understandable, fast, and resilient when YouTube behavior or connectivity changes.
- Give users a consistent, artwork-led, translucent frosted-glass experience without sacrificing legibility, battery, accessibility, or low-end-device performance.
- Make every visible control truthful: the label, confirmation, state change, persisted result, and error message must agree.
- Keep core listening usable without a DHUN account or custom DHUN backend.
- Make Android and desktop behavior consistent where their capabilities overlap, and explicitly disclose platform-specific differences.
- Make the website's pages reachable from every other website page and ensure links work at the deployed project-page base path.
- Treat tests and on-device/desktop verification as completion evidence, not documentation claims.

## 3. Users and primary jobs

1. Open DHUN and immediately understand whether the catalogue is live or a fallback.
2. Search for a track, artist, album or playlist; distinguish loading, no results, offline, rate-limited, unsupported and failed states.
3. Start playback and understand buffering/recovery/errors without losing the selected item or queue.
4. Open Now Playing, control playback and queue, and enter/exit lyrics mode without stopping audio.
5. Like tracks, manage playlists, review history and manage downloaded tracks.
6. Change appearance and playback preferences, clear the intended storage category, and receive honest feedback when an operation succeeds or fails.
7. On the website, move between Home, Features and Interface pages, return Home, open the repository/issues/changelog and understand that a rolling test build is pre-release.

## 4. Product scope

### Native app capabilities present in the repository (verify on target before claiming fully accepted)
- Home, Search, Library; artist, album and playlist details.
- Mini-player and full-screen player; queue, shuffle/repeat, lyrics (synced/unsynced where available), related tracks.
- Local playlists / Liked Songs, history and persistent offline downloads.
- Settings, appearance/theme tokens, cache controls and platform integrations.
- Android media/session/widget capabilities and desktop tray/jump-list/SMTC capabilities as implemented by platform modules.

### Required quality work (urgent)
- Correct every action-to-result mismatch; especially Clear all downloads and other destructive actions.
- Fix website internal links for the GitHub Pages project path; verify the canonical deployed site, not just local root paths.
- Prove the home page and every secondary page are mutually navigable using header, footer, CTAs, browser Back and direct URLs.
- Verify dialog geometry, text field rendering, overlay layering, focus/dismiss behavior, loading/error states and responsive layouts.
- Add interaction-level regression tests for bugs that currently have only static markup or DOM-stub coverage.
- Keep `website/` quality checks isolated from app CI where intended, while ensuring failures in each workstream are visible.

### Explicitly not a current product commitment
- A production browser music player, PWA, hosted audio/API proxy, account system, cloud sync, server-side library or user tracking.
- React/Tauri migration of the existing native app.
- Any feature not present in the current code or explicitly approved through an ADR and roadmap update.

## 5. Non-functional requirements

- **Truthfulness:** label sample/fallback data and unavailable playback; never simulate successful audio or deletion.
- **Accessibility:** keyboard operation on desktop/web, accessible names, focus visibility, meaningful headings, dialog focus management, sufficient contrast on glass, reduced-motion support and touch targets appropriate to platform.
- **Reliability:** cancellation and retries are bounded; errors are typed and user-actionable; destructive operations have deterministic semantics.
- **Privacy/security:** no sign-in requirement, no credentials/cookies/tokens in logs, no secrets in repo, no unapproved proxy, validate external URLs and provider payloads.
- **Performance:** artwork blur is prepared/cached rather than recomputed every frame; use opaque fallback when blur is unavailable or costly; avoid blocking UI during I/O.
- **Consistency:** one shared navigation state contract in the native app; one central website nav/link data source; do not duplicate conflicting action names.
- **Compatibility:** document platform-specific capability gaps instead of showing controls that cannot work.

## 6. Mandatory interaction contracts

### Clear all downloads
- The action is visible only when there are downloads to clear.
- Opening it presents a confirmation dialog whose wording names the exact data being removed and the count; it must distinguish completed downloads from active/queued/paused/failed partial items if the operation treats them differently.
- **Cancel, Escape/back, outside dismissal (where allowed), or close must perform no deletion.**
- Confirm must disable duplicate submissions, invoke one clear operation, and remain in a pending state until its result is known. Do not close the dialog and silently swallow an error.
- Success: refresh the observed download list and storage summary; show an explicit success/empty state; clean temporary/partial files and cancel active jobs according to the documented download-manager contract.
- Failure: preserve data and selection where possible, show a recoverable error, and allow retry. Never show an empty list merely because the dialog was dismissed.
- Add tests for cancel, confirm, duplicate tap, operation failure, active downloads, stale UI, and final persistence/file cleanup.

### Other destructive or stateful actions
The same contract applies to clear history, delete selected downloads, remove a single download, delete/rename playlist, remove a playlist item, clear cache and reset preferences: exact target, confirmation for irreversible bulk actions, no action on cancel, pending/disabled state, success feedback, error recovery, and persistence verification.

### Navigation and website links
- Native app: Home/Search/Library are the only primary tabs; detail pages, settings and FullPlayer are stacked overlays/routes following the shared AppNavState back contract. Back closes FullPlayer, then detail, then returns through tab history, then delegates to the platform.
- Marketing website: every page shares the same header/footer; Home/Features/Interface are linked from each page; the brand returns Home; active page is announced; CTAs go to verified destinations.
- Internal website links must be generated relative to the configured deployment base path (currently a GitHub Pages project site under `/DHUN/`) rather than hard-coded domain-root paths that escape the project. Validate rendered links from the actual deployed origin.
- Do not link the marketing site to `app-web/` as a supported player until playback and deployment are explicitly approved and verified.

## 7. Acceptance criteria

A requirement is complete only when all applicable layers pass:
1. Unit tests for domain/state transitions.
2. UI/interaction tests for visible controls, dialog transitions and navigation.
3. Build/contract tests for routes, tokens, generated icons and internal links.
4. Accessibility checks plus keyboard/focus checks.
5. Real rendering and interaction verification on Android and desktop; browser verification for the website where a browser runner is available.
6. A written verification record names commit, environment, steps, expected/actual result and remaining limitations.
7. `.ai/ROADMAP.md` and `.ai/KNOWN_LIMITATIONS.md` are updated from evidence; no unverified item is marked complete.

## 8. Success signals

- No dead-end website route or internal link on Home, Features, Interface or 404.
- All destructive actions have a tested cancel/no-op path and success/failure path.
- No user-visible action silently fails or reports success before completion.
- No unlabelled sample catalogue, fake audio, or false production-web claim.
- No high-severity UI defect remains without an owner, priority and verification gate.
