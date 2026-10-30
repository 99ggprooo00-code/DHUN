# 35 — Background blur, dynamic blur and dynamic colours: on-device validation

> **Status: OPEN.** No Android device, emulator or interactive Windows session
> was available to the agent that wrote this feature, so **none of the six
> visual checks below has been performed**. CI proves the code compiles and its
> unit tests pass; it says nothing about how the app looks. This page is the
> procedure for the person who *can* look — it is written so it can be followed
> without reading any code.

## 0. What is proven, and what is not

| Question | Answer | Evidence |
| --- | --- | --- |
| Does it compile for Android and Windows? | Yes | CI `build`, `apk`, `msi` jobs |
| Do the preference, precedence, migration and persistence rules hold? | Yes | `BackgroundSettingsTest` (JVM, 500+ tests in the suite) |
| Does every frosted surface ask its own section for the radius? | Yes, by static gate | `scripts/test_design_system.py` |
| Do the blooms look natural behind real covers? | **Unknown** | needs a screen |
| Are the settings readable over a bright cover? | **Unknown** | needs a screen |
| Do the five sections actually differ on screen? | **Unknown** | needs a screen |
| Are track-to-track palette changes smooth? | **Unknown** | needs a screen |
| Does Android 8–11 behave sensibly with no real blur? | **Unknown** | needs a screen |
| Are the layers in the right order? | Reasoned from the code, **not seen** | needs a screen |

Do not mark anything below PASS without having seen it. A screenshot of a
failure is worth more than a paragraph of description.

## 1. Getting a build onto the device

### Android

1. Open the pull request (or the branch) on GitHub and go to the **Checks** tab.
2. Open the **`apk`** job that ran on the commit you want to test, scroll to
   **Artifacts**, and download **`apk`** (a `.zip` containing `dhun-test.apk`).
   The separate **Build APK** workflow publishes the same thing as
   **`debug-apk`**. You must be signed in to GitHub; artifacts expire after 14
   days, so use a recent run.
3. Unzip it, then install in one of two ways:
   - with a USB cable: `adb install -r dhun-test.apk`
   - without a cable: copy the `.apk` to the phone, tap it, and allow your file
     manager to install unknown apps.
4. Write down the file name and its checksum (`sha256sum dhun-test.apk`) in the
   environment table in §3 — a PASS without a build identity is not evidence.

The debug build installs next to a release build only if the application id
differs; if installation is refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
uninstall the old DHUN first (that also erases saved settings, so note that you
did it).

### Windows

1. In the same Checks list, open the **`msi`** job and download the **`msi`**
   artifact (`dhun-test.msi`).
2. Run it. Windows may warn about an unknown publisher; the package is signed
   with the project's test certificate, not a commercial one.
3. Record the MSI `ProductVersion` (shown in the installer and in the app's
   About panel).

## 2. Where the controls are

**Library → Settings (top right) → Appearance → Backgrounds.**

You will see, in order:

- **Background blur** — the master switch.
- **Dynamic blur** — greyed out while the master is off, with the caption
  "Paused while Background blur is off. Your choice is remembered."
- **Dynamic colors** — independent of both blur switches.
- **Section backgrounds** — one row each for Home, Search, Library, Miniplayer
  and Full player. Tapping a row opens that section's dialog: a preview strip,
  "Showing: …" (why it looks like that), blur / dynamic blur / artwork-colours
  switches, three colour layers you can pick, and restore-defaults.
- **Blur strength** lives in the **Backdrop detail** group below.

Playing something first makes all of this easier to judge: with no track, there
is no artwork colour to see.

## 3. Environment record — fill this in

| Field | Android | Windows |
| --- | --- | --- |
| Device model | | |
| OS version / API level | | |
| Build installed (file name) | | |
| Build commit (from About) | | |
| Date + local time | | |
| Does this device support real blur? (Android 12 / API 31 or newer) | | |

## 4. The six checks

For each check: do it, then write **PASS / FAIL** plus one sentence and (on
FAIL) a screenshot name. If you cannot judge something, write "not judged" —
that is a useful answer, a guess is not.

### Check 1 — Artwork bloom quality

Pick three tracks with very different covers: one **dark**, one **near-white or
pale**, one **strongly saturated** (deep red, green or blue).

1. Play the track, go to **Home**.
2. Look at the colour behind the shelves. There should be a soft glow, strongest
   near the top-left corner, with the page's own background colour still
   visible through it.
3. Repeat for all three covers.

Passes if: the glow reads as *light in the room*, not a painted wall; the three
covers look different from each other; no grey rectangle, banding or visible
blur edge appears where the blurred cover ends.
Fails if: colours are harsh, the screen looks flat-tinted, there is a seam, or
text on top becomes harder to read than with no track playing.

### Check 2 — Settings readability

1. With a **bright, busy** cover playing, open **Settings → Appearance**.
2. Read: every group title, every switch subtitle, the "Showing: …" line in the
   per-section dialog, the colour-layer labels, and the confirm dialog text.
3. Repeat with a **very dark** cover.
4. Repeat in the **light theme** (Appearance → theme).

Passes if: all of the above stays comfortably readable, disabled rows still look
deliberately disabled rather than invisible, and the theme and accent pickers
remain distinguishable.
Fails if: any label washes into the background, a selected/unselected state
becomes ambiguous, or the palette dialog's preview strip makes its own text
unreadable.

### Check 3 — Section-specific blur

1. Play a track. In **Section backgrounds → Home**, turn **Blur on this
   surface** off and close the dialog.
