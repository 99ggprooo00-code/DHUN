# CURRENT ACTIVE TASK

Updated **2026-09-09 (UTC)** \u00b7 session **`arena/01a08455-dhun`** (**widgets**, merge order **fifth** of this batch, rebased onto new main) \u00b7 **`origin/main` = `d0534cd`** = **PR #45 MERGED** (`feat(shell): two-pane` @ `d0534cd`; `CI 34311465073` **success**, `test-release` **success**). Previous `origin/main` `847b258` now superseded. Branch `arena/01a08455-dhun` was **even** at `821663f`, now **rebased** onto `d0534cd` (2 widget commits replayed, no equalizer — scope pure). Verified via `git fetch` + `gh pr list` + `gh run list`.

**Board state (verified 2026-09-09 05:10Z via `gh pr list` + `gh run list`):** OPEN PRs = **#40** (`arena/01a07a6b-dhun-release`, **CONFLICTING/DIRTY**) + **#46** (`arena/01a08455-dhun` **this widgets PR**, head `821663f` pre-rebase, now rebased, **MERGEABLE** pending new CI). PR **#45** (`arena/01a08454-dhun` two-pane) **MERGED** as `d0534cd` (the rebase base). Every other worker from prior batch merged (`#43` @ `eda73e4`, `#41` @ `dd0fe14`, `#42` @ `f562093`). `rot-drill` red on every push including `main` = pre-existing issue #14, not a gate.

**This session \u2014 widgets (candidate 26 \u2014 ROADMAP deferred pool, now activated for this parallel batch):**

