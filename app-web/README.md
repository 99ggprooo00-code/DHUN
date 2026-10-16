# `app-web/` — the DHUN web app

DHUN as a web app: the same shell, design tokens, navigation, shortcuts,
equaliser, synced-lyrics parser and legal pages as the Android and Windows
builds, running in a browser with no server of its own. It installs from the
page (manifest + service worker), opens offline once loaded, and keeps your
library in browser storage.

**What a browser cannot be promised.** Playback depends on YouTube's origin
answering a cross-origin request from a static page, which no author controls
(ADR-008 boundary 4). The app tries, and when it is refused it says so on screen
— with the labelled demo catalogue offered as a last resort rather than passed
off as a real result.

## Run it

```bash
node tools/serve.mjs 4173     # http://0.0.0.0:4173/
npm test                      # the whole suite, no install, no network
node tools/build.mjs          # copies src/ → dist/ and checks the icons
npm run gen:icons             # regenerate js/icons.js from DhunIcons.kt
```

There is nothing to install. The module has **no dependencies** — not even
development ones: the server is `node:http`, the tests are `node --test`, and
the build is a copy plus a generated-file check.

## What it mirrors

Every value and every behaviour traces to a file in `shared/`, and the trace is
enforced by tests rather than by convention:

| Web | Source |
|---|---|
| `src/css/tokens.css` | `design/DhunAppearance.kt`, `DhunTypography.kt`, `DhunShapes.kt`, `DhunSpacing.kt`, `DhunAnimations.kt` |
| `src/js/icons.js` | `design/DhunIcons.kt` — generated, `--check` in CI |
| `src/js/nav.js` | `ui/shell/AppNavState.kt`, `DhunShellLayout.kt` |
| `src/js/views.js` | `ui/home`, `ui/search`, `ui/library`, `ui/settings`, `ui/browse` |
| `src/js/player-ui.js` | `ui/player/{MiniPlayer,FullPlayer,TransportControls,PlayerSeekBar}.kt` |
| `src/js/player.js` | `player/QueueManager.kt`, `DhunPlayer` |
| `src/js/equalizer.js` | `player/equalizer/{EqualizerBands,EqualizerPreset,EqualizerSession}.kt` |
| `src/js/lyrics.js` | `lyrics/LrcParser.kt` |

`scripts/test_app_web.py` (part of app CI step 1, which has no Node) asserts
the same rules as `tests/tokens.test.mjs`: every colour traces to the Kotlin
source, no raw hex or px outside `tokens.css`, no external asset, `noindex`,
a strict CSP, and the honesty notices.

## What it does, and what it does not

- **Real streams, with the failures named.** Playback resolves through YouTube
  Music's anonymous `player` endpoint, or through an endpoint you configure in
  Settings; there is no DHUN proxy and no DHUN server (ADR-008). A refusal — CORS,
  an HTTP error, a dead stream — becomes a state on screen, and the row that could
  not resolve keeps its place with its reason.
- **A labelled demo catalogue, last.** When nothing real resolves, the app says
  so and offers labelled fictional rows so the interface can still be used. Demo
  rows are never presented as search results, and they never produce audio.
- **No downloads.** ADR-006's device-side store has no browser equivalent, so the
  Downloads tab is gone rather than stubbed; the Library is playlists, likes and
  history, all in browser storage.
- **Offline is the shell, not the music.** The service worker caches the app's own
  files (every module, in the shell list `tools/build.mjs` checks) so it opens
  without a network. It does not cache audio.
- **No pixels proof in CI.** No browser binary exists in the build environment, so
  the suites drive the app against a DOM stub and assert rendered *output* as
  strings; live layout checks are a human step.

## Where it is deployed

`/app/` on the project's Pages site, published by the site workflow, which stays
the single owner of the Pages artifact (ADR-008 amendment, 2026-10-09 (2);
`docs/verification/29-web-app-mirror.md`). It is the destination of the site's
"Open the app" button and the docked bar on every page.

## Keyboard

`Space` play/pause · `Shift+→` next · `Shift+←` previous · `/` search ·
`Esc` close the player, page or sheet, in that order.
