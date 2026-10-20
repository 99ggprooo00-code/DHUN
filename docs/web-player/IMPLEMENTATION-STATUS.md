# DHUN Web — status and evidence

> **Status: the build ships and works; it is NOT VERIFIED FOR RELEASE.**
> Last updated: 2026-10-20.

Two facts set that status, and both are named in "Not verified" below: nobody has
heard audio come out of the **deployed** origin, and the catalogue this build
reads is a community-run wrapper with no support contract. Everything else is
either implemented and unit-tested, or measured in a real browser on every pull
request, or both.

## What is verified, and by what

Every row names a file in this repository that exists and does the work. The gate
`scripts/web_player_docs.py` fails when a row's evidence stops resolving, so this
table cannot quietly describe a build that has moved on.

| Claim | Evidence |
|---|---|
| The build assembles from the sources, and every module is reachable from `main.js` | `app-web/tools/build.mjs` — it fails on an unreachable module or a shell entry that is not in the build |
| 153 assertions over the pure modules pass with no install and no network | `app-web/tests/` — routing, escaping, queue, stream selection, library shapes, API normalisation, transfer, screens, CSS coverage, shortcuts |
| The web build is a real app, not a preview: regions before the module runs, a strict CSP, `noindex`, no third-party asset, no downloads surface, a labelled sample catalogue | `scripts/test_app_web.py` |
| Every screen renders one `h1`, resets focus to it after navigation, and titles the document | `website/tests/browser.mjs` (`entity outline`, `/app/ dialog focus`) |
| Rendered contrast meets 4.5:1 / 3:1 on every visible line, in both themes | `website/tests/browser.mjs` (`/app/ contrast`) |
| axe reports no serious or critical violation on any route, in both themes, with the player and the sheets open | `website/tests/browser.mjs` (`/app/ axe`) |
| Sheets are dialogs: focus enters, Tab wraps, Escape closes, focus returns to the button that opened it | `website/tests/browser.mjs` (`/app/ dialog focus`) |
| A playlist made in the browser opens, keeps its rows across a reload, and renames through the same sheet | `website/tests/browser.mjs` (`local playlist`), `app-web/src/js/library.js` |
| The shell opens with the network off, and says which catalogue answered | `website/tests/browser.mjs` (`offline`), `app-web/src/sw.js` |
| The player deep link says what it is waiting for, then opens the player when a track starts | `website/tests/browser.mjs` (`player deep link`) |
| The appearance control themes the document, not just the chrome | `website/tests/browser.mjs` (`appearance`), `app-web/src/css/tokens.css` |
| The catalogue seam turns a timeout, an offline browser and an HTTP error into distinct sentences, with the technical detail labelled | `app-web/src/js/api.js`, `app-web/tests/api.test.mjs` |
| A dead instance is skipped, the one that answered is remembered, and a total failure names every host | `app-web/src/js/api.js` (`createRacer`, `describeAttempts`), `app-web/tests/api.test.mjs` (six failover tests), `scripts/test_app_web.py` (the CSP must name every instance `api.js` requests) |
| A row with no stream is refused in words rather than played silently | `app-web/src/js/player.js`, `app-web/src/js/player-ui.js` |
| The site and `/app/` deploy together from one workflow, and the served bytes are re-read after each publish | `.github/workflows/website.yml`, `scripts/website_smoke.py` |
| These documents describe the build that exists | `scripts/web_player_docs.py` |

## Not verified

- **Nobody has heard audio from the deployed `/app/`.** The code path is real —
  a URL from the catalogue's `downloadUrl` list goes into an `<audio>` element —
  but no author of this repository can state from evidence that the deployed page
  produced sound. A browser check on a runner cannot hear it either. Treat this as
  the release gate until a person records what happened.
- **The live catalogue from the deployed origin.** Three instances are configured
  and the failover is unit-tested against a fake network, but the browser run
  records the source badge it finds; it has never recorded a live response from
  the deployed page. A CORS refusal, a 429 or an empty shelf is a finding to
  record, not a success to assume.
- **Browsers other than the one CI launches.** The measurements come from one
  Chromium build on Linux. Safari, Firefox and an Android browser on a phone are
  untested.
- **A screen-reader pass.** axe checks what a DOM can be checked for; nobody has
  listened to the app with a screen reader.
- **A person's judgement of the look.** CI measures geometry, contrast and
  semantics. It does not see whether the result is pleasant to use.
- **The service's terms.** This build reads a community wrapper around JioSaavn.
  Whether that is permitted, and what must be disclosed, is an open question for
  the project and for a lawyer — not something the code can settle. The app says
  what it calls and where, and makes no claim of authorisation.

## What "done" would mean

The checklist below is the definition of done. A ticked box means the claim was
verified and this file names the evidence; an unticked box means it was not.

- [x] **The interface ships and is reachable** — the site and `/app/` are live at
  `https://99ggprooo00-code.github.io/DHUN/`, and `scripts/website_smoke.py`
  re-reads the served bytes after every publish.
- [x] **The code is unit-tested** — `cd app-web && npm test` → 153 pass;
  `python3 -m unittest discover -s scripts -p 'test_*.py'` → 459 tests OK, the
  web app's source-level rules included.
- [x] **The rendered pages are measured in a real browser** — `website/tests/browser.mjs`
  runs on every pull request and prints its numbers as check annotations.
- [x] **No paid infrastructure and no DHUN server** — static GitHub Pages, one
  third-party API called from the tab, no function, no database, no proxy.
- [x] **No tracking** — no analytics, telemetry or crash reporting; the CSP names
  three catalogue deployments and its CDN, and nothing else.
- [x] **The limits are visible in the product** — the source badge, the sample
  catalogue notice, the failure states, `Settings → Catalogue`, and
  [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md).
- [x] **Licensing and attribution** — `LICENSE` (GPL-3.0-or-later),
  `THIRD_PARTY.md`, and the licences page rendered from the bundle.
- [ ] **A real catalogue answers the deployed page** — unverified; see above.
- [ ] **At least one track is heard to play** — unverified; this is the gate.
- [ ] **A second browser, and an Android phone, are measured** — not done.
- [ ] **A screen-reader pass** — not done.
- [ ] **A person has listened and looked** — not done.

## How to move it forward

In order; nothing below the first line matters until it is done.

1. Open the deployed `/app/` in a real browser on a real network. Search, play a
   track, and write down exactly what happened — sound, or the sentence the app
   showed.
2. If it refused, keep the refusal verbatim. A CORS, 429 or "no stream" answer is
   evidence about the source, not a bug to hide; it belongs in
   [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) and in the source's own words on
   screen.
3. Only then: the second browser and the phone, the screen-reader pass, and the
   release decision.