*   **Scope (assigned):** home-screen app widgets \u2014 Now Playing (artwork/title/transport) + quick-play, fed by existing playback-service state, updating on track/state change, failing gracefully when idle. **Additive only.** NOT in scope: Android Auto, deep playback-engine changes, on-device widget verification (CI = compile + unit tests only; launcher rendering is the user's hardware gate).
*   **Owned paths (this branch only, rebased):** `app-android/src/main/kotlin/dev/dhun/android/widgets/**` (5 files), `app-android/src/main/res/layout/widget_*.xml` (2), `app-android/src/main/res/xml/widget_*_info.xml` (2), `app-android/src/main/res/drawable/widget_*.xml` (6), `app-android/src/main/AndroidManifest.xml` (2 receivers), `app-android/src/main/res/values/strings.xml` (2 descriptions), `app-android/src/test/**/widgets/**` (4 classes, ~20 tests). **Frozen:** `shared/**`, `download/**`, `extraction/**`, `.github/**`, `tools/**`, `app-desktop/**`, `app-android/.../playback/DhunPlaybackService.kt` (observed read-only), `app-android/.../download/**`.
*   **Exact files (owned-only, additive):** `widgets/DhunNowPlayingWidgetProvider.kt` \u00b7 `widgets/DhunQuickPlayWidgetProvider.kt` \u00b7 `widgets/DhunWidgetUpdater.kt` (MediaController \u2192 RemoteViews, idle fallback) \u00b7 `widgets/WidgetIntents.kt` \u00b7 `DhunWidgetState.kt` \u00b7 `res/layout/widget_now_playing.xml` \u00b7 `res/layout/widget_quick_play.xml` \u00b7 `res/xml/widget_now_playing_info.xml` \u00b7 `res/xml/widget_quick_play_info.xml` \u00b7 `res/drawable/widget_background.xml` + `widget_ic_*.xml` (5 icons) \u00b7 `src/test/.../widgets/{DhunWidgetStateTest,WidgetIntentsTest,WidgetXmlTest,DhunWidgetUpdaterTest}.kt` + `AndroidManifest.xml` receivers.
*   **Last error:** **none** after fix `821663f` — previous 3 failures at `778dc20` were **test-correctness** (WidgetIntents not mocked, widgetCategory int vs string, dimen int vs raw) fixed in `821663f`; CI **success** for that head (`CI 34311148605` 5m7s, `test-release` `34311148558` 4m39s, `msi` pass; push `34311145646` success). Rebase onto `d0534cd` is **pending CI** — this ROADMAP commit is the rebase.
*   **Exact next step (IN ORDER, per assignment):** (1) this ROADMAP rebase commit; (2) commit **widgets code** (already staged, owned-only) on top of `d0534cd` — **scope-gated, additive**; (3) push `arena/01a08455-dhun` (force, widgets pure, no equalizer) + ensure **own PR #46** (`MERGEABLE`) shows `build-and-test` + `apk` + `msi` **green**; (4) `git fetch` + rebase if needed + re-run CI until green (merge order fifth — after #45 which is now on main); (5) `gh pr merge 46 --merge` **last**. Honest limits apply: widget render / launcher update is **not claimed** by CI.

**Live / remaining \u2014 beyond this widget work:**

1.  **Release v0.1.0 (human-gated, candidate 18).** #40 is the pipeline (release-candidate `test-release.yml` + CHANGELOG draft). Needs human `workflow_dispatch` for a `DRAFT` `v0.1.0` release; CI alone cannot finish it.
2.  **User hardware gates (never closable by CI):** Phase 15 launcher (icons + dynamic Now Playing tap\u2192FullPlayer + TalkBack + rotation/process-death) + 15a visuals (scrub-pill clamp, blur-once backdrop, swipe-down collapse, mini-player lift, spring lyrics) + **new widget launcher rendering / onResume auto-update on a real device** (deferred to user; CI will not claim it). Two-pane hardware gate is now also on main and is the user's.
3.  **Open by omission:** 30-min LeakCanary soak never run; widgets quick-play surface is this session (rebase pending). Equalizer (candidate 22) lives on its own deferred branch and **must not** ride this PR — this rebase keeps it pure.
4.  No `docs/verification/15-*.md` or `docs/verification/26-widgets.md` yet \u2014 a phase earns one when hardware evidence exists.

<details>
<summary><b>Prior snapshot (`arena/01a08427-dhun` — coordinator reconcile to main=`eda73e4`; MERGED as `847b258` via PR #44)</b></summary>
Updated **2026-09-09 (UTC)** · coordinator session **`arena/01a08427-dhun`** · **`origin/main` = `eda73e4`** = **PR #43 MERGED** (state `MERGED`, head `aabd9df`, merged 08:01:57Z). Post-merge `test-release` on main = **success** (run `34101231724`). **Both Phase-15 halves are now on `main`:** candidate **15a** (ADR-002 player immersion) via **PR #41** and **Phase 15 Android native polish** (`app-android/**` only) via **PR #43** — verified from live GitHub, not inherited.

**Board state (verified 2026-09-09):** no worker session is mid-flight; every `agent-*.md`/`phase15-android-polish-status.md` is historical. The **only OPEN PR is #40** — the release v0.1.0 pipeline (`arena/01a07a6b-dhun-release`, head `0b3f2ed`, **MERGEABLE/CLEAN**). Its required CI is green (`build-and-test` `34229561878` pass; `apk`+`msi` `34229561792` pass; `publish`/`release_draft`/`aab` `skipping` by design). #40 is **stale through #38 only** — its CHANGELOG "Unreleased"/ROADMAP predate #39–#43 and need a refresh before merge. `rot-drill` remains red on every push incl. `main` = pre-existing **issue #14** (runner IP gating, non-code, not a merge gate).

**Live / remaining — nothing code-open beyond #40.**

1. **Release v0.1.0 (human-gated, candidate 18).** #40 is the pipeline (release-candidate `test-release.yml` job + CHANGELOG draft + README release docs). To complete, a human must (a) green-light merging #40 → `main`; (b) click **`workflow_dispatch`** on `test-release.yml` to build a **DRAFT** `v0.1.0` release; (c) publish manually after the Phase-14 gates. CI alone can never finish it.
2. **The user's hardware gates** — never closable by CI: Phase 15 on-device (long-press launcher surface + three dedicated icons, dynamic "Now Playing" tap→FullPlayer, TalkBack on spinner/headings/degraded banner, rotation + process-death restore) and 15a visual (scrub-pill centering/clamp, blur-once backdrop, swipe-down collapse, mini-player lift, spring lyric emphasis).
3. **Open by omission, not accident:** tablet two-pane (`shared/ui/shell/DhunAppShell.kt`; outside #43's `app-android/**` scope) and the 30-minute LeakCanary soak (never run).
4. No `docs/verification/15-*.md` yet — a phase earns one when hardware evidence exists; that is the missing half.

**Next coordinator step (this session):** reconcile this header + the phase rows to `eda73e4`/PR #43 (done here); refresh #40's stale CHANGELOG/ROADMAP before it merges; and **do not launch speculative candidate code** — roadmap Doctrine: candidates 16–30 are "not designed, not scheduled until the user picks them."


</details>

<details>
<summary><b>Prior snapshot (`arena/01a07ad8-dhun` — PR #43 Android Phase-15 half; MERGED as `eda73e4`)</b></summary>

Updated **2026-09-07 (UTC)** · session **`arena/01a07ad8-dhun`** (follow-up to `arena/01a07a6b-dhun`) · **`origin/main` = `dd0fe14`** = **PR #41 MERGED 08:20:38Z**, so **candidate 15a (ADR-002 player immersion) is on `main`** with `build-and-test` ×2 + `apk` + `msi` green at its head `cd40c1f`. **PR #43** — this session's, **`app-android/**` only** — is the remaining Phase-15 half: green at `434ad92` (`build-and-test` **pass** 4m50s `34099826064`; `apk` **pass** 2m22s + `msi` **pass** 4m19s `34099826022`), and `gh pr merge 43 --merge` is this session's last action. Post-merge CI for #41 on main (`CI #348`, `test-release #121`) was still running while this block was written; #43 will re-verify main with both halves together.

**Why there are two PRs at all (shared-branch history, disclosed).** `arena/01a07a6b-dhun` @ `7c24fde` carried the Android batch *and* the player batch, and did not compile. This session was created to land the ROADMAP commit that PR's merge had been waiting on; it instead found the head red, fixed two defects, and was mid-flight when the a6b session revived and force-pushed that branch to a **player-only** `cd40c1f`, explicitly relocating the Android work to #43. This branch was therefore **re-cut** to `origin/main` + the 8 `app-android` commits at new SHAs + the nav fix, and the `shared/**` fix first written here (`f2d2359`) was **deliberately dropped** so the seek bar was not patched twice by two PRs — #41's own `d685ddc` is what merged. Zero overlap; nothing stranded; `49fab38` (A6's docs-only carry) was likewise not cherry-picked, its one live row re-applied instead.

**The handoff this session inherited was wrong in three checkable ways** — recorded because a follow-up agent will trust this file, not the chat log: (1) *"the platform reports PR #41 as merged-or-closed"* → `gh pr view 41` said **OPEN** (it merged only at 08:20:38Z, 25 min later); (2) *"9 commits, pushed and safe at `32e38c5`"* → the branch held **13**, and `32e38c5` was **red** — `NavStatePersistenceTest` was failing there in runs `34092576415`/`34092579729` and was never fixed on that branch; (3) *"full details written to `phase15-android-polish-status.md`"* → that file existed in **no commit on any branch** (`git log --all -- '*phase15*'` → empty). It is now written, from GitHub evidence — **reconstructed, not recovered**. Every one of the three was one `gh` call from being caught: the pre-push ritual's "verify on GitHub, not locally" applies to **inherited claims**, not only to your own.

**The two defects, and where each ended up:**

| # | Defect | Effect | Landed in |
|---|---|---|---|
| 1 | `ff28f4a` fed `trackAlignedItemOffsetPx(itemWidthPx, widthPx, fraction)` an out-of-scope `width`, and inverted its contract — the first argument is the **item** width, not the track width | `Unresolved reference 'width'` at `PlayerSeekBar.kt:245`/`:261` → `:shared:compileKotlinJvm` + `compileDebugKotlinAndroid` → `build-and-test`, `apk`, `msi` all red on `7c24fde`, **and it masked defect 2 by stopping the build before any test ran** | **#41 → `main`** via `d685ddc` (thumb size + pill measured *outside* `padding`, so the whole pill drives the clamp) |
| 2 | `NavStatePersistenceTest."corrupt or future route entries are dropped, valid ones survive"` — persisted `"artist:"` decoded to `ArtistPage(id=)`; `split(':', limit = 3)` yields `""` at index 1 and `?.let` guards only **null** | `:app-android:testDebugUnitTest` red since `32e38c5`; a corrupt entry restored onto the nav back stack, where it can never resolve | **#43** via `17d5123` — all three route kinds require a non-blank id; the six round-trip cases unchanged |

**What #43 delivers** — the Phase 13 leftovers Phase 15 was scoped to, `app-android/**` only: the module's **first test source set** (7 Robolectric/JVM classes) including **`AppModuleGraphTest`**, the C1 Koin self-recursion regression gate `INTEGRATION.md` §8 asked for, which closes the "`appModule` still unverified" residual in `.ai/KNOWN_LIMITATIONS.md` once this merges; `ShortcutIntents` + `NavStatePersistence` extracted verbatim so the `Bundle` process-death format is testable; three dedicated accent-styled shortcut icons; a dynamic **Now Playing** shortcut via `ShortcutManagerCompat` (tap → expanded FullPlayer, deduped, failure-swallowed); the a11y pass (spinner `contentDescription`, headings, `liveRegion` on the degraded-mode banner); the lazy `assembleDebug → testDebugUnitTest` coupling; and a re-boot-safe `DhunApp` for Robolectric's shared JVM.

**Last error:** none. Both fixes are CI-verified at their own heads. No JDK in the sandbox and no Maven/Gradle egress — **CI is the only compiler here**, so nothing below rests on a local build. `rot-drill` is red on every push including `main`: pre-existing issue **#14** (runner IP gating), non-code, not a required gate.

**Exact next step:** (1) merge #43 when its run on the merge-resolved head is green; (2) **the user's hardware gates**, which no CI on either PR can close — long-press launcher surface with the three dedicated icons, dynamic "Now Playing" tap→FullPlayer, TalkBack (spinner, headings, degraded-mode banner), rotation + process-death restore on device, and *for the 15a half now on `main`*: scrub-pill centering/clamp, blur-once backdrop, swipe-down collapse, mini-player lift, spring lyric emphasis; (3) **still open by omission, not accident: tablet two-pane** (lives in `shared/ui/shell/DhunAppShell.kt`; untouched by #43's scope, nothing forked Android-side) and the **30-minute LeakCanary soak** (never run); (4) no `docs/verification/15-*.md` exists — a phase earns one when its evidence does, and hardware evidence is the missing half.

</details>


<details>
<summary><b>Prior snapshot (`arena/01a07a6b-dhun` — PR #41 player immersion 15a; MERGED as `dd0fe14`)</b></summary>

Updated **2026-09-07 (UTC)** · session **`arena/01a07a6b-dhun`** (player-immersion workstream) · **`origin/main` = `f562093`** (PR #42 desktop single-instance merged) · **PR #41 OPEN**, retargeted to `main + player commits only`, **CI GREEN** (`build-and-test` run `34099051885`).

**LIVE ITEM: nothing code-open in this session.** Full-Screen player immersion polish (ADR-002 P3–P9, trajectory candidate 15a) is implemented, pushed and CI-green on **PR #41** — files: `shared/.../ui/player/{FullPlayer,PlayerTabs,PlayerSeekBar,MiniPlayer,SyncedLyrics}.kt`, `shared/.../design/{DhunIcons,DhunSpacing}.kt` + 4 mirrored `jvmTest` files. Commits `33c3a7e` (Lyrics/ClosedCaption icons + enum-uniqueness test), `9fb8e3e` (blur-once `PlayerBackdrop` keyed by `BlurredArtworkCache` + CC lyrics toggle + swipe-down collapse), `d685ddc` (scrub preview bubble + shared `trackAlignedItemOffsetPx` clamp; includes the CI fixes — `Modifier.offset{}` carries a Density receiver, not `Size`), `cd40c1f` (P8 spring lyric emphasis + sub-pixel recenter floor + MiniPlayer swipe lift). Additive only; no public signature changed; engine/presentation untouched. **Last error:** none on this head — the two earlier compile breaks were squashed into `d685ddc` before retargeting. **Hardware/visual acceptance is OPEN and stays the user's gate** (backdrop look, CC placement, bubble position, swipe feel, on a phone + a desktop window); CI proves none of it.

**Shared-branch history (disclosed, nothing stranded):** this branch's head earlier carried the app-android Phase-15 agent's 9 unmerged commits; that agent relocated them to its own branch → **PR #43** (open; its stack carries my four player commits at identical SHAs plus its own equivalent `PlayerSeekBar` fix). `arena/01a07a6b-dhun` was then rebuilt as `main + player work` with `--force-with-lease` and #41's title/body updated. Whichever of #41/#43 lands second conflicts once, trivially, on `PlayerSeekBar.kt` — take main's version. PR #40 (release pipeline, other branch) unaffected.

**Exact next technical step:** (1) this roadmap commit re-verifies green on PR #41; (2) `gh pr merge 41 --merge` as the **LAST** action of the session; (3) #43's owner rebases onto the new main; (4) player visual acceptance on a real device/desktop window (user), then `rot-drill`'s live-green verdict (issue #14) — none of these are claimed by CI.

**Superseded:** the desktop single-instance snapshot below (session `arena/01a07a6a-dhun`, PR #42 — now merged into `f562093`), the A6 snapshot, and the coordinator's `481b77b` consolidation are all preserved verbatim below.

</details>

<details>
<summary><b>Prior snapshot (`arena/01a07a6a-dhun` — PR #42 desktop single-instance guard; MERGED as `f562093`)</b></summary>

Updated **2026-09-07 (UTC)** · session **`arena/01a07a6a-dhun`** · **`origin/main` = `62e3241`** (PR #39 merged; the A6 snapshot below is superseded) · **PR #42 OPEN**, rebased onto `62e3241`, **CI GREEN**, `MERGEABLE`/`CLEAN` · scope **`app-desktop/**` ONLY** — 2 files, **+529/−0**.

**LIVE ITEM: desktop single-instance behavior — Phase 15a/27 leftover (native polish).** Files worked on: `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/native/SingleInstance.kt` (new, commit `ff74c94`) and its wiring in `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt` (commit `c5ddc58`).

**What was actually broken — and what was deliberately NOT redone.** PR #28 deleted the separate mini-player window, PR #34 deleted every `JOptionPane` path, and PR #38 audited that no *in-process* second-window path survives; **that work stands and was not repeated.** The uncovered path was a second **process**: double-clicking twice, a pinned launch beside an autostart entry, or a relaunch during a slow cold start produced two full windows, two tray icons, two SMTC sessions and **two writers on one `userdata/dhun.db`** — which the `DataLayer` one-connection SQLite contract is explicitly not safe for. `SingleInstance` now decides the role in `main()` **before** the module probes, `startKoin`, the `DataLayer` and `application {}`: the second process handshakes over a per-(user, dataDir) loopback port, asks the running instance to surface, and exits `0` having created **no AWT/Compose/Koin/SQLite surface at all**. Surfacing converges on the tray's own **"Open DHUN"** path (`showMainWindow()`), now un-iconifying first — so a relaunch reads as *refocus*, not *duplicate*, whether the window was visible, hidden to tray, or minimized. `quit()` releases the lease, so a relaunch cannot handshake with a dying instance and leave the user with **no window at all**.

**Non-obvious decisions (all in `SingleInstance.kt`'s KDoc).** Windows binds `reuseAddress = false` (→ `SO_EXCLUSIVEADDRUSE`) because the JDK default `SO_REUSEADDR` lets a duplicate **hijack** an already-bound port there and silently defeat the guard · `BASE_PORT = 24601`, below Linux's `ip_local_port_range` (32768-60999) and the Windows/macOS ephemeral start (49152), so DHUN's own outbound sockets cannot steal the rendezvous port · the port is derived through MurmurHash3 `fmix32` because raw FNV-1a hit only **141 of 512** slots across 300 near-identical keys · every request carries an **identity token**, so a colliding port can never make a *different* install/user defer to a window it cannot see and vanish · **fail open** whenever the port is held by anything that is not this DHUN, because "refuses to launch" is worse than "launched twice" · `-Ddhun.single-instance=false` disables it, matching the existing `-Ddhun.smct=false` convention.

**Last error:** none. Tree clean, both commits pushed. This sandbox has **no JDK** (`java: command not found`; the GitHub release-asset CDN is blocked, so no kotlinc either) — CI is the sole compile authority. Protocol/port logic was validated against a faithful simulation of `SingleInstance.kt`: **34/34 checks** — rendezvous incl. relaunch-after-quit re-binding; identity rejection on a brute-forced **real** colliding port pair (owner defers + surfaces, other install fails open and does *not* surface); fail-open vs hang-up and silent listeners, bounded ~0.8 s; framing abuse incl. 5000-byte newline-free junk and the 4-request-per-connection bound; port determinism/spread/range; 300/300 unique tokens. **Simulation validates design — not compilation, and not hardware.**

**CI evidence at `c5ddc58` (rebased onto `62e3241`):** `build-and-test` **pass** 4m4s (run `34093066392`, includes `:app-desktop:compileKotlinJvm`) · `apk` **pass** 1m42s and `msi` **pass** 4m31s (run `34093066453`) · push CI `34093064789` **success**. The pre-rebase head `ee42b1b` was green too (`34092542039` / `34092541866`). `rot-drill` is red on this branch **and on `main` (`62e3241`, `d1e0408`, `dd1ab31`)** — pre-existing issue **#14**, GitHub-runner IP gating, `.github/**` is out of this session's scope, and it is not one of the three required gates. `publish` is `skipping` by design on PRs; the rolling `test` assets republish only on a push to `main`.

**Deliberately UNTOUCHED per this session's directive** — `Smct.kt`, `DhunTray.kt`, `TrayIcons.kt`, `DesktopDhunPlayer.kt` and `app-desktop/build.gradle.kts` are **byte-identical to `main`**; the whole diff is +529/−0, zero deletions. Observed and recorded in PR #42 but **not** fixed: window-geometry restore (no off-screen/min clamp, maximized placement not persisted, saved only on quit/close-to-tray) · SMTC (fixed `delay(2_000)` race before `FindWindowW`, duplicated `"DHUN"` title, nav buttons blind to `repeatMode`/`shuffleEnabled`, and **no `log` passed → its diagnostics never reach `dhun-startup.log`**) · tray (`start()`'s `Boolean` discarded → an unsupported tray plus `CLOSE_TO_TRAY=true` can hide the window with no way back; `invokeLater` icon removal racing `exitApplication()` → Windows ghost icon; no `log` passed) · the `initError != null && koinInstance == null` startup gate (a broken `DataLayer` with Koin already started exits with **no window**) · dead `ui/DesktopHarness*.kt` (337 lines, referenced only by itself).

**Exact next technical step:** (1) `git fetch origin main` — if main advanced past `62e3241`, rebase and re-run CI until green; (2) `gh pr merge 42 --merge` as the **LAST** action of the session; (3) the **user's hardware gate on Windows** — launch twice → **one** window, raised *and focused* (also from minimized, and from tray-hidden); quit → relaunch → normal startup; `userdata/dhun-startup.log` should show `single-instance: primary — listening on 127.0.0.1:<port>` for the first launch, and `cannot own 127.0.0.1:<port> … — checking for a running instance` followed by `DHUN second instance: … exiting without opening a window` for the second. **CI green claims none of that.**

**Superseded:** the A6 snapshot below (session `arena/01a07a6b-dhun`, PR #39 ADR-006 downloads-UI polish) is preserved verbatim; PR #39 is now **merged** into `62e3241`, so its "PR #39 OPEN / checks pending" wording is historical.

</details>


<details>
<summary><b>Prior snapshot (arena/01a07a6b-dhun — PR #39, ADR-006 Library/Home/Search downloads-UI polish; merged as 62e3241)</b></summary>

Updated **2026-09-07 (UTC)** · session **`arena/01a07a6b-dhun`** · **`origin/main` = `92ee545`** (PR #38 merged; ROADMAP's earlier `481b77b` snapshot is superseded) · **PR #39 OPEN** on this session's pinned branch.

**LIVE ITEM: ADR-006 Library/Home/Search UI polish (agent A6).** Files: `shared/.../presentation/library/LibraryViewModel.kt`, `shared/.../ui/library/LibraryScreen.kt`, `shared/.../ui/home/HomeScreen.kt`, `shared/.../ui/search/SearchScreen.kt`, plus `shared/src/jvmTest/.../LibraryDownloadsViewModelTest.kt` (the one test file covering that ViewModel; no active agent owns it). Shipped on `arena/01a07a6b-dhun` → **PR #39**: `DownloadsListUi` sectioned ordering (Active downloads above Downloaded, newest-first within each), queue-context `playDownloaded` (full completed list, tapped track first; single-track fallback), storage view reachable from the empty Downloads tab + zero-row MANAGE polish, Home quick-picks duplicate-index fix, icon-only download badges on Home quick-picks and Search song/video rows, 3 new pure-JVM tests. Additive only; engine, `DownloadManager`/`DownloadRepository` contracts, and `design/**` untouched.

**Shared-branch disclosure:** the pinned branch already carried two unmerged commits from the probe/rot-drill owner (A3: `e213c9e` probe hardening, `d18a720` rot-drill GA wiring) — carried forward per the nothing-stranded rule, so PR #39 contains both workstreams (title/body say so).

**Last error:** none — implementation commits `7a8b57f..893f408` pushed; sandbox has no JDK, so CI on PR #39 is the sole compile/test authority (checks were pending when this snapshot was written).

**Exact next technical step:** (1) `gh pr checks 39` → require **build-and-test GREEN** (shared JVM tests + Android assembleDebug + probe compile + desktop compile); fix + re-push if red. (2) `git fetch origin main`; if main advanced past `92ee545`, rebase, re-run CI until green. (3) `gh pr merge 39 --merge` as the **LAST** action of the session. (4) Hardware/visual acceptance of the polished surfaces (badges, sections, storage figures, offline queue behavior on device/desktop) stays the **user's** gate — CI green claims none of it.

**Superseded:** the prior coordinator snapshot (`arena/01a07a07-dhun`, PR #38 consolidation, `origin/main = 481b77b`) below remains historically accurate for its merge batch; #38 is now merged into `92ee545`. The six-agent ADR-006 integration it reports is complete and stable.

</details>

<details>
<summary><b>Prior coordinator snapshot (arena/01a07a07-dhun — PR #38 era, retained for the merge-batch evidence)</b></summary>

**Phase: 14 — Robustness, rot-drill, UI/UX polish & feature enhancements. IN PROGRESS (ADR-006 offline downloads: all six worker agents integrated and CI-green on main; hardware acceptance still open).**

This session is the **coordinator/lead** for the six parallel ADR-006 worker agents. It owns only `INTEGRATION.md`, `.ai/ROADMAP.md`, `.ai/KNOWN_LIMITATIONS.md`, and `.ai/DEBUG_LOG.md`, and is **read-only** over `app-android/**`, `app-desktop/**`, `shared/**`, `tools/**`, and every `agent-N-status.md`. Full reconciliation detail lives in **`INTEGRATION.md`**.

## **ALL STABLE — PRs #34 + #35 + #36 + #37 merged; new main head `481b77b`**

| Order | PR | Agent(s) | Merged (UTC) | Main commit | Method |
|---|---|---|---|---|---|
| 1 | **#34** | 4 (desktop) + 5 (verify/docs) | `04:18:28Z` | `d1e0408` | merge commit (by the coordinator) |
| 2 | **#35** | 1 (Android download FGS) | `04:47:43Z` | `40eff1d` | squash (owner session) |
| 3 | **#37** | 6 (player UX) | `04:50:13Z` | `b6aec3a` | merge commit (owner session) |
| 4 | **#36** | 2 (Library) + 3 (download UI) | `04:52:00Z` | **`481b77b`** | merge commit (owner session) |

**Final gate on main @ `481b77b`:** CI run `34084678724` → `build-and-test` **success**; test-release run `34084678720` → `apk` **success**, `msi` **success**, `publish` **success**. Intermediate gate after #34: CI `34082610125` + test-release `34082610094` both success (rolling `test` release replaced `04:26:24Z`, APK 17,581,785 B / MSI 112,287,744 B + `.sha256`). After #37: CI `34084576275` success; its test-release `34084576184` was **cancelled** — superseded by #36 landing minutes later, not a failure.

**Every agent's work verified present on main** (file-level checks at `481b77b`; detail in `INTEGRATION.md` §3): agent 1 FGS + `AppModule.kt:106 delegate = get<FileDownloadManager>()` + manifest `FOREGROUND_SERVICE_DATA_SYNC` · agent 2 `StorageSpace` expect/actual + Library storage management · agent 3 `DownloadAffordances.kt` + `DhunIcons.Pending` + `DhunAppShell.kt:544/559` pass-through · agent 4 `JOptionPane` import count **0** · agent 5 `OfflineMain.kt` + fixture + `offlineProbe` task · agent 6 `SyncedLyrics`/`PlayerSeekBar`/`TransportControls` + 4 test files + the 4 new `PlayerViewModel` functions. **Nothing stranded.** Only open PRs: **#38** (this consolidation) and **#31** (`CONFLICTING`/`DIRTY`, excluded).

## LIVE ITEM — Desktop single-window startup (ADR-004 + PR #34): fix merged, hardware re-test open

**User report:** opening DHUN on Windows also opens a second small mini-player
window alongside the real app; a separate mini-player is not wanted because the app
already ships a docked in-app `MiniPlayer`.

**This exact report is already recorded and already fixed — twice.**
`docs/decisions/ADR-004-remove-separate-miniplayer-window.md` quotes the identical
complaint against the `test` build published `2026-09-06T06:51:40Z` and is marked
**ACCEPTED (user decision, 2026-09-06)**.

| Fix | Merged | What it did |
|---|---|---|
| **PR #28** (`b8f148d`) | `07:10:11Z` 2026-09-06 | Deleted `ui/MiniPlayerWindow.kt`; removed the second Compose `Window` from `Main.kt`; stripped the SMTC `GetWindowRect`/`SetWindowPos` window-movement calls; dropped two window-only `DhunSpacing` tokens |
| **PR #34** (`d1e0408`) | `04:18:28Z` 2026-09-07 | Removed every `JOptionPane` startup/fatal path — the last remaining surface able to own a second small native window |

**Static audit of `origin/main` @ `481b77b` — no second-window path survives:**
- `app-desktop` contains **no** `MiniPlayerWindow.kt` (or any `*Window*.kt`).
- `Main.kt` holds exactly **two** `Window(` calls and they are **mutually exclusive**:
  line 228 is the startup-error window, gated by `if (initError != null &&
  koinInstance == null)` and terminated by `return@application` at line 245; line 420
  is the main window, reachable only on the normal path.
- `import javax.swing.JOptionPane` count = **0** (the only textual mention is the
  comment at `Main.kt:112` documenting its absence). No `JDialog`/`JWindow`/`JFrame`
  instantiation anywhere.
- `showMainWindow()` (`Main.kt:293`) only sets `isVisible`/`toFront`/`requestFocus` on
  the **existing** `mainWindowRef` window — it never creates one.
- `DhunTray` builds an AWT `TrayIcon` + `PopupMenu`; neither is a window.
- `Smct.kt` uses `FindWindowW` only to **find** the existing `SunAwtFrame` HWND for
  `GetForWindow`; the `GetWindowRect`/`SetWindowPos` movement calls that served the
  removed mini-player are deleted (see the note at `Smct.kt:442-443`).

**Conclusion:** on `481b77b`, **two simultaneous windows are not reachable from DHUN's
own code.** Either the tested build predates `481b77b`, or Koin init failed — and in
that case the error window *replaces* the main window rather than accompanying it, so
the user would still see one.

**Outstanding work:** re-test the rolling `test` MSI (`481b77b`, published
`2026-09-07T04:58:25Z`, 112,492,544 B) on a real Windows machine. If a second window
still appears, capture `<installDir>/userdata/dhun-startup.log` (or
`%TEMP%/dhun-startup.log`) plus whether the second window has a title bar — that
distinguishes the Compose startup-error window from a leftover AWT surface. **No
`app-desktop` change is warranted until that re-test says otherwise.**

**Cross-cutting findings, all resolved:**
- **C1 — Koin self-recursion (agent 1): FIXED and merged.** `single<DownloadManager> { ForegroundServiceDownloadManager(delegate = get(), …) }` recursed because `delegate: DownloadManager` made the unqualified `get()` resolve the singleton being constructed; Koin 4.0.2 caches singletons *after* construction. It fired at app launch via `MainActivity.kt:197`, while **all three CI checks were green** — `:app-android:assembleDebug` is a type-check gate and `:app-android` has no test source set. Fixed `ef69f82` → `delegate = get<FileDownloadManager>()`. The regression test took three commits to compile (`705a946` → `4fd9636` wrong turn adding `koin-test` → `fb32711` using `GlobalContext.get()` directly). **The production fix compiled throughout**; both red runs failed `:shared:compileTestKotlinJvm` with every annotation inside the test file. **Residual:** the test pins the registration *shape* with fakes in `:shared:jvmTest`; the real `appModule` needs Robolectric and remains a CI follow-up.
- **C2 — inert download badges (agent 3): CLOSED.** `DhunAppShell` did not forward `downloadManager` to `HomeScreen`/`SearchScreen`. Fixed by agent 3 in `e987f64`; verified on main at lines 544/559. The coordinator's exemption to write it was not exercised. Residual hazard: the parameter was inserted mid-list, safe only because callsites use named arguments.
- **C3 — shared `.ai` ownership: resolved by supersession.** Agent 5 edited the three coordinator-owned docs in #34; this consolidation sits on top and agent 5's commits are **not** stripped or rewritten.
- **Agent 1's `shared/build.gradle.kts` change: benign.** 9 insertions, 0 deletions, **one** dependency line — `implementation("io.insert-koin:koin-core-jvm:4.0.2")` inside `jvmTest.dependencies { }`, the other eight lines comments. Test-scoped only; cannot affect the shipped APK/MSI; version deliberately matches `app-android`'s `koin-android:4.0.2`; reversible by deleting one line.

**Contracts verified clean:** `DownloadManager` interface unchanged on every branch · `LibraryViewModel` ctor byte-identical · `FullPlayer`/`MiniPlayer` signatures identical · `DhunIcon` gains only `Pending` · `DownloadRepository`, `DownloadedTrack`, `TrackOverflowDialog`, `StreamResolver` zero diffs · no duplicate `DownloadManager` implementation (agent 1 decorates, agents 2/3 consume).

**Branch-map correction:** five of the six requested `agent/*` branch names were never created. Agents **2+3** shared `arena/01a079f5-dhun`; agents **4+5** shared `arena/01a079f6-dhun`, so neither PR could be gated per-agent. Agent 2, initially reported missing, pushed its Library Downloads + storage-management work onto agent 3's branch — **that work is now on main; do not relaunch agent 2.** Agent 6 opened PR #37 itself, so the coordinator's `gh pr create` correctly failed as a duplicate.

**Merge-order deviation, recorded:** the actual order was #35 → #37 → #36, not the requested #36 → #37 → #35, because the owner sessions merged concurrently and the coordinator merged none of them. The intent behind #35-last (test against fully-integrated main) was satisfied anyway by the final gate at `481b77b`. #35 landed as a **squash** commit, so its individual commits are not on main's first-parent history. The coordinator did merge **#34** itself, under the prior explicit approval, before the one-session-one-PR constraint arrived — disclosed in `INTEGRATION.md` §2.

**Open non-code gate (excluded from the verdict):** `rot-drill` is **RED** — run `34083253658` @ `d1e0408` **failure**, tracked as issue **#14**. GitHub-runner **IP gating**, a known environment limitation, **not a code defect**; no rot-drill ran at `481b77b`. A green **live** rot-drill verdict stays open on the human/device side.

**HARDWARE GATES STILL OPEN — green CI is a compile/unit-test gate only, and does NOT mean downloaded tracks play offline.** Offline playback of a downloaded track (Android Media3 `FileDataSource` route; Desktop vlcj local-path load) · audible audio (streaming and offline) · live Home pagination · player visual acceptance (glyph placement, shuffle, colour styling) · tray/SMTC and agent 4's one-native-window startup claim · install-over upgrade · clean-target hygiene · 30-minute soaks including the OEM battery-saver soak agent 1's FGS exists to pass · a green **live** rot-drill verdict. Agent 5's `offlineProbe` is compiled by CI but **never executed** by the workflow, so not even `offline-verdict|PASS` is established. All of the above need a real device, PC, libVLC runtime, or display.

**Last error:** none on the three merge gates — main @ `481b77b` is fully green. The only red workflow on main is `rot-drill` (issue #14, IP gating, non-code). The coordinator could not run Gradle locally (no JDK/Android SDK in the sandbox), so CI is the sole compile/test authority, and the original C1 diagnosis was **static analysis**, never a reproduced stack trace.

**Current exact files (coordinator-owned only):** `INTEGRATION.md`, `.ai/ROADMAP.md`, `.ai/KNOWN_LIMITATIONS.md`, `.ai/DEBUG_LOG.md`.

**Exact next technical step:** (0) **Windows re-test of `481b77b`** — install the rolling `test` MSI published `2026-09-07T04:58:25Z` and confirm exactly one window on startup; if a second window appears, attach `dhun-startup.log`. Hold all `app-desktop` edits until that result is in — the audit above says the fix is already landed, and re-fixing a fixed bug would only add risk. (1) merge consolidation **PR #38** (docs only — `INTEGRATION.md` + the three `.ai` files; the coordinator makes no source changes); (2) run the four follow-ups in `INTEGRATION.md` §8 — add `:app-android:testDebugUnitTest` + Robolectric with a `checkModules()` graph test, add a CI step that **executes** `:tools:playback-probe:offlineProbe`, decide PR #31's fate, and get a green live rot-drill verdict to close issue #14; (3) take the hardware checklist in the paragraph above to a real device/PC — **CI green is not hardware acceptance, and ADR-006's offline playback has never been demonstrated on a device.**

**Superseded:** the previous CURRENT ACTIVE TASK snapshots (session `arena/01a079f6-dhun` / agent 5 at `f157245`, and this coordinator's own earlier "NOT STABLE — #35 red" verdict) are superseded by this one. The #35-red verdict was accurate when written — CI genuinely failed at `705a946` and `4fd9636` — and was made obsolete by `fb32711`. No commits were stripped; agent 5's docs are intact in main's history at `d1e0408`.

</details>

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
| 12 | Desktop native | 🟨 — tray/shortcuts/SMTC and packaging on main; PR #28 removed the separate mini-player window (ADR-004), leaving the docked in-app MiniPlayer. **PR #42 (this session) adds the single-instance guard: a second launch now refocuses the running window instead of opening a second one (+529/−0, `app-desktop` only, CI green at `c5ddc58`).** Latest Desktop compile + MSI publishing green on `main@0920148` (`34018809911` / `34018809913`). Prior JVM-launch fix was confirmed by the user; the user now confirms one-window startup after manual reinstall (evidence local/pending publication); upgrade, native integrations and clean-target hygiene remain OPEN | docs/verification/12 · ADR-004 · CURRENT ACTIVE TASK |
| 13 | Android polish (insets, shortcuts, tablet, soak) | 🟨 code + CI green (`8669e09` + `c2a86df` + `4de9795`, run `33958894084`); rotation/shortcut/insets/tablet/OEM soak evidence OPEN. **PR #43 MERGED as `eda73e4` supplied the missing automated half — `app-android` had no test source set at all** (7 Robolectric/JVM classes, incl. `AppModuleGraphTest`, the C1 regression gate) | `MainActivity.kt`, `DhunAppShell.kt`, `shortcuts.xml` |
| 14 | Robustness + rot-drill CI + release v0.1.0 | 🟨 IN PROGRESS — **ADR-006 offline downloads fully integrated: PRs #34/#35/#36/#37 merged, `main` / `test` at `481b77b`, ALL STABLE.** CI `34084678724` and test-release `34084678720` green; rolling test republished `2026-09-07T04:58:25Z`. Audio User-Agent fix, Home continuation, bounded resolve/diagnostics, restyle, single-window code, Android download FGS, Library storage management, per-track badges and player UX are all on GitHub. `rot-drill` still RED (issue #14, GitHub-runner IP gating, non-code); hardware re-tests, offline/recovery checks, clean targets, soaks and v0.1.0 remain OPEN | Phase 14 step table below; `INTEGRATION.md`; issue #14; docs/verification/14 |

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
| **Single-instance behavior** (Phase 15a/27 leftover — **NEW this session**) | 🟨 **PR #42** (`native/SingleInstance.kt` + `Main.kt` wiring; `ff74c94` + `c5ddc58`, **+529/−0**, `app-desktop` only, no new deps). A second launch of the same install/user handshakes over a per-(user, dataDir) loopback port, asks the running instance to surface through the tray's own "Open DHUN" path (`showMainWindow()`, now un-iconifying first) and exits `0` **before** the module probes/Koin/DB/`application {}` — so no second window, tray icon, SMTC session or SQLite writer. Windows binds `SO_EXCLUSIVEADDRUSE` (the JDK default `SO_REUSEADDR` would let the duplicate hijack the port and defeat the guard); `BASE_PORT = 24601` sits below every OS ephemeral range; identity-token handshake so a colliding port can never make a *different* install/user defer and vanish; **fail-open** whenever the port is not this DHUN; `-Ddhun.single-instance=false` disables it. CI green at `c5ddc58` (`build-and-test` `34093066392`, `apk`+`msi` `34093066453`); protocol/port logic **34/34** in simulation — **hardware OPEN** (launch-twice, minimized, tray-hidden, quit→relaunch, and a two-user/two-install box) |
| Packaging: jpackage MSI + clean-VM install | ✅ MSI packaging on GitHub (`34018809913`, `test@0920148`, 112,009,680 B). The user previously confirmed install → launch of the JVM-fixed `1.0.5` build; user now reports one-window startup after manual reinstall; 🔴 in-place upgrade failed. Clean-target installation, data preservation and uninstall hygiene remain OPEN |
| Verification doc + KNOWN_LIMITATIONS + THIRD_PARTY | ✅ done + pushed (`ffa138b`); docs/verification/12 + KNOWN_LIMITATIONS updated for the mini-player removal (ADR-004) |
| Acceptance 1–4 (media keys / tray / installer) | 🟨 OPEN — on hardware (checklist in docs/verification/12). Acceptance 3 (separate mini-player window) is **superseded by ADR-004**; the docked mini-player is covered by the Phase 08 checklist instead |

### Phase 13 step status — 🟨 CODE + CI GREEN @ `8669e09` + `c2a86df` + `4de9795` (hardware OPEN)

| Step | Status |
|---|---|
| Edge-to-edge and safe-drawing inset audit | 🟨 implemented in `MainActivity.kt` for connecting, ready, and failure roots; CI/device gesture-nav verification OPEN |
| App shortcuts: Search / Resume / Library | 🟨 static XML plus `onCreate`/`onNewIntent` routing on `main`. **PR #43 adds** three dedicated accent-styled `ic_shortcut_*` drawables (no longer sharing the notification glyph) and a dynamic **Now Playing** shortcut via `ShortcutManagerCompat` (`Playing / <track> — <artist>`, tap → expanded FullPlayer, deduped by track id, failure-swallowed so an OEM launcher cannot disturb playback). `ShortcutIntentsTest` / `StaticShortcutsXmlTest` / `NowPlayingShortcutLabelsTest` / `AndroidStringsTest` pin the resource<->code contract; **launcher verification OPEN** |
| Battery optimization rationale and exemption handoff | 🟨 in-app rationale plus guarded system settings handoff implemented; OEM behavior verification OPEN |
| Rotation and back-stack state survival | 🟨 tab / expanded player / detail routes saved-restored through `Bundle`. **Robolectric coverage delivered in PR #43**: `NavStatePersistence` extracted verbatim from `MainActivity`, pinned by round trip, defaults, unknown-tab fallback and corrupt-route dropping (`17d5123` closes the blank-id hole red since `32e38c5`); **device rotation check OPEN** |
| Tablet / large-screen navigation | 🟨 shared shell switches to an 840dp `NavigationRail` and docks MiniPlayer; tablet two-pane and visual verification OPEN — **not attempted by Phase 15**: it lives in `shared/ui/shell/DhunAppShell.kt`, outside `app-android/**`, and nothing was forked Android-side |
| Acceptance 1–4 (rotation, back stack, shortcuts, 30-minute unrestricted battery soak) | 🟨 OPEN — requires CI plus real Android/device/OEM evidence; no Phase 13 acceptance is complete here. PR #43 supplies the automated half for rotation/shortcuts only; **the 30-minute LeakCanary soak was never run** |

### Phase 14 step status — 🟨 IN PROGRESS (GitHub verified 2026-09-07, coordinator session `arena/01a07a07-dhun`)

**Current GitHub snapshot:** `main` and the rolling `test` tag both point to
**`481b77b`** (PR #36, the last of the six-agent ADR-006 batch). Main CI
**`34084678724` success** (`build-and-test`) and test-release
**`34084678720` success** (`apk` + `msi` + `publish`) built and published the
APK + MSI + checksums; the rolling `test` release was republished at
**`2026-09-07T04:58:25Z`** (MSI 112,492,544 B / APK 17,696,545 B + `.sha256`).
**All four ADR-006 worker PRs are merged — `ALL STABLE`** (see
`INTEGRATION.md`). The only red workflow on main is `rot-drill` (issue #14,
GitHub-runner IP gating, non-code). Hardware and stable-release gates remain
open; consolidation PR **#38** carries this documentation.

**Recent work actually merged on GitHub, not outstanding local work:**

| PR | Merge / GitHub `mergedAt` (UTC, 2026-09-06) | Landed work |
|---|---|---|
| #24 | `c247fb4` · `05:56:49Z` | Stream User-Agent fix (`5a89b81`) + Home continuation and regression tests |
| #25 | `6497b1b` · `06:19:33Z` | Playback-error diagnostics + bounded 45-second resolving (`d390dd0`) |
| #26 | `ef4c8d7` · `06:47:41Z` | UI restyle / icon fixes (`11d6f75`) + CI push coverage for `arena/**` |
| #27 | `0eb8e76` · `06:59:41Z` | ADR-003 **proposal only**, not approval or parallelism implementation |
| #28 | `b8f148d` · `07:10:11Z` | Separate desktop mini-player window removed (`a01f8ca`), ADR-004 and accompanying docs |
| #29 | `0920148` · `07:18:53Z` | Post-merge documentation; no further application-code change |
| #33 | `f157245` · `03:19:51Z` (2026-09-07) | ADR-006 persistent downloads: data layer, engine, offline-first routing, minimal UI, and platform wiring |
| #34 | `d1e0408` · `04:18:28Z` (2026-09-07) | Desktop single-window hardening — removed the `JOptionPane` startup/fatal Swing surfaces (agent 4); `tools/playback-probe:offlineProbe` deterministic local-file check + WAV fixture (agent 5) |
| #35 | `40eff1d` · `04:47:43Z` (2026-09-07) | Android download foreground service (ADR-006) + the **C1 Koin self-recursion fix** + `KoinDownloadStackTest` (agent 1). Squash-merged |
| #37 | `b6aec3a` · `04:50:13Z` (2026-09-07) | Shared player UX — synced-lyrics follow, tab selection semantics, accessible queue actions, `PlayerSeekBar` / `TransportControls` extraction (agent 6) |
| #36 | `481b77b` · `04:52:00Z` (2026-09-07) | Library Downloads storage-management view + `StorageSpace` expect/actual (agent 2); per-track download badges + the `DhunAppShell` pass-through that closed C2 (agent 3) |

Earlier Phase 14 milestones remain merged: PR #16 at `290e0f6`, #17 at
`29eeb93`, #19 at `6d81eb2`, #20 at `8310383`, #22 at `e90dba6`, and the
PR #23 documentation at `9294520`. The old `04:45:40Z` / `06:51:40Z` /
`07:13:35Z` publication snapshots are superseded by the current release.

**Read the columns separately:** ✅ in the GitHub column completes only
that named code/test/publishing milestone. It does **not** close the
hardware or release gate in the last column. All Phase 14 acceptance
criteria remain open; green build CI does not mean green live extraction.
The repair batch is merged through PR #30 at `76c68eb`; automated code/package checks and rolling test publishing passed. Hardware and stable-release acceptance remain open.

| Step | Complete on GitHub / evidence | Remaining gate |
|---|---|---|
| **Windows-report repair batch (PR #30 merged)** | ✅ **`75c4a8b`**, [CI **34025807972**](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972) **success**: Python checks, shared JVM regressions, Android debug build, probe/Desktop compilation. First compile errors corrected; Quick-picks projection wired into real UI | **Merged in PR #30 and published in the rolling test build.** MSI data-sentinel checks pass in PR/main runs; app playback/live Home/visual/native runtime and stable release gates remain OPEN |
| **Desktop single-window startup (ADR-004 + PR #34)** | ✅ **Two independent fixes merged.** (a) PR #28 (`b8f148d`) deleted `ui/MiniPlayerWindow.kt`, removed the second Compose `Window` from `Main.kt`, and stripped the SMTC `GetWindowRect`/`SetWindowPos` window-movement calls. (b) PR #34 (`d1e0408`) removed every `JOptionPane` startup/fatal path. **Static audit of `481b77b` finds no remaining second-window path:** `Main.kt` holds exactly two `Window(` calls — line 228 (startup-error window, gated by `initError != null && koinInstance == null`, closed by `return@application` at line 245) and line 420 (main window) — and they are **mutually exclusive**. `import javax.swing.JOptionPane` count = **0**; no `JDialog`/`JWindow`/`JFrame` instantiation; `showMainWindow()` only toggles `isVisible`/`toFront` on the existing window; `DhunTray` builds an AWT `TrayIcon` + `PopupMenu`, never a frame; `Smct` uses `FindWindowW` only to *find* the existing `SunAwtFrame` HWND for `GetForWindow` | 🟨 **Hardware re-test OPEN** — the user's screenshot predates `481b77b`. Requires a real Windows machine against the rolling `test` MSI published `2026-09-07T04:58:25Z`. **On `481b77b` two simultaneous windows are not reachable from DHUN's own code** |
| Error taxonomy, 429 backoff, offline banner, recovery UX | ✅ Existing typed-error paths, `RateLimitGate`, `ConnectivityMonitor` and `PlaybackState.Recovering` merged via PR #16; recovery extended by PR #20; current main CI `34018809911` green | 🟨 Full db-path/error-taxonomy review and real offline/429/403/forced-error behavior not verified |
| Stream-URL cache (TTL + invalidation) | ✅ `DhunStreamCache` five-hour TTL and 403 invalidation on main; latest playback code builds in CI | 🟨 Stale-URL recovery on hardware |
| Bounded audio cache — Android | ✅ `DhunAudioSegmentCache` / Media3 `SimpleCache` LRU merged in PR #16; PR #20 adds corrupt-cache direct-stream fallback; current Android build green | 🟨 Offline span replay, eviction/budget behavior and cache-failure fallback on a device |
| Bounded audio cache — Desktop | ✅ `AudioFileCache` + `DesktopDhunPlayer` wiring and cache tests merged in PR #17, updated in PR #24 for User-Agent propagation; current shared tests and Desktop compile green | 🟨 Fully cached track replays offline, uncached-track error and eviction on a libVLC desktop |
| **Persistent offline downloads (ADR-006)** | 🟨 **Foundation, engine, offline-first routing, and minimal UI merged in PR #33 at `f157245` and CI-green**: schema v3 + migration, repositories, Range-resume/atomic download manager, `OfflineFirstStreamResolver`, Android `file://`/`FileDataSource` route, Desktop local-path load, and Downloads tab/action. This session adds `tools:playback-probe:offlineProbe`; branch compile evidence is green in `34080947691`, while runtime evidence is not produced by the existing workflow. **Downloads-UI polish (agent A6, PR #39):** `DownloadsListUi` sectioned ordering, queue-context `playDownloaded`, storage view reachable from the empty tab, Home quick-picks duplicate-index fix, icon-only badges on Home/Search rows + 3 JVM tests — merged state tracked via PR #39; CI-gated only. | ⬜ **Hardware acceptance remains OPEN**: Android foreground/WorkManager service, per-track badge/storage-management/progress polish, and real Android device + Desktop/PC offline playback/decoding/audible verification. The deterministic probe proves JVM repository-to-file loading only; CI cannot close the hardware gate. |
| Daily rot-drill workflow + failure alerts | ✅ `.github/workflows/rot-drill.yml` merged, cron `17 4 * * *`. **Scheduled execution and alert path verified:** run `34011539225` fired on `main@dd1ab31`, event `schedule`, failed, uploaded `rot-drill-34011539225`, and auto-commented on issue #14 at `04:29:14Z` | 🟨 A green live production-probe verdict and auto-close-on-recovery remain unproven; placeholder-workflow greens do not count |
| Live extraction verdict / residential playback | 🔴 Latest real drill **`34011539225`**, scheduled on `dd1ab31`: `PROBE\|resolve+stream\|FAIL\|IllegalStateException: resolve via resolving(own-innertube-player -> yt-dlp): Unavailable`; own-client WATCH `Unavailable`, yt-dlp WATCH `AuthRequired` / bot-gate text; version/search/related PASS; final verdict FAIL. Source: run metadata and issue #14's preserved probe output; issue remains OPEN | No newer live probe verdict on `0920148`. Fresh Windows feedback also reports failed audio; do not dismiss this as CI-only gating. Candidate diagnostics and a real uncached playback/byte test are required |
| Device-feedback recovery + artwork/splash/sheets/spacing | ✅ PR #20 implementation on main, followed by PR #24 audio correction, PR #25 diagnostics and PR #26 restyle; current main CI green | 🔴 Last reported device audio result was negative. Earlier corrections are **published**, but the fresh Windows re-test still fails audio, Home and visual acceptance. New repairs pass branch CI but are not released/device-tested; keep the blocker open |
| Install/uninstall hygiene | ✅ PR #19 per-user MSI and `DhunUserDirs`, PR #22 startup ruggedization and Android private-storage policy on main; latest MSI rebuilt/published green in `34018809913` | 🟨 Fresh in-place upgrade FAILED with “Another version…”; only manual uninstall/reinstall succeeded. Corrected MSI 1.34.1 passes hosted-Windows legacy install-over, future-upgrade-removal and explicit-uninstall tests in PR run 34030730743. User-machine/full runtime hygiene still needs evidence |
| `CHANGELOG.md` (Unreleased history) | ✅ On GitHub main, including PR #28's mini-player removal entry | Unreleased history exists; `[0.1.0]` release entry/final review waits for actual release gates |
| Rolling **test** APK/MSI publishing | ✅ **`test@f157245`**, published **`2026-09-07T03:27:18Z`** after `34079283231`: `dhun-test.msi` **112,287,744 B**, `dhun-test.apk` **17,581,785 B**, both `.sha256` assets uploaded | Not evidence for an AAB, stable v0.1.0, clean-target installation or successful playback |
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
| 15a | **Full-Screen player immersion polish (ADR-002 P3–P9)** — 🟨 **on `main`**: merged as `dd0fe14` via PR #41, **CI green at `cd40c1f`** (`33c3a7e`..`cd40c1f`): blur-once `PlayerBackdrop` (P4), CC toggle + Lyrics tab glyph (P3/P6/rule 5), swipe-down collapse (P9), scrub preview bubble (P2 polish), spring lyric emphasis + recenter floor (P8), MiniPlayer swipe lift; `DhunIcon.Lyrics`/`ClosedCaption` + `scrubBubbleHeight` token; 4 test files. **Visual acceptance on hardware OPEN** (user gate) | Signature UX; streams proven, polish shipped — acceptance rides the device |
| 15 | **Android native polish finish** (Phase 13 leftovers: app shortcuts, Robolectric/UI tests, tablet two-pane, 30-min soak with LeakCanary) | 🟨 **CODE MERGED — PR #43 → `main` as `eda73e4`** (green at head `434ad92`; post-merge `test-release` `34101231724` success) — shortcut surface (3 dedicated icons + dynamic Now Playing), the module's first test source set (7 classes) incl. the `appModule` Koin-graph gate closing the C1 residual, a11y semantics, and the nav-restore blank-id fix. **Remaining OPEN: tablet two-pane** (lives in `shared/ui/shell`) **and the 30-min LeakCanary soak** (never run); all on-device hardware gates are the user's |
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
