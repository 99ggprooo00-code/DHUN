# DHUN website — build plan (Option A marketing site)

> **Part A below is the plan of record for the `website/` workstream and
> supersedes the Option-B framing of the material preserved as Part B.**
> Part B (the 2026-10-08 `arena/ae65f1a5-dhun` research) is retained because
> its comparative research, licence snapshot and Pages evidence are still
> accurate and are cited by this plan; it is *not* the current decision.

Status vocabulary used throughout: **verified** = I read the fact from a tool
output in this session (git, `gh`, a build, a local command, a fetched URL);
**expected** = plausible but unchecked; **not verified** = explicitly unchecked.

---

## 1. Scope — Option A marketing site (decided, not re-opened)

The deliverable is a **static marketing/download site** for the applications
that already exist: a new top-level `website/` directory, its own workflow,
and nothing else.

**Forbidden by decision D1:** anything resembling a web player — no
`app.`-style property, no PWA, no browser playback, no Kotlin/JS, no
TypeScript application code, no proxy/backend. `.ai/MASTER_PROMPT.md` line 46
("Web is deferred (v2 candidate, likely 'no')") and the "Explicitly NOT in
S1–S6" list both keep production Web out of scope; accepted
`docs/decisions/ADR-008-browser-web-player-target.md` authorized only the B1
deployed-origin feasibility spike, which is closed as **BLOCKED**. Building a
player would need a new ADR, so instead of building one this session records
the marketing-site scope as **PROPOSED ADR-009** (`docs/decisions/ADR-009-marketing-site.md`,
status **PROPOSED** — not implemented as a product target, and it does not
widen ADR-008).

**Isolation (D2).** The site lives in `website/`; it is built by
`.github/workflows/website.yml`, which I add. The four existing workflows
(`ci.yml`, `build-apk.yml`, `test-release.yml`, `extraction-health.yml`),
Gradle files, `shared/`, `app-android/`, `app-desktop/`, `settings.gradle.kts`
and `build.gradle.kts` are **not modified**. The only edits outside
`website/`, `.ai/`, `docs/`, `README.md` and `THIRD_PARTY.md` are:

- `scripts/test_website_claims.py` + `scripts/website_claims.py` +
  `scripts/website_quality.py` (new files, auto-discovered by CI step 1), and
- `scripts/test_website_quality.py` (new file, same discovery).

A site *build* failure cannot redden app CI: app CI's Python step reads the
**committed** built HTML (see §5) with no `npm`, no network and no Node
requirement. The site's own build/validate/deploy lives in its own workflow.

## 2. Verified baseline (this session, 2026-10-08)

| Fact | State | Evidence (this session) |
|---|---|---|
| Working branch | `arena/fc918d37-dhun`, clean tree at boot | `git status`, `git branch --show-current` |
| Branch base / GitHub `main` | `ae44c7a` — **PR #137 merge**, not `8a8d6c5` | `git log --oneline -5`, `origin/main` |
| PR #135 post-merge verdicts (left unread by the previous session) | ✅ **success** on `8a8d6c5` for CI `37772063324`, test-release `37772063364`, Build APK `37772063380`, pages `37772062338` | `gh api .../actions/runs/<id>` |
| Rolling `test` release | republished `2026-10-08T14:14:51Z`; assets `dhun-test.apk` 18,405,859 B, `dhun-test-arm64-v8a.apk` 18,355,786 B, `dhun-test-armeabi-v7a.apk` 18,352,944 B, `dhun-test.msi` 112,971,776 B (+ four `.sha256` sidecars) | `gh release view test --json assets` |
| Pages configuration | `build_type = legacy`, `source = main:/`, `status = built`, `https_enforced = true` | `gh api repos/99ggprooo00-code/DHUN/pages` |
| Canonical URL content | **serves the rendered root `README.md`**, not a 404 — the D9/§10 "404" premise was the *old* `99ggprooo00` hostname and is stale | content fetched from `https://99ggprooo00-code.github.io/DHUN/` this session |
| `README.md` line 2 | already the canonical `99ggprooo00-code.github.io` URL, and it resolves | repo file + fetched page |
| Node / npm / Python | `v22.22.3` / `10.9.8` / `3.11.2` | `node --version`, `npm --version`, `python3 --version` |
| Go / Ruby / Hugo / browsers | absent — no Lighthouse, axe or visual check can run locally | `which go ruby bundle hugo chrome chromium firefox` |
| Eleventy installs | `@11ty/eleventy@3.1.6` → 129 packages in 7 s from `registry.npmjs.org` | `npm install` in `website/` this session |
| Actions log archives | unreachable; **check-run annotations** are reachable | prior session's finding, re-used deliberately |
| Token capability | repository `admin: true`; Pages API readable | `gh api repos/... --jq .permissions` |

**Pages diagnosis (D10, done — 15-minute box respected).** The site is *not*
misconfigured in the sense the brief assumed: legacy Pages on `main:/`
publishes the repository root and Jekyll renders `README.md` into an
`index.html`, which is why the URL resolves to README text. That is a *product*
defect (the front door is an engineering document), not a deployment break
like the 2026-10-08 auto-created `pages-build-deployment` run
`37759719910`. The fix is to publish a real static artifact from Actions
(`build_type: workflow`). The exact one-line change and its unverified status
are recorded in §7.

## 3. Stack decision (D3)

**Eleventy (`@11ty/eleventy`) 3.1.6, pinned exactly, lockfile committed.**

- Chosen over Astro because its output *is* the input: no bundler, no asset
  hashing, no client runtime, no hydration concept to accidentally enable. The
  build result is plain, byte-stable HTML + CSS, which is what the committed
  dist mirror and the drift check in §5 need.
- Rejected: Astro (also viable, heavier install, hashed asset names make the
  committed mirror noisier); Hugo and Jekyll (not installed here — cannot be
  built or verified locally, so the work would be CI-blind); a hand-written
  script-only site (no reason to avoid a real generator, and Eleventy gives
  layout inheritance for the three pages).
- Build is one command: `npm ci && npm run build` in `website/` → `website/dist/`.
- Zero client-side JavaScript is shipped. No analytics, no CDN, no third-party
  request at runtime. `@11ty/eleventy` and its 128 transitive packages are
  **build-time only**; nothing from `node_modules` is copied to `dist/`, and a
  test asserts that (`scripts/test_website_quality.py`).

## 4. Content truth contract (D5)

DHUN's real surface, restated so no page can drift from it:

- Android, `minSdk 24` (Android 7.0) — primary. Desktop: Windows first;
  Linux/macOS "free via the JVM build", **never hardware-verified**.
- No iOS, no web client, no accounts, no cross-device sync, no library import,
  no casting, no Android Auto, no store release.
- **No audio-quality number exists.** No bitrate, no "lossless", no "FLAC",
  no "24/192" may appear anywhere on the site.

**May lead with (each claim gets an HTML comment citing its ADR/PR):**
no sign-in / no cookies / no PO tokens (ADR-001, ADR-003) · GPL-3.0, free,
no ads, no telemetry · persistent offline downloads (ADR-006) · synced lyrics
(LRCLIB → YTM lyrics → cache) · real desktop client (tray, SMTC, jump lists,
single-instance, 10-band EQ) · Android home-screen widgets (Quick Play).

**Must appear visibly on the front page:** extraction is "borrowed time"
(MASTER_PROMPT §2); the rolling build is literally "Rolling UNVERIFIED
development build"; the S3 hardware gates are **OPEN**.

**Enforced by tests, not prose** (three checks in `scripts/`, wired into app
CI step 1 and the site workflow, mutation-proven red-then-green):

1. **Forbidden claims** — fails on any forbidden platform or capability claim
   in the *built* HTML (iOS, web player/PWA, sync, import, FLAC, bitrate, and
   every platform DHUN does not ship).
2. **Required caveats** — fails if the front page is missing the unverified /
   borrowed-time / open-gate caveats.
3. **No baked digests** — fails on any hard-coded SHA-256 or byte size in the
   built HTML or site sources; downloads link by URL only, because the rolling
   assets change on every merge to `main`.

## 5. Enforcement architecture (why committed `dist/`)

App CI step 1 is `python3 -m unittest discover -s scripts -p 'test_*.py'`. To
keep that step pure-Python, network-free and incapable of being reddened by a
site *build* failure while still asserting against **built HTML**, the built
site is committed at `website/dist/` and:

- app CI step 1 (and the site workflow) check the **committed** HTML for
  claims, caveats, digests, links, weight budget and HTML validity;
