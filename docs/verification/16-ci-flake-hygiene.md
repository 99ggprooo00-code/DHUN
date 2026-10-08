# 16 — CI flake hygiene: the two recurring `:shared:jvmTest` flakes

> **Status: PR #125 merged; initial post-merge repetition clean; surveillance continues.**
> PR #125 (session `arena/cf69112a-dhun`) merged as
> `b1dba0c0cec9c5cde7f910f04405b7fe81a25b0e` on 2026-10-06T03:58:26Z. Its
> post-merge CI **37411494455**, Build APK **37411494443**, and test-release
> **37411494399** passed. The merge did not change the test-only scope: this
> document has no device gate and proves nothing about hardware behavior.
>
> **Post-merge observation:** the fixed `:shared:jvmTest` suite passed on the
> `b1dba0c` main CI **37411494455** and the later `f0225f4` main CI
> **37548884056**; neither run reports either repaired test as a failure. This
> is two post-merge observations, not a statistical claim that the race can
> never recur. Continue watching later unrelated PR/main CI runs.
>
> **Release-path evidence:** test-release **37411494399** ran the complete MSI
> install-over `2.170.1 → 2.171.1`, with userdata/cache sentinels preserved,
> future-upgrade guard and uninstall smoke passing (no skip). The later current
> main test-release **37548884077** likewise ran full install-over
> `2.171.1 → 2.172.1` (`buildOnly=false`), with the same guard/sentinel/uninstall
> checks passing. Current rolling `test` targets `f0225f4`; APK hash
> `21a5fe862b0c948fbc038417e156310e9eaab74bf9ea2f59214b9807f8c9cc2c`, MSI
> 2.172.1 hash
> `c27175cecca8cc071364f76704e67faa04ec290d1afd58710b48ff3643fe17d6` (from
> publisher provenance annotations; device downloads must still be checked
> against their current release sidecars).
>
> The scheduled extraction-health run **37455619019** separately classified
> **`ENVIRONMENT_BLOCKED`** because the hosted runner could not verify live
> playback. That run is not the CI suite and does not change the test-flake
> result. Local Gradle remains unavailable without a JDK; GitHub CI is the
> Kotlin compiler/test gate.

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
| PR #125 merge to `b1dba0c` | ✅ merged 2026-10-06T03:58:26Z | merge commit `b1dba0c` |
| Post-merge main CI | ✅ shared `jvmTest` green; both fixed names absent | run **37411494455** |
| Later main CI observation | ✅ shared `jvmTest` green; both fixed names absent | run **37548884056** on `f0225f4` |
| Post-merge test-release | ✅ full MSI upgrade `2.170.1 → 2.171.1`, no skip | run **37411494399** |
| Latest test-release | ✅ full MSI upgrade `2.171.1 → 2.172.1`, no skip | run **37548884077** |

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

## 6. Exit criteria and continued surveillance

PR #125 is merged, and the repaired tests have passed on two observed
post-merge `main` CI runs (`b1dba0c` / **37411494455** and `f0225f4` /
**37548884056**) without either test appearing as a failure. This satisfies the
merge-time evidence gate; it does not prove permanent absence of a race.

If either test fails again, read the label first—it identifies the wait and
observed state—and treat the result as a race-reopen signal, not as a flake to
retry. Keep checking subsequent unrelated PR and `main` runs. The separate
`ENVIRONMENT_BLOCKED` extraction-health result is not evidence about these
`:shared:jvmTest` tests.
