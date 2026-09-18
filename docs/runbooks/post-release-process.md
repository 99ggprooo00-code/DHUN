# Runbook: how DHUN is worked on after v0.1.0 ships

**Audience:** the next agent session, or the repo owner returning after a
break. Read this *after* `.ai/README.md` (boot protocol) — this file is the
steady-state operating process; that one is the per-session checklist.

**Status of this document:** written 2026-09-18 while the project was still
pre-release (Stages S1–S6 open, no `v0.1.0` tag). Everything under
**[MANDATE]** is already binding in `.ai/MASTER_PROMPT.md`, `.ai/README.md`,
`.ai/RISK_REGISTER.md` or the workflows. Everything under **[PROPOSAL]** is
new here and needs the owner's OK before anyone treats it as law.

---

## 0. What "project completion" actually means

Completion is not "the code works". Per MASTER_PROMPT §7, Stage S6 acceptance
requires **all** of:

- S1 live verdict green (a human-dispatched `extraction-health` run on `main`)
- S3 hardware checklists signed (`docs/verification/s3-hardware-checklist` /
  `docs/runbooks/s3-hardware-checklist.md`)
- 30-minute soaks logged — Android unrestricted-battery **and** Desktop libVLC,
  zero-crash
- All three artifacts clean-installed on clean targets: APK, AAB, MSI
- Explicit user go-ahead

Only then: tag `v0.1.0`, publish the GitHub Release, finalize `CHANGELOG.md`
(drop DRAFT markers), review `KNOWN_LIMITATIONS.md` and `THIRD_PARTY.md`.

**After that, the project does not become "finished".** It becomes
*maintained*. The reason is the project's own first fact: **extraction is
maintenance, not implementation.** YouTube changes; the app's core function
depends on that not changing. Everything below is built around that.

---

## 1. The two rules that outrank everything else

**[MANDATE] Source-of-truth hierarchy** (MASTER_PROMPT §9):

1. Current source code on `main`
2. Current tests + CI verdicts (on GitHub, not local claims)
3. Accepted ADRs (`docs/decisions/`, statuses as filed)
4. `MASTER_PROMPT.md`
5. `.ai/ROADMAP.md`
6. Historical research
7. Old plans

If documentation contradicts code: investigate first. Never implement
documentation that contradicts verified current behavior.

**[MANDATE] Locked-decision changes need an ADR + owner OK** (§8 rules 9, 11;
`.ai/README.md`). Silent divergence is forbidden. This applies to the
extraction engine, the stack, and the platform list. The web client episode of
2026-09-18 is the worked example of what going around this looks like — see
`KNOWN_LIMITATIONS.md` (2026-09-18).

---

## 2. Every session, in order

**[MANDATE] Boot protocol** (`.ai/README.md`) — before touching code:

```bash
# 1. What is the active task, and what was the last error?
sed -n '1,60p' .ai/ROADMAP.md
# 2. Open incidents and environment traps — do not re-diagnose these
sed -n '1,60p' .ai/DEBUG_LOG.md
# 3. What actually landed since the docs were written? (docs lag code)
git log --oneline -15 && git status
# 4. The plan and the behaviour rules
sed -n '1,140p' .ai/MASTER_PROMPT.md
```

