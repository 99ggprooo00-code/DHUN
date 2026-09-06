# Windows candidate — quick test

This is an **unsigned development MSI from a branch build**, not a stable
release. It includes Java; you do **not** need a JDK or an Android SDK.
A system VLC installation is still required for audio output. The optional
yt-dlp fallback is separate and is not bundled.

## Use the right download

Download the **`msi` artifact ZIP** from the Actions run linked in the agent's
message, then **extract the ZIP before opening the MSI**. Do not use the old
Releases download for this candidate. The ZIP contains:

- `dhun-test.msi`
- `dhun-test.msi.sha256`
- `dhun-test.msi.build-info.json` — source commit, run link, internal MSI
  version and the exact binary hash/size
- this guide

Optional checksum check in PowerShell, from the extracted folder:

```powershell
Get-FileHash .\dhun-test.msi -Algorithm SHA256
```

Compare the result with the `.sha256` file. The internal MSI version is a
build sequence, not the application's public semantic version.

## Four things to check

1. **Close and back up.** Quit DHUN from its tray menu; closing the window
   alone may leave it running. Before testing an upgrade, copy the `userdata`
   folder from `%LOCALAPPDATA%\DHUN` somewhere safe, such as your Desktop.
   Do not delete the original. Test builds are not for irreplaceable data.
2. **Install over the old build.** Run the extracted MSI without manually
   uninstalling first. An unsigned-build/SmartScreen warning may appear.
   Confirm it opens one main window and your saved library is still there.
   If “Another version…” appears, report it rather than uninstalling to hide
   the failure.
3. **Try real playback and Home.** Play a song that was not previously cached;
   check both audible sound and advancing time. Scroll Home through more
   recommendations. Also look at artwork size, shuffle/previous/next/repeat
   alignment and selected-button colours.
4. **Send the short result.** Tell the agent whether upgrade, sound, scrolling
   and controls worked. If playback fails, open **Details** and copy its text
   (it includes the track ID). If it reports a missing yt-dlp fallback, send
   that message first; you do not need to set up a development environment.
   Do not share cookies, tokens, signed media URLs or unredacted private logs.

The build workflow checks the MSI's actual version/upgrade identity and can
exercise silent install-over with disposable userdata/cache sentinels on a
hosted Windows runner. That is **not** proof of sound, visuals, media keys,
VLC integration or behavior on your own machine. Those checks remain open
until you report the results.
