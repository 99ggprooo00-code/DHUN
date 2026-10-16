# Phase 4 — Real search and catalogue

> Status: ⚠️ Complete with exceptions — the adapter is real, the live query is not verified from the deployed origin
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

Make search return what a real source actually said, show which source answered,
and never present a fixture as a result.

## Acceptance criteria

- [x] A query goes to a real adapter and its answer is rendered — there is no
  hardcoded result list anywhere in the search path.
- [x] The screen states which source answered, above the results.
- [x] An empty answer, a refused request and an offline browser are three
  different states with three different sentences.
- [x] Recent searches are the visitor's own, kept locally, and clearable.
- [x] Filtering by result type happens on real data.
- [ ] A live query from the deployed origin is performed and recorded — **not
  done**; it shares Phase 2's blocker.

## What exists

| Piece | Where | Notes |
|---|---|---|
| Query, browse, and entity lookups | `app-web/src/js/source.js` | One anonymous client identity; no key, no account |
| Recent searches | `app-web/src/js/storage.js`, rendered by `views.js` | Bounded list, cleared from the search screen |
| Result-type filters | `views.js` (`SEARCH_FILTERS`) | Filters the live answer, not a second query per type |
| Source badge | `views.sourceBadge()` | "YouTube Music", "Your endpoint" or "Demo data" — always on screen, never implied |
| Demo fixture | `app-web/src/js/data/sample-catalog.js` | Fictional rows, no audio, labelled wherever shown |
| Search-screen states | `views.searchScreen()` | Idle, loading, empty, refused, demo |

## Exception

The adapter is exercised by `app-web/tests/source.test.mjs` against the shape the
endpoint returns, and `scripts/test_app_web.py` asserts the labelling rules at
source level. What has never happened is a **live query from the deployed page**:
that request leaves the visitor's browser for an origin the project does not
control, and the only honest way to record it is from a browser on a real
network. Until then, the claim is "implemented and unit-tested", not "verified".

## Risks identified

- **Metadata answering does not imply playback.** A search that returns rows is
  not a player that produces sound, and the two are recorded separately in this
  project on purpose.
- **Result shapes change.** The adapter reads the fields it needs and tolerates
  their absence; a field that disappears becomes an empty state rather than a
  crash, and the daily extraction drill notices the change before a user does.

## Test results

| Test | Result |
|---|---|
| `app-web/tests/source.test.mjs` | Pass — query, empty answer, refusal, kind tagging |
| `app-web/tests/views.test.mjs` | Pass — the search screen's states, the demo label, the badge |
| `browser` job | Pass — `/app/` mounts, names its source, throws nothing |
| Live query from the deployed origin | **Not tested** |

## Completion decision

- [x] Acceptance criteria met except the live query
- [x] Tests pass, in the files named above
- [x] Documentation: this file, plus the app's own screens
- [ ] The live query recorded

**Go / No-Go: Conditional Go.** Search is not the release gate — playback is —
but the exception is real and stays written down.
