# S3 Hardware Verification Checklist

> **Purpose:** Device-side acceptance for the current rolling `test` build at
> `main@8a8d6c5adc536f34c8b677c9b20e5245747ed2e9`, published
> **2026-10-08T11:50:38Z**. Universal APK: **18,405,859 B**; MSI:
> **112,971,776 B**, ProductVersion **2.210.1**.
>
> **Required current digests:** universal APK
> `9665b75f9201d2953e278af155da19ea9b140f4facc82e7490acde5155efed97`;
> MSI `8a2383475947f8b9f5557d584cf0d6f0d34cec489aee3d3a40ab543d8618e298`.
> The arm64 and v7a split APKs are different files; use universal unless the
> report explicitly records a split filename and its matching release digest.
>
> **Rule:** Upgrade in place for round 4; do not uninstall existing data.
> Round 5 may use a clean API 24–25 device, but must record model, OS/API level,
> APK filename and installed SHA-256. Screenshot every FAIL. Follow
> `15-test-build-gate.md` §1 and verify each downloaded file against its matching
> release `.sha256` sidecar **before** installing.

---

## 0. Active environment record — rounds 4 and 5

| Field | Round 4 Android | Round 4 Windows | Round 5 Android API 24–25 |
|---|---|---|---|
| Device model | | | |
| OS version / API | | | |
| Previous build | | | n/a / __________ |
| Installed filename | `dhun-test.apk` | `dhun-test.msi` | `dhun-test.apk` |
| Installed SHA-256 | `9665b75f9201d2953e278af155da19ea9b140f4facc82e7490acde5155efed97` | `8a2383475947f8b9f5557d584cf0d6f0d34cec489aee3d3a40ab543d8618e298` | `9665b75f9201d2953e278af155da19ea9b140f4facc82e7490acde5155efed97` |
| Date + local time | | | |

## Round 4 — mini-player docking in constrained layouts (OPEN)

The mini-player must remain a **compact docked bar**; it must not expand into or
be covered by the full player. **Home, Search and Library must all remain
visible and reachable above the docked bar.** Do not modify the FullPlayer
surface itself — it remains governed by ADR-002.

| Platform | Setup and action | Pass condition | Result |
|---|---|---|---|
| Android | Start playback, return to the shell, rotate to landscape; visit Home, Search and Library | One compact docked mini-player bar; all three destinations visible above it; no ghost/duplicate content after animation settles | ☐ pass ☐ fail → __________ |
| Windows | Start playback, maximize/fullscreen the desktop window; visit Home, Search and Library | One compact docked mini-player bar; all three destinations visible above it; no overlap or unreachable navigation | ☐ pass ☐ fail → __________ |

For any failure, capture the whole window/screen after layout animation has
settled and record resolution, scale, and orientation.

## Round 5 — API 24–25 hardware floor (OPEN)

Run this on an Android 7.0/API 24 or Android 7.1/API 25 hardware device. CI's
`NewApi` lint gate is static evidence only and cannot pass this round.

| # | Action | Pass condition | Result |
|---|---|---|---|
| 1 | Install the verified universal APK | Install succeeds; recorded SHA matches | ☐ pass ☐ fail → __________ |
| 2 | Inspect launcher, then cold-launch | DHUN icon renders (not blank/default); app reaches Home without crash | ☐ pass ☐ fail → __________ |
| 3 | Open Search and submit a query | Results load without crash | ☐ pass ☐ fail → __________ |
| 4 | Play a result | Audio starts; mini/full player state updates | ☐ pass ☐ fail → __________ |
| 5 | Leave DHUN / lock the device while playing | Audio continues in background; notification/media control remains usable | ☐ pass ☐ fail → __________ |

A report without **device model + Android version/API + installed APK SHA-256**
is exploratory evidence only and cannot close round 5.

### Active-round report template

```text
ROUND: 4 Android / 4 Windows / 5 API-floor
DEVICE: <model>
OS: <Android version + API, or Windows build>
RESOLUTION / SCALE / ORIENTATION: <round 4>
PREVIOUS BUILD: <version or n/a>
FILE: <filename>
SHA-256: <full hash> -> MATCH / MISMATCH
RESULT: pass / fail
FAILED CHECK(S): <row/step and exact symptom>
SCREENSHOTS: <filenames/links, required for failure>
```

