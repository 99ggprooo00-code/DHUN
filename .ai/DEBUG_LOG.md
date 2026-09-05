# DEBUG_LOG — incidents, root causes, environment traps

Format: date · title · symptom (with stack where available) · root cause ·
fix · verification state. Newest first. If you hit one of these again,
read this entry before re-diagnosing.

---

## 2026-09-05 · rot-drill run 33961533965 FAILED — first live drill red (yt-dlp bot-gated from CI IP)

**Symptom** (job 101295458477, step "Fail the workflow after alerting"):
`Process completed with exit code 1.` — that step is the INTENTIONAL
kill switch (`exit 1` when `steps.probe.outcome == 'failure'`), so it is
the alert, not the cause. Real failure output, from issue #14 (tail of the
run's `rot-drill.log` artifact):

```
PROBE|version|PASS|WEB_REMIX 1.20260901.12.00 (scraped from homepage HTML)
PROBE|search|PASS|20 music-song results
PROBE|resolve+stream|FAIL|IllegalStateException: resolve: AuthRequired(detail=null)
PROBE|related|PASS|50 related tracks
WATCH|newpipe-stream|BROKEN|Parse(detail=JSON response is too short)
PROBE|verdict|FAIL|extraction-pipeline-broken
```

**Root cause** (chain of evidence, no guessing):
1. `PROBE|resolve+stream` failed inside `YtDlpStreamResolver.resolve`:
   that mapping fires ONLY when yt-dlp's last non-blank stderr line contains
   "Sign in to confirm" — YouTube's bot-gate text
   (`JvmStreamResolvers.kt: message.contains("Sign in to confirm") →
   DhunError.AuthRequired()`).
2. CI installed yt-dlp **2026.08.19** (latest on PyPI today — verified from
   this sandbox), the exact version ADR-001 measured tokenless-working from
   a hostile datacenter IP on 2026-09-01 ⇒ not a version regression;
   YouTube tightened player-endpoint gating for the Actions runner IP class
   between 09-01 and 09-05.
3. Metadata endpoints kept working from the SAME runner in the SAME run
   (version scrape, 20 search results, 50 related) ⇒ player-endpoint
   gating, not a blanket IP block, not an InnerTube-shape break.
4. Classification: **YouTube/datacenter-IP bot blocking (category 8)**.
   Residential impact unproven either way — this is CI-network evidence.

**Aggravating defects found while diagnosing (all real, all to fix):**
- Probe misalignment: fatal step drove `YtDlpStreamResolver` ALONE (the
  desktop FALLBACK per ADR-001), never the production primary
  (`OwnClientStreamResolver`, the ONLY engine Android ships). The drill
  gated the verdict on an engine production uses second.
- Diagnostics loss: yt-dlp's stderr line was discarded when typing
  `AuthRequired` → printed `detail=null`, violating ADR-001's
  "detail carries the per-attempt evidence" contract. We had to infer the
  trigger text from the code path instead of reading it in the log.
- Workflow quoting bug: `` `rot-drill-${GITHUB_RUN_ID}` `` inside the
  double-quoted bash issue body executed as command substitution → issue
  #14 shows "The attached  artifact" with the name swallowed.
- Evidence gap: `yt-dlp --version` printed only to the step log; the
  `rot-drill.log` artifact starts at Gradle, so the artifact cannot prove
  which engine version ran.

**Environment traps re-confirmed this session:** this sandbox has no JDK
(CI is the compile gate) and YouTube egress is TLS-blocked here
(`yt-dlp` fails `TLS/SSL connection has been closed (EOF)`) — live
reproduction must happen on GitHub Actions, not locally.

**Fix (this session, small commits):**
1. `YtDlpStreamResolver`: carry yt-dlp's last stderr line into
   `AuthRequired(detail=…)` / `Unknown(causeMessage=…)` — typed errors and
   fail-loud behavior unchanged.
2. Probe `Main.kt`: fatal `resolve+stream` step now drives the REAL
   production chain (`ResolvingStreamResolver(OwnClientStreamResolver →
   YtDlpStreamResolver)`, identical to `forDesktop`), with new per-engine
   `WATCH` lines (`WATCH|own-client`, `WATCH|ytdlp`) alongside the existing
   NewPipe watch. NOT a weakening: audio bytes still HTTP-fetched and
   magic-byte-verified; both engines gated ⇒ verdict still FAIL.
