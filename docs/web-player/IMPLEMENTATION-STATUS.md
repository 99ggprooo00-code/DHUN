# DHUN Web — status and evidence

> **Status: Partial integration; source/unit evidence, NOT VERIFIED FOR RELEASE.**
> Current behavior is checked by the named source/tests below; earlier product alignment is [audit 06](../research/06-multi-source-claims-audit.md).

DHUN is multi-source by direction ([PRD §1](../PRD.md#1-product-definition-and-source-of-truth)),
YouTube Music first; native currently uses YouTube Music, this separate browser
uses unofficial JioSaavn wrappers. Spotify is planned/deferred, not implemented.
The interface and test paths exist; live catalogue and audible deployed-origin
playback remain unverified. Permissions, legal adoption and notice completeness
are separate release gates, not cured by passing geometry/unit checks.

The table identifies the code/gates that check each behaviour. A gate existing
is not proof that it ran in a particular environment. Run the commands in
TESTING.md and read that PR/CI execution's results. Earlier CI/browser evidence
is dated, not a new canonical-origin or hardware check.

## What is verified, and by what

Every row names a file in this repository that exists and does the work. The gate
`scripts/web_player_docs.py` fails when a row's evidence stops resolving, so this
table cannot quietly describe a build that has moved on.

| Claim | Evidence |
|---|---|
| The build assembles from the sources, and every module is reachable from `main.js` | `app-web/tools/build.mjs` — it fails on an unreachable module or a shell entry that is not in the build |
| 305 assertions over modules and Node controller fixtures pass with no install and no network | `app-web/tests/` — routing, escaping, queue, stream selection, library shapes, API normalisation, transfer, screens, CSS coverage, shortcuts |
| The web build is a real app, not a preview: regions before the module runs, a strict CSP, `noindex`, no third-party asset, no downloads surface, a labelled sample catalogue | `scripts/test_app_web.py` |
| Every screen renders one `h1`, resets focus to it after navigation, and titles the document | `website/tests/browser.mjs` (`entity outline`, `/app/ dialog focus`) |
| Rendered contrast meets 4.5:1 / 3:1 on every visible line, in both themes | `website/tests/browser.mjs` (`/app/ contrast`) |
| axe reports no serious or critical violation on any route, in both themes, with the player and the sheets open | `website/tests/browser.mjs` (`/app/ axe`) |
| Sheets are dialogs: focus enters, Tab wraps, Escape closes, focus returns to the button that opened it | `website/tests/browser.mjs` (`/app/ dialog focus`) |
| A playlist made in the browser opens, keeps its rows across a reload, and renames through the same sheet | `website/tests/browser.mjs` (`local playlist`), `app-web/src/js/library.js` |
| The browser test checks one offline reload and its catalogue label; shell caching remains best-effort, not a guarantee for every install | `website/tests/browser.mjs` (`offline`), `app-web/src/sw.js` |
| The player deep link says what it is waiting for, then opens the player when a track starts | `website/tests/browser.mjs` (`player deep link`) |
| The appearance control themes the document, not just the chrome | `website/tests/browser.mjs` (`appearance`), `app-web/src/css/tokens.css` |
| The catalogue seam turns a timeout, an offline browser and an HTTP error into distinct sentences, with the technical detail labelled | `app-web/src/js/api.js`, `app-web/tests/api.test.mjs` |
| A dead instance is skipped, the one that answered is remembered, and a total failure names every host | `app-web/src/js/api.js` (`createRacer`, `describeAttempts`), `app-web/tests/api.test.mjs` (six failover tests), `scripts/test_app_web.py` (the CSP must name every instance `api.js` requests) |
| Screen requests retire quietly; cached pages suspend/resume; explicit destruction removes observers and ignores late continuations without cancelling another live owner | `app-web/src/js/main.js`, `app-web/tests/navigation-requests.test.mjs`, `app-web/tests/pwa.test.mjs` (controlled Node integration, not browser acceptance) |
| Same-intent browser results retain the composing input; composition keys and partial submission stay with the IME | `app-web/src/js/main.js`, `app-web/src/js/views.js`, `app-web/tests/search-composition.test.mjs`, `website/tests/browser.mjs` (scripted protocol/DOM checks, not a physical Android IME) |
| A row with no stream is refused in words rather than played silently | `app-web/src/js/player.js`, `app-web/src/js/player-ui.js` |
| The site and `/app/` deploy together from one workflow, and the served bytes are re-read after each publish | `.github/workflows/website.yml`, `scripts/website_smoke.py` |
| These documents describe the build that exists | `scripts/web_player_docs.py` |

## Not verified

- **Current records still lack audible acceptance from the deployed `/app/`.** The code path is real —
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
- **Permission and legal review.** Targeted JioSaavn Terms clauses were re-read
  on 2026-10-28 and restrict unofficial/automated access absent separate permission.
  This build still reads community wrappers.
  Whether that is permitted, and what must be disclosed, is an open question for
  the project and for a lawyer — not something the code can settle. The app says
  what it calls and where, and makes no claim of authorisation.

## What "done" would mean

The checklist below is the definition of done. A ticked box means the claim was
verified and this file names the evidence; an unticked box means it was not.

- [x] **Publication path exists** — `.github/workflows/website.yml` assembles
  site + `/app/`, and `scripts/website_smoke.py` checks served bytes after an
  authorised deployment. No fresh deployment/canonical-origin verification is
  asserted by this audit.
- [x] **The code is unit-tested** — `cd app-web && npm test` → 305 pass;
  baseline Python suite (2026-10-28 before alignment): 466 tests OK. Fresh
  current results come from the executable commands and PR/CI output; audit 06
  retains earlier alignment results, not a new run inferred from a count.
- [x] **Browser checks are configured** — `website/tests/browser.mjs` runs
  locally/CI against an assembled artifact, not proof of sound at the deployed origin.
- [x] **No paid infrastructure and no DHUN server** — static GitHub Pages, one
  third-party API called from the tab, no function, no database, no proxy.
- [x] **No tracking** — no analytics, telemetry or crash reporting; the CSP names
  three catalogue deployments and its CDN, and nothing else.
- [x] **The limits are visible in the product** — the source badge, the sample
  catalogue notice, the failure states, `Settings → Catalogue`, and
  [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md).
- [x] **Software licence/notices are displayed** — repository `LICENSE`,
  `THIRD_PARTY.md` and browser licences page; grants/verbatim texts unchanged.
- [ ] **Provider permission, legal adoption and complete release notices** —
  unresolved; GPL is not a music/content/API licence, and the dependency table
  is not a completed resolved/transitive distribution review.
- [ ] **A real catalogue answers the deployed page** — unverified; see above.
- [ ] **At least one track is heard to play** — unverified; this is the gate.
- [ ] **A second browser, and an Android phone, are measured** — not done.
- [ ] **A screen-reader pass** — not done.
- [ ] **A person has listened and looked** — not done.

## How to move it forward

In dependency order; permission/legal and technical validation are independent
gates, neither makes the other unnecessary.

1. Resolve provider permission/legal/notice prerequisites; also open the deployed `/app/` in a real browser on a real network. Search, play a
   track, and write down exactly what happened — sound, or the sentence the app
   showed.
2. If it refused, keep the refusal verbatim. A CORS, 429 or "no stream" answer is
   evidence about the source, not a bug to hide; it belongs in
   [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) and in the source's own words on
   screen.
3. Only then: the second browser and the phone, the screen-reader pass, and the
   release decision.


## Controlled runtime reliability coverage

`app-web/tests/` covers storage acknowledgement/retry, transport intent, shared
request ownership, teardown, Search drafts and shell-cache refusals. The browser
harness uses explicit catalogue failures and controlled synthetic media to test
UI branches without contacting community providers. A passing fixture or CI run
is not current deployed-provider, hardware, permission or human-audio acceptance.
