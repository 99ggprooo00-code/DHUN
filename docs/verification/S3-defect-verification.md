# S3 Defect Verification — Exact Build + Taps

**Source build (broken):** `8635851` — `test` published `2026-09-20T23:38:30Z` — APK `17,948,508 B` / MSI `112,861,184 B`.
**Fixed build (this PR chain):** `main@2c619b9` — `test` rolling pre-release tag **`test`** re-published **`2026-09-21T01:13:10Z`** (`created 2026-09-21T01:07:23Z`, `updated 2026-09-21T01:13:07Z/09Z`). Artifacts exactly:

- `dhun-test.apk` — `17,948,508 B` — `https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.apk` (SHA256 sidecar `dhun-test.apk.sha256` 80 B, same URL + `.sha256`)
- `dhun-test.msi` — `112,861,184 B` — `https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.msi` (SHA256 sidecar `dhun-test.msi.sha256` 81 B)

Workflow that published it: `test-release` on `main` push `35549853635` (`Merge pull request #101`, `publish` job `success` at `01:13:25Z`; companion `CI` run `35549853650` ✓ `4m51s`, `Build APK` run `35549853636` ✓ `2m09s`). No other artifact has those exact byte counts and timestamps — if your file manager shows a different size or GitHub Release page shows a different `Published` time under `Releases → test`, you have the wrong build.

Branch that equals this build: `arena/01a0c154-dhun@2c619b9` (fast-forward of `main`, `git rev-parse HEAD` must be `2c619b9…`).

