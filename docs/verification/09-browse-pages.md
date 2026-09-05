# Phase 09 verification — Artist / Album / Playlist pages

Status: 🟨 **CODE COMPLETE** — units tested in CI; on-hardware acceptance
(3 artists / 3 albums) OPEN alongside Phase 03–05 hardware checklist.

## Built

- **Parsers** (`shared/innertube/BrowseParsers.kt`): `parseArtistPage`,
  `parseAlbumPage`, `parsePlaylistPage` — shape-tolerant renderers walkers
  (singleColumn + twoColumn + fallback collection). Search/home/radio track
  rows now also carry `artistId`/`albumId` from subtitle run navigation
  endpoints (`browseIdsOf`).
- **Client/provider**: `InnerTubeClient.artistPage/albumPage/playlistPage`
  (browse endpoint) → `MusicProvider` interface + `YouTubeMusicProvider`.
- **Entities**: `ArtistPage`, `AlbumDetail`, `PlaylistDetail`.
- **ViewModels** (`shared/presentation/browse/`): `ArtistViewModel`
  (load/playTopSongs/shuffle/radio via `/next` of top song),
  `AlbumViewModel` (ordered play/shuffle), `PlaylistViewModel` (remote +
  local CRUD: rename/delete/remove/move + `deleted` flow).
- **Screens** (`shared/ui/browse/`): `ArtistScreen` (parallax artwork header,
  collapse-on-scroll glass toolbar, Shuffle/Radio actions, Top songs with
  rank+overflow, Albums/Singles/Featured/Fans-might-also-like carousels,
  About glass card), `AlbumScreen` (artwork-tinted animated header, ordered
  numbered tracks, "More by artist"), `PlaylistScreen` (remote + local edit:
  rename dialog, delete confirm, swipe-remove, drag-reorder via
  `ReorderableList`).
- **Navigation**: `AppNavState` (selectedTab + detailStack + playerExpanded,
  `closeTop()` contract used by the Android BackHandler; desktop uses on-page
  ← Back buttons). Overflow "Go to artist/album" navigates by id when the
  parsers yielded one, else falls back to filtered search (Phase 07 path).

## Fixtures

YouTube is unreachable from the authoring sandbox (egress blocked; only
GitHub allowed), so the three new fixtures are **schema-authored against the
shapes of the existing captured fixtures** (they show the same renderer
structure as `browse-home.json`) — same convention the repo already uses for
synthetic samples:

- `tests/fixtures/browse-artist-queen.json` (also in jvmTest resources)
- `tests/fixtures/browse-album-anato.json`
- `tests/fixtures/browse-playlist-todays-hits.json`

**Live re-capture** (per MASTER_PROMPT "fixtures captured for tests") is
scheduled for the next network-capable session; the daily rot drill will
flag drift.

## Tests (jvmTest, no network)

- `ParserFixtureTest` +3: artist (name, monthly listeners, top-songs order +
  ids/durations + playlist id, albums/singles/featured/related, about),
  album (meta, artistId, 6 ordered tracks, duration parsing), playlist
  (author/count, 5 ordered tracks).
- `BrowseViewModelTest`: artist success/error, album ordered play + shuffle,
  remote playlist play, local playlist CRUD (create→add→reorder→remove→
  rename→play→delete) incl. `deleted` flow.

## On-hardware acceptance (OPEN)

- [ ] Artist page for 3 artists: all sections correct
- [ ] Album track order correct for 3 albums
- [ ] Local playlist CRUD + reorder verified in-app (both platforms)
- [ ] Overflow "Go to artist/album" lands on the detail page

## Known gaps (carry to KNOWN_LIMITATIONS)

- YTM "Videos" shelf on artist pages is skipped (v1 scope).
- Artist-page browses ("show all") use `topSongsPlaylistId` when present but
  the sub-page itself is Phase-09 follow-up work.
- YouTube browse layout drift risk is real; parsing is tolerant + rot drill
  watches endpoints.

## M3 glass browse (2026-09-05)

| Item | Status |
|---|---|
| Artist frosted collapse toolbar | 🟨 |
| Album/Playlist frosted track rows | 🟨 |
| Floating frosted back chip | 🟨 |
| Hardware checklist | ⬜ still OPEN |
