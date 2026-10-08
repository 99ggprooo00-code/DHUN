# Runbook: S3 hardware checklist (pre-tag device testing)

**Why this needs a human:** the agent sandbox has no devices, no speakers,
and no Windows taskbar. Everything below must be seen/heard on real
hardware. Budget ~2 hours wall-clock (mostly the unattended soaks).

Builds: the rolling [`test` pre-release](https://github.com/99ggprooo00-code/DHUN/releases/tag/test)
(`dhun-test.apk`, `dhun-test.msi`), replaced on every push to `main`.
Always re-download after the merge you are qualifying, and note the
`main@<sha>` you tested.

**Build identity (rechecked 2026-10-08, session `arena/688214aa-dhun`):** rolling
`test` is a published pre-release targeting
**`4607e07076e038f4290045f3f23f5f7fd082a058`** (PR #130, merged
2026-10-08T07:06:09Z), published **2026-10-08T07:11:13Z** from test-release run
**37741393816** (`publish` job 113194175657). CI **37741393880** and Build APK
**37741393815** also passed on that SHA. Current artifacts (one APK, not the
three-APK set — that lands only after the minSdk-24 PR merges):

| Asset | Bytes | Identity |
|---|---|---|
| `dhun-test.apk` | 18,383,603 | provenance notice **and** GitHub asset digest `590bd34a4b61f004185248043b04058644e7f64ec4067ee2b1aaa0918b8ad023` |
| `dhun-test.msi` | 112,971,776 | ProductVersion **2.189.1**, provenance notice **and** GitHub asset digest `ad036fffc1f41be428d1232580fb0632cc50bf2c6ac142d16ebd67596d8634a9` |

MSI install-over on that run: `2.186.1 → 2.189.1`, baseline
`b15da5091254be81fb8a92e3201adc29bbf040342053b22edc51901e3b6d5e1c`, sentinels
preserved, future-upgrade guard and uninstall smoke passed, **no skip**. The
`.sha256` sidecar *files* were not downloaded in the sandbox — **verify the
release's own sidecars before installing** and record what you actually
downloaded. This package **contains the round-4 mini-player dock** (`6ef48e9`).
It does **not** contain minSdk 24 or the ABI-split APKs.

**This package contains PRs #127–#130 and has not been hardware-signed-off.**
It includes the Search-Enter policy wiring (PR #129) and the docked rail-layout
mini-player (PR #130 / `6ef48e9`). It does **not** lower minSdk and it does
**not** publish ABI-split APKs. Whichever build you install, record its SHA-256
first — do not reuse `f0225f4` (APK `21a5fe86…9cc2c`, MSI 2.172.1
`c27175…3fe17d6`) or any PR-only `buildOnly=true` artifact to validate a merged
fix. Use `docs/verification/15-test-build-gate.md` §1 to verify current
sidecars. The rolling release changes on every `main` push, so re-download and
record the actual target, version and hashes immediately before testing.

**Preserve user data:** upgrade-install over the existing app first and check
playlists, settings and downloads remain. Do not uninstall your daily-use
installation to prepare this test. Clean-install and destructive uninstall
acceptance require a disposable device/profile or an explicitly approved backup
and restore plan. Hosted CI's sentinel tests do not replace device acceptance.

Recording: check each box with `[x]`, device model + OS version, and any
failure as *expected vs actual*. Paste the filled checklist back to the
agent — failures become S3-found functional bugs (the only UI work allowed
pre-tag besides this list).

## S3 hardware round 5 — API 24–25 (Android 7.0–7.1) install (after the minSdk-24 release is published)

**Not yet possible.** The rolling `test` release above is still minSdk 26 and a
single APK. Do this only after the minSdk-24 change is merged and the release
is republished with three APKs. Record the new `main@<sha>` and the APK
SHA-256 you actually installed — do not reuse `590bd34a…`.

1. Download **`dhun-test.apk`** (the universal). The `arm64-v8a` and
   `armeabi-v7a` assets are splits of the same build; DHUN bundles no native
   code, so they are expected to match the universal's SHA-256. Install the
   universal unless a split's sidecar differs and you are specifically testing
   that file.
2. Device: **API 24 or 25** (Android 7.0 or 7.1). Record model, `Build.VERSION.RELEASE`,
   API level, and the APK SHA-256.
3. **Launcher icon must render** — a play mark on a dark tile, not a blank or
   default Android icon. (The previous icon was adaptive-only and did not
   resolve below API 26.)
4. App must **launch**, **search**, **play** audible audio, and keep playing
   with the screen off / app backgrounded (notification or lock-screen controls
   respond). A crash on launch or a blank icon is a fail — paste logcat.

## S3 hardware round 4 — mini-player docked in landscape / fullscreen (retest against `4607e07`)

**Not yet on a device. The fix is already in the rolling `test` release above**
(`4607e07`, APK `590bd34a…`, MSI 2.189.1 `ad036ffc…`). Targets the
mini-player-covers-content defect fixed by commit `6ef48e9`. Record the APK/MSI
digests you install (verify sidecars; do not reuse older hashes).

1. **Android (Redmi Note 12 4G / Android 15), LANDSCAPE:** start a track, expand
   the full player, then collapse it back to Home. The mini-player must be a
   **compact bottom bar**; the Home/Search/Library list must be **fully visible
   and scrollable above it**, never covered. Switch to Search and Library and
   confirm the same.
2. **Windows 11, FULLSCREEN / maximized:** same sequence — mini-player docked at
   the bottom, tab content fully usable above it. Confirm the full-screen player
   still opens full-bleed on expand (unchanged) and Ctrl+F / Escape / Jump List
   still behave.
3. Record `winver`, the installed MSI ProductVersion + SHA-256, and the APK
   SHA-256 you tested. Any residual coverage of the list is a new defect — file
   it, do not sign off.

## S3 hardware round 2 — user report received 2026-10-07 (partial; not sign-off)

Execution date/time was not recorded. The device report says Android: Redmi
Note 12 4G / Android 15, APK SHA-256 `21a5fe86…9cc2c`; this matches the
current `f0225f4` APK provenance and the byte-identical APK produced at
`b1dba0c`. General playback/library/download, lyrics/settings and steps 19–22
were reported as working. `Go to album` was not found, the HTTP 403 case was not
tested, and offline streaming reportedly buffered for a long time without a
clear error before recovery after the network returned. These are partial
results, not an Android S3 pass. Lyrics are working; do not modify them for the
user's future idea unless asked.

Windows 11 was reported without its exact build or VLC version. The reported MSI
hash `c27175…3fe17d6` is the **current** `f0225f4` MSI 2.172.1; the report also
calls the test based on `b1dba0c`, whose MSI was 2.171.1 / SHA-256
`353cfa107a61114890386696d012e61f5dd6d562f7aa27f59428a25088244020`. The hash's
pre/post-upgrade timing is not specified, so do not claim the Windows result is
bound to the older MSI until the installed version and sequence are confirmed.
Reported issues: DHUN Jump List tasks absent, Space not responding, and a
full-window player surface preventing Home/Search/Playlists interaction.

Source trace found several code defects, fixed and verified by CI on product-code
head `3071d1d`: Jump List's zero-delay path called COM on the caller despite the
dedicated-worker contract; Space ran after child key dispatch; Ctrl+F and the
album-name search fallback could select a page beneath FullPlayer; Android
checked INTERNET without VALIDATED and drew the offline banner behind FullPlayer.
The first code head `1b2dea2` exposed missing Compose key-event imports; these
were added in `3071d1d` and the successor CI/build/package workflows passed.
FullPlayer remains intentionally immersive and the docked MiniPlayer remains a
separate fixed **72 dp** row. Tab-navigation commands now collapse the expanded
surface. The default AppUserModelID is not changed without evidence of an
installer/process identity mismatch; Windows privacy/policy can still suppress
Jump Lists. S3 remains **OPEN** until the post-merge `test` candidate is tested
on hardware. Final-head automated checks and all device acceptance remain gates.
Detailed ledger: `docs/verification/14-release.md`.

### Windows retest — exact reproduction and evidence

1. Install only the new post-merge candidate. Record `winver`, display
   resolution/scale, installed DHUN version, VLC version (or “not installed”),
   and pre/post-upgrade MSI file hashes. Verify the MSI against the current
   `test` release sidecar.
2. Start a track, expand FullPlayer, then press **Ctrl+F**. Search must become
   visible because the shortcut also collapses the expanded player. Press
   **Escape** or the **Collapse player** control as a separate check; Home,
   Search and Playlists must then be interactive, with only the fixed 72 dp
   docked MiniPlayer remaining. Capture a full-window screenshot if any page
   remains covered.
3. Test **Space** with focus on non-editable content and confirm playback
   toggles. Focus Search and type spaces; they must remain text and must not
   toggle playback. Repeat once while a DHUN name field is focused if available.
4. In Windows Settings → Personalization → Start, turn on **“Show recently
   opened items in Start, Jump Lists, and File Explorer”** (record if policy
   locks it off). While packaged DHUN is running, play a track and wait for
   `jump list: committed ...` in `dhun-startup.log`; then right-click the
   running/pinned taskbar icon. Expected app tasks include **Play / Pause** and
   **Open DHUN** (plus recent tracks after playback). If only Windows
   pin/unpin/close items appear, capture the menu and all `jump list:` log lines
   from `<install-dir>\userdata\dhun-startup.log` or `%TEMP%\dhun-startup.log`.
   Sanitize personal paths before sharing.
5. On Android, use a known album-linked track and record `Go to album`'s
   destination; also try a name-only album track if available. Test uncached
   streaming offline both with Wi-Fi connected but no validated Internet and in
   airplane mode, while FullPlayer is open. Confirm the offline message remains
   visible above the player, downloaded tracks remain available, and streaming
   resumes when Internet returns. Record the wait and recovery time. HTTP 403
   remains “not tested” unless a safe reproducible response is available.

## S3 hardware round 1 — partial report received 2026-10-05

The user reports Android failures at steps **1, 6, 7, 9** and pass on the
Windows core loop (steps 10–13). Android reported artwork on notification,
lock-screen and widget surfaces but said it looked small; album/artist/page
backdrop, menu-label and remaining-step detail is recorded in
`docs/verification/14-release.md`. The compact menu passed visually, with a
suggestion to anchor it near the originating ⋮. Device/OS, test date, build
hashes or prior build were not included. Four screenshot attachments were
listed; the four inline images have now been reviewed but are not mapped to
specific steps. Album views show placeholder metadata/art; playlist views show
row thumbnails. Treat this as partial evidence, not S3 sign-off. Do not infer
the tested bytes
from the release's matching asset sizes.

## Partial credit already banked (2026-09-20, `main@d99060e`)

The S1 residential test covered a few of these boxes. Recorded here so they are
not re-litigated, with scope stated honestly — everything else below is still open.

- **Android, install + search + play:** PASS — clean install from the rolling
  `test` APK, search worked, 4 songs played audibly with advancing position, no
  failures. Uninstall easy.
- **Android, background audio:** PASS for *audio continuation* with the screen
  locked. **Not** covered: whether the media notification's title/artwork/buttons
  are correct and functional — still tick that separately.
- **Windows, install + play:** PASS — install and uninstall easy, search, audible
  playback. **Not** covered: every native box (single-instance, tray, close-to-tray,
  jump-list verb, media keys/SMTC, shortcuts).
- **Still entirely open on both platforms:** 30-minute soaks, rotation/process
  death, downloads + airplane-mode offline, lyrics, Settings/theme/accent
  persistence, EQ, resume toggle.

## A. Android (upgrade first; clean install separately)

- [ ] Upgrade-install `dhun-test.apk`; verify existing playlists/settings/downloads.
- [ ] Separately, clean-install on a disposable device/profile; record that environment.
- [ ] Cold start → Home feed loads within ~10 s on Wi-Fi.
- [ ] Search a song → play → audio starts; MiniPlayer appears; expand to
      FullPlayer; seek, pause/resume, next/previous all respond.
- [ ] Background: switch apps / lock screen → audio continues; the media
      notification shows correct title/artwork and its buttons work.
- [ ] Rotation: open an artist page, rotate → no crash (nav restore best-effort).
- [ ] Recents dismissal: swipe away, relaunch → no crash; note playback behaviour.
- [ ] Force-stop via Android Settings → Apps → DHUN, relaunch → no crash.
- [ ] Process-death restoration: on a test device, background DHUN and reproduce
      OS process termination; record method and restoration result. Recents
      dismissal/force-stop alone does not prove this check.
- [ ] Downloads: download a track → airplane mode → plays from Downloads.
- [ ] Lyrics tab on a popular track shows synced or plain lyrics.
- [ ] **Settings (S4):** Library → **Settings** → switch theme to Light
      (applies instantly) → kill + relaunch → still Light; pick an accent
      (instant) → relaunch → still picked; set cache to 512 MB.
- [ ] **Equalizer (S4):** Settings → Equalizer → enable → pick **Full Bass**
      → play a bass-heavy track → change is clearly audible; disable →
      back to flat. (If nothing changes on any preset, note the device
      model — its DSP may lack an equalizer; that is a supported
      degradation, not a bug, but it must be recorded.)
- [ ] **30-minute soak:** queue a playlist, let it play 30 min untouched
      (screen off is fine). Note any stall, skip, crash, or notification
      going stale.
- [ ] Resume toggle: Settings → off → play → force-stop → relaunch →
      queue does NOT restore; back on → restores.

## B. Desktop Windows (upgrade first, needs VLC installed)

- [ ] Upgrade-install `dhun-test.msi`; verify existing playlists/settings/downloads.
- [ ] Separately, clean-install on a disposable Windows profile/VM.
- [ ] On that disposable installation only, verify ordinary uninstall removes
      test userdata (intentional, unlike upgrade); never use personal data here.
- [ ] Launch → Home loads; search + play. Test Space with focus on
      non-editable content, then focus Search and a DHUN name field and verify
      spaces type normally without toggling playback. Test ←/→/Ctrl+←/→ separately.
- [ ] Expand FullPlayer, then use **Ctrl+F**: Search must appear and FullPlayer
      must collapse. Also test Escape and the Collapse control. Home, Search and
      Playlists must be interactive afterward; docked MiniPlayer is the fixed
      72 dp row. If not, screenshot the entire window (see retest above).
- [ ] Double-launch `DHUN.exe` while running → no second window (the
      existing one surfaces).
- [ ] **Jump-list verb (S4):** first verify Windows' “Show recently opened
      items in Start, Jump Lists, and File Explorer” setting is on. After playing
      a track, wait for `jump list: committed ...` and right-click the running/
      pinned taskbar icon. **Play / Pause** toggles playback WITHOUT surfacing
      the window; **Open DHUN** surfaces it; a recent task appears. If only
      standard pin/unpin/close items appear, screenshot the menu and collect all
      `jump list:` lines from the startup log path above; record `winver` and MSI
      version. An explicit AppUserModelID has not been introduced; if commit is
      logged but tasks remain absent, report the shortcut identity and Windows
      setting state rather than treating the COM call as hardware acceptance.
- [ ] **Close/minimize contract:** window **X always exits and stops playback**;
      the separate minimize control leaves the app/tray playback running; tray
      **Quit** exits. Do not expect X to hide to tray (the old close-to-tray
      preference no longer changes this behavior).
- [ ] **Settings (S4):** theme Light + an accent → instant → restart →
      persisted. Cache budget selectable.
- [ ] **Equalizer (S4):** enable → Full Treble on a bright track → clearly
      audible; disable → flat.
- [ ] Media keys / SMTC (if the keyboard/OS exposes them): play/pause +
      metadata respond.
- [ ] **30-minute soak:** 30 min untouched playback; note stalls/skips.

## C. Sign-off

- [ ] Record the scheduled drill SHA/verdict separately from residential playback.
      `ENVIRONMENT_BLOCKED` is not GREEN or residential failure. Follow
      `s1-residential-evidence.md` for outside-runner evidence; do not reclassify
      a blocked runner because playback on a different network passed.
- [ ] No unchecked failure above, OR every failure pasted to the agent with
      *expected vs actual* + device/OS.
- [ ] Evidence line appended to `docs/verification/14-release.md`:

```text
- S3 <YYYY-MM-DD>: <Android device + OS> + <Windows version> on main@<sha> — PASS/FAIL — <notes>
```