Install that APK/MSI on a fresh phone/PC checkout. The four checks below are ordered highest→lowest as requested and each is one `git revert` away from the others (commits `b850067`, `32bd8aa+a37303c`, `70775bb`, `129f582` merged as PRs #98–#101).

---

## 1 — Android downloads broken (highest, commit b850067 → PR #98)

**Why it broke:** `ForegroundServiceDownloadManager.enqueue` tagged the download dir *before* the FGS, then observed state with a race; on targetSdk 35 (`dataSync`) the service could be killed before `enqueue` returned. Filed under `app-android/...ForegroundServiceDownloadManager.kt` and `DownloadServiceController.kt`.

**Taps (Android APK only):**
1. Fresh install the `test` APK above; grant storage/media permission when prompted.
2. Bottom bar → `Search` → type `Believer` → tap first result → `▶ Play`.
3. On the now-playing queue row ••• → `Download` (or long-press queue row → Download). You must see a Foreground notification “Downloading …” within 1 s; the app must NOT silently disappear to background or show “Download failed”.
4. Pull notification shade: the `Downloading` notification stays until completion, then flips to “Download complete”.
5. File manager → `Android/data/dev.dhun.dhun/files/` (or `Downloads/DHUN`) → file appears with size >0; Settings → Downloads inside DHUN shows the row with a checkmark.
6. Force-stop DHUN, relaunch → Downloads still lists the file. Revert check: `git revert b850067` reproduces the race (notification flashes then vanishes, file 0 B).

## 2 — Play-radio restarts current song (commit 32bd8aa + test a37303c → PR #99)

**Why it broke:** `parseRelatedTracks` (Piped `next` parser) returned the currently-playing track as item 0 of the radio list; `playQueue(related, 0)` therefore re-queued and restarted the same song.

**Taps (phone or desktop):**
1. Play any track with a populated `Related` tab (e.g. search `Blinding Lights` → play the YouTube result with thumbnail).
2. Open bottom sheet → `Related` tab → note the header “Based on <title>” and the first row’s title. It must NOT equal the currently-playing title at the top. The test fixture now asserts this: `ParserFixtureTest` loads `fixtures/related.json` and fails if `tracks[0].id == current.id`.
3. Tap `Play radio (n)` — playback must **not** seek to 0:00; the seek bar continues from its current position, and the queue (Queue tab) now shows `n` tracks whose first entry is the former `Related[0]`, not the track you were just on.
4. Tap a single `Related` row’s `▶` — same invariant: queue starts at that row, current track is not duplicated at index 0. Revert check: `git revert 32bd8aa` puts the current track back at `Related[0]` and `Play radio` audibly restarts it.

## 3 — Shuffle toggles but doesn’t shuffle (commit 70775bb → PR #100)

**Why it broke:** `QueueManager.shuffleEnabled` was set but no reordering was published; `DesktopDhunPlayer` delegated to `queueManager` but `AndroidDhunPlayer` never reordered `ExoPlayer` mediaItems, and `displayQueue` wasn’t exposed.

**Taps:**
1. Build a known queue: Search → add 5 distinct tracks to queue (tap `+ Add to queue` on each search row). Open Queue tab → note the order `A B C D E` and the `1 of 5` label on the first row.
2. Start playback at `A`. Tap the shuffle icon (bottom player bar) → icon tints with `accent`, toast/label says Shuffle on, and the Queue tab **visibly reorders** within 200 ms: the playing track stays first, the remaining 4 are permuted (not `B C D E`). `QueueManagerTest.setShuffleIsIdempotentAndToggleIsInverse` and `shuffleDisplayOrderDiffersFromSourceWithSeededRandom` cover this.
3. Tap `Next` → you get the shuffled successor, not `B`. Tap shuffle again → Queue snaps back to source order `A B C D E` and the `1 of 5` row is still highlighted.
4. With shuffle ON, long-press drag the second row to the bottom or swipe-remove it → the operation applies to the displayed (shuffled) order and the backing ExoPlayer playlist stays in sync (Android only previously failed). `QueueManagerTest.displayQueueShowsShuffledOrderWithCurrentFirst` asserts `displayQueue[0] == current`.

## 4 — Lyrics UI polish (commit 129f582 → PR #101, last)

**What changed:** Synced lyrics already had `activeLyricIndex` + `centerLyric` auto-scroll + `Follow lyrics` affordance, but the highlight was faint (titleLarge, SemiBold, accent 12%, scale 0.94→1.0, dim `textTertiary`). Unsynced was plain `bodyMedium`.

**Taps:**
1. Play a track with **synced** LRC (e.g. any YouTube-sourced track that shows timestamps — search `Bohemian Rhapsody` and pick the result where the Lyrics tab shows centered lines, not “Not time-synced”). Lyrics tab must show lines in `headlineSmall` (~24 sp, visibly larger than before), centered, with vertical padding half-viewport so first/last line can center.
2. As the song advances, the active line pops to `accent` colour, `Bold`, `1.04×` scale, with a `16%` accent pill background; inactive lines are `textSecondary 72%`, `Medium`, `0.92×`. The list auto-centers the active line; scroll the list manually with a finger/wheel → auto-follow pauses and a bottom `Follow lyrics` pill appears; tapping it or tapping a timestamped line seeks and re-enables follow.
3. Pick a track with **unsynced** lyrics (e.g. a SoundCloud-sourced track or any where the tab shows “Not time-synced” header). The plain text must now be `titleMedium` with `1.15×` relaxed lineHeight — noticeably larger and airier than the old `bodyMedium`. Bottom `Spacer(huge)` remains so the last verse isn’t pinned to the nav bar.
4. No other player behaviour changes (extraction/probe semantics untouched per ADR-007, FullPlayer redesign deferred).

---

### How to know you have the right build without guessing

On the Releases page (`github.com/99ggprooo00-code/DHUN/releases`) the `test` tag must read **Published 2026-09-21 01:13:10 UTC** (hover shows `2026-09-21T01:13:10Z`). Downloading the APK and running `wc -c dhun-test.apk` or checking file properties must report **17948508**; `msiexec` or file properties for the MSI must report **112861184**. The SHA256 files next to them must validate (`sha256sum -c dhun-test.apk.sha256`). Any other byte count or timestamp = you are not on `2c619b9`.

Revert matrix: each PR merges one commit (or two for #99) and CI stays green on revert: `git revert b850067`, `git revert 32bd8aa && git revert a37303c`, `git revert 70775bb`, `git revert 129f582` — `ci.yml` business job still passes (`5m45s` on #101); only the corresponding verification section above fails.
