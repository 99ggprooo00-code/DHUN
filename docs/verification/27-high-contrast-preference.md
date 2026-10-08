# 27 — "Increase contrast" increases contrast

Session `arena/af3e7f66-dhun`, 2026-10-08, fifth phase of the same session (records
23–26 precede it). Vocabulary as in `.ai/WEBSITE_PLAN.md` Part A: **verified** =
read from tool output this session · **expected** = plausible but unchecked ·
**not verified** = explicitly unchecked.

## The gap

`contrast_violations` asserts a 4.5:1 floor across 13 token pairs. That is the
requirement, but it is not what a reader wants when they turn on the operating
system's *increase contrast* setting (`prefers-contrast: more`, exposed by Windows
and macOS): they are asking for more than the sheet's default ladder. Measured from
the tokens this session (corrected after the scoping bug below, `--text-3` on the
page background):

| Scheme | `--text-3` on `--bg` | on `--surface` | on `--surface-variant` |
|---|---|---|---|
| dark | 7.47:1 | 7.13:1 | **6.71:1** |
| light | **4.83:1** | **4.99:1** | **4.71:1** |

Everything clears 4.5:1; two surfaces in the light set and one in the dark set sit
below the 7:1 a reader asking for more contrast is asking for. A grep for
`prefers-contrast` over `website/css/` returned nothing: the preference was not
honoured at all.

## The change

```css
@media (prefers-contrast: more) {
  :root { --text-2: var(--text); --text-3: var(--text); }
}
```

in `website/css/tokens.css`. Both secondary rungs become the primary text colour:
**18.10:1** on `--bg` in the dark set, **16.26:1** in the light set (measured).
Written as a *reference* — `var(--text)` — so one declaration improves both colour
schemes instead of hard-coding one scheme's colour into the other.

## The check

`high_contrast_violations` (check count 28 → 29) walks each built page's own CSS
and: requires the block to exist; requires at least one redefinition; requires each
redefinition to resolve in **both** schemes; and requires every redefined token to
be a strict *improvement* that lands at **≥ 7:1** against `--bg`, `--surface` and
`--surface-variant`. A block that restates the defaults, picks a literal colour,
references a missing token, or improves nothing therefore fails the build.

| Mutation on a copy of the real build | Message |
|---|---|
| Delete the block | `/: no @media (prefers-contrast: more) block ships, so a visitor who asks the operating system for more contrast gets the default ladder` |
| `--text-3: #3d3934` (a literal, one scheme's colour) | `/features/: --text-3 in the high-contrast block is '#3d3934', which this rule cannot resolve — use `var(--…)` so both colour schemes get the improved contrast` |
| `--text-3: var(--text-3)` (a no-op) | `/ui/: --text-3 in the high-contrast block reaches only 4.83:1 on --bg in the light scheme, short of the 7:1 a visitor asking for more contrast expects` |
| `--text-3: var(--text-missing)` | `/404.html: --text-3 in the high-contrast block refers to --text-missing, which the dark and light scheme does not define` |

All four are Python tests in `HighContrastPreference` as well (5 cases, including
the clean committed build).

## Two traps found while writing the check (both recorded in `.ai/DEBUG_LOG.md`)

1. **Scope, not position, identifies a token block.** The first version read the
   light scheme as "every `:root` block after the first" — which also swallows the
   *print* and forced-colours palettes, so the "light" values were paper's
   (`--bg: #ffffff`). The check passed while measuring the wrong thing; printing the
   parsed maps is what exposed it. It now walks `css_units` and takes only `:root`
   rules inside a `prefers-color-scheme: light` group.
2. **The minifier drops the last `;` in a block.** The redefinition parser required
   a trailing semicolon, so the last declaration of the block was invisible and the
   literal-colour mutation passed. `[^;}]+` fixed it; the mutation now fails with
   the message in the table above.

## Measured cost and gates

| Route | Before | After | Δ |
|---|---|---|---|
| all four routes | — | — | **+80 B** each |

| Gate | Result |
|---|---|
| `npm run build` | `pruned 4 page(s): 27693 bytes of CSS no page can use` · `minified: saved 53466 bytes` |
| `npm run verify:minify` | `OK: … (53616 bytes saved by minification).` |
| `npm run test:rules` | `# tests 33 # pass 33 # fail 0` |
| `python3 scripts/website_quality.py website/dist` | `OK: 29 quality checks pass on website/dist.` |
| `python3 scripts/website_claims.py website/dist` | `OK: 4 built page(s) pass the honesty contract (…)` |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | `Ran 272 tests in 8.245s` → `OK` (267 before) |
| `npx html-validate "dist/**/*.html"` | exit 0, no output |

## Not verified here

- **Rendered contrast under the preference.** The numbers above are computed from
  the declared tokens, not read from a browser with `prefers-contrast: more`
  emulated; the sandbox has no browser (record 23). Whether Playwright's `contrast`
  emulation is available on the pinned 1.64.0 is untested here, so no browser-level
  claim is made for this preference.
- **The printed page.** The print palette is a separate block and is unchanged; the
  high-contrast block comes later in the sheet, so a reader who prints with
  "increase contrast" on gets whichever block the cascade resolves — not measured
  here.
- **User stylesheets.** Nothing is claimed about readers' own overrides.
