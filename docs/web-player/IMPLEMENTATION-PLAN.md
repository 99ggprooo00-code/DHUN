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

## 2. Known defects (found while writing these documents)

Each defect below was confirmed by reading the code. None has a test yet that
would have caught it.

| ID | Defect | Effect on a visitor | Evidence |
|---|---|---|---|
| WD-1 | `LIBRARY_LIMITS.snapshots` (2,500) is declared and **never enforced**. | Snapshots grow with every like, history entry and playlist addition. Storage can fill, and then saves stop (see DATA-SCHEMA §1). | `app-web/src/js/library.js`: the constant is defined at line 30 and not read anywhere else. |
| WD-2 | `applyUpdate()` in `pwa.js` is exported but **no control calls it**. | A new version is announced, but the only way to use it is to close every DHUN tab. | `app-web/src/js/pwa.js` (definition and export); `main.js` (the notice). |
| WD-3 | Import does **not check the `version` field**. | A future format would be read as if it were version 3. | `app-web/src/js/transfer.js` checks `app` only. |

## 3. Proposed next (not scheduled)

Ordered by what each depends on.

1. **WD-1: enforce the snapshot cap.** Prune snapshots that no list refers to,
   then the oldest ones. Add a test that fills a library past the cap. Depends on
   nothing.
2. **WD-3: check the export version on import.** Refuse a version the build does
   not know, with the reason. Add a test. Depends on nothing.
3. **WD-2: decide the update experience.** Either wire `applyUpdate` to a visible
   "Reload to update" action, or remove the dead function and keep the notice.
   This changes what the visitor sees, so it needs the owner's decision.
4. **A human audio check on the deployed origin**, written into
   IMPLEMENTATION-STATUS.md with a date and the browser used. This cannot be done
   by the repository alone.
5. **Browser coverage beyond Chromium.** The CI browser job uses one engine. Adding
   Firefox or WebKit runs needs the browser binaries that the pinned tooling
   installs; cost is zero, but the run time and flake risk need measuring first.
6. **A web UX audit document** (the web counterpart of `docs/ux-audit.md`). It
   should be written from a recorded browser run, not from memory.

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
