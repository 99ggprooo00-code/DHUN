# 21 — Marketing site: one request per route, and claims that are measured instead of argued

Session `arena/9b791057-dhun`, 2026-10-08. Branch point and `main` at boot:
`505c3d59e66248f15bd29586f672d5b5b4b9bfac` (PR #138 merge — the site itself,
recorded in `docs/verification/20-marketing-site.md`).

Status vocabulary is the one `.ai/WEBSITE_PLAN.md` Part A fixes: **verified**
read from a tool output · **expected** plausible but unchecked · **not
verified** explicitly unchecked. "It builds locally" is not "it works in a
browser": this session's whole point is that the second claim had never been
measured, and the first real browser run proved it.

## Addendum — the redirection: the site became a product site, not a download page

Mid-session the direction changed, and it is recorded here rather than folded
quietly into the work: **the website is not the distribution channel.**
`/download/` was deleted (page, stylesheet, built route) and **`/ui/`** took its
place. The rolling pre-release is still one link away, as its release *page*.

What that means concretely, all of it locally verified this session:

| Check | Result | Where |
|---|---|---|
| Routes | `/`, `/features/`, `/ui/` + a real 404 — the three-route shape holds | `website/src/*.njk` |
| Quality gates (now 18) | `OK: 18 quality checks pass on website/dist.` | `scripts/website_quality.py` |
| Honesty contract | `OK: 4 built page(s) pass the honesty contract (8 forbidden-claim rules, 3 required caveats, 5 digest rules).` | `scripts/website_claims.py` |
| Unit tests | `Ran 173 tests in 0.378s` → `OK` | `python3 -m unittest discover -s scripts -p 'test_*.py'` |
| Markup validity | `html-validate "dist/**/*.html"` → no output | local, pinned 11.16.2 |
| Minification proof | `OK: every built file matches a fresh unminified build ignoring whitespace (39902 bytes saved by minification).` | `website/tools/verify-minify.mjs` |
| Route weights, uncompressed (HTML+CSS as served) | `/` 51,280 B · `/features/` 52,757 B · `/ui/` 53,941 B — headroom 10,160 / 8,683 / 7,499 B under the 60 KB budget | `website/budget-baseline.json` |
| Budget ratchet | baseline regenerated deliberately: `/download/` dropped, `/ui/` added, `/features/` +6,521 B for the two trust FAQs and the expanded catalogue | `website/budget-baseline.json` |

**The new rule, mutation-proven** (`/tmp/mut2`, real `dist` untouched):

```
direct .apk link:         exit=1 :: links a downloadable artifact: href="…/releases/download/…
releases/download path:   exit=1 :: links a downloadable artifact: href="…/releases/download/test/thing"
sha256sum instruction:    exit=1 :: carries installation/verification instructions (‘sha256sum’)
sideload instruction:     exit=1 :: carries installation/verification instructions (‘Sideload’)
restored:                 exit=0 :: OK: 18 quality checks pass
```

`distribution_boundary_violations` bans two things: a direct link to a release
asset, and installation/verification wording on any page. The claims test that
*required* the download page to link all eight rolling assets was **inverted**
into one that requires the opposite — no asset link anywhere, and the release
page still reachable — because deleting it would have been weakening the
contract, and the contract still holds; its subject changed.

**One stale claim was found and corrected.** The negative list said "no Android
equaliser yet — it is an open item", while the tree ships one:
`shared/src/commonMain/kotlin/dev/dhun/player/equalizer/EqualizerBands.kt`
(`COUNT = 10`, 60 Hz–16 kHz, ±20 dB, libVLC geometry), wired to
`android.media.audiofx` on Android and to libVLC on desktop, with the UI in
`ui/settings/SettingsScreen.kt` (presets, preamp, per-band sliders). The feature
catalogue now claims it and the negative list no longer denies it.

**Two traps closed, both of which failed quietly before:**

