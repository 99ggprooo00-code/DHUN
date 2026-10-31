## DHUN beta — private draft

> **Current scope (2026-10-28):** this rolling channel contains native builds of the
> [multi-source DHUN product](../PRD.md#1-product-definition-and-source-of-truth), currently using
> YouTube Music. Spotify is planned and deferred, not included. Browser JioSaavn is a separate
> static build; native features here do not imply browser parity. Provider permissions remain unresolved.

This is a collaborator-only, rolling development build of DHUN's `main` branch. A successful `main` build replaces it automatically when app source/build files change; documentation-only merges do not publish a new artifact. PR builds remain run-scoped Actions artifacts. This private test channel includes a **Settings → Test tools → Theme catalogue** preview; ordinary development and public-release builds do not show the entry. An old restored catalogue route is discarded when a public Android build starts. It is not stable, store-ready, or intended for daily use.

### Assets

- `dhun-test.apk` — Android universal APK (Android 7.0 / API 24 or newer; preferred install).
- `dhun-test-arm64-v8a.apk` and `dhun-test-armeabi-v7a.apk` — optional ABI APKs from the same build.
- `dhun-test.msi` — Windows per-user installer. VLC is required separately for audio.
- Each binary is accompanied by a `.sha256` checksum and a `.provenance.txt` provenance record (plain `key=value` text; release assets do not use JSON).

### Install

Verify the checksum sidecar before installing. On Android, enable installation from your file manager/browser when prompted, then open the universal APK. On Windows, run the MSI; SmartScreen can warn because it is unsigned. See the [installation guide](https://github.com/99ggprooo00-code/DHUN/blob/main/docs/INSTALL.md) for the longer guide and uninstall/data warnings.

### Important

These artifacts use a public Android test key; the Windows MSI is unsigned. YouTube can change the endpoints used for playback. CI and emulator tests do not replace device testing. Do not install on a device where data or reliable playback is critical.

### Increasing versions

Every test APK records Android `1.00.<run>-test.<attempt>` as its readable
versionName. Its integer versionCode is the package job's allocation time in Unix
seconds. Build APK and test-release have independent run counters, so the shared
clock—not either run number—orders their packages and prevents a newer PR artifact
from becoming an accidental downgrade. Later allocations increase the code;
same-second jobs can share one, which Android accepts as a reinstall. Bad clocks
and values above Android's 2,100,000,000 limit fail rather than wrap.

Windows keeps its separate workflow run/attempt MSI ProductVersion sequence. The
rolling `test` tag and asset filenames stay stable; the draft title stays "DHUN beta"
while the installed binary identity changes. Public `v1.00.002` is a separate fixed release and is not rewritten.
Android will reject installing a lower-versionCode public build over a newer test build; back up app-private data before uninstalling the test build.

App-code/build changes merged or pushed to main update this private draft after
both platform jobs succeed. Release-script/workflow changes also trigger it;
documentation-only changes do not. PRs only produce Actions artifacts. As before,
superseded main builds are canceled and cannot overwrite a newer main build.
