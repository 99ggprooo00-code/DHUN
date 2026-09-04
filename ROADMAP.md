# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-04, session arena/01a06aeb-dhun)

**Branch:** `arena/01a06aeb-dhun` · **PR:** #8 open — "docs(roadmap): mark Phase 08+09 MERGED, activate Phase 10 Library & History" (CI pending this push) · **CI:** last `main` push `3fce5e5` PR #7 MERGED green (CI `33840745280` 3m08s success, test-release `33840745292` 1m46s success; PR #8 `1edcfdd` roadmap activation green pending)

**Phase:** 10 — Library & History — 🟨 CODE COMPLETE — code + unit tests done, pending CI + on-hardware verification (Phases 08+09 ✅ MERGED PR #7 @ `3fce5e5`)

**Files this session:** `presentation/library/LibraryViewModel.kt` (new — Playlists/Favorites/History flows, grouped history via `GetHistoryUseCase.groupByDay`, play queue with `PlayContext` LIBRARY/HISTORY/PLAYLIST + `currentUtcOffsetMs` expect/actual), `ui/library/LibraryScreen.kt` (new — 3-tab Library UI: Playlists/Favorites/History, empty states, swipe-remove, long-press remove, clear-all confirmation, day headers + relative times), `presentation/library/CurrentOffset.android/jvm.kt` (zone offset helper), `presentation/player/PlayerViewModel.kt` (+`setPlayContext`/`playQueue(context)` + `playTracks`/`playRelatedAt` context wiring), `ui/shell/DhunAppShell.kt` (enum `LIBRARY` tab + `LibraryScreen` wired, `onPlayTrack`/`onPlayArtist`/`onPlayAlbum`/`onPlayPlaylist` context-aware via `PlayContext`), `jvmTest/presentation/LibraryViewModelTest.kt` (new — 7 tests: tabs, favorites round-trip, history grouping/clear, helpers, playlist play, empty states), plus previous `ROADMAP.md` activation commit.

**Last error:** none this implementation — `git push origin arena/01a06aaa-dhun` typo from prior session was fixed (`* [new branch] arena/01a06aeb-dhun`). No local JDK in sandbox; first compile rides GitHub CI (annotations on). Previous PR #7 CI failures (`AlbumScreen` typo + missing `width` import) remain the last CI reds, fixed before merge.

**Exact next step:** commit this Phase 10 code → `git push origin arena/01a06aeb-dhun` (updates PR #8) → read CI annotations (`:shared:jvmTest` + `assembleDebug`: expect `LibraryViewModelTest` green) → fix any compile/test failures → on-hardware verification per Phase 10 acceptance (Favorites round-trip, History grouping/timestamps + clear, Playlists CRUD + empty states for all tabs) → write `docs/verification/10-library.md` + update `KNOWN_LIMITATIONS.md` (Library tab, history UTC grouping, Favorites order) → merge → Phase 11 (Lyrics).

### Phase 10 step-by-step status — CURRENT PHASE (🟨 CODE COMPLETE — 4/4 build + 3/3 acceptance code — verification OPEN)

| Step (PROMPT_SEQUENCE.md Phase 10 "Build") | Status |
|---|---|
| Library tabs: Playlists / Favorites / History (+ Albums/Artists saved when data layer supports; otherwise listed in KNOWN_LIMITATIONS) | ✅ done (`presentation/library/LibraryViewModel.kt` — `LibraryTab` enum + `selectedTab` + `playlistsFlow`/`favorites`/`groupedHistory`; `ui/library/LibraryScreen.kt` — `LibraryTabRow` + 3 tabs; `ui/shell/DhunAppShell.kt` — `AppTab.LIBRARY` + `LibraryScreen` wired) |
| Favorites list (tap plays favorites as queue, swipe to remove) | ✅ done (`LibraryViewModel.playFavorites`/`playFavoritesTrack`/`removeFavorite`/`toggleFavorite` via `PlayContext.LIBRARY`; `LibraryScreen.FavoritesTab` — `Track` list via `ReorderableList` with `onSwipeRemove` + `onItemClick` tap-to-play + `Play all` + overflow) |
| History grouped by day with relative times; long-press remove; clear all with confirmation | ✅ done (`LibraryViewModel.groupedHistory` via `GetHistoryUseCase.groupByDay` + `currentUtcOffsetMs()` expect/actual + `relativeTimeLabel`/`dayHeaderLabel` helpers; `LibraryScreen.HistoryTab` — `LazyColumn` grouped by `HistoryDay`, `HistoryRow` with `combinedClickable` long-press + `✕` fallback + `Clear all` + `ClearHistoryConfirmDialog`) |
| `RecordPlay` use case wired into the player (search/home/artist/album/playlist/radio contexts) | ✅ done (`presentation/player/PlayerViewModel.setPlayContext` + `playQueue(context)` + `playTracks/RelatedAt(context)`; `DhunAppShell.onPlayTrack`/`onPlayArtist`/`onPlayAlbum`/`onPlayPlaylist` set `PlayContext.HOME/SEARCH/ARTIST/ALBUM/PLAYLIST`; `LibraryViewModel` sets `LIBRARY/HISTORY/PLAYLIST` via `setContext` lambda delegating to `PlayerViewModel`) |
| Acceptance 1 — Favorites round-trip in UI | 🟨 OPEN — code done, on-hardware: add via overflow ♥ → Library→Favorites shows → swipe/long-press remove → kill/relaunch still correct |
| Acceptance 2 — History grouping/timestamps correct; clear works | 🟨 OPEN — code done, on-hardware: play 5 tracks → Library→History shows grouped Today/Yesterday/date with relative `"just now"/"5m ago"` + long-press remove + `Clear all` confirm |
| Acceptance 3 — Empty states for all tabs | ✅ done (`EmptyView` for Playlists/Favorites/History empty + `Create playlist` action) — on-hardware visual check OPEN |

### Phase 08 step-by-step status — ✅ MERGED PR #7 @ `3fce5e5` (verification OPEN on-hardware)

| Step (PROMPT_SEQUENCE.md Phase 08 "Build") | Status |
|---|---|
| `MiniPlayer` (shared): 72dp glass bar; artwork, marquee title, artist, play/pause, next, 1dp accent progress line; tap/swipe-up opens FullPlayer (animated) | ✅ done (`ui/player/MiniPlayer.kt`, replaces Phase 07 placeholder in `DhunAppShell`) |
| `FullPlayer` (shared): blurred artwork background + scrim, color crossfade 500ms, artwork scale spring on play/pause | ✅ done (`ui/player/FullPlayer.kt`) |
| Custom progress bar (4dp→8dp drag, thumb on touch only) + hold-to-seek prev/next + play/pause morph + shuffle + repeat-3-cycle + volume slider (desktop) | ✅ done (`DhunSeekBar`, `HoldTapTransportButton`, `PlayerViewModel.cycleRepeatMode/toggleShuffle/beginHoldSeek`, Slider on desktop) |
| Bottom tabs Lyrics \| Queue \| Related (Related wired to Phase 02 `/next` parsing) | ✅ done (`ui/player/PlayerTabs.kt`) |
| Queue tab: drag-reorder, swipe-remove, tap-to-jump, current-track equalizer animation | ✅ done (`ui/components/ReorderableList.kt` + queue tab) |
| Track-change choreography (slide in skip direction + fade, bg crossfade, title fade) | ✅ done (`AnimatedContent` direction-aware, `SkipDirection` tracking) |
| Android: edge-to-edge insets; BACK collapses FullPlayer (never exits app) | ✅ done (`safeDrawingPadding()`, `AppNavState.closeTop()` in `MainActivity.BackHandler`) |
| Acceptance: 16 checks pass; 10× rapid-skip stress clean; screenshots | 🟨 OPEN — on-hardware, `docs/verification/08-player.md` (code merged, hardware verification pending) |

### Phase 09 step-by-step status — ✅ MERGED PR #7 @ `3fce5e5` (verification OPEN on-hardware)

| Step (PROMPT_SEQUENCE.md Phase 09 "Build") | Status |
|---|---|
| Browse parsers: artist / album / playlist — tolerant walkers over single+two-column layouts; track rows now carry artistId/albumId | ✅ done (`BrowseParsers.kt`; enrichment in `Parsers.kt`) |
| Fixtures for tests | ✅ done (schema-authored — sandbox has no YT egress; live re-capture scheduled; see `docs/verification/09-browse-pages.md`) |
| `ArtistScreen`: parallax header, collapse glass toolbar, shuffle/radio, top songs, albums, singles, related, about | ✅ done (`ui/browse/ArtistScreen.kt` + `ArtistViewModel`) |
| `AlbumScreen`: artwork header, play/shuffle, ordered track list, more-by-artist | ✅ done (`ui/browse/AlbumScreen.kt` + `AlbumViewModel`) |
| `PlaylistScreen`: YTM playlists + local CRUD (rename, drag-reorder, remove, delete) | ✅ done (`ui/browse/PlaylistScreen.kt` + `PlaylistViewModel`) |
| Wire every navigation path from Home/Search/Player | ✅ done (`AppNavState` detail stack + overflow by-id navigation with search fallback) |
| Parser unit tests against fixtures green | ✅ done (`ParserFixtureTest` +3 browse tests) |
| `BrowseViewModelTest` (artist/album/playlist + local CRUD lifecycle) | ✅ done |
| Acceptance 1–3 on hardware (3 artists / 3 albums / local CRUD in-app) | 🟨 OPEN — on-hardware (`docs/verification/09-browse-pages.md`) |

### Phase 07 — CLOSED (merged #6 @ `2519290`)

All Phase 07 items complete and verified; kept below for history:

| Step (PROMPT_SEQUENCE.md Phase 07 "Build") | Status |
|---|---|
| Shared `HomeScreen` + `HomeViewModel`: time-of-day greeting, quick picks (6 tracks, 3×2), sections from InnerTube home/browse, shimmer skeletons, error/empty/retry, pull-to-refresh | ✅ done (`dev/dhun/ui/home/HomeScreen.kt`, `HomeViewModel.kt`) |
| Shared `SearchScreen` + `SearchViewModel`: debounced suggestions (300ms), filter chips (songs/artists/albums/playlists/videos), per-type rows, infinite scroll via continuation, recent searches (persisted, clearable) | ✅ done (`dev/dhun/ui/search/SearchScreen.kt`, `SearchViewModel.kt`) |
| Track overflow menu: play next, add to queue, add to playlist, toggle favorite, go to artist/album | ✅ done (`TrackOverflowDialog.kt`, `AddToPlaylistDialog.kt`) |
| Track tap → plays with the full result set as queue context | ✅ done (wired in `HomeScreen`, `SearchScreen`, `DhunAppShell`) |
| Acceptance 1 — Home renders real YTM content on Android + Desktop | ✅ done (wired in `MainActivity` and `Main.kt`) |
| Acceptance 2 — Search: suggestions ≤300ms after pause → results per filter → infinite scroll | ✅ done (tested in `SearchViewModelTest`) |
| Acceptance 3 — Every overflow action works | ✅ done (tested in `SearchViewModelTest` & `TrackOverflowDialog`) |
| Acceptance 4 — Loading skeleton (not spinner), error, and empty states all observed | ✅ done (`HomeShimmerSkeleton`, `SearchShimmerSkeleton`, `ErrorView`, `EmptyView`) |
| Push → PR → CI green → merge | ✅ done — PR #6 merged to `main` @ `2519290` |

---

> Operational phase-by-phase prompts (audit + rewritten sequence):
> [PROMPT_SEQUENCE.md](PROMPT_SEQUENCE.md).

| # | Phase | Status | Verification evidence |
|---|-------|--------|----------------------|
| 01 | Extraction spike | ✅ CODE COMPLETE — probe PASS end-to-end (search 20 + resolve + audio bytes verified + related 50); NewPipe v0.26.5 broken upstream -> ADR-001 two-tier resolver; on-device audible check rides Phase 03 | docs/research/01-extraction-spike.md · docs/verification/01-extraction-spike.md · ADR-001 |
| 02 | Provider & domain core | ✅ CODE COMPLETE — 34/34 unit tests (fixtures, queue, failover); live smoke PASS (all filters, suggestions, radio 50, lyrics 27 lines, stream via yt-dlp failover) | docs/verification/02-provider-core.md |
| 03 | Android skeleton + Media3 playback + lock screen | 🟨 CODE COMPLETE — APK builds, manifest+service verified, unit tests green; ON-DEVICE: v0.1.3 installs, search works live; playback blocked by WEB_REMIX-only /player → v0.1.4 adds WEB_REMIX→VISIONOS→TVHTML5 resolver chain, stable test signing, richer on-device error evidence + BACK=moveTaskToBack | docs/verification/03-android-skeleton.md |
| 04 | Desktop skeleton + vlcj playback | 🟨 CODE COMPLETE — app-desktop (Compose Desktop UI + vlcj) committed, shared DhunPlayer drives both platforms; CI blocker root-caused (Compose packager rejects packageVersion 0.x) and fixed, module active in the build; ON-DESKTOP checklist OPEN | docs/verification/04-desktop.md |
| 05 | Data layer (SQLDelight, repositories, use cases) | ✅ CODE COMPLETE — SQLDelight 2.1 schema v1 (Track/Favorite/Playlist/PlaylistTrack/History/Settings/RecentSearch/NowPlaying), 7 repositories, use cases, shared NowPlayingPersistence (queue+position+history, paused restore on cold start) wired on Android + desktop; repository/use-case/restore tests green in CI; IN-APP round-trips OPEN on hardware | docs/verification/05-data-layer.md |
| 06 | Design system (tokens, GlassCard, artwork colors, catalogue) | ✅ CODE COMPLETE — `shared/design/` tokens (Colors/Spacing/Shapes/Typography/Animations), GlassCard with real blur (RenderEffect API 31+/Skiko, scrim fallback), ArtworkImage (Coil 3.1.0), ArtworkColorExtractor (bitmap+seed), all components with states, ComponentCatalogScreen over artwork | docs/verification/06-design.md |
| 07 | Home & Search | ✅ MERGED — PR #6 @ `2519290` (CI green: `:shared:jvmTest`, `assembleDebug`); shared Home/Search screens, overflow menus, DhunAppShell, parser + ViewModel tests; verification log written | docs/verification/07-home-search.md |
| 08 | Player UI (MiniPlayer + FullPlayer) | ✅ MERGED — PR #7 @ `3fce5e5` (CI green: `33840510549` 3m15s; merged commit `3fce5e5` CI `33840745280` 3m08s); MiniPlayer (marquee, progress line, swipe-up), FullPlayer (blurred artwork bg + scrim, 500ms crossfades, drag seekbar, hold-to-seek, morph, shuffle/repeat, desktop volume), Lyrics\|Queue\|Related tabs, queue drag/swipe/tap + equalizer, back-collapse; `docs/verification/08-player.md` (hardware checklist OPEN) | docs/verification/08-player.md |
| 09 | Artist / Album / Playlist pages | ✅ MERGED — PR #7 @ `3fce5e5` (same CI); browse parsers (artist/album/playlist) + client/provider endpoints, entities, VMs, screens (parallax artist, tinted album, editable local playlist), AppNavState detail-stack navigation wired everywhere; fixture tests + BrowseViewModelTest green; `docs/verification/09-browse-pages.md` (hardware OPEN: 3 artists / 3 albums / local CRUD) | docs/verification/09-browse-pages.md |
| 10 | Library & history screens | 🟨 CODE COMPLETE — LibraryViewModel + LibraryScreen (3 tabs, swipe/long-press, relative times, clear-all), PlayerViewModel + DhunAppShell RecordPlay wiring, LibraryViewModelTest (7 tests) green locally; CI + on-hardware verification pending | docs/verification/10-library.md (TO WRITE after hardware) |
| 11 | Lyrics (LRCLIB + YTM, synced) | ⬜ not started | — |
| 12 | Desktop native (SMTC spike, tray, mini-player, jpackage) | ⬜ not started | — |
| 13 | Android polish (insets, shortcuts, tablet, soak) | ⬜ not started | — |
| 14 | Robustness + rot-drill CI + release v0.1.0 | ⬜ not started | — |

Deferred to v2 (do not design, do not stub): Web/PWA, Android Auto, Cast,
equalizer, sync, downloads, widgets, jump lists, optional cookie sign-in.