1. `eleventy.config.js` filtered a page's `cssModules` against its allowlist, so
   an unknown name was *dropped* — `/ui/` shipped with no stylesheet and only the
   class-coverage rule noticed. The filter now iterates what the page asked for
   and throws on anything unknown; `download` left the list and `ui` joined it.
2. Nested `<section>` elements break the per-section traceability scan (the
   non-greedy match consumes the outer section up to the first inner close), so
   the surface walkthrough is five top-level sections rather than one wrapper
   section with five inside it.

### What is not verified about `/ui/`

Everything above is static analysis. **`/ui/` has never been rendered in a
browser** — no browser exists in this sandbox, and the browser job runs on the
pushed head. Its overflow, touch-target, contrast and axe results belong to that
run, and are reported in the PR comment for **PR #139**, not predicted here.

## What changed

**Tier A — one HTTP request per route.** Each route's stylesheet is inlined
into the page (`inlineCss` in `website/src/_includes/base.njk`); nothing else is
fetched. The trade-off is recorded rather than hidden: inlined CSS is re-sent on
every navigation and cannot be cached across routes — at ~20 KB of *source* CSS
that costs a repeat visitor a few hundred bytes of gzip per route, and buys a
first visit zero blocking round-trips on a static host with no HTTP/2 push.
`scripts/verify-minify.mjs` proves the minifier is lossless (it re-parses the
minified CSS and compares the declaration list), and a weight ratchet
(`budget_ratchet_violations`, tolerance 5 %) fails the build when a route grows
without a deliberate baseline update.

**Tier A — real byte wins.** The 24 icons are one `<symbol>` sprite referenced
by `<use>`; the four mockups share the sprite. There is no client JavaScript, no
webfont and no third-party runtime asset to remove, so the remaining wins were
copy-level, not tricks: the previous session's Lighthouse `cache-insight` cannot
be satisfied on GitHub Pages (it sets `Cache-Control` itself) and is recorded as
a host constraint instead of being chased.

**Tier B — browser-measured, in the `browser` job only.** `website/tests/browser.mjs`
(Playwright, `@axe-core/playwright`) drives the built site at 320×568, 360×800,
390×844, 768×1024, 1280×800 and 1440×900 and checks: no horizontal overflow
(`scrollWidth <= innerWidth + 1`, plus a pass that names the *spilling* element,
not just the document), 44×44 touch targets, keyboard skip link and visible focus
ring, no console errors, computed-style contrast (≥ 4.5:1 body, ≥ 3:1 large, in
dark and light) and `prefers-reduced-motion`. Screenshots are uploaded as CI
artifacts and never committed. A separate `lighthouse` job runs three samples per
route and gates on the **median** (a11y ≥ 0.95, perf ≥ 0.90). A `served` job
smoke-tests the bytes a visitor actually gets, and only on `main` after deploy.

**Tier C — depth where the site makes claims.** `/download/` now carries
per-OS verification commands, the upgrade/uninstall lifecycle, what the test
keystore does and does not mean, and the cost of "no stable release".
`/features/` explains the extraction chain in five stages and how it breaks
(own InnerTube client → tokenless identity waves → desktop-only yt-dlp →
NewPipeExtractor **v0.26.5**, pinned and watched → a daily live probe), plus what
is desktop-only. `/` states briefly and factually why there is no sign-in.

**Tier D — traceability.** Every `<section>` in the built pages carries a
citation comment naming the file it came from; a rule fails the build when a
section loses it. The mockup ↔ `.ai/WEBSITE_PLAN.md` §9 backlog table is
machine-read (4 `shipped`, 2 `planned`) so a mockup cannot drift from the plan,
and a stale-fact rule rejects version strings, build numbers and dates in copy —
the rolling `test` assets change on every push, so any baked number is a lie with
a shelf life.

## Local evidence (this session, output pasted from the tool)

