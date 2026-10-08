# 23 — Every route ships only the CSS it can use

Session `arena/af3e7f66-dhun`, 2026-10-08. Branch point and `main` at boot:
`1062a869258705bdf17dbd3125542d8556bb9925` (PR #140 merge, recorded in
`docs/verification/22-one-request-hard-viewports-print-and-structured-data.md`).
Working tree clean at boot. Status vocabulary is the one `.ai/WEBSITE_PLAN.md`
Part A fixes: **verified** = read from a tool output in this session · **expected**
= plausible but unchecked · **not verified** = explicitly unchecked. There is no
browser in this sandbox (see the last section), so every browser-dependent
statement here is marked as not verified locally.

## What this session changed, and what it measured

The site inlines one stylesheet per route, composed from the modules the page's
front matter declares (`website/eleventy.config.js`). Modules are shared, so a
route carried rules it could never use: measured at boot, `/` shipped 18 classes
it never uses, `/features/` 52, `/ui/` 53 and `/404.html` 34.

`website/tools/prune-css.mjs` now runs between Eleventy and the minifier and
drops, per page, the rules that page cannot use — decided against the page's own
`class` attributes rather than a hand-maintained list. The sources stay in one
readable module per layer; nothing moved between files.

| Route | Boot (B) | Now (B) | Δ | Δ % | Inlined CSS now (B) |
|---|---|---|---|---|---|
| `/` | 53,554 | **51,311** | −2,243 | −4.2 % | 20,380 |
| `/features/` | 54,881 | **48,019** | −6,862 | −12.5 % | 15,759 |
| `/ui/` | 56,263 | **49,508** | −6,755 | −12.0 % | 19,045 |
| `/404.html` | 17,441 | **12,794** | −4,647 | −26.6 % | 7,251 |

All eight numbers on both sides were read from tool output in this session: the
"now" column from `website_quality.page_weight_bytes`, the "boot" column from
`git show HEAD:website/dist/...` piped through `wc -c`. Client-side JavaScript is
still **0 bytes** on every route, and the route count and request count did not
change (still one request per route: the document).

| Check | Result (this session) |
|---|---|
| `npm run build` | `pruned 4 page(s): 27693 bytes of CSS no page can use` · `minified: saved 42725 bytes` |
| `npm run verify:minify` | `OK: every built file matches a fresh unminified build ignoring whitespace (42811 bytes saved by minification).` |
| `npm run test:rules` (`node --test`) | `# tests 26 # pass 26 # fail 0` — 17 pruner cases + 9 browser-rule cases |
| `npx html-validate "dist/**/*.html"` | exit 0, no output |
| `python3 scripts/website_quality.py website/dist` | `OK: 26 quality checks pass on website/dist.` |
| `python3 scripts/website_claims.py website/dist` | `OK: 4 built page(s) pass the honesty contract (8 forbidden-claim rules, 3 required caveats, 5 digest rules, 12 telemetry-SDK rules …).` |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | `Ran 250 tests in 0.872s` → `OK` (242 at boot) |
| Ratchet | regenerated: `/` 51,311 · `/features/` 48,019 · `/ui/` 49,508 (`tolerance` 0.05) |

### What the pruner is allowed to do

- A style rule whose selector list has classes and none of them appear in the
  page's markup loses the selectors that can never match. A selector with **no
  positive class** — `body`, `*`, `[aria-current]`, `a:not(.btn)` — is never
  removed, because it can match an element that has no class at all.
- A conditional group (`@media`, `@supports`, `@container`, `@layer`) is pruned
  recursively and dropped only when nothing survives inside it.
- Every other at-rule (`@keyframes`, `@font-face`, `@import`) is kept verbatim:
  its body is not a selector list.
- `:root` and the custom-property blocks are **never** touched. Tokens are the
  design system's published contract — `/ui/` prints their values — so an unused
  token still ships. Rule pruning only; adding token pruning later is a small,
  separate change (reversal cost: one function, one test file).
- Where nothing can be pruned, the output is byte-identical to the input, which
  is what lets `verify-minify.mjs` and the drift check compare a pruned build with
  a pruned source and still mean something.

## Mutation proofs (all run on this tree, each reverted afterwards)

