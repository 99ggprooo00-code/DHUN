# Phase 5 — Complete playback engine

> Status: ✅ Complete — implemented and unit-tested; audible output is Phase 2's question, not this phase's
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

A player that behaves correctly for the whole of a listening session: a queue
that keeps its shape, transport that does what it says, and failures that stay
attached to the track that caused them.

## Acceptance criteria

- [x] Play, pause, seek, next, previous, shuffle and repeat are a state machine
  with tests, not a set of event handlers that each touch the DOM.
- [x] The queue survives a track failing to resolve — the row stays, with its
  reason — so a failure cannot silently renumber the rest.
- [x] The next track is resolved before the current one ends, so the gap between
  tracks is not a request round trip.
- [x] A browser that refuses to start audio before a gesture produces a prompt
  with an action, not an error.
- [x] Only one tab plays at a time, and the other tab says why it stopped.
- [x] The OS media controls (play, pause, next, previous, seek, metadata,
  artwork) reflect the same state as the interface.
- [x] An expired stream URL is re-resolved exactly once, rather than skipped or
  retried forever.
- [x] The progress readout follows the media element's own clock.

## What exists

| Piece | Where |
|---|---|
| Transport, queue, shuffle/repeat, pre-resolution, failure records | `app-web/src/js/player.js` |
| The media element, the equaliser graph, honest status reporting | `app-web/src/js/audio.js` |
| Gesture policy and cross-tab election | `app-web/src/js/tabsync.js`, `pwa.js`, `main.js` |
| OS transport controls and metadata | `app-web/src/js/mediasession.js` |
| Mini and full player surfaces | `app-web/src/js/player-ui.js` |
| Settings that affect playback (volume, normalization, equaliser) | `storage.js`, `main.js`, `equalizer.js` |

## What this phase does *not* claim

That any of it has been heard. Every item above is implemented and unit-tested
against a stubbed media element; the question "does sound come out of the
speakers on a real network" belongs to [PHASE-02](PHASE-02-PLAYBACK-POC.md) and
is still open. The distinction is deliberate: an engine can be complete and
correct while the source in front of it refuses, and conflating the two is how a
project starts lying to its own users.

## Risks identified

- **Background tab throttling.** Browsers throttle timers in hidden tabs; the
  position readout comes from the media element rather than a timer for this
  reason, and the cross-tab election exists so that two hidden tabs cannot both
  believe they are the player.
- **A finished queue is not an error.** End-of-queue, repeat-all and repeat-one
  are separate states, and each has a test.

## Test results

| Test | Result |
|---|---|
| `app-web/tests/player.test.mjs` | Pass — transport, queue, shuffle/repeat, failures, expiry |
| `app-web/tests/audio.test.mjs` | Pass — status reporting, gesture handling, equaliser wiring |
| `app-web/tests/equalizer.test.mjs`, `shortcuts.test.mjs`, `mediasession` paths in `views.test.mjs` | Pass |
| `cd app-web && npm test` | 221 pass, 0 fail (2026-10-19) |
| Audible output | **Not tested** — Phase 2 |

## Completion decision

- [x] Acceptance criteria met
- [x] Tests pass, in the files named above
- [x] Documentation: this file, and the app's own keyboard-shortcut sheet
- [x] The playback claim is left to Phase 2

**Go / No-Go: Go.** The engine is not what is blocking release.
