# KNOWN_LIMITATIONS

Updated every phase. Nothing hidden.

## Latest Windows result / merged repair — 2026-09-06

The user's negative report (install-over fails “Another version…”, audio
fails, Home does not page, player glyph placement/shuffle/colour styling not
accepted) concerned the old `test@0920148`, 07:22:29Z build. That is now
superseded: the repair code merged through PR #30 (`76c68eb`) and the branch
accumulated through PR #32 (`862f0ac`), with main CI 34072908037 / test-release
34072908097 passing, and the rolling `test` pre-release published 2026-09-07T01:29:28Z
at `862f0ac` (MSI 112,136,192 B, APK 17,516,190 B). The user-machine re-test
against this *newer* build is still required; the earlier failure cannot be
explained away as Actions-IP-only gating.

Repairs are **merged in PR #30**; the session branch is retained. Code CI passes on `b6d47bd` (branch 34030728903 / PR 34030730736).
Native packaging run **34030730743** also passes: MSI **1.34.1**, actual
version/upgrade identity, published-1.0.5 install-over with userdata/cache
sentinels preserved, upgrade-flag removal preserving data, and explicit
uninstall cleanup after reinstall. Main publishing repeated these checks
successfully for MSI 1.36.1 and uploaded APK/MSI/checksums to `test`.

The first native test caught real userdata deletion in MSI 1.33.1. That
artifact was withheld and the PR not merged. The installer finalizer now
adds an upgrade-only cleaner-property guard plus a narrow legacy HKCU
cleanup bridge; it uses built-in Windows PowerShell without execution-policy
bypass, and never moves/deletes userdata itself. Raw `packageMsi` output is
not safe to distribute without `stage_msi.ps1` finalization. Back up user
data before testing. If an old-version upgrade is cancelled after legacy
preparation, its old cleanup registration can remain suppressed until a
successful retry; this preserves data but is not perfect rollback hygiene.

The sandbox still cannot run Java locally. The manual dispatch API remains
403-denied and is not retried; normal PR/main workflows supplied native
verification and rolling test publishing. The publisher still reports a
Node-20 deprecation warning for download-artifact v4; this is an action-runtime
maintenance item, not a compiler failure or a claimed zero-warning audit.
**No app launch, actual VLC/audio playback, live Home/user visuals, media-key
integration or soak acceptance is claimed.** Installer sentinels on a hosted
Windows runner are not the user's complete hardware test.

The user-provided Windows yt-dlp installation state is unknown. The old
locator could miss an installed `yt-dlp.exe`; the new candidate checks PATH /
`DHUN_YTDLP` and provides explicit missing-tool evidence. yt-dlp remains
optional/user-provided, not bundled; no cookies, login or PO-token minting was
added (ADR-003 is now ACCEPTED — Option C staged-wave fan-out — which only
changes the *scheduling* of the existing tokenless identities, never their
membership/order; no credentials are introduced).

MSI ProductVersion must advance independently of the app's semantic version.
The candidate keeps the stable upgrade UUID and uses a run/attempt sequence
(plus a stale-ref publishing guard). Manual packaging must also supply a
higher internal version; future stable packaging must not reset it to 0.1.0.
Hosted-Windows install-over/data sentinels pass; the user-machine upgrade and real library/queue preservation still need a re-test.
See `docs/verification/12-desktop-native.md` and `14-release.md` for evidence.

## Platform and service limitations

- Web platform intentionally absent from v1 (browser YouTube streaming is
  blocked by PO tokens/SABR for third-party apps — see
  PROBLEMS_AND_FIXES.md P7).
- Stream extraction depends on maintained upstream extractors; when YouTube
  changes, playback breaks until a patch release. The daily rot-drill CI
  detects this within 24h.
- The pinned NewPipeExtractor v0.26.5 remains a non-fatal recovery watch,
  not the production primary. Both platforms use the own player-client
  chain; Desktop adds the optional yt-dlp fallback (ADR-001). No live
  evidence of NewPipe recovery was verified in this session.
- Datacenter/server IPs are bot-flagged by YouTube's player endpoint more
  aggressively than residential IPs; the rot drill may show resolve-step
  failures on CI runners that do not affect normal users. Repeated red +
  local green = investigate; both red = rot.
