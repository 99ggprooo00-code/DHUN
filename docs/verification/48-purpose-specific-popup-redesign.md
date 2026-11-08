# 48 — Purpose-specific popup redesign

Date: 2026-11-07. Work branch: `arena/b3400d42-dhun`; PR #223 remains open.
The workspace is pinned to this branch and the review is based on current `main`
`da2199650a37fd66abb903dca3a583fcffaf78b3`, including merged PR #222 (Lyrics
Controls). This is a popup inventory and redesign record, not a replacement for
PR #223's existing shared-glass and mutation work.

## Design contract

- Treat each popup as a task surface, not as an instance of one dialog template.
- Retain the shared DHUN high-contrast glass, artwork-aware lyric/menu material,
  palette and shape tokens where they support the task. Shared material is a
  foundation only; it does not dictate each popup's content hierarchy or size.
- Make only concrete composition changes where the audit finds a problem. Keep
  already-purposeful Lyrics Controls, track menus, queue menus, and the queue /
  Related sheet intact unless a test reveals a specific regression.
- Keep all existing callbacks, repository writes, provider choices, navigation,
  back/outside dismissal rules, pending/retry state, keyboard submission, and
  persistence behavior. Tests must exercise actions as well as render frames.
- Compare actual before/after surfaces in both themes and, where relevant, at
  narrow widths, increased text scale, and with input active. Compose/Skia frames
  are renderer evidence, not Android-device evidence.

## Popup inventory and audit decisions

| Surface / purpose | Current composition and concrete finding | Decision |
|---|---|---|
| Destructive confirmations: playlist delete, download/cache/history clears, reset, and recent-search clear | Shared `DhunConfirmDialog` already preserves mutation ownership, pending guards, inline retry, and grouped actions. Its title/body are still a flat text stack; the confirm button shares the ordinary accent treatment, so consequence and recovery state are not immediately scannable. | Keep the shared API and each caller's specific copy/callback. Give the confirmation content a distinct consequence header, a clearer warning/error region, and a purpose-appropriate destructive primary action. Preserve pending/error semantics and narrow/large-text behavior. |
| Create playlist | Private `CreatePlaylistDialog` in Library uses `GlassDialog`, an unlabeled single-line name field, inline failure text, and a right-aligned Cancel/Create footer. It does not present a keyboard submit hook. | Make the name field the center of a compact create form, label the task and field, keep errors adjacent to the field, and support the existing create operation from the keyboard as well as the button. Preserve trim/write/error/close ordering. |
| Rename playlist | `PlaylistRenameDialog` uses `GlassDialog`, a title, a prefilled field, optional error text, and Cancel/Save. The field's purpose is implicit and the existing name is not shown as context. | Use a rename-specific header and explicit name label/context, keep the result-driven Save/retry behavior, and provide a keyboard submit path without changing the caller's name or mutation rules. |
| Add track to playlist | `AddToPlaylistDialog` uses the track-art menu material/header and `MenuActionRow` for destination playlists. The existing track identity, result-driven mutation gate, duplicate/failure handling, create-and-add flow, and open-playlist route are good. The destination choice itself reads like another action menu, and “Open” competes with the add target. | Preserve the useful artwork/context and mutation logic, but distinguish a destination picker from the track overflow menu: give destination rows a clear target hierarchy, make opening a playlist a separate secondary affordance, and reserve a distinct create-new action/form. Keep the list content-sized and scrollable. |
| Lyrics Match / local TTML | `LyricsMatchPicker` is a wide `GlassDialog` with a long vertical stack: privacy/provider caveats, query/actions, local TTML controls, status/errors, and result metadata/previews. The complete information is present, but search, import, result review, and recovery compete in one scrolling column. | Recompose as a lyrics-specific artwork-aware matching surface with a clear search area, visually separate local-only import, scannable result cards, and a stable close/recovery area. Preserve every privacy caveat, controller call, selected-choice override, provider failure, import limit, preview, and choose action. |
| Country / region selection | `SettingsScreen` uses a wide `GlassDialog` with a search field and plain name/code rows. Only the selected name is accent-colored when visible in the result list; no current-selection summary or explicit check indicator is shown. | Add a persistent current-region summary, explicit selected-row mark/semantics, and a search-first list hierarchy. Keep country data/search and “select then dismiss” behavior unchanged. |
| Lyrics provider and romanization choice | Compact anchored `DropdownMenu`s use the shared menu material and rows. Selection is exposed semantically, but not visibly marked in the choice list. | Keep them compact menus; add a visible current-choice mark and selected treatment. Do not turn either into a generic modal. |
| Playback error / retry | `PlaybackErrorDialog` presents user error text, track title/ID, and full selectable technical diagnostics together in one scrollable stack; Close and Retry are both text buttons. The user-facing recovery action is too quiet relative to the diagnostics. | Prioritize the playable error and track context, make Retry the primary recovery action, and tuck complete selectable diagnostics behind an explicit disclosure. Preserve the exact retry/dismiss ordering and diagnostic content/limit. |
| Android background-playback rationale | `MainActivity` uses a text-only `GlassDialog` with explanatory copy and equal-weight “Not now” / “Allow” text actions. The settings route is correct, but the permission rationale does not make the allow action or background-playback context salient. | Give this platform-specific rationale a concise playback-context header and one clear primary “Allow” action. Keep the Android settings handoff and dismiss behavior unchanged. |
| Lyrics Controls | Purpose-built, compact, artwork-backed `Dialog` / track surface with provider, romanization, timing, and action controls. PR #222 is the quality benchmark, not a layout template. | Preserve it; only run its existing light/dark, narrow, large-text, persistence, provider, and action regressions. |
| Track overflow / artist and album actions | The track overflow is a content-sized, artwork-backed 44dp menu with its action order and visibility pinned by policy tests. Track/artist/album details do not have a separate modal; “Go to artist/album” route from this menu or row. | Preserve the menu and navigation. Do not invent a track-information popup solely to fill scope. |
| Queue-row actions and Library playlist actions | Anchored compact menus with purpose-specific action sets; enabled/destructive states are already represented in the menu rows. | Preserve their menu composition and action ordering; cover them in regression evidence. |
| Queue / Related | `FullPlayer` has a custom raised Queue/Related sheet with its own artwork treatment, tabs, handle, and scrollable content. It is not a centered dialog. | Preserve the sheet and its navigation/playback callbacks. |
| Search, settings, and other inline notices | Search is a full screen with recent-search actions; only clear-history confirmation is modal. Settings reset/cache actions use the shared confirmation. Other network/action errors use inline banners or page states. | Keep inline states inline; cover their existing confirmation callers and do not convert full screens or banners into dialogs. |
| OS-owned permission/file/keyboard surfaces | System permission prompts, file pickers, and the OS keyboard are owned by the platform. | Exclude the OS-owned surface itself; redesign only DHUN's rationale or surrounding app popup. |

