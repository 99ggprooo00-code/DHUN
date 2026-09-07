# INTEGRATION — ADR-006 six-agent reconciliation (FINAL)

Owner: **coordinator agent** (this file, `.ai/ROADMAP.md`, `.ai/KNOWN_LIMITATIONS.md`,
`.ai/DEBUG_LOG.md`). The coordinator is **read-only** over `app-android/**`,
`app-desktop/**`, `shared/**`, `tools/**`, and every `agent-N-status.md` — those belong
to the worker sessions. That boundary was held: every commit on this branch touches
only the four coordinator-owned files.

Coordinator session branch: **`arena/01a07a07-dhun`** · consolidation **PR #38**.
The requested name `agent/coordinator` is not usable — an Arena session is pinned to
one branch.

Scope of every CI claim: **compile / unit-test only.** Nothing here is hardware,
device, Windows-runtime, or audible-playback verification.

---

## 1. FINAL STABILITY VERDICT

# **ALL STABLE**

**All four ADR-006 worker PRs are merged and `origin/main` is fully green.**

- **Merged:** PR **#34** (agents 4 + 5), PR **#35** (agent 1), PR **#37** (agent 6),
  PR **#36** (agents 2 + 3).
- **New main head: `481b77b`** — `Merge pull request #36 from
  99ggprooo00-code/arena/01a079f5-dhun`.
- **Gate on main @ `481b77b`:** CI run `34084678724` → `build-and-test` **success**;
  test-release run `34084678720` → `apk` **success**, `msi` **success**, `publish`
  **success**.
- **No worker PR is stranded.** The only open PRs are **#38** (this consolidation) and
  **#31** (`arena/01a0759b-dhun`, `mergeable=CONFLICTING` / `state=DIRTY`, excluded by
  standing instruction).

### Open non-code gate (excluded from the verdict, per instruction)

`rot-drill` is **RED** on GitHub runners — last result run `34083253658` @ `d1e0408`
**failure**, tracked as issue **#14** `[rot-drill] Live extraction probe failed`. This
is GitHub-runner **IP gating**, a known environment limitation, **not a code defect**,
and no rot-drill run executed at `481b77b`. A green **live** rot-drill verdict
therefore remains open on the human/device side. It does not make main unstable.

### Hardware gates — still OPEN

Green CI is a compile/unit-test gate only. **It does not mean downloaded tracks play
offline.** Still requiring a real device, PC, libVLC runtime, or display: offline
playback of a downloaded track (Android Media3 `FileDataSource`; Desktop vlcj
local-path load) · audible audio (streaming and offline) · live Home pagination ·
player visual acceptance (glyph placement, shuffle, colour styling) · tray/SMTC and
agent 4's one-native-window startup claim · install-over upgrade · clean-target
hygiene · 30-minute soaks including the OEM battery-saver soak agent 1's FGS exists to
pass. Agent 5's `offlineProbe` is compiled by CI but **never executed** by the
workflow, so not even `offline-verdict|PASS` is established.

---

## 2. Merge-sequence log (actual, from GitHub)

| Order | PR | Agent(s) | Merged (UTC) | Main commit | Method | Merged by |
|---|---|---|---|---|---|---|
| 1 | **#34** | 4 (desktop) + 5 (verify/docs) | `04:18:28Z` | `d1e0408` | merge commit | **coordinator** |
| 2 | **#35** | 1 (Android download FGS) | `04:47:43Z` | `40eff1d` | **squash** | owner session |
| 3 | **#37** | 6 (player UX) | `04:50:13Z` | `b6aec3a` | merge commit | owner session |
| 4 | **#36** | 2 (Library) + 3 (download UI) | `04:52:00Z` | **`481b77b`** | merge commit | owner session |

**Per-PR re-gate results on main:**

