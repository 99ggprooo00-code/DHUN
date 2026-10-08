# 15 — Test-build gate: step-by-step procedure

> **Current gate (2026-10-08 — product-code head green; final PR head and hardware gates remain).**
> Main and the rolling `test` release still target `f0225f4d68c1dfcfb3dfcb798ca8e3b95aaaafe5`.
> Baseline CI **37548884056**, Build APK **37548884107**, and test-release
> **37548884077** passed, but predate the fixes. Baseline APK is 18,367,219 B,
> SHA-256 `21a5fe862b0c948fbc038417e156310e9eaab74bf9ea2f59214b9807f8c9cc2c`;
> baseline MSI is 112,947,200 B, ProductVersion 2.172.1, SHA-256
> `c27175cecca8cc071364f76704e67faa04ec290d1afd58710b48ff3643fe17d6`. Do not
> use this old package to validate fixes or confuse it with the PR build-only
> artifacts; after merge, verify the new rolling release's own `.sha256` sidecars.
>
> **User report round 2 (received 2026-10-07; execution date not supplied).**
> Android: Redmi Note 12 4G / Android 15, reported APK hash `21a5fe86…9cc2c`;
> general playback/library/download, lyrics/settings and steps 19–22 reported
> working. “Go to album” was not found, HTTP 403 was not tested, and offline
> streaming buffered without clear feedback before network recovery. Windows 11:
> exact build/VLC not supplied; missing Jump List tasks, Space not responding,
> and FullPlayer blocking Home/Search/Playlists were reported. MSI hash
> `c27175…3fe17d6` identifies old 2.172.1 despite the report's reference to
> `b1dba0c` / MSI 2.171.1; artifact timing remains ambiguous. Lyrics work and
> must not be changed.
>
> **Root-cause fixes in product-code head `3071d1d` (automated code/package checks green):**
> JumpList immediate commits had bypassed the COM worker; all commits now run on
> the dedicated thread with per-thread apartment lifetime. Space uses a preview
> handler with text-input focus tracking. Tab selection, Ctrl+F, Android launcher
> shortcuts, and name-only album/artist fallbacks collapse FullPlayer before
> revealing the destination. Android requires INTERNET+VALIDATED and layers the
> offline banner above FullPlayer. FullPlayer remains immersive; a track with no
> album metadata still has no valid album destination. Default AppUserModelID
> remains unchanged pending identity evidence; Windows privacy/policy can hide tasks.
>
> Product-code head `3071d1d6650291d51559f2884f4ac7734d3aac75` passed push CI
> **37710630655**, PR CI **37710634901**, Build APK **37710634909**, and
> test-release **37710634914**. CI includes shared JVM, Android Robolectric/build,
> desktop compile/JVM tests, and packaging helpers. The MSI job completed the full
> hosted `2.172.1 → 2.176.1` install-over, preserved userdata/cache sentinels, and
> passed future-upgrade/uninstall checks (no skip). PR test artifacts are
> `buildOnly=true`, not the published `test` release: APK 18,383,603 B / SHA-256
> `75c9da37e5e4beeda31306e9f834d4855d17c14a21888c8e142cb77d6d3659d2`; MSI
> 2.176.1 / SHA-256
> `18bfc43e0fda45978f7fbe2d280c9c6cfd580786e9d801bc301f94840bdf67a0`.
> The earlier head `1b2dea2` failed due to omitted Compose key-event extension
> imports; `3071d1d` fixes them. A documentation-status successor must also pass
> CI, Build APK and test-release on the final PR head before ready/merge. PR-path
> `publish`/`release_draft` are expected skips; APK/MSI and the actual MSI
> install-over must pass. Physical S3 remains OPEN until the user tests the new
> post-merge hashes. Exact steps: `docs/runbooks/s3-hardware-checklist.md`.
>
> **PR #120 candidate device retest — user-reported PASS, 2026-10-05.** The user
> downloaded the build-only `apk` artifact from test-release run **37322658878**,
> verified its APK checksum, and tested on Android 15 build `SQ3A.240829.003`
> (device model not supplied). Single cover/rows/full-player art, another album
> cover, playlist cover/rows, and Home/Search thumbnails work. The now-playing
> backdrop appears only during playback, as expected. Step-6 row shape and
> `Go to album` are reported as “seems working.” The artifact ZIP is compressed
> (~17 MB); the APK inside is 18,367,219 B with SHA-256
> `c351341edbeaa7935c7a52ec096141d6d28dc18133000ff2bc00cf63473c5458`. This
> closes the targeted artwork retest, **not** all 18 steps or S3/S6 acceptance.
> See `14-release.md` for the record and remaining scope.
>
> The 18 checks still cover the UI fixes (step 11: playlist backdrop). PR #118's
> offline-broadcast verdict and delayed retry-budget refund have no deterministic
> manual step here: observe them if a real offline broadcast or long-session
> recovery is available, otherwise record **not exercised**. PR #119 contains
> release automation/documentation fixes; verify no regression in steps 1–18.

