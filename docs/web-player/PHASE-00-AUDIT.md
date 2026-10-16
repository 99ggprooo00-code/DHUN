# Phase 0 — Repository audit and baseline

> Status: ✅ Complete — audited 2026-10-16 (session `arena/59c43e0a-dhun`)
> Branch: `arena/59c43e0a-dhun` (this repository works on session branches; see [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md))
> Last updated: 2026-10-16

## Objective

Establish what already exists for the browser build, what it proves, and what
the bootstrap plan would have created that this repository already has — before
any new code is written. The plan's own rule is *audit before editing*, and its
Phase 0 note for a migration says: inspect the existing `app-web/`, decide what
can be reused, document the playback pipeline's real status, and make sure
nothing is lost.

## What the audit found

**`app-web/` is not a scaffold.** It is a complete, tested application, further
along than the plan's Phase 6 in most respects and behind it in exactly one: the
playback proof of concept.

| Measurement | Value | How it was read |
|---|---|---|
| Deployed source files | 32 (518,017 bytes) | `git ls-files app-web/src \| wc -l`, `wc -c` |
| JavaScript modules | 23, all reachable from `main.js` | `git ls-files 'app-web/src/js/*.js'`; `app-web/tools/build.mjs` fails if one is unreachable |
| Service-worker shell entries | 32, all present | `node tools/build.mjs` |
| Node test files | 16 (`node:test`, no runner installed) | `npm test` |
| Node tests | 165 pass, 0 fail | `cd app-web && npm test` |
| Repository Python suite | 23 test modules | `python3 -m unittest discover -s scripts -p 'test_*.py'` |
| Site build | 5 pages, minification saves 86,623 bytes | `cd website && npm run build` |
| Site gates | 31 quality checks; 4 pages pass the honesty contract | `scripts/website_quality.py`, `scripts/website_claims.py` |
| Browser job | Green on all routes and both schemes | `.github/workflows/website.yml` `browser` job |
| Deployment | Live: site at `/DHUN/`, app at `/DHUN/app/` | `served` job + reading the deployed pages |

### What is reusable, and was reused

Everything. The bootstrap plan's Step 5 migration note is satisfied by *not*
copying anything anywhere: the web app already lives at `app-web/`, the site that
publishes it at `/app/` lives at `website/`, and the Python contract suite that
holds both to their claims lives at `scripts/`. Creating a second repository
would have duplicated all of it.

Specifically, the plan's proposed modules map onto working code:

| Plan's module | Exists as | Does |
|---|---|---|
| `main.js` | `app-web/src/js/main.js` | Boot, the shell, the delegate listener, every action |
| `audio.js` | `app-web/src/js/audio.js` | The media element, the equaliser graph, honest status reporting |
| `player.js` | `app-web/src/js/player.js` | Queue, shuffle, repeat, pre-resolution, failure records |
| `player-ui.js` | `app-web/src/js/player-ui.js` | Mini and full player surfaces |
| `source.js` + `providers/` | `app-web/src/js/source.js` | The source-neutral contract, the anonymous adapter, the user endpoint, the labelled demo catalogue |
| `views.js` | `app-web/src/js/views.js` | Every screen as a pure function of its view model |
| `router.js` | `app-web/src/js/router.js` | Hash routing, deep links |
| `storage.js` | `app-web/src/js/storage.js` | Settings, library, session, export/import |
| `mediasession.js` | `app-web/src/js/mediasession.js` | OS transport controls |
| `tabsync.js` | `app-web/src/js/tabsync.js` | One tab plays at a time |
| `pwa.js` | `app-web/src/js/pwa.js` | Install prompt, update prompt, offline state |
| `utils.js` | `dom.js`, `format.js`, `version.js` | The same job, split by subject |
| `frosted-glass.css` | `css/tokens.css` (generated) + `css/app.css` | The app's own design tokens, mirrored from Kotlin |

There is no `lyrics.js` placeholder — `app-web/src/js/lyrics.js` is implemented.

## The playback pipeline's real status

This is the finding the phase exists for, and it is unchanged from
`ADR-008`'s B1 result:

```
search / browse  ──►  InnerTube adapter (anonymous client identity)
                        │
                        ├─ answered  ──►  real results, tagged "YouTube Music"
                        └─ refused / offline
                                │
                                ├─ user endpoint configured?  ──►  ask it
                                └─ otherwise                   ──►  demo catalogue,
                                                                    labelled, last
play a row       ──►  resolve stream  ──►  <audio>  ──►  sound
                        │
                        └─ CORS, HTTP error, dead URL  ──►  a state on the row that
                                                            says which failure
```

