# 47 — Screenshot-by-screenshot visual audit

Date: 2026-11-08. Branch: `arena/b3400d42-dhun`; PR #223 remains open.

This record accounts for every supplied Android capture individually. The 17
screenshots in the original list plus two recovered captures make 19 Android
references. The separate `Intended ui.jpg` and `Before vs after .jpg` are the
specific Lyrics Controls target/comparison; `Picsart_26-11-07_17-08-31-279.jpg`
is byte-identical to the comparison image (SHA-256
`675affe846957f16518916ca532eaf2aba8f718f3bad22d8bda8f6f1568d897a`). Uploaded
references are cited by their original filenames and are not copied into Git.

## Why PR #222 / PR #223 did not meet the visual objective

- PR #222 replaced timing buttons with a working discrete slider and added
  callback/element assertions, but it left the title and artist on separate
  rows, the provider selector full-width, Edit/Refetch/Search as three
  independent tonal buttons, Find Matches as a filled full-width button, and
  Reset visible at zero. Its Compose/Skia frames showed those elements; they
  were not a close visual comparison with the supplied intended card.
- PR #223 improved shared glass/confirmation components and several callers,
  but its actual change list did not include `LyricsControlsDialog.kt`; the
  Lyrics Controls hierarchy therefore remained materially unchanged.
- This continuation makes concrete, scoped changes to the Lyrics Controls
  composition and the catalog header, and adds per-reference Compose/Skia
  evidence. It deliberately does not restyle every page or fill the large
  natural empty area in one-item Library states.

## Reference-by-reference record

