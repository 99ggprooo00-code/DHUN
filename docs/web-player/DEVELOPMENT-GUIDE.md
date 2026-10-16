# DHUN Web — development guide

Everything below runs in this repository. Where a path differs from the one in
the bootstrap plan that this directory implements, the mapping table at the end
explains why.

## Prerequisites

- **Node.js 18 or newer** — for the app's own tooling only. The application
  ships no runtime dependency, so Node is not needed to *run* it, only to build,
  test and serve it locally.
- **Python 3.11 or newer** — the repository's contract suite
  (`scripts/test_*.py`) is the gate that runs in CI with no Node and no network.
- **A modern browser.** Chrome/Edge 123+, Firefox 120+ or Safari 17.5+ for the
  two-scheme token sheet and the appearance control (`light-dark()` and `:has()`).
- **Git**, and `gh` if you want to open pull requests from the terminal.

## Setup

```bash
git clone https://github.com/99ggprooo00-code/DHUN.git
cd DHUN
npm test                     # the web app's own suite; no install needed
python3 -m unittest discover -s scripts -p 'test_*.py'   # the repository gate
```

There is **no install step for the web app** — that is a rule of the project,
not an oversight (`app-web/package.json` declares no dependency, and
`scripts/test_app_web.py` fails if one appears). The site's toolchain is the one
exception: `cd website && npm ci` fetches Eleventy and its plugins.

## Running it locally

```bash
npm run dev                  # → app-web/tools/serve.mjs
```

The dev server exists to make local behaviour match the deployed behaviour, and
it is worth reading its header before changing it:

- it serves `app-web/dist/` when a build exists and `app-web/src/` otherwise, so
  what you test is what shipped;
- it sends the same Content-Security-Policy the deployed page sends, so a
  header-only relaxation cannot hide a violation during development;
- it serves the service worker with the right MIME type and
  `Service-Worker-Allowed`, so the offline shell can be exercised;
- it binds `0.0.0.0`, so a sandbox preview or a phone on the same network can
  reach it.

**To test the app the way a visitor gets it** — the site at `/` and the app at
`/app/`, which is what the deployment assembles:

```bash
npm run build                                   # app-web/dist
cd website && npm run build && cd ..            # website/dist
rm -rf /tmp/dhun-preview && mkdir -p /tmp/dhun-preview/app
cp -r website/dist/. /tmp/dhun-preview/
cp -r app-web/dist/. /tmp/dhun-preview/app/
python3 -m http.server 8080 --bind 0.0.0.0 --directory /tmp/dhun-preview
```

Then `/`, `/features/`, `/ui/` and `/app/`. This matters more than it looks:
root-hosted local servers do not reproduce the `/DHUN/` project sub-path that
the deployed site runs under, and this repository has shipped that bug once.

### Looking at the states without a network

```bash
npm run states                       # → /tmp/dhun-states/states-dark.html and states-light.html
npm run states -- --scheme dark      # or --out <dir>
```

The app's failure states are not edge cases: the primary source is a cross-origin
request to an origin nobody here controls, so "the source refused" is one of the
first things a real visitor can meet. That makes them exactly the states nobody
looks at, and this app shipped the consequence once — an error screen whose
headline was the browser's `TypeError: Failed to fetch`.

`app-web/tools/states.mjs` renders **every** state the chrome can be in, from the
real modules, into one page per colour scheme: the refusal, a 502 from a search, a
shape change, a timeout, the demo catalogue, an empty library, a browser that
refuses to store anything, the gesture prompt, the framed refusal, the player
playing. It is a development tool: it writes outside the repository by default,
it is never copied into `app-web/dist`, and the build would fail on it if it
were. The buttons carry the app's action names and are inert — nothing on the
page is wired up.

It measures nothing, and it is not a substitute for the browser job. Its job is
the one thing a person is still needed for: reading the words on the screen.

## Testing

