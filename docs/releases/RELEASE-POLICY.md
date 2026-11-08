# Release policy (versioning decided 2026-10-10; other rules pending approval)

> **Status:** the fixed-version choice in section 2 (Option A) was decided on 2026-10-10. On 2026-11-01 the maintainer authorised the rolling channel to become the public **DHUN beta** prerelease: no draft state, with each publication replacing the previous `beta` release/tag after its gates pass. The numbered rules for future fixed releases remain **proposals** except where existing workflow/user decisions are stated. Items marked **[decision]** still need approval. Fixed versioned releases and their assets are not rewritten by the rolling-beta migration.

## 1. Release candidate identifiers

| Identifier | Current value | Meaning | Where it is declared |
|---|---|---|---|
| Git tag / GitHub release | `v1.00.002` (after the reviewed release-intent merge builds and publishes successfully); `v1.00.001` remains fixed at `6ebc45c` | Fixed public pre-releases | `beta-release.yml` (hard-coded) |
| App version (`versionName`) | `1.00.002` | What users see on Android | `app-android/build.gradle.kts` |
| Android `applicationId` | `dev.dhun.android` | Package identity | `app-android/build.gradle.kts` |
| Android `versionCode` | `7` | Integer Android uses for upgrades; must increase on each published build | `app-android/build.gradle.kts` |
| MSI `ProductVersion` | increasing workflow counter (`3.61.1` for v1.00.001) | Windows Installer sequence; **not** the app version | computed by `scripts/installer_version.py` from the run number and attempt |
| MSI `UpgradeCode` | `31ddb86b-9666-4071-b11c-45f16fa4682d` | Stable identity; changing it orphans existing installs | `app-desktop/build.gradle.kts` |
| Rolling tag / release | `beta` / **DHUN beta** | Public prerelease (`isDraft=false`); each successful beta publication replaces the previous beta release/tag | `beta-release.yml` |
| Rolling Android `versionName` | `1.00.<run>-beta.<attempt>` | Readable beta/development package identity; the renamed beta workflow's run includes the 867 continuity offset; fixed public versions do not use this suffix | `scripts/beta_version.py` |
| Rolling Android `versionCode` | allocation time in Unix seconds | Nondecreasing across APK workflows; same-second packages may reinstall, never downgrade | `scripts/beta_version.py` |
| Rolling assets | `dhun-beta.apk`, `dhun-beta-arm64-v8a.apk`, `dhun-beta-armeabi-v7a.apk`, `dhun-beta.msi` | Exactly four installers; no release text sidecars | `beta-release.yml` |

The identifier `1.00.001` is not canonical SemVer: SemVer forbids leading zeros in numeric identifiers, and a Git tag `v1.00.001` is not the same string as `v1.0.1`. The project changelog asks for SemVer unless the maintainer requests otherwise. This identifier stays preserved exactly. The 1.00.002 candidate uses the same padded format.

## 2. Versioning decision for 1.00.002

**Decided by the maintainer (2026-10-10): Option A.** The current release candidate is `1.00.002` (tag `v1.00.002`, `versionCode` 7). Later minor releases continue the same pattern: `1.00.003`, `1.00.004`, and so on. The Gradle and workflow identity are bumped together in the release PR; publication requires an explicit main-branch dispatch or a reviewed one-shot release-intent addition on `main`.

- **Option A — keep the padded scheme (chosen).** This release: `1.00.002`, with `versionCode` 7 and tag `v1.00.002`. Pro: matches the published lineage with no ambiguity. Con: not SemVer, and the zero padding is unusual.
- **Option B — switch to SemVer for new releases.** Example: `1.0.1` (tag `v1.0.1`), or a pre-release `1.0.1-rc.1`. Pro: follows the changelog's stated default. Con: `v1.00.001` and `v1.0.1` would look like two different versions of one project, and the publish workflow needs a new code path.

