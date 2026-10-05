# 15 — Test-build gate: step-by-step procedure

> **Latest release metadata checked — 2026-10-05.** PR #119 is merged as
> `73b88b60b647120662812a5d33b233876acad283` (`73b88b6`). Post-merge CI
> **36467187148**, Build APK **36467187128**, and test-release **36467187181**
> passed. Rolling `test` published **2026-09-28T18:48:23Z**, targeting `73b88b6`;
> release metadata confirms that tag/target and the asset names/sizes. This is
> automated build/package evidence, **not hardware acceptance**.
>
> **Important digest correction:** the APK/MSI SHA-256 values currently available
> in the sandbox have **not** been independently verified for `73b88b6`. The
> `.sha256` asset contents/downloads returned empty/EOF here, and the publish-job
> check annotations contain no artifact digests. This sheet previously carried
> forward the **PR #118 (`16ad2e5`) hashes as though they were #119 hashes**; that
> was not justified. Do not use those values or infer byte identity from equal
> file sizes. §1 now marks the #119 digests unverified.
>
> **S3 hardware round 1 — partial user report received 2026-10-05.** The user
> reports Android failures at steps **1, 6, 7, 9**; the Windows core steps
> **10–13** are reported pass. Android step 13 is partial (five actions tried;
> `Go to album` not found), step 16 was not understood, and step 18 was not
> reported. Device/OS, exact test date/build hash, and prior build were not supplied. Four
> inline screenshots were visually reviewed but not labeled to steps: album views
> show placeholder artwork/generic metadata, while playlist views show row art.
> The exact step-6 mismatch remains unclear. Tested artifact identity is unconfirmed
> and **S3 remains open**. Details: `14-release.md`.
>
> The 18 checks still cover the UI fixes (step 11: playlist backdrop). PR #118's
> offline-broadcast verdict and delayed retry-budget refund have no deterministic
> manual step here: observe them if a real offline broadcast or long-session
> recovery is available, otherwise record **not exercised**. PR #119 contains
> release automation/documentation fixes; verify no regression in steps 1–18.

> **Purpose.** Device-side acceptance for the `test` rolling build. The sheet
> was written around PR #114's three defects — album artwork on playback,
> album/artist page backdrops, and one compact ⋮ menu — and now also covers
> what shipped after it: PR #115 (the ⋮ menu wraps its content, non-square
> covers *fill* the player card via Crop, album header covers fall back to
> the shallowest `thumbnails` array), PR #116 (endless-radio refill race fix,
> `download-artifact` v7) and PR #117 (playlist pages sit on their own
> blurred artwork — step 11 below). PR #118 changed two engine-room
> behaviours (offline-broadcast error text, retry-budget refund) and adds no
> step: see the banner. Always test the **newest** publish;
> see the banner above for how to verify which one you have.
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

> **Why verify hashes:** these files have the same byte sizes as earlier
> publishes. Equal size is not equal bytes. The sandbox confirmed release
> metadata but could not retrieve the #119 artifact hashes independently; verify
> your downloaded bytes against the matching sidecars below.

**Linux:**
```sh
sha256sum -c dhun-test.apk.sha256
```

**macOS:** use `shasum -a 256 dhun-test.apk` and compare with
`cat dhun-test.apk.sha256`.

**Windows PowerShell** (repeat for both files you downloaded):
```powershell
Get-FileHash .\dhun-test.apk -Algorithm SHA256
Get-Content .\dhun-test.apk.sha256
Get-FileHash .\dhun-test.msi -Algorithm SHA256
Get-Content .\dhun-test.msi.sha256
```

