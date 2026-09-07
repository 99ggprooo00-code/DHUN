# ADR-006: Persistent Offline Music Downloads Architecture, Storage Management, and Multiplatform Offline Playback

## Status

**ACCEPTED** — 2026-09-07. Formulated following architecture review of open-source music players (ViMusic, InnerTune, Metrolist, SimpMusic, OuterTune) to provide full offline playback on Android and Desktop.

## Context

Streaming music over InnerTube client identities provides access to global catalog content, but users frequently encounter offline scenarios (e.g. flights, subway commutes, remote areas, low cellular bandwidth). To ensure DHUN delivers an uncompromising music experience:
1. Users must be able to download individual tracks, entire playlists, albums, and their Liked Songs for persistent offline playback.
2. Downloaded media must be permanently preserved until explicitly deleted by the user (unlike ephemeral pre-buffers and LRU stream caches governed by ADR-005).
3. Artwork, title, artist, and duration metadata must remain 100% visible and accessible offline even when disconnected from the internet.
4. When playing any track that is downloaded, playback must resolve instantly from local storage with zero network latency, zero bandwidth usage, and zero risk of token expiration or CDN 403 errors.

## Research & Open-Source Benchmark

| Project | Download Strategy | Metadata & Artwork | Storage Strategy |
|---|---|---|---|
| **ViMusic** | Custom HTTP chunked downloader with resume | Cached artwork in Room DB + separate media file | App-scoped internal directory |
| **InnerTune** | Coroutine-managed download queue + foreground service | Stored locally in SQLite + disk image cache | Persistent storage with custom directory selector |
| **Metrolist** | Tokenless InnerTube extraction + direct audio stream sink | Tagged audio metadata + SQLite lookup | Scoped external storage |
| **SimpMusic** | Chunked background downloader with retry exponential backoff | Embedded ID3 tags + local cache | Music directory with folder hierarchy |

## Decision

### 1. Unified Download Manager Architecture (`dev.dhun.download`)
A multiplatform download management engine living in `shared`:
- **`DownloadManager` Interface:** Exposes reactive flows (`downloadQueue`, `activeDownloads`, `downloadProgress(trackId)`), and operations (`enqueue(track)`, `enqueueAll(tracks)`, `pause(trackId)`, `resume(trackId)`, `cancel(trackId)`, `removeDownload(trackId)`, `clearAll()`).
- **Stream Extraction for Downloads:** Utilizes `OwnClientStreamResolver` to request the highest available audio-only stream (preferring Opus 160kbps `itag 251` or AAC 128-256kbps `itag 140`).
- **Resumable Chunked Downloader:**
  - Streams bytes directly to a temporary file: `<downloadsDir>/.tmp/<trackId>.part`.
  - Supports HTTP `Range` headers (`bytes=start-`) for resuming interrupted downloads on unstable connections.
  - Upon 100% byte transfer and integrity check, atomically renames the file to `<downloadsDir>/audio/<trackId>.<ext>`.
  - Concurrently fetches high-resolution thumbnail artwork and saves to `<downloadsDir>/art/<trackId>.jpg`.

### 2. SQLDelight Persistence Schema
Add a dedicated `DownloadedTrack` table in the database:
```sql
CREATE TABLE IF NOT EXISTS DownloadedTrack (
    trackId TEXT NOT NULL PRIMARY KEY,
    title TEXT NOT NULL,
    artistName TEXT NOT NULL,
    albumName TEXT,
    durationSeconds INTEGER NOT NULL,
    thumbnailUrl TEXT,
    localAudioPath TEXT NOT NULL,
    localArtworkPath TEXT,
    fileSizeBytes INTEGER NOT NULL,
    mimeType TEXT NOT NULL,
    bitrateKbps INTEGER,
    downloadState TEXT NOT NULL, -- QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED
    downloadedAtEpochMs INTEGER NOT NULL
);
```

### 3. Offline-First Playback Integration
- In `DhunPlayer` (Android `PlaybackGraph` and Desktop `DesktopDhunPlayer`):
  - Before querying the network or InnerTube tokenless resolver, query `DownloadRepository.getDownloadedTrack(trackId)`.
  - If a completed local file exists on disk:
    - Android: ExoPlayer receives `MediaItem.fromUri("file://$localAudioPath")` directly with null network data source.
    - Desktop: libVLC loads `file://$localAudioPath` immediately.
  - If not downloaded: fall back to the standard online streaming + pre-buffering pipeline (ADR-005).

### 4. Background Execution & Platform Services
- **Android:**
  - Employs a foreground `DhunDownloadService` displaying a persistent notification showing download progress, current track title, and active queue count.
  - Utilizes `WorkManager` for resilient background scheduling when Wi-Fi only download constraint is enabled.
- **Desktop (Windows/macOS/Linux):**
  - Coroutine worker pool managing up to 3 concurrent downloads with automatic backoff and rate pacing to prevent InnerTube rate limits.

### 5. UI & UX Integration
- **Download Actions:**
  - Individual track download option in `TrackOverflowDialog` and `FullPlayer`.
  - "Download all" action button in `PlaylistScreen` and `AlbumScreen`.
- **Downloaded Folder & Section:**
  - Dedicated "Downloaded" view / card in `LibraryScreen` displaying total downloaded tracks, offline search, and total storage footprint.
- **Visual Badging:**
  - Distinct download checkmark badge icon (`DhunIcon.CheckCircle` / `DhunIcon.DownloadDone`) on all downloaded tracks across search, playlists, and full player.

## Storage Management & Eviction
- User-controlled storage screen showing:
  - Total DHUN download size in MB / GB.
  - Individual track deletion option (removes audio, artwork, and database row).
  - "Delete All Downloads" confirmation modal.
- Storage directories:
  - **Android:** `context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)` or `context.filesDir/downloads`.
  - **Windows / Desktop:** `System.getProperty("user.home") + "/.dhun/downloads"`.

## Consequences

- **Positive:** True offline freedom for users without internet connectivity.
- **Positive:** Zero latency and zero network data consumption when playing downloaded music.
- **Positive:** High resilience — partial downloads resume seamlessly without restarting from zero.
- **Neutral:** Local disk storage is allocated for downloaded audio files and artwork as managed by the user.