| Check | Result | Where |
|---|---|---|
| Build | `[11ty] Copied 2 · Wrote 5 files in 0.19 seconds (v3.1.6)` then `minified: saved 33682 bytes` | local, Node v22.22.3 |
| Quality gates | `OK: 17 quality checks pass on website/dist.` | `scripts/website_quality.py` |
| Honesty contract | `OK: 4 built page(s) pass the honesty contract (8 forbidden-claim rules, 3 required caveats, 5 digest rules).` | `scripts/website_claims.py` |
| Unit tests (all rules, mutation-exercised) | `Ran 167 tests in 0.344s` → `OK` | `python3 -m unittest discover -s scripts -p 'test_*.py'` |
| Markup validity | `html-validate "dist/**/*.html"` → no output (0 problems) | local, pinned 11.16.2 |
| Route weights (uncompressed, HTML+CSS on the wire) | `/` 49,772 B · `/features/` 46,236 B · `/download/` 30,442 B | `website_quality.py` |
| Against the 60 KB/route budget | headroom 11,668 B · 15,204 B · 30,998 B | `website_quality.py` |
| Inlined CSS per page | 20,170 B (`/`, `/features/`) · 12,602 B (`/download/`) | `website_quality.py` |
| Budget ratchet | baseline regenerated to the numbers above; deltas vs the previous baseline **+29 B / +29 B / −47 B** (all far inside the 5 % tolerance) | `website/budget-baseline.json` |
| Requests per route | 1 (HTML), + 0 | `test_every_route_is_a_single_request` |

The ratchet baseline was updated **after** these numbers were read, and the
deltas are recorded here so the update is auditable: the only growth this session
is 29 bytes on the two routes carrying the phone mockup, from the comment-free
rules that fix the defect below.

### Copy hygiene is now a rule, not a review habit

The first browser-adjacent read of the *source* (not a browser) showed the
download page shipping a literal Markdown backtick (`` `sha256` ``) and a
`<code>` tag that had been **escaped** into visible text
(`&lt;code&gt;FOREGROUND_SERVICE_DATA_SYNC&lt;/code&gt;`). Both are valid HTML,
so every existing gate — weight, links, claims, traceability, `html-validate` —
correctly said nothing. The new `copy_hygiene_violations` rule fails on a literal
backtick, escaped markup, a dead `href="#"`/`javascript:` link and placeholder
wording, and it is mutation-proven:

```
literal backtick:            exit=1 :: - /download/: literal backtick #1 in the copy: …sha256sum: a digest, two spaces, the fil…
escaped code tag:            exit=1 :: - /download/: escaped markup is rendered as text: &lt;code…
dead link:                   exit=1 :: - /download/: a link goes nowhere: href="#"
lorem ipsum:                 exit=1 :: - /download/: placeholder wording ‘Lorem ipsum’
negated 'coming soon':       exit=0 :: OK: 17 quality checks pass
restored:                    exit=0 :: OK: 17 quality checks pass
```

The last two lines are the point: `/features/` says the missing features are
"absent, not 'coming soon'", which is the opposite of a promise, so the rule
imports `website_claims.py`'s negation-cue test rather than inventing a second
one. Five unit tests hold the rule down, and the old ones were not touched: the
suite went 161 → 167.

## CI evidence

**Run 37802245177 (head `0570377`) — the first run that measured anything.**
Lighthouse `/`: three samples 98/100/100 → **median 100** on performance,
accessibility, best-practices and SEO; FCP = LCP = SI **1106 ms**, TBT **0 ms**,
CLS **0.000**, **49.9 kB in 2 requests**. The same run's browser job reported the
defects this session then fixed — `a.wordmark` 90.9×28 (all routes), footnote
refs 7.1×14 / 7.4×14 / 6.4×14, footer prose links mis-flagged as standalone
targets, and `/download/` overflowing at 360 and 320 with
`documentElement.scrollWidth 444`.

**Run 37803215249 (head `01e28a1`) — the run this document's fixes answer.** The
`build` job **succeeded** with the drift notice on record; the browser job
**failed**, and this time it named every offender instead of aborting:

