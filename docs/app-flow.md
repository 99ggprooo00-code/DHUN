# DHUN — Application Flow and Navigation Contract

**Status:** Current-behavior map plus required UX contract. Validate against live code before implementation; this file must not silently redefine the architecture.

## 1. Surface map

- **Native app:** Home, Search, Library, detail destinations, Settings and FullPlayer.
- **Marketing website:** Home (`/`), Features (`/features/`), Interface (`/ui/`), 404 and external source/support/release destinations.
- **Browser mirror:** separate engineering preview; not the marketing site and not a production audio player.

## 2. Native app global navigation

### Primary tabs
- Home: discovery/recommendations/recent listening when data is available.
- Search: query and result list for tracks, artists, albums and playlists.
- Library: Playlists/Liked Songs, Downloads and History as implemented.
- Settings: reachable from the documented entry point; never a dead-end modal/page.

### Detail stack and Back
- Selecting an artist, album or playlist opens its detail view while retaining the source context.
- MiniPlayer tap opens FullPlayer; playback continues.
- Back order is strict: (1) collapse FullPlayer, (2) close/pop the top detail or Settings destination, (3) return to the previous tab if history exists, (4) delegate to platform behavior.
- Re-tapping the current tab should pop one detail level when the native navigation contract specifies it.
- On wide layouts, master/detail may show both panes; the immersive player must not intercept taps intended for the navigation rail.
- After process recreation, restore only safe navigation/preferences; never restore `isPlaying=true` without checking the actual player state.

## 3. Website information architecture

Every page uses the same global header and footer:
- Brand wordmark → Home.
- Main navigation → Home, Features, Interface; current route is marked accessibly.
- Footer → Features, Interface, repository, issues, changelog, test release, license/third-party information.
- Home links to Features and Interface; Features links back to Home/Interface and to source/release; Interface links to Features/Home and source/release; 404 provides all three internal destinations.
- All internal links must resolve from the deployed GitHub Pages project base `https://99ggprooo00-code.github.io/DHUN/`. Do not hard-code `/`, `/features/`, or `/ui/` unless a build step demonstrably prefixes them. Use one base-path helper/filter for generated internal URLs and validate the emitted HTML.
- Never point visitors to the engineering preview as a functioning player. Do not say the website can stream audio unless a production web-player decision and deployed-origin proof exist.

### Required website journeys
1. Direct-open Home → click Features → click Interface → click wordmark → return Home.
2. Home → Interface → Features → repository; browser Back/Forward restores expected page.
3. Direct-open each nested URL under `/DHUN/` (not only localhost root) and refresh it; it must not 404.
4. From every route, verify all header/footer internal links, CTA links, active-page marker and 404 recovery.
5. Open test-release CTA and confirm it reaches the release page, not a stale direct artifact or unrelated root path.

## 4. Search flow

1. User enters query.
2. Empty query: show a clear idle/instruction state; do not send an empty upstream request.
3. New query cancels or supersedes the previous request; debounce where implemented.
4. Show loading state and preserve the query.
5. Normalize provider response into domain models.
6. Results: selecting a row opens the correct detail or plays only when Play was explicitly requested.
7. No results: explain that nothing matched and allow editing the query.
8. Offline / timeout / rate limit / provider failure: preserve query and offer an appropriate retry; do not present stale sample results as live.
9. Unsupported item: explain why it cannot play and keep the user in context.

## 5. Playback flow

1. User presses Play on a track/list/album/playlist.
2. Record intended play context and establish queue order/index.
3. Resolve a playable source through the provider boundary.
4. Show loading/recovering/buffering state; do not imply audio has started until the engine reports it.
5. On success, synchronize current item, queue, progress, mini-player, FullPlayer, lyrics and system controls.
6. On recoverable stream failure, use the approved bounded recovery chain and communicate “Reconnecting…”/failure; do not loop indefinitely.
7. On final failure, stop spinner, preserve the selected item/queue where possible, and expose retry/next/back.
8. Pause/seek/next/previous/shuffle/repeat update the player engine and all visible state consistently.
9. Back/collapse from FullPlayer never stops audio unless the user explicitly presses Stop (if that control exists).

