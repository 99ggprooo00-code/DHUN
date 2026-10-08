# Publishing the site

**Status of the canonical URL: it is not the site yet.** As of 2026-10-08 the
Pages API reports `build_type: legacy`, `source: main:/`, `status: errored`, so
<https://99ggprooo00-code.github.io/DHUN/> serves Jekyll's rendering of the
repository's root `README.md` — an engineering document with a status table and
links into `.ai/` — not the marketing site in `website/dist/`. Everything needed
to publish is built, checked and green; one repository setting is not set.

Read from a tool output, this session:

```text
gh api repos/99ggprooo00-code/DHUN/pages
  {"build_type":"legacy","source":{"branch":"main","path":"/"},"https_enforced":true,"status":"errored"}
gh api -X PUT repos/99ggprooo00-code/DHUN/pages -f build_type=workflow
  {"message":"Resource not accessible by integration","status":"403"}
```

## The fix (needs Pages write access — a human)

Either:

1. **Web UI:** repository → **Settings** → **Pages** → **Build and deployment** →
   **Source** → **GitHub Actions**. No branch selection, no folder selection:
   the workflow supplies the artifact.
2. **CLI**, with a token that has Pages write (a repository admin PAT, not the
   integration token this workspace authenticates with):

   ```bash
   gh api -X PUT repos/99ggprooo00-code/DHUN/pages -f build_type=workflow
   ```

Then push to `main` (or run **Actions → website → Run workflow**, which is
`workflow_dispatch`). On the next `website` run:

- `build` builds `website/dist` and runs every gate (unchanged);
- `deploy` sees `build_type == workflow` and actually uploads and publishes;
- `served` stops being skipped and re-runs the honesty contract against the
  bytes a visitor gets, at the canonical URL.

## How to tell whether it worked, without a browser

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

## Reversal

`gh api -X PUT repos/99ggprooo00-code/DHUN/pages -f build_type=legacy` (with a
Pages-write token) returns the URL to Jekyll's rendering of the README. Nothing
in this repository is destroyed by switching either way; the deployed artifact
is rebuilt from `main` on the next run.
