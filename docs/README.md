# DHUN documentation index

These documents describe the current Kotlin Multiplatform product, the static marketing website, and the separately maintained browser engineering mirror. They must be read alongside the live source, accepted ADRs, and current verification evidence.

## Product and design specifications

- [PRD](PRD.md) — product scope, users, quality bar, action contracts and acceptance criteria.
- [TRD](TRD.md) — actual architecture, platform boundaries, navigation, design tokens and quality gates.
- [Application flow](app-flow.md) — native journeys, website navigation, lyrics, downloads, destructive-action behavior and error states.
- [UI/UX design](ui-ux-design.md) — translucent frosted-glass visual contract, player/lyrics behavior, accessibility and website structure.
- [Backend and data schema](backend-schema.md) — local persistence and data consistency; confirms no custom backend is currently authorized.
- [Implementation plan](implementation-plan.md) — prioritized defect fixes, test strategy and release verification.

## Binding references

- [Current master prompt](../.ai/MASTER_PROMPT.md)
- [Current roadmap and active task](../.ai/ROADMAP.md)
- [Debug log / known incidents](../.ai/DEBUG_LOG.md)
- [Known limitations](../.ai/KNOWN_LIMITATIONS.md)
- [Website plan](../.ai/WEBSITE_PLAN.md)
- [FullPlayer design decision](decisions/ADR-002-fullscreen-player-design.md)
- [Browser-player boundary](decisions/ADR-008-browser-web-player-target.md)
- [Offline downloads architecture](decisions/ADR-006-offline-music-downloads-architecture.md)
- [Browser mirror verification](verification/29-web-app-mirror.md)

## Status language

“Present in source”, “unit-tested”, “CI-green”, “verified on Android/Windows hardware”, “verified in a real browser”, and “verified on the deployed website” are separate claims. Do not mark one as another. Where documents conflict, inspect the current implementation and accepted ADR before changing code.
