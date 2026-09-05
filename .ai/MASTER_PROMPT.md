# DHUN — Master Prompt (v2, Feasibility-Corrected)

> This file replaces the original 30-phase master plan.
> Why it was replaced: [PROBLEMS_AND_FIXES.md](PROBLEMS_AND_FIXES.md).
> Summary of what changed: 30 phases → 14 · 3 platforms → 2 (Web deferred) ·
> hand-rolled InnerTube extraction → maintained extractors + daily rot drill ·
> docs-first → code-first · license ambiguity → GPL-3.0.
> The full audit of the original 30-phase prompt set and the rewritten,
> fully-detailed phase-by-phase prompts live in
> [PROMPT_SEQUENCE.md](PROMPT_SEQUENCE.md).

---

## What DHUN Is

DHUN is a serious, cross-platform music application streaming from
YouTube Music.

- **v1 platforms:** Android (primary) and Windows/Linux/macOS Desktop
  (secondary, shared codebase).
- **Web:** explicitly deferred. Not designed, not stubbed, not promised.
- **UI philosophy:** premium glassy design, artwork-driven, dark-first,
  ViMusic-quality. This is not a prototype. This is a real application.
- **License:** GPL-3.0. Non-negotiable (see PROBLEMS_AND_FIXES.md P5).

---

## The One Doctrine That Decides Everything

**Extraction is a maintenance problem, not an implementation problem.**

Every open YouTube Music client that hand-rolled stream extraction is now
archived or broken (ViMusic, RiMusic, InnerTune, SmPm — see audit P1/P2).
YouTube enforces PO tokens + SABR; clients `android_music`/`ios_music` now
require sign-in per video; clients that work tokenless change monthly.

Therefore DHUN **never hand-rolls stream extraction**:

| Layer | Implementation | Why |
|---|---|---|
| Search / browse / related / suggestions / YTM lyrics | DHUN's own thin InnerTube client (`WEB_REMIX`, metadata only) | These endpoints are stable and tokenless; owning them keeps DHUN independent |
| **Stream URL resolution** | **NewPipe Extractor** (GPL-3.0, pure Java) — in-process on Android AND Desktop | One dependency absorbs YouTube's breakage on both platforms; maintained daily by the NewPipe team |
| Desktop fallback extraction | **yt-dlp** subprocess (Unlicense), optional | Fastest-moving extractor in existence; used only if NewPipe Extractor fails |
| Lyrics fallback | LRCLIB public API | Keyless, stable, synced lyrics |

And DHUN accepts the maintenance contract, stated openly in the README:
*streaming clients rot; when extraction breaks, DHUN ships a patch release —
fast. A daily CI job (the "rot drill") detects breakage within 24 hours.*

---

## What Happened Before (Reference Only)

The repository `99ggprooo00-code/dhun-music-failed` is the previous attempt.
Established facts (do not re-research):

- It was a **GPL-3.0 fork of Nagi** — C#/.NET 10/WinUI 3, **local-file
  player only**. Its own rules prohibited unofficial extraction, so it
  structurally could never become DHUN (audit P4).
- It produced excellent documentation and an app that never attempted the
  core mission. That failure mode — **docs-first, code-second** — is what
  this master prompt is designed to prevent.
- Nothing from it is reused. Not concepts-into-code, not code. One lesson
  document exists: PROBLEMS_AND_FIXES.md. Done.

---

## Non-Negotiable Requirements

### Platforms (revised)
- **Android:** full music player — background playback, media session,
  lock screen + notification controls, queue, playlists, lyrics.
- **Desktop (Windows first; Linux/macOS free via JVM):** windowed app,
  system tray, media-key integration (SMTC where feasible, documented
  fallback if not), mini-player window, keyboard shortcuts, installer.
- **Web:** cut from v1. No compatibility shims, no `expect/actual` stubs,
  no dead code "for later."

### Music source (revised)
- YouTube Music via the Doctrine (table above). No paid API. No keys.
- **Extraction proof precedes everything** (Phase 01). If audio cannot be
  resolved and played on real hardware in week one, everything stops until
  it can.

### UI (unchanged from the original vision)
- Glassy translucent surfaces with **real blur**, layered depth,
  artwork-driven backgrounds, prominent artwork, rounded surfaces, subtle
  borders/shadows/gradients, dark-first, premium typography, animations and
  micro-interactions, every state designed (hover/focus/pressed/loading/
  error/empty), mini-player and full player both designed properly.