3. `rot-drill.yml`: quote the issue body safely (no backtick execution),
   append `yt-dlp --version` into `rot-drill.log`.

**Verification state:** pending — PR CI (tests+builds) then re-dispatch the
drill; green requires a real `PROBE|verdict|PASS` line. If the own-client
tier is ALSO gated from CI, the drill stays red (CI-network gating
evidence — see KNOWN_LIMITATIONS "CI-network vs residential" note) and
residential verification moves to real hardware.

---

## 2026-09-05 · Android FATAL: `MediaController method is called from a wrong thread`

**Symptom** (user-reported crash, artist page → shuffle play):

```
IllegalStateException: MediaController method is called from a wrong thread
    at androidx.media3.session.MediaController.verifyApplicationThread(MediaController.java:…)
    at androidx.media3.session.MediaController.setMediaItems(MediaController.java:…)
    at dev.dhun.android.playback.AndroidDhunPlayer.prepareQueue(AndroidDhunPlayer.kt:91)
    at dev.dhun.presentation.browse.ArtistViewModel$playTopSongsShuffled$1.invokeSuspend(ArtistViewModel.kt:70)
    … on Dispatchers.Default (StandaloneCoroutine Cancelling)
```

**Root cause:** the shared `DhunPlayer` interface is implemented on Android by
`AndroidDhunPlayer`, which holds the connected `MediaController`. Media3
enforces application-thread (main) access on **every** `MediaController`
method — setters, getters, `prepare()`, even `release()`. All ViewModels
(`ArtistViewModel`, `PlayerViewModel` play paths, `NowPlayingPersistence`)
run on `Dispatchers.Default` and call the player directly → any of them
crashes. The reported stack is only the first call to hit the check
(`setMediaItems` in `prepareQueue`); the 500 ms position poll and every
transport button are the same class of bug.

**Fix:** `AndroidDhunPlayer` now owns the threading invariant — callers are
untouched:
- `private val mainHandler = Handler(Looper.getMainLooper())` +
  `onMain { }` (inline when already on main, preserves FIFO order),
- every controller call (setMediaItems/addMediaItem/seekTo/play/pause/
  removeMediaItem/moveMediaItem/stop/prepare/getters/repeat/shuffle/volume)
  runs inside `onMain`,
- `prepareQueue` (suspend) uses `withContext(Dispatchers.Main)` so callers
  that chain calls (restore → seekTo) keep ordering,
- the 500 ms position poll is pinned `scope.launch(Dispatchers.Main)`,
- `refresh()` (StateFlow projection reading controller getters) and
  `release()` are marshalled the same way.

**Verification:** compile gate = CI (`:app-android:assembleDebug`); on-device
retest on the user's hardware (artist shuffle play no longer crashes;
background playback checklist `docs/verification/03-android-skeleton.md`).

**Rule for the future:** the `DhunPlayer` implementation for a process-bound
engine (ExoPlayer) does NOT need this — only `MediaController` (cross-process)
does. If a new Android player implementation wraps a controller, it MUST
marshal to main.

---

## 2026-09-05 · CI red #4 (run 33938679193, head a20165b): shared unit test — NOT the desktop code

**Symptom:** `:shared:jvmTest` failed; steps "Android debug build" and
"Probe compiles" skipped. Failing assertion
(`NowPlayingPersistenceTest.queueAndProgressArePersistedThenRestoredPaused`,
line 129): `expected:<[T1, T2, T3]> but was:<[T1, T2, …truncated…]>` — the
queue persisted right after `prepareQueue` didn't round-trip.

**Root cause:** race in `NowPlayingPersistence`. `prepareQueue` sets
`queue` and then `currentTrack`; both collectors fire `snapshot()` almost
simultaneously. Each `snapshot()` hops to `Dispatchers.Default` inside
`withContext(io)`, so two `saveQueue` transactions (clearQueue → insert 3
rows → upsertState) interleave on the DB. The test's in-memory driver is
`JdbcSqliteDriver(IN_MEMORY)` = ONE shared connection, so the interleaving
corrupts the queue rows. Same latent race exists in production (Android
activityScope is Main, but the same `withContext(Default)` hops make two
snapshots concurrent on the DB layer). The test was green on main and on
earlier PR commits — it's a load/timing flake, which is why CI runs 1–3
(red on the DESKTOP compile at step 6, see below) never surfaced it: step 4
never got reached as a failure.