2. Go to **Home**: it should now be sharp (the cover visible, still dimmed by the
   veil). Go to **Search** and **Library**: both should still be frosted.
3. From **Library**, open a playlist, then an album. Those pages paint their own
   cover backdrop; they must match **Library's** setting (frosted), not Home's.
4. Turn **Library's** blur off too and re-open the album: it should now be sharp.
5. Turn the master **Background blur** off: everything becomes sharp, and the
   per-section rows show as held by the global switch. Turn the master back on:
   Home and Library return to sharp (their own choice), Search to frosted.
6. Repeat for **Miniplayer** (the dock above the tabs) and **Full player** (tap
   the dock to expand).

Passes if: exactly the surface you changed changes; the others do not; re-enabling
the master restores each section's own choice.
Fails if: changing one section changes another, a detail page follows a
different section than the tab it was opened from, or a section's choice is lost
across a master toggle.

### Check 4 — Palette transitions

1. Queue two tracks whose covers are very different in hue (for example a warm
   orange cover next to a cool blue one).
2. Skip back and forth between them several times while watching the background.

Passes if: the background fades from one palette to the other over roughly half
a second, with no flash to grey/black, no jump, and no visible intermediate
colour that belongs to neither track.
Fails if: you see a flicker, a snap, a sudden white or black frame, or the
background briefly falls back to the default palette before the new colour
arrives.

### Check 5 — Android 8–11 (no real blur)

Needs a device or emulator on **API 30 or older** (in Android Studio: Device
Manager → Create device → system image API 30 → run).

1. Play a track and go to Home.
2. Confirm the cover is dimmed by the veil but **not** frosted.
3. Open **Settings → Appearance**: the caption "This device cannot render real
   blur. Your choices are saved, and the cover is dimmed instead of frosted."
   must be present.
4. Toggle every blur switch, change sections, and restart the app.

Passes if: the caption appears, nothing frosts, the switches still save and
restore, and nothing crashes or shows a blank/black background.
Fails if: the app renders an unblurred *and* undimmed cover under text, shows no
explanation, or the settings do not survive a restart.

### Check 6 — Layer ordering

With a track playing, blur on and dynamic colours on, inspect each layer from the
back forwards:

1. Page colour wash (the base tint) — behind everything.
2. Blurred cover.
3. The veil/scrim that dims it.
4. Cards, rows and text.
5. The miniplayer dock above the tab bar.
6. The full player, which must cover the tab bar and the dock completely.
7. A floating menu: long-press / overflow a track and open its menu — first from
   Home, then from inside the full player.

Passes if: each layer above is visible and none is painted behind something that
should be under it; the menu is fully above the dock and the content; the menu
frosts with the switches of the surface it was opened over.
Fails if: the veil or a tint is drawn on top of text, the dock is clipped by the
tab bar, a menu appears behind a surface, or a menu opened in the player is
frosted like a page.

### Record your results

| Check | Result (PASS/FAIL/not judged) | Note | Screenshot |
| --- | --- | --- | --- |
| 1. Bloom quality | | | |
| 2. Settings readability | | | |
| 3. Section-specific blur | | | |
| 4. Palette transitions | | | |
| 5. Pre-Android-12 fallback | | | |
| 6. Layer ordering | | | |

## 5. Known limitations you can confirm rather than "find"

These are documented trade-offs, not bugs to report. Each has a symptom you can
look for, and a condition under which it *would* deserve a code change.

| Limitation | What you may notice | Becomes a bug if… |
| --- | --- | --- |
| Colours are derived from a hash of the artwork URL, not sampled from the image (one image pipeline by design) | Two different covers can land in a similar colour family, and a cover's glow need not match its dominant colour | The mismatch is obvious on most covers — then bitmap sampling has to be brought into the same pipeline deliberately, not as a second one |
| The dynamic blur band (70–100 %) was reasoned, not profiled | Bright/busy covers keep the full frost; dark/calm covers are slightly sharper — the difference between two extremes is subtle | On a real screen the difference is invisible (the feature does nothing) or so large that songs look sharp vs. smeared |
| A failed colour extraction falls back to the default palette instead of retrying | A track with unusable artwork shows the DHUN default colours | The default flashes on tracks that clearly *do* have artwork, i.e. the failure is ours, not the image's |
| Layer order is reasoned from the composition, never seen | See Check 6 | Any layer is visibly in the wrong place |
| Readability comes from the scrim and brightness veil, not per-text contrast maths | Very bright or high-frequency artwork can still be busier behind text | A specific label is hard to read in a specific state — pin that state down, then raise the surface opacity rather than the artwork |

## 6. What was verified without a device (this session)

Static audit plus gates, recorded here so the audit is not repeated:

- **Found and fixed:** album, artist and playlist pages painted their backdrop
  with a hardcoded `BackgroundSection.HOME`, so a page opened from Library kept
  Home's blur switches. The section is now inherited from
  `LocalBackgroundSection` on all three, exactly like the dock, the dialogs and
  the player — see `docs/design/glass-system.md` §3a and the new
  `test_page_backdrops_follow_the_page_they_are_composed_on` gate.
- **Re-checked in code:** one artwork seed writer (`DhunAppearance`-style single
  `LaunchedEffect` in the shell), palette crossfades via `animateColorAsState`
  with no path back through the default palette on a track change, per-section
  reads in `MiniPlayer`, `FullPlayer` and the shell, and the pre-API-31 branch
  (`supportsRealtimeBlur`) that skips blur entirely and keeps the veil.

None of that is a substitute for §4.
