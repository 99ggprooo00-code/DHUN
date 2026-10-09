# DHUN — Backend, Persistence and Data Schema

**Status:** Proposed data contract grounded in the current repository. Despite the filename, DHUN has no custom server backend in the current product architecture. This document primarily describes local persistence and runtime state. It must not be used as authorization to add an API, account system or cloud sync.

## 1. Storage boundary

| Data class | Purpose | Durability / policy |
|---|---|---|
| Provider metadata | Tracks, artists, albums, playlists and related items returned by YouTube/provider adapters | Cacheable; validate at boundary; expire/re-fetch when appropriate |
| User library | Playlists, playlist entries, liked tracks, play history and preferences | Local persistent data; only mutate through repositories/use cases |
| Download records/files | User-requested offline tracks and download job metadata | Persistent until explicit removal; distinct from stream cache |
| Audio stream cache | Bounded playback cache/LRU | Volatile/evictable; clearing it must not remove user downloads |
| Artwork cache | Artwork images and processed blur | Evictable derived data; never source of truth |
| Stream URL/session cache | Short-lived provider resolution values | Expire conservatively; never expose sensitive values in logs |
| Runtime player state | Current item, queue, index, position, playback/buffering/recovery | Memory-first; restore cautiously; do not trust stale `isPlaying=true` |
| Website content | Static source and built artifacts | No user database, account or telemetry |

Current persistence implementation is SQLDelight/repositories in the native project. Follow existing schemas/migrations and inspect the live database definitions before adding fields. Do not introduce a parallel database simply because this document names conceptual entities.

## 2. Conceptual entities

Names below describe domain responsibilities; map them to existing SQLDelight table/model names rather than blindly creating duplicates.

### Track
- `id`: stable namespaced provider ID (e.g. `youtube:<video-id>`).
- `title`: required display title.
- `artistIds`: normalized artist references; retain display fallback if provider metadata is incomplete.
- `albumId`: optional album reference.
- `durationMs`: optional non-negative duration.
- `artworkUrl`: optional validated URL.
- `providerId` / `sourceItemId`: origin identity; do not use a stream URL as identity.
- `availability`: available / unavailable / unknown as needed; do not equate metadata presence with playable audio.
- `lastFetchedAt` / cache expiry: optional cache metadata, not user preference.

### Artist and Album
- Stable namespaced IDs, display name/title, validated artwork URL, optional release metadata, provider source ID.
- Album track order must be explicit and stable; artist relationships may be many-to-many.
- Missing artwork or release dates must not crash layouts.

### Playlist and PlaylistEntry
- Playlist: stable local ID, name, optional description, created/updated timestamps and ownership/type.
- Entry: stable entry ID, playlist ID, track ID or normalized track snapshot reference, explicit position/order, added timestamp.
- Reordering must be deterministic; delete/rename/append must persist atomically where the current store supports transactions.
- Liked Songs follows the current product rule as a dedicated folder within Playlists, not a second independent top-level Favorites collection unless code migration explicitly requires it.

### PlayHistoryEntry
- Stable entry ID, track reference/snapshot, played timestamp, source/play context, optional final position.
- Record only when the product's playback rule says a play counts; prevent duplicate events from repeated state emissions.
- Clear-history affects history only; it must not delete playlists or downloads.

### UserPreferences
- Theme/accent, reduced motion if supported, resume-on-launch and playback preferences.
- Validate defaults and migrations; changes persist and update all relevant surfaces.
- Resetting preferences must not implicitly clear library/download data.

### DownloadRecord / DownloadJob
- Stable track ID, status (queued/downloading/paused/completed/failed/cancelled), progress/known size, destination metadata and timestamps as already modeled.
- Never persist an expiring stream URL as the file's identity.
- Partial file/temp-file state must be recoverable or cleaned after interrupted/cancelled jobs.
- Completed file existence and DB status must reconcile after startup; missing files should become a visible recoverable state.
- Clearing downloads must define whether all statuses are removed, cancel active jobs, clean partial files and delete metadata. DB rows and files must not diverge silently.

### CacheMetadata
- Cache kind/key, size if available, creation/expiry, schema/version and last-access metadata for LRU where required.
- Keep audio cache, artwork/blur cache and persistent download storage separate.

## 3. Runtime player state (not a server table)

- `currentTrackId`, queue entries, queue index, current position, known duration, playing/buffering/recovering state, shuffle/repeat, playback context and last typed error.
- Queue references must remain valid when a row is removed; define behavior if current track is removed.
- The actual engine is authoritative for audible state. A persisted snapshot must not claim playing until the engine confirms it.
- Player UI, mini-player, FullPlayer, lyrics and system media controls consume one consistent state stream.

## 4. Data consistency and destructive operations

### Clear downloads transaction
1. Capture the requested scope and operation ID.
2. Stop/cancel active work according to policy and prevent a new job from being added to the clearing set mid-operation.
3. Delete completed files and partial files safely; handle already-missing files idempotently.
4. Remove matching database records only when file/job cleanup semantics are known; define recovery for partial failure.
5. Emit a typed success/failure result and refresh the observed records/storage summary.
6. On failure, preserve enough metadata to retry cleanup and expose the failure to the UI.
7. Test DB/file divergence, permission failure, locked file, cancellation race, repeated confirm, app restart and active transfer.

Never swallow an exception for a user-triggered delete. A dialog disappearing is not proof of success.

### Other scopes
- Clear history: history rows only.
- Clear cache: evictable cache only; never persistent downloads.
- Remove one download: one track/job/file and its matching metadata.
- Delete playlist: playlist and entries only, unless explicit product semantics say otherwise.
- Reset settings: preferences only.

## 5. IDs, validation, migrations

- Namespaced IDs prevent collisions across sources; sanitize ID shape before file paths or persistence.
- Validate URLs against allowed schemes and reject malformed/unsafe values. Never trust an external provider response as a database invariant.
- Timestamps use a consistent instant representation; timezone formatting belongs in presentation.
- Schema changes require a migration, a migration test with representative prior data, and preservation of user playlists/history/downloads.
- Do not persist personal data or credentials that the feature does not need. Do not store cookies, visitor tokens or attestation secrets in logs or database.
- Keep test fixtures sanitized and deterministic.

## 6. Backend/API decision

**No custom backend for the current scope.** Provider adapters communicate with upstream sources according to existing architecture. No DHUN server, account, device registration, cloud sync, user analytics, or proxy is authorized by this schema.

If a future feature demonstrably requires a server, write an ADR and define:
- exact user benefit and why local-only cannot meet it;
- data controller/retention/deletion model;
- authentication and authorization model;
- threat model, rate limits, abuse controls and operational costs;
- versioned API contract and error schema;
- migrations/backups/restore;
- secrets management and incident response;
- privacy policy and consent where required;
- tests for access control, deletion, failure and data export.

Do not create speculative `users`, `devices`, `sync_records` or `privacy_requests` tables until that decision is accepted.
