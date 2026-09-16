# Changelog

All notable changes to DHUN are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/).

**No versioned release exists yet.** A `[0.1.0]` section below is prepared
as a **DRAFT** by the 2026-09-07 release-prep session: it is the planned
first-release notes and is **not tagged or published**. `v0.1.0` will be
tagged only after the Phase 14 evidence in `docs/verification/14-release.md`
is real (live rot-drill verdict, 30-minute soaks on Android and Desktop,
clean-target installs) and the user gives the go-ahead. Until then the only
publicly downloadable build is the rolling
[`test` pre-release](https://github.com/99ggprooo00-code/DHUN/releases/tag/test)
(`dhun-test.apk`, `dhun-test.msi`), replaced on every push to `main` — it
has an unversioned public tag; Windows still requires an increasing internal
MSI ProductVersion, separate from the app's semantic version. The prepared
v0.1.0 candidate additionally stages a debug-keystore-signed Android App
Bundle (`dhun-v0.1.0.aab`) beside the APK and MSI.

The maintenance contract applies to every entry below: stream extraction
rots; when it breaks, DHUN ships a patch release fast (see README and
`.ai/RISK_REGISTER.md`).

## [Unreleased]

### Fixed — navigation adjustment package — 2026-09-16
- **Pushing a page now collapses the player, as documented.**
  `AppNavState.push()` promised this in its KDoc but never did it, so
  "Go to artist/album" from the track overflow dialog and "open
  playlist" from add-to-playlist landed invisibly under the expanded
  FullPlayer and read as dead taps. The collapse is now real, and the
  overflow search-redirect fallbacks collapse too. Pinned by
  `pushCollapsesThePlayerSoThePageIsVisible`.
- **Two-pane master shows the tab list again.** The large-screen master
  column was rendering the detail route a second time instead of the
  tab content — the same page twice, its data loaded twice, and the
  tab list unreachable while a page was open. The master now renders
  the tab (`detailRoute = null`); the detail pane keeps the page.
- **Desktop: Escape is Back.** The desktop window had shortcuts for
  transport, search and quit but no way to collapse the player or pop
  a page from the keyboard. Escape now runs the shared back contract
  (collapse → pop page → previous tab).
- **Restoring onto CATALOG no longer strands the user.** The developer
  catalog has no nav-bar entry and a no-op close; an old bundle or
  debug session naming it (or carrying it in tab history) now
  sanitizes to Home on restore. Pinned by two
  `NavStatePersistenceTest` cases.

### Changed — full project re-baseline (docs only) — 2026-09-16
- **`.ai/MASTER_PROMPT.md` → v3.** Rewritten from the live repo
  (`main@d555959`): revised extraction doctrine (own-client wave chain
  acknowledged as the production primary, NewPipe as drill watch,
  ADR-007 as contingency gated by triggers T1/T2), corrected stack
  (Ktor CIO, platform logging, shared navigator on both platforms),
  build history Phases 01–16 (all code-merged), and sequential
  single-agent completion Stages S1–S6. v2's phase text is preserved
  in git history — it must never be implemented from again.
- **`.ai/ROADMAP.md` rewritten.** 122 KB of session snapshots
  collapsed (full text in the file's git history); standing user
  directives kept; new live status from code/CI/GitHub; v2 trajectory
  numbers retired (several "candidates" already shipped); PR/issue
  hygiene recorded (#53 close-unmerged, #54 contingency reference,
  #14 needs a fresh S1 verdict, #60/#63 are v2 backlog).
- **Reconciled:** `.ai/KNOWN_LIMITATIONS.md` (re-baseline entry;
  pre-#57 "playback broken" entries marked stale-but-kept),
  `.ai/RISK_REGISTER.md` (drill outage + own-client rot now head the
  table; repaired risks retired, not deleted),
  `docs/decisions/README.md` (missing ADR-006 entry added; ADR-007
  PROPOSED pointer added).
- **No behavior change.** No app, extraction, playback-engine or ADR
  change in this session.

### Fixed — desktop unit tests now execute in CI — 2026-09-16
- **`:app-desktop:jvmTest` is a named CI step.** The five jump-list/tray
  test classes existed since candidate 27 but CI only ran
  `:app-desktop:compileKotlinJvm`, which compiles `jvmMain` and never
  executes `jvmTest` — a red desktop test was impossible. `ci.yml` now runs
  the suite as its own "Unit tests — Desktop (JVM)" step after
  "Desktop compiles", so a compile break and a test failure fail different
  steps. `scripts/test_ci_workflow.py` pins the step and its order.
- **The new gate immediately caught three latent failures** (first CI run,
  `:app-desktop:jvmTest`): two `JumpListModelTest` assertions forgot the
  separator entry the documented task order includes, and `isValidTrackId`
  used Unicode-aware `isLetterOrDigit()`, admitting `ä` against its own
  `[A-Za-z0-9_-]` contract. Separator assertions corrected; the validator is
  now ASCII-only (no real-world change — track ids are YouTube video ids).
  These tests had never executed anywhere since PR #47.

### Fixed — Android <12 backdrop guard across FullPlayer and NowPlayingBackdrop — 2026-09-16
- **Sharp unblurred backdrop suppressed on Android <12.** `Modifier.blur` is a
  `RenderEffect` supported only on API 31+. While `NowPlayingBackdrop` already
  checked `supportsRealtimeBlur`, `FullPlayer`'s blurred bleed layer and `LyricsCard`
  did not check platform capability, drawing sharp stretched artwork under the
  controls on Android 8.0–11. Both components now use a unified policy guard
  (`shouldRenderPlayerBackdrop` / `shouldRenderBackdrop`), consistently falling back
  to the clean dark surface and ambient scrim when realtime blur is unsupported.

### Fixed — mouse-rail fling + named Android unit-test CI step — 2026-09-16
- **Windows rails coast after a mouse flick.** Hold-and-slide (PR #68) stopped
  dead on pointer-up. Releasing with leftover velocity now decays into a fling
  in the same direction the drag was already moving; a slow release, a cancelled
  drag, and the end of the rail still stop. Touch keeps the framework fling;
  the mouse wheel stays a vertical control; trackpad two-finger pans are
  untouched.
- **CI names `:app-android:testDebugUnitTest`.** A red Android suite used to
  abort the step called "Android debug build" because `assembleDebug` depended
  on the tests. `ci.yml` now runs the suite as its own "Unit tests — Android
  (Robolectric)" step before assemble. Packaging jobs still go through
  assembleDebug, so that coupling is kept — a red suite cannot ship an APK.

### Fixed — Home/Search/Library backdrop, Android tab BACK, Windows rails — 2026-09-16
- **The app now sits on the song you are playing.** Home, Search and Library get
  an edge-to-edge background built from the **currently playing** track's
  artwork: blurred 64dp, darkened, overscaled past the edges so no blur rim
  shows, and painted *under* the existing glass surfaces. It follows playback —
  change the track and the backdrop crossfades on whichever of the three screens
  you are on — and it never borrows artwork from whatever the screen is listing.
  When there is no artwork to show (no thumbnail, blank or failed URL, or an
  Android below 12 where a real blur does not exist) the previous DHUN background
  is what you get, unchanged; nothing broken, empty or placeholder-shaped is ever
  stretched across a screen.
- **Android BACK no longer leaves the app from Search or Library.** Those are
  tabs, not pages on the navigation stack, so Back had nothing to close and
  parked the app. Back now walks the navigation history: the full player
  collapses, then one page pops, then the tab returns to the tab you came from,
  and only Home — the landing screen — hands Back to the system as before.
  Playback is untouched by all of it, and the walk survives rotation and process
  death.
- **Windows: horizontal shelves can finally be moved sideways.** Compose refuses
  mouse drags on scrollable containers, and a vertical mouse wheel is (correctly)
  a vertical control, so on Windows the albums/artists/playlists/chip rails could
  only be reached with a trackpad. They now support **hold the pointer and
  slide**, plus a glassy scrollbar that fades in while a rail moves, shows how
  much is off-screen and can be dragged. Trackpad two-finger panning and
  Shift+wheel keep working exactly as before, and ordinary vertical page
  scrolling is unchanged — no page was made sideways-scrollable.
### Fixed — the Full Player rises *with* the Related/Queue sheet — 2026-09-16
- **One composition, not one moving picture.** PR #66 made the panel and the
  player share a transition, but it left the control cluster
  counter-translated (`-playerOffsetY`) and parked the sheet *above* the chrome
  as a footer. The result on Android and Windows was the same: the cover slid up
  while the title, artist, timeline, transport and desktop volume stayed where
  they were, and the panel rose into the gap. The counter-translate is gone, the
  footer inset is gone, and the sheet's height now *is* the transition's travel
  distance: `playerOffsetY = -travel · progress`,
  `sheetOffsetY = travel · (1 - progress)` from one `updateTransition` progress
  (0 closed, 1 open), so the panel's top edge and the player's lower boundary are
  the same y on every frame — no empty gap under the panel, nothing buried under
  it, no `-500.dp` guess.
- **Opening is the exact reversible counterpart of closing.** The panel no
  longer fades in over a still player (`alpha = progress` is gone): it is
  opaque, clipped by the player's own bounds, mounted on the first frame at its
  real frozen height, and driven by the same numbers as the close. Reversing the
  target mid-flight continues from the current progress instead of restarting,
  because the sheet stays in the tree until the close lands on exactly zero.
- **Travel is captured from responsive geometry and frozen for the flight.**
  Safe-area height (Android status bar / cutout / gesture inset via
  `safeDrawingPadding`, the raw window box on Desktop), the density-corrected
  measured chrome, and the panel's own share of the room left over. Idle frames
  keep re-tracking that measurement (rotation, a dragged window, a track change),
  and the value in force when the target flips is what the whole motion runs
  against. `DhunSpacing.queuePanelPlayerBandFloor` additionally caps the travel
  so a short viewport shrinks the panel rather than pushing the player out.
- **The cover becomes a thumbnail instead of being cropped.** Half the rise is
  absorbed by the existing weighted artwork field
  (`relatedSheetLayoutRiseDp`), so `fittedPlayerArtworkSize` re-fits the cover as
  the field shortens; the header stays at the top of the safe area, which keeps
  its swipe-down-to-collapse gesture reachable while the panel is up.
- **The row the panel replaces gets out of the way.** Queue / shuffle / repeat /
  lyrics are faded out for the entire mount window — with their measured space
  preserved, because that measurement is what sizes the travel — and restored on
  the frame the close lands, so nothing flickers back on mid-motion. Hidden
  buttons also stop being hittable.
- Shared `commonMain` only: no Android- or Windows-only fork, no second player,
  no duplicated navigation destination, no screenshot UI. `PlayerViewModel`, the
  Queue/Related state, tabs, Play Radio, loading/error/empty states, add-to-queue,
  the ✕, Back, and the drag behaviour are all the existing ones.
- Tests: `PlayerSheetLayoutTest` pins the seam at 0 / 0.5 / 1 (and between) for
  small and large Android portrait, tablet, landscape, and wide, maximised and
  resized Desktop windows; opening-vs-closing frame equality; rapid reversal
  continuity; travel freeze/unfreeze; action-row visibility for the whole
  transition; the untouched layout restored after closing; and geometry that
  never depends on how many songs have arrived.

### Changed — one widget only: Quick Play survives, Now Playing deleted — 2026-09-15
- **Now Playing is gone**, on device feedback after PR #61. It "added but
  couldn't load": its layout declared `minHeight="140dp"` for a 4×2 slot that
  measures ~110dp, and the reporting launcher refuses an oversized widget
  instead of growing it a row. DHUN ships **Quick Play** as its single
  home-screen widget. Removed: the provider, all three tier layouts, its
  picker preview, its `appwidget-provider` XML, its manifest receiver and its
  picker string.
- **Quick Play's identity is frozen on purpose.** The provider class,
  `widget_quick_play`/`widget_quick_play_wide`, every view id and
  `xml/widget_quick_play_info.xml` keep their exact names — an instance
  already sitting on a home screen is only revived by the update while those
  identifiers are unchanged. `WidgetXmlTest` pins "exactly one widget receiver
  in the manifest" and asserts the declared minimums still fit a 2×2 cell, so
  the size bug that killed Now Playing cannot return silently.
- **AMOLED card in every palette and mode.** `widget_background` is now
  `#000000` in light *and* dark, pre-S and on Material You hosts; the whole
  `values-night-v31` overlay is deleted because the palette is no longer
  mode-dependent. Wallpaper tint is kept on the accent only
  (`system_accent1_200` / `system_accent1_900` on API 31+): play disc,
  progress fill and any future active toggle follow the wallpaper, the card
  does not.
- **Corners use the launcher's radius, not our guess.** A new
  `@dimen/widget_corner_radius` resolves to the platform's
  `system_app_widget_background_radius` on API 31+ and falls back to 28dp
  below that. It is read by both renderers — the static `widget_background`
  chrome and the runtime glass bitmap — so the card silhouette and the host's
  clip agree instead of one outrunning the other.
- **Sharper glass**: the `WidgetGlass` edge cap goes 256 → 320 px. A flat fill
  upscaled from 256 read soft at 2×2; 320² ARGB is 400 KB, and artwork
  (256² ≈ 256 KB) still rides the same transaction comfortably inside the
  ~1 MB binder limit.
- **Size tiers stay a Quick Play feature** by decision: the small 2×2 layout
  is the floor, and the wide tier (artwork + next) takes over at ≥ 200dp.
- `DhunWidgetUpdater` is now single-provider: the Now Playing builders, id
  lookups and tier functions are deleted, and the shared engine — snapshot →
  `DhunWidgetState`, two-phase artwork push, glass, service-push plus
  controller-pull — is what remains.
- Tests: Now Playing assertions dropped; Quick Play coverage extended (tier
  boundaries at 199/200dp, both tiers against idle/playing/paused states,
  slot-fit and single-receiver guards), plus new `WidgetPaletteLightModeTest`
  / `WidgetPaletteDarkModeTest` for the AMOLED contract and glass coverage
  for the radius resolution and the 320px budget.
- `scripts/check_widget_refs.py` — the toolchain-free static gate for this
  area (XML well-formedness, `R.*`/`@res` reference resolution, "Now Playing
  is gone", and Kotlin comment/string nesting). It earned its place by
  catching an *unclosed block comment*: Kotlin comments nest, so a
  `@android:color/*` glob written inside KDoc swallows the closing `*/`.
  That shipped once and cost a CI cycle.
- **Name collision, on purpose left alone**: "Now Playing" as the *in-app*
  player screen (the entry below, PR #62) is untouched — only the home-screen
  widget that shared the name is gone.

### Fixed — Now Playing: fit-to-card artwork, bottom-docked controls, working queue sheet — 2026-09-15
- **Artwork is no longer zoomed/cropped.** The now-playing cover is a square,
  clipped card sized by `fittedPlayerArtworkSize` (fits the width, the height
  and `DhunSpacing.playerArtworkMaxSize`) and drawn with `ContentScale.Fit`
  inside it, so a cover is shown whole — nothing is cut off on Android or
  Windows. Free bands reveal the blurred backdrop below instead of grey bars
  (`ArtworkImage(placeholderBase = false)`), and the card keeps its slide/fade
  per track plus a light play-scale.
- **The control cluster is docked to the bottom.** The artwork field between
  the top bar and the chrome is now a `weight(1f)` box that is emitted on
  every composition. It used to be an invisible `AnimatedVisibility`, which
  emits no layout node at all once its exit transition finishes — so the
  weight vanished and title/progress/transport/action row rode up under the
  "NOW PLAYING" bar. Lyrics-dominant mode still raises its card into that
  same field.
- **Blurred, darkened artwork backdrop behind the controls.** The sharp
  full-bleed copy is gone; the screen is carried by the once-per-track blurred
  bleed (`BlurredArtworkCache`, blur-before-scrim order kept) plus a
  bottom-weighted scrim (`playerAmbientScrimStops`) that stays transparent
  across the middle so the blur glows and darkens monotonically towards the
  bottom so titles, progress and transport stay legible.
- **"More songs"/queue opens a usable panel on both platforms.** The sheet's
  bottom inset was a *pixel* count handed to `Dp()`, i.e. 2–3.5× too large on
  Android (density ≈ 2.75 left no row height at all, so the tap looked dead)
  and enough to squeeze desktop into a thin bar. Inset and height now come
  from `chromeHeightDp` + `queuePanelMetrics`, which guarantees a real height
  (floored at the new `DhunSpacing.queuePanelMinHeight`), never overflows the
  safe area, and still docks above the chrome. The sheet also paints an opaque
  base under the glass (`GlassBottomBar(opaqueBase = true)`) — the strongest
  glass token is ~72% translucent, which was unreadable over artwork. On a
  wide window it is capped to `DhunSpacing.playerContentMaxWidth` and centred
  (all four corners rounded, since it floats above the cluster rather than
  touching the bottom edge).
- **Readable queue rows + an obvious way out.** Rows keep 48dp artwork (was
  44dp), title, artist, current-track highlight and tap-to-play, and gain the
  track duration (`queueRowDurationLabel`: `m:ss`, `h:mm:ss` past an hour,
  nothing when the provider reported no length). The sheet has a header with a
  grab pill, an `Up next`/`Related` title and a 48dp ✕ close target beside the
  existing queue-glyph toggle.
- Tests: `PlayerSheetLayoutTest` pins the artwork-fit contract, the
  panel-geometry/px→dp regressions, the backdrop scrim contract (clear in the
  middle, dark at the bottom, never flat black) and the duration label.

### Changed — home-screen widgets rebuilt (Material You) — 2026-09-15
*Superseded in part by the "one widget only" entry at the top of this
section: the two-widget set and the wallpaper-tinted card described below
were replaced by a single Quick Play widget on an AMOLED card.*

- **Both widgets redesigned around Material You**: dynamic wallpaper-tinted
  palette on API 31+ (light + dark), 28dp rounded card, filled accent play
  disc, rounded artwork well, and a real layout preview in the widget
  picker (`previewLayout`). Pre-S falls back to a DHUN dark-first palette.
- **Real artwork**: the updater decodes the session's `artworkUri` off-thread
  (sampled, center-cropped, rounded, 256px binder-safe) with an LRU cache;
  text pushes instantly and artwork lands when ready. Stale-track arrivals
  are dropped.
- **Progress + times**: position/duration from the session drive a progress
  bar on every tier, with `m:ss`/`h:mm:ss` labels on the tall tier.
- **More controls**: shuffle toggle and repeat cycle (off → all → one) join
  prev/play/next on the tall Now Playing tier; both providers share one
  transport executor (`WidgetTransport`).
- **Responsive tiers**: Now Playing picks compact (< 200dp wide), standard,
  or tall (≥ 180dp high) per instance; Quick Play picks small or wide
  (artwork + skip at ≥ 200dp). Both are resizable with target cells declared.
- **Live updates**: `DhunPlaybackService` pushes widget state on every player
  event (debounced) plus a 10s progress tick while playing — no more stale
  track info. The controller-pull path remains for the app-dead case.
- **Translucent M3 card**: the dynamic tint is rendered at runtime as a flat
  translucent rounded card (80% — Google Search widget style, no sheen or
  faux edge) with opaque content on top. True blur is not exposed to widgets
  on any API level, so translucency over the wallpaper is the platform's
  glass look; the tint still follows wallpaper + light/dark mode. Picker
  previews are static solid mockups.
- Tests: state/progress/time-format, tier selection, artwork pipeline, glass
  renderer, new intent actions, and the XML/drawable/palette contract are
  pinned (`DhunWidgetStateTest`, `DhunWidgetUpdaterTest`,
  `WidgetArtworkLoaderTest`, `WidgetGlassTest`, `WidgetTransportTest`,
  `WidgetIntentsTest`, `WidgetXmlTest`).

### Fixed — `main` build + debug-APK workflow — 2026-09-10
- **`main` no longer compiles since `073083c`** (PR #55, merged with its
  `build-and-test`/`apk`/`msi` checks failing): `InnerTubeClient.altContext`
  nested `contentPlaybackContext` with `put(key) { … }`, which
  `buildJsonObject` does not offer, and the `X-Goog-Visitor-Id` header was
  read from a `visitorData` parameter `postAltJson` never had. Both fixed —
  `putJsonObject`, and the parameter threaded through.
- **The alt-identity `/player` request is byte-for-byte unchanged** unless a
  caller supplies session material, and none does: `visitorData` and
  `signatureTimestamp` stay optional and null by default, so the header is
  omitted rather than sent empty. **This does not repair gated playback** —
  `AUTH_REQUIRED` on Android (issue #14) is an open ADR-007 decision, not a
  patch. Two `MockEngine` tests now pin the request body/headers in both
  states.
- **`Build APK` workflow corrected:** it ran `./gradlew :app:assembleDebug`,
  which this repo has no module for (Gradle: "project 'app' is ambiguous…
  Candidates are: 'app-android', 'app-desktop'"), and uploaded a path that has
  never existed. It now builds `:app-android:assembleDebug` and uploads
  `app-android/build/outputs/apk/debug/app-android-debug.apk`; action versions
  bumped off the deprecated Node-20 pair. `scripts/test_apk_workflow.py`
  fails any workflow whose `*/build/outputs/...` path is not under a module
  `settings.gradle.kts` includes, so the mistake cannot return silently.

### FullPlayer immersive full-screen redesign — 2026-09-09
- **Immersive Now Playing** — the now-playing artwork is now the entire
  background: a sharp full-bleed copy (slide + fade on track change) over a
  once-per-track blurred bleed of the same image (ADR-002 P4 contract kept;
  the bleed is scaled 1.2× so the blur rim is never visible), with a smooth
  bottom scrim that fades into the surface so the overlaid chrome stays
  legible. The art no longer sits in a boxed card over a heavy dark wash.
- **Overlay chrome** — title + artist, the progress bar, and transport are
  overlaid on the artwork: circular "more" / "favorite" glass chips beside
  the track name, plain oversized previous / play / next icons (no disc,
  no shadow), and a bottom action row — queue · shuffle · repeat · lyrics.
- **Queue sheet + lyrics card** — the queue glyph opens a glass bottom
  sheet (Queue | Related tabs, drag-reorder and all queue actions kept)
  that docks above the chrome; the lyrics glyph (CC) switches to the
  lyrics-dominant view: a rounded card carrying the synced lyrics over a
  blurred artwork while the sharp backdrop recedes to a darkened blur.
  Collapse stays chevron / swipe-down / system-back — never exits the app.
- `DhunIcon.ChevronDown` added; `playerTransportMetrics` re-derived for the
  three-button transport (48dp skip targets + 52dp play target preserved,
  icons step up on roomy widths) with the regression tests updated to the
  new geometry.

### ADR-006 offline downloads + desktop single-window hardening — 2026-09-07 (PRs #33–#38 merged)
- **Persistent offline downloads (ADR-006, PR #33 `f157245`)** — schema v3 +
  migration, download repositories, range-resume atomic `DownloadManager`,
  `OfflineFirstStreamResolver`, Android `file://`/`FileDataSource` route,
  Desktop local-path load, Downloads library tab + actions.
- **Android download foreground service (PR #35)** —
  `ForegroundServiceDownloadManager` (`FOREGROUND_SERVICE_DATA_SYNC`), plus
  the C1 Koin self-recursion fix (`delegate = get<FileDownloadManager>()`)
  and its regression test.
- **Downloads storage management + per-track badges (PR #36)** —
  `StorageSpace` expect/actual storage-summary view, per-track download
  state/action badges, and the `DhunAppShell` pass-through closing C2.
- **Player UX (PR #37)** — track-aware synced-lyrics follow, explicit related
  enqueue, accessible queue actions, `PlayerSeekBar`/`TransportControls`
  extraction.
- **Desktop single-window hardening + deterministic offline probe (PR #34)** —
  every `JOptionPane` startup/fatal Swing surface removed (static audit finds
  no surviving second-window path on main) and
  `tools:playback-probe:offlineProbe` verifies JVM repository → local-file
  playback against a WAV fixture.
- **Consolidation (PR #38, `92ee545`)** — `INTEGRATION.md` + the three `.ai`
  docs reconciled; main and the rolling `test` tag are stable at `92ee545`.
  **Offline playback of a downloaded track has NOT yet been demonstrated on a
  real device** — hardware acceptance remains open (docs/verification/14-release.md).

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

## [0.1.0] — 2026-09-07 (DRAFT — not tagged, not published)

> Prepared by the release-prep session as the planned first-release notes.
> **Nothing in this section is released.** The `v0.1.0` tag, a GitHub
> Release and any public download exist only after the Phase 14 gates in
> `docs/verification/14-release.md` are real and the user approves.
> Per-merge detail stays in `[Unreleased]` until this section is finalized
> at publish time (when its version-comparison link is added too).

v0.1.0 is the first DHUN release: a Kotlin Multiplatform music player for
Android and Windows/Linux/macOS desktop (Compose Multiplatform), streaming
from YouTube Music, GPL-3.0, built on maintained extractors with a daily
rot-drill maintenance contract.

### Added
- **Android playback** — Media3/ExoPlayer `MediaSessionService` with lock
  screen + notification controls, background playback, OEM battery-saver
  resilience, foreground media service, 403 mid-stream auto-recovery and a
  bounded Media3 audio-segment cache.
- **Desktop playback** — vlcj over a system libVLC, whole-track LRU audio
  cache, system tray, keyboard shortcuts, close-to-tray with remembered
  geometry, Windows SMTC.
- **Shared app core** — own InnerTube metadata client, ADR-001 multi-client
  resolver chain (own client primary, optional yt-dlp desktop fallback,
  NewPipe recovery watch), queue engine, SQLDelight data layer, lyrics
  (LRCLIB + YouTube Music, synced), Library & History, Artist/Album/Playlist
  pages, Home & Search, glass design system with real blur.
- **Persistent offline downloads (ADR-006)** — background download service,
  Downloads library tab with per-track state and storage management,
  offline-first playback routing.
- **Daily rot-drill CI** — scheduled live-extraction health check that
  opens an issue on red.
- **Android polish** — edge-to-edge insets, app shortcuts, battery-opt
  rationale, 840 dp navigation rail on large screens.

### Changed
- Windows MSI is per-user (`%LOCALAPPDATA%\DHUN`, no UAC); runtime data
  lives under `<installDir>/userdata`. The internal MSI ProductVersion is an
  independent, strictly increasing installer sequence — it must never be
  reset to the app's semantic version.
- Extraction follows ADR-001 (own InnerTube player-client chain primary,
  yt-dlp desktop fallback) and is monitored daily by the rot drill.
- Desktop is a single-window app (ADR-004): the separate always-on-top
  mini-player window was removed; the docked in-app MiniPlayer remains.

### Fixed
- Android `MediaController` thread violation (crash on playback start).
- Koin C1 self-recursion in the ADR-006 download stack.
- ExoPlayer latch-on-error — bounded re-resolve recovery UX instead.
- MSI in-place upgrade deleting existing userdata (upgrade data policy +
  hosted install-over sentinel checks).
- SQLDelight write-race hang, splash/artwork/dialog visual defects,
  stale-stream-URL playback.

### Removed
- Separate desktop mini-player window and its Ctrl+M toggle (ADR-004, user
  decision).

### Security & packaging notes
- Android artifacts are signed with the committed **public test keystore**
  (`app-android/keystores/dhun-test.p12`, password `android`) — debug/test
  only, never store-upload; anyone with the repo can mint same-key APKs.
- Windows MSI is **not Authenticode-signed** (SmartScreen warns). Playback
  needs a system VLC install; the yt-dlp fallback is optional and not
  bundled.
- Known limitations are tracked honestly in `.ai/KNOWN_LIMITATIONS.md`;
  release gates and open hardware acceptance live in
  `docs/verification/14-release.md`.

[Unreleased]: https://github.com/99ggprooo00-code/DHUN/compare/290e0f6...HEAD
