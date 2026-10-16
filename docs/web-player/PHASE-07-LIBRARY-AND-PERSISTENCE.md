# Phase 7 — Library, playlists and persistence

> Status: ✅ Complete
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

The parts of a music client that make it yours: playlists, liked tracks, recent
listening, and settings that survive a reload — in a browser, where "storage" is
a promise the browser can break at any time.

## Acceptance criteria

- [x] The library survives a reload and a browser restart — playlists, likes and
  history are written to IndexedDB.
- [x] Settings survive a reload — theme, accent, volume, source choice, equaliser
  and playback preferences live in `localStorage`.
- [x] Storage being unavailable is a *state*, not a crash: a private window that
  refuses IndexedDB gets an in-memory library and an on-screen notice saying
  the library will not be kept.
- [x] A write failure does not silently discard the user's action — it surfaces.
- [x] The library can be written out to a file and read back, as a copy the user
  owns.
- [x] Removing data is possible and explicit (clear history, clear recent
  searches, delete a playlist).
- [x] The Library screen has exactly the tabs the browser can honour — playlists,
  liked, history.

## What exists

| Piece | Where |
|---|---|
| Settings store, library store, session read/write, export/import | `app-web/src/js/storage.js` |
| Where the library lives, and what happens when it cannot | `platform.js` (`indexedDbWorks`, `storageWorks`) |
| Library, playlist and liked screens | `views.js` (`libraryScreen`, `playlistScreen`) |
| No-storage notice and its settings action | `views.libraryScreen`, `main.js` |

## Why there is no downloads tab

The native builds can keep audio offline; a browser page cannot keep a signed
stream URL working, and the project does not operate a service to hand one out.
Rather than render a tab that explains it cannot work, the web app's Library is
playlists, liked tracks and history. `scripts/test_app_web.py` fails if a
device-side download surface or a cache-budget setting comes back, because a
control that explains its own uselessness is worse than an absent control.

## Risks identified

- **Eviction.** Browsers may clear storage under pressure, and a user may clear
  site data. Both are documented in
  [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) rather than papered over with a
  "your data is safe" claim.
- **Two stores, one mental model.** Settings in `localStorage` and the library in
  IndexedDB is two mechanisms, chosen for two different shapes of data; the
  export path reads both so a user's copy is complete.

## Test results

| Test | Result |
|---|---|
| `app-web/tests/storage.test.mjs` | Pass — settings, library, export/import, malformed input |
| `app-web/tests/platform.test.mjs` | Pass — storage probes, including a store that throws on write |
| `app-web/tests/views.test.mjs` | Pass — the no-storage notice and the library's empty states |
| Reload persistence in a real browser | **Not tested** — the same browser gap as Phase 2 |

## Completion decision

- [x] Acceptance criteria met
- [x] Tests pass, in the files named above
- [x] Documentation: this file, and the storage section of
  [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md)
- [ ] Reload persistence confirmed by hand in a real browser

**Go / No-Go: Go.**
