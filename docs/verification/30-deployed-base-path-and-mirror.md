# 30 — The deployed base path, and the interface mirror at `/app/`

Session `arena/90cb6d2c-dhun`, 2026-10-09. Branch point and GitHub `main` at
boot `8b35dcf1c9a6808ed6720ff44179d72bf4e2cc96` (PR #145 merge).

Status vocabulary: **verified** = read out of a tool output in this session;
**not verified** = explicitly unchecked; **expected** = plausible but unchecked.

## The bug: every internal link on the deployed site was dead

The site is a GitHub **project** page: its documents live at
`https://99ggprooo00-code.github.io/DHUN/`, not at the origin's root. The
built pages used root-absolute `href`s (`/`, `/features/`, `/ui/`), which a
browser resolves against the origin — a different, empty site.

| Fact | State | Evidence (this session) |
|---|---|---|
| The live nav 404s | **verified** | `fetch_page https://99ggprooo00-code.github.io/ui/` → GitHub Pages "Site not found" (404); `…/DHUN/` → the real site |
| CI could not see it | **verified** | browser/Lighthouse jobs serve the build at a local origin **root** (where root-absolute links work); the served job's resolver did `f"{base}{href}"` — string join, not URL resolution |

Three independent gates all missed it. That is the incident's real lesson: a
check that serves the artifact at a different URL structure than the host
measures a site that does not exist, and string-joining URLs in a test is a
test of the test.

## What this session changed, and the evidence

| # | Item | State | Evidence |
|---|---|---|---|
| 1 | One `path` value (`/DHUN`) in `website/src/_data/site.js` prefixes every internal link (wordmark, nav, CTAs, footer, 404, `/ui/` surface links) | **verified** | `grep` of the built pages: zero root-absolute internal `href`s; all prefixed |
| 2 | `_resolve_internal` strips the base path before mapping onto the deploy tree, and resolves `/app/…` against `app-web/src` (the mirror's build is a byte copy, so source == deploy) | **verified** | `BasePath` test class (7 tests) incl. dead/valid `/app/` links |
| 3 | New quality rule `base_path_violations`: any internal link without the prefix fails the build | **verified** | mutation: wordmark back to `href="/"` → red on two rules (`missing the /DHUN base path` + `current-page marker points at /, not at … /DHUN/`); green on revert |
| 4 | Smoke test resolves served links against the origin with `urllib.parse.urljoin`, like a browser | **verified** | the fixed check run against the **old** bytes served under `/DHUN/`: `/: served page links /features/, which returns HTTP 404` (the old string-join logic passed the same bytes); pinned by `RootRelativeResolution` (3 tests) |
| 5 | CI serves under the published `/DHUN/` sub-path (browser + Lighthouse jobs) | **CI-verified** | website run **37863859204** (head `8e60298`): all jobs green; Lighthouse `/DHUN/` **100/100/100/100** (samples 95·100·100, median-gated) · `/DHUN/features/` **100/100/100/100** · `/DHUN/ui/` **100/100/100/100`, all `TBT=0ms · CLS=0.000 · requests=1`, 52.0/48.7/50.2 kB; browser job: current-page markers `→ /DHUN/…` on all three routes, touch targets smallest standalone **44 px** at all nine viewports, skip link + Enter-to-`<main>` + focus rings clean |
| 6 | `app-web` deploys at `/app/` through the site workflow (single owner of the Pages artifact, no second workflow); ADR-008 amendment 2026-10-09 (2) records the decision and supersedes B3 row 15 | **verified locally, served-verification pending merge** | local deploy simulation: assembled tree (site + 17-file mirror) served under `/DHUN/` — `/` 52,233 B · `/features/` 48,513 B · `/ui/` 50,127 B · `/app/` 200 (1,747 B shell) · `/app/js/main.js` 200 (27,789 B) · `/app/css/tokens.css` 200 (16,559 B) · `/app/css/app.css` 200 (33,876 B); `OK: 3 served route(s) … and the mirror at /app/ serves the application shell with its CSP intact.` |
| 7 | Primary CTA "See the interface" + 404's button → `/app/`; `/ui/`'s primary CTA → "Open the live interface"; one honest line under the hero CTA (worded around the honesty contract's forbidden claims) | **verified** | `grep` of built pages; claims checker green on the copy (8 forbidden-claim rules) |
| 8 | `browser` job: guarded Playwright pass on `/DHUN/app/` (both schemes, 1280×800 + 390×844): boot, engineering-preview notice, nav, no unhandled errors, no overflow; which catalogue answered is recorded, not gated | **verified — and it did its job** | first run (website run 37864321503, head `6376b6d`) **failed**: `#app` empty, notice absent, 0 nav items, all four viewport/scheme combinations, no unhandled or console error — the app had never booted in a real browser (incident below); green run on the fix head closes this row |
| 9 | `served` job: fetches `/app/` and asserts the shell, `noindex`, strict CSP, and the module + stylesheets over the wire | **verified locally** (item 6); served execution happens after the merge to `main` | `ServedWebApp` test class (5 tests) offline |

### Mutation proofs (break → red → revert → green)

1. Old dist (pre-fix) served under `/DHUN/` through the fixed smoke check →
   red, naming the dead links; the old logic on the same bytes → green.
2. Wordmark mutated back to `href="/"` → red on `base_path_violations` and the
   published-href navigation rule; revert → 30/30 checks green.
3. `/DHUN/app/js/nope.js` as a link → red (`dead internal link` + `names a
   file the build does not ship`); `/DHUN/app/` → resolves.
4. Rename `/app/js/main.js` away in the assembled tree → smoke check red with
   `the page's js/main.js returns HTTP 404`; restore → green.
5. Delete the top-level `boot();` call from `app-web/src/js/main.js` →
   `test_the_entry_module_actually_boots` red; restore → green.

## The incident the new check caught: the mirror never booted

The first real browser measurement of `/DHUN/app/` (website run
37864321503, head `6376b6d`) reported, on all four viewport/scheme
combinations: `#app` empty, the engineering-preview notice absent, zero nav
items — and **no unhandled error and no console error**. The failure mode is
silent by construction: `src/index.html` loads `main.js` as the page's only
script, and `main.js` *defined and exported* `boot` without ever calling it.
A module that exports its entry point but has no top-level side effect loads
cleanly, defines everything, and paints nothing. Nothing previously could see
this — the 61 DOM-stub boot tests import the module and call `boot`
themselves through `tests/helpers/boot-harness.mjs`, the module graph is
valid, there is no error of any kind, and no static check can distinguish
"booted" from "exported". This is exactly the failure class record 29 called
unprovable without a browser, and the first browser run found it.

Fix: a top-level `boot();` in `main.js` (the stub harness's explicit `boot()`
then simply re-renders idempotently — all 61 tests green), plus a Python
contract test in `scripts/test_app_web.py`
(`test_the_entry_module_actually_boots`) that asserts the call exists, so the
regression is caught in the browser-free CI step even before a browser
exists. The follow-up run on the fix head is the first green browser
measurement of the mirror.

### The second finding: five track rows that only overflowed on a phone

With the mirror booting, the next browser run (37872180179) failed on two
findings — and both were the same defect: on the 390×844 viewport, five
`<li>` track rows in the home feed were spilling
(`scrollWidth 491 > clientWidth 350`, identical 141 px on all five; the
desktop viewport was clean). The five were exactly the rows whose
"Artist • Album" subtitle is long. `.dhun-track__title` /
`.dhun-track__subtitle` are `<span>`s, and `overflow: hidden` +
`text-overflow: ellipsis` have no effect on non-replaced inline boxes —
the rule that looked like a working ellipsis was dead CSS, and the
title/subtitle also flowed on one line. Fixed with `display: block` on both
classes (stacking as the Android TrackRow intends; ellipsis engages).
The first finding could not be named by the check ("`li.` spilling" with no
text), so the web app overflow finding now reports the text and the widths,
matching the marketing check's format.

### The evidence channel had to be built before either finding could be read

The runs between the boot fix and the readable record failed with zero
annotations and an empty step summary while their `browser-evidence`
artifacts carried the complete screenshot set — the suite was running to
completion, but nothing of its record survived to be read from this sandbox
(the job log archive and the artifact hosts are outside the egress allowlist;
the check-run API does not expose step summaries). The record now travels on
a channel that is: every finding is written to the step summary and emitted
as an annotation the moment it happens; per-check progress lines; a
process-level crash annotation; a final "script finished cleanly" marker;
and a workflow step (issue #150, `if: always()`, the browser job's
`issues: write` scope) that posts the full step log as a comment. The
record for run 37872180179 — the first readable one — showed the script
finishing cleanly: there was never a process death; the job had been red on
the genuine findings above all along.

## Not verified (this session)

- The **served public origin** at `/app/` — the `served` job runs only after
  the merge to `main`; until then the deployment claim rests on the assembled
  bytes above, which are byte-identical to what the deploy job uploads.
- A **human looking at the mirror**. First-ever browser render is CI's
  Chromium; the captures are CI artifacts (`browser-evidence`), never
  committed (the repository contains no image files on purpose).
- **Audio playback from a browser origin** — B1's finding stands; the labelled
  clock and the on-page notice are the honest state, unchanged by deployment.
- **Check-run annotation retention from this sandbox** — the 6376b6d run's 12
  browser annotations read fine ~15 minutes after completion (and were still
  readable after the branch advanced), but annotations from every later run
  read back as zero through the check-run API even while that run was the
  branch head and even though its step log (read via the issue #150 record)
  shows the `::error`/`::notice` lines were emitted. The mechanism is not
  determinable from this sandbox; the issue #150 step-log record is now the
  authoritative readable channel, and annotations are a bonus, not a
  dependency.
- **Lighthouse/axe scores for the mirror** — deliberately not run (it is a JS
  application, not a marketing route; see `.ai/KNOWN_LIMITATIONS.md`).

## Amendment (2026-10-09, later in the same session): the shipped mechanism

Mid-session, PR #149 merged to `main` carrying a fix for this same bug with a
cleaner mechanism than this record's item 1. This branch absorbed it (merge
`01f66ab`) and the *shipped* design is:

| # | Item (as shipped) | State | Evidence |
|---|---|---|---|
| A1 | Every internal `href` in the `.njk` sources routes through the `sitePath` filter (`website/eleventy.config.js`); the filter is a no-op unless `DHUN_SITE_PATH_PREFIX` is set | **verified** | rooted build: `grep` of built pages shows `href="/"`, `href="/app/"`, `href="/features/"`, `href="/ui/"` — zero prefixes in the committed tree |
| A2 | The committed `website/dist` is the **rooted** build (drift-checked, the target of every local gate); the workflow builds a second, `/DHUN`-prefixed artifact for the Pages deploy | **verified** | two builds of the same sources (env set/unset); the prefixed build emits `href="/DHUN/"` … `href="/DHUN/app/"` |
| A3 | The workflow's "Verify GitHub Pages internal links" step runs against the **assembled** `pages-deploy` (prefixed site + mirror at `app/`), so the CTA's `/DHUN/app/` link is proven to resolve, not just be prefixed | **verified locally, CI-verification pending on the merge head** | local simulation of the exact step: 4 site files pass with every prefixed link resolving; `app/index.html` and `app/js/main.js` present in the assembled tree |
| A4 | The local test artifact (`dhun-site`) is the rooted site + mirror at `/app/`; the browser and Lighthouse jobs serve it at a root and measure `/`, `/features/`, `/ui/` and `/app/` | **verified locally, CI-verification pending** | workflow reads back as wired (YAML parsed, artifact names checked); layout/behaviour is URL-structure-invariant — the prefix is a byte-level href transform |
| A5 | The committed-build rule flips to `root_relative_violations`: internal links must be rooted; a prefixed or protocol-relative link in the tree now fails | **verified** | mutation tests: `href="/DHUN/features/"` and `href="//example.com/x"` fail, rooted links pass; 312/312 Python suite green |

Consequence for this record's items 1 and 3: they described the *first*
attempt (a `path` value spelling the prefix into the committed build and a
matching `base_path_violations` rule). The diagnosis in this record's opening
table is unchanged and is what ordered the fix; the mechanism shipped is
#149's, and the mirror work (items 2, 5, 6) now rides on it: the mirror is
assembled into **both** artifacts, rooted next to the site for local tests and
prefixed at `/DHUN/app/` for the deploy.

The phone-viewport overflow that blocked the mirror's browser gate on the
earlier heads was not layout at all: thirteen nested templates in
`app-web/src/js/views.js` were interpolated without `raw()`, so the
`html` tag escaped their markup and the track-row overflow button rendered
as a text node — the escaped string was the 491 px run. Fixed in `fc0bfe7`
(`raw()` at all thirteen sites, `loadingState`'s `Array.join` coercion
included) with the regression guard `app-web/tests/escaping.test.mjs`
(every view rendered in Node, failing on any escaped angle bracket).
