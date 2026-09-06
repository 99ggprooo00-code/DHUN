# Changelog

All notable changes to DHUN are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

**No versioned release exists yet.** `v0.1.0` is tagged only after the
Phase 14 evidence in `docs/verification/14-release.md` is real (residential
stream verification, 30-minute soaks on Android and Desktop, clean-target
installs). Until then the only downloadable build is the rolling
[`test` pre-release](https://github.com/99ggprooo00-code/DHUN/releases/tag/test)
(`dhun-test.apk`, `dhun-test.msi`), replaced on every push to `main` — it
has an unversioned public tag; Windows still requires an increasing internal
MSI ProductVersion, separate from the app's semantic version.

The maintenance contract applies to every entry below: stream extraction
rots; when it breaks, DHUN ships a patch release fast (see README and
`.ai/RISK_REGISTER.md`).

## [Unreleased]

### Windows/Home/player repairs — 2026-09-06 (PR #30 merged; rolling test build)
- **Merged and test-published:** PR #30 → `76c68eb`; main CI
  **34031477321** and publishing **34031477327** passed. The repair-code test
  release was published at **11:58:17Z**, MSI **1.36.1**. This is still
  `Unreleased` for stable semver: v0.1.0/audio/visual/native/soak gates are open.
- **Installer data safety:** the real PR smoke caught jpackage deleting
  existing userdata during a major upgrade. Finalize the unsigned MSI with
  an upgrade-only cleaner guard and a matching legacy-HKCU cleanup bridge;
  explicit uninstall still removes userdata. PR run **34030730743** at
  `b6d47bd` passes 1.0.5 → 1.34.1 data/cache preservation, upgrade-flag
  removal, reinstall and explicit uninstall. This is not an app/audio test;
  backups are still recommended for legacy/failed-upgrade recovery.
- **MSI upgrades:** increasing internal versions per build/run attempt,
  stable upgrade UUID, stale-ref publishing protection and startup version
  logging. Five Python version-ordering/bounds regressions pass locally;
  hosted native sentinel checks now pass; user-machine verification remains open.
- **Windows playback path:** executable/Python discovery without Unix
  `which` or Windows Store aliases; cancellable process/pipe cleanup;
  explicit missing-tool evidence; reason-preserving bounded diagnostics
  across the sequential primary/fallback chain. This is **not a claim that
  the user's playback now works**.
- **Home:** feed-owned continuation tokens and modern append actions,
  consistent request client version, content-aware shelf deduplication,
  explicit page retry/end UI, bounded empty-page following and stale-refresh
  protection. Later quick-picks shelves retain new music.
- **Player:** origin-pivot icon scaling, canonical Material shuffle/repeat
  paths, artwork sized to available width **and height**, centred bounded
  transport/volume with consistent toggle styling, complete selectable
  diagnostics from the docked/full player. The separate window stays removed.
- **Local follow-up:** keep unrelated Home continuation targets separate
  (17 synthetic fixture cases), reject ambiguous cursors, fix cancelled
  Previous/Next presses and hold cleanup, reset stale scrub state, and bound
  yt-dlp pipe draining as well as child exit. Ten Python helper tests and
  strict validation of 29 JSON fixture files pass; this is not an app build.
- **Branch CI passed:** [34025807972](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972) at `75c4a8b`
  validates the shared JVM regressions, Android debug build and probe/Desktop
  compilation. The first run's four Home interpolation errors were fixed;
  fresh Quick picks now pass through the screen's real category projection.
  Local Gradle still cannot start without a JDK. PR #30 is merged;
  actual Windows upgrade/playback/visual verification remains open in
  `docs/verification/14-release.md`.

### Added
- **Desktop bounded audio cache** — whole-track LRU file cache for the vlcj
  player (`AudioFileCache`, `<data dir>/cache/audio`), filled in the
  background during first play; cached tracks play from disk without a
  stream resolve, so they work offline. Same `cache_size_mb` budget as
  Android (default 1 GiB).
- **Android bounded audio-segment cache** — Media3 `SimpleCache` LRU keyed
  by video id (survives stream-URL rotation / 403 recovery); offline
  replay of already-downloaded spans when resolve fails.
- **Robustness UX (Phase 14)** — typed error taxonomy end-to-end, offline
  banner (platform `ConnectivityMonitor`), process-wide 429 backoff gate,
  and a visible "Reconnecting…" state during 403 mid-stream recovery.
- **Daily rot drill** — `rot-drill.yml` runs the playback probe against
  live YouTube on the production resolver chain, watches each engine
  separately, and opens an issue on failure. Latest verified scheduled
  run 34011539225 is red (production Unavailable; yt-dlp WATCH AuthRequired).
  Windows audio also failed; do not dismiss it as CI-only gating.
- **Material 3 glass UI** — frosted (not blurred-content) chrome across
  Home, Search, Library, browse pages and the player; sans typography;
  artwork-driven ambient wash; lyrics-dominant full player (ADR-002).
  Explicitly *not* Apple "Liquid Glass".
- **Android native polish (Phase 13)** — edge-to-edge insets, app
  shortcuts (Search / Resume / Library), battery-optimisation rationale +
  handoff, rotation/back-stack state restore, 840 dp navigation rail.
- **Desktop native (Phase 12)** — system tray with playing/paused icons,
  keyboard shortcuts (Space, ←/→ 5 s, Ctrl+←/→, Ctrl+F, Ctrl+Q),
  close-to-tray with remembered window geometry, Windows SMTC (JNA/WinRT),
  MSI packaging. The separate always-on-top mini-player and Ctrl+M were
  subsequently removed in accepted ADR-004; the docked MiniPlayer remains.
- **Foreground media service + OEM resilience** on Android; all
  `MediaController` calls marshalled to the main thread.
- **Lyrics (Phase 11)** — synced lyrics via LRCLIB with YouTube Music text
  fallback, persisted cache (schema v2), tap-to-seek.
- **Library & History (Phase 10)**, **Artist / Album / Playlist pages
  (Phase 09)**, **Mini + Full player (Phase 08)**, **Home & Search
  (Phase 07)**, **design system with real blur** (Phase 06), **SQLDelight
  data layer** (Phase 05), **Desktop vlcj skeleton** (Phase 04),
  **Android Media3 skeleton with lock-screen controls** (Phase 03),
  **provider & domain core** (Phase 02), **extraction spike** (Phase 01).

### Changed
- Extraction resolver chain expanded to a wider tokenless player-client
  set (own client primary, yt-dlp failover on desktop — ADR-001).
- CI now compiles `app-desktop` on every PR (previously only on `main`'s
  MSI job).
- **Windows MSI is per-user** (`perUserInstall`, no admin UAC). Runtime
  data (SQLite + audio cache) lives in `<installDir>/userdata` so
  Settings → Apps → Uninstall removes it. No leftover `%APPDATA%\DHUN`.
  Android already wiped private storage on uninstall; the manifest now
  also sets `hasFragileUserData=false` and `usesCleartextTraffic=false`.
- Now Playing transport cluster tightened (compact 72 dp row, edges
  aligned to the title/seek column); Home quick-action chips use the same
  padded `LazyRow` pattern as the mood chips so edge insets hold while
  scrolling.

### Fixed
- **Android startup splash** — the connecting screen no longer prints raw
  attempt/log lines; brand + activity indicator + static "Initializing
  audio engine…" with a subtle corner version (diagnostics stay in Logcat).
- **Android playback hardening** — per-segment load retries raised for
  stall-heavy mobile networks; a corrupt segment-cache dir now degrades
  to direct streaming (service + fallback paths) instead of killing all
  audio; failed artwork loads settle to a static placeholder instead of
  pulsing forever.
- **Dialog sheets** — overflow + add-to-playlist sheets use 28 dp radii
  with a faint edge (`GlassCard.borderColor`) so they melt into the dark
  glass instead of drawing a hard boundary.
- **Android playback recovery (APK "Error — tap to see")** — transient
  ExoPlayer failures (expired-URL 403s, timeouts, dropped connections,
  resolve failures) now auto-recover with a bounded re-resolve
  (invalidate → seek → prepare, `playWhenReady` restored, backoff on
  repeat hits) instead of latching a permanent error; the mini-player
  error tap opens a diagnosis dialog with Retry, the FullPlayer shows an
  inline error banner with Retry, and the play button routes through
  recovery instead of sitting dead in error-idle.
- **Now Playing artwork sharpness** — parsers keep the largest thumbnail
  entry (was: smallest-first `w60`), proxy size params rewrite
  generically, and the FullPlayer stage + backdrop load the 1024 tier
  (one shared Coil key, fetched once); cards keep the 544 tier.
- SQLDelight IO serialised — resolved a `NowPlayingPersistenceTest` hang
  and a queue-save write race.
- `onRateLimited` made `suspend` so the 429 gate actually trips.
- Assorted Compose Desktop 1.8.2 / JNA 5.17 / Media3 1.5.1 API corrections
  found by CI.

### Removed
- **Separate desktop mini-player window** — the 320×88 always-on-top second
  window (Phase 12) opened at every launch is gone per user decision
  (ADR-004): the docked in-app mini-player above the bottom nav is the
  product's mini-player. The window, its Ctrl+M toggle, and the
  mini-player-only `Smct.moveWindow` helper were deleted; desktop DHUN is a
  single-window app.

### Known limitations
See `.ai/KNOWN_LIMITATIONS.md` (honest > complete). Highlights: Web is not
a v1 platform; SMTC round-trip unverified on hardware; blur floor is
Android 12+ / Skiko; desktop first-play spends bandwidth twice (stream +
cache fill); cache budget changes apply on next start.

[Unreleased]: https://github.com/99ggprooo00-code/DHUN/compare/290e0f6...HEAD
