# CURRENT ACTIVE TASK

Updated **2026-09-18 ~05:31 UTC** · session **`arena/01a0b224-dhun`** · `main`/`origin/main` `33e94b06125b8ce1eefe9aab0a2faca116ca53fe`.

**Phase/status:** Stage **S1 — restore the maintenance contract** remains **RED** and S2 remains blocked. Owner-triggered candidate run **35310771629** tested `arena/01a0b224-dhun@dbb3c0872dac2e7d010883b4e5ff7482561bc62a` and still failed `home-more`; the diagnostic identified the known top-level `contents` envelope with `singleColumnBrowseResultsRenderer`. Resolver `AuthRequired`/NewPipe evidence remains separate.

**Exact files/task currently being worked on:** `HomeFeedParser.kt` now has scoped support for section lists or direct section entries nested under the known single-/two-column browse renderers, without recursive flattening. `HomeFeedParserTest.kt` and `nested-browse-contents.json` add regression coverage. `Parsers.kt` and `tools/playback-probe/.../Main.kt` are unchanged. The follow-up is committed as **`8dc88a1`**; it is CI-green but not live-validated.

**GitHub state verified live:**
- `origin/main` remains `33e94b0`; PR **#91** is OPEN, unmerged, and `CLEAN` at `8dc88a187a81e779f7dd9625ce90734b69e08216`.
- Candidate `8dc88a1` passed push CI **35311036178**, PR CI **35311039453**, Build APK **35311039470**, and test-release **35311039459**. Test-release AAB/publish jobs were skipped by design. No local Kotlin/Gradle test was run.
- Owner run **35310771629** (`workflow_dispatch`, `arena/01a0b224-dhun@dbb3c08`) completed `failure`; probe job **105492165940** uploaded artifact `rot-drill-35310771629` (id **10533178016**, 4,432 B), and issue #14 comment **5725612380** preserves the tail. Artifact/log blob downloads return `EOF` in this sandbox.

**Last actual error:** run **35310771629** passed version/search, Home first page, related, and offline checks. `home-more` failed with `shape=top[contents,responseContext,trackingParams];continuation[-];contents[singleColumnBrowseResultsRenderer];...`; own-client/yt-dlp remained `AuthRequired`, and NewPipe remained `Parse(detail=JSON response is too short)`. The new parser candidate is a narrow, synthetic-test-backed response-shape hypothesis—not a live fix claim.

**Evidence state:** the authoritative main run **35306224822** remains mixed RED; candidate runs **35308796439** and **35310771629** both kept Home RED. PR #91 remains open/unmerged. No cookies, sign-in, PO tokens, BotGuard, attestation, ADR-007, resolver-chain, or playback changes were added.

**Exact next technical step and blocker:** have the owner trigger `extraction-health` again on current candidate head **`8dc88a187a81e779f7dd9625ce90734b69e08216`**. Inspect whether `home-more` passes with the nested-browse parser; keep S1 RED until then. The agent token still cannot dispatch this workflow (HTTP 403). Do not start S2 or merge PR #91.

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

**`main@33e94b0` (2026-09-17, after PR #90 merge): post-merge CI and rolling release green**
(Build APK **35246193174**, CI **35246193151**, and test-release
**35246193097** — all success on the merge SHA). Rolling `test` currently
targets exactly `33e94b0` and has `dhun-test.apk`, `dhun-test.msi`, and both
`.sha256` sidecars; release assets are 17,948,508 B and 112,852,992 B.
Workflow **360655315 `extraction-health`** is active and correctly named,
and owner-dispatched run **35306224822** completed RED/mixed on `main@33e94b0`; S1 therefore remains open.

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
| **S1** | Restore the rot drill; fresh live verdict; issue #14 reflects reality | 🔴 workflow **35306224822** ran on `main@33e94b0` and produced artifact `rot-drill-35306224822`; offline/metadata/search/related passed, Home continuation returned a parser failure, and own-client/yt-dlp were bot-gated. Mixed RED requires Home-shape investigation; no tokens/ADR-007. | Reconcile the mixed RED evidence, verify residential playback separately, then record S1 outcome before S2 |
| **S2** | Architectural cleanup (dead harness UI, PR #53/#54 hygiene, docs index, stale root notes) | ⬜ | CI green; zero dead screens; PRs resolved |
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

## 3. Board hygiene (PRs + issues — verified via `gh` 2026-09-18)

| Item | State | Decision |
|---|---|---|
| PR #53 `docs: reconcile extraction playback research handoff` (+182/−513, would wipe this file from a stale base) | **CLOSED unmerged 2026-09-16** (session `arena/01a0ac91-dhun`, per this decision) | Superseded by the re-baseline; the research track continues in open PR #54. Nothing in it survived. |
| PR #54 `docs: PO-token/InnerTubeX research + ADR proposal` (+326/−1, ADR-007 PROPOSED) | OPEN, research-only | **Keep as contingency reference** (merge docs-only with ADR-007 staying PROPOSED, or leave open — user's call). NEVER implement without trigger T1/T2 + explicit go-ahead. (A labeled agent test comment "test-ping (delete me)" from 2026-09-16 could not be deleted by the agent token — safe to remove manually.) |
| PR #91 `docs(s1): reconcile extraction-health handoff at main 33e94b0` | OPEN, session branch `arena/01a0b224-dhun`, last CI-verified evidence head `005b526` | S1 docs plus narrow Home parser candidate; CI green in runs 35307916827 / 35307913766, Build APK 35307916736, test-release 35307916668. Do not merge without explicit user instruction. |
| PR #88 `S1 attempt 4 — new workflow extraction-health.yml` | **MERGED as `3c593fb`** (2026-09-17T15:40Z) | Attempt 4 SUCCESS — id 360655315 healthy, name=extraction-health, no phantom push. Old wedged 360227450 fired 35241808266 phantom on same merge. |
| Issue #14 `[rot-drill] Live extraction probe failed` | OPEN; workflow **35306224822** on `main@33e94b0` is the current RED verdict with artifact `rot-drill-35306224822`; it contains both runner bot-gating and a separate `home-more` parser failure | Keep open; reconcile the mixed evidence, do not treat `AuthRequired` as parser rot, and do not dismiss the Home continuation failure without the raw shape. Agent cannot write issue comments (403). |
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

1. S1: live drill verdict green on the release candidate build (now extraction-health, id 360655315).
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
2. **S1** — **registration repair succeeded; live verdict RED and mixed:** workflow **35306224822** ran on current `main@33e94b0` and uploaded `rot-drill-35306224822`. Offline verification passed, stream resolution was bot-gated, and Home continuation produced a separate parse failure. Remaining: capture/reconcile the Home response shape, record the runner-network classification, and complete residential/device verification before S2. Agent dispatch remains HTTP 403; support ticket #4765894 is optional after the distinct-file workaround.
3. **S2** (agent: dead-code + PR/docs hygiene) — unblocked after S1 GREEN.
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