**Fix:** `NowPlayingPersistence` serializes all now-playing writes with a
`Mutex` (`writeMutex.withLock` around `save(...)` and `save.progress(...)`).
Each `saveQueue` remains a single atomic `db.transaction`; serialization
removes the only hazardous interleaving (two queue-rewriting transactions).

**Verification:** CI run for the fix push (step 4 must pass; then step 6
finally compile-checks the desktop round-3 code — first real signal for it).

---

## 2026-09-05 · CI reds #1–#3 (runs 33887658349 / 33928843140 / 33930616806): Compose Desktop 1.8.2 API

All three failed at CI step 6 "Probe compiles" — `:tools:playback-probe`
**chains the desktop compile** in CI only (`if (System.getenv("GITHUB_ACTIONS") == "true")` →
`dependsOn(":app-desktop:compileKotlinJvm")` in its build.gradle.kts; ci.yml
has no desktop step by policy of the time). So "desktop compile errors"
appear under a step named "Probe compiles". Remember that mapping.

The 1.8.2 desktop Window API is NOT what 1.9+/2.x docs show. Verified from
source: `JetBrains/compose-multiplatform-core` tag `v1.8.2`
(`compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/window/…`).

| Do NOT use (1.8.2) | Use instead (1.8.2) |
|---|---|
| `windowScope` parameter | content lambda receiver is implicit `FrameWindowScope`; `window` = `ComposeWindow : JFrame` (public AWT) |
| `LocalWindow` / `LocalComposeWindow` | **internal** in 1.8.2 — use `FrameWindowScope.window` or AWT `Frame.getFrames()` title lookup |
| `rememberWindowState(position = Offset)` | `position: WindowPosition` (`WindowPosition(x.dp, y.dp)` = Absolute) |
| `Float.px` | does not exist — convert via density where needed |
| `skipTaskbar` param | absent in 1.8.2 (mini-player shows in taskbar — KNOWN_LIMITATIONS) |
| `Key.LEFT` / `Key.RIGHT` | `Key.DirectionLeft` / `Key.DirectionRight` |
| `Key.Space` | `Key.Spacebar` |
| `KeyEvent.type` "unresolved" (round 3) | cascade of the unresolved `Window(...)` call — `type`/`isCtrlPressed` DO exist |

Round-3 fix `a20165b` was source-verified against the above; its CI
verification was blocked by the test race (previous entry) — confirmed only
when a CI run reaches step 6 green.

Round 4 (run 33943041377 on `4602d9d`) finally reached the desktop module
and exposed 8 more — all small API/type mixups:
- `Main.kt` `rememberWindowState(width/height/position=…)`: **`Long.dp`
  does not exist** (Int/Float/Double do) — the persisted geometry is `Long`
  (px) → convert with `.toFloat()` before `.dp`. (miniState with Int args
  compiled fine — the failing args were exactly the Long-derived ones.)
- `TrayIcons.kt`: `0xFF161616` / `0xFFBB86FC` are **Long literals** in
  Kotlin (> Int.MAX) → `Color(Int)` mismatch → `.toInt()`.
  `(s * 0.12f).coerceAtLeast(1)` — Float receiver got an Int →
  `.toInt().coerceAtLeast(1)`.
- `DhunTray.kt`: `java.awt.MenuItem` has **`label`**, not `text`
  (getLabel/setLabel); and an `inline` lambda passed to
  `SwingUtilities.invokeLater` needs **`noinline`**.

Also settled from the v1.8.2 source (use as reference): `application { }`
**IS a composable context** (KDoc: `fun main() = application { Window … }`),
and the `Window` overload with `undecorated: Boolean = false` has defaults
for every parameter (state/title/resizable/alwaysOnTop/onKeyEvent/content
all named-safe).

