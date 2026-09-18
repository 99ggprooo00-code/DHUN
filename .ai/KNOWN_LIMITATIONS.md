# KNOWN_LIMITATIONS

Updated every phase. Nothing hidden.

## 2026-09-18 (~04:17 UTC) — S1 live run 35306224822 is RED / mixed (`arena/01a0b224-dhun`)

- **Authoritative current gate:** `extraction-health` run **35306224822** was owner-dispatched on `main@33e94b06125b8ce1eefe9aab0a2faca116ca53fe`, completed with conclusion `failure`, and emitted `PROBE|verdict|FAIL|extraction-pipeline-broken`. Artifact `rot-drill-35306224822` (id **10532130174**, 4,357 bytes) exists; the issue #14 comment preserves the log tail. The signed artifact download returned `EOF` in this sandbox, so no raw payload is claimed.
- **What passed:** Java 17 / tool setup, version, search (20 songs), first Home page (2 sections + token), related (50 tracks), and deterministic offline playback with zero network calls.
- **Separate live failures:** Home continuation (`home-more`) returned `Parse(detail=Home response contained no section list or Home continuation action)`; own-client and yt-dlp watch paths returned YouTube `AuthRequired` / `LOGIN_REQUIRED` bot-gating, so no live audio bytes were validated; NewPipe returned the separate non-fatal `Parse(detail=JSON response is too short)` watch result.
- **Interpretation:** `AuthRequired` is runner-network evidence that requires approved residential/device verification. It is not permission to add cookies, sign-in, PO tokens, BotGuard, attestation, or ADR-007. The Home parse failure is independently unresolved: source review shows the parser recognized no supported continuation shape, but the raw response is not captured, so no speculative parser patch is justified yet.
- **Gate:** S1 remains RED/open; S2 is blocked and PR #91 remains open/unmerged. Capture a sanitized Home continuation response or fixture, then make a narrow parser fix/regression only if the live shape confirms one. No application code or local Kotlin/Gradle test was added for this run; this sandbox has no JDK.


## 2026-09-18 (~01:32 UTC) — S1 handoff reconciled against `main@33e94b0` (`arena/01a0b224-dhun`)

- **Current GitHub baseline:** `origin/main`, local `main`, and the rolling `test` tag all resolve to `33e94b06125b8ce1eefe9aab0a2faca116ca53fe`. Main CI **35246193151**, Build APK **35246193174**, and test-release **35246193097** are green; the `test` release contains APK/MSI and both checksum sidecars.
- **S1 is not closed.** Workflow `extraction-health` (id **360655315**) is active and correctly named; owner-dispatched run **35306224822** is the current RED/mixed result. The deleted `rot-drill-daily.yml` entry is no longer the active path. The agent cannot dispatch workflows (`HTTP 403`); no second run is needed until the mixed findings are reconciled.
- **Current live extraction evidence:** run **35306224822** on `main@33e94b0` failed the production probe with bot-gated `AuthRequired` outcomes, a separate Home continuation parse failure, and the non-fatal NewPipe short-JSON parse; metadata/search/related and offline passed. This supersedes the stale scheduled run **34083253658** as the current verdict.
- **No new application behavior is claimed in this handoff.** No extraction identities, token/cookie paths, probe semantics, ADRs, or hardware status were changed. ADR-007 remains proposed and contingency-only.
- **Environment:** this sandbox has no `java`, `javac`, `ANDROID_HOME`, or `adb`; no local Gradle/Kotlin/Android test was run. CI remains the compiler and hardware gates remain open.
- **Current handoff:** the five-file reconciliation is pushed as PR #91 last verified head `257a395`; CI **35298335178**, push CI **35298332023**, Build APK **35298335216**, and test-release **35298335174** all pass. The PR remains unmerged; do not merge without explicit user instruction.
- **Next gate:** record the first `extraction-health` run and artifact in `docs/verification/14-release.md`, update the debug log and issue state through the workflow, then reassess S1. S2 cleanup is blocked until that evidence exists.

## 2026-09-17 (~16:00 UTC) — S1 attempt 4 SUCCESS — extraction-health.yml clean registration (`arena/01a0aff7-dhun`, main 3c593fb)