- NOT: generic dashboards, default component-library look, "AI app"
  aesthetics.

### Architecture (unchanged in shape, revised in content)
```
┌─────────────────────────────────────┐
│   UI (Compose Multiplatform)        │  shared screens; platform accents
├─────────────────────────────────────┤
│   Application layer                 │  screen models, state, navigation
├─────────────────────────────────────┤
│   Domain layer                      │  use cases, entities, interfaces
├─────────────────────────────────────┤
│   Provider abstraction              │  MusicProvider interface
├──────────────────┬──────────────────┤
│ InnerTube client │ Extraction engine│
│ (metadata only)  │ NewPipe Extractor│
│                  │ (+ yt-dlp, desktop fallback)
├──────────────────┴──────────────────┤
│   Playback: Media3 (Android) · vlcj (Desktop)
└─────────────────────────────────────┘
```

---

## Technology Stack — LOCKED

Decisions are made. They are not re-opened without a written ADR proving a
blocking defect.

| Concern | Decision | Notes |
|---|---|---|
| Language/KMP | **Kotlin Multiplatform** | Targets: `androidTarget()` + `jvm()` only. No JS/native targets. |
| UI | **Compose Multiplatform** | Shared screens Android+Desktop; platform adapters where needed |
| Android playback | **Media3 / ExoPlayer** | MediaSessionService, notification, lock screen for free |
| Desktop playback | **vlcj (libVLC)** | Proven HTTP-audio streaming; SPI-free JNA binding |
| Extraction | **NewPipe Extractor** (dependency) | Runs on both targets |
| Extraction fallback | **yt-dlp subprocess** (desktop only, optional at runtime) | Feature-flagged |
| InnerTube metadata | **DHUN's own client** (Ktor + kotlinx.serialization) | `WEB_REMIX`; search/browse/next/suggestions/YTM lyrics only |
| DB | **SQLDelight 2.x** | Android + JVM drivers only |
| Networking | **Ktor client** | OkHttp engine both platforms |
| DI | **Koin** | KMP-first |
| Android navigation | **Navigation Compose** | |
| Desktop navigation | Small custom navigator over shared screen models | |
| Images | **Coil 3** (Compose Multiplatform) | |
| Lyrics | LRCLIB API + YTM lyrics via InnerTube | |
| Logging | Kermit | |

**Explicitly rejected:** Flutter (desktop audio + channels weakness),
Electron/Tauri (separate UI codebase = the Nagi mistake), separate backend
(no server; client-only app), Compose for Web / Kotlin-JS (experimental),
Room (Android-only), Hilt (not KMP).

---

## Repository Structure

```
DHUN/
├── MASTER_PROMPT.md            # this file
├── PROBLEMS_AND_FIXES.md       # plan audit (the "why")
├── ROADMAP.md                  # phase status tracker, kept current
├── README.md
├── LICENSE                     # GPL-3.0
├── THIRD_PARTY.md              # every dependency: name, license, commit/tag
├── KNOWN_LIMITATIONS.md        # honest list, updated every phase
├── RISK_REGISTER.md            # extraction rot, SMTC, kill-switch criteria
├── .gitignore
├── .github/workflows/
│   ├── ci.yml                  # build + unit tests (Android + Desktop)
│   └── rot-drill.yml           # daily extraction suite vs live YouTube
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
│
├── shared/                     # KMP module (android+jvm)
│   └── src/{commonMain,androidMain,jvmMain}/kotlin/dev/dhun/
│       ├── core/               # entities, DhunResult, errors
│       ├── innertube/          # OWN InnerTube metadata client
│       ├── extraction/         # NewPipe Extractor adapter (+ yt-dlp bridge, jvm)
│       ├── provider/           # MusicProvider impl wiring the two above
│       ├── player/             # PlayerState, QueueManager (shared logic)
│       ├── data/               # SQLDelight db, repositories
│       ├── lyrics/             # LRCLIB + YTM sources, LRC parser
│       ├── domain/             # use cases
│       └── design/             # tokens, glass components, artwork colors
│
├── app-android/                # Compose UI (Android), Media3 service, DI
├── app-desktop/                # Compose UI (Desktop), vlcj, tray, packaging
│
├── tools/playback-probe/       # Phase 01 CLI harness — PROVES extraction
├── tests/fixtures/             # captured InnerTube JSON for parser tests
└── docs/
    ├── research/               # spike findings (short, factual)
    ├── decisions/              # ADRs (only real decisions)
    └── verification/           # per-phase on-hardware verification logs
```

