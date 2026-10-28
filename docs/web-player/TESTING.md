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
writing that number is **165** (`app-web/tests/`, fifteen files including the
helpers folder).

## 2. The browser-app suite

`app-web/tests/` holds the tests for the pure modules and the rendered markup:

| File | Its own stated purpose (from the file's header comment) |
|---|---|
| `actions.test.mjs` | Every control does something, and every handler can be reached. |
| `api.test.mjs` | The catalogue seam. |
| `demo.test.mjs` | The sample catalogue. |
| `dom.test.mjs` | The markup contract. |
| `format.test.mjs` | Presentation helpers. |
| `legal.test.mjs` | The legal renderer. |
| `library.test.mjs` | Likes, playlists, history, settings and the storage handle. |
| `player.test.mjs` | The queue and the transport. |
| `router.test.mjs` | Routes, and that every route survives a round trip. |
| `shell.test.mjs` | The shipping contract: one app, one stylesheet set, one offline shell. |
| `shortcuts.test.mjs` | The keyboard map, and a refusal to take keys from someone typing. |
| `tokens.test.mjs` | Design tokens. |
| `transfer.test.mjs` | Importing a library from a file. |
| `views.test.mjs` | The screens render, escape, and tell the truth. |

The file headers are the authors' statement of purpose. The assertions themselves
are the authority; read the file before relying on a specific behaviour.

`app-web/tests/helpers/` holds shared fixtures.

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
