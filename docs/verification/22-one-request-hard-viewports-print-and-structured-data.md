# 22 — One request per route, the viewports nobody measured, print, and structured data

Session `arena/37ec95ed-dhun`, 2026-10-08. Branch point and `main` at boot:
`c6414f4ac9c3bb8e12337ab04d7b46b61dcb5cb4` (PR #139 merge, recorded in
`docs/verification/21-marketing-site-performance-and-browser-evidence.md`).
Working PR: **#140**. Working tree clean at boot; the clone is **shallow**
(`git rev-list --count HEAD` → 1), so every historical claim below comes from the
GitHub API, not from local history.

Status vocabulary is the one `.ai/WEBSITE_PLAN.md` Part A fixes: **verified** =
read from a tool output in this session · **expected** = plausible but unchecked ·
**not verified** = explicitly unchecked. "It builds" is not "it renders": no
browser exists in this sandbox, so every browser-dependent statement below is
either a CI annotation quoted with its run id or marked not verified.

## What this session set out to do, and what it did

| # | Item | State |
|---|---|---|
| A1 | Every route is exactly one request (favicon inlined as a build-time `data:` URI) | **built, gated, mutation-proven** — the measured `requests=1` belongs to the next Lighthouse run |
| A2 | The Lighthouse reporter names the remaining subresources and dependency-tree items instead of counting them | **built + tested** |
| B1 | 280 px, landscape phone, 200 %-zoom viewport in the matrix | **built**; CI-measured, not verified locally |
| B2 | Rendered heading order, duplicate link text, `:focus-visible` on *every* tab stop | **built + rule-tested without a browser** |
| B3 | `forced-colors: active` and `prefers-contrast: more` emulated, each asserting the preference reaches the page | **built**; the CSS that answers it is static-gated |
| B4 | Print measured: caveats still rendered, printed contrast above the floors | **built**; the print ramp is asserted locally, the rendering is CI-measured |
| D | JSON-LD `SoftwareApplication`, theme-color per scheme, og:url = canonical, `og:image` refused with a reason | **built + gated + mutation-proven** |
| E | sitemap well-formedness, "a referenced file must exist", dist-vs-source CSS drift | **built + mutation-proven** |
| F | The site's "no telemetry, no crash reporting, no advertising SDK" claim checked against the app's dependency graph | **built + mutation-proven** |
| C | New content depth | **parked with a reason** — see below |

### Item C, parked deliberately

