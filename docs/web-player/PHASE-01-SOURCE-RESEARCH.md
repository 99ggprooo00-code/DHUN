# Phase 1 — Research and technical feasibility

> Status: ⚠️ Complete with exceptions — one candidate source is unevaluated
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

Decide where browser audio can actually come from, under a hard rule: total
running cost of nothing, and no service the project operates. Write down what is
proven, what is assumed, and what has not been tried — before building anything
on top of it.

## Acceptance criteria

- [x] The candidate sources are compared against cost, licensing, and whether a
  browser can reach them at all.
- [x] The result of each *attempt* is recorded as an attempt (a date, what was
  tried, what came back), never as a capability.
- [x] The chosen path is recorded as an accepted decision —
  [`ADR-001`](../decisions/ADR-001-extraction-engine.md) for the extraction
  engine, [`ADR-008`](../decisions/ADR-008-browser-web-player-target.md) for the
  browser target and its boundaries.
- [ ] An open-licensed catalogue with item-level licence metadata is evaluated
  against the app's provider contract — **not done**, see "Exception" below.

## Candidates

| Source | Cost | Can a browser reach it? | Licence position | Status here |
|---|---|---|---|---|
| YouTube Music via an anonymous `player` request from the page | Nothing — no key, no account, no proxy | Sometimes. It is a cross-origin request to an origin the project does not control, and the origin may refuse it. This is the whole of B1's result. | Unofficial client boundary; the project documents its own terms position in `legal/` and `docs/legal/` | **Attempted.** Metadata readable; the player request refused where it was tested. ADR-008 records the boundaries |
| A user-configured endpoint that speaks the app's small documented contract | Nothing to the project — it is the *user's* infrastructure | Yes, if the user's endpoint is reachable from their browser | The user's own responsibility; the project documents the contract and never proxies it | **Implemented.** `app-web/src/js/source.js` |
| An open-licensed catalogue (Internet Archive and similar) | Nothing; the metadata API is open and item pages carry licence metadata | Usually yes — metadata APIs are CORS-friendly, and audio files are direct URLs | **Per item, not per site.** Some recordings are freely reusable, some are not, and the licence has to be read from the item | **Not evaluated.** This is the exception |
| An embedded third-party player | Nothing | Yes — it is an iframe | The embed carries the provider's branding and terms requirements | Rejected for now: a cross-origin frame cannot report whether it is actually playing, so the interface could not tell the truth about playback |
| Any hosted music API with a free tier | A payment method, and a bill when the tier ends | Yes | Contractual | **Rejected by the budget rule**, not evaluated |

`app-web/src/js/data/sample-catalog.js` is deliberately **not** in this table.
It is not a source: it is a bundled fixture of fictional rows, every one of them
labelled as demo on screen, used only to keep the interface navigable when
nothing else answers.

## What the research produced

- `docs/decisions/ADR-001-extraction-engine.md` — the tokenless client chain and
  why it is the project's own thin client rather than a third-party library.
- `docs/decisions/ADR-008-browser-web-player-target.md` — the browser target,
  its six boundaries, the B1 spike, and the 2026-10-16 amendment that accepted
  the web app while keeping "no DHUN backend or proxy" in force.
- `docs/research/` — the extraction research the decisions rest on.
- `web-spike/` — the B1 probe, kept as the historical artifact of a blocked
  result.
- `docs/decisions/ADR-009-marketing-site.md` — the site that publishes the app.

## Exception, and the next action for this phase

**The open-licensed catalogue has not been evaluated**, and it is the most
promising remaining candidate:

- it needs no account, no key and no service the project operates;
- its metadata is served from a CORS-friendly API, which is the specific thing
  the YouTube path cannot promise;
- its audio files are plain URLs a browser can fetch and play directly;
- **and** its licences are per item, so a catalogue view would have to carry
  licence information per row rather than a blanket claim. That is a design
  requirement, not a footnote: a player that shows a licence badge it has not
  read is exactly the kind of lie this project's rules forbid.

The honest next step for Phase 1 is a written evaluation, not an implementation:
read the API's terms, confirm the licence fields an item exposes, and decide
whether per-row licence display is enough. Only then does it become a candidate
for Phase 2's proof of concept.

## Risks identified

- **Extraction rot.** Any source reached by talking to a service the project does
  not control can stop answering without notice. The project's answer is not to
  pretend otherwise: `.github/workflows/extraction-health.yml` runs a live probe
  daily and opens an issue when the answer changes.
- **A single-source player is a single failure.** This is why the seam in
  `source.js` exists before a second source does.
- **Provenance.** A source that mixes freely reusable and restricted material
  cannot be presented as "open", and a mixed catalogue needs the licence shown
  where the track is.

## Test results

This phase is research; its "tests" are the recorded attempts and the accepted
decisions above. Where it does produce code — the source seam, the demo
catalogue's labelling — that code is covered by
`app-web/tests/source.test.mjs` and `scripts/test_app_web.py`.

## Completion decision

- [x] The comparison exists and names a cost for every row
- [x] Every attempt is recorded as an attempt
- [ ] The open-licensed candidate is evaluated — **outstanding**
- [x] The chosen path is an accepted ADR with enforced boundaries

**Go / No-Go: No-Go for a second source until the evaluation is written; Go for
Phase 2 on the path already implemented.** Phase 2 does not depend on the
exception.
