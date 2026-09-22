# 15 — Test-build gate: step-by-step procedure

> **Purpose.** Device-side acceptance for the `test` rolling build that targets
> **`edf19e0`** (`edf19e03a6d8c3818c0392ed8e59cc0bf28860cd`, PR #114), published
> **2026-09-22T06:15:32Z**. This is the gate that closes three defects: album
> artwork on playback, album/artist page backdrops, and one compact ⋮ menu.
>
> **How to use it.** Work top to bottom, in order. Tick as you go. Write
> something down **only** where the sheet asks you to — a passing step needs a
> tick, not a sentence. Budget ~10 min for the walkthrough once installed.
>
> **Rule of one screenshot:** screenshot every fail, plus steps **5** and **10**
> regardless of outcome. Nothing else needs a picture.

---

## 0. Before you touch the app — fill this in once

| Field | Your value |
|---|---|
| Platform (Android / Windows / both) | |
| Device model | |
| OS version (Android __ / Windows build ____) | |
| Build installed *before* this one | |
| Date + local time you started | |

Windows build: `Win + R` → `winver`. Android version: Settings → About phone.

---

## 1. Verify the digest (do this BEFORE installing)

> **Why this is step 1 and not optional:** the APK is **18,367,219 B — byte-identical
> in size to the previous publish.** Size proves nothing. Only the hash proves you
> have PR #114 and not the old build. This step could not be verified from the
> coding sandbox (no network path to GitHub's asset hosts), so it is entirely on you.

**Android / macOS / Linux:**
```sh
sha256sum dhun-test.apk
```

**Windows PowerShell:**
```powershell
Get-FileHash .\dhun-test.msi -Algorithm SHA256
```

**Compare against:**

| Asset | Expected SHA-256 |
|---|---|
| `dhun-test.apk` | `75ed9fbb4ebdb3a820ec323f49e73505db0d86d96b529f5954fd7756b91204e7` |
| `dhun-test.msi` | `C95B004F86A6ED47C16E6A534AA2FA02572CF3D3670A3DF38527EE7B2D58F5A3` |

**Note down:**

- Computed hash: ______________________________________
- Match? ☐ yes ☐ **NO**

> 🛑 **STOP if it does not match. Do not install.** Tell me the computed hash and
> stop — a mismatch means the publish is not what we think it is, and testing it
> would invalidate the whole gate.

*(Optional second source: the release also ships `dhun-test.apk.sha256` /
`.msi.sha256` sidecars. Comparing against those too is cheap.)*

---

## 2. Install over the top

- **Android:** open the APK, allow "install unknown apps" if prompted — or
  `adb install -r dhun-test.apk`
- **Windows:** run the MSI; it upgrades in place.

**Do not uninstall** — that wipes your data and changes what you're testing.

**Note down:** ☐ installed over existing build, data intact

---

## 3. The walkthrough — 18 steps

For each step: **do** the action, check the **expect**, tick **pass** or **fail**.
A fail = one line describing what you saw + one screenshot.

---

### Fix 1 — album artwork on playback

**Step 1 — album track rows**
- **Do:** search an album, open it.
- **Expect:** every track row has a small square cover beside the track number; all rows show the same image (the album cover).
- **Note the album you used:** ____________________
- ☐ pass ☐ fail → ________________________________

**Step 2 — full player**
- **Do:** tap any track.
- **Expect:** the full player shows the album cover (blurred background + square art). *This is the originally reported bug.*
- ☐ pass ☐ fail → ________________________________

**Step 3 — mini player**
- **Do:** swipe down to the mini player.
- **Expect:** art visible, not an empty placeholder.
- ☐ pass ☐ fail → ________________________________

**Step 4 — shell backdrop**
- **Do:** go back to Home / Search / Library while it plays.
- **Expect:** the whole shell's blurred backdrop is that album cover.
- ☐ pass ☐ fail → ________________________________

**Step 5 — lock screen / notification / widget** *(Android only — screenshot regardless)*

> ⚠️ **Check these as three separate things.** Code analysis predicts they can
> disagree: the notification's large icon is sourced from `artworkData`, which
> nothing ever populates, while the widget fetches the art URL itself. Record
> each line independently — do not collapse them into one verdict.

- **5a. Notification shade** — pull the shade. Is there artwork (large icon)?
  ☐ art present ☐ blank / no art
- **5b. Lock screen** — lock the phone. Does the lock-screen media surface show art?
  ☐ art present ☐ blank / no art
- **5c. Home-screen media widget** — does the widget show art?
  ☐ art present ☐ blank / no art ☐ no widget installed
- **Note:** ________________________________
- **Screenshot:** yes (all three if you can)

**Step 6 — negative: playlist rows unchanged**
- **Do:** open a playlist.
- **Expect:** rows look **exactly** as before — 64dp borderless thumbs, unchanged. Not the new album-row treatment.
- ☐ pass (unchanged) ☐ fail (changed) → ________________________________

---

### Fix 2 — album & artist page backdrops

**Step 7 — album page backdrop**
- **Do:** with **nothing playing**, open an album page.
- **Expect:** page background is the album's own cover, blurred and darkened. Not a flat near-black slab, and **no hard edge** where the header wash ends.
- ☐ pass ☐ fail → ________________________________

**Step 8 — artist page backdrop**
- **Do:** open an artist page; scroll slowly.
- **Expect:** portrait blurred behind the content; artist name stays readable; the sharp hero fades into the blurred page as a **soft seam, not a cut line**.
- ☐ pass ☐ fail → ________________________________

**Step 9 — own cover, not the playing track**
- **Do:** play a track from somewhere else, then open that album page.
- **Expect:** the page shows **its own** cover as the backdrop (not the playing track's). This is by design.
- ☐ pass ☐ fail → ________________________________

**Step 10 — bright-cover stress test** *(screenshot regardless)*

> This is the one CI cannot judge. The dim is `0.40` and the mid-page scrim is
> only `0.32`, and both were recently lowered for more artwork glow — so a
> near-white cover is the weakest case. If it fails, it will fail on the
> **mid-page track list**, not the header (the header carries an extra veil).

- **Do:** find an album with a **white or very pale** cover, open it, and read the track titles and the album meta.
- **Expect:** everything stays legible.
- **Album name (required either way):** ____________________
- ☐ legible ☐ hard to read ☐ unreadable
- **Where it struggled:** ☐ header ☐ mid-page track list ☐ bottom
- **Note:** ________________________________
- **Screenshot:** yes

**Step 11 — negative: playlist pages stay flat**
- **Do:** open a playlist page; also glance at Home / Search / Library cards.
- **Expect:** playlist pages are **flat/opaque** — deliberate and recorded, not a regression. Home/Search/Library cards unchanged.
- ☐ pass (flat, as intended) ☐ fail → ________________________________

---

### Fix 3 — the ⋮ menu

**Step 12 — shape of the menu**
- **Do:** tap ⋮ on any list row (Home, Search, Library, album, playlist).
- **Expect:** a **small frosted card** with the track's own blurred art behind it: header (thumbnail + title + artist), then the actions. **No divider, no Close button.** It wraps its content instead of stretching full-width. Roughly **two-thirds the height** of the old sheet.
- ☐ pass ☐ fail → ________________________________

**Step 13 — the labels**
- **Expect** exactly these, short, no `(Artist Name)` suffix, no "for offline":
  `Play next` · `Add to queue` · `Add to playlist` · `Download` · `Go to artist` · `Go to album`
- ☐ pass ☐ fail → which label was wrong? ____________________

**Step 14 — dismissing**
- **Do:** tap outside. Then reopen and press Back.
- **Expect:** both dismiss it. No Close button needed.
- ☐ tap-outside works ☐ Back works ☐ either fails → ____________________

**Step 15 — every action once**
- `Play next` ☐ really inserts next
- `Add to queue` ☐ really appends
- `Add to playlist` ☐ opens the picker
- `Download` ☐ starts
- `Go to artist` ☐ navigates
- `Go to album` ☐ navigates
- Failing action, if any: ____________________

**Step 16 — policy unchanged**
- On a track with **no album** → "Go to album" must be **absent**. ☐ correct ☐ wrong
- On a **non-downloadable** track → "Download" must be **absent**. ☐ correct ☐ wrong

**Step 17 — queue dropdown**
- **Do:** full player → Queue tab → ⋮ on a queue row.
- **Expect:** an **anchored dropdown right under the button**, same frosted look, exactly three actions — `Move up` / `Move down` / `Remove from queue`. Should feel like the same menu family, not a second design.
- ☐ pass ☐ fail → ________________________________

**Step 18 — negative: one dialog everywhere**
- **Do:** open ⋮ from all seven surfaces (Home, Search, Library, album, playlist, full player, queue).
- **Expect:** **nowhere** does the old centered dialog with a divider and a Close button appear. All seven share one dialog.
- ☐ pass ☐ fail → which surface still shows the old one? ____________________

---

## 4. The judgment call CI can't make

**Does the menu feel compact in the hand?**

- ☐ Yes — reads as a menu, comfortable, nothing cramped
- ☐ Mostly, but... ______________________________________
- ☐ No — ______________________________________

Anything that felt off even though it technically passed — tap targets too small,
too narrow, frosted card hard to read over a busy cover — say it here. This is
the only question on the sheet with no right answer:
____________________________________________________

---

## 5. Report back — copy this block and fill it in

```
PLATFORM: Android <model>, Android <version>  /  Windows <build>
BUILT OVER: <previous build id>

DIGEST: apk <hash or n/a>  -> MATCH / MISMATCH
        msi <hash or n/a>  -> MATCH / MISMATCH

STEPS 1-18: pass / fail -> <list the failing numbers only>
  (e.g. "all pass except 5")

STEP 5  (Android only):
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

---

## 6. Stop conditions

- **Digest mismatch** → do not install, do not continue. Report the hash.
- **Crash or force-close** at any step → note the step and what you were doing; you can resume at the next step after relaunching.
- **Step 5a blank** → this is the predicted outcome, not a surprise. Report it as
  observed so it can be filed as its own defect; it is **separate** from the three
  defects this gate closes and does **not** block them.
