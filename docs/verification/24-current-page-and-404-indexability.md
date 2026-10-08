# 24 — The header marks the page you are on, and the 404 asks not to be indexed

Session `arena/af3e7f66-dhun`, 2026-10-08, second phase of the same session (the
first is `docs/verification/23-per-route-css-pruning.md`). Status vocabulary is
the one `.ai/WEBSITE_PLAN.md` Part A fixes: **verified** = read from a tool output
in this session · **expected** = plausible but unchecked · **not verified** =
explicitly unchecked. No browser exists in this sandbox, so every rendered
statement below is CI-only and marked as such.

## What was wrong

- **No page marked itself.** `aria-current` appeared nowhere in the built site
  (grep over `website/dist/**/*.html` returned no match), so the header navigation
  never said which of its destinations the visitor was reading. Screen-reader
  users get link list context from `aria-current`; sighted users get it from the
  styling that goes with it. Neither existed.
- **`/404.html` was offered for indexing.** It carried no `<meta name="robots">`
  at all. It is not in `sitemap.xml` (already asserted), and Pages serves it with
  a 404 status, but a soft-404 path can still surface it and nothing in the build
  said otherwise.

Both are *absence* defects — no rule asked the question, so no mutation could have
found them (root cause in `.ai/DEBUG_LOG.md`).

## What changed

| Change | File |
|---|---|
| `aria-current="page"` on the wordmark for `/`, and on the matching nav item for `/features/` and `/ui/` | `website/src/_includes/base.njk` |
| The mark drawn twice: a pill (background + text colour) and an accent underline that survives Windows High Contrast | `website/css/base.css` |
| `<meta name="robots" content="noindex">` on the 404 only, via front matter | `website/src/404.njk` |
| New check `navigation_state_violations` (27 checks now, was 26) | `scripts/website_quality.py` |
| `noindex` asserted in both directions | `crawlability_violations` |
| New token pair `--text` on `--surface-variant` (the pill) | `CONTRAST_PAIRS` |
| New browser check `current page` incl. a forced-colours pass | `website/tests/browser.mjs` |
| `currentPageProblem()` and `markerPerceivable()` with must-pass/must-fail cases | `website/tests/rules.mjs`, `rules.test.mjs` |
| 6 new Python cases (marker missing / duplicated / misdirected / on the 404 / unstyled / background-only) | `scripts/test_website_quality.py` |

## Measured (this session, from tool output)

| Route | Before phase 2 (B) | After (B) | Δ | Inlined CSS (B) |
|---|---|---|---|---|
| `/` | 51,311 | **51,601** | +290 | 20,650 |
| `/features/` | 48,019 | **48,309** | +290 | 16,029 |
| `/ui/` | 49,508 | **49,798** | +290 | 19,315 |
| `/404.html` | 12,794 | **13,102** | +308 | 7,521 |

The +290 B per route is the cost of the feature (270 B of CSS that survives
pruning on every route, 20 B of attribute), recorded deliberately in
`website/budget-baseline.json`, which was regenerated in the same commit. After
phase 1 and phase 2 together the site is still smaller than at boot: `/` −1,953 B,
`/features/` −6,572 B, `/ui/` −6,465 B, `/404.html` −4,339 B against the committed
boot build measured via `git show HEAD:website/dist/... | wc -c`.

| Gate | Result |
|---|---|
| `npm run build` | `pruned 4 page(s): 27693 bytes of CSS no page can use` · `minified: saved 44426 bytes` |
| `npm run verify:minify` | `OK: every built file matches a fresh unminified build ignoring whitespace (44520 bytes saved by minification).` |
| `npm run test:rules` | `# tests 29 # pass 29 # fail 0` (12 rule cases + 17 pruner cases) |
| `npx html-validate "dist/**/*.html"` | exit 0, no output |
| `python3 scripts/website_quality.py website/dist` | `OK: 27 quality checks pass on website/dist.` |
| `python3 scripts/website_claims.py website/dist` | `OK: 4 built page(s) pass the honesty contract (…)` |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | `Ran 257 tests in 7.903s` → `OK` (250 before this phase) |

## Mutation proofs

| Mutation (on a copy of the real build, or as a unit case) | Verdict |
|---|---|
| Remove `aria-current="page"` from `/features/` | `test_a_page_without_a_marker_fails`: `/features/: expected exactly one aria-current="page", found 0` |
| Add a second marker (on the wordmark of `/ui/`) | `test_a_marker_on_two_links_fails`: `found 2` |
| Point the marker at `/ui/` from `/features/` | `test_a_marker_pointing_at_another_route_fails` |
| Put a marker on `/404.html` | `test_a_marker_on_the_404_fails` |
| Delete every `[aria-current="page"]` rule from `/`'s CSS | `no rule in this page's own CSS styles [aria-current="page"]` |
| Keep only the pill, drop the underline rule | `the current-page marker is a colour or background only, which Windows High Contrast removes` |
| `currentPageProblem` with 0/2 markers, a wrong href, or an identical sibling signature | 3 must-fail cases in `rules.test.mjs`, all red before the fix |
| `markerPerceivable` with a colour-only mark (no decoration, no frame) | must-fail case: `false` — forced colours erases a colour-only mark |

## Not verified here

- **The rendered mark.** No browser in this sandbox (established in record 23), so
  "the current link looks different from the other links" and "the underline
  survives `forced-colors: active`" are measured only in the CI `browser` job. The
  *decision* half is mutation-proven locally; the *gathering* half (`getComputedStyle`
  fields, the forced-colours emulation probe) is not.
- **Light-scheme contrast of the pill.** The static token check resolves the dark
  `:root` block only; the light scheme is measured by the browser contrast check,
  which walks every text node in both schemes.
- **Crawler behaviour.** `noindex` and absence from `sitemap.xml` are asserted in
  the built HTML; what an actual crawler does with them is not observable from
  here and is not claimed.