- the site workflow rebuilds from source and **fails on drift** between the
  fresh build and the committed mirror, then deploys the freshly built
  artifact, so what is served is what was built, and what is tested is byte
  identical to it.

Honest limitation: the deployed artifact and the committed mirror are proven
equal by the drift check inside the deploy workflow — that check cannot run
before the merge (the workflow is not on `main` yet), so the drift check is
**not verified pre-merge**.

## 6. Pages and deployment (D9, D10)

- Canonical URL stays `https://99ggprooo00-code.github.io/DHUN/` (no custom
  domain — D7).
- Deployment moves to Actions: `.github/workflows/website.yml` builds,
  validates and deploys the `website/dist` artifact with
  `actions/{configure-pages,upload-pages-artifact,deploy-pages}` and
  `permissions: pages: write, id-token: write` on the `github-pages`
  environment.
- The Pages **source** must be switched to `build_type: workflow` (a
  repository setting). `gh api repos/.../pages` is readable with this token;
  whether the token can `PUT` the setting is tested at the end of this run and
  recorded in the session docs. If it cannot, the workflow still ships and the
  one-line user action is: *Settings → Pages → Build and deployment → Source →
  GitHub Actions*.
- Fallback while legacy mode is active: the committed mirror is reachable at
  `https://99ggprooo00-code.github.io/DHUN/website/dist/` under the current
  `main:/` source. That is a fallback, not the goal — the goal is the root.

## 7. Information architecture (D6, D7)

Exactly three routes, English only, copy centralised in `website/src/_data/`

> **Dated amendment, 2026-10-08 (session `arena/9b791057-dhun`):** the three
> routes are now `/`, `/features/` and **`/ui/`**. `/download/` was removed by
> direction — this site is not a distribution channel, so it carries no
> artifact links and no installation or verification instructions; it links the
> rolling `test` pre-release as a page. `/ui/` shows the app's interface (five
> surfaces), the design tokens read from
> `shared/src/commonMain/kotlin/dev/dhun/design/`, and the recreation contract.
> §9 gained rows 5–6 (`mock-search-phone`, `mock-settings-phone`).
so translation stays additive later (no i18n is built):

| Route | Job |
|---|---|
| `/` | promise, proof, the two CTAs, honestly framed risk, closing CTA |
| `/download` | the rolling `test` release, what it is and is not, verification steps, build-it-yourself |
| `/features` | what actually exists, each item traceable, plus an explicit "not in DHUN" list |

Landing-page order (abstracted from the verified Volta pattern in Part B §3,
with DHUN substitutions): skip link → header → badge row
`FREE · GPL-3.0 · NO SIGN-IN · NO ADS` → 4-word H1 → one-sentence value prop →
**exactly two CTAs** (`Download` → `/download`; `Source on GitHub` →
repository) → platform strip → hero visual (Home) → 4-up stat row
(`0` accounts · `GPL-3.0` free forever · `Android 24+` / desktop via the JVM
build · `0` trackers) → **honesty footnote under the stats** → three feature
sections (**NO SIGN-IN / OFFLINE / DESKTOP**) each kicker + promise + its own mockup (player / downloads / desktop window — four distinct screens in total, so no screen is repeated) →
"borrowed time" risk section → closing CTA repeating both buttons → footer
with the GPL-3.0 notice, source link and `THIRD_PARTY.md`.

`/download` states, without hedging: `minSdk 24` / Android 7.0; the MSI needs
a system VLC for playback; the build is debug-keystore-signed and test-grade,
not store-signed; verify the `.sha256` sidecar before installing; the three
APKs are **not** byte-identical (they are ABI splits of the same build); the
universal APK is the one to install; nothing here is a stable release.

## 8. Visual system (D4) — no images exist, so none will be faked

There is no raster or vector artwork in the repository (verified in Part B
§2.2 and unchanged: the launcher art is XML), no display and no device, so
**there are no screenshots and none will be invented**.

- Hero and feature visuals are **hand-written CSS/SVG device mockups** that
  recreate DHUN's real UI: colours, type scale, spacing and component
  structure are read out of `shared/src/commonMain/kotlin/dev/dhun/design/`
  and `…/ui/`.
- Tokens used (read from `DhunAppearance.kt` this session): background
  `#161616`, surface `#1E1E1E`, surfaceVariant `#262626`, surfaceElevated
  `#303030`, surfaceHighest `#363636`, surfaceCard `#2A2A2A`, accent
  `#BB86FC`, onAccent `#000000`, accentContainer `#3A2A5A`,
  onAccentContainer `#E8D5FF`, text ladder `#FFFFFF` / `CC` / `99` / `61`,
  border `#1AFFFFFF`, glass edges `#38FFFFFF`; shapes 4/8/12/16/28/32 dp
  (`DhunShapes`); spacing scale and 44/48 dp touch targets (`DhunSpacing`);
  type scale from `DhunTypographyTokens` (display 57/45/36, headline 32/28/24,
  title 22/16/14, body 16/14/12, brand 12 sp with 3 sp tracking).
- **Forbidden:** stock photos, third-party album art, other projects'
  screenshots, AI-generated imagery, and any mockup showing a feature DHUN
  does not have.
- Every mockup carries a visible `<figcaption>` that says it is an illustrative
  recreation, **not a screenshot**, plus a screen-reader description of what the
  recreation shows; the drawing itself is `aria-hidden`, so assistive tech never
  reads placeholder UI text as if it were content. `scripts/website_quality.py`
  fails the build if a mockup loses either the label or its §9 backlog id.
- §9 lists exactly which real captures should replace each mockup.

## 9. Screenshot capture backlog (D4 follow-through)

To be captured by the user during the S3 rounds (device + Windows), in this
priority order. Until each arrives, the named mockup stands in, labelled as a
recreation.

The **Status** column is machine-read: `scripts/website_quality.py` parses this
one table and fails the build in both directions — a `shipped` row whose figure
is missing from the built pages, a `planned` row that appears on a page before
its capture exists, and a figure on a page that no row names. Adding a mockup to
the site without adding it here (or the reverse) is therefore a red test, not a
review note.

| # | Mockup (site id) | Status | Real capture that should replace it | What it must show |
|---|---|---|---|---|
| 1 | `mock-home-phone` | shipped | Android Home, portrait | rail layout, now-playing backdrop, bottom nav + docked mini-player |
| 2 | `mock-player-phone` | shipped | Android FullPlayer, portrait | blurred artwork backdrop, Lyrics tab, transport row, play disc |
| 3 | `mock-downloads-phone` | shipped | Android Library → Downloads | downloaded rows with offline badges, in-progress row |
| 4 | `mock-desktop-window` | shipped | Windows desktop window, 1200×780 | rails layout, tray-adjacent mini-player, EQ or queue panel open |
| 5 | `mock-search-phone` | shipped | Android Search with a query typed | result-type chips, rows with duration, query visible |
| 6 | `mock-settings-phone` | shipped | Android Settings → Equalizer | ten band sliders with labels, presets, preamp row |
| 7 | `mock-widget` | planned | Android home screen with the Quick Play widget | widget on a launcher, not the app |
| 8 | `mock-lyrics` | planned | Android FullPlayer → Lyrics, mid-song | a line highlighted against real synced lyrics |

Content-safety rule (unchanged from Part B §7): only legally safe content with
recorded provenance may enter Git; no third-party album art without
permission.

## 10. Quality gates (D-quality, §5 of the brief)

| Gate | How it is asserted | Where it runs |
|---|---|---|
| Page weight budget | `scripts/website_quality.py`: per-route HTML+CSS ≤ 60 KB uncompressed, JS ≤ 10 KB, no single asset > 150 KB | app CI step 1 + site workflow |
| HTML validity | `html-validate` over the built output | site workflow (npm, pinned) |
| Internal link check | `scripts/website_quality.py` — every internal href/src resolves to a built file | app CI step 1 + site workflow |
| Honesty (D5) | the three checks in §4 | app CI step 1 + site workflow |
| Responsive | mobile-first layout, `clamp()` type, breakpoints 480/768/1024/1440, no fixed px widths that break 320 px; asserted by a source-level scan for fixed-width declarations plus a manual reading of the CSS | local + site workflow |
| Accessibility | semantic landmarks, one `h1` per page, skip link, visible focus, ≥4.5:1 body contrast, `prefers-reduced-motion`, `prefers-color-scheme`, keyboard-reachable nav | asserted where a machine can (structure/contrast maths in `website_quality.py`), Lighthouse/axe in CI |
| Lighthouse / axe | real numbers from CI on `ubuntu-latest`, surfaced as **check-run annotations** so they are readable from here. **Measured, run 37795271256:** `/` 96/100/100/100 and `/features/` + `/download/` 100/100/100/100 (performance/accessibility/best-practices/SEO). `@axe-core/cli` exited 1 without writing a report on all three routes, so **no axe number exists** and none is claimed. Gated: accessibility ≥ 0.95, performance ≥ 0.90 | site workflow |
| Craft | 404 page, canonical, Open Graph + Twitter meta, favicon from DHUN's own XML launcher art, `robots.txt`, `sitemap.xml`, `lang`, GPL notice in the footer | asserted by the quality check + review |

