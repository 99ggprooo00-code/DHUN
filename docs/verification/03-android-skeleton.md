# Phase 03 — Android App Skeleton + Playback — verification evidence

Date: 2026-09-01. Environment: sandbox (no Android device attached).

## What is verified HERE

| Check | Result |
|---|---|
| `./gradlew :app-android:assembleDebug` | ✅ BUILD SUCCESSFUL (1m46s), APK 15.4 MB |
| APK binary manifest | ✅ package `dev.dhun.android` v0.1.0 · service `DhunPlaybackService` exported, `foregroundServiceType=mediaPlayback`, intent-filter `androidx.media3.session.MediaSessionService` · permissions INTERNET/POST_NOTIFICATIONS/FOREGROUND_SERVICE(+MEDIA_PLAYBACK)/WAKE_LOCK |
| Shared module on Android target | ✅ `:shared:compileDebugKotlinAndroid` — commonMain needed zero changes (target-agnostic as planned) |
| Unit tests unaffected | ✅ 34/34 jvmTest green |
| Whole-project compile sanity | ✅ probe (`compileKotlin`) + shared (both targets) build clean |
| APK artifact | copied to workspace root as `dhun-debug.apk` for on-device sideload |

## What NEEDS A REAL DEVICE (next step of this phase)

Per MASTER_PROMPT.md, these acceptance criteria are only meaningful on
hardware and remain OPEN until then:
1. Real audio audible from YouTube Music (resolution → ExoPlayer → speaker)
2. Lock screen controls + notification player appear and work
3. Background playback survives app swipe-away
4. 403 mid-stream recovery observed (resolve → play → invalidate → resume at position)
5. No-network → clean typed error, no crash

**How to verify:** install `dhun-debug.apk` on Android 8+ (allow unknown
sources), open DHUN, grant notification permission, search "queen", tap a
row, hear audio, lock the phone, use lock controls, swipe the app away.

## Implementation notes (what shipped)

- `DhunPlaybackService` (Media3 MediaSessionService): owns ExoPlayer +
  MediaSession — background audio, focus handling
  (`handleAudioFocus=true`), noisy-stream pause, local wake lock.
- Resolution: media items carry `dhun://track/<id>` URIs; a
  `ResolvingDataSource` rewrites them to real stream URLs via
  `DhunStreamCache` (TTL 5h). ANY resolve failure surfaces as IOException →
  player error (never crashes the service).
- 403 mid-stream recovery: on `ERROR_CODE_IO_BAD_HTTP_STATUS` the cache
  entry is invalidated, the player seeks back to its current position and
  re-prepares (max 2 retries per track) — playback resumes where the user
  was.
- `AndroidDhunPlayer`: shared `DhunPlayer` interface over a MediaController;
  state/position/duration flows polled at 500ms; queue projected from the
  session timeline.
- Koin graph: InnerTubeClient → OwnClientStreamResolver (ADR-001: no yt-dlp
  on Android) → YouTubeMusicProvider → DhunStreamCache → HarnessViewModel.
- POST_NOTIFICATIONS runtime request on 33+.

## Divergences (documented, not silent)

- Test harness screen is intentionally plain Compose (deleted in later UI
  phases). App icon is the framework media-play icon until Phase 06/13.
- Android uses Media3's native queue/shuffle/repeat (the shared QueueManager
  drives the desktop player in Phase 04; keeping one source of truth per
  engine avoids two-queue-state bugs).
