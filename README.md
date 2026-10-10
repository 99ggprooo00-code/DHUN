# DHUN

[Features](https://99ggprooo00-code.github.io/DHUN/features/) · [Source](https://github.com/99ggprooo00-code/DHUN)

DHUN is a free, open-source music player for Android and Windows desktop. It uses YouTube Music for catalogue data and playback, with no DHUN account or sign-in.

> **Pre-release:** `v1.00.001` is an experimental build, not a stable or store-ready release. Playback depends on upstream YouTube behavior and can stop working without warning. Android and Windows hardware acceptance is still in progress. The rolling `test` build is restricted to repository collaborators.

<!-- Screenshots will be added after maintainer device captures are available. -->

## What DHUN includes

- Home discovery, search, and artist, album, and playlist browsing.
- A personal library with liked songs, playlists, listening history, and downloads for offline playback.
- Android background playback with notification/media controls, plus a mini-player and full player.
- Queue management, shuffle, repeat, lyrics when available, and related-track/radio playback.
- A Windows desktop app with a system tray, media controls, and taskbar jump lists.
- Appearance and playback settings. Desktop audio uses the system's VLC/libVLC installation.

The browser interface at [`/app/`](https://99ggprooo00-code.github.io/DHUN/app/) is an engineering preview: it has sample/live metadata but **does not play audio**.

## Install the pre-release

Download builds from the [DHUN v1.00.001 pre-release](https://github.com/99ggprooo00-code/DHUN/releases/tag/v1.00.001). Read the [installation guide](docs/INSTALL.md) first, and verify each file against its `.sha256` sidecar before installing. These builds use a public test signing key and are not intended for a daily-use device. Security reports: see [SECURITY.md](SECURITY.md). Release rules: see [release policy (proposal)](docs/releases/RELEASE-POLICY.md).

- **Android:** Android 7.0 (API 24) or newer. Install the universal APK unless you specifically need an ABI split.
- **Windows:** install the MSI per user. Windows SmartScreen may warn because the installer is unsigned; playback requires VLC installed separately.

## Build from source

Requires JDK 17. Android builds also need Android SDK 35 and `ANDROID_HOME` set.

```bash
./gradlew :app-android:assembleDebug
./gradlew :app-desktop:run
./gradlew :shared:jvmTest
```

The committed Android test key is public and for test builds only. Do not use it for a store release. The Windows MSI's internal version is an installer-upgrade sequence, not the DHUN app version.

## Status and scope

DHUN has no DHUN-hosted account, cloud sync, iOS app, or production browser audio player. The web interface is only a preview. YouTube extraction is a maintenance risk, and passing CI does not replace testing playback and installation on real devices.

## Website

The [DHUN site](https://99ggprooo00-code.github.io/DHUN/) is published by GitHub Actions (`build_type: workflow`) and checked after deployment. If Pages falls back to `legacy`, see [the publishing runbook](docs/runbooks/publishing-the-site.md).

## License

GPL-3.0. See [LICENSE](LICENSE) and [THIRD_PARTY.md](THIRD_PARTY.md). DHUN is not affiliated with YouTube or Google.

For release details, see [`CHANGELOG.md`](CHANGELOG.md). For development and known limitations, see [`docs/`](docs/).
