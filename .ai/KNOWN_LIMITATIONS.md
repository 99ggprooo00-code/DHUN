# KNOWN_LIMITATIONS

Updated every phase. Nothing hidden.

## 2026-09-10 — the daily rot-drill is **not running**, and its "red" runs are 0-job noise

Two separate things were conflated across many sessions and are separated here:

- **Noise:** `.github/workflows/rot-drill.yml` has only `schedule` +
  `workflow_dispatch` triggers. The red `rot-drill` entries on pushes and PR
  branches (`104`–`113` on 2026-09-10 alone) are GitHub's **no-matching-trigger
  runs with 0 jobs** — they carry no probe verdict and must never be cited as
  extraction evidence. (`…/actions/runs/<id>/jobs → total_count: 0` is the test.)
- **Real gap:** the last `event=schedule` run is **#10, `2026-09-07T04:28Z`
  (failure, `d1e0408`)**, and #2/#3/#4 (09-03…09-05) were **green**. **Three
  daily slots (09-08, 09-09, 09-10) never fired** although the workflow is
  `state=active`. Cause is not determinable from the API, so it is recorded as
  open, not explained away. Impact: the maintenance contract's "breakage
  detected within 24 hours" is not currently held, and a red live extraction
  (issue #14) could sit unnoticed for days.
- **Who can act:** an agent cannot — `gh workflow run` returns **HTTP 403** for
  this session's token. A human should press **Run workflow** on `main` and
  check Settings → Actions (schedule/allow-list). Until a green-or-red
  **scheduled** verdict exists, "drill is red because of issue #14" is a claim
  about run `34011539225`/`#10`, not about today.

## 2026-09-10 — Android auth-gating is **not** fixed; PR #55's session fields are inert, and `main` was red

**Do not read "the visitorData PR merged" as "playback repaired".** Verified by reading
the call sites, not the PR title:

- `InnerTubeClient.altPlayerResponse(videoId, alt, visitorData = null, signatureTimestamp = null)`
  — **every one of the six call sites in `extraction/OwnClientStreamResolver.kt` passes
  neither**, so `context.client.visitorData`, `contentPlaybackContext.signatureTimestamp`
  and `X-Goog-Visitor-Id` are absent from every request. Android's failure mode is
  unchanged: on bot-gated networks YouTube answers `LOGIN_REQUIRED` →
  `DhunError.AuthRequired("Sign in to confirm you're not a bot")` (issue #14, and the
  user's own Windows residential network fails the same way — this is **not** a
  CI-IP-only problem).
- The correct way to *obtain* a visitor session / attestation is a **decision, not a
  patch**: `docs/decisions/ADR-007-android-stream-attestation.md` (status **PROPOSED**,
  drafted on open PR #54) lays out options A–D. Per ADR-001/ADR-003 the chain is locked
  tokenless, so nothing here may be improvised — an ADR + the user's OK come first, and a
  live probe verdict + on-device audio come after. Until then the fix is a typed, immediate
  error (`ResolveOutcomeLog`, PR #50), never a fake token.
- `signatureTimestamp` is typed `String?` and emitted as a JSON string. YouTube's
  `contentPlaybackContext.signatureTimestamp` is conventionally a number (yt-dlp sends an
  integer). Left as-is on purpose: with no caller and no live capture, choosing the wire
  type would be guessing. Settled together with ADR-007 and pinned by a test then.
- **Resolve Wave 1 shrank** (PR #55): `[web_embedded, visionos]` → `[visionos]`.
  `web_embedded` is still in `STRATEGIES` but unreachable from every wave, so it is
  retained-but-untried, and `WEB_EMBEDDED_PLAYER` (with its `thirdParty.embedUrl`) now only
  runs if a wave is re-cut. Consequence: fewer identities before the 429/timeout cascade,
  and one less identity in the per-identity diagnostic string.
- **`main` build health** WAS red from `073083c` (merged as `6e4d057`, `2026-09-10T03:10Z`)
  through `58d9ac8`: `:shared:compileKotlinJvm` / `:shared:compileDebugKotlinAndroid` failed
  in `InnerTubeClient.kt`, so `build-and-test`, `apk`, `msi`, the new `Build APK` workflow
  and `rot-drill` were all red, and `rot-drill`'s probe verdicts on those SHAs are
  **worthless** (it builds the app before it probes). The rolling `test` build is frozen at
  `cd97464` (`2026-09-10T02:02:44Z`), i.e. PRs #51/#52 are merged but unpublished.
  **Repaired and merged as `be51d7d` (PR #56) on `2026-09-10T04:21:16Z`**; `main` CI
  `34436859375`, test-release `183` (`apk`/`msi`/`publish`) and `Build APK` run `6` are
  green, and the rolling `test` build now ships `be51d7d` (published `04:25:25Z`) — i.e.
  #51, #52 and the docs pushes are finally in a downloadable artifact.
- **Environment trap, restated because it is the actual cause:** this sandbox has no JDK and
  no Maven/Gradle or Actions-log egress, so **CI is the only compiler and check-run
  annotations are the only log**. Pushing code that has never been compiled is normal here;
  merging it is not. `gh pr checks <n>` must be read before every merge — a `MERGEABLE`
  PR with red gates is exactly what produced this incident.

## 2026-09-07 — Phase 15 Android polish (`arena/01a07ad8-dhun`) alongside 15a player immersion (`arena/01a07a6b-dhun`)

**Status after the split: 15a is on `main`, Phase 15 is not finished.** The player batch
merged as `dd0fe14` (head `cd40c1f`, `build-and-test`/`apk`/`msi` green); the Android half
is PR #43, green at `434ad92`, unmerged. The head that started this — `7c24fde`, which
carried *both* workstreams — **did not compile at all**, and #43 carries the fix to a test
failure inherited from `32e38c5`. Per the contract (*done = pushed + CI green +
hardware-verified where specified*), every Phase-15 row here is a **code + CI** claim with
the device half still open.

- **A `MERGEABLE` flag is not a health signal.** #41 was `mergeable: MERGEABLE` with
  `build-and-test`, `apk` and `msi` all **failing**; `mergeStateStatus: UNSTABLE` was
  the only honest field, and the session that inherited the PR trusted a platform
  message ("merged-or-closed") instead of `gh pr view`. Read `gh pr checks` before
  merging anything, including work you were told is finished.
- **A compile failure hides every test result behind it.** `PlayerSeekBar.kt` failed
  resolution, so `:shared:jvmTest` and `:app-android:testDebugUnitTest` never executed
  on that head — the four 15a test files had not run anywhere until #41's `cd40c1f`
  (which is green and on `main`), and a *separate*, real `NavStatePersistenceTest`
  failure that predated the break was invisible beneath it. Two
  defects, one red: fixing the compile error is what reveals the second. Read "CI red" as
  *unknown*, not as *one known failure*.
- **An Android *unit-test* failure now surfaces as a build failure.** `6f45a27` coupled
  `:app-android:assembleDebug` to `testDebugUnitTest` so CI executes the suite; the
  side effect is that a red test aborts the step named **"Android debug build"**, which
  reads like a compiler problem. Check the test report before hunting syntax.
- **Robolectric shares one JVM, so app-level `startKoin` must be re-boot safe.** The
  first Android suite died on `KoinApplicationAlreadyStartedException` from
  `DhunApp.onCreate` (`4816c81` → guarded in `32e38c5`). Any future Android test that
  boots the application inherits this, and none of it is reproducible locally: the
  sandbox has no JDK and no Maven/Gradle egress, so CI is the only compiler.
- **Compose offset inputs have no automated gate.** `trackAlignedItemOffsetPx` is pure
  and JVM-tested, but *what is fed to it* is not: the player PR's `ff28f4a` passed an out-of-scope
  `width` (three red jobs), and an `onSizeChanged` placed inside rather than outside
  `.padding(horizontal = xsPlus)` reports the text box instead of the whole pill — a
  silent 12px clamp error no compile error, lint or test would catch. Placement
  relative to `padding` is the whole difference; verify on device.
- **`split(':', limit = 3)` does not reject empty payloads.** `?.let` guards null, not
  `""`, so `"artist:"` restored `ArtistPage(id=)` onto the nav back stack forever. Any
  string-encoded persistence in this repo needs the blank-vs-null distinction.
- **Open by design, not omission:** tablet two-pane (`shared/ui/shell/DhunAppShell.kt`,
  untouched), the 30-minute LeakCanary soak (never run), TalkBack, the launcher
  long-press surface, the dynamic shortcut on a real launcher, and no
  `docs/verification/15-*.md`.

## 2026-09-07 — Coordinator reconciliation: integration limits found while merging six ADR-006 agents

Recorded by the coordinator session `arena/01a07a07-dhun`. Full detail in
`INTEGRATION.md`. **CI green is a compile/unit-test gate only — none of this closes a
hardware gate.**

- **The Android Koin graph has no automated verification (C1).** `app-android` has
  **no test source set**, so `:app-android:assembleDebug` is a *type-check* gate and
  cannot see a dependency-resolution cycle. This already produced a real defect:
  `single<DownloadManager> { ForegroundServiceDownloadManager(delegate = get(), …) }`
  recursed because the unqualified `get()` inferred the interface being constructed,
  and **all three checks were green** on that commit. It fired at app launch
  (`MainActivity.kt:197` resolves `DownloadManager` during composition), not on first
  download. Fixed in `ef69f82` as `delegate = get<FileDownloadManager>()`.
  **Merged and green:** the fix landed via PR #35 (squash `40eff1d`, main `481b77b`).
  **Residual:** `KoinDownloadStackTest` pins the registration *shape* with minimal
  fakes in `:shared:jvmTest` and reads Koin through `GlobalContext.get()` — the real
  `appModule` is **still unverified**, because exercising it needs `androidContext()`,
  hence Robolectric plus an `:app-android:testDebugUnitTest` source set. **That source set now exists, and `AppModuleGraphTest` (PR #43) resolves every `appModule` definition — CI-pending, so until #43 merges, C1 is still OPEN on `main`.** **Standing
  rule (from agent 1, PR #35):** any new `app-android` Koin registration that takes
  another Koin-resolved dependency must be covered by a `checkModules()` call or a
  `koin.get<…>()` smoke test — `:app-android:assembleDebug` will not catch it.
- **`DownloadManager?` parameters were inserted mid-list in shared composables.**
  Agent 3 added `downloadManager: DownloadManager? = null` as the 7th of 12 parameters
  in `HomeScreen` and 7th of 8 in `SearchScreen`. This compiles and behaves correctly
  **only** because every `DhunAppShell` callsite uses named arguments. A future
  positional caller would silently misbind. Append new optional parameters at the end.
- **Two worker sessions share one branch, so their PRs cannot be gated separately.**
  PR #34 bundles agent 4 (desktop) + agent 5 (verify/docs); PR #36 bundles agent 2
  (Library) + agent 3 (download UI). Merging either lands both agents at once — a
  standing violation of the single-session-branch rule.
- **Agent status files are not reliably in the tree.** Agent 1 added
  `agent-1-status.md` to `.gitignore`, so its status exists only in the PR #35 body.
  Reconciling by reading status files alone would have missed agent 1 entirely.
- **The real Android Koin `appModule` and the offline-probe runtime task are both
  unexercised by CI.** `ci.yml` compiles `tools/playback-probe` but never runs
  `:tools:playback-probe:offlineProbe`, so `offline-verdict|PASS` is not established
  even though the probe compiles.


## 2026-09-07 — Windows "second small window on startup": fixed twice, hardware re-test still required

The user's report that opening DHUN on Windows also opens a second small mini-player
window is **not an open code defect**. It is recorded in
`docs/decisions/ADR-004-remove-separate-miniplayer-window.md` (ACCEPTED, user decision
2026-09-06) against the `test` build published `2026-09-06T06:51:40Z`, and it has been
fixed twice:

- **PR #28** (`b8f148d`) deleted `ui/MiniPlayerWindow.kt`, removed the second Compose
  `Window` from `Main.kt`, and stripped the SMTC `GetWindowRect`/`SetWindowPos` calls.
- **PR #34** (`d1e0408`) removed every `JOptionPane` startup/fatal path — the last
  surface able to own a second small native window.

A static audit of `origin/main` @ `481b77b` finds **no surviving second-window path**:
exactly two `Window(` calls in `Main.kt` and they are mutually exclusive (the
startup-error window is gated by `initError != null && koinInstance == null` and ends
in `return@application`); `JOptionPane` import count 0; no `JDialog`/`JWindow`/
`JFrame` instantiation; `showMainWindow()` only toggles `isVisible` on the existing
window; `DhunTray` builds a `TrayIcon` + `PopupMenu`, not a frame; `Smct` uses
`FindWindowW` only to locate the existing `SunAwtFrame` HWND.

**Limitation that remains:** this is a **static** audit. No Windows machine, display,
or jpackage runtime exists in this environment, so the one-window startup behaviour
has **never been verified on hardware** — not for `481b77b`, and not for any earlier
build. Green CI compiles the desktop module; it cannot observe a window. Any user
report against a build older than `481b77b` (published `2026-09-07T04:58:25Z`) does
not describe the current code.

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

- **Foundation + engine implemented and CI-green on merged PR #33 at
  `f157245`:** schema v3 `DownloadedTrack` table (`migrations/2.sqm`),
  `DownloadRepository` + `SqlDelightDownloadRepository` wired as
  `DataLayer.downloads`, `DownloadStorage` file abstraction,
  `StreamDownloader` (Ktor, Range-resume, progress, resolving User-Agent
  isolation, cancellation keeps the `.part`), `DownloadManager` +
  `FileDownloadManager` (bounded 3-slot pool, atomic `.part`→final commit,
  best-effort artwork, QUEUED/DOWNLOADING/COMPLETED/FAILED/PAUSED state
  transitions), and jvmMain `JvmDownloadStorage`. Tests
  `DownloadRepositoryTest`/`StreamDownloaderTest`/`FileDownloadManagerTest`
  are green in `:shared:jvmTest`.
- **Offline-first playback routing is implemented and CI-green:** the shared
  `OfflineFirstStreamResolver` returns a `file://` URI for a COMPLETED download
  (deferring to the network chain otherwise); Android's `PlaybackGraph` routes
  `file://` to a `FileDataSource` (via `SchemeRoutingDataSource`) and checks
  the download repo before the network resolver; the desktop `MusicProvider`
  wraps its resolver with `OfflineFirstStreamResolver` and the vlcj player
  skips cache-fill/pre-buffer when a resolve returns a local `file://` MRL.
  Download managers are wired into both platforms' DI.
- **Deterministic probe coverage is now available:**
  `./gradlew :tools:playback-probe:offlineProbe --offline --no-daemon`
  uses the real JVM SQLDelight download repository plus a valid WAV fixture,
  asserts the completed row resolves to `file://`, opens the local file, checks
  the RIFF/WAVE header, and fails if the network resolver is called. The
  sandbox could not execute this command because Java/JAVA_HOME is unavailable.
  Branch CI run `34080947691` successfully compiled the probe, but the existing
  workflow does not execute `offlineProbe`; a JDK-equipped checkout or explicit
  CI execution step is still needed for runtime PASS evidence.
- **Hardware limitation remains explicit:** the probe verifies shared/JVM
  repository-to-file loading only. It does **not** verify Android Media3
  `FileDataSource`, Desktop vlcj decoding, airplane-mode behavior, or audible
  offline playback. A real Android device and Desktop/PC remain mandatory for
  those checks; no agent or CI run can close that gate.
- **Download UI is wired but minimal:** a Library "Downloads" tab lists
  downloads (play/remove/clear-all + storage byte summary), and a "Download for
  offline" action appears in the track overflow menu. There is **no** per-track
  download badge, no Android foreground/WorkManager download service (downloads
  run on the manager's worker pool inside the app process), and no dedicated
  storage-management screen beyond the Downloads tab's clear-all — these remain
  open follow-ups.
- Downloads are **persistent** (survive until the user deletes them) and are
  distinct from the ADR-005 LRU stream cache, which is volatile and evicts on
  budget.

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

## 2026-09-09 — Candidate 22 equalizer (shared model + desktop vlcj; not audible)

- Platform-neutral 10-band EQ (VLC `f_vlc_frequency_table_10b` + 18 VLC
  presets) lives in `shared/.../player/equalizer`. Desktop applies it through
  the existing vlcj `MediaPlayer.audio().setEqualizer` API. **`DhunPlayer` is
  unchanged.** There is no Compose EQ screen in this slice — presentation is
  a pure `EqualizerUiModel` snapshot for a later UI to bind.
- **Android `AudioEffect` is not in this slice.** Enabling EQ on Android is a
  later stream.
- **CI green is compile + unit tests.** This environment has no libVLC and no
  audio device; nothing here claims audible EQ on hardware. Native apply is
  best-effort and silent when VLC is missing.

## 2026-09-09 — Candidate 28 themes (PR #49, `arena/01a08455-themes`) — light scheme + accent selector

Limits found while building it. Each is a measured or code-verified fact;
**visual appearance on hardware is claimed nowhere.**

- **Dark `error` on `errorContainer` is 3.92:1 — below WCAG AA (4.5:1) for
  text.** Shipped values (`#CF6679` on `#4D1A24`), left byte-identical on
  purpose: retuning them would break the "dark stays the default and
  unchanged" guarantee, which is the whole basis of an additive theme system.
  `DhunThemeContrastTest` therefore asserts 3:1 for that pair in dark and
  4.5:1 in light (the new light pair, `#B00020` on `#F9DEDC`, measures
  5.76:1). A future pass can retune the dark pair knowingly — it is a real
  accessibility defect, recorded here rather than silently asserted away.
- **`app-desktop` tray icons stay dark in light mode.**
  `native/TrayIcons.kt:19-21` reads `DhunColors.surface/border/accent` in an
  object initialiser — once, before any composition exists — so it captures
  the default palette. Fixing it means touching a frozen file.
- **One appearance per process, not per window.** `DhunAppearance` is a
  process-wide snapshot-state holder, so desktop's startup-error window and
  main window share a theme. Intended for a toggle; stated because it is a
  constraint, not an omission.
- **Nothing persists the choice.** A store exists (`SettingsRepository` +
  `SettingsKeys.THEME` = `"dark" | "light" | "system"`, default `"dark"`), but
  **no app code reads that key** — only `RepositoriesTest` — there is no
  Settings screen, and `design` must not depend on `data` (layer inversion).
  `DhunThemeMode.id` already uses exactly those strings, so wiring is
  mechanical; until then a restart returns to dark. `"system"` is storable but
  unimplemented (no `expect/actual` hook), so `fromId("system")` returns
  `null` and falls back to dark rather than pretending to work.
- **The toggle is dev-reachable only.** It is mounted in
  `design/catalog/ComponentCatalogScreen.kt`, which has no entry point in
  either app. Making it user-reachable requires editing at least one frozen
  theme entry point (`MainActivity.kt:134`, desktop `Main.kt:278`/`:554`).
- **Kotlin nests block comments — a path glob in a KDoc is a compile break.**
  `shared/ui/**` inside a KDoc opens a nested comment that nothing closes, so
  the enclosing comment never terminates and the rest of the file is
  commented out. This bit both this session (caught by a delimiter-balance
  sweep before push) and PR #45 (caught by CI). Do not quote path globs in
  Kotlin comments.
- **`workflow_dispatch` is refused for an agent session's token** (`HTTP 403:
  Resource not accessible by integration`), and Actions job logs are unreadable
  here (the log CDN is behind the same egress wall as Maven). If a workflow
  only triggers on `pull_request` and that event does not fire for a given PR,
  its jobs need a human — which is exactly how `apk`/`msi` were obtained for
  PR #49 (run `34315184472`).
