# KNOWN_LIMITATIONS

Updated every phase. Nothing hidden.

- Web platform intentionally absent from v1 (browser YouTube streaming is
  blocked by PO tokens/SABR for third-party apps — see
  PROBLEMS_AND_FIXES.md P7).
- Stream extraction depends on maintained upstream extractors; when YouTube
  changes, playback breaks until a patch release. The daily rot-drill CI
  detects this within 24h.
- NewPipeExtractor v0.26.5 stream extraction is broken upstream-unfixed
  (2026-09-01). Desktop streams go through yt-dlp; Android uses DHUN's
  minimal own player-endpoint client until upstream recovers (ADR-001).
- Datacenter/server IPs are bot-flagged by YouTube's player endpoint more
  aggressively than residential IPs; the rot drill may show resolve-step
  failures on CI runners that do not affect normal users. Repeated red +
  local green = investigate; both red = rot.
- YTM lyrics via InnerTube are unsynced text only; synced lyrics are now via LRCLIB fallback (`LrcLibSource` + `LyricsRepository` cache→YTM→LRCLIB, see Lyrics bullet above) — YTM remains primary unsynced fallback.
- The PLAYLISTS search filter returns mixed result types from YouTube;
  classification routes them by browseId prefix (harmless, refined later).
- The shared module currently builds the JVM target only; the Android target
  (same commonMain sources) is added with the AGP/SDK setup in Phase 03.
- Android: harness UI is a throwaway Compose screen (replaced in Phase 06+);
  app icon is a framework placeholder until the design phase.
- Android: stream resolution is the own-client only (ADR-001). On networks
  where YouTube gates WEB_REMIX player calls, playback shows a typed
  "needs signed-in session" error instead of audio until upstream engines
  are drill-green.
- Desktop (Phase 04): module is in the build; CI configures it on every
  run but only compiles it once the owner adds the workflow step (agent
  token lacks `workflows` permission — see docs/verification/04-desktop.md).
  Installer `packageVersion` is 1.0.x (Compose packagers reject MAJOR 0).
- Data layer (Phase 05+11): schema is now v2 (v1 + `LyricsCache` via `migrations/1.sqm`); the DB file is
  `dhun.db` (Android app data dir; desktop per-OS user data dir). Restored
  sessions come back **paused** at the saved position — stream URLs expire,
  so the desktop player re-resolves lazily on the first play press.
  Playback history is local-only; nothing leaves the device.
- Desktop (Phase 04) runtime needs a system libVLC install and `yt-dlp` on
  PATH (streams resolve own-client first, yt-dlp failover — ADR-001).
