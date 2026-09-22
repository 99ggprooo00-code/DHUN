# DHUN — Master Prompt (v3, Re-baselined 2026-09-16)

> **Document status: CURRENT.** This file describes the project **as it
> exists today and as it should be completed from here forward**. It
> supersedes the v2 "Feasibility-Corrected" master prompt below — v2's
> phase definitions (01–14) are retained as build history, not as future
> work: all 14 phases have merged code on `main`.
>
> **Re-baseline record (2026-09-16, session `arena/01a0ab12-dhun`):**
> the project was audited against the live repository (`main@d555959`,
> PRs #1–#71), open PRs (#53, #54), open issues (#14, #60, #63), CI, and
> ADRs 001–006. Why the rewrite was needed:
> - The v2 doctrine ("never hand-roll extraction, NewPipe is THE engine")
>   was superseded in practice by ADR-001 (interim) + PR #55/#57: the
>   production primary is now DHUN's own InnerTube player-client chain
>   with session corroboration sourced from YouTube pages. The doctrine
>   below is rewritten to match that reality instead of forbidding it.
> - Phases 01–14 are code-complete; post-14 extras (downloads, EQ,
>   widgets, jump lists, themes, player immersion, Phase-16 UI slice)
>   shipped without a plan owning them. They are now recorded as
>   Phases 15–16 (merged).
> - The remaining work is **verification, hardening and release**, not
>   feature construction. The completion plan is therefore stages S1–S6,
>   sequential, single-agent (per the user's 2026-09-16 decision: no more
>   parallel agents).
> - Corrected stale stack claims: Ktor uses the **CIO** engine (not
>   OkHttp); logging is platform-native (Kermit was never adopted);
>   navigation is the shared custom `AppNavState` on **both** platforms
>   (Navigation Compose was never adopted).
>
> **Where v2 went:** the full v2 text (14-phase build prompts) is
> preserved in git history (`git log -- .ai/MASTER_PROMPT.md`) and its
> phase table is summarized in §"Build history" below. The original
> 30-phase audit still lives in
> [PROMPT_SEQUENCE.md](PROMPT_SEQUENCE.md) (historical reference only).

---

## 1. What DHUN Is (today)

DHUN is a working, cross-platform music application streaming from
YouTube Music — **past feature construction, into verification and
release hardening.**

- **Platforms:** Android (primary) and Desktop (Windows first;
  Linux/macOS free via JVM). Web is deferred (v2 candidate, likely "no").
- **Current surface:** Home, Search, Library (Playlists / Favorites /
  History / Downloads), Artist / Album / Playlist pages, MiniPlayer +
  immersive FullPlayer (Lyrics | Queue | Related), synced lyrics,
  persistent offline downloads, queue engine with shuffle/repeat,
  history-seeded recommendations, Android widgets (Quick Play), Windows
  tray + jump lists + SMTC + single-instance, light/dark themes with
  accent selector (dev-reachable; wiring open), desktop 10-band EQ
  (Android EQ open).
- **UI philosophy (unchanged):** premium glassy design,
  artwork-driven, dark-first, ViMusic-quality. FullPlayer background is
  real blurred artwork; Home/Search/Library sit on the now-playing
  blurred backdrop (PR #68).
- **License:** GPL-3.0. Non-negotiable.

---

## 2. The Doctrine (revised — read carefully)

**v2 said:** *"Extraction is a maintenance problem; therefore DHUN never
hand-rolls stream extraction — NewPipe Extractor is THE engine."*

**What actually happened:** NewPipe Extractor v0.26.5 broke upstream
during the Phase 01 spike (2026-09-01) with no upstream fix; YouTube
simultaneously began bot-gating tokenless `/player` identities
(`LOGIN_REQUIRED` / "Sign in to confirm you're not a bot"). Waiting
would have frozen the project. ADR-001 (accepted 2026-09-01) therefore
made DHUN's **own InnerTube player-client chain** the interim primary,
and PR #55/#57 (merged 2026-09-10/15) added anonymous session
corroboration (`visitorData` + `signatureTimestamp`, sourced from
YouTube pages, cached, fail-open). User reports as of 2026-09-16:
**Android playback works well; Windows playback is acceptable.**

**The revised doctrine:**

> **DHUN owns a minimal, drill-watched extraction surface, and treats
> every line of it as borrowed time.**
>
> 1. The production primary is the **own-client staged-wave resolver**
>    (`OwnClientStreamResolver`: VISIONOS → TV×3 → MWEB/WEB_REMIX,
>    ADR-003 Option C) with fail-open session corroboration. This *is*
>    hand-rolled extraction — the thing v2 forbade — adopted deliberately
>    because every maintained engine was broken or gated first.
> 2. **Desktop fallback:** user-provided `yt-dlp` subprocess (not
>    bundled; `DHUN_YTDLP` or PATH). **Android has no fallback** — the
>    own-client chain is the only engine (ADR-001).
> 3. **NewPipe Extractor stays a pinned dependency in a non-fatal
>    recovery watch**, never the production primary, until the rot drill
>    proves upstream recovery (ADR-001's standing rule — unchanged).
> 4. **No PO tokens, no BotGuard, no attestation, no cookies, no
>    sign-in** unless a trigger below fires. That research (open PR #54 /
>    proposed ADR-007) is **contingency reference material**, not a
>    backlog item.
> 5. The maintenance contract stands and is **restored as of 2026-09-20**:
>    the daily drill runs (`extraction-health`, id 360655315, cron
>    `17 4 * * *`) and Stage S1 closed **GREEN** — the runner verdict is
>    `ENVIRONMENT_BLOCKED` (datacenter gating) while user-supplied
>    residential evidence on `main@d99060e` proved real audible playback on
>    Android **and** Windows (4 songs, no failures, Android lock-screen
>    audio continued). The two verdicts describe the *same code* — the
>    `6f7fa48...d99060e` diff is documentation only — so the block is
>    network-shaped, not rot. Keep the contract honest: a red drill on a
>    residential re-test, not on the runner, is what triggers a patch.

| Layer | Implementation (actual) | Status |
|---|---|---|
| Search / browse / home / related / suggestions / YTM lyrics | Own thin InnerTube client (`WEB_REMIX`, metadata only) | ✅ working |
| Stream URL resolution (both platforms) | `OwnClientStreamResolver` + `ResolvingStreamResolver` 45s budget + `OfflineFirstStreamResolver` wrapper | ✅ working per user report; drill proof open |
| Desktop fallback extraction | `YtDlpStreamResolver` (user-provided binary) | ✅ wired; hardware proof open |
| NewPipe Extractor | Pinned dep, drill watch only | ⏳ waits on upstream + drill |
| Lyrics | LRCLIB (synced) → YTM text → cache | ✅ shipped; 5-track check open |
| Attestation / PO-token path (ADR-007) | PROPOSED, unimplemented | 🛑 contingency only |

**Contingency triggers (implement ADR-007 options ONLY if):**
- T1: reproducible `AuthRequired`/gating failures on residential
  (non-datacenter) networks with the current chain, confirmed by a
  green-CI build + captured probe output — not by CI-runner IPs alone.
  **Status 2026-09-20: DISPROVEN, not merely unmet.** A residential home-WiFi
  test of `main@d99060e` on Android and Windows played 4 songs audibly with
  zero failures. Re-opening T1 requires *new* residential failure evidence.
- T2: rot drill red on the production chain ≥14 days with no upstream
  recovery (per RISK_REGISTER).
- Until a trigger fires, extraction work is **forbidden** except:
  client-version bumps, wave/identity maintenance, and drill upkeep.

---

## 3. Non-Negotiable Requirements (current)

### Platforms
- **Android:** full player — Media3 background playback, media session,
  notification + lock-screen controls, queue, playlists, lyrics,
  downloads with foreground service, widgets, shortcuts. minSdk 26;
  real blur is API 31+ with a designed dark fallback below (PR #70).
- **Desktop:** single-window app (ADR-004 — never re-add a second
  window), system tray, media keys, SMTC where stable (round-trip
  unverified), jump lists, single-instance guard, close-to-tray,
  keyboard shortcuts, per-user unsigned MSI (test-grade until release
  signing is decided).
- **Web:** cut. No shims, no stubs, no dead code "for later."

### Music source
- YouTube Music via the revised doctrine above. No paid API. No keys.
  Guest-first, account-optional (issue #60 is the v2 direction; no
  login exists or is required).

### UI
- Glassy translucent surfaces with **real blur** where the platform
  supports it (Android 12+/Skiko), graceful dark fallback below;
  artwork-driven backgrounds; dark-first (light theme exists but is
  dev-reachable only); premium typography; every state designed.
- NOT: generic dashboards, default component-library look.

### Architecture (actual — see §5 for the map)
```
┌─────────────────────────────────────┐
│   UI (Compose Multiplatform)        │  shared screens; platform shells
├─────────────────────────────────────┤
│   Presentation                      │  ViewModels (Home/Search/Library/
│                                     │  Player/Browse), shared navigator
├─────────────────────────────────────┤
│   Domain                            │  use cases, entities, interfaces
├─────────────────────────────────────┤
│   Provider abstraction              │  MusicProvider interface
├──────────────────┬──────────────────┤
│ InnerTube client │ Extraction chain │  own-client waves (primary) /
│ (metadata only)  │ yt-dlp (desktop  │  NewPipe (watch only)
│                  │ fallback only)   │
├──────────────────┴──────────────────┤
│   Playback: Media3 (Android) · vlcj (Desktop)
│   Data: SQLDelight (schema v3) · Koin DI · Coil 3 · Ktor/CIO
└─────────────────────────────────────┘
```

---

## 4. Technology Stack — CORRECTED AND LOCKED

Corrections vs v2 are marked **(corrected)**. Locked means: not
re-opened without a written ADR proving a blocking defect.

| Concern | Decision | Notes |
|---|---|---|
| Language/KMP | **Kotlin Multiplatform** | Targets: `androidTarget()` + `jvm()` only |
| UI | **Compose Multiplatform 1.8.2** (shared) + AndroidX Compose (android shell) | Shared screens; platform shells |
| Android playback | **Media3 1.5.1** (ExoPlayer + MediaSessionService) | `dhun://track/<id>` resolving source, segment cache, 403 recovery, fast-fail diagnostics |
| Desktop playback | **vlcj 4.8.2** (system libVLC) | Whole-track cache, next-track prebuffer, EQ |
| Stream resolution | **Own InnerTube wave chain** (primary) | **(corrected)** was "NewPipe THE engine"; ADR-001 interim, still in force |
| Desktop fallback | **yt-dlp subprocess** (user-provided, optional) | Feature-present, not bundled |
| Recovery watch | **NewPipe Extractor** (pinned, non-fatal) | **(corrected)** was primary; drill-watched |
| InnerTube metadata | **Own client** (Ktor + kotlinx.serialization) | `WEB_REMIX` + alt identities for `/player` |
| DB | **SQLDelight 2.x** (schema v3) | Android + JVM drivers |
| Networking | **Ktor 3.1.3, CIO engine** | **(corrected)** v2 said OkHttp; code uses CIO everywhere |
| DI | **Koin 4.0.2** | KMP-first; `appModule` graph test exists |
| Navigation | **Shared custom `AppNavState`** (both platforms) | **(corrected)** v2 said Navigation Compose on Android; never adopted — deliberate simplification |
| Images | **Coil 3.1.0** | Crossfade, tiered sizes (544 list / 1024 player) |
| Lyrics | LRCLIB + YTM lyrics | Persisted cache |
| Logging | **Platform-native** (`android.util.Log` / JDK logging) | **(corrected)** Kermit was never adopted; do not add a logging framework now |
| Desktop native | **JNA 5.17** (SMTC, jump lists) | Base JNA only; fail-open off-Windows |

**Still explicitly rejected:** Flutter, Electron/Tauri, separate
backend, Compose for Web / Kotlin-JS, Room, Hilt, account sign-in for
core playback (guest-first per #60).

---

## 5. Repository Structure (actual)

```
DHUN/
├── .ai/                        # agent operating files (NOT product docs)
│   ├── MASTER_PROMPT.md        # this file (the contract)
│   ├── ROADMAP.md              # live status + completion plan (CURRENT ACTIVE TASK at top)
│   ├── KNOWN_LIMITATIONS.md    # honest gaps, updated every session
│   ├── DEBUG_LOG.md            # incidents: stack → root cause → fix
│   ├── PROBLEMS_AND_FIXES.md   # HISTORICAL: why v1 died (do not rewrite)
│   ├── PROMPT_SEQUENCE.md      # HISTORICAL: original 30-phase audit (do not rewrite)
│   └── RISK_REGISTER.md        # extraction rot, drill, kill-switch criteria
├── docs/
│   ├── decisions/              # ADRs 001–006 ACCEPTED; 007 PROPOSED on open PR #54
│   ├── research/               # spike findings (short, factual)
│   └── verification/           # per-phase on-hardware logs (many gates OPEN)
├── shared/                     # KMP module (android+jvm)
│   └── src/{commonMain,androidMain,jvmMain}/kotlin/dev/dhun/
│       ├── core/               # entities, DhunResult, errors, gates
│       ├── innertube/          # OWN InnerTube metadata + alt player client
│       ├── extraction/         # own-client waves; Resolving/OfflineFirst;
│       │                       # jvmMain: yt-dlp + NewPipe watch
│       ├── provider/           # MusicProvider + YouTubeMusicProvider
│       ├── player/             # DhunPlayer, QueueManager, persistence, EQ model
│       ├── data/               # SQLDelight db, repositories, settings keys
│       ├── download/           # ADR-006 engine (common) + platform storage
│       ├── lyrics/             # LRCLIB + YTM sources, LRC parser, cache
│       ├── domain/             # use cases
│       ├── presentation/       # ViewModels (home/search/library/player/browse)
│       ├── ui/                 # ALL screens incl. shell, player, design consumers
│       └── design/             # tokens, glass components, artwork, catalog
├── app-android/                # shell activity, Media3 service+graph, widgets,
│                               # shortcuts, downloads FGS, DI (Koin)
├── app-desktop/                # Compose window, vlcj player, tray, SMTC,
│                               # jump lists, single-instance, packaging
├── tools/playback-probe/       # Phase 01 CLI harness — STILL the rot drill's probe
│                               # (+ OfflineMain deterministic check, SmokeMain)
├── tests/fixtures/             # captured InnerTube JSON for parser tests
├── scripts/                    # python packaging/CI-contract gates (29 tests)
└── .github/workflows/          # ci.yml · test-release.yml · build-apk.yml ·
                                # extraction-health.yml (id 360655315 — the
                                # restored daily drill; superseded the deleted
                                # rot-drill.yml, schedule fixed 2026-09-17)
```

---

## 6. Build history — Phases 01–16 (all code-merged; hardware gates open)

Phases 01–14 are the v2 locked plan. Every one has merged,
CI-green code on `main`. "🟨" = code merged, on-hardware verification
open. Nothing below needs re-implementation — only the verification and
hardening in §7.

| # | Phase | Status | What actually shipped (deviations in italic) |
|---|---|---|---|
| 01 | Extraction spike | 🟨 merged | Probe (search/resolve/audio-bytes/related) ✅; *NewPipe broken upstream → ADR-001 own-client interim* |
| 02 | Provider & domain core | 🟨 merged | Entities, `DhunResult`, InnerTube metadata client, `StreamResolver` chain, `MusicProvider`, `QueueManager`, `DhunPlayer` ✅ |
| 03 | Android skeleton + playback | 🟨 merged | Media3 service, lock screen, FGS, battery-exemption handoff, 403 recovery, segment cache, fast-fail diagnostics ✅ |
| 04 | Desktop skeleton + playback | 🟨 merged | vlcj player, window-state persist ✅; *shared harness, not separate* |
| 05 | Data layer | 🟨 merged | SQLDelight schema v3, 7+ repos, use cases, now-playing restore ✅ |
| 06 | Design system | 🟨 merged | Tokens, GlassCard real blur, ArtworkImage, color extraction, catalog ✅; *harness screens never deleted (~680 lines dead code — see S2)* |
| 07 | Home & Search | 🟨 merged | Home shelves, search + suggestions + filters + overflow ✅ |
| 08 | Player UI | 🟨 merged | MiniPlayer + immersive FullPlayer + Queue/Related sheet ✅; *coordinated sheet transition (PR #66/#67), backdrop (PR #68)* |
| 09 | Artist/Album/Playlist | 🟨 merged | Browse parsers + pages + local playlist CRUD ✅ |
| 10 | Library & History | 🟨 merged | Tabs, favorites, history, RecordPlay ✅ |
| 11 | Lyrics | 🟨 merged | LRCLIB + YTM + cache + synced UI ✅ |
| 12 | Desktop native | 🟨 merged | Tray, shortcuts, SMTC (unverified), close-to-tray, MSI, single-instance, jump lists ✅; *separate mini-player REMOVED (ADR-004)* |
| 13 | Android polish | 🟨 merged | Edge-to-edge, shortcuts, rotation restore + Robolectric tests, 840dp rail, two-pane shell ✅; *soak never run* |
| 14 | Robustness + rot-drill + release prep | 🟨 merged | Error taxonomy, caches, rolling `test` release pipeline, v0.1.0 DRAFT prep ✅; *drill schedule broken; soaks open; no tag* |
| 15 | Beyond-plan extras (merged, unplanned) | 🟨 merged | ADR-006 downloads, desktop EQ, Quick Play widget, jump lists, themes (dev-only), player immersion, recommendations, playback diagnostics, visitorData/sts (PR #57) |
| 16 | UI/platform repair slice (merged 2026-09-16) | 🟨 merged | Now-playing backdrop, Android tab BACK, Windows rails + fling, Android<12 guard, named CI test steps (PRs #66–#71) |

**Deliberate deviations from v2, all accepted:** own-client extraction
primary (ADR-001); staged-wave parallelism (ADR-003); next-track
prebuffer desktop (ADR-005); offline downloads (ADR-006); no separate
mini-player (ADR-004); shared navigator both platforms; CIO engine;
platform logging; themes/EQ/widgets/jump-lists shipped from the v2
"deferred" pool by user approval.

**Never implement from v2's phase text again.** If a v2 phase step
contradicts shipped code, the code wins; file the correction in
KNOWN_LIMITATIONS, don't rewrite the code to match v2.

---

## 7. Completion plan — Stages S1–S6 (sequential, single-agent)

The user runs **one agent at a time** (2026-09-16 decision — parallel
agents did not work out). Stages run **in order**; each stage's
acceptance gates the next. File-level tasking lives in
[ROADMAP.md](ROADMAP.md).

### Stage S1 — Restore the maintenance contract ✅ COMPLETE (GREEN 2026-09-20)
- **Objective:** the drill runs daily again and produces a live verdict on
  the current chain; issue #14 reflects reality.
- **Outcome:** both acceptance halves met. Daily schedule restored (PR #88,
  id 360655315) with honest classification — runs 35421383687 / 35489268023
  → `ENVIRONMENT_BLOCKED`. Residential half supplied by the user on
  `main@d99060e` (rolling `test` published 2026-09-20T16:46:20Z, APK
  17,948,508 B / MSI 112,861,184 B, home WiFi, no VPN): installs easy on both
  platforms, 4 songs searched and played, audible with advancing position,
  no failures, Android playback continued on the locked screen. The probed
  SHA and the tested SHA differ by documentation only (0 code files), so the
  runner block is datacenter gating. Issue #14 is closable by the user (agent
  token gets 403 on issue writes). **Not** claimed by S1: soaks, media
  controls, offline, lyrics, EQ, Windows native surface — those are S3/S6.
- Tasks (status as of 2026-09-20): ~~fix schedule/cadence~~ **DONE** —
  `.github/workflows/extraction-health.yml` (id **360655315**) fires daily on
  `17 4 * * *` UTC (PR #88 → `3c593fb`; the deleted `rot-drill.yml` /
  `rot-drill-daily.yml` registrations are retired history, do not cite them);
  ~~close/supersede stale PR #53~~ **DONE** (closed unmerged 2026-09-16);
  ~~record fresh verdict~~ **DONE, non-green** — runs 35421383687 (09-19) +
  35489268023 (09-20) classified `ENVIRONMENT_BLOCKED`, issue #14 correctly
  untouched (the workflow files against `FAIL` only);
  ~~**OPEN:** one honest residential/device playback result~~ **DONE 2026-09-20**
  (user evidence on `main@d99060e` — see Outcome above); former text:
  one honest residential/device playback result
  (`docs/runbooks/s1-residential-evidence.md`) — a human *Run workflow* click
  on `extraction-health@main` is optional now that the schedule fires (agents
  get 403 on dispatch); the 0-job push-noise is documented and no longer cited
  as a verdict (the stale `push:` branch trigger was RETIRED 2026-09-22 as the
  S2 agent task — the ROADMAP's newer S1-close assignment `6a55dc9` superseded
  this parenthetical's "needs the user's OK"; executed under a live drill
  watch in PR #111).
- **Acceptance (MET 2026-09-20):** ≥1 scheduled (or manually dispatched) drill
  verdict on current `main` **plus** a playback result outside the GitHub
  runner — a runner-only `ENVIRONMENT_BLOCKED` never closes S1 in either
  direction. Both halves are now on record.
- **Files:** `.github/workflows/extraction-health.yml`, issue #14,
  `docs/runbooks/`, ROADMAP, KNOWN_LIMITATIONS. **Tests:** none (live verdict
  IS the test).

### Stage S2 — Architectural cleanup (small, safe, unblocks review)
- **Objective:** remove dead/confusing weight; zero behavior change.
- Tasks: delete dead harness UI (`HarnessScreen`, `DesktopHarness*`,
  Android DI registration) OR wire it behind a debug flag — delete is
  preferred (v2 Phase 06 ordered deletion); fix `docs/decisions`
  index (ADR-006 missing); reconcile stale `docs/verification/14-release.md`
  header (ends at PR #32); archive or delete root `agent-*-status.md`
  + `phase15-android-polish-status.md` into `docs/` history or delete
  (they are superseded session notes); decide PR #54 (merge
  research-docs-only with ADR-007 staying PROPOSED, or keep open as the
  contingency reference — user call).
- **Acceptance:** CI green; no dead screens; decisions index complete;
  PR #53/#54 resolved (closed or merged-docs).
- **Tests:** existing suites must stay green; no new tests required
  except keeping DI-graph tests passing.

### Stage S3 — Hardware verification round 1 (user + agent; the gates that only a device can close)
- **Objective:** convert "CI-green" into "works on hardware" for the
  core loop on both platforms.
- Tasks (each recorded in `docs/verification/` with build identity):
  Android: install `test` APK → search → play → lock-screen controls →
  background survival → 403-recovery sanity → backdrop/BACK/rails feel
  → widget add/interact → download → offline play. Desktop: install
  `test` MSI on clean Windows (+VLC) → launch-once (single-instance) →
  play → tray → media keys/SMTC → jump list → close-to-tray → upgrade
  over previous (data preserved) → uninstall (data removed).
- **Acceptance:** checklists in `docs/verification/03/04/08/12/14`
  signed with build SHAs; any failure becomes a tracked fix, not a
  silent skip.
- **Tests:** none new — evidence logs ARE the deliverable.

### Stage S4 — Settings + themes + EQ wiring (the "keys without UI" gap)
- **Objective:** every user-facing setting that exists as a key gets a
  reachable surface, or the key is removed.
- Tasks: minimal Settings surface (location TBD — Library row or
  overflow; must not fork navigation): theme mode (dark/light/system —
  `SettingsKeys.THEME`, today unread), cache budget
  (`CACHE_SIZE_MB`, Phase 14 promised "user-settable"), close-to-tray
  (Phase 12 promised a setting), download storage view link (exists in
  Library — keep). Android EQ (`AudioEffect`) — implement or formally
  defer to v2 with a user decision (candidate 22 excluded it).
  Jump-list Play/Pause verb hook (one-line `Main.kt` arg — the
  documented follow-up).
- **Acceptance:** each key readable/writable from UI with tests on the
  persistence round-trip; EQ decision recorded.
- **Tests:** ViewModel round-trip tests; theme-persistence test.

### Stage S5 — Testing + hardening (protect what works)
- **Objective:** highest-value missing coverage; no test-count theater.
- Tasks: probe/rot-drill assertions stay meaningful (update fixtures
  if parsers drift); add resolve-chain regression tests for any wave
  change; Robolectric back-stack/rotation tests already exist — extend
  only if S3 finds holes; fix the dark `error/errorContainer` 3.92:1
  contrast defect (KNOWN_LIMITATIONS, deliberate) with user-visible
  before/after; dependency audit (pinned versions still current?
  upgrade one at a time); `THIRD_PARTY.md` + licenses review.
- **Acceptance:** CI green; contrast gate ≥4.5:1 both schemes or a
  re-recorded exception; dependency review logged.
- **Tests:** the stage IS tests + the fixes they force.

### Stage S6 — Release v0.1.0 (gated by S1–S5)
- **Objective:** earn the tag.
- Tasks: 30-minute soaks (Android unrestricted-battery + Desktop
  libVLC) with zero-crash logs; clean-target installs of APK + AAB +
  MSI; release-signing decisions (Play key? Authenticode? — user call;
  until decided, artifacts stay test-grade and the release stays
  DRAFT-private); finalize CHANGELOG (drop DRAFT markers), README,
  KNOWN_LIMITATIONS, RISK_REGISTER; tag `v0.1.0`; publish GitHub
  release. Rolling `test` pre-release continues unchanged.
- **Acceptance (ALL required):** S1 live verdict green · S3 checklists
  signed · soaks logged · three artifacts clean-installed · user
  go-ahead. Then and only then: tag + publish.

### Explicitly NOT in S1–S6 (v2 backlog — see ROADMAP)
Web/PWA · Android Auto · Cast · cross-device sync · optional cookie
sign-in (#60) · EQ beyond S4 · widgets beyond Quick Play · security
hardening program (#63) · store releases · v1.0 GA · any ADR-007
implementation (contingency only).

---

## 8. Philip Behavior Rules (code-first, single-agent)

1. **One agent at a time.** Never assume a parallel session will finish,
   rebase, or review your work. Verify on GitHub, rebase onto current
   `main` yourself, and leave the tree reviewable by a human.
2. **Ship running code every work unit.** Docs are written *after* the
   code they describe, from the code. A unit with beautiful docs and
   nothing on hardware/CI is a failed unit.
3. **Current code + current tests + accepted ADRs outrank all plans.**
   If this prompt contradicts the repo, stop, investigate, and update
   the prompt — never silently rewrite working code to match paper.
4. Read MASTER_PROMPT.md, ROADMAP.md (CURRENT ACTIVE TASK first), and
   the relevant verification log before starting work.
5. Tests after implementation, before commit. No test that tests
   nothing. Mutation-prove CI steps that must execute (precedent: PRs
   #68/#71).
6. Update ROADMAP.md status and KNOWN_LIMITATIONS.md every session.
7. Commit small and meaningful. Never big-bang. Never merge until the
   user says so; never force-push; never rewrite history.
8. No stubs, no TODO-left-in-production, no "compiles = done."
9. If reality invalidates a locked decision, stop, write the ADR, get
   the user's OK, then proceed. Silent divergence is forbidden.
10. When a step exceeds ~30 minutes without visible progress, split it
    or report back. Silent grinding is forbidden.
11. **Do not touch extraction/probe/rot-drill semantics** without
    checking §2's contingency triggers — and never implement ADR-007
    without the user's explicit go-ahead.
12. Skippable is not allowed; pretending is less allowed.

---

## 9. Source-of-truth hierarchy (set by the 2026-09-16 re-baseline)

1. Current source code on `main`
2. Current tests + CI verdicts (on GitHub, not local claims)
3. Accepted ADRs (`docs/decisions/`, statuses as filed)
4. This master prompt (v3)
5. `.ai/ROADMAP.md` (live status + S1–S6 plan)
6. Historical research (incl. open PR #54 — reference, not backlog)
7. Old plans (v2 text in git history, PROMPT_SEQUENCE, PROBLEMS_AND_FIXES)

If documentation contradicts code: investigate first. Never implement
documentation that contradicts verified current behavior.

---

## 10. RISK_REGISTER — standing content (mirror; canonical file is `.ai/RISK_REGISTER.md`)

| Risk | Likelihood | Trigger | Response |
|---|---|---|---|
| Own-client chain gated or broken (visitorData/sts/identity rot) | High, recurring | rot drill red / residential failures | Client bump → wave maintenance → patch release ≤72h; T2 → consider ADR-007 |
| Rot drill not running (OPEN since 2026-09-07) | **Happening now** | S1 | Restore schedule; human dispatch + Actions-settings check |
| NewPipe upstream recovery missed | Medium | drill watch line | Re-enter as implementation option per ADR-001 |
| Upstream fix slow (>14 days, all engines) | Medium | drill red 14 days | Kill-switch: stop-and-decide with the user |
| SMTC via JNA unstable | Medium | S3 hardware | Ship documented fallback (tray + media keys) |
| Blur unavailable (Android <31) | Certain | shipped | Dark fallback (PR #70); pre-blurred bitmap is a v2 idea |
| Ktor/Coil/Compose MP regression | Low | CI | Pin versions; upgrade one at a time |
| GPL compliance drift | Low | S5 review | THIRD_PARTY.md per release; no incompatible deps |