| Command | What it proves |
|---|---|
| `npm test` (root, or in `app-web/`) | The app's own suite: 165 tests over the views, the player state machine, the audio engine, storage, the router, the source contract, the icons and the shell. |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | The repository's contract suite — the same step CI runs first, with no Node and no network. Includes the web app's source-level rules and the site's honesty rules. |
| `python3 scripts/website_quality.py website/dist` | The site's design-system, contrast, accessibility and budget rules on the built pages. |
| `python3 scripts/website_claims.py website/dist` | The site's honesty contract: forbidden claims, required caveats, no baked digests, no telemetry SDK. |
| `python3 scripts/web_player_docs.py docs/web-player` | These documents: required structure, evidence beside every completed phase, and no claim the repository cannot back up. |
| `cd website && npm run test:rules` | The browser job's decision rules, as pure functions, with unit tests. |
| `cd website && node tools/verify-minify.mjs` | The committed `website/dist` matches a fresh build byte for byte. |
| `cd website && npx html-validate "dist/**/*.html"` | Markup validity on the built pages. |
| `npm run states` | Not a test: renders every app state to a page for a person to read, including the ones that only appear when somebody else's server says no. |

`npm run check` at the repository root runs the web workstream's local gate in
one command (icons, build, tests, documents).

## What only CI can verify

This is worth stating plainly, because it is where the project's honesty rules
come from:

- **A real browser.** `website/tests/browser.mjs` renders every route at four
  viewport/scheme combinations and measures overflow, contrast, tab order,
  keyboard paths, motion, print and axe. It runs in the `browser` job of
  `.github/workflows/website.yml`, never locally in this environment, which has
  no browser binary.
- **The deployed bytes.** `scripts/website_smoke.py` re-reads the live site after
  a publish and applies the same honesty rules to what a visitor actually gets.
- **The native clients.** Gradle, the Android build and the desktop build are
  CI-only here; a web change must not be able to redden them, and the CI job
  builds all of them on every pull request, web-only ones included.

## Project structure

```
app-web/                    the application (no dependency, no bundler)
├── src/                    what is deployed; a byte-for-byte copy of this is the build output
│   ├── index.html          the document, the CSP, the manifest link
│   ├── manifest.webmanifest, sw.js, assets/
│   ├── css/                tokens.css (generated from the app's Kotlin tokens) + app.css
│   └── js/                 main.js and the modules it reaches
├── tests/                  node:test, no runner to install
├── tools/                  build.mjs, serve.mjs, the icon generators
└── dist/                   generated; git-ignored, rebuilt by CI
website/                    the site that publishes the app at /app/
├── src/                    Eleventy sources (nunjucks, data, css)
├── css/, tools/            the generated token sheet and its generator
├── tests/                  the browser job, and rules.mjs with its unit tests
└── dist/                   committed build output (the gates read it)
scripts/                    the Python contract suite and the site's rules
docs/web-player/            this directory
```

## Repository entry points

The web workstream has one command for each thing a person wants to do, and none
of them needs an install at the root:

| File | What it is |
|---|---|
| `package.json` (root) | `npm run dev` / `build` / `test` / `gen:icons` / `check:icons` delegate to `app-web/`; `check:docs` runs `scripts/web_player_docs.py`; `check:repo` runs the repository's Python contract suite; `check` runs icons, build, tests and documents in one go. It declares **no dependency**, so nothing has to be fetched to use it. |
| `.env.example` | Documents that there is **nothing to configure**: no API key, no token, no account, no analytics id, and no `.env` loader anywhere in the tree. The only environment variable in the workstream is the dev server's optional `PORT`, read as a plain shell variable (`PORT=4173 npm run dev`). |
| `.editorconfig` | Line endings, indentation and trailing-whitespace rules. Markdown keeps its trailing whitespace, because two spaces at the end of a line are a line break. |
| `.github/CODEOWNERS` | Review routing, with explicit lines for the files that decide what the project is allowed to claim: `scripts/website_claims.py`, `scripts/website_quality.py`, `scripts/web_player_docs.py` and `app-web/src/index.html` (the CSP). |
| `.github/ISSUE_TEMPLATE/` | Three templates. The playback-failure one asks for the browser, the network, the source badge and the console output, because "it did not play" with none of those is not actionable. |
| `.github/pull_request_template.md` | Extended, not replaced, with a web-workstream section: the documents gate, the no-fake-playback and no-overpromise rules, and the reminder that a phase status may only change in the same pull request as its evidence. |