Rules: no `apps/{android,windows,web}` split (that assumed 3 native apps).
No placeholder modules for cut platforms. Every directory that exists,
earns its existence.

---

## The Sequence — 14 Phases

Each phase: objective → build → verify on hardware → tests → docs touched
→ commit. **A phase is done when the feature runs on real hardware and the
verification log says so.** Compiling is not done. Documents are not done.
(See AI Rules at the end.)

---

### Phase 01 — Extraction Spike (the phase everything depends on)

**Objective:** Prove the Doctrine on real hardware before any app code.
Time-box: 1 week of calendar time, hard stop.

**Build — `tools/playback-probe/` (CLI, Kotlin JVM):**
1. Search: own InnerTube `WEB_REMIX` call to `/youtubei/v1/search` with
   songs filter → parse `videoRenderer`/`musicResponsiveListItemRenderer`
   → print top 10 results for a query.
2. Resolve: NewPipe Extractor `YoutubeStreamLinkHandlerFactory` +
   `YoutubeStreamExtractor` for a result's video ID → get audio-only stream
   URL (choose best audio-only format).
3. Play: desktop — pipe the URL into a raw `vlcj` player and hear audio.
4. Metadata sanity: `/youtubei/v1/next` with the videoId → parse related
   tracks (this powers radio/queue later).
5. Fallback probe (desktop): same resolution via `yt-dlp -f bestaudio -g`
   subprocess; record when it succeeds vs NewPipe Extractor.

**Capture:** save raw JSON responses into `tests/fixtures/` (search, next).
**Record:** `docs/research/01-extraction-spike.md` — exact client context
used, what worked, failure modes seen, fixture file list.

**Acceptance criteria:**
1. Probe prints 10 real search results for "Bohemian Rhapsody".
2. Audio audibly plays from a resolved URL on a real machine.
3. `curl -I` on a resolved URL returns 200 with `audio/*` content type.
4. Related tracks parse for 3 different video IDs.
5. Both extractor paths (NewPipe, yt-dlp) tested; results recorded.
6. Fixtures committed.

**Kill switch:** if no path to audible audio exists after the time-box,
STOP. Write up findings. Decide with the user before anything else.

---

### Phase 02 — Provider & Domain Core

**Objective:** Turn the spike into the stable API every layer uses.

**Build:**
- `shared/core`: entities — `Track, Artist, Album, Playlist, SearchResults,
  HomeSection, Lyrics(Synced/Unsynced/NotAvailable), StreamInfo,
  PlayerState, RepeatMode, HistoryEntry`; `DhunResult<T>` sealed
  (Network, Parse, Unavailable, RateLimited, AuthRequired, Unknown) with
  `toUserMessage()`.
- `shared/innertube`: the metadata client from the spike, hardened —
  Ktor, timeouts, retry w/ exponential backoff on 429/5xx, `DhunResult`
  mapping, response models + parsers for search (all filters),
  search suggestions, next/related, YTM lyrics browse.
- `shared/extraction`: `StreamResolver` interface;
  `NewPipeStreamResolver` (both targets); `YtDlpStreamResolver`
  (jvmMain, subprocess, feature-flagged, default off until needed);
  `ResolvingStreamResolver` = primary with fallback + 403-aware re-resolve.
- `shared/provider`: `MusicProvider` interface + `YouTubeMusicProvider`
  implementation gluing innertube + extraction.
- `shared/player`: `DhunPlayer` interface, `QueueManager` (pure logic:
  add/remove/reorder/shuffle/repeat/next/prev), `PlayerState` machine.

**Tests:** parsers vs committed fixtures; QueueManager full coverage;
`ResolvingStreamResolver` 403→re-resolve logic (simulated).

**Acceptance criteria:**
1. `MusicProvider` complete — zero stubs, every method returns real data.
2. Parser unit tests green in CI (no network).
3. Queue logic tests green: 20+ assertions covering shuffle/repeat/edges
   (empty queue, single track, remove playing track).
4. No TODOs in `shared/`.

---

