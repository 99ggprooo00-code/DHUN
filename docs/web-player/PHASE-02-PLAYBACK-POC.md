# Phase 2 — Real browser playback proof of concept

> Status: ❌ Blocked — the one phase that gates everything else
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

Prove, with evidence, that a real track produces real audible audio from the
deployed site in a real browser — or record exactly which refusal stops it.

This is the phase the whole plan is ordered around, and it is the phase this
repository cannot complete from inside its own CI. The honest status is:
**attempted, unproven.**

## Acceptance criteria

- [ ] A track plays with audible audio from `https://99ggprooo00-code.github.io/DHUN/app/`
  in a browser, and the observation is written here with the date, the browser,
  the track, and what was heard.
- [ ] The progress indication matches the audio: it does not advance while
  nothing is playing, and it stops when the audio stops.
- [ ] If the attempt is refused instead, the refusal is recorded with its words
  — the on-screen state and the console line — and the phase stays open.
- [x] The app distinguishes "playing" from "tried and refused" in its own code,
  so the interface cannot claim the first while doing the second.
- [x] The failure taxonomy is implemented and unit-tested: refused, expired,
  unsupported, offline and unknown are separate states with their own sentences.

## What has been attempted

**B1, 2026-10-08** (recorded in
[`ADR-008`](../decisions/ADR-008-browser-web-player-target.md) and
`docs/verification/19-browser-feasibility-spike.md`):

| Step | Result |
|---|---|
| Probe deployed to the canonical origin | Served |
| Metadata request from the page | **Answered** — search and browse data came back |
| Player (stream) request from the page | **Refused** by the origin in the one Chromium-family browser available to the test |
| Firefox and Safari | Unavailable to that test, so their behaviour is unknown rather than assumed |

The verdict recorded at the time was **BLOCKED**, and the ADR was written on
that basis. Nothing since has contradicted it, and nothing since has completed
it either.

## What the app does with a refusal today

Implemented, and asserted by tests rather than by inspection:

- `app-web/src/js/source.js` resolves through adapters and returns a typed
  failure — the failure carries the stage that failed, not just a message.
- `app-web/src/js/player.js` records a failed resolution against *the row that
  failed*, so the queue keeps its shape and the failure is visible where the
  user was looking.
- `app-web/src/js/player-ui.js` renders that failure with an action: retry, or
  open the data-source settings when the failure is the demo catalogue's
  "there is no audio to fetch".
- `app-web/src/js/audio.js` reports its own status; the progress readout follows
  the media element's clock, so a paused or refusing element cannot advance it.
- `scripts/test_app_web.py` (`DoesNotSellWhatABrowserCannotDo`) fails if the
  demo catalogue stops being labelled as demo, or if a module starts offering a
  device-side download the browser cannot honour.

A state is not sound. That distinction is the reason this phase is open.

## Why it cannot be finished here

- The build environment has **no browser binary** and cannot fetch one; the
  `browser` job runs only in CI, and it renders CI's Chromium on a CI network.
- Even a browser in CI would answer a different question. The one that matters
  is "on the visitor's network, with the visitor's browser, does this origin
  answer?" — CI's network is not representative, and a green CI run must not be
  presented as proof of playback. That is exactly the mistake the project's
  honesty rules exist to prevent.

## The test a person should run

Fifteen minutes, and it either closes this phase or produces the finding the
next attempt is built on:

1. Open <https://99ggprooo00-code.github.io/DHUN/app/> in a desktop browser.
2. Open the browser's developer tools, Console tab, and leave it visible.
3. Search for a track — a well-known one, so a failure is not "no such song".
4. Press play on a row and listen. Do not watch the interface first; listen.
5. Write down, in this file: the date, the browser and version, the network
   (home, mobile, VPN, proxy), the track, **whether there was sound**, what the
   app said on screen, and any console error mentioning a status code,
   `googlevideo`, or `blocked`.
6. Repeat on an Android browser, and repeat once with the network deliberately
   off, to confirm the offline state.

If the answer is "no sound, and the app said the stream request was refused",
that is a **successful test with a negative result**, and it belongs in this
file. It is also the finding that makes the open-licensed candidate in
[PHASE-01](PHASE-01-SOURCE-RESEARCH.md) worth evaluating seriously.

## Risks identified

- **The interface can look finished while this phase is open.** That risk is
  managed by the overall status line in
  [`IMPLEMENTATION-STATUS.md`](IMPLEMENTATION-STATUS.md) and by the claim rules
  that stop the site promising browser playback.
- **CI green can be misread as playback proven.** The `browser` job checks that
  the app mounts, names its source, throws nothing and does not overflow. It has
  never heard anything, and it never will.

## Test results

| Test | Result |
|---|---|
| `cd app-web && npm test` (state machine, engine, source contract) | 224 pass (2026-10-20) |
| `browser` job on `/app/` (mount, source badge, no unhandled error, no overflow) | Pass |
| Audible playback from the deployed site | **Not tested** |

## Completion decision

- [x] The failure taxonomy is implemented and unit-tested
- [x] The B1 attempt and its result are recorded
- [ ] Audible audio from the deployed site — **not proven**
- [ ] The negative case recorded with its on-screen and console words

**Go / No-Go: No-Go.** This phase stays open until a person runs the test above
and records what happened. Until then the project's own rule applies: the web
app is **IN DEVELOPMENT — NOT VERIFIED FOR RELEASE**, and no document in this
repository may say otherwise.
