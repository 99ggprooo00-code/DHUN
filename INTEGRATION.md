# INTEGRATION — ADR-006 six-agent reconciliation

Owner: **coordinator agent** (this file, `.ai/ROADMAP.md`, `.ai/KNOWN_LIMITATIONS.md`,
`.ai/DEBUG_LOG.md`). The coordinator is **read-only** over `app-android/**`,
`app-desktop/**`, `shared/**`, `tools/**`, and every `agent-N-status.md` — those belong
to the worker sessions.

Coordinator session branch: **`arena/01a07a07-dhun`**. The requested name
`agent/coordinator` is not usable — an Arena session is pinned to one branch.

Scope of every CI claim below: **compile / unit-test only.** Nothing here is
hardware, device, Windows-runtime, or audible-playback verification.

---

## 1. Final stability verdict

**NOT STABLE — three of the four worker PRs are still unmerged, and PR #35's
re-green after the C1 fix has not completed.**

Specific red / open items, in priority order:

1. **PR #35** (agent 1, `agent/download-svc` @ `705a946`) — CI **in progress**
   (runs `34083073966` CI, `34083073890` test-release). The C1 blocker is fixed in
   code but the gate has not returned green on the fixing head yet.
2. **PR #36** (agents 2 + 3, `arena/01a079f5-dhun` @ `c94b87d`) — `build-and-test`
   and `msi` **pending**; `apk` pass. Unmerged.
3. **PR #37** (agent 6, `arena/01a079f7-dhun` @ `96e5e32`) — all three checks
   **green**, but unmerged.
4. **Hardware gates remain OPEN** (see §6). No CI result can close them.

Only PR #34 has landed on main.

---

## 2. Agent → branch → PR map

**Five of the six requested `agent/*` branch names were never created.** Every worker
session is pinned to its own Arena session branch, so the work landed there instead.
Two branches each carry **two** agents (see §4).

| Agent | Requested branch | Actual branch | PR | Head | CI (`build-and-test` / `apk` / `msi`) | Status file | State |
|---|---|---|---|---|---|---|---|
| 1 — Android download FGS | `agent/download-svc` | ✅ `agent/download-svc` | #35 | `705a946` | **in progress** / in progress / in progress | none — `.gitignore` excludes `agent-1-status.md`; status lives in the PR #35 body | **BLOCKED → fixed, awaiting re-green** |
| 2 — Library downloads | `agent/library-downloads` | ❌ never created; work landed on `arena/01a079f5-dhun` | #36 (shared) | `c94b87d` | pending / pass / pending | `agent-2-status.md` (on #36's branch) | built, CI pending |
| 3 — Track download UI | `agent/track-download-ui` | `arena/01a079f5-dhun` | #36 (shared) | `c94b87d` | pending / pass / pending | `agent-3-status.md` | built, CI pending |
| 4 — Desktop player | `agent/desktop-player` | `arena/01a079f6-dhun` | #34 (shared) | `571f7c0` | pass / pass / pass | `agent-4-status.md` | **MERGED** |
| 5 — Verify / docs | `agent/verify-docs` | `arena/01a079f6-dhun` | #34 (shared) | `571f7c0` | pass / pass / pass | `agent-5-status.md` | **MERGED** |
| 6 — Player UX | `agent/player-ux` | `arena/01a079f7-dhun` | #37 | `96e5e32` | pass / pass / pass | `agent-6-status.md` | built, CI green, unmerged |

Agent 2 was reported missing at the start of this reconciliation. It then pushed its
Library Downloads / storage-management work onto agent 3's session branch
(`1c72af1`, `fcd4e7a`, `9c7bc80`, `e33190f`) rather than the relaunched
`agent/library-downloads`. **Check for a duplicate before relaunching agent 2** — the
Library "Downloads" tab + storage-management work described as never-done **is now
implemented** on `arena/01a079f5-dhun`.

---

## 3. Merge-sequence log

Order requested by the user: `#34 → #36 → #37 → #35`. **One session = one branch =
one PR = one merge; each session merges only its own PR.**

| # | PR | Agent(s) | Action | Result | Gate re-run on main |
|---|---|---|---|---|---|
| 1 | **#34** | 4 + 5 | **Merged by the coordinator** at `2026-09-07T04:18:28Z` | main → **`d1e0408`** | CI run `34082610125` **success**; test-release `34082610094` **success** — jobs `msi`, `apk`, `publish` all success. Rolling `test` pre-release replaced `2026-09-07T04:26:24Z` (APK 17,581,785 B, MSI 112,287,744 B, + `.sha256` each) |
| 2 | #36 | 2 + 3 | **HELD** — belongs to the agent-2/3 session | — | — |
| 3 | #37 | 6 | **HELD** — belongs to the agent-6 session | — | — |
| 4 | #35 | 1 | **HELD** — belongs to the agent-1 session; C1 must re-green first | — | — |

