# Phase 15 — Android native polish (PR #43) + the 15a player batch it was entangled with (PR #41)

Updated: 2026-09-07 (UTC) · session `arena/01a07ad8-dhun`, continuing `arena/01a07a6b-dhun`

> **Naming note.** The repo convention is `agent-N-status.md` (agent 1 owns Android and
> has never had a file — only `agent-2` … `agent-6` exist). This keeps the filename the
> incoming handoff promised, so the next agent finds it where it was told to look.
> Rename to `agent-1-status.md` whenever the Android owner wants one.

## State — nothing here is "done"

`origin/main` = `f562093` (PR #42 desktop single-instance, unrelated). Phase 15 lives in
**two independent PRs**:

| PR | Scope | Shape | Merge? |
|---|---|---|---|
| **#43** — `arena/01a07ad8-dhun` (this session) | **`app-android/**` only** | 9 commits, 18 files, **+1010/−63**, zero `shared/**` | **CI green at `434ad92`** (`build-and-test` `34099826064`, `apk`+`msi` `34099826022`) — merge is this session's last action |
| **#41** — `arena/01a07a6b-dhun` (its owner's) | ADR-002 player immersion = **candidate 15a**, `shared/ui/player/**` + `design/**` | 4 commits `33c3a7e..cd40c1f`, 11 files, +417/−86 | **MERGED as `dd0fe14` (08:20:38Z)** — `build-and-test` ×2 + `apk` + `msi` green at `cd40c1f` |

They no longer overlap. They *did*: #43 was built on #41's head while that PR still
carried both workstreams, then re-cut after the a6b session revived, force-pushed the
branch to player-only `cd40c1f`, and merged it 25 minutes into this session. **`main`
already has the 15a half; #43 is the only Phase-15 PR still open**, and it is
`app-android/**`-only by construction — no `shared/**` file appears in its diff.

## How this session started — three inherited claims were false

Recorded because the next agent inherits this file, not the chat log:

1. *"the platform reports PR #41 as merged-or-closed; the merge never happened under my
   control"* → `gh pr view 41` reported **OPEN**, head `7c24fde` — nothing had been merged. (#41 did merge, at 08:20:38Z as `dd0fe14`,
   ~25 min after this session opened: the claim was false at handoff and only came true
   later, through someone else's action.)
2. *"Everything else is pushed and safe — 9 commits at `32e38c5`, push-verified"* → the
   branch held **13** commits (`c3bf45a..7c24fde` had landed after the handoff was
   written), and `32e38c5` was **red**: `NavStatePersistenceTest` was failing there in
   runs `34092576415` / `34092579729` and was never fixed on that branch.
3. *"Full details written to `phase15-android-polish-status.md` at the repo root"* → the
   file was in **no commit on any branch** (`git log --all -- '*phase15*'` → empty, absent
   from both trees). This document is **reconstructed from GitHub evidence**, not recovered.

All three were checkable in one `gh` call. The repo's pre-push ritual ("verify on GitHub,
not locally") applies to *inherited* claims, not just to your own.

## What #41's head actually broke, and who fixed it

| Defect | Effect | Fixed in |
|---|---|---|
| `ff28f4a` passed an out-of-scope `width` to `trackAlignedItemOffsetPx(itemWidthPx, widthPx, fraction)` — also inverting its contract (first arg is the *item* width) | `:shared:compileKotlinJvm` + `:shared:compileDebugKotlinAndroid` → `build-and-test` ×2, `apk`, `msi` red; **no test executed anywhere** | **#41 `d685ddc`, on `main` via `dd0fe14`**. This session fixed the same break as `f2d2359`, then **dropped that commit from #43** so the file was never patched twice |
| `NavStatePersistenceTest` — `"artist:"` decoded to `ArtistPage(id=)`; `split(':', limit = 3)` yields `""` and `?.let` guards only **null** | A corrupt entry restored onto the nav back stack, unreachable forever; red `:app-android:testDebugUnitTest`, hidden under the compile failure | **#43 `17d5123`** — every route kind requires a non-blank id; the six round-trip cases unchanged |

## What #43 delivers — `app-android`, the Phase 13 leftovers Phase 15 was scoped to

1. **The module's first test source set** (`98f84d6`): before this, `app-android` had
   *zero* tests, which is exactly how C1 (Koin self-recursion) reached launch with three
   green CI checks — `assembleDebug` is a type-check gate. 7 classes:
   `ShortcutIntentsTest`, `StaticShortcutsXmlTest`, `NavStatePersistenceTest`,
   `NowPlayingShortcutLabelsTest`, `AndroidStringsTest`, `AppModuleGraphTest`.
2. **`AppModuleGraphTest`** (`834c570`) — resolves every `appModule` definition under
   Robolectric: the C1 regression gate `INTEGRATION.md` §8 requested. Closes the
   "real `appModule` is still unverified" residual in `.ai/KNOWN_LIMITATIONS.md` once
   merged, not before.
3. **Extracted, verbatim, to make the formats testable** — `ShortcutIntents`
   (extras → nav action, hostile values ignored) and `NavStatePersistence` (the
   rotation / process-death `Bundle` contract, previously inline in `MainActivity`).
4. **Three dedicated launcher-shortcut icons** (`9cae226`) — `ic_shortcut_search` /
   `resume` / `library`, dark disc + `#BB86FC` accent, glyph paths from shared
   `DhunIcons` instead of reusing the notification glyph.
5. **Dynamic "Now Playing" shortcut** (`ab00486`) — published through
   `ShortcutManagerCompat` while playing (`Playing / <track> — <artist>`), tap lands in
   the expanded FullPlayer; deduped by track id; failure-swallowed so an OEM launcher
   cannot disturb playback.
6. **Accessibility pass** (`12815a4`) — `contentDescription` on the startup spinner,
   heading semantics on `FailureScreen` and the battery-dialog title, `liveRegion` on
   the local-mode banner so degraded playback is announced.
7. **CI coupling** (`869df7c`) — `assembleDebug` → `testDebugUnitTest` wired lazily, so
   `build-and-test` and the `apk` job actually *execute* the suite. Marked for removal
   once a workflow runs the task explicitly (`.github/**` out of scope).
8. **Robolectric-safe app boot** (`075b33f`) — `DhunApp` re-bootable in the shared JVM,
   after `4816c81` fixed a `org.koin.core.get` import that does not exist in Koin 4.0.2.

**Carried, disclosed:** A6's docs-only `49fab38` rode the shared branch inside #41. It is
**not** cherry-picked here — `f562093` rewrote that same ROADMAP block; its one live edit
(the Phase-14 ADR-006 "merged in PR #39 at `62e3241`, CI green" row) is re-applied in
#43's docs commit. `49fab38` stays reachable in history.

## Evidence, and its limit

- **Green on the Android-only head `434ad92`: `build-and-test` pass 4m50s
  (`34099826064`); `apk` pass 2m22s and `msi` pass 4m19s (`34099826022`); `publish`
  skipping by design on PRs.** That is the run that matters: it executes
  `:app-android:testDebugUnitTest` (7 classes, incl. `AppModuleGraphTest` and the
  repaired `NavStatePersistenceTest`) against current `main`, with no player code
  involved. The pre-split heads were green too (`CI #339` at `f2d2359`; `00a432c` =
  `build-and-test` `34098780631`, `apk`/`msi` `34098780641`).
- **Nothing was compiled locally.** This sandbox has no JDK (`java: command not found`)
  and no Maven/Gradle egress (SSL_ERROR_SYSCALL to `repo1.maven.org`,
  `services.gradle.org`) — CI is the only compiler, as the #42 session recorded too.
- `rot-drill` red on every push on this branch **and `main`**: pre-existing issue **#14**
  (GitHub-runner IP gating), non-code, not a required gate, not a Phase-15 signal.
- The 15a pure logic (`toggleLyricsDominant`, `shouldCollapseFullPlayer`,
  `shouldRecenterLyric`, `lyricCenterScrollDelta`, `activeLyricIndex`, `DhunIcon`
  name/path rules) was hand-checked assertion-by-assertion against its four test files:
  every expectation matches its implementation. A hand-check is not a gate: those files
  were only proven by #41's green run at `cd40c1f`, which was their first execution and
  is on `main` now.

## Hardware gates — the user's, unclaimed by either PR

- Long-press the DHUN icon → three distinct shortcut icons, each tap lands (Search /
  Resume / Library).
- Play → long-press → "Playing / <track> — <artist>" → tap expands FullPlayer.
- TalkBack: startup spinner announced, headings navigable, local-mode banner announced on
  change.
- Device rotation + process death restore tab / expanded player / detail stack — the
  *automated* half exists in #43; the device half does not.
- Tablet 840dp rail + docked MiniPlayer: **two-pane was never attempted** — it lives in
  `shared/ui/shell/DhunAppShell.kt`, outside `app-android/**`, nothing was forked.
- 30-minute unrestricted soak with LeakCanary: **never run.**
- On-device rendering of the scrub pill, blur backdrop, mini-player lift and spring
  lyrics (15a).

## Next technical step

1. `gh pr merge 43 --merge` — green at `434ad92`, `MERGEABLE`/`CLEAN`, main unmoved.
2. ~~#41 when green~~ — **done**: merged as `dd0fe14`, all gates green at `cd40c1f`.
3. Then the user's hardware list above. Only after that may the Phase 15 / 15a rows in
   `.ai/ROADMAP.md` leave 🟨 — and `docs/verification/15-*.md` still does not exist,
   because no evidence for it exists yet.
4. If #43 goes red after the re-cut, suspect the *cherry-pick boundary*, not the code:
   the 9 commits were rebuilt onto `f562093`, and `bf4449a`→`17d5123` is the only line
   this session authored in `app-android/**`.
