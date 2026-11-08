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

The current PR #223 visual-evidence run `42432357921` has 13 Compose/Skia tests
and 89 PNG frames. The initially reviewed popup frames are in the workspace
cache `.cache/popup-baseline-424323/` (not a tracked deliverable). It includes
confirmations, Lyrics Match, country/region, add-to-playlist, track overflow,
and Queue/Related. Direct create-playlist, rename-playlist, and playback-error
baseline frames are still needed before those surfaces change. Capture them in
both themes, then retain a stable before/after evidence map in this document.

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

## Current validation state

- Audit: complete; the concrete redesign/preservation decisions are listed above.
- Missing baseline captures: create playlist, rename playlist, playback error.
- Production redesign: not started in this baseline phase.
- Android emulator/physical-device verification: not yet performed; do not infer
  device behavior from Skia output.

The final PR record will replace this status with the exact changed files,
visual evidence run/manifest, regression results, known limitations, and APK
instructions. PR #223 must remain open; no merge or release is part of this work.
