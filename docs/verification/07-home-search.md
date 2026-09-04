# Phase 07 — Home & Search: Verification Log

**Date:** 2026-09-04
**Session:** `arena/01a06a42-dhun`
**PR:** TBD

## What was built

### Shared (`shared/src/commonMain/kotlin/dev/dhun/`)

**Core entities (`core/Entities.kt`):**
- `HomeSection(title, browseId, items)` — a horizontal shelf on the YTM home feed
- `HomeItem` sealed interface — `TrackItem`, `AlbumItem`, `ArtistItem`, `PlaylistItem`

**InnerTube client (`innertube/InnerTubeClient.kt`):**
- `homeFeed()` — POST `/browse` with `FEmusic_home` browseId
- `browse(browseId)` — generic browse (albums, playlists)
- `artist(browseId)` — artist page
- `searchContinuation(token)` — paginated results

**Parsers (`innertube/Parsers.kt`):**
- `parseHomeSections(root)` — walks `sectionListRenderer` → `musicShelfRenderer` → items
- `parseTwoRowItem(renderer)` — album/artist/playlist/track from the two-row grid format
- `parseResponsiveListItem(renderer)` — track from the responsive list format
- `SearchContinuation` data class — continuation result holder
- `parseSearchContinuation(root)` — walks `continuationContents.musicShelfContinuation`

**UI (`ui/`):**
- `ui/navigation/Screen.kt` — `Home`, `Search`, `Library`, `Player`, `Artist(browseId)`, `Album(browseId)`, `Playlist(browseId)`
- `ui/navigation/AppNavigator.kt` — `AppShell` + `BottomNavBar` (no library dependency)
- `ui/home/HomeViewModel` + `HomeScreen` — greeting, quick picks, section rows/grids, shimmer, pull-to-refresh
- `ui/home/HomeViewModelFactory.kt`
- `ui/search/SearchViewModel` + `SearchScreen` — debounced suggestions, filter chips, infinite scroll, overflow
- `ui/search/SearchViewModelFactory.kt`

**Platform wiring:**
- Android `di/AppModule` — `HomeViewModelFactory`, `SearchViewModelFactory` in Koin
- Android `MainActivity` — shows `AppShell` when `ConnectUi.Ready`
- Desktop `Main.kt` — `InnerTubeClient` in Koin, `AppShell` replacing harness

**Tests:**
- `HomeParserTest` — albums, artists, playlists, tracks, skip empty sections, skip "Listen again"
- `SearchFilterTest` — displayNames, params, HomeItem type safety, HomeSection creation
- `parseSearchContinuation` — continuation token, songs extraction, empty continuation

### New files in this commit

```
shared/src/commonMain/kotlin/dev/dhun/
├── core/Entities.kt (HomeSection, HomeItem)
├── innertube/
│   ├── InnerTubeClient.kt (homeFeed, browse, artist, searchContinuation)
│   └── Parsers.kt (parseHomeSections, parseTwoRowItem, parseResponsiveListItem, SearchContinuation, parseSearchContinuation)
└── ui/
    ├── home/
    │   ├── HomeViewModel.kt
    │   ├── HomeViewModelFactory.kt
    │   └── HomeScreen.kt
    ├── search/
    │   ├── SearchViewModel.kt
    │   ├── SearchViewModelFactory.kt
    │   └── SearchScreen.kt
    └── navigation/
        ├── Screen.kt
        └── AppNavigator.kt
shared/src/jvmTest/kotlin/dev/dhun/innertube/
├── HomeParserTest.kt
└── SearchFilterTest.kt
docs/verification/07-home-search.md
```

## Acceptance criteria

| # | Criterion | Status |
|---|-----------|--------|
| 1 | Home renders real YTM content on Android + Desktop | ⬜ hardware |
| 2 | Search: suggestions ≤300ms after pause | ⬜ hardware |
| 3 | Search: results per filter (6 filters) | ⬜ hardware |
| 4 | Search: infinite scroll (continuation) | ⬜ hardware |
| 5 | Every overflow action (favorite, add to queue, add to playlist) works | ⬜ hardware |
| 6 | Loading skeleton (not spinner) | ✅ code |
| 7 | Error and empty states all observed | ⬜ hardware |
| 8 | Both platforms | ⬜ hardware |

## Hardware verification checklist

### Android
- [ ] Home screen: greeting ("Good morning/afternoon/evening") visible
- [ ] Home screen: "Listen again" quick picks show recently played tracks
- [ ] Home screen: YTM sections load with artwork (album cards, track rows)
- [ ] Home screen: pull-to-refresh refreshes sections
- [ ] Search: tap search bar → keyboard appears, auto-focus
- [ ] Search: type "bohemian" → suggestions appear within 300ms
- [ ] Search: tap suggestion or press enter → results load
- [ ] Search: filter chips (All/Songs/Videos/Artists/Albums/Playlists) switch results
- [ ] Search: scroll to bottom → more results load (pagination)
- [ ] Search: tap a track → audio plays
- [ ] Search: favorite button (♥/♡) toggles correctly
- [ ] Navigation: bottom nav (Home / Search / Library) navigates correctly
- [ ] Error state: airplane mode → error message + Retry button
- [ ] Empty state: search for nonsense → "No results" message

### Desktop
- [ ] Same Home screen checks as Android
- [ ] Same Search screen checks as Android
- [ ] Window resize doesn't break layout
- [ ] No Java exceptions in console on cold start

## Known limitations

- Home sections: "Listen again" banner sections are skipped (ephemeral YTM content)
- Home: browse navigation (tap album/artist/playlist → Phase 09 stub) shows placeholder
- Search: "MoreVert" overflow icon drawn with Canvas dots (placeholder; Phase 08+ replaces with proper icon)
- Search: navigation to album/artist/playlist detail pages is Phase 09 stub
- Player: mini-player not yet built (Phase 08)
