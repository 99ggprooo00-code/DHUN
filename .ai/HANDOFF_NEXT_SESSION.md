# HANDOFF — next session (and hardware re-test script)

Created **2026-09-21, session `arena/01a0c174-dhun`** (the file this message
thread referred to as "`.ai/HANDOFF_NEXT_SESSION.md` §Round 2 results" was
never committed by the earlier session — its content survived in the session
message and is transcribed verbatim below, now in-repo).

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

### PASSES banked (do not re-test)

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

## Build identity for the re-test below (release API, live-verified)

- Rolling `test` **published 2026-09-21T01:24:37Z**, `target_commitish`
  **`4d693ce`** (= PR #102 merge HEAD), assets: `dhun-test.apk`
  **17,948,508 B**, `dhun-test.msi` **112,861,184 B** (+ `.sha256` sidecars).
  Post-merge CI on exactly `4d693ce`: CI 35550567894 ✓ · Build APK
  35550567913 ✓ · test-release 35550567905 ✓.

---

## HARDWARE RE-TEST SCRIPT — build `test` @ 2026-09-21T01:24:37Z (APK 17,948,508 B / MSI 112,861,184 B)

Re-download BOTH installers after this merge (rolling `test` was replaced at
the publish time above; if your file sizes differ, you have a stale build).
Report per number: PASS / FAIL (+ what you saw; use "Show playback details"
on the error band if anything fails to play).

### A. Android (upgrade-install over your current build)

1. **Download (defect 1 — the headline re-test):** open any track's menu →
   **Download**. Expect: a download notification appears and the track lands
   in Library → Downloads. Then switch on **airplane mode** and play it from
   Downloads → must play offline.
2. **Download while app in background:** start a download, send the app to
   the background immediately. Expect: download still completes (FGS race
   fix).
3. **Play radio (defect 2):** play any song, let it get a few seconds in,
   then tap **"Play radio"** (or Start radio). Expect: it moves to a
   DIFFERENT song, never restarting the current one. Also check the
   **Related tab**: the currently-playing song must NOT appear as a row.
4. **Shuffle (defect 3 — visible reordering):** queue up several tracks
   (album or radio) → open the **Queue tab** → toggle **shuffle ON**.
   Expect: the list VISIBLY REORDERS, with the playing track at top and its
   highlight following as songs advance. Let one song end naturally: the
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

### C. Still open after this round (S3/S6 — unchanged)

- 30-minute soaks (both platforms), rotation/process death.
- Settings/theme/accent persistence.
- Windows native column: single-instance, tray, close-to-tray, jump-list
  Play/Pause, media keys/SMTC.
