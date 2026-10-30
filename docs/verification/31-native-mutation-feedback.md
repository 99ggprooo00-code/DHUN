# 31 — Native mutation feedback: "nothing happened" is now a message

Date: 2026-10-29. Branch: `arena/8b985cde-dhun`. Base: `main` @ `1ec8c06`
(merge of PR #211).

Scope: **native applications only** — `shared/` (Compose Multiplatform UI +
screen models), consumed by `app-android/` and `app-desktop/`. No web, website,
provider, extraction or release work is part of this change.

## 1. What the audit found

Playlist deletion had just been made result-driven (PR #210 closed as a
superseded duplicate, PR #211 merged). The audit checked whether the same defect
class existed in the rest of the native app, by following each mutating control
from the Compose screen through the screen model into the repository:

| Surface | Control | Defect found in the source | User-visible effect |
| --- | --- | --- | --- |
| Settings → Privacy | Clear search history | `scope.launch { action() }`, dialog closed on tap | A failed clear looked exactly like a successful one |
| Settings → Privacy | Clear play history | same | same |
| Settings → Lyrics | Clear lyrics cache | same | same |
| Settings → Storage | Clear audio cache | dialog closed on tap; error flag set but no retry, no pending state | Repeating the tap was the only recovery, with no indication it was pending |
| Settings → About | Reset settings to defaults | fire-and-forget write; appearance/EQ reset applied on tap even if the write failed | Settings could look reset while the database still held the old values |
| Settings (all) | Every preference write | unguarded `scope.launch` inside `writeMutex.withLock` | A failed write escaped to the thread's uncaught-exception handler — an app crash on Android |
| Library → Playlists | New playlist | `try { … } catch (_: Exception) {}` then `onConfirm(...)` | The dialog closed as if the playlist had been created |
| Library → History | Remove one play (row button / long-press) | `scope.launch { runCatching { history.remove(id) } }` | Silent no-op; the row stayed with no explanation |
| Library → Downloads | Remove / cancel / pause / resume / enqueue | `scope.launch { runCatching { … } }` | Silent no-op |
| Library → Playlists | Rename playlist (model path) | `scope.launch { runCatching { rename } }` | Silent no-op |
| Library → Playlists | Play a playlist | `runCatching { observeTracks(id).first() }.getOrNull() ?: emptyList()` | A read failure looked like an empty playlist: Play did nothing |
| Library → Liked Songs | Unlike / Like | `runCatching` inside `scope.launch` | Heart did not change and nothing said why |
| Home / Search / queue / full player | Like / Unlike | `scope.launch { toggleFavoriteUseCase(track) }` with no handler | Crash on Android on a failed write; otherwise silent |
| Playlist page (local) | Rename | dialog closed immediately, rename launched | Failed rename looked saved |
| Playlist page (local) | Swipe-remove, drag-reorder | `scope.launch { playlists.… }` with no handler | Row stayed / order snapped back, with no message |
| Add to playlist sheet | Add / Create & add | repository call outside any try/catch | Crash on Android on failure; no in-sheet error |
| Add to playlist sheet | Add (duplicate) | `addTrack` returns `false` when the track is already there; the sheet ignored the result | "Added to <playlist>" + the sheet closed for a write that changed nothing |
| Settings (startup) | reading the persisted values | `init { scope.launch { …read every key… } }` with no handler | A failed read killed the coroutine (crash on Android) and left `loaded` false for ever: Settings never rendered and no stored preference was applied |

Root cause, one sentence: **the app confirmed mutations optimistically (by
closing its UI or by saying nothing) instead of after the suspend call
returned.** PR #210/#211 fixed exactly one instance of that; this change fixes
the rest of the native surface.

## 2. What changed

### Shared KMP — one contract

`shared/src/commonMain/kotlin/dev/dhun/presentation/MutationState.kt` (new):

- `MutationState(runningKeys, errorMessage, errorKey)` — what is in flight, what
  failed, and which action owns the message (so one dialog never shows another
  dialog's error, and a screen banner can show only messages no dialog owns).
- `MutationTracker` — `run(key, failureMessage, onSuccess) { … }` claims the key
  **before** launching (a second tap before recomposition is ignored), starts
  undispatched so serialized write queues keep their order, converts an exception
  into a message instead of letting it escape the coroutine, and runs
  `onSuccess` only for a block that returned normally.

Applied to: `SettingsViewModel`, `LibraryViewModel`, `PlaylistViewModel`,
`HomeViewModel`, `SearchViewModel` and the add-to-playlist sheet.

### Android + Desktop (shared Compose UI, one implementation for both)

- `ui/components/ActionErrorBanner.kt` (new): the screen-level failure strip —
  same surface family as the existing offline banner, a polite live region so
  TalkBack announces it, with a Dismiss action.
- Settings: every destructive confirmation is now result-driven. It stays open
  while the action runs (`loading` confirm button, "Clearing…"), shows its own
  retryable failure, and closes only when `lastCompletedKey` reports success.
  Reset applies the live appearance/equalizer reset on that success signal, not
  on the tap. A failed preference write shows a dismissible notice instead of
  crashing.
- Library: a failure banner under the tab row for row-level mutations; the
  create-playlist dialog keeps itself open with the error and a "Try again".
- Playlist page: rename is a `suspend` call with pending/error state in the
  dialog; remove/reorder failures appear in the banner over the page.
- Shell: one banner above the page (and above the expanded player, where the
  same heart lives) for a failed Like/Unlike from Home, Search, the queue or the
  full player.
- Add to playlist: pending/error inside the sheet; `onAdded`/`onDismiss` only
  after the write returned, and a track that is already in the chosen playlist
  is reported as "Already in <name>" instead of a false "added".
- Settings startup: a failed read of the persisted values now falls back to the
  documented defaults, still reports `loaded`, and shows a notice — instead of
  dying with an uncaught exception and leaving the screen permanently unloaded.

No provider, extraction, download-transport, database-schema, navigation or
visual-identity change. No new dependency.

## 3. Tests (shared JVM, `:shared:jvmTest`)

New:

- `presentation/MutationTrackerTest` — pending published before return,
  duplicate submission rejected then allowed after completion, success callback
  exactly once, failure captured (asserted *not* to reach a
  `CoroutineExceptionHandler`), failure releases the key for a retry, message
  scoping between actions, cancellation rethrown without a user-facing message,
  independent keys running concurrently.
- `presentation/settings/SettingsMaintenanceActionsTest` — failed clear keeps
  its dialog open and is retryable; duplicate clear tap ignored; audio clear
  reports the bytes it reclaimed; a failure is scoped to its own action; a
  failed preference write is reported (not thrown out of the coroutine); a
  failed reset does not claim success; a failed settings read falls back to
  defaults, still loads, and reports why.

Extended regressions:

- `LibraryViewModelTest` — failed Like reported instead of thrown; failed
  history removal reported and the row stays; a successful removal leaves no
  message.
- `LibraryDownloadsViewModelTest` — failed download removal reported, then a
  successful retry.
- `HomeViewModelTest` — failed Like surfaces on Home.
- `BrowseViewModelTest` — playlist remove/reorder failures reported; rename
  propagates its failure to the dialog.
- `HomeViewModelTest` also pins the fix for a lagging derived flow: the message
  is read from the tracked `MutationState`, so dismissing it is visible
  immediately (the first CI run on this branch failed on exactly that gap).

## 4. Verification status (kept separate on purpose)

| Check | Where | Result |
| --- | --- | --- |
| `:shared:jvmTest` (all suites incl. the new ones) | GitHub Actions `CI` | see the PR's check list — this sandbox has no JDK, Gradle distribution, Google Maven or VS/Windows toolchain |
| `:app-android:testDebugUnitTest` (Robolectric) | GitHub Actions `CI` | same |
| `:app-android:assembleDebug`, `bundleDebug`, `lintDebug`, `:shared:lintDebug` | GitHub Actions `CI` | same |
| `:app-desktop:compileKotlinJvm`, `:app-desktop:jvmTest` | GitHub Actions `CI` | same |
| Android emulator / physical device run of the dialogs | — | **NOT RUN.** No ADB, SDK, emulator or device in this environment |
| Windows desktop interactive run | — | **NOT RUN.** No Windows host or display; CI compiles/tests the JVM code only |
| Python repository-hygiene suite (docs links, claims) | this sandbox | run locally, see §5 |

This document does **not** claim the Compose interaction was exercised on a
device. The state machines are covered by JVM tests; the rendering paths
(dialog copy, banner placement) are source-reviewed only.

## 5. Local checks that did run here

- `python3 -m unittest discover -s scripts -p 'test_*.py'` (repository hygiene,
  relative-link resolution, product claims) — includes this file's links.

## 6. Remaining native findings from the same audit (not done here)

- Android: no device verification of notification/lock-screen transport,
  audio-focus loss and recovery, process death with a restored queue, or the
  foreground download service — unchanged by this work, still unverified.
- Desktop: libVLC failure surfacing, tray/media-key behaviour and MSI
  install-over remain CI-packaging-only evidence, not interactive Windows
  evidence.
- Track/lyrics/provider request failures already had explicit error states
  (`ErrorView`, `PlaybackErrorDialog`) and were not touched.
