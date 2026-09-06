# CURRENT ACTIVE TASK

Updated **2026-09-06 (UTC)** · session **`arena/01a0750c-dhun`** · branch at `e1f69b4`, **CI `34014443760` green** (5 commits ahead of `main@9294520`).

**Phase:** **14 — Robustness, rot-drill, release v0.1.0. IN PROGRESS — first real hardware verdict is in, and it closed one gate and opened a blocker.**

**Hardware verdict (user, real hardware, builds from the rolling `test` release):**

- ✅ **Both `dhun-test.msi` and `dhun-test.apk` install and launch.** The "Failed to launch JVM" fix (PR #22 @ `e90dba6`, `java.sql` modules + `includeAllModules`, `1.0.5`) is **hardware-confirmed**. That gate is closed.
- 🔴 **No audio plays on either platform.** Not one track. This is now the blocking Phase 14 defect.
- 🔴 **No endless scroll** on either app.
- 🔴 **UI reads as poor** on both apps, and the two look much alike. No screenshots were provided, so nothing was restyled rather than guess.

**Exact files worked on this session (both commits):**

1. **`5a89b81` — `fix(playback): send the resolving identity's User-Agent when fetching audio`**
   `shared/…/core/Entities.kt` (`StreamInfo.userAgent`), `shared/…/innertube/InnerTubeClient.kt` (`AltInnertubeClient.userAgent` public), `shared/…/extraction/OwnClientStreamResolver.kt` (stamp the winning strategy), `shared/src/jvmMain/…/JvmStreamResolvers.kt` (yt-dlp `--user-agent`), `app-android/…/DhunStreamCache.kt` + `PlaybackGraph.kt` (`UserAgentDataSource`), `app-desktop/…/DesktopDhunPlayer.kt` (agent to the downloader + local-copy fallback when libVLC rejects a URL), `shared/src/jvmMain/…/AudioFileCache.kt`, `AudioFileCacheTest.kt`.
2. **This commit — home endless scroll + docs**
   `shared/…/core/Entities.kt` (`HomeFeedPage`, `HomeFeed.continuationToken`), `shared/…/innertube/InnerTubeClient.kt` (`homeFeedPage` / `homeFeedContinuation`), `shared/…/provider/MusicProvider.kt`, `shared/…/domain/UseCases.kt` (`GetHomeFeedUseCase.loadMore`), `shared/…/presentation/home/HomeViewModel.kt` (`isLoadingMore` + `loadMore()`), `shared/…/ui/home/HomeScreen.kt` (near-bottom trigger, index-safe shelf keys, spinner), 4 test fakes, `UseCasesTest.kt` (+3 pagination tests), `.ai/DEBUG_LOG.md`, `docs/verification/14-release.md`, this file.

**Last error, and its root cause (found by code audit, not yet re-verified on hardware):**

A googlevideo stream URL is **bound to the InnerTube client identity that resolved it**. `OwnClientStreamResolver` tries seven identities with seven different User-Agents, then returned only the URL — `StreamInfo` had no field for the identity. Every byte-reading layer then sent its own hardcoded Chrome agent (`PlaybackGraph` Android-14, `AudioFileCache` Windows-Chrome), and **libVLC cannot send a custom User-Agent at all** through vlcj. The CDN refuses the mismatch → resolution succeeds, audio never arrives. This is the same failure the drill saw once and mis-attributed to IP gating (`DEBUG_LOG` 2026-09-05: "expanded chain got googlevideo URL, CDN 403 on bytes").

Dead end recorded so nobody repeats it: calling `httpFactory.setUserAgent(…)` inside the `ResolvingDataSource.Resolver` does **nothing** — `ResolvingDataSource` constructs its upstream data source once, in its own constructor. The agent must be stamped on the live instance per `open()`.

**Exact next step:**

1. **Re-test audio on hardware with the NEXT build** (the fix is not in the currently published `test` assets — those are still `9294520`/`1.0.5`). After this branch merges and `test-release` republishes: play an uncached track on the APK and on the MSI. Capture, this time: the exact player error text (if any), `adb logcat -s DHUN` lines showing `resolved <id>: … ua=…`, OS + VLC versions, MSI sha256, and `dhun-startup.log`.
2. **If audio still fails**, the logcat `resolved … ua=…` line splits the problem cleanly: no such line ⇒ resolution is gated on that network (extraction problem, ADR-001); line present but still no audio ⇒ the CDN rejects even a matching agent (PO-token territory, needs an ADR).
3. Then: endless scroll check on Home, UI pass **once screenshots exist**, offline-cache checks, 30-min soaks, clean uninstalls, `v0.1.0`.

**Verification honesty:** this sandbox has **no JDK** and **no egress except `github.com`** (`api.adoptium.net`, `services.gradle.org`, `repo1.maven.org`, `dl.google.com`, `music.youtube.com` all `000`; `raw.githubusercontent.com` also blocked, so the media3 1.5.1 `DataSource` interface was read through `api.github.com`). CI is therefore the only executable check available here. Run `34014443760` **passed** on `e1f69b4` — `:shared:jvmTest` (which covers `shared/src/jvmTest`, so the new `AudioFileCacheTest` and 3 `UseCasesTest` pagination tests ran), `:app-android:assembleDebug`, probe, desktop compile. **Audio itself is still NOT verified** — that needs the next build on a device.

**Four CI rounds were needed to get there.** My first three `UserAgentDataSource` attempts rested on media3 APIs that do not exist in 1.5.1: instance-level `DefaultHttpDataSource.setUserAgent` (there is none — only `Factory.setUserAgent`), `DefaultHttpDataSource.Builder` (does not exist), `override val uri` (it is `getUri()`), and a missing `addTransferListener` override (abstract, not default). Recorded in DEBUG_LOG so nobody repeats it.

**Unpushed / carried-work audit:** both commits pushed to `arena/01a0750c-dhun`. Earlier this session, all 16 remote `arena/*` branches were audited (`gh api …compare` + `git cherry`) — nothing stranded, no cherry-picks needed. PR #24 (the earlier docs sync) is open and CI-green; this work supersedes its next-step section.

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
| 12 | Desktop native | 🟨 — tray/mini-player/shortcuts and SMTC phase 2 on main; Desktop compilation + MSI packaging green on `main@9294520` (`34012157207` / `34012157287`, **`1.0.5` with JVM fix**); Windows runtime and clean-install **still OPEN — new MSI needs install→launch verification** | docs/verification/12 · `.ai/DEBUG_LOG.md` |
| 13 | Android polish (insets, shortcuts, tablet, soak) | 🟨 code + CI green (`8669e09` + `c2a86df` + `4de9795`, run `33958894084`); rotation/shortcut/insets/tablet/OEM soak evidence OPEN | `MainActivity.kt`, `DhunAppShell.kt`, `shortcuts.xml` |
| 14 | Robustness + rot-drill CI + release v0.1.0 | 🟨 PRs #16/#17/#19/#20/#22/#23 **MERGED** through `main@9294520`; main CI `34012157207` and rolling test APK/MSI release `34012157287` green (`1.0.5`, MSI 112,001,488 B / APK 17,467,038 B, published `2026-09-06T04:45:40Z`). Caches, recovery, device-feedback fixes, **Windows JVM startup fix**, and Unreleased changelog all on GitHub; live drill still RED (`34011539225`); residential/recovery/offline/soaks/clean installs/v0.1.0 still OPEN | Phase 14 step table below; issue #14; docs/verification/14 |

Deferred to v2 (NOT designed, NOT stubbed — the "Phase 15–30" pool, see
trajectory below): Web/PWA, Android Auto, Cast, equalizer, sync, downloads,
widgets, jump lists, optional cookie sign-in, themes beyond dark-first.

### Phase 12 step status — 🟨 IN PROGRESS

| Step | Status |
|---|---|
| SMTC spike (3-day timebox) | 🟨 **phase 2 code pushed in `7ca2f5d`, CI green `33958287878`** (`Smct.kt` — WinRT activation via JNA/combase → `GetForWindow` → `DisplayUpdater`/music metadata/remote thumbnail + retained `ButtonPressed` COM callback; corrected `IsEnabled` slot-10 probe; `-Ddhun.smct=false` off) — Windows round-trip and fallback verdict OPEN |
| System tray (playing/paused icon, 6-item menu) | 🟨 code pushed (`DhunTray.kt` + `TrayIcons.kt`, AWT, EDT-marshaled, headless-safe) — main Desktop compile green `34001706156`; hardware OPEN |
| Mini-player window (320×88, always-on-top, drag, click-opens-main) | 🟨 code pushed (`MiniPlayerWindow.kt` + second Compose `Window`; hide-not-close; Ctrl+M) — main Desktop compile green `34001706156`; hardware OPEN; taskbar visibility is a 1.8.2 limitation (no `skipTaskbar`) |
| Keyboard shortcuts (Space, ←/→ 5s, Ctrl+←/→, Ctrl+F, Ctrl+M, Ctrl+Q) | 🟨 code pushed (KeyDown-only, text-field-safe, `Key.DirectionLeft/Right`/`Spacebar`) — main Desktop compile green `34001706156`; hardware OPEN |
| Close-to-tray (default on) + remembered geometry | 🟨 code pushed (`SettingsKeys.CLOSE_TO_TRAY`/`WINDOW_GEOMETRY`; public-AWT `Frame.getFrames()` title lookup; `WindowPosition` Dp) — main Desktop compile green `34001706156`; hardware OPEN |
| Packaging: jpackage MSI + clean-VM install | ✅ `packageMsi` `1.0.5` with `java.sql` modules — **install → launch confirmed on hardware 2026-09-06**, no \"Failed to launch JVM\"; 🟨 clean-VM *uninstall* leaves-nothing check still unobserved |
| Verification doc + KNOWN_LIMITATIONS + THIRD_PARTY | ✅ done + pushed (`ffa138b`) |
| Acceptance 1–4 (media keys / tray / mini-player / installer) | 🟨 OPEN — on hardware (checklist in docs/verification/12) |

### Phase 13 step status — 🟨 CODE + CI GREEN @ `8669e09` + `c2a86df` + `4de9795` (hardware OPEN)

| Step | Status |
|---|---|
| Edge-to-edge and safe-drawing inset audit | 🟨 implemented in `MainActivity.kt` for connecting, ready, and failure roots; CI/device gesture-nav verification OPEN |
| App shortcuts: Search / Resume / Library | 🟨 static XML resources plus `onCreate`/`onNewIntent` routing implemented; launcher verification OPEN |
| Battery optimization rationale and exemption handoff | 🟨 in-app rationale plus guarded system settings handoff implemented; OEM behavior verification OPEN |
| Rotation and back-stack state survival | 🟨 selected tab, expanded player, and detail routes saved/restored through `Bundle`; Robolectric/UI test coverage and device rotation check OPEN |
| Tablet / large-screen navigation | 🟨 shared shell switches to an 840dp `NavigationRail` and docks MiniPlayer; tablet two-pane and visual verification OPEN |
| Acceptance 1–4 (rotation, back stack, shortcuts, 30-minute unrestricted battery soak) | 🟨 OPEN — requires CI plus real Android/device/OEM evidence; no Phase 13 acceptance is complete here |

### Phase 14 step status — 🟨 IN PROGRESS (GitHub verified 2026-09-06, session `arena/01a0750c-dhun` — after PR #23 docs merge)

**GitHub-verified snapshot (this session, `gh` not local state):**
`origin/main@9294520` (PR #23 merged `2026-09-06T04:41:51Z`), main CI
**`34012157207` success** and rolling test-release **`34012157287` success**
(links in CURRENT ACTIVE TASK). The last *application-code* change is still
**PR #22 @ `e90dba6`** — the Windows "Failed to launch JVM" fix (java.sql
modules + `includeAllModules` + VLC ruggedization + startup diagnostics,
`packageVersion` **1.0.5**). `9294520` is docs-only, so the `test`
pre-release assets are the same `1.0.5` binaries, re-published
`2026-09-06T04:45:40Z`.
PR #16 merged at `290e0f6`, #17 at `29eeb93`, #19 at `6d81eb2`, #20
at `8310383`, **#22 at `e90dba6`**, **#23 at `9294520`**. These are remote
commits, not unpushed local work.

**Read the columns separately:** ✅ in the GitHub column completes only
that named code/test/publishing milestone. It does **not** close the
hardware or release gate in the last column. All Phase 14 acceptance
criteria remain open; green build CI does not mean green live extraction.

| Step | Complete on GitHub / evidence | Remaining gate |
|---|---|---|
| Error taxonomy, 429 backoff, offline banner, recovery UX | ✅ Existing typed-error paths, `RateLimitGate`, `ConnectivityMonitor`, and `PlaybackState.Recovering` merged via PR #16; recovery extended by PR #20; main CI green | 🟨 Full db-path/error-taxonomy review and real offline/429/403/forced-error behavior not verified |
| Stream-URL cache (TTL + invalidation) | ✅ `DhunStreamCache` five-hour TTL and 403 invalidation are on main; latest playback code compiles in CI | 🟨 Stale-URL recovery on hardware |
| Bounded audio cache — Android | ✅ `DhunAudioSegmentCache` / Media3 `SimpleCache` LRU merged in PR #16; PR #20 adds corrupt-cache direct-stream fallback; main Android build green | 🟨 Offline span replay, eviction/budget behavior, and cache-failure fallback on a device |
| Bounded audio cache — Desktop | ✅ `AudioFileCache` + `DesktopDhunPlayer` wiring + nine cache unit tests merged in PR #17; shared tests and Desktop compile green | 🟨 Fully cached track replays offline, uncached-track error, and eviction on a libVLC desktop |
| Daily rot-drill workflow + failure alerts | ✅ `.github/workflows/rot-drill.yml` merged; cron `17 4 * * *`; real failing probes upload logs, update issue #14, and fail the job. **Scheduled-run + alert path now has real evidence:** scheduled run `34011539225` fired, `probe` job `failure`, issue #14 auto-commented `2026-09-06T04:29:14Z` with the run link + log tail | 🟨 A **green** live production-probe verdict and the auto-close-on-recovery path are still unproven; older placeholder-workflow greens do not count |
| Live extraction verdict / residential playback | 🔴 Latest real drill `34011539225` (schedule, ran on `main@dd1ab31` — the SHA recorded in issue #14, not `e90dba6`) and prior `33970045379` attempt 2 on `d9f4083`: `PROBE\|resolve+stream\|FAIL\|IllegalStateException: resolve: AuthRequired(detail=null)`, `PROBE\|verdict\|FAIL\|extraction-pipeline-broken`; `version`/`search`/`related` PASS; issue #14 OPEN (comment `2026-09-06T04:29:14Z`) | No green live production-probe verdict since `33944557207` (`d27eb37`, 2026-09-05T04:26Z). Verify the latest APK on a residential network; do not label Actions bot/CDN gating as proven residential failure |
| Device-feedback recovery + artwork/splash/sheets/spacing | 🟨 PR #20 code on main, but **hardware verdict 2026-09-06 is negative: no audio plays on either platform** | 🔴 Blocking. Root cause found (User-Agent mismatch on the byte fetch) and fixed in `5a89b81` — **not yet in a published build, not yet re-verified** |
| Install/uninstall hygiene | ✅ PR #19 per-user MSI and `DhunUserDirs` + PR #22 startup ruggedization (log + dialog + in-memory fallback); Android private-storage manifest policy on main; current MSI `1.0.5` rebuilt green at `34012157287` (112,001,488 B, `includeAllModules`) | 🟨 Clean Windows install/run/uninstall and startup-log + absence of leftover runtime data must be observed on hardware — **no \"Failed to launch JVM\" on launch** |
| `CHANGELOG.md` (Unreleased history) | ✅ Created in PR #17 and updated in PR #19/#20; present on GitHub main (PR #22 bumps `packageVersion` to `1.0.5` but changelog `[0.1.0]` still waits for release) | Unreleased milestone complete; `[0.1.0]` entry waits for a real release |
| Rolling **test** APK/MSI publishing | ✅ `test@9294520` (verified via `gh release view test`): `dhun-test.msi` **112,001,488 B** + `.sha256`, `dhun-test.apk` **17,467,038 B** + `.sha256`, published by `34012157287` at `2026-09-06T04:45:40Z` — the same `1.0.5` JVM-fix binaries as `34011563630` (`e90dba6`) | Not evidence for an AAB, stable v0.1.0, or clean-target installation; **MSI launch must still be verified on Windows hardware** |
| Windows desktop startup (JVM launch) | ✅ **CLOSED on hardware 2026-09-06** — user installed and launched `dhun-test.msi` (`1.0.5`, PR #22 fix); no \"Failed to launch JVM\". APK launches too | None for launch. Clean-VM *uninstall* hygiene still unobserved; `dhun-startup.log` contents not captured |
| **Stream byte fetch presents the resolving identity** (audio fix) | 🟨 `5a89b81` on the session branch: `StreamInfo.userAgent` populated by every resolver; Android stamps it per `open()` via `UserAgentDataSource`; desktop downloader receives it and libVLC rejection falls back to the local copy; `AudioFileCacheTest` asserts the agent reaches the socket | 🟨 **CI-green, hardware-unverified.** Run `34014443760` passed all four steps (shared `jvmTest`, `assembleDebug`, probe, desktop compile) on `e1f69b4`. Needs the next `test` build on a device |
| **Home endless scroll** | 🟨 session branch: `HomeFeedPage` + `homeFeedContinuation` (client → provider → use case → ViewModel) + near-bottom trigger and index-safe shelf keys in `HomeScreen`; 3 new pagination tests in `UseCasesTest` | 🟨 CI-green in `34014443760` (`:shared:jvmTest` covers `shared/src/jvmTest`, so the 3 new pagination tests ran); hardware check outstanding. Search load-more was already wired and is unchanged |
| UI quality (both apps) | ⬜ Not started — user reports \"bad/terrible\" UI, no screenshots provided | Blocked on screenshots: restyling blind would likely fix the wrong thing |
| Android 30-minute soak | ⬜ No completed device evidence committed | Physical device, unrestricted battery/OEM settings, lock-screen controls, zero crashes/leaks; record timestamps/results |
| Desktop 30-minute soak | ⬜ No completed desktop evidence committed | libVLC desktop, transport/tray/mini-player/SMTC or fallback, clean exit, zero crashes; record timestamps/results |
| v0.1.0 artifacts / tag / release | ⬜ Only the rolling `test` pre-release exists; no v0.1.0 completion | APK + AAB + MSI release artifacts, clean-target runs, soaks, live drill, final docs/risk/license review, then tag/release |
| Final release documentation review | 🟨 Docs exist on main, including limitations and verification checklists | Reconcile stale CI/live-run wording in the verification/limitations docs and attach real device evidence; README, THIRD_PARTY, RISK_REGISTER, CHANGELOG final review still open |

Cache configuration already stored on main: `SettingsKeys.CACHE_SIZE_MB`,
default **1024 MB**, applied on process restart on both platforms. This is
not evidence that a user-facing cache-budget control or runtime change has
been verified. Desktop caches whole tracks, not Media3-style segments.

**Phase 14 acceptance (MASTER_PROMPT):**
1. **OPEN / red:** rot drill green **and** scheduled — cron is configured,
   but no green live production-probe verdict or first scheduled-run proof.
2. **OPEN:** 30-minute soaks on **both** platforms — neither logged.
3. **OPEN:** all three release artifacts + clean-target install/run —
   rolling test APK/MSI builds alone do not satisfy this.
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
