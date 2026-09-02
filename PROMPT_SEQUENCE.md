# DHUN — Prompt Sequence (rewritten, feasible)

> The 14-phase contract lives in [MASTER_PROMPT.md](MASTER_PROMPT.md).
> This file is the **operational prompt sequence**: a full audit of the
> original 30-phase prompt set (the one that said "first create a github repo
> named DHUN", Windows + Android + Web day one, hand-rolled InnerTube, ten
> research documents before any code), the evidence-backed replacements, and
> the rewritten phase-by-phase prompts at the same level of detail as the
> original. Written 2026-09-02, on top of the current repo state
> (Android v0.1.4 shipped, CI green, rolling `test` release live).
>
> Reading order: MASTER_PROMPT.md → PROBLEMS_AND_FIXES.md (why the plan looks
> like this) → ROADMAP.md (live status) → this file (what to build, phase by
> phase).

---

# Part A — Audit of the original 30-phase prompt

Each problem cites the original text, says why it fails **on 2026-09-02**
(with evidence already in this repo), and names the replacement. This audit
is intentionally terse: the full evidence base is
[PROBLEMS_AND_FIXES.md](PROBLEMS_AND_FIXES.md),
[ADR-001](docs/decisions/ADR-001-extraction-engine.md), and
[docs/research/01-extraction-spike.md](docs/research/01-extraction-spike.md).

## A1 — "First create a github repo named DHUN" — already done

The repository `99ggprooo00-code/DHUN` exists, is public, and is connected
(remote `origin`, `gh` authenticated, CI + `test-release` workflows green for
`598bbe6`). The user policy from the workflow prompt stands: **no SSH, no
tokens, no git clone, one rolling `test` release, one PR when asked.** There
is nothing to create; the audit below is about the *plan*, not the repo.

## A2 — Three platforms day one (Windows + Android + Web) — cut to two

Original: *"All three platforms are designed from day one. No 'port it later'
thinking."* — with full Web phases (09 shell, 21 PWA, 26 web deploy, 30 web
test session).

Why it fails: Web streaming from YouTube is effectively dead for third
parties — PO tokens/BotGuard cannot be minted in a browser app, SABR
replaces progressive URLs, CORS is uncontrolled (evidence: P7, and the
spike's R1–R5 rot table). The previous attempt was Windows-only with half
this scope and still stalled (P3/P4).

**Replacement:** Android = primary (Media3 gives media session, lock screen,
notification, background audio for free). Desktop = second via Compose
Multiproject sharing ~90% of UI and 100% of domain/extraction code with
Android. **Web is deferred to v2, not stubbed.** No JS target, no PWA phase,
no Lighthouse phase.

## A3 — Ten research documents before any code — replaced by a 2-day spike

Original Phase 01: *"No code is written in this phase"*, ten
`docs/research/0X-*.md` deliverables; Phase 02 adds a full ARCHITECTURE.md,
ten ADRs, a UI specification, and repository scaffolding **before any
application code exists**.

Why it fails: docs-first is exactly how the previous repository died —
excellent documents, an app that never attempted its core mission (P6).

**Replacement:** Phase 01 was a **2-day time-boxed extraction spike** with
fixed outputs (probe CLI, fixtures, one findings file) — completed and
PASS (search → resolve → audio bytes → related). ADRs exist only for
decisions with real alternatives (currently one: ADR-001 + its 2026-09-02
addendum). UI spec lives as code (tokens + component catalogue in Phase 06),
not a 40-page markdown.

## A4 — Hand-rolled stream extraction — replaced by the two-tier resolver + rot drill

Original MASTER_PROMPT and Phase 04 told us to implement
`/youtubei/v1/player` ourselves with `WEB_REMIX` / `ANDROID_MUSIC` /
`IOS_MUSIC` and *"Reimplement — do not fork"* the dead clients.

Why it fails: YouTube now enforces PO tokens; `android_music`/`ios_music`
require sign-in per video; SABR breaks the progressive-URL model; BotGuard
cannot be beaten by a plain HTTP client (P1). The clients the old plan leaned
on are the ones that died.

**Replacement (ADR-001 + addendum):**
- DHUN's own InnerTube client is **metadata-only** (search / browse / next /
  suggestions / lyrics browse — `WEB_REMIX`, client version scraped fresh).
  Proven working from hostile IPs.