---

## Historical round-2 environment record

| Field | Android | Windows |
|---|---|---|
| Device model | Redmi Note 12 4G | |
| OS version | Android 15 | Windows 11 (build pending `winver`) |
| Previous build installed | | 2.172.1 |
| Date + local time started | 2026-10-08 | 2026-10-08 |

---

## Round 2 result status (report received 2026-10-08)

**Windows 11 Test (MSI 2.178.1)**
- **SHA-256 confirmed:** `0c67d2bf2c870e4209757da6296ca8bb0c68a5c921f17d4679b1096366e1d721`
- **Resolution:** 1920x1200, scale 125%
- Space shortcut regression is **FIXED**. Spaces type correctly in text inputs without triggering playback.
- **NEW DEFECT:** Enter key does not submit search in the Search input field.
- **NEW DEFECT:** Navigation Rail obscured/unreachable underneath the FullPlayer immersive view.

**Android Test (APK SHA-256 pending)**
- **Status reported:** "android all ✓" (all retests passed)
- **Pending Verification:** Needs exact APK SHA-256 confirmation before marking as formally PASSED for this release build.
- **NEW OBSERVATION:** Screenshots reveal an Android landscape layout displaying apparent duplicated/ghosted content. User must confirm if this visual artifact persists after layout animation settles or if it is a transient rotation snapshot.


## Round 1 result status (report received 2026-10-05)

Partial user report: Android steps 1, 6, 7 and 9 marked failed; Windows core
steps 10–13 reported pass. Android steps 5a–5c had art but it was described as
small; step 12 passed with a request for an anchored menu; step 13/15 were
partial because `Go to album` was not found; step 16 was not understood; step
18 was not reported. Exact symptoms, device/OS, execution date, previous build
and file hashes were not supplied. Four screenshots were visually reviewed but
not labeled to steps: album views show generic metadata/placeholders, while
playlist views show row art. Full per-step record: `docs/verification/14-release.md`.

## A. Android — Core Loop (9 steps)

### Fix 1 — Album artwork on playback (PR #114)

| Step | Action | Expect | Result |
|---|---|---|---|
| 1 | Search an album, open it | Every track row shows album cover (same image); note album name | ☐ pass ☐ fail → _______________ |
| 2 | Tap any track → Full Player | Full player shows album cover (blurred bg + square art) | ☐ pass ☐ fail → _______________ |
| 3 | Swipe down to Mini Player | Artwork visible, not empty placeholder | ☐ pass ☐ fail → _______________ |
| 4 | Go to Home / Search / Library while playing | Shell blurred backdrop = that album cover | ☐ pass ☐ fail → _______________ |
| 5a | Pull notification shade | Artwork present as large icon | ☐ art ☐ blank |
| 5b | Lock phone | Lock-screen media surface shows art | ☐ art ☐ blank |
| 5c | Home-screen media widget | Widget shows art (or not installed) | ☐ art ☐ blank ☐ n/a |
| 6 | Open a playlist | Rows unchanged — 64dp borderless thumbs | ☐ pass (unchanged) ☐ fail → ______ |

### Fix 2 — Album & Artist page backdrops (PR #114, #117)

