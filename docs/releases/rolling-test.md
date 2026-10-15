## DHUN rolling test build — private draft

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

Every test-release workflow run assigns Android `1.00.<run>-test.<attempt>` and a
strictly increasing Android versionCode. Reruns increase the attempt; the next run
outranks all supported attempts of the previous run. Windows already uses the same
workflow run/attempt counter for its increasing MSI ProductVersion. The rolling
`test` tag and asset filenames stay stable; the release title and installed binary
identity change. Public `v1.00.002` is a separate fixed release and is not rewritten.
Android will reject installing a lower-versionCode public build over a newer test build; back up app-private data before uninstalling the test build.

App-code/build changes merged or pushed to main update this private draft after
both platform jobs succeed. Release-script/workflow changes also trigger it;
documentation-only changes do not. PRs only produce Actions artifacts. As before,
superseded main builds are canceled and cannot overwrite a newer main build.
