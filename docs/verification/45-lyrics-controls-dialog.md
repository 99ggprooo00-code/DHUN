# 45 — Lyrics Controls dialog refresh

Date: 2026-11-07. Work branch: `arena/c0cc2714-dhun`. Review PR: #222.

## Scope and behavior

This is a focused refresh of the existing Lyrics Controls dialog only. The Full
Player, lyrics-reading surface, and unrelated settings dialogs were not
redesigned. The dialog uses the current track's blurred artwork with DHUN's
lyrics veil, theme-aware text/control colors, a compact width ceiling, and a
scrollable body whose height respects the available dialog viewport.

| Control | Existing behavior retained / route |
|---|---|
| Track subtitle | Shows the current title and artist; the song title is prominent and ellipsized on narrow widths. |
| Romanized line + mode | Uses the existing persisted `LyricsRomanizationController` settings. The dialog keeps the selected mode visible when the toggle is off; other settings surfaces keep their previous conditional mode row. |
| Lyrics provider | Retains the existing provider list, current selection, and preference-change callback; the selected provider is displayed in the full-width selector. |
| Edit | Opens the existing local TTML paste/preview/choose flow with the import section expanded. This is not a new line-by-line lyric editor and it does not read the clipboard or contact a provider during preview. |
| Refetch | Calls `PlayerViewModel.refreshLyrics()` for the current track. |
| Search | Opens the existing match picker and starts its default artist/title query through `LyricsMatchController`. |
| Find lyrics matches | Retains a separate full-width entry to the existing match picker without auto-starting a search. |
| Offset | Replaces Earlier/Later buttons with a discrete slider while preserving the stored per-track range (±10,000 ms), 100 ms precision, repository persistence, and lyric-seek timing path. The requested −5s / 0 / +5s labels are placed at their actual quarter / midpoint / three-quarter positions on that preserved range. A visible Reset action calls the existing reset API. |
| Dismissal | Close, outside tap, and system back continue to use the dialog's existing dismissal callback. |

The glass artwork layer honors DHUN's blur preference and platform capability.
Platforms without real-time blur retain the existing dimmed-artwork fallback;
that fallback was not validated on a physical Android version in this task.

## Validation record

| Environment / check | Result | Notes |
|---|---|---|
| `git diff --check` | PASS | Passed on the final source/test changes. |
| Local Gradle compilation/tests | NOT RUN | Installed and verified Temurin JDK 17.0.9 under `~/.cache/toolchain/jdk4py`. Gradle 8.14.2 is not cached; the wrapper points to `services.gradle.org`, which is outside this sandbox's allowed outbound hosts, so local Gradle compilation/tests could not be started. The corresponding GitHub Actions checks did run successfully. |
| GitHub Actions JVM/Android CI | PASS | PR CI run [42372994634](https://github.com/99ggprooo00-code/DHUN/actions/runs/42372994634): `:shared:jvmTest` 848 tests, `:app-desktop:jvmTest` 53 tests, Android Robolectric 169 tests, playback-probe 4 tests; all reported 0 failures/errors. APK and MSI jobs also passed in [run 42372994441](https://github.com/99ggprooo00-code/DHUN/actions/runs/42372994441). |
| Slider policy test | PASS IN CI | `LyricsOffsetSliderPolicyTest` is included in the passing `:shared:jvmTest` run; it covers bounds, 100 ms snapping, marker fractions, and offset labels. |
| Compose/Skia visual and action test | PASS; frames inspected | Visual evidence run [42372994541](https://github.com/99ggprooo00-code/DHUN/actions/runs/42372994541) reports 7 tests, 0 failures/errors, and 66 total evidence images. It includes `lyrics-controls-dark.png` and `lyrics-controls-light.png`; both were fetched from the evidence manifest and inspected. The frames show the current title/artist, selected provider, romanization mode, action row, offset readout/Reset/−5s/0/+5s markers, full-width Find Matches action, and artwork-backed surface in both themes. Manifest blob: `e68f7bff1562e2bf965ecd2acb06a4784a24eb33`. |
| Python packaging/fixture helper tests | PASS LOCALLY | `python3 -m unittest discover -s scripts -p 'test_*.py'`: 521 tests passed. This includes the Kotlin/web icon parity check. |
| Web icon sync/tests | PASS LOCALLY | Ran `npm run gen:icons`, `npm run check:icons`, and `npm test` in `app-web`; icon mirror is current and all 340 Node tests passed. |
| Android emulator | NOT TESTED | No emulator was available in this environment. |
| Physical Android device | NOT TESTED | No device was available; Compose/Skia evidence is not device acceptance. |