**[MANDATE] Pre-push ritual** (`.ai/README.md`, "Permanent maintenance
contract"):

1. Verify on **GitHub**, not locally, what is pushed and green.
2. Rewrite `ROADMAP.md` → CURRENT ACTIVE TASK (file · last error · exact next
   step).
3. Mark steps from that evidence only.
4. Commit everything and push, including any stranded unpushed commits.
5. Re-check CI; fix the marks in a follow-up commit if they changed.

**[MANDATE] Definition of done:** pushed + CI green + (where specified)
on-hardware verified. Unpushed or CI-unverified work is marked **NOT done**.

**[MANDATE] Session close:** update `.ai/ROADMAP.md` and
`.ai/KNOWN_LIMITATIONS.md` (honest > complete). Significant crashes, CI reds
and environment traps get a `.ai/DEBUG_LOG.md` entry in the form
*symptom with stack → root cause → fix → verification state*.

---

## 3. The recurring calendar

### Daily — automated

`extraction-health` runs at **04:17 UTC** (`cron: '17 4 * * *'`). It:

- installs the current `yt-dlp`,
- runs `:tools:playback-probe:offlineProbe` (deterministic, ADR-006) then the
  live probe,
- uploads `rot-drill-<run_id>` (14-day retention),
- on failure **opens or comments on** `[rot-drill] Live extraction probe failed`
  with the last 12 KB of log,
- on success **closes** an open rot-drill issue automatically.

Nothing to do on green.

### Daily — one human glance **[PROPOSAL]**

Check whether a `[rot-drill]` issue is open:

```bash
gh issue list --state open --search 'rot-drill in:title'
```

The schedule has silently missed windows before (silent from 2026-09-07 to
2026-09-17 — see `docs/runbooks/rot-drill.md`). **A missing run is not a green
run.** If no scheduled run appeared in the last 24 h, dispatch one manually.

### Per release candidate — human, mandatory

**[MANDATE]** Confirm stream health for *this* commit. The daily schedule does
not prove the candidate. An agent **cannot** do this: `workflow_dispatch`
returns **HTTP 403** for agent tokens (re-verified 2026-09-17).

Actions → `extraction-health` → **Run workflow** → branch `main`. Watch the
`probe` job (≈5–12 min). Then append exactly one line to
`docs/verification/14-release.md` under "Live evidence log" → "Rot-drill":

```
- extraction-health <run_id> (<YYYY-MM-DD HH:MM UTC>, main@<sha>): GREEN — <note>
```

**That line is the S1 exit criterion. No tag without it.**

### Weekly **[PROPOSAL]**

- Dependency drift: the stack is version-pinned in module build files (there is
  **no `gradle/libs.versions.toml`**). Upgrade **one at a time**; CI is the
  check. RISK_REGISTER lists Ktor/Coil/Compose MP regression as the live risk.
- `THIRD_PARTY.md` still matches reality — every dependency's name, license and
  version. GPL compliance drift is a standing risk; the project is GPL-3.0
  *because* it reuses GPL extractors.
- Open PRs/issues that are rotting (`#54`, `#60`, `#63`, `#14` as of this
  writing).

---

## 4. When the rot drill goes red

**[MANDATE]** This is the Doctrine, not an incident to improvise around.

**Step 1 — read the verdict correctly.** Open the run → `probe` job →
*Run playback probes*.

| Signal | Meaning |
|---|---|
| `LOGIN_REQUIRED` / "Sign in to confirm you're not a bot" | YouTube gating the **GitHub runner's datacenter IP**. *Not* proof of user breakage. Verify residential playback (a phone on mobile data) before treating it as rot. |
| Anything else | Probable extractor rot. |
| Grey / skipped probe job | Run never started (concurrency cancel). Re-dispatch. |
| Red run with **zero jobs** | Trigger noise, **never** a verdict. This repo has a history of phantom 0-job push runs from wedged workflow registrations. |

**Step 2 — respond on the pre-agreed ladder** (RISK_REGISTER):

1. Client bump → wave maintenance → **patch release within 72 hours**.
2. Upstream fix slow (>14 days, all engines) → **kill-switch: stop and decide
   with the owner.**
3. ADR-007 (Android stream attestation / PO-token options) is **contingency
   only** — implement solely on triggers T1/T2 from MASTER_PROMPT §2 **and with
   the owner's explicit go-ahead**. Never pre-emptively.

**Step 3 — write it down.** Incident → `DEBUG_LOG.md`. If the response changes
a locked decision → new ADR in `docs/decisions/` **before** the code.

**Never** hand-roll extraction, and never "fix" YouTube breakage by silently
changing the locked stack.

---

## 5. Changing code

**[MANDATE]** The flow is: session branch → small commits → PR → CI green →
owner merges.

- **One agent at a time.** Never assume a parallel session will finish, rebase
  or review. Rebase onto current `main` yourself; leave the tree reviewable.
- **Ship running code every work unit.** Docs are written *after* the code they
  describe, from the code. A unit with beautiful docs and nothing on
  hardware/CI is a **failed unit**.
- **Tests after implementation, before commit.** No test that tests nothing.
  **Mutation-prove any CI step that must execute** (precedent: PRs #68/#71).
- **No stubs, no TODO-left-in-production, no "compiles = done."**
- **Never merge without the owner saying so. Never force-push. Never rewrite
  history.**
- If a step exceeds ~30 minutes without visible progress: split it or report
  back. Silent grinding is forbidden.
- A phase ends with its verification log in `docs/verification/NN-*.md`.

### What CI actually runs (`.github/workflows/ci.yml`)

On every push to `main` **and** `arena/**`, plus every PR. These eight steps
are what `main` runs as of `33e94b0`:

1. `python3 -m unittest discover -s scripts -p 'test_*.py'` — packaging and
   fixture helper tests
2. `./scripts/check_powershell_syntax.ps1`
3. `:shared:jvmTest` — domain, queue, parsers, resolvers
4. `:app-android:testDebugUnitTest` — Robolectric
5. `:app-android:assembleDebug`
6. `:tools:playback-probe:compileKotlin` (also compiles `:app-desktop` when
   running under GitHub Actions)
7. `:app-desktop:compileKotlinJvm`
8. `:app-desktop:jvmTest`

> Branch `arena/01a0b342-dhun` adds two more steps (`:tools:web-bridge`
> compile, web client jsdom tests). They belong to the dismissed web work and
> are **not** on `main` — treat this list as the baseline unless that work is
> formally adopted.

**[PROPOSAL]** Any new module gets its own named compile step here. A module
that CI never names is a module nobody verifies — that is exactly how
`:tools:web-bridge` had to be added on 2026-09-18.

Other workflows: `build-apk.yml` (artifact-only debug APK per push/PR),
`test-release.yml` (the rolling `test` pre-release, and manual build-only runs
on session branches), `extraction-health.yml` (the drill).

### Reading CI results from a sandbox

Agent sandboxes **cannot download Actions log archives** (the
results-receiver host is network-blocked). Two consequences, both already
handled in this repo:

- `settings.gradle.kts` prints the Gradle failure cause chain as `::error::`
  workflow commands, so failures surface as **check-run annotations**, which
  *are* reachable via the REST API.
- To read them:
  ```bash
  gh api repos/99ggprooo00-code/DHUN/commits/<sha>/check-runs --jq '.check_runs[].id' \
    | xargs -I{} gh api repos/99ggprooo00-code/DHUN/check-runs/{}/annotations
  ```

---

## 6. Cutting a release

### The rolling test build (unchanged by any of this)

**[MANDATE]** Exactly **one** rolling pre-release exists — tag `test`, assets
`dhun-test.apk` and `dhun-test.msi`, auto-replaced on every push to `main`.
Stable URLs:

```
https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.apk
https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.msi
```

These are **debug/test-grade**. Not store-ready, not stable. No versioned
releases for unfinished builds.

### Version numbers — three separate things

| Thing | Where | Rule |
|---|---|---|
| App version | `versionCode` / `versionName` in `app-android/build.gradle.kts` | Bump deliberately. Currently `5` / `0.1.4` — **independent of the `v0.1.0` tag.** |
| Release tag | `v0.1.0` etc. | Only when S6 acceptance is met and the owner says go. |
| **MSI ProductVersion** | Allocated by CI via `scripts/installer_version.py` from the workflow run counter | **An installer sequence, NOT the app version.** Must strictly increase on every build, never be reset. The `upgradeUuid` (`31ddb86b-9666-4071-b11c-45f16fa4682d`) stays stable **forever**. |

Mixing these up breaks Windows upgrades silently. `scripts/stage_msi.ps1`
applies the upgrade-data policy *before* checksums; raw
`:app-desktop:packageMsi` output alone is **not** distribution-ready.

### Signing

**[MANDATE — and a trap]** The APK/AAB are signed with the **committed public
throwaway key** `app-android/keystores/dhun-test.p12` (passwords `android`,
alias `androiddebugkey`). It exists so CI builds share a signature and can
update an existing install.

- It is **not** a store key. The `release` build type has **no** signing config.
- **Do not mint a Play/Store key from this file.**
- Anyone with repo access can sign a same-key APK — only install artifacts
  from the repo's own CI URLs.
- Play signing and Authenticode are **open owner decisions**. Until made,
  artifacts stay test-grade and any release stays DRAFT-private.

### Release checklist

1. Close the gates in `docs/verification/14-release.md`: green live rot-drill
   verdict on this commit, Android + Desktop soaks, clean-target installs of
   all three artifacts.
2. Actions → **test-release** → Run workflow → ref `main` →
   `build_release_candidate` ticked; `publish_v010_draft` only when the private
   DRAFT needs refreshing. `build_only` stays at its default.
3. Verify the draft's assets and provenance (`.sha256`,
   `.build-info.json` with source SHA, run URL and MSI ProductVersion).
4. Owner go-ahead → `gh release publish v0.1.0`.
5. Finalize `CHANGELOG.md` (drop DRAFT markers, add the compare link), review
   `KNOWN_LIMITATIONS.md` and `THIRD_PARTY.md`, record verification evidence.

The draft job refuses to touch an already-**published** release. That guard is
deliberate; do not work around it.

---

## 7. Hardware verification never goes away

CI cannot prove audio. **[MANDATE]** Hardware gates stay open until a device
closes them.

- `docs/runbooks/s3-hardware-checklist.md` — the device pass
- `docs/verification/windows-candidate.md` — the short Windows candidate guide;
  use it whenever a successful build link is supplied
- `docs/verification/NN-*.md` — one log per phase
- Hosted-Windows install-over checks are **not** proof of a real upgrade,
  launch, audio, or hardware behaviour. They are sentinel checks.

A feature is not "done" because CI is green if its acceptance criterion was a
device.

---

## 8. Who can do what

Some things are structurally impossible for an agent in this repo. Knowing the
boundary up front stops a session burning an hour on a 403.

**Owner only**

- `workflow_dispatch` on any workflow (**HTTP 403** for agent tokens)
- Publishing a release, tagging
- Merging a PR
- Hardware passes and soaks
- Approving an ADR / amending MASTER_PROMPT
- Signing decisions (Play key, Authenticode)
- Enabling GitHub Pages or any new repo setting (agent token is `admin: false`)

**Agent**

- Branches, commits, PRs from a session branch
- Reading CI verdicts and check-run annotations
- Writing code, tests, docs, ADR drafts, verification logs
- Updating `.ai/` state files

---

## 9. Sandbox facts (re-verified 2026-09-18)

- **No JDK.** `java` is absent, `JAVA_HOME` unset, no `~/.gradle` cache.
  **CI is the compiler.** Do not plan local Gradle work.
- **Egress is mostly blocked.** `repo1.maven.org`, `api.adoptium.net`,
  `music.youtube.com` and `*.github.io` all fail with `SSL_ERROR_SYSCALL`.
  `api.github.com` works (so `gh` works) and `registry.npmjs.org` works.
- `apt-get` is not usable (no root).
- Toolchain restore steps live in `docs/development/sandbox-toolchain.md`;
  `scripts/restore-toolchain.sh` exists. Workspace `.cache` and `.git/config`
  are wiped between sessions.

**Consequence:** anything that needs compiling, or any live YouTube call, is
verified in CI or on hardware — never locally. Say so plainly rather than
presenting unverified work as done.

---

## 10. Backlog governance (where new ideas go)

**[MANDATE]** The v2 backlog is explicit in MASTER_PROMPT §7 — "Explicitly NOT
in S1–S6":

> Web/PWA · Android Auto · Cast · cross-device sync · optional cookie sign-in
> (#60) · EQ beyond S4 · widgets beyond Quick Play · security hardening (#63) ·
> store releases · v1.0 GA · any ADR-007 implementation (contingency only)

**[PROPOSAL] The adoption path for any item on that list:**

1. Owner states they want it. (Not the agent inferring it from a request.)
2. Amend `MASTER_PROMPT.md` — §3 (platforms/requirements) and §7 (remove from
   the "NOT in" list). §8 rule 3 explicitly allows updating the prompt when
   reality contradicts it; §9 puts the prompt *below* code and ADRs, so the
   amendment must be honest about the change.
3. Write the ADR in `docs/decisions/` — only for decisions with real
   alternatives **and** a blocking defect scenario.
4. Owner approves the ADR.
5. Then and only then, code.

Skipping to step 5 is what happened with the web client on 2026-09-18. It cost
a session, produced work that contradicts a locked decision, and is recorded
in `KNOWN_LIMITATIONS.md` as **not approved scope**. The technical finding was
sound — `PROBLEMS_AND_FIXES.md` P7 had already predicted that a web client
"requires a self-hosted proxy component, which is a separate, explicit project
decision" — but the decision was never made. **The process failure was
skipping steps 1–4, not the architecture.**

---

## 11. Hygiene

**[MANDATE]**

- No secrets, no device identifiers, no captured credentials or personal data
  anywhere in the repo. Test fixtures are sanitized JSON.
- Every dependency (name, license, version) listed in `THIRD_PARTY.md`.
- Build output never committed.
- License is **GPL-3.0** — required for legitimate reuse of GPL extractors
  (NewPipe Extractor). Do not add an incompatible dependency.
- Commits small and meaningful.

---

## 12. One-page version

```
BEFORE CODE     ROADMAP (CURRENT ACTIVE TASK) → DEBUG_LOG → git log → MASTER_PROMPT
DAILY           extraction-health 04:17 UTC. Glance at [rot-drill] issues.
                A missing run is not a green run.
RED DRILL       Read verdict → is it LOGIN_REQUIRED (runner IP) or real rot?
                Client bump → wave maintenance → patch ≤72h.
                >14 days all engines → kill-switch, stop, ask.
                ADR-007 only on T1/T2 + explicit owner OK.
CHANGING CODE   Branch → small commits → tests → PR → CI green → owner merges.
                Never merge, force-push, or rewrite history.
NEW MODULE      Add a named CI compile step, or it is unverified.
RELEASE         Human dispatch extraction-health on main → record the line in
                docs/verification/14-release.md → gates → owner says go → tag.
SESSION END     Update ROADMAP + KNOWN_LIMITATIONS (+ DEBUG_LOG on incidents).
                Done = pushed + CI green + hardware-verified where required.
NEW PLATFORM    Owner asks → amend MASTER_PROMPT → ADR → owner OK → code.
                In that order. No exceptions.
```
