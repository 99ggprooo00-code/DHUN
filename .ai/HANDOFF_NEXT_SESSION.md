# HANDOFF — next session (and hardware re-test script)

Created **2026-09-21, session `arena/01a0c174-dhun`** (the file this message
thread referred to as "`.ai/HANDOFF_NEXT_SESSION.md` §Round 2 results" was
never committed by the earlier session — its content survived in the session
message and is transcribed verbatim below, now in-repo).

## CURRENT STATE — endless radio merged, 2026-09-21, session `arena/01a0c3b7-dhun`

This section supersedes the statuses below it.

**PR #109 (endless radio) — the next session starts HERE.** Implementation
landed in `62de262`; three CI-driven fix commits followed (`ad3d614`,
`c5b85e5`, `063040d`); docs updated in the pre-merge docs commit. Merged
into `main` under this session's standing merge-without-asking directive —
merge SHA and post-merge CI recorded in the ROADMAP top block (re-pin it
here if it landed after this writing). The rolling `test` release
republished on merge carries the feature; the user's hardware verdict
(re-download + 30-min station soak, watching for a gap at the first
refill) is the open gate. The station chain (`RadioSession`) is
in-memory by design — a cold start re-seeds a fresh `/next` from the
current track when the restored queue falls to ≤3 songs (see
KNOWN_LIMITATIONS).

**Sandbox notes that held this session (no JDK, logs EOF, annotations
API is the readout) are unchanged** — see the DEBUG_LOG entry
"Endless radio: implementation + 6 regression tests" for the three
compiler/test gotchas CI caught (var smart-cast, `args![0]` K2 parser
cascade, 3-arg `assertEquals` not resolving in Android tests) and the
fake-page-threshold test-design slip.

## PREVIOUS STATE — post-merge sync, 2026-09-21, session `arena/01a0c2c7-dhun`

**PR #107 (UI polish) has since been MERGED** as `44e1ffd`
(2026-09-21T09:54:37Z, user-authorized; all checks green on `1def7ee` — a
Maven Central 403 flake on `b8b669d` was retriggered away). Rolling `test`
republished 2026-09-21T09:59:24Z at target/tag `44e1ffd`: APK 18,334,451 B,
MSI **112,885,760 B** (the size that identifies the polish build). The
user's Windows "its great" verdict was on the previous `810bef1` build; the
polish look itself awaits their re-download and eyeball on both platforms.

**Merge + release (verified on GitHub):** PR #106 merged into `main` as
**`810bef1`** at 2026-09-21T05:41:01Z after full green CI, under explicit user
authorization. Rolling `test` re-published **2026-09-21T05:47:48Z**; tag AND
target = **`810bef13f34227360282df88f57372d6b0feba7c`**; APK **18,334,451 B**,
MSI **112,889,856 B** (old MSI was **112,873,472 B** — this size distinguishes
new vs old installer). The previous session's post-merge doc commits never
reached `main` (its GitHub access closed after the merge; the branch is
deleted), so `main`'s `.ai` docs were stale until this session's docs-sync
commit.

**Hardware verdicts (exact, updated 2026-09-21):**
- **Android: PASS** on the merged build — user: "android all working" (Home
  moods, pull-to-refresh, close/swipe behavior).
