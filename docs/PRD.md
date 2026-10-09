# Dhun (धुन) — Product Requirements Document

Status: Draft for review. Dhun is in development; this document describes intended behavior, not features already shipped.

## Vision
Dhun is an open-source, cross-platform music client for Android, Windows desktop, and the web. It aims to make music discovery and playback feel focused, fast, accessible, and personal, using a polished Material 3-inspired interface with restrained glass effects.

## Audience and problem
Listeners want search, playback, queues, a personal library, and a good full-screen player across devices. The project is AI-assisted, so work must be broken into small, testable steps with explicit acceptance criteria.

## Platforms
- Android: touch-first UI, lifecycle-aware playback, system media controls where supported.
- Windows desktop: keyboard/mouse, desktop window behavior, media keys where supported.
- Website / Web SPA: responsive browser experience; PWA only if validated.
- Share design tokens and domain behavior where practical; adapt layouts to each platform.

## Core user stories
1. Search tracks, artists, albums, and playlists.
2. Start playback and see clear loading, buffering, and error states.
3. Control playback, seek, skip, shuffle/repeat, volume, and queue.
4. View details and manage local favorites/playlists where implemented.
5. Open a full-screen Now Playing screen with artwork, metadata, controls, queue, and lyrics when available.
6. Tap the lyrics/CC control to reveal readable lyrics over blurred artwork without interrupting playback.
7. Persist settings and local library data on the device/browser.
8. Use Dhun with keyboard navigation, screen readers, scalable text, and reduced motion.
9. Understand local-only behavior, network dependencies, and upstream limitations.

## MVP scope
- Responsive shell/navigation on each supported platform.
- Search, result lists, and detail views.
- Playback abstraction, queue, transport controls, and full-screen player.
- Lyrics states: loading, available, unavailable, and error.
- Local settings/persistence; empty, offline, retry, and upstream-failure states.
- Build/test documentation, attribution and license notices, clear development-status disclaimer.

## Later scope
Richer playlists/library, improved synchronized lyrics, history, desktop integrations, PWA enhancements, and cloud sync only after a security/privacy design is approved.

## Exclusions until separately approved
No paid subscription, no storing/proxying copyrighted audio on Dhun servers, no mandatory account for basic local use, no cloud sync without threat model/privacy policy, and no promise of uninterrupted unofficial upstream access.

## Design principles
Use lightweight 2D blur, translucent Material 3 surfaces, gradients, and subtle transitions. Performance and readability beat visual effects. Do not imply official affiliation with YouTube, Google, or Apple. Review terms, laws, and licenses before release.

## Success and acceptance criteria
- Search → select → playback → queue/player controls works on each claimed platform.
- Mini-player and full-screen player share consistent state.
- Lyrics control handles loading, timed/plain lyrics, unavailable, and error states.
- Settings persist; private data is not logged.
- Web does not assume native Android/Tauri capabilities.
- Responsive and accessibility checks pass; release notes distinguish implemented, partial, and planned features.

## Risks and open decisions
Upstream instability, media rights/terms, platform background audio, packaging/signing, and low-end-device performance. Confirm whether web/desktop share React and whether a custom backend is needed. See TRD.md, app-flow.md, ui-ux-design.md, backend-schema.md, and implementation-plan.md.
