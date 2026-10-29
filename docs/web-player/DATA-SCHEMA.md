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

At startup, accessing the storage property and probing writes are guarded.
A failed write probe does **not** discard an existing readable library. Each
save retries and confirms a write by reading it back; quota/permission recovery
can therefore work in the same tab. Ordinary failed edits remain useful in
memory, with a visit-only warning rather than a durable-success claim. An older
saved copy may still exist; export before closing when persistence is uncertain.

Erase, playlist deletion/removal, recent-search clear and replacement import use
`save(..., { requirePersistence: true })`: previous in-tab data is published over
only after storage acknowledges the candidate. Failure retains that data and,
for confirmations, an inline announced error with retry/cancel. This is a
single-key browser acknowledgement, not a cross-tab transaction, indefinite
retention promise or deletion of provider logs. Browser site-data controls clear
the **origin**, potentially affecting other projects on the same host.

History is recorded once per playback selection, not every progress event.
Clearing while a track continues does not immediately recreate its history;
a later selection can create new history. Storage key and export format remain
**v3**; no existing library is migrated or discarded by this change.

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
| Likes | 2,000 | New likes are refused with a message until one is removed. |
| History | 200 | The oldest entry is dropped. |
| Playlists | 200 | Creation is refused with a message until one is deleted. |
| Tracks per playlist | 1,000 | New tracks are refused with a message until one is removed. |
| Recent searches | 10 | The oldest search is dropped. |
| Snapshots | None of their own | Snapshots exist only for ids the likes, history or playlists refer to, so the total is bounded by the limits above. Unreferenced snapshots are removed (`pruneSnapshots`). |

History and recent searches roll over silently. The saved-collection limits
refuse new entries rather than evicting existing ones. Loading an over-limit
stored document still truncates lists during normalization; import rejects it.

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
uploaded. Before the download starts, the app validates the serialized backup
against the restore policy. If it cannot be restored by this version, the visitor
is warned and may still download the complete file for recovery; it is not
truncated. Serialization failures are reported without a success message.

**Import** a file that Export library wrote (Settings → This browser → Import library):

1. The file must be at most **24 MiB** (25,165,824 UTF-8 bytes), contain at
   most **1,500,000 JSON tokens** and have at most **128 nested containers**.
   Token and depth checks run before JSON parsing; existing collection limits
   remain in force. The file must be valid JSON with no duplicate object keys and
   no numeric tokens that would change value on re-export (including negative zero),
   `app: "DHUN web"`, and `version: 3` (`FORMAT_VERSION`).
2. All six library fields in §2 must be present with the documented array/object
   shapes. Playlists need an id, nonempty name and track-id array; snapshot ids
   must match their keys. Unknown top-level fields are rejected except the export
   metadata (`app`, `version`, `exportedAt`).
3. Existing §4 collection limits, 120-character playlist names, 400-character
   descriptions and 40-character `sourceId` apply. Duplicate likes, playlist
   ids, playlist track ids or searches that collapse on normalization are refused.
   Invalid nested values, unsupported snapshot fields, orphan snapshots and
   values that would be clamped, truncated or discarded are refused with a reason.
   This also rejects older exports carrying obsolete equalizer settings rather
   than silently discarding them. Keep the original file and inspect it if refused.
4. Compatibility conversions already used by the storage reader are accepted when
   they preserve the supplied values: numeric ids to strings, numeric strings to
   numbers (only when their decimal value survives conversion), whitespace trim,
   historical string history entries, and defaults for missing optional nested
   fields/settings. Missing snapshots for referenced ids are permitted for older
   libraries; such rows may not be visible until their metadata is recovered.
5. Validation and serialization finish before any library write. A pre-write
   read rejects a library already changed since this tab last observed it. A
   candidate is then written to the single storage key and read back before
   replacing in-tab data. If a write throws and readback confirms the previous
   value, the saved library is unchanged. If readback fails or differs after
   an attempted write, the durable outcome is **unconfirmed**: it may contain
   the import or another tab's data while this tab retains its previous library.
   After an unconfirmed outcome or detected conflict, this tab will not write
   again; ordinary edits are visit-only. Export the in-tab library, keep the
   original file, and open a fresh tab to review the saved library. There is no
   cross-tab transaction or compare-and-swap: a concurrent write between the
   pre-check and replacement can still be overwritten. The app does not roll
   back blindly, because that could overwrite another tab's changes. The backup
   file itself is never modified.

The 24 MiB budget accommodates the reproduced 9.8 MB valid export but is not a
promise that every library fits: browser memory and quota differ. The complexity
budgets also reject unusually wide or deeply nested extension data, even below
24 MiB. A file within these limits can still fail when browser storage is full or
unavailable. Keep the original file and existing library; free storage, try a
browser/device with sufficient capacity, or seek recovery help. Do not delete
the only copy to make room. No other format version is imported.

Startup's permissive recovery (§5) is unchanged; import is intentionally stricter
because it replaces a library. The post-write uncertainty and concurrent-write
race described above remain limitations; the import is not fully transactional.
The guard is per tab and does not stop another open tab from writing.

A narrowly scoped follow-up would migrate the library and a revision to an
IndexedDB object store and commit replacements in a read-write transaction that
checks the revision. Migration must retain the legacy localStorage value until
verified, coordinate older tabs and downgrade behavior, test aborted and
interrupted transactions and browser storage policies, and avoid claiming
protection against later site-data eviction. Until that work is verified, do not
claim every unsuccessful import leaves the saved copy unchanged.

## 7. What is not stored

- No audio, no artwork files, no catalogue responses (other than the track
  snapshot fields listed in §2).
- No account identifier, no device identifier, no IP address, no analytics
  identifier. The build has no code that writes one.
- No credentials. The catalogue is called without cookies (`credentials: "omit"`).