| Mutation | Verdict from the gate |
|---|---|
| Build the site without pruning (the boot `dist` restored into a copy) | `FAIL: 4 quality violation(s)` — e.g. `/: … unit 54 differs: built 'rule:.lede{…}', expected 'rule:.site-footer{…}' (built 264 unit(s), expected 234 …)` |
| Hand-add `.handedited{color:red}` to `/`'s built sheet | `the build carries 1 extra unit(s), first: 'rule:.handedited{color:red}'` **and** `CSS defines 1 class(es) no built page uses (dead CSS): handedited` |
| Delete `.card{break-inside:avoid}` from `/features/`'s built sheet | `/features/: … unit 73 differs: built 'end', expected 'rule:.card{break-inside:avoid}'` |
| Edit one declaration in `/ui/`'s built sheet | `/ui/: … unit 179 differs: built 'rule:\u200b\u200b.swatch-chip{…}'` |
| Reorder two adjacent rules in the built sheet | `test_a_reordered_sheet_fails` passes (the Python suite fails the same way the gate does) |
| Delete the `@media (forced-colors: active)` block from `/ui/`'s built sheet | `/ui/: no @media (forced-colors: active) block ships in this page's own CSS …` |
| Remove `.mock .device { display: none; }` from `css/base.css` and rebuild | 3 violations: `/: the @media print block does not hide the decorative mockup drawing, so printing spends a page of ink on a recreation` (on the three routes that draw one; `/404.html` is correctly silent) |
| Break `positiveClasses()` so it never matches | `# tests 26 # pass 15 # fail 11`, first: `not ok 1 - a rule whose classes the page never uses is dropped`; restored → `26 pass 0 fail` |

## Two defects found while doing this (root causes in `.ai/DEBUG_LOG.md`)

1. **The print rule's drawing half could not fail.** `print_style_violations`
   sliced 3000 characters *after* the `@media print` marker and searched that
   window for `.device`. Because the mockup's own `.device` rule follows the print
   block in the same inlined sheet, deleting the hiding rule still passed. Both
   print and forced-colours now read the block itself, by brace matching
   (`_at_rule_block`), and a regression test pins it
   (`test_a_rule_after_the_block_cannot_satisfy_it`).
2. **`".device" in markup` is always false.** The drawing check searched the
   markup for the CSS selector string rather than the class attribute, so the new
   conditional rule could never fire either. It now uses the same
   `classes_used_by_page()` the pruner's mirror uses.

The print rule also changed *meaning* deliberately: hiding the drawing is required
of a page that draws one (three routes) and not of `/404.html`, which draws
nothing. What it used to prove for `/404.html` was vacuous; what it proves now is
the same floor wherever the effect exists.

## The check that had to be adapted

`dist_source_drift_violations` used to prove: *each page's inlined CSS equals the
byte-for-byte composition of the modules its front matter declares* — so a stale
`dist` or a hand-edit was a red test, without Node and therefore in app CI too.

With pruning in the pipeline that equality is false by design, so the check now
proves: *each page's inlined CSS equals the composition of those modules **pruned
with the same predicate the build used*** — every rule the page can use is
present, no rule it cannot use ships, the order is the source order, and a changed
declaration is a difference. It is implemented in Python
(`css_units`/`_prune_units`/`_flatten_units` in `scripts/website_quality.py`), so
app CI still checks the committed build with no Node, and the four mutation rows
above are the proof it still bites. The predicate is now written twice — once in
`website/tools/prune-css.mjs`, once in Python — which is the price of checking a
Node build from a Python-only job; each half is unit-tested
(`tests/prune.test.mjs`, `CssPruningPredicate`) and the build fails loudly if they
disagree.

## What is not verified here

- **Rendering.** No browser exists in this sandbox (`~/.cache/ms-playwright` does
  not exist; `cdn.playwright.dev`, `playwright.azureedge.net` and
  `storage.googleapis.com` all returned `000` to `curl` this session), so the
  *effect* of the pruned CSS — layout, contrast, screenshots, the print and
  forced-colours emulations — is **not verified locally**. The CI `browser` job
  measures the committed `dist/` and its annotations are the only rendered
  evidence; `lighthouse` remains the only score source.
- **A class added at runtime** would be invisible to a build-time pruner. The site
  ships no JavaScript (asserted), and the coverage rule fails the build if a page
  uses a class its own sheet does not define — so the risk is bounded, not zero,
  and it is recorded in `.ai/KNOWN_LIMITATIONS.md`.
- The two implementations could drift. Nothing catches a *semantic* divergence
  that both sides agree on; the mutation table only shows each side catches the
  cases described above.
