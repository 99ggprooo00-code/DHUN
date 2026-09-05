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
has no version number by design.

The maintenance contract applies to every entry below: stream extraction
rots; when it breaks, DHUN ships a patch release fast (see README and
`.ai/RISK_REGISTER.md`).

## [Unreleased]

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
  separately, and opens an issue on failure. (From GitHub-hosted runners
  both engines are currently bot-gated — category 8 network evidence, not
  extractor rot; residential verification is the user-impact gate.)
- **Material 3 glass UI** — frosted (not blurred-content) chrome across
  Home, Search, Library, browse pages and the player; sans typography;
  artwork-driven ambient wash; lyrics-dominant full player (ADR-002).
  Explicitly *not* Apple "Liquid Glass".
- **Android native polish (Phase 13)** — edge-to-edge insets, app
  shortcuts (Search / Resume / Library), battery-optimisation rationale +
  handoff, rotation/back-stack state restore, 840 dp navigation rail.
- **Desktop native (Phase 12)** — system tray with playing/paused icons,
  always-on-top mini-player window, keyboard shortcuts (Space, ←/→ 5 s,
  Ctrl+←/→, Ctrl+F, Ctrl+M, Ctrl+Q), close-to-tray with remembered window
  geometry, Windows SMTC spike (JNA/WinRT), MSI packaging.
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

### Fixed
- SQLDelight IO serialised — resolved a `NowPlayingPersistenceTest` hang
  and a queue-save write race.
- `onRateLimited` made `suspend` so the 429 gate actually trips.
- Assorted Compose Desktop 1.8.2 / JNA 5.17 / Media3 1.5.1 API corrections
  found by CI.

### Known limitations
See `.ai/KNOWN_LIMITATIONS.md` (honest > complete). Highlights: Web is not
a v1 platform; SMTC round-trip unverified on hardware; blur floor is
Android 12+ / Skiko; desktop first-play spends bandwidth twice (stream +
cache fill); cache budget changes apply on next start.

[Unreleased]: https://github.com/99ggprooo00-code/DHUN/compare/290e0f6...HEAD