- Stream resolution is a **`StreamResolver`** interface: on Android an
  in-JVM own-client resolver running a **3-identity chain
  (`WEB_REMIX` → `VISIONOS` → `TVHTML5`)** (shipped in v0.1.4); on Desktop
  **yt-dlp subprocess** is primary; **NewPipeExtractor** is retained and
  WATCHED by the drill, re-entering only when drill-green.
- A **daily rot-drill CI job** detects breakage within 24h; the risk
  register has the pre-agreed responses.

## A5 — "Study ViMusic, SimpMusic, InnerTune deeply" — the graveyard

Original research list: ViMusic, SimpMusic, InnerTune… "Reimplement — do not
fork."

Why it fails: ViMusic archived Oct 2024, RiMusic archived Jul 2025,
InnerTune discontinued, OuterTune paused, SmPm discontinued. Every death was
extraction rot + maintainer burnout (P2). Copying their code would inherit
their rot; their approach (hand-rolled client) is exactly what the doctrine
rejects.

**Replacement:** lessons studied conceptually during the spike (what
endpoints work, what formats arrive); nothing copied; the maintenance
contract (patch releases ≤72h, rot drill, pinned `extraction.properties`
client constants) replaces "implement once and forget."

## A6 — Windows playback "vlcj or … document which" + promised SMTC — locked now

Original Phase 05: *"Research from Phase 01 which approach is used. If using
VLC via Kotlin (vlcj or similar)… If using a different approach (document
which and why)"* — while Phase 07 *promised* tray, SMTC, mini-player,
global hotkeys, notifications.

Why it fails: the highest-risk desktop decision was left open while its
dependencies were promised as done (P8).

**Replacement:** **vlcj (libVLC)** is locked for desktop audio; SMTC is a
time-boxed spike (Phase 12) with a documented degradation path (tray
controls + media keys) — no silent partial integrations.

## A7 — Auth phase (cookie-pasting) — cut from v1

Original Phase 17: paste cookies from a browser, encrypt at rest.

Why it fails: brittle, ToS-gray, and not needed for the v1 feature set
(search/stream/library all work anonymously) (P10).

**Replacement:** v1 is fully anonymous. Optional cookie sign-in is Phase 13
(optional, last, cuttable) and exists only to unlock age/region-restricted
content and personal playlists. If ever built: platform-keystore-encrypted,
never logged, documented.

## A8 — Android Auto + home-screen widget — cut to v2

Original Phase 20. **Replacement:** v2 candidates, not designed, not stubbed
(P9). MediaBrowserService compatibility is only a documented fallback if a
v2 decision changes, never a v1 promise.

## A9 — Emulator UI tests, >80% coverage, Lighthouse >90 — replaced by real verification

Original Phases 24/25: Espresso/Compose UI tests on an emulator, coverage
targets, Lighthouse performance/PWA audits.

