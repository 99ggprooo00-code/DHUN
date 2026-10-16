# Phase 9 — GitHub Pages deployment and release

> Status: ⚠️ Complete with exceptions — the deployment works and is verified; the release is not, because Phase 2 is open
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

Publish the site and the app from `main`, verify what visitors actually receive,
and be honest about the difference between "deployed" and "released".

## Acceptance criteria

- [x] The site and the app deploy together, from `main`, with no manual step.
- [x] The deployment works under the project's real URL prefix — GitHub Pages
  serves this repository at `/DHUN/`, not at a domain root, and internal links
  are built for that prefix.
- [x] The published bytes are re-read after every publish, and the honesty rules
  are applied to them — not only to the build that produced them.
- [x] A failed publish is visible: the deploy job's output gates the served
  check, so a "deploy succeeded, site did not publish" state cannot pass quietly.
- [x] One workflow owns the Pages artifact.
- [ ] A release of the web app is declared — **not done, and deliberately**: the
  release gate is [PHASE-02](PHASE-02-PLAYBACK-POC.md).

## How it deploys

```
push to main
   │
   ├─ Build the site (root build)         ── quality + claims gates on dist/ ──┐
   ├─ Build the app (app-web/dist)        ── build checks + 165 tests ─────────┤
   ├─ Assemble a local artifact           ── the browser job renders it ───────┘
   │
   ├─ Rebuild the site with the /DHUN prefix
   ├─ Assemble the Pages artifact (site at the root, the app at /app/)
   ├─ Verify every internal link against the prefixed tree
   ├─ Publish
   └─ Re-read the live site and re-apply the honesty rules to the served bytes
```

Why one workflow and not two: the deployed site *is* the marketing pages plus
the app; a second workflow publishing to the same Pages environment would race
the first, and each would overwrite the other's output depending on the order
they finished. The repository's rule is that one artifact has one owner.

## Live URLs

| What | URL | Checked how |
|---|---|---|
| Site | <https://99ggprooo00-code.github.io/DHUN/> | Fetched after the last publish; the served check re-reads it on every publish |
| Interface walkthrough | <https://99ggprooo00-code.github.io/DHUN/ui/> | Same |
| Features | <https://99ggprooo00-code.github.io/DHUN/features/> | Same — this is the page whose copy was corrected on 2026-10-16 after it had denied the browser build, which is why the served check now carries the rule that catches that sentence |
| The app | <https://99ggprooo00-code.github.io/DHUN/app/> | Same; the served check asserts the shell, the strict CSP, the assets and that the page stays out of search indexes |

## The "deployed" / "released" distinction

Deployment is a fact about the network: the files are there and a visitor can
load them. Release is a claim about quality, and this project's definition of
done (see [`IMPLEMENTATION-STATUS.md`](IMPLEMENTATION-STATUS.md)) is not met
while playback is unproven. So:

- the app is **deployed** and reachable;
- the app is **not released**, and nothing in this repository says it is;
- the native Android and Windows builds remain the supported players, which the
  site states where a visitor chooses what to install.

## Exceptions

| Item | Status |
|---|---|
| Branch protection requiring review on `main` | Not enabled by this workstream: repository settings need owner action, and the pull-request discipline is currently convention plus CI rather than an enforced rule |
| A release tag or a GitHub Release for the web app | Not created, deliberately — see above |
| Automated rollback | None. A bad deploy is fixed by a new commit; the previous build is in the workflow's artifacts |

## Risks identified

- **A green deploy is not a working site.** The publish goes through a CDN,
  which can serve a previous deployment briefly; the served check reports what it
  actually fetched, with a digest, so "stale" and "wrong" can be told apart.
- **The base path is the classic failure here.** This repository has shipped
  root-absolute links that 404'd only after deployment, because local servers run
  at a root and the deployed site does not. The prefixed-link step exists for
  that, and every local instruction in
  [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md) tells a developer to serve at a
  path rather than a root.

## Test results

| Test | Result |
|---|---|
| `served` job on the last publish to `main` | Pass — the site's routes carry their caveats, trip no forbidden claim, every internal link resolves, and `/app/` serves the shell with its CSP |
| Deployed `/features/` page after the 2026-10-16 fix | Verified by fetching it: the stale denial is gone |
| The app in a browser on a real network | **Not tested** — Phase 2 |

## Completion decision

- [x] Acceptance criteria met, except the release itself
- [x] The deployment is verified on the bytes a visitor receives
- [x] Documentation: this file, and the deployment section of
  [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md)
- [ ] A release declared — blocked on Phase 2 by choice

**Go / No-Go: Deployment complete. Release: No-Go until Phase 2 and the Phase 8
manual matrix are done.**