- **Windows: PASS (user-tested 2026-09-21)** — the user installed the updated
  app (the `810bef1` rolling release, PR #106 scope) and reports **"its
  great"**; the earlier failure was the stale install. Gate-4 items were not
  itemized individually and no failure was reported. The user's "blurry
  thumbnail is gone" remark was retracted minutes later: "sorry thumbnail
  wasent loded well" — a transient artwork-load hiccup, not a regression
  (desktop blur support is unconditional, `BlurSupport.jvm.kt` = true).
- **PR #107 (UI polish) is NOT in any released build** — the user authorized
  its merge ("it's good go ahead") knowing this; its visuals arrive with the
  next rolling `test` release after merge. The four-gate procedure below is
  retained for any future clean-install verification.

**Saved Windows verification procedure — run it when the user tests Windows:**
- **Gate 1:** downloaded `dhun-test.msi` must be exactly **112,889,856 bytes**;
  if different, re-download from
  `https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.msi`.
- **Gate 2:** kill any dhun process; Settings → Apps → uninstall ALL DHUN
  entries.
- **Gate 3:** install the MSI, launch from the Start menu (old pins may point
  at a stale exe).
- **Gate 4:** proof = the round refresh icon next to the Home greeting is
  **GONE**. Then verify: moods switch the whole page to "<Mood> songs"; For
  you restores Home; X fully quits and stops music; downloaded track
  `O3-6zB3kg8M` plays from Library → Downloads (online first, then airplane
  mode; keep the existing file).
- If Gate 4 fails with correct MSI size + clean install: inspect the shipped
  MSI binary itself (search bundled jars for HomeMood / "Refresh music"
  strings).
- The user cannot comfortably collect console logs — prefer on-screen
  feedback; redact signed URLs/credentials/personal paths. For Android download
  issues ask for the exact `DHUN download <id>: <stage>` log line
  (resolve/bytes/worker) and fix the named stage, never guess.

**Session queue (priority order):** (1) help verify/fix Windows per user
reports; (2) user-requested UI polish — **IMPLEMENTED 2026-09-21 on the
session branch**: dark ladder +0x0C per rung (bg 0x16, surfaces 1E→36,
highest 3C), FullPlayer dim 0.42/0.10 + ambient stops lowered (bottom 0.86),
shell backdrop dim 0.45 + stops 0.50/0.32/0.44/0.62, `DARK_LEGIBILITY_FLOOR`
0.42→0.45 (contrast replicated locally, all WCAG gates pass),
`artworkThumb` 56→64dp (no clip risks found), new continuous `GlassDock`
(blurred-artwork veil under glassBarTop→glassStrong) holding
MiniPlayer(`embedded = true`) + NavigationBar on the single-pane shell;
CI green then user visual verdict on both platforms — record "awaiting user
visual verdict", no merge without it; (3) endless radio via `/next`
continuation when ≤3 songs remain — ask the user before starting (Android is
PASS; Windows accepted but untested); supersedes the old #99 "different song"
behavior — same song, same position, no gap, tail replaced on refill; (4)
remaining #105 checklist: S3/S6 soaks, rotation/process death, persistence,
Windows tray/jump-list/SMTC/media keys.

## Final pre-merge verification — superseded by the CURRENT STATE section above

Code head **35d76f6** on PR #106 is pushed and GREEN: build **35564494454**,
build-and-test **35564494452**, APK/MSI **35564494458**. Full shared, Android
Robolectric and Desktop JVM suites passed, including 18 added regression cases
(7 Home, 3 task dismissal, 6 URI/resolver, 2 local-error routing). Local Python
packaging/helper suite: **29 passed**. Full diff whitespace check passed.

User explicitly authorized finish → verify/test → merge. Final docs-only
verdict commit still needs its own green checks before merging. After merge,
verify post-merge CI/release target/tag/asset sizes; the #105 build identity
below is only the **last pre-merge baseline**, NOT a new #106 download.
If this session loses GitHub after merge, the next session checks release first.
Hardware tests below are still OPEN; CI does not certify VLC/device gestures.

## Home categories / refresh and current authorization — 2026-09-21

User reports For you/Focus/Chill/Workout/Party leave music unchanged and requests
pull-down refresh at the top instead of the header refresh icon. Original chips
only reordered title matches in lower shelves; top rows never changed.

PR #106 adds topic-song searches for the four moods; For you uses the original
Home browse feed. This is explicitly NOT a signed-in personalized mood API.
Mood state belongs to the model: selection, retry and refresh keep the category;
page and first-load generation guards discard old-category responses. Category
views hide unrelated history/recommendations, and chips stay usable on errors.
Material3 PullToRefreshBox handles overscroll-at-top threshold/indicator; no
header refresh icon. Footer + F5 (when Home has focus) + accessibility refresh
are non-touch fallbacks. Seven new HomePaginationTest regressions added.

**Latest authorization supersedes earlier holds in historical entries below:**
user said to finish, create PR, verify/test and merge. Existing PR #106 is the
session PR. Must await exact-head green CI and record verdicts BEFORE merge.

**Home hardware script (OPEN):** tap every category → topic-song row replaces
Home rows; tap For you → normal Home returns. Tap moods rapidly, including on
a slow network; newest selection must win. On a mood, pull down at top far
enough and release → indicator then refreshed same-category results. Mid-list
scroll should scroll, not refresh until top; short/horizontal drags should not
refresh. Test empty/error recovery, pagination and Windows footer/F5 fallback.
Server can return identical results on refresh; no artificial randomization.

## Explicit-close report and clarified contract — 2026-09-21

User: music continues after closing on both platforms; Windows needs Task
Manager and Android notification pause. Clarified Android means **swipe away
from Recents**; Windows **X must close the app completely and stop music**.
This supersedes close-to-tray as a supported setting, not merely its default.

In-flight PR #106 (same session branch, NOT merged) now includes:
- Windows X → existing full `quit()` path (release player, native session,
  tray, single-instance lease, persistence and scope; exit application).
  Old `close_to_tray=true` is ignored; removed the setting from the UI.
  Legacy key/model retained for storage/API compatibility, not consumed by host.
- Android task removal → pause + stop engine, remove foreground notification,
  stop service. Not in Activity lifecycle, so Home/lock/rotation remain safe.
  Unit coverage for shutdown ordering, missing session and engine-stop exception.
- ASCII MSI description avoids the reported `a€"` dash encoding artifact.

**Required hardware checks (not yet passed):**
1. Windows: play → X. Sound stops, window and tray disappear; no Task Manager
   needed. Relaunch works. Repeat with old close-to-tray enabled in existing DB.
2. Windows: minimize continues playback; Ctrl+Q and tray Quit still shut down.
3. Android: play → swipe out of Recents; sound stops and foreground media
   notification goes away. Repeat while buffering and while paused.
4. Android: Home, lock and rotation continue playback; reopen after dismissal
   and play again. Download service is separate and is not stopped by this change.
5. Check Windows installer description no longer has a garbled dash.

Previous docs head c6326f0 passed CI. Close-fix CI/hardware still pending.
No merge permission. Rolling release remains #105 until an authorized merge.

## Round 2 results (user, 2026-09-20, build `8635851`) — verbatim

> Notification works normally as well as in lock screen. Button also works
> well (shuffle button turns on and off but doesn't shuffle the queue).
> Download on windows is working. Download on android is not working. Lyrics
> is working needs some ui adjustment (font size increase, auto scroll
> lyrics, highlight the lyrics that is being played) later. No EQ on app but
> I have EQ by default on phone, and it works on windows. UI elements need
> adjustment on full player, I'll explain later what changes are needed —
> for now it is OK. On play radio, the song that is playing starts over from
> start.

### Historical Round-2 passes (latest failures supersede these)

- Android media notification correct and functional, incl. lock screen.
- Notification/lock-screen transport **buttons** work.
- **Windows downloads work.**
- Lyrics load and display on both (quality only — defect 4).
- Desktop EQ works. Phone-level EQ covers Android (recorded S4 deferral —
  `AudioEffect` was never implemented; do not "fix" without a decision).
- Full player visually acceptable for now — **user will specify changes
  later; do not redesign speculatively.**

## Defect ledger after 2026-09-21 session (all four have MERGED fixes)

| # | Defect | Fix | Root cause (verified in code) |
|---|---|---|---|
| 1 | Android downloads dead (Windows fine) | **#98** merged `b102c78` | `DownloadServiceController.attach()` collected the first (empty) download list before the QUEUED row existed → `stopSelf()` microseconds after start. Fixed with a `hasSeenWork` latch (stop only on had-work→empty transition) + try/catch around FGS start/stop for Android 14/15 `dataSync` policy. Test: `DownloadServiceStopLogicTest`. |
| 2 | Play radio restarts current song | **#99** merged `870d3bc` | `parseRelatedTracks` kept every `playlistPanelVideoRenderer`; panel[0] IS the current video → `startRadio()` = `playRelatedAt(0)` = replay. Fixed by filtering `it.id != track.id` in `loadRelated` (also fixes the Related tab listing the playing song). Tests: `ParserFixtureTest` (fixture has current as panel[0]), `PlayerViewModelTest`. |
| 3 | Shuffle toggles but doesn't shuffle | **#100** merged `80e94bf`, corrected by **#102** merged `4d693ce` | Shuffle only reordered *traversal*; the visible queue stayed source order. #100 published the shuffled list but shipped three real holes, fixed by #102: (a) Android never re-synced QueueManager after engine auto-advance → highlight stuck on the previous row (`refresh()` now `syncCurrent`s to the sounding item); (b) desktop consumed SOURCE indices while showing the SHUFFLED list → taps played the wrong row (now translated via `playAtDisplay`/`removeAtDisplay`/`moveInDisplay`); (c) every queue mutation rebuilt+re-shuffled the upcoming order → "play next" landed randomly (now order-preserving playOrder splices). 9 new provable-deterministic `QueueManagerTest` cases. |
| 4 | Lyrics UI (font, auto-scroll, highlight) | **#101** merged `2c619b9` | Machinery (activeIndex, accent animation, `centerLyric` auto-scroll) already existed for synced lyrics; the styling was too subtle and the unsynced path was `bodyMedium`. #101: synced base `titleLarge`→`headlineSmall`, active Bold + spring scale 0.92→1.04 + 16% accent wash, inactive dimmed; unsynced bumped to `titleMedium` + 15% more line height. Unsynced lyrics can never auto-scroll/highlight (no timestamps) — by design. |

## Concurrency incident (single-agent doctrine breached by circumstance)

Session `arena/01a0c154-dhun` (the one that produced #98/#99) was **still
live** after this session started: while this session was reviewing its open
PR #100, that session merged **#100 (00:58:25Z)** and opened+merged **#101
(01:07:26Z)**. This session detected it via the Actions run list, rebased its
in-flight corrective work onto the new main, and merged it as **#102
(01:20:08Z)** — so #100's holes lived on main for ~22 minutes and are closed
by #102. **User action: confirm no other agent session is still running
before starting the next one** (the one-agent rule in MASTER_PROMPT §8.1).

## Build identity for the re-test (verified 2026-09-21)

- Rolling `test` published **2026-09-21T03:00:04Z**. Release target and tag
  both resolve to **`414cd7941d69e185b5628e8b9b7fffe5a0a5a4f0`** (PR #105).
- APK **18,334,451 bytes**; MSI **112,873,472 bytes**, with SHA-256 sidecars.
- Build/release runs 35555585912 / 35555585908 / 35555585893 succeeded.
- Scheduled extraction-health 35561269411 later failed: check-run annotations
  say **ENVIRONMENT_BLOCKED**; live health remains unverified.
- Recheck the release target/tag and sidecars whenever `test` is replaced.
  Byte sizes alone do not prove identity; reproducibility across rebuilds is
  not assumed. Old c4c5d04 / 17,948,508-byte APK instructions are superseded.

## Latest hardware report / triage — 2026-09-21

**Latest user clarification:** row says **Downloaded**; playback launched from
**Library → Downloads**. User cannot collect console logs; do not keep blocking
on those. Candidate fix now in this session PR: portable escaped file URI from
raw path + local-file error routing, not a transport rewrite. Tests exercise
Windows paths on Linux CI so this cannot hide behind platform-specific coverage.
Hardware cause is still not proven without a replay of the corrected build.
Retest the existing download online then offline before deleting/redownloading;
if it fails, request the updated on-screen playback details first.


**Windows download/playback FAIL**, track `O3-6zB3kg8M`: user reports it
“downloads but on playing” shows “Stream rejected by the CDN and no local copy
could be fetched”, with details “libVLC rejected the stream URL and the cache
fill produced no file (VLC cannot send the resolving identity's User-Agent).”
Tests 1–5 remain **unconfirmed**; do not interpret the supplied PASS criteria as
results. Earlier Round-2 Windows pass is historical, not a verdict on this build.

**Original #105 code trace (branches corrected by the pending PR), not hardware root-cause verdict:** desktop DI wraps playback resolution in
`OfflineFirstStreamResolver`: COMPLETED row + existing file → local file URI;
otherwise network resolver. `DesktopDhunPlayer` checks its separate bounded
cache first, then the provider. It sets `streamingRemoteUrl = info.audioUrl`
even for a local URI. `handlePlaybackError` does not reject local URIs before
entering the purported CDN fallback; it checks only the bounded cache afterward.
Thus the reported text alone cannot distinguish network failure from local-file
playback failure. Do not claim a CDN rejection or successful persistent download
based only on that text.

**Remaining optional evidence:** installed build and matching `DHUN download O3-6zB3kg8M:` completion/failure and `DHUN cache:`
lines. If completed, confirm the reported local file exists and is nonempty.
Redact signed URLs, credentials and personal path segments. The raw-path URI and misleading local-error branches now have candidate fixes
and regression coverage; no speculative transport changes.

**Android download failure:** request logcat line exactly as emitted:
- `DHUN download <id>: resolve failed: …`
- `DHUN download <id>: bytes failed: …`
- `DHUN download worker failed for <id>: …`
Do not guess which stage failed. Completion is logged as
`DHUN download <id>: completed <bytes> bytes -> <path>`.

## PR #105 supersedes the old radio/shuffle/download ledger

- Radio uses `replaceQueueKeepingCurrent`: same sounding song and position,
  no pause/restart; new radio tail. Related excludes current; row taps still play.
- Android shuffle no longer reloads/prepares the timeline; mutations preserve
  the sounding item. Queue display/cursor fixes from #102 remain.
- Android downloads use OkHttp, always-Range, safe resume/restart and complete
  416 handling, IO workers and terminal FAILED on crashes. Hardware gate open.
- Only after all hardware checks pass: endless radio via `/next` continuation
  when ≤3 tracks remain (read-only reference notes in DEBUG_LOG). No forks or
  vendoring. Then S3/S6 tail below. Never merge without user permission.

---

## HARDWARE RE-TEST SCRIPT — verified #105 build above

Report PASS / FAIL for each test; include playback details on errors.

### A. Android (upgrade-install over your current build)

1. **Download (defect 1 — the headline re-test):** open any track's menu →
   **Download**. Expect: a download notification appears and the track lands
   in Library → Downloads. Then switch on **airplane mode** and play it from
   Downloads → must play offline.
2. **Download while app in background:** start a download, send the app to
   the background immediately. Expect: download still completes (FGS race
   fix).
3. **Play radio (defect 2):** play any song, let it get a few seconds in,
   then tap **"Play radio"** (or Start radio). Expect: the SAME song keeps
   playing at the same position, without pause/restart; Queue shows it on
   top with radio songs behind it. Also check the
   **Related tab**: the currently-playing song must NOT appear as a row.
4. **Shuffle (defect 3 — visible reordering):** queue up several tracks
   (album or radio) → open the **Queue tab** → toggle **shuffle ON**.
   Expect: the list VISIBLY REORDERS, with the playing track at top and its
   highlight following as songs advance. Toggle ON/OFF several times while
   listening: no hiccup, pause or restart. Let one song end naturally: the
   highlight must move down the (shuffled) list — it must not stick.
5. **Shuffle taps still hit the right row:** with shuffle ON, tap any visible
   queue row → that exact track must play.
6. **Play next under shuffle:** shuffle ON → "Play next" on some track →
   expect it appears directly under the playing row AND is what plays when
   the current song ends.
7. **Notification + lock-screen regression:** play, lock, use next/previous
   from the lock screen. Expect: controls work and the queue-tab highlight
   (after unlock) matches what played.
8. **Lyrics (defect 4):** open a popular song's lyrics with a synced source.
   Expect: larger text, current line highlighted (accent + bold) and
   auto-scrolling to center; scrolling by hand pauses follow ("Follow
   lyrics" chip resumes). On a plain-text (unsynced) track: readable larger
   text, no auto-scroll (no timestamps exist).
9. **Sanity:** one fresh search → play (nothing above should have broken
   basic playback).

### B. Windows (upgrade-install over your current build; VLC required)

10. **Shuffle (defect 3):** same as 4–6 — Queue tab visibly reorders on
    toggle, highlight tracks natural advance, tap-to-jump plays the row you
    tapped, "Play next" is next.
11. **Radio (defect 2):** as 3 — starting radio must not restart the song.
12. **Sanity:** play/pause, next/previous, tray still alive after 10 min.
13. **Windows download + offline:** download via a track's menu, wait for
    completion in Library → Downloads, disconnect network and play it from
    Downloads. On failure send row state and the evidence requested above.

### C. Still open after this round (S3/S6 — unchanged)

- 30-minute soaks (both platforms), rotation/process death.
- Settings/theme/accent persistence.
- Windows native column: single-instance, tray, close-to-tray, jump-list
  Play/Pause, media keys/SMTC.
