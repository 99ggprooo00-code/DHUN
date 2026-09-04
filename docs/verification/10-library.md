# Phase 10 verification — Library & History screens

Status: 🟨 **CODE COMPLETE** — units tested in CI; on-hardware acceptance OPEN (needs a physical run on Android + desktop, like Phases 03/04/05/08/09 checklists).

## What was built (code-level, auditable)

| Spec item (PROMPT_SEQUENCE.md Phase 10) | Implementation |
|---|---|
| Library tabs: Playlists / Favorites / History | `shared/presentation/library/LibraryViewModel.kt` — `LibraryTab` enum (`PLAYLISTS/FAVORITES/HISTORY`), `selectedTab` flow, `playlistsFlow` (`observePlaylists`), `favorites`/`favoriteIds` (`observeFavorites`), `historyEntries`/`groupedHistory` (`observeHistory` + `GetHistoryUseCase.groupByDay`); `shared/ui/library/LibraryScreen.kt` — `LibraryTabRow` (pill tabs), scaffold header “Your library”, `Column` with `weight(1)` content; `shared/ui/shell/DhunAppShell.kt` — `AppTab.LIBRARY` (📚) added, `DhunAppShell` creates `LibraryViewModel` via `remember(dataLayer, player, scope)` with `setContext` → `PlayerViewModel.setPlayContext`, `TabContent` routes `LIBRARY` → `LibraryScreen` |
| Favorites list (tap plays favorites as queue, swipe to remove) | `LibraryViewModel.playFavorites(startIndex)` / `playFavoritesTrack(track)` / `removeFavorite` / `toggleFavorite` — queue handoff with `PlayContext.LIBRARY` via `setContext` lambda; `LibraryScreen.FavoritesTab` — empty `EmptyView` (“No favorites yet”), else `ReorderableList` (swipe-remove `onSwipeRemove → removeFavorite`, tap `onItemClick → playFavoritesTrack`, drag handle retained but `onMove` no-op — ordering is `addedAt DESC` from SQL), `Play all` button, overflow `⋮` per row |
| History grouped by day with relative times; long-press remove; clear all with confirmation | `LibraryViewModel.historyEntries` (`observeHistory(300)`) + `_offsetMs`/`_groupedHistory` (`combine(historyEntries, _offsetMs)` → `groupByDay`), `refreshHistoryGrouping(offsetMs)` ticker every 60 s via `LaunchedEffect`; helpers `relativeTimeLabel` (“just now”/“5m ago”/“2h ago”/“3d ago”/“Xmo ago”) + `dayHeaderLabel` (“Today”/“Yesterday”/ISO `YYYY-MM-DD` via civil_from_days without `java.time`); `LibraryScreen.HistoryTab` — `LazyColumn` grouped by `HistoryDay`, header row (`dayHeaderLabel` + “Play day”), `HistoryRow` (`combinedClickable` long-press + `✕` fallback button), relative text `track • 5m ago • SEARCH • completed`, `Clear all` → `ClearHistoryConfirmDialog` (`GlassCard` + `DhunButton`) |
| `RecordPlay` use case wired into the player (search/home/artist/album/playlist/radio contexts) | `shared/presentation/player/PlayerViewModel.kt` — `setPlayContext(ctx)` / `playQueue(tracks,index,context)` / `playTracks(...,context)` / `playRelatedAt(...,context)` (`persistence?.setPlayContext` before `prepareQueue`); `DhunAppShell` — `onPlayTrack` (tab-aware: `HOME/SEARCH/LIBRARY` → `PlayContext.HOME/SEARCH/LIBRARY`), `onPlayArtist` → `ARTIST`, `onPlayAlbum` → `ALBUM`, `onPlayPlaylist` → `PLAYLIST`; `LibraryViewModel` → `LIBRARY/HISTORY/PLAYLIST` via `setContext` lambda; `NowPlayingPersistence` already records with `playContext` on track change + marks completed at ≥90% |
| Platform UTC offset without `kotlinx-datetime` | `shared/presentation/library/LibraryViewModel.kt` — `expect fun currentUtcOffsetMs(): Long`; `androidMain/.../CurrentOffset.android.kt` + `jvmMain/...CurrentOffset.jvm.kt` — `TimeZone.getDefault().getOffset(System.currentTimeMillis())` (handles DST) |
| Navigation + bottom bar | `AppTab.LIBRARY` inserted between SEARCH and CATALOG → 4-tab bar (Home/Search/Library/Catalog); `BackHandler` unchanged (`playerExpanded → detailStack → moveTaskToBack`) — Library is a tab, not a detail route, so BACK on Library stays in-app |
| Design tokens | Uses only `shared/design/` (`DhunColors`, `DhunSpacing`, `DhunShapes`, `DhunTypography`), `GlassCard`/`ArtworkImage`/`EmptyView`/`DhunButton`/`DhunOutlinedButton`; no raw hex/dp outside tokens |

