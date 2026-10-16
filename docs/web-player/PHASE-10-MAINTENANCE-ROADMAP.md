# Phase 10 — Maintenance and future roadmap

> Status: ✅ Complete — and it stays open as a living document
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

Decide what happens when the thing that makes this app work stops working — and
make that decision before it happens, because the answer is not "we will notice".

## Acceptance criteria

- [x] The maintenance contract is written down and, where possible, executed by
  a scheduled workflow rather than by a promise in a document.
- [x] What the project will not build is listed with the reason, so the list
  cannot be reopened by accident — a decision recorded is a decision kept.
- [x] The open items are ordered, with the release gate first and features last.
- [x] The documentation itself is machine-checked, because documentation drift is
  the failure this project has actually suffered.
- [x] The roadmap is a living document with a named trigger for review.

## The maintenance contract

Any client that reaches a service it does not control is borrowing time. This
project's answer is a commitment rather than a promise:

1. **The chain is watched.** `.github/workflows/extraction-health.yml` runs a
   live playback probe once a day, resolves a real track, checks that audio bytes
   come back, and opens or closes an issue when the answer changes. It is a
   daily drill, not a unit test, because the thing it tests is somebody else's
   service.
2. **The failure is visible to users before it is visible to us.** The app
   surfaces the refusal as a state with a reason, so a user sees "this did not
   work, and here is why" rather than a spinner.
3. **Fixes are patches, not rewrites.** The removal of one identity from the
   chain has been a small change every time, because the chain is data
   (`source.js`) rather than logic spread through the app.
4. **The site never promises what the chain cannot deliver.** The claim rules
   enforce this, and they run on the served bytes as well as the build.
5. **The native builds are the fallback.** When the browser path refuses, the
   Android and Windows clients remain the supported players, and the site says
   so where a visitor is choosing.

## What is explicitly out of scope, and why

| Idea | Why not |
|---|---|
| A DHUN-operated backend or proxy | It would make the project a service: cost, data-handling and legal responsibilities the project does not want and the budget forbids (ADR-008 boundary 1) |
| A paid API, even as a fallback | The budget rule is absolute, and a fallback that needs a card is not a fallback |
| Audio downloads in the browser | A page cannot keep a signed stream working offline; the native builds exist for that (ADR-006) |
| A single provider hard-wired into the screens | It is what the source seam exists to prevent |
| Accounts, sync, telemetry | Each needs a service the project will not run |

## The open list, in the order it should be worked

1. **Close Phase 2** — run the playback test in a real browser and record the
   result, positive or negative.
2. **Perform the Phase 8 manual matrix** — ten specific tests, including a phone
   and a screen reader.
3. **Write the open-licensed source evaluation** — the outstanding item in
   [PHASE-01](PHASE-01-SOURCE-RESEARCH.md). It is the most promising path that
   costs nothing and does not depend on an origin the project cannot influence.
4. **Only then** consider features. A visualiser or an equaliser preset is
   polish on top of a player whose playback nobody has heard.

## How this directory is kept honest

The failure mode this file is written against is documentation drift. It has
happened in this repository more than once: the site has described the web app as
a preview, denied that it exists, and promised playback it cannot guarantee —
each time in a document nobody was checking. The answer is a machine check:

- `scripts/web_player_docs.py` reads this directory and fails if a phase file
  loses its required sections, if a phase is marked complete with no evidence
  beside it, if the definition-of-done checklist ticks a box with nothing behind
  it, or if any document here carries a claim the repository cannot back.
- `scripts/test_web_player_docs.py` feeds that checker deliberately broken
  documents, so a rule that silently stops matching is a red test rather than a
  quiet regression.

That is the maintenance plan for the documentation itself, and it is the same
pattern the site's honesty rules use.

## Test results

| Test | Result |
|---|---|
| `python3 scripts/web_player_docs.py docs/web-player` | Pass |
| `python3 -m unittest scripts.test_web_player_docs` | Pass — including the mutations |
| The daily extraction drill | Owned by CI; its verdict is on the repository's issues |

## Completion decision

- [x] The maintenance contract is written and enforced by a workflow
- [x] The out-of-scope list is written with reasons
- [x] The open list is ordered, and its first item is not a feature
- [x] The documentation drift risk has a machine check rather than a good
      intention

**Go / No-Go: Complete.** This phase has no end state; it is reviewed whenever
extraction breaks or a phase closes.