- **Attempt 4 (new file `extraction-health.yml`, PR #88, merged `3c593fb` ~15:40 UTC) — SUCCESS.**
  New workflow id **360655315** registered with correct `name: extraction-health` (NOT file path), `state: active`,
  `path: .github/workflows/extraction-health.yml`. No phantom 0-job push run fired on the merge (unlike 360227450 which fired 35241808266 on same merge).
  This confirms the previous wedge was path-specific corruption tied to `rot-drill.yml` / `rot-drill-daily.yml` registrations (ids 348098190, 360227450),
  not a global repo breakage. The new file bypassed it.
- **Old wedged workflows still present before cleanup:**
  - 360227450 `.github/workflows/rot-drill-daily.yml` (active, wedged: name=file path, phantom push noise)
  - 348098190 now `state: deleted` (orphaned to deleted `rot-drill.yml` path)
  - 347425736 `dev-release` (orphaned, file deleted earlier) — also active but file absent.
  Cleanup: delete `rot-drill-daily.yml` in next PR to orphan 360227450; keep `extraction-health.yml` as the healthy drill.
- **Dispatch still 403 for agent token** (re-verified: `gh workflow run extraction-health --ref main` → 403, API dispatch 403).
  User must click **Run workflow** on `extraction-health` in Actions UI (left sidebar shows `extraction-health`, not file path).
  Schedule `17 4 * * *` (04:17 UTC) will fire next window after merge — first scheduled run will be the live verdict.
- **Fable 5.6 API key provided:** user gave `xpl_0ca217e3ac34270c4f9c0759d5ff94dd718c07b7` for coding assistance.
  Sandbox egress to `api.aicodewith.ai` blocked (SSL_ERROR_SYSCALL, same as Maven/Gradle — only api.github.com reachable, verified via curl -v).
  Key NOT stored in repo (gitignored via local.properties). User then said "No need that" for AI DJ product feature, so AI integration code was reverted
  (ai/, presentation/ai/, ui/ai/ removed, working tree clean). Continuation is S1–S6 per MASTER_PROMPT, not new AI surface.
- **Post-merge CI on `3c593fb`:** Build APK success 2m46s, CI success 5m23s, test-release success 6m33s, rolling `test` republished 15:46:15Z apk / 15:47:22Z msi (all four assets, verified via release API). Phantom 0-job push run 35241808266 from old wedged entry on same merge — expected, not a verdict.
- **Next PR (this session):** delete `rot-drill-daily.yml`, update docs/runbooks/rot-drill.md + 14-release.md + ROADMAP to record success.

## 2026-09-17 (~02:30 UTC) — S1 delete+re-add fix after attempt-1 failure (`arena/01a0ad18-dhun`, handoff v3)

- **Re-registration attempt 1 (comment-only edit, PR #77 = `3ff3a55`)
  FAILED.** The comment-only change to `rot-drill.yml` did not
  re-register the wedged workflow: registry entry **348098190** still
  shows name as the file path (not `rot-drill`), `updated_at` frozen
  at 2026-09-16T23:57:41Z, phantom 0-job push run 35171317970 fired
  on the PR #77 merge, and the Actions UI still has no `rot-drill`
  entry / Run-workflow button (user-confirmed 2026-09-17). The entry
  ignores file-content changes.
- **Fix plan executed: delete-then-restore** (PR A = delete (#78),
  PR B = verbatim re-add (#79)). **ATTEMPT 2 FAILED.** PR A briefly
  dropped entry 348098190 from `gh workflow list` (~90 s), but when
  PR B re-added the file (verbatim from `3ff3a55` plus one updated
  comment block; triggers/jobs unchanged, verified by direct
  diff), GitHub reattached the SAME wedged registry id 348098190
  keyed by file path — it did NOT create a fresh id. Name stayed
  as `.github/workflows/rot-drill.yml` (not `rot-drill`), phantom
  0-job push run 35174080320 fired on the PR B merge push, and the
  Actions UI entry did not return. File-level fixes cannot evict
  this entry. Agent tokens cannot disable/enable workflows
  (`gh workflow enable`/`disable` returns 403) either.
- **Attempt 3 (rename to `rot-drill-daily.yml`, PR #84, merged
  ~05:40 UTC) — ALSO FAILED.** New workflow id 360227450 was created
  (old id 348098190 now shows `state: deleted`), but the new entry
  also has name stuck at the file path `.github/workflows/
  rot-drill-daily.yml` (not `rot-drill`) and phantom 0-job push run
  35186690348 fired on the merge push. The bug reproduces on a
  fresh id even at a new filename — the repository's workflow-
  registration layer is failing to read the `name:` field for any
  new workflow at present.
- **ALL file changes to workflows STOPPED per plan (step 5).**
  GitHub Support ticket **#4765894** filed by user (~03:15 UTC;
  auto-receipt ~06:50 UTC); awaiting human response. New id
  360227450 is the target for support to re-sync.

  **Previous note (historical):** the repo owner submitted a GitHub
  support ticket (text provided in the 2026-09-17 agent chat
  session, not committed to repo per request) to
  https://support.github.com/contact?tags=rr-actions asking
  GitHub support to force a clean re-registration (reset the
  entry or delete it so the next push recreates it fresh).
- **Re-registration summary for the ticket:** workflow id
  348098190; last healthy scheduled run 34083253658 (2026-09-07
  04:28 UTC); ≥10 missed windows 09-08 → 09-17; absent from UI
  while `state: active`; registry name = file path, not `name:`
  value; phantom 0-job push runs on every push since 09-07 05:56
  UTC despite no `push:` trigger ever declared; decay began around
  edit `da9d779` (2026-09-07 06:17 UTC); two fix attempts
  (comment-only, delete+re-add) both failed.
- **Merge-last directive #14 in effect:** merging ends the session's
  GitHub connection, so PR B (restore) must have its branch
  complete, diff-verified, and pushed BEFORE merging; same-turn
  post-merge checks happen in the same turn as the merge.
- **Still agent-403 (re-verified handoff v3):** `workflow_dispatch`,
  workflow disable/enable, issue comments. Agent CAN push, open PRs,
  merge routine green PRs, and delete fully-merged branches. User
  can click Merge on PRs and one Run-workflow button click once the
  UI returns.

## 2026-09-17 — S1 continuation: UI absence, revised dispatch path, branch cleanup (`arena/01a0acb6-dhun`)

- **Rot-drill is absent from the Actions UI entirely** (user report
  2026-09-17: no list entry, no Run-workflow button) while the REST
  registry reports `state: active`, workflow id 348098190, and
  `gh workflow list` shows the registry name as the file path
  `.github/workflows/rot-drill.yml`. Schedule still silent since
  09-07 04:28 UTC (9 missed 04:17 windows; the 09-17 window was
  pending at session start). 0-job push noise continues (35163767716
  on the `bcb65cc` merge push). Live-verdict path revised:
  re-registration (comment-only workflow edit) → UI button → user's
  Run-workflow click; GitHub support ticket as the fallback.
- **The user cannot perform manual GitHub steps** (handoff v2,
  2026-09-17): no CLI/API/PAT actions — only merge clicks on session
  PRs and one Run-workflow click once the button exists. All prior
  plans assuming an operator API dispatch are void.
- **Agent dispatch 403 re-verified** this session (same integration
  token). Agent-side merge DOES work — this app merged PRs #72/#74/#75
  (`mergedBy` verified via the API) — as do branch deletions
  (`git push origin --delete`).
- **Branch cleanup (user request, executed 2026-09-17):** 17
  fully-merged remote branches deleted (each verified `ahead_by == 0`
  vs `main` via the compare API); kept `main`, `arena/01a0890b-dhun`
  (open PR #54) and the session branch. Stale unmerged branches
  `arena/01a08455-dhun` (+3) and `arena/01a08676-dhun` (+3) were
  deleted 2026-09-17 ~03:35 UTC — their ahead commits (09-09/09-10
  docs snapshots + an early extraction-auth research draft) were
  fully superseded by the 09-16 re-baseline and PR #54; no open
  PRs referenced them. Remote branches 21 → 2 (main + PR #54).
- **Lost commit 771552a:** the previous session's unpushed post-merge
  docs commit never reached GitHub (this fresh clone has no trace);
  its content is restated in handoff v2 and re-applied by this
  session's first PR.
- **Capability check (once, this session):** no JDK/Gradle/Android SDK
  → CI is the compiler; `gh` + GitHub API reachable (incl. branch
  delete + PR merge); the local clone is shallow (depth 1) — GitHub
  API remains the history source of truth.

## 2026-09-16 (late) — S1 rot-drill diagnosis + post-merge reconciliation (`arena/01a0ac91-dhun`)

Evidence-first reconciliation after the PR #74 merge; no code changed,
no local build, no hardware claims.

- **Post-merge state verified on GitHub (not assumed):** `main@c5b1793`
  passed build-and-test 35135429018, Build APK 35135429102 and
  test-release 35135429240; the rolling `test` pre-release was
  republished 2026-09-16 18:41:19 UTC at exactly `c5b1793` (apk + msi
  + both `.sha256` sidecars). S4/S5 code (settings, Android EQ,
  jump-list verb, contrast/sts hardening, dependency audit) is on
  `main` via PR #74.
- **Rot-drill full lifecycle reconciled from the GitHub API (all 189
  runs):** the schedule fired daily 09-02 → 09-07 (green 09-02/03/04/05;
  RED 34011539225 on 09-06 and 34083253658 on 09-07 — both pre-#57-chain
  SHAs `dd1ab31`/`d1e0408`); it has been **silent since 09-07 04:28
  UTC** — 9 missed 04:17 UTC windows (09-08 → 09-16) with the repo
  active daily and the workflow registry `state: active` (id
  **348098190**). **Every push since 09-07 05:56 UTC created a 0-job
  `push` run** (0 jobs, `conclusion: failure`, `failure_reason: null`)
  although the file has **no `push:` trigger in any version** (verified
  at 6 SHAs, 09-01 → `c5b1793`). Working diagnosis: GitHub-side
  trigger-registration anomaly around the 09-07 06:17 file edit
  (`da9d779`) — the registry name still shows the file path, not
  `rot-drill`. Fix options (in `docs/runbooks/rot-drill.md`): force
  re-registration (trivial commit to `main` + one manual run — needs
  user OK as a workflow change) or a GitHub support ticket (workflow id
  348098190). The 0-job runs are **not** extraction evidence and are
  never cited as verdicts (e.g. 35135427771 on the merge push).
- **Capability re-check (this session):** no JDK/Gradle/Android SDK →
  CI is the compiler; `gh` + GitHub API reachable; **the local clone is
  shallow (depth 1)** — all history claims above come from the GitHub
  API, not local `git log`.
- **`gh workflow run rot-drill.yml --ref main` → HTTP 403**
  (integration token), re-confirmed 2026-09-16 — dispatch stays an
  operator task. Additionally, **issue-comment writes are 403 for this
  token** (PR comments work), so issue #14 could not be updated by
  comment; the reconciliation is recorded here, in ROADMAP and the
  runbook instead, and the rot-drill workflow (`issues: write`) will
  update #14 on the next live run. A test comment on PR #54 could not
  be deleted (delete 403) — remove manually.
- **Board hygiene:** stale PR #53 closed unmerged per the recorded
  ROADMAP §3 decision (superseded by the re-baseline; the research
  track lives in open PR #54).

## 2026-09-16 — S5 continuation: stale palette regression baseline

`DhunAppearanceTest` still expected the pre-S5 error and border hue after
production tokens were retuned. Both expectations now match `#D5798A`;
the baseline name/comments explicitly allow this intentional change.
CI verification PASSED on PR #74 at `19c7b0d`: shared/Android/desktop tests,
APK, MSI and build checks all green. The Android build also required
calling `generateAudioSessionId()` on the context AudioManager instance,
not statically. No local JDK or hardware verification is claimed. S1
dispatch and S3 device checks remain user-blocked; S6 is not authorized
by the autonomous merge approval.

## 2026-09-16 — S4 slice 1: settings with keys but no behaviour

Verified by grep (no app caller outside tests — only `UseCasesTest`
touches the getters): the S4 Settings page exposes exactly the five
settings that do something (theme, accent, cache budget, resume-on-launch,
close-to-tray). These keys exist in `SettingsKeys` but are deliberately
*not* surfaced, because persisting a value nothing reads would be a lie:

- `AUDIO_QUALITY` ("low/medium/high") — `GetSettingUseCase.audioQuality()`
  has no caller; `InnerTubeClient` is constructed with fixed defaults.
- `COUNTRY_CODE` — same; InnerTube `gl` stays `"US"`.
- `LYRICS_ENABLED` — no reader at all (lyrics always on).
- `ACCENT_MODE` ("artwork"/"static") — no reader; dynamic artwork palettes
  are unimplemented (the 6-way accent hue is the separate `ACCENT` key).
- `EXPLICIT_CONTENT` — no reader; nothing filters explicit tracks.

Wiring any of these is a v0.2.0+ feature, not a settings-UI gap. `THEME`
`"system"` likewise stays storable-but-unhonoured (falls back to dark).

## 2026-09-16 — S4 slice 3: Android EQ notes

- The engine attaches to DHUN's own audio session id only. Session 0 (the
  global output mix) is refused by design: EQ must never affect — or be
  affected by — another app's audio.
- Devices whose DSP/HAL omits an equalizer degrade to silent bypass (one log
  line per session id). No `MODIFY_AUDIO_SETTINGS` permission is needed for
  own-session effects.
- Audible EQ proof is S3-hardware-gated: no emulator reproduces a DSP
  equalizer, so the binder calls are reviewed, not executed. The curve
  mapping itself is JVM unit-tested (`EqualizerBandMapperTest`).

## 2026-09-16 — S4 slice 2: per-track jump entries surface, don't play

`--dhun-play=<id>` converges on `RemoteCommand.Show`: `MusicProvider` has no
track-by-id lookup (only search/feed/related/page), so resolving a bare video
id into a playable `Track` would mean a search round-trip with fuzzy matching
— a wrong-track play is worse than a surface. The Play/Pause verb (the one S4
promised) genuinely toggles. A `provider.track(id)` API + jump-play would be a
v0.2.0+ feature.

## 2026-09-16 — second-look code findings (same session, engine-room read)

Read end to end: `InnerTubeClient`, `OwnClientStreamResolver`,
`PlaybackGraph`, `DesktopDhunPlayer`, `AndroidDhunPlayer`,
`PlayerViewModel`, `DhunStreamCache`, `RateLimitGate`,
`QueueManager`, sheet-transition math. No CRITICAL/HIGH found; two
items worth scheduling, rest nits/notes. All verified against code,
not inherited:

- **[PERF/MEDIUM — S5] `signatureTimestampOrNull` fetches the watch
  page on EVERY resolve** (`InnerTubeClient.kt:316-346`). The
  base.js→sts mapping is cached, but the watch-page GET that yields
  the jsUrl runs unconditionally per track: +1 RTT and ~0.5–1 MB HTML
  on every cold resolve, part of the desktop startup budget. Fix:
  TTL-guard sts revalidation (reuse cached sts if validated within
  N hours; re-fetch watch page only on TTL expiry or AuthRequired).
  **RESOLVED 2026-09-16 (S5):** count-based budget instead of wall-clock TTL
  (`STS_REVALIDATE_EVERY = 25`; commonMain has no clock) — 1 watch GET per 26
  resolves, forceRefresh bypasses, failed revalidation backs off + serves
  stale. Covered in `AltPlayerIdentityTest`.
- **[BUG-RISK/LOW-MEDIUM — S2 or S5 one-liner]
  `cancelCacheFill()` nulls the job WITHOUT cancelling it**
  (`DesktopDhunPlayer.kt`): only the AtomicBoolean is set, so a fill
  blocked in a socket read keeps consuming bandwidth after a skip;
  `handlePlaybackError`'s `fill?.join()` can also see `null` while a
  fill is actually running (narrow race → premature Error instead of
  local-copy recovery). `cancelPrebuffer()` in the same file does it
  right (`job?.cancel()`); mirror that + a test.
- **[UNVERIFIED/LOW — S3 checklist] vlcj volume scale mapping**
  (`DesktopDhunPlayer.kt`: init `volume()/100f`, set `(v*100)`).
  Assumes native 0–100. If libVLC's native range differs, init still
  coerces safely but max-slider may cap below true max. Needs one
  hardware comparison (DHUN max vs VLC-app max). NOT claimed as a bug.
- **[NIT/LOW — S5] `PlaybackGraph.retries` never reset on success.**
  Per-track error counts accumulate for the process lifetime; a track
  that recovered once has fewer retries left hours later. Clear on
  STATE_READY/playing. Memory bounded by distinct tracks (trivial).
- **[NIT/LOW] `checkPlayability` passes `LIVE_STREAM_OFFLINE` as OK**
  → downstream "no formats" Parse error. Harmless for a music app;
  could map to Unavailable("live stream") if touched.
- **[NOTE] `visitorData` cached forever, never revalidated.**
  Fail-open covers fetch failure, not mid-session staleness (stale
  value likely behaves as no value). Extraction-maintenance note.
- **[NOTE] Desktop concurrent bandwidth**: stream + cache-fill +
  prebuffer-next can run together (prebuffer correctly waits for the
  `playing` event). Documented trade-off; S3 slow-network soak should
  watch startup behavior.
- **[NOTE] `playCurrentLocked` holds `opMutex` across network
  resolve**: transport ops queue behind a slow resolve (45s budget
  worst case). Prevents overlap bugs; accepted trade-off.
- **[CORRECTION] ADR-005 is implemented on BOTH platforms** — Android
  via `DhunStreamCache.prefetch` (`AndroidDhunPlayer.kt:281`) +
  Media3 queue, Desktop via prebuffer + temp cache — not "desktop
  half" as first reported.
- **[CONFIRMED GOOD]** Sheet math (`relatedSheetTravel*`, frozen
  capture, finite-guards, floor/ceiling coherence) holds up to
  reading — PR #67's claims verified in code, no bug found.
  ExoPlayer tuning (500 ms playback buffer, audio-only tracks,
  UA-per-videoId isolation, key-stable cache) is careful work,
  consistent with the user's "Android buffering fine" report.
  `RateLimitGate` (monotonic, extend-only) and `DhunStreamCache`
  (5 h TTL, UA-paired, invalidate-on-403) are correct.
  `QueueManager.setQueue` coerces bounds; `PlayerViewModel`
  collectors die with `activityScope` (cancelled in onDestroy) /
  app scope on desktop — no leak.

## 2026-09-16 — project re-baselined (`arena/01a0ab12-dhun`, base `d555959`)

Docs-only session: `.ai/MASTER_PROMPT.md` → v3, `.ai/ROADMAP.md`
rewritten around build history (Phases 01–16, all code-merged) and
sequential completion Stages S1–S6 (single-agent era — the user runs
one agent at a time from here on). What this changes about the entries
below:

- **Device playback reports are the user's, not CI's.** "Android works
  well / Windows acceptable" (2026-09-16) is a user report on recent
  `test` builds. No live drill verdict exists on the current chain
  (last real one: `34011539225` @ `dd1ab31`, 2026-09-07, pre-#57), so
  every pre-2026-09-16 "playback broken / gated" entry below is
  **stale evidence, kept for history** — re-baselined by Stage S1, not
  deleted.
- **PO-token / InnerTubeX / ADR-007 research (open PR #54) is
  contingency reference, not backlog.** Implementation is gated by
  triggers T1/T2 (MASTER_PROMPT §2) + the user's explicit go-ahead.
- **PR #53 is do-not-merge** (stale ROADMAP wipe); recommendation is
  close-unmerged (user's call). Issue #60 (guest-first login) and #63
  (security hardening) are v2 backlog, not S1–S6 work.
- **No Settings screen exists.** `SettingsKeys` (theme, cache budget,
  close-to-tray…) are keys without UI — recorded as the Stage S4 gap.
- **Dead code identified, not yet removed:** the Phase 03/04 harness
  screens (`HarnessScreen`, `DesktopHarness*`, ~680 lines, no call
  sites — v2 Phase 06 ordered deletion) are Stage S2 work.
- **Trajectory candidate numbers (15–30) are retired** — several
  shipped already (EQ, widgets, jump lists, themes). Remaining ideas
  are the unnumbered v2 backlog in ROADMAP §8.

## 2026-09-16 — desktop unit tests execute in CI (`arena/01a0aa8e-dhun`)

Closes the gap candidate 27 recorded and every entry since repeated: the five
`app-desktop/src/jvmTest` classes (jump-list/tray pure cores) were authored and
locally runnable but **never executed by CI**, because the desktop gate was
`:app-desktop:compileKotlinJvm` only. `ci.yml` now runs `:app-desktop:jvmTest`
as a named step after the compile step, and `scripts/test_ci_workflow.py` pins
both the step and its order. What is **not** established:

- **Execution is proven by a genuine red, not a mutation.** The first CI run
  failed `:app-desktop:jvmTest` with three real assertion failures (test names
  + file:line in the annotations), so no separate mutation run is needed — a
  step that merely compiled could not produce those. Caveat: that red consumed
  all 10 check-run annotations (the per-run cap), so further failures could be
  hiding behind it; the re-run is the confirmation.
- **Validator contract tightened to ASCII `[A-Za-z0-9_-]`** (was Unicode-aware
  `isLetterOrDigit()`, which admitted `ä`). No real-world change: track ids
  are YouTube video ids.
- **Still no local build.** No JDK; Maven/Gradle/dl.google.com egress-blocked.
  CI is the only compiler, exactly as before.

## 2026-09-16 (session `arena/01a0aa7a-dhun`) — Android <12 backdrop guard unified across FullPlayer and NowPlayingBackdrop

- **Android <12 devices consistently receive clean dark fallback without sharp bleed.**
  `supportsRealtimeBlur` now guards both `FullPlayer` (`PlayerBackdrop` + `LyricsCard`)
  and `NowPlayingBackdrop`. On Android below API 31 (where `Modifier.blur` is a RenderEffect
  no-op), unblurred artwork is suppressed across all screens.
- **Still no pre-blurred bitmap generation on API <31.** Android 8.0–11 devices display
  the designed clean dark surface and ambient scrim rather than an offscreen pre-blurred
  bitmap cache.
- **Hardware verification OPEN.** Visual look on physical Android 8–11 devices remains an open
  hardware check.

## 2026-09-16 (later) — mouse-rail fling + named Android unit-test CI step (`arena/01a0aa5e-dhun`)

Follow-ups listed by PR #68, not a new product surface. What is **not**
established:

- **Fling feel is unproven on hardware.** The decay is `exponentialDecay`
  (density-free, commonMain) rather than the framework's spline fling, which
  still belongs to touch. A Windows mouse user is the gate; CI cannot see a
  frame. A cancelled drag does not coast, by construction.
- **The assembleDebug ↔ testDebugUnitTest coupling is still there on
  purpose.** `ci.yml` now *names* `:app-android:testDebugUnitTest`, which was
  the actual gap. `test-release.yml` and `build-apk.yml` still invoke only
  assembleDebug, so removing the `dependsOn` would let a red suite produce a
  downloadable APK. The extra CI cost should be UP-TO-DATE after the named
  step; if it ever re-runs the whole Robolectric suite, that is waste, not a
  silent skip.
- **Android <12 pre-blurred backdrop is still a decision, not a patch.** Same
  as #68: `Modifier.blur` is a no-op below API 31, so those devices keep the
  old background.
- **Still no local build.** No JDK; Maven/Gradle/dl.google.com egress-blocked.

## 2026-09-16 — Phase 16 UI/platform slice: CI is green and mutation-proven, but nothing here was seen on a device (`arena/01a0a9c4-dhun`, PR #68)

Three user-reported defects fixed (now-playing blurred backdrop on
Home/Search/Library, Android BACK out of Search/Library, Windows horizontal
rails). What is **not** established, and should not be inferred from the green
checks:

- **No hardware evidence of any kind.** This sandbox has no Android
  device/emulator and no Windows machine, so the physical Back button/gesture,
  the visual weight of the backdrop, the feel of hold-and-slide and real trackpad
  hardware were **not** exercised. No screenshot or recording is attached to the
  PR, and `docs/verification/` has no line for this phase. The desktop *root
  cause* is quoted from the pinned Compose Multiplatform 1.8.2 sources
  (`Scrollable.kt` `CanDragCalculation`, `MouseWheelScrollable.kt`
  `canConsumeDelta`, `ComposeSceneMediator.desktop.kt`, AWT `WmMouseWheel`) —
  that is evidence about the framework, not about a user's laptop.
- **Android <12 keeps the old background, by design.** `Modifier.blur` is a
  `RenderEffect`: a silent no-op below API 31. Shipping the artwork there would
  have meant a *sharp* full-screen album cover, so `supportsRealtimeBlur` gates
  the backdrop and those devices fall back. minSdk is 26, so that is a real
  population, not a corner. A pre-blurred bitmap tier is an open follow-up.
- **Rail scrollbar geometry is estimated.** A lazy list exposes no total content
  extent, so `lazyRailMetrics` reconstructs it from the mean measured item size
  plus the layout's spacing and content padding: exact for the uniform card
  rails this ships on, approximate for a mixed-content rail.
- **No fling after a mouse drag.** Superseded by `arena/01a0aa5e-dhun` (see
  the 2026-09-16 later entry): mouse release now decays leftover velocity.
  Touch still uses the framework fling. Hardware feel remains unproven.
- **Corrected environment fact (this contradicts what PR #68's first summary
  said, and refines the "CI proves compile + unit only" line below):**
  `:app-android:assembleDebug` **executes** the app-android unit suite, it does
  not merely compile it. Proven by mutation, not by reading the workflow: a
  reversed expectation in `NavStatePersistenceTest` turned the `Android debug
  build` step red with `AssertionError: expected:<[SEARCH, HOME]> but was:<[HOME,
  SEARCH]> @ NavStatePersistenceTest.tab back history survives a round
  trip…(:111)`. A second mutation (`RailScrollbarGeometry.MIN_THUMB_FRACTION`
  0.18f→0.05f) red-flagged `HorizontalRailTest.thumbFractionIsFlooredSoItStays
  Grabable(:49)` the same way, so the green `:shared:jvmTest` runs are known to
  execute the new tests rather than merely compile them. Both mutations are
  reverted on the branch. **The named-step gap is closed on
  `arena/01a0aa5e-dhun`:** `ci.yml` now runs `:app-android:testDebugUnitTest`
  as its own step. The assembleDebug coupling remains for packaging jobs
  (see the 2026-09-16 later entry).
- **Still no local build.** No JDK, and Maven/Gradle/dl.google.com remain
  egress-blocked (`curl` → `000`); `gh` reaches api.github.com, which is how the
  framework sources and the check-run annotations above were read. CI is the only
  compiler, exactly as the entries below record.

## 2026-09-10 (later) — #57 wires the session fields; device proof still open (`arena/01a0897a-dhun`)

Supersedes the "inert" bullets of the "auth-gating is **not** fixed" entry below (kept verbatim — true on `main@be51d7d`): PR #57 sources `visitorData` (YouTube-homepage ytcfg) and `signatureTimestamp` (watch page → base.js, emitted as a JSON **number**), both cached and fail-open, and `OwnClientStreamResolver.resolve()` races them through all 7 alt strategies; `playbackContext` moved top-level per the InnerTube schema. **Still** honestly open: whether YouTube's gate clears — no on-device audio demonstrated with these fields, CI proves compile + unit only. ADR-007 (PR #54) stays the fallback if the gate survives.

## 2026-09-10 — the daily rot-drill is **not running**, and its "red" runs are 0-job noise

Two separate things were conflated across many sessions and are separated here:

- **Noise:** `.github/workflows/rot-drill.yml` has only `schedule` +
  `workflow_dispatch` triggers. The red `rot-drill` entries on pushes and PR
  branches (`104`–`113` on 2026-09-10 alone) are GitHub's **no-matching-trigger
  runs with 0 jobs** — they carry no probe verdict and must never be cited as
  extraction evidence. (`…/actions/runs/<id>/jobs → total_count: 0` is the test.)
- **Real gap:** the last `event=schedule` run is **#10, `2026-09-07T04:28Z`
  (failure, `d1e0408`)**, and #2/#3/#4 (09-03…09-05) were **green**. **Three
  daily slots (09-08, 09-09, 09-10) never fired** although the workflow is
  `state=active`. Cause is not determinable from the API, so it is recorded as
  open, not explained away. Impact: the maintenance contract's "breakage
  detected within 24 hours" is not currently held, and a red live extraction
  (issue #14) could sit unnoticed for days.
- **Who can act:** an agent cannot — `gh workflow run` returns **HTTP 403** for
  this session's token. A human should press **Run workflow** on `main` and
  check Settings → Actions (schedule/allow-list). Until a green-or-red
  **scheduled** verdict exists, "drill is red because of issue #14" is a claim
  about run `34011539225`/`#10`, not about today.

## 2026-09-10 — Android auth-gating is **not** fixed; PR #55's session fields are inert, and `main` was red

**Do not read "the visitorData PR merged" as "playback repaired".** Verified by reading
the call sites, not the PR title:

- `InnerTubeClient.altPlayerResponse(videoId, alt, visitorData = null, signatureTimestamp = null)`
  — **every one of the six call sites in `extraction/OwnClientStreamResolver.kt` passes
  neither**, so `context.client.visitorData`, `contentPlaybackContext.signatureTimestamp`
  and `X-Goog-Visitor-Id` are absent from every request. Android's failure mode is
  unchanged: on bot-gated networks YouTube answers `LOGIN_REQUIRED` →
  `DhunError.AuthRequired("Sign in to confirm you're not a bot")` (issue #14, and the
  user's own Windows residential network fails the same way — this is **not** a
  CI-IP-only problem).
- The correct way to *obtain* a visitor session / attestation is a **decision, not a
  patch**: `docs/decisions/ADR-007-android-stream-attestation.md` (status **PROPOSED**,
  drafted on open PR #54) lays out options A–D. Per ADR-001/ADR-003 the chain is locked
  tokenless, so nothing here may be improvised — an ADR + the user's OK come first, and a
  live probe verdict + on-device audio come after. Until then the fix is a typed, immediate
  error (`ResolveOutcomeLog`, PR #50), never a fake token.
- `signatureTimestamp` is typed `String?` and emitted as a JSON string. YouTube's
  `contentPlaybackContext.signatureTimestamp` is conventionally a number (yt-dlp sends an
  integer). Left as-is on purpose: with no caller and no live capture, choosing the wire
  type would be guessing. Settled together with ADR-007 and pinned by a test then.
- **Resolve Wave 1 shrank** (PR #55): `[web_embedded, visionos]` → `[visionos]`.
  `web_embedded` is still in `STRATEGIES` but unreachable from every wave, so it is
  retained-but-untried, and `WEB_EMBEDDED_PLAYER` (with its `thirdParty.embedUrl`) now only
  runs if a wave is re-cut. Consequence: fewer identities before the 429/timeout cascade,
  and one less identity in the per-identity diagnostic string.
- **`main` build health** WAS red from `073083c` (merged as `6e4d057`, `2026-09-10T03:10Z`)
  through `58d9ac8`: `:shared:compileKotlinJvm` / `:shared:compileDebugKotlinAndroid` failed
  in `InnerTubeClient.kt`, so `build-and-test`, `apk`, `msi`, the new `Build APK` workflow
  and `rot-drill` were all red, and `rot-drill`'s probe verdicts on those SHAs are
  **worthless** (it builds the app before it probes). The rolling `test` build is frozen at
  `cd97464` (`2026-09-10T02:02:44Z`), i.e. PRs #51/#52 are merged but unpublished.
  **Repaired and merged as `be51d7d` (PR #56) on `2026-09-10T04:21:16Z`**; `main` CI
  `34436859375`, test-release `183` (`apk`/`msi`/`publish`) and `Build APK` run `6` are
  green, and the rolling `test` build now ships `be51d7d` (published `04:25:25Z`) — i.e.
  #51, #52 and the docs pushes are finally in a downloadable artifact.
- **Environment trap, restated because it is the actual cause:** this sandbox has no JDK and
  no Maven/Gradle or Actions-log egress, so **CI is the only compiler and check-run
  annotations are the only log**. Pushing code that has never been compiled is normal here;
  merging it is not. `gh pr checks <n>` must be read before every merge — a `MERGEABLE`
  PR with red gates is exactly what produced this incident.

## 2026-09-07 — Phase 15 Android polish (`arena/01a07ad8-dhun`) alongside 15a player immersion (`arena/01a07a6b-dhun`)

**Status after the split: 15a is on `main`, Phase 15 is not finished.** The player batch
merged as `dd0fe14` (head `cd40c1f`, `build-and-test`/`apk`/`msi` green); the Android half
is PR #43, green at `434ad92`, unmerged. The head that started this — `7c24fde`, which
carried *both* workstreams — **did not compile at all**, and #43 carries the fix to a test
failure inherited from `32e38c5`. Per the contract (*done = pushed + CI green +
hardware-verified where specified*), every Phase-15 row here is a **code + CI** claim with
the device half still open.

- **A `MERGEABLE` flag is not a health signal.** #41 was `mergeable: MERGEABLE` with
  `build-and-test`, `apk` and `msi` all **failing**; `mergeStateStatus: UNSTABLE` was
  the only honest field, and the session that inherited the PR trusted a platform
  message ("merged-or-closed") instead of `gh pr view`. Read `gh pr checks` before
  merging anything, including work you were told is finished.
- **A compile failure hides every test result behind it.** `PlayerSeekBar.kt` failed
  resolution, so `:shared:jvmTest` and `:app-android:testDebugUnitTest` never executed
  on that head — the four 15a test files had not run anywhere until #41's `cd40c1f`
  (which is green and on `main`), and a *separate*, real `NavStatePersistenceTest`
  failure that predated the break was invisible beneath it. Two
  defects, one red: fixing the compile error is what reveals the second. Read "CI red" as
  *unknown*, not as *one known failure*.
- **An Android *unit-test* failure now surfaces as a build failure.** `6f45a27` coupled
  `:app-android:assembleDebug` to `testDebugUnitTest` so CI executes the suite; the
  side effect is that a red test aborts the step named **"Android debug build"**, which
  reads like a compiler problem. Check the test report before hunting syntax.
- **Robolectric shares one JVM, so app-level `startKoin` must be re-boot safe.** The
  first Android suite died on `KoinApplicationAlreadyStartedException` from
  `DhunApp.onCreate` (`4816c81` → guarded in `32e38c5`). Any future Android test that
  boots the application inherits this, and none of it is reproducible locally: the
  sandbox has no JDK and no Maven/Gradle egress, so CI is the only compiler.
- **Compose offset inputs have no automated gate.** `trackAlignedItemOffsetPx` is pure
  and JVM-tested, but *what is fed to it* is not: the player PR's `ff28f4a` passed an out-of-scope
  `width` (three red jobs), and an `onSizeChanged` placed inside rather than outside
  `.padding(horizontal = xsPlus)` reports the text box instead of the whole pill — a
  silent 12px clamp error no compile error, lint or test would catch. Placement
  relative to `padding` is the whole difference; verify on device.
- **`split(':', limit = 3)` does not reject empty payloads.** `?.let` guards null, not
  `""`, so `"artist:"` restored `ArtistPage(id=)` onto the nav back stack forever. Any
  string-encoded persistence in this repo needs the blank-vs-null distinction.
- **Open by design, not omission:** tablet two-pane (`shared/ui/shell/DhunAppShell.kt`,
  untouched), the 30-minute LeakCanary soak (never run), TalkBack, the launcher
  long-press surface, the dynamic shortcut on a real launcher, and no
  `docs/verification/15-*.md`.

## 2026-09-07 — Coordinator reconciliation: integration limits found while merging six ADR-006 agents

Recorded by the coordinator session `arena/01a07a07-dhun`. Full detail in
`INTEGRATION.md`. **CI green is a compile/unit-test gate only — none of this closes a
hardware gate.**

- **The Android Koin graph has no automated verification (C1).** `app-android` has
  **no test source set**, so `:app-android:assembleDebug` is a *type-check* gate and
  cannot see a dependency-resolution cycle. This already produced a real defect:
  `single<DownloadManager> { ForegroundServiceDownloadManager(delegate = get(), …) }`
  recursed because the unqualified `get()` inferred the interface being constructed,
  and **all three checks were green** on that commit. It fired at app launch
  (`MainActivity.kt:197` resolves `DownloadManager` during composition), not on first
  download. Fixed in `ef69f82` as `delegate = get<FileDownloadManager>()`.
  **Merged and green:** the fix landed via PR #35 (squash `40eff1d`, main `481b77b`).
  **Residual:** `KoinDownloadStackTest` pins the registration *shape* with minimal
  fakes in `:shared:jvmTest` and reads Koin through `GlobalContext.get()` — the real
  `appModule` is **still unverified**, because exercising it needs `androidContext()`,
  hence Robolectric plus an `:app-android:testDebugUnitTest` source set. **That source set now exists, and `AppModuleGraphTest` (PR #43) resolves every `appModule` definition — CI-pending, so until #43 merges, C1 is still OPEN on `main`.** **Standing
  rule (from agent 1, PR #35):** any new `app-android` Koin registration that takes
  another Koin-resolved dependency must be covered by a `checkModules()` call or a
  `koin.get<…>()` smoke test — `:app-android:assembleDebug` will not catch it.
- **`DownloadManager?` parameters were inserted mid-list in shared composables.**
  Agent 3 added `downloadManager: DownloadManager? = null` as the 7th of 12 parameters
  in `HomeScreen` and 7th of 8 in `SearchScreen`. This compiles and behaves correctly
  **only** because every `DhunAppShell` callsite uses named arguments. A future
  positional caller would silently misbind. Append new optional parameters at the end.
- **Two worker sessions share one branch, so their PRs cannot be gated separately.**
  PR #34 bundles agent 4 (desktop) + agent 5 (verify/docs); PR #36 bundles agent 2
  (Library) + agent 3 (download UI). Merging either lands both agents at once — a
  standing violation of the single-session-branch rule.
- **Agent status files are not reliably in the tree.** Agent 1 added
  `agent-1-status.md` to `.gitignore`, so its status exists only in the PR #35 body.
  Reconciling by reading status files alone would have missed agent 1 entirely.
- **The real Android Koin `appModule` and the offline-probe runtime task are both
  unexercised by CI.** `ci.yml` compiles `tools/playback-probe` but never runs
  `:tools:playback-probe:offlineProbe`, so `offline-verdict|PASS` is not established
  even though the probe compiles.


## 2026-09-07 — Windows "second small window on startup": fixed twice, hardware re-test still required

The user's report that opening DHUN on Windows also opens a second small mini-player
window is **not an open code defect**. It is recorded in
`docs/decisions/ADR-004-remove-separate-miniplayer-window.md` (ACCEPTED, user decision
2026-09-06) against the `test` build published `2026-09-06T06:51:40Z`, and it has been
fixed twice:

- **PR #28** (`b8f148d`) deleted `ui/MiniPlayerWindow.kt`, removed the second Compose
  `Window` from `Main.kt`, and stripped the SMTC `GetWindowRect`/`SetWindowPos` calls.
- **PR #34** (`d1e0408`) removed every `JOptionPane` startup/fatal path — the last
  surface able to own a second small native window.

A static audit of `origin/main` @ `481b77b` finds **no surviving second-window path**:
exactly two `Window(` calls in `Main.kt` and they are mutually exclusive (the
startup-error window is gated by `initError != null && koinInstance == null` and ends
in `return@application`); `JOptionPane` import count 0; no `JDialog`/`JWindow`/
`JFrame` instantiation; `showMainWindow()` only toggles `isVisible` on the existing
window; `DhunTray` builds a `TrayIcon` + `PopupMenu`, not a frame; `Smct` uses
`FindWindowW` only to locate the existing `SunAwtFrame` HWND.

**Limitation that remains:** this is a **static** audit. No Windows machine, display,
or jpackage runtime exists in this environment, so the one-window startup behaviour
has **never been verified on hardware** — not for `481b77b`, and not for any earlier
build. Green CI compiles the desktop module; it cannot observe a window. Any user
report against a build older than `481b77b` (published `2026-09-07T04:58:25Z`) does
not describe the current code.

## Latest Windows result / merged repair — 2026-09-06

The user's negative report (install-over fails “Another version…”, audio
fails, Home does not page, player glyph placement/shuffle/colour styling not
accepted) concerned the old `test@0920148`, 07:22:29Z build. That is now
superseded: the repair code merged through PR #30 (`76c68eb`) and the branch
accumulated through PR #32 (`862f0ac`), with main CI 34072908037 / test-release
34072908097 passing, and the rolling `test` pre-release published 2026-09-07T01:29:28Z
at `862f0ac` (MSI 112,136,192 B, APK 17,516,190 B). The user-machine re-test
against this *newer* build is still required; the earlier failure cannot be
explained away as Actions-IP-only gating.

Repairs are **merged in PR #30**; the session branch is retained. Code CI passes on `b6d47bd` (branch 34030728903 / PR 34030730736).
Native packaging run **34030730743** also passes: MSI **1.34.1**, actual
version/upgrade identity, published-1.0.5 install-over with userdata/cache
sentinels preserved, upgrade-flag removal preserving data, and explicit
uninstall cleanup after reinstall. Main publishing repeated these checks
successfully for MSI 1.36.1 and uploaded APK/MSI/checksums to `test`.

The first native test caught real userdata deletion in MSI 1.33.1. That
artifact was withheld and the PR not merged. The installer finalizer now
adds an upgrade-only cleaner-property guard plus a narrow legacy HKCU
cleanup bridge; it uses built-in Windows PowerShell without execution-policy
bypass, and never moves/deletes userdata itself. Raw `packageMsi` output is
not safe to distribute without `stage_msi.ps1` finalization. Back up user
data before testing. If an old-version upgrade is cancelled after legacy
preparation, its old cleanup registration can remain suppressed until a
successful retry; this preserves data but is not perfect rollback hygiene.

The sandbox still cannot run Java locally. The manual dispatch API remains
403-denied and is not retried; normal PR/main workflows supplied native
verification and rolling test publishing. The publisher still reports a
Node-20 deprecation warning for download-artifact v4; this is an action-runtime
maintenance item, not a compiler failure or a claimed zero-warning audit.
**No app launch, actual VLC/audio playback, live Home/user visuals, media-key
integration or soak acceptance is claimed.** Installer sentinels on a hosted
Windows runner are not the user's complete hardware test.

The user-provided Windows yt-dlp installation state is unknown. The old
locator could miss an installed `yt-dlp.exe`; the new candidate checks PATH /
`DHUN_YTDLP` and provides explicit missing-tool evidence. yt-dlp remains
optional/user-provided, not bundled; no cookies, login or PO-token minting was
added (ADR-003 is now ACCEPTED — Option C staged-wave fan-out — which only
changes the *scheduling* of the existing tokenless identities, never their
membership/order; no credentials are introduced).

MSI ProductVersion must advance independently of the app's semantic version.
The candidate keeps the stable upgrade UUID and uses a run/attempt sequence
(plus a stale-ref publishing guard). Manual packaging must also supply a
higher internal version; future stable packaging must not reset it to 0.1.0.
Hosted-Windows install-over/data sentinels pass; the user-machine upgrade and real library/queue preservation still need a re-test.
See `docs/verification/12-desktop-native.md` and `14-release.md` for evidence.

## Platform and service limitations

- Web platform intentionally absent from v1 (browser YouTube streaming is
  blocked by PO tokens/SABR for third-party apps — see
  PROBLEMS_AND_FIXES.md P7).
- Stream extraction depends on maintained upstream extractors; when YouTube
  changes, playback breaks until a patch release. The daily rot-drill CI
  detects this within 24h.
- The pinned NewPipeExtractor v0.26.5 remains a non-fatal recovery watch,
  not the production primary. Both platforms use the own player-client
  chain; Desktop adds the optional yt-dlp fallback (ADR-001). No live
  evidence of NewPipe recovery was verified in this session.
- Datacenter/server IPs are bot-flagged by YouTube's player endpoint more
  aggressively than residential IPs; the rot drill may show resolve-step
  failures on CI runners that do not affect normal users. Repeated red +
  local green = investigate; both red = rot.
- **CI-network vs residential gating (measured 2026-09-05):**
  - Run **33961533965** / **33968612285** (`main@a554594`, yt-dlp-only probe):
    yt-dlp 2026.08.19 bot-gated ("Sign in to confirm" → `AuthRequired`) from
    the Actions runner while metadata PASS.
  - Run **33968950214** (`arena/01a07170-dhun@10ad025`, production chain):
    **both** `OwnClientStreamResolver` (web_remix + visionos + tv all
    `AUTH_REQUIRED`) **and** yt-dlp 2026.08.19 bot-gated from the same
    runner class; metadata (version/search/related) still PASS; NewPipe
    still `Parse(JSON too short)`. Full `AuthRequired.detail` now rides
    along. This is stronger CI-network evidence: Android's only engine and
    desktop's primary+fallback are all gated from GitHub-hosted runners as
    of 2026-09-05.
  - Those are historical runner observations, not proof that IP class is
    the only cause. Latest scheduled run **34011539225** (2026-09-06,
    `dd1ab31`) reports production/own Unavailable, yt-dlp WATCH AuthRequired,
    metadata PASS; issue #14 remains open. The fresh Windows failure is
    independent user-impact evidence. Keep the kill switch and byte
    validation; no cookies/sign-in without an ADR + user sign-off.
- **Rot-drill probe coverage (fixed and live-proven 2026-09-05):** the
  fatal resolve step now drives the production own-client→yt-dlp chain and
  emits `WATCH|own-client` / `WATCH|ytdlp` / `WATCH|newpipe-stream`. Run
  33968950214 confirmed those lines fire on CI.
- YTM lyrics via InnerTube are unsynced text only; synced lyrics are now via LRCLIB fallback (`LrcLibSource` + `LyricsRepository` cache→YTM→LRCLIB, see Lyrics bullet above) — YTM remains primary unsynced fallback.
- The PLAYLISTS search filter returns mixed result types from YouTube;
  classification routes them by browseId prefix (harmless, refined later).
- The shared module now builds **Android and JVM** targets; real Home,
  Search, Library, browse and player UI replaced the original harness.
  Android/native installer app-icon acceptance remains separate from
  the in-app vector icon repairs.
- Android: stream resolution is the own-client only (ADR-001). The
  2026-09-05 chain tries WEB_EMBEDDED → VISIONOS → TV → TV_DOWNGRADED →
  TV_SIMPLY → MWEB → WEB_REMIX (tokenless, no cookies). On networks where
  every identity is gated, playback shows a typed AuthRequired error with
  per-client detail instead of audio. Rot-drill 33968950214 showed the
  previous 3-identity chain fully gated from Actions IPs; residential
  impact is verified on device, not assumed from CI red.
- Android background playback (Phase 1 directive, 2026-09-05): the
  `MediaSessionService` is now a genuine FOREGROUND service (mediaPlayback
  type) with the live media notification
  (`MediaStyleNotificationHelper.MediaStyle(session)`), and the app asks
  the user once per process for the battery-optimization exemption
  (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) while in use. What Android does
  NOT let any app request programmatically: MIUI/HyperOS "Auto-start" +
  "lock in recent apps", and OneUI "Unrestricted" battery setting — those
  are manual per-device toggles. If background music still stops on a
  user's device, that is the cause (checklist:
  docs/verification/03-android-skeleton.md). Until toggled, aggressive OEM
  cleaners can still kill the service.
- Desktop compilation is part of current CI (verified green at 34018809911),
  and MSI packaging is green at 34018809913. These do not verify native
  playback/integration or the unpublished candidate. Packagers require a
  positive-major native version; the old fixed 1.0.5 prevented rolling upgrades.
- Data layer (Phase 05+11+14): schema is now **v3** (v1 + `migrations/1.sqm` `LyricsCache` + `migrations/2.sqm` `DownloadedTrack`); the DB file is
  `dhun.db` (Android app-private storage — deleted with the app;
  desktop packaged = `<installDir>/userdata`, also deleted with the
  MSI/DMG/DEB; desktop `gradle run` = `%APPDATA%\DHUN` /
  `~/Library/Application Support/DHUN` / `~/.local/share/dhun`). Restored
  sessions come back **paused** at the saved position — stream URLs expire,
  so the desktop player re-resolves lazily on the first play press.
  Playback history is local-only; nothing leaves the device.
- Desktop runtime needs system libVLC. The optional yt-dlp fallback is a
  separate user-provided executable/module (PATH or `DHUN_YTDLP`); it is not
  installed with VLC. Missing fallback is diagnosed in the candidate, not
  silently classified as an offline network.
- **Visual system lock (2026-09-05):** Material 3 only. **No Liquid Glass**
  renderer, no continuous full-res reblur. Atmosphere = **glass-morphism**
  tokens (translucent multi-stop fills, sheen, hairline edge) on chrome;
  content stays sharp. Real backdrop blur only on FullPlayer artwork layer
  (once-per-track via `BlurredArtworkCache`) + lightweight shell ambient
  wash from now-playing seed colors.
- Design system (Phase 06): `GlassCard` uses `Modifier.blur()` / `RenderEffect`
  on Android 12+ (API 31+) and Desktop Skiko; below that floor it degrades to
  a translucent scrim (`DhunColors.glass` 60% #99111111 + 10% white border) — still
  glassy but not blurred. Verified via `ComponentCatalogScreen` over a gradient
  backdrop; screenshot pending. The design tokens are the single source of
  truth; throwaway harness screens still contain raw hex/dp (they are deleted in
  Phase 07 when real Home/Search replace them — not counted as production code).
- Player UI (Phase 08+11): FullPlayer background blur has the same <API 31 floor —
  below it the artwork sharpens and the scrim carries legibility. Swipe-remove
  is horizontal-drag–based so it works with a mouse on desktop as well as touch.
  Volume slider is desktop-only; Android relies on hardware volume keys.
  Lyrics tab now syncs (Phase 11 LRCLIB + cache → YTM → LRCLIB) — see Lyrics bullet for karaoke/rate-limit caveats.
- Browse pages (Phase 09): artist/album/playlist parsers cover the current
  YTM browse layouts (single/two-column, legacy + responsive headers); the
  companion fixtures are schema-authored in the sandbox (YT egress blocked
  there) and scheduled for live re-capture in the Phase 09 hardware pass on a
  network-capable machine — the rot drill guards drift. "Videos" shelves on
  artist pages are intentionally skipped in v1.
- Lyrics (Phase 11): FullPlayer Lyrics tab now has synced lyrics via LRCLIB fallback (`shared/lyrics/LrcLibSource` → `GET https://lrclib.net/api/get?artist_name=&track_name=&album_name=&duration=`, parsed by `LrcParser`). `LyricsRepository` is `cache → YTM → LRCLIB → NotAvailable` with `LyricsCache` table (SQLDelight schema v2, `migrations/1.sqm`, `dhun.db`) — `Synced`/`Unsynced` cached, `NotAvailable` not cached (so lyric-less tracks still hit network each open, ~600 ms). `LrcParser` handles `[mm:ss.xx]`/`[mm:ss.xxx]`/`[mm:ss]`, multi-timestamp (`[00:10.00][00:12.00]Repeat` → 3 lines), strips enhanced `<mm:ss.xx>` word timings to line granularity (per-word karaoke deferred), skips metadata `[ti:/ar:/al:/by:]`, sorts by `startTimeMs`; blank lines kept as `` → UI shows "♪". `LyricsTabContent` shows active line `titleMedium` bright centered with smooth `animateScrollToItem`, tap line → `seekTo(startTimeMs)` (±0 s, within ±1 s spec), unsynced as scrollable `Text`, empty as `EmptyView`. LRCLIB is a volunteer service — 429 maps to `NotAvailable` (YTM remains unsynced fallback, no retry banner in v1). Cache re-serializes synced as `[mm:ss.cs]` (centisecond, 10 ms rounding for 3-digit inputs — audibly negligible). Very similar title/artist covers may return wrong LRC (LRCLIB fuzzy) — no client-side pick in v1.
- Library & History (Phase 10): bottom nav now has a dedicated **Library** tab (Home/Search/**Library**/Catalog) — `shared/ui/library/LibraryScreen` with three pill tabs. History groups by local calendar day via `GetHistoryUseCase.groupByDay` using `TimeZone.getDefault().getOffset(now)` (raw+ DST) — groups reflect the device's current offset at query time (travel-day history groups by the new zone — same tradeoff documented in `docs/verification/10-library.md`). The ViewModel caps the observed history at 300 most-recent rows for list virtualization (the DB retains all; raise if users hit the cap). Favorites are ordered `addedAt DESC` (newest ♥ on top); drag-reorder handle is shown but is a no-op in v1 — explicit `favorite_position` will be added if users request manual ordering. `RecordPlay` contexts are now wired: Home→`HOME`, Search→`SEARCH`, Artist→`ARTIST`, Album→`ALBUM`, Playlist→`PLAYLIST`, Related/Radio→`QUEUE`, Library→`LIBRARY`, History→`HISTORY` (via `PlayerViewModel.setPlayContext` + `LibraryViewModel` lambda). “Albums/Artists saved” tabs are deferred — schema has `Track.albumId/artistId` but no `SavedAlbum`/`SavedArtist` tables; those tabs will be added when typed saves land. Empty states for all three Library tabs use `EmptyView` (no spinner).
- Desktop native (Phase 12, code complete; hardware open): system tray uses
  AWT `SystemTray` (works Windows/Linux/macOS; silently absent on headless —
  app keeps working). SMTC phase 2 now activates WinRT for the AWT HWND,
  updates title/artist/album plus a best-effort remote thumbnail, mirrors
  playback and queue-button state, and registers `ButtonPressed` through a
  retained base-JNA COM callback. Startup logs the full HRESULT-guarded
  `SMTC probe PASS/FAIL — … phase2=ok/FAIL (…)` line; `-Ddhun.smct=false`
  disables it. **This integration is not hardware-verified in the sandbox**:
  Windows media-key round trip, lock/quick-settings tile, remote thumbnail,
  tray, close-to-tray, geometry, and clean MSI install remain
  open in `docs/verification/12-desktop-native.md`. If activation or event
  registration fails, the app intentionally remains usable through the tray
  and keyboard shortcuts (Space/←→/Ctrl+←→/Ctrl+F).
  Close-to-tray is on by default (`SettingsKeys.CLOSE_TO_TRAY`).
  **The separate 320×88 mini-player window was removed on 2026-09-06 per user
  decision (ADR-004)** — the docked in-app MiniPlayer above the bottom nav is
  the product's mini-player; the Ctrl+M toggle went with the window, so the
  desktop app now opens exactly one window; the latest user report confirms
  this after manual reinstall. Window geometry
  (`SettingsKeys.WINDOW_GEOMETRY`) persists across restarts. jpackage MSI is **per-user** (`perUserInstall`, upgradeUuid
  `31ddb86b-9666-4071-b11c-45f16fa4682d`), not Authenticode-signed (SmartScreen
  warn is expected). Runtime data is `<installDir>/userdata` so Apps-and-Features
  uninstall is intended to remove DB + audio cache (clean-target cleanup
  evidence remains open). Published ProductVersion is 1.0.5; dynamic
  candidate versions are local, and upgrade/data preservation is unverified.
  **Phase 14 Windows JVM fix (2026-09-06, main@e90dba6, PR #22):** MSI
  `dhun-test.msi` installed but launch showed \"Failed to launch JVM\".
  Root causes: (1) bundled jlink runtime omitted `java.sql` (and
  `jdk.unsupported`/`java.naming`) needed by SQLDelight/sqlite-jdbc —
  the Compose plugin does not auto-detect modules; (2) `DesktopDhunPlayer`
  eagerly constructed `MediaPlayerFactory` before the window, so a
  missing VLC crashed startup. Fixed in `app-desktop/build.gradle.kts`
  via `modules(\"java.sql\", \"java.sql.rowset\", \"java.naming\",
  \"jdk.unsupported\", …) + includeAllModules=true` and `packageVersion`
  `1.0.5`; `DesktopDhunPlayer` now degrades to an `Error` state with
  install-VLC guidance; `Main.kt` logs every startup exception to
  `<installDir>/userdata/dhun-startup.log` (fallback `%TEMP%`) and
  shows an AWT dialog + minimal error Window so the MSI user sees the
  real cause. `DataLayer` now falls back to in-memory DB on file-DB
  corruption. MSI `1.0.5` built at `34011563630` (112 MB, `includeAllModules`);
  the user subsequently confirmed launch (and now one-window startup after
  manual reinstall). Clean-target/tray/log verification remains OPEN;
  failed install-over and failed audio are separate current blockers.

## Phase 14 — ADR-006 persistent offline downloads (2026-09-07)

- **Foundation + engine implemented and CI-green on merged PR #33 at
  `f157245`:** schema v3 `DownloadedTrack` table (`migrations/2.sqm`),
  `DownloadRepository` + `SqlDelightDownloadRepository` wired as
  `DataLayer.downloads`, `DownloadStorage` file abstraction,
  `StreamDownloader` (Ktor, Range-resume, progress, resolving User-Agent
  isolation, cancellation keeps the `.part`), `DownloadManager` +
  `FileDownloadManager` (bounded 3-slot pool, atomic `.part`→final commit,
  best-effort artwork, QUEUED/DOWNLOADING/COMPLETED/FAILED/PAUSED state
  transitions), and jvmMain `JvmDownloadStorage`. Tests
  `DownloadRepositoryTest`/`StreamDownloaderTest`/`FileDownloadManagerTest`
  are green in `:shared:jvmTest`.
- **Offline-first playback routing is implemented and CI-green:** the shared
  `OfflineFirstStreamResolver` returns a `file://` URI for a COMPLETED download
  (deferring to the network chain otherwise); Android's `PlaybackGraph` routes
  `file://` to a `FileDataSource` (via `SchemeRoutingDataSource`) and checks
  the download repo before the network resolver; the desktop `MusicProvider`
  wraps its resolver with `OfflineFirstStreamResolver` and the vlcj player
  skips cache-fill/pre-buffer when a resolve returns a local `file://` MRL.
  Download managers are wired into both platforms' DI.
- **Deterministic probe coverage is now available:**
  `./gradlew :tools:playback-probe:offlineProbe --offline --no-daemon`
  uses the real JVM SQLDelight download repository plus a valid WAV fixture,
  asserts the completed row resolves to `file://`, opens the local file, checks
  the RIFF/WAVE header, and fails if the network resolver is called. The
  sandbox could not execute this command because Java/JAVA_HOME is unavailable.
  Branch CI run `34080947691` successfully compiled the probe, but the existing
  workflow does not execute `offlineProbe`; a JDK-equipped checkout or explicit
  CI execution step is still needed for runtime PASS evidence.
- **Hardware limitation remains explicit:** the probe verifies shared/JVM
  repository-to-file loading only. It does **not** verify Android Media3
  `FileDataSource`, Desktop vlcj decoding, airplane-mode behavior, or audible
  offline playback. A real Android device and Desktop/PC remain mandatory for
  those checks; no agent or CI run can close that gate.
- **Download UI is wired but minimal:** a Library "Downloads" tab lists
  downloads (play/remove/clear-all + storage byte summary), and a "Download for
  offline" action appears in the track overflow menu. There is **no** per-track
  download badge, no Android foreground/WorkManager download service (downloads
  run on the manager's worker pool inside the app process), and no dedicated
  storage-management screen beyond the Downloads tab's clear-all — these remain
  open follow-ups.
- Downloads are **persistent** (survive until the user deletes them) and are
  distinct from the ADR-005 LRU stream cache, which is volatile and evicts on
  budget.

## Phase 14 — robustness / rot-drill / release (2026-09-05)

- The daily live extraction workflow is wired and **failure path is
  live-proven** on the fixed branch (run 33968950214). It has **not**
  produced a green `PROBE|verdict|PASS` from GitHub-hosted runners. Latest
  scheduled failure is 34011539225 (production Unavailable, yt-dlp WATCH
  AuthRequired). User Windows audio also fails; successful playback and
  validated bytes on a real user network remain gates, not assumptions.
- Android currently caches resolved stream URLs for five hours and invalidates
  them on HTTP 403. **Android audio-segment cache** (Phase 14) is now in
  code: Media3 `SimpleCache` LRU under `cacheDir/audio-segments`, default
  1 GiB (`SettingsKeys.CACHE_SIZE_MB`), stable keys = video id, offline
  replay of already-downloaded spans when resolve fails. Hardware offline
  check OPEN. **Desktop audio cache** (Phase 14, `AudioFileCache` in
  `shared/jvmMain`, wired into `DesktopDhunPlayer`): libVLC has no
  data-source layer, so desktop caches **whole tracks** (`<data dir>/cache/
  audio/<videoId>.audio`, LRU by last-used, same `CACHE_SIZE_MB` budget).
  Consequences: a first play streams AND downloads (bandwidth ×2 for that
  track); a track only becomes offline-playable once the background fill
  completes (skipping mid-track cancels the fill, nothing is kept); cache
  hits play the local file with no resolve. Desktop offline check on a real
  machine OPEN. Cache budget changes apply on next process start (both
  platforms). The rolling `test` APK/MSI is not the signed
  stable `v0.1.0` release.
- Phase 14 Android/Desktop soak tests, clean-target installation checks, and
  release evidence remain open because this environment has no Android device,
  OEM runtime, Windows machine, libVLC runtime, or display.

## 2026-09-09 — Candidate 22 equalizer (shared model + desktop vlcj; not audible)

- Platform-neutral 10-band EQ (VLC `f_vlc_frequency_table_10b` + 18 VLC
  presets) lives in `shared/.../player/equalizer`. Desktop applies it through
  the existing vlcj `MediaPlayer.audio().setEqualizer` API. **`DhunPlayer` is
  unchanged.** There is no Compose EQ screen in this slice — presentation is
  a pure `EqualizerUiModel` snapshot for a later UI to bind.
- **Android `AudioEffect` is not in this slice.** Enabling EQ on Android is a
  later stream.
- **CI green is compile + unit tests.** This environment has no libVLC and no
  audio device; nothing here claims audible EQ on hardware. Native apply is
  best-effort and silent when VLC is missing.

## 2026-09-09 — Candidate 28 themes (PR #49, `arena/01a08455-themes`) — light scheme + accent selector

Limits found while building it. Each is a measured or code-verified fact;
**visual appearance on hardware is claimed nowhere.**

- **~~Dark `error` on `errorContainer` is 3.92:1~~ RESOLVED 2026-09-16 (S5):**
  retuned `#CF6679` → `#D5798A` (same hue, container unchanged) — now 4.66:1,
  and `DhunThemeContrastTest` asserts 4.5:1 in both schemes. The
  "byte-identical dark" migration guarantee is retired for this pair only,
  with before/after values recorded here.
- **`app-desktop` tray icons stay dark in light mode.**
  `native/TrayIcons.kt:19-21` reads `DhunColors.surface/border/accent` in an
  object initialiser — once, before any composition exists — so it captures
  the default palette. Fixing it means touching a frozen file.
- **One appearance per process, not per window.** `DhunAppearance` is a
  process-wide snapshot-state holder, so desktop's startup-error window and
  main window share a theme. Intended for a toggle; stated because it is a
  constraint, not an omission.
- **Nothing persists the choice.** A store exists (`SettingsRepository` +
  `SettingsKeys.THEME` = `"dark" | "light" | "system"`, default `"dark"`), but
  **no app code reads that key** — only `RepositoriesTest` — there is no
  Settings screen, and `design` must not depend on `data` (layer inversion).
  `DhunThemeMode.id` already uses exactly those strings, so wiring is
  mechanical; until then a restart returns to dark. `"system"` is storable but
  unimplemented (no `expect/actual` hook), so `fromId("system")` returns
  `null` and falls back to dark rather than pretending to work.
- **The toggle is dev-reachable only.** It is mounted in
  `design/catalog/ComponentCatalogScreen.kt`, which has no entry point in
  either app. Making it user-reachable requires editing at least one frozen
  theme entry point (`MainActivity.kt:134`, desktop `Main.kt:278`/`:554`).
- **Kotlin nests block comments — a path glob in a KDoc is a compile break.**
  `shared/ui/**` inside a KDoc opens a nested comment that nothing closes, so
  the enclosing comment never terminates and the rest of the file is
  commented out. This bit both this session (caught by a delimiter-balance
  sweep before push) and PR #45 (caught by CI). Do not quote path globs in
  Kotlin comments.
- **`workflow_dispatch` is refused for an agent session's token** (`HTTP 403:
  Resource not accessible by integration`), and Actions job logs are unreadable
  here (the log CDN is behind the same egress wall as Maven). If a workflow
  only triggers on `pull_request` and that event does not fire for a given PR,
  its jobs need a human — which is exactly how `apk`/`msi` were obtained for
  PR #49 (run `34315184472`).

## 2026-09-17 (~03:20 UTC) — support ticket submitted (`arena/01a0ad18-dhun`)

- **GitHub Support ticket submitted by the user (~03:15 UTC)** for
  workflow 348098190 after both file-level fix attempts failed.
  GitHub's own diagnostic page confirmed the diagnosis: "stale or
  corrupted workflow registration... no self-service endpoint for
  forcing a clean re-registration". Ticket text is recorded in the
  2026-09-17 agent chat session (not committed to the repo per
  user request — PR #81 removed the earlier draft file).
- **Awaiting support response.** Until then: expect the phantom
  0-job push run to continue firing on every `main` push (the
  most recent: 35176591866 on the PR #81 merge); ignore those
  runs — they carry no probe output. No further edits to
  `.github/workflows/rot-drill.yml`.
- **Phantom-merge-run count as of `78b16ad`:** 6 phantom runs in
  this session alone (35170942908, 35171317970 from PRs #76/#77;
  35174080320 from #79; 35175253985 from #80; 35176591866 from
  #81; plus one on PR A's branch push before delete).