**Metadata: readable. Streams: unproven.** B1 read metadata successfully and the
player request was refused by the origin in the one Chromium-family browser
available to it; Firefox and Safari were unavailable to that test, so the result
is "refused where it was tested", not "refused everywhere". The app surfaces
that refusal as a state instead of hiding it, which is what the plan's honesty
rules require — but a state is not audio, so the level below is the only honest
one:

> **Implemented and unit-tested, not verified.** No audio has been heard from the
> deployed site by any author of this repository.

## Deviations from the bootstrap plan, and why

| Plan | This repository | Reason |
|---|---|---|
| Create `99ggprooo00-code/dhun-webapp` | Not created; work continues in `99ggprooo00-code/DHUN` | The user's instruction, and the plan's own "nothing is lost" rule: a second repository would duplicate a working app, a published site, and two sets of honesty gates |
| `LICENSE` = MIT | GPL-3.0-or-later | The repository's licence, which `app-web` already declares; the plan's own rule is to respect existing conventions |
| `.github/workflows/deploy.yml` deploying `app-web` | Folded into `website.yml` | One Pages artifact with one owner; a second publisher would race it and each would overwrite the other |
| A project board with a column per phase | [`IMPLEMENTATION-STATUS.md`](IMPLEMENTATION-STATUS.md) | A board is a view; the tracker is the record, and a tracker file cannot go stale silently because CI reads it (`scripts/web_player_docs.py`) |
| Placeholder modules for future phases | Not created | `app-web/tools/build.mjs` fails on a module `main.js` cannot reach, and an empty provider file is exactly that |
| Four hand-written stylesheets | One generated sheet plus one component sheet | The tokens are generated from the app's Kotlin design sources, and the site's sheet is generated from the app's; hand-writing four copies is how a design drifts |

## Acceptance criteria

- [x] The existing `app-web/` is inspected and its modules inventoried — the
  table above, read from `git ls-files` and the build output.
- [x] The gates are run and their real numbers recorded — 165 Node tests, 31
  quality checks, the claims contract, the browser job, the served check.
- [x] The playback pipeline's status is documented as it is, not as it was
  intended — "metadata readable, streams unproven", with B1 cited.
- [x] Nothing from the existing project is lost — nothing was moved, copied or
  deleted; this phase adds documents and one gate.
- [x] The deviations from the plan are recorded with their reasons.
- [x] The phase tracker and the limitations file exist and agree with this audit.

## Risks identified

- **The plan's phase order invites a false sense of progress.** Phases 6 and 7
  are complete while Phase 2 is blocked. Anyone reading the tracker top-down
  could mistake "nine phases touched" for "nearly done". The tracker's overall
  status line exists to prevent exactly that, and Phase 2 is listed as the
  release gate in both the tracker and the limitations file.
- **Documentation drift.** This repository's own documents have, at different
  points, described the web app as a preview, denied the browser build's
  existence, and promised playback it cannot guarantee — the second of those was
  live on the deployed site until 2026-10-16. That is why this directory is
  covered by a machine check (`scripts/web_player_docs.py`) rather than by good
  intentions.

## Test results

Read in this session, on this tree:

| Command | Result |
|---|---|
| `cd app-web && npm test` | 165 pass, 0 fail |
| `cd app-web && node tools/build.mjs` | 32 files, 518,017 bytes, 23 reachable modules, 32 shell entries |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | 454 tests, OK (the session's count, after this workstream's 18 new mutation tests for the documents gate) |
| `python3 scripts/website_quality.py website/dist` | 31 checks pass |
| `python3 scripts/website_claims.py website/dist` | 4 pages pass (9 forbidden-claim rules, 3 caveats, 5 digest rules, 12 telemetry rules) |
| `cd website && npm run build && node tools/verify-minify.mjs` | byte-accurate `dist/` |
| `cd website && npm run test:rules` | 48/48 |
| `node website/tools/sync-tokens.mjs --check` | the site's sheet matches the app's tokens |

## Completion decision

- [x] Acceptance criteria met
- [x] Tests run, with the results above rather than an assurance
- [x] Documentation written: this file, the tracker, the limitations file, the
  development guide, and the phase files
- [x] Nothing deleted, moved, or rebuilt

**Go / No-Go: Go — to Phase 1 and Phase 2.**

Phase 1 (source research) can be finished at a desk. **Phase 2 cannot be done
from this repository alone**: proving playback needs a browser and a network, and
that is the next action a person should take. The concrete steps are at the end
of [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md).