### Phase 03 — Android App Skeleton + Real Playback

**Objective:** DHUN plays a YouTube Music track on an Android phone with
background playback and lock screen controls. The app is ugly. It works.

**Build:**
- `app-android`: single-activity Compose app, Koin wired, dark theme from
  tokens (temporary minimal tokens).
- `DhunPlaybackService`: Media3 `MediaSessionService` + ExoPlayer;
  `AndroidDhunPlayer : DhunPlayer` bridging to Media3 (position polling
  500ms, state flows).
- `Media3StreamLoader`: resolves via `StreamResolver`, builds `MediaItem`,
  **on 403 mid-stream: re-resolve, seek to previous position, resume**
  (this is the single most important robustness feature — write it now).
- Audio focus handling (loss→pause, transient→pause, gain→resume-if-was).
- Test harness screen (clearly marked, deleted in Phase 06): search field,
  result list, play/pause/next/prev, position bar.
- Manifest: foregroundServiceType `mediaPlayback`, POST_NOTIFICATIONS
  runtime request, `WAKE_LOCK`.

**Verify on hardware:** play → lock phone → audio continues → lock screen
controls work → notification works → app killed ≠ audio killed →
notification swipe = stop.

**Acceptance criteria:**
1. Real audio on a real device from YouTube Music.
2. Lock screen controls functional.
3. Background playback survives app swipe-away.
4. 403-recovery verified (test by expiring/resolving a stale URL).
5. No-network → clean error state, no crash.

---

### Phase 04 — Desktop App Skeleton + Real Playback

**Objective:** The same core plays a track on the desktop.

**Build:**
- `app-desktop`: Compose Desktop `Window` (1200×780, min 800×600), Koin.
- `DesktopDhunPlayer : DhunPlayer` wrapping **vlcj**
  (`MediaPlayerFactory` + `MediaPlayer`); position polling 500ms;
  `AudioPlayerComponent` only (no video surface).
- Reuse the same test-harness screen as Android (shared harness
  composable in `shared` for now).
- Window state (size/pos) persisted via `java.util.prefs` behind a
  `SettingsStore` interface.

**Verify on hardware:** search → play → hear audio → pause/resume →
seek → next/prev → close window = app exits cleanly, no dangling VLC
processes (`ps` check).

**Acceptance criteria:**
1. Real audio on desktop.
2. All transport controls correct.
3. VLC process lifecycle clean (no leaks on stop/exit).
4. Same `DhunPlayer` API drives both platforms — prove it by running the
   shared harness on both.

---

### Phase 05 — Data Layer

**Objective:** Library, favorites, playlists, history, settings — persisted.

**Build:**
- SQLDelight schema: `Track, Playlist, PlaylistTrack, Favorite, History,
  Settings, RecentSearch` tables (keyed by YouTube video/playlist IDs;
  `cachedAt` columns for future caching).
- Drivers: Android `AndroidSqliteDriver`, JVM `JdbcSqliteDriver`.
- Repositories behind interfaces: `TrackRepository, LibraryRepository,
  PlaylistRepository, HistoryRepository, SettingsRepository,
  SearchRepository` (recent searches). Flows out, suspend in.
- Use cases: `ToggleFavorite, CreatePlaylist, AddToPlaylist,
  RemoveFromPlaylist, RecordPlay, GetRecentlyPlayed, GetHistory,
  UpdateSetting…`
- Settings keys object (audio quality, theme, accent mode, lyrics prefs,
  cache size, country code).
- Wire "now playing" persistence: restore last queue + position on cold
  start (both platforms).

**Tests:** every repository against in-memory DB; every use case with
fakes; queue-restore round-trip.

**Acceptance criteria:**
1. All repo tests green both targets.
2. Favorite → observe → unfavorite round-trip verified in-app on both
   platforms.
3. Queue survives app restart on Android.

---

### Phase 06 — Design System (living, in code)

**Objective:** The DHUN look, as real components — not a spec document.

**Build — `shared/design/`:**
- Tokens: `DhunColors` (surfaces `#0A0A0A…#2A2A2A`, glass 60% `#99111111`,
  border 10% white, text 4-step alpha, accent `#BB86FC` static fallback),
  `DhunTypography` (Material-3-compatible scale), `DhunSpacing`,
  `DhunShapes`, `DhunAnimations` (150/300/500ms + spring spec).
