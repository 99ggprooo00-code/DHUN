# Testing — DHUN Web (`/app/`)

What is tested, by which command, where it runs, and what is not tested. The
native test strategy is in [`docs/development/`](../development/) and the
verification records in [`docs/verification/`](../verification/).

## 1. Commands

Run from the repository root unless noted.

| Command | What it runs | Where |
|---|---|---|
| `npm run check` | Icon check, build, JavaScript suite, docs gate. The full local gate. | Root `package.json` |
| `npm test` | The browser-app JavaScript suite (`node --test "tests/*.test.mjs"` in `app-web/`). | `app-web/` |
| `node tools/build.mjs` | Copies `app-web/src/` to `app-web/dist/` and checks the build. | `app-web/` |
| `npm run check:docs` | `scripts/web_player_docs.py` over `docs/web-player/`. | Root |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | The repository's Python suite, including the docs gate's own tests, the link and secret checks, and the site checks. | Root |
| `npm run check:icons` | Checks that the generated icons match their sources. | Root |
| `npm run build` (in `website/`) | The marketing site build, with the honesty contract and quality gates. | `website/` |
| `npm run test:rules` (in `website/`) | `node --test` over the browser-check rules and the CSS pruner. | `website/` |
| `node tests/browser.mjs` (in `website/`) | Playwright checks against a served build: viewports, keyboard, contrast, motion, axe, icons, and `/app/` offline. | `website/` |

The counts printed by these commands are the source of truth. The docs gate
reads the number of `test(` declarations in `app-web/tests/*.test.mjs` and fails
if a document states a different number for the browser suite. At the time of
writing that number is **269**, including controlled Node boot/controller
integration. The test-file inventory is separate from helper files.

## 2. The browser-app suite

`app-web/tests/` holds pure-module, rendered-markup and controlled Node
boot/controller integration tests (not real browser interaction):