## Baseline evidence and implementation checklist

The refreshed **pre-redesign** PR #223 Visual Evidence run `42441448532`
completed successfully at baseline commit `858b911`: 15 Compose/Skia tests,
101 PNG frames, zero failures and zero errors. Its inspectable manifest is Git
blob `ef56268ee0b4822c80315d995c6fb8c0a6ae282a`. Selected baseline images are
cached under `.cache/popup-baseline-424414/` (not a tracked deliverable),
including confirmation, playlist create/rename, add-to-playlist, playback-error,
battery-rationale, Lyrics Match, country/region, and dark/light provider and
romanization-menu captures. This is baseline evidence only; it does not validate
the redesign that follows.

| Work item | Baseline / responsive coverage to retain | Interaction guard |
|---|---|---|
| Confirmations | Dark/light, 360dp at increased text scale; actual settings/library copy in app context | Pending Back/Cancel/confirm remain inert; retry reaches the original callback once; dismissal remains available only when idle |
| Create / rename | Dark/light, narrow and large text; keyboard-submit path where the Compose test host supports it | Name rules, exact callback values, write failure/retry, loading guard, cancel/back behavior |
| Add to playlist | Dark/light over real player/artwork context; populated, empty/create, failure, and busy states | Add, duplicate, create-and-add, open-playlist, dismissal, and retry callbacks remain correct |
| Lyrics Match | Dark/light over the player/artwork context; narrow and increased text; searching, results, and local-import mode | Search/Retry, Use automatic, TTML preview/selection, provider errors, close, and saved-choice behavior |
| Country / region | Dark/light; narrow and increased text; search and selected row | Search filters the same region data; selection persists and dismisses; Back/outside only dismisses |
| Provider menus | Existing Lyrics Controls frames in both themes | Current selection remains visible/accessible; provider changes still reach the configured provider |
| Playback error | Dark/light; narrow/large text and long diagnostics | Retry invokes the same dismiss then retry callbacks; full diagnostics remain selectable |
| Battery rationale | Android Compose/Skia-compatible component in both themes; narrow width | “Not now” dismisses only; “Allow” closes then opens the same Android battery-settings route |
| Preserved menus/sheet | Existing track menu, queue/Related, and Lyrics Controls frames | Existing queue, navigation, and provider/timing interactions continue to pass |

## Post-redesign validation

The audit and implementation are complete on `arena/b3400d42-dhun`, head
`02502dcf0f47c50fc8a29286490a2a557a14993d`; PR #223 remains open.

### Rendered evidence