Honesty about the environment: GitHub Pages gives no control over
compression or cache headers — no `Content-Encoding` or `Cache-Control`
tuning is possible, only file size and request count. Stated on the download
page's technical footnote rather than claimed as optimised.

### Dated amendment — 2026-10-08 (session `arena/37ec95ed-dhun`): one request for real, the hard viewports, print, and structured data

Read the numbers in `docs/verification/22-one-request-hard-viewports-print-and-structured-data.md`;
this is the decision record, not the evidence table.

**D11 — the tab icon is a build-time `data:` URI, and no asset file ships.** The
merged head measured `requests=2` per route, the second request being
`/assets/dhun-favicon.svg`. `src/_data/favicon.js` now inlines the SVG (base64 of
the source with inter-tag whitespace folded; measured 638 B against 692 B for the
percent-encoded form and 620 B for one that leaves literal spaces in a URI and is
therefore invalid), the passthrough copy is removed, and
`favicon_violations` decodes the URI out of every built page and compares it with
`src/assets/dhun-favicon.svg`, so the icon cannot drift from the file it claims to
be. Trade-off recorded rather than hidden: a client that does not render SVG
favicons shows no tab icon instead of fetching one — see
`.ai/KNOWN_LIMITATIONS.md`. Reversal cost: restore `addPassthroughCopy`, the
`<link>` href and delete the data file; ~10 minutes.

**D12 — the browser matrix includes the awkward viewports, not just the common
ones.** 280×653 (fold-class), 844×390 (landscape phone) and 640×512 (a 1280×1024
window at 200 % zoom — the layout viewport, which `deviceScaleFactor` does *not*
emulate) join the six earlier widths, and a per-viewport `touch` flag replaces the
`width <= 768` test so the touch-target floor follows the context rather than the
orientation. Windows High Contrast and `prefers-contrast: more` are emulated;
each context asserts the emulation is visible to the page before measuring
anything, because a forced-colours check whose condition did not apply is a false
green. Reversal cost: delete the entries.

**D13 — the decision logic of those checks is a pure module, tested without a
browser.** `website/tests/rules.mjs` holds every predicate (target floors, heading
order, duplicate link text, focus change, forced-colours boundary, print caveats)
and `rules.test.mjs` exercises each with a must-pass and a must-fail case under
`node --test` in the *build* job. `browser.mjs` only gathers data. This is the
only way a rule that can run only on a Chromium runner could be mutation-proven
in an environment that has no browser at all. Reversal cost: high — the
alternative is untestable rules.

**D14 — print and forced-colours get real CSS.** No `@media print` rule existed;
the site is dark-first and browsers drop background colours, so a printed page was
white text on white paper. Both blocks are asserted per route by
`website_quality.py` (including the printed `--text` on `--bg` contrast ratio,
computed locally) and measured in the browser job. What is *not* claimed: the
print and forced-colours **rendering** is CI-measured, not eyeballed — no browser
exists in this environment.

**D15 — structured data is one `SoftwareApplication` block, and `og:image` is
refused.** The JSON-LD block states name, category, Android 7.0+/Windows, free,
LICENSE, canonical URL and repository; `structured_data_violations` asserts a
banned-key list (`aggregateRating`, `offers`, `price`, `softwareVersion`,
`datePublished`, `downloadUrl`, `installUrl`, …) is absent, because structured
data is what a machine repeats without the caveats around it. `og:image` is
**deliberately not added**: the repository contains no image, the only honest
option would be a hand-authored SVG, and no major crawler renders SVG for
`og:image` — an `og:image` pointing at an SVG would be a claim that fails where it
is consumed. A PNG would be exactly the fabricated imagery §8 forbids. Reversal
cost: low, if a real capture ever enters the repository.

**D16 — the honesty contract now reads the app's dependency graph.** `/features/`
says "no telemetry, no crash reporting, no advertising SDK". Nothing could check
that: the forbidden-claim rules only read the site's words back. The claim is now
checked against every `*.gradle.kts` / `*.versions.toml` in the repository
(12 name-shaped SDK patterns), with the four claim/SDK combinations exercised
against synthetic trees in `test_website_claims.py`. Reversal cost: none — it is
an added rule.

**D17 — the URL is reported on every run; the site is not planted at the repo
root.** The public URL renders the README (D9/§10 said so a session ago, and it
is still true), and that was recorded in planning documents only. A visitor, and
a reviewer reading a pull request, saw nothing. Decision: the `build` job now
reads the Pages source first, writes it to the run summary and raises a
`::warning::` naming the exact setting (Settings → Pages → Build and deployment →
Source → GitHub Actions) while `build_type != workflow`; the deploy job keeps its
publish gate and `served` keeps gating on the real publish output, so a skip is
still a skip. Deliberately **not** done: a copy of `dist` at the repository root
(`index.html`, `features/`, `ui/`) so legacy Jekyll would serve *something* —
that is a second, ungated copy of the site that every future site change has to
mirror by hand, published next to `docs/` and `.ai/`, and it would still not be
the artifact the deploy job publishes. Cost of the chosen path: publishing waits
on a human with Pages write access; the wait is now visible in every run.
Reversal cost: delete the step (its tests fail, by design).

**D18 — a route ships only the CSS it can use, pruned at build time against its own markup.**
Read the numbers in `docs/verification/23-per-route-css-pruning.md`; this is the
decision record, not the evidence table. Modules are shared (`base.css` carries the
header *and* the home hero; `components.css` carries the stat row *and* the 404
block), so a route was shipping rules it could never match — measured at boot: 18
unused classes on `/`, 52 on `/features/`, 53 on `/ui/`, 34 on `/404.html`.
`website/tools/prune-css.mjs` now runs between Eleventy and the minifier and drops
those rules per page: a selector survives when it has no positive class (so `body`,
`*`, `[aria-current]` and `a:not(.btn)` are never touched) or at least one class
the page uses; conditional groups are pruned recursively and dropped only when
empty; `@keyframes`/`@font-face`/`@import` are kept verbatim. Tokens are
deliberately **not** pruned — they are the design system's published contract
(`/ui/` prints their values) — so the win is smaller than it could be and the
sources stay one file per layer. Measured: `/` 53,554 → 51,311 B (−4.2 %),
`/features/` 54,881 → 48,019 B (−12.5 %), `/ui/` 56,263 → 49,508 B (−12.0 %),
`/404.html` 17,441 → 12,794 B (−26.6 %), with 0 bytes of client JavaScript and one
request per route unchanged. `dist_source_drift_violations` was adapted rather than
weakened — it now proves the committed CSS equals the *pruned* composition (same
rule set, same order, no missing rule, no extra rule) with the predicate re-derived
in Python, so app CI still needs no Node; `print_style_violations` now requires the
print block to hide a mockup only on a page that draws one. Both adaptations and
the two defects they exposed are mutation-proven (eight mutations, recorded in the
verification record). Reversal cost: delete the `prune-css.mjs` step from the
`build` script and rebuild — the drift check's expectation is the only other change
to undo; ~15 minutes. Cost of keeping it: the pruning predicate exists twice (JS
build, Python check), which the mutation table covers but no test can prove
*equivalent*.

**D19 — the header marks the page the visitor is on, and `/404.html` asks not to be indexed.**
Measured before the change: `aria-current` appeared **nowhere** in `website/dist/`
(this session) and none of the three routes marked itself; the 404 shipped no
`<meta name="robots">` at all. Now the wordmark carries `aria-current="page"` on
`/` and the matching nav item carries it on `/features/` and `/ui/`, styled twice
over: a filled pill for sighted users and an underline with the accent colour that
**survives Windows High Contrast**, where the engine drops author backgrounds. The
404 is not a destination, so it carries no marker and does carry `noindex`; the
three real routes must *not* carry it, because `noindex` on a real page removes it
from search and no other check here would notice. The static rule
(`navigation_state_violations`, +1 check → 27) asserts exactly-one-marker,
marker-points-here, and a marker rule that is not background-only; the rendered
half is `website/tests/browser.mjs` `current page` (including the forced-colours
pass) whose decision logic lives in `rules.mjs` and is mutation-proven without a
browser. Cost: **+290 B per route** (measured: `/` 51,311 → 51,601 B, `/features/`
48,019 → 48,309 B, `/ui/` 49,508 → 49,798 B, `/404.html` 12,794 → 13,102 B) — an
accessibility feature that buys no bytes, recorded so the ratchet shows it
deliberately. Reversal cost: delete the two `{% if %}` clauses, the CSS rule, the
`extraHead` line and the check; ~15 minutes, and the score disappears.

