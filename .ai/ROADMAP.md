# CURRENT ACTIVE TASK

Updated **2026-09-06 (UTC)** · session **`arena/01a0759b-dhun`** · last verified GitHub `main` / `test` **`0920148`** · rolling release published **`2026-09-06T07:22:29Z`**. The repairs below are a **CI-green branch candidate, not in that installer**. Latest branch HEAD **`33cc71b`**, CI **34026254275 success**.

**Latest user clarification:** the user meant **do not invoke the one-time action that ends the Arena session**, not a general restriction on ordinary development/GitHub work. Continue coding, committing, pushing and testing on this same branch; do not create extra working branches. The exact session-ending UI action has not been identified, so ask for its label/screenshot before invoking anything that might finalize the session. Do not assume that PR creation or merge is harmless to Arena's session lifecycle. The earlier CI-only wording below describes the completed checkpoint, not a permanent ban. No branch deletion or repository-setting change is authorised merely by the question about branch count.

**Latest error / dispatch boundary:** the build-only workflow is pushed at `9317050`, but `gh workflow run test-release.yml --ref arena/01a0759b-dhun -f build_only=true` was denied with **HTTP 403: Resource not accessible by integration**. GitHub's packaging-runs API confirms **no branch packaging run started**. Do not bypass that integration permission denial. The GitHub connection needs attention/reconnection for agent-initiated dispatch, or the owner can manually run this workflow on the existing branch from GitHub Actions. Do not choose `main`; keep build-only enabled/defaulted. An initial `--json` CLI attempt was rejected locally because this installed gh version accepts string-valued inputs; that attempt also did not start a run.

**Current technical task — branch artifacts, no session finalization:** `.github/workflows/test-release.yml` now has a default-on `build_only` dispatch input, read-only build permissions and a job-level main/ref/mode publishing guard. Non-main refs cannot publish even if that input is false, and branch builds cannot cancel main's release group. Reusing this workflow preserves the MSI run-number sequence. `scripts/stage_artifact.py`, `scripts/msi_helpers.ps1`, `scripts/stage_msi.ps1` and `scripts/check_msi_upgrade.ps1` add exact binary provenance, actual MSI property validation and a disposable-hosted-Windows install-over/userdata/uninstall smoke check. `docs/verification/windows-candidate.md` goes in the artifact for the user. These packaging changes are **not yet CI/Windows verified**; do not call the candidate built yet.

**Phase: 14 — Robustness, rot-drill, release v0.1.0. IN PROGRESS.** The user has now re-tested Windows: install-over fails with **“Another version of this product is already installed…”**; manual uninstall/reinstall launches **one window**. Audio still fails, Home still does not load further pages, and transport icons/alignment/colours are not accepted. Do not repeat ADR-004's already-merged separate-window removal; retain the docked MiniPlayer.

**Local review history:** the earlier “continue” request kept the work local. The subsequent explicit **“permission approved”** authorises the branch CI checkpoint only, not a release. Hardened `HomeFeedParser.kt` against mixing horizontal/vertical action targets and added 17 on-disk synthetic response fixtures; fixed cancelled Previous/Next presses and hold cleanup through `ui/player/TransportPress.kt`, plus stale seek-duration handlers in `FullPlayer.kt`; extended `YtDlpStreamResolver.kt`'s own timeout to include output draining after process exit, single disposal, typed start-denial and exact HTTP-429 classification. The Kotlin regressions now **pass in branch CI at `75c4a8b`** (details below). `scripts/validate_fixtures.py` now checks the exact JSON files used by tests without a JVM.

**Exact current files / repair batch for branch CI:**

- **Installer:** `app-desktop/build.gradle.kts`, `.github/workflows/test-release.yml`, `scripts/installer_version.py` and its test — increasing numeric MSI version including fresh rerun builds, stable upgrade UUID / public `test` asset, stale-ref publishing guard. `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt` logs the internal installer version.
- **Playback:** `shared/src/jvmMain/kotlin/dev/dhun/extraction/YtDlpStreamResolver.kt`; `shared/src/commonMain/kotlin/dev/dhun/{core/DhunResult.kt,extraction/OwnClientStreamResolver.kt,extraction/StreamResolver.kt,innertube/InnerTubeClient.kt}` — Windows PATH/launcher discovery, bounded pipe draining and cancellation cleanup, reason-preserving/sanitized diagnostics, body/header client-version agreement. The seven own-client identities remain **sequential**; ADR-003 is still **PROPOSED/unapproved**.
- **Home:** `shared/src/commonMain/kotlin/dev/dhun/{innertube/HomeFeedParser.kt,domain/UseCases.kt,presentation/home/HomeViewModel.kt,ui/home/HomeScreen.kt}` — feed-owned tokens, content-aware deduplication, visible retry/end states, bounded empty-page following, cycle and stale-refresh protection.
- **Player UI:** `shared/src/commonMain/kotlin/dev/dhun/{design/DhunIcons.kt,design/PlayerLayout.kt,design/DhunSpacing.kt,ui/player/FullPlayer.kt,ui/player/PlaybackErrorDialog.kt,ui/player/MiniPlayer.kt}` — origin-pivot SVG scaling, canonical Material shuffle/repeat paths, artwork bounded on both axes, centred and consistently styled transport/volume, selectable diagnostics instead of clipped text.
- **Regression/evidence:** `shared/src/jvmTest/kotlin/dev/dhun/{extraction,innertube,domain,presentation,design}/`, `shared/build.gradle.kts`, `.github/workflows/ci.yml`, `docs/verification/12-desktop-native.md`, `docs/verification/14-release.md`, `.ai/DEBUG_LOG.md`, `.ai/KNOWN_LIMITATIONS.md`, README, CHANGELOG, THIRD_PARTY and this file. The new Kotlin regressions **pass in branch CI 34025807972**; they are not hardware acceptance. Test-only dependencies use the project's existing Ktor/coroutines/Compose versions.

