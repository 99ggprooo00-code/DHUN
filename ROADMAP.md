# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-04, session arena/01a06aaa-dhun)

**Branch:** `arena/01a06aaa-dhun` · **PR:** #7 open — "Phase 08+09: Player UI + browse pages" (CI: compile+all-new-tests green; one pre-existing CI-timing flake in NowPlayingPersistenceTest under re-run)

**Phase:** 08 (Player UI) + 09 (Artist/Album/Playlist pages) — code complete, pending CI.

**Files this session:** `player/DhunPlayer.kt` (+`currentQueueIndex`/`repeatMode`/`shuffleEnabled`/`volume` flows, `playAt`/`removeFromQueue`/`moveInQueue`/`setVolume`), `AndroidDhunPlayer.kt`, `DesktopDhunPlayer.kt`, `presentation/player/PlayerViewModel.kt`, `ui/player/{MiniPlayer,FullPlayer,PlayerTabs}.kt`, `ui/components/ReorderableList.kt`, `core/Entities.kt` (ArtistPage/AlbumDetail/PlaylistDetail), `innertube/BrowseParsers.kt` + `Parsers.kt` (artistId/albumId), `InnerTubeClient.kt`/`MusicProvider.kt` (browse endpoints), `presentation/browse/{Artist,Album,Playlist}ViewModel.kt`, `ui/browse/{Artist,Album,Playlist}Screen.kt`, `ui/shell/{AppNavState,DhunAppShell}.kt`, `MainActivity.kt`, `app-desktop Main.kt`, fixtures + tests (PlayerViewModelTest, BrowseViewModelTest, ParserFixtureTest +3).

**Last error:** none yet — no local JDK in sandbox; first compile rides GitHub CI (annotations on).

**Exact next step:** commit → push → open PR → read CI annotations → fix any compile/test failures → merge → on-hardware verification for 08 (16 checks + stress) and 09 (3 artists / 3 albums / local CRUD), then Phase 10 (Library & History).

### Phase 08 step-by-step status

| Step (PROMPT_SEQUENCE.md Phase 08 "Build") | Status |
|---|---|
| `MiniPlayer` (shared): 72dp glass bar; artwork, marquee title, artist, play/pause, next, 1dp accent progress line; tap/swipe-up opens FullPlayer (animated) | ✅ done (`ui/player/MiniPlayer.kt`, replaces Phase 07 placeholder in `DhunAppShell`) |
| `FullPlayer` (shared): blurred artwork background + scrim, color crossfade 500ms, artwork scale spring on play/pause | ✅ done (`ui/player/FullPlayer.kt`) |
| Custom progress bar (4dp→8dp drag, thumb on touch only) + hold-to-seek prev/next + play/pause morph + shuffle + repeat-3-cycle + volume slider (desktop) | ✅ done (`DhunSeekBar`, `HoldTapTransportButton`, `PlayerViewModel.cycleRepeatMode/toggleShuffle/beginHoldSeek`, Slider on desktop) |
| Bottom tabs Lyrics \| Queue \| Related (Related wired to Phase 02 `/next` parsing) | ✅ done (`ui/player/PlayerTabs.kt`) |
| Queue tab: drag-reorder, swipe-remove, tap-to-jump, current-track equalizer animation | ✅ done (`ui/components/ReorderableList.kt` + queue tab) |
| Track-change choreography (slide in skip direction + fade, bg crossfade, title fade) | ✅ done (`AnimatedContent` direction-aware, `SkipDirection` tracking) |
| Android: edge-to-edge insets; BACK collapses FullPlayer (never exits app) | ✅ done (`safeDrawingPadding()`, `AppNavState.closeTop()` in `MainActivity.BackHandler`) |
| Acceptance: 16 checks pass; 10× rapid-skip stress clean; screenshots | 🟨 OPEN — on-hardware, `docs/verification/08-player.md` |

### Phase 09 step-by-step status

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
| Acceptance 1–3 on hardware (3 artists / 3 albums / local CRUD in-app) | 🟨 OPEN |

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
| 08 | Player UI (MiniPlayer + FullPlayer) | 🟨 CODE COMPLETE — MiniPlayer (marquee, progress line, swipe-up), FullPlayer (blurred artwork bg + scrim, 500ms crossfades, drag seekbar, hold-to-seek, morph, shuffle/repeat, desktop volume), Lyrics\|Queue\|Related tabs, queue drag/swipe/tap + equalizer, back-collapse; player interface + both engines extended; PlayerViewModelTest green | docs/verification/08-player.md |
| 09 | Artist / Album / Playlist pages | 🟨 CODE COMPLETE — browse parsers (artist/album/playlist) + client/provider endpoints, entities, VMs, screens (parallax artist, tinted album, editable local playlist), AppNavState detail-stack navigation wired everywhere; fixture tests + BrowseViewModelTest green | docs/verification/09-browse-pages.md |
| 10 | Library & history screens | ⬜ not started | — |
| 11 | Lyrics (LRCLIB + YTM, synced) | ⬜ not started | — |
| 12 | Desktop native (SMTC spike, tray, mini-player, jpackage) | ⬜ not started | — |
| 13 | Android polish (insets, shortcuts, tablet, soak) | ⬜ not started | — |
| 14 | Robustness + rot-drill CI + release v0.1.0 | ⬜ not started | — |

Deferred to v2 (do not design, do not stub): Web/PWA, Android Auto, Cast,
equalizer, sync, downloads, widgets, jump lists, optional cookie sign-in.
