# 17 — API-24 floor gate: Android Lint `NewApi` in CI (both Android modules)

> **Status: PR #134 merged (app module); PR #135 (shared module) — CI-proven on
> the PR head, awaiting merge authorization.** This document records a **CI
> gate**, not hardware behaviour. It proves that an unguarded framework call
> above API 24 fails the build. It does **not** prove that DHUN works on an
> Android 7.0/7.1 device — that is S3 round 5 and it is still open.

## What the gate is, and why it exists

`minSdk = 24` (Android 7.0) in `:app-android` and `:shared`. Nothing else in CI
checks against that floor: `assembleDebug` links an API-26+ framework call
without complaint, and that call then throws at runtime on API 24–25. Android
Lint's `NewApi` rule is the only static tool here that knows which framework
call needs which API level, so it is the floor gate.

Both Android modules need their own run. **Android Lint analyses the module it
runs in**; AGP does not lint a module's library dependencies unless
`checkDependencies` is set, and it is not set in this repo.

| Module | Config | CI step |
|---|---|---|
| `:app-android` | `app-android/build.gradle.kts` → `lint { checkOnly += setOf("NewApi"); abortOnError = true; checkReleaseBuilds = false }` | `Android Lint — API 24 floor (NewApi)` → `./gradlew :app-android:lintDebug` |
| `:shared` | `shared/build.gradle.kts` → same three settings | `Android Lint — shared androidMain API 24 floor (NewApi)` → `./gradlew :shared:lintDebug` |

`checkOnly` scopes each run to `NewApi` so unrelated lint warnings cannot redden
the build. `checkReleaseBuilds = false` keeps `lintVitalRelease` out of release
assembly. Lint is not part of `assembleDebug`, so `Build APK` and `test-release`
are unaffected.

## Evidence — `:app-android` (PR #134, merged)

- Gate green: CI pull_request **37755824901**, push **37755820899** on code head
  `107151f`.
- **Mutation proof:** probe commit `74341d0` (unguarded `NotificationChannel`,
  API 26) → CI pull_request **37757080119** and push **37757074153** /
  **37757075795** all failed on the `Android Lint — API 24 floor (NewApi)` step.
  Reverted in `20efd8b`.
- First attempt `18ff436` broke Gradle *configuration* for the whole
  multi-project build (the `lint { }` block sat at the top level, where there is
  no `lint` extension) and reddened every Gradle job. Fixed in `107151f`.
- Merged as `6f1e6ba730e590cca693c4735a558556cd8378ae` (PR #134,
  2026-10-08T09:53:02Z).

## Evidence — `:shared` (PR #135)

Session `arena/8be68e2c-dhun`, base `main@6f1e6ba`.

**Baseline (gate added, no violation), code head `46583a4`:**

| Run | Type | Result |
|---|---|---|
| **37764256149** | CI, pull_request | ✅ 14/14 steps; step 10 = `Android Lint — shared androidMain API 24 floor (NewApi)` **success** |
| **37764251898** | CI, push | ✅ |
| **37764255902** | Build APK, pull_request | ✅ |
| **37764255892** | test-release, pull_request | ✅ |

**Mutation proof, probe commit `5f74af3`** (new file
`shared/src/androidMain/kotlin/dev/dhun/LintMutationProbe.kt` containing an
unguarded `android.app.NotificationChannel(...)` constructor — API 26, floor 24):

| Run | Type | Result |
|---|---|---|
| **37765481344** | CI, pull_request | ❌ **failed on step 10 only** (steps 1–9 success, 11–14 skipped) |
| **37765475775** | CI, push | ❌ failed |
| **37765481328** | Build APK, pull_request | ✅ (the probe compiles; lint is not in `assembleDebug`) |
| **37765481508** | test-release, pull_request | ✅ |

The failure annotation from job **113271981287**, verbatim:

```
Lint found 1 errors, 0 warnings. First failure:

/home/runner/work/DHUN/DHUN/shared/src/androidMain/kotlin/dev/dhun/LintMutationProbe.kt:18:
Error: Call requires API level 26 (current min is 24):
android.app.NotificationChannel() [NewApi]

Execution failed for task ':shared:lintDebug'.
```

