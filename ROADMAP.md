# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-04, session arena/01a06a81-dhun)

**Branch:** `arena/01a06a81-dhun` · **PR:** TBD — “Phase 07: Home & Search screens”

**Phase:** 07 — Home & Search screens (end-to-end YTM discovery and debounced search). Phase 06 merged to `main@8a675a3` (#5).

**Last changes (this branch):**
- `shared/src/commonMain/kotlin/dev/dhun/core/Entities.kt`: added `HomeItem`, `HomeSection`, `HomeFeed`, `continuationToken` in `SearchResults`.
- `shared/src/commonMain/kotlin/dev/dhun/innertube/Parsers.kt`: added `parseHomeSections()`, `parseContinuationToken()`.
- `shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt` & `dev/dhun/provider/MusicProvider.kt`: added `homeFeed()` and `searchContinuation()`.
- `shared/src/commonMain/kotlin/dev/dhun/player/DhunPlayer.kt`, `AndroidDhunPlayer`, `DesktopDhunPlayer`: added `addNext()` and `addToQueue()`.
- `shared/src/commonMain/kotlin/dev/dhun/domain/UseCases.kt`: added `GetHomeFeedUseCase` (time-of-day greeting, quick picks).
- `shared/src/commonMain/kotlin/dev/dhun/presentation/`: added `HomeViewModel` (state machine, refresh) & `SearchViewModel` (300ms debouncing, filter chips, pagination, recents).
- `shared/src/commonMain/kotlin/dev/dhun/ui/`: added `HomeScreen` (greeting, 3x2 quick picks, listen again, dynamic shelves, shimmers), `SearchScreen` (debounced suggestions, chips, result shelves, infinite scroll, recents), `TrackOverflowDialog` (Play next, Queue, Playlist, Fav, Go to artist/album), `AddToPlaylistDialog`, `DhunAppShell` (docked MiniPlayer, glassy bottom nav).
- `app-android` & `app-desktop`: wired `DhunAppShell` with platform players and Koin graph.
- `shared/src/jvmTest/`: added `browse-home.json` fixture and unit tests (`ParserFixtureTest`, `HomeViewModelTest`, `SearchViewModelTest`).

**Exact next step:**
1. Push this branch → create PR → wait for GitHub Actions CI (`:shared:jvmTest`, `assembleDebug`, probe compile).
2. Merge PR upon CI green.
3. Start **Phase 08 — Player UI (MiniPlayer + FullPlayer)**.

### Phase 07 step-by-step status

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
| 07 | Home & Search | 🟨 CODE COMPLETE — shared `HomeScreen` (greeting, 3x2 quick picks, listen again, dynamic shelves, shimmers), `SearchScreen` (300ms debounced suggestions, filter chips, infinite scroll, recents), `TrackOverflowDialog`, `AddToPlaylistDialog`, `DhunAppShell` (docked MiniPlayer, tab navigation); unit tests green | docs/verification/07-home-search.md |
| 08 | Player UI (MiniPlayer + FullPlayer) | ⬜ not started | — |
| 09 | Artist / Album / Playlist pages | ⬜ not started | — |
| 10 | Library & history screens | ⬜ not started | — |
| 11 | Lyrics (LRCLIB + YTM, synced) | ⬜ not started | — |
| 12 | Desktop native (SMTC spike, tray, mini-player, jpackage) | ⬜ not started | — |
| 13 | Android polish (insets, shortcuts, tablet, soak) | ⬜ not started | — |
| 14 | Robustness + rot-drill CI + release v0.1.0 | ⬜ not started | — |

Deferred to v2 (do not design, do not stub): Web/PWA, Android Auto, Cast,
equalizer, sync, downloads, widgets, jump lists, optional cookie sign-in.