**Build identity** — rolling `test` targeting
**`73b88b60b647120662812a5d33b233876acad283`** (PR #119), published
**2026-09-28T18:48:23Z**; MSI ProductVersion **2.137.1**. Release metadata
reports these asset sizes; the current SHA-256 values remain **unverified**.

| Asset | Bytes | SHA-256 status |
|---|---:|---|
| `dhun-test.apk` | 18,367,219 | **Unverified for `73b88b6` — check the release `.sha256` sidecar** |
| `dhun-test.msi` | 112,934,912 | **Unverified for `73b88b6` — check the release `.sha256` sidecar** |

The values previously shown here — APK
`8276e0298c0df6d22084e07d8ff3477ab22550e586d4daa41de43e60aa8de770` and MSI
`4e28c551db2834c699351b3eb1c4d03c96dc46d536156242203845ee28d46b6e` — belong
to the earlier **`16ad2e5`** publish; they are historical only, not expected
hashes for this build. Matching sizes do not prove matching bytes.

> Compare each computed file hash with its matching `.sha256` sidecar from this
> same `test` release. If you cannot verify either sidecar/hash pair, stop
> before installing and report both what you computed and what the sidecar says.

**Note down:**

- Computed hash: ______________________________________
- Match? ☐ yes ☐ **NO**

> 🛑 **STOP if it does not match. Do not install.** Tell me the computed hash and
> stop — a mismatch means the publish is not what we think it is, and testing it
> would invalidate the whole gate.

*The release `.sha256` sidecars are the expected digests for this check; verify
both artifacts against their matching sidecars.*

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

**Step 11 — playlist page backdrop** *(changed by PR #117; was "negative: playlist pages stay flat")*
- **Do:** open a remote (YTM) playlist page, then one of your local playlists.
- **Expect:** each page sits on its **own blurred, darkened cover** — remote: the playlist's own cover; local: the **first track's** art (the same art the header shows) — the same treatment as the album/artist pages (step 7), including while nothing is playing. Rows and header layout unchanged; Home/Search/Library cards unchanged.
- A local playlist with **no tracks** paints no backdrop (shell backdrop / base colour shows through) — that is the designed fallback, not a bug.
- **Note the playlist you used:** ____________________
- ☐ pass ☐ fail → ________________________________

---

### Fix 3 — the ⋮ menu

**Step 12 — shape of the menu**
- **Do:** tap ⋮ on any list row (Home, Search, Library, album, playlist).
- **Expect:** a **small centered frosted dialog** with the track's own blurred art behind it: header (thumbnail + title + artist), then the actions. **No divider, no Close button.** It wraps its content instead of stretching full-width. Roughly **two-thirds the height** of the old sheet.
- The menu is centered by the current design. A compact anchored menu emerging near the ⋮ is a separate UX proposal, not a failure of this step.
- ☐ pass ☐ fail → ________________________________

**Step 13 — the labels**
- **Do:** open ⋮ on a track known to belong to an album (for example, a track row on the album page from step 1).
- **Expect:** exactly these labels when their metadata/capability is present, short, no `(Artist Name)` suffix, no "for offline": `Play next` · `Add to queue` · `Add to playlist` · `Download` · `Go to artist` · `Go to album`.
- `Go to album` is conditional: it is absent when the selected track has neither an album name nor an album ID. Use a known album track to verify the label and navigation.
- ☐ pass ☐ fail ☐ partial (one or more unavailable) → ____________________

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
- On a track with **no album metadata** (`albumName` and `albumId` both absent) → `Go to album` must be **absent**. On a known album track → it must be present and navigate. ☐ correct ☐ wrong ☐ not tested
- **Correction to the old instruction:** `Download` is controlled by whether the host supplies a `DownloadManager`, not by a per-track "downloadable" flag. The current Android and Windows builds supply one, so there is no normal device-side "non-downloadable track" to choose; expect `Download` to be present and test it in step 15. The no-manager hidden case is pinned by `TrackMenuPolicyTest`, not manually selectable in these builds. ☐ understood ☐ not applicable on device

**Step 17 — queue dropdown**
- **Do:** full player → Queue tab → ⋮ on a queue row.
- **Expect:** an **anchored dropdown right under the button**, same frosted look, exactly three actions — `Move up` / `Move down` / `Remove from queue`. Should feel like the same menu family, not a second design.
- ☐ pass ☐ fail → ________________________________

**Step 18 — negative: one menu family everywhere**
- **Do:** open ⋮ from all seven surfaces (Home, Search, Library, album, playlist, full player, queue).
- **Expect:** **nowhere** does the old tall dialog with a divider and a Close button appear. The track menu remains centered in the current design; the queue menu is anchored, but uses the same compact frosted style.
- ☐ pass ☐ fail ☐ not tested → which surface still shows the old one? ____________________

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