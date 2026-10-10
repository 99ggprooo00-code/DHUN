## DHUN rolling test build — private draft

This is a collaborator-only, rolling development build of DHUN's `main` branch. It is replaced when a newer successful main build is published. It is not stable, store-ready, or intended for daily use.

### Assets

- `dhun-test.apk` — Android universal APK (Android 7.0 / API 24 or newer; preferred install).
- `dhun-test-arm64-v8a.apk` and `dhun-test-armeabi-v7a.apk` — optional ABI APKs from the same build.
- `dhun-test.msi` — Windows per-user installer. VLC is required separately for audio.
- Each binary is accompanied by a `.sha256` checksum and a `.provenance.txt` provenance record (plain `key=value` text; release assets do not use JSON).

### Install

Verify the checksum sidecar before installing. On Android, enable installation from your file manager/browser when prompted, then open the universal APK. On Windows, run the MSI; SmartScreen can warn because it is unsigned. See the [installation guide](https://github.com/99ggprooo00-code/DHUN/blob/main/docs/INSTALL.md) for the longer guide and uninstall/data warnings.

### Important

These artifacts use a public Android test key; the Windows MSI is unsigned. YouTube can change the endpoints used for playback. CI and emulator tests do not replace device testing. Do not install on a device where data or reliable playback is critical.
