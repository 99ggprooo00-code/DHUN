# 28 — the finish sequence: the frozen tree, the gate batch, and what CI measured

Session `arena/af3e7f66-dhun`, 2026-10-08. This record closes the session's six
work items (records 23–27) with the evidence that must be read *after* the work,
on the tree that ships: the local gate batch, and the website workflow's first run
for this branch. Vocabulary as in `.ai/WEBSITE_PLAN.md` Part A.

Frozen tree: `72d9bad` (`website/`), with the PR at **#141** → `main`.

## Local gate batch (built and tested locally, on `72d9bad`)

| Gate | Output |
|---|---|
| `npm run build` | `pruned 4 page(s): 27693 bytes of CSS no page can use` · `minified: saved 53466 bytes` |
| `npm run verify:minify` | `OK: every built file matches a fresh unminified build ignoring whitespace (53616 bytes saved by minification).` |
| `npm run test:rules` | `# tests 33 # pass 33 # fail 0` |
| `python3 scripts/website_quality.py website/dist` | `OK: 29 quality checks pass on website/dist.` |
| `python3 scripts/website_claims.py website/dist` | `OK: 4 built page(s) pass the honesty contract (8 forbidden-claim rules, 3 required caveats, 5 digest rules, 12 telemetry-SDK rules against the shipped dependency graph).` |
| `npx html-validate "dist/**/*.html"` | exit 0, no output |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | `Ran 272 tests in 8.134s` → `OK` (267 at the start of this session) |
| `git status --short -- website/dist` | empty (the committed build is the build) |

## What the website workflow did — run `37821648145`, head `72d9bad`

| Job | Result | What it carries |
|---|---|---|
| Build and check the site | success | `website/dist matches a fresh build of website/src` (drift check) |
| Browser measurements | success | 61 measurements; the numbers quoted below |
| Lighthouse | **success** | `/` 100·100·100·100 (samples 99 · 100 · 100) · `/features/` 100·100·100·100 · `/ui/` 100·100·100·100 |
| Deploy / Served-site smoke | skipped | by design — Pages `build_type: legacy` |

Lighthouse detail: `TBT=0ms · CLS=0.000 · requests=1` on all three routes;
`bytes=52.0kB` (`/`, document 51,956 B), `48.7kB` (`/features/`, 48,664 B),
`50.2kB` (`/ui/`, 50,153 B); `unused-css-rules: none`; the only remaining
insight is the always-present `network-dependency-tree-insight` URL line.

## The red run, and how it was resolved rather than explained away

The first website run for this branch (`37820644105`, head `4f4c2b2`) failed
**Lighthouse**: `/` performance median **81**, from samples 71 · 81 · 100 with the
job's own warning that sample 1 failed and was retried. The other two routes in
the same job were 100/100/100, and `/`'s metrics included `TBT=819ms` on a
document with **zero scripts** (`requests=1`, JSON-LD only) — a main-thread
blocking time the page cannot produce, on the route measured first.

Re-running was not available: `gh run rerun 37820644105 --failed` →
`run 37820644105 cannot be rerun; its workflow file may be broken`, the REST
re-run → `403 Resource not accessible by integration`, `workflow_dispatch` → the
same 403, and the PR's `workflow_dispatch` path filter does not watch
`docs/**` or `.ai/**`, so a docs-only push could not re-trigger it either.

What settled it was an ordinary next commit. `72d9bad` touches only
`website/tests/browser.mjs` and three documents — **`website/dist` is byte-identical
to `4f4c2b2`'s** — and the workflow that ran on it scored `/` at 99 · 100 · 100
with `TBT=0ms`. Two runs, one site, 81 against 100: the earlier number was the
runner, not the bytes. The gate is median-of-three precisely because one sample
swings; this is the first time in this workstream that a red gate has been
retested on identical bytes rather than argued about.

## What the browser job measured (CI-verified, quoted from the annotations)

