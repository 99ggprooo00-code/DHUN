# 35 — Background blur, dynamic colours and surface colours: on-device validation

> **Status: OPEN.** No Android device, emulator or interactive Windows session has
> been available to the agent that wrote this feature, so **none of the checks
> below has been performed**. CI proves the code compiles and its unit tests pass;
> it says nothing about how the app looks. This page is the procedure for the
> person who *can* look — it is written so it can be followed without reading any
> code.
>
> The model under test (2026-10-30): **two independent switches** —
> *Background blur* and *Dynamic colours* — plus **one colour per interface
> surface**. See `docs/design/glass-system.md` §3a.

## 0. What is proven, and what is not

| Question | Answer | Evidence |
| --- | --- | --- |
| Does it compile for Android and Windows? | Yes | CI `build`, `apk`, `msi` jobs |
| Do "blur off removes the artwork", the two-switch matrix and per-surface colours hold in code? | Yes | `BackgroundSettingsTest` (JVM) |
| Does every artwork layer gate on the blur switch? | Yes, by static gate | `scripts/test_design_system.py` |
| Does "blur off" really remove every thumbnail on a screen? | **Unknown** | needs a screen |
| Do the blooms look natural behind real covers? | **Unknown** | needs a screen |
| Is the navigation bar's own colour readable next to the dock? | **Unknown** | needs a screen |
| Are settings readable over a bright cover? | **Unknown** | needs a screen |
| Are track-to-track colour changes smooth? | **Unknown** | needs a screen |
| Does Android 8–11 behave sensibly with no real blur? | **Unknown** | needs a screen |

Do not mark anything below PASS without having seen it. A screenshot of a failure
is worth more than a paragraph of description.

## 1. Getting a build onto the device

### Android

1. Open the pull request (or the branch) on GitHub and go to **Checks**.
2. Open the **`apk`** job for the commit you want to test, scroll to
   **Artifacts**, and download **`apk`** (a zip containing `dhun-test.apk`). The
   separate **Build APK** workflow publishes the same thing as **`debug-apk`**.
   Artifacts expire after 14 days, so use a recent run.
3. Unzip, then install either with a cable (`adb install -r dhun-test.apk`) or by
   copying the `.apk` to the phone and tapping it.
4. Record the file name and `sha256sum dhun-test.apk` in §3 — a PASS without a
   build identity is not evidence.

If installation is refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall the
old DHUN first (that also erases saved settings — note that you did it).

### Windows

1. In the same Checks list, open the **`msi`** job and download **`msi`**.
2. Run it (Windows warns about an unknown publisher; the package uses the project's
   test certificate).