Round 5 (run 33944244828 on `21201fc`): Main/DhunTray/TrayIcons all
compiled; errors moved to `Smct.kt` (JNA):
- `com.sun.jna.platform.win32.GUID` **does not exist** (the platform GUID
  is nested in `WinNT`) — the stable home is base-jna
  `com.sun.jna.win32.Guid.GUID` (Data1 int / Data2 short / Data3 short /
  Data4 byte[8]) → added a `guidFromIid()` byte converter (Win32 GUID =
  first 3 fields LE, last 8 bytes as-is; verified against the
  ddb0472d-… bytes by hand).
- jna-platform (User32) was only on the classpath **transitively** (via
  vlcj) — declared `jna-platform:5.17.0` explicitly + THIRD_PARTY line.
- JNA `Memory` constructor takes **long** — `Memory(Native.POINTER_SIZE)`
  (Int const) rejected → `.toLong()` / `4L`.
- RUNTIME caveat (not a compile issue, machine-verify): raw
  `Function.invoke` marshals Structure args **by reference**, so the
  REFIID inside `vtableCall` may need by-value marshaling on a real
  machine. The probe is failure-isolated (logs HRESULT, never throws;
  documented fallback = tray path) so this can't break the app.

Round 6 (run 33944782718 on `a47fd84`): `com.sun.jna.win32.Guid` STILL
unresolved (import line!) and `User32.FindWindowW` / `User32.RECT`
unresolved too — while the User32 *import* resolved and base-jna
Function/Memory/Native all resolved. Conclusion: **the exact win32
helper classes of the JNA artifacts are version/artifact-split fragile;
do not build DHUN interop on them.** Rewrote `Smct.kt` fully
self-contained on base JNA: own `WinGuid`/`WinRect` `Structure`s
(explicit `getFieldOrder`), own `Native.load("user32")` interface with
`Pointer`-typed HWND params, SWP_* as local consts. Dropped the
jna-platform direct dep again (nothing references it; vlcj still pulls
it transitively for itself).