> **PR #121 candidate (2026-10-06) — the two Full Player surfaces.** The user
> reported (on the digest-verified #120 candidate): ⋮ → **Add to playlist** was
> "still the old Material 3 interface" with "an unusually long/oversized box",
> and ≡♪ → **Queue/Related** "still shows the old interface" and does not close
> on a downward swipe. Both are fixed on branch `arena/cf4e91ba-dhun` (PR #121):
> the picker is rebuilt on the compact frost-through-artwork menu family with a
> content-sized list and the DHUN input, and the panel's header strip is now a
> working grab handle (plus the panel stopped painting the retired near-black
> slab). Candidate APK: test-release run **37389110912**, `apk` job green —
> `dhun-test.apk` 18,367,219 B, SHA-256
> `21a5fe862b0c948fbc038417e156310e9eaab74bf9ea2f59214b9807f8c9cc2c`, source
> `a9e8c9d7` (PR merge ref of head `85e73eb`). It was a **build-only artifact**,
> but the rolling `test` release now carries a **byte-identical** APK (same
> 18,367,219 B, same digest — verified from the post-merge run's provenance
> notice), so the candidate is downloadable from
> <https://github.com/99ggprooo00-code/DHUN/releases/tag/test>. CI is green (PR CI
> **37389109897**, Build APK **37389110248**); nothing here is device-verified
> yet. **For this retest, run steps 19–22 below and keep the
> steps 1–18 results you already have** (the parser is untouched).
>
> **The rolling release carries installation files again (PR #122 merged as
> `5e664c1`, 2026-10-06).** The `msi` job had been red in **every** test-release
> run since 2026-10-05T16:56:46Z: the rolling `test` release was a **Draft** and
> the job's read-scoped token cannot read one — and because `publish` has
> `needs: [apk, msi]`, the only job that could republish the release was skipped
> with it (run **37403248318**: `msi` failed → `publish` skipped). PR #122 grades
> an unreadable baseline into a *skip* (`MSI install-over SKIPPED` —
> build-verified only, never a pass) and has `publish` re-assert `--draft=false`
> and prove `isDraft` is `false`. The post-merge run **37406381117** republished
> `test` as a **published pre-release** (target `5e664c1`, four assets) — so the
> Windows column below is runnable again: download `dhun-test.msi` from the
> release, verify its `.sha256` sidecar, install over the existing build. Read the
> `msi` job's annotations first: `MSI upgrade smoke PASS` means the sentinel
> checks really ran; `MSI install-over SKIPPED` means they did not. (The repair
> run **37406381117** itself skipped — the baseline was still the draft when it
> checked — and announced that on the job. The next `main` push, **37408148220**,
> ran the full check: `2.160.1 -> 2.163.1`, both sentinels preserved, no skip.)

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
> publishes. Equal size is not equal bytes. The hashes below come from the
> publisher's provenance annotations; release asset blobs themselves return
> EOF in the sandbox. Verify your downloaded bytes against the matching live
> `.sha256` sidecars before installing.

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

**Build identity — current rolling `test` (last rechecked 2026-10-07).** The
published pre-release targets `f0225f4d68c1dfcfb3dfcb798ca8e3b95aaaafe5`,
was published **2026-10-06T23:56:54Z**, and was produced by test-release run
**37548884077**. These hashes are from publisher provenance annotations, not a
substitute for the release's sidecars:

| Asset | Bytes | Product version | SHA-256 (publisher provenance) |
|---|---:|---|---|
| `dhun-test.apk` | 18,367,219 | APK | `21a5fe862b0c948fbc038417e156310e9eaab74bf9ea2f59214b9807f8c9cc2c` |
| `dhun-test.msi` | 112,947,200 | 2.172.1 | `c27175cecca8cc071364f76704e67faa04ec290d1afd58710b48ff3643fe17d6` |

The latest MSI run checked the full upgrade from 2.171.1 (baseline SHA-256
`353cfa107a61114890386696d012e61f5dd6d562f7aa27f59428a25088244020`) to
2.172.1, with userdata/cache sentinels preserved, the future-upgrade guard and
uninstall smoke passing, and `buildOnly=false`. The MSI published from
`b1dba0c` was 2.171.1 with that baseline hash; the latest 2.172.1 MSI belongs to
`f0225f4`. The APK hash is unchanged between the two because the `f0225f4`
commit changes only the README after `b1dba0c`.

> **The `.sha256` sidecar on the current release is authoritative for your
> download.** The `test` tag is replaced on every `main` push. If it moves after
> this check, verify the target and compare your downloaded file to that new
> release's matching sidecar. Do not infer identity from equal byte sizes or a
> digest from an older publish. If you cannot verify both sidecars, stop before
> installing and report the computed and expected hashes.

## 2. Install over the top

- **Android:** open the APK, allow "install unknown apps" if prompted — or
  `adb install -r dhun-test.apk`
- **Windows:** run the MSI; it upgrades in place.

**Do not uninstall** — that wipes your data and changes what you're testing.

**Note down:** ☐ installed over existing build, data intact

---

## 3. The walkthrough — 18 steps (plus steps 19–22 for the PR #121 slice)

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

### Fix 4 — the two Full Player surfaces (PR #121 candidate)

**Step 19 — the playlist picker (look)**
- **Do:** play any track → ⋮ → `Add to playlist`.
- **Expect:** the **same compact frosted family as the ⋮ menu** (track's blurred
  artwork behind it, thumbnail + title + artist header), one caption line, then
  one row per playlist with the name and `N tracks` under it and a small `Open`
  at the right, then a `New playlist` row. **No** Material-style outlined box, no
  giant empty list area, no Close/Cancel button row. The card is roughly the ⋮
  menu's width and only as tall as what it holds — with one playlist it should be
  visibly *shorter* than before, and it must never look like a full-height slab.
- ☐ pass ☐ fail → what it looked like: ____________________
- **Screenshot on fail** (this is the step the "oversized box" report was about).

**Step 20 — the playlist picker (behaves)**
- **Do:** in that picker, tap `New playlist`; type a name; use the keyboard's
  Done action (or `Create & add`).
- **Expect:** the text appears in a single-line rounded field with an accent
  caret; the name box grows only as tall as one line; `Back` returns to the
  list; `Create & add` creates the playlist, adds the track and closes the
  dialog; an empty name shows `Name cannot be empty` under the field instead of
  creating anything. Then reopen the picker: the new playlist is listed with the
  track count and its `Open` breadcrumb goes to the playlist page.
- ☐ pass ☐ fail → ____________________

**Step 21 — the panel's swipe (the gesture report)**
- **Do:** full player → the queue glyph (≡♪). Then, using the **grab pill / title
  band at the top of the panel** (not the list), drag downward slowly and release
  after a long drag. Reopen, and drag a short distance (≈20dp) and release.
  Finally reopen and drag down on the **list rows** themselves.
- **Expect:** while the finger is down the whole panel follows it, with the
  player content moving down by the same amount (no gap ever opens between the
  panel and the player above it); releasing after a long drag closes the panel;
  a short drag snaps it back open; dragging on the list rows scrolls the list
  instead (it does **not** close the panel). Reopen once more and close with the
  ✕ in the panel header, to confirm the old exit still works.
- ☐ follows the finger ☐ commits on a long drag ☐ snaps back on a short drag
  ☐ list drag still scrolls ☐ ✕ still closes → failures: ____________________

**Step 22 — same defect class, the Library/playlist dialogs**
- **Do:** Library → Playlists → `+ New playlist`; and rename a local playlist
  (⋮ on the playlist → `Rename`).
- **Expect:** the name field is the same DHUN box as step 20 (no Material
  outlined field), and neither dialog lets the page underneath read through its
  text (the card is opaque). The delete/clear confirm dialogs too.
- ☐ pass ☐ fail → ____________________

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
STEPS 19-22: pass / fail -> <list the failing numbers only>
  (e.g. "all pass except 5")

WINDOWS FOLLOW-UP (if tested):
  winver build / VLC version:
  Installed DHUN version; pre-upgrade MSI SHA / post-upgrade MSI SHA:
  FullPlayer collapse (Escape/button) -> pages interactive? dock is 72 dp?
  Space outside text focus -> works? Search still types spaces?
  Jump List tasks after playback -> shown/missing; attach `jump list:` log lines

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