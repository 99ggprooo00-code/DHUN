# 25 — In-page jumps land below the sticky header

Session `arena/af3e7f66-dhun`, 2026-10-08, third phase of the same session (the
first is `docs/verification/23-per-route-css-pruning.md`, the second record 24).
Status vocabulary is the one `.ai/WEBSITE_PLAN.md` Part A fixes: **verified** =
read from tool output in this session · **expected** = plausible but unchecked ·
**not verified** = explicitly unchecked. This sandbox has no browser, so every
rendered statement is CI-only and marked as such.

## The defect

The header is `position: sticky; top: 0` (base.css) and nothing padded the
scrollport: `grep -rn 'scroll-margin\|scroll-padding' website/css/` returned only a
`scroll-behavior: auto !important` line inside the reduced-motion block. The
site's only in-page jumps are the skip link's `#main` (every page) and four
footnote links on `/`, so each of them scrolled its target flush with the top of
the viewport — behind the header. The header's height, read off the page's own
`:root` block (`--target` 44px, `--sp-4` 16px):

| Viewport | Header | Height |
|---|---|---|
| ≥ 480px, navigation on one line | wordmark row + nav row | 104px = 2 × 44 + 16 |
| < 480px, navigation wraps to two lines | wordmark row + two nav lines | 164px = 3 × 44 + 2 × 16 |

For a footnote link the covered first line *is* the footnote.

## The fix

```css
:root { scroll-padding-top: 12rem; }                                 /* 192px ≥ 164px */
@media (min-width: 480px) { :root { scroll-padding-top: 7rem; } }    /* 112px ≥ 104px */
```

in `website/css/base.css`, next to the sticky header it exists for, with the
arithmetic and the reason for `rem` (a visitor who raises the browser default font
size gets a larger offset, not a smaller one) written above it. Mobile-first, like
the rest of the sheet: the narrow case is the base, the wide case is the
`min-width` override.

## What now asserts it

| Layer | Rule | Proves |
|---|---|---|
| Static (local) | `anchor_landing_violations` in `scripts/website_quality.py` (check count 27 → 28) | every `href="#…"` in a built page has a matching `id`; and a page whose own CSS makes its `<header>` sticky, and which has an in-page jump, declares `scroll-padding-top` on `:root`/`html` **outside any conditional group** (a `@media`-only declaration is not a base), with the base value at or above the **three-row** floor and no declared value below the two-row floor — all four numbers re-derived from that page's `--target` and `--sp-4`, never hard-coded (the three-row floor was added in record 26) |
| Rendered (CI only) | `anchors land below the header` in `website/tests/browser.mjs` | at 1280×800, 380×800 and 280×653 — after each in-page jump the target's top edge is below the header's bottom edge and inside the viewport, and the record prints the effective `scroll-padding-top` |
| Decision logic (local) | `anchorLandingProblem()` in `website/tests/rules.mjs`, 4 tests | covered target, landed target, overshot target, missing target/unmeasurable header — must-pass and must-fail halves, with the padding in the message |

## Mutation proofs (all read from tool output this session)

Against disposable copies of the committed build (`AnchorLandings` in
`scripts/test_website_quality.py`) and a mutated copy of `dist`:

| Mutation | Verdict (message) |
|---|---|
| Remove the base declaration *and* the override from `/` | `/: the header sticks to the top and the page jumps to in-page anchors, but no scroll-padding-top is declared, so the target's first line lands behind the header` |
| Keep only the `@media`-only declaration (move the base onto `.wrap`) | `/404.html: scroll-padding-top is declared only inside a conditional group, so it does not apply at every viewport — and the header sticks at every viewport` |
| Shrink the **base** declaration to `8rem` on `/` (still above the two-row floor) | `/: the base scroll-padding-top is 128px, but below 480px the navigation itself can wrap, making the header three rows (44px + 16px + 44px + 16px + 44px = 164px), so a jump there lands behind it` |
| Shrink the ≥480px override to `4rem` on `/features/` | `/features/: scroll-padding-top falls to 64px at some viewport, but the header is two rows (44px + 16px + 44px = 104px) on a narrow one, so in-page jumps land partly behind it` |
| Rename the skip-link target (`id="main"`) | `/ui/: links to #main, which no element on the page has — the jump goes nowhere` |
| Remove every in-page jump from `/ui/` *and* the padding | no violation — a page with nothing to land has nothing to pad (`test_a_page_with_no_in_page_anchors_is_exempt`) |

## Measured (this session, from tool output)

| Route | Before this phase (committed `9e31320`) | After | Δ |
|---|---|---|---|
| `/` | 51,631 | **51,688** | +57 |
| `/features/` | 48,339 | **48,396** | +57 |
| `/ui/` | 49,828 | **49,885** | +57 |
| `/404.html` | 13,132 | **13,189** | +57 |

The pruner's own line is unchanged at
`pruned 4 page(s): 27693 bytes of CSS no page can use` — the two new declarations
have no class selector, so every route keeps them; the ratchet baseline was
regenerated in the same commit.

| Gate | Result |
|---|---|
| `npm run build` | `pruned 4 page(s): 27693 bytes of CSS no page can use` · `minified: saved 49418 bytes` |
| `npm run verify:minify` | `OK: every built file matches a fresh unminified build ignoring whitespace (49544 bytes saved by minification).` |
| `npm run test:rules` | `# tests 33 # pass 33 # fail 0` |
| `python3 scripts/website_quality.py website/dist` | `OK: 28 quality checks pass on website/dist.` |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | `Ran 264 tests in 8.061s` → `OK` (263 before the rewrite) |

## Not verified here

- **The rendered landing** (the CI-only half above): the sandbox has no browser
  (record 23), so the numbers the browser check will print — target top, header
  bottom, effective padding, at three widths — have not been read yet. The
  *decision* half is mutation-proven locally; the *gathering* half
  (`getBoundingClientRect` after the fragment jump, six animation frames to settle,
  `getComputedStyle(document.documentElement).scrollPaddingTop`) is not.
- **The 280×653 header height.** 164px is arithmetic from the tokens and the
  documented wrapping, not a measurement; if the header is taller than 192px there,
  the browser check fails and names the number to raise.
- **`scroll-padding-top` in browsers other than Chromium.** The property is
  standard and the CI job runs one engine; no other engine was measured here.
- **A user font size large enough to wrap the navigation at ≥480px.** That is the
  three-row case the base value exists for; the browser check runs at default font
  size only.
