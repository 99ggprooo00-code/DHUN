# 16 — CI flake hygiene: the two recurring `:shared:jvmTest` flakes

> **Status: CI-verified, awaiting repetition.** This document is the evidence
> sheet for PR #125 (session `arena/cf69112a-dhun`, commit `df0504f`,
> base `main@a9204c59`). It is a **test-infrastructure** record: there is no
> hardware gate for it, and by construction it cannot be signed off on a
> device. Read the "What green CI does not prove" section before quoting it.

## 1. What was broken

Two `:shared:jvmTest` tests failed intermittently since **2026-09-18**, always
on PRs whose diff could not have caused it, and always with the same shape: a
green run of the *identical* SHA in another workflow.

| Test | Recorded failures | Symptom in the annotation |
|---|---|---|
| `LibraryViewModelTest.historyPlaybackQueuesCorrectly` | **35309124090** (2026-09-18), **37320618809** (2026-10-05), **37407043852** (2026-10-06) | `TimeoutCancellationException: Timed out waiting for 15000 ms` @ `LibraryViewModelTest.kt:82` |
| `PlayerViewModelTest.endlessRadioDropsAPageFetchedForAQueueThatChangedMidFetch` | **37320626452** (2026-10-05) | `ComparisonFailure: expected:<tok-2> but was:<tok-a>` @ `PlayerViewModelTest.kt:517` |

Both were carried in `.ai/KNOWN_LIMITATIONS.md` as a "load/timing flake class",
mitigated only by raising the deadline 5 s → 15 s.

## 2. Root cause (one class, two instances)

A test waits for one piece of state and then reads a **sibling** piece that the
production code publishes as a **separate step**:

- `LibraryViewModel.groupedHistory` is derived asynchronously
  (`combine`-collector on the VM scope, `LibraryViewModel.kt:281-285`). The
  test waited for `historyEntries.size == 3`, then read
  `groupedHistory.value.firstOrNull()` and **silently skipped** the day-play
  block when it was still empty. Because `playHistoryEntry` issues exactly one
  `prepareQueue` call, the following `prepareCalls >= 2` wait could then
  **never** be satisfied — a coin-flip race became a guaranteed 15 s timeout.
- A radio refill publishes the swapped queue **and then** consumes the page's
  continuation token. `endlessRadioDropsAPageFetchedForAQueueThatChangedMidFetch`
  polled the queue and read `radioSession.continuationToken` in the gap.

Neither was a product defect: the end state is always reached.

## 3. The fix

- `LibraryViewModelTest`: wait for the derived state (labelled), read
  `first()` unconditionally (the day-play path is now mandatory coverage), and
  pin `day.entries.size == player.lastPrepared.size` so the day path is
  distinguished from the single-entry path.
- `PlayerViewModelTest`: `RadioFixture.awaitContinuationToken(token)` waits for
  the token consumption; applied to the three sibling radio tests that read an
  advanced token after a queue wait.
- Diagnostics: both `eventually` helpers take `label: () -> String`
  (evaluated **only on expiry**) and fail via `kotlin.test.fail`, so the
  check-run annotation reads
  `eventually(15000ms) timed out: <label + live state>`.

## 4. Evidence

| Gate | Result | Reference |
|---|---|---|
| Push CI on `df0504f` | ✅ 12/12 steps, incl. `Unit tests — shared domain` | run **37408908111** |
| PR CI on `df0504f` | ✅ | run **37408920918** |
| Build APK | ✅ | run **37408921035** |
| test-release (PR path, artifacts only) | ✅ | run **37408920959** |
| Packaging/fixture helpers (local) | ✅ 31 tests OK | `python3 -m unittest discover -s scripts` |
| Fixture validation (local) | ✅ PASS: 39 files | `scripts/validate_fixtures.py` |
| Delimiter sanity on both edits (local) | ✅ balanced (with untouched controls) | string/comment-aware scanner |

The sandbox has **no JDK and no egress** (`api.adoptium.net`,
`repo1.maven.org`, `services.gradle.org` all refused this session), so CI is
the compiler for Kotlin; the local gates are the JDK-free ones.

## 5. What green CI does *not* prove

- It does not prove the races are gone. It proves the **known-bad
  interleavings are removed**, a previously silently-skipped coverage path is
  now mandatory, and a recurrence will name the exact wait and its state.
- Repetition is the real signal: the next unrelated PRs and `main` pushes must
  stay free of these two test names. A recurrence is a **reopen signal for the
  race**, not evidence for "flake, retry".
- The labelled `eventually` exists in two files only; `BrowseViewModelTest`,
  `HomeViewModelTest`, `LibraryDownloadsViewModelTest` and
  `SearchViewModelTest` still time out with the bare message (deliberate,
  recorded in `.ai/KNOWN_LIMITATIONS.md`).
- No hardware/manual gate applies to a test-only diff; the S3 round
  (`docs/runbooks/s3-hardware-checklist.md`) is unchanged and still open.

## 6. Exit criteria

1. Recurrence-free across every run between `df0504f`'s merge and the next
   unrelated `main` push.
2. If either test fails again: read the label first — it names the wait and the
   observed state — and treat it as a new race, not as a flake to retry.
