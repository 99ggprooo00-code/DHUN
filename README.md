# DHUN (धुन)

**A free, open-source, cross-platform, multi-source music client.** YouTube / YouTube Music is the first implementation priority. Native Android and Windows builds currently use YouTube Music through unofficial access. Spotify is planned, not implemented, and deferred pending technical, account/Premium, policy, legal, playback, privacy and cost review.

A separate experimental browser client uses JioSaavn through community-run wrappers. It has a different feature set from the native apps; live catalogue and audible playback remain unverified. See the [browser guide](docs/web-player/USER-GUIDE.md).

[Download DHUN beta](https://github.com/99ggprooo00-code/DHUN/releases/tag/beta) · [Install guide](docs/INSTALL.md) · [Beta release notes](docs/releases/rolling-beta.md)

> **Experimental prerelease:** DHUN beta is not stable, store-ready or fully accepted on user hardware. YouTube changes can interrupt playback. The Android APK uses a public test key; the Windows MSI is unsigned. Back up data before uninstalling.

## Download and install

The public rolling beta provides four files:

- Android 7.0+ (API 24): [universal APK](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta.apk) is recommended for most devices.
- Optional Android splits: [64-bit ARM](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta-arm64-v8a.apk) · [32-bit ARM](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta-armeabi-v7a.apk).
- Windows: [per-user MSI](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta.msi). Windows may show a SmartScreen warning; install VLC separately for desktop audio.

On Android, open the APK and allow your browser or file manager to install apps when prompted. On Windows, run the MSI. To update, install the new beta over the old one—do not uninstall or clear app data. Android uninstall removes app-private library data and downloads. The beta has no separate `.sha256` sidecars; GitHub records SHA-256 digests in release-asset metadata, while detailed checksum and provenance records stay in the build workflow.

The separate fixed [v1.00.002 prerelease](https://github.com/99ggprooo00-code/DHUN/releases/tag/v1.00.002) is not changed by beta updates. See the install guide for its checksum instructions and full upgrade/data warnings.

## What you can do

- Search and browse YouTube Music tracks, artists, albums and playlists in the native apps.
- Keep a local library with liked songs, playlists, listening history and downloads.
- Play and queue music, shuffle or repeat, and view lyrics when available.
- Use Android background playback and media controls, or the Windows desktop app's tray and media controls.

## Platforms and limits

Current native downloads are for Android and Windows. The browser client is a separate JioSaavn experiment—not a YouTube or Spotify web version—and does not provide offline audio, an equalizer or lyrics. Native and browser features are not interchangeable.

DHUN is not affiliated with or endorsed by YouTube, Google, JioSaavn or Spotify. The GPL covers DHUN code, not third-party music, artwork or lyrics. Permission and policy questions for native downloads/extraction remain unresolved; see the [YouTube policy notes](docs/research/03-youtube-youtube-music.md). YouTube access and playback depend on upstream services and may stop working. CI and emulator tests do not establish reliable playback on every device.

DHUN has no DHUN-hosted account or cloud library. Library data is stored locally, but searches and playback send requests to providers, which receive your IP address. Read the [privacy description (draft)](legal/privacy.md) before use. The [terms (draft)](legal/terms.md) are not yet in force or legal advice.

## Help and project information

- [Report a bug or request a feature](https://github.com/99ggprooo00-code/DHUN/issues/new/choose) · [Security reporting](SECURITY.md)
- [Changelog](CHANGELOG.md) · [Release policy](docs/releases/RELEASE-POLICY.md) · [Product direction](docs/PRD.md)
- [GPL-3.0 license](LICENSE) · [Third-party notices](THIRD_PARTY.md) · [Open-source license details](legal/open-source-licenses.md)

## Website

This README is the repository quick start. The [DHUN project website](https://99ggprooo00-code.github.io/DHUN/) is a separate build published from `website/` by GitHub Actions (`build_type: workflow`); see the [site publishing runbook](docs/runbooks/publishing-the-site.md).