## Branching and pull requests

- `main` is the only long-lived branch and it is always deployable.
- Work happens on a session branch (`arena/<id>-dhun`) and arrives by pull
  request. The repository's CI runs on every pull request and on every push to
  an `arena/**` branch.
- One pull request, one subject. If a change fixes two things, say so in the
  body and keep the commits separate — the history is read by people.
- Every pull request body states what was **not** tested, in the same list as
  what was. "Passed CI" is not the same claim as "verified in a browser", and
  the template asks for the difference.
- Never merge with a red check. If a check is red for a reason that looks like a
  flake, re-run it and say so; do not delete the check.

## Deployment

The site and the app deploy together, from `.github/workflows/website.yml`, on a
push to `main`. The workflow is the **single owner of the Pages artifact**: it
builds the site with the `/DHUN` project prefix, copies `app-web/dist` to
`/app/`, verifies every internal link, publishes, and then re-reads the live site
with `scripts/website_smoke.py`.

There is deliberately **no second deploy workflow**. A separate workflow
publishing the app to the same Pages environment would race the site's, and each
would overwrite the other's output — the deployment is one artifact, so it has
one owner.

Where the deployed site lives:

- site: `https://99ggprooo00-code.github.io/DHUN/`
- app: `https://99ggprooo00-code.github.io/DHUN/app/`

## Why this directory does not match the bootstrap plan's tree exactly

The plan was written for a new repository with an empty `app-web/`. This
repository already had one, further along. Rebuilding it to match the plan's
sketch would have deleted working, tested code — so the plan's *structure* is
kept where it adds something and mapped where it does not:

| Plan's path | Reality in this repository | Why |
|---|---|---|
| `app-web/index.html` | `app-web/src/index.html`, copied to `dist/` by the build | The build output is separate from the source, so a source edit cannot be mistaken for a deploy |
| `app-web/manifest.json` | `app-web/src/manifest.webmanifest` | Same file, the correct extension for the MIME type the server must send |
| `app-web/src/js/{audio,player,player-ui,views,router,storage,lyrics,mediasession,tabsync,pwa}.js` | Same names, same directory | No change needed |
| `app-web/src/js/providers/*.js` | `app-web/src/js/source.js` | The provider seam is *inside* `source.js`: one module holds the contract, the kinds, the failure taxonomy and the adapters, so a new provider cannot drift from the contract it implements. Splitting it into stub files would have created empty modules — and the build fails on a module `main.js` does not reach |
| `app-web/src/js/utils.js` | `dom.js`, `format.js`, `version.js` | Three small modules with single jobs rather than one grab-bag |
| `app-web/src/css/{main,player,frosted-glass,responsive}.css` | `tokens.css` (generated) + `app.css` | The design tokens are generated from the app's Kotlin sources, and the site's sheet is generated from *that*; four hand-written stylesheets would be four places for the design to drift. This is the one place the plan's structure was rejected on purpose |
| `app-web/tests/*.test.mjs` | Same, sixteen files instead of four | The plan's four are included (`source`, `player`, `audio`, `storage`); the rest cover views, router, tokens, icons, shell, shortcuts, lyrics, equalizer and platform |
| `app-web/tools/build.mjs` | Same, and it does more than validate: it proves the generated icons are current, that every module is reachable and cached by the service worker, and it prints the shipped weight | A build that only checks for file existence cannot catch the two failures that actually reach users |
| `.github/workflows/deploy.yml` | Folded into `website.yml` | One Pages artifact, one owner — see Deployment above |
| `LICENSE` (MIT) | `LICENSE` (GPL-3.0-or-later) | The repository's licence, which the web app inherits; see the note in [README.md](README.md) |
| `docs/web-player/*` | These files | New, and the substance of this workstream |