The backlog's content candidates were: how extraction breaks and what the daily
drill learns · what "no stable release" costs a user · how to build from source ·
what the app never sends. Three of the four are already on the site (the drill and
the extraction chain are in `/features/` §chain, the cost of no stable release is
in the opening caveat cards, and `/features/` states "no telemetry, no crash
reporting, no advertising SDK"). The fourth — "what the app never sends" — cannot
be written honestly from this tree today: `shared/src/commonMain/kotlin/dev/dhun/`
contains a component catalogue with `picsum.photos` demo URLs
(`design/catalog/ComponentCatalogScreen.kt`) and the InnerTube client sends a
`thirdPartyEmbedUrl = "https://www.reddit.com/"` field inside its request payload
(`innertube/InnerTubeClient.kt:612`). Neither is a runtime telemetry path, and
neither is something a visitor-facing page can state without a claim this session
could not support from evidence. **Under-claiming beats an unsupported claim**, so
the section was not written; instead the *existing* claim was made machine-checked
(item F), which is the half of that work that can be proven today.

## Local evidence (pasted from tool output, this session)

| Check | Result | Where |
|---|---|---|
| Build | `[11ty] Copied 1 Wrote 5 files in 0.13 seconds (v3.1.6)` then `minified: saved 49454 bytes` | `npm run build` in `website/`, Node v22.22.3 |
| Minification proof | `OK: every built file matches a fresh unminified build ignoring whitespace (49554 bytes saved by minification).` | `node tools/verify-minify.mjs` |
| Python suite | `Ran 232 tests in 0.672s` → `OK` (178 at boot) | `python3 -m unittest discover -s scripts -p 'test_*.py'` |
| Rule tests without a browser | `# pass 9` · `# fail 0` | `npm run test:rules` (`node --test tests/rules.test.mjs`) |
| Honesty contract | `OK: 4 built page(s) pass the honesty contract (8 forbidden-claim rules, 3 required caveats, 5 digest rules, 12 telemetry-SDK rules against the shipped dependency graph).` | `scripts/website_claims.py website/dist` |
| Quality gates | `OK: 26 quality checks pass on website/dist.` (18 at boot) | `scripts/website_quality.py website/dist` |
| Markup validity | no output (0 problems) | `npx html-validate "dist/**/*.html"`, pinned 11.16.2 |
| Dist drift | a fresh build leaves `git status --short -- website/dist` empty | local |
| Build files the telemetry rule reads | `app-android/build.gradle.kts`, `app-desktop/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `shared/build.gradle.kts`, `tools/playback-probe/build.gradle.kts` | `website_claims.build_files()` |

### Route weights and the ratchet (numbers read before the baseline was written)

| Route | Boot | Now | Δ | Headroom under 60 KB | Inlined CSS |
|---|---|---|---|---|---|
| `/` | 51,748 B | **53,554 B** | +1,806 | 7,886 B | 22,621 B (was 21,847) |
| `/features/` | 53,075 B | **54,881 B** | +1,806 | 6,559 B | 22,621 B |
| `/ui/` | 54,319 B | **56,125 B** | +1,806 | 5,315 B | 25,672 B (was 24,898) |
| `/404.html` | — | 17,441 B | — | 43,999 B | 11,896 B |

The +1,806 B per route is measured, not estimated: **+774 B is CSS** (the print and
forced-colours blocks — `cssInlined` 21,847 → 22,621), **+614 B is the inlined
icon** (the 638 B `data:` URI minus the 24-byte `/assets/dhun-favicon.svg` href it
replaced), and the remainder is the JSON-LD block. Client-side JavaScript is still
**0 bytes**, and the ratchet baseline was regenerated deliberately
(`python3 scripts/website_quality.py --write-baseline website/dist`) in the same
commit that records these numbers.

### Mutation proofs (each against a copy of the real build, restored green after)

```
separate icon file                exit=1 :: the icon is not an inlined data: URI … a second HTTP request on every visit
corrupted base64                  exit=1 :: the inlined icon has drifted from dhun-favicon.svg (384 B vs 459 B)
unreferenced built file           exit=1 :: assets/leftover.svg ships but no page references it
forced-colors block deleted (/ only)  exit=1 :: no @media (forced-colors: active) block ships in this page's own CSS
forced-colors block without .btn  exit=1 :: does not mention .btn
print block deleted (/ui/ only)   exit=1 :: no @media print block ships in this page's own CSS
print --text white on white --bg  exit=1 :: 1.00:1, below the 4.5:1 floor — the sheet would be unreadable
invented aggregateRating         exit=1 :: JSON-LD claims 'aggregateRating', which this repository cannot support
plain <script> tag                exit=1 :: inline <script> tag found: <script>
onclick attribute                 exit=1 :: inline event handler attribute found: onclick
light theme-color mutated         exit=1 :: missing the light-scheme theme-color (#F6F4F1)
og:url disagrees with canonical   exit=1 :: og:url should be https://99ggprooo00-code.github.io/DHUN/ (it is https://example.com/)
unclosed </urlset>                exit=1 :: sitemap.xml is not well-formed XML: no element found: line 7, column 0
<img src="/assets/nope.svg">      exit=1 :: src="/assets/nope.svg" names a file the build does not ship
dist CSS edited by hand           exit=1 :: the committed CSS is not the composition of tokens, base, components, mockups
source CSS appended, no rebuild   exit=1 :: same message on all four routes
report_lighthouse.py dropped from a filter  FAILED :: is used by website.yml but does not trigger it on both push and pull_request
restored (each time)              exit=0 :: OK: 26 quality checks pass
```

**Three of those mutations initially passed**, and the fixes are the interesting
part of this session: `forced_colors_violations` read the union of every page's CSS
(a per-page deletion was invisible), `print_style_violations` only checked that the
paper tokens were *mentioned* (white-on-white passed), and
`checkTabStops` passed pre-computed signatures into `focusChanged`, which computes
them itself — a rule that could never fire. All three are written up in
`.ai/DEBUG_LOG.md`; the print one is now a local 4.5:1 check on the print palette,
and the forced-colours one reads each route's own stylesheet.

## Publishing: the canonical URL does not serve this site yet

Read this session, from the GitHub API — this is the first time the repository
has recorded the *publishing* state with its full history rather than one line:

```text
GET repos/99ggprooo00-code/DHUN/pages
  {"build_type":"legacy","source":{"branch":"main","path":"/"},"https_enforced":true,"status":"errored"}

GET repos/99ggprooo00-code/DHUN/pages/builds?per_page=5
  building  2026-10-08T16:27:39Z  commit=c6414f4  dur=0ms
  errored   2026-10-08T14:58:05Z  commit=505c3d5  "Page build failed."
  built     2026-10-08T14:14:54Z  commit=ae44c7a  dur=101561ms
  built     2026-10-08T13:19:51Z  commit=2a20024  dur=135241ms
  built     2026-10-08T12:41:21Z  commit=fddc436  dur=63349ms

PUT repos/99ggprooo00-code/DHUN/pages -f build_type=workflow
  {"message":"Resource not accessible by integration","status":403}
```

Consequences, stated plainly:

- <https://99ggprooo00-code.github.io/DHUN/> serves Jekyll's rendering of the
  repository `README.md` — an engineering document — not `website/dist/`.
- The repository root has no `index.*`, no `_config.yml` and no `.nojekyll`, so
  there is nothing else the legacy build could serve at `/`.
- The switch is a human action: the integration token gets HTTP 403 on the Pages
  endpoint, and `gh auth status` shows a GitHub App token, not a PAT.
- Therefore this session's deliverable for that request is visibility and
  documentation, not a serving change: the `build` job reports `build_type`, the
  URL it actually serves, and the exact setting on **every** trigger (run summary
  + `::warning::`), `docs/runbooks/publishing-the-site.md` records the switch,
  the verification steps and the two rejected alternatives (a second copy of the
  site at the repository root; a `_config.yml`), and the README no longer
  presents that URL as the marketing site.
- Six tests in `scripts/test_website_workflow.py` pin it, including one that
  fails if the warning is ever turned into an error — the deploy job's
  `published` output and the `served` job's gate are re-asserted unchanged, so a
  skip stays a skip and nothing claims a publish that did not happen.
- **Still not verified:** any byte served from that origin. `served` runs
  `scripts/website_smoke.py` against the public URL only after a real publish, so
  it remains a skip, not a pass.

## CI evidence

Read from CI annotations this session (head `89834c0`, website run
**37814413312**; the first time any commit of this branch executed in a browser):

| Job | Result | What the annotations said |
|---|---|---|
| `build` | **success** | `Drift check: website/dist matches a fresh build of website/src.` |
| `lighthouse` | **success** | `/` 100/100/100/100 (samples 98·100·100) · `/features/` 100/100/100/100 · `/ui/` 100/100/100/100 — medians of three |
| `browser` | **failure** | one annotation: `Process completed with exit code 1`; no screenshots written |
| `deploy`, `served` | skipped | `build_type: legacy` (see the publishing section above) |

The one-request goal is now **measured, not architectural**: every route reports
`requests=1` with `subresources: http://127.0.0.1:8080/…/ (Document)` as the only
entry, `network-dependency-tree-insight` resolving to that same URL, and
`unused-css-rules: none`. Bytes 53.7 kB (`/`) / 55.1 kB (`/features/`) / 56.3 kB
(`/ui/`), FCP=LCP 1027/1001/1032 ms, TBT 0 ms, CLS 0.000. The reporter's new
naming is what makes this readable at all — the previous run said
`network-dependency-tree-insight: 3 item(s)` and nothing else.

**The `browser` failure is a defect this session shipped and CI caught**, and the
diagnosis is worth the space: `forcedColorsReport`, a function serialized into the
page by `page.evaluate`, called `forcedColorsBoundaryMissing` — imported from
`./rules.mjs`, therefore undefined *inside the page*. A `ReferenceError` in the
page rejected the evaluate, the script threw before `emitAnnotations()` ran, and
the readable channel carried nothing but the exit code. Two fixes: the page
function returns raw styles and Node applies the rule (the architecture this file
already claims), and every check now runs behind a guard that records a crash as
a failure and emits the annotations from a `finally` block. `scripts/test_website_workflow.py`
gained four tests, mutation-proved by reintroducing the exact bug, dropping a
check from the runner, removing the guard's recording, and moving
`emitAnnotations()` out of the `finally` — each red, then restored.

**Still not measured on this head:** the nine viewports, forced colours,
increased contrast, print and the tab walk. Their rules are proven without a
browser (`node --test`, 9 cases) and the crash that hid them is fixed; the run on
the fix commit is the first one that can report them. The PR comment carries what
that run said. What was on record
before this session's head existed (run `37808955045`, the merged head `c6414f4`,
quoted because it is what this session is measured against):

| Measurement | Merged head `c6414f4` | This head |
|---|---|---|
| Lighthouse, median of three | `/` 100/100/100/100 (samples 96·100·100) · `/features/` and `/ui/` 100/100/100/100 | **not verified** — pending the run on this head |
| Requests per route | **2** (`network-dependency-tree-insight: 3 item(s)` on every route) | **not verified** — expected 1 |
| Bytes | 52.6 / 54.0 / 55.2 kB | **not verified** |
| Browser job | success on `c6414f4`: axe 0 violations (6 scans), contrast ≥ 5.71:1 dark / 4.6:1 light, targets ≥ 44 px, skip link + Enter verified, 51 icons render | **failure on `89834c0`**, diagnosed and fixed here — a page function called a Node-scope import; the fix commit's run is the first that can carry these measurements |
| `pages-build-deployment` | **failure** (legacy Jekyll source, `status: errored`) | unchanged by this work; switching the Pages source remains a one-line repository setting the agent token cannot change (HTTP 403) |

## What is not verified

- **Everything browser-dependent on this head.** The nine viewports, the Tab
  walk, forced colours, increased contrast and print are built and their decision
  logic is mutation-proven without a browser; their *measurements* come from the
  `browser` job on the pushed head.
- **The single-request result.** The architecture makes the second request
  impossible (nothing else is referenced) and the static rules assert that; only
  Lighthouse's `requests` and the reporter's new `subresources:` line can confirm
  it, and those run in CI.
- **The served site.** `curl https://99ggprooo00-code.github.io/DHUN/` fails with
  `OpenSSL SSL_connect: SSL_ERROR_SYSCALL` from this sandbox — its egress allowlist
  is github.com/npm/pypi only — so the public URL was not fetched this session by
  any means.
- **Anything about another engine.** One Chromium, nine viewports; no Firefox, no
  WebKit, no screen reader, no real device, no Windows High Contrast session.
- **`prefers-contrast: more` has no rendering of its own** — the site declares no
  such rules; the check records that the preference reaches the page and measures
  the unchanged contrast, which is the honest version of that claim.

## Decisions taken (each with its reversal cost)

| # | Decision | Why | Reversal cost |
|---|---|---|---|
| D11 | Icon inlined as a base64 `data:` URI; no asset file ships | Removes the only remaining subresource; base64 measured 638 B vs 692 B percent-encoded vs 620 B for an invalid variant | ~10 min (restore passthrough copy + href) |
| D12 | 280 px, 844×390, 640×512@200 % added; `touch` is a per-viewport flag | The floors should follow the context, not the width; 200 % zoom is a layout-viewport fact | delete three lines |
| D13 | Browser rules split into a pure module, tested by `node --test` in the build job | The only way to mutation-prove checks that need a browser the environment does not have | high — the alternative is untestable rules |
| D14 | Print and forced-colours CSS added, gated per route | Paper was white-on-white and High Contrast hid the buttons' boundaries | delete two CSS blocks (rules turn red if unaccompanied) |
| D15 | One `SoftwareApplication` block; `og:image` refused | Ratings/offers/versions/download URLs are unsupportable claims; an SVG `og:image` is not rendered by crawlers and a PNG would be fabricated imagery | low |
| D16 | The telemetry claim is checked against the Gradle dependency graph | A claim about the app cannot be checked by reading the site's own words | none (added rule) |
| — | Content depth parked | The remaining candidate cannot be stated honestly from this tree (see above) | none |

## Commit trail (all pushed to `arena/37ec95ed-dhun`)

`dfd8539` recon · `6240fa6` one request + reporter naming · `8795c38` viewports,
High Contrast, print, rule tests · `e8f2701` structured data + metadata assertions
· `48f5cd9` sitemap/asset/drift rules · `4203d94` telemetry rule + docs + ratchet
baseline · `89834c0` the two dead focus helpers deleted (33 lines out, 0 in) ·
then the publishing report, its runbook and the `.md` sweep.
