# DHUN — Technical Requirements Document

**Status:** Proposed engineering contract grounded in the repository as of 2026-10-09. This document is not a migration proposal. Do not replace the existing stack with React/Tauri because an older planning draft mentioned it.

## 1. Current architecture and surface boundaries

| Surface | Current implementation | Contract |
|---|---|---|
| Android app | Kotlin / Compose Multiplatform shared UI + Android host; Media3 playback | Primary production app; lifecycle, audio focus, media session and device behavior require Android verification |
| Desktop app | Kotlin/JVM + Compose Multiplatform; VLCJ playback; Windows integrations | Native desktop client; test Windows-specific tray, SMTC, jump lists and single-instance behavior on Windows |
| Shared core | `shared/`, common domain, provider, player, UI, persistence | Keep UI independent of InnerTube/provider implementation details |
| Marketing website | Static Eleventy project under `website/` | Content/navigation/download discovery only; no playback or account backend |
| Browser app mirror | Dependency-free ES modules under `app-web/` | Engineering preview, not a production-supported audio client; sample catalogue and clock must remain clearly labelled until real capability is proven |

No custom DHUN backend is part of the current architecture. Local persistence is used for library/history/preferences/download metadata; the audio cache is not the same thing as persistent user downloads.

## 2. Architectural rules

- Preserve the shared `MusicProvider` / resolver boundary; UI code must not parse provider-specific InnerTube payloads.
- Keep one authoritative player/queue state per running client. The web mirror, where maintained, mirrors the app's behavior but is not a shared production runtime.
- Navigation state is the custom shared `AppNavState` / shell policy, not Navigation Compose. Any change to back behavior must update shared tests and mirror tests.
- Use existing design tokens/components in `shared/design/`; do not invent ad hoc colors, radii, blur values, spacing, icons or one-off dialog styles.
- Keep I/O, database access, network access, artwork processing and downloads off the UI thread. Expose progress/results through observable state.
- Treat provider output, artwork URLs, links and file paths as untrusted. Validate IDs and URLs; never log cookies, tokens, personal data or stream URLs with sensitive query parameters.
- Do not add authentication, server-side proxying, a web-player framework, new extraction mechanism or platform target without an accepted ADR.
- Keep `website/` and `app-web/` isolated. The marketing site does not import app runtime code; the mirror must not be linked as a supported product until its gates pass.

## 3. Required layers

1. **Presentation:** Compose screens/components, dialogs, responsive layouts, accessibility semantics, loading/empty/error states.
2. **Presentation state/use cases:** ViewModels and observable state; action results must be represented explicitly rather than swallowed by `runCatching`.
3. **Domain:** Track/artist/album/playlist/history/download/playback models and deterministic rules.
4. **Provider:** source-neutral metadata/search/stream resolution/lyrics/related-track interfaces and normalized DTOs.
5. **Playback:** `DhunPlayer`, queue manager, platform engine adapters; keep queue, current track, seek position and playback state consistent.
6. **Persistence:** SQLDelight/repository layer for durable local data; separate volatile stream/audio cache from user-owned downloads.
7. **Platform adapters:** Android lifecycle/audio focus/media session/permissions; desktop filesystem/VLCJ/SMTC/tray; browser limitations explicitly represented.
8. **Website:** static templates/data, shared layout/navigation, deployment-base-aware URLs, build/link/accessibility checks.
9. **Verification:** unit, UI/interaction, contract, accessibility, browser, and hardware evidence; record unsupported checks honestly.

## 4. Operation result contract

Every asynchronous user action must expose at least `Idle`, `InProgress`, `Succeeded`, and `Failed(error)` where relevant. The UI may optimistically update only when it can safely roll back. Do not dismiss a destructive-action dialog before the operation's result is known. Do not catch and discard exceptions for user-visible actions. Errors must map to concise, actionable text and preserve the prior state.

For bulk operations, define:
- scope (which rows/files/metadata are affected);
- treatment of active, queued, paused, failed and partial operations;
- cancellation semantics and temporary-file cleanup;
- duplicate-request behavior;
- persistence transaction/atomicity guarantees;
- observable completion and failure states;
- retry/idempotency behavior.

