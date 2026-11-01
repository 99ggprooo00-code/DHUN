# Persistent update recovery — 2026-10-31

## Status

**Investigation/fix candidate; not phone-accepted and not merged.** A fresh install or
clear-data result is not treated as proof of recovery. The current branch must still pass
CI and fresh/install-over/repeated-update verification on the user's phone before merge.

## Evidence that changed the investigation

The reported installation received roughly 7–10 debug installs over the same app data.
Uninstall/reinstall then made the app—and older builds that had failed—work. Therefore
persisted state and update compatibility are investigated before assigning the failure to
YouTube.

The rollback snapshot uses one `dhun.db` at SQLDelight schema version 3. Recent inspected
builds did not add a later schema migration. Home/Search continuation tokens, served client
context, visitor data and signature timestamps are process-memory state in this snapshot;
no deliberate persisted Home/Search response cache was found. Persistent state also includes
settings, track/playlist/favourite/history/queue/lyrics/download rows, downloaded audio and
artwork, Media3 audio segments, and platform image-loader storage.

## Controlled anonymous country probe

Temporary diagnostic run **41201498495** (check job **124538033297**) compared the rollback
request shape without cookies or an account. Only status/result counts were emitted; visitor
values, signed URLs and response bodies were not logged. The temporary workflow was removed
after the probe.

| Scenario | Search, legacy Songs params | Home `/browse` | Related `/next` |
| --- | --- | --- | --- |
| Fresh/default `US` | 200 | 200 | 200; 50 panel tracks |
| Fresh `NP` | 200 | 200 | 200; 50 panel tracks |
| Saved `NP` after anonymous session creation | 200 | 200 | 200; 50 panel tracks |
| Saved invalid `ZZ` after anonymous session creation | **400, `INVALID_ARGUMENT/badRequest`** | not used as a success claim | not used as a success claim |

This is non-phone evidence, not proof that the phone contains `ZZ`. It does prove two things:
valid `NP` is not inherently broken, and a persisted invalid region can reproduce the exact
Search 400 while a fresh default succeeds.

The code mismatch was concrete: Settings normalized an unsupported stored value and displayed
`US`, while `InnerTubeClient` observed the raw settings row and could send `ZZ` as `gl`.
Country normalization now lives in the shared core region model and is used by both paths.
Valid regions are preserved; an invalid row is not deleted or rewritten merely to make the
app start.

## Persistence defects fixed in this candidate

1. **Unverified partial downloads.** Any non-empty part receiving HTTP 416 used to be
   accepted as complete. A short response could also be returned as success despite its
   declared total. The byte downloader now retries an unverified/stale part once from zero,
   validates 206 `Content-Range`, rejects truncated completions, and omits signed media URLs
   from error details.
2. **Cross-update partial identity.** Audio/artwork parts now have a small source metadata
   record. An absent record from an older build or a changed source restarts only the
   temporary part. Completed audio/artwork and user database rows are not cleared.
3. **Orphaned download rows after update.** `QUEUED`/`DOWNLOADING` rows whose workers died
   with the old process become `PAUSED`, so the existing Resume action can recover them.
   A completed row with absent/empty audio becomes retryable `FAILED` without deleting its
   metadata. Intact completed audio remains completed.
4. **Completed-before-artwork interruption.** Completion was persisted before best-effort
   artwork, but later enqueue/restart trusted the completed row forever. Missing covers are
   now repaired in the background using only that track's saved remote cover and its own
   YouTube video cover. Artwork failure never turns healthy audio into a failed download.
5. **Saved downloaded cover ignored.** Library/playback projection used only the remote
   `thumbnailUrl`; it now prefers the downloaded local cover URI and retains the remote URL
   as fallback.
6. **Metadata degradation across queue saves.** Saving a partial older track model could
   overwrite existing artist/album/duration/artwork fields with null. Track updates now keep
   the existing optional value when the incoming value is null; favourite/playlist links are
   not replaced or cascaded away.

These are source-level causes reproduced by the new fixtures. They are not collectively
claimed as the phone's sole root cause without the phone acceptance run.

## Scenario coverage

| Scenario | Automated coverage in this branch | Device status |
| --- | --- | --- |
| A. Fresh install/default data | Fresh in-memory repository/view-model tests; absent country emits `US` on the wire | Not run here |
| B. One old-data update | File-backed v1 fixture migrated by the real SQLDelight driver | Not run here |
| C. Several successive revisions | Same migrated file closed/reopened eight times with user-data assertions each time | Not run here |
| D. Old schema → latest | v1 → v3 migration; later lyrics/download tables exercised and retained on later opens | Not run here |
| E. Cache-only clearing | Existing Media3 corruption/clear tests; new temporary-part recovery tests | Android cache-only operation not run |
| F. Data clearing/reinstall | Not used as a success claim; fresh fixtures are distinguished from update fixtures | Not run here |

The old-data fixture seeds favourites, a playlist and its track, listening history, a recent
search, settings, queue position/modes and rich track artwork/metadata. It then adds lyrics
and a completed download in a newer revision and verifies those too on subsequent opens.
No test wipes all data to pass.

## Release-asset cleanup

The private rolling `test` draft at `1e2f7cb` now contains exactly four installers. Only its
eight `.sha256`/`.provenance.txt` assets were removed. The rolling workflow uploads just the
four installers and asserts the exact asset set; internal build provenance remains available
for verification/promotion. No public release was published or edited.

## Verification still required

- CI compilation and the complete existing/new regression suites (results must be recorded
  from actual runs, not inferred from source inspection).
- Android APKs from Actions artifacts for arm64-v8a, armeabi-v7a and universal; desktop MSI
  from its artifact. No local Android SDK/emulator/phone or interactive Windows environment
  is available in this workspace.
- On the phone: fresh install, one install-over, and several successive install-over runs;
  Search, Home and continuation, all four moods, album/artist/playlist/Related, artwork in
  and outside Full Player, Mini/Full Player, queue/playback, downloads and Library/settings
  persistence. Confirm user data survives each update.
- Merge remains blocked until the underlying phone scenario is explained and the user gives
  functional acceptance. Green CI alone is not acceptance.
