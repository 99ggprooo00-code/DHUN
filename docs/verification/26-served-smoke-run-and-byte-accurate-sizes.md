# 26 — The served site, checked locally, and sizes reported in bytes

Session `arena/af3e7f66-dhun`, 2026-10-08, fourth phase of the same session
(records 23–25 are the first three). Vocabulary as in `.ai/WEBSITE_PLAN.md` Part A:
**verified** = read from tool output in this session · **expected** = plausible but
unchecked · **not verified** = explicitly unchecked.

## What was run

`scripts/website_smoke.py` is the check for the **served** site — the bytes a
visitor actually receives. Until now this session could only report the repository
as *built* and *tested locally*; the served column was empty for this branch,
because the workflow only runs that script after a successful Pages deploy, and
Pages serves the repository `README.md` through Jekyll today (`build_type:
legacy`, see `.ai/ROADMAP.md`).

It turns out the script does not need Pages: it takes a base URL. Served locally
from the committed build with a stock static server —

```
cd website/dist && python3 -m http.server 8080 --bind 0.0.0.0
python3 scripts/website_smoke.py http://127.0.0.1:8080
```

— it reports:

```
/: HTTP 200, 51688 bytes, sha256:51d14e26ad75
/features/: HTTP 200, 48396 bytes, sha256:8821d8908116
/ui/: HTTP 200, 49885 bytes, sha256:1310f7b176af
OK: 3 served route(s) carry the three required caveats, trip no forbidden-claim
rule, and every internal link resolves.
```

That is **served** evidence for the honesty contract (the three required
caveats present, no forbidden claim, every root-relative link resolving over
HTTP) and a reproducibility recipe for any later session. It is *not* evidence
about the canonical URL: the pages' `<link rel="canonical">` points at
`https://99ggprooo00-code.github.io/DHUN`, which this sandbox cannot reach, so the
published-site half of the script remains unverified here.

## The defect found while reading that output

The line said `51627 bytes` for `/` while the file is **51,688 bytes**: the script
printed `len(markup)` — *characters* — and called it bytes. The site's copy is full
of em dashes, arrows and multiplication signs (61 of them on `/`), each costing two
or three bytes in UTF-8, so every page was understated by 55–61 bytes in the one
number a reader would take as the page's weight.

Fixed with a named function rather than an inline `len(...)` so the rule is
testable: `served_size(markup) -> len(markup.encode("utf-8"))`. Mutation: replacing
it with the old character count fails the new test
(`Ran 15 tests … OK` → with the mutant, `/` reports 51,627 against a 51,688-byte
file, and the test's committed-page equality check fails).

| What the new test asserts | Why it is not a tautology |
|---|---|
| `served_size("a") == 1`, `served_size("—") == 3`, `served_size("≈") == 3`, `served_size("ab—c") == 6` | pins the *encoding*, not the function name |
| for every committed `dist/**/*.html`: `served_size(text) == path.stat().st_size` | ties the reported number to the file the build ships; a future encoding change (or a `decode("latin-1")`) breaks it |

## Also tightened in this phase

`anchor_landing_violations` (record 25) checked that no declared
`scroll-padding-top` fell below the **two-row** floor. The base declaration is the
one that applies *below* 480px, where the navigation can wrap and the header is
three rows, so the base is now held to the **three-row** floor
(3 × 44px + 2 × 16px = 164px) as well. Mutation: shrink the base to `8rem` on `/` →
`/: the base scroll-padding-top is 128px, but below 480px the navigation itself
can wrap, making the header three rows (44px + 16px + 44px + 16px + 44px = 164px),
so a jump there lands behind it`. The value that ships (12rem = 192px) passes; the
shipped override (7rem = 112px, from 480px up) is held to the two-row floor.

## Gate results after both changes (this session, from tool output)

| Gate | Result |
|---|---|
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | all tests OK, including 8 `AnchorLandings` cases and 15 smoke cases |
| `python3 scripts/website_quality.py website/dist` | `OK: 28 quality checks pass on website/dist.` |
| `python3 scripts/website_smoke.py http://127.0.0.1:8080` | the served run quoted above |

## Not verified here

- **The published site.** `https://99ggprooo00-code.github.io/DHUN` is unreachable
  from this sandbox (recorded in `.ai/KNOWN_LIMITATIONS.md`) and Pages is still on
  the legacy source, so the served run above is a *local* server of the committed
  bytes, not GitHub Pages.
- **GitHub Pages' own headers and caching.** A stock `http.server` sets no
  cache-control, no compression and no security headers; what Pages adds is not
  observable here and is not claimed.
- **Response-size under compression.** The reported sizes are uncompressed bytes;
  the served site is likely gzipped in transit and no compressed figure is claimed.