- `GlassCard`: **real blur** — `Modifier.blur()`/`RenderEffect` where the
  platform supports it (Android 12+/desktop skiko), graceful fallback to
  scrim+translucency below, flagged in KNOWN_LIMITATIONS.md per platform.
- `ArtworkImage` (Coil 3): crossfade, pulsing placeholder, error gradient.
- `ArtworkColorExtractor`: palette from artwork bitmap →
  `ArtworkColors(primary, onPrimary, container, backgroundTint)`.
- Components: `TrackRow, TrackCard, ArtistCard, AlbumCard, PlaylistCard,
  SectionHeader, DhunButton, DhunIconButton, LoadingShimmer, ErrorView,
  EmptyView, Chip` — each with normal/pressed/disabled/loading states.
- `ComponentCatalogScreen` (debug builds only) rendering every state.

**Verify:** catalogue screen on Android + Desktop over a colorful artwork
background; blur visibly real; extracted-colors test screen.

**Acceptance criteria:**
1. Glass blur visibly real over artwork (screenshot in
   `docs/verification/06-design.md`).
2. No raw hex/px values outside `shared/design/`.
3. All states present in catalogue.
4. Artwork color extraction returns sane palettes for 5 artworks.

---

### Phase 07 — Home & Search

**Objective:** The two screens that prove the app is real.

**Build:**
- Shared `HomeScreen` + `HomeViewModel`: time-of-day greeting, quick picks
  (6 tracks, 3×2), sections from InnerTube home/browse parsing
  ("Listen again" from history + YTM sections), shimmer skeletons,
  error/empty/retry states, pull-to-refresh (Android).
- Shared `SearchScreen` + `SearchViewModel`: debounced suggestions
  (300ms), filter chips (songs/artists/albums/playlists), results with
  per-type rows, infinite scroll via continuation, recent searches
  (persisted, clearable), track overflow menu (play next / add to queue /
  add to playlist / go to artist-album — all functional).
- Track tap → plays with the full result set as queue context.

**Verify on hardware, both platforms:** real data end-to-end.

**Acceptance criteria:**
1. Home renders real YTM content on Android + Desktop.
2. Search: type → suggestions ≤300ms after pause → results per filter →
   infinite scroll.
3. Every overflow action works.
4. Loading skeleton (not spinner), error, and empty states all observed.

---

### Phase 08 — Player UI (the showstopper)

**Objective:** MiniPlayer + FullPlayer at ViMusic quality.

**Build:**
- `MiniPlayer` (shared): 72dp glass bar above bottom nav (Android) /
  docked bottom (desktop); artwork, title marquee, artist, play/pause,
  next, 1dp accent progress line; tap/swipe-up opens FullPlayer
  (animated); swipe-to-skip optional.
- `FullPlayer` (shared): full-bleed **blurred artwork background + dark
  scrim**, color crossfade on track change (500ms); large artwork w/
  shadow, scale animation playing-vs-paused (spring); custom progress
  bar (4dp→8dp on drag, thumb on touch only); prev/next (hold-to-seek),
  animated play/pause morph; shuffle + repeat(cycle 3); volume slider
  (desktop); bottom tabs: **Lyrics | Queue | Related** (Related wired to
  `/next` parsing from Phase 02 — radio/queue works now).
- Queue tab: reorder (drag), remove (swipe), tap-to-jump; current track
  highlighted with equalizer animation.
- Track-change choreography: artwork slide in skip direction + fade,
  background color crossfade, title fade-update-fade.
- Android: edge-to-edge insets correct; back from FullPlayer collapses
  (never exits app).

**Verify on hardware:** every acceptance item in
`docs/verification/08-player.md` with screenshots.

**Acceptance criteria:**
1. Background is real blurred artwork (not a color).
2. All 16 visual/interaction checks from the verification doc pass.
3. Rapid skip-stress (10 fast nexts) — no state inconsistency, no crash.
4. Both platforms.

---

### Phase 09 — Artist, Album, Playlist Pages

**Objective:** Browse the catalog properly.

**Build:**
- InnerTube browse parsers: artist (`MUSIC_PAGE_TYPE_ARTIST` browseIds),
  album (`MPRE`…), playlist (`VL`…) — fixtures captured for tests.
- `ArtistScreen`: parallax header, collapse-on-scroll with glass toolbar,
  shuffle/radio (radio = `/next` radio playlist), top songs, albums,
  singles, related artists, about.