| # | Supplied Android source and state | Specific issue / decision | Intended change and production files | Matching after frame | Status and limitations |
|---:|---|---|---|---|---|
| 1 | `Screenshot_2026-11-07-20-52-48-617_dev.dhun.android.jpg` — Lyrics Match picker, local TTML paste/preview | Dense editor state, but no independently demonstrated layout defect beyond the already shared dialog treatment; preserve its distinct form/editor hierarchy. | No production change. Keep local-only preview, saved choice, and provider callbacks. | `lyrics-match-picker-dark.png` | Frame visually reviewed; existing Compose/Skia capture. Fixture data and renderer are not the captured Android device. |
| 2 | `Screenshot_2026-11-07-20-52-52-975_dev.dhun.android.jpg` — Lyrics Controls | Current card spends height on split metadata, a full-width provider button, three separate action surfaces, visible Reset at zero, a pill-like slider thumb, and a filled Find Matches button. | One-line title/source metadata; compact glass provider chip; unified translucent action rail; round slider thumb/tick marks; plain Find Matches row; icon Reset only for nonzero offsets. `LyricsControlsDialog.kt`, `LyricsRomanizationControls.kt`, `PlayerTabs.kt`, `DhunControls.kt`. | `lyrics-controls-dark.png`, `lyrics-controls-light.png`, `lyrics-controls-compact-large-text-dark.png` | Implemented; dark/light and 360×640, 1.25× frames visually reviewed. ±10,000 ms storage/adjustment range and 100 ms steps are retained. Because the target labels −5s/+5s while the existing range is ±10s, those labels remain at their truthful quarter/three-quarter positions rather than falsely labeling the endpoints. This is an explicit fidelity tradeoff, not device validation. |
| 3 | `Screenshot_2026-11-07-20-52-56-715_dev.dhun.android.jpg` — Lyrics Match picker while searching/parsing/saving, with a prior LRCLIB choice | This is a transient provider/search state with an existing per-song choice, distinct from the local TTML form. A static default picker would omit the busy state and saved-choice context. | Keep the existing Search callback and picker. Seed a deterministic saved match and gate provider responses to capture the in-progress state without changing app behavior. `VisualAppEvidenceTest.kt` only. | `lyrics-match-searching-dark.png`, `lyrics-match-searching-light.png` | Dark/light frames visually reviewed; recorded provider fixture and synthetic track data. Not a live-network or device capture. |
| 4 | `Screenshot_2026-11-07-21-02-42-369_dev.dhun.android.jpg` — Full Player with the Queue sheet raised | The queue sheet, playback controls, and queue rows are already distinct and legible; no concrete screenshot-specific defect justifies another layout change. | No production change; preserve queue interactions and sheet travel. | `app-player-queue-dark.png` | Reviewed; existing Compose/Skia evidence. Queue data and Android system bars differ from the reference capture. |
| 5 | `Screenshot_2026-11-07-21-02-45-010_dev.dhun.android.jpg` — Full Player / Related sheet | Separate Related surface, tabs, radio action, and result rows are already present; no specific mismatch warrants cosmetic churn. | No production change; preserve radio/queue behavior. | `app-player-related-dark.png` | Reviewed; existing Compose/Skia evidence. Related results come from the recorded provider fixture. |
| 6 | `Screenshot_2026-11-07-21-03-01-081_dev.dhun.android.jpg` — Full Player track actions menu, faint-appearance capture (recovered capture) | The supplied menu appears lower-contrast than the later capture, but the Compose/Skia dialog is already fully legible 100ms after opening. This looks like an Android window/capture transition not reproduced by the Skia scene, not a persistent menu-layout defect. | No production change. Capture the same menu near entry and keep its settled state as a separate reference frame. `VisualAppEvidenceTest.kt` only. | `audit-21-03-01-full-player-track-actions-dark.png` | Frame visually reviewed; it matches the menu state, not the source's transient opacity. Synthetic artwork and Skia do not reproduce Android window animation. |
| 7 | `Screenshot_2026-11-07-21-03-04-102_dev.dhun.android.jpg` — Full Player track actions menu, clearly visible over artwork | Same action menu as #6, now fully visible; no persistent layout mismatch is established. | No production change; retain the existing menu and capture a settled frame separately. `VisualAppEvidenceTest.kt` only. | `audit-21-03-04-track-menu-settled-dark.png` | Frame visually reviewed; it uses the same deterministic track/artwork fixture and is a settled capture, not a substitute for #6. |
| 8 | `Screenshot_2026-11-07-21-03-51-946_dev.dhun.android.jpg` — Library / Liked Songs | Single-item state is naturally sparse. Do not add filler or duplicate empty-state content. | No production change. Add an exact Liked Songs state capture using one saved track. `VisualAppEvidenceTest.kt` only. | `audit-21-03-51-liked-songs.png` | Frame reviewed; generated row metadata is deterministic test data. Sparse layout intentionally remains sparse. The shared mini-player title is sampled mid-marquee in this render; that is a transient animation phase, not a persistent Library layout defect. |
| 9 | `Screenshot_2026-11-07-21-04-02-193_dev.dhun.android.jpg` — Library / History | Eight-play history is already grouped and readable; keep its real content and clear action, with no added filler. | No production change. Add an eight-entry history fixture and capture. `VisualAppEvidenceTest.kt` only. | `audit-21-04-02-history.png` | Frame reviewed; timestamps/context are test data and not device-local history. The shared mini-player title is sampled mid-marquee; its clipped prefix is transient animation, not a History layout change. |
| 10 | `Screenshot_2026-11-07-21-04-04-853_dev.dhun.android.jpg` — Library / Downloads | Preserve the naturally sparse one-download state. The first Compose/Skia frame placed free-space and total-capacity numbers on the same vertical band, making them read together ambiguously at phone width. | Keep the storage summary; align capacity with “On this device” and leave free-space wording on its own lower line. Do not add filler. `LibraryScreen.kt`, `VisualAppEvidenceTest.kt`. | `audit-21-04-04-downloads.png` | Layout correction visually reviewed; total capacity is aligned with its label, and free space remains on its own line. The single downloaded row remains. The shared mini-player title is sampled mid-marquee. Device capacity/path are host-dependent in Skia; exact bytes/device storage are not claimed. |
| 11 | `Screenshot_2026-11-07-21-04-13-459_dev.dhun.android.jpg` — Component Catalog (recovered capture) | “Close” wraps into two lines beside the long catalog title at phone width. | Reserve a minimum action width so the title ellipsizes first; assert one-line Close at 360dp. `ComponentCatalogScreen.kt`, `VisualAppEvidenceTest.kt`. | `component-catalog-header-phone-dark.png` | Close label and narrow-width frame visually reviewed. Compose/Skia only; no Android after-device capture. |
| 12 | `Screenshot_2026-11-07-21-04-23-173_dev.dhun.android.jpg` — About & Legal / Reset settings confirmation | Standard confirmation hierarchy and wording are already legible; preserve its reset-specific copy and callback. | No production change. Capture the real Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-04-23-reset-settings-confirm.png` | Frame visually reviewed; confirmation is not submitted, so no preferences are reset. |
| 13 | `Screenshot_2026-11-07-21-04-32-612_dev.dhun.android.jpg` — Downloads / Clear all confirmation | The one-item confirmation said “all 1 downloaded track” and used plural “them.” Keep the warning specific, but use grammatical singular wording. | Use “the only downloaded track … it” for count 1; retain the plural count wording otherwise. Cancel/Clear actions and deletion scope stay unchanged. `LibraryScreen.kt`, `VisualAppEvidenceTest.kt`. | `audit-21-04-32-clear-downloads-confirm.png` | Final frame visually reviewed: count-one copy reads naturally; regression assertion passed. Cancel is exercised and the in-memory download remains. |
| 14 | `Screenshot_2026-11-07-21-04-38-254_dev.dhun.android.jpg` — History / Clear history confirmation | Existing history-specific warning and confirmation are legible; preserve the history-only scope. | No production change. Capture the actual Library History confirmation. `VisualAppEvidenceTest.kt` only. | `audit-21-04-38-clear-library-history-confirm.png` | Frame visually reviewed; cancellation is exercised; no history is cleared. |
| 15 | `Screenshot_2026-11-07-21-05-23-664_dev.dhun.android.jpg` — Lyrics & Romanization / Clear lyrics cache | The longer explanatory message is specific to lyrics choices/cache; do not replace it with generic confirmation copy. | No production change. Capture the real Settings row/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-05-23-clear-lyrics-cache-confirm.png` | Frame visually reviewed; cancellation is exercised; no cache is cleared. |
| 16 | `Screenshot_2026-11-07-21-05-35-252_dev.dhun.android.jpg` — Country/Region picker | Searchable list and country codes are visible within a bounded dialog; no concrete mismatch found. | No production change; retain the wider list dialog and its search. Capture the actual Settings picker. `VisualAppEvidenceTest.kt` only. | `audit-21-05-35-country-region-picker.png` | Frame visually reviewed; country data is repository test data. Selection is not changed. |
| 17 | `Screenshot_2026-11-07-21-05-44-921_dev.dhun.android.jpg` — Privacy & history / Clear search history | Existing confirmation is appropriately specific and reversible only by the user's choice; no layout change warranted. | No production change. Capture the actual Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-05-44-clear-search-history-confirm.png` | Frame visually reviewed; cancellation is exercised; no search history is cleared. |
| 18 | `Screenshot_2026-11-07-21-05-47-493_dev.dhun.android.jpg` — Privacy & history / Clear play history | Existing confirmation is separate from search history and correctly describes the operation; preserve that distinction. | No production change. Capture the actual Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-05-47-clear-play-history-confirm.png` | Frame visually reviewed; cancellation is exercised; no play history is cleared. |
| 19 | `Screenshot_2026-11-07-21-06-07-650_dev.dhun.android.jpg` — Storage & cache / Clear audio cache | Existing warning is audio-cache-specific and distinct from persistent downloads; no extra visual treatment needed. | No production change. Capture the actual Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-06-07-clear-audio-cache-confirm.png` | Frame visually reviewed; confirmation is not submitted, so no audio cache is cleared. |

