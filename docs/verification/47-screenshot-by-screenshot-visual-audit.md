# 47 — Screenshot-by-screenshot visual audit

Date: 2026-11-07. Branch: `arena/b3400d42-dhun`; PR #223 remains open.

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
| 1 | `Screenshot_2026-11-07-20-52-48-617_dev.dhun.android.jpg` — Lyrics Match picker, local TTML paste/preview | Dense editor state, but no independently demonstrated layout defect beyond the already shared dialog treatment; preserve its distinct form/editor hierarchy. | No production change. Keep local-only preview, saved choice, and provider callbacks. | `lyrics-match-picker-dark.png` | Audited; existing Compose/Skia frame. Fixture data and renderer are not the captured Android device. |
| 2 | `Screenshot_2026-11-07-20-52-52-975_dev.dhun.android.jpg` — Lyrics Controls | Current card spends height on split metadata, a full-width provider button, three separate action surfaces, visible Reset at zero, a pill-like slider thumb, and a filled Find Matches button. | One-line title/source metadata; compact glass provider chip; unified translucent action rail; round slider thumb/tick marks; plain Find Matches row; icon Reset only for nonzero offsets. `LyricsControlsDialog.kt`, `LyricsRomanizationControls.kt`, `PlayerTabs.kt`, `DhunControls.kt`. | `lyrics-controls-dark.png`, `lyrics-controls-light.png`, `lyrics-controls-compact-large-text-dark.png` | Implemented; both theme frames and a 360×640, 1.25× font-scale frame. ±10,000 ms storage/adjustment range and 100 ms steps are retained. Because the target labels −5s/+5s while the existing range is ±10s, those labels remain at their truthful quarter/three-quarter positions rather than falsely labeling the endpoints. This is an explicit fidelity tradeoff, not device validation. |
| 3 | `Screenshot_2026-11-07-20-52-56-715_dev.dhun.android.jpg` — Full Player / Queue sheet | Dedicated queue sheet and player chrome are already distinct and legible; no concrete screenshot-specific defect justifies another layout change. | No production change; preserve queue interactions and sheet travel. | `app-player-queue-dark.png` | Reviewed; existing Compose/Skia evidence. Queue data and Android system bars differ from the reference capture. |
| 4 | `Screenshot_2026-11-07-21-02-42-369_dev.dhun.android.jpg` — Lyrics Match searching/saving state | This is a transient provider/search state, not the same state as the local TTML editor. It must not be silently represented by a static default picker. | Keep the existing Search callback and picker. Add a controlled provider gate to capture the in-progress state without changing app behavior. `VisualAppEvidenceTest.kt` only. | `lyrics-match-searching-dark.png`, `lyrics-match-searching-light.png` | Evidence capture added; provider is deterministic test data. Not a live-network or device capture. |
| 5 | `Screenshot_2026-11-07-21-02-45-010_dev.dhun.android.jpg` — Full Player / Related sheet | Separate Related surface, tabs, radio action, and result rows are already present; no specific mismatch warrants cosmetic churn. | No production change; preserve radio/queue behavior. | `app-player-related-dark.png` | Reviewed; existing Compose/Skia evidence. Related results come from the recorded provider fixture. |
| 6 | `Screenshot_2026-11-07-21-03-01-081_dev.dhun.android.jpg` — Full Player track action/context overlay (recovered capture) | The screenshot shows the player context menu over artwork; existing menu has its own artwork-backed material and clear action labels. No new mismatch established. | No production change. Add a full-player-over-artwork capture to distinguish this state from the standalone menu. `VisualAppEvidenceTest.kt` only. | `audit-21-03-01-full-player-track-actions-dark.png` | Evidence capture added; artwork is a synthetic fixture, not the original album art/device renderer. |
| 7 | `Screenshot_2026-11-07-21-03-04-102_dev.dhun.android.jpg` — Library / Liked Songs | Single-item state is naturally sparse. Do not add filler or duplicate empty-state content. | No production change. Add an exact Liked Songs state capture using one saved track. `VisualAppEvidenceTest.kt` only. | `audit-21-03-04-liked-songs.png` | Evidence capture added; generated row metadata is deterministic test data. Sparse layout intentionally remains sparse. |
| 8 | `Screenshot_2026-11-07-21-03-51-946_dev.dhun.android.jpg` — Track overflow menu | The compact track menu already uses the shared artwork-backed menu surface and exposes the normal actions; no concrete visual defect. | No production change; retain current menu behavior. | `app-track-menu-dark.png` | Reviewed; existing Compose/Skia frame, not an Android after capture. |
| 9 | `Screenshot_2026-11-07-21-04-02-193_dev.dhun.android.jpg` — Library / Downloads | One downloaded row leaves substantial open space, but that is a true one-item state, not missing UI. Keep the storage summary and avoid filler. | No production change. Add a completed-download fixture and capture this state. `VisualAppEvidenceTest.kt` only. | `audit-21-04-02-downloads.png` | Evidence capture added. Device capacity/path are host-dependent in Skia; exact bytes/device storage are not claimed. |
| 10 | `Screenshot_2026-11-07-21-04-04-853_dev.dhun.android.jpg` — Component Catalog (recovered capture) | “Close” wraps into two lines beside the long catalog title at phone width. | Reserve a minimum action width so the title ellipsizes first; assert one-line Close at 360dp. `ComponentCatalogScreen.kt`, `VisualAppEvidenceTest.kt`. | `component-catalog-header-phone-dark.png` | Implemented; Compose/Skia narrow-width capture. No Android after-device capture. |
| 11 | `Screenshot_2026-11-07-21-04-13-459_dev.dhun.android.jpg` — Library / History | Eight-row history state is already grouped and readable; keep its real content and clear action, with no added filler. | No production change. Add an eight-entry history fixture and capture. `VisualAppEvidenceTest.kt` only. | `audit-21-04-13-history.png` | Evidence capture added; timestamps/context are test data and not device-local history. |
| 12 | `Screenshot_2026-11-07-21-04-23-173_dev.dhun.android.jpg` — About & Legal / Reset settings confirmation | Standard confirmation hierarchy and wording are already legible; preserve its reset-specific copy and callback. | No production change. Capture the real Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-04-23-reset-settings-confirm.png` | Evidence capture added; confirmation is not submitted, so no preferences are reset. |
| 13 | `Screenshot_2026-11-07-21-04-32-612_dev.dhun.android.jpg` — Downloads / Clear all confirmation | Existing confirmation explains the destructive effect and keeps Cancel/Clear actions; no new layout defect identified. | No production change. Capture the actual Library Downloads confirmation. `VisualAppEvidenceTest.kt` only. | `audit-21-04-32-clear-downloads-confirm.png` | Evidence capture added; cancellation is exercised; download data is in-memory. |
| 14 | `Screenshot_2026-11-07-21-04-38-254_dev.dhun.android.jpg` — History / Clear history confirmation | Existing history-specific warning and confirmation are legible; preserve the history-only scope. | No production change. Capture the actual Library History confirmation. `VisualAppEvidenceTest.kt` only. | `audit-21-04-38-clear-library-history-confirm.png` | Evidence capture added; cancellation is exercised; no history is cleared. |
| 15 | `Screenshot_2026-11-07-21-05-23-664_dev.dhun.android.jpg` — Lyrics & Romanization / Clear lyrics cache | The longer explanatory message is specific to lyrics choices/cache; do not replace it with generic confirmation copy. | No production change. Capture the real Settings row/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-05-23-clear-lyrics-cache-confirm.png` | Evidence capture added; cancellation is exercised; no cache is cleared. |
| 16 | `Screenshot_2026-11-07-21-05-35-252_dev.dhun.android.jpg` — Country/Region picker | Searchable list and country codes are visible within a bounded dialog; no concrete mismatch found. | No production change; retain the wider list dialog and its search. Capture the actual Settings picker. `VisualAppEvidenceTest.kt` only. | `audit-21-05-35-country-region-picker.png` | Evidence capture added; country data is repository test data. Selection is not changed. |
| 17 | `Screenshot_2026-11-07-21-05-44-921_dev.dhun.android.jpg` — Privacy & history / Clear search history | Existing confirmation is appropriately specific and reversible only by the user's choice; no layout change warranted. | No production change. Capture the actual Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-05-44-clear-search-history-confirm.png` | Evidence capture added; cancellation is exercised; no search history is cleared. |
| 18 | `Screenshot_2026-11-07-21-05-47-493_dev.dhun.android.jpg` — Privacy & history / Clear play history | Existing confirmation is separate from search history and correctly describes the operation; preserve that distinction. | No production change. Capture the actual Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-05-47-clear-play-history-confirm.png` | Evidence capture added; cancellation is exercised; no play history is cleared. |
| 19 | `Screenshot_2026-11-07-21-06-07-650_dev.dhun.android.jpg` — Storage & cache / Clear audio cache | Existing warning is audio-cache-specific and distinct from persistent downloads; no extra visual treatment needed. | No production change. Capture the actual Settings action/dialog. `VisualAppEvidenceTest.kt` only. | `audit-21-06-07-clear-audio-cache-confirm.png` | Evidence capture added; confirmation is not submitted, so no audio cache is cleared. |

## Behavior, theme, and responsive coverage

- Lyrics source/provider selection still writes the existing preference; Edit
  still opens local TTML import/preview; Refetch and Search retain their current
  ViewModel/controller callbacks; Find matches opens the picker without
  automatically searching.
- Timing still uses the existing per-track repository and seek path, supports
  −10,000…+10,000 ms in 100 ms increments, and retains Reset; Reset is shown as
  a 48dp icon target only when the saved offset is nonzero. No preference,
  playback, lyrics, or persistence contract was reduced.
- The romanization component's compact row is opt-in from Lyrics Controls;
  Settings keeps its existing layout. Translucent DHUN material is retained;
  no generic Material 3 reskin or extra blur layer was added.
- Lyrics Controls has dark/light Compose/Skia captures plus a narrow 360×640
  1.25× font-scale interaction/capture. Other app-state references are captured
  individually at a nominal 360dp phone canvas, with the player/menu cases at
  their existing 412×915 evidence size.

## Validation status

The implementation and one-to-one evidence captures are in the worktree. The
new GitHub Actions run and artifact-manifest review are the next validation
step. Compose/Skia evidence must not be described as Android emulator or device
validation. No emulator or physical-device after capture has been performed in
this environment.
