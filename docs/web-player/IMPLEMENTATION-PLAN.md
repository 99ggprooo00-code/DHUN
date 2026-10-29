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
| Library and storage | `app-web/src/js/library.js` | IndexedDB transactions and legacy migration; limits; see [DATA-SCHEMA.md](DATA-SCHEMA.md). |
| Export and import | `app-web/src/js/transfer.js`, `library.js` | 24 MiB UTF-8 cap and pre-parse complexity limits, `app` marker. |
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

## 3. Proposed next (dependency order, not implementation authorisation)

1. **Existing provider permissions and privacy/notice gaps:** JioSaavn Terms
   restrict unofficial/automated access. Resolve wrapper/content permission,
   controller/contact, retention and release notices, not just audible playback.
2. **Live catalogue and human audible deployed-origin check:** record date,
   origin, browser, query, source badge and exact sound/error outcome in
   IMPLEMENTATION-STATUS.md. Unit/browser geometry tests cannot establish this.
3. **Bounded architecture design before a second source:** provider-scoped IDs,
   snapshot/user-data migration, capabilities, accounts/auth/security, player
   ownership/error and privacy lifecycle. Preserve JS/native KMP split (D5).
4. **Browser/phone/screen-reader/visual coverage:** broader binaries and devices
   with dated evidence; no inferred human acceptance from axe.
5. **YouTube visible Watch mode remains a proposal:** not the next authorised
   build/spike. D2/current CSP remain; obtain explicit third-party runtime/CSP/
   privacy approval before any live runtime change, then test Referer/visibility
   and controls. Official IFrame queue/seek exists; audio-only parity does not.
6. **Spotify planned, execution deferred:** no platform implementation chosen;
   technical/account/Premium/policy/legal/playback/privacy/licence/cost and
   public-access gates all apply. Premium alone does not clear §III.5.

## 4. Not implemented / not authorised now

- No YouTube IFrame or Spotify in the browser; these are not silently removed
  from DHUN's long-term direction. See [PRD §1](../PRD.md#1-product-definition-and-source-of-truth).
- No audio downloads/offline audio, equaliser, lyric provider, account/sync or
  DHUN backend/proxy/paid recurring service. Do not add a stub for absent features.

## 5. Done means

A change to the web build is done when:

- the JavaScript suite passes and the docs gate passes (`npm run check`);
- the website workflow's build job passes on the pull request;
- any document that describes the changed behaviour is updated in the same change;
- any new limit, key or origin is written into the matching document above.