3. Record the MSI `ProductVersion` (installer, and the app's About panel).

## 2. Where the controls are

**Library → Settings (top right) → Appearance → Backgrounds.**

In order:

- **Background blur** — the artwork switch.
  - On: "The playing track's artwork is used as a blurred background, tinted with
    each surface's colour."
  - Off: "No artwork behind the app at all. Every surface uses its own solid
    colour."
- **Adaptive blur** — greyed out while Background blur is off ("Paused while
  Background blur is off. Your choice is remembered.").
- **Dynamic colours** — "Surface colours come from the playing track's artwork.
  Works with or without background blur."
- **Surface colours** — one row each for **Main pages**, **Navigation bar**,
  **Miniplayer**, **Full player**. Each row shows a colour preview and a line like
  "From the current artwork · artwork behind it", "Your colour · solid colour" or
  "DHUN default · blur paused".
- Tapping a row opens that surface's dialog: preview strip, "Showing: …", a
  **Background blur here** switch, a **Take colour from artwork** switch, the
  colour swatches (**DHUN default** plus Violet, Azure, Jade, Amber, Rose, Cyan),
  and **Restore defaults**.
- **Blur strength** lives in the **Backdrop detail** group below.

Play something first: with no track there is no artwork colour to judge.

## 3. Environment record — fill this in

| Field | Android | Windows |
| --- | --- | --- |
| Device model | | |
| OS version / API level | | |
| Build installed (file name) | | |
| Build commit (from About) | | |
| Date + local time | | |
| Supports real blur? (Android 12 / API 31+) | | |

## 4. The checks

For each: do it, then write **PASS / FAIL / not judged** plus one sentence and, on
FAIL, a screenshot name. "Not judged" is a useful answer; a guess is not.

### Check 1 — Blur OFF genuinely removes the thumbnail

This is the check that decides whether the switch means anything.

1. Play a track with a recognisable cover. Note the page backdrop.
2. Turn **Background blur** off.
3. Look at **Home**, **Search**, **Library**, the **Miniplayer**, the **Full
   player**, the track **overflow menu** (long-press/⋮ on a track) and a
   **dialog** (Settings itself).
4. Repeat with the device in the light theme.

Passes if: no cover, no cropped or dimmed fragment of a cover, and no grey slab
of it is visible anywhere; surfaces are flat coloured glass.
Fails if: any surface still shows the artwork — sharp, dimmed, washed or as a
blurred remnant — or the screen turns plain black/white with no colour at all.

### Check 2 — The four combinations

Set a distinctive colour on **Main pages** first (for example Jade), then visit
each combination and confirm what you see:

| Background blur | Dynamic colours | Expect on the pages |
| --- | --- | --- |
| off | off | solid Jade-tinted surface, **no artwork at all** |
| off | on | surface coloured from the cover, **still no artwork** |
| on | off | blurred cover, tinted Jade |
| on | on | blurred cover, coloured from the cover |

Passes if all four are visibly different and match the table.
Fails if "off + on" looks identical to "off + off", or "on + off" ignores the
picked colour.

### Check 3 — One colour per surface, independently

1. Give the four surfaces four different colours: Main pages **Jade**,
   Navigation bar **Violet**, Miniplayer **Amber**, Full player **Rose**.
2. Turn **Dynamic colours** off so the picks are what you see.
3. Visit each surface. Then change only the navigation bar's colour to **Azure**
   and check again.

Passes if: each surface shows its own colour; the tab bar/rail is visibly its own
colour rather than inheriting the dock; changing one surface leaves the others
untouched.
Fails if: two surfaces always share a colour, changing one changes another, or the
navigation bar shows no colour of its own.

### Check 4 — Palette transitions

1. Queue two tracks whose covers are very different in hue (for example warm
   orange next to cool blue).
2. Skip back and forth several times, watching the background, with Dynamic
   colours on.

Passes if: the colour fades from one to the other in roughly half a second, with
no flash to grey/black and no jump.
Fails if: you see a flicker, a snap, or an intermediate colour belonging to
neither track.

### Check 5 — Settings readability and live preview

1. With a **bright, busy** cover playing, open **Settings → Appearance**.
2. Read: every switch subtitle, each surface row's "Showing: …" line, the swatch
   labels, and the confirm dialog text. Open one surface dialog and watch its
   preview strip while the track changes.
3. Repeat with a **very dark** cover, and in the **light theme**.

Passes if: everything stays comfortably readable, disabled rows look deliberately
disabled, and the preview follows the track.
Fails if: any label washes into the background, the selected swatch is ambiguous,
or the preview disagrees with what the surface actually paints.

### Check 6 — Android 8–11 (no real blur)

Needs an **API 30 or older** device/emulator (Android Studio → Device Manager →
system image API 30).

1. Play a track, go to Home.
2. Confirm the cover is dimmed by the veil but **not** frosted, and that the
   Settings page says "This device cannot blur artwork…".
3. Toggle every switch, change surface colours, restart the app.

Passes if: the caption appears, nothing frosts, the switches and colours save and
restore, and nothing crashes or goes blank.
Fails if: an unblurred and undimmed cover sits under text, the explanation is
missing, or settings do not survive a restart.

### Check 7 — Layer ordering

With a track playing, blur on and Dynamic colours on, inspect from the back:

1. Page colour, 2. blurred cover, 3. veil/scrim, 4. cards, rows and text,
5. the miniplayer dock above the tab bar, 6. the full player (must cover the tab
bar and the dock completely), 7. a track menu opened first from Home and then from
inside the full player.

Passes if: each layer is visible and nothing is painted behind something that
should be under it; the menu is fully above the dock and content; the menu follows
the surface it was opened over.
Fails if: a veil/tint is drawn on top of text, the dock is clipped by the tab bar,
a menu appears behind a surface, or a menu opened in the player is coloured like a
page.

### Record your results

| Check | Result | Note | Screenshot |
| --- | --- | --- | --- |
| 1. Blur off removes the thumbnail | | | |
| 2. Four combinations | | | |
| 3. One colour per surface | | | |
| 4. Palette transitions | | | |
| 5. Settings readability + preview | | | |
| 6. Pre-Android-12 fallback | | | |
| 7. Layer ordering | | | |

## 5. Known limitations to confirm rather than "find"

Documented trade-offs, not bugs. Each has a symptom to look for and a condition
under which it would deserve code.

| Limitation | What you may notice | Becomes a bug if… |
| --- | --- | --- |
| Colours derive from a hash of the artwork URL, not sampled pixels (one image pipeline by design) | Two different covers can land in a similar colour family; a cover's glow need not match its dominant colour | The mismatch is obvious on most covers |
| The adaptive band (70–100 %) was reasoned, not profiled | Bright/busy covers keep full frost, dark/calm ones slightly less | The difference is invisible, or songs look sharp vs. smeared |
| A failed extraction falls back to the surface's colour | A track with unusable artwork shows your colour / the default | The default flashes on tracks that clearly have artwork |
| Solid surfaces use a stronger tint (0.55 vs 0.30) with the same luminance clamp | Picked colours read as deep coloured surfaces, not bright ones | Colours look like mud, or text contrast visibly degrades |
| The colour picker is named swatches, not a wheel | You cannot choose an arbitrary hex | The six hues feel too limiting in normal use |
| Layer order is reasoned from the composition, never seen | See Check 7 | Any layer is visibly in the wrong place |
| Version-1 installs lose per-tab colours | Home/Search/Library keep one colour after upgrading | You relied on Home and Search having *different* colours |

## 6. What was verified without a device (this session)

- **Unit tests** cover every cell of the blur × colour matrix on all four
  surfaces, per-surface independence, one surface opting out of artwork colour,
  colour with the artwork removed, adaptive bounds, the strength floor, the
  luminance band for solid and blurred palettes in both themes, the colour codec,
  persistence across a restart, both migrations and an unwritable database.
- **Design gates** (`scripts/test_design_system.py`): the two features may not
  read each other's switches; every artwork layer must gate on the blur switch
  (`paintsArtwork` / `showsArtwork`); `NowPlayingBackdrop` must return *before*
  building its image request; the four surfaces must each be wired to their own
  component; the settings screen must not expose palette layers.
- **Audited, not observed:** one artwork-seed writer in the shell, palette
  animation that never passes through the default on a track change, per-surface
  reads in MiniPlayer/FullPlayer/navigation, and the pre-API-31 branch that skips
  `Modifier.blur` and keeps the veil.

None of that substitutes for §4.