**D20 — an in-page jump lands below the sticky header, and every anchor target exists.**
The header is `position: sticky` (base.css) and its row is 64px tall with a 44px
target floor inside it, so it wraps as the viewport narrows: measured off the
page's own `:root` block, `--target` 44px + `--sp-4` 16px + `--target` 44px =
**104px** for the two-row header, and **164px** (3 × 44 + 2 × 16) where the
navigation itself wraps to two lines. Nothing pushed the scrollport down, so the
skip link's `#main` and the footnote links on `/` — the site's only in-page jumps —
landed with their first line *behind* the header; for a footnote the covered line
is the whole footnote. Fixed with a mobile-first pair: `:root { scroll-padding-top:
12rem }` (192px, covering the 164px three-row worst case up to 479px) and
`@media (min-width: 480px) { :root { scroll-padding-top: 7rem } }` (112px for the
104px two-row case, 8px slack). Both are in `rem` so a visitor who raises the
browser's default font size gets a proportionally larger offset, not a smaller one.
The new static check (`anchor_landing_violations`, check count 27 → 28) asserts
both directions of the landing: every `href="#…"` on a page has a matching `id`,
and a page that is sticky-headed *and* has an in-page jump must declare
`scroll-padding-top` on `:root`/`html` **outside any conditional group** (a
`@media`-only declaration is not a base) with no declared value below the two-row
floor — both numbers re-derived from the page's own tokens rather than trusted as
104. The rendered half — the target's real position under the real header, at
1280×800, 380×800 and 280×653 (the smallest display class the site supports, where
the navigation can wrap) — is the browser check `anchors land below the header`,
decided by the mutation-proven `anchorLandingProblem()`, which also reports the
effective `scroll-padding-top` so a failure names the number to change. Cost:
**+57 B per route** (measured: `/` 51,631 → 51,688 B, `/features/` 48,339 →
48,396 B, `/ui/` 49,828 → 49,885 B, `/404.html` 13,132 → 13,189 B), recorded in
the ratchet. Reversal cost: delete the two CSS declarations, the check and its
registration; ~10 minutes, and the jump defect returns.

## 11. Work plan, execution and honest status

| Phase | Deliverable | Status |
|---|---|---|
| P0 | this plan; ROADMAP current-task correction; PR #135 verdicts read | ✅ done (commit `2e0dcf8`, this file) |
| P1 | comparative research retained from Part B §4 with URLs, plus 3 adopted / 2 rejected techniques (§12) | ✅ done (§12) |
| P2 | Pages diagnosis recorded (§2, §6) | ✅ diagnosed — `legacy` `main:/` renders the README; the source switch is applied at the end of the session and its outcome recorded in the PR |
| P3 | `website/` scaffold builds locally; own workflow | ✅ done — `npm ci && npm run build`, workflow added |
| P4 | design tokens from the Compose source | ✅ done — dark + light token sets mirrored in `styles.css` |
| P5 | CSS/SVG mockups | ✅ done — 4 mockups, labelled, ids matching §9 |
| P6 | `/`, `/download`, `/features` | ✅ done — plus a real 404, robots.txt, sitemap.xml |
| P7 | quality pass (weight, a11y, HTML validity, links, meta) | ✅ done locally — budget 50,455 B worst route, html-validate clean, 12 contrast pairs pass, no third-party origin |
| P8 | the three D5 tests, CI wiring, mutation proof | ✅ done — 47 tests in CI step 1, 3 mutations proven red then green after revert |
| P9 | CI results read once, root-cause fixes | ✅ read at the finish sequence; verdicts recorded in the PR comment |
| P10 | docs: ROADMAP, DEBUG_LOG, KNOWN_LIMITATIONS, verification record, README, THIRD_PARTY, CHANGELOG | ✅ done |
| P11 | **post-merge only**: `website.yml` on `main` and the canonical URL | ⏳ cannot be observed from inside the merging session. The Pages source itself is **blocked**: the token gets HTTP 403 on `PUT /repos/{owner}/{repo}/pages`, so switching it to `build_type: workflow` is a user-only one-liner |

**Unverified as of P0 (deliberately listed rather than glossed):** the CI
verdicts for any commit of this branch; the drift check; the Actions Pages
deployment; whether the token may switch the Pages source; `html-validate`,
Lighthouse and axe results; the served content at the canonical root.

## 12. Comparative research — what is adopted and what is rejected

The eight-project survey (Spotube, RiMusic, InnerTune, ViMusic, OuterTune,
Harmony Music, Moosync, Echo Music) with canonical URLs, licence snapshot and
a cross-project synthesis table already exists, verified 2026-10-08, in
**Part B §4**; it is cited rather than re-run, and the three claims that drive
this plan were re-checked directly (see §12.1).

**Three techniques adopted**

1. **Two CTAs, one of them the source** (Spotube, Moosync shape; Volta shape):
   `Download` and `Source on GitHub`. No third destination competes with them,
   and download detail lives on its own route.
2. **Lifecycle status outranks marketing** (RiMusic, OuterTune, Harmony all
   lead with archived/inactive state). DHUN is alive but its rolling build is
   unverified and its hardware gates are open, so that status is stated in the
   first viewport, not in a footer.
3. **Every visual is a real interface, labelled** (Echo names its six
   screenshots; OuterTune/Harmony show that a decorative banner proves
   nothing). DHUN has no captures yet, so each mockup is labelled as a
   recreation and §9 names the capture that will replace it.

**Two techniques rejected**

1. **Store/distribution badges** (F-Droid / IzzyOnDroid / Flathub): DHUN has
   no such channel. Displaying a badge pattern copied from InnerTune or
   RiMusic would be a claim DHUN cannot support.
2. **Hide the extraction risk behind architecture language** (Spotube frames
   it as bring-your-own plug-ins; Echo and InnerTune leave it in a disclaimer
   or FAQ). DHUN states the borrowed-time maintenance reality plainly on the
   front page and repeats it where the download is offered.

### 12.1 Spot-checks performed this session

Re-checked directly rather than trusted from Part B: the DHUN repository facts
in §2 (all re-measured here), and the licensing position in §13. Part B's
per-project URL list is retained as cited evidence from 2026-10-08 and is
**not** re-fetched in full — stated as a limitation, not a claim.

## 13. Licence (D8)

- The site is part of this GPL-3.0 repository and stays **GPL-3.0**; the
  footer carries the notice and links `LICENSE` and `THIRD_PARTY.md`.
- **Fonts:** the default is the system font stack — zero downloads, zero
  licence risk, fastest paint. If a webfont is ever added it must be OFL-1.1,
  self-hosted, subset, `font-display: swap`, and listed in `THIRD_PARTY.md`.
- **No third-party runtime asset ships**: no stock imagery, no icon library,
  no CDN, no analytics, no webfont file in this first build. The favicon is
  derived from DHUN's own Android launcher art (XML, first-party) and the
  vector is written by hand into `website/src/assets/`.
- Build-time tooling licences (Eleventy MIT, `html-validate` MIT) are recorded
  in `THIRD_PARTY.md` and ship nothing to the browser.

---

---

# Part B — prior research and evidence (2026-10-08, session `arena/ae65f1a5-dhun`)

> **Retained as cited evidence, not as the current plan.** Part A above is the
> plan of record. Everything below was verified on 2026-10-08 by that session
> and is quoted by Part A where it is still accurate: §B.2 (Pages + asset +
> toolchain baseline), §B.4 (comparative research, licences, distribution
> links, cross-project synthesis) and §B.7 (asset/licence gate). Its Option-B
> framing of §B.1 and its Option-A-as-fallback conclusion in §B.6 are
> **superseded** by Part A §1. Section numbers below are prefixed `B.` and do
> not refer to Part A.

# DHUN web presence — research and gated implementation plan

