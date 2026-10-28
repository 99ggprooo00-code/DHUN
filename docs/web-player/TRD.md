# Technical requirements — DHUN Web (`/app/`)

The web counterpart of [`docs/TRD.md`](../TRD.md). It states the technical
constraints the browser build is built and checked against. The module-by-module
description is in [ARCHITECTURE.md](ARCHITECTURE.md); this document is the list
of the constraints themselves and where each one is enforced.

## 1. Platform

| Item | Requirement | Where it is enforced |
|---|---|---|
| Delivery | Static files only: one HTML document, two stylesheets, ES modules, a manifest and a service worker. | `app-web/tools/build.mjs` copies `app-web/src/` to `app-web/dist/`. |
| Runtime dependencies | None. `app-web/package.json` declares no `dependencies`. | `app-web/package.json`; `scripts/test_app_web.py::ShipsNoThirdPartyRuntimeAsset`. |
| Script loading | ES modules, loaded with `type="module"`. No inline script. | `script-src 'self'` in the CSP. |
| Browsers | Modern evergreen browsers are the target. | Only Chromium is measured in CI (see [TESTING.md](TESTING.md)). Firefox, Safari and Edge are not measured by this repository. |
| Hosting | GitHub Pages at `/DHUN/app/`, and a local `/app/` for tests. | `.github/workflows/website.yml`. |
| Build step | Node.js for the copy and checks; no install is needed for the build tool itself. | `app-web/tools/build.mjs`. |

## 2. Content Security Policy

The page carries a `<meta http-equiv="Content-Security-Policy">` in
`app-web/src/index.html`. Its directives, as shipped:

```
default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline';
img-src 'self' data: https://*.saavncdn.com;
media-src https://*.saavncdn.com blob:;
connect-src https://saavn.dev https://saavn.sumit.co https://saavnx.vercel.app https://*.saavncdn.com;
manifest-src 'self'; worker-src 'self'; base-uri 'none'; form-action 'none';
```

Each widening is explained next to the meta tag in the HTML. A change to any
origin list is a change to the security posture and is recorded in
[DECISIONS-AND-CHANGES.md](DECISIONS-AND-CHANGES.md).

`'unsafe-inline'` is allowed for styles only. The reason, as the HTML comment
gives it: the app writes an inline `background-image` on a hero's blurred
backdrop, and no user-controlled CSS path exists. Scripts are not allowed inline.

## 3. Module and code layout

- Entry: `app-web/src/js/main.js`. It runs the framing refusal before storage is
  touched or any node is drawn.
- Pure modules (no DOM, no `fetch`) hold the logic that is tested directly:
  router, library, player rules, transfer, format, shortcuts, legal text.
- Impure modules (DOM, `fetch`, `localStorage`, `navigator`) are kept at the edge.
- The catalogue is reached only through `app-web/src/js/api.js`. The player is
  handed a `resolve` function and never imports the network itself.

The number of JavaScript modules and their names are checked against
`ARCHITECTURE.md` by `scripts/web_player_docs.py`.

## 4. Network

| Item | Value | Source |
|---|---|---|
| Catalogue sources, in preference order | `saavn.dev`, `saavn.sumit.co`, `saavnx.vercel.app` | `API_SOURCES` in `api.js` |
| Request timeout | 15,000 ms | `REQUEST_TIMEOUT_MS` |
| Source probe timeout | 8,000 ms | `PROBE_TIMEOUT_MS` |
| Page size | 20 | `PAGE_SIZE` |
| Concurrency | While a failover is running, other requests wait for it rather than starting their own race. | `createRacer` in `api.js` |
| Credentials | None. No cookies or authorisation header are sent to the catalogue. | `api.js` request construction |

Endpoint paths are listed in [PROVIDER-INTEGRATION.md](PROVIDER-INTEGRATION.md).

## 5. Storage

- `localStorage` under one key, `dhun.web.v3`. Schema and limits are in
  [DATA-SCHEMA.md](DATA-SCHEMA.md).
- No `IndexedDB`, no `Cache Storage` for user data. The only cache is the
  service worker's shell cache (`dhun-web-v3.0.2`), which holds application
  files, not audio and not catalogue responses.

## 6. Service worker

- Registered by `pwa.js`. Shell list in `app-web/src/sw.js` (`SHELL`).
- Navigations are network-first with a cached fallback.
- Other same-origin `GET` requests inside the scope are cache-first.
- Everything else (audio, artwork, catalogue) goes straight to the network and is
  not intercepted.
- A new version is announced with a notice to close the tab and reopen DHUN. It
  takes over when no DHUN tab is open. There is deliberately no reload-now control:
  a reload would stop playback (see [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) §2).
- Old caches named `dhun-web-*` are removed on activation.

The shell list is checked against the real build by `app-web/tools/build.mjs`.

## 7. Quality constraints

- Honesty contract on the fresh build (the honesty step in
  `.github/workflows/website.yml`), and `scripts/website_claims.py` over the copy.
- HTML validation of the published pages (html-validate, pinned).
- Minification must change no behaviour (`website/tools/verify-minify.mjs`).
- A committed build must match a fresh build (drift check).
- Browser checks: viewports, keyboard, contrast, reduced motion, forced colours,
  and axe. See [TESTING.md](TESTING.md).

## 8. Accessibility

- Keyboard reachable controls; shortcuts never fire from a text field.
- Reduced-motion and forced-colours media queries are present in the stylesheet.
- Live regions (`aria-live`) in the shell and components announce status and
  errors. The browser checks in [TESTING.md](TESTING.md) measure contrast, keyboard
  and axe results; no manual screen-reader audit is recorded in this repository.

## 9. Not required

- Server-side rendering, a backend, a database, a message queue, a CDN purge
  step, or any credential that must be kept secret.
