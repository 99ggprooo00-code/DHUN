# DHUN Web — development guide

Everything here runs with Node's own tooling. There is **no install step**: the
module declares no dependency, and neither does the site that publishes it.

## Run it

```bash
cd app-web
node tools/serve.mjs 4173     # http://127.0.0.1:4173/ — serves src/, no build
npm test                      # every assertion in tests/, no browser, no network
node tools/build.mjs          # src/ → dist/, then checks the shell and the modules
```

`tools/serve.mjs` serves the **source** tree, so an edit is one reload away. Run
`node tools/build.mjs` before anything that reads `dist/` — the browser run, the
Pages artifact, or the "does every module load" check.

## The gates

| Command | What it proves |
|---|---|
| `cd app-web && npm test` | The pure half: routing round trips, escaping, the queue and repeat/shuffle rules, stream selection, the library's return shapes, API normalisation, transfer files, every screen's rendered markup, CSS class coverage, and the keyboard map against the dispatcher. |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | The repository's Python contract suite, `scripts/test_app_web.py` included: regions exist before the module runs, no raw hex or px outside `tokens.css`, no third-party asset, `noindex`, no invented lyrics, the sample catalogue is labelled, the service-worker shell is complete, and the theme/rail rules that have regressed before. |
| `node tools/build.mjs` | Every module is reachable from `main.js`, every file the worker names exists in the build, and the build reports its own byte counts. |
| `npm run gen:icons -- --check` | `js/icons.js` still matches `DhunIcons.kt` — the icon set is generated, not hand-edited. |
| `python3 scripts/web_player_docs.py` | These documents still describe the build that exists: every module named is present, every evidence path resolves, no retired name survives, no claim the repository cannot back. |
| `website/tests/browser.mjs` | The real run: Chromium at two viewports in both themes, axe on every `/app/` route, rendered contrast on every visible line, dialog focus and Tab traps, the local playlist round trip across a reload, the player deep link, offline reload, and the appearance control. |

CI runs all of them on every pull request (`.github/workflows/ci.yml` and
`.github/workflows/website.yml`). None of them needs a credential, a service or
a network connection except the browser job's own run.

## Adding a screen

A screen is four edits, and the gates will tell you if one is missing:

1. **`js/router.js`** — add the name to the route set and to `parse`/`format`,
   and give it a title in `title()`. The round-trip test renders every route, so a
   route that cannot be written is a red test.
2. **`js/views.js`** — export a function that takes a view-model and returns
   `html\`…\``. It must render its own `<h1>` (the browser run fails on two, or on
   a screen with none), and it must render loading, empty and error states where
   the data can be absent.
3. **`js/main.js`** — build that view-model in `renderView()`, fetch anything it
   needs in the route resolver, and add its actions to the action table. Every
   `data-action` the markup emits has to be in that table; nothing wires
   listeners per element.
4. **`js/shell.js`** — only if the screen belongs in the navigation.

Then add a case to `app-web/tests/views.test.mjs`. The escaping test renders every
view with representative data and fails on any escaped angle bracket — the `html`
tag escapes interpolated values, and a nested template without `raw()` renders as
visible markup.

## Rules that are easy to break

- **No inline event handlers, no `innerHTML` with user data.** Markup goes
  through `html`; `raw()` is for markup this code built.
- **No colour and no pixel length outside `tokens.css`.** Use
  `var(--dhun-…)`. Media-query breakpoints are the only exception, and they are
  cross-checked against the token file.
- **A media query adds no specificity.** A responsive override has to come after
  the rule it overrides.
- **Every state says what happened.** Loading, empty, error, offline and
  "this row has no stream" are different sentences, and the technical detail is
  never the headline.
- **No fake data.** A sample row is `demo: true`, has no stream, and the screen
  says the catalogue is a sample. Never merge sample rows into live results.
- **Do not add a dependency.** The build, the tests and the app all rely on that
  staying true; `scripts/test_app_web.py::ModuleHasNoRuntimeDependency` fails on
  a `dependencies` block.
- **Do not add a DHUN endpoint.** No proxy, no serverless function, no analytics
  relay. The CSP is the contract and `scripts/test_app_web.py` reads it.
- **Keyboard.** A new shortcut goes in `js/shortcuts.js`, which the dispatcher
  and the shortcut sheet both read; `tests/shortcuts.test.mjs` fails on a key the
  dispatcher does not handle.

## When the catalogue disagrees with this build

The catalogue instances are community-run and independent of this project. If a
field disappears, the fix belongs in
`normalise*()` in `js/api.js` — the rest of the app reads the normalised shape
and should not learn about the API's own JSON. If the endpoint changes, the
`ENDPOINTS` table is the only place to edit, and the failure text a visitor sees
is generated by `describeFailure()` rather than written per screen.

## Committing

Small, reviewable changes; one concern per pull request. The repository's gates
are the review: a change that makes a measurement worse is a change to explain or
undo. If a check fails, diagnose it — do not widen the check to fit the code.

## Where the pieces live

| Path | What it is |
|---|---|
| `app-web/src/index.html` | The document: regions, CSP, `noindex`, manifest, icons |
| `app-web/src/js/` | The modules (see [ARCHITECTURE.md](ARCHITECTURE.md)) |
| `app-web/src/css/tokens.css` | The app's tokens, mirrored from the Kotlin design source |
| `app-web/src/css/app.css` | Every rule the interface draws with |
| `app-web/src/sw.js` | The shell-only service worker |
| `app-web/tools/` | `serve.mjs`, `build.mjs`, `gen-icons.mjs`, `gen-icons-png.mjs` |
| `app-web/tests/` | The Node suite, one file per module plus rendered-markup tests |
| `scripts/test_app_web.py` | The source-level contract the Node suite cannot see |
| `website/tests/browser.mjs` | The Chromium run against the built site and `/app/` |
| `.github/workflows/` | `ci.yml` (app + native), `website.yml` (site, browser, deploy) |
