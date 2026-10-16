# Release policy (versioning decided 2026-10-10; other rules pending approval)

> **Status:** the versioning choice in section 2 (Option A) was decided by the maintainer on 2026-10-10. The numbered rules in section 3 remain **proposals** until the maintainer approves them. Nothing here changes an existing tag, release, or asset. Items marked **[decision]** need the maintainer's approval before they take effect. This file describes the release candidate, what each identifier means, and what a future release must prove.

## 1. Release candidate identifiers

| Identifier | Current value | Meaning | Where it is declared |
|---|---|---|---|
| Git tag / GitHub release | `v1.00.002` (after the reviewed release-intent merge builds and publishes successfully); `v1.00.001` remains fixed at `6ebc45c` | Fixed public pre-releases | `test-release.yml` (hard-coded) |
| App version (`versionName`) | `1.00.002` | What users see on Android | `app-android/build.gradle.kts` |
| Android `applicationId` | `dev.dhun.android` | Package identity | `app-android/build.gradle.kts` |
| Android `versionCode` | `7` | Integer Android uses for upgrades; must increase on each published build | `app-android/build.gradle.kts` |
| MSI `ProductVersion` | increasing workflow counter (`3.61.1` for v1.00.001) | Windows Installer sequence; **not** the app version | computed by `scripts/installer_version.py` from the run number and attempt |
| MSI `UpgradeCode` | `31ddb86b-9666-4071-b11c-45f16fa4682d` | Stable identity; changing it orphans existing installs | `app-desktop/build.gradle.kts` |
| Rolling tag | `test` | Private draft, replaced on every `main` build | `test-release.yml` |

The identifier `1.00.001` is not canonical SemVer: SemVer forbids leading zeros in numeric identifiers, and a Git tag `v1.00.001` is not the same string as `v1.0.1`. The project changelog asks for SemVer unless the maintainer requests otherwise. This identifier stays preserved exactly. The 1.00.002 candidate uses the same padded format.

## 2. Versioning decision for 1.00.002

**Decided by the maintainer (2026-10-10): Option A.** The current release candidate is `1.00.002` (tag `v1.00.002`, `versionCode` 7). Later minor releases continue the same pattern: `1.00.003`, `1.00.004`, and so on. The Gradle and workflow identity are bumped together in the release PR; publication requires an explicit main-branch dispatch or a reviewed one-shot release-intent addition on `main`.

- **Option A — keep the padded scheme (chosen).** This release: `1.00.002`, with `versionCode` 7 and tag `v1.00.002`. Pro: matches the published lineage with no ambiguity. Con: not SemVer, and the zero padding is unusual.
- **Option B — switch to SemVer for new releases.** Example: `1.0.1` (tag `v1.0.1`), or a pre-release `1.0.1-rc.1`. Pro: follows the changelog's stated default. Con: `v1.00.001` and `v1.0.1` would look like two different versions of one project, and the publish workflow needs a new code path.

Either option needs the same identity change everywhere: Gradle `versionName`, the publish workflow's version and `--version` values, the release notes file name, the install guide and the changelog. `scripts/test_release_identity.py` will fail until those agree.

The v1.00.002 workflow deliberately marks it as a **pre-release**, not Latest, because hardware acceptance remains open. The older GitHub v1.00.001 release is historically marked non-prerelease in GitHub metadata; its tag and assets are left untouched.

## 3. Rules that apply to every future release (proposed)