## 5. Navigation and route contract

### Native
Primary tabs: Home, Search, Library. Details (artist/album/playlist), Settings, and FullPlayer are secondary destinations/overlays. The shared back policy is: collapse FullPlayer → pop detail/settings → return to prior tab if tab history exists → platform default. Re-tapping the active tab pops one detail level when appropriate. FullPlayer must not block interactive navigation rail hit targets on two-pane layouts.

### Marketing website
Routes currently authored: `/`, `/features/`, `/ui/`, plus `/404.html`. Every route must use the same base layout and navigation data. The project is deployed under a repository path (`/DHUN/`); root-absolute links such as `/features/` can escape to the domain root. All internal paths must be base-path aware and tested against the built artifact and deployed URL. Include Home, Features, Interface, source, issues, changelog and test-release destinations. A link that works only on localhost root is not sufficient.

### Browser mirror
Use the same visible navigation contract and source-derived design tokens/icons. It must visibly disclose lack of audible playback/live catalogue when applicable. No proxy, credentials, or false success state. Do not assume it is production web support.

## 6. Design-system technical requirements

The intended visual identity is **translucent frosted glass** with artwork-led color, not a Material 3 visual theme. Compose Material components may be used as implementation primitives, but their default appearance must not override the agreed visual contract. Source of truth: `shared/design/DhunAppearance.kt`, `DhunTypography.kt`, `DhunShapes.kt`, `DhunSpacing.kt`, `DhunAnimations.kt`, `DhunIcons.kt`, and the glass component policies.

- Blur artwork once per artwork/track generation, cache the result, and avoid per-frame full-resolution blur.
- Layer blur + controlled tint + gradient scrim + translucent surfaces; ensure text and controls remain readable on bright and dark art.
- Dialogs and sheets require an opaque or sufficiently opaque base beneath frosted material; do not let underlying content bleed through form fields or destructive confirmations.
- Provide non-blurred opaque fallbacks for unsupported/expensive blur; respect reduced motion and high contrast.
- Sharp cover art must fit its intended card without cropping faces; ambient blur may bleed full-screen behind it.
- Use consistent design tokens and shared components for all forms/dialogs; no raw one-off style values without a documented reason.

## 7. Data, network and privacy

- Normalize external data at provider boundaries and use namespaced stable IDs.
- Handle timeout, offline, cancellation, rate limiting, unsupported formats, unavailable lyrics, malformed responses and upstream changes distinctly.
- Use bounded retries with backoff; never retry destructive operations automatically.
- Persist only what the feature needs. No account or cloud-sync schema unless separately approved.
- Keep persistent downloads, volatile LRU audio cache, artwork cache, stream URL cache, and provider metadata cache as separate concepts with different eviction and recovery rules.
- Website must not load third-party runtime assets or analytics unless an approved decision changes that rule. External links must be intentional and identified.

## 8. Quality gates

- Unit tests for reducers/state machines and repository behavior.
- UI tests for action-to-result behavior, focus, dialog cancel/confirm, error and loading states.
- Contract tests ensuring browser mirror tokens/icons/navigation remain derived from native sources.
- Static website build checks: all output routes exist; internal anchors resolve; page nav/footer links resolve under `/DHUN/`; each page marks exactly one current navigation item; no false product claims.
- Browser tests at compact phone, large phone, tablet, desktop and narrow/wide desktop widths; keyboard-only tab path; forced colors/high contrast; reduced motion; print where applicable.
- Hardware acceptance for Android and Windows; note that source-level/unit tests do not substitute for audio, offline, lifecycle, rotation, storage, tray or SMTC checks.
- Update verification record, `.ai/ROADMAP.md` and `.ai/KNOWN_LIMITATIONS.md` with commit-specific evidence.

## 9. Non-goals

No new server, cloud account, proxy, React/Tauri migration, production browser playback, or provider/extraction redesign is implied by this TRD. Such work requires an accepted ADR, risk review and separate validation gate.