> **Status (2026-10-08): RESEARCH / PLAN ONLY. W0 answers are recorded; the
> user selected a browser player and separately accepted ADR-008 for its B1
> deployed-origin feasibility spike only. B1 has now run and is BLOCKED: the
> dependency-free probe in `web-spike/` is deployed at the canonical Pages
> origin, anonymous metadata was readable, and the player request was blocked
> before a readable response in the only available browser
> (Brave/Chromium-family; Firefox and Safari unavailable). No Web-support claim
> is permitted, and the next step is a separate ADR-008 B2 user decision.**
> There is no production `website/` directory, site dependency, Pages workflow
> change, or web-target application code.
>
> Standing instruction: research first, compare several approaches, preserve
> the analysis in a `.md` file, and plan when to implement rather than rushing
> into a copy of the sample site.

## B.1. The decision that comes before all implementation

The reference URL hides two different products:

- <https://volta-music.com/en> is Volta's **marketing/download website**.
- <https://app.volta-music.com/> is the **Flutter web application**. Re-fetched
  on 2026-10-08, its title is `Volta` and the extracted body is only
  `flutter typography measurement`, which is Flutter's bootstrap surface, not
  the marketing page.

DHUN therefore has two materially different options:

| Option | Meaning | Architectural effect | Status |
|---|---|---|---|
| **A — marketing/download site** | A static public site describing the existing Android and Desktop applications, linking the rolling release and source | New deployment workstream, but not a new application target; does not contradict the accepted Android/Desktop architecture | **Not selected; retained as fallback** |
| **B — DHUN web player** | A browser-playable third application target | Contradicts the prior Web deferral/cut and Android+JVM-only stack; accepted ADR-008 permitted only a B1 feasibility spike before any product architecture choice | **Selected; B1 ran at the canonical origin and is BLOCKED — B2 decision pending** |

The research recommendation remains **Option A** because it addresses the
public presence without reopening a rejected platform target. The user instead
selected **Option B** after the distinction was restated in simple terms. That
choice was recorded separately from architecture approval. The user then
accepted `docs/decisions/ADR-008-browser-web-player-target.md` for its B1
feasibility spike only, and B1 has now run to a **BLOCKED** result (see §1.1).
No production player, backend/proxy, extraction change or B2 stack selection is
approved, and B1 must not be restarted.

### B.1.1 W0 answers recorded on 2026-10-08

| Question | User choice | Consequence |
|---|---|---|
| Product | **Play music inside the website (Option B)** | ADR-008 B1 is accepted as the next evidence gate; full product work remains blocked |
| Hero visuals | **Real screenshots captured during S3** | W3 waits for Android/Windows captures with safe content and provenance |
| Warning placement | **Product-first headline; warning lower on the first screen** | The unofficial/upstream-breakage notice remains on the first viewport, not only in a footer |
| URL | **Canonical GitHub Pages URL** | Use `https://99ggprooo00-code.github.io/DHUN/`; no custom-domain work |
| Languages | **English only for v1** | No locale-prefixed routes initially; structure may remain translation-ready |
| README line 2 | **Correct now** | Updated to the canonical `-code` hostname in this session |

W0 is answered and ADR-008 B1 is explicitly accepted. The smallest static
candidate is implemented in `web-spike/`, contract-tested, merged via PR #136
(`2a20024`) and now deployed at the canonical Pages origin
(`https://99ggprooo00-code.github.io/DHUN/web-spike/`, revision `b1-v1`). The
canonical manual run is complete and classified **BLOCKED**: metadata passed,
the player response was blocked before a readable response, and the media/audio
stages were never reached. Firefox and Safari were unavailable. A full
web-player architecture remains unapproved, no Web-support claim is permitted,
and any continuation is a separate B2 user decision.

## B.2. Verified baseline — repository, Pages and the advertised URL

Verified on 2026-10-08 against GitHub and the checked-out repository:

### B.2.1 The advertised URL was wrong; W0 authorized the correction while Pages kept working

Before the W0 correction, `README.md` line 2 advertised:

- <https://99ggprooo00.github.io/DHUN/> — **404**, “There isn't a GitHub Pages
  site here.”

The repository owner is `99ggprooo00-code`, not `99ggprooo00`. The Pages API
reports:

```text
build_type = legacy
source.branch = main
source.path = /
html_url = https://99ggprooo00-code.github.io/DHUN/
status = built
```

The canonical URL is therefore:

- <https://99ggprooo00-code.github.io/DHUN/> — **live**, currently rendered by
  GitHub Pages/Jekyll from the root `README.md`.

The post-PR-#135 Pages run **37772062338** on `main@8a8d6c5` succeeded, and the
Pages build API records build **1269157028** as `built` with no error. The
pipeline is not publishing into a black hole. The earlier hypothesis “Pages
source is misconfigured or the artifact is empty” is disproven. The concrete
fault is a missing `-code` in the README hostname. There is still a product
problem: the canonical site is a rendered engineering README, not the planned
public experience.

**W0 outcome:** the user chose to correct line 2 immediately. It now points
to the canonical `-code` URL. This repairs the link; it does not turn the
rendered README into a product site or web player.

### B.2.2 Asset inventory

A repository-wide search (excluding `.git`) finds **zero** `png`, `jpg`,
`jpeg`, `webp`, `svg` or `gif` files. Android launcher art is XML. No product
screenshots are available.

A Volta-like page is image-led, so this is a content blocker rather than an HTML
problem. The preferred capture opportunity is the same device work already
required by S3 rounds 4 and 5. Asset alternatives are:

1. user captures Android landscape/portrait and Windows fullscreen during the
   S3 sessions;
2. an emulator screenshot pipeline is researched and proved separately (not
   available in this sandbox and not assumed viable);
3. CSS-only device frames and schematic UI mockups are used, with no claim that
   they are literal screenshots.

The user selected **real screenshots captured during S3**. CSS schematics
remain an unselected fallback if safe/current captures cannot be produced and
the user approves that substitution.

### B.2.3 Toolchain measured in this sandbox

| Tool | Measured result |
|---|---|
| Node | `v22.22.3` |
| npm / npx | `10.9.8` / `10.9.8` |
| Python | `3.11.2` |
| Go | not installed |
| Ruby / Bundler | not installed |
| Hugo | not installed |
| npm registry | allowed by the sandbox network policy |

Consequence: Astro, Eleventy, or a no-build static site can be built and checked
locally. Jekyll and Hugo would be CI-blind in this environment. Do not choose
those generators merely because GitHub Pages has historical defaults for them.

## B.3. Volta — verified pattern, not content to copy

Sources re-fetched 2026-10-08:

- <https://volta-music.com/en>
- <https://volta-music.com/en/download>
- <https://volta-music.com/en/features>
- <https://app.volta-music.com/>

### B.3.1 Information architecture

The marketing homepage follows this sequence:

1. skip link;
2. eyebrow badge row (`FREE · FLAC 24/192* · NO ADS`);
3. four-word H1 (`Your music. Amplified.`);
4. one-sentence value proposition;
5. exactly two CTAs (`Download for free`, `Open in browser`);
6. platform strip;
7. three real product images;
8. four compact proof/stat blocks;
9. an honesty footnote immediately under those claims;
10. three feature sections, each a kicker, one promise, and a UI demonstration;
11. `AND MORE`, feature cards, and `/features` link;
12. closing CTA that repeats the same two actions.

It has three marketing routes under a locale prefix: `/en`, `/en/download`,
`/en/features`. Download and browser app are separate destinations. The
screenshots do most of the persuasion.

### B.3.2 What transfers to DHUN

- one promise per viewport;
- two primary actions, not a wall of badges;
- a separate download page and feature page;
- a real demonstration or an adjacent footnote for every product claim;
- a closing CTA that repeats rather than inventing a third conversion path;
- visible support/platform qualifiers close to the claim.

### B.3.3 What must not transfer

Volta claims six platforms, cross-device sync, import, accounts, and up to FLAC
24/192. DHUN has no evidence for those claims. DHUN currently has Android and a
Desktop JVM client (Windows first); Linux/macOS are not hardware-verified. It
has no iOS app, web app, account sync, import, or verified audio-quality number.

Copying Volta's words, visual assets, logo, fonts, or measurements is out of
scope. The reference is for page structure only.

## B.4. Comparable open-source music projects — W2 research

Research rule: prefer each project's controlled domain or canonical GitHub
repository. Third-party APK/SEO sites are not treated as product evidence.
“Not found” or “archived” is itself a finding.

### B.4.1 Spotube

**Sources:** <https://spotube.cc/>,
<https://github.com/team-spotube/spotube> (the old `KRTirtho/spotube` URL now
resolves to this repository), <https://spotube.cc/downloads>.

