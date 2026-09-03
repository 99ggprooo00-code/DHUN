# Phase 04 — Desktop skeleton + vlcj playback

Status: **CODE COMPLETE — module ACTIVE in the build and CI-green (blocker root-caused 2026-09-03, see below); hardware verification OPEN.**

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

## CI configuration blocker — ROOT-CAUSED AND FIXED (2026-09-03)

**Root cause:** `compose.desktop.application { nativeDistributions { packageVersion = "0.1.4" } }`.
Compose Desktop's DMG/MSI packagers require `MAJOR > 0` and validate the
version at **project configuration time**, so `:app-desktop` failed to
configure with

```
org.gradle.api.GradleException: * Illegal version for 'Dmg': '0.1.4' is not a valid version.
  * Correct format: 'MAJOR[.MINOR][.PATCH]', where MAJOR is an integer > 0 …
```

Because Gradle configures every project before running any task, the
failure surfaced on whatever task was first in the invocation
(`:shared:jvmTest`) and looked like a plugin-flavor conflict. The earlier
bisection rows below were misread: the "kotlin(jvm)" template rows carried
the same `packageVersion`, and the `kotlin("multiplatform")`-only row passed
because it had no `compose.desktop` block to validate.

**How it was finally seen:** `settings.gradle.kts` now prints the Gradle
failure cause chain as `::error::` workflow commands when running under
GitHub Actions; those land in check-run *annotations*, which the agent
sandbox CAN read via the REST API even though log archives are blocked.
(Evidence: PR #4 run `33710693678`, annotation "Gradle failure (2)".)

**Fix:** `packageVersion = "1.0.4"` (installer versions map DHUN 0.x →
1.0.x until v1.0.0), `include(":app-desktop")` re-enabled, CI compiles the
module (`:app-desktop:compileKotlinJvm`) on every PR. `jvm { withJava() }`
dropped (deprecated in KGP 2.1 and not needed).

### Historical bisection (2026-09-02, kept for the record)

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

### Owner action — desktop compile step in CI (needs `workflows` permission)

The agent token cannot edit `.github/workflows/*`. Until the owner adds it,
the desktop module is configured on every CI run (so a config regression
would fail CI) but not compiled there. To compile it too, add after the
Android step in `.github/workflows/ci.yml`:

```yaml
      - name: Desktop module compiles (Phase 04)
        run: ./gradlew :app-desktop:compileKotlinJvm --no-daemon
```

(Also: `actions/setup-java@v4` is deprecated — bump to `@v5` in the same edit.)

### Why the module uses `kotlin("multiplatform")` with a single `jvm()` target

Same plugin flavor as `:shared`; sources live in `src/jvmMain`. Kept as is —
it works and avoids a second KGP flavor in the build.

## Hardware verification checklist (OPEN — requires a desktop with VLC/libVLC)

1. `yt-dlp` on `PATH`; `libvlc` installed (e.g. Ubuntu: `sudo apt install
   libvlc-dev vlc`). KNOWN_LIMITATIONS.md has details.
2. `./gradlew :app-desktop:run --no-daemon` (module is included by default now)
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
