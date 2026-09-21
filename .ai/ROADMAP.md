# CURRENT ACTIVE TASK

Updated **2026-09-21** · session **`arena/01a0c174-dhun`** · `main`/`origin/main` **`4d693ce`** (PR #102 merged 2026-09-21T01:20:08Z; beneath it #101 `2c619b9` defect 4, #100 `80e94bf` defect 3 v1, #99 `870d3bc` defect 2, #98 `b102c78` defect 1, #97 `8635851` S1 close).

**Phase: Stage S3 in progress — all four Round-2 device defects now have MERGED, CI-green fixes; blocked only on the user's hardware re-test.** Round 2 (user, build `8635851`, transcribed in `.ai/HANDOFF_NEXT_SESSION.md`) banked: notification + lock-screen buttons, Windows downloads, lyrics loading, desktop EQ, full-player visual OK. It opened four defects; every one is fixed: (1) Android downloads — #98, `hasSeenWork` latch killed the start/stop race + FGS-policy try/catch; (2) play-radio restart — #99, current track filtered out of the `/next` panel in `loadRelated` (fixes the Related tab too); (3) shuffle — #100 + corrective **#102** (this session): the visible queue now IS the shuffled play order on both platforms, engine-driven advances re-sync the queue cursor (`syncCurrent`), visual-row indices are translated (`playAtDisplay`/`removeAtDisplay`/`moveInDisplay`), and queue mutations preserve the user's arranged order instead of re-shuffling; (4) lyrics UI — #101: synced base size up to `headlineSmall` with bold-accent active line, unsynced bumped to readable `titleMedium` (no auto-scroll is possible without timestamps — by design).

**Concurrency incident (user must arbitrate):** the previous session (`arena/01a0c154-dhun`) was still live when this one started and merged #100/#101 mid-flight (00:58Z / 01:07Z) while this session was reviewing #100. #100's three real holes (stale Android highlight after natural advance; desktop source-vs-display index mismatch → wrong-track taps under shuffle; rebuild-on-mutation reshuffle) lived on main ~22 minutes until #102 closed them. This session rebased onto the moved main and merged #102 as the corrective follow-up. The one-agent rule (MASTER_PROMPT §8.1) needs the user to confirm the other session is dead before the next session starts.

**CI evidence (this session, exact final heads):** PR #102 @ `4b8204c`: build ✓ (35550218902) · build-and-test ✓ incl. 9 new QueueManagerTest cases (35550218900) · apk ✓ (35550218899) · msi ✓. Post-merge on `4d693ce`: CI 35550567894 ✓ · Build APK 35550567913 ✓ · test-release 35550567905 ✓. Rolling `test` republished **2026-09-21T01:24:37Z** targeting **`4d693ce`** — APK **17,948,508 B** / MSI **112,861,184 B**. (First #102 CI run failed Android compile — missing `queueManager` field+import, caught by the annotations API, fixed in `4b8204c`; see DEBUG_LOG.)

**Exact next step: the user re-tests on hardware** against the build identity above, following the numbered script in `.ai/HANDOFF_NEXT_SESSION.md` (Android 1–9: download + offline, radio, visible shuffle + highlight tracking + tap-correctness + play-next, lyrics, lock-screen regression, sanity; Windows 10–12: shuffle set, radio, tray sanity). Agent-side S3/S6 remainder is unchanged and waits on those results or is agent-executable docs/CI work: 30-min soaks, rotation/process-death, Settings/theme persistence checks, Windows native column, then S2 leftovers (`extraction-health.yml` trigger retirement needs a live drill watch; `14-release.md` stale merge-chain section; ubuntu-latest/Node-20 hygiene). Issue #14 still needs the user to close it (agent 403 on issue writes).

---

## Re-baseline record (2026-09-16 — why this file was rewritten)

The old ROADMAP was 122 KB of nested session snapshots accurate for
their day but unusable as a plan: phase tables referenced builds from
`be51d7d`, the "trajectory to Phase 30" candidate numbers collided
with shipped work (candidate "16 = audio cache" vs merged "Phase 16 =
UI slice"), and several standing claims contradicted the repo (e.g.
"main is red", "`main` build health" rows, rot-drill noise cited as
verdicts). The re-baseline audited live code, CI, `gh` (PRs #53/#54
open; issues #14/#60/#63 open; PRs #1–#71 merged), ADRs 001–006, and
all `.ai/` docs, then:

- Marked Phases 01–14 as **code-merged, hardware gates open** (no
  re-implementation — ever — from old phase text).
- Recorded shipped-but-unplanned work as **Phase 15 (extras)** and
  **Phase 16 (2026-09-16 UI slice)**, both merged.
- Retired the v2 "candidate 15–30" numbers; remaining ideas moved to
  the **v2 backlog** (§8, unnumbered).
- Defined completion as sequential single-agent **Stages S1–S6**
  (user decision 2026-09-16: one agent at a time — parallel agents
  did not work out).
- Reclassified PO-token/BotGuard/InnerTubeX research (open PR #54) as
  **contingency reference**, gated by triggers T1/T2 (see
  MASTER_PROMPT §2) — not backlog.

**History preservation:** nothing was deleted from history. The full
old snapshot text lives in this file's git history (`git log -p --
.ai/ROADMAP.md`); per-change history lives in `CHANGELOG.md`; merge
evidence lives in `git log --merges`. What was removed from *this
file* is duplicated day-to-day session chatter, not facts.

---

# ROADMAP — live status

Rules (permanent, from the user):
- **CURRENT ACTIVE TASK goes at the very top** — file worked on, last
  error, exact next step.
- Mark **exactly** which steps are complete. **Done = pushed + CI green +
  (where the phase says so) on-hardware verified.** Unpushed or
  CI-unverified work is NOT done, no matter how good it looks locally.
- Update this file every session.
- **Pre-push / pre-merge ritual (every time, no exceptions — user rule
  2026-09-05):**
  1. Verify state **on GitHub, not locally** (`git fetch`, `gh pr checks`,
     `gh run list`): which steps are actually pushed and CI-green.
  2. Rewrite **CURRENT ACTIVE TASK** at the very top: exact file(s) ·
     last error (or "none") · exact next step.
  3. Mark the status tables from step 1's evidence only.
  4. Commit **everything** (no dirty tree left behind) and push to the
     session branch. **Also push any unpushed commits** found on the
     branch — nothing gets left behind. (Single-agent era: there are no
     "previous session branches" to carry over from; if one appears,
     reconcile it explicitly, never auto-merge.)
  5. After the push, re-check CI and update the marks again if the
     status changed (a commit can't truthfully mark *itself* as pushed
     and green — the ROADMAP always lags the push by one small commit).
  Only then open/merge the PR. **Never merge until the user says so.**
  **Merge-last (2026-09-17, directive #14):** merging ends the
  session's GitHub connection — do everything (code + docs) BEFORE the
  merge; same-turn post-merge verification OK, post-merge turns are
  not. A PR must be complete (code + docs + verdicts) before its
  merge; post-merge CI / rolling-`test` checks happen in the same turn
  as the merge, never pre-claimed.

---

## 1. Instruction audit — standing user directives (permanent)

Everything below is a standing directive from the conversation (kept
here so no session loses it; the user asked on 2026-09-05 that ALL
instructions be stored permanently in `.ai/`).

| # | Directive | Where it's enforced |
|---|---|---|
| 1 | **Boot protocol:** no code before boot — ROADMAP → DEBUG_LOG → `git log` → MASTER_PROMPT; reply = phase summary + exact next step + permission ask. | `.ai/README.md` boot protocol |
| 2 | **"do it accordingly" = execute the documented plan autonomously**, no multiple-choice questions. | Session behavior |
| 3 | **ROADMAP maintenance:** CURRENT ACTIVE TASK at top; exact step marks; **unpushed/unverified = undone**; pre-push/pre-merge ritual. | Rules block above |
| 4 | **Code-first** (MASTER_PROMPT AI rules): no stubs, no TODOs in production, hardware verification before a stage is done, small commits, update ROADMAP + KNOWN_LIMITATIONS each session, report stalls (>30 min no progress), ADR before changing a locked decision. | `.ai/MASTER_PROMPT.md` §8 |
| 5 | **Rolling test release policy** (2026-09-01): exactly ONE release tagged `test` (`dhun-test.apk` + `dhun-test.msi`), every push to main REPLACES it, no version numbers/history for unfinished builds. Stable URLs never change. | `.github/workflows/test-release.yml` |
| 6 | **Single agent** (2026-09-16): no parallel agents; stages are sequential. | MASTER_PROMPT §7/§8 |
| 7 | **Re-baseline hierarchy** (2026-09-16): code > tests/CI > accepted ADRs > master prompt > roadmap > historical research > old plans. Never implement paper that contradicts verified behavior. | MASTER_PROMPT §9 |
| 8 | **Compilation-warning policy:** "zero warnings" = address warnings surfacing in CI annotations for the compiled modules; library-internal warnings DHUN cannot fix go to KNOWN_LIMITATIONS, not workarounds. | CI annotations |
| 9 | **Autonomy boundary (2026-09-16 handoff):** act without asking for routine permission; merge routine fixes ONLY after required CI is green on the exact final head; verify post-merge CI and rolling `test` publication. NOT authorized: signing decisions, the `v0.1.0` tag, stable-release publication, bypassing S1/S3. | This session's behavior |
| 10 | **Document before merge, not after:** before every merge update CURRENT ACTIVE TASK + KNOWN_LIMITATIONS with the changes, verified CI evidence, remaining blockers and exact next step; documentation rides in the PR; CI green on the PR's final head (including the docs commit) before merging; post-merge results recorded afterward in the PR comment and reconciled in the next documentation update — never pre-claimed. | Rules block above |
| 11 | **Tool discipline:** one asserted patch per file per block; grep-verify every edit; read the full diff against current main before every push. | Session behavior |
| 12 | **Blocked work:** when blocked on hardware, unavailable GitHub actions, or release/signing decisions, give an exact step-by-step guide and continue only genuinely unblocked work permitted by the stage gates; never invent features or mark blocked gates complete. | rot-drill.md / s3-hardware-checklist.md |
| 13 | **Sandbox capability rule:** check capabilities ONCE per session (JDK, `gh`, egress). If no JDK: CI is the compiler — inspect failed step names via `gh run view --json jobs` and diagnose through code review; never claim local tests ran; never infer an exact failure from a step name alone. | KNOWN_LIMITATIONS |
| 14 | **Merge-last (2026-09-17, user):** merging ends the session's GitHub connection — do everything (code + docs) BEFORE the merge; same-turn post-merge verification OK, post-merge turns are not. | Rules block above + session behavior |

**Handoff record (2026-09-16, session `arena/01a0ac91-dhun`):** PR #73
closed unmerged, superseded by PR #74, merged as `c5b1793` (S4/S5
changes, palette-test fix, Android AudioManager API fix). Post-merge CI
(35135429018 / 35135429102 / 35135429240) and rolling `test`
publication (18:41:19 UTC at exactly `c5b1793`, all four assets)
verified — evidence in PR #74's final comments. S1 remains blocked on
the operator dispatch (`workflow_dispatch` 403 re-verified this
session); the rot-drill schedule breakage and 0-job push noise are now
fully diagnosed (see CURRENT ACTIVE TASK + KNOWN_LIMITATIONS). S3 needs
real-device evidence; S6 needs explicit user go-ahead. No v0.1.0 tag,
no stable release, no signing decision was made or is authorized.

**Handoff record (2026-09-17, session `arena/01a0ac91-dhun` →
`arena/01a0acb6-dhun`, "handoff v2"):** PR #75 merged as `bcb65cc`;
post-merge CI (35163768301 / 35163768295 / 35163768291) and rolling
`test` publication (2026-09-16T23:46:36Z at exactly `bcb65cc`, all
four assets) verified in the PR's post-merge comment. The closed
session left one unpushed local commit (771552a — post-merge ROADMAP
reconciliation); it never reached GitHub and is re-applied by the
successor's first PR. New user facts: the user cannot perform manual
GitHub steps (only merge clicks + one Run-workflow click once the
button returns); rot-drill is absent from the Actions UI while
registry-active (id 348098190) — the live-verdict path is
re-registration → UI button → user click, with a GitHub support ticket
as fallback. Branch cleanup requested and executed (17 fully-merged
branches deleted; unmerged-unknowns kept and reported). The
re-registration workflow edit was approved 2026-09-17 (agent merges on
green CI). S3/S6 gates unchanged; no v0.1.0 tag, no stable release, no
signing.

**Handoff record (2026-09-17, session `arena/01a0aff7-dhun`, attempt 4 SUCCESS):**
PR #88 merged as `3c593fb` ~15:40 UTC, new workflow id 360655315 extraction-health
active with correct name (not file path), no phantom push on merge (unlike
360227450 which fired 35241808266). Post-merge CI: Build APK 2m46s PASS,
CI 5m23s PASS, test-release 6m33s PASS, rolling test republished 15:46:15Z apk /
15:47:22Z msi all four assets. Old wedged 360227450 still active, to be deleted
in next PR. Dispatch 403 re-verified — user must click Run workflow on
extraction-health. Fable key provided, sandbox blocked, AI DJ rejected per user
"No need that" — code reverted.

---

## 2. True progress (exactly what is proven, nothing more)

Legend: ✅ done (pushed + CI green + verified where required) ·
🟨 code merged + CI green, **hardware verification open** ·
⬜ not started · 🔴 blocked/open problem.

**`main@d99060e` (2026-09-20, PR #96): post-merge CI and rolling release green**
(CI **35523474108**, Build APK **35523474088**, test-release **35523474032** —
all success on the merge SHA; PR #94's `7304abb` CI 35518928489/35518928490/
35518928487 was green in turn). Rolling `test` targets exactly `d99060e`
(published **2026-09-20T16:46:20Z**) with `dhun-test.apk` **17,948,508 B**,
`dhun-test.msi` **112,861,184 B** and both `.sha256` sidecars (sizes unchanged
since `6f7fa48` — every merge since has been docs-only). Workflow **360655315
`extraction-health`** is active and correctly named; scheduled runs
**35421383687** (2026-09-19) + **35489268023** (2026-09-20) both classified
`ENVIRONMENT_BLOCKED` on `main@6f7fa48` (annotation-verified via
`check-runs/<job_id>/annotations` + the job-steps API: steps 1–10 success,
issue steps skipped, only the intentional gate step failed with exit 2); the
first scheduled run on `main@d99060e` is due 2026-09-21 04:17 UTC.

**S1 is closed GREEN (2026-09-20).** The missing half arrived as user evidence
on this exact build: Android + Windows installs from the rolling `test`
release over home WiFi (no VPN), **4 songs played — audible, position
advancing, no failures**, Android audio continued with the screen locked;
build identity confirmed against the release API by publish time (22:16 IST =
16:46:20Z) and both byte sizes. Because `compare/6f7fa48...d99060e` contains
**9 documentation files and 0 code files**, the runner's `ENVIRONMENT_BLOCKED`
and the user's successful playback are the *same extraction code* on two
networks — datacenter gating, not product rot. Trigger **T1 is disproven**;
ADR-007 stays PROPOSED. What is still unproven and belongs to S3/S6: 30-minute
soaks, notification/lock-screen *controls*, downloads + offline, lyrics,
Settings/theme persistence, Android EQ, and the Windows native column.

### 2a. Build history — Phases 01–16 (ALL code-merged; do not re-implement)

| # | Phase | Status | Evidence (code + CI; hardware open) |
|---|---|---|---|
| 01 | Extraction spike | 🟨 | Probe PASS (search/resolve/audio-bytes/related); ADR-001 own-client interim (`OwnClientStreamResolver`, `tools/playback-probe`) |
| 02 | Provider & domain core | 🟨 | `MusicProvider`, InnerTube metadata client, resolver chain, `QueueManager`, parsers + fixture tests green |
| 03 | Android skeleton + playback | 🟨 | Media3 service, lock screen, FGS, 403 recovery, segment cache, fast-fail diagnostics (`app-android/.../playback/`); **user reports playback works well** |
| 04 | Desktop skeleton + playback | 🟨 | vlcj player + whole-track cache + prebuffer (`DesktopDhunPlayer.kt`); **user reports playback acceptable** |
| 05 | Data layer | 🟨 | SQLDelight schema v3, repos, use cases, now-playing restore; tests green |
| 06 | Design system | 🟨 | Tokens, real-blur glass, Coil artwork, color extraction, catalog screen |
| 07 | Home & Search | 🟨 | `HomeScreen`/`SearchScreen` + ViewModels, suggestions, filters, overflow; merged PR #6 |
| 08 | Player UI | 🟨 | MiniPlayer + immersive FullPlayer + coordinated Queue/Related sheet (PRs #66/#67); merged PR #7 |
| 09 | Artist/Album/Playlist | 🟨 | Browse parsers (fixture-tested) + pages + local CRUD; merged PR #7 |
| 10 | Library & History | 🟨 | Tabs, favorites, history, RecordPlay; merged PR #8 |
| 11 | Lyrics | 🟨 | LRCLIB + YTM + cache + synced UI; merged PR #8 |
| 12 | Desktop native | 🟨 | Tray, shortcuts, SMTC code, close-to-tray, MSI, single-instance (PR #42), jump lists (PR #47); mini-player removed (ADR-004) |
| 13 | Android polish | 🟨 | Edge-to-edge, shortcuts, rotation restore + Robolectric tests (PR #43), 840dp rail + two-pane (PR #45); soak never run |
| 14 | Robustness + rot-drill + release prep | 🟨 | Error taxonomy, caches, rolling-`test` pipeline, v0.1.0 DRAFT prep (PR #40); **drill schedule broken since 09-07, fixed 09-17 via extraction-health.yml id 360655315** |
| 15 | Beyond-plan extras (merged) | 🟨 | ADR-006 downloads (PRs #33–#39), EQ desktop (PRs #46/#48), widgets (PRs #46/#61/#64 — Quick Play only), themes dev-only (PR #49), player immersion (PRs #41/#51), recommendations (PR #52), playback diagnostics (PR #50), visitorData/sts (PRs #55–#57) |
| 16 | UI/platform repair slice (merged 2026-09-16) | 🟨 | Now-playing backdrop, Android tab BACK, Windows rails + fling, Android<12 guard, named CI steps (PRs #66–#71) |

Hardware/acceptance checklists per phase: `docs/verification/01–14`.
All are 🟨/⬜ — closing them is Stage S3.

### 2b. Completion — Stages S1–S6 (sequential; the actual remaining project)

| Stage | Objective | Status | Gate |
|---|---|---|---|
| **S1** | Restore the rot drill; fresh live verdict; issue #14 reflects reality | ✅ **GREEN 2026-09-20.** Drill fires daily (runs **35421383687**/**35489268023** on `6f7fa48` → `ENVIRONMENT_BLOCKED`, honest runner-gating). **Residential evidence supplied by the user on `main@d99060e`** (rolling `test` published 2026-09-20T16:46:20Z = 22:16 IST; APK 17,948,508 B ≈ "17 MB", MSI 112,861,184 B ≈ "108 MB"; home WiFi, no VPN): installed on Android **and** Windows, searched, **4 songs played — audible, position advancing, zero failures**, Android audio continued on the locked screen. `compare/6f7fa48...d99060e` = **9 files, all docs, 0 code** → runner block and residential success are the *same code*, so the block is a datacenter-IP artifact and **T1 is disproven**. | **MET:** ≥1 scheduled verdict on current `main` **+** a playback result outside the runner. |
| **S2** | Architectural cleanup (workflow-trigger retirement + CI hygiene, PR #54 disposition, stale `14-release.md` body) | 🟨 **unblocked; mostly already clean.** Re-audited 2026-09-20: no `HarnessScreen`/`DesktopHarness*` in any `*.kt` (only a private `Harness` test class + 3 comments — legitimate), no root `agent-*-status.md`, `docs/decisions/README.md` complete (ADR-001…006 + PROPOSED 007). Remaining: inert `push: [arena/01a0b224-dhun]` trigger in `extraction-health.yml` (edit re-registers the workflow — registration wedged twice, ticket #4765894, so do it with a live drill watch), `ubuntu-latest`→Ubuntu 26 (2026-10-19) + Node-20 action deprecations, PR #54 (user: no action), stale "merge chain ends at PR #32" section. | CI green; zero dead screens; PRs resolved; drill still firing after any workflow edit |
| **S3** | Hardware verification round 1 (core loop both platforms, signed checklists) | ⬜ | `docs/verification/` checklists signed with build SHAs |
| **S4** | Settings surface + themes/EQ wiring (keys-without-UI gap) | 🟨 code merged + CI green (PR #74); S4 hardware boxes ride in S3 | Every shipped key reachable or removed; EQ decision recorded |
| **S5** | Testing + hardening (contrast fix, dep audit, THIRD_PARTY review) | ✅ merged (PR #74): CI green; contrast 4.66:1 asserted both schemes (`DhunThemeContrastTest`); dep audit all-HOLD with post-tag upgrade order (`.ai/DEPENDENCY_AUDIT.md`); THIRD_PARTY reviewed | CI green; contrast ≥4.5:1 or re-recorded exception; review logged |
| **S6** | Release v0.1.0 (soaks, clean installs, signing decisions, tag) | ⬜ | ALL S1–S5 gates + user go-ahead → tag + publish |

Full tasking per stage: `MASTER_PROMPT.md` §7. Execution order is
fixed: S1 → S2 → S3 → S4 → S5 → S6. S3 needs the user (devices); S1
needs the user (one Run-workflow click once re-registration restores
the UI button — agents get 403, re-verified 2026-09-17; the user
cannot API-dispatch). Note: S4/S5 CODE was executed out of stage order
with user authorization and merged in PR #74 (2026-09-16); the S4
hardware boxes ride in S3. Remaining work order is unchanged:
S1 → S2 → S3 → S6.

---

## 3. Board hygiene (PRs + issues — verified via `gh` 2026-09-20)

| Item | State | Decision |
|---|---|---|
| PR #53 `docs: reconcile extraction playback research handoff` (+182/−513, would wipe this file from a stale base) | **CLOSED unmerged 2026-09-16** (session `arena/01a0ac91-dhun`, per this decision) | Superseded by the re-baseline; the research track continues in open PR #54. Nothing in it survived. |
| PR #54 `docs: PO-token/InnerTubeX research + ADR proposal` (+326/−1, ADR-007 PROPOSED) | OPEN, research-only | **Keep as contingency reference** (merge docs-only with ADR-007 staying PROPOSED, or leave open — user's call). NEVER implement without trigger T1/T2 + explicit go-ahead. (A labeled agent test comment "test-ping (delete me)" from 2026-09-16 could not be deleted by the agent token — safe to remove manually.) |
| PR #91 `docs(s1): reconcile extraction-health handoff at main 33e94b0` | **MERGED as `6f7fa48`** (2026-09-18T14:06Z) | Home continuation request-contract repair + S1 docs; post-merge CI 35354234754 / Build APK 35354234582 / test-release 35354234760 green; rolling `test` retargeted at `6f7fa48`. Merge claimed the repair + CI only — not live playback acceptance. |
| PRs #92 / #93 `docs(s1): reconcile …` | **MERGED** — `39b8748` (2026-09-20T07:10:47Z) and `fabeb5f` (2026-09-20T13:29:15Z) | Both docs-only: post-merge CI green on each merge SHA (35496174865/35496174870/35496174877 and 35513643996/35513643853/35513643918); rolling `test` republished at each SHA with unchanged asset sizes. Neither claims live playback acceptance. |
| PR #94 `docs(s1): reconcile evidence-path docs with code at main fabeb5f` | **MERGED as `7304abb`** (2026-09-20T15:14:02Z); post-merge CI 35518928489 / Build APK 35518928490 / test-release 35518928487 green on that SHA and rolling `test` retargeted at `7304abb` (15:19:07Z) | Docs-only correctness pass: probe README output protocol + drill file reference, `rot-drill.md` state/dispatch/readout recipe, S1 user-guide build identity + error-capture affordances, `14-release.md` baseline, MASTER_PROMPT §5/§7 drill references. Still no code, workflow or probe-semantics change; the merge claims docs correctness only — not live playback acceptance. |
| PR #96 `docs(s1): cherry-pick #95 ledger re-pin` | **MERGED as `d99060e`** (2026-09-20T16:40Z); post-merge CI **35523474108** / Build APK **35523474088** / test-release **35523474032** green, rolling `test` retargeted at `d99060e` (published 16:46:20Z) | Docs-only. **This is the build the user tested for S1** — the release the user downloaded ("~6 hrs ago, 10 pm", 17 MB / 108 MB) resolves to exactly this SHA. |
| PR #88 `S1 attempt 4 — new workflow extraction-health.yml` | **MERGED as `3c593fb`** (2026-09-17T15:40Z) | Attempt 4 SUCCESS — id 360655315 healthy, name=extraction-health, no phantom push. Old wedged 360227450 fired 35241808266 phantom on same merge. |
| Issue #14 `[rot-drill] Live extraction probe failed` | OPEN; scheduled runs **35421383687**/**35489268023** on `main@6f7fa48` classify `ENVIRONMENT_BLOCKED` (runner bot-gating, no audio bytes validated); the workflow correctly did not update the issue (issue step fires on `FAIL` only; last update 2026-09-18) | **Now closable by the user** — the 2026-09-20 residential evidence on `main@d99060e` (4 songs audible on Android + Windows, docs-only diff from the probed `6f7fa48`) shows the probe failure is runner-network gating, not extraction rot. The agent token cannot comment on or close issues (403), so the user closes it with the S1-green note; see the handoff in this session's summary. |
| Issue #60 `Guest-First + Optional YTM Login` | OPEN (future plan, self-declared not-current) | v2 backlog (§8). Guest-first is already architecture — no action now |
| Issue #63 `Security hardening…` | OPEN (enhancement) | v2 backlog (§8). No action in S1–S6 except S5's dep/license review (done in PR #74) |

---

## 4. Known contradictions resolved by this re-baseline

| # | Contradiction | Resolution (source of truth) |
|---|---|---|
| 1 | v2 doctrine "NewPipe is THE engine; never hand-roll" vs ADR-001 + PR #57 own-client primary with page-sourced session fields | Doctrine rewritten (MASTER_PROMPT §2): own-client primary acknowledged, drill-watched, contingency-gated |
| 2 | v2 stack "Ktor OkHttp both platforms" vs `HttpClient(CIO)` in code + `ktor-client-cio` dep | Stack corrected to CIO (code wins; no behavior change) |
| 3 | v2 stack "Logging: Kermit" vs zero Kermit refs (platform logging used) | Stack corrected to platform-native; no framework to be added |
| 4 | v2 stack "Android navigation: Navigation Compose" vs shared `AppNavState` on both platforms | Stack corrected to shared custom navigator (deliberate simplification, tested) |
| 5 | Trajectory "candidate 16 = audio cache" vs merged "Phase 16 = UI slice"; candidates 22/26/27/28 already shipped | Trajectory numbers retired → build history 15/16 + v2 backlog (§8) |
| 6 | Old snapshot claims "main is red / unbuildable" (true 09-10 @ `073083c`) vs `main@d555959` all-green | Superseded: build health restored by PR #56; current CI green |
| 7 | "Rot drill red everywhere = issue #14" vs 0-job push noise + no schedule since 09-07 | Separated: noise ≠ verdict; S1 restores schedule; #14 needs fresh verdict — now fixed via extraction-health id 360655315 |
| 8 | Old "Android playback broken / stuck buffering" (true pre-#57 on gated networks) vs user report 2026-09-16 "works well" | Docs were stale: playback assessment follows current chain + user report; drill proof still open (S1) |
| 9 | `docs/decisions/README.md` missing ADR-006 | Fixed this session |
| 10 | Phase 14 "cache user-settable" + Phase 12 "close-to-tray setting" vs no Settings screen anywhere | Recorded as S4 gap (keys exist, UI missing) — S4 CODE merged PR #74, hardware boxes in S3 |

---

## 5. Recurring maintenance & repo sanitization (standing, permanent)

- **Extraction rot:** drill red → client bump / wave maintenance /
  patch release ≤72h (`.ai/RISK_REGISTER.md`). Datacenter-IP-only reds
  are environment, not verdicts — re-run + residential check first.
- **Each session:** ROADMAP (this file) + `.ai/KNOWN_LIMITATIONS.md` +
  `docs/verification/` evidence + small commits.
- **Repo sanitization:** no secrets/device data/credentials in the
  repo; sanitized fixtures; `THIRD_PARTY.md` complete per release; no
  build output committed.
- **Rolling release:** `test` tag replaced, never appended; stable URLs.
- **Single-agent era:** verify on GitHub, rebase onto `main` yourself,
  never merge until asked, never force-push, never rewrite history.

---

## 6. Release gate (v0.1.0 — ALL required, no exceptions)

1. ~~S1: live drill verdict green on the release candidate build (now extraction-health, id 360655315).~~ **SATISFIED 2026-09-20** — daily drill honest (`ENVIRONMENT_BLOCKED` = runner gating) **plus** residential playback proven on `main@d99060e` (Android + Windows, 4 songs, audible, zero failures). Re-confirm on the final release candidate if extraction code changes before the tag; docs-only merges do not invalidate it.
2. S3: Android + Desktop core-loop checklists signed (build SHAs).
3. S6 soaks: 30-min Android (unrestricted battery) + 30-min Desktop,
   zero crashes, logs committed.
4. Clean-target installs: APK + AAB + MSI install and run; upgrade
   preserves data; uninstall removes it.
5. Signing decisions recorded (Play key? Authenticode? or stays
   test-grade + DRAFT-private — user's call, but it must be explicit).
6. CHANGELOG finalized (DRAFT markers dropped), README build docs
   verified, KNOWN_LIMITATIONS + RISK_REGISTER reviewed.
7. **User go-ahead.** Then: tag `v0.1.0` → publish GitHub release.

---

## 7. Completion execution order (single agent — exact sequence)

1. ~~Merge this re-baseline (user review).~~ **DONE** — merged as PR #72
   (main `5023b38`, 2026-09-16T17:08:09Z).
2. ~~**S1** — restore the drill + fresh live verdict.~~ **DONE / GREEN 2026-09-20.** The drill fires daily and classifies honestly (`ENVIRONMENT_BLOCKED` on the runner), and the user supplied the residential half on `main@d99060e`: Android + Windows installs, 4 songs, audible, advancing, no failures, Android lock-screen audio continued. The `6f7fa48...d99060e` compare is docs-only (0 code files), so runner-block and residential-success are the same code — T1 disproven, ADR-007 stays PROPOSED. Next scheduled run (2026-09-21 04:17 UTC, first on `d99060e`) is expected to stay `ENVIRONMENT_BLOCKED`; that is now a known-correct runner result, not an open question.
3. **S2** (agent: workflow-trigger retirement + CI hygiene + `14-release.md` body; PR #54 stays open per the user) — **unblocked now.** Most original S2 items were already clean (verified 2026-09-20). The workflow edit must be made with a live drill watch, never blind.
4. **S3** (user drives devices; agent records + fixes fallout) — includes
   the S4 hardware boxes (settings, EQ, jump-list verb, close-to-tray).
5. ~~**S4** (agent: settings/themes/EQ/jump-list verb).~~ CODE MERGED in
   PR #74 (2026-09-16, user-authorized) — hardware boxes moved to S3.
6. ~~**S5** (agent: tests/hardening/audit).~~ MERGED in PR #74
   (2026-09-16): contrast gate, sts revalidation, palette baseline,
   dependency audit, THIRD_PARTY review.
7. **S6** (soaks + clean installs + user go-ahead → release).
8. v2 backlog (§8) only after v0.1.0 — in user-picked order.

No parallel workstreams are defined: the user runs one agent.
(If that ever changes, S2/S5-docs and S4/S5-code are the natural
split — but do not plan for it now.)

---

## 8. v2 backlog (ideas, NOT scheduled — user picks order after v0.1.0)

Source: retired trajectory candidates + open issues + recorded
follow-ups. None are designed, stubbed, or promised.

- Web/PWA evaluation (likely "no" — PO/SABR blocks third-party
  browser streaming; a written "no" is a valid completion)
- Android Auto · Cast · cross-device sync (explicitly experimental)
- Optional cookie sign-in (issue #60 direction; ADR + user sign-off
  required; core stays guest-first)
- Security hardening program (issue #63)
- Android `AudioEffect` EQ (if S4 defers it) · pre-blurred bitmap
  backdrop for Android <12 · rail-scrollbar exact geometry
- 30-min LeakCanary soak automation · tablet two-pane visual pass
- Store releases (Play AAB with real key + signed MSI) · v1.0 GA
- ADR-007 attestation/token path — contingency ONLY (triggers T1/T2)

---

> Historical references (do not treat as plans):
> [.ai/PROMPT_SEQUENCE.md](PROMPT_SEQUENCE.md) (original 30-phase
> audit) · [.ai/PROBLEMS_AND_FIXES.md](PROBLEMS_AND_FIXES.md) (why v1
> died) · `CHANGELOG.md` (per-change history) · `git log --merges`
> (merge evidence) · `git log -p -- .ai/ROADMAP.md` (this file's
> superseded snapshots).