Rounds 7–9 (runs 33945300702 → 33945909159/33946130860 → 33946527454
GREEN): the JNA rewrite held; the rest was small 1.8.2/JNA-5.17 API
graining — **and the first lesson is process, not code:**
- **Check-run annotations are capped at 10 per run** (8 are consumed by
  Gradle's own build-failure annotations). In run 33945300702 the
  visible 10 hid 12+ real errors: `DesktopHarnessScreen.kt` had 12
  `Color(0xFF…)` literals — every 0xFFxx value > Int.MAX = Long →
  `Color(Int)` mismatch. Rule: when a desktop file is untested,
  pre-scan it for the known hazard classes (Long hex literals,
  missing dp extensions) instead of waiting for the capped list.
- JNA 5.17: `Pointer.getPointer(offset)` takes a **long** offset;
  `Function.invokeInt(Object[])` is NOT vararg (no `*all` spread);
  `Pointer` has **no `toLong()`** — log via `toString()`, pass the
  `Pointer` itself as the HWND arg.
- Compose 1.8.2: `awaitFirstDown()` takes **no parameters**
  (`requireCapture` is a later version); `PointerInputChange` has no
  `press()` — use `consume()`.
- vlcj 4.8.2 (javadoc-verified): the track-ended event is
  `MediaPlayerEventAdapter.finished(MediaPlayer)` — NOT `ended`.
- MiniPlayerWindow.kt: `ArtworkImage` lives in
  `dev.dhun.design.components`, and **`edit_file` can silently not
  persist in this sandbox** — verify edits with grep after applying;
  sed/perl/python are the reliable hammers.
- Also fixed while in the file (runtime correctness, machine-verify):
  `RoGetActivationFactory` takes an **HSTRING handle** — built with
  `WindowsCreateString`/freed with `WindowsDeleteString`; `FindWindowW`
  takes `WString` (wide).

Final state: run `33946527454` on `3cd4bf8` = **first fully green CI**
(shared tests, android assembleDebug, probe + desktop compile). PR #9
merged @ `697cf54`.

---

## 2026-09-05 · Background playback killed by OEM battery savers (MIUI/HyperOS/OneUI)

**Symptom (class of report):** music stops when the phone is locked / app
swiped away; OEM devices kill the playback service within minutes.

**Root cause (code):** `DhunPlaybackService` (MediaSessionService) never
called `MediaSession.startForeground(...)` — no foreground status, no
media notification. A background-only service is exactly what MIUI
cleaners, OneUI "battery saver" and HyperOS kill first. The manifest
already had `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` + `mediaPlayback` type —
the runtime call was missing.

**Fix:**
- `DhunPlaybackService.onCreate` → plain `Service.startForeground(1,
  notification)` — with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
  on API 29+ (targetSdk 35 REQUIRES the type on Android 14+). The
  notification uses `NotificationCompat` +
  `MediaStyleNotificationHelper.MediaStyle(session)` (the media3 1.5.x
  helper's NESTED MediaStyle bound to the session — the system drives the
  transport state from it) + `setShowActionsInCompactView(0,1,2)`; a
  Player.Listener re-posts on track transition / play-state change so
  title/artwork stay live. Channel `dhun_playback`, `ic_notification`
  vector (minSdk 26 → vectors are fine).

**API correction (learned the hard way, do NOT repeat):** in media3 **1.5.1**
there is NO `MediaSession.startForeground(...)`, NO `MediaSession.sessionId`,
and NO static `MediaStyleNotificationHelper.createNotification(session,
icon, intent)` — first attempt failed CI compile on exactly those (run
33941799559). The real pattern is Service.startForeground + the nested
`MediaStyleNotificationHelper.MediaStyle(session)`. Second compile round
(run 33942371150) — smaller mixups: `NotificationCompat.CATEGORY_MEDIA`
does not exist, `MediaMetadata.artworkData` is a `ByteArray` (no
`toBitmap()` — use `BitmapFactory.decodeByteArray(data, 0, data.size)`).
Third round (run 33942622916): `android.app.Notification.CATEGORY_MEDIA`
**also does not exist** — the framework has no media category constant at
all (setCategory removed; MediaStyle + the mediaPlayback FGS type carry
the semantics). Lesson, repeated twice: **verify small API surfaces
against the compiler, not memory.**
- `MainActivity.attach()` → one-shot (per process) system dialog via
  `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `package:` URI
  (needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission, added to
  manifest). Asked only while the app is in use.
- ExoPlayer already holds a partial wake lock (`C.WAKE_MODE_LOCAL`) and
  handles audio-focus / becoming-noisy — unchanged.

**Still manual (cannot be requested programmatically — put on the user's
checklist, `docs/verification/03-android-skeleton.md`):** MIUI/HyperOS
"auto-start" + "lock in recent apps" per-app switches; OneUI battery
settings "unrestricted".

**Verification:** on-device — play → lock → 30 min → audio continues;
`adb shell dumpsys activity services dev.dhun.android | grep fg` shows
foreground + mediaPlayback.

---

## 2026-09-05 · SMTC status (desktop media keys / now-playing tile)

Phase 1 (spike) code is in: `desktop/Smct.kt` — WinRT activation via JNA/
combase from the unpackaged JVM → `ISystemMediaTransportControlsInterop` →
`GetForWindow` → `IsTransportControlsButtonVisible` live check; HRESULTs
logged at startup (`SMTC probe PASS/FAIL — …` console line), off-switch
`-Ddhun.smct=false`.

Phase 2 (`UpdateMetadata` + `ButtonPressed` events, i.e. the actual tile +
media-key handling) needs two interface IIDs pulled **on a Windows
machine** from the system winmd — procedure in
`docs/verification/12-desktop-native.md`. Until then hardware media keys do
NOT drive DHUN; the shipping fallback is tray menu + keyboard shortcuts +
mini-player (documented in `.ai/KNOWN_LIMITATIONS.md`).

---

## Environment traps (this sandbox + CI mechanics) — recurring

- **No JDK / Android SDK / adb / display** in the sandbox. CI is the
  compile gate. Never claim "compiles" locally — claim "source-verified
  against <source of the API>", and let CI confirm.
- **Sandbox resets the repo between user turns** (re-clone to the merge
  base `d27eb37`; working tree survives, local commits/objects can be
  wiped). After every turn start: `git log --oneline` + `git fetch origin`
  + compare local vs remote head; unpushed commits must be re-pushed or
  they are gone (commit `04f00eb` was lost once this way and recreated as
  `c808819`).
- **GitHub token flaps mid-session.** `git push` / `gh` fail with
  "could not read Username" / 401 intermittently, then work again. Retry
  a few times; if persistently failing, the user must reconnect GitHub in
  Arena.
- **CI logs are unreachable** from this sandbox (`gh run view --log-failed`
  → results-receiver EOF). Use the annotations API instead:
  `gh api repos/99ggprooo00-code/DHUN/check-runs/<job-id>/annotations`
  (job id from `gh run view <run> --json jobs`). Annotation messages are
  first-line / truncated — enough for compile errors, too short for full
  test output.
- **Egress:** direct `curl` mostly blocked (lrclib 35, maven 000);
  `fetch_page` works; `raw.githubusercontent.com` works for some repos
  (compose-multiplatform-core: yes; androidx/media3 tag paths: 404 —
  don't retry). `git` egress works only for the DHUN repo (session token).
- **Stack is KMP/Gradle, not npm/Tauri.** Desktop packaging = jpackage
  (`:app-desktop:createMsi`, Windows-only host). Any "npm run build /
  Tauri installer" instruction adapts to that — do NOT create npm tooling.
- **Desktop compile in CI runs under the "Probe compiles" step** (probe
  module chains it, GITHUB_ACTIONS-guarded) — see reds #1–#3 above.
- **Rolling test release policy (user decision 2026-09-01):** exactly one
  release tagged `test`, replaced on every push to main; assets
  `dhun-test.apk` (+ `.sha256`) and — since 2026-09-05 — `dhun-test.msi`
  (+ `.sha256`). No version numbers, no history for unfinished builds.
  Stable URLs: `/releases/download/test/dhun-test.apk`, `…/dhun-test.msi`.

## 2026-09-05 · Phase 13 Android native polish compile gate

**Implementation:** `8669e09` adds edge-to-edge setup and safe-drawing insets,
static launcher shortcuts, saved navigation state, battery-exemption rationale,
and an 840dp shared navigation rail. `c2a86df` was an intermediate attempt to
reuse the bottom navigation item helper.

**CI failure:** run `33958722933` reached `:shared:compileKotlinJvm` and
reported `Unresolved reference 'NavigationBarItem'` plus two composable-context
errors at `DhunAppShell.kt:394-398`. Material3's `NavigationBarItem` is a
`RowScope` extension; the helper had been moved outside the `NavigationBar`
content scope. An import alias did not fix receiver resolution (`33958802810`
reproduced the same error).

**Fix:** `4de9795` splits the shared rendering into `RowScope.AppBottomNavigationItem`
and `ColumnScope.AppRailNavigationItem`, leaving each Material3 item in its
required layout scope. CI run `33958894084` passed shared tests, Android debug
build, and the Desktop/probe compilation.

**Environment:** local `./gradlew` remains blocked by the sandbox's missing
`JAVA_HOME`/`java`; CI is the compile gate. Device rotation, gesture-nav,
shortcut launcher, battery/OEM, and 30-minute playback soak evidence remain
open and must not be inferred from CI.

## 2026-09-05 · Phase 14 rot-drill dispatch gate

**Attempt:** After pushing `5897c5c` + `5573f9a`, a manual dispatch was
requested with `gh workflow run rot-drill.yml --ref arena/01a070b3-dhun`.

**Result:** GitHub returned `HTTP 403: Resource not accessible by integration`.
The authenticated GitHub bot is valid and PR CI is green, but the workflow is
not on the default branch yet; `gh workflow view rot-drill.yml` still shows
the old placeholder definition from `main`. No live probe verdict or issue
alert/recovery exercise can be claimed from this attempt.

**Next action:** Keep the workflow code staged and run it once the workflow is
available on the default branch or Actions dispatch permission is restored.
Until then, the Phase 14 rot-drill step remains open; do not mark it green
because the YAML has not been live-executed.

**Workflow review fix:** `29326cc` removes `cache: pip` from
`actions/setup-python@v5`; this repository has no requirements file, so the
rot-drill must install `yt-dlp` directly without asking the action to resolve a
missing cache dependency. The live workflow remains unexecuted because the
manual dispatch is still blocked by the GitHub 403 above.
