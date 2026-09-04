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
- YTM lyrics via InnerTube are unsynced text only; synced lyrics arrive in
  Phase 11 via LRCLIB.
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
- Data layer (Phase 05): schema is v1 with no migrations yet; the DB file is
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
- Player UI (Phase 08): FullPlayer background blur has the same <API 31 floor —
  below it the artwork sharpens and the scrim carries legibility. Swipe-remove
  is horizontal-drag–based so it works with a mouse on desktop as well as touch.
  Volume slider is desktop-only; Android relies on hardware volume keys.
  Synced-lyrics UI exists but lines only sync after Phase 11 (LRCLIB).
- Browse pages (Phase 09): artist/album/playlist parsers cover the current
  YTM browse layouts (single/two-column, legacy + responsive headers); the
  companion fixtures are schema-authored in the sandbox (YT egress blocked
  there) and scheduled for live re-capture in the Phase 09 hardware pass on a
  network-capable machine — the rot drill guards drift. "Videos" shelves on
  artist pages are intentionally skipped in v1.
