# Installing DHUN 1.00.001

DHUN 1.00.001 is a **public pre-release for testing**, not a stable, store-signed, or hardware-accepted release. Use it only if you are comfortable with experimental software and possible playback breakage. The release is built from this repository; DHUN is not affiliated with YouTube or Google.

## Download and verify

Open the [DHUN v1.00.001 release page](https://github.com/99ggprooo00-code/DHUN/releases/tag/v1.00.001). Download the platform asset and its matching `.sha256` sidecar. The release also includes `*.build-info.json` provenance files.

On Linux or macOS, verify an APK with:

```sh
sha256sum -c dhun-v1.00.001.apk.sha256
```

On Windows, compare the result of this command with the first value in the matching `.sha256` file:

```powershell
(Get-FileHash .\dhun-v1.00.001.msi -Algorithm SHA256).Hash.ToLowerInvariant()
```

Do not install an artifact if its checksum does not match.

## Android

1. Download **`dhun-v1.00.001.apk`** (the universal APK) and its checksum. The `arm64-v8a` and `armeabi-v7a` APKs are optional alternatives.
2. Verify the checksum using the command above.
3. Open the APK on the device. If prompted, allow your browser or file manager to install unknown apps, then return to the installer and confirm.
4. Launch DHUN. The minimum supported Android version is 7.0 (API 24).

The APK is signed with DHUN's public throwaway test key so subsequent test builds can update the same installation. It is not a Play Store signing key. Uninstalling DHUN removes its app-private database, cached audio, and downloaded tracks; back up anything important before uninstalling.

## Windows

1. Download **`dhun-v1.00.001.msi`** and its checksum sidecar.
2. Verify the checksum.
3. Run the MSI. It installs per-user and should not require administrator privileges. Windows SmartScreen may warn because the MSI is not Authenticode-signed. Install only if you trust this source and intend to test the build.
4. Install VLC separately if you want desktop audio playback. DHUN does not bundle or install VLC.
5. The optional desktop extraction fallback is not bundled. If needed, install the official `yt-dlp.exe` on `PATH`, or set `DHUN_YTDLP` to its full path, then restart DHUN. VLC and yt-dlp do different jobs.

Quit DHUN from its tray menu before updating. Back up `%LOCALAPPDATA%\DHUN\userdata` before an in-place update. Uninstall from **Settings → Apps → DHUN**. Do not share cookies, credentials, signed stream URLs, or unredacted logs when reporting a problem.

## Known limitations

- YouTube can change or restrict the endpoints DHUN uses; playback may fail or require a future update.
- The build is not signed for Google Play or the Microsoft Store. Do not treat it as stable or suitable for a daily-use device.
- CI and emulator tests do not prove real-device playback, audio quality, battery behavior, or every installer path. Hardware acceptance remains in progress.
- The `/app/` browser interface is an engineering preview and produces no audible audio.
- Uninstalling the Android app removes its app-private library and downloads. Back up anything you cannot replace.

Report a playback problem with the track title, device/OS, and the app's bounded **Playback details** text. Never include cookies, tokens, signed media URLs, or account credentials.