| File | Its own stated purpose (from the file's header comment) |
|---|---|
| `actions.test.mjs` | Every control does something, and every handler can be reached. |
| `api.test.mjs` | The catalogue seam. |
| `demo.test.mjs` | The sample catalogue. |
| `dom.test.mjs` | Markup escaping and idempotent delegated/optional listener cleanup. |
| `format.test.mjs` | Presentation helpers. |
| `legal.test.mjs` | The legal renderer. |
| `library.test.mjs` | Likes, playlists, history, settings and the storage handle. |
| `player.test.mjs` | The queue and the transport. |
| `player-ui.test.mjs` | Full-player structural keys/progress contracts. |
| `request-cancellation.test.mjs` | Per-caller API cancellation and shared failover ownership. |
| `navigation-requests.test.mjs` | Actual boot/router/handlers, disposal, cached-page protocol, queued callbacks and independent owners with controlled document/fetch/audio doubles. |
| `pwa.test.mjs` | Page-owned observer/prompt/registration cleanup, not actual worker/installation acceptance. |
| `service-worker.test.mjs` | VM-isolated shell-cache failure and activation contracts. |
| `router.test.mjs` | Routes, and that every route survives a round trip. |
| `shell.test.mjs` | The shipping contract: one app, one stylesheet set, one offline shell. |
| `shortcuts.test.mjs` | The keyboard map, and a refusal to take keys from someone typing. |
| `search-composition.test.mjs` | Connected browser input ownership, no script value/selection writes during composition, stale events and submission/shortcut guards; not a native IME test. |
| `tokens.test.mjs` | Design tokens. |
| `transfer.test.mjs` | Importing a library from a file. |
| `views.test.mjs` | The screens render, escape, and tell the truth. |

The file headers are the authors' statement of purpose. The assertions themselves
are the authority; read the file before relying on a specific behaviour.

`app-web/tests/helpers/` holds shared fixtures. `boot-fixture.mjs` calls the real
`boot({win, doc})` with private storage and controlled fetch captured during
construction; globals are restored immediately. Delegated handlers use minimal
document doubles. Coverage includes query/filter/hash changes, retirement, Home
batching, Retry/Refresh, artist fan-out and independent player resolution. This
is request/state integration, not DOM layout, focus, accessibility, worker
activation, real media or provider acceptance. Existing API cancellation tests
are retained unchanged. Disposal cases cover pending multi-step artist/import/
registration/prompt work, queued toast/route callbacks, listener counts across
repeated navigation/remount, cached-page suspension/resumption, independent
shared failover and player lifetimes. No search debounce exists or is invented;
expected cancellation must remain silent and the Node runner rejects unhandled
rejections. Full component disposal is distinct from ordinary rerender/visibility
changes. PWA cleanup never uninstalls the persistent worker or clears caches. Disposable mutants verify that
stale-badge and finalizer regressions catch missing guards without changing the
real implementation.

## 3. The Python checks that cover the web build

- `scripts/web_player_docs.py`: presence of the documents, the module map in
  `ARCHITECTURE.md`, evidence paths in `IMPLEMENTATION-STATUS.md`, the browser
  suite's stated size, retired names, and the site's claim rules.
- `scripts/test_web_player_docs.py`: the gate's own tests, run against deliberately
  broken copies of the documents.
- `scripts/test_app_web.py`: no third-party runtime asset, no pretend features
  (`DoesNotSellWhatABrowserCannotDo`), a real web app and not a preview
  (`IsARealWebAppNotAPreview`).
- `scripts/test_repo_health.py`: links, and no committed secrets.
- `scripts/website_claims.py`: the copy rules the site and the app share.

## 4. The site workflow

`.github/workflows/website.yml` has four gating and evidence jobs:

| Job | Gates a publish? | What it does |
|---|---|---|
| `build` | Yes | Installs with the pinned lockfile, builds the site, checks that minification changes no behaviour, runs rule tests, checks the committed build against a fresh one (drift), runs the honesty contract and quality gates, validates HTML, builds the web app and runs its tests. |
| `browser` | No (evidence) | Installs the pinned Chromium, serves the built site, runs `tests/browser.mjs`, uploads screenshots, posts the step log. |
| `lighthouse` | No (evidence) | Three samples per route, gated on the median in its own step; uploads reports. |
| `deploy` | — | Runs only on `main`, after `build`, and sets the `published` output only when Pages is in workflow mode. |
| `served` | — | Smoke-checks the published bytes. |

Its triggers are path-scoped: changes to `website/**`, `app-web/**` and the named
scripts start it. A change to the Android or desktop code does not.

The browser UI measurements use `website/tests/catalogue-fixture.mjs` to abort
only the configured wrapper `/api/` requests. This exercises the real network
failure → labelled silent-sample path without calling community APIs. A separate
controlled HTTP 503 case checks that fictional Home rows stay explicitly labelled
and that an HTTP detail error is not replaced by a successful demo album. The
seven fixture unit tests prove interception scope, explicit failures, preserved
context/service-worker options and error propagation. Unexpected console/page
errors, local assets, CSP and all geometry/accessibility gates remain unchanged.
The initial PR #197 browser run accidentally called live wrappers and failed on
CORS console messages; that was not deployed-origin or audible-playback proof.

## 5. What is not tested

- **Audio.** No check listens to a stream. The player is tested against a fake
  media element, and the browser run drives the page, not the sound.
- **Firefox, Safari, Edge.** Not run in CI.
- **Real phones and touch.** Viewports are emulated.
- **Screen readers.** No recorded pass.
- **The community catalogue.** Unit contracts use fixtures; browser UI checks
  use controlled network/HTTP failure responses, not a successful live service.
  This does not establish provider availability, CORS permission or playback.
  A live check is a manual step, recorded in
  [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) when done.
- **Storage at scale.** The suite does not fill the browser's storage quota. Snapshot
  pruning is tested on small libraries (`library.test.mjs`).
- **Legal text.** The tests check that tokens are substituted. They do not check
  the law, and no legal review has been done.

## 6. Adding a test

- A pure rule goes in the matching `*.test.mjs` file with a plain `test(` at the
  top level. The docs gate counts top-level `test(` calls, so a nested test does
  not change the stated number.
- A new limit, key or origin is also written into the matching document in this
  directory in the same change. The gate does not check every number, so this is
  a rule for people.


### Bundled legal and erase-scope browser contract

`website/tests/browser.mjs` includes `/app/ legal and erase scope`: it opens all
five browser-owned legal routes, checks visible product/provider/draft/rights
text and resolved About version, blocks external requests, then uses a disposable
synthetic library to exercise the actual Erase confirmation. Likes, playlists,
history, searches and snapshots must clear while settings and shell cache remain.
This is local/CI rendering and handler evidence, not live catalogue playback,
third-party deletion, native hardware acceptance or legal approval. Exact local
execution/results: [audit 06](../research/06-multi-source-claims-audit.md).


### Runtime storage and media regressions — 2026-10-28

The dependency-free app suite also runs `player-ui.test.mjs` (stable structural
keys and loading-control labels) and `service-worker.test.mjs` (VM-isolated cache
HTTP/quota/refusal/activation contracts). The unit suite includes late transport
completions, shuffled deletion/hydration, storage getter/read/write/retry/import
failures, media-session cleanup and cancellation during JSON reads.

The existing browser CHECKS list now includes **storage failures** and
**synthetic media transport**. A disposable context supplies explicit test-only
metadata and generated 30-second PCM through `website/tests/runtime-fixtures.mjs`.
This verifies real HTMLMediaElement decoding/controls, five-second keyboard seek,
shuffle removal, stable FullPlayer controls and history staying erased on progress.
It is **not a live JioSaavn response, provider permission, audible-output acceptance
or a new application fallback**. Demo IDs still receive no synthetic successful
song from that fixture; production sample and HTTP-refusal checks remain intact.
Four independent site tests validate fixture PCM/range handling and interception
scope. No CSP, web-security setting or ordinary failure gate is loosened.

Storage tests use disposable synthetic libraries, not personal data. They check
blocked-property startup, readable data under write refusal, failed destructive
confirmation/accessibility, recovery, same-file import retry and honest visit-only
editing. Execution commands are listed above; actual run output, not a test-file count,
establishes which checks passed in that environment.

The focused Search cases model field replacement in Node and verify draft/caret
ownership. The existing Chromium harness also runs a controlled delayed-response
Search draft/caret check. It checks real DOM focus/values, not visibility of a
phone's software keyboard or actual provider/audio acceptance.

### Composition evidence boundaries

For the same Search request intent, tests now require the **same input node**,
not merely restored text/caret in a replacement. The result-region patch leaves
the form and IME target connected and does not write identical value/selection.
Composition Escape/processing keys belong to the IME, and composing Search does
not submit an incomplete draft. Ended/stale events, new explicit queries and
retirement are covered. The prior replacement-specific fixture assertion was
strengthened to input identity; all earlier draft/caret/error assertions remain.

The Chromium delayed-response check dispatches composition/input/key events and
checks actual DOM continuity, focus and selection. Those scripted events do not
open an Android keyboard, emulate a native Compose text field, or verify a real
IME's composition buffer. Physical-device Search/IME work is still required;
use the maintained [hardware checklist](../runbooks/s3-hardware-checklist.md).
