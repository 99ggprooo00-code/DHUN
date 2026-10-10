# Installing DHUN 1.00.001

DHUN 1.00.001 is a **public pre-release for testing**, not a stable, store-signed, or hardware-accepted release. Use it only if you are comfortable with experimental software and possible playback breakage. The release is built from this repository; DHUN is not affiliated with YouTube or Google.

## Which file to download

| Platform | File | Notes |
|---|---|---|
| Android (most devices) | `dhun-v1.00.001.apk` | Universal APK. Use this unless you know you need a split. |
| Android, 64-bit ARM | `dhun-v1.00.001-arm64-v8a.apk` | Optional split. Same app version. |
| Android, 32-bit ARM | `dhun-v1.00.001-armeabi-v7a.apk` | Optional split. Same app version. |
| Windows (desktop) | `dhun-v1.00.001.msi` | Per-user installer, built on a hosted x64 Windows runner. Unsigned. Windows versions and the MSI architecture have not been verified on user hardware. |

Each file has a matching `.sha256` sidecar. The v1.00.001 release also carries `*.build-info.json` provenance files, which are historical. Later builds use `*.provenance.txt` (plain `key=value` text) instead.

Open the [DHUN v1.00.001 release page](https://github.com/99ggprooo00-code/DHUN/releases/tag/v1.00.001) to download the files.

## Verify the checksum first

A checksum proves the file you downloaded is byte-identical to the file the release page published. It does **not** prove who built the file. Do not install a file whose checksum does not match.

Linux or macOS (run in the folder that contains both files):

```sh
sha256sum -c dhun-v1.00.001.apk.sha256
```

Windows PowerShell (run in the folder that contains both files):

```powershell
$expected = (Get-Content .\dhun-v1.00.001.msi.sha256 -Raw).Split()[0].ToLowerInvariant()
$actual = (Get-FileHash .\dhun-v1.00.001.msi -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -eq $expected) { "OK: checksum matches" } else { "MISMATCH: do not install" }
```

Replace the file name with the file you downloaded.

## Android

1. Download the APK and its `.sha256` sidecar, then verify the checksum as above.
2. Open the APK on the device. If prompted, allow your browser or file manager to install unknown apps, then return to the installer and confirm.
3. Launch DHUN. The minimum supported Android version is 7.0 (API 24).

Signing: the APK is signed with DHUN's **public throwaway test key**, committed at `app-android/keystores/dhun-test.p12`. Its certificate is `CN=DHUN Test Build, O=DHUN` with SHA-256 digest `d8e57ec69f40956d34adff137872db9fec3b817e149e2d0c33af5924142468c3`, derived from the committed keystore. That key is public, so **anyone can sign a file with it**. The signature only lets later test builds update an existing install. It is not evidence that a file came from the DHUN maintainers. It is not a Google Play signing key, and this build is not Play-upload-ready.

Uninstalling DHUN removes its app-private database, cached audio, and downloaded tracks. Back up anything you cannot replace.

## Windows

1. Download the MSI and its checksum sidecar, then verify the checksum as above.
2. Quit DHUN from its tray menu. If you want to keep your data, back up `%LOCALAPPDATA%\DHUN\userdata` first.
3. Run the MSI. It installs per-user and should not require administrator privileges. Windows SmartScreen may warn because the MSI is **not Authenticode-signed**. Install only if you trust this source and intend to test the build.
4. Install VLC separately if you want desktop audio playback. DHUN does not bundle or install VLC.
5. The optional desktop extraction fallback is not bundled. If needed, install the official `yt-dlp.exe` on `PATH`, or set `DHUN_YTDLP` to its full path, then restart DHUN. VLC and yt-dlp do different jobs.

Upgrading: the installer's internal version (for example `3.61.1`) is an installer sequence, not the app version. Do not expect the Windows version number to read `1.00.001`. An in-place upgrade over an older DHUN MSI is not proven on a user machine yet; back up `userdata` first. Uninstall from **Settings → Apps → DHUN**.

Do not share cookies, credentials, signed stream URLs, or unredacted logs when reporting a problem.

## Known limitations

- YouTube can change or restrict the endpoints DHUN uses; playback may fail or require a future update.
- Not signed for Google Play or the Microsoft Store. Android uses a public test key, and the Windows MSI is unsigned. Do not treat either as stable or suitable for a daily-use device.
- CI and emulator tests do not prove real-device playback, audio quality, battery behavior, or every installer path. Hardware acceptance remains in progress.
- The `/app/` browser interface is an engineering preview and produces no audible audio.
- Uninstalling the Android app removes its app-private library and downloads. Back up anything you cannot replace.

Report a playback problem with the track title, device/OS, and the app's bounded **Playback details** text. Never include cookies, tokens, signed media URLs, or account credentials.