- **Above the fold:** `Spotube`, then “A cross-platform extensible open-source
  music streaming platform,” a plugin-oriented value proposition, and two CTAs:
  `Downloads` and `Learn More`.
- **Visual proof:** one large real desktop screenshot and a mobile screenshot
  carousel; images appear before the long feature inventory.
- **Risk framing:** the captured homepage does not foreground “unofficial” or
  “may break.” It frames service dependency indirectly as bring-your-own
  metadata/audio plugins. A release note acknowledges a prior hiatus from
  legal/structural complications, but that warning is not above the fold.
- **Distribution:** a dedicated downloads route plus GitHub Releases, F-Droid,
  Flathub, package managers and platform installers.
- **Lesson for DHUN:** strong separation of landing content and download detail;
  real screenshots and two CTAs work. Do **not** copy its broad platform claim
  or hide DHUN's extraction risk behind architecture language.

### B.4.2 RiMusic

**Sources:** <https://github.com/fast4x/RiMusic>, historical domain
<https://rimusic.xyz/>, and repository `docs/index.html`.

- **Current state first:** the repository was archived on 2025-07-30 and the
  README says “This project, is closed.” Its checked-in site intentionally
  renders `Website not available.` The historical domain no longer provides a
  usable product site.
- **Above the fold (canonical README):** logo, multilingual/multiplatform
  sentence, ViMusic lineage, customization and “does not collect any data,”
  immediately followed by the closed-project notice.
- **Visual proof:** six phone screenshots.
- **Risk framing:** closure is prominent; the affiliation disclaimer is much
  lower. It does not frame extraction as a service that may break.
- **Distribution:** GitHub, OpenAPK, Accrescent, Obtainium, IzzyOnDroid and
  F-Droid badges remain in the archived README.
- **Lesson for DHUN:** lifecycle status must outrank marketing. Do not leave a
  polished download page pretending that an unverified or retired build is
  current.

### B.4.3 InnerTune

**Source:** <https://github.com/z-huang/InnerTune>. No separate homepage is
listed by the canonical repository.

- **Above the fold:** app icon, `A Material 3 YouTube Music client for Android`,
  release/license/download badges, then GitHub, F-Droid and IzzyOnDroid install
  badges.
- **Visual proof:** five phone screenshots after the feature list.
- **Risk framing:** an explicit warning says unsupported YouTube Music regions
  need a proxy/VPN; a bottom disclaimer says the project is not affiliated with
  YouTube/Google. It does not say extraction may break.
- **Distribution:** GitHub Releases, F-Droid and IzzyOnDroid are first-class.
- **Lesson for DHUN:** trusted distribution choices can be presented cleanly,
  but DHUN currently has only test-grade GitHub artifacts and must not imply an
  F-Droid/store channel that does not exist.

### B.4.4 ViMusic

**Sources:** original project <https://github.com/vfsfitvnm/ViMusic> and the
separate site <https://vimusic.vercel.app/>.

- **Identity warning:** the original `vfsfitvnm/ViMusic` repository is archived
  (2026-03-15) and has no homepage. `vimusic.vercel.app` belongs to the separate
  `ab007shetty/ViMusic` project: a React/Vite/Supabase web version with Google
  sign-in/cloud sync. It is not the original Android project's marketing site.
- **Above the fold (original README):** icon, one-line Android/YouTube Music
  description, then six screenshots before features.
- **Above the fold (separate web app):** a live library/player (`Master's Mix`),
  not a conventional marketing page.
- **Risk framing:** original README has a non-affiliation disclaimer at the
  bottom, not a may-break notice. Archive state is now supplied by GitHub.
- **Distribution:** original README links GitHub Releases, IzzyOnDroid and
  F-Droid.
- **Lesson for DHUN:** verify ownership before using a domain as inspiration;
  a familiar product name can point at a different architecture and operator.

### B.4.5 OuterTune

**Source:** <https://github.com/OuterTune/OuterTune>. The domain
`outertune.app` fetched in research is an unrelated SEO/APK site and is **not**
used as canonical evidence.

- **Above the fold:** icon, one-line Material 3 description, then an unusually
  honest stop notice: the app is no longer in active development and suggests
  replacements.
- **Visual proof:** the collapsed historical README contains three large app
  screenshots plus a gallery.
- **Risk framing:** inactive status is the first substantive content. The old
  README also has a YouTube Music region warning and non-affiliation disclaimer.
- **Distribution:** historical GitHub, IzzyOnDroid and Obtainium links remain
  inside the collapsed section.
- **Lesson for DHUN:** this is the strongest comparable for status honesty. It
  also demonstrates why official links should be allow-listed: a polished
  third-party download site can look more “official” than the repository.

### B.4.6 Harmony Music

**Source:** <https://github.com/anandnet/Harmony-Music>. No separate homepage is
listed by the canonical repository.

- **Above the fold:** `This repository is no longer maintained`, then a wide
  cover image, product name, cross-platform statement and feature list.
- **Visual proof:** a cover/banner exists, but no app screenshot gallery is
  presented in the README.
- **Risk framing:** maintenance status is prominent; a long generic
  non-affiliation/no-warranty disclaimer is below download and licence content.
- **Distribution:** GitHub Releases and F-Droid.
- **Lesson for DHUN:** a banner is not product proof. Screenshots are more useful
  than a decorative hero when the promise is an interface.

### B.4.7 Moosync

**Sources:** <https://moosync.app/>,
<https://github.com/Moosync/Moosync>, and the public website source
<https://github.com/Moosync/Moosync.github.io>.

- **Above the fold:** `An Open Source Music Player`, H1 `A music player / For
  the community`, an OS-sensitive download control, `Download for other
  platforms`, and a decorative listening illustration.
- **Visual proof:** the page later uses a laptop screenshot among six feature
  callouts. The current app repository's screenshot section is still `TODO`, so
  the site and README are not equally complete.
- **Risk framing:** the marketing page does not foreground “unofficial” or
  “may break.” YouTube integration restrictions and the need for a user API key
  for private content are explained in the wiki instead.
- **Distribution:** direct OS download, other-platform route, GitHub Releases,
  and package channels.
- **Lesson for DHUN:** one detected-platform CTA reduces choice overload, but
  DHUN should not auto-label an OS build “supported” when its hardware gate is
  still open.

### B.4.8 Echo Music

**Sources:** <https://echomusic.fun/> and
<https://github.com/EchoMusicApp/Echo-Music>. The GitHub organization is
verified for `echomusic.fun`.

- **Above the fold:** `Music that moves you. No ads. No limits.` with an Android
  APK action; the site presents itself as an Android product.
- **Visual proof:** six named screenshots (Home, Search, Material player, Apple
  style player, Lyrics, Library), repeated in a horizontal showcase.
- **Risk framing:** an FAQ says it is a third-party YouTube Music client and does
  not host songs; the repository carries a very long legal disclaimer. Neither
  presents service breakage as a primary message.
- **Distribution:** the site sends downloads to GitHub Releases; its download
  interstitial contains ads, while the app is described as ad-free.
- **Lesson for DHUN:** named screenshots help users understand what they are
  seeing. Avoid ambiguous “no ads” copy if the download path itself uses ads;
  DHUN can truthfully keep both site and app free of ad/analytics code.

### B.4.9 Canonical distribution-link snapshot

These are the destinations exposed by the canonical site/README on 2026-10-08,
not a recommendation that DHUN copy every channel. “None found” means no such
link was found in the canonical material reviewed; it is not proof that a
third-party package does not exist.

| Project | GitHub release/download | F-Droid-family link exposed by project | Constraint |
|---|---|---|---|
| Spotube | <https://github.com/team-spotube/spotube/releases/latest>, <https://spotube.cc/downloads> | <https://f-droid.org/packages/oss.krtirtho.spotube> | broad multi-platform matrix is Spotube evidence only |
| RiMusic | <https://github.com/fast4x/RiMusic/releases/latest> | <https://f-droid.org/packages/it.fast4x.rimusic/>, <https://apt.izzysoft.de/fdroid/index/apk/it.fast4x.rimusic/> | repository is archived/closed; links are historical |
| InnerTune | <https://github.com/z-huang/InnerTune/releases/latest> | <https://f-droid.org/packages/com.zionhuang.music>, <https://apt.izzysoft.de/fdroid/index/apk/com.zionhuang.music> | Android-only channels |
| ViMusic (original) | <https://github.com/vfsfitvnm/ViMusic/releases/latest> | <https://f-droid.org/packages/it.vfsfitvnm.vimusic/>, <https://apt.izzysoft.de/fdroid/index/apk/it.vfsfitvnm.vimusic> | original repository is archived |
| OuterTune | <https://github.com/OuterTune/OuterTune/releases/latest> | IzzyOnDroid: <https://apt.izzysoft.de/fdroid/index/apk/com.dd3boh.outertune> | maintenance-stop notice outranks old download badges |
| Harmony Music | <https://github.com/anandnet/Harmony-Music/releases/latest> | <https://f-droid.org/packages/com.anandnet.harmonymusic> | README says no longer maintained |
| Moosync | <https://github.com/Moosync/moosync-tauri/releases>, <https://moosync.app/> | none found | desktop packaging, not Android/F-Droid |
| Echo Music | <https://github.com/EchoMusicApp/Echo-Music/releases/latest> | none found | site routes Android downloads to GitHub Releases |

