# ADR-009 — A static marketing site is the only public web property

- **Status:** PROPOSED (written by the `arena/fc918d37-dhun` session, 2026-10-08,
  on the user's explicit instruction to build the site; **not** an architecture
  change to the app, and it does not widen ADR-008)
- **Supersedes:** nothing
- **Related:** `docs/decisions/ADR-008-browser-web-player-target.md` (B1 closed
  BLOCKED; B2 is a separate user decision), `.ai/MASTER_PROMPT.md` §1
  ("Production Web remains deferred"), §7 "Explicitly NOT in S1–S6"
- **Affected code:** new top-level `website/` (static output only) and its own
  workflow `.github/workflows/website.yml`. **No application code, no Gradle
  file, no shared module, and none of the four existing workflows are touched.**

## Amendment — 2026-10-08 (session `arena/9b791057-dhun`)

The decision below stands, with one correction of scope: the site's role is
**the product, not the distribution**. By explicit direction, the download page
was removed and its route replaced by `/ui/` (the interface: surfaces, design
tokens, and the recreation contract). The site therefore has three routes —
`/`, `/features/`, `/ui/` — plus a 404, and it links the rolling `test`
pre-release only as a page, never as an artifact: no digest, no byte size, no
installation or verification instructions. `scripts/website_quality.py` enforces
that boundary (`distribution_boundary_violations`), and the claims contract
(`scripts/website_claims.py`) is unchanged otherwise.

## Context

`README.md` line 2 has advertised a GitHub Pages URL since early in the
project. Pages is configured `legacy` / `main:/`, so Jekyll renders the
repository README at <https://99ggprooo00-code.github.io/DHUN/>. The link is
therefore not broken, but the project's public front door is an engineering
document: it opens with a build-status table, requires a JDK to act on, and
never tells a normal person what DHUN is, what it runs on, or how to install
it.

Separately, the reference product the user pointed at
(<https://volta-music.com/en>) runs **two** web properties: a marketing site
and a web player. The user's session instruction resolves the ambiguity: build
the **marketing site** only. A browser player is explicitly out of scope, and
ADR-008's B1 probe — the only authorized web implementation work — is closed
as **BLOCKED** in the only available browser (Brave/Chromium family; Firefox
and Safari unavailable).

## Decision

Add a **static marketing and download site** as a first-class, isolated
deliverable:

1. Source lives in `website/`, builds with one command to plain static files
   (`website/dist/`) with **zero client-side JavaScript**.
2. It describes **only** the applications that exist (Android, and the
   Windows-first desktop client with Linux/macOS free via the JVM build) and
   states their real status, including the unverified rolling build and the
   open S3 hardware gates.
3. It links the rolling `test` release **by URL only**. No digest or size is
   copied into page copy, because those bytes change on every merge to `main`.
4. It ships **no third-party runtime asset**: system font stack, hand-written
   CSS/SVG, a favicon derived from DHUN's own launcher art. The repository has
   no images, and none are fabricated.
5. Deployment moves Pages to `build_type: workflow`, so the canonical URL
   serves a built artifact instead of a rendered README.
6. Truthfulness is enforced by Python tests in `scripts/` that run in the
   existing CI step 1 and in the site's own workflow, not by review prose.

## Alternatives considered

1. **Do nothing / leave the README at the front door.** Rejected: the URL is
   already advertised in `README.md` and the user asked for the site; the cost
   of leaving it is that every visitor's first impression is a build matrix.
2. **Build a browser player (Option B).** Rejected, and explicitly forbidden by
   the session instruction. It contradicts MASTER_PROMPT line 46 and the
   "Explicitly NOT in S1–S6" list, needs its own ADR, and the only feasibility
   evidence available (ADR-008 B1) is a **BLOCKED** result in the only browser
   that could be tested.
3. **Put the site under `app.` or any second hostname.** Rejected: DHUN has no
   custom domain, and creating a second property implies a second product that
   does not exist.
4. **Generate with a headless-CMS or a JS framework (Next/Nuxt/Svelte).**
   Rejected: a static site with three routes needs no runtime, no data layer
   and no hydration; a framework would add client JavaScript, a build-time
   supply chain and a reason for the app workflow to change.
5. **Publish only from a `gh-pages` branch by hand.** Rejected: unverifiable,
   and it puts generated files on a second branch that no test covers.

## Consequences

- `website/` becomes a build subject with its own gates (honesty, weight
  budget, HTML validity, link check, Lighthouse/axe where a browser exists in
  CI). **A site build failure cannot fail app CI**: the app's step 1 reads the
  committed built HTML with Python only — no Node, no network.
- The built output is **committed** at `website/dist/` so the honesty contract
  is assertable from app CI; the site workflow rebuilds and fails on drift, so
  what is served cannot silently diverge from what was tested.
- One repository setting must change: Pages source → **GitHub Actions**. Until
  that switch is made, the legacy rendering of the README stays live and the
  built site is also reachable under `/DHUN/website/dist/`.
- The site is **GPL-3.0**, like the rest of the repository, and carries the
  licence notice in its footer with links to `LICENSE` and `THIRD_PARTY.md`.
- This ADR does **not** authorize a player, a backend, a proxy, a
  Kotlin/JS–Wasm–TypeScript target, or any change to extraction. ADR-008 B2
  remains a separate, explicit user decision.
- If the project later wants real screenshots, `.ai/WEBSITE_PLAN.md` §9 lists
  exactly which capture replaces which labelled mockup.
