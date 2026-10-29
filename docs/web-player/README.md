# DHUN Web (`/app/`) — the browser build

**Direction (2026-10-28):** DHUN is a free, open-source, cross-platform,
**multi-source music client**; [PRD §1](../PRD.md#1-product-definition-and-source-of-truth)
is authoritative. YouTube / YouTube Music is first implementation priority.
This separate browser client currently uses **JioSaavn via unofficial community
wrappers**, not the native YouTube path. Spotify is planned, not implemented,
and deferred pending all technical/account/Premium/policy/legal/playback/privacy/
licence/cost gates. Other sources require evidence; platform features may differ.
No new backend, provider or paid service is adopted by documentation alignment.

This directory describes **DHUN in a browser**: the application at
[`/app/`](https://99ggprooo00-code.github.io/DHUN/app/) and the static site that
publishes it.

It exists because the browser build fails differently from the native ones. On
Android and Windows a broken player is visibly broken; in a browser it is very
easy to draw a player that looks right and produces no sound. These documents
describe the build that is actually in `app-web/` — not a plan for one, and not
the one it replaced. `scripts/web_player_docs.py` fails when a module named here
stops existing, when an evidence path does not resolve, or when this prose
carries a claim the repository cannot back.

> **Status: runs, and is not verified for release.** The interface, the library
> and the player ship and are measured in Chromium on every pull request. Two
> things are not proven and are named in
> [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md): nobody has heard audio
> from the deployed origin, and the catalogue it reads is a community-run
> service with no support contract.

## The build in one paragraph

A static page — one HTML file, two stylesheets, twenty ES modules, a manifest
and a service worker — with **no runtime dependency, no build step that needs an
install, no DHUN-operated server and no account**. The catalogue is JioSaavn,
read through unofficial community wrappers at `saavn.dev`, `saavn.sumit.co` and
`saavnx.vercel.app` with ordinary
`fetch` from the visitor's own browser. Likes, playlists, history and settings
are in IndexedDB, with the legacy `dhun.web.v3` localStorage key retained. When the catalogue does not
answer, the app says so in the network's own words, keeps whatever it has, and
falls back to a **labelled sample catalogue** whose rows carry no audio.

## The priority order

When two of these conflict, the higher one wins.

1. **Honesty.** Every control either does the thing or says plainly why it
   cannot. No simulated results, no invented progress, no silent failure.
2. **A real player.** Audio comes from a URL the catalogue returned, played by
   an `<audio>` element whose own clock draws the progress bar.
3. **No running cost and no DHUN server.** Static hosting only; the request goes
   from the tab to the catalogue, never through anything this project operates.
4. **The DHUN interface** — dark first, frosted glass, the app's own tokens.
5. **A phone and a desktop are both first-class.** Same shell, same shortcuts.
6. **Polish.**

## The hard rules, and what enforces each one

A rule that nothing checks is a rule that lasts until the next busy session.

| Rule | Enforced by |
|---|---|
| **No running cost, no DHUN backend.** The only origins the page may reach are the catalogue and its CDN. | `app-web/src/index.html`'s CSP (`connect-src https://saavn.dev https://saavn.sumit.co https://saavnx.vercel.app https://*.saavncdn.com`); `scripts/test_app_web.py::ShipsNoThirdPartyRuntimeAsset`; `docs/decisions/ADR-008-browser-web-player-target.md` boundary 1 |
| **No fake playback.** Progress is drawn from the media element's own clock; a "playing" state implies a media element that is actually playing. | `app-web/src/js/player.js` (queue and transport), `app-web/src/js/player-ui.js` (the clock), `app-web/tests/player.test.mjs` |
| **No simulated catalogue.** Sample rows are labelled at the source *and* on screen, and carry no stream. | `app-web/src/js/demo.js`, `app-web/src/js/components.js` (`sourceBadge`, `notice`), `scripts/test_app_web.py::DoesNotSellWhatABrowserCannotDo` |
| **Honest failures.** Timeout, offline, HTTP error and a row with no stream are distinct states that name what happened. | `app-web/src/js/api.js` (`SourceError`, `describeFailure`), `app-web/src/js/components.js` (`errorState`), `app-web/tests/api.test.mjs` |
| **No browser error as the headline.** A failure leads with a sentence this build wrote; the browser's or the server's string follows, labelled "Technical detail". | `app-web/tests/api.test.mjs`; `app-web/tests/views.test.mjs` (rendered markup) |
| **No promise the browser cannot keep.** No downloads, no offline audio, no equaliser, no lyrics, no sync. | `scripts/test_app_web.py::DoesNotSellWhatABrowserCannotDo` (source), `scripts/website_claims.py` (the site's copy) |
| **The shell opens offline; the music does not.** Every module is in the worker's shell list. | `app-web/src/sw.js` (`SHELL`), `app-web/tools/build.mjs` (list checked against the real build), `website/tests/browser.mjs` (`/app/ offline`) |
| **No secrets, no tracking.** No analytics, telemetry, crash-reporting or third-party script. | `scripts/test_repo_health.py`; `website_claims.py` telemetry rules over the shipped dependency graph |
| **This page is not indexable and not framable.** | `noindex, nofollow` in `app-web/src/index.html`; the boot-time framing refusal in `app-web/src/js/main.js`; `scripts/test_app_web.py::IsARealWebAppNotAPreview` |
| **Nothing is lost from the native project.** The web app is a second client, not a replacement. | No file under `shared/`, `app-android/` or `app-desktop/` is touched by web work; `.github/workflows/ci.yml` builds and tests all of them on every pull request |

## Files in this directory

| File | What it is |
|---|---|
| [README.md](README.md) | This page: what the build is, the rules that hold it, and the parity table below |
| [PRD.md](PRD.md) | Product requirements: purpose, users, goals, functional requirements, non-goals, open questions |
| [TRD.md](TRD.md) | Technical requirements: platform, Content Security Policy, network, storage, service worker, accessibility |
| [APP-FLOW.md](APP-FLOW.md) | Routes, boot, the flows a visitor follows, failure paths, screen states |
| [UI-UX-DESIGN.md](UI-UX-DESIGN.md) | The interface that exists: layout, tokens, components, motion, contrast, what the browser checks measure |
| [DATA-SCHEMA.md](DATA-SCHEMA.md) | IndexedDB transactions, legacy migration, recovery, limits and the export file |
| [PROVIDER-INTEGRATION.md](PROVIDER-INTEGRATION.md) | The catalogue origins, endpoints, failover, failure kinds, the sample catalogue, sources not in the build |
| [PLAYBACK.md](PLAYBACK.md) | Player statuses, stream choice, queue and repeat and shuffle rules, failures, Media Session |
| [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) | What leaves the browser, what is stored, framing, CSP, input handling, open policy questions |
| [TESTING.md](TESTING.md) | Commands, the browser suite, the Python checks that cover the web build, the site workflow, what is not tested |
| [RELEASE-DEPLOYMENT.md](RELEASE-DEPLOYMENT.md) | Version identifiers, the path to publication, rollback, hosting |
| [RUNBOOK.md](RUNBOOK.md) | What to do when the catalogue, playback, storage, cache or a publish misbehaves |
| [RESEARCH-REGISTER.md](RESEARCH-REGISTER.md) | Research that bears on the browser, verification records, and open research questions |
| [DECISIONS-AND-CHANGES.md](DECISIONS-AND-CHANGES.md) | Decisions that shape the browser build, and dated changes to this set |
| [USER-GUIDE.md](USER-GUIDE.md) | For a visitor: opening, the library, installing, keyboard, troubleshooting |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the modules fit together: boot, routing, the catalogue seam, the player, storage, the service worker |
| [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md) | How to run, build and test it; the conventions a change has to follow |
| [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) | What is verified, by what, and what is still unproven |
| [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) | What is built, the known defects, and the proposed next steps in dependency order (not a schedule) |
| [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) | The limits a visitor can meet, in plain language |

### Parity with the native document set

The native app's documents live in [`docs/`](../README.md). Each one below is
either mirrored here for the browser build or stated as not applicable. The docs
gate checks that every file in the table above exists.

| Native document | Browser counterpart | Note |
|---|---|---|
| `docs/PRD.md` | [PRD.md](PRD.md) | |
| `docs/TRD.md` | [TRD.md](TRD.md) | |
| `docs/app-flow.md` | [APP-FLOW.md](APP-FLOW.md) | |
| `docs/implementation-plan.md` | [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) | Not a schedule. |
| `docs/ui-ux-design.md` | [UI-UX-DESIGN.md](UI-UX-DESIGN.md) | |
| `docs/backend-schema.md` | [DATA-SCHEMA.md](DATA-SCHEMA.md) | The browser has no backend; its schema is the local document. |
| `docs/INSTALL.md` | [USER-GUIDE.md](USER-GUIDE.md) | Opening the web app instead of installing a package. |
| `docs/development/` | [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md) | |
| `docs/decisions/` | [DECISIONS-AND-CHANGES.md](DECISIONS-AND-CHANGES.md), and the shared ADRs in `docs/decisions/` | ADR-008 and ADR-010 apply to the browser build. |
| `docs/research/` | [RESEARCH-REGISTER.md](RESEARCH-REGISTER.md) | |
| `docs/verification/` | [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) and the web records listed in the research register | The browser's evidence is the status table and the browser run. |
| `docs/runbooks/` | [RUNBOOK.md](RUNBOOK.md) | The site's publishing runbook stays in `docs/runbooks/`. |
| `docs/releases/` | [RELEASE-DEPLOYMENT.md](RELEASE-DEPLOYMENT.md) | The native release policy does not apply to the browser build. |
| `docs/legal/` | [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) (engineering description only) | The in-app privacy and terms texts are in `app-web/src/js/data/web-legal.js`. No legal review exists. |
| `docs/ux-audit.md` | Not mirrored yet | Needs a recorded browser run; listed in the implementation plan. |
| `docs/METROLIST_PARITY.md` | Not applicable | Covers the native lyrics parity audit, not the browser. |
| `docs/history/` | Not applicable | Historical records stay where they are. |

The site that publishes it is described in `.ai/WEBSITE_PLAN.md`; the record of
what was measured under `/app/` is `docs/verification/` and the browser run's own
annotations. The Android and Windows builds are documented in `docs/` and are
**not** described here.

## Where it is deployed

`/app/` on the project's GitHub Pages site. The site workflow owns the Pages
artifact and copies `app-web/dist/` into it, so the web app and the marketing
site are published from one repository and one deploy, at one URL:
`https://99ggprooo00-code.github.io/DHUN/app/`.

## Keyboard

`Space` play/pause · `N` / `P` next / previous · `→` / `←` seek five seconds ·
`↑` / `↓` volume · `M` mute · `S` shuffle · `R` repeat · `/` search · `?` the
shortcut sheet · `Esc` closes a sheet, then the player. Keys are never taken from
a text field and never from a combination the browser owns
(`app-web/src/js/shortcuts.js`, checked against the dispatcher by
`app-web/tests/shortcuts.test.mjs`).
