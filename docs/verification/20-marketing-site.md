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

## What is **not** verified here

- **CI verdicts for this branch**: not read at the time this record was
  written; the session's finish sequence records the run IDs it read.
- **The Actions Pages deployment**: `build_type` was `legacy` on `main:/`
  when this session started (`gh api repos/.../pages`), so the canonical URL
  rendered the root `README.md`. Whether the repository token may switch the
  source to `build_type: workflow`, and whether the `deploy-pages` job then
  publishes, is recorded in the session's final notes and PR comment.
- **Lighthouse and axe**: **no browser and no display exist in this sandbox**
  (no `google-chrome`, `chromium` or `firefox` binary), so no score can be
  measured locally and none is claimed. The numbers, if any, come from the
  `a11y` job's check-run annotations on `ubuntu-latest`.
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
