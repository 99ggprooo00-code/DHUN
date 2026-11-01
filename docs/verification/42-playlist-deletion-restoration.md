# Stage 1 — local playlist deletion

## Scope

Restores the deletion behaviour identified in
[the restoration inventory](41-native-restoration-and-glass-plan.md), without
restoring #211's website/release changes or any later glass UI.

- The local playlist detail confirmation belongs to the screen, above the
  SQLDelight row observer. A missing-row emission cannot dismiss a pending
  operation or navigate by itself.
- A synchronous operation gate rejects repeated confirmation taps.
- `deleted` becomes true only after `PlaylistRepository.delete` returns
  normally. This is the sole automatic success-navigation signal.
- Failure and cancellation do not report success. The confirmation remains
  available with a truthful message and retry; cancellation is rethrown.
- A post-commit exception is treated as an uncertain outcome, not success.
  Retrying the idempotent delete can confirm the requested absence.
- Cancelling the confirmation performs no repository write.
- The unused Library delete method now propagates its result to its future
  caller instead of swallowing failure. Library row management is Stage 4.

## Regression coverage

`BrowseViewModelTest` covers pending-before-completion, duplicate confirmation,
success-only completion, visible failure and retry, post-commit uncertainty,
cancellation, missing local rows, remote-playlist refusal, and preservation of
the independent player queue.

`RepositoriesTest` uses real SQLite drivers to cover:

- rollback when a trigger aborts the playlist-row delete after membership
  deletion;
- a successful retry after removing that failure trigger;
- database close/reopen after deletion;
- preservation of an unrelated playlist and its description, standalone track
  metadata, favourite, history, completed download and audio file, and the saved
  queue/index/position/repeat/shuffle state;
- safe deletion of a missing playlist ID.

## Verification boundary

No JDK/Android SDK, emulator, ADB device, or interactive desktop display is
available in the authoring sandbox. The Kotlin tests and Compose interaction
therefore require the exact-head GitHub Actions run; this document does not
claim a local Kotlin test or device result. Android and desktop compilation,
the shared JVM suite, and Android unit tests are required CI gates. Physical
confirmation of dialog timing, navigation, and retained user data remains a
separate device acceptance step.
