# Phase 3 — Source-neutral architecture

> Status: ✅ Complete
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

Make the player depend on a *contract* rather than on one provider's quirks, so
that adding a source is a small, testable change and a provider breaking is a
state on screen rather than a rewrite.

## Acceptance criteria

- [x] Every screen asks for data through one interface, never a provider's own
  shape — the screens receive a view model; `app-web/src/js/source.js` is the
  only module that knows an endpoint's URL.
- [x] A response says which source produced it, and that travels to the screen —
  `kind`, plus `describeActive()`, plus the badge `views.sourceBadge()` renders.
- [x] Failures are typed, not strings — the failure carries its stage, so the
  interface can say *what* was refused instead of "something went wrong".
- [x] There is a documented minimum contract for a user-supplied endpoint, small
  enough for a person to implement.
- [x] The demo catalogue is a provider of last resort, labelled as demo wherever
  it appears, and never silently substituted for a real answer.
- [x] A new provider can be added without touching a screen —
  `app-web/tests/source.test.mjs` covers the seam.

## The design

```
                 ┌──────────────────────────────────────────────┐
  screens  ─────►│  source manager: kind, describe, resolve     │
  (views.js,     │  app-web/src/js/source.js                    │
   main.js)      └───┬──────────────┬───────────────┬───────────┘
                     │              │               │
              anonymous client   user endpoint   demo catalogue
              (default, last     (opt-in, the    (labelled, last
               resort: never      user's own      resort, no audio
               silently falls     service, the    at all)
               back past it)      project never
                                  proxies it)
                     │
                     ▼
        typed result: { ok: true, kind, … }  |  { ok: false, failure: { stage, … } }
```

Three properties do the work:

1. **The kind travels with the data.** A screen cannot show live results as
   though they were local, or demo rows as though they were live, because the
   row's own response carries the kind and the badge reads it.
2. **Failures are values.** A refused resolution is a result, not an exception
   that unwinds into a blank screen, so the queue keeps its shape and the
   failure stays attached to the row that caused it.
3. **Falling back is a decision with a label.** The demo catalogue is reached
   only when nothing else answered, and when it is reached the page says so —
   the browser job fails if the demo source answered and the notice is absent.

## Boundaries (from ADR-008, unchanged)

- No DHUN-operated backend or proxy: the project's own code never forwards a
  request on a user's behalf.
- A user-configured endpoint is permitted and documented, because it is the
  user's infrastructure. The project does not operate one, and the app does not
  need one to run.
- No claim of playback is made anywhere on the strength of this architecture.

## Risks identified

- **A contract with one implemented provider is a contract in name only.**
  Mitigated by the demo catalogue and the user endpoint implementing the same
  seam: three implementations exist, so the seam is exercised.
- **The endpoint contract could grow.** It is deliberately small and published in
  the settings screen, so a user can see exactly what their endpoint must answer.

## Test results

| Test | Result |
|---|---|
| `app-web/tests/source.test.mjs` | Pass — the seam, the kind tagging, the failure taxonomy |
| `scripts/test_app_web.py::DoesNotSellWhatABrowserCannotDo` | Pass — the demo catalogue is labelled at the source level |
| `browser` job, `/app/` | Pass — the source badge is on screen and named in the record |

## Completion decision

- [x] Acceptance criteria met
- [x] Tests pass, with the named files above
- [x] Documentation: this file; the endpoint contract is in the app's own
  Settings screen, and the boundaries are in ADR-008

**Go / No-Go: Go.** The next source can be added behind this seam without
touching a screen — which is what [PHASE-01](PHASE-01-SOURCE-RESEARCH.md)'s
outstanding candidate would use.
