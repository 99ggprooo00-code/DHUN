# 18 — App Bundle path: `:app-android:bundleDebug` is now a merge gate (S6)

> **Status: merged and post-merge CI-verified on
> `main@8a8d6c5adc536f34c8b677c9b20e5245747ed2e9`. Compilation only — the AAB
> is still not staged by the release job, install-verified or Play-uploadable.**
> S6's "clean-target install of APK + AAB + MSI" stays open.

## The gap this closes

Stage S6 requires a clean-target install of **APK + AAB + MSI**. Before this
change, the only job that built an AAB was `test-release.yml`'s `aab` job, and
that job is gated:

```yaml
if: ${{ github.event_name == 'workflow_dispatch' && (inputs.build_release_candidate == true || inputs.publish_v010_draft == true) }}
```

`:app-android:bundleDebug` therefore ran **only** on a human *Run workflow*
click. The maintenance agent cannot click it — verified this session, not
assumed:

```
$ gh workflow run test-release.yml --ref arena/8be68e2c-dhun \
    -f build_only=true -f build_release_candidate=true -f publish_v010_draft=false
could not create workflow dispatch event: HTTP 403: Resource not accessible by
integration (…/actions/workflows/347450723/dispatches)
```

That turned the previous handoff's parenthetical ("agents get 403 on dispatch")
into a checked fact for this specific workflow.

Meanwhile **PR #131 added a `splits { abi }` block** to `:app-android` (universal
+ `arm64-v8a` + `armeabi-v7a`) *after* the `aab` job had last run. Whether AGP
ignores, tolerates or rejects ABI splits when building an App Bundle was an
unanswered question sitting on the release path, with no automated way to ask
it.

## The change

`.github/workflows/ci.yml`, after `Android debug build`:

```yaml
- name: "Android App Bundle compiles (S6 AAB gate)"
  run: ./gradlew :app-android:bundleDebug --no-daemon
```

`scripts/test_ci_workflow.py` gained two contract tests (45 → 47) pinning the
step and its position after `assembleDebug`, so a shared compile break fails
`Android debug build` rather than masquerading as a bundle failure. Both are
mutation-proven locally: deleting the step's `run:` line turns **2** tests red
(`FAILED (failures=1, errors=1)`); restoring returned the then-current suite to
**47/47 OK**. The current repository-wide suite is **55/55 OK** after ADR-008
B1 added eight unrelated Web-spike contract tests.

## The verdict — AGP tolerates `splits { abi }` alongside a bundle

CI pull_request **37769519510** on head `59ac12f`, job `build-and-test`,
18/18 steps success:

| # | Step | Result |
|---|---|---|
| 8 | Android debug build (`assembleDebug`) | ✅ |
| **9** | **Android App Bundle compiles (S6 AAB gate)** | ✅ **success** |
| 10 | Android Lint — API 24 floor (NewApi) | ✅ |
| 11 | Android Lint — shared androidMain API 24 floor (NewApi) | ✅ |

Same commit, the other three workflows: CI push **37769514613** ✅, Build APK
**37769519324** ✅, test-release **37769519439** ✅.

So: **`:app-android:bundleDebug` succeeds with the `splits { abi }` block
present**, under AGP 8.7.2 / Gradle 8.14.2 / JDK 17. The open question is
answered and now stays answered on every PR and every push to `main`.

## Post-merge close-out

PR #135 merged at 2026-10-08T11:44:32Z as
`8a8d6c5adc536f34c8b677c9b20e5245747ed2e9`. CI push **37772063324** succeeded;
its `Android App Bundle compiles (S6 AAB gate)` step is green. Build APK
**37772063380** and test-release **37772063364** also succeeded, but neither
result changes the boundary of this gate: the automatic test-release event
skipped its `aab` job as designed.

The dispatch-only staging path therefore remains **unexercised**. A user must
run `test-release` once with `build_only=true` and
`build_release_candidate=true`; that is what executes `stage_artifact.py` over
the bundle. Even a green dispatch will be staging evidence, not the still-open
bundletool/device install required by S6.

## What this does NOT prove

- **Not an install.** `bundletool build-apks --mode=universal …` plus a device
  is still required. The `aab` job's own release notes already say this.
- **Not the `aab` job itself.** The dispatch-gated job still stages
  `app-android/build/outputs/bundle/debug/app-android-debug.aab` and runs
  `scripts/stage_artifact.py` over it. This gate proves the Gradle task builds;
  it does not execute that staging path. Confirm it on the first
  `build_release_candidate: true` dispatch (user-only).
- **Not Play-readiness.** The bundle is signed with the committed public
  `testBuild` debug keystore, exactly like the debug APK. It is installable for
  testing and is **not** a store artifact. Release signing is an open user
  decision (S6).
- **Not the bundle's contents.** This gate does not assert which ABI/base splits
  the AAB carries, or that AGP silently ignored the `splits` block rather than
  honouring it. If that distinction ever matters, inspect the bundle with
  `bundletool dump manifest` / `badging` on a machine that has it.

## Files

- `.github/workflows/ci.yml` — the gate step
- `scripts/test_ci_workflow.py` — the contract that keeps it wired
- `.github/workflows/test-release.yml` — the dispatch-only `aab` job it shadows
