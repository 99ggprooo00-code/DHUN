# Dhun — Backend and Data Schema

## Backend decision
Initial recommendation: no custom backend for MVP unless a specific requirement proves it necessary. Use provider adapters for upstream access and local persistence for preferences, history and playlists. A backend schema does not mean a backend already exists.

## Local entities
- Track: id, title, artistIds, albumId?, durationMs?, artworkUrl?, source, sourceItemId, isPlayable?.
- Artist: id, name, artworkUrl?, source, sourceItemId.
- Album: id, title, artistIds, artworkUrl?, releaseDate?, trackIds.
- Playlist: id, name, description?, createdAt, updatedAt, isLocal, trackEntries.
- PlaylistEntry: id, playlistId, trackId, position, addedAt.
- PlayHistoryEntry: id, trackId, playedAt, positionMs?; only if history is enabled.
- UserPreferences: theme, accentMode, reducedMotion, playback preferences, updatedAt.
- CacheMetadata: cacheKey, schemaVersion, createdAt, expiresAt?.

Namespace provider IDs (for example, youtube:<provider-id>) to avoid collisions. Validate all external URLs.

## Runtime playback state
currentTrackId, queue, queueIndex, positionMs, durationMs?, isPlaying, isBuffering, repeatMode, shuffleEnabled, lastError?. Do not restore stale isPlaying=true without checking the actual player.

## Optional future server entities
Only after an approved cloud feature: accounts, devices, sync_records, and privacy_requests. Add only fields required for the approved feature. Never store upstream session cookies or credentials.

## API conventions if a backend is approved
Version routes under /api/v1; return stable JSON errors with code, message, requestId and safe optional details; validate inputs; enforce authorization and rate limits; paginate lists; use UTC timestamps; document database migrations, backups, export and deletion.

## Privacy and retention
Local-first by default. No account, cloud sync, server-side listening history, or telemetry by default. Provide clear controls for cache/history and future cloud-data export/deletion. Never log queries, listening history, cookies, tokens, or personal data.

## Schema evolution
Version local schemas and test migrations from empty and previous-version databases. Treat upstream payloads as external DTOs, not the only stored source of truth.