## Behavior, theme, and responsive coverage

- Lyrics source/provider selection still writes the existing preference; Edit
  still opens local TTML import/preview; Refetch and Search retain their current
  ViewModel/controller callbacks; Find matches opens the picker without
  automatically searching.
- Timing still uses the existing per-track repository and seek path, supports
  −10,000…+10,000 ms in 100 ms increments, and retains Reset; Reset is shown as
  a 48dp icon target only when the saved offset is nonzero. The UI test drives
  the Slider's accessible `SetProgress` action, checks the persisted readout,
  and resets it; JVM policy/repository tests cover bounds, precision, and
  persistence. No preference, playback, lyrics, or persistence contract was
  reduced.
- The romanization component's compact row is opt-in from Lyrics Controls;
  Settings keeps its existing layout. Translucent DHUN material is retained;
  no generic Material 3 reskin or extra blur layer was added.
- Lyrics Controls has dark/light Compose/Skia captures plus a narrow 360×640
  1.25× font-scale interaction/capture. Other app-state references are captured
  individually at a nominal 360dp phone canvas, with the player/menu cases at
  their existing 412×915 evidence size. Library and Settings frames include the
  shared mini-player's existing `basicMarquee`; its title can be mid-cycle in a
  still image, which is a transient animation phase rather than persistent text
  clipping.

