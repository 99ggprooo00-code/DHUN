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

## 2026-09-22 — album artwork on playback + page backdrops (session `arena/01a0c772-dhun`)

Two of the three visual defects fixed in that session land on this phase's
surfaces. Recorded here because they change what the parsers emit and what the
two pages paint; the Phase-09 descriptions above are otherwise unchanged.

- **`parseAlbumPage` stamps the header cover onto rows that carry none.**
  `parseBrowseSongRow` used to call `thumbnailOf(item)` only, so album pages
  that YTM ships without a per-row `musicThumbnailRenderer` produced tracks
  with `thumbnailUrl = null`. Those are exactly the tracks `AlbumViewModel`
  queues (`play` / `playShuffled` / row tap), so album playback reached the
  full player, the mini player and the shell backdrop with no artwork, while
  Home / Search playback (rows that do carry art) looked fine. The row parser
  now takes a `fallbackThumbnail` and the album page passes its header cover
  (`thumbnailsLastUrl(header)`, i.e. the `croppedSquareThumbnailRenderer`) to
  both row-collection paths. It is a pass-through, never a guess: a page with
  neither row art nor a header cover still yields `null`. Row art wins where a
  page ships it. `AlbumTrackRow` renders that artwork at the playlist-row size
  (48dp `touchTarget`, `DhunShapes.medium`) beside the track number.
- **`AlbumScreen` and `ArtistScreen` are transparent and paint their own
  blurred artwork.** Both used to cover themselves with an opaque
  `DhunColors.background`, which hid the shell's `NowPlayingBackdrop` entirely
  and left the album header a flat `ArtworkColorExtractor` seed tint instead of
  blurred cover art. They now paint `PageArtworkBackdrop` — a wrapper on
  `NowPlayingBackdrop`, so the recipe *and* every pinned number
  (`NowPlayingBackdropPolicy`: list tier, 64dp blur, dim 0.40, scrim
  0.50/0.32/0.44/0.62) are the shell's, pointed at the album cover / artist
  portrait. That means the page glows with its own artwork **while nothing is
  playing**, which is when the shell backdrop has nothing to show. With no
  artwork, or on a platform that cannot really blur (`supportsRealtimeBlur`),
  nothing is drawn and the shell backdrop / base colour stays the fallback. The
  album header wash now fades to transparent instead of to opaque background;
  the artist hero's fade ends on the existing lyrics-card veil (0.62) so the
  seam into the blurred page stays soft. Accepted Home / Search / Library cards
  were not restyled to do this.
- **Not changed here:** `PlaylistScreen` still paints an opaque background — the
  same defect class, deliberately left out of scope for that session and
  recorded in `.ai/KNOWN_LIMITATIONS.md`.


YouTube is unreachable from the authoring sandbox (egress blocked; only
GitHub allowed), so the three new fixtures are **schema-authored against the
shapes of the existing captured fixtures** (they show the same renderer
structure as `browse-home.json`) — same convention the repo already uses for
synthetic samples:

- `tests/fixtures/browse-artist-queen.json` (also in jvmTest resources)
- `tests/fixtures/browse-album-anato.json`
- `tests/fixtures/browse-playlist-todays-hits.json`
- `tests/fixtures/browse-album-anato-no-row-thumbs.json` (added 2026-09-22,
  also in jvmTest resources) — the album fixture above with all six per-row
  `thumbnail` renderers removed, i.e. the shape YTM really ships when the cover
  exists only on the header's `croppedSquareThumbnailRenderer`. Regression for
  album rows inheriting that cover, so album playback has artwork.

**Live re-capture** (per MASTER_PROMPT "fixtures captured for tests") is
scheduled for the next network-capable session; the daily rot drill will
flag drift.

## Tests (jvmTest, no network)

- `ParserFixtureTest` +3: artist (name, monthly listeners, top-songs order +
  ids/durations + playlist id, albums/singles/featured/related, about),
  album (meta, artistId, 6 ordered tracks, duration parsing), playlist
  (author/count, 5 ordered tracks).
- `ParserFixtureTest` +3 (2026-09-22, album artwork): rows with no thumbnail
  inherit the header cover; rows that do carry one keep it (the cover must not
  overwrite real per-row art); a page with no artwork anywhere invents no
  thumbnail.
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