- Visual Evidence run [`42468911818`](https://github.com/99ggprooo00-code/DHUN/actions/runs/42468911818)
  passed on the head above in 18m05s: 21 tests (20 `VisualAppEvidenceTest`
  tests and one `VisualEvidenceTest`), 139 real Compose/Skia PNG frames, zero
  failures, errors, or skips.
- Inspectable manifest: Git blob
  `88648a2293e6e91bd183a54a0ac877280c4b30e4`. The normal
  `compose-visual-evidence` Actions artifact contains the frames and JUnit
  reports.
- Reviewed the dark/light selected-country captures
  `popup-after-country-picker-selected-dark.png` and
  `popup-after-country-picker-selected-light.png`: the current-region summary,
  selected row, and check mark are visible. The narrow, large-text playback
  error frames also show Retry after scrolling while retaining selectable
  diagnostics.
- The playback-error test now uses an upward touch gesture from “Hide
  diagnostics” and advances the manual Compose clock, rather than waiting on a
  bring-into-view animation. The country-picker test waits for the cleared query
  to recompose, refocuses before the second search, and searches again before
  asserting the selected row in the reopened picker.

### CI, packaging, and regression results

- CI run [`42468911805`](https://github.com/99ggprooo00-code/DHUN/actions/runs/42468911805)
  passed: shared JVM 848, Android Robolectric 169, playback probe 4, Desktop
  JVM 46; zero failures, errors, or skips in each group.
- Build APK run [`42468911813`](https://github.com/99ggprooo00-code/DHUN/actions/runs/42468911813)
  passed and uploaded the `debug-apk` artifact (universal, arm64-v8a, and
  armeabi-v7a). Verified package identity: `1.00.781-beta.1`, version code
  `1794131483`.
- DHUN beta run [`42468911795`](https://github.com/99ggprooo00-code/DHUN/actions/runs/42468911795)
  passed after rerunning the failed Windows job. MSI identity, install-over,
  userdata/cache preservation, uninstall cleanup, and installed-app launch
  passed. The repeated WiX release download HTTP 504 prompted a narrowly scoped
  three-attempt retry for that exact transient failure in
  `.github/workflows/beta-release.yml`; other Gradle failures remain immediate.
  On this PR, `publish_beta` and `publish` were skipped; no release or
  publication occurred.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: 524 tests passed.
  `git diff --check`: passed.
- Local Gradle compilation remains unavailable because this sandbox has no
  Java runtime; the remote CI, APK, Windows MSI, and Visual Evidence workflows
  provide the build/test results above.

### Files changed

- Workflows and workflow contracts: `.github/workflows/beta-release.yml`,
  `.github/workflows/ci.yml`, `.github/workflows/visual-evidence.yml`,
  `scripts/test_build_workflow.py`, `scripts/test_ci_workflow.py`,
  `scripts/test_design_system.py`.
- Platform and evidence tests: `app-android/src/main/kotlin/dev/dhun/android/MainActivity.kt`,
  `app-desktop/build.gradle.kts`,
  `app-desktop/src/jvmTest/kotlin/dev/dhun/desktop/VisualAppEvidenceTest.kt`,
  `app-desktop/src/jvmTest/kotlin/dev/dhun/desktop/VisualEvidenceTest.kt`.
- Shared design and popup composition: `shared/src/commonMain/kotlin/dev/dhun/design/DhunSpacing.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/design/catalog/ComponentCatalogScreen.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/design/components/DhunButton.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/design/components/DhunConfirmDialog.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/design/components/DhunControls.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/design/components/DhunTextField.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/design/components/GlassCard.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/components/AddToPlaylistDialog.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/components/PlaylistDialogs.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/components/TrackOverflowDialog.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/player/LyricsControlsDialog.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/player/LyricsMatchPicker.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/player/LyricsRomanizationControls.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/player/MiniPlayer.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/player/PlaybackErrorDialog.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/player/PlayerTabs.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/search/SearchScreen.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/settings/BatteryPlaybackRationaleDialog.kt`,
  `shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt`.
- Supporting records and repository hygiene: `.gitignore`,
  `docs/design/glass-system.md`,
  `docs/verification/45-lyrics-controls-dialog.md`,
  `docs/verification/46-app-wide-dialog-consistency.md`,
  `docs/verification/47-screenshot-by-screenshot-visual-audit.md`,
  `docs/verification/48-purpose-specific-popup-redesign.md`.

### Limitations and release scope

Compose/Skia output is renderer evidence, not Android-device or audible-playback
acceptance. The Windows install evidence records a window capture but does not
claim visual review or audible playback. For manual Android testing, download
`debug-apk` from the Build APK run above; it is a debug-signed test artifact,
not a store-ready release.

No merge, release, or publication was performed. Merging this PR to `main`
would activate the rolling public DHUN beta publication path, so the existing
no-release/no-publish constraint remains in force while PR #223 stays open.