- Design system (Phase 06): `GlassCard` uses `Modifier.blur()` / `RenderEffect`
  on Android 12+ (API 31+) and Desktop Skiko; below that floor it degrades to
  a translucent scrim (`DhunColors.glass` 60% #99111111 + 10% white border) — still
  glassy but not blurred. Verified via `ComponentCatalogScreen` over a gradient
  backdrop; screenshot pending. The design tokens are the single source of
  truth; throwaway harness screens still contain raw hex/dp (they are deleted in
  Phase 07 when real Home/Search replace them — not counted as production code).
- Player UI (Phase 08+11): FullPlayer background blur has the same <API 31 floor —
  below it the artwork sharpens and the scrim carries legibility. Swipe-remove
  is horizontal-drag–based so it works with a mouse on desktop as well as touch.
  Volume slider is desktop-only; Android relies on hardware volume keys.
  Lyrics tab now syncs (Phase 11 LRCLIB + cache → YTM → LRCLIB) — see Lyrics bullet for karaoke/rate-limit caveats.
- Browse pages (Phase 09): artist/album/playlist parsers cover the current
  YTM browse layouts (single/two-column, legacy + responsive headers); the
  companion fixtures are schema-authored in the sandbox (YT egress blocked
  there) and scheduled for live re-capture in the Phase 09 hardware pass on a
  network-capable machine — the rot drill guards drift. "Videos" shelves on
  artist pages are intentionally skipped in v1.
- Lyrics (Phase 11): FullPlayer Lyrics tab now has synced lyrics via LRCLIB fallback (`shared/lyrics/LrcLibSource` → `GET https://lrclib.net/api/get?artist_name=&track_name=&album_name=&duration=`, parsed by `LrcParser`). `LyricsRepository` is `cache → YTM → LRCLIB → NotAvailable` with `LyricsCache` table (SQLDelight schema v2, `migrations/1.sqm`, `dhun.db`) — `Synced`/`Unsynced` cached, `NotAvailable` not cached (so lyric-less tracks still hit network each open, ~600 ms). `LrcParser` handles `[mm:ss.xx]`/`[mm:ss.xxx]`/`[mm:ss]`, multi-timestamp (`[00:10.00][00:12.00]Repeat` → 3 lines), strips enhanced `<mm:ss.xx>` word timings to line granularity (per-word karaoke deferred), skips metadata `[ti:/ar:/al:/by:]`, sorts by `startTimeMs`; blank lines kept as `` → UI shows "♪". `LyricsTabContent` shows active line `titleMedium` bright centered with smooth `animateScrollToItem`, tap line → `seekTo(startTimeMs)` (±0 s, within ±1 s spec), unsynced as scrollable `Text`, empty as `EmptyView`. LRCLIB is a volunteer service — 429 maps to `NotAvailable` (YTM remains unsynced fallback, no retry banner in v1). Cache re-serializes synced as `[mm:ss.cs]` (centisecond, 10 ms rounding for 3-digit inputs — audibly negligible). Very similar title/artist covers may return wrong LRC (LRCLIB fuzzy) — no client-side pick in v1.
- Library & History (Phase 10): bottom nav now has a dedicated **Library** tab (Home/Search/**Library**/Catalog) — `shared/ui/library/LibraryScreen` with three pill tabs. History groups by local calendar day via `GetHistoryUseCase.groupByDay` using `TimeZone.getDefault().getOffset(now)` (raw+ DST) — groups reflect the device's current offset at query time (travel-day history groups by the new zone — same tradeoff documented in `docs/verification/10-library.md`). The ViewModel caps the observed history at 300 most-recent rows for list virtualization (the DB retains all; raise if users hit the cap). Favorites are ordered `addedAt DESC` (newest ♥ on top); drag-reorder handle is shown but is a no-op in v1 — explicit `favorite_position` will be added if users request manual ordering. `RecordPlay` contexts are now wired: Home→`HOME`, Search→`SEARCH`, Artist→`ARTIST`, Album→`ALBUM`, Playlist→`PLAYLIST`, Related/Radio→`QUEUE`, Library→`LIBRARY`, History→`HISTORY` (via `PlayerViewModel.setPlayContext` + `LibraryViewModel` lambda). “Albums/Artists saved” tabs are deferred — schema has `Track.albumId/artistId` but no `SavedAlbum`/`SavedArtist` tables; those tabs will be added when typed saves land. Empty states for all three Library tabs use `EmptyView` (no spinner).
- Desktop native (Phase 12, in progress): system tray uses AWT `SystemTray`
  (works Windows/Linux/macOS; silently absent on headless — app keeps
  working). **SMTC spike is phase 1 only**: it proves WinRT activation +
  `GetForWindow` from the unpackaged JVM (probe logs `SMTC probe
  PASS/FAIL — …` at startup, disable with `-Ddhun.smct=false`); the
  now-playing tile (`UpdateMetadata`) and media-key handling
  (`ButtonPressed` events) are spike phase 2 and need one round trip on a
  Windows machine to pull two IIDs from the system winmd (procedure in
  `docs/verification/12-desktop-native.md`). **Until then, hardware media
  keys do NOT drive DHUN** — transport is available via tray menu,
  keyboard shortcuts (Space/←→/Ctrl+←→/Ctrl+F/Ctrl+M/Ctrl+Q) and the
  mini-player window; this is the documented RISK_REGISTER fallback path.
  Close-to-tray is on by default (`SettingsKeys.CLOSE_TO_TRAY`); the
  mini-player window starts visible (Ctrl+M or its X hides it — hiding,
  not closing, so it can always come back). Window geometry
  (`SettingsKeys.WINDOW_GEOMETRY`) persists across restarts.
  The mini-player window shows in the OS taskbar (Compose Desktop 1.8.2's
  `Window` has no `skipTaskbar` parameter — a 1.9+-era API; hiding via
  Ctrl+M/X is the supported way to get it out of the way).
  jpackage installers: `packageVersion` stays 1.0.x (packager rejects
  MAJOR 0); clean-VM install test OPEN on hardware.
