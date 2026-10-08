# 19 — ADR-008 B1 browser feasibility spike

> **Status: IMPLEMENTED FOR REVIEW, NOT DEPLOYED TO THE CANONICAL ORIGIN, NO
> PLAYBACK VERDICT.** The static probe candidate is `web-spike/`. A local/Arena
> preview is useful only for UI and early CORS behavior; ADR-008 requires the
> final run from `https://99ggprooo00-code.github.io/DHUN/web-spike/` after the
> containing commit reaches the Pages source. Do not report Web support from
> this document.

## Scope

Accepted ADR-008 authorizes one question before any browser stack is selected:
can a credential-free browser page on DHUN's actual GitHub Pages origin obtain
minimum anonymous metadata, receive one direct media candidate, read a byte
range, and produce audible media?

The candidate is intentionally not a product:

- three dependency-free static files (`index.html`, `styles.css`, `probe.js`);
- no Kotlin/JS, Wasm, TypeScript, framework, package manager, source-set change,
  backend, proxy, service worker or Pages workflow;
- no sign-in, cookies, credentials, PO tokens, BotGuard or storage;
- no remote fonts, images, lyrics, analytics or telemetry;
- no search, library, queue or product navigation;
- `noindex, nofollow`, a strict CSP and a visible “not a Web player” boundary.

Legacy Pages already publishes `main:/`. Once the files are on `main`, Jekyll
can copy this plain static directory to `/DHUN/web-spike/` without changing
Pages settings or replacing the rendered README front page.

## Request contract

The probe runs only after a button click and uses a caller-supplied 11-character
video ID. It makes these fixed, credential-omitting requests:

1. `GET https://www.youtube.com/oembed` for minimum anonymous metadata (a
   non-empty title and author must exist, but their values are immediately
   discarded);2. one `POST https://music.youtube.com/youtubei/v1/player?prettyPrint=false`
   using the current repository's WEB_REMIX fallback identity (`67`,
   `1.20250310.01.00`);
3. only if the response contains a direct HTTPS `*.googlevideo.com` candidate,
   a 32 KiB `Range` fetch and a user-clicked `<audio crossorigin="anonymous">`
   load.

The browser may add protocol-required `OPTIONS` requests for CORS preflight;
those are expected on the same allow-listed origins, not extra application
endpoints. Redirects are left to the browser. The range row records the initial
and final hosts plus `response.redirected` without retaining either URL or its
query, and CSP prevents a redirect from silently reaching an unlisted host.

The page does not export response bodies, titles, author names, stream URLs or
query strings. Its JSON contains stage verdicts, endpoint paths, HTTP status
when CORS exposes it, format counts, selected MIME/bitrate/host, redirect state,
media events, origin, browser user-agent and an explicit user confirmation of
audible audio. The stream URL remains in memory and is discarded on reset/reload.

A browser `TypeError: Failed to fetch` is useful evidence: it means script on
that origin could not read the cross-origin response. An HTTP/playability
response is a different result and must not be collapsed into CORS failure.
Likewise, a byte-fetch CORS failure does not automatically prove `<audio>` will
fail, so the page keeps those rows separate.

## Test content and rights

The default ID `aqz-KE-bpKQ` points to Blender Foundation's *Big Buck Bunny*.
The film is published under Creative Commons Attribution 3.0; project source:
<https://peach.blender.org/about/>. The probe copies no film, thumbnail, title
response or artwork into this repository. It presents synthetic DHUN-owned UI,
identifies the work for attribution, and asks the upstream host for ephemeral
test playback. A tester may replace the ID only with content they are authorized
to test.

## Automated checks

`scripts/test_web_spike.py` pins the B1 boundary:

- only three self-contained static files and relative local assets;
- CSP/noindex/no-referrer and no image/iframe/autoplay;
- fixed YouTube endpoints with `credentials: "omit"`;
- no persistence, cookies, WebSocket, beacon or analytics API;
- allow-listed googlevideo hosts and sanitized JSON;
- accepted ADR text remains explicitly B1-only.

Local executable gates:

```text
python3 -m unittest discover -s scripts -p 'test_*.py'  # 55 tests
node --check web-spike/probe.js
```

These prove static contract/syntax only. They do not prove CORS, upstream
playability, media bytes, codecs or sound.

## Manual evidence procedure

### Preflight — non-accepting origin

1. Serve `web-spike/` without modifying files.
2. Confirm the page labels itself as an engineering probe and shows the actual
   preview origin.
3. Run the default item, then click **Play resolved stream** if enabled.
4. If sound is audible, click **I heard audio**.
5. Copy the sanitized JSON and record it as **preflight only**.

A preflight result must not fill the canonical-origin row below.

### Canonical B1 run — required

1. Verify Pages API still reports `legacy`, source `main:/`, and `built` for the
   exact deployed commit.
2. Open `https://99ggprooo00-code.github.io/DHUN/web-spike/` in a normal window.
3. Run the default item. Do not sign in or relax browser privacy settings.
4. If enabled, click **Play resolved stream**. Click **I heard audio** only when
   sound is actually audible.
5. Copy the sanitized JSON. In DevTools, confirm there are no requests outside
   the page origin, `www.youtube.com`, `music.youtube.com`, and
   `*.googlevideo.com`.
6. Repeat in current Chromium and Firefox. Record Safari as tested or explicitly
   unavailable/unsupported; do not infer it.

## Evidence ledger — intentionally open

| Field | Chromium | Firefox | Safari |
|---|---|---|---|
| Browser/version | ⏳ | ⏳ | ⏳ tested / unavailable |
| Origin exactly canonical | ⏳ | ⏳ | ⏳ |
| Deployed commit / Pages build | ⏳ | ⏳ | ⏳ |
| Anonymous metadata | ⏳ | ⏳ | ⏳ |
| Readable player response | ⏳ | ⏳ | ⏳ |
| Direct URL candidate | ⏳ | ⏳ | ⏳ |
| CORS byte range | ⏳ | ⏳ | ⏳ |
| Media `playing` event | ⏳ | ⏳ | ⏳ |
| Tester heard audio | ⏳ | ⏳ | ⏳ |
| Sanitized JSON attached/reviewed | ⏳ | ⏳ | ⏳ |

## Decision rule

- **B1 PASS** requires audible tester confirmation from the canonical origin in
  current Chromium and Firefox, with no credential/proxy/allow-list violation.
- **B1 PARTIAL** means metadata/player/direct URL is reachable but range/media
  differs by browser. Stop and take the exact evidence to B2; do not call it Web
  support.
- **B1 BLOCKED** means browser policy or upstream response prevents the direct
  path. This is a valid result. Do not alter extraction or add a proxy. B2 may
  select “stop / static Option A”; a backend remains a separate ADR.

Any result stops at B2. It cannot close Android/Windows S3 or S6.
