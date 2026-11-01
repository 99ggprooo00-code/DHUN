## DHUN beta

> **Public rolling prerelease.** DHUN beta is experimental, not stable, store-ready,
> or a fixed release archive. Every successful beta publication replaces the previous
> `beta` release and tag after the Android and Windows package gates pass. The release
> is public (`isDraft=false`) and remains marked as a prerelease.

> **Current scope (2026-11-01):** this rolling channel contains native builds of the
> [multi-source DHUN product](../PRD.md#1-product-definition-and-source-of-truth), currently using
> YouTube Music. Spotify is planned and deferred, not included. Browser JioSaavn is a separate
> static build; native features here do not imply browser parity. Provider permissions remain unresolved.

A successful `main` app/source/build publication updates DHUN beta automatically.
Documentation-only merges do not publish a new artifact, and PR builds remain run-scoped
Actions artifacts. Superseded `main` builds are cancelled and the publisher refuses to
replace beta from a commit that is no longer the tip of `main`.

This beta-only channel includes **Settings → Beta tools → Theme catalogue**. Ordinary
development and fixed public builds do not show the entry, and a fixed public Android
build discards a restored catalogue route. These previews are experimental and are not
a hardware-acceptance claim.

### Assets

DHUN beta exposes exactly four installers and no text sidecars:

- `dhun-beta.apk` — universal Android APK (Android 7.0 / API 24 or newer; preferred install).
- `dhun-beta-arm64-v8a.apk` — optional 64-bit ARM APK from the same build.
- `dhun-beta-armeabi-v7a.apk` — optional 32-bit ARM APK from the same build.
- `dhun-beta.msi` — Windows per-user installer. VLC is required separately for audio.

Checksums and revision provenance remain in the internal workflow artifacts for build
verification and promotion. They are not uploaded as `.sha256` or `.provenance.txt`
release assets. Do not expect the fixed-release checksum-sidecar instructions to work
for these four beta downloads.

### Install and update

On Android, enable installation from your file manager/browser when prompted, then open
the universal APK unless you specifically need an ABI split. On Windows, run the MSI;
SmartScreen can warn because it is unsigned. See the
[installation guide](https://github.com/99ggprooo00-code/DHUN/blob/main/docs/INSTALL.md)
for platform details and uninstall/data warnings.

Android keeps application ID `dev.dhun.android` and the existing signing identity; Windows
keeps the existing MSI UpgradeCode. Install a newer beta over the old one **without clearing
app data or uninstalling first**. Update recovery must not erase playlists, favourites,
settings, downloads, queues or history. Uninstalling Android removes app-private data;
back up anything you cannot replace before any uninstall.

The Android APK uses a public throwaway test signing key, so its signature does not prove
who built a file. The Windows MSI is not Authenticode-signed. YouTube can change the
endpoints used for playback. CI, emulator checks and a single install report do not replace
the full device, audible-playback, accessibility or performance acceptance matrix.

### Increasing versions

Every beta/development APK records Android `1.00.<run>-beta.<attempt>` as its readable
versionName. Its integer versionCode is the package job's allocation time in Unix seconds.
Build APK and DHUN beta workflow runs have independent run counters, so the shared clock —
not either run number — orders their packages and prevents a newer PR artifact from becoming
an accidental downgrade. Later allocations increase the code; same-second jobs can share
one, which Android accepts as a reinstall. Bad clocks and values above Android's
2,100,000,000 limit fail rather than wrap.

The old `test-release` workflow ended at run 867. The renamed beta workflow therefore adds
an explicit 867 offset to its raw GitHub run number for readable Android and Windows package
identities. If GitHub resets the counter, the first effective beta run is 868; if it continues
the counter, the sequence jumps forward safely instead of becoming a downgrade. Build APK
leaves this offset unset because its Android versionCode is time-ordered. Invalid or exhausted
sequences fail rather than wrap.

Windows keeps its separate, increasing offset-aware workflow run/attempt MSI ProductVersion sequence.
The rolling tag name stays `beta`, the title stays **DHUN beta**, and the four asset names
stay stable while each publication deletes and recreates the release/tag at the new commit.
Fixed public releases, including `v1.00.002`, are separate and are not rewritten.

A rolling beta has a much higher Android versionCode than the fixed public build. Android
will reject installing that lower-versionCode fixed APK directly over a newer beta. Do not
use clearing data as a downgrade or recovery step; back up app-private data before any
uninstall.
