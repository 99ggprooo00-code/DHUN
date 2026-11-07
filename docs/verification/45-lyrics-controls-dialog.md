# 45 — Lyrics Controls dialog refresh

Date: 2026-11-07. Work branch: `arena/c0cc2714-dhun`. Review PR: #222.

## Scope and behavior

This is a focused refresh of the existing Lyrics Controls dialog only. The Full
Player, lyrics-reading surface, and unrelated settings dialogs were not
redesigned. The dialog now uses the current track's blurred artwork with DHUN's
lyrics veil, theme-aware text/control colors, a compact width ceiling, and a
scrollable body whose height also respects the available dialog viewport.

| Control | Existing behavior retained / route |
|---|---|
| Track subtitle | Shows the current title and artist. |
| Romanized line + mode | Uses the existing persisted `LyricsRomanizationController` settings. The dialog keeps the selected mode visible even when the romanized-line toggle is off; other settings surfaces keep their previous conditional mode row. |
| Lyrics provider | Retains the existing provider list, current selection, and preference-change callback. |
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
| `git diff --check` | PASS | No whitespace errors in the working diff. |
| Local Gradle compilation/tests | NOT RUN | `./gradlew :shared:compileKotlinJvm :app-desktop:compileKotlinJvm` could not start: this sandbox has no `java` executable and `JAVA_HOME` is unset. |
| Slider policy test | ADDED, NOT RUN LOCALLY | `LyricsOffsetSliderPolicyTest` covers preserved range/step precision, marker positions, and value labels. |
| Compose/Skia visual and action test | ADDED, NOT RUN LOCALLY | `VisualAppEvidenceTest.renderLyricsControlsDialogInBothThemesAndExerciseItsActions` renders both themes and exercises refetch, local-edit entry, and automatic match search. Expected generated frames: `lyrics-controls-dark.png` and `lyrics-controls-light.png` under `app-desktop/build/outputs/visual-evidence/`. |
| Android emulator | NOT TESTED | No emulator was available in this environment. |
| Physical Android device | NOT TESTED | No device was available; Compose/Skia evidence is not device acceptance. |

Update this record with the actual PR CI results and any inspectable rendered
frames before claiming those checks passed.