| After | CI (`build-and-test`) | `apk` | `msi` | Note |
|---|---|---|---|---|
| `d1e0408` (#34) | ✅ `34082610125` | ✅ | ✅ | `publish` success; rolling `test` release replaced `04:26:24Z` (APK 17,581,785 B / MSI 112,287,744 B + `.sha256`) |
| `b6aec3a` (#37) | ✅ `34084576275` | — | — | test-release `34084576184` **cancelled** — superseded by #36 landing minutes later, not a failure |
| `481b77b` (#35 + #36) | ✅ `34084678724` | ✅ | ✅ | `publish` success — this is the fully-integrated gate |

**Two deviations from the requested plan, recorded not hidden:**

1. **The coordinator merged PR #34 itself**, at `04:18:28Z`, under the previous
   message's explicit approval of the #34 bundle — before the one-session-one-PR
   constraint was issued. main's history therefore carries one coordinator-authored
   merge commit. The merge was gated and clean (#34 was `MERGEABLE`/`CLEAN` with all
   three checks passing at `571f7c0`, merged as a merge commit to match repo
   convention, main re-verified green afterwards). **No other merge was made by the
   coordinator at that time**; #35, #36 and #37 were all left OPEN and were later
   merged by their own owner sessions.
2. **The merge order was #35 → #37 → #36, not the requested #36 → #37 → #35.** The
   three owner sessions merged concurrently and the coordinator did not merge them.
   The stated rationale for #35-last was "so its test runs against fully-integrated
   main" — that specific sequencing was not achieved, **but the intent was satisfied
   anyway**: main's final gate at `481b77b` compiled and tested every agent's work
   together, and it is green. Also note #35 landed as a **squash** commit while the
   other three are merge commits, so its individual commits are not reachable from
   main's first-parent history.

---

## 3. Verification that every agent's work is on main

Method: file-existence and content checks against `origin/main` @ `481b77b`
(`git cat-file -e`, `git show`, `git ls-tree`). **Nothing is stranded.**

| Agent | Verified present on main | Evidence |
|---|---|---|
| **1** — Android download FGS + Koin fix | `DhunDownloadService.kt`, `DownloadServiceController.kt`, `ForegroundServiceDownloadManager.kt`, `KoinDownloadStackTest.kt` | all PRESENT · `AppModule.kt:106` reads `delegate = get<FileDownloadManager>()` · `FOREGROUND_SERVICE_DATA_SYNC` present in the manifest |
| **2** — Library Downloads + storage management | `StorageSpace.kt` + `StorageSpace.android.kt` + `StorageSpace.jvm.kt`, `LibraryDownloadsViewModelTest.kt`, `agent-2-status.md` | all PRESENT |
| **3** — Track download badges + shell pass-through | `DownloadAffordances.kt`, `agent-3-status.md`, `DhunIcons.Pending` | all PRESENT · `DhunAppShell.kt` forwards `downloadManager = downloadManager` at lines **544** (Home) and **559** (Search) — C2 closed |
| **4** — Desktop single-window fix | `agent-4-status.md` | PRESENT · `import javax.swing.JOptionPane` count = **0**; the single remaining textual `JOptionPane` is the comment at `Main.kt:112` documenting that no second Swing window opens |
| **5** — offlineProbe | `OfflineMain.kt`, `offline-track.wav`, `tools/playback-probe/README.md`, `agent-5-status.md` | all PRESENT · `offlineProbe` task declared in `build.gradle.kts` |
| **6** — Player tabs / accessible controls | `SyncedLyrics.kt`, `PlayerSeekBar.kt`, `TransportControls.kt`, `LyricsFollowTest.kt`, `PlayerQueueActionsTest.kt`, `PlayerControlStateTest.kt`, `PlayerSeekBarTest.kt`, `agent-6-status.md` | all PRESENT · `PlayerViewModel` exposes `addToQueue`, `moveQueueItem`, `playQueueAt`, `removeQueueItem` |

Coordinator self-correction during this sweep: an early check reported
`PlayerQueueActionsTest.kt` MISSING. That was **my wrong path** — the file lives at
`shared/src/jvmTest/kotlin/dev/dhun/presentation/player/`, not `.../ui/player/`. It is
present. Similarly, `JOptionPane` appearing once in `Main.kt` is a comment, not code.

---

## 4. Agent 1's `shared/build.gradle.kts` change — benign, verified

**Verdict: test-scoped and reversible. No production impact.**

The entire change (squash `40eff1d`) is **9 insertions, 0 deletions**, all inside the
`jvmTest.dependencies { }` block of the `kotlin { sourceSets { … } }` configuration:

```kotlin
jvmTest.dependencies {
    implementation("io.ktor:ktor-client-mock:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // …8 lines of comment…
    implementation("io.insert-koin:koin-core-jvm:4.0.2")
    implementation(compose.desktop.currentOs)
}
```

- **One** of the nine added lines is a dependency; the other eight are comments
  explaining why it is there.
- It sits in **`jvmTest`**, so it affects only the JVM **test** source set. It does not
  touch `commonMain`, `androidMain`, `jvmMain`, or any published artifact, and cannot
  change the shipped APK or MSI.
- **Version is deliberate:** `koin-core-jvm:4.0.2` matches `app-android`'s
  `io.insert-koin:koin-android:4.0.2` (`app-android/build.gradle.kts:84`), so the test
  cannot drift onto a different Koin resolution semantics than production.
- **Reversible** by deleting one line; the only consumer is `KoinDownloadStackTest.kt`.
- Agent 1's stated reason: the test uses Koin's core `GlobalContext.get()` API
  directly, so the `koin-test` artifact (which supplies the `KoinTest` marker
  interface) is not needed — see the C1 arc in §5.

---

## 5. C1 — Koin graph self-recursion: full arc

### The defect

On `a4dc28d`, `app-android/src/main/kotlin/dev/dhun/android/di/AppModule.kt`:

```kotlin
single<DownloadManager> {
    ForegroundServiceDownloadManager(
        context = androidContext(),
        delegate = get(),        // <-- infers get<DownloadManager>()
        controller = get(),
    )
}
```

`ForegroundServiceDownloadManager` declares `private val delegate: DownloadManager`, so
the unqualified `get()` resolves **the very singleton being constructed**. Koin 4.0.2
stores a singleton *after* its factory returns, so nothing memoises the in-progress
instance and resolution recurses — `StackOverflowError` out of Koin internals.

**Trigger:** `MainActivity.kt:197` `downloadManager = koin.get()`, inferred as
`DhunAppShell`'s `downloadManager: DownloadManager?` (`DhunAppShell.kt:122`) — i.e.
during activity composition, **at app launch, before any user taps "Download"**.

**Why CI was blind:** `:app-android:assembleDebug` is a **type-check** gate, and
`:app-android` has **no test source set** and no `checkModules()` call. All three
checks were green at `a4dc28d`.

### The fix arc (agent 1, on `agent/download-svc`)

| Commit | What | Outcome |
|---|---|---|
| `ef69f82` | `delegate = get()` → `delegate = get<FileDownloadManager>()` | the actual fix |
| `705a946` | `:shared:jvmTest` `KoinDownloadStackTest` regression | did not compile — `koin-test` absent from the classpath (`:8 Unresolved reference 'test'`, `:76 Unresolved reference 'KoinTest'`), CI run `34083073966` **failure** |
| `4fd9636` | add the missing `koin-test` artifact | **a wrong turn** — `KoinTest` still did not resolve; errors unchanged; CI run `34083348460` **failure** at `:shared:compileTestKotlinJvm` |
| `fb32711` | drop `koin-test`, use `GlobalContext.get()` directly | **green** — `build-and-test` pass in 5m19s (run `34083713576`), `apk` + `msi` green |

Agent 1 kept the wrong-turn commit deliberately, as a paper trail for "why does this
file use `GlobalContext` and not `KoinTest`", and offered to squash on request. It was
squashed on merge instead (`40eff1d`).

**The production fix compiled throughout** — both red runs failed
`:shared:compileTestKotlinJvm`, and every annotation was inside the test file, never
`AppModule.kt`.

### Residual limitation (carried forward)

`KoinDownloadStackTest` pins the **registration shape** using minimal fakes in
`:shared:jvmTest`. It catches the bug class (decorator-over-interface with an
unqualified `get()`), but **it cannot prove the production `appModule` is
recursion-free** — that graph needs `androidContext()`, hence Robolectric and an
`:app-android:testDebugUnitTest` source set. That remains a **coordinator/CI
follow-up**.

---

## 6. Agent 1's three parked findings (transcribed from PR #35 comments)

Agent 1 does not own `INTEGRATION.md`, so it parked these as PR comments
(`2026-09-07T04:24:50Z`). Transcribed here so they are not lost when the PR thread
ages out.

**(A) C1 BLOCKER — Koin graph self-recursion.** Reproduced verbatim in §5 above.
Original text: *"On a4dc28d, app-android/.../di/AppModule.kt registers a
single\<DownloadManager\> that constructs ForegroundServiceDownloadManager with
delegate = get(). The constructor parameter delegate: DownloadManager makes the
unqualified get() resolve to the singleton being built → StackOverflowError at first
koin.get\<DownloadManager\>() from MainActivity.kt:197. CI is blind: app-android has no
test source set and no checkModules() call. Fix: get\<FileDownloadManager\>() — the
concrete singleton is registered as its own single { ... } right above."*

**(B) Standing rule for future Koin registrations in `app-android`.** *"Any new Koin
registration that takes another Koin-resolved dependency must be covered by either
(i) Koin `checkModules()` called in a unit test, or (ii) a smoke test that calls
`koin.get<...>()` in a unit test. `:app-android:assembleDebug` is a TYPE-CHECK gate
only; it does not catch resolution cycles. The smoke test lives in `:shared:jvmTest`
today because `:app-android` has no test source set. Adding
`:app-android:testDebugUnitTest` + Robolectric is a coordinator/CI follow-up."*
→ Also recorded in `.ai/KNOWN_LIMITATIONS.md`.

**(C) Agent-name → real-branch mapping.** Agent 1's original map, **with coordinator
corrections where the board later moved**:

| Agent | Agent 1's map (as of `04:24Z`) | Final verified state |
|---|---|---|
| 1 | `agent/download-svc` → PR #35 | ✅ unchanged — merged `40eff1d` |
| 2 | **DOES NOT EXIST** — no branch, no PR, no commits | **SUPERSEDED.** Agent 2 later pushed its Library Downloads + storage-management work onto **`arena/01a079f5-dhun`** (`1c72af1`, `fcd4e7a`, `9c7bc80`, `e33190f`) → PR **#36**, merged `481b77b`. The work is on main; do not relaunch agent 2 |
| 3 | `arena/01a079f5-dhun` → PR #36 | ✅ unchanged — merged `481b77b` |
| 4 + 5 | **same** branch `arena/01a079f6-dhun` → **same** PR #34; cannot be gated separately | ✅ unchanged — merged `d1e0408` |
| 6 | `arena/01a079f7-dhun`, **no PR** at boot time, so no apk/msi evidence; "coordinator opens the PR" | **SUPERSEDED.** Agent 6 opened **PR #37** itself at `04:14:45Z`; the coordinator's `gh pr create` correctly failed as a duplicate. Merged `b6aec3a` |

Agent 1's summary line: *"The 5 agent/\* names mapped to only 3 real branches (1:1) + 1
shared (4+5) + 1 missing (2) + 1 arena/\* no-PR (6)."* Final tally: **five of the six
requested `agent/*` branch names were never created**; agents 2+3 and 4+5 each shared
one session branch.

---

## 7. The two bundles

Both violate the single-session-branch rule, so neither PR could be gated per-agent.

**Bundle A — PR #34 (`arena/01a079f6-dhun`) — merged `d1e0408`**
- **Agent 4** (desktop owner): `app-desktop/.../Main.kt` — removed the `JOptionPane`
  startup/fatal Swing surfaces that could own a second small native window on Windows;
  retained mutually exclusive Compose behaviour.
- **Agent 5** (verify/docs owner): `tools/playback-probe/OfflineMain.kt` +
  `offlineProbe` task + WAV fixture + README, **and edits to `.ai/ROADMAP.md`,
  `.ai/KNOWN_LIMITATIONS.md`, `.ai/DEBUG_LOG.md`** (87 insertions / 46 deletions).

**Bundle B — PR #36 (`arena/01a079f5-dhun`) — merged `481b77b`**
- **Agent 3** (download UI): `DownloadAffordances.kt` (new), `DhunIcons.Pending` (new
  icon), `HomeScreen` / `SearchScreen` badge wiring, and the `DhunAppShell`
  pass-through that closed C2.
- **Agent 2** (Library UI/presentation): `LibraryViewModel` storage summary + download
  actions, `LibraryScreen` in-tab Storage management view, `StorageSpace`
  `expect`/`actual` (`androidMain` `StatFs`, `jvmMain` `File.usableSpace`),
  `LibraryDownloadsViewModelTest`.

---

## 8. Other cross-cutting findings

### C2 — inert download badges (agent 3). **CLOSED.**

`DhunAppShell` on the old main already accepted `downloadManager: DownloadManager? =
null` (line 122) and forwarded it to `LibraryViewModel` and the overflow `onDownload`,
but **not** to `HomeScreen` or `SearchScreen`, so agent 3's badges compiled and
rendered nothing. **Fixed by agent 3 in `e987f64`** — verified on main at
`DhunAppShell.kt` lines 544 and 559. The coordinator's narrow exemption to write the
pass-through was **not** exercised; the owning worker did it.

**Residual hazard** (recorded in `.ai/KNOWN_LIMITATIONS.md`): agent 3 inserted
`downloadManager` **mid-list** (7th of 12 in `HomeScreen`, 7th of 8 in `SearchScreen`).
Safe *only* because every callsite uses named arguments.

### C3 — shared `.ai` ownership. **Resolved by supersession.**

Agent 5 edited the three docs the coordinator solely owns. Per instruction, #34 merged
first and this consolidation sits on top. Agent 5's commits are **not** stripped,
reverted, or rewritten — they are in main's history at `d1e0408`.

### Contracts verified clean

Textual conflict risk was **nil** — `git merge-tree` was clean for every branch
pairwise *and* in a full sequential four-way merge in a scratch clone, so every
finding above is **semantic**, i.e. invisible to a conflict check.

| Contract | Verdict |
|---|---|
| `DownloadManager` interface | **Unchanged** on all branches; `observeProgress(trackId)` already existed on main, so agent 3's progress read and agent 1's decorator are both valid |
| `LibraryViewModel` constructor | **Byte-identical** to pre-#36 main; agent 2 added no required param, using top-level `expect`/`actual` instead |
| `FullPlayer` / `MiniPlayer` params | **Signature-identical** despite agent 6's internal rework and two extracted components |
| `DhunIcon` enum | Only agent 3 adds an entry (`Pending`); agent 6 uses existing `MoreVert` / `Play` |
| `DownloadRepository`, `DownloadedTrack`, `TrackOverflowDialog` params, `StreamResolver` | **Zero diffs** across all branches |
| Duplicate `DownloadManager` implementation | **None.** Agent 1 **decorates** the shared engine; agents 2/3 **consume** it. Both reuse `observeProgress`, previously declared but unconsumed by UI |

### Deferred, routed to a playback-engine owner

Raised by agent 6 (source review only, outside its scope): `QueueManager.move` reads
`current` after mutating its item list when recovering the current index.
Current-track preservation during reorder deserves a regression check.

### Follow-ups for the next coordinator

1. Add `:app-android:testDebugUnitTest` + Robolectric and a `checkModules()` graph test
   so the real `appModule` is verified, not just its shape (from finding **B**).
2. Add a CI step that **executes** `:tools:playback-probe:offlineProbe`; today the
   workflow only compiles it, so `offline-verdict|PASS` is unestablished.
3. Decide what to do with **PR #31** (`CONFLICTING`/`DIRTY`, superseded by #32's docs).
4. Get a green **live** rot-drill verdict to close issue #14.