- **CI-network vs residential gating (measured 2026-09-05):**
  - Run **33961533965** / **33968612285** (`main@a554594`, yt-dlp-only probe):
    yt-dlp 2026.08.19 bot-gated ("Sign in to confirm" → `AuthRequired`) from
    the Actions runner while metadata PASS.
  - Run **33968950214** (`arena/01a07170-dhun@10ad025`, production chain):
    **both** `OwnClientStreamResolver` (web_remix + visionos + tv all
    `AUTH_REQUIRED`) **and** yt-dlp 2026.08.19 bot-gated from the same
    runner class; metadata (version/search/related) still PASS; NewPipe
    still `Parse(JSON too short)`. Full `AuthRequired.detail` now rides
    along. This is stronger CI-network evidence: Android's only engine and
    desktop's primary+fallback are all gated from GitHub-hosted runners as
    of 2026-09-05.
  - Those are historical runner observations, not proof that IP class is
    the only cause. Latest scheduled run **34011539225** (2026-09-06,
    `dd1ab31`) reports production/own Unavailable, yt-dlp WATCH AuthRequired,
    metadata PASS; issue #14 remains open. The fresh Windows failure is
    independent user-impact evidence. Keep the kill switch and byte
    validation; no cookies/sign-in without an ADR + user sign-off.
- **Rot-drill probe coverage (fixed and live-proven 2026-09-05):** the
  fatal resolve step now drives the production own-client→yt-dlp chain and
  emits `WATCH|own-client` / `WATCH|ytdlp` / `WATCH|newpipe-stream`. Run
  33968950214 confirmed those lines fire on CI.
- YTM lyrics via InnerTube are unsynced text only; synced lyrics are now via LRCLIB fallback (`LrcLibSource` + `LyricsRepository` cache→YTM→LRCLIB, see Lyrics bullet above) — YTM remains primary unsynced fallback.
- The PLAYLISTS search filter returns mixed result types from YouTube;
  classification routes them by browseId prefix (harmless, refined later).
- The shared module now builds **Android and JVM** targets; real Home,
  Search, Library, browse and player UI replaced the original harness.
  Android/native installer app-icon acceptance remains separate from
  the in-app vector icon repairs.
- Android: stream resolution is the own-client only (ADR-001). The
  2026-09-05 chain tries WEB_EMBEDDED → VISIONOS → TV → TV_DOWNGRADED →
  TV_SIMPLY → MWEB → WEB_REMIX (tokenless, no cookies). On networks where
  every identity is gated, playback shows a typed AuthRequired error with
  per-client detail instead of audio. Rot-drill 33968950214 showed the
  previous 3-identity chain fully gated from Actions IPs; residential
  impact is verified on device, not assumed from CI red.
- Android background playback (Phase 1 directive, 2026-09-05): the
  `MediaSessionService` is now a genuine FOREGROUND service (mediaPlayback
  type) with the live media notification
  (`MediaStyleNotificationHelper.MediaStyle(session)`), and the app asks
  the user once per process for the battery-optimization exemption
  (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) while in use. What Android does
  NOT let any app request programmatically: MIUI/HyperOS "Auto-start" +
  "lock in recent apps", and OneUI "Unrestricted" battery setting — those
  are manual per-device toggles. If background music still stops on a
  user's device, that is the cause (checklist:
  docs/verification/03-android-skeleton.md). Until toggled, aggressive OEM
  cleaners can still kill the service.
- Desktop compilation is part of current CI (verified green at 34018809911),
  and MSI packaging is green at 34018809913. These do not verify native
  playback/integration or the unpublished candidate. Packagers require a
  positive-major native version; the old fixed 1.0.5 prevented rolling upgrades.
