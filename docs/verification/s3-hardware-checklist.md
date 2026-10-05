# S3 Hardware Verification Checklist

> **Purpose:** Device-side acceptance for the rolling `test` build. Release metadata
> currently points to **`73b88b60b647120662812a5d33b233876acad283`** (PR #119),
> published **2026-09-28T18:48:23Z**. APK: 18,367,219 bytes; MSI: 112,934,912
> bytes; ProductVersion **2.137.1**.
>
> **Digest status:** the #119 SHA-256 values have not been independently verified.
> Do not use the PR #118 hashes previously printed here; they are not a verified
> checksum for this publish. Follow `15-test-build-gate.md` §1 and verify each
> downloaded file against its matching release `.sha256` sidecar **before**
> installing. Equal sizes do not prove identical bytes.
>
> **Rule:** Upgrade in place; do not uninstall existing data. Screenshot every
> FAIL plus steps 5 and 10 regardless. Round 1 report status is in
> `14-release.md` (received 2026-10-05); required build/device metadata and
> four screenshots have now been visually reviewed, but the step mapping and failure details remain outstanding.

---

## 0. Environment Record

| Field | Android | Windows |
|---|---|---|
| Device model | | |
| OS version | Android ___ | Windows build ____ (run `winver`) |
| Previous build installed | | |
| Date + local time started | | |

---

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

## C. Remaining S3/S6 Gates (not in this run)

- 30-minute soaks (Android unrestricted battery + Desktop libVLC), zero crashes
- Rotation / process death / Recents swipe / Force-stop (separate checks)
- Settings / theme / accent persistence
- Windows native column: single-instance, tray, close-to-tray, jump-list Play/Pause, media keys / SMTC round-trip
- Clean-target installs: APK + AAB + MSI install/run; upgrade preserves data; uninstall removes data
- Signing decisions (Play key? Authenticode? or stay test-grade + DRAFT-private)
- CHANGELOG finalized, README verified, KNOWN_LIMITATIONS + RISK_REGISTER reviewed
- User go-ahead → tag `v0.1.0` → publish GitHub release

---

## Reporting

Copy the block below, fill in, and send back:

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