| Step | Action | Expect | Result |
|---|---|---|---|
| 7 | **Nothing playing** → open album page | Page backdrop = album's own cover, blurred + darkened; no hard edge at header | ☐ pass ☐ fail → _______________ |
| 8 | Open artist page; scroll slowly | Portrait blurred behind content; name readable; sharp→blurred seam soft | ☐ pass ☐ fail → _______________ |
| 9 | Play track from elsewhere → open that album page | Page shows **its own** cover (not playing track's) | ☐ pass ☐ fail → _______________ |
| 10 | Find album with **white/pale** cover → open & read track titles | All text legible (mid-page track list is weakest) | ☐ legible ☐ hard ☐ unreadable<br>Where: ☐ header ☐ mid-page ☐ bottom<br>Album: _______________ |
| 11 | Open remote (YTM) playlist → then local playlist | Each page on its own blurred cover (remote: playlist cover; local: first track's art). Empty local = no backdrop (shell shows through). | ☐ pass ☐ fail → _______________ |

### Fix 3 — Compact ⋮ menu (PR #115)

| Step | Action | Expect | Result |
|---|---|---|---|
| 12 | Tap ⋮ on any list row | Small centered frosted card with track's blurred art; header (thumb+title+artist); actions; no divider, no Close; ~⅔ old height. Anchoring near ⋮ is a separate UX proposal. | ☐ pass ☐ fail → _______________ |
| 13 | Check labels on a known album track | Expected labels include `Play next` · `Add to queue` · `Add to playlist` · `Download` · `Go to artist` · `Go to album` (no suffixes); `Go to album` is conditional on album metadata | ☐ pass ☐ fail → wrong/missing: _______ |
| 14 | Tap outside → reopen & press Back | Both dismiss | ☐ tap-outside ☐ Back ☐ fail → ____ |
| 15 | Execute each action once | All 6 actions work as labelled | ☐ all ☐ failing: _______________ |
| 16 | Policy: no album metadata → `Go to album` absent; known album track → present/navigates. `Download` is host-manager based, not per-track; both shipped hosts provide one, so its hidden case is not selectable on-device. | ☐ correct ☐ wrong ☐ not tested |
| 17 | Full player → Queue tab → ⋮ on row | Anchored dropdown under button; same frosted look; 3 actions: Move up / Move down / Remove | ☐ pass ☐ fail → _______________ |
| 18 | Open ⋮ from all 7 surfaces | **Nowhere** shows the old tall dialog with divider + Close. Current compact track dialog is centered; queue dropdown is anchored. | ☐ pass ☐ fail ☐ not tested → surface: ______ |

### Menu Feel (judgment call)

| | |
|---|---|
| ☐ Yes — compact, comfortable | ☐ Mostly, but... _______________ |
| ☐ No — _______________________ | |

---

## B. Windows — Core Loop (4 steps)

| Step | Action | Expect | Result |
|---|---|---|---|
| 10 | Shuffle (queue tab → toggle) | List visibly reorders; playing track at top; highlight follows advance; no hiccup | ☐ pass ☐ fail → _______________ |
| 11 | Radio (start radio on a track) | Same song continues at same position; queue gets radio tail; Related tab excludes current track | ☐ pass ☐ fail → _______________ |
| 12 | Play/pause/next/prev; tray alive 10 min | Controls work; tray icon persists | ☐ pass ☐ fail → _______________ |
| 13 | Download track → wait complete → disconnect network → play from Library→Downloads | Plays offline from local file | ☐ pass ☐ fail → _______________ |

---

## C. Remaining S3/S6 gates beyond active rounds 4 and 5

- 30-minute soaks (Android unrestricted battery + Desktop libVLC), zero crashes
- Rotation / process death / Recents swipe / Force-stop (separate checks)
- Settings / theme / accent persistence
- Windows native column: single-instance, tray, close-to-tray, jump-list Play/Pause, media keys / SMTC round-trip
- Clean-target installs: APK + AAB + MSI install/run; upgrade preserves data; uninstall removes data
- Signing decisions (Play key? Authenticode? or stay test-grade + DRAFT-private)
- CHANGELOG finalized, README verified, KNOWN_LIMITATIONS + RISK_REGISTER reviewed
- User go-ahead → tag `v0.1.0` → publish GitHub release

---

## Historical rounds 1–2 reporting template

Copy the block below only when reconstructing those historical steps:

```
PLATFORM: Android <model>, Android <version>  /  Windows <build>
BUILT OVER: <previous build id>

DIGEST: apk <hash or n/a>  -> MATCH / MISMATCH
        msi <hash or n/a>  -> MATCH / MISMATCH

STEPS 1-18: pass / fail -> <failing numbers only>
  (e.g. "all pass except 5")

STEP 5 (Android only):
  5a notification: art / blank
  5b lock screen:  art / blank
  5c widget:       art / blank / not installed
  note: <one line>

STEP 10 (bright cover):
  album: <name>
  verdict: legible / hard to read / unreadable
  where: header / mid-page / bottom

MENU FEEL: compact / mostly / no  -> <one line>

SCREENSHOTS: <which steps>
OTHER: <anything that seemed wrong but technically passed>
```