# docs/decisions — ADRs
Only for decisions with real alternatives AND a blocking defect scenario.
Product intent/status vocabulary lives in [PRD §1](../PRD.md#1-product-definition-and-source-of-truth); current stack instructions live in `.ai/MASTER_PROMPT.md`; an ADR is written when one
of them is being challenged, not before.
- [ADR-001](ADR-001-extraction-engine.md) — Extraction engine / tokenless client chain
- [ADR-002](ADR-002-fullscreen-player-design.md) — Full-Screen Now Playing design lock (2026-09-05)
- [ADR-003](ADR-003-parallel-identity-chain.md) — Staged wave parallel identity chain (Option C, 2026-09-06)
- [ADR-004](ADR-004-remove-separate-miniplayer-window.md) — Remove the separate desktop mini-player window (user decision 2026-09-06; the docked in-app MiniPlayer is the product mini-player)
- [ADR-005](ADR-005-next-track-prebuffering-and-temp-cache.md) — Next-track pre-buffering, temporary cache lifecycle, and stream isolation (2026-09-06)
- [ADR-006](ADR-006-offline-music-downloads-architecture.md) — Persistent offline downloads: schema v3, range-resume engine, offline-first routing (ACCEPTED 2026-09-07)
- ADR-007 (PROPOSED, not filed — lives on open PR #54) — Android stream attestation / PO-token options A–D. Contingency reference only: implement solely on triggers T1/T2 (MASTER_PROMPT §2) with the user's explicit go-ahead.
- [ADR-008](ADR-008-browser-web-player-target.md) — Browser-player target feasibility gate (**ACCEPTED for the B1 deployed-origin spike only, 2026-10-08**; **amended 2026-10-16**: the web app is built and ships at `/app/`, with the six boundaries — no DHUN backend or proxy above all — unchanged).
- [ADR-009](ADR-009-marketing-site.md) — **PROPOSED** — a static marketing site is the only public web property (Option A, 2026-10-08); no player, no PWA, no `app.` host, and it does not widen ADR-008. *Its "no player, no PWA" clause is superseded for `/app/` by the ADR-008 amendment of 2026-10-16; the rest of it — the site is static, has its own workflow, and widens no boundary — still holds.*
- [ADR-010](ADR-010-multi-source-provider-direction.md) — **ACCEPTED for owner-delegated decisions** (2026-10-20): native YouTube path kept, no web IFrame, Spotify planned and deferred (not removed) pending all technical/account/Premium/policy/legal/playback/privacy/licensing/cost gates, not Premium alone. **Amended 2026-10-28** to reaffirm multi-source intent, actual native/browser scope and design-only provider work. Policy risks remain open; no adapter is added.
