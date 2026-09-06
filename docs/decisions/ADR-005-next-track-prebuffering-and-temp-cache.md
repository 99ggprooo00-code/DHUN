# ADR-005: Next-Track Pre-buffering, Temporary Cache Lifecycle, and Stream Isolation

## Status

**ACCEPTED** — 2026-09-06. Addressing real hardware test findings: long buffering pauses on skips, audio overlap during desktop transitions, and Android stream rejection during multi-item queue loading.

## Context

Hardware testing on Android and Windows revealed three interrelated playback issues:
1. **Long Buffering & Track Transition Latency:** Skipping tracks or waiting for the next track in a queue incurred a noticeable buffering pause because stream resolution and byte fetching only began after the track was requested.
2. **Audio Overlap During Transitions (Desktop):** In `DesktopDhunPlayer`, the currently playing track continued playing audio in libVLC while the next track was resolving over the network.
3. **Android Pre-buffering User-Agent Collision:** On Android, Media3 / ExoPlayer automatically prepares upcoming queue items. In `PlaybackGraph.kt`, a single shared `AtomicReference<String?>` was overwritten when the next track resolved, causing subsequent range requests for the currently playing track to present the wrong User-Agent to the YouTube CDN, triggering HTTP 403 Forbidden ("Almost no music plays").
4. **Cache Retention Constraint:** Pre-buffered data for upcoming tracks must not permanently pollute the disk cache if the user does not actually play those tracks (e.g. skips to another playlist or closes the app).

## Decision

### 1. Pre-buffering Policy
- **Condition:** Pre-loading of the next track in the queue initiates **only after** the currently playing track is in `PlaybackState.Playing` (loaded and actively playing). This prevents network contention during the primary track's initial start.
- **Target:** Only the immediate next track (`orderCursor + 1` in `QueueManager`) is pre-buffered.

### 2. Temporary Cache Lifecycle (Desktop)
- The next track is pre-resolved and downloaded into a temporary pre-buffer file (`<cacheDir>/temp/<videoId>.prebuff`).
- **Promotion:** When playback advances to the pre-buffered track, the `.prebuff` file is atomically promoted/renamed to `<cacheDir>/audio/<videoId>.audio` in the permanent LRU cache.
- **Eviction / Cleanup:** If the user skips to an arbitrary track, reorders the queue, switches playlists, or exits the application, all unplayed `.prebuff` files are immediately deleted.
- On startup, any leftover `.prebuff` or `.part` files in the temp directory are swept and removed.

### 3. Android User-Agent Isolation Map
- In `PlaybackGraph.kt`, replace the global `AtomicReference<String?>` with a concurrent key-to-agent mapping (`ConcurrentHashMap<String, String>`).
- When `ResolvingDataSource.Resolver` resolves `videoId`, it associates `videoId` and the resolved URL with the exact User-Agent returned by the winning InnerTube identity.
- When `UserAgentDataSource.open(dataSpec)` opens an HTTP connection for any segment of any track, it queries the map using `dataSpec.key` (or URI), ensuring every segment of every track always presents its own matching User-Agent.

### 4. Clean Audio Transition (Desktop)
- When a track change is triggered, if the next track is already pre-buffered, it begins playing immediately with near-zero latency.
- If the target track is not pre-buffered, the active player is immediately stopped/paused before entering `PlaybackState.Buffering`, eliminating audio overlap from the previous track.

### 5. Future Web & Platform Compatibility
- For future Web (or other targets), pre-buffering follows the same decoupled state machine: only pre-resolve when active playback is stable, store chunks in temporary browser storage (CacheStorage / IndexedDB), and purge unplayed buffers when queue context changes.

## Consequences

- **Positive:** Instant/seamless track transitions when following the queue; eliminates the long buffering pauses reported on Android and Desktop.
- **Positive:** Fixes the Android playback failure where ExoPlayer's background preparation corrupted the active stream's User-Agent.
- **Positive:** Zero disk cache bloat from skipped/unplayed pre-buffered tracks.
- **Neutral:** Slight extra bandwidth usage when pre-buffering the next track while listening, which is standard for modern streaming applications.
