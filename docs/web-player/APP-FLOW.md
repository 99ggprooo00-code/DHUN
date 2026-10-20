# App flow — DHUN Web (`/app/`)

The web counterpart of [`docs/app-flow.md`](../app-flow.md). It describes the
screens, the routes that reach them, and the flows a visitor follows. Routes are
parsed in `app-web/src/js/router.js` and screens are drawn in
`app-web/src/js/views.js` and `app-web/src/js/player-ui.js`.

## 1. Routes

The app uses a **hash router**, so every route is served by the one static
`index.html` and no server rewrite is needed.

| Hash | Screen | Notes |
|---|---|---|
| `#/` (or empty) | Home | Seven shelves: trending, Hindi, chill, Punjabi, new albums, artists, playlists. |
| `#/search?q=…` | Search | Results for songs, albums, artists and playlists. `q` is the query. |
| `#/album/{id}` | Album | Tracks of one album. |
| `#/artist/{id}` | Artist | Songs and albums of one artist. |
| `#/playlist/{id}` | Playlist | Tracks of a catalogue playlist. |
| `#/collection/{id}` | Collection | A saved collection of the device library. |
| `#/library/playlists`, `#/library/likes`, `#/library/history` | Library | Three tabs over the device's own data. Any other tab name opens Playlists. |
| `#/settings` | Settings | Sections: Appearance, Playback, Catalogue, This browser, About. |
| `#/about`, `#/about/{document}` | About | Versions and the legal texts: `about`, `privacy` (default), `terms`, `licences`, `support`. |

Any hash whose first segment is not a known name opens **Home**. The router has
no separate not-found screen. The manifest's shortcuts open `#/search` and
`#/library/playlists`.

The player is not a route of its own. It opens over the current screen. The
address may carry `player=open`, and `ptab` selects its Queue or Lyrics tab.

## 2. Boot

1. The framing check runs first. If the page is inside a frame, the framed
   refusal screen is drawn and nothing else starts.
2. The library is read from `localStorage`. A missing or unreadable record starts
   an empty library. It is not shown as an error.
3. The router, player, shell and shortcuts are created.
4. The service worker is registered (see [TRD.md](TRD.md) §6).
5. Home loads its shelves if the route is Home. A search route in the address
   bar runs its query.

## 3. Flows

### 3.1 Find and play a track

```
Home or Search  →  track row  →  Play
                               ↓
                    player shows "loading"
                               ↓
          stream URL from the catalogue  →  <audio> plays
                               ↓
          "playing", with a clock drawn from the media element
```

- A row with no stream URL is not played. The player reports it instead of
  loading an empty source. A sample row says it is a sample.
- A browser that refuses to start audio without a gesture shows the **blocked**
  state; pressing play again is the recovery. See [PLAYBACK.md](PLAYBACK.md).

### 3.2 Save to the library

- Liking a track, creating a playlist and adding a track to one all write to the
  library on this device. The record keeps the track's id and the time it was
  added, not its audio. Audio is read again from the catalogue when the track
  plays.
- When a list is full, the oldest entry is dropped. The limits are in
  [DATA-SCHEMA.md](DATA-SCHEMA.md) §4.
- If the catalogue no longer returns a saved track, the player says the row has no
  audio URL and tells the visitor to open the track from its source.

### 3.3 Search

- The query is taken from the search field, or from `q` in the address. Each
  query is recorded in recent searches, which keep the 10 most recent.
- Songs, albums and playlists requests carry a page number; the catalogue returns
  up to 20 results per page (`PAGE_SIZE`).

### 3.4 Failure paths

- **Catalogue timeout, offline or HTTP error:** the screen states what happened
  in a sentence the build wrote. The raw status and URL are under "Technical
  detail". The screen offers "Try again".
- **Every catalogue source fails:** Home can show the sample catalogue. Every
  sample row is labelled as a sample on screen, and playing one says it is a
  sample with no audio.
- Saved library content is on the device and does not depend on the catalogue.

### 3.5 Export and import

- Settings → This browser → **Export library** writes a JSON file (`app:
  "DHUN web"`, `version: 3`). The file is saved by the browser; nothing is
  uploaded.
- Settings → This browser → **Import library** accepts a file of at most 8 MB
  whose `app` field is `DHUN web`. Any other file is refused with the reason
  shown. A valid file replaces the library on this device.

### 3.6 Keyboard

- `?` opens the shortcut sheet. `Esc` closes a sheet or the player, from anywhere
  including a text field. The other shortcuts are not taken from a text field.

### 3.7 Install and update

- When the browser offers installation, the app shows an install button. Once
  installed, the app reports so.
- When a new version has been downloaded, the app shows a notice: close this tab
  and open DHUN again to use it. The new version takes over when no DHUN tab is
  open. The app does not reload the page on its own. The function that could
  apply the update immediately exists in `pwa.js` but no control calls it; this
  is recorded in [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md).

## 4. Screen states

Each data screen is rendered in its loaded and empty states, and in its failed
state, by `app-web/tests/views.test.mjs`. An empty screen says how to fill it:
for example, an empty library says "No playlists yet". A failed Home offers a way
to try again. Loading states and the sample state are drawn by the same screens
but are not separately asserted in that test file.
