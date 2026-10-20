# `app-web/` — the DHUN web app

DHUN in a browser tab: the same shell as the Android and Windows builds — the
glass app bar, the three destinations, the docked mini player, the full player,
the sheets — running as a static site with **no DHUN server, no account and no
build-time dependency**. Your likes, playlists, history and settings live in
this browser's storage.

The catalogue is **JioSaavn**, read through community deployments of the same
open-source wrapper: `saavn.dev`, `saavn.sumit.co` and `saavnx.vercel.app`, tried
in that order (ADR-008: DHUN operates no proxy and no backend for any of them).
Each is a free community deployment with no support contract, so one hostname is
not a source — the app remembers the instance that answered and moves on when it
stops. When none answers, the interface names every host and what each said, and
falls back to a **labelled sample catalogue** whose rows carry no audio — never
to a pretend result, a simulated queue or invented progress.

## Run it

```bash
node tools/serve.mjs 4173     # http://0.0.0.0:4173/ (serves src/)
npm test                      # 155 tests; no install, no network
node tools/build.mjs          # src/ → dist/, then checks every shell entry
npm run gen:icons             # regenerate js/icons.js from DhunIcons.kt
```

The module has **no dependencies** — not even development ones. The server is
`node:http`, the tests are `node --test`, and the build is a copy plus the
checks that every module is reachable and every file the service worker names
exists.

## The modules

| File | What it is |
|---|---|
| `js/main.js` | Boot, state, the action table (one delegated listener), the render pass |
| `js/router.js` | Hash routes as data: parse, format, tab, title, the detail set |
| `js/api.js` | The JioSaavn seam: timeouts, failure kinds, normalisers, the shelves |
| `js/demo.js` | The labelled sample catalogue — fictional, silent, clearly marked |
| `js/library.js` | Likes, playlists, history, settings, storage (`dhun.web.v3`) |
| `js/player.js` | Queue, repeat, shuffle, stream resolution, media session |
| `js/player-ui.js` | The full player: transport, scrubber, queue and lyrics tabs |
| `js/shell.js` | App bar, bottom navigation, navigation rail, mini player |
| `js/views.js` | Home, Search, Library, album, artist, playlist, collection, Settings, About |
| `js/components.js` | Cards, rows, rails, states, sheets, notices, the source badge |
| `js/legal.js`, `js/markdown.js`, `js/data/web-legal.js` | This build's own legal pages |
| `js/transfer.js` | Export and import of the library as a file |
| `js/pwa.js` | Service-worker registration, install prompt, update state |
| `js/shortcuts.js` | The keyboard map the dispatcher and the help sheet share |
| `js/dom.js` | `html` (escaping template), markup marking, focus helpers |

`src/css/tokens.css` mirrors `design/DhunAppearance.kt`, `DhunTypography.kt`,
`DhunShapes.kt`, `DhunSpacing.kt` and `DhunAnimations.kt`; `src/js/icons.js` is
generated from `design/DhunIcons.kt` and checked against it, not hand-edited.
`js/data/legal-content.js` is the shared legal corpus, generated from
`legal/*.md`; the web build renders its *own* pages from `data/web-legal.js`,
because the installed app's documents describe downloads and a SQLite store
that a browser tab cannot honour.

## What it does, and what it does not

- **A catalogue that survives one host going down.** `api.js` keeps an ordered
  list (`API_SOURCES`); the remembered instance is tried first and alone, the
  rest are raced only if it fails, and the winner is remembered. A failure
  carries every host's answer, so "the catalogue could not be reached" says which
  host said what.
- **Real streams, or a named failure.** A track plays if JioSaavn returns a
  stream URL. A blocked request, a timeout, an HTTP error or a row with no URL
  becomes a state on screen — with the URL or status the failure carried — and
  the row keeps its place. Nothing is retried behind the visitor's back, and
  nothing pretends to buffer.
- **A labelled sample catalogue, last.** With no upstream the app still draws a
  usable interface, says which shelves are examples, and gives every sample row
  `demo: true` with an empty stream. Pressing play on one says why it is silent.
- **Lyrics are not invented.** The build has no lyric service and no parser. The
  player's Lyrics tab states that the words are not available here rather than
  showing an empty pane or a fake line.
- **No equaliser.** Its bands are a platform audio binding; routing the
  catalogue's cross-origin audio through a browser Web Audio graph can fail
  silently and mute the product. The control is absent, not stubbed.
- **No downloads.** ADR-006's device-side store has no browser equivalent. The
  Library is playlists, liked songs and history, all in this browser.
- **Offline is the shell, not the music.** The service worker caches the app's
  own files (`tools/build.mjs` checks that the shell list is complete) so the app
  and your library open without a network. Streams are cross-origin and signed;
  they are neither cached nor offered as a download.
- **This browser is the account.** Everything is local: no login, no sync, no
  server copy. Settings can export the library to a file, import it back, and
  erase it. A private window that cannot store anything is told exactly that.
- **Nothing is framed.** `frame-ancestors` cannot be sent by a meta tag, so
  `boot()` refuses to start inside a frame and says why.

## Gates

- `npm test` — 155 assertions over the pure modules: routing round trips,
  escaping, the queue and repeat/shuffle rules, stream resolution, the library's
  return shapes, API normalisation, transfer files, every screen's rendered
  output, CSS class coverage, and the keyboard map against the dispatcher.
- `scripts/test_app_web.py` — the no-Node half, run in app CI: regions exist in
  the HTML before the module runs, no raw hex or px outside `tokens.css`, no
  third-party asset or analytics, `noindex`, no invented lyrics, the sample
  catalogue is labelled, the service-worker shell is complete.
- `website/tests/browser.mjs` — the real Chromium run against the deployed
  `/app/`: boot, source badge, one `h1` per screen, dialog focus and Tab traps,
  rendered contrast in both themes, axe with zero violations, the local
  playlist round trip across a reload, the player deep link, and the theme
  control switching the *document*, not just the chrome.

## Where it is deployed

`/app/` on the project's Pages site, published by the site workflow, which stays
the single owner of the Pages artifact (ADR-008 amendment, 2026-10-09 (2);
`docs/verification/29-web-app-mirror.md`). It is the destination of the site's
"Open the app" button and the docked bar on every page.

## Keyboard

`Space` play/pause · `N` / `P` next / previous · `→` / `←` seek five seconds ·
`↑` / `↓` volume · `M` mute · `S` shuffle · `R` repeat · `/` search · `?` this
list · `Esc` closes a sheet, then the player. Keys are never taken from a text
field and never from a combination the browser owns.
