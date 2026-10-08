# Publishing the site

**Status: published (2026-10-08).** The marketing site is live at
<https://99ggprooo00-code.github.io/DHUN/>. Pages `build_type` is `workflow`,
the `deploy` and `served` jobs both succeeded on run #33 (push to main, head
`b2889dfb`), and the served-site smoke check verified the published bytes.

Verification (this session):

```text
gh api repos/99ggprooo00-code/DHUN/pages --jq .build_type   → workflow
gh api repos/99ggprooo00-code/DHUN/pages --jq .status        → built
gh api repos/99ggprooo00-code/DHUN/pages/builds --jq '.[0]'  → status: built
gh run view 37831474600 → all 5 jobs succeeded (Build, Lighthouse, Browser, Deploy, Served)
Deploy job steps: configure-pages ✓, upload-pages-artifact ✓, deploy-pages ✓ — none skipped
Served job: passed (scripts/website_smoke.py verified the three required caveats)
```

## How it works

The `website` workflow (`.github/workflows/website.yml`) triggers on pushes to
`main` that touch `website/**` or related paths, and on `workflow_dispatch`.
The `deploy` job uploads a Pages artifact via `actions/deploy-pages`; the
`served` job fetches the live site and re-runs the honesty contract
(`scripts/website_smoke.py`) against the published bytes.

The `deploy` and `served` jobs accept both `push` and `workflow_dispatch` events
on main, so an operator can trigger a deploy from the Actions UI without pushing
to main directly (e.g. after changing a repository setting).

## How to tell whether it still works, without a browser

```bash
gh api repos/99ggprooo00-code/DHUN/pages --jq .build_type      # workflow
gh run list --workflow website.yml --limit 3                   # deploy: success
gh api repos/99ggprooo00-code/DHUN/pages/builds --jq '.[0]'    # status: built
gh run view <run-id> --log-failed                              # if it is red
```

The `served` job is the real proof: it fetches the published pages and re-runs
`scripts/website_smoke.py`, which asserts the three required caveats are in the
**published** bytes. It is gated on the deploy job's real publish output, so it
stays skipped (`skipped`, not green) until a publish has actually happened —
"we did not fetch the site" must never look like "the site is fine".

Annotations are the readable channel in this repository: the `build` job opens
every run by reporting `build_type` and the URL in the run summary and as a
`::warning::` while it is not `workflow`.

## What does *not* need doing (and why)

- **No Jekyll work.** No `_config.yml`, no front matter, no theme. Under the
  Actions source, Pages publishes the uploaded artifact byte for byte; Jekyll is
  not in the path at all.
- **No `.nojekyll`.** That flag belongs to branch-based publishing; with
  `actions/deploy-pages` the artifact is served as-is.
- **No copy of the site at the repository root.** It was considered and
  rejected: `index.html`, `features/`, `ui/` at the root would be a second,
  ungated copy of `website/dist/` that every site change has to remember to
  mirror, and it would be published by Jekyll alongside `docs/` and `.ai/`. The
  site's own canonical URL, sitemap and `robots.txt` already assume it is served
  from the root of this origin — which is exactly what the Actions source gives
  it. One setting, one source of truth.
- **No custom domain.** `<https://99ggprooo00-code.github.io/DHUN/>` is the
  canonical URL in the pages' `<link rel="canonical">`, the sitemap, `robots.txt`
  and the JSON-LD block. Changing it is a site-wide edit, not a Pages setting.

## If Pages ever falls back to legacy

The `build` job's warning fires whenever `build_type` is not `workflow`. When
that happens, the reader it points here needs the click-path, not just the API:

1. **Settings → Pages → Build and deployment → Source → GitHub Actions.**
2. Trigger **Actions → website → Run workflow** (`workflow_dispatch` works on
   `main` for both the `deploy` and `served` jobs), or push to `main` touching
   `website/**`.
3. Re-check with the commands in "How to tell whether it still works".

Under `legacy` the repository root is republished by Jekyll, so the URL renders
README.md instead of the artifact — a product defect, not a broken deploy.

## Reversal

`gh api -X PUT repos/99ggprooo00-code/DHUN/pages -f build_type=legacy` (with a
Pages-write token) returns the URL to Jekyll's rendering of the README. Nothing
in this repository is destroyed by switching either way; the deployed artifact
is rebuilt from `main` on the next run.