# Runbook: S3 hardware checklist (pre-tag device testing)

**Why this needs a human:** the agent sandbox has no devices, no speakers,
and no Windows taskbar. Everything below must be seen/heard on real
hardware. Budget ~2 hours wall-clock (mostly the unattended soaks).

Builds: the rolling [`test` pre-release](https://github.com/99ggprooo00-code/DHUN/releases/tag/test)
(`dhun-test.apk`, `dhun-test.msi`), replaced on every push to `main`.
Always re-download after the merge you are qualifying, and note the
`main@<sha>` you tested.

**Build identity (verified 2026-09-28):** rolling `test` published
**13:04:52Z**, target and tag **`16ad2e5`** (PR #118); MSI **2.137.1**.
Use `docs/verification/15-test-build-gate.md` §1 for full SHA and digests,
and run its **18-check walkthrough first**, then the download/radio/shuffle
re-test in `.ai/HANDOFF_NEXT_SESSION.md` and the standing checks below.
If `test` has moved, verify the new release target/tag and SHA-256 sidecars.
Matching byte sizes do not prove matching bytes, even on docs-only rebuilds.

**Preserve user data:** upgrade-install over the existing app first and check
playlists, settings and downloads remain. Do not uninstall your daily-use
installation to prepare this test. Clean-install and destructive uninstall
acceptance require a disposable device/profile or an explicitly approved backup
and restore plan. Hosted CI's sentinel tests do not replace device acceptance.

Recording: check each box with `[x]`, device model + OS version, and any
failure as *expected vs actual*. Paste the filled checklist back to the
agent — failures become S3-found functional bugs (the only UI work allowed
pre-tag besides this list).

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
- [ ] Launch → Home loads; search + play; Space/←/→/Ctrl+←/→ shortcuts work.
- [ ] Double-launch `DHUN.exe` while running → no second window (the
      existing one surfaces).
- [ ] **Jump-list verb (S4):** right-click the taskbar icon → **Play/Pause**
      → playback toggles WITHOUT the window surfacing; **Open DHUN**
      surfaces it. (If the tasks are missing, note it — jump-list
      registration is Windows-version-sensitive.)
- [ ] **Close-to-tray (S4):** default on → window X hides to tray, music
      keeps playing, tray **Quit** exits. Settings → off → restart app →
      X now quits the app.
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
