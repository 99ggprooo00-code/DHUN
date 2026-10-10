# Release policy (PROPOSAL — pending maintainer approval)

> **Status: proposal.** Nothing here changes an existing tag, release, or asset. Items marked **[decision]** need the maintainer's approval before they take effect. This file describes the current state, what each identifier means, and what a future release must prove.

## 1. Identifiers in use today

| Identifier | Current value | Meaning | Where it is declared |
|---|---|---|---|
| Git tag / GitHub release | `v1.00.001` → commit `6ebc45c` | Fixed public pre-release | `test-release.yml` (hard-coded) |
| App version (`versionName`) | `1.00.001` | What users see on Android | `app-android/build.gradle.kts` |
| Android `applicationId` | `dev.dhun.android` | Package identity | `app-android/build.gradle.kts` |
| Android `versionCode` | `6` | Integer Android uses for upgrades; must increase on each published build | `app-android/build.gradle.kts` |
| MSI `ProductVersion` | `3.61.1` (published) | Windows Installer sequence; **not** the app version | computed by `scripts/installer_version.py` from the run number and attempt |
| MSI `UpgradeCode` | `31ddb86b-9666-4071-b11c-45f16fa4682d` | Stable identity; changing it orphans existing installs | `app-desktop/build.gradle.kts` |
| Rolling tag | `test` | Private draft, replaced on every `main` build | `test-release.yml` |

The identifier `1.00.001` is not canonical SemVer: SemVer forbids leading zeros in numeric identifiers, and a Git tag `v1.00.001` is not the same string as `v1.0.1`. The project changelog asks for SemVer unless the maintainer requests otherwise. This is the only published version, and it is preserved exactly.

## 2. Versioning options for the next release — **[decision]**

The maintainer has not chosen the next version. Do not guess it.

- **Option A — keep the padded scheme.** Next release: `1.00.002`, with `versionCode` 7 and tag `v1.00.002`. Pro: matches the published lineage with no ambiguity. Con: not SemVer, and the zero padding is unusual.
- **Option B — switch to SemVer for new releases.** Example: `1.0.1` (tag `v1.0.1`), or a pre-release `1.0.1-rc.1`. Pro: follows the changelog's stated default. Con: `v1.00.001` and `v1.0.1` would look like two different versions of one project, and the publish workflow needs a new code path.

Either option needs the same identity change everywhere: Gradle `versionName`, the publish workflow's version and `--version` values, the release notes file name, the install guide and the changelog. `scripts/test_release_identity.py` will fail until those agree.

Pre-release markers and "Latest" marking: **[decision]** — current releases are prereleases and must not be marked Latest.

## 3. Rules that apply to every future release (proposed)

1. **`versionCode` strictly increases** for each published Android build. Never reuse or lower it.
2. **MSI `ProductVersion` strictly increases** over every published MSI. It is derived from the workflow run number and attempt (`1 + run // 256 . run % 256 . attempt`), so any build from a newer run outranks the previous one. Do not reset it. A local `packageMsi` without `-PdhunInstallerVersion` uses the fallback `1.0.6`, which is **lower** than the published `3.61.1` and would be refused as a downgrade. Such a file must never be published.
3. **`UpgradeCode` never changes.**
4. **Tags are immutable.** A published tag is never moved or re-pointed. A fix is a new version.
5. **The tag, the commit, and the built binaries must match.** The publish job refuses a superseded `main` commit. For the fixed `v1.00.001` path, `stage_versioned_release.py` requires every input's `sourceSha` to equal `GITHUB_SHA`, which is also the release `--target`. The rolling `test` draft uses `--target $GITHUB_SHA` but does not re-check its inputs.
6. **Assets are never edited in place.** A change to a published asset requires a new version.
7. **No `.json` files are added to release assets.** Future sidecars are `*.sha256` (standard `sha256sum` format) and `*.provenance.txt` (flat `key=value`). The v1.00.001 release keeps its historical `.build-info.json` assets, which are not modified.

## 4. Verifying a published file

Checksums only prove that a download matches the release page. They do not prove who built the file.

**Linux / macOS**

```sh
sha256sum -c dhun-v1.00.001.apk.sha256
```

**Windows PowerShell**

```powershell
$expected = (Get-Content .\dhun-v1.00.001.msi.sha256 -Raw).Split()[0].ToLowerInvariant()
$actual = (Get-FileHash .\dhun-v1.00.001.msi -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -eq $expected) { "OK" } else { "MISMATCH" }
```

**Android signer (needs Android build-tools `apksigner`)**

```sh
apksigner verify --print-certs dhun-v1.00.001.apk
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
| Public release creation | Hard-coded to `v1.00.001`, created once; later main runs skip it | A new version needs a reviewed workflow change and a **draft** step first **[decision]** |
| Human approval before publishing | None. Publishing happens automatically on push to `main`. | **[decision]** require a manual approval environment for public releases |
| Hardware acceptance (Android, Windows) | Not done | Required before any "works on device" claim |

## 6. Known risks in the publish path (not yet changed)

- `publish.sh` at the repository root rewrites `origin` to an SSH remote and pushes `main` directly. It is not part of the release path, but it bypasses CI. **[decision]** remove it or move it under `.ai/`.
- The publish job deletes old `dev-*` releases and tags with `|| true`, so a failed delete is silently ignored.
- `publish` runs on every push to `main` and replaces the private `test` draft each time.
