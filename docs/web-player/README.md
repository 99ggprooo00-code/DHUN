# DHUN Web (`/app/`) — the browser build

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
read through the public [`saavn.dev`](https://saavn.dev) wrapper with ordinary
`fetch` from the visitor's own browser. Likes, playlists, history and settings
are `localStorage`, under one key (`dhun.web.v3`). When the catalogue does not
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
| **No running cost, no DHUN backend.** The only origins the page may reach are the catalogue and its CDN. | `app-web/src/index.html`'s CSP (`connect-src https://saavn.dev https://*.saavncdn.com`); `scripts/test_app_web.py::ShipsNoThirdPartyRuntimeAsset`; `docs/decisions/ADR-008-browser-web-player-target.md` boundary 1 |
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
| [README.md](README.md) | This page: what the build is, and the rules that hold it |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the modules fit together: boot, routing, the catalogue seam, the player, storage, the service worker |
| [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md) | How to run, build and test it; the conventions a change has to follow |
| [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) | What is verified, by what, and what is still unproven |
| [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) | The limits a visitor can meet, in plain language |

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
