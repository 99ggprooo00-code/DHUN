# DHUN Web

The browser client for DHUN, served from GitHub Pages:
**https://99ggprooo00-code.github.io/DHUN/**

Static-only by design — no bundler, no framework, no build step beyond
generating demo audio. It reads its visual language straight from the app's
own design tokens, so it looks like the Android and desktop clients rather
than a separate website.

```
web/
├── index.html                  app shell (markup only — icons come from JS)
├── assets/
│   ├── tokens.css              derived from DhunAppearance.kt / DhunTypography.kt
│   ├── dhun.css                component styles (consumes tokens.css only)
│   ├── dhun.js                 state, player, queue, bridge client
│   ├── icons.js                inline SVG icon set
│   └── demo/                   generated — NOT committed (see below)
├── tools/
│   ├── make-demo-audio.py      synthesises demo audio (stdlib only)
│   └── mock-bridge.mjs         dev-only bridge stand-in (no network needed)
└── tests/
    └── client.test.mjs         runs the real dhun.js under jsdom
```

## Why this client needs a bridge

The web client cannot talk to YouTube directly. Two independent reasons, both
already documented in this repo:

1. **The bytes are bound to a User-Agent.** `dev.dhun.core.StreamInfo` says it
   outright: *"googlevideo binds a signed stream URL to the identity that
   requested it: fetching the bytes with a different User-Agent is rejected
   (403 at open, or mid-stream)."* A browser's `<audio>` element can only ever
   send the browser's own agent, so it can never read those bytes itself.
   Something has to replay `StreamInfo.userAgent` — that is the bridge's
   `/audio/{videoId}` route.

2. **Extraction needs a runtime.** DHUN resolves playback through its own
   InnerTube chain (`dev.dhun.innertube.InnerTubeClient`: PO tokens, visitor
   ids, signature timestamps, alt player clients). GitHub Pages serves static
   files only; there is no server to perform those cross-origin POSTs.

So metadata and bytes both flow through a **DHUN Bridge**
(`tools/web-bridge`), which reuses `:shared` unchanged — the same
`YouTubeMusicProvider.forDesktop()` chain the desktop app uses under ADR-001.
No extraction logic is duplicated.

Without a bridge the client still runs in **demo mode** against locally
generated audio, so the player, queue, seek, shuffle and Media Session paths
are all exercisable offline.

## Running it locally

```bash
# 1. Generate demo audio (only needed for demo mode)
python3 web/tools/make-demo-audio.py

# 2. Serve the client
python3 -m http.server 8080 --bind 0.0.0.0 --directory web

# 3a. Optional: the real bridge (needs JDK 17 + network to YouTube)
./gradlew :tools:web-bridge:run --args="--port 8787"

# 3b. Or the mock bridge, for UI work with no network and no JDK
node web/tools/mock-bridge.mjs --port 8787
```

Open http://localhost:8080 and paste `http://localhost:8787` into the
**Bridge** field in the sidebar.

## Tests

```bash
npm install --no-save jsdom
python3 web/tools/make-demo-audio.py
node --test web/tests/client.test.mjs
```

The harness builds a jsdom document from the real `index.html`, stubs
`<audio>` (jsdom has no `HTMLMediaElement`), and imports the shipped
`assets/dhun.js`. It is not a re-implementation: assertions run against the
app's own state and DOM. Bridge-dependent cases talk to a live bridge over
HTTP and **skip** rather than fail when nothing is listening, so the suite
runs offline. CI runs it on every PR.

## Deploying to GitHub Pages

One admin-only step, then it is automatic on every push to `main` that
touches `web/`:

> **Settings → Pages → Build and deployment → Source: `GitHub Actions`**

`.github/workflows/deploy-pages.yml` then generates the demo audio, uploads
`web/` as the Pages artifact and deploys it. The repo's agent token has
`admin: false`, so that toggle has to be flipped by a human — everything else
is already wired.

All asset URLs in `index.html` are relative, so the `/DHUN/` path prefix
needs no base-href configuration.

## Bridge API

JSON, mirroring `dev.dhun.core` types. Every response carries CORS headers
(`--allowed-origin`, default `*`).

| Route                  | Returns                                  |
| ---------------------- | ---------------------------------------- |
| `GET /health`          | `{ok, engine, version, country}`         |
| `GET /search?q=`       | `SearchResults` (`songs`, …)             |
| `GET /suggestions?q=`  | `List<String>`                           |
| `GET /stream/{id}`     | `StreamInfo` (`audioUrl` → `/audio/{id}`)|
| `GET /audio/{id}`      | proxied bytes, `Range` supported         |
| `GET /lyrics/{id}`     | `Lyrics` with an explicit `kind`         |

`/stream` deliberately reports `audioUrl` as the bridge's own `/audio/{id}`
rather than the googlevideo URL — handing the raw URL to the browser would
hit the User-Agent binding in reason 1 above.

## Legal

Unchanged from the rest of DHUN: GPL-3.0 (see `../LICENSE`), streaming via
YouTube Music with the repo's own extraction chain. The bridge performs the
same extraction the desktop app does — it adds no new third-party dependency,
so `../THIRD_PARTY.md` is unaffected.
