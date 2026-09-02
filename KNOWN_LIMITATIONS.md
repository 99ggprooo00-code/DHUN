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
- Desktop (Phase 04): module code is committed but CI-opt-in —
  `include(":app-desktop")` is commented in settings.gradle.kts pending a
  toolchain fix; applying a second Kotlin Gradle plugin flavor (kotlin
  "jvm" or org.jetbrains.compose) alongside :shared's kotlin
  "multiplatform" breaks Gradle configuration of every task in this repo's
  CI (full bisection evidence: docs/verification/04-desktop.md). Runs
  locally via `./gradlew :app-desktop:run` after uncommenting.
- Desktop (Phase 04) runtime needs a system libVLC install and `yt-dlp` on
  PATH (streams resolve own-client first, yt-dlp failover — ADR-001).
