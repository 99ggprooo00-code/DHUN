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
> **S3 hardware round 1 — partial initial report, 2026-10-05.** The first
> report had Android failures at steps **1, 6, 7, 9**; Windows core steps
> **10–13** were reported pass. Step 13 was partial (`Go to album` not found),
> step 16 misunderstood, step 18 unreported; device/build identity was absent.
> Four screenshots were not mapped to steps. The rolling `test` at that point
> was #119, not the parser candidate. Historical details: `14-release.md`.
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

**Build identity** — rolling `test` is a **published pre-release**:
published **2026-10-06T03:20:40Z**, target
**`a9204c59fdc2a44e132ad903df2d556e9c63530c`**, assets uploaded 03:20:35Z, MSI
ProductVersion **2.163.1** (test-release **37408148220**, the first `main` push
after the repair — its `msi` job ran the **full** install-over check with no
skip). The digests below come from that run's provenance notices — the sandbox
cannot download release assets (Azure-blob EOF), so they are **not**
independently re-verified here; verify your own download against the release
`.sha256` sidecar. **The release is replaced on every `main` push and the MSI hash
moves with it** (its ProductVersion counter advances); the APK hash moves only
when the app code changes.

| Asset | Bytes | SHA-256 (from run 37408148220's provenance notices) |
|---|---:|---|
| `dhun-test.apk` | 18,367,219 | `21a5fe862b0c948fbc038417e156310e9eaab74bf9ea2f59214b9807f8c9cc2c` |
| `dhun-test.msi` | 112,947,200 | `12745f81394a357c266c8453903e70aa78c2f7b29450d97e5c8348dfc45f5f8e` (2.163.1) |

The APK digest equals the PR #120 candidate's `c351341e…` **and** the PR #121
candidate's `21a5fe86…`: those heads' only deltas were docs and CI scripts, so the
APK rebuilt byte-identically — that is reproducibility evidence, and it means the
release APK is the same bytes as the retest candidate. The MSI digest is new
every publish.

The values previously shown here — APK
`8276e0298c0df6d22084e07d8ff3477ab22550e586d4daa41de43e60aa8de770` and MSI
`4e28c551db2834c699351b3eb1c4d03c96dc46d536156242203845ee28d46b6e` — belong
to the earlier **`16ad2e5`** publish; they are historical only. Matching sizes do
not prove matching bytes, and an unchanged APK digest does not mean the MSI one
is unchanged.

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