1. **`versionCode` strictly increases** for each published Android build. Never reuse or lower it.
2. **MSI `ProductVersion` strictly increases** over every published MSI. It is derived from the workflow run number and attempt (`1 + run // 256 . run % 256 . attempt`), so any build from a newer run outranks the previous one. Do not reset it. A local `packageMsi` without `-PdhunInstallerVersion` uses the fallback `1.0.6`, which is **lower** than the published `3.61.1` and would be refused as a downgrade. Such a file must never be published.
3. **`UpgradeCode` never changes.**
4. **Tags are immutable.** A published tag is never moved or re-pointed. A fix is a new version.
5. **The tag, the commit, and the built binaries must match.** The publish job refuses a superseded `main` commit. For the fixed `v1.00.002` path, `stage_versioned_release.py requires every input's `sourceSha` to equal `GITHUB_SHA`, which is also the release `--target`. The rolling `test` draft uses `--target $GITHUB_SHA` but does not re-check its inputs.
6. **Assets are never edited in place.** A change to a published asset requires a new version.
7. **No `.json` files are added to release assets.** Future sidecars are `*.sha256` (standard `sha256sum` format) and `*.provenance.txt` (flat `key=value`). The v1.00.001 release keeps its historical `.build-info.json` assets, which are not modified.
8. **Destructive cleanup is opt-in.** A manual publish deletes the legacy `dev`, `dev-v0.1.0` and `dev-v0.1.1` releases and tags only when `purge_legacy_dev_releases` is ticked for that dispatch (default off). The rolling `test` draft is replaced on main app-code pushes, not by a manual public dispatch. Restricting who may approve a publish needs a GitHub environment with required reviewers; see `.ai/KNOWN_LIMITATIONS.md`.

## 4. Verifying a published file

Checksums only prove that a download matches the release page. They do not prove who built the file.

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
| Android build, debug-signed | In `test-release.yml` | Keep |
| Release-only Android build (`assembleRelease`) | Not built. `release` buildType has no signing config and is not minified. | **[decision]** whether a release build type is needed |
| MSI build and identity check | In `test-release.yml` | Keep |
| MSI install-over on hosted Windows | Required on `main` (skip fails); skipped on PR/branch | Keep |
| Public release creation | Hard-coded to `v1.00.002`; a reviewed one-shot main merge or manual dispatch builds, checks and publishes a verified draft once | Keep reviewed version changes and draft verification |
| Human approval before publishing | Ordinary pushes to `main` build and test only. The reviewed PR adding `.github/release-intent/v1.00.002` selects a public build exactly once when merged into `main`; a manual `workflow_dispatch` with `build_only=false` remains supported. Either path requires APK/MSI gates and a verified draft before publication. | **[decision]** add a protected approval environment for future release jobs |
| Hardware acceptance (Android, Windows) | Not done | Required before any "works on device" claim |

## 6. Known risks in the publish path (not yet changed)

- `publish.sh` at the repository root rewrites `origin` to an SSH remote and pushes `main` directly. It is not part of the release path, but it bypasses CI. **[decision]** remove it or move it under `.ai/`.
- The publish job deletes old `dev-*` releases and tags with `|| true`, so a failed delete is silently ignored.
- `publish` does not run on ordinary pushes to `main`. An ordinary merge refreshes only the private test draft. The single merge that **adds** `.github/release-intent/v1.00.002` selects the public build and publishes the fixed tag only after its own build gates; subsequent pushes cannot reuse the existing marker. A manual dispatch with `build_only=false` remains available. The push path cannot purge legacy dev releases.

## Live public-release metadata (2026-10-16)

The published `v1.00.002` tag still targets `1280e34`. Its GitHub metadata is now **non-prerelease**, its universal APK asset is named `dhun-universal-v1.00.002.apk`, and the release lists eight nonempty assets: four binaries and four checksum sidecars. The four provenance text assets present in the original draft are not currently listed. This differs from the one-shot publisher and historical candidate notes above. The historical `sha256sum -c` example in section 4 no longer applies to the renamed universal APK: its sidecar still names the original file; use the digest-comparison recipe in `docs/INSTALL.md`. The maintainer chose to preserve the current public release rather than reclassify, rename, replace, or upload any assets. The stale one-time provenance-repair proposal in PR #170 is therefore not safe to run; it expected the original prerelease flag and filenames. No release/tag mutation is part of the replacement verification fix.
