# Phase 05 — Data layer (SQLDelight, repositories, use cases)

Status: **CODE COMPLETE — unit-verified in CI; in-app hardware round-trips OPEN.**

Date: 2026-09-03. PR #4.

## What Phase 05 delivers

1. **Schema v1** (`shared/src/commonMain/sqldelight/dev/dhun/database/*.sq`,
   generated class `dev.dhun.database.DhunDatabase`):
   `Track`, `Favorite`, `Playlist`, `PlaylistTrack`, `History`, `Settings`,
   `RecentSearch`, `NowPlayingQueue`, `NowPlayingState`. Keyed by YouTube
   IDs; `Track.cachedAt` reserved for the caching phase. Foreign keys ON on
   both drivers.
   - No `INSERT … ON CONFLICT` (UPSERT) anywhere: Android API 26 ships
     SQLite 3.18, which lacks it. `Track` uses insert-ignore + update inside
     a transaction so `REPLACE` can never cascade-delete favorites/playlist
     rows. `REPLACE` is only used on tables nothing references.
2. **Drivers:** Android `AndroidSqliteDriver` (FKs enabled in `onOpen`),
   JVM `JdbcSqliteDriver` (`foreign_keys=true`; file in
   `%APPDATA%\DHUN`, `~/Library/Application Support/DHUN`, or
   `$XDG_DATA_HOME/dhun`; in-memory for tests).
3. **Repositories** (`dev.dhun.data`, Flows out / suspend in):
   `TrackRepository`, `LibraryRepository` (favorites), `PlaylistRepository`
   (create/rename/delete/add/remove/move with dense renumbering),
   `HistoryRepository` (record/complete/"listen again"), `SettingsRepository`
   (typed helpers, corrupt values → defaults), `SearchRepository` (recent
   searches, deduped + trimmed), `NowPlayingRepository` (queue + position
   snapshot, cheap progress updates, clamped restore). `DataLayer` bundles them.
4. **Use cases** (`dev.dhun.domain`): ToggleFavorite, ObserveFavorites,
   Create/Rename/Delete/AddTo/RemoveFrom/Reorder/ObservePlaylists,
   RecordPlay (+complete handle), GetRecentlyPlayed, GetHistory (day
   grouping with zone offset), Get/UpdateSetting (key validation),
   RecentSearches, Save/RestoreNowPlaying (honours `resume_on_launch`).
5. **`SettingsKeys`**: audio quality, theme, accent mode, lyrics, cache MB,
   country code, explicit, close-to-tray, resume-on-launch, window geometry.
6. **Now-playing persistence (shared, both platforms)** —
   `dev.dhun.player.NowPlayingPersistence` observes any `DhunPlayer`:
   queue change → snapshot; every 5 s while playing → position; track
   transition → history row, previous row marked completed at ≥ 90 %.
   `restore()` on cold start re-queues **paused** at the saved position.
   To make that honest, `DhunPlayer.prepareQueue` gained
   `playWhenReady: Boolean = true`; Media3 maps it to `playWhenReady`,
   the desktop player defers stream resolution until the first play press
   (stream URLs expire) and applies the pending seek once playing.
7. **Wiring:** Android Koin module builds `DataLayer` + use cases;
   `MainActivity.attach()` restores then starts persistence. Desktop
   `Main.kt` does the same in a `LaunchedEffect`. Harness screens grew a
   ♥ toggle per row, a "Recent:" searches line and a "Listen again" strip —
   all read from the DB, so a restart proves persistence visually.

## Tests (run in CI: `./gradlew :shared:jvmTest`)

| File | Covers |
|---|---|
| `data/RepositoriesTest.kt` | every repository against a real in-memory SQLite DB: track upsert round-trip, favorite add/observe/remove (+ row survives), playlist create/add/dup/move/out-of-range/remove/renumber/rename/delete, playlist ordering by update, history record/complete/listen-again/limit/remove/clear, settings all types + corrupt value, recent-search dedupe/trim, now-playing queue round-trip/progress/replace/clamp/clear, schema version = 1 |
| `domain/UseCasesTest.kt` | toggle semantics, playlist rules (blank rename rejected, bulk add count), record→complete marks only that row, day grouping across zone offsets, settings key validation + defaults, recent searches, restore honours resume setting |
| `player/NowPlayingPersistenceTest.kt` | **queue-restore round-trip** with a scripted player: session 1 plays → DB has queue/position/history; session 2 restores paused at the position and seeks; restore does not add history; natural completion marks the row; restore is a no-op when nothing saved or player busy |

## Acceptance criteria (PROMPT_SEQUENCE.md Phase 05)

| # | Criterion | Evidence |
|---|---|---|
| 1 | All repo tests green both targets | CI `:shared:jvmTest` (JVM). Android target compiles the same commonMain + android driver in `:app-android:assembleDebug`; Android instrumentation is not run in CI (no emulator — project policy). |
| 2 | Favorite → observe → unfavorite round-trip verified in-app both platforms | **OPEN — hardware.** Harness: tap ♡ on a result → turns ♥ → kill app → reopen → search again → still ♥ → tap → ♡. |
| 3 | Queue survives app restart on Android | **OPEN — hardware.** Play a song ~30 s → swipe app away → reopen → the harness shows the same track paused at ~0:30; press ▶ resumes. Same on desktop (first press re-resolves). |

## Hardware checklist (user-run; log dated results under Evidence)

1. Install the PR's test APK / run `./gradlew :app-desktop:run`.
2. Search "queen" → "Recent: queen" appears above the results.
3. Tap a song; after a few seconds kill and relaunch → "Listen again" shows
   it, now-playing bar shows it **paused** at the previous position.
4. ♡ → ♥ on two songs; relaunch; both still ♥. Unfavorite one; relaunch; only one ♥.
5. Nothing crashes with airplane mode on (DB works offline; search errors are typed).

## Evidence

(empty — hardware verification pending)