**Current verified result / last error:** **no outstanding compiler/test error at `75c4a8b`**. Branch [CI 34025807972](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972) **SUCCEEDED** on `75c4a8b9e6b3a030d24a360b0cb98923a4de5a0f`, completed **2026-09-06T09:57:34Z** (job 101466441642, 6m13s). Verified via run/job/check APIs: Python checks, **`:shared:jvmTest`**, **`:app-android:assembleDebug`**, **`:tools:playback-probe:compileKotlin`** and **`:app-desktop:compileKotlinJvm`** all passed. The check has **0 annotations**; this is not a separate full compiler-warning audit. The first run (34025629231 / `f914050`) failed on four Home `$index_` expressions; `${index}` fixed them. `remainingHomeSections` now actually retains fresh Quick picks, and CI checkout uses Node-24 v5.

**Environment distinction:** the sandbox still lacks a runnable JDK and cannot download it, but **the authorised GitHub CI route has now completed the Kotlin/build verification**. Local helper checks pass too (10 Python tests, 29 JSON fixtures, diff checks). No live extraction, Windows MSI package/install, audible playback, native integration or visual acceptance was tested by this workflow.

**Playback evidence:** screenshot/report for **“Ko Cha Ra (Official Audio)” — John Rai**, **0:00 / 4:49**, generic unavailable banner, no useful diagnostic detail. Windows/VLC versions, track ID, installer checksum and sanitized current logs were not supplied. A broken Windows `which` lookup and discarded service/fallback errors are confirmed source defects, **not proof of this machine's yt-dlp installation state or the service's rejection cause**. Playback remains a blocker.

**GitHub-verified this session (`gh` APIs + fetched history, not local completion claims):**

