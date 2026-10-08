# 20 — Marketing site (Option A): build, gates and deployment

Session `arena/fc918d37-dhun`, 2026-10-08. Branch point and `main` at the start:
`ae44c7a74191950c00385f705e9617b3ef71658c` (PR #137 merge).

Status vocabulary is the one `.ai/WEBSITE_PLAN.md` Part A fixes: **verified**
read from a tool output in this session · **expected** plausible but unchecked ·
**not verified** explicitly unchecked.

## What was built

A three-route static marketing site for the applications that already exist —
`/`, `/features/`, `/download/` plus a real 404, `robots.txt` and
`sitemap.xml` — in a new top-level `website/` directory, with its own workflow
`.github/workflows/website.yml`. It ships **no client-side JavaScript**, no
third-party runtime asset and no webfont, and it carries the project's own
GPL-3.0 notice with links to `LICENSE` and `THIRD_PARTY.md`.

**Out of scope by decision, not by omission:** no web player, no PWA, no
`app.`-style property, no backend/proxy, no Kotlin/JS. `docs/decisions/ADR-009-marketing-site.md`
is filed **PROPOSED**; ADR-008 stays closed at its BLOCKED B1 result.

## Local evidence (this session)

| Check | Result | Where |
|---|---|---|
| Build | `npm ci && npm run build` in `website/` → `dist/` in well under a second | local, Node v22.22.3 |
| Determinism | two consecutive builds byte-identical (`diff -r` → no output) | local |
| Route weights, uncompressed | `/` 29,148 B · `/features/` 21,069 B · `/download/` 13,431 B · 404 4,148 B · `styles.css` 21,307 B | local, `stat` |
| Page-weight budget (HTML+CSS ≤ 60 KB/route, JS ≤ 10 KB, asset ≤ 150 KB) | **pass** — worst route `/` = 50,455 B | `scripts/website_quality.py` |
| HTML validity | `npx html-validate "dist/**/*.html"` → **clean, 0 errors** | local, html-validate 11.16.2 |
| Honesty contract | **pass** — 8 forbidden-claim rules, 3 required caveats, 5 digest rules on 4 built pages | `scripts/website_claims.py` |
| Quality gates | **pass** — 10 checks | `scripts/website_quality.py` |
| Token contrast (WCAG AA) | **pass** — 12 pairs, 18.10:1 (`--text`/`--bg`) down to 5.71:1 (`--accent`/`--surface-variant`, non-text floor 3:1) | `scripts/website_quality.py` |
| Minification is lossless | **pass** — a fresh unminified build is identical to `dist/` ignoring whitespace; 12,942 B saved | `website/tools/verify-minify.mjs` |
| Committed build matches a fresh build | **pass** — rebuilding leaves `website/dist` unchanged (`git status --porcelain` → 0 lines) | local |
| Script test suite | **pass** — `python3 -m unittest discover -s scripts -p 'test_*.py'` → **102 tests** (55 pre-existing + 47 new) | local |
| App CI untouched | the site workflow is a fifth workflow; the four app workflows, all Gradle files, `shared/`, `app-android/`, `app-desktop/` are unmodified | `git diff --stat` |

### Mutation proof for the three honesty gates (D5)

Each mutation was applied, the site rebuilt, the gate run, then the file
reverted with `git checkout -- <file>` and the gate re-run. Transcripts from
this session:

| Mutation | Result before revert | After revert |
|---|---|---|
| `DHUN is also available for iOS.` added to the front page | **FAIL** — `forbidden claim (Apple/iOS platform): ‘iOS’` with the surrounding sentence quoted | OK |
| the `data-caveat="borrowed-time"` disclosure removed and its heading reworded | **FAIL** — `required caveat ‘borrowed-time’ is not present` | OK |
| a real SHA-256 pasted into the download page copy | **FAIL** — `baked labelled digest ‘SHA-256: 9665b75f…’` | OK |
| the same SHA-256 injected directly into **built** `dist/download/index.html` | **FAIL** — 2 violations (`baked SHA-256 digest`, `baked labelled digest`) | OK — rebuilding restored the committed bytes exactly |

## CI evidence (read from run annotations — log archives are unreachable)

`website` run **37795271256** on `e6cbb42` — **success**:

| Job | Verdict | Evidence |
|---|---|---|
| Build and check the site | **success** | annotation *"Drift check: website/dist matches a fresh build of website/src"* — the committed mirror and a fresh build agree in CI, not just locally |
| Lighthouse and axe | **success** | scores below |
| Deploy to GitHub Pages | **skipped** | correct: the workflow only deploys on `main` |

Real Lighthouse numbers (check-run annotations, `ubuntu-latest`, Chrome
supplied by the runner):

| Route | performance | accessibility | best-practices | SEO |
|---|---:|---:|---:|---:|
| `/` | 96 | **100** | 100 | 100 |
| `/features/` | 100 | **100** | 100 | 100 |
| `/download/` | 100 | **100** | 100 | 100 |

**The first run on this branch failed, and the failure was real.** Run
**37794857026** on `e496464`: the build job was green, and the a11y job failed
with accessibility **95** on `/features/` and `/download/` (against 100 on `/`).
Root cause: the "traceable source" line and the list markers used `--text-4`
(0.45-alpha white ≈ 3.9:1), below the 4.5:1 body-text floor — and those elements
exist on exactly the two pages that scored 95. Fixed by moving that text to
`--text-3` (7.5:1) and adding a test that fails the build if any `color:`
declaration uses `--text-4` again (`scripts/test_website_quality.py`). The next
run scored 100 on all three routes.

Non-scored performance **insights** the annotations still list on the
`/download/` and `/features/` routes: unused CSS, render-blocking requests,
network dependency tree and cache lifetimes. The last of those is a GitHub Pages
constraint — the site cannot set `Cache-Control` — which the download page says
out loud rather than claiming it was optimised.

**axe-core did not run.** All three `@axe-core/cli` invocations exited 1 without
writing a report
(`axe-core /download/: axe-core CLI did not produce a report (exit 1)`), which is
the driver/browser handshake failing rather than a finding; its output is kept in
the run's `reports/` artifact, which this environment cannot retrieve. So: no
axe number is claimed in either direction, and Lighthouse's accessibility
category (which embeds axe-core rules) is the accessibility evidence that
exists.

## What is **not** verified here

- **CI verdicts for the app workflows on this head**: read in the session's
  finish sequence; the run IDs are in the PR comment.
- **The Pages source switch**: attempted and **blocked** — see below.
- **The Actions Pages deployment is blocked by a permission, not by the site.**
  This session verified that the repository's Pages source cannot be changed
  with the available token: `PUT /repos/99ggprooo00-code/DHUN/pages -f
  build_type=workflow` → **HTTP 403 `Resource not accessible by integration`**.
  The setting therefore stays at the state it was found in
  (`build_type=legacy`, `source=main:/`, `status=built`,
  `html_url=https://99ggprooo00-code.github.io/DHUN/`, `https_enforced=true`),
  and the canonical URL keeps rendering the root `README.md` until the source is
  switched. The one-line fix is in the PR comment; the `deploy` job in
  `website.yml` detects `legacy` and skips the three publishing steps with a
  warning annotation naming that fix, so `main` stays green instead of failing
  on a setting it is not allowed to change.
- **Lighthouse and axe could not run locally** — no `google-chrome`,
  `chromium` or `firefox` binary and no display exist in this sandbox. The
  numbers above come from the CI run; axe did not run there either (above).
- **Published-site identity**: no post-merge verification is possible from
  inside the merging session.
- **Cross-browser rendering**: not tested anywhere. The responsive work is
  reasoned from the CSS and asserted mechanically (breakpoints present, no
  fixed width above 320 px, no `100vw`, clamp() type, 44 px targets); it is
  not a browser measurement.

## Design decisions worth keeping

- **Committed build.** `website/dist/` is committed so app CI step 1 can
  assert the honesty contract against built HTML with Python only — no Node,
  no npm, no network. The site workflow rebuilds and **fails on drift**.
- **Mockups, never fakes.** The repository has no image files; every visual is
  a hand-written CSS device mockup of the real Compose UI, labelled
  "not a screenshot" with an `aria-hidden` visual and a caption naming the
  capture that will replace it (`website_quality.py` enforces the label and
  the backlog id). The front page's lyric snippet is public-domain
  placeholder text, and artwork is a token-coloured gradient.
- **Claims are cited in the markup.** Every claim block carries an HTML
  comment naming its ADR or PR, so a reviewer can trace a sentence to its
  source in the built page.
- **Downloads by URL only.** The rolling `test` release is replaced on every
  push to `main`; a digest printed on a marketing page is wrong within hours,
  so the page links the `.sha256` sidecars instead, and a rule fails the build
  if a digest or a byte size ever appears in the built pages or the sources.