Either option needs the same identity change everywhere: Gradle `versionName`, the publish workflow's version and `--version` values, the release notes file name, the install guide and the changelog. `scripts/test_release_identity.py` will fail until those agree.

The v1.00.002 workflow deliberately marks it as a **pre-release**, not Latest, because hardware acceptance remains open. The older GitHub v1.00.001 release is historically marked non-prerelease in GitHub metadata; its tag and assets are left untouched.

## 3. Rules that apply to every future release (proposed)

1. **Android never downgrades.** Fixed public `versionCode` values strictly increase. Rolling beta/development codes come from allocation time: later allocations increase, while a same-second build may reuse a code for an accepted reinstall. Never wrap or lower a code.
2. **MSI `ProductVersion` strictly increases** over every published MSI. It is derived from the effective workflow run number and attempt (`1 + run // 256 . run % 256 . attempt`), so any build from a newer run outranks the previous one. The renamed beta workflow adds 867 to its raw run number to continue the old `test-release` sequence whether GitHub resets or continues that counter. Do not reset it. A local `packageMsi` without `-PdhunInstallerVersion` uses the fallback `1.0.6`, which is **lower** than the published `3.61.1` and would be refused as a downgrade. Such a file must never be published.
3. **Application and upgrade identities never change.** Android keeps `dev.dhun.android` and its signing identity; Windows keeps `UpgradeCode` `31ddb86b-9666-4071-b11c-45f16fa4682d`. Updates must not require clearing playlists, favourites, settings, downloads, queues or history.
4. **Fixed tags are immutable; rolling `beta` is the explicit exception.** A fixed published tag is never moved or re-pointed, and a fix gets a new fixed version. Each authorised rolling-beta publication instead deletes and recreates the `beta` release/tag at the verified new `main` commit. A failed build does not delete the current beta.
5. **The tag, the commit, and the built binaries must match.** The publish job refuses a superseded `main` commit. For the fixed `v1.00.002` path, `stage_versioned_release.py` requires every input's `sourceSha` to equal `GITHUB_SHA`, which is also the release `--target`. Rolling beta consumes artifacts from the same workflow run and targets `$GITHUB_SHA`.
6. **Fixed assets are never edited in place.** A change to a fixed published asset requires a new version. Rolling beta is replaced as a complete four-installer release rather than having individual assets edited.
7. **No `.json` files are added to release assets.** Fixed-release sidecars are `*.sha256` (standard `sha256sum` format) and, where staged, `*.provenance.txt` (flat `key=value`). Rolling beta exposes only its four installers; checksums/provenance stay in internal workflow artifacts. Historical fixed-release assets are inspected and preserved as published, not rewritten to fit later asset rules.
8. **Unrelated destructive cleanup is opt-in.** A manual fixed publish deletes the legacy `dev`, `dev-v0.1.0` and `dev-v0.1.1` releases and tags only when `purge_legacy_dev_releases` is ticked for that dispatch (default off). Rolling beta replacement is the separately authorised automatic exception. During migration, the legacy `test` release/tag is removed only after the replacement public beta is verified; fixed public releases are never cleanup targets. Restricting who may approve a fixed publish needs a GitHub environment with required reviewers; see `.ai/KNOWN_LIMITATIONS.md`.

## 4. Verifying a published file

Checksums only prove that a download matches the release page. They do not prove who built the file. The commands below are for fixed releases with published sidecars. Rolling **DHUN beta** deliberately has exactly four installer assets and no checksum/provenance sidecars; its verification records remain in internal workflow artifacts.

**Linux / macOS**

```sh
sha256sum -c dhun-v1.00.002.apk.sha256
```

**Windows PowerShell**

```powershell
$expected = (Get-Content .\dhun-v1.00.002.msi.sha256 -Raw).Split()[0].ToLowerInvariant()
$actual = (Get-FileHash .\dhun-v1.00.002.msi -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -eq $expected) { "OK" } else { "MISMATCH" }
```

