# Phase 07 Verification — Home & Search

**Date:** 2026-09-04  
**Branch:** `arena/01a06a81-dhun`  
**Phase:** 07 — Home & Search  
**Status:** CODE COMPLETE & READY FOR HARDWARE VERIFICATION

---

## 1. Objectives & Scope

Phase 07 delivers the primary discovery and browsing interface of DHUN:
1. **Shared `HomeScreen` & `HomeViewModel`:**
   - Dynamic time-of-day greeting ("Good morning", "Good afternoon", "Good evening", "Good night").
   - 3×2 Quick Picks grid using compact glassy cards (`QuickPickItem` with artwork, title, artist, overflow menu).
   - "Listen again" row populated from local database history (`HistoryRepository`).
   - Dynamic InnerTube browse sections (`FEmusic_home`) displaying horizontal carousels of tracks, albums, playlists, and artists (`TrackCard`, `AlbumCard`, `PlaylistCard`, `ArtistCard`).
   - Shimmer skeleton loading state with zero generic spinners (`HomeShimmerSkeleton`, `SectionShimmer`, `TrackRowShimmer`).
   - Clean error state (`ErrorView`) and empty state (`EmptyView`) with retry actions.
   - Pull-to-refresh / header refresh action.
2. **Shared `SearchScreen` & `SearchViewModel`:**
   - Real-time 300ms debounced search suggestions from InnerTube (`/youtubei/v1/music/get_search_suggestions`).
   - Search filter chips (`DhunFilterChip`) supporting Songs, Artists, Albums, Playlists, and Videos.
   - Per-type result rendering with track rows and horizontal media shelves.
   - Infinite scroll continuation pagination (`searchContinuation` with InnerTube continuation tokens).
   - Recent searches persistence via `SearchRepository` with individual deletion and "Clear all" actions.
   - Shimmer skeleton loading state (`SearchShimmerSkeleton`).
   - Clean empty state ("No matches found for...") and error state with retry.
3. **Track Overflow Actions & Playback Context:**
   - Glassy modal action sheet (`TrackOverflowDialog`):
     - "Play next" (`player.addNext(track)`).
     - "Add to queue" (`player.addToQueue(track)`).
     - "Add to playlist…" (`AddToPlaylistDialog` with SQLDelight local playlist creation & selection).
     - "Toggle favorite" (♥/♡ synced with `LibraryRepository`).
     - "Go to artist" & "Go to album" (switches to search tab and executes query).
   - Track tap anywhere in Home or Search queues the entire context playlist and begins playback.
4. **Shared Shell Integration (`DhunAppShell`):**
   - Docked bottom `MiniPlayerBar` with 1dp progress line, live track info, and playback transport controls.
   - Glass bottom navigation bar with Home, Search, and Catalog tabs.
   - Wired in Android `MainActivity` and Desktop `Main.kt`.

---

## 2. Architecture & File Inventory

```
shared/src/commonMain/kotlin/dev/dhun/
├── core/
│   └── Entities.kt                     # Added HomeItem, HomeSection, HomeFeed, continuationToken
├── innertube/
│   ├── InnerTubeClient.kt              # Added homeFeed(), searchContinuation()
│   └── Parsers.kt                      # Added parseHomeSections(), parseContinuationToken()
├── provider/
│   └── MusicProvider.kt                # Added homeFeed(), searchContinuation()
├── player/
│   └── DhunPlayer.kt                   # Added addNext(), addToQueue()
├── domain/
│   └── UseCases.kt                     # Added GetHomeFeedUseCase
├── presentation/
│   ├── home/HomeViewModel.kt           # Home feed state machine & refresh
│   └── search/SearchViewModel.kt       # Debouncing (300ms), filter switching, pagination
├── design/components/
│   └── TrackRow.kt                     # Added onOverflowClick support
└── ui/
    ├── home/HomeScreen.kt              # Glassy Home screen with quick picks & shelves
    ├── search/SearchScreen.kt          # Debounced Search screen with chips & recents
    ├── components/
    │   ├── TrackOverflowDialog.kt      # Overflow action sheet (Play next, Queue, Playlist, Fav)
    │   └── AddToPlaylistDialog.kt      # Local playlist selector + creation
    └── shell/DhunAppShell.kt           # Shared shell with docked MiniPlayer & tab navigation

app-android/src/main/kotlin/dev/dhun/android/
├── MainActivity.kt                     # Renders DhunAppShell with AndroidDhunPlayer
├── di/AppModule.kt                     # Koin graph updated with Home/Search ViewModels & use cases
└── playback/AndroidDhunPlayer.kt       # Media3 addMediaItem implementation for addNext/addToQueue

app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/
├── Main.kt                             # Renders DhunAppShell with DesktopDhunPlayer
└── player/DesktopDhunPlayer.kt         # QueueManager addNext/addToQueue implementation

shared/src/jvmTest/
├── kotlin/dev/dhun/
│   ├── innertube/ParserFixtureTest.kt  # Unit tests for home browse & continuation parsing
│   ├── presentation/HomeViewModelTest.kt
│   └── presentation/SearchViewModelTest.kt
└── resources/fixtures/browse-home.json # Live fixture for home browse parsing tests
```