Why they fail in this project's reality: no emulator or device is attached
to the sandbox (verification is on the user's hardware with logged evidence),
Web is cut, and the project policy is "verification logs over vanity
metrics."

**Replacement:** parser/domain/queue unit tests against committed fixtures
(34 green today, more added each phase), live integration checks via the
probe/smoke CLIs, and **on-device verification checklists** in
`docs/verification/` (03 exists; every later phase adds one). CI runs unit
tests + APK build + probe compile on every push/PR.

## A10 — Release workflow building signed APK + Windows installer + web deploy — replaced by the rolling-release policy

Original Phase 26 produced versioned signed artifacts; Phase 29 a "1.0.0"
release.

**Replacement (user decision 2026-09-01):** exactly **one rolling release**
tagged `test` with `dhun-test.apk`, auto-replaced on every push to main
(`.github/workflows/test-release.yml`). No versioned releases until the
Phase-14 v0.1.0 milestone (debug-keystore signed APK/AAB + desktop
installers), which is the first real Release. Nothing in Releases is stable
or store-ready before then.

## A11 — Internal contradictions (P12) — resolved

| Open question in the old prompt | Locked decision |
|---|---|
| "Koin or Dagger/Hilt" | **Koin** (KMP-first) |
| "SQLDelight or Room" | **SQLDelight 2.x**, Android + JVM drivers only |
| "Decompose or Navigation Compose" | **Navigation Compose** (Android); small custom navigator over shared screen models (Desktop) |
| "Zustand or Redux" (web) | cut with Web |
| Phase 03 web build (`jsBrowserDevelopmentRun`) | no JS target |
| "REST/gRPC shared backend" alternative | no server; client-only app |
| "SQLite in browser (sql.js/OPFS)" | cut with Web |

## A12 — Interface drift — the rewrite matches shipped code

The old prompt's `MusicProvider` / `DhunPlayer` signatures differ from what
actually shipped and is tested in `shared/` (DhunResult/DhunError taxonomy
with `toUserMessage()`, `StreamResolver` chain, `QueueManager` as pure logic,
Media3-native queue on Android). The rewritten prompts below reference the
**shipped contracts**, not a re-design.

## A13 — Sequencing: extraction proof must come first

The old order was research → architecture → foundation → YouTube client →
playback → UI. The doctrine requires **proof of playback before anything
else**. The repo's actual order — spike (01) → provider/domain core (02) →
Android skeleton (03) → desktop (04) → data (05) → UI phases — is preserved
in Part B.

---

# Part B — The rewritten prompt sequence

Rules that apply to every phase below:

1. **Read MASTER_PROMPT.md, PROBLEMS_AND_FIXES.md, ROADMAP.md, and the
   previous phase's verification log before starting.**
2. **Ship running code.** A phase is done when it runs on real hardware and
   the verification log says so. Compiling is not done. Documents are not
   done.
3. Tests after implementation, before commit. No test that tests nothing.
4. Update ROADMAP.md + KNOWN_LIMITATIONS.md every phase. Update THIRD_PARTY
   when dependencies enter.
5. Commit small and meaningful. No big-bang commits.
6. No stubs, no TODO-left-in-production.
7. If reality invalidates a locked decision: stop, write the ADR, get the
   user's OK, then proceed. Silent divergence is forbidden.
8. When a step exceeds ~30 minutes of wall-clock effort without visible
   progress, split it or report back.
9. GitHub flow in this chat: edit files on the session branch, **one PR**,
   user merges; the `test-release` workflow then replaces the rolling APK.
   Never SSH, never tokens, never new versioned releases.
10. Sandbox reality: egress is allowlisted to GitHub only; restore the
    toolchain from `docs/development/sandbox-toolchain.md` before builds;
    CI on GitHub Actions is the authoritative build + test path.

Dependency map (same as MASTER_PROMPT):

```
01 ─ 02 ─ 03 ─┬─ 06 ─ 07 ─ 08 ─ 09 ─ 10 ─ 11 ─┐
              └─ 04 ────────────────┬─────────┼─ 14
                    05 ────────────┘  12 · 13 ┘
```

---

## Phase 03-C — Complete Android on-device verification (IN PROGRESS)

**Objective:** Close every open hardware criterion of Phase 03. The app
already builds, publishes, and searches live on a phone; playback is the
remaining question.

**Current state (evidence):** v0.1.4 (`598bbe6`, versionCode 5) carries the
3-identity `/player` chain, stable test signing, live diagnostics log on the
connecting screen, session-less local-ExoPlayer fallback, failure screen
with Retry, BACK = background. CI + test-release both green; rolling APK at
`https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.apk`.

**Build (what to change, in order, based on diagnostics):**
1. Collect from the user: exact on-screen log lines, failure message,
   whether audio ever plays (local-fallback banner), device OEM + Android
   version.
2. Map the evidence to a cause:
   - Controller bind fails / times out → fallback should already play audio;
     if it doesn't, debug `PlaybackGraph.buildExoPlayer` + `ResolvingDataSource`.
   - `AuthRequired` from all three identities → network-level gating; add the
     typed message + retry, and record it in KNOWN_LIMITATIONS (no silent
     "Source error").
   - Crash → fix the crash path, ship v0.1.5.
3. If needed, ship the fix as the next version bump (v0.1.5, versionCode 6)
   through the same PR → CI → rolling-release loop.

**Verify on hardware (the five Phase-03 criteria):**
1. Real audio audible from YouTube Music (resolution → ExoPlayer → speaker).
2. Lock screen controls + notification player appear and work.
3. Background playback survives app swipe-away; notification swipe = stop.
4. 403 mid-stream recovery: stale URL re-resolves and resumes at position.
5. No-network → clean typed error, no crash.

**Docs:** update `docs/verification/03-android-skeleton.md` with on-device
results; ROADMAP Phase 03 → ✅ when all five pass.

**Definition of done:** all five hardware checks pass and are logged; the
blocker is closed; ROADMAP says ✅.

---

## Phase 04 — Desktop skeleton + vlcj playback

**Objective:** the same `DhunPlayer` API plays a track on the desktop, with
a clean VLC lifecycle.

**Prerequisites:** Phase 03-C verified (playback pipeline proven on-device);
shared module healthy.

**Build:**
- `app-desktop` module (Compose Desktop): `Window` 1200×780, min 800×600,
  Koin graph reusing `shared` provider/factories
  (`ProviderFactoriesJvm.forDesktop()`), `settings.gradle.kts` includes it.
- `DesktopDhunPlayer : DhunPlayer` wrapping **vlcj**
  (`MediaPlayerFactory` + `MediaPlayer`, audio-only, no video surface);
  position polling 500ms; same state/queue projection as Android.
- **Shared `QueueManager` is the queue source of truth on desktop**
  (Android keeps Media3-native queue; documented divergence in Phase 03
  verification).
- Stream resolution: `YtDlpStreamResolver` primary (yt-dlp on `PYTHONPATH`),
  own-client chain fallback, NewPipe watched.
- Reuse the harness screen (shared composable) for search → play → transport.
- `SettingsStore` interface over `java.util.prefs` (window size/pos).
- THIRD_PARTY: vlcj + libVLC (LGPL-2.1, dynamic link) row confirmed.

**Verify on hardware:** search → play → audible audio → pause/resume → seek
→ next/prev → close window = clean exit, no dangling VLC processes (`ps`).

**Acceptance criteria:**
1. Real audio on desktop via vlcj.
2. All transport controls correct.
3. VLC process lifecycle clean on stop/exit.
4. Same `DhunPlayer` drives both platforms (run the shared harness on both).

**Definition of done:** all four verified; `docs/verification/04-desktop.md`
logged; ROADMAP Phase 04 ✅.

---

## Phase 05 — Data layer (SQLDelight, repositories, use cases)

**Objective:** library, favorites, playlists, history, settings — persisted,
observable, restored across restarts.

**Build:**
- SQLDelight schema: `Track, Playlist, PlaylistTrack, Favorite, History,
  Settings, RecentSearch` (keyed by YouTube IDs; `cachedAt` for future
  caching). Migrations from v1 onward.
- Drivers: Android `AndroidSqliteDriver`, JVM `JdbcSqliteDriver`.
- Repositories (Flows out, suspend in): `TrackRepository`,
  `LibraryRepository`, `PlaylistRepository`, `HistoryRepository`,
  `SettingsRepository`, `SearchRepository`.
- Use cases: `ToggleFavorite, CreatePlaylist, AddToPlaylist,
  RemoveFromPlaylist, RecordPlay, GetRecentlyPlayed, GetHistory,
  UpdateSetting…`
- Settings keys object (audio quality, theme, accent mode, lyrics prefs,
  cache size, country code).
- **Now-playing persistence:** restore last queue + position on cold start
  (both platforms).

**Tests:** every repository against in-memory DB; every use case with fakes;
queue-restore round-trip. THIRD_PARTY: SQLDelight row confirmed.

**Acceptance criteria:**
1. All repo tests green both targets.
2. Favorite → observe → unfavorite round-trip verified in-app both platforms.
3. Queue survives app restart on Android.

**Definition of done:** all three verified; ROADMAP Phase 05 ✅.

---

## Phase 06 — Design system (living, in code)

**Objective:** the DHUN look as real components — tokens, real blur,
artwork-driven color — verified visually, not specced.

**Build — `shared/design/`:**
- Tokens: `DhunColors` (surfaces `#0A0A0A…#2A2A2A`, glass 60% `#99111111`,
  border 10% white, text 4-step alpha, accent `#BB86FC` static fallback),
  `DhunTypography` (Material-3-compatible), `DhunSpacing`, `DhunShapes`,
  `DhunAnimations` (150/300/500ms + spring).
- `GlassCard`: **real blur** — `Modifier.blur()`/`RenderEffect` on
  Android 12+/desktop skiko; scrim+translucency fallback below; flagged in
  KNOWN_LIMITATIONS per platform.
- `ArtworkImage` (Coil 3): crossfade, pulsing placeholder, error gradient.
- `ArtworkColorExtractor`: palette from artwork → `ArtworkColors`.
- Components: `TrackRow, TrackCard, ArtistCard, AlbumCard, PlaylistCard,
  SectionHeader, DhunButton, DhunIconButton, LoadingShimmer, ErrorView,
  EmptyView, Chip` — every state (normal/pressed/disabled/loading).
- `ComponentCatalogScreen` (debug builds only) rendering every state.
- Replace the harness UI + framework icon here or in Phase 07 (harness is
  marked throwaway; delete it the moment real screens replace it).

**Verify:** catalogue screen over a colorful artwork background; blur visibly
real; extracted-colors test screen. Screenshot in
`docs/verification/06-design.md`.

**Acceptance criteria:**
1. Glass blur visibly real over artwork (screenshot logged).
2. No raw hex/px values outside `shared/design/`.
3. All states present in catalogue.
4. Artwork color extraction returns sane palettes for 5 artworks.

**Definition of done:** 1–4 verified on Android (Desktop as available);
ROADMAP Phase 06 ✅.

---

## Phase 07 — Home & Search

**Objective:** the two screens that prove the app is real, end-to-end with
live YTM data.

**Build:**
- Shared `HomeScreen` + `HomeViewModel`: time-of-day greeting, quick picks
  (6 tracks, 3×2), sections from InnerTube home/browse ("Listen again" from
  history + YTM sections), shimmer skeletons, error/empty/retry,
  pull-to-refresh (Android).
- Shared `SearchScreen` + `SearchViewModel`: debounced suggestions (300ms),
  filter chips (songs/artists/albums/playlists), per-type rows, infinite
  scroll via continuation, recent searches (persisted, clearable), track
  overflow menu (play next / add to queue / add to playlist / go to
  artist-album — all functional).
- Track tap → plays with the full result set as queue context.

**Verify on hardware, both platforms:** real data end-to-end; loading
skeleton (not spinner), error, and empty states all observed.

**Acceptance criteria:**
1. Home renders real YTM content on Android + Desktop.
2. Search: suggestions ≤300ms after pause → results per filter → infinite
   scroll.
3. Every overflow action works.
4. Skeleton, error, empty states all observed.

**Definition of done:** 1–4 verified; `docs/verification/07-home-search.md`
logged; ROADMAP Phase 07 ✅.

---

## Phase 08 — Player UI (MiniPlayer + FullPlayer)

**Objective:** ViMusic-quality player with blurred artwork background,
correct choreography, and a usable queue — the showstopper phase.

**Build:**
- `MiniPlayer` (shared): 72dp glass bar above bottom nav (Android) / docked
  bottom (desktop); artwork, marquee title, artist, play/pause, next, 1dp
  accent progress line; tap/swipe-up opens FullPlayer; swipe-to-skip
  optional.
- `FullPlayer` (shared): full-bleed **blurred artwork background + dark
  scrim**, color crossfade on track change (500ms); large artwork with
  shadow, playing-vs-paused spring scale; custom progress bar (4dp→8dp on
  drag, thumb on touch only); prev/next (hold-to-seek), animated play/pause
  morph; shuffle + repeat cycle; volume slider (desktop); bottom tabs
  **Lyrics | Queue | Related** (Related wired to `/next` parsing).
- Queue tab: drag reorder, swipe remove, tap-to-jump; current track
  highlighted with equalizer animation.
- Track-change choreography: artwork slide in skip direction + fade,
  background color crossfade, title fade-update-fade.
- Android: edge-to-edge insets correct; BACK from FullPlayer collapses
  (never exits).

**Verify:** every check in `docs/verification/08-player.md` with screenshots
(16 visual/interaction checks from MASTER_PROMPT); rapid-skip stress (10
fast nexts) — no state inconsistency, no crash.

**Acceptance criteria:**
1. Background is real blurred artwork (not a color).
2. All 16 visual/interaction checks pass.
3. Rapid-skip stress clean.
4. Both platforms.

**Definition of done:** 1–4 verified; ROADMAP Phase 08 ✅.

---

## Phase 09 — Artist, Album, Playlist pages

**Objective:** browse the catalog properly, including local playlist CRUD.

**Build:**
- InnerTube browse parsers: artist (`MUSIC_PAGE_TYPE_ARTIST`), album
  (`MPRE…`), playlist (`VL…`) — fixtures captured for tests.
- `ArtistScreen`: parallax header, collapse-on-scroll with glass toolbar,
  shuffle/radio (`/next` radio playlist), top songs, albums, singles,
  related artists, about.
- `AlbumScreen`: artwork header, play/shuffle, ordered track list, "more by
  artist".
- `PlaylistScreen`: YTM playlists (play, add-to-library mirror) + local
  playlists (rename, drag reorder, remove tracks, delete).
- Wire every navigation path from Home/Search/Player into these pages.

**Acceptance criteria:**
1. Artist page for 3 artists: all sections correct.
2. Album track order correct for 3 albums.
3. Local playlist CRUD + reorder verified.
4. Parser tests against fixtures green.

**Definition of done:** 1–4 verified; ROADMAP Phase 09 ✅.

---

## Phase 10 — Library & History screens

**Objective:** the user's own space.

**Build:**
- Library tabs: Playlists / Favorites / History (+ Albums/Artists saved when
  the data layer supports; otherwise listed in KNOWN_LIMITATIONS).
- Favorites list (tap plays favorites as queue, swipe to remove).
- History grouped by day with relative times; long-press remove; clear all
  with confirmation.
- `RecordPlay` use case wired into the player (search/home/artist/album/
  playlist/radio contexts).

**Acceptance criteria:**
1. Favorites round-trip in UI.
2. History grouping/timestamps correct; clear works.
3. Empty states for all tabs.

**Definition of done:** 1–3 verified; ROADMAP Phase 10 ✅.

---

## Phase 11 — Lyrics (LRCLIB + YTM, synced)

**Objective:** synced lyrics that scroll with the music, with honest fallback.

**Build:**
- `shared/lyrics`: `LyricsSource` interface; `LrcLibSource` (title+artist+
  duration match, synced LRC); `YouTubeLyricsSource` (InnerTube browse);
  `LyricsRepository` = cache → YTM → LRCLIB → NotAvailable; `LrcParser`
  ([mm:ss.xx] + enhanced word timing tolerated).
- Lyrics tab in FullPlayer: active line large/bright/centered, others
  dim/smaller; smooth auto-scroll; tap line = seek; on seek, jump to line;
  unsynced = scrollable text; empty state.
- Lyrics persisted cache (Phase 05 DB).

**Acceptance criteria:**
1. Synced lyrics track audio on 5 diverse tracks.
2. Tap-to-seek correct within ±1s.
3. LRCLIB fallback verified on tracks absent from YTM lyrics.
4. Second open = instant (cache hit).
5. Parser unit tests green.

**Definition of done:** 1–5 verified; ROADMAP Phase 11 ✅.

---

## Phase 12 — Desktop native integrations

**Objective:** the desktop app feels native, with honest fallbacks.

**Build (spike-ordered):**
1. **SMTC spike (3 days, time-boxed):** Windows System Media Transport
   Controls via JNA/WinRT — now-playing tile, artwork, media keys. Stable →
   integrate. Unstable → ship the documented fallback (tray-based controls +
   focused-window media keys), record in KNOWN_LIMITATIONS.
2. System tray: icon (playing/paused variants), menu (track title /
   play-pause / next / prev / open / quit).
3. Mini-player window: 320×88 always-on-top, artwork, title, transport,
   progress; draggable; click opens main window.
4. Keyboard shortcuts: Space, ←/→ seek 5s, Ctrl+←/→ prev/next, Ctrl+F
   search, Ctrl+M mini-player, Ctrl+Q quit.
5. Close-to-tray setting (default on), remembered window state.
6. Packaging: `jpackage` (.msi via `createDistributable`/`packageMsi`) with
   app icon; test install on a clean Windows VM/user.

**Acceptance criteria:**
1. Media keys control playback (SMTC or documented fallback).
2. Tray fully functional; close-to-tray ≠ quit; quit exits clean.
3. Mini-player mirrors state live.
4. Installer installs and runs on a clean machine.

**Definition of done:** 1–4 verified; ROADMAP Phase 12 ✅.

---

## Phase 13 — Android native polish

**Objective:** Android feels native and survives real-world use.

**Build:**
- Edge-to-edge audit with `WindowInsets` on every screen; gesture-nav
  compatibility (no edge-swipe conflicts in player/queue).
- App shortcuts (long-press): Search, Resume, Library.
- Battery-optimization exemption prompt with rationale (standard for media
  apps), documented in first-run.
- Robolectric/unit tests where feasible: rotation state survival, back-stack
  behavior, notification permission flow (CI-runnable; on-device where
  needed).
- Tablet/large-screen: bottom nav → navigation rail at width ≥840dp;
  two-pane player where space allows.
- Optional cookie sign-in (cuttable): platform-keystore-encrypted session,
  never logged, unlocks age/region-restricted content + personal playlists;
  documented in KNOWN_LIMITATIONS if cut.

**Acceptance criteria:**
1. Rotation: no state loss, no crash.
2. Back stack per spec (tabs exit, pages pop, player collapses).
3. Shortcuts work.
4. Background playback with battery optimization unrestricted: 30-min soak,
   no kill, no leak.

**Definition of done:** 1–4 verified; ROADMAP Phase 13 ✅.

---

## Phase 14 — Robustness, rot-drill CI, release v0.1.0

**Objective:** earn the right to exist past first contact with reality.

**Build:**
- **Error taxonomy sweep:** every network/db/playback path returns typed
  errors → human, actionable messages; offline banner; 429 global backoff;
  403-during-playback auto-recovery UX-visible.
- **Audio cache (simple, bounded):** played audio segments cached LRU
  (default 1GB, user-settable); replay of cached tracks works offline;
  stream-URL cache with TTL + 403 invalidation (extends Phase-03
  `DhunStreamCache`).
- **Rot-drill CI:** `.github/workflows/rot-drill.yml` — daily cron runs the
  Phase-01 probe suite (search → resolve → HTTP-200/audio check) against
  live YouTube; failure opens an issue automatically. Replaces the current
  placeholder.
- **30-minute soak** on Android + Desktop: normal use, zero crashes.
- **Release v0.1.0:** version bump, debug-keystore signed APK + AAB,
  desktop installers (Phase 12), CHANGELOG.md, README finalized (build
  instructions that actually work + the maintenance contract),
  THIRD_PARTY.md + RISK_REGISTER reviewed, git tag `v0.1.0`, GitHub release
  with artifacts (this is the first real release — replaces nothing; the
  rolling `test` release stays until then).

**Acceptance criteria:**
1. Rot drill green and scheduled.
2. 30-min soak passed on both platforms (logged in
   `docs/verification/14-release.md`).
3. All artifacts built and install/run on clean targets.
4. KNOWN_LIMITATIONS.md current and honest.

**Definition of done:** 1–4 verified; v0.1.0 tagged and released; ROADMAP
Phase 14 ✅; project reaches "v1 done" per MASTER_PROMPT.

---

# Part C — Cross-cutting reminders (from the master contract)

- **Deferred to v2 (do not design, do not stub):** Web/PWA · Android Auto ·
  Cast · equalizer · cross-device sync · downloads beyond cache · widgets ·
  jump lists · themes beyond dark-first · account sign-in (optional cookies
  = Phase 13, last, cuttable).
- **Kill switch:** if every maintained extraction path is down ≥14 days with
  no upstream fix, STOP, write up, and decide with the user (RISK_REGISTER).
- **License:** GPL-3.0, non-negotiable. Nothing enters the build unless
  GPL-3.0-compatible; every reuse recorded in THIRD_PARTY.md.
- **Verification discipline:** every phase logs real hardware evidence in
  `docs/verification/0N-*.md` before ROADMAP flips ✅. Screenshots + exact
  commands + observed behavior; nothing asserted from an IDE.
