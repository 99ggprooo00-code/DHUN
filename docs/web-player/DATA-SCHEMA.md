# Data schema — DHUN Web (`/app/`)

The browser build has no backend. Its source of truth is an origin-scoped
IndexedDB database, not the legacy localStorage key. The library payload remains
v3 JSON; the database envelope has a separate schema and revision.

## 1. Storage and transaction boundaries

| Item | Value |
|---|---|
| Canonical database / object store | `dhun.web.library` / `library` |
| Records | `current`, `previous`, and optional `legacy-recovery:*` archives |
| Current envelope | schema 1, revision, serialized library, legacy baseline, optional recovery epoch |
| Legacy input | `localStorage` key `dhun.web.v3`, retained and never written by the new app |
| Implementation | `indexeddb-storage.js`, `app-storage.js`, `main.js` |

Startup waits for validation and migration before enabling the app. With no
current or previous record, a valid legacy library (or an empty library when
there is no legacy key) is installed transactionally at revision 1. Existing
current data is never replaced by migration. The legacy key remains untouched;
interrupted migration can be retried. Invalid, unreadable, missing/inconsistent
records or unavailable IndexedDB open a recovery screen, not an empty writable
fallback. A missing database and missing legacy key cannot reveal whether a
browser previously evicted or cleared data.

Each mutation validates before a read-write transaction that checks the tab's
expected revision and recovery epoch and atomically writes the previous and current records. Only
transaction completion acknowledges success. The UI gates overlapping writes,
serializes background changes and waits for destructive actions and imports.
An abort leaves the preceding transaction intact; a conflict blocks further
writes in that tab. Ordinary in-tab changes can remain visit-only, with a warning
and an explicitly labelled export. Reload/review the saved data before retrying.
Export reads a consistent committed snapshot without advancing a stale tab's
expected revision; it does not authorize a later overwrite.

Erase clears the **current** library while retaining settings and shell cache.
The preceding recovery record, old localStorage key and archives can still
contain removed data. This is not secure deletion; browser site-data controls
clear the whole origin, including settings and unrelated projects. Export needed
data first. A later track selection may record new history.

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
refuse new entries rather than evicting existing ones. An over-limit stored document is guarded and requires recovery; import rejects it.

## 5. Reading a stored document back

Startup validates library text and envelope metadata before permitting writes.
Unsupported, lossy, malformed or over-budget data is left in place and presented
for recovery, not normalized into a writable fallback. Raw legacy text and JSON
representations of supported database records can be downloaded; a record that
cannot be represented as JSON remains in browser storage. Validated importable
backups are offered only when the relevant data passes the full backup policy.
Neither validation nor recovery can prove data was not lost before these copies
were written.

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
5. Validation and serialization finish before any write. A read-write IndexedDB
   transaction compares the expected revision and commits the validated replacement
   and previous record together. An abort does not publish the candidate. A
   concurrent revision conflict blocks further writes and keeps the prior in-tab
   state. An operation may nevertheless be unconfirmed if legacy storage changes
   around transaction completion; saved data may have changed. Keep the backup,
   export any visit-only changes and review in a fresh tab.

The 24 MiB budget accommodates the reproduced 9.8 MB valid export but is not a
promise that every library fits: browser memory and quota differ. The complexity
budgets also reject unusually wide or deeply nested extension data, even below
24 MiB. A file within these limits can still fail when browser storage is full or
unavailable. Keep the original file and existing library; free storage, try a
browser/device with sufficient capacity, or seek recovery help. Do not delete
the only copy to make room. No other format version is imported.

### Older versions and recovery

IndexedDB and localStorage cannot participate in one transaction. An old open tab
or downgraded build can still write the legacy key, even after migration. New
sessions compare its exact text to the recorded baseline on open, before a write
and after commit. A detected mismatch stops writes and preserves both sides; a
post-commit mismatch is reported as unconfirmed, not as a failed transaction.
A legacy write can occur immediately after any check, so detection is not a
lock or a guarantee of immediate notification. Close old tabs before review.

The recovery screen offers separate raw representations and, where valid,
importable canonical and legacy backups. After review, the visitor may explicitly
retain the canonical database library, or replace it with the validated legacy
library, while archiving both database records, the differing legacy text and
prior baseline in the database. The transaction rechecks the reviewed
canonical record and legacy text. It never modifies the legacy key. If either
changes again, review must be repeated; a post-commit race is unconfirmed.
There is no automatic merge or automatic selection of either side. When the
current key is truly missing and the previous record is valid, recovery offers
a validated previous backup and an explicit restore. It rechecks absence, the
previous record and legacy baseline transactionally before installing it. A
fresh random epoch prevents an old tab from matching a reused revision. A
previous record may omit newer changes. If current is corrupt rather than missing,
no automatic replacement is offered; preserve copies and seek recovery help.
Browser eviction, device failure, quota, memory pressure and unrepresentable
corrupt records remain outside the transaction guarantee.

## 7. What is not stored

- No audio, no artwork files, no catalogue responses (other than the track
  snapshot fields listed in §2).
- No account identifier, no device identifier, no IP address, no analytics
  identifier. The build has no code that writes one.
- No credentials. The catalogue is called without cookies (`credentials: "omit"`).