- `AlbumScreen`: artwork header, play/shuffle, ordered track list,
  "more by artist".
- `PlaylistScreen`: YTM playlists (play, add-to-library mirror) + local
  playlists (rename, reorder via drag, remove tracks, delete).
- Wire every navigation path from Home/Search/Player into these pages.

**Acceptance criteria:**
1. Artist page for 3 artists: all sections correct.
2. Album track order correct for 3 albums.
3. Local playlist CRUD + reorder verified.
4. Parser tests against fixtures green.

---

### Phase 10 — Library & History Screens

**Objective:** The user's own space.

**Build:**
- Library: tabs Playlists / Favorites / History (+ Albums/Artists saved
  when data layer supports — otherwise listed in KNOWN_LIMITATIONS).
- Favorites list (tap plays favorites as queue, swipe to remove).
- History grouped by day with relative times; long-press remove; clear
  all with confirmation.
- Play-played-context recording (search/home/artist/album/playlist/radio)
  via `RecordPlay` use case from Phase 05, wired into player.

**Acceptance criteria:**
1. Favorites round-trip in UI.
2. History grouping/timestamps correct; clear works.
3. Empty states for all tabs.

---

### Phase 11 — Lyrics

**Objective:** Synced lyrics that scroll with the music.

**Build:**
- `shared/lyrics`: `LyricsSource` interface; `LrcLibSource`
  (title+artist+duration match, synced LRC); `YouTubeLyricsSource`
  (InnerTube browse lyrics); `LyricsRepository` = cache → YTM → LRCLIB →
  NotAvailable; `LrcParser` ([mm:ss.xx] + enhanced word timing tolerated).
- Lyrics tab in FullPlayer: active line large/bright/centered, others
  dim/smaller; smooth auto-scroll; tap line = seek; on seek, jump to
  line; unsynced = scrollable text; empty state.
- Lyrics persisted cache (Phase 05 DB).

**Acceptance criteria:**
1. Synced lyrics track audio on 5 diverse tracks.
2. Tap-to-seek correct within ±1s.
3. LRCLIB fallback verified on tracks absent from YTM lyrics.
4. Second open = instant (cache hit).
5. Parser unit tests green.

---

### Phase 12 — Desktop Native Integrations

**Objective:** The desktop app feels native. With honest fallbacks.

**Build (spike-ordered):**
1. **SMTC spike (time-boxed 3 days):** Windows System Media Transport
   Controls via JNA/WinRT — now-playing tile, artwork, media keys.
   If stable → integrate. If not → fallback: tray-based controls +
   focused-window media keys, **documented in KNOWN_LIMITATIONS.md**.
2. System tray: icon (playing/paused variants), menu
   (track title / play-pause / next / prev / open / quit).
3. Mini-player window: 320×88 always-on-top second window; artwork,
   title, transport, progress; draggable; click opens main window.
4. Keyboard shortcuts: Space, ←/→ seek 5s, Ctrl+←/→ prev/next,
   Ctrl+F search, Ctrl+M mini-player, Ctrl+Q quit.
5. Close-to-tray setting (default on), remembered window state.
6. Packaging: `jpackage` (.msi via `createDistributable`/
   `packageMsi`) with app icon; test install on a clean Windows VM/user.

**Acceptance criteria:**
1. Media keys control playback (SMTC or documented fallback).
2. Tray fully functional; close-to-tray ≠ quit; quit from tray exits
   clean (no zombie processes).
3. Mini-player mirrors state live.
4. Installer installs and runs on a clean machine.

---

### Phase 13 — Android Native Polish

**Objective:** Android feels native and survives real-world use.

**Build:**
- Edge-to-edge audit with `WindowInsets` on every screen; gesture-nav
  compatibility (no edge-swipe conflicts in player/queue).
- App shortcuts (long-press): Search, Resume, Library.
- Battery-optimization exemption prompt with rationale (standard for
  media apps), documented in first-run.
- Robolectric/UI tests: rotation state survival, back-stack behavior,
  notification permission flow.
- Tablet/large-screen layout: bottom nav → navigation rail at width
  ≥ 840dp; two-pane player where space allows.