## Validation status

- Final Visual evidence run `42432357921` completed for commit `e1ddab8`:
  13 tests, zero failures/errors, 89 PNGs. Manifest blob
  `02cf6e510fe0112495c1a409e5beb3128b6d3489` records the run and all frames.
  All 89 image SHA-256 values match the previously reviewed run `42427116676`
  for `a60b880`; no rendered frame changed in the Windows workflow fixes.
- All 19 tracker rows map to 22 expected after-frame names in that manifest.
  Twenty-one files are byte-identical to the already SHA-verified and visually
  reviewed `4f733b4` frames; the changed row-13 confirmation frame was fetched,
  hash-checked against the final manifest, and visually reviewed. It shows the
  singular copy correction (SHA-256
  `e0b6bc7fc4203eb90e74f8664a67cb928480aa9c157b730e5c133ae9a0e6dabc`). All 22
  final mapped frames have therefore been reviewed. The earlier failed run
  `42403809166` is superseded.
- The full CI run for application commit `a60b880`, `42427116683`, passed
  1,080 tests (848 shared JVM, 169 Android Robolectric, 4 playback-probe, 59
  desktop JVM; zero failures/errors). Windows beta run `42427116702` for the same
  commit was canceled at the MSI job's 25-minute maximum
  execution time while `Live theme regression — Windows JVM` was still running;
  no Windows regression result was produced, and MSI packaging/install checks
  were skipped. The beta release-plan and APK jobs passed; AAB was skipped by
  workflow configuration.
- Root cause of the Windows timeout was an over-broad platform run: after the
  full app-state visual-evidence suite (89 frames overall) was added, Windows
  Skiko remained in that matrix for more than 34 minutes before MSI packaging.
  The `f2f7928` attempt removed redundant shared tests but still included this
  heavy app matrix; run `42429755177` was superseded while that test step was
  still active, not because of a failing assertion.
- Follow-up `e1ddab8` limits the Windows-specific regression to
  `VisualEvidenceTest` (a small real Skiko primitive render) and
  `LiveAppearanceTest` (live palette observers/concurrent reads). The complete
  app state/screenshot matrix and full shared/desktop tests remain covered by
  canonical Linux CI. The Windows job returns to its 25-minute budget, leaving
  time for MSI packaging and install checks. A workflow-contract test guards
  the focused selection; the local Python suite passes 522 tests.
- On `e1ddab8`, CI run `42432357898` passed 1,080 tests (848 shared JVM, 169
  Android Robolectric, 4 playback-probe, 59 desktop JVM; zero failures/errors).
  Build APK run `42432357893` also passed.
- Windows beta run `42432357981` passed the release plan and APK jobs, focused
  Windows regression (1m25s), MSI packaging/identity, and disposable
  install/userdata verification. The installed Windows evidence job also passed
  and published its artifact. AAB and release publication were skipped by
  workflow configuration. All required checks for `e1ddab8` are green.
- Local `python3 -m unittest discover -s scripts -p 'test_*.py'`: 522 passed.
  `git diff --check` passed. Local Kotlin/Compose tests are unavailable because
  Gradle 8.14.2 is not cached and `services.gradle.org` is blocked by the
  sandbox's outbound-host policy; GitHub Actions is the Kotlin/Compose path.
- These are Compose ImageComposeScene/Skia renders, not Android emulator or
  physical-device validation. No Android after-device capture was performed.
