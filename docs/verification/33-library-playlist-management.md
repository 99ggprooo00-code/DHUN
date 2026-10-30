# 33 — Rename and delete a saved playlist from the Library list

Date: 2026-10-30. Branch: `arena/97df3ebc-dhun`. Base: `main` @ `3ce83dd`.
Scope: `shared/` — the Compose Multiplatform Library screen used by both
`app-android/` and `app-desktop/`. Builds on the playlist deletion work in
#210/#211 (detail page) without changing it.

## 1. What was wrong

The Library's **Playlists** tab lists every saved playlist, but a row offered
only "open" and "play". `LibraryViewModel.renamePlaylist` was reachable from
nowhere (`deletePlaylist` literally carried the comment *"No current UI caller"*),
so the only way to rename or delete a playlist was to open it, use the detail
page's header buttons, and come back. The list — the place the playlists are
actually visible — could not manage them at all.

## 2. What changed

### Model (`presentation/library/LibraryViewModel.kt`)

Two dialogs' worth of state now lives in the screen model instead of in local
`remember` flags, so each step is assertable without a rendered frame:

| Member | Meaning |
| --- | --- |
| `renamingPlaylist` / `beginRenamePlaylist(playlist)` | the open rename dialog and the playlist it targets |
| `deletingPlaylist` / `beginDeletePlaylist(playlist)` | the open delete confirmation and the playlist it names |
| `cancelRenamePlaylist()` / `cancelDeletePlaylist()` | close without a write; refused while a write is still running |
| `confirmRenamePlaylist(name)` / `confirmDeletePlaylist()` | run the write, clear the dialog **only** when it returned |
| `renamePlaylist(id, name, onSuccess)` | tracked rename (key `playlist-rename:<id>`) |
| `deletePlaylist(id, onSuccess)` | tracked delete (key `playlist-delete:<id>`) — replaces the untracked `suspend fun` that had no callers |
| `renameActionKey(id)` / `deleteActionKey(id)` | the keys the dialogs read their pending/error state from |
| `playlistDialogOwnsError(state)` | true when a playlist dialog reports the failure inline, so the screen banner does not repeat it |

Rules preserved from the existing app behaviour (not invented here):

- the name rule is `trim()` + not blank — the same check `create` uses and the
  same `enabled = name.isNotBlank()` the detail page's dialog applies;
- re-saving the current name is not a write (the detail page short-circuits it
  too in `PlaylistViewModel.rename`);
- both writes run through the shared `MutationTracker`, so a second tap while a
  write is in flight is ignored, the failure is a message rather than an escaped
  exception, and success is only signalled when the store returned.

### UI

- **One pair of dialogs, two screens.** `RenameDialog` and `DeleteConfirmDialog`
  were private copies inside `ui/browse/PlaylistScreen.kt`. They moved to
  `ui/components/PlaylistDialogs.kt` as `PlaylistRenameDialog` /
  `PlaylistDeleteDialog` (same layout, same copy, same opaque `GlassCard`) and
  both screens now use them — no second playlist-management implementation, and
  the two cannot drift.
- **Row action.** Each Library playlist row gained a trailing ⋮
  (`contentDescription = "Manage <name>"`) that opens the same anchored
  `DropdownMenu` over `TrackMenuSurface` + `MenuActionRow` the queue row uses —
  one menu system, not a new one. Rows with no playlist artwork pass
  `artworkUrl = null`, which is the surface's documented no-veil case.
- **Wiring.** The dialogs read `isSaving`/`isDeleting` and `errorMessage` from
  `mutation` for that playlist's key, so a failed rename/delete keeps the dialog
  open with an inline retry while the list keeps the stored name / the row.
- **Banner.** `ActionErrorBanner` keeps every other single-row failure
  (like/unlike, history removal, downloads) and skips the one a playlist dialog
  already shows.

No schema, migration, navigation, provider or visual-token change. The playlist
detail page keeps its own rename/delete entry points and its behaviour.

## 3. Tests — `:shared:jvmTest`, `LibraryPlaylistActionsTest` (new, 11 tests)

| Test | Property |
| --- | --- |
| `openingRenameTargetsThePlaylistThatWasTapped` | the dialog targets the tapped row, nothing is deleted, other playlists untouched |
| `aSuccessfulRenameUpdatesTheLibraryList` | trimmed name reaches the list and the store; dialog closes only after the write |
| `reSavingTheSameNameClosesWithoutAWrite` | unchanged name writes nothing and closes |
| `aBlankNameIsRefusedAndTheDialogStaysOpen` | blank never reaches the store, dialog stays, no false error |
| `aFailedRenameKeepsTheStoredNameAndTheDialog` | list still shows the stored name, store unchanged, dialog open with its own error, banner suppressed |
| `openingDeleteAsksForConfirmationAndDeletesNothing` | confirmation names the tapped playlist, zero deletes |
| `cancellingTheConfirmationDeletesNothing` | cancel clears the dialog and never calls the store |
| `confirmingTheDeletionRemovesThePlaylistFromTheLibrary` | the row disappears from the observed list, only the intended playlist goes |
| `aFailedDeletionKeepsThePlaylistAndReportsItInItsDialog` | failed delete keeps the row and the store row, dialog open with its error |
| `aSecondSaveWhileTheRenameIsStillWritingIsIgnored` | one write per save, pending write cannot be dismissed |
| `onlyPlaylistDialogsSuppressTheScreenBanner` | like/history failures still reach the banner; rename/delete ones do not |

Each assertion is about state the ViewModel and the database actually reach, not
about a button existing. The Compose rendering (menu placement, dialog layout)
is shared with the detail page and is not rendered in JVM tests.

Detail-screen regressions stay covered by the existing `:shared:jvmTest` suite
for `PlaylistViewModel` (rename/delete/deletion state) and the repository tests,
which this change does not modify.

## 4. Verification

| Check | Where | Result |
| --- | --- | --- |
| `:shared:jvmTest` (incl. the new suite) | GitHub Actions `CI` | see this PR's checks |
| `:app-android:testDebugUnitTest`, `assembleDebug`, `bundleDebug`, both NewApi lint gates | GitHub Actions `CI` | see this PR's checks |
| `:app-desktop` compile + `jvmTest`, MSI packaging | GitHub Actions `CI` / `Build APK` | see this PR's checks |
| Repository hygiene suite (`scripts/test_*.py`) | this sandbox | run locally |
| Android emulator / physical device | — | **NOT RUN** — no SDK, emulator or ADB in this sandbox |
| Windows host / interactive desktop | — | **NOT RUN** |

Compilation and unit tests are CI-only: this sandbox has no JDK, Gradle or
Android SDK. Nothing below "CI" is claimed as observed behaviour.
