# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-04, session arena/01a06aaa-dhun)

**Branch:** `arena/01a06aaa-dhun` — working tree clean, HEAD = `2519290` (= merged Phase 07) · **PR:** #6 merged ✅ — no Phase 08 PR open yet

**Phase:** 08 — Player UI (MiniPlayer + FullPlayer). Phase 07 merged to `main@2519290` (#6). Phase 06 merged to `main@8a675a3` (#5).

**Files last worked on (Phase 07 — all merged via #6):**
- `shared/src/commonMain/kotlin/dev/dhun/ui/shell/DhunAppShell.kt` — last file touched pre-merge; contains the Phase 07 placeholder docked MiniPlayer + glassy bottom nav that Phase 08 must replace with the real `MiniPlayer`.
- Supporting (merged): `ui/home/HomeScreen.kt`, `ui/search/SearchScreen.kt`, `presentation/home/HomeViewModel.kt`, `presentation/search/SearchViewModel.kt`, `domain/UseCases.kt` (`GetHomeFeedUseCase`), `ui/components/TrackOverflowDialog.kt`, `AddToPlaylistDialog.kt`.

**Last error:** none this session (boot/status review only; tree clean, no build run yet). Most recent historical failures — fixed before #6 merged:
1. `NowPlayingPersistenceTest.FakePlayer` failed to compile — missing `addNext()`/`addToQueue()` after the `DhunPlayer` interface gained them → implemented in the fake.
2. `HomeViewModelTest` / `SearchViewModelTest` flaked on CI → made deterministic with isolated `CoroutineScope`s + `eventually` polling; `NowPlayingPersistenceTest` timeout/interval tuned.

**Exact next step (Phase 08 kickoff, in order):**
1. `shared/src/commonMain/kotlin/dev/dhun/ui/player/MiniPlayer.kt` (new package) — 72dp glass bar: artwork, marquee title+artist, play/pause, next, 1dp accent progress line, tap/swipe-up → FullPlayer (animated). Wire into `DhunAppShell.kt`, replacing the placeholder bar.
2. `shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt` — full-bleed **blurred artwork background + dark scrim**, 500ms color crossfade on track change, artwork scale spring (playing vs paused), custom progress bar (4dp→8dp on drag, thumb on touch only), prev/next hold-to-seek, animated play/pause morph, shuffle + repeat (3-cycle), volume slider (desktop), bottom tabs **Lyrics | Queue | Related** (Related wired to `/next` parsing from Phase 02).
3. Queue tab: drag-reorder, swipe-remove, tap-to-jump, current track highlighted with equalizer animation.
4. Track-change choreography: artwork slide in skip direction + fade, background color crossfade, title fade-update-fade.
5. Android: edge-to-edge insets correct; BACK from FullPlayer collapses (never exits app). Desktop: same FullPlayer via `app-desktop` `Main.kt`.
6. Verification: `docs/verification/08-player.md` — all 16 visual/interaction checks with screenshots + rapid 10-skip stress test, both platforms → then PR + CI (`:shared:jvmTest`, `assembleDebug`).

### Phase 08 step-by-step status

| Step (PROMPT_SEQUENCE.md Phase 08 "Build") | Status |
|---|---|
| `MiniPlayer` (shared): 72dp glass bar above bottom nav (Android) / docked bottom (desktop); artwork, marquee title, artist, play/pause, next, 1dp accent progress line; tap/swipe-up opens FullPlayer (animated) | ⬜ not started — placeholder bar exists in `ui/shell/DhunAppShell.kt` from Phase 07, to be replaced |
| `FullPlayer` (shared): blurred artwork background + scrim, color crossfade 500ms, artwork scale spring on play/pause | ⬜ not started |
| Custom progress bar (4dp→8dp on drag, thumb on touch only) + hold-to-seek prev/next + animated play/pause morph + shuffle + repeat-3-cycle + volume slider (desktop) | ⬜ not started |
| Bottom tabs Lyrics \| Queue \| Related (Related wired to Phase 02 `/next` parsing) | ⬜ not started |
| Queue tab: drag-reorder, swipe-remove, tap-to-jump, current-track equalizer animation | ⬜ not started |
| Track-change choreography (artwork slide in skip direction + fade, bg crossfade, title fade-update-fade) | ⬜ not started |
| Android: edge-to-edge insets; BACK collapses FullPlayer (never exits app) | ⬜ not started |
| Acceptance: real blurred artwork bg (not a color); 16 checks pass; 10× rapid-skip stress clean; both platforms; screenshots in `docs/verification/08-player.md` | ⬜ not started |

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
| 08 | Player UI (MiniPlayer + FullPlayer) | 🟨 STARTING — active task (see CURRENT ACTIVE TASK above); no code written yet; Phase 07 placeholder MiniPlayer in `DhunAppShell.kt` is the integration point | — |
| 09 | Artist / Album / Playlist pages | ⬜ not started | — |
| 10 | Library & history screens | ⬜ not started | — |
| 11 | Lyrics (LRCLIB + YTM, synced) | ⬜ not started | — |
| 12 | Desktop native (SMTC spike, tray, mini-player, jpackage) | ⬜ not started | — |
| 13 | Android polish (insets, shortcuts, tablet, soak) | ⬜ not started | — |
| 14 | Robustness + rot-drill CI + release v0.1.0 | ⬜ not started | — |

Deferred to v2 (do not design, do not stub): Web/PWA, Android Auto, Cast,
equalizer, sync, downloads, widgets, jump lists, optional cookie sign-in.
