# DHUN Web (`/app/`) — the web workstream

This directory is the plan, the status record and the rules for **DHUN in a
browser**: the application at [`/app/`](https://99ggprooo00-code.github.io/DHUN/app/)
and the static site that publishes it.

It exists because the browser build has a different set of problems from the
native ones, and a different failure mode: on Android and Windows a broken
player is visibly broken, while in a browser it is very easy to build something
that *looks* like a player and produces no sound at all. The documents here are
written so that the difference is recorded rather than discovered later.

> **Status: IN DEVELOPMENT — NOT VERIFIED FOR RELEASE.** The reason is one
> specific thing, and it is at the top of the phase list: no author of this
> repository has yet heard audio come out of the deployed site. See
> [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) for the full ledger and
> [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) for what is known broken or
> unproven.

## The priority order

When two of these conflict, the higher one wins.

1. **A real, audible player.** Every playback control either produces sound or
   says plainly why it cannot.
2. **A source that costs nothing.** No paid API, no server, no proxy, no
   database, no domain, no trial that expires into a bill.
3. **Accurate controls and honest queue behaviour.**
4. **The DHUN interface** — dark first, frosted glass, the app's own tokens.
5. **Desktop and mobile browsers that both work.**
6. **Optional features and polish.**

That order is why there is no equaliser screenshot in this directory and a
first-class record of a refused stream. It is also why the phase list puts a
playback proof of concept (Phase 2) *before* the interface work (Phase 6), even
though the interface work is already done in this repository — see the note on
retro-mapping below.

## The hard rules, and what enforces each one

A rule that nothing checks is a rule that lasts until the next busy session.
Each rule below names the mechanism that fails when it is broken.

| Rule | Enforced by |
|---|---|
| **No running cost.** No backend, proxy, serverless function, paid database, paid API or paid domain. | `docs/decisions/ADR-008-browser-web-player-target.md` boundary 1; `scripts/test_app_web.py` asserts no module defines a DHUN-operated endpoint, and the CSP (`app-web/src/index.html`) names no DHUN origin. |
| **No fake playback.** The progress bar never advances without audio; a "playing" state implies a media element that is actually playing. | `app-web/tests/player.test.mjs` and `audio.test.mjs` drive the state machine and the engine through a stubbed element; `app-web/src/js/player-ui.js` renders position from the player's own clock. |
| **No simulated search.** No hardcoded catalogue is ever presented as a live result. | `app-web/src/js/source.js` tags every response with its `kind`; `views.sourceBadge()` puts that on screen; `scripts/test_app_web.py` (`DoesNotSellWhatABrowserCannotDo`) asserts the demo catalogue is labelled as demo at the source level. |
| **Honest states.** Loading, refused, expired, unsupported, offline and failed are distinct and visible. | `app-web/tests/*.test.mjs` for the state machine; the `browser` job in `.github/workflows/website.yml` fails on an unhandled page error on `/app/`. |
| **No claim without evidence.** Neither the site nor these documents may promise browser playback. | `scripts/website_claims.py` (the site's copy, on built pages **and** on the served bytes), `scripts/web_player_docs.py` (these documents). |
| **No secrets.** No key, token or credential in the tree. | `scripts/test_repo_health.py` (`test_no_secret_patterns_outside_the_public_allowlist`). |
| **No tracking.** No analytics, telemetry or crash reporting. | `scripts/website_claims.py`'s telemetry-SDK rules read the shipped dependency graph; `app-web/tools/build.mjs` fails on any module the service worker does not cache, and the app ships no third-party script at all. |
| **Small, reviewable, tested changes.** | Pull requests only, with the repository's CI as the gate; see [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md). |
| **Nothing is lost from the native project.** The web app is a second client, not a replacement. | No file under `shared/`, `app-android/` or `app-desktop/` is touched by web work; `.github/workflows/ci.yml` builds and tests all of them on every pull request, including web-only ones. |

## Files in this directory

| File | What it is |
|---|---|
| [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) | The master tracker: every phase, its status, its evidence, and what remains. The release-readiness checklist at the end is the definition of done. |
| [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) | What is currently unproven, unsupported or awkward — written for a reader deciding whether to rely on this. |
| [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md) | How to run it, test it, build it and deploy it, in the repository as it actually is. |
| `PHASE-00` … `PHASE-10` | One file per phase, each with an objective, acceptance criteria, the evidence that exists, and an explicit go/no-go decision. Phase files are **not** aspirational documents: where a phase is complete, the file says what proved it; where it is not, the file says what is missing. |

## Two things to read before the phases

**This is not a fresh repository.** The bootstrap plan that this directory
implements was written for a new repository (`dhun-webapp`) with an empty
`app-web/`. That repository was deliberately not created: the user's instruction
was to do the same work *in this repository*, and duplicating a second web app
would have produced two projects to maintain, two sets of claims to keep honest,
and no shared design system. So these documents are a **retro-mapping**: the
phase structure of the plan is applied to the code that already exists here,
with each phase's status reflecting what is genuinely proven — not what the plan
assumed would happen.

The consequence is visible in the tracker: several phases that the plan puts
*after* the playback proof of concept (the interface, the library, the
persistence layer) are already implemented in this repository, while Phase 2 —
the one that mattered most — is still open. The phases are ordered by what they
depend on; the repository reached them in a different order. The tracker records
both facts.

**License.** The bootstrap plan specified MIT. This repository is
**GPL-3.0-or-later** (`LICENSE`), and the web app inherits it — `app-web`'s own
manifest declares `GPL-3.0-or-later`. Changing that for one directory is not
possible without making the tree self-contradictory, so the license stays as the
repository has it. The plan's own rules say to respect existing repository
conventions and licenses, which is exactly what happened here.

## Deviations from the bootstrap plan

The plan was written against a repository that does not exist yet. Where it
described something this repository already does differently, the repository's
reality wins and the difference is recorded here rather than quietly ignored —
the alternative is two web apps, or documents that describe a project nobody
runs.

| The plan said | This repository | Why |
|---|---|---|
| Create `dhun-webapp` as a second repository | One repository; the web workstream lives in `docs/web-player/` and `app-web/` | The instruction was to do this *in this repository*; a second repo would duplicate the app, the legal bundle and the design system, and each copy would need its own honesty gates |
| MIT license | GPL-3.0-or-later, as the repository already is | A per-directory license change would make the tree self-contradictory; `app-web/package.json` declares the repository's license |
| A new `deploy.yml` workflow | Deployment stays in `.github/workflows/website.yml` | Both would publish to the same Pages environment and race each other; one artifact has one owner |
| `app-web/index.html`, `app-web/manifest.json` | `app-web/src/index.html`, `app-web/src/manifest.webmanifest` | The app has been built this way since before the plan; the build asserts it |
| Four hand-written CSS files (`variables.css`, `main.css`, `player.css`, `responsive.css`) | `app-web/src/css/app.css` plus a **generated** `tokens.css` | One stylesheet plus generated tokens avoids a second palette; the generator is the reason the site and the app cannot drift apart |
| Placeholder provider modules to be filled in later | No placeholder modules | Module stubs that describe a provider they do not implement are the "fake progress" the plan itself forbids, and `app-web/tools/build.mjs` fails on a module the service worker does not cache |
| A project board and branch protection | Neither, from this workstream | Both are repository-settings changes that need the owner; the workstream's discipline is the pull-request gate documented in [DEVELOPMENT-GUIDE.md](DEVELOPMENT-GUIDE.md). Recorded as an exception in [PHASE-09](PHASE-09-DEPLOYMENT.md) |
| A `.env.example` | Shipped, and it documents that there is nothing to configure | The web app has no build-time or runtime secret, no API key and no account; the only environment variable anywhere is the development server's optional `PORT`. A `.env` file is not read by any tool here, and the file says so instead of implying otherwise |
| Its own system prompt / master prompt document | The repository's `.ai/` documents, updated to match | A second master prompt in a repository that already has one is how two sources of truth start |