| Annotation | Measurement |
|---|---|
| `contrast — 2 problem(s)` | `span.winbtn.winbtn--close "×" 3.99:1 < 4.5:1` on `/` and `/features/` (dark) |
| `skip link is not visible when focused` | `rect top=-48 left=16` |
| `overflow — 13 problem(s)` | `/download/`: `documentElement.scrollWidth 444 > innerWidth 320/360/390`; `/` and `/features/`: mockup internals (`div.tabbar 106>69`, `div.tabs 88>53`, `ul.rows 72>53`, `div.mini 70>57`) |
| notices | touch targets **smallest standalone 44 px** on every route × viewport; contrast dark lowest 3.99:1, light lowest 4.6:1; reduced motion honoured; 51 icons, smallest bounding box 8 user units |

Those annotations are the deliverable the previous session could not produce:
before, a failing run printed one line and exited. The three remaining defects
were then traced to their causes rather than guessed at:

- **The mockups were collapsed.** `.mock` is a grid item inside a
  `place-items: center` container, so `width: min(100%, 300px)` on `.device`
  resolved against a *shrink-to-fit* box that was itself sized from its
  min-content — about 85 px wide. The phone drew as a sliver and spilled its own
  tabbar, mini-player, tabs and rows. Fixed by giving the figure an explicit
  `width: 100%` so the percentage chain has a definite containing block.
- **Two long `<code>` tokens set the intrinsic widths.** On `/features/`, the
  only token ≥ 24 characters is the 40-character
  `.github/workflows/extraction-health.yml`; on `/download/`, the named 409 px
  paragraph is exactly the 50-character `Get-FileHash .\dhun-test.apk
  -Algorithm SHA256` command (50 chars × ~8.2 px ≈ 410 px). `code` now carries
  `overflow-wrap: anywhere`, which — unlike `break-word` — also reduces
  min-content width, which is where the overflow came from.
- **The `×` close glyph was `--error` on `--surface-highest` = 3.99:1.** It is
  now the same ramp lightened to **5.83:1** (computed by hand from the two token
  values, then asserted by the browser job's contrast pass).
- **The skip-link failure was the test, not the site.** The 120 ms transition
  meant the first skeleton frame was measured at `top=-48`. The check now waits
  for the link to settle (≤ 1500 ms) before measuring, and still fails if it
  never settles.

## What is *not* verified

- **The four fixes above are reasoned and locally gated, not browser-verified.**
  No browser binary exists in this sandbox (checked: no `chromium`, no `firefox`),
  so the CSS cannot be rendered here. Their verdict belongs to the `browser` job
  of the run for the head that carries them; by this session's merge rule that
  verdict is recorded in the PR comment for **PR #139** rather than predicted
  here.
- **A headless Chromium is not a device.** Six viewports measured by one engine
  is a floor, not an accessibility audit: no Firefox, no WebKit, no Safari, no
  screen reader, no real touch hardware, no Windows High Contrast mode. See
  `.ai/KNOWN_LIMITATIONS.md` for the standing entry.
- **Lighthouse numbers are synthetic.** The 1106 ms FCP/LCP came from a runner in
  a GitHub datacenter hitting a local `python3 -m http.server`; it is evidence
  that the page is small and unblocked, not a field measurement of the public
  URL.
- **The public URL was never fetched from this sandbox** — a Pages 404 was
  reported earlier because the repository's Pages source is still *legacy
  branch/Jekyll* (`build_type: legacy`). Switching the source is a repository
  setting, out of this session's scope; the `served` job exists so that the real
  bytes are smoke-tested once the switch happens.

## Decisions recorded

1. **Inline CSS, per route** (above): chosen over a shared `styles.css` for
   first-visit latency, with the cache cost written down and the byte cost
   ratcheted.
2. **The honesty gates stay tests, never prose.** Every forbidden-claim,
   caveat, digest, traceability, drift, stale-fact, crawlability and copy-hygiene
   rule has a mutation that must fail and a build that must pass — each one is
   mutation-proven in `scripts/test_website_quality.py`.
3. **Copy correctness is a gate too.** A rule that cannot see a mistake a visitor
   can read is not a rule; that is why copy hygiene exists rather than a note in
   a checklist.
