# Phase 04 — Desktop skeleton + vlcj playback

Status: **CODE COMPLETE — CI-green module committed but CI-opt-in; hardware verification OPEN.**

Module: `app-desktop` (JVM target, Compose Multiplatform desktop UI, vlcj audio engine).

## What Phase 04 delivers

1. **Desktop entry point** — `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt`:
   Koin graph (`YouTubeMusicProvider.forDesktop()` → `DesktopDhunPlayer` →
   `DesktopHarnessViewModel`), 1200×780 window, full libVLC teardown +
   scope cancel on close (`Window.onCloseRequest`).
2. **Real audio on desktop via vlcj** — `DesktopDhunPlayer.kt`: vlcj
   (`MediaPlayerFactory("--no-video", "--quiet")`) implements the **shared**
   `DhunPlayer` contract; stream URLs come from the desktop resolver chain
   (own InnerTube client primary, yt-dlp failover — ADR-001).
3. **Same shared player logic on both platforms** — desktop drives the shared
   `QueueManager` (next/previous/shuffle/repeat decisions, incl.
   REPEAT_ONE) exactly like Android Phase 03 drives its own copy of the
   same class. State surfaces through the shared `PlaybackState` flows.
4. **Throwaway harness screen** — `DesktopHarnessScreen` mirrors the Android
   Phase-03 harness (search → queue → transport → seek → flows) so the same
   verification loop runs on desktop. Replaced by the real UI in later
   phases.
5. **Packaging** — `compose.desktop.application` with Dmg/Msi/Deb
   `targetFormats`, package `dev.dhun.desktop`.

## Acceptance criteria (from PROMPT_SEQUENCE.md)

| # | Criterion | Evidence |
|---|-----------|----------|
| 1 | Real audio on desktop via vlcj | HW checklist below — OPEN |
| 2 | All transport controls correct | HW checklist below — OPEN |
| 3 | Clean VLC process lifecycle on stop/exit | HW checklist below — OPEN |
| 4 | Same shared `DhunPlayer` drives both platforms | `DesktopDhunPlayer : DhunPlayer` (shared iface), shared `QueueManager` reused; desktop harness in this module for now |

## CI configuration blocker (2026-09-02) — READ BEFORE UNCOMMENTING

`app-desktop` is committed but **not activated in CI**: its `include(":app-desktop")`
line in `settings.gradle.kts` is commented out. Reason: an empirical blocker
found while getting this PR green.

### Bisection evidence (all runs on GitHub Actions, Gradle 8.14.2, Kotlin 2.1.20)

| `app-desktop` build script | Root pins | CI result |
|---|---|---|
| (no module included) | main shape | ✅ PASS |
| empty script (no plugins) | main shape | ✅ PASS |
| `plugins { java }` only | main shape | ✅ PASS |
| `kotlin("multiplatform")` only | main shape | ✅ PASS |
| `kotlin("jvm")` (template form) | main shape | ❌ FAILS at config of `:shared:jvmTest` |
| `kotlin("jvm")` + compose + deps | full Phase 04 pins | ❌ FAILS at config of `:shared:jvmTest` |
| `kotlin("multiplatform")` + `org.jetbrains.compose` 1.8.2 | + root compose pin | ❌ FAILS at config of `:shared:jvmTest` |
| `kotlin("multiplatform")` + `kotlin.plugin.compose` + `org.jetbrains.compose` | full Phase 04 pins | ❌ FAILS at config of `:shared:jvmTest` |

Every failure is a **configuration-time failure of an unrelated task**
(`:shared:jvmTest`, the very first task Gradle configures) the moment a
second Kotlin-Gradle-plugin flavor (`kotlin("jvm")`) or the Compose
Multiplatform plugin loads alongside `:shared`'s `kotlin("multiplatform")`
in one build. The failing step is always `Unit tests — shared domain
(queue, parsers, resolvers)` (job step 4), which never even reaches its
test. Memory growth (`-Xmx1536m`, metaspace 512m), compiler execution
strategy (`daemon`/`in-process`), and plugin version pinning were all
eliminated as causes.

CI error logs were unreachable from the sandbox that ran these trials
(GitHub's results-receiver host is network-blocked there), so the exact
Gradle exception text could not be captured. The blocker reproduces with a
single `kotlin("jvm")` and zero dependencies, so it is a toolchain
interaction, not a DHUN-code issue.

### Owner action to activate desktop CI (needs `workflows` permission)

1. Uncomment `include(":app-desktop")` in `settings.gradle.kts`.
2. In `.github/workflows/ci.yml` after the Android debug build step, add:
   ```yaml
   - name: Desktop module compiles (Phase 04)
     run: ./gradlew :app-desktop:compileKotlinJvm --no-daemon
   ```
3. If the same configuration failure appears, capture `./gradlew
   :shared:jvmTest --stacktrace` output locally (or on any runner with
   reachable logs) — the full exception will identify the plugin
   interaction. Expected root cause per upstream reports: Kotlin Gradle
   plugin 2.1.x cross-version/flavor classpath conflict when both the JVM
   and Multiplatform flavors configure the same build (see KT issue
   tracker), and/or Compose Multiplatform 1.8.x hot-reload sub-plugin
   resolution on the portal.

### Why the module still uses `kotlin("multiplatform")`

Phase 04 sources live in `src/jvmMain` and the module applies
`kotlin("multiplatform")` with a single `jvm()` target (same plugin flavor
as `:shared`, which is proven to configure in this build) rather than
`kotlin("jvm")`. That is deliberate and documented in the build script.

## Hardware verification checklist (OPEN — requires a desktop with VLC/libVLC)

1. `yt-dlp` on `PATH`; `libvlc` installed (e.g. Ubuntu: `sudo apt install
   libvlc-dev vlc`). KNOWN_LIMITATIONS.md has details.
2. Uncomment `include(":app-desktop")`, then:
   `./gradlew :app-desktop:run --no-daemon`
3. Search a known song → tap it → audio audible through the desktop
   speakers within a few seconds (resolver may fail over to yt-dlp).
4. Transport: pause/resume, next, previous (incl. previous on first track =
   restart), seek slider; repeat ALL / ONE / OFF and shuffle all behave like
   Android Phase 03.
5. Auto-advance at track end; REPEAT_ONE replays the same track.
6. Close the window mid-playback and after stop → `ps aux | grep -i vlc`
   shows no orphaned `vlc`/`libVLC` process; app exits cleanly.
7. Log each result in this file under Evidence, dated.

## Evidence

(empty — hardware verification pending; CI state above is the only
machine evidence so far, all runs listed in the table are recorded in the
repo's PR #3 run history on 2026-09-02.)

## Divergences & notes

- Android keeps Media3's native queue; desktop drives the shared
  `QueueManager` — both converge on the shared `DhunPlayer` contract.
- Desktop state flows are the shared `PlaybackState` values; polling
  (500 ms) reads position/duration from libVLC because vlcj has no
  push-model position stream at this layer.
- vlcj callbacks (`playing`, `paused`, `finished`, `error`) never call
  back into libVLC on the native thread — queue advance happens on an app
  coroutine (vlcj 4 threading rule).