DHUN currently has only its own GitHub rolling release. It must not display
F-Droid, store or “stable release” badges based on another project's pattern.

### B.4.10 Repository licence snapshot

Verified from each canonical repository on 2026-10-08. These are **code
repository licences**, not permission to reuse site copy, branding, screenshots,
album art, fonts or other third-party assets.

| Project | Canonical repository licence | Evidence boundary |
|---|---|---|
| Spotube | BSD-4-Clause | root `LICENSE` self-identifies as BSD-4-Clause; GitHub's API currently returns `NOASSERTION`, so preserve the actual notice rather than relying on API labelling |
| RiMusic | GPL-3.0 | canonical repository licence metadata/file; archived repository |
| InnerTune | GPL-3.0 | canonical repository licence metadata/file |
| ViMusic (original) | GPL-3.0 | canonical `vfsfitvnm/ViMusic` repository; archived |
| OuterTune | GPL-3.0 | canonical `OuterTune/OuterTune` repository |
| Harmony Music | GPL-3.0 | canonical repository; README says it is no longer maintained |
| Moosync | GPL-3.0 | canonical `Moosync/Moosync` repository |
| Echo Music | GPL-3.0 | canonical `EchoMusicApp/Echo-Music` repository |

This survey does not make any of those projects' visual assets available to
DHUN. W3 still requires asset-by-asset provenance and permission.

### B.4.11 Cross-project synthesis

| Pattern | Observed | DHUN rule proposed |
|---|---|---|
| Real UI screenshots | Strong on Spotube, RiMusic, InnerTune, ViMusic, OuterTune and Echo | Do not build an image-led landing page before the asset/licence gate |
| GitHub Releases | Common primary or fallback channel in all eight | Link the rolling `test` release, but call it **test / unverified**, never stable |
| F-Droid / Izzy | Common for mature Android-only clients | Do not show badges for channels DHUN does not have |
| “Unofficial” notice | Usually buried in a disclaimer or FAQ | DHUN should be more candid: concise notice near the first download and full status on `/download` |
| “May break” notice | Rare; closure/maintenance notices appear only after projects stop | Do not wait for failure. State the InnerTube maintenance risk while the project is alive |
| Project lifecycle | RiMusic, ViMusic, OuterTune and Harmony expose archived/inactive state | Site content must derive release/status labels from checked facts, not evergreen copy |
| Screenshots as proof | More persuasive than long adjective lists | One screenshot per major promise; no screenshot means footnote or schematic label |

## B.5. DHUN's content truth contract

Every public claim must be one of:

1. **demonstrated** by a current screenshot or visible source/release fact;
2. **qualified** by a directly adjacent footnote;
3. **omitted**.

Candidate claims and their required qualifiers:

| Candidate site claim | Repository evidence | Required public wording boundary |
|---|---|---|
| No sign-in, no cookies, no PO tokens | ADR-001 / ADR-003 and current extraction doctrine | Say “No sign-in required”; do not claim the upstream service can never gate access |
| Free, GPL-3.0, no app ads, no telemetry | `LICENSE`, MASTER_PROMPT, current architecture | Keep the static site analytics-free too, or the no-telemetry message becomes ambiguous |
| Android 7.0+ | both modules `minSdk = 24`, CI `NewApi` gates | Footnote: API 24–25 hardware acceptance is still open |
| Windows desktop client | Compose Desktop/libVLC and rolling MSI | Footnote: unsigned test build; system VLC required; S3 native-surface checks open |
| Linux/macOS via JVM | MASTER_PROMPT platform wording | Do not put in a “supported platforms” strip until packaged and hardware-verified |
| Offline downloads | ADR-006 + merged UI/engine | Demonstrate only after the S3 offline round is accepted |
| Synced lyrics | LRCLIB → YTM → cache and user report | No accuracy/coverage percentage; availability varies by track/source |
| Widgets / tray / SMTC / jump lists / EQ | merged code | Separate “available” from “hardware-verified”; do not bundle them into one unchecked claim |
| Streaming from YouTube Music | accepted architecture | Prominent `Unofficial; upstream changes can interrupt playback` notice |
| “Stable” / “release” | not supported yet | Forbidden until S3/S6 close and a real versioned release is published |
| Audio quality number | no measured contract | Forbidden; do not say lossless, 256 kbps, hi-res, or copy Volta's FLAC metric |

Proposed concise notice (wording requires user approval):

> **Unofficial test build.** DHUN is not affiliated with YouTube or Google.
> Playback relies on upstream interfaces that can change without notice.

That notice should be visible on the landing page near the first download CTA,
not only in a footer. `/download` should carry the expanded prerequisites,
checksum instructions, signing warning, current S3 state and stable GitHub
source/release links.

## B.6. Option-A experience (not selected; retained as fallback/reference)

### B.6.1 Audience and job

Primary visitor: someone deciding in under a minute whether to try DHUN on
Android or Windows. Secondary visitor: a developer checking source, licence and
risk posture. The site is not a replacement for engineering documentation.

### B.6.2 Exactly three routes

All routes are static and live under the repository base path `/DHUN/`:

1. `/DHUN/` — promise, proof, key features, status footnote, two CTAs;
2. `/DHUN/download/` — Android/Windows test artifacts, checksums,
   prerequisites, warnings and install guides;
3. `/DHUN/features/` — traceable feature matrix with platform and verification
   status.

Do not add blog, account, pricing, app dashboard or web-player routes in v1.

### B.6.3 Landing-page sequence

A DHUN-specific adaptation of the useful Volta structure:

1. skip link and compact navigation (`Features`, `Download`, `GitHub`);
2. eyebrow: `OPEN SOURCE · NO SIGN-IN · NO APP ADS`;
3. short H1 (copy not decided; avoid unprovable superlatives);
4. one sentence naming Android + Desktop and unofficial YouTube Music source;
5. exactly two CTAs: **Download test build** and **View source**;
6. verification strip, not a platform boast: `ANDROID 7+* · WINDOWS* · GPL-3.0`;
7. one real hero composition (Android + Windows screenshots) or clearly labelled
   CSS schematics;
8. proof row: `NO ACCOUNT`, `OFFLINE*`, `SYNCED LYRICS*`, `DESKTOP CLIENT*`;
9. adjacent honesty footnotes;
10. three demonstrated feature sections: private-by-default entry, offline
    library, lyrics/player immersion;
11. secondary feature cards: widgets, tray/native controls, EQ, queues;
12. unofficial/upstream-risk panel;
13. closing repetition of the same two CTAs.

The rolling release must be labelled **Rolling UNVERIFIED development build**,
matching GitHub. The site must not hide that status to improve conversion.

### B.6.4 Visual direction

- dark, artwork-led, but not a clone of Volta;
- DHUN's existing Material 3 / artwork / translucent-surface vocabulary;
- system font stack for v1 unless a font licence and self-hosting plan are
  reviewed first;
- no remote font, analytics, advertising, autoplay audio/video or tracking
  pixel;
- restrained motion with `prefers-reduced-motion` parity;
- screenshots carry meaningful alt text; decorative frames carry empty alt;
- readable without backdrop blur or JavaScript.

## B.7. Asset and licence gate (W3)

No image or font lands before its rights are recorded.

### Preferred screenshot capture set

Capture during S3 rather than scheduling a competing device session:

- Android portrait: Home with mini-player, Full Player + synced lyrics, Library
  downloads;
- Android landscape: Home/Search/Library with compact docked mini-player (also
  round-4 acceptance evidence);
- Windows: standard window Home, fullscreen with compact docked mini-player,
  Full Player/lyrics, tray or settings only if it actually passed its check.

For each file record: device, OS, app commit/release digest, capture date,
screen, crop/edits, and licence/provenance.

### Content-safety constraint