| Evidence | Verified state |
|---|---|
| Main / latest merges | PR #28 **MERGED** `2026-09-06T07:10:11Z` → `b8f148d`; PR #29 **MERGED** `07:18:53Z` → `0920148` (docs-only). PRs #24–#27 are also merged; audio/Home, diagnostics and restyle are all on main |
| Build/test CI | [34018809911](https://github.com/99ggprooo00-code/DHUN/actions/runs/34018809911) **success**, `headSha=0920148`: shared JVM tests, Android debug build, probe compilation, Desktop compilation |
| Rolling test publishing | [34018809913](https://github.com/99ggprooo00-code/DHUN/actions/runs/34018809913) **success**, `headSha=0920148`: APK, MSI and publish jobs. [`test`](https://github.com/99ggprooo00-code/DHUN/releases/tag/test) published **`07:22:29Z`**; MSI **112,009,680 B**, APK **17,483,422 B**, both checksum assets uploaded |
| Live drill / alert | [34011539225](https://github.com/99ggprooo00-code/DHUN/actions/runs/34011539225) **failure**, event `schedule`, `headSha=dd1ab31`; artifact `rot-drill-34011539225` exists; [issue #14](https://github.com/99ggprooo00-code/DHUN/issues/14) remains **OPEN**, with the probe output in its `04:29:14Z` automated comment |
| Pending publication | No open PRs on GitHub. This session started with a clean tree and HEAD equal to fetched `origin/main` (0 ahead / 0 behind); no pre-existing unpushed commits on this branch. This does not assert an audit of unavailable local work from other sessions |

Full Actions log-archive download hit the known sandbox `results-receiver` EOF limitation; the live failure above is verified from run metadata and [the issue's preserved probe output](https://github.com/99ggprooo00-code/DHUN/issues/14#issuecomment-5556894160), not a new local probe.

**Exact next action / session boundary:** add/run PowerShell syntax validation in ordinary branch CI, verify the packaging helper tests and existing Kotlin/Android/probe/Desktop checks, then give the owner the minimal manual-dispatch step (or request GitHub reconnection). The manually selected workflow ref must be **`arena/01a0759b-dhun`**, with `build_only=true` (this branch defaults it on and cannot publish even if it is off). Once dispatch access or an owner-started run is available, monitor actual APK/MSI builds, property/sentinel checks, artifact provenance and `publish=skipped`; do not claim an MSI exists before then. Keep this session and branch; no PR/merge, release, branch cleanup or one-time finalization action.

**Branch audit (read-only, 2026-09-06):** GitHub has **21 branches: `main` plus 20 `arena/…` branches**, including this one and **19 older session branches**. Many heads are associated with merged PRs, but not all branches are proven disposable (PR #15 was closed without merging, and some heads need further comparison). Repository `delete_branch_on_merge` is **false**. No branches were deleted or renamed, no setting was changed, and this session has used only its existing branch.

**Product gates remain unchanged:** in-place upgrade/data preservation, uncached audible playback, live Home paging and visual re-test still need a candidate build and user-machine evidence. Cache/recovery, clean-target checks, soaks and live probe bytes remain open. ADR-003 is still proposed; this workflow clarification does not approve identity fan-out or a stable v0.1.0 release.

**GitHub branch evidence:** packaging `758e0d6`, provider/Home `d77a269`, player `d2a7095`, initial verification docs `f914050`, and CI correction `75c4a8b` are pushed to **`arena/01a0759b-dhun`**. CI **34025807972** is green on the correction. Main/test still point to **`0920148`**, release publication **07:22:29Z**, and no PR exists. The published installer does not contain this branch's repairs.

**Commit/push status:** packaging/guide/helpers were committed at `6fedf8a`, with policy/evidence docs at `9317050`, and pushed only to this session branch. Automatic CI 34028039448 started on `9317050`; no packaging dispatch succeeded. The syntax-check/evidence follow-up is pending a same-branch push. Local Python tests: 19 pass; JSON fixtures: 29 syntax-valid. No new MSI, PR, release, branch deletion or settings change exists.

---

# ROADMAP — live status

Rules (permanent, from the user):
- **CURRENT ACTIVE TASK goes at the very top** — file worked on, last
  error, exact next step.
- Mark **exactly** which steps are complete. **Done = pushed + CI green +
  (where the phase says so) on-hardware verified.** Unpushed or
  CI-unverified work is NOT done, no matter how good it looks locally.
- Update this file every phase and every session.
- **Pre-push / pre-merge ritual (every time, no exceptions — user rule
  2026-09-05):**
  1. Verify state **on GitHub, not locally** (`git fetch`, `gh pr checks`,
     `gh run list`): which steps of the current Phase are actually pushed
     and CI-green.
  2. Rewrite **CURRENT ACTIVE TASK** at the very top: exact file(s) being
     worked on · last error (or "none") · exact next step.
  3. Mark the Phase step table from step 1's evidence only.
  4. Commit **everything** (no dirty tree left behind) and push to the
     session branch. **Also push any unpushed commits** found on the
     branch, and carry over (cherry-pick) any unmerged commit stranded
     on a previous session branch — nothing gets left behind.
  5. After the push, re-check CI and update the marks again if the
     status changed (a commit can't truthfully mark *itself* as pushed
     and green — the ROADMAP always lags the push by one small commit).
  Only then open/merge the PR.

---

## Instruction audit — what the user directed, and where it lives

Everything below is a standing directive from the conversation (kept here
so no session loses it; the user asked on 2026-09-05 that ALL
instructions — future updates, recurring maintenance, repo sanitization —
be stored permanently in `.ai/`).

| # | Directive | Where it's enforced |
|---|---|---|
| 1 | **Boot protocol:** no code before boot — MASTER_PROMPT → ROADMAP → `git log`; reply = phase summary + exact next step + permission ask. | `.ai/README.md` boot protocol |
| 2 | **"do it accordingly" = execute the documented plan autonomously**, no multiple-choice questions. | Session behavior |
| 3 | **ROADMAP maintenance:** CURRENT ACTIVE TASK at top; exact step marks; **unpushed/unverified = undone**. **Pre-push/pre-merge ritual** (verify on GitHub → rewrite CURRENT ACTIVE TASK → mark steps → commit all + push, incl. stranded unpushed commits → re-check CI). | Rules block above |
| 4 | **Code-first** (MASTER_PROMPT AI rules): no stubs, no TODOs in production, hardware verification before a phase is done, small commits, update ROADMAP + KNOWN_LIMITATIONS each phase, report stalls (>30 min no progress), ADR before changing a locked decision. | `.ai/MASTER_PROMPT.md` §AI Behavior Rules |
| 5 | **Rolling test release policy** (2026-09-01): exactly ONE release tagged `test`, asset `dhun-test.apk` always that name, every push to main REPLACES it, no version numbers/history for unfinished builds. Stable URLs never change. | `.github/workflows/test-release.yml` (header comment); extended 2026-09-05 with `dhun-test.msi` |
| 6 | **2026-09-05 Phase 1 (critical):** fix MediaController thread violation (ALL controller methods on main/UI thread); background/power-saver resilience across MIUI/HyperOS/OneUI; audio playback audit (InnerTube extraction, seamless playback desktop+mobile). | Done this session: crash fix + FGS/battery (items 1–3 above); audit findings below |
| 7 | **2026-09-05 Phase 2 (docs):** audit ALL instructions from conversation; store them + recurring-maintenance + repo-sanitization instructions permanently in `.ai/` so they survive across agent sessions; DEBUG_LOG with stack traces + solutions. | `.ai/` directory (user's exact words: "put the unnecessary things … into a separate branch `.ai`" — implemented as `.ai/` dir because the session is pinned to one branch; see CURRENT ACTIVE TASK item 4) |
| 8 | **2026-09-05 Phase 3 (builds & releases):** build verification + produce `dhun-test.apk` + Windows installer + GitHub **Pre-Release** with both attached; "zero compilation warnings". Stack mismatch noted & adapted: repo is KMP/Gradle — **no npm/Tauri exists here**; "npm run build" ≙ CI gradle build, "Tauri installer" ≙ jpackage `:app-desktop:createMsi`. | `test-release.yml` (apk+msi → `test` pre-release); warning policy below |
| 9 | **"Also continue doing previous work"** — Phase 12 CI green → merge PR #9 → hardware checklists → SMTC phase 2 or fallback. | CURRENT ACTIVE TASK next steps |

### Audio playback audit (directive item 6c) — findings 2026-09-05

- **Android path:** `DhunStreamCache` (TTL 5h ≈ under YouTube's ~6h URL
  TTL, invalidated on 403) → `ResolvingDataSource` rewrites
  `dhun://track/<id>` at read time; resolver chain is own-client
  WEB_REMIX → VISIONOS → TVHTML5 (ADR-001); ExoPlayer 403-mid-stream
  recovery in `PlaybackGraph` (invalidate → seek → re-prepare, max 2
  retries per track). Wake lock `WAKE_MODE_LOCAL`, audio focus,
  becoming-noisy handled. **Seamless playback** = Media3's own
  prepare-next behavior (unchanged, correct). Gap found & fixed this
  session: none in the stream path — the gaps were the thread crash and
  the FGS absence (items 1–3).
- **Desktop path:** `DesktopDhunPlayer` wraps vlcj (system libVLC) +
  same shared resolver (yt-dlp failover, ADR-001). Known limitation, not
  a defect: vlcj plays one URL at a time; the 500 ms position poll and
  track-transition logic live in the shared player layer. Re-resolution
  on 403 happens lazily on next play press (documented in
  `.ai/KNOWN_LIMITATIONS.md` — stream URLs expire, restore is paused).
  No defect found that blocks Phase 12 merge.

### Compilation-warning policy (directive item 8)

"Zero compilation warnings" = CI compiles `:shared:jvmTest` (compiles
shared), `:app-android:assembleDebug` (compiles android + shared android
target), probe step (compiles probe + **app-desktop**). Warnings in those
streams are addressed as they surface in CI annotations; K2/Compose
library-internal warnings that DHUN cannot fix are listed in
`.ai/KNOWN_LIMITATIONS.md` rather than papered over.

---

## True progress (exactly what is proven, nothing more)

Legend: ✅ done (pushed + CI green + verified where required) ·
🟨 code done, verification open · ⬜ not started.

| # | Phase | Status | Evidence |
|---|-------|--------|----------|
| 01 | Extraction spike | ✅ — probe PASS end-to-end (search 20 + resolve + audio bytes + related 50); NewPipe v0.26.5 stream extraction broken upstream → ADR-001 two-tier resolver; on-device audible check rode Phase 03 | docs/research/01 · docs/verification/01 · ADR-001 |
| 02 | Provider & domain core | ✅ — 34/34 unit tests; live smoke PASS (all filters, suggestions, radio 50, lyrics 27, stream via yt-dlp failover) | docs/verification/02 |
| 03 | Android skeleton + Media3 + lock screen | 🟨 — APK builds in CI; on-device v0.1.4: search works, playback via own-client chain; WEB_REMIX-gated networks → typed error (ADR-001) | docs/verification/03 (FGS + battery exemption on main; Android build green `34001706156`; remaining device checks OPEN) |
| 04 | Desktop skeleton + vlcj | 🟨 — Desktop compile and MSI build green on `main@8310383`; ON-DESKTOP checklist open; needs libVLC (+yt-dlp fallback) | docs/verification/04 |
| 05 | Data layer | ✅ — schema v2, 7+ repos, use cases, shared NowPlayingPersistence (queue/position/history, paused restore); repo/use-case/restore tests green in CI; write-race fix on main (latest shared tests green `34001706156`) | docs/verification/05 |
| 06 | Design system | ✅ — tokens, GlassCard real blur (API 31+/Skiko, scrim below), ArtworkImage (Coil 3.1.0), color extraction, catalogue screen | docs/verification/06 |
| 07 | Home & Search | ✅ MERGED PR #6 @ `2519290` (CI green) | docs/verification/07 |
| 08 | Player UI (Mini+Full) | ✅ MERGED PR #7 @ `3fce5e5` (CI green `33840510549`) — hardware 16-check list OPEN | docs/verification/08 |
| 09 | Artist/Album/Playlist | ✅ MERGED PR #7 @ `3fce5e5` — fixtures schema-authored (no YT egress in sandbox; live re-capture scheduled); hardware 3/3/CRUD OPEN | docs/verification/09 |
| 10 | Library & history | ✅ MERGED PR #8 @ `d27eb37` (CI green `33842104141`) — hardware checklist OPEN | docs/verification/10 |
| 11 | Lyrics (LRCLIB + YTM) | ✅ MERGED PR #8 @ `d27eb37` — test tracks live-pre-verified (4 synced EN/HI/KR/ES + 1 unsynced JP); hardware 5-acceptance OPEN | docs/verification/11 |
| 12 | Desktop native | 🟨 — tray/shortcuts/SMTC and packaging on main; PR #28 removed the separate mini-player window (ADR-004), leaving the docked in-app MiniPlayer. Latest Desktop compile + MSI publishing green on `main@0920148` (`34018809911` / `34018809913`). Prior JVM-launch fix was confirmed by the user; the user now confirms one-window startup after manual reinstall (evidence local/pending publication); upgrade, native integrations and clean-target hygiene remain OPEN | docs/verification/12 · ADR-004 · CURRENT ACTIVE TASK |
| 13 | Android polish (insets, shortcuts, tablet, soak) | 🟨 code + CI green (`8669e09` + `c2a86df` + `4de9795`, run `33958894084`); rotation/shortcut/insets/tablet/OEM soak evidence OPEN | `MainActivity.kt`, `DhunAppShell.kt`, `shortcuts.xml` |
| 14 | Robustness + rot-drill CI + release v0.1.0 | 🟨 IN PROGRESS — implementation milestones merged through PR #28, docs through PR #29 (`main` / `test` at `0920148`); CI `34018809911` and rolling test publishing `34018809913` green, published `2026-09-06T07:22:29Z`. Audio User-Agent fix, Home continuation, bounded resolve/diagnostics, restyle and single-window code are all on GitHub. Live drill `34011539225` still RED; hardware re-tests, offline/recovery checks, clean targets, soaks and v0.1.0 remain OPEN | Phase 14 step table below; issue #14; docs/verification/14 |

Deferred to v2 (NOT designed, NOT stubbed — the "Phase 15–30" pool, see
trajectory below): Web/PWA, Android Auto, Cast, equalizer, sync, downloads,
widgets, jump lists, optional cookie sign-in, themes beyond dark-first.

### Phase 12 step status — 🟨 IN PROGRESS (mini-player window REMOVED per ADR-004)

| Step | Status |
|---|---|
| SMTC spike (3-day timebox) | 🟨 **phase 2 code pushed in `7ca2f5d`, CI green `33958287878`** (`Smct.kt` — WinRT activation via JNA/combase → `GetForWindow` → `DisplayUpdater`/music metadata/remote thumbnail + retained `ButtonPressed` COM callback; corrected `IsEnabled` slot-10 probe; `-Ddhun.smct=false` off) — Windows round-trip and fallback verdict OPEN |
| System tray (playing/paused icon, 6-item menu) | 🟨 code pushed (`DhunTray.kt` + `TrayIcons.kt`, AWT, EDT-marshaled, headless-safe) — main Desktop compile green `34001706156`; hardware OPEN |
| Mini-player window (320×88, always-on-top, drag, click-opens-main) | ❌ **REMOVED per user decision — ADR-004**, code `a01f8ca` merged in **PR #28 @ `b8f148d`**, included in `test@0920148` (CI `34018809911` + publishing `34018809913` green). `MiniPlayerWindow.kt`, the second normal-startup `Window`, Ctrl+M and window-only helpers/tokens are gone. The docked Phase 08 MiniPlayer remains; one-window launch on the new MSI is still hardware-unverified |
| Keyboard shortcuts (Space, ←/→ 5s, Ctrl+←/→, Ctrl+F, Ctrl+Q) | 🟨 code pushed (KeyDown-only, text-field-safe, `Key.DirectionLeft/Right`/`Spacebar`) — main Desktop compile green `34001706156`; hardware OPEN; Ctrl+M removed with the mini-player window (ADR-004) |
| Close-to-tray (default on) + remembered geometry | 🟨 code pushed (`SettingsKeys.CLOSE_TO_TRAY`/`WINDOW_GEOMETRY`; public-AWT `Frame.getFrames()` title lookup; `WindowPosition` Dp) — main Desktop compile green `34001706156`; hardware OPEN |
| Packaging: jpackage MSI + clean-VM install | ✅ MSI packaging on GitHub (`34018809913`, `test@0920148`, 112,009,680 B). The user previously confirmed install → launch of the JVM-fixed `1.0.5` build; user now reports one-window startup after manual reinstall; 🔴 in-place upgrade failed. Clean-target installation, data preservation and uninstall hygiene remain OPEN |
| Verification doc + KNOWN_LIMITATIONS + THIRD_PARTY | ✅ done + pushed (`ffa138b`); docs/verification/12 + KNOWN_LIMITATIONS updated for the mini-player removal (ADR-004) |
| Acceptance 1–4 (media keys / tray / installer) | 🟨 OPEN — on hardware (checklist in docs/verification/12). Acceptance 3 (separate mini-player window) is **superseded by ADR-004**; the docked mini-player is covered by the Phase 08 checklist instead |

### Phase 13 step status — 🟨 CODE + CI GREEN @ `8669e09` + `c2a86df` + `4de9795` (hardware OPEN)

| Step | Status |
|---|---|
| Edge-to-edge and safe-drawing inset audit | 🟨 implemented in `MainActivity.kt` for connecting, ready, and failure roots; CI/device gesture-nav verification OPEN |
| App shortcuts: Search / Resume / Library | 🟨 static XML resources plus `onCreate`/`onNewIntent` routing implemented; launcher verification OPEN |
| Battery optimization rationale and exemption handoff | 🟨 in-app rationale plus guarded system settings handoff implemented; OEM behavior verification OPEN |
| Rotation and back-stack state survival | 🟨 selected tab, expanded player, and detail routes saved/restored through `Bundle`; Robolectric/UI test coverage and device rotation check OPEN |
| Tablet / large-screen navigation | 🟨 shared shell switches to an 840dp `NavigationRail` and docks MiniPlayer; tablet two-pane and visual verification OPEN |
| Acceptance 1–4 (rotation, back stack, shortcuts, 30-minute unrestricted battery soak) | 🟨 OPEN — requires CI plus real Android/device/OEM evidence; no Phase 13 acceptance is complete here |

### Phase 14 step status — 🟨 IN PROGRESS (GitHub verified 2026-09-06, session `arena/01a0759b-dhun`)

**Current GitHub snapshot:** `main` and the rolling `test` tag both point to
**`0920148`** (PR #29, docs-only). Main CI **`34018809911` success** covers
shared JVM tests, Android debug build, probe compilation and Desktop
compilation. Test-release **`34018809913` success** built/published APK +
MSI + checksums at **`2026-09-06T07:22:29Z`**. Links and asset sizes are in
CURRENT ACTIVE TASK. No open PRs; only the `test` release/tag exists.

**Recent work actually merged on GitHub, not outstanding local work:**

| PR | Merge / GitHub `mergedAt` (UTC, 2026-09-06) | Landed work |
|---|---|---|
| #24 | `c247fb4` · `05:56:49Z` | Stream User-Agent fix (`5a89b81`) + Home continuation and regression tests |
| #25 | `6497b1b` · `06:19:33Z` | Playback-error diagnostics + bounded 45-second resolving (`d390dd0`) |
| #26 | `ef4c8d7` · `06:47:41Z` | UI restyle / icon fixes (`11d6f75`) + CI push coverage for `arena/**` |
| #27 | `0eb8e76` · `06:59:41Z` | ADR-003 **proposal only**, not approval or parallelism implementation |
| #28 | `b8f148d` · `07:10:11Z` | Separate desktop mini-player window removed (`a01f8ca`), ADR-004 and accompanying docs |
| #29 | `0920148` · `07:18:53Z` | Post-merge documentation; no further application-code change |

Earlier Phase 14 milestones remain merged: PR #16 at `290e0f6`, #17 at
`29eeb93`, #19 at `6d81eb2`, #20 at `8310383`, #22 at `e90dba6`, and the
PR #23 documentation at `9294520`. The old `04:45:40Z` / `06:51:40Z` /
`07:13:35Z` publication snapshots are superseded by the current release.

**Read the columns separately:** ✅ in the GitHub column completes only
that named code/test/publishing milestone. It does **not** close the
hardware or release gate in the last column. All Phase 14 acceptance
criteria remain open; green build CI does not mean green live extraction.
The repair batch is on the session branch and CI is green at `75c4a8b`; only its automated code/test checkpoint is complete, not merge/release/hardware acceptance.

| Step | Complete on GitHub / evidence | Remaining gate |
|---|---|---|
| **Windows-report repair batch (session branch only)** | ✅ **`75c4a8b`**, [CI **34025807972**](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972) **success**: Python checks, shared JVM regressions, Android debug build, probe/Desktop compilation. First compile errors corrected; Quick-picks projection wired into real UI | **Not merged or released.** MSI packaging/upgrade, audible playback, live Home behavior, UI acceptance and all native/release gates still OPEN |
| Error taxonomy, 429 backoff, offline banner, recovery UX | ✅ Existing typed-error paths, `RateLimitGate`, `ConnectivityMonitor` and `PlaybackState.Recovering` merged via PR #16; recovery extended by PR #20; current main CI `34018809911` green | 🟨 Full db-path/error-taxonomy review and real offline/429/403/forced-error behavior not verified |
| Stream-URL cache (TTL + invalidation) | ✅ `DhunStreamCache` five-hour TTL and 403 invalidation on main; latest playback code builds in CI | 🟨 Stale-URL recovery on hardware |
| Bounded audio cache — Android | ✅ `DhunAudioSegmentCache` / Media3 `SimpleCache` LRU merged in PR #16; PR #20 adds corrupt-cache direct-stream fallback; current Android build green | 🟨 Offline span replay, eviction/budget behavior and cache-failure fallback on a device |
| Bounded audio cache — Desktop | ✅ `AudioFileCache` + `DesktopDhunPlayer` wiring and cache tests merged in PR #17, updated in PR #24 for User-Agent propagation; current shared tests and Desktop compile green | 🟨 Fully cached track replays offline, uncached-track error and eviction on a libVLC desktop |
| Daily rot-drill workflow + failure alerts | ✅ `.github/workflows/rot-drill.yml` merged, cron `17 4 * * *`. **Scheduled execution and alert path verified:** run `34011539225` fired on `main@dd1ab31`, event `schedule`, failed, uploaded `rot-drill-34011539225`, and auto-commented on issue #14 at `04:29:14Z` | 🟨 A green live production-probe verdict and auto-close-on-recovery remain unproven; placeholder-workflow greens do not count |
| Live extraction verdict / residential playback | 🔴 Latest real drill **`34011539225`**, scheduled on `dd1ab31`: `PROBE\|resolve+stream\|FAIL\|IllegalStateException: resolve via resolving(own-innertube-player -> yt-dlp): Unavailable`; own-client WATCH `Unavailable`, yt-dlp WATCH `AuthRequired` / bot-gate text; version/search/related PASS; final verdict FAIL. Source: run metadata and issue #14's preserved probe output; issue remains OPEN | No newer live probe verdict on `0920148`. Fresh Windows feedback also reports failed audio; do not dismiss this as CI-only gating. Candidate diagnostics and a real uncached playback/byte test are required |
| Device-feedback recovery + artwork/splash/sheets/spacing | ✅ PR #20 implementation on main, followed by PR #24 audio correction, PR #25 diagnostics and PR #26 restyle; current main CI green | 🔴 Last reported device audio result was negative. Earlier corrections are **published**, but the fresh Windows re-test still fails audio, Home and visual acceptance. New repairs pass branch CI but are not released/device-tested; keep the blocker open |
| Install/uninstall hygiene | ✅ PR #19 per-user MSI and `DhunUserDirs`, PR #22 startup ruggedization and Android private-storage policy on main; latest MSI rebuilt/published green in `34018809913` | 🟨 Fresh in-place upgrade FAILED with “Another version…”; only manual uninstall/reinstall succeeded. Dynamic MSI versions are a branch candidate (CI green, Windows install untested); upgrade/data preservation and clean-target uninstall still need evidence |
| `CHANGELOG.md` (Unreleased history) | ✅ On GitHub main, including PR #28's mini-player removal entry | Unreleased history exists; `[0.1.0]` release entry/final review waits for actual release gates |
| Rolling **test** APK/MSI publishing | ✅ **`test@0920148`**, published **`2026-09-06T07:22:29Z`** by `34018809913`: `dhun-test.msi` **112,009,680 B**, `dhun-test.apk` **17,483,422 B**, both `.sha256` assets uploaded | Not evidence for an AAB, stable v0.1.0, clean-target installation or successful playback |
| Windows desktop startup (JVM launch) | ✅ Earlier user-reported install → launch confirmed the PR #22 JVM fix (`1.0.5`) on 2026-09-06; that report is recorded in the verification docs on GitHub. APK launches too | No open JVM-launch defect from that report. Fresh one-window startup is user-confirmed after manual reinstall (local evidence). Startup-log capture, upgrade and clean-target hygiene remain separate gates |
| **Stream byte fetch presents the resolving identity** (audio fix) | ✅ **PR #24 merged at `c247fb4`**: `StreamInfo.userAgent`, Android per-open `UserAgentDataSource`, desktop downloader propagation / local-file fallback and the socket-agent regression assertion are on main and in the published build; current CI `34018809911` green | 🟨 Audible uncached-track playback and failure/recovery behavior must be re-tested on both platforms; CI does not prove the CDN accepts the bytes |
| **Home endless scroll** | ✅ **PR #24 merged at `c247fb4`**: `HomeFeedPage`, continuation through client/provider/use case/ViewModel, near-bottom trigger and index-safe shelf keys; three pagination regressions covered by shared JVM CI | 🔴 Fresh Windows re-test still does not load further Home pages. Feed-token/dedup/VM/UI repairs and regressions are on the branch with green CI, awaiting device verification. Search load-more is unchanged |
| **Bounded resolving + playback diagnostics** | ✅ **PR #25 merged at `6497b1b`**: 45-second resolver budget, regression tests and error-detail propagation; PR #26 surfaces detail in the docked MiniPlayer too; current main CI green | 🔴 Fresh failure still had no diagnostic detail. Detail-bearing Network/Unavailable, combined engine evidence and cancellable Windows fallback are on the branch with green CI; device diagnostics still need re-test. ADR-003 remains unapproved |
| UI quality (both apps) | ✅ **PR #26 merged at `ef4c8d7`**: restrained artwork palette / control accents, corrected skip-icon paths, artwork placeholders, frosted error dialogs, opaque-base floating glass, bounded volume slider and hidden Catalog tab; palette regressions in green shared CI | 🔴 User says styling is somewhat better but glyph position, shuffle shape and transport colours are wrong. Origin-pivot paths and bounded/centred player layout are on the branch, not visually accepted |
| **Single-window desktop (ADR-004)** | ✅ **PR #28 merged at `b8f148d`**: second mini-player window, Ctrl+M, window-only helpers and tokens removed; docked MiniPlayer retained. Included in green `test@0920148` | User confirms **one window** after manual uninstall/reinstall; this fresh evidence is recorded locally. Do not infer tray/SMTC/shortcut acceptance or a successful install-over upgrade |
| Android 30-minute soak | ⬜ No completed device evidence committed | Physical device, unrestricted battery/OEM settings, lock-screen controls, zero crashes/leaks; record timestamps/results |
| Desktop 30-minute soak | ⬜ No completed desktop evidence committed | libVLC desktop, transport/tray/docked mini-player/SMTC or fallback, clean exit, zero crashes; record timestamps/results |
| v0.1.0 artifacts / tag / release | ⬜ GitHub has only the rolling `test` pre-release/tag; no v0.1.0 release | APK + AAB + MSI release artifacts, clean-target runs, soaks, live drill and final docs/risk/license review, then tag/release |
| Final release documentation review | 🟨 Docs exist on main, including ADR-004, limitations and verification checklists; PR #29 is merged | Fresh Windows evidence, current release/drill reconciliation, README and icon provenance are pushed to the session branch. Update verified CI evidence at this checkpoint; final hardware/risk/license/release review remains open |

Cache configuration already stored on main: `SettingsKeys.CACHE_SIZE_MB`,
default **1024 MB**, applied on process restart on both platforms. This is
not evidence that a user-facing cache-budget control or runtime change has
been verified. Desktop caches whole tracks, not Media3-style segments.

**Phase 14 acceptance (MASTER_PROMPT):**
1. **OPEN / red:** rot drill green **and** scheduled — scheduled execution
   is **proven** by `34011539225`, but that run failed; no green live
   production-probe result for the current build. Issue #14 remains open.
2. **OPEN:** 30-minute soaks on **both** platforms — neither logged.
3. **OPEN:** all three release artifacts + clean-target install/run —
   rolling test APK/MSI builds and the prior successful launch report alone
   do not satisfy this; AAB/release and clean-target evidence remain open.
4. **OPEN:** KNOWN_LIMITATIONS current and honest — final reconciliation
   and hardware-informed release review not yet complete.

### Phase 11 step status — ✅ MERGED @ `d27eb37` (hardware OPEN)

| Step | Status |
|---|---|
| `shared/lyrics` — `LyricsSource`, `LrcLibSource` (title+artist+duration, synced LRC), `YouTubeLyricsSource`, `LyricsRepository` (cache→YTM→LRCLIB→NotAvailable), `LrcParser` ([mm:ss.xx] + enhanced tolerated) | ✅ done (in `shared/…/lyrics/`) |
| Lyrics tab (active line bright/centered, smooth auto-scroll, tap=seek, unsynced scrollable, empty) | ✅ done (`PlayerTabs.kt` → `LyricsTabContent`) |
| Persisted lyrics cache (schema v2) | ✅ done (`LyricsCache.sq` + `migrations/1.sqm` + `SqlDelightLyricsCacheRepository`) |
| Wiring Android + Desktop (Koin) + `PlayerViewModel` Track-keyed | ✅ done |
| Acceptance 1–4 (5 diverse tracks / tap±1s / LRCLIB fallback / instant second open) | 🟨 OPEN — on hardware; concrete tracks pre-verified live against LRCLIB in docs/verification/11 |
| Acceptance 5 — parser unit tests | ✅ done (`LrcParserTest`, 10 tests, CI green in PR #8) |

### Phase 10 step status — ✅ MERGED @ `d27eb37` (hardware OPEN)

| Step | Status |
|---|---|
| Library tabs Playlists/Favorites/History | ✅ done (`LibraryViewModel` + `LibraryScreen`) |
| Favorites (tap-plays, swipe-remove) | ✅ done |
| History grouped by day, relative times, long-press remove, clear-all confirm | ✅ done |
| RecordPlay wired into every play context | ✅ done (HOME/SEARCH/ARTIST/ALBUM/PLAYLIST/RADIO/QUEUE/LIBRARY/HISTORY) |
| Acceptance 1–3 on hardware | 🟨 OPEN — docs/verification/10 |

### Phase 08 / 09 / 07 — MERGED (PR #6 `2519290`, PR #7 `3fce5e5`); hardware checklists OPEN per their docs.

---

## Trajectory to Phase 30 (beyond the locked 14)

The locked plan ends at Phase 14. The user (2026-09-05) asked for the
trajectory all the way to Phase 30. The pool is the original 30-phase
vision (`.ai/PROMPT_SEQUENCE.md` audit) minus what 01–14 already cover.
**These are candidates with a suggested order — NOT designed, NOT
stubbed, NOT scheduled** until the user picks them (Doctrine: no
"later" code).

| # | Candidate | Why this slot |
|---|---|---|
| 15a | **Full-Screen player immersion polish (ADR-002 P3–P9)** — lyrics-dominant mode, blur-once cache, gesture simplicity; only after P0 extraction truth + Phase 08/11 hardware smoke | Signature UX; must not outrun streams |
| 15 | **Android native polish finish** (Phase 13 leftovers: app shortcuts, Robolectric/UI tests, tablet two-pane, 30-min soak with LeakCanary) | Same platform as the crash/FGS work just done; cheap while context is warm |
| 16 | **Audio cache (bounded LRU) + offline replay of cached tracks** | Phase 14 item pulled forward; user-visible value, no new surface |
| 17 | **Rot-drill GA** — wire `tools/playback-probe` into the daily cron (replacing the placeholder), auto-issue on red, 24h detection contract live | The Doctrine's maintenance leg; must exist before any public distribution |
| 18 | **Release v0.1.0** (signed debug-keystore APK + AAB, jpackage installers, CHANGELOG, README build docs, tag, GitHub release) | Phase 14; gates everything "real" |
| 19 | **Web/PWA evaluation** (the big deferred item; hard gate: PO tokens/SABR block third-party browser streaming — see `.ai/PROBLEMS_AND_FIXES.md` P7) | Only after the kill-switch data from 17 exists; probably "no" |
| 20 | **Android Auto** (media app on the platform; needs a stable media session — just built) | Natural once 15+18 done |
| 21 | **Cast** | Same dependency as 20 |
| 22 | **Equalizer** (Android: `AudioEffect` platform EQ; desktop: libVLC audio filter) | Feature, no platform risk |
| 23 | **Cross-device sync** (experimental; local-first DB design must survive) | Explicitly experimental in the prompt |
| 24 | **Optional cookie sign-in** (unlock age/region + personal playlists; treated as experimental) | High ToS/legal sensitivity — ADR required first |
| 25 | **Downloads beyond cache** (bounded, offline library) | Extends 16 |
| 26 | **Widgets** (now-playing / quick-play Android widgets) | Session foundation now exists |
| 27 | **Windows jump lists + tray polish** | Phase 12 leftovers |
| 28 | **Themes beyond dark-first** (accent system, light theme) | Design system is token-ready |
| 29 | **Store releases** (Play Store AAB + Windows store MSI, real signing) | After 18 proves the pipeline |
| 30 | **v1.0 GA** — soak on both platforms, RISK_REGISTER review, docs finalized, tag | The finish line |

Ordering constraints: 17 before any public build; 18 before 19–30
anything user-visible; 24 requires its own ADR + user sign-off; 19's
likely outcome is a written "no" — that is also a valid completion.

---

## Recurring maintenance & repo sanitization (standing, permanent)

- **Extraction rot:** rot-drill red → pin last-good, adopt upstream patch,
  patch release ≤72h (`.ai/RISK_REGISTER.md`). Datacenter-IP rot
  (CI-only reds) handled per the KNOWN_LIMITATIONS note.
- **Each phase:** ROADMAP (this file) + `.ai/KNOWN_LIMITATIONS.md` +
  `docs/verification/NN-*.md` + small commits.
- **Repo sanitization:** no secrets/device data/credentials in the repo;
  sanitized fixtures; `THIRD_PARTY.md` complete; no build output committed.
- **Rolling release:** `test` tag replaced, never appended; stable URLs.

---

> Operational phase-by-phase prompts (audit + rewritten sequence):
> [.ai/PROMPT_SEQUENCE.md](PROMPT_SEQUENCE.md).
