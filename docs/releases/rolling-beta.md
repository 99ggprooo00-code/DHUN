## DHUN beta

**Public rolling prerelease — experimental, not stable or store-ready.** The `beta` tag always points to the newest successfully verified beta. A new beta replaces the previous rolling beta; fixed releases such as [`v1.00.002`](https://github.com/99ggprooo00-code/DHUN/releases/tag/v1.00.002) are not changed.

## Highlights

- Clearer, more consistent popups and confirmations, with task-specific actions for playlist management, lyrics matching, region selection and playback recovery.
- Improved contrast and action hierarchy make key choices and destructive actions easier to distinguish.

## Downloads

- Android 7.0+ (API 24): [universal APK](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta.apk) is recommended for most devices.
- Optional Android splits: [64-bit ARM](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta-arm64-v8a.apk) · [32-bit ARM](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta-armeabi-v7a.apk).
- Windows: [per-user MSI](https://github.com/99ggprooo00-code/DHUN/releases/download/beta/dhun-beta.msi). Install VLC separately for desktop audio.

The Android splits are alternatives from the same build. The Windows MSI is unsigned, so SmartScreen may warn. Beta downloads do not include separate `.sha256` or provenance sidecars. GitHub records SHA-256 digests in release-asset metadata; the build workflow retains the full checksum and provenance records.

## Install and update

On Android, open the APK and allow your browser or file manager to install apps if prompted. On Windows, run the MSI. Install a newer beta over the existing one; do not uninstall or clear data to update. Uninstalling Android removes app-private library data and downloads. Read the full [installation guide](https://github.com/99ggprooo00-code/DHUN/blob/main/docs/INSTALL.md) before changing or removing an install.

## Scope and limits

DHUN is a free, open-source, cross-platform, multi-source client. YouTube / YouTube Music is the first implementation priority; the native Android and Windows builds currently use it through unofficial access. Spotify is planned, not implemented, and deferred pending technical, account/Premium, policy, legal, playback, privacy and cost review.

The experimental browser client is separate and uses JioSaavn through community-run wrappers. It is not feature-equivalent to the native apps; live catalogue access and audible playback remain unverified. Provider changes can interrupt playback, and CI is not a substitute for device acceptance. DHUN is not affiliated with or endorsed by YouTube, Google, JioSaavn or Spotify.

Read the [quick-start README](https://github.com/99ggprooo00-code/DHUN/blob/main/README.md), [privacy description (draft)](https://github.com/99ggprooo00-code/DHUN/blob/main/legal/privacy.md), [terms (draft)](https://github.com/99ggprooo00-code/DHUN/blob/main/legal/terms.md), and [third-party notices](https://github.com/99ggprooo00-code/DHUN/blob/main/THIRD_PARTY.md). [Report a bug](https://github.com/99ggprooo00-code/DHUN/issues/new/choose).