> **⚠ Deviation, recorded rather than hidden.** The coordinator merged #34 itself.
> That happened under the *previous* instruction ("MERGING — APPROVE the #34
> bundle … then rebase your doc consolidation on the new main and supersede"),
> before the one-session-one-PR constraint was issued. It is disclosed here because
> main's history contains a coordinator-authored merge commit.
> The merge itself was gated and clean: #34 was `MERGEABLE`/`CLEAN` with all three
> checks passing at `571f7c0`, merged with a merge commit to match repo convention,
> and main was re-verified green afterwards. **No further merges were made by the
> coordinator** — #35, #36, #37 were all left OPEN. If #34's merge commit needs to be
> reverted or re-authored, that is a decision for the user; the coordinator will not
> rewrite main unilaterally.

Baseline before step 1: main `f157245` (PR #33), CI `34079283259` success,
test-release `34079283231` success. All four live branches shared merge-base `f157245`.

Excluded: **PR #31** (`arena/01a0759b-dhun`) — GitHub reports
`mergeable=CONFLICTING`, `mergeState=DIRTY`; already carried a standing
"do not merge without user instruction" note. **Issue #14** `[rot-drill] Live
extraction probe failed` remains OPEN (GitHub-runner IP gating, known environment
limitation).

---

## 4. The two bundles

Both violate the single-session-branch rule and therefore **cannot be gated
per-agent** — merging either lands two agents at once.

**Bundle A — PR #34 (`arena/01a079f6-dhun`) — MERGED**
- **Agent 4** (desktop owner): `app-desktop/.../Main.kt` — removed the
  `JOptionPane` startup/fatal Swing surfaces that could own a second small native
  window on Windows; retained mutually exclusive Compose behaviour.
- **Agent 5** (verify/docs owner): `tools/playback-probe/OfflineMain.kt` +
  `offlineProbe` task + WAV fixture + README, **and edits to `.ai/ROADMAP.md`,
  `.ai/KNOWN_LIMITATIONS.md`, `.ai/DEBUG_LOG.md`** (87 insertions / 46 deletions).

**Bundle B — PR #36 (`arena/01a079f5-dhun`) — OPEN**
- **Agent 3** (download UI): `DownloadAffordances.kt` (new), `DhunIcons.Pending`
  (new icon), `HomeScreen` / `SearchScreen` badge wiring, and the `DhunAppShell`
  pass-through that closes C2.
- **Agent 2** (Library UI/presentation): `LibraryViewModel` storage summary +
  download actions, `LibraryScreen` in-tab Storage management view,
  `StorageSpace` `expect`/`actual` (`androidMain` `StatFs`, `jvmMain`
  `File.usableSpace`), `LibraryDownloadsViewModelTest`.

---

## 5. Cross-cutting findings and decisions

Textual conflict risk was **nil**: `git merge-tree` against main was clean for every
branch pairwise, and a full sequential four-way merge in a scratch clone produced no
conflicts in any order. Every finding below is **semantic** — i.e. invisible to a
conflict check.

### C1 — BLOCKER (agent 1): Koin graph self-recursion. **FIXED, awaiting re-green.**

Original defect at `a4dc28d`, in `app-android/.../di/AppModule.kt`:

```kotlin
single<DownloadManager> {
    ForegroundServiceDownloadManager(
        context = androidContext(),
        delegate = get(),        // infers get<DownloadManager>() — the definition being built
        controller = get(),
    )
}
```

`ForegroundServiceDownloadManager` declares `delegate: DownloadManager`, so the
unqualified `get()` resolves the singleton currently under construction. Koin 4.0.2
caches singletons *after* construction, so nothing breaks the cycle. Trigger point:
`MainActivity.kt:197` `downloadManager = koin.get()`, inferred as
`DhunAppShell`'s `downloadManager: DownloadManager?` (`DhunAppShell.kt:122`) — i.e.
during activity composition, **at app launch, before any user taps "Download"**.

**Why CI could not see it:** `:app-android:assembleDebug` is a type-check gate, and
`:app-android` has no test source set, so no smoke test existed. All three checks were
green at `a4dc28d`.

**Fix (by agent 1, commit `ef69f82`):** `delegate = get<FileDownloadManager>()` —
explicit type, pointing at the concrete singleton registered immediately above.
Verified by reading `AppModule.kt` on head `705a946`.

**Regression test (commit `705a946`):** `shared/src/jvmTest/kotlin/dev/dhun/di/KoinDownloadStackTest.kt`.
**Recorded limitation:** this test uses *minimal fakes mirroring the production
shape* in `:shared:jvmTest`, not the real `app-android` `appModule`. It pins the
registration shape so the unqualified-`get()` pattern cannot silently return; it does
**not** verify the actual Android graph. The real graph still has no automated
verification (`checkModules()` or an `:app-android` smoke test remain a follow-up).

Coordinator disclosure: the original C1 diagnosis was **static analysis only** — no
JDK/Gradle in the sandbox, and the standing rule is git + gh only. It was never a
reproduced stack trace.

### C2 — Integration gap (agent 3): inert download badges. **FIXED on branch.**

`DhunAppShell` on main already accepted `downloadManager: DownloadManager? = null`
(line 122) and forwarded it to `LibraryViewModel` (139) and the overflow `onDownload`
(364) — but **not** to `HomeScreen` or `SearchScreen`. Agent 3's original head
(`2cd116f`) added the parameter to both screens and deliberately left
`shared/.../ui/shell/**` untouched, so the badges compiled and rendered nothing.

**Fixed by agent 3, commit `e987f64`** (`fix(download-ui): forward download manager
to home and search`). Verified by reading `DhunAppShell.kt` on head `c94b87d`: both
callsites now pass `downloadManager = downloadManager`. The coordinator's narrow
exemption to write the pass-through was **not exercised** — the owning worker did it.

**Residual hazard, recorded in `.ai/KNOWN_LIMITATIONS.md`:** agent 3 inserted
`downloadManager` **mid-list** in both composables (7th of 12 in `HomeScreen`, 7th of
8 in `SearchScreen`). This is safe *only* because both callsites use named arguments.
Any future positional caller silently misbinds.

### C3 — Shared `.ai` ownership. Resolved by supersession.

Agent 5 edited the three docs the coordinator solely owns. Decision, per the user:
let #34 merge, then **this consolidation supersedes on top of the new main**. Agent
5's commits were **not** stripped, reverted, or rewritten — they are in main's history
at `d1e0408`, and the coordinator's edits sit on top of them.

### Contracts checked and found clean

| Contract | Verdict |
|---|---|
| `DownloadManager` interface | **Unchanged** on all branches. `observeProgress(trackId)` already existed on main, so agent 3's progress read and agent 1's decorator are both valid; the decorator implements all 11 members |
| `LibraryViewModel` constructor | **Byte-identical** to main (diff of the ctor block, empty). Agent 2 added no required param, using top-level `expect`/`actual` instead |
| `FullPlayer` / `MiniPlayer` params | **Signature-identical** on agent 6's head `96e5e32`, despite internal rework and two extracted components (`PlayerSeekBar.kt`, `TransportControls.kt`) |
| `DhunIcon` enum | Only agent 3 adds an entry (`Pending`). Agent 6 uses existing `MoreVert` / `Play`. No collision |
| `DownloadRepository`, `DownloadedTrack`, `TrackOverflowDialog` params | **Zero diffs** across all branches |
| `StreamResolver` / `OfflineFirstStreamResolver` | **Zero diffs** — agent 1 reuses the existing chain |
| Duplicate `DownloadManager` usage | None. Agent 1 **decorates** the shared engine rather than reimplementing it; agent 2/3 **consume** it. Both reuse `observeProgress`, previously declared but unconsumed by UI |

---

## 6. Hardware gates — still OPEN

**Green CI is a compile/unit-test gate only. It does not mean downloaded tracks play
offline.** Every item below requires a real device, PC, libVLC runtime, or display:

- Offline playback of a downloaded track (Android Media3 `FileDataSource` route;
  Desktop vlcj local-path load)
- Audible audio at all (streaming and offline)
- Home pagination on live data
- Player visual acceptance (glyph placement, shuffle, colour styling)
- Tray / SMTC behaviour, and the one-native-window startup claim from agent 4
- Install-over upgrade
- Clean-target installation hygiene
- 30-minute soaks, including the OEM battery-saver soak agent 1's FGS exists to pass
- A green **live** rot-drill verdict (issue #14)

Agent 5's `offlineProbe` is deterministic shared/JVM repository-to-file verification.
CI compiles it but **does not execute** the runtime task, so not even
`offline-verdict|PASS` is established.

---

## 7. Per-agent status summary

| Agent | Built | CI green | Blocked | Merged |
|---|---|---|---|---|
| 1 | ✅ | ⬜ pending on the C1-fix head `705a946` | ✅ C1 (fixed in code, gate outstanding) | ⬜ |
| 2 | ✅ | ⬜ pending on `c94b87d` | — | ⬜ |
| 3 | ✅ | ⬜ pending on `c94b87d` | — | ⬜ |
| 4 | ✅ | ✅ `34081572340` / `34081572339` | — | ✅ `d1e0408` |
| 5 | ✅ | ✅ (same runs) | — | ✅ `d1e0408` |
| 6 | ✅ | ✅ `34082466491` / `34082466496` | — | ⬜ |

Deferred, routed to a playback-engine owner (raised by agent 6, source review only,
outside its scope): `QueueManager.move` reads `current` after mutating its item list
when recovering the current index. Current-track preservation during reorder deserves
a regression check.
