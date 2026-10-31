# DHUN documentation index

> **Start here:** [authoritative product direction and status vocabulary](PRD.md#1-product-definition-and-source-of-truth),
> [provider capability matrix](research/05-source-capability-matrix.md),
> [ADR-010 and amendments](decisions/ADR-010-multi-source-provider-direction.md),
> and [2026-10-28 alignment audit](research/06-multi-source-claims-audit.md).
> Code is evidence of current behaviour, not a redefinition of the multi-source vision.

These documents describe the current Kotlin Multiplatform product, the static marketing website, and the separate browser build at `/app/` — a real client sharing design tokens, not the native code or all its features. Native currently uses YouTube Music; browser JioSaavn; Spotify remains planned/deferred. The record for that workstream is in [`web-player/`](web-player/README.md). Read these alongside the live source, accepted ADRs, and current verification evidence; current code is evidence of behaviour, while owner direction/PRD controls intent. Investigate conflicts instead of turning an implementation gap into a new product definition.

## Product and design specifications

- [PRD](PRD.md) — product scope, users, quality bar, action contracts and acceptance criteria.
- [TRD](TRD.md) — actual architecture, platform boundaries, navigation, design tokens and quality gates.
- [Application flow](app-flow.md) — native journeys, website navigation, lyrics, downloads, destructive-action behavior and error states.
- [UI/UX design](ui-ux-design.md) — translucent frosted-glass visual contract, player/lyrics behavior, accessibility and website structure.
- [Backend and data schema](backend-schema.md) — local persistence and data consistency; confirms no custom backend is currently authorized.
- [Implementation plan](implementation-plan.md) — prioritized defect fixes, test strategy and release verification.
- [UI/UX audit](ux-audit.md) — source-grounded severity-ranked findings, evidence, required fixes and verification checklist.

## The web workstream

The browser build and the site that publishes it have their own architecture, status ledger and rules. They are machine-checked by `scripts/web_player_docs.py` (run as `npm run check:docs`), so a module cannot be renamed without the documents that describe it, and an evidence path that moves is a red gate.

- [Web workstream README](web-player/README.md) — the priority order, the hard rules and what enforces each one.
- [Architecture](web-player/ARCHITECTURE.md) — the module map, boot, routing, the catalogue seam, the player, storage and the service worker.
- [Implementation status](web-player/IMPLEMENTATION-STATUS.md) — what is verified and by what, what is not, and the release gate. **Currently: ships, not verified for release.**
- [Known limitations](web-player/KNOWN-LIMITATIONS.md) — what is unproven, unsupported or awkward, for a reader deciding whether to rely on it.
- [Development guide](web-player/DEVELOPMENT-GUIDE.md) — how to run, test, build and deploy, in the repository as it actually is.
- [Release gate](web-player/IMPLEMENTATION-STATUS.md) — playback, provider-permission and other prerequisites for a release claim, stated with what would close it.

## Binding references

- [Current owner-directed pre-PR #196 native rollback](verification/39-pre-pr196-native-rollback.md)
- [Historical native recovery and device-acceptance ledger](verification/38-native-application-recovery.md)
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
