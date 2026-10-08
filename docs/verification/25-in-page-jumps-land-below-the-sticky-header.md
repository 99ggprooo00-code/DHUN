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
the viewport — behind the header. On a viewport narrow enough for the header to
wrap, the header is **104px** tall, read off the page's own `:root` block:
`--target` 44px + `--sp-4` 16px + `--target` 44px; for a footnote link the covered
first line *is* the footnote.

## The fix

```css
:root { scroll-padding-top: 7rem; }   /* 112px = 104px worst case + 8px slack */
```

in `website/css/base.css`, next to the sticky header it exists for, with the
arithmetic and the reason for `rem` (a visitor who raises the browser default font
size gets a larger offset, not a smaller one) written above it.

## What now asserts it

| Layer | Rule | Proves |
|---|---|---|
| Static (local) | `anchor_landing_violations` in `scripts/website_quality.py` (check count 27 → 28) | every `href="#…"` in a built page has a matching `id`; and a page whose own CSS makes its `<header>` sticky, and which has an in-page jump, declares `scroll-padding-top` on `:root`/`html` of at least the two-row floor — re-derived from that page's `--target` and `--sp-4`, not hard-coded |
| Rendered (CI only) | `anchors land below the header` in `website/tests/browser.mjs` | at 1280×800 and at 380×800 (header wraps): after each in-page jump the target's top edge is below the header's bottom edge and inside the viewport |
| Decision logic (local) | `anchorLandingProblem()` in `website/tests/rules.mjs`, 4 tests | covered target, landed target, overshot target, missing target/unmeasurable header — must-pass and must-fail halves |

## Mutation proofs (all read from tool output this session)

| Mutation on a copy of the real build | Verdict |
|---|---|
| Delete `:root{scroll-padding-top:7rem}` from `/` | `/: the header sticks to the top and the page jumps to in-page anchors, but no scroll-padding-top is declared, so the target's first line lands behind the header` |
| Shrink it to `4rem` on `/features/` | `/features/: scroll-padding-top is 64px, but the header is two rows (44px + 16px + 44px = 104px) on a narrow viewport, so in-page jumps land partly behind it` |
| Break a footnote target's `id` (rename `id="fn-a"`) | `/: links to #fn-a, which no element on the page has — the jump goes nowhere` |
| Move the declaration onto `.wrap` instead of `:root` | `/404.html: the header sticks to the top and the page jumps to in-page anchors, but no scroll-padding-top is declared …` (the scrollport belongs to the root element, so a declaration elsewhere does not count) |
| Remove every in-page jump from `/ui/` *and* the padding | no violation — a page with nothing to land has nothing to pad (unit test `test_a_page_with_no_in_page_anchors_is_exempt`) |

The same four mutations exist as Python tests in `AnchorLandings`
(`scripts/test_website_quality.py`) against disposable copies of the committed
build, so the rule cannot silently stop firing.

## Measured (this session, from tool output)

| Route | Before this phase (committed `a67f9ac`) | After | Δ |
|---|---|---|---|
| `/` | 51,601 | **51,631** | +30 |
| `/features/` | 48,309 | **48,339** | +30 |
| `/ui/` | 49,798 | **49,828** | +30 |
| `/404.html` | 13,102 | **13,132** | +30 |

Inlined CSS per route after the build: `/` 20,680 B, `/features/` 16,059 B,
`/ui/` 19,345 B. The pruner's own line is unchanged at
`pruned 4 page(s): 27693 bytes of CSS no page can use` — the rule added here has no
class selector, so the pruner keeps it on every route; the ratchet baseline was
regenerated in the same commit.

| Gate | Result |
|---|---|
| `npm run build` | `pruned 4 page(s): 27693 bytes of CSS no page can use` · `minified: saved 48282 bytes` |
| `npm run verify:minify` | `OK: every built file matches a fresh unminified build ignoring whitespace (48392 bytes saved by minification).` |
| `npm run test:rules` | `# tests 33 # pass 33 # fail 0` |
| `python3 scripts/website_quality.py website/dist` | `OK: 28 quality checks pass on website/dist.` |
| `python3 scripts/website_claims.py website/dist` | `OK: 4 built page(s) pass the honesty contract (…)` |
| `npx html-validate "dist/**/*.html"` | exit 0, no output |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | `Ran 263 tests in 7.985s` → `OK` (257 before this phase) |

## Not verified here

- **The rendered landing** (the CI-only half above): the sandbox has no browser
  (record 23), so the numbers the browser check will print — target top, header
  bottom, at both widths — have not been read yet. The *decision* half is
  mutation-proven locally; the *gathering* half (`getBoundingClientRect` after the
  fragment jump, six animation frames to settle) is not.
- **`scroll-padding-top` in browsers other than Chromium.** The property is
  standard and the CI job runs one engine; no other engine was measured here.
- **Whether 112px is *always* enough.** It covers the two-row header measured from
  the page's own tokens (104px); a user font size large enough to wrap the *nav
  into two lines* would make the header three rows. The browser check is what would
  catch that, at the two widths it runs.
