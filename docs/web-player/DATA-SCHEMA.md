# Data schema — DHUN Web (`/app/`)

The web counterpart of [`docs/backend-schema.md`](../backend-schema.md). The
browser build has **no backend and no database**. Everything a visitor keeps is
one JSON document in the browser's `localStorage`, under one key. This file
describes that document, the limits on it, how a stored document is read back,
and the export file. The code is in `app-web/src/js/library.js`.

## 1. Storage

| Item | Value |
|---|---|
| Store | `window.localStorage` (origin-scoped) |
| Key | `dhun.web.v3` (`STORAGE_KEY`) |
| Encoding | UTF-8 JSON, one object |
| Written by | `createStorage().save()`, on every change |
| Read by | `createStorage().load()`, once at boot |
| Other keys | None. The build does not read keys written by earlier builds. |

At start-up the build probes whether storage accepts a write (`persistent`). If a
later save is refused (private mode, or storage full), the library stays in
memory for the rest of the visit and the save is not retried. Settings then says
the browser is "not saving data … and is lost when the tab closes". Whether every
browser reports a full store in the same way is not verified.

## 2. Document shape

```json
{
  "likes":         ["<song id>", "..."],
  "playlists":     [{ "id": "...", "name": "...", "description": "",
                      "trackIds": ["<song id>", "..."],
                      "createdAt": 0, "updatedAt": 0 }],
  "history":       [{ "id": "<song id>", "at": 0 }],
  "snapshots":     { "<song id>": { "...the song fields the rows draw..." } },
  "recentSearches": ["..."],
  "settings":      { "theme": "dark", "accent": "brand", "volume": 0.8,
                     "repeat": "off", "shuffle": false, "sourceId": "" }
}
```

- `likes` and `history` hold ids. `history` entries carry the time they were
  played.
- `snapshots` holds one **copy of each track's display fields** (title, artwork
  reference, artists, album, duration and similar), keyed by song id. Playlists,
  likes and history draw their rows from snapshots, so they still show when the
  catalogue cannot be reached. A snapshot is kept only while a like, a history entry
  or a playlist refers to its track; it is removed on save and on read otherwise.
  A snapshot is written when a track is liked, added
  to a playlist, or played.
- Playlists store track ids and look the rows up in `snapshots`.
- `settings` holds only the values the interface can honour. Unknown values fall
  back to the defaults.

Allowed values:

| Setting | Values | Default |
|---|---|---|
| `theme` | `dark`, `light`, `system` | `dark` |
| `accent` | `brand`, `azure`, `jade`, `amber`, `rose`, `cyan` | `brand` |
| `volume` | number from 0 to 1 | `0.8` |
| `repeat` | `off`, `all`, `one` | `off` |
| `shuffle` | boolean | `false` |
| `sourceId` | text, up to 40 characters | `""` (the first source in the list). Written when failover moves to another catalogue origin, so the next visit starts there. The visitor does not choose it. |

## 3. Names and text

- A playlist name is trimmed and cut to 120 characters. A playlist description is
  cut to 400 characters.
- A playlist name cannot be empty; an empty name is refused and nothing is saved.
- Playlist ids are generated from the creation time.

## 4. Limits

These are enforced when a list is written. Each limit is in
`LIBRARY_LIMITS` in `library.js`.

| List | Limit | When the limit is reached |
|---|---|---|
| Likes | 2,000 | The oldest like is dropped. |
| History | 200 | The oldest entry is dropped. |
| Playlists | 200 | The oldest playlist (last in the list) is dropped. |
| Tracks per playlist | 1,000 | The newest added track is not kept. |
| Recent searches | 10 | The oldest search is dropped. |
| Snapshots | None of their own | Snapshots exist only for ids the likes, history or playlists refer to, so the total is bounded by the limits above. Unreferenced snapshots are removed (`pruneSnapshots`). |

Dropping is silent: nothing tells the visitor that the oldest entry went.
[KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) lists it as a limit a visitor can
meet.

## 5. Reading a stored document back

`normaliseLibrary` takes whatever `localStorage` returns and produces a valid
library. It does this without throwing:

- A value that is not an array is treated as an empty array.
- Each list is capped at its limit on the way in.
- A playlist without a usable id or name is removed.
- A snapshot whose id does not match its key is removed.
- Numbers are clamped; invalid settings fall back to defaults.

A corrupt document therefore gives a usable, possibly smaller library. It does
not give an error screen. Nothing in the build backs up the previous value before
normalising, so a corrupt document that is saved over is lost.

## 6. Export and import

**Export** (Settings → This browser → Export library) writes:

```json
{ "app": "DHUN web", "version": 3, "exportedAt": "<ISO time>", "...the library..." }
```

The file is created in the browser and downloaded by the visitor. Nothing is
uploaded.

**Import** a file that Export library wrote (Settings → This browser → Import library):

1. The file must be at most **8 MB**.
2. It must be valid JSON.
3. Its `app` field must be exactly `DHUN web`. Any other file is refused with the
   reason.
4. The document is normalised (§5) and **replaces** the library on this device,
   then is saved.

The file's `version` must be `3` (`FORMAT_VERSION`). Any other version, or none, is
refused with the version named, and the library is not touched. A future format
needs its own reader before its files can be imported.

## 7. What is not stored

- No audio, no artwork files, no catalogue responses (other than the track
  snapshot fields listed in §2).
- No account identifier, no device identifier, no IP address, no analytics
  identifier. The build has no code that writes one.
- No credentials. The catalogue is called without cookies (`credentials: "omit"`).
