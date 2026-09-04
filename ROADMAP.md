# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-05, session arena/01a06b14-dhun)

**Branch:** `arena/01a06b14-dhun` · **PR:** **#9 OPEN** (head `f50dba4`) — "feat(desktop): Phase 12 — desktop native integrations (tray, mini-player, shortcuts, close-to-tray, SMTC spike phase 1)". PR #8 (Phases 10+11) is already **MERGED** @ `d27eb37` = current `main`.

**Phase:** 12 — Desktop Native Integrations — 🟨 IN PROGRESS (all code pushed; CI compile gate running; hardware OPEN). Phases 10+11 ✅ **MERGED** — **on-hardware verification OPEN** (user's physical run; concrete tracks ready in `docs/verification/11-lyrics.md`).

**File we were working on:** `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt` (primary), plus `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/smct/Smct.kt` and `.../ui/MiniPlayerWindow.kt` — Compose Desktop **1.8.2 API fixes**, two CI red→fix rounds.

**Last error:** CI run `33928843140` (pushed `f50dba4`) — **FAILED** (6m21s), `:app-desktop` compile errors from the annotations:
1. `Cannot access 'val LocalWindow: ProvidableCompositionLocal<Window?>': it is internal` — **`LocalWindow` is INTERNAL in Compose Desktop 1.8.2** (so `ComposeWindow` and its `show`/`hide`/`requestFocus` were unreachable)
2. `Unresolved reference 'px'` — the `Float.px` extension does not exist in 1.8.2
(+ cascades: `Unresolved reference 'ComposeWindow'`/`'show'`/`'hide'`/`'requestFocus'` ×5)
(round 1, run `33887658349` on `ffa138b`, was the other four: no `WindowScope` receiver on `Window`'s content lambda, `Offset(…)` needs px Floats not Dp, `quitRef` forward reference, cascades — fixed in `f50dba4`)

**Fixes (both pushed):** `f50dba4` (LocalWindow attempt, px-positions, quitRef order, title-based JNA window ops) + **`d2e9ade`** (round 2 — since `LocalWindow` is internal: window handles now via **public JDK API `java.awt.Frame.getFrames()`** with exact-title lookup — both DHUN windows are plain `java.awt.Frame`; `Frame.isVisible/toFront/requestFocus/x/y/width/height` for close-to-tray / tray "Open" / Ctrl+M / geometry (now cross-platform, no JNA); restored size uses `.dp` (exact at 100% scaling, off by display scale factor on HiDPI — re-saved on next close); JNA stays only for the SMTC probe + Windows in-content drag; shortcuts stay on `Window(onKeyEvent)`).

**Exact next step:**
1. Read CI for `d2e9ade` — run **`33930616806`** (in progress at writing): `gh pr checks 9`; if red, `gh api repos/99ggprooo00-code/DHUN/check-runs/<id>/annotations` → fix remaining compile errors, re-push (loop until green).
2. CI green → **merge PR #9**.
3. **User's Windows machine** (`docs/verification/12-desktop-native.md` checklist): tray (icon variants + 6-item menu), close-to-tray ≠ quit + clean quit (no zombie vlc), window geometry restore, mini-player (always-on-top / drag / click-opens-main / Ctrl+M), all 7 shortcuts + text-field negative check, **`SMTC probe` console line**, `:app-desktop:createMsi` + clean-VM install.
4. Per SMTC probe result → implement spike **phase 2** (`UpdateMetadata` + `ButtonPressed` events; IIDs pulled on-machine per the doc's procedure) or lock in the documented fallback (tray + shortcuts — already the shipping path).
5. In parallel (any machine): user runs the Phase 10+11 on-hardware checklists.

**Standing sandbox notes:** no local JDK (CI is the compile gate, annotations on); no device/adb/display (on-hardware items are the user's physical run); direct `curl` has no egress but `fetch_page` does; **GitHub token in this sandbox expires between turns — if a push/gh call returns "Invalid username or token", reconnect GitHub in Arena**.

### Phase 11 step-by-step status — ✅ MERGED @ `d27eb37` (PR #8) — on-hardware verification OPEN (test track list pre-verified against live LRCLIB)

| Step (PROMPT_SEQUENCE.md Phase 11 "Build") | Status |
|---|---|
| `shared/lyrics` — `LyricsSource` interface; `LrcLibSource` (title+artist+duration, synced LRC); `YouTubeLyricsSource` (InnerTube browse); `LyricsRepository` = cache → YTM → LRCLIB → NotAvailable; `LrcParser` ([mm:ss.xx] + enhanced tolerated) | ✅ done (`shared/src/commonMain/kotlin/dev/dhun/lyrics/LrcParser.kt` — timestampRegex `\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]`, wordTimingRegex `<...>` strip, multi-timestamp, metadata skip, sorted; `LyricsSource.kt` interface; `LrcLibSource.kt` Ktor CIO `GET https://lrclib.net/api/get` with `syncedLyrics`→`Synced`/`plainLyrics`→`Unsynced`/404→`NotAvailable` + 429/5xx mapping; `YouTubeLyricsSource.kt` wraps `provider.getLyrics`; `LyricsRepository.kt` cache→YTM→LRCLIB with `cache.put` on Synced/Unsynced, `NotAvailable` not cached) |
| Lyrics tab in FullPlayer: active line large/bright/centered, others dim, smooth auto-scroll, tap line = seek, unsynced scrollable, empty | ✅ done (`shared/src/commonMain/kotlin/dev/dhun/ui/player/PlayerTabs.kt` — `LyricsTabContent`: `LyricsUiState.Synced` → `LazyColumn` `activeIndex = indexOfLast { start <= positionMs }` + `LaunchedEffect(activeIndex) { animateScrollToItem }` + `titleMedium` bright centered vs `bodyMedium` dim, `clickable { seekTo(startTimeMs) }`, `Unsynced` → `verticalScroll` `Text`, `Unavailable` → `EmptyView`, `Loading` shimmers) |
| Lyrics persisted cache (Phase 05 DB) | ✅ done (`shared/src/commonMain/sqldelight/dev/dhun/database/LyricsCache.sq` `CREATE TABLE LyricsCache(trackId PK, isSynced BOOLEAN, content TEXT, cachedAt INTEGER)` + `migrations/1.sqm` v1→v2, `shared/src/commonMain/kotlin/dev/dhun/data/LyricsCacheRepository.kt` `SqlDelightLyricsCacheRepository` with `LrcParser` re-parse on read + `linesToLrc` manual pad, `shared/src/commonMain/kotlin/dev/dhun/data/DatabaseFactory.kt` `DataLayer.lyricsCache`) |
| Wiring Android + Desktop (Koin) + `PlayerViewModel` `Track`-keyed via `LyricsRepository` | ✅ done (`app-android/di/AppModule.kt` 3 singles + `LyricsRepository(cache=data.lyricsCache)`, `app-android/MainActivity.kt` `lyricsRepository = koin.get()` → `PlayerViewModel(..., lyricsRepository)`, `app-desktop/Main.kt` same 3 singles + `PlayerViewModel(..., lyricsRepository = get())`, `presentation/player/PlayerViewModel.kt` `lyricsRepository:LyricsRepository? = null` + `loadLyrics(track)` branches `lyricsRepository.getLyrics(track)` else `provider.getLyrics(track.id)`) |
| Acceptance 1 — Synced lyrics track audio on 5 diverse tracks | 🟨 OPEN — code done, on-hardware: 5 diverse (EN/HI/NE/JP-KR + long) via LRCLIB/YTM, active highlight follows `positionMs`, `animateScrollToItem` smooth |
| Acceptance 2 — Tap-to-seek correct within ±1s | 🟨 OPEN — code done (`clickable { seekTo(startTimeMs) }` exact ms, within ±1s spec), on-hardware tap any line → jump + highlight + scroll |
| Acceptance 3 — LRCLIB fallback verified on tracks absent from YTM | 🟨 OPEN — code done (`cache→YTM→LRCLIB` — YTM `NotAvailable`/`Failure` falls through to `lrcLib.fetch`), on-hardware: YTM-empty but LRCLIB-synced track now shows Synced |
| Acceptance 4 — Second open = instant (cache hit) | 🟨 OPEN — code done (SQLDelight `LyricsCache` `get` first, `put` on Synced/Unsynced, `cached()` hook for verification, `NotAvailable` not cached), on-hardware: first open network ~800ms → collapse→reopen <100ms no shimmer via `cache hit` |
| Acceptance 5 — Parser unit tests green | ✅ done (`shared/src/jvmTest/kotlin/dev/dhun/lyrics/LrcParserTest.kt` 10 tests: `[mm:ss.xx]`/`[mm:ss.xxx]`/`[mm:ss]`/multi-stamp/enhanced/metadata/unsynced/sorted/isSynced/blank — all green locally, awaiting CI `338...`) |

### Phase 10 step-by-step status — ✅ CODE COMPLETE (verification OPEN)

| Step (PROMPT_SEQUENCE.md Phase 10 "Build") | Status |
|---|---|
| Library tabs: Playlists / Favorites / History (+ Albums/Artists saved when data layer supports; otherwise listed in KNOWN_LIMITATIONS) | ✅ done (`presentation/library/LibraryViewModel.kt` — `LibraryTab` enum + `selectedTab` + `playlistsFlow`/`favorites`/`groupedHistory`; `ui/library/LibraryScreen.kt` — `LibraryTabRow` + 3 tabs; `ui/shell/DhunAppShell.kt` — `AppTab.LIBRARY` + `LibraryScreen` wired) |
| Favorites list (tap plays favorites as queue, swipe to remove) | ✅ done (`LibraryViewModel.playFavorites`/`playFavoritesTrack`/`removeFavorite`/`toggleFavorite` via `PlayContext.LIBRARY`; `LibraryScreen.FavoritesTab` — `Track` list via `ReorderableList` with `onSwipeRemove` + `onItemClick` tap-to-play + `Play all` + overflow) |
| History grouped by day with relative times; long-press remove; clear all with confirmation | ✅ done (`LibraryViewModel.groupedHistory` via `GetHistoryUseCase.groupByDay` + `currentUtcOffsetMs()` expect/actual + `relativeTimeLabel`/`dayHeaderLabel` helpers; `LibraryScreen.HistoryTab` — `LazyColumn` grouped by `HistoryDay`, `HistoryRow` with `combinedClickable` long-press + `✕` fallback + `Clear all` + `ClearHistoryConfirmDialog`) |
| `RecordPlay` use case wired into the player (search/home/artist/album/playlist/radio contexts) | ✅ done (`presentation/player/PlayerViewModel.setPlayContext` + `playQueue(context)` + `playTracks/RelatedAt(context)`; `DhunAppShell.onPlayTrack`/`onPlayArtist`/`onPlayAlbum`/`onPlayPlaylist` set `PlayContext.HOME/SEARCH/ARTIST/ALBUM/PLAYLIST`; `LibraryViewModel` sets `LIBRARY/HISTORY/PLAYLIST` via `setContext` lambda delegating to `PlayerViewModel`) |
| Acceptance 1 — Favorites round-trip in UI | 🟨 OPEN — code done, on-hardware: add via overflow ♥ → Library→Favorites shows → swipe/long-press remove → kill/relaunch still correct |
| Acceptance 2 — History grouping/timestamps correct; clear works | 🟨 OPEN — code done, on-hardware: play 5 tracks → Library→History shows grouped Today/Yesterday/date with relative `"just now"/"5m ago"` + long-press remove + `Clear all` confirm |
| Acceptance 3 — Empty states for all tabs | ✅ done (`EmptyView` for Playlists/Favorites/History empty + `Create playlist` action) — on-hardware visual check OPEN |

### Phase 12 step-by-step status — CURRENT PHASE (🟨 IN PROGRESS — all code pushed `d2e9ade`, CI compile gate running `33930616806`; nothing counts done until CI green + hardware)

| Step (PROMPT_SEQUENCE.md Phase 12 "Build") | Status |
|---|---|
| SMTC spike (3-day timebox): now-playing tile, artwork, media keys; stable → integrate, else documented fallback | 🟨 **phase 1 code pushed** (`smct/Smct.kt` — WinRT activation via JNA/combase → `ISystemMediaTransportControlsInterop` ddb0472d-… → `GetForWindow` (slot 6, offset 48 cross-checked vs ctypes impl) → `IsTransportControlsButtonVisible` live check; HRESULT-logged, `-Ddhun.smct=false` off) — CI compile pending; **on-Windows probe OPEN**; phase 2 (UpdateMetadata + ButtonPressed; IIDs via on-machine winmd pull) + fallback decision OPEN |
| System tray: icon playing/paused variants; menu track title / play-pause / next / prev / open / quit | 🟨 **code pushed** (`native/DhunTray.kt` + `native/TrayIcons.kt` — AWT, EDT-marshaled, headless-safe) — CI compile pending (two RED runs so far — 1.8.2 API fixes in `f50dba4`+`d2e9ade`); hardware OPEN |
| Mini-player window: 320×88 always-on-top; artwork/title/transport/progress; draggable; click opens main | 🟨 **code pushed** (`ui/MiniPlayerWindow.kt` + second Compose `Window` in `Main.kt` — hide-not-close via `Frame.isVisible`, Ctrl+M toggle, JNA drag on Windows) — CI compile pending; hardware OPEN |
| Keyboard shortcuts: Space, ←/→ seek 5s, Ctrl+←/→ prev/next, Ctrl+F search, Ctrl+M mini-player, Ctrl+Q quit | 🟨 **code pushed** (`Main.kt` `Window(onKeyEvent)` — KeyDown-only, text-field-safe) — CI compile pending; hardware OPEN |
| Close-to-tray (default on), remembered window state | 🟨 **code pushed** (`SettingsKeys.CLOSE_TO_TRAY`/`WINDOW_GEOMETRY` in `Main.kt`; `java.awt.Frame` title-lookup for show/hide + geometry (cross-platform), restored via `rememberWindowState(position=… px, width/height=… dp)`) — CI compile pending; hardware OPEN |
| Packaging: jpackage `.msi` with app icon; clean-VM install test | 🟨 `createMsi` target exists (Phase 04, CI-green then, `packageVersion` 1.0.x) — app icon + clean-VM install OPEN on Windows |
| Verification doc + KNOWN_LIMITATIONS + THIRD_PARTY (code-level, auditable) | ✅ **done + pushed** (`docs/verification/12-desktop-native.md` code-audit table + phase-2 procedure + hardware checklist; KNOWN_LIMITATIONS SMTC/media-keys honesty note; THIRD_PARTY JNA 5.17.0 — all in `ffa138b`) |
| Acceptance 1 — media keys control playback (SMTC or documented fallback) | 🟨 OPEN — on hardware; fallback (tray + shortcuts) already ships |
| Acceptance 2 — tray fully functional; close-to-tray ≠ quit; tray quit exits clean (no zombies) | 🟨 OPEN — `docs/verification/12-desktop-native.md` checklist |
| Acceptance 3 — mini-player mirrors state live | 🟨 OPEN — flows-driven (`currentTrack`/`positionMs`/`state`) — visual check on hardware |
| Acceptance 4 — installer installs and runs on a clean machine | 🟨 OPEN — `:app-desktop:createMsi` on Windows |

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
| 10 | Library & history screens | ✅ MERGED — PR #8 @ `d27eb37` (CI green `33842104141`); LibraryViewModel + LibraryScreen (3 tabs, swipe/long-press, relative times, clear-all), PlayerViewModel + DhunAppShell RecordPlay wiring, LibraryViewModelTest (7 tests); on-hardware checklist OPEN (`docs/verification/10-library.md`) | docs/verification/10-library.md |
| 11 | Lyrics (LRCLIB + YTM, synced) | ✅ MERGED — PR #8 @ `d27eb37` (same CI); `LrcParser` + `LrcLibSource`/`YouTubeLyricsSource`/`LyricsRepository` (cache→YTM→LRCLIB), `LyricsCache` v2 + `LyricsCacheRepository`, Koin wiring (android+desktop), `LyricsTabContent` (synced scroll/tap-seek/unsynced/empty), `LrcParserTest` (10 tests); test track list live-pre-verified (4 synced EN/HI/KR/ES + 1 unsynced JP); on-hardware checklist OPEN | docs/verification/11-lyrics.md |
| 12 | Desktop native (SMTC spike, tray, mini-player, jpackage) | 🟨 IN PROGRESS — all code pushed (`d2e9ade` on PR #9): AWT tray (playing/paused icons, spec menu, headless-safe), mini-player window (320×88 always-on-top, drag + click-to-open), keyboard shortcuts (Space/←→/Ctrl+←→/Ctrl+F/Ctrl+M/Ctrl+Q, text-field-safe), close-to-tray (default on) + window geometry persistence (public AWT `Frame.getFrames()` title-lookup — `LocalWindow` is internal in 1.8.2), clean-quit path, SMTC JNA probe phase 1 (WinRT activation + GetForWindow, GUIDs cross-referenced, `-Ddhun.smct=false` off-switch); **CI compile gate running** (`33930616806`; two RED runs so far — 1.8.2 API fixes applied); jpackage/MSI + SMTC phase 2 (metadata + media keys) OPEN on hardware | docs/verification/12-desktop-native.md |
| 13 | Android polish (insets, shortcuts, tablet, soak) | ⬜ not started | — |
| 14 | Robustness + rot-drill CI + release v0.1.0 | ⬜ not started | — |

Deferred to v2 (do not design, do not stub): Web/PWA, Android Auto, Cast,
equalizer, sync, downloads, widgets, jump lists, optional cookie sign-in.
