# `app-web/` — DHUN in a browser

The application's interface, mirrored in a browser. **Engineering preview:
audio playback is not proven from this origin** (ADR-008 boundary 4), and the
page says so on screen.

## Run it

```bash
node tools/serve.mjs 4173     # http://0.0.0.0:4173/
npm test                      # 91 tests, no install, no network
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

## What it does not do

- **No audio.** No stream is reachable from a browser origin, so the transport
  advances a labelled clock — the seek bar, queue, lyrics sync and equaliser all
  respond, nothing is audible, and a persistent notice says so. Adding a proxy
  would breach ADR-008 and was not done.
- **No live catalogue.** The metadata on screen is a fictional sample
  (`src/js/data/sample-catalog.js`), labelled as such. The anonymous InnerTube
  `WEB_REMIX` request is attempted first and reports its own failure.
- **No offline downloads.** A browser has no equivalent of ADR-006's
  device-side store; the Downloads tab renders the app's two sections empty with
  the reason.
- **No verification of layout or paint.** No browser exists in this
  environment; `tests/boot.test.mjs` drives the app against a DOM stub, which
  proves wiring, not pixels.
- **Deployed, at `/app/`, under the same origin as the marketing site** —
  <https://99ggprooo00-code.github.io/DHUN/app/> — by the site workflow, which
  stays the single owner of the Pages artifact (ADR-008 amendment,
  2026-10-09 (2); `docs/verification/29-web-app-mirror.md`). It is `noindex`
  and out of the sitemap: a preview linked from the site's call to action, not
  a product surface.

## Layout

```
src/index.html            one page: CSP, noindex, two stylesheets, one module
src/css/tokens.css        the design system, mirrored
src/css/app.css           chrome and screens — tokens only
src/js/                   pure modules; main.js is the only file that touches the DOM
src/js/data/              sample catalogue and sample LRC (both labelled)
tests/                    node --test: nav, equalizer, lyrics, format, escaping, tokens, player, boot
tools/                    serve, build, gen-icons
```

## Keyboard

`Space` play/pause · `Shift+→` next · `Shift+←` previous · `/` search ·
`Esc` close the player, page or sheet, in that order.