**Android signer (needs Android build-tools `apksigner`)**

```sh
apksigner verify --print-certs dhun-v1.00.002.apk
```

Expected for the current test key: certificate SHA-256 `d8e57ec69f40956d34adff137872db9fec3b817e149e2d0c33af5924142468c3` (derived from `app-android/keystores/dhun-test.p12`, not yet compared with a published APK). Because the key is public, matching it is **not** evidence of origin.

## 5. Publication gates (current and proposed)

| Gate | Current state | Proposed |
|---|---|---|
| Unit tests (shared, Android, desktop) | In CI (`ci.yml`) | Keep |
| Android build, debug-signed | In `beta-release.yml` | Keep |
| Release-only Android build (`assembleRelease`) | Not built. `release` buildType has no signing config and is not minified. | **[decision]** whether a release build type is needed |
| MSI build and identity check | In `beta-release.yml` | Keep |
| MSI install-over on hosted Windows | Required on `main` (skip fails); skipped on PR/branch | Keep |
| Fixed public release creation | Hard-coded to `v1.00.002`; a reviewed one-shot main merge or manual dispatch builds, checks and publishes a verified draft once | Keep reviewed version changes and draft verification |
| Rolling beta creation | Ordinary verified `main` app/build updates publish **DHUN beta** directly as a public prerelease (never a draft), replacing the previous `beta` release/tag. Exactly four installers; no sidecars. PRs upload run-scoped artifacts only. The popup-redesign PR #223 uses a one-shot build-only preservation marker so its merge does not replace the current beta. | Keep replacement, target, title, prerelease and asset assertions |
| Human approval before fixed publishing | The reviewed PR adding `.github/release-intent/v1.00.002` selects a fixed public build exactly once when merged into `main`; a manual `workflow_dispatch` with `build_only=false` remains supported. Either path requires APK/MSI gates and a verified draft before publication. Ordinary `main` pushes can update only rolling beta, not a fixed tag. | **[decision]** add a protected approval environment for future fixed release jobs |
| Hardware acceptance (Android, Windows) | Not complete; individual install reports and hosted checks are not a full device/playback/accessibility/performance matrix | Required before broad "works on device" claims |

## 6. Known risks in the publish path (not yet changed)

- `publish.sh` at the repository root rewrites `origin` to an SSH remote and pushes `main` directly. It is not part of the release path, but it bypasses CI. **[decision]** remove it or move it under `.ai/`.
- The publish job deletes old `dev-*` releases and tags with `|| true`, so a failed delete is silently ignored.
- Fixed `publish` does not run on ordinary pushes to `main`. An ordinary qualifying merge instead replaces the public rolling **DHUN beta** prerelease after its gates, unless a newly added one-shot preserve marker selects a build-only run. PR #223 adds `.github/release-intent/preserve-rolling-beta-pr223` for that single merge; the marker is inert after it reaches `main`. The single merge that **adds** `.github/release-intent/v1.00.002` selects the fixed public build and publishes that fixed tag only after its own build gates; subsequent pushes cannot reuse the existing marker. A manual dispatch with `build_only=false` remains available. The push path cannot purge legacy dev releases or rewrite fixed releases.

## Live fixed-release metadata (verified 2026-11-01)

Release API snapshot: `v1.00.002` is release ID `414955833`, `draft=false`, `prerelease=true`, targeting `1e672f45d77547161d76ecc4de2bbe03c252ab93`; the tag ref resolves to that same commit. Its universal APK is `dhun-v1.00.002.apk`. It lists 12 nonempty assets: four installers, four matching `.sha256` sidecars and four `.provenance.txt` records. The release's `updated_at` value is `2026-10-16T12:47:16Z`. The rolling-beta migration must leave this release, its tag and every asset unchanged; historical candidate notes that describe other names or asset counts are not current download guidance.