## 6. FullPlayer and lyrics/CC

- MiniPlayer → FullPlayer; the current track and playback position are preserved.
- Default mode: fitted sharp artwork, track/artist, seek/progress, primary transport, secondary controls and Lyrics/Queue/Related access.
- Lyrics/CC action transitions to lyrics-dominant mode: artwork recedes, cached blurred artwork fills the backdrop, frosted translucent lyrics surface appears, transport remains reachable.
- Synced lyrics emphasize the current line, keep nearby lines visible and scroll without jitter; tapping a timed line seeks to its timestamp.
- Unsynced lyrics are readable scrollable text and do not pretend to be time-synced.
- No lyrics / unavailable / loading / provider error each has a distinct non-crashing state.
- Leaving lyrics mode returns to the same track, queue, progress and previous player context. The transition must not restart audio or reset seek position.
- On short windows/landscape/small phones, essential transport and dismiss/back remain reachable without clipping.

## 7. Library and downloads flows

### Add/remove favorites and playlists
- Every menu item has a unique action identity. Opening a sheet and confirming an item must be different actions (e.g. `open-add-to-playlist` vs `confirm-add-to-playlist`); a selection row must not reopen its own sheet.
- After mutation, update the visible list and persistent repository. Show an explicit result or error.
- Liked Songs is a folder inside Playlists per current app contract, not a duplicate primary tab.

### Clear all downloads (urgent)
1. Downloads tab → choose Clear all.
2. If there are no rows, do not show the action.
3. Open a confirmation that names “downloaded tracks and their local files” and states the exact count/scope, including whether in-progress/partial items will be cancelled and removed.
4. Cancel/back/close → close dialog only; no state or filesystem mutation.
5. Confirm → set pending state and prevent duplicate submissions; call the download manager once.
6. Wait for completion result. Keep the dialog or show a blocking progress state until resolved.
7. Success → close dialog, refresh rows/storage summary, remove applicable temporary files, reset selection and show the empty state.
8. Failure → keep remaining rows visible, show error and retry; do not swallow exceptions or present success.
9. Tests cover completed, active, queued, paused, failed and partial download cases, plus cancellation and manager failure.

**Confirmed code concern to fix:** `LibraryScreen.kt` currently calls `onClearAll()`, clears selection and dismisses the confirmation immediately; `LibraryViewModel.clearDownloads()` launches `dm.clearAll()` in a coroutine and wraps it in `runCatching` without exposing a result. This makes failure invisible to the user. The implementation plan must add observable operation state/result and tests before considering this flow complete.

### Clear history, batch delete and cache
Use the same confirmation → pending → result pattern. Scope must be explicit: clearing play history must not delete downloads/playlists; clearing the volatile audio cache must not delete persistent downloaded tracks; resetting appearance must not erase the library. Verify persistence and UI refresh.

## 8. Settings and system flows

- Settings controls show current value, apply it, persist it and restore it on relaunch.
- Theme/accent changes must update all screens, dialogs, player, navigation and text contrast—not only one screen.
- Cache controls clearly distinguish volatile cache from offline downloads.
- Platform-only settings/features are hidden or clearly marked unavailable on other targets.
- About/legal/license/source actions open valid destinations and can be dismissed/backed out of predictably.

## 9. Global states and error recovery

Every screen defines loading, populated, empty, offline, failure, retry and partial-data behavior as applicable. Every overlay defines opening, focused state, cancel/dismiss, confirm, pending and error. No blank screen, invisible action, unresponsive control, lost query, silent destructive failure, or dead-end route is acceptable.

## 10. Verification matrix

Run unit/state tests; UI interaction tests; website built-output link crawl; route refresh/back/forward; accessibility/keyboard/contrast; responsive viewport checks; and real Android/Windows smoke tests. If a browser or device is unavailable, mark the test **not verified**, never passed by inference.
