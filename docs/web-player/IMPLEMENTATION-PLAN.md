# Implementation plan — DHUN Web (`/app/`)

The web counterpart of [`docs/implementation-plan.md`](../implementation-plan.md).
This plan is **not a schedule**. It lists what is built, what is known to be wrong
(found while writing these documents, and each confirmed in the code), and what
is proposed next, in dependency order. Status of each item, with its evidence, is
in [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md).

No item below is scheduled or promised. Each needs an owner decision where the
product changes, and a test before it is called done.

## 1. Built

| Area | Where | Notes |
|---|---|---|
| Shell, hash router, screens | `app-web/src/js/main.js`, `router.js`, `views.js`, `shell.js` | Seven routes plus player state; see [APP-FLOW.md](APP-FLOW.md). |
| Catalogue client and failover | `app-web/src/js/api.js` | Three origins, five failure kinds; see [PROVIDER-INTEGRATION.md](PROVIDER-INTEGRATION.md). |
| Player | `app-web/src/js/player.js`, `player-ui.js` | Queue, repeat, shuffle, failures; see [PLAYBACK.md](PLAYBACK.md). |
| Library and storage | `app-web/src/js/library.js` | One key; limits; see [DATA-SCHEMA.md](DATA-SCHEMA.md). |
| Export and import | `app-web/src/js/transfer.js`, `library.js` | 8 MB cap, `app` marker. |
| Shortcuts | `app-web/src/js/shortcuts.js` | Thirteen keys, one table; see [USER-GUIDE.md](USER-GUIDE.md) for the list a visitor sees. |
| Offline shell and install | `app-web/src/sw.js`, `pwa.js`, `manifest.webmanifest` | Shell only. |
| Legal texts | `app-web/src/js/data/web-legal.js`, `legal.js` | About, privacy, terms, licences, support. |
| Build and checks | `app-web/tools/build.mjs`, `scripts/web_player_docs.py` | See [TESTING.md](TESTING.md). |

## 2. Defects found while documenting, and what was done

Each was confirmed in the code and reproduced by a test before the fix.

| ID | Defect | Resolution |
|---|---|---|
| WD-1 | `LIBRARY_LIMITS.snapshots` (2,500) was declared and never enforced; snapshots of unliked tracks and aged-out history stayed in storage for good. | **Fixed.** `pruneSnapshots` keeps only rows that a like, history entry or playlist refers to, on save and on read. The unenforced constant was removed. Tests: `library.test.mjs`. |
| WD-2 | `pwa.applyUpdate()` existed with no control calling it; the notice tells the visitor to close and reopen. | **Decided, not wired.** A reload-now control would stop playback, and the queue is in memory. The dead functions (`applyUpdate`, `checkForUpdate`) and the waiting-worker field were removed. The update still applies when every DHUN tab is closed. |
| WD-3 | Import did not check the export `version` field. | **Fixed.** Only format version 3 is read; others are refused with the version named. Tests: `transfer.test.mjs`. |

Still open, found while checking the above:

- The worst case for the library is still larger than the browser's storage quota
  (200 playlists of 1,000 tracks each, plus likes and history). When a save fails,
  the Library screen says nothing is being saved; the library is not split or shrunk. Recorded, not
  scheduled.

## 3. Proposed next (not scheduled)

Ordered by what each depends on.

1. **YouTube "Watch" mode, as a spike first.** A visible IFrame player beside the JioSaavn
   audio path. It needs a referrer check on the deployed origin, then owner approval for the
   third-party script, then a separate mode. Plan in [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md)
   (2026-10-21 follow-up). Not built.
2. **A human audio check on the deployed origin**, written into
   IMPLEMENTATION-STATUS.md with a date and the browser used. This cannot be done
   by the repository alone.
3. **Browser coverage beyond Chromium.** The CI browser job uses one engine. Adding
   Firefox or WebKit runs needs the browser binaries that the pinned tooling
   installs; cost is zero, but the run time and flake risk need measuring first.
4. **A web UX audit document** (the web counterpart of `docs/ux-audit.md`). It
   should be written from a recorded browser run, not from memory.

## 4. Not planned in the browser build
## 4. Not planned in the browser build

- YouTube or YouTube Music playback in the browser (owner decision D2,
  [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md)).
- Spotify in the browser, which is not built (owner decision D3: deferred, not
  removed; the native design keeps it as a planned source).
- Downloads, offline audio, equaliser, synced lyrics, accounts and sync are not planned.
- Any server operated by the project. This would change the cost position (D4).

## 5. Done means

A change to the web build is done when:

- the JavaScript suite passes and the docs gate passes (`npm run check`);
- the website workflow's build job passes on the pull request;
- any document that describes the changed behaviour is updated in the same change;
- any new limit, key or origin is written into the matching document above.