- Data layer (Phase 05+11+14): schema is now **v3** (v1 + `migrations/1.sqm` `LyricsCache` + `migrations/2.sqm` `DownloadedTrack`); the DB file is
  `dhun.db` (Android app-private storage — deleted with the app;
  desktop packaged = `<installDir>/userdata`, also deleted with the
  MSI/DMG/DEB; desktop `gradle run` = `%APPDATA%\DHUN` /
  `~/Library/Application Support/DHUN` / `~/.local/share/dhun`). Restored
  sessions come back **paused** at the saved position — stream URLs expire,
  so the desktop player re-resolves lazily on the first play press.
  Playback history is local-only; nothing leaves the device.
- Desktop runtime needs system libVLC. The optional yt-dlp fallback is a
  separate user-provided executable/module (PATH or `DHUN_YTDLP`); it is not
  installed with VLC. Missing fallback is diagnosed in the candidate, not
  silently classified as an offline network.
- **Visual system lock (2026-09-05):** Material 3 only. **No Liquid Glass**
  renderer, no continuous full-res reblur. Atmosphere = **glass-morphism**
  tokens (translucent multi-stop fills, sheen, hairline edge) on chrome;
  content stays sharp. Real backdrop blur only on FullPlayer artwork layer
  (once-per-track via `BlurredArtworkCache`) + lightweight shell ambient
  wash from now-playing seed colors.
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
- Desktop native (Phase 12, code complete; hardware open): system tray uses
  AWT `SystemTray` (works Windows/Linux/macOS; silently absent on headless —
  app keeps working). SMTC phase 2 now activates WinRT for the AWT HWND,
  updates title/artist/album plus a best-effort remote thumbnail, mirrors
  playback and queue-button state, and registers `ButtonPressed` through a
  retained base-JNA COM callback. Startup logs the full HRESULT-guarded
  `SMTC probe PASS/FAIL — … phase2=ok/FAIL (…)` line; `-Ddhun.smct=false`
  disables it. **This integration is not hardware-verified in the sandbox**:
  Windows media-key round trip, lock/quick-settings tile, remote thumbnail,
  tray, close-to-tray, geometry, and clean MSI install remain
  open in `docs/verification/12-desktop-native.md`. If activation or event
  registration fails, the app intentionally remains usable through the tray
  and keyboard shortcuts (Space/←→/Ctrl+←→/Ctrl+F).
  Close-to-tray is on by default (`SettingsKeys.CLOSE_TO_TRAY`).
  **The separate 320×88 mini-player window was removed on 2026-09-06 per user
  decision (ADR-004)** — the docked in-app MiniPlayer above the bottom nav is
  the product's mini-player; the Ctrl+M toggle went with the window, so the
  desktop app now opens exactly one window; the latest user report confirms
  this after manual reinstall. Window geometry
  (`SettingsKeys.WINDOW_GEOMETRY`) persists across restarts. jpackage MSI is **per-user** (`perUserInstall`, upgradeUuid
  `31ddb86b-9666-4071-b11c-45f16fa4682d`), not Authenticode-signed (SmartScreen
  warn is expected). Runtime data is `<installDir>/userdata` so Apps-and-Features
  uninstall is intended to remove DB + audio cache (clean-target cleanup
  evidence remains open). Published ProductVersion is 1.0.5; dynamic
  candidate versions are local, and upgrade/data preservation is unverified.
  **Phase 14 Windows JVM fix (2026-09-06, main@e90dba6, PR #22):** MSI
  `dhun-test.msi` installed but launch showed \"Failed to launch JVM\".
  Root causes: (1) bundled jlink runtime omitted `java.sql` (and
  `jdk.unsupported`/`java.naming`) needed by SQLDelight/sqlite-jdbc —
  the Compose plugin does not auto-detect modules; (2) `DesktopDhunPlayer`
  eagerly constructed `MediaPlayerFactory` before the window, so a
  missing VLC crashed startup. Fixed in `app-desktop/build.gradle.kts`
  via `modules(\"java.sql\", \"java.sql.rowset\", \"java.naming\",
  \"jdk.unsupported\", …) + includeAllModules=true` and `packageVersion`
  `1.0.5`; `DesktopDhunPlayer` now degrades to an `Error` state with
  install-VLC guidance; `Main.kt` logs every startup exception to
  `<installDir>/userdata/dhun-startup.log` (fallback `%TEMP%`) and
  shows an AWT dialog + minimal error Window so the MSI user sees the
  real cause. `DataLayer` now falls back to in-memory DB on file-DB
  corruption. MSI `1.0.5` built at `34011563630` (112 MB, `includeAllModules`);
  the user subsequently confirmed launch (and now one-window startup after
  manual reinstall). Clean-target/tray/log verification remains OPEN;
  failed install-over and failed audio are separate current blockers.

## Phase 14 — ADR-006 persistent offline downloads (2026-09-07)

- **Foundation + engine implemented and CI-green on PR #33 (`a1064b7`):**
  schema v3 `DownloadedTrack` table (`migrations/2.sqm`), `DownloadRepository`
  + `SqlDelightDownloadRepository` wired as `DataLayer.downloads`,
  `DownloadStorage` file abstraction, `StreamDownloader` (Ktor, Range-resume,
  progress, resolving User-Agent isolation, cancellation keeps the `.part`),
  `DownloadManager` + `FileDownloadManager` (bounded 3-slot pool, atomic
  `.part`→final commit, best-effort artwork, QUEUED/DOWNLOADING/COMPLETED/
  FAILED/PAUSED state transitions), and jvmMain `JvmDownloadStorage`. Tests
  `DownloadRepositoryTest`/`StreamDownloaderTest`/`FileDownloadManagerTest`
  are green in `:shared:jvmTest`.
- **Not yet user-facing:** there is **no UI to enqueue a download yet**, no
  Library "Downloaded" section, no download-action/badge, and no storage
  management screen. `DownloadManager` is a tested library component, not a
  wired app feature.
- **Offline-first playback is NOT wired and would NOT yet play a downloaded
  track.** Resolving to a local `file://` requires the platform byte layer to
  read local files: Android uses a `ResolvingDataSource` over an HTTP/cache
  `DataSource`, so a `file://` resolved URL would fail unless the
  `MediaItem`/data source route is updated (e.g. `FileDataSource` /
  `DefaultDataSource`) to handle a local path; the Desktop player loads remote
  MRLs via vlcj and would need a local-path load first. This platform routing
  is the immediate next step and is **device/hardware verified** only after it
  lands. Green CI does **not** mean offline playback works.
- Downloads are **persistent** (survive until the user deletes them) and are
  distinct from the ADR-005 LRU stream cache, which is volatile and
  evicts on budget.

## Phase 14 — robustness / rot-drill / release (2026-09-05)

- The daily live extraction workflow is wired and **failure path is
  live-proven** on the fixed branch (run 33968950214). It has **not**
  produced a green `PROBE|verdict|PASS` from GitHub-hosted runners. Latest
  scheduled failure is 34011539225 (production Unavailable, yt-dlp WATCH
  AuthRequired). User Windows audio also fails; successful playback and
  validated bytes on a real user network remain gates, not assumptions.
- Android currently caches resolved stream URLs for five hours and invalidates
  them on HTTP 403. **Android audio-segment cache** (Phase 14) is now in
  code: Media3 `SimpleCache` LRU under `cacheDir/audio-segments`, default
  1 GiB (`SettingsKeys.CACHE_SIZE_MB`), stable keys = video id, offline
  replay of already-downloaded spans when resolve fails. Hardware offline
  check OPEN. **Desktop audio cache** (Phase 14, `AudioFileCache` in
  `shared/jvmMain`, wired into `DesktopDhunPlayer`): libVLC has no
  data-source layer, so desktop caches **whole tracks** (`<data dir>/cache/
  audio/<videoId>.audio`, LRU by last-used, same `CACHE_SIZE_MB` budget).
  Consequences: a first play streams AND downloads (bandwidth ×2 for that
  track); a track only becomes offline-playable once the background fill
  completes (skipping mid-track cancels the fill, nothing is kept); cache
  hits play the local file with no resolve. Desktop offline check on a real
  machine OPEN. Cache budget changes apply on next process start (both
  platforms). The rolling `test` APK/MSI is not the signed
  stable `v0.1.0` release.
- Phase 14 Android/Desktop soak tests, clean-target installation checks, and
  release evidence remain open because this environment has no Android device,
  OEM runtime, Windows machine, libVLC runtime, or display.
