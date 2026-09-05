# DEBUG_LOG — incidents, root causes, environment traps

Format: date · title · symptom (with stack where available) · root cause ·
fix · verification state. Newest first. If you hit one of these again,
read this entry before re-diagnosing.

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