**Acceptance criteria:**
1. Rotation: no state loss, no crash.
2. Back stack per spec (tabs exit, pages pop, player collapses).
3. Shortcuts work.
4. Background playback with battery optimization unrestricted: 30-min
   soak, no kill, no leak (LeakCanary clean).

---

### Phase 14 — Robustness, Rot-Drill, Release v0.1.0

**Objective:** Earn the right to exist past first contact with reality.

**Build:**
- **Error taxonomy sweep:** every network/db/playback path returns typed
  errors → user-facing messages (human, actionable); offline banner;
  429 global backoff; 403-during-playback auto-recovery (from Phase 03,
  now UX-visible).
- **Audio cache (simple, bounded):** played audio segments cached LRU
  (default 1GB, user-settable), replay of cached tracks works offline;
  stream-URL cache with TTL + 403 invalidation.
- **Rot drill CI:** `.github/workflows/rot-drill.yml` — daily cron runs
  the Phase 01 probe suite (search → resolve → HTTP-200 check) against
  live YouTube; failure opens an issue automatically.
- **30-minute soak test** on Android + Desktop: normal use, zero crashes
  (required to pass).
- Release: version 0.1.0, signed debug-keystore APK + AAB, `jpackage`
  installers, CHANGELOG.md, README finalized (build instructions that
  actually work + the maintenance contract), THIRD_PARTY.md, RISK_REGISTER
  reviewed, git tag `v0.1.0`, GitHub release with artifacts.

**Acceptance criteria:**
1. Rot drill green and scheduled.
2. 30-min soak passed on both platforms (logged in
   `docs/verification/14-release.md`).
3. All three artifacts built and install/run on clean targets.
4. KNOWN_LIMITATIONS.md current and honest (Web absence, SMTC status,
   blur floor, anything else).

---

## Deferred (v2 candidates — NOT designed, NOT stubbed)

Web/PWA · Android Auto · Cast · equalizer · cross-device sync · account
sign-in (optional cookies; unlock age/region restrictions + personal
playlists — treat as experimental if ever built) · downloads beyond cache ·
widgets · jump lists · themes beyond dark-first.

---

## RISK_REGISTER.md — required content (created Phase 01, kept current)

| Risk | Likelihood | Trigger | Response |
|---|---|---|---|
| NewPipe Extractor breaks (PO/SABR change) | High, recurring | rot-drill red | Pin last-good; adopt upstream patch; ship patch release ≤72h |
| Upstream fix slow (>14 days) | Medium | rot-drill red 14 days | Enable yt-dlp fallback path (desktop); document Android impact; user decision on pivot |
| SMTC via JNA unstable | Medium | Phase 12 spike | Ship documented fallback (tray + media keys) |
| Blur unavailable (old Android) | Certain <API31 | Phase 06 | Scrim+translucency fallback; note in limitations |
| Ktor/Coil/Compose MP regression | Low | CI | Pin versions; upgrade deliberately, one at a time |

---

## AI Behavior Rules (code-first)

1. **Ship running code every phase.** Docs are written *after* the code
   they describe, from the code. A phase with beautiful docs and nothing
   on hardware is a failed phase — that is exactly how the last attempt
   died.
2. Read MASTER_PROMPT.md, PROBLEMS_AND_FIXES.md, ROADMAP.md, and the
   previous phase's verification log before starting a phase.
3. Verify on real hardware. Record evidence (screenshots/logs) in
   `docs/verification/`.
4. Tests after implementation, before commit. No test that tests nothing.
5. Update ROADMAP.md status and KNOWN_LIMITATIONS.md every phase.
6. Commit small and meaningful. Never "big bang" commits.
7. No stubs, no TODO-left-in-production, no "compiles = done."
8. If reality invalidates a locked decision, stop, write the ADR, get the
   user's OK, then proceed. Silent divergence is forbidden.
9. When a step exceeds ~30 minutes of wall-clock effort without visible
   progress, split it or report back. Silent grinding is forbidden.
10. Skippable is not allowed; pretending is less allowed.

---

## Phase Dependency Map

```
01 ─ 02 ─ 03 ─┬─ 06 ─ 07 ─ 08 ─ 09 ─ 10 ─ 11 ─┐
              └─ 04 ────────────────┬─────────┼─ 14
                    05 ────────────┘  12 · 13 ┘
```
(01→02→{03,04,05 in either order}→06→07→08→09→10→11 and {12,13} any time
after their platform base — 14 last.)
