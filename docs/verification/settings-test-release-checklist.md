# Settings overhaul — test-release acceptance checklist

**Scope:** The initial Settings overhaul merged in PR #158 at `22c006f53ffcb5f030cf4747556bbc4967fb495d`; follow-up fixes and the test-only Theme Catalogue entry are being validated in PR #159. This is test-release validation, not approval of a public release or an app-version advance.

## Automated evidence recorded (2026-10-12)

| Check | Evidence | Result |
|---|---|---|
| Main CI: shared JVM tests, Android unit tests/build, desktop compile and JVM tests | [Run 38140705474](https://github.com/99ggprooo00-code/DHUN/actions/runs/38140705474), `main` at `22c006f` | Passed |
| Debug APK build | [Run 38140705571](https://github.com/99ggprooo00-code/DHUN/actions/runs/38140705571) | Passed |
| Main test-release: Windows theme JVM regressions, APK/MSI build and MSI install-over | [Run 38140705490](https://github.com/99ggprooo00-code/DHUN/actions/runs/38140705490), run 603, attempt 1 | APK/MSI jobs passed; AAB and publish skipped as expected |
| MSI install-over/data preservation | In run 38140705490, the required `Check install-over and userdata on disposable Windows` step passed. On `main`, `REQUIRE_INSTALL_OVER=true`; the script fails if the baseline is unavailable and the check is skipped. Its success path verifies the baseline checksum and upgrade identity, installs per-user, checks a userdata sentinel and cache sentinel after upgrade, and exercises upgrade-removal/uninstall cleanup. | Hosted disposable-Windows check passed. This is not interactive GUI, playback, or visual acceptance. |
| Python packaging/helper suite | `python3 -m unittest discover -s scripts -p 'test_*.py'` | 382 tests passed |
| app-web | `npm test` | 135 tests passed |
| Website | `npm ci`, then `npm run build` (run sequentially after Python tests) | Build passed. `npm ci` reports 9 existing dependency advisories (5 high, 4 moderate); no forced dependency change was made. |
| Local Gradle | Java/JDK is not installed in this sandbox | Not runnable locally; hosted main CI above supplies Gradle evidence. |

In the recorded main run `38140705490`, the APK/MSI and installer diagnostics were run-scoped Actions artifacts, not release assets; its `publish` job was skipped. The APK/MSI manifests include `.sha256` and `.provenance.txt` sidecars, and the upload steps use `if-no-files-found: error`. GitHub's artifact API reports archive digests `bd1055bc38d8711a18f91c3fc00a4aa0f42dc4899fb7fc1f6a26371bd14770a2` (APK) and `433f49defab5dfe009ac5330d20e3ddaa4e899405a84553e97f4871ae91d334d` (MSI); these are **artifact ZIP digests**, not the APK/MSI file checksums. The sandbox could not fetch those archives from GitHub's external Actions blob-storage host, so their sidecar contents and binary hashes were not independently re-read here.

PRs and build-only dispatches remain run-scoped artifacts. Under PR #159's workflow change, a successful `push` to `main` that includes app source/build changes replaces only the private rolling `test` draft with the passed APK/MSI artifacts. Docs-only pushes do not publish it. The fixed public `v1.00.001` prerelease remains on the separate manual path and is not changed by this test-only work. Each test artifact receives an explicit `test` build channel; ordinary development builds and public-release builds do not expose the Theme Catalogue.

## User device report (2026-10-15)

| Platform | Report | Acceptance status |
|---|---|---|
| Android 15 | Test APK updated the existing installation; the user's existing app data remained intact. | **PASS for in-place update/data retention only.** Playback, lyrics, region filtering, cache, equalizer, and reset checks remain unreported. |
| Windows 11 | MSI installation completed, then DHUN crashed. The crash stage, Windows build, exact error, and log have not yet been provided. | **FAIL / blocking.** Installer success is not an app-startup pass; do not accept Windows until the crash is diagnosed and the interactive test is repeated. |

For the Windows report, the packaged startup log should be at `%LOCALAPPDATA%\DHUN\userdata\dhun-startup.log`; if startup could not create that folder, check `%TEMP%\dhun-startup.log`. Also capture the matching **Windows Logs → Application** Event Viewer entry (faulting application/module and exception code). Before another install or uninstall, copy `%LOCALAPPDATA%\DHUN\userdata` somewhere safe and quit DHUN, including its tray process.

## Human acceptance still required

Mark each item **PASS**, **FAIL**, or **NOT RUN**. For failures, include exact reproduction steps and relevant startup-log/Event Viewer details. Do not treat JVM tests or MSI install checks as substitutes for these tests.

### Windows interactive GUI

Test the MSI from the run-scoped artifact on a Windows installation (preferably a disposable test machine for install-over). Record Windows edition, version/build, architecture, display scaling, and artifact/run used. The current user report is a blocking app crash after MSI installation; the installer itself succeeding does not satisfy app acceptance.

- [ ] Diagnose and retest the reported Windows 11 crash. Record whether it fails before the first window, after the window opens, or after an interaction; collect `dhun-startup.log` and the matching Application Event Viewer details.
- [ ] Repeatedly switch Light, Dark, and System themes; while playback and lyrics are active, change the OS theme too. Watch for crashes, freezes, partial/stale colours, and startup-log or Windows Event Viewer errors.
- [ ] In this explicitly tagged test build, open Settings → Theme catalogue, exercise the live previews, close back to Settings, and confirm theme/accent choices persist after restart. Confirm the catalogue row is absent in a public/non-test build.
- [ ] Switch static and dynamic artwork accents repeatedly while playback and lyrics remain active; confirm dynamic mode noticeably updates accent-colored controls across the app, while static mode keeps the selected accent.
- [ ] Change the preferred lyric provider directly from the lyrics view; confirm the current track reloads using that provider and the preference survives a restart.
- [ ] Adjust blur and brightness together across their ranges; close/reopen and restart DHUN to confirm appearance preferences persist.
- [ ] Test X/close-to-tray, reopen from the tray, and both Ctrl+Q and tray Quit. Confirm playback/lyrics do not leave a frozen or stranded window.
- [ ] Reset window geometry and confirm the window returns to the documented centered size/position.
- [ ] Install over the existing test installation and confirm the real queue, Settings, and user data remain. The hosted MSI test covers seeded userdata/cache sentinels, not the user's actual queue or application visuals.

### Android device or emulator

Record device/emulator model, Android version/API level, ABI, and APK artifact/run used. Restart after changing settings, then confirm persistence.

- [ ] In the test APK, open Settings → Theme catalogue, exercise the live previews, close back to Settings, and confirm theme/accent choices persist after restart. Confirm the catalogue row is absent in a public/non-test build.
- [ ] Playback quality selection, keyboard seek step, endless radio, shuffle/repeat policy, and in-app zero-volume pause. Do not infer platform audio-focus-loss or task-clear behavior from the in-app zero-volume setting.
- [ ] Lyrics enable/disable, synced preference, auto-scroll, text size, provider preference/order, and graceful fallback.
- [ ] Search the full country/region picker by full name and code; verify the selected catalogue region and explicit-content filtering in Home/Search/radio surfaces.
- [ ] Cache budget, audio-cache/history clearing, equalizer enable/preset/gains/preamp, and full Settings reset.
- [ ] On API 31+, adjust blur and brightness together. Below API 31, verify the supported sharp-artwork brightness fallback rather than expecting hardware blur.

### Network-dependent lyrics

- [ ] On a working network, exercise LRCLIB search and KuGou, including provider-order changes and a deliberately unavailable/failed provider; verify a graceful fallback.
- [ ] Record whether a failure is an HTTP/service outage or a local parsing/provider-order regression. These public tokenless endpoints can change independently of DHUN. Deterministic parser/resolver tests do not prove either live service is currently available.

## Evidence to return

For each platform, send the environment and artifact details above, PASS/FAIL/NOT RUN per item, and reproduction steps for every failure. For the Windows crash, specify whether it happens when launching immediately after install or only after a particular screen/action, the Windows edition/build, and the full exception/faulting-module details. Include `%LOCALAPPDATA%\DHUN\userdata\dhun-startup.log` (or `%TEMP%\dhun-startup.log`) and the matching Event Viewer entry, with private account or personal data removed. Live lyrics failures should include provider, approximate time, network state, and whether fallback succeeded.

## Release boundary

The source still declares Android `versionName` `1.00.001` and `versionCode` 6; neither changes in PR #159. No public release was changed or published for this validation. After this workflow change merges, the `publish_test` job may replace only the private rolling `test` draft after a successful app-code push to `main`; the fixed public `v1.00.001` path remains separate and manual. Any future app-version change or public release requires a distinct release decision.
