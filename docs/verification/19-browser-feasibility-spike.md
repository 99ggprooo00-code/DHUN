# 19 — ADR-008 B1 browser feasibility spike

> **Superseded status note (2026-10-10):** the canonical `/DHUN/web-spike/` URL below returned 404 when checked. The result recorded here is historical.

> **Status: DEPLOYED AND RUN — RESULT «BLOCKED» FOR THE AVAILABLE
> BRAVE/CHROMIUM-FAMILY RUN. CROSS-BROWSER COVERAGE UNAVAILABLE. NO WEB-SUPPORT
> CLAIM PERMITTED. B1 STOPS HERE; B2 REQUIRES A SEPARATE USER DECISION.**
>
> PR #136 merged as `2a20024d4b626b3f40ae95776cefb6b1e49cfdca`, so legacy Pages
> now serves the probe from the canonical origin
> `https://99ggprooo00-code.github.io/DHUN/web-spike/`. The user ran the
> canonical test in Brave `1.96.61` (Chromium `154.0.8037.98`, Official Build,
> 64-bit). Anonymous metadata passed; the player request was blocked before a
> readable response, so the direct-URL, byte-range, codec/media, `playing`-event
> and audible-playback stages were **not reached**. No sanitized JSON,
> screenshot or DevTools network trace was supplied, so the failure **must not**
> be narrowed to a specific HTTP response, a preflight rejection,
> extension/Shield behavior or a network policy. Firefox and Safari were
> unavailable — that is not an inferred failure.

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

Legacy Pages publishes `main:/`, so once the files reached `main` (PR #136),
Jekyll copied this plain static directory to `/DHUN/web-spike/` without any
Pages-settings change and without replacing the rendered README front page. No
Pages workflow, source-set change or site scaffold was added.

## Request contract

The probe runs only after a button click and uses a caller-supplied 11-character
video ID. It makes these fixed, credential-omitting requests:

1. `GET https://www.youtube.com/oembed` for minimum anonymous metadata (a
   non-empty title and author must exist, but their values are immediately
   discarded);
2. one `POST https://music.youtube.com/youtubei/v1/player?prettyPrint=false`
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

## Post-merge deployment provenance

| Item | Evidence |
|---|---|
| Merge | PR #136 merged with explicit user authorization at **2026-10-08T13:19:49Z** as **`2a20024d4b626b3f40ae95776cefb6b1e49cfdca`** |
| `main` tip | `2a20024` (merge commit; verified as an ancestor of `origin/main` from this checkout) |
| CI | run **37783500689** — success |
| Build APK | run **37783500620** — success |
| test-release | run **37783500838** — success (`aab` and `release_draft` skipped on a push, as designed) |
| Pages | run **37783499138** — success; build `built` with no error at commit `2a20024` |
| Pages API | `build_type=legacy`, `source=main:/`, `status=built`, https enforced |
| Pages artifact | artifact **11552239018** exists (1,171,094 bytes). Its archive redirects to a blob host that was unavailable in the previous session, so the archive was **never inspected** — it is not browser evidence and must not be cited as such |
| Canonical probe | `https://99ggprooo00-code.github.io/DHUN/web-spike/` served the probe page (`b1-v1`). (Superseded 2026-10-10: this URL returned 404 when checked. Pages moved to workflow deploy on 2026-10-08, and the probe is not in the website workflow's artifact.) |

## Canonical B1 run — recorded result (2026-10-08)

User-confirmed manual run from the canonical origin. Browser: **Brave 1.96.61**,
based on Chromium **154.0.8037.98**, Official Build, 64-bit (Chromium family —
**not** a stock Chrome/Chromium run).

| Probe card | Result | What the user reported |
|---|---|---|
| Page | PASS | The probe UI rendered at the canonical URL |
| 01 Anonymous metadata | PASS | Non-empty title/author metadata returned; the values were discarded |
| 02 Player response | FAIL | The browser/CORS layer blocked the player request before a readable response |
| 03 CORS + byte range | not reached | Blocked behind card 02 |
| 04 Browser media | not reached | Blocked behind card 02 |
| Direct-URL candidate / `playing` event / audible playback | not reached | No playback stage was reached; no "I heard audio" confirmation exists |

Overall UI verdict as reported by the user: **Player path blocked.**

### What this evidence does and does not establish

- It establishes that the deployed probe loaded and that anonymous metadata
  could be read from the canonical origin in this one browser session.
- It does **not** identify the failing HTTP response, because the request failed
  before a readable response. Do not narrow it to an HTTP status, an `OPTIONS`
  preflight rejection, an extension/Shield cause, or a network/ISP/VPN policy;
  none of those was observed or supplied.
- No sanitized JSON, screenshot or DevTools network trace was supplied. The
  report therefore stays at card granularity and is **unreproducible in
  CI/local environments**.
- Brave is Chromium-family; Firefox and Safari were **unavailable**, not
  failed. Cross-browser coverage is therefore **unavailable**, and no
  `B1 PASS` (which requires audible playback in current Chromium **and**
  Firefox) is possible.
- Honest outcome: **B1 BLOCKED for the available Brave/Chromium-family run**,
  with cross-browser coverage unavailable.

## Evidence ledger — closed for this B1 attempt

| Field | Brave (Chromium family) | Firefox | Safari |
|---|---|---|---|
| Browser/version | ✅ `1.96.61` / Chromium `154.0.8037.98` Official 64-bit | ⛔ unavailable | ⛔ unavailable |
| Origin exactly canonical | ✅ `https://99ggprooo00-code.github.io` (`b1-v1` served) | ⛔ unavailable | ⛔ unavailable |
| Deployed commit / Pages build | ✅ `2a20024`, build `built`, no error, run 37783499138 | ⛔ unavailable | ⛔ unavailable |
| Anonymous metadata | ✅ pass (values discarded) | ⛔ unavailable | ⛔ unavailable |
| Readable player response | ❌ **fail — blocked before a readable response** | ⛔ unavailable | ⛔ unavailable |
| Direct URL candidate | ➖ not reached | ⛔ unavailable | ⛔ unavailable |
| CORS byte range | ➖ not reached | ⛔ unavailable | ⛔ unavailable |
| Media `playing` event | ➖ not reached | ⛔ unavailable | ⛔ unavailable |
| Tester heard audio | ➖ not reached | ⛔ unavailable | ⛔ unavailable |
| Sanitized JSON attached/reviewed | ❌ not supplied | ⛔ unavailable | ⛔ unavailable |

`✅` verified · `❌` verified failure · `➖` not reached · `⛔` browser
unavailable (not a failure)

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

## Recorded disposition (2026-10-08)

This run is classified **B1 BLOCKED**, closest to the blocked branch: the
player request never produced a readable response, so no downstream stage could
be measured. The disposition is intentionally narrow:

- B1 **stops here**. No further B1 probing is authorized by this record.
- Do **not** add a proxy or backend; that requires its own ADR.
- Do **not** change extraction or production client profiles to make the probe
  pass.
- Do **not** adopt Kotlin/JS, Wasm, TypeScript or any production browser stack
  on the strength of this record.
- Do **not** implement the static Option-A fallback as if it had been selected.
- Any continuation is a **separate ADR-008 B2 user decision**. A B2 decision may
  also be “stop”.
- No Web-support claim is permitted anywhere (README/site/CHANGELOG), because
  no browser completed the playback path.