---

## 3. Automated Test Evidence

Unit tests cover:
1. `ParserFixtureTest`:
   - `parsesLiveSearchSongsFixture`: 20 songs with metadata & durations.
   - `parsesLiveRadioFixture`: 50 related tracks parsed from `/next`.
   - `parsesHomeBrowseFixture`: 2 sections (Quick picks with responsive items, Recommended albums with two-row items).
   - `parsesSuggestionsShape`: suggestion parsing.
   - `parsesLyricsBrowseIdByPrefix` & `parsesLyricsShelf`.
   - `parsesContinuationToken`: continuation token extraction.
2. `HomeViewModelTest`:
   - `greetingCalculation`: hour-based greeting verification across 24h boundaries.
   - `homeViewModelLoadsFeedAndExtractsQuickPicks`: feed loading, quick picks extraction (up to 6 tracks), section mapping.
   - `homeViewModelHandlesError`: error mapping to `HomeUiState.Error`.
   - `homeViewModelTogglesFavorites`: live favorite toggling and state emission.
3. `SearchViewModelTest`:
   - `searchExecutionAndResults`: query submission, result mapping, recent searches recording.
   - `searchContinuationInfiniteScroll`: pagination appends additional songs to results.
   - `filterSelectionUpdatesSearch`: filter change triggers search with updated filter.
   - `recentSearchesManagement`: delete search and clear all searches.
   - `addToPlaylistAction`: adds track to SQLDelight local playlist.

---

## 4. Acceptance Criteria Checklist

| # | Acceptance Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Home renders real YTM content on Android + Desktop | ✅ Ready | `HomeScreen` + `HomeViewModel` parses `FEmusic_home` into responsive shelves & quick picks |
| 2 | Search: type → suggestions ≤300ms after pause → results per filter → infinite scroll | ✅ Ready | `SearchViewModel` has 300ms debounce flow, `SearchFilter` chips, and `searchContinuation` pagination |
| 3 | Every overflow action works | ✅ Ready | `TrackOverflowDialog` wires Play next, Add to queue, Add to playlist, Favorite toggle, Go to artist/album |
| 4 | Loading skeleton (not spinner), error, and empty states all observed | ✅ Ready | `HomeShimmerSkeleton`, `SearchShimmerSkeleton`, `ErrorView`, `EmptyView` |

## M3 Home depth (2026-09-05)

| Item | Status |
|---|---|
| Sans-serif UI type / brand wordmark only | 🟨 |
| Quick-action chips (Liked, Offline, Sleep) | 🟨 |
| Mood & genre filter chips | 🟨 |
| Quick picks grid up to 12 | 🟨 |
| Classified shelves (mix/charts/albums) | 🟨 code — depends on feed titles |
| Ambient shell wash | 🟨 lightweight seed tint |
| Hardware visual pass | ⬜ OPEN |

## M3 glass Search/lists (2026-09-05)

| Item | Status |
|---|---|
| Frosted XL search field | 🟨 |
| Frosted TrackRow cells | 🟨 |
| Airier screen padding (20dp) | 🟨 |
| Hardware visual pass | ⬜ |