## Playlists tab specifics

- Observes `PlaylistRepository.observePlaylists()` (ordered `updatedAt DESC` from SQL) — mirrors desktop/Android DB.
- `PlaylistRow` — `GlassCard` 56 dp placeholder ♫, name + `"N tracks • updated <relative>"` (`relativeBrief` via `LibraryViewModel.relativeTimeLabel` + `EpochClock.System.nowMs()`), `▶` play button (`onPlayPlaylist` → `prepareQueue` first value from `observeTracks`).
- `CreatePlaylistDialog` — `OutlinedTextField` + `DhunButton` (“Creating…” spinner state) → `playlistRepository.create` → dialog closes; flow then emits new playlist.
- Empty: `EmptyView("No playlists", "Create one…", actionLabel "New playlist")`.

## Favorites tab specifics

- Observes `LibraryRepository.observeFavorites()` (SQL `ORDER BY addedAt DESC` — newest heart on top).
- Swipe threshold 35% width (from `ReorderableList`), reveals “Remove” layer; `onSwipeRemove` → `library.removeFavorite`.
- Tap plays the favorite in-place as the favorite queue (Phase 10 spec: “tap plays favorites as queue”); next/prev traverse the favorites list, not the prior queue.
- Favorite-ids also exposed as `favoriteIds` for overflow heart state elsewhere (no duplicate favorite button in Library — single `⋮` overflow remains for “Add to playlist / Go to artist/album” via shell).

## History tab specifics

- Observes `HistoryRepository.observeHistory(300)` (SQL `ORDER BY playedAt DESC LIMIT ?` — newest first).
- Grouping: `groupByDay(entries, offsetMs)` → `HistoryDay(dayStartEpochMs, entries)` sorted `dayStart DESC` — same algorithm the domain tests cover. UTC offset supplied by UI (`currentUtcOffsetMs()`), refreshed every minute and on zone change.
- Relative: `relativeTimeLabel` uses integer buckets (<1 m → “just now”, <60 m → “Nm ago”, <24 h → “Nh ago”, <7 d → “Nd ago”, else “Nd/mo ago”) — no date library, stable in commonMain.
- Day header: `dayHeaderLabel` — compares `dayStartMs` to `todayStart` (today at local midnight) → “Today”/“Yesterday” else ISO via `civil_from_days` (Howard Hinnant, no `java.time`).
- Entry: `HistoryEntry(track, playedAtEpochMs, playedFromContext, completedPlayback, entryId)` — row text includes all three (`artist • relative • CONTEXT • completed`).
- Removal: `combinedClickable(onLongClick = remove)` + dedicated `✕` 32 dp button for mouse/desktop; `entryId` is the `History.id` (SQLite AUTOINCREMENT). Clear: top `Clear all` → `ClearHistoryConfirmDialog` → `history.clear()` (SQL `DELETE FROM History`).

## Unit tests (jvmTest, no network)

- `presentation/LibraryViewModelTest.kt` — 7 tests, all green in CI `33841726541` (2m41s):
  * `libraryTabsAndPlaylistsObserved` — tab switching + playlist flow emits newly created `LocalPlaylist`
  * `favoritesRoundTripAndSwipeRemove` — `addFavorite` ×3 → flow size 3, `playFavorites` queues 3, `removeFavorite` → size 2, `toggleFavorite` add/remove round-trip
  * `historyGroupedByDayAndClearWorks` — synthetic `HistoryEntry` grouping (day 10 ×2 + day 9 ×1 → 2 groups), real `recordPlay` via `HistoryRepository` → `removeHistoryEntry(entryId)` → `clearHistory()` → empty flows
  * `historyPlaybackQueuesCorrectly` — `recordPlay` ×3 → `playHistoryDay` + `playHistoryEntry` queue handoffs, offset change recomputes `groupedHistory`
  * `relativeAndHeaderHelpers` — `relativeTimeLabel` buckets + `dayHeaderLabel` Today/Yesterday/ISO regex
  * `playlistPlayFires` — `create` + `addTrack` ×2 → `playPlaylist(id, 1)` → `FakePlayer.lastPrepared == [p1,p2]` + `lastIndex == 1`
  * `emptyStatesStillExposeFlows` — empty DB → all flows empty → `playFavorites`/`playHistoryDay` no-ops (0 `prepareCalls`)