- **Current page**: `/` marked by `a.wordmark “DHUN” → /`; `/features/` by
  `a. “Features”`; `/ui/` by `a. “Interface”`; the other nav links render
  differently. Under forced colours all three keep their mark as a
  `text-decoration: underline` after backgrounds are repainted — the fallback
  drawn for exactly that engine behaviour.
- **Anchor landings** (three viewports, every in-page hash): 1280×800 `#main` at
  65 px against a header bottom of 65 px (`scroll-padding-top` 112 px); 380×800 at
  109 px/109 px (192 px); 280×653 at 161 px/161 px (192 px); each footnote anchor
  lands at the padding, clear of the header.
- **Contrast, default schemes**: lowest 5.71:1 (dark) and 4.6:1 (light) across
  181–244 text nodes per route, gradient-adjacent nodes measured against the
  layered background colour.
- **Touch targets**: smallest standalone 44 px on all three routes at 280×653,
  320×568, 360×800, 390×844, 844×390 and 768×1024.
- Also: 51 icons rendered, 12–23 rendered headings per route with no skipped
  level, 23 tab stops each showing a visible change when focused, no link text
  reused for two destinations, skip link first in tab order and Enter moving
  focus to `<main id=main>`.

## A claim found stale by the work, and fixed (commit `72d9bad`)

The reporter printed, under emulated `prefers-contrast: more`: *"this site
declares no `prefers-contrast` rules, so the number is the same as the default
scheme by design"* — true when written, false one commit after `tokens.css` gained
the block, and passing either way because nothing in the check depended on the
sentence. A `grep -rn prefers-contrast` (run while writing record 27) found the
same claim in `.ai/KNOWN_LIMITATIONS.md` and in record 22.

Now: the page counts its own `prefers-contrast` blocks in-page, the same route is
measured a second time **with the preference unset**, the recorded line carries
both ratios, and a ratio *below* the default's is a `fail`
(`asking for more contrast made the page worse`) rather than a notice. Proved
locally that the in-page regex finds exactly one block in the built page
(`style elements: 1; carrying a prefers-contrast block: 1`, prelude
`@media (prefers-contrast:more)`); whether the recorded line reads as expected is
a browser fact and is CI-only. The living document was rewritten; the historical
record keeps its sentence with a dated supersede note.

## Not readable in CI, with the root cause — a parked fix

The `prefers-contrast` and axe lines are **not** in this run's annotations. The
carry annotation is 4,057 B and ends mid-word (`…measured against the laye`),
while the reporter's own limit is `CARRY_CLIP = 24000` — so the truncation is
GitHub's per-message limit (~4 KB), not ours, and a large part of the 61
measurements (both axe scan sets among them) is dropped by the platform on every
run. This is the exact failure the reporter's own comment calls *"a bug in this
file, not a display detail"*, and it cannot be fixed by raising a constant.

Parked, deliberately, with the reason: **fixing it means emitting more, smaller
annotations within GitHub's ~10-per-step cap, or shortening each measurement —
a reporter design change whose effect is visible only in a CI run, which the
finish sequence has no budget for.** It is the first item handed to the next
session in `.ai/ROADMAP.md`.

## Not verified here (and by what)

- **The published site.** Pages is still `build_type: legacy` (`main:/` → Jekyll
  renders the root `README.md`), so `deploy` and `served` skip; the sandbox cannot
  reach `*.github.io`. Nothing in this session says anything about the public URL.
- **Anything rendered by a human.** No browser, no display, no screen reader here;
  every browser number above is Chromium's, read from check-run annotations. No
  person has looked at this site in this session.
- **`prefers-contrast: more` as pixels.** Covered statically (record 27) and by
  the emulated browser measurement; not eyeballed on any platform, and the
  emulation is Chromium's.
- **Firefox, WebKit, real devices, Windows High Contrast.**
- **The merged result.** `main` after the merge of #141 has not run at the time
  this record is written; its website run is the next session's first read.