Live music screenshots often contain copyrighted album artwork, artist photos,
titles and lyrics. Do **not** commit them merely because the app displayed them.
Use one of:

- a project-owned test fixture with user-created or CC0 artwork and synthetic
  metadata;
- artwork with a licence explicitly permitting redistribution, recorded beside
  the asset;
- a schematic CSS mockup labelled as an illustration.

Blurring a third-party cover is not automatically a licence. Lyrics are also
copyrighted content; a screenshot should use project-authored test text unless
permission is established.

### Asset acceptance

- no secrets, account identity, notification content or personal library data;
- no third-party trademark used as endorsement;
- lossless source retained outside Git if large; optimized WebP/AVIF plus
  fallback committed only when sizes are reasonable;
- width/height declared to prevent layout shift;
- dark and small-screen readability reviewed.

## B.8. Option-A generator and deployment analysis

This section applies to the static marketing-site fallback. **Astro is not a
web-player architecture decision.** Option B must follow ADR-008 and its
browser feasibility evidence before choosing any client stack.

### B.8.1 Candidates

| Approach | Local verifiability | Fit | Cost/risk |
|---|---|---|---|
| Plain HTML/CSS | excellent | fine for one page | repeated nav/footer and three-route drift without templates |
| Eleventy | excellent with Node/npm | very small static generator | less built-in asset/routing structure; still a valid fallback |
| **Astro** | excellent with Node/npm | static-first components, route structure, image pipeline, minimal client JS | dependency surface larger than plain HTML; must pin lockfile and keep zero-JS defaults |
| Jekyll | poor here (Ruby/Bundler absent) | native legacy Pages convention | locally CI-blind; reject for this environment |
| Hugo | poor here (binary absent) | fast static output | locally CI-blind; reject for this environment |

**Recommendation, not decision:** Astro in `website/`, configured for static
output and `base: "/DHUN"`. It best fits three content routes and reusable claim
/ footnote / feature components while shipping no framework runtime by default.
Eleventy is the fallback if the dependency audit finds Astro disproportionate.

### B.8.2 Repository boundary

Proposed layout after W0–W3 pass:

```text
website/
  package.json
  package-lock.json
  astro.config.mjs
  public/
  src/
    components/
    layouts/
    pages/{index,download,features}.astro
```

The website gets its own dependency files and tests. Do not add npm dependencies
at repository root and do not mix generated `dist/` into Git.

### B.8.3 Pages migration

Current Pages mode is `legacy`, source `main:/`, and serves the README. W4 would
replace that with a dedicated GitHub Actions Pages deployment **only after one
real page builds locally**. Proposed isolated workflow:

1. checkout;
2. setup Node from pinned major;
3. `npm ci` in `website/`;
4. format/lint/content-contract tests;
5. `npm run build`;
6. link/base-path check against `website/dist`;
7. `upload-pages-artifact`;
8. `deploy-pages` only from `main`.

It must be a standalone workflow (for example `website-pages.yml`), not a job in
app `ci.yml`; a site failure must not redden Kotlin CI or block rolling test
artifacts. Pull requests build and inspect the static output but do not deploy.
The workflow's action versions must avoid the Node-20 warning currently emitted
by GitHub's auto-created legacy Pages workflow.

Migration acceptance includes checking both:

- <https://99ggprooo00-code.github.io/DHUN/> (canonical);
- the user-approved README link target.

A rollback is switching Pages back to `main:/`; because W4 is static and no
application target changes, rollback does not touch Android/Desktop releases.

## B.9. Staged work and gates

The order is intentionally after or alongside the remaining hardware capture,
never instead of S3.

### W0 — Decide

- ✅ User selected **Option B: browser player**.
- ✅ Real S3 screenshots; product-first tone with the warning lower on the first
  viewport; canonical github.io URL; English-only v1; correct README now.
- ✅ ADR-008 written and separately **accepted for B1 feasibility only**.
- ✅ Dependency-free B1 candidate implemented in `web-spike/`; static contract
  and JavaScript syntax pass locally (55-test suite).
- ✅ B1 merged (PR #136 → `2a20024`), deployed to the canonical origin, and run
  manually by the user.
- ⛔ **B1 result: BLOCKED** for the available Brave/Chromium-family run;
  cross-browser coverage unavailable. No production site/player scaffold,
  backend/proxy, extraction change or B2 stack is authorized.

**Gate:** W0/B0 complete; B1 closed as BLOCKED. W1–W6 for the unselected
Option-A fallback stay unstarted; B2 is a separate user decision.

### W1 — Diagnose Pages and front-door link

- ✅ Diagnose actual Pages settings and canonical URL.
- ✅ Disprove the empty-artifact/source-misconfiguration hypothesis.
- ✅ Applied the user-approved README correction to the canonical `-code` URL.
- ⏳ Decide migration/rollback steps only after the B2 decision determines what,
  if anything, is deployable. B1 has reported BLOCKED and settled nothing about
  deployment architecture.

**Gate:** canonical URL and README now agree, and the deployed probe lives at
`/DHUN/web-spike/`; any product deployment architecture remains blocked on an
explicit B2 user decision (which may be “stop”).

### W2 — Comparable research

- ✅ Spotube, RiMusic, InnerTune, ViMusic, OuterTune, Harmony Music, Moosync and
  Echo Music reviewed above with canonical URLs, screenshot/distribution and
  risk-framing findings.
- ⏳ Recheck any source that changes before implementation; several projects are
  archived or have impersonating third-party sites.

**Gate:** user approves the DHUN content posture, not merely the visual reference.

### W3 — Assets and claims

- ✅ User selected real S3 screenshots rather than CSS mockups.
- Capture the agreed hero path during S3.
- Record provenance/licence before committing each asset.
- Build a final claim-to-evidence ledger from current main and the latest S3
  report.
- Reject any screenshot that embeds unlicensed music art/lyrics.

**Gate:** at least one legal, current Android visual and one Windows visual, or
explicit approval for labelled CSS schematics.

For the selected Option B, the ADR-008 B1 probe is implemented, deployed and
run; the canonical run is **BLOCKED**, so no browser playback visual exists and
none may be implied. W4–W6 below describe only the unselected static Option-A
fallback, which is not authorized for implementation.

### W4 — Scaffold (Option-A fallback only)

- Create `website/` with the selected static generator.
- Add its own PR build workflow.
- Implement one real landing page with actual copy, skip link, two CTAs,
  canonical metadata, OG metadata and `/DHUN` base-path checks.
- Change Pages deployment only after local and PR checks pass.

**Gate:** one content-complete page at the canonical URL; no app CI regression.

### W5 — Build out (Option-A fallback only)

- Add `/download/` and `/features/`.
- Generate artifact links from stable rolling-release URLs, never scrape an
  untrusted mirror.
- Keep checksums/instructions and verification labels explicit.
- Add structured data only where it describes current facts.

**Gate:** every visible product claim maps to the claim ledger.

### W6 — Accept (Option-A fallback only)

- keyboard-only and screen-reader landmark review;
- WCAG AA contrast and visible focus;
- reduced-motion check;
- 320px, common phone, tablet and desktop responsive review;
- Lighthouse accessibility/performance/best-practices run with recorded version;
- broken-link and `/DHUN` base-path crawl;
- no cookies, analytics, remote fonts or unexpected requests;
- user wording and visual sign-off.

**Gate:** user approval. Site acceptance does not close S3 or S6.

## B.10. W0 decisions and the remaining approval

W0 answers are complete:

1. **Product:** browser player (Option B).
2. **Hero evidence:** real screenshots captured during S3.
3. **Voice:** product-first headline, with the unofficial/may-break notice lower
   on the first viewport.
4. **URL:** `https://99ggprooo00-code.github.io/DHUN/`.
5. **Language:** English-only v1.
6. **README now:** corrected to the canonical `-code` URL.

**Architecture state:** ADR-008 was accepted for B1 only, and B1 is now closed
as **BLOCKED** — deployed at the canonical origin, metadata readable, player
request blocked before a readable response, media/audio never reached, Firefox
and Safari unavailable, and no sanitized JSON/screenshot/trace supplied, so the
failure mode must not be narrowed. No production Web claim is approved, and
nothing downstream is authorized. Before choosing **B2.1** Kotlin browser,
**B2.2** TypeScript, **B2.3** a separately approved backend/proxy, or **B2.4**
stop/fallback, the user must make an explicit B2 decision; “stop” is a
legitimate and currently acceptable outcome. B1 is not to be restarted, and the
static Option-A fallback is not to be implemented as if it had been selected.