That single message establishes four separate things, which a green lint run
cannot:

1. `:shared:lintDebug` **exists** (a KMP `com.android.library` module does expose
   the task under AGP 8.7.2 / Kotlin 2.1.20).
2. It **analyses `shared/src/androidMain`** — the file it names is in that source
   set, not in `app-android`.
3. It resolves the **floor as 24**, i.e. it reads `minSdk` from this module.
4. `abortOnError = true` **actually aborts** (it was `false` before this change,
   so a `:shared` lint run could not have failed).

**Revert:** `a66b342` deletes the probe file. `git diff --stat 46583a4 a66b342`
is **empty** — the tree is byte-identical to the pre-probe head — and CI
pull_request **37766214968** is green on it, 14/14 steps with step 10
**success**.

**Head PR #135 asks to merge:** `f027dfc` (the ledger above, on top of
`a66b342`) plus the trailing commit that records `f027dfc`'s own runs — no code
change since `a66b342`. All four workflows green on `f027dfc`: CI pull_request
**37767256325** (14/14, step 10 success), CI push **37767248647**, Build APK
**37767256472**, test-release **37767256281**.

## Contract tests (runnable without a JDK)

`scripts/test_ci_workflow.py` (executed by CI step 1,
`python3 -m unittest discover -s scripts -p 'test_*.py'`) grew from 39 to 45
tests. Six pin this gate:

- both lint steps exist in `ci.yml`, with their distinct honest names, app module
  first;
- both `build.gradle.kts` lint blocks scope to `NewApi` **and** set
  `abortOnError = true` (a step naming a task whose gate is off is green theater);
- both Android modules keep `minSdk = 24` — the merged manifest enforces the
  **higher** of the two, so drift would silently raise the real floor and leave
  the gate checking the wrong API level.

Mutation-proven locally: removing the `:shared` lint step together with the
`checkOnly`/`abortOnError` pair turns **3** of the new tests red
(`FAILED (failures=2, errors=1)`); restoring returns **45/45 OK**.

## Static read of `shared/src/androidMain` (7 files)

`AndroidConnectivityMonitor.kt`, `Clock.android.kt`,
`DatabaseDriverFactory.android.kt`, `BlurSupport.android.kt`,
`DownloadHttpClient.android.kt`, `CurrentOffset.android.kt`,
`StorageSpace.android.kt`.

No unguarded API>24 call found:

- `ConnectivityManager.registerDefaultNetworkCallback` — API 24, **is** the floor
  and is the documented reason the floor is 24 rather than 21;
- `StatFs.blockCountLong` / `blockSizeLong` / `availableBlocksLong` — API 18;
- `Modifier.blur` / `RenderEffect` — reached only behind
  `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`, with the dark fallback for
  API 24–30;
- `java.util.TimeZone.getOffset`, `System.currentTimeMillis` — API 1;
- `AndroidSqliteDriver` / `SupportSQLiteDatabase.setForeignKeyConstraintsEnabled`
  — AndroidX, min-aware through its own manifest;
- `DownloadHttpClient.android.kt` — Ktor + OkHttp only, no framework call above
  the floor.

`commonMain` cannot reference Android APIs at all — the KMP compiler enforces
that — so `androidMain` is the entire risk surface. **A grep is not proof of
absence**; the lint runs above are the proof, and they are green on the
violation-free tree.

## What this does NOT prove

- Not runtime behaviour. `NewApi` is static: a reflection call, a manifest
  attribute, or a resource that inflates differently below API 26 is outside it.
- Not hardware. **S3 round 5** (an API 24–25 device: launcher icon renders, app
  launches, searches, plays, keeps background audio) is still **open** and
  user-gated. The current rolling `test` build is the package to install.
- Not `commonMain` coverage. Lint sees `androidMain`; `commonMain` is protected
  by the KMP compiler instead.

## Files

- `.github/workflows/ci.yml` — both lint steps
- `app-android/build.gradle.kts`, `shared/build.gradle.kts` — the lint blocks
- `scripts/test_ci_workflow.py` — the contract that keeps both wired
- `.ai/ROADMAP.md`, `.ai/KNOWN_LIMITATIONS.md`, `.ai/DEBUG_LOG.md` — status
