# DHUN Web — implementation status

> **Overall status: IN DEVELOPMENT — NOT VERIFIED FOR RELEASE**
> Last updated: 2026-10-16 (session `arena/59c43e0a-dhun`)

The one fact that sets the overall status: **no one has yet heard audio come out
of the deployed site.** Everything else in the phase table below is either
implemented and unit-tested, or CI-measured, or both — playback is the item that
cannot be asserted from this repository, and it is Phase 2.

## Phase tracker

| Phase | Name | Status | Evidence | Remaining work |
|---|---|---|---|---|
| 0 | Repository audit and baseline | ✅ Complete | [PHASE-00-AUDIT.md](PHASE-00-AUDIT.md) — audit of the existing `app-web/`, the gates, and what is reused rather than rebuilt | Keep the tracker current; that is Phase 10's job |
| 1 | Research and feasibility | ⚠️ Complete with exceptions | [PHASE-01-SOURCE-RESEARCH.md](PHASE-01-SOURCE-RESEARCH.md) — `docs/decisions/ADR-001`, `ADR-008` (including the B1 result), `docs/research/`, `web-spike/` | An open-licensed source (Internet Archive) has been identified and **not** evaluated against the app's provider contract — see the phase file |
| 2 | Real playback proof of concept | ❌ Blocked | [PHASE-02-PLAYBACK-POC.md](PHASE-02-PLAYBACK-POC.md) — B1's result: metadata readable, the player request refused by the origin in the one available browser; the app surfaces that refusal (`app-web/src/js/source.js`, `player-ui.js`) | A real browser, on a real network, playing a real track from the deployed `/app/` and reporting what happened — sound or an error with a reason |
| 3 | Source-neutral architecture | ✅ Complete | [PHASE-03-ARCHITECTURE.md](PHASE-03-ARCHITECTURE.md) — `app-web/src/js/source.js`: `SOURCE_KIND`, one `describeActive()`, a failure taxonomy, a documented endpoint contract | Nothing; a new provider is added behind the same interface |
| 4 | Real search and catalogue | ⚠️ Complete with exceptions | [PHASE-04-SEARCH-AND-CATALOGUE.md](PHASE-04-SEARCH-AND-CATALOGUE.md) — `source.js` InnerTube adapter, unit-tested against the recorded contract; the demo catalogue is labelled and last | Live search from the deployed origin is unverified (it shares Phase 2's blocker) |
| 5 | Complete playback engine | ✅ Complete | [PHASE-05-PLAYBACK-ENGINE.md](PHASE-05-PLAYBACK-ENGINE.md) — `player.js`, `audio.js`, `tabsync.js`; 165 `app-web` tests | Audible output depends on Phase 2 |
| 6 | Interface and full-screen player | ✅ Complete | [PHASE-06-INTERFACE.md](PHASE-06-INTERFACE.md) — `player-ui.js`, `views.js`, generated tokens; the `browser` job measures viewports, contrast, tab stops and axe on every route | A human's eyes; no capture from the web app has been looked at by a person |
| 7 | Library, playlists, persistence | ✅ Complete | [PHASE-07-LIBRARY-AND-PERSISTENCE.md](PHASE-07-LIBRARY-AND-PERSISTENCE.md) — `storage.js` (settings in `localStorage`, library in IndexedDB, export/import, private-mode degradation) | No cross-device story, by design and by budget |
| 8 | Reliability, accessibility, privacy, security | ⚠️ Complete with exceptions | [PHASE-08-TEST-PLAN.md](PHASE-08-TEST-PLAN.md) — the four gates, the CSP, the no-telemetry assertions | A manual browser and screen-reader pass; real network-failure testing |
| 9 | Deployment and release | ⚠️ Complete with exceptions | [PHASE-09-DEPLOYMENT.md](PHASE-09-DEPLOYMENT.md) — the site and `/app/` are live on GitHub Pages; the served-bytes check re-reads them after every publish | The release gate itself is Phase 2, and no release of the web app is claimed |
| 10 | Maintenance and roadmap | ✅ Complete | [PHASE-10-MAINTENANCE-ROADMAP.md](PHASE-10-MAINTENANCE-ROADMAP.md) — the daily extraction drill, the maintenance contract in `README.md`, `docs/decisions/ADR-001` | Living document: reviewed whenever extraction breaks |

## Status legend

| Mark | Meaning |
|---|---|
| ✅ Complete | Implemented, tested, and the evidence is named in the row |
| ⚠️ Complete with exceptions | The work is done and the exception is written down — read the phase file before relying on it |
| 🔄 In progress | Started, not finished |
| 🔲 Not started | Not begun |
| ❌ Blocked | Cannot proceed for a reason outside this repository's control |
| 🚫 Skipped | Deliberately not done, with the reason recorded |

## Release readiness checklist

This is the definition of done. A ticked box means the claim was verified and
the evidence is named; an unticked box means it was not.

- [x] **The interface ships and is reachable** — the site and `/app/` are live at
  `https://99ggprooo00-code.github.io/DHUN/` and re-checked after every publish
  by the `served` job (`scripts/website_smoke.py`).
- [x] **The code is unit-tested** — `cd app-web && npm test` → 165 pass;
  `python3 -m unittest discover -s scripts` → the repository's Python contract
  suite, which includes the web app's source-level rules.
- [x] **The rendered pages are measured in a real browser** — the `browser` job
  (viewports, contrast in both schemes, tab stops, radio groups, keyboard, axe)
  passes on every route, `/app/` included.
- [x] **No paid infrastructure, and none hidden** — no backend, no proxy, no
  serverless function, no database service, no paid API, no paid domain: the
  deployment is a static GitHub Pages site and a static app, and
  `scripts/test_app_web.py` fails if the app grows a DHUN-operated endpoint.
- [x] **No tracking** — no analytics, telemetry or crash-reporting SDK; asserted
  against the shipped dependency graph by `scripts/website_claims.py`.
- [x] **Licensing and attribution are recorded** — `LICENSE` (GPL-3.0-or-later),
  `THIRD_PARTY.md`, and the legal bundle generated into the app.
- [x] **The limitations are visible** — [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md)
  and [`.ai/KNOWN_LIMITATIONS.md`](../../.ai/KNOWN_LIMITATIONS.md).
- [ ] **A real source provides genuine results, from the deployed site** — the
  adapter is real and unit-tested, but a live query from the deployed origin has
  not been performed and recorded. Blocked by the same thing as the next line.
- [ ] **At least one supported source plays audible audio** — **not proven.**
  This is the release gate. See [PHASE-02](PHASE-02-PLAYBACK-POC.md).
- [ ] **Track changes and errors are handled honestly in practice** — the states
  exist and are unit-tested; that they behave as designed on a real network is
  part of the same unperformed test.
- [ ] **Desktop and Android browser tests pass** — CI renders the app in one
  Chromium build on a Linux runner. A desktop browser other than that, and an
  Android browser on a phone, are untested.
- [ ] **A human has looked at the result** — nobody has watched a page render,
  on the site or in `/app/`. CI measures geometry and semantics, not taste, and
  it does not see what a person sees.

## How to move the status forward

In order, and nothing below the first line matters until it is done:

1. Open the deployed `/app/` in a real browser on a real network, search for
   something, press play, and write down exactly what happened — sound, or the
   error the app showed. Record it in [PHASE-02](PHASE-02-PLAYBACK-POC.md).
2. If it refused: read the refusal. A CORS or origin refusal is the expected
   outcome on some networks and is a finding to record, not a failure to hide.
   The honest alternatives are in [PHASE-01](PHASE-01-SOURCE-RESEARCH.md) —
   notably an open-licensed catalogue, which needs no account and no service the
   project operates.
3. Only then: the manual browser and screen-reader pass (Phase 8), and the
   release decision.