## On-hardware checklist (OPEN)

- [ ] **Install**: `./gradlew :app-android:assembleDebug` or `:app-desktop:run` — bottom bar now shows 4 tabs (Home/Search/**Library**/Catalog) — Library shows header “Your library” + 3 pill tabs
- [ ] **Playlists** — create 2 local playlists via Library→Playlists→“+ New playlist” (or via any track overflow → Add to playlist → New), add 2 tracks each via overflow, verify Library list shows 2 rows with correct track counts + “updated just now”, tap row → navigates to `PlaylistScreen` (local), play from Library row header `▶` queues that playlist, back → Library still shows
- [ ] **Favorites** — add 3 favorites via Search/Home overflow ♥, go Library→Favorites → 3 rows newest first, tap row 1 → plays favorites as queue (next goes to row 2), swipe row 2 left >35% → removes + flow updates immediately, kill app → reopen → Library→Favorites still 2 (persistence), toggle back via overflow → 3 again
- [ ] **History** — play 5 tracks from different contexts (Search, Home, Artist detail, Album detail, Radio/Related), go Library→History → grouped headers “Today” / “Yesterday” / `YYYY-MM-DD` with relative `just now`/`5m ago`/`2h ago`, long-press entry → removes single row, `✕` button also removes, “Play day” → queues that day’s tracks, tap entry → queues entry’s day-centered queue at that index, `Clear all` → confirm → history empty + `EmptyView` (“No history yet”), favorites/playlists unaffected, kill/relaunch → history still empty (cleared persisted)
- [ ] **RecordPlay contexts** — after the 5 plays, inspect `History` rows’ context labels (visible as `• SEARCH/HOME/ARTIST/ALBUM/PLAYLIST/QUEUE/LIBRARY/HISTORY`) — each should match where the play was started (radio via Related → `QUEUE`)
- [ ] **Empty states** — fresh install (clear data) → Library each tab shows its `EmptyView`: Playlists “No playlists → New playlist”, Favorites “No favorites yet”, History “No history yet” — no spinner, no crash
- [ ] **Desktop mouse** — swipe-remove in Favorites/History works with horizontal drag; `✕` and “Play day” buttons work with click; “Clear all” dialog focus traverses correctly
- [ ] **Back stack** — from Library, BACK does not exit (tabs exit per Android spec — single tab host, BACK parks app via `moveTaskToBack` only when shell decides; Library follows same as Home/Search) — detail pages (playlist detail pushed from Library) pop first

## Known gaps (carry to KNOWN_LIMITATIONS)

- History grouping uses `TimeZone.getDefault().getOffset(now)` (raw+ DST) — correct for the device’s default zone; per-entry zone history (e.g. travel) is not tracked (groups by the current offset at query time — same tradeoff the domain tests document).
- History is capped at 300 most-recent entries in the ViewModel (SQL `LIMIT 300`) — the DB retains all; the cap is a list virtualization choice (600+ entry devices still scroll smoothly — raise if needed).
- Favorites reordering (drag) is a no-op in v1 — `ReorderableList` drag handle is shown but `onMove` is ignored; ordering is `addedAt DESC` (`SqlDelightLibraryRepository`). A future phase can add explicit `favorite_position` if users request manual ordering.
- “Albums/Artists saved” Library tabs are deferred — the Phase 05 schema has `Track.albumId/artistId` but no dedicated `SavedAlbum`/`SavedArtist` tables; those tabs will be added when `LIBRARY` gains typed saves (listed in `KNOWN_LIMITATIONS.md` as “Albums/Artists saved: not in v1”).
- History “completed” flag (≥90% playback) is set by `NowPlayingPersistence` — the Library row annotates it but does not expose a separate filter (e.g. “completed only”) — intentionally minimal for v1.

## Screenshots (to capture on hardware)

- Library header + 3 pill tabs (Playlists selected) + empty / populated
- Favorites populated (3 tracks) + swipe-reveal + `Play all` affordance
- History grouped “Today” (3 entries) + “Yesterday” (1) with relative timestamps + `Clear all` dialog
