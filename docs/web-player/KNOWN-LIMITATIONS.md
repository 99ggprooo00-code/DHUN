# DHUN Web — known limitations

> Last updated: 2026-10-16 (session `arena/59c43e0a-dhun`)

This is the web build's view of what is unproven, unsupported or awkward. It is
written for someone deciding whether to rely on the browser build today.

**The canonical record is [`../../.ai/KNOWN_LIMITATIONS.md`](../../.ai/KNOWN_LIMITATIONS.md)**,
which covers the whole project in date order, session by session. This file is
the summary a reader of the web workstream needs, kept deliberately short: where
the two disagree, the canonical file wins, and that file is the one the project
updates after every session.

An unticked box is not a to-do list — several of these are permanent properties
of a static site in a browser, and are listed so that nobody promises otherwise.

## Playback

- [ ] **No audio has been verified as playing from the deployed site.** This is
  the release gate. The app attempts real resolution, and the attempt has never
  been completed by a person on a real network. Nothing in this repository can
  close this line; a browser and a network can.
- [ ] **Browsers may refuse the stream request.** A page cannot make YouTube's
  origin answer a cross-origin request. When it refuses, the app shows the
  refusal on the row instead of pretending — that behaviour is implemented and
  unit-tested, but it is not a substitute for playback.
- [ ] **Autoplay policy.** A browser will not start audio before the visitor has
  gestured. The app surfaces this as a prompt with a Play action rather than an
  error; the first press on a fresh page may therefore be a prompt, not sound.
- [ ] **Format support varies.** What the browser can decode is the browser's
  decision. An unsupported format becomes an error state, not a silent failure.
- [ ] **The equaliser's effect has not been heard either.** The web build carries
  the app's ten bands, its preamp and the eighteen stock presets, bound to the
  browser's own audio graph, and it says on screen when a source's bytes cannot
  be routed through it (a cross-origin stream that the browser will not let the
  graph touch). That the sliders move ten real filters is asserted by tests
  against the graph, not by an ear — the same gap as playback itself.

## Sources

- [ ] **The YouTube Music path is attempted, not proven.** Metadata retrieval
  does not prove playback, and this project treats the two as separate claims.
- [ ] **An open-licensed catalogue has not been evaluated.** Internet Archive
  hosts recordings whose licences vary per item, and some are freely reusable.
  It is the most promising source that needs no account and no service the
  project operates, and it is not implemented. See
  [PHASE-01](PHASE-01-SOURCE-RESEARCH.md).
- [ ] **A user-configured endpoint is the escape hatch, not a product.** It
  exists for someone whose network blocks the default path; the project does not
  operate it, and the contract it must speak is small and documented in
  `app-web/src/js/source.js`.
- [ ] **A source badge is an honesty device, not a guarantee.** It names who
  answered the last request — "YouTube Music", "Your endpoint" or "Demo data".
  A real source answering does not mean every row in it can play.

## Hosting

- [ ] **GitHub Pages is static.** No server-side logic, no proxy, no database,
  no response headers the project can set. This is the constraint that shaped
  everything else in this file.
- [ ] **CORS is decided by the other end.** Some requests the app would like to
  make are refused before they leave the browser, and no amount of client code
  changes that.
- [ ] **A browser extension or proxy can change behaviour** in ways this project
  cannot reproduce or support.

## Data

- [ ] **Everything stays in one browser, on one device.** Settings live in
  `localStorage`, the library in IndexedDB. There is no account and no service to
  move data through, so there is nothing to move it with.
- [ ] **Clearing site data erases the library.** It is the documented way to
  remove it; there is no undo and no copy elsewhere.
- [ ] **A private window degrades the library.** When IndexedDB is unavailable,
  the app says so on screen and keeps the library in memory for the tab's life.
- [ ] **No data export beyond the app's own.** The app can write a library file
  and read one back; that is the whole story.

## Interface and accessibility

- [ ] **No human has looked at the rendered interface.** CI measures viewports,
  contrast, tab order, radio-group behaviour and axe rules on every route; it
  cannot see what a person sees.
- [ ] **No screen reader has been used.** The markup follows the conventions the
  CI checks can assert (labels, landmarks, roles, focus behaviour); a real
  screen-reader pass has not happened.
- [ ] **Blur and transparency cost rendering.** Frosted glass is layered on
  purpose; on a slow device, or with the preference set to reduce transparency,
  the surfaces fall back to opaque panels.
- [ ] **The motion preference is respected, not merely detected.** Reduced-motion
  turns animation off rather than shortening it; a couple of transitions then
  happen instantly, which is intentional.

## Features that do not exist

Listed so that nobody goes looking: audio downloads, a visualiser, an account,
and any form of cross-device library movement.
The Android and Windows builds have some of these; the browser build does not,
because each of them needs a service, a signed stream that stays valid, or a
device-side store that a web page does not have.

## Browser support

- [ ] **The tested browser is the one CI runs** (Chromium on a Linux runner, at
  four viewport/scheme combinations). Any other browser — including any browser
  on an Android phone — is untested by this project.
- [ ] **Install-to-home-screen behaviour differs between browsers**, and so does
  the amount of storage available. The app does not require installation.
