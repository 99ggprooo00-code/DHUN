# Phase 6 — DHUN interface and full-screen player

> Status: ✅ Complete — measured by CI on every route, never seen by a person
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

The browser build should be recognisably DHUN: dark first, frosted glass, the
application's own components and tokens, with a full-screen player that behaves
like the native one. It should not look like a website that happens to play
audio.

## Acceptance criteria

- [x] The design tokens are the application's, not a second palette — the web
  app's `css/tokens.css` mirrors the Kotlin design sources, and the site's sheet
  is generated from *that* by `website/tools/sync-tokens.mjs`.
- [x] Dark first, light optional, and the app's six accent ramps are available.
- [x] The shell is the app's shell: a glass app bar, navigation that is a bottom
  bar on a phone and a rail above the app's own breakpoint, and the docked
  mini-player where the app keeps it.
- [x] A full-screen player with the blurred artwork backdrop, transport, seek
  scrubber and the queue sheet.
- [x] Blur is layered and cached rather than recomputed per frame, with an opaque
  fallback where transparency is unwelcome (reduced transparency, forced
  colours, print).
- [x] Responsive at phone, tablet and desktop widths without a horizontal
  scrollbar at any of them.
- [x] Keyboard operable, with a shortcut sheet generated from the one shortcut
  table so the documentation cannot drift from the bindings.
- [x] Reduced motion honoured.
- [x] A failure is written, not dumped: the state leads with a sentence the app
  owns, and the browser's or the server's own string is demoted to a labelled
  **Technical detail** line — never the headline.

## What exists

| Piece | Where |
|---|---|
| Screens as pure functions of a view model | `app-web/src/js/views.js` |
| Mini and full player | `app-web/src/js/player-ui.js` |
| Component styles and the shell | `app-web/src/css/app.css` |
| Generated tokens (both schemes, six ramps) | `app-web/src/css/tokens.css` |
| The marketing shell that reads as the same product | `website/css/shell.css`, `website/src/_includes/base.njk` |
| The token tables on the site's interface page | `website/src/_data/designTokens.js` |

### The failure surfaces

Every one of these states is reachable by a real visitor, because the primary
source is a cross-origin request to an origin this project does not control. Two
of them were wrong until 2026-10-16, and both were found by rendering the states
and reading them rather than by a gate:

| Before | After |
|---|---|
| A failed screen whose body was `TypeError: Failed to fetch` — the same words for a flat network, a blocked origin and an ad-blocker, naming no remedy. | The app's own sentence, then a labelled diagnostic line carrying the raw string — kept, because a bug report is easier to write when the app shows the string instead of hiding it in a console. |
| The player's demo failure offered **two** buttons to the same screen ("Data source" and "Configure a source"), and a demo row offered a Retry for a thing that cannot be retried. | One action per outcome: a demo row offers the source picker; a refusal offers Retry, the source picker, and the explanation. |

The copy lives in one table in `app-web/src/js/source.js` with three registers —
`summary` for a row or a toast, `help` for a state's body, and the raw string for
the diagnostic — and `npm run states` renders all of them to a page, so that
reading what a refused network actually says is a two-minute job instead of a
browser session on a bad network.

## What has been measured, and what has not

Measured, by the `browser` job on every route and both colour schemes:

| Measurement | Result |
|---|---|
| Horizontal overflow at 280, 320, 390 and 1280 px | Clean |
| Text contrast, dark and light | Every pair at or above the floor |
| Tab order and focus visibility | Every reachable control takes focus, in order |
| Radio groups (theme, accent) | One tab stop each, arrows move within, exactly one selected |
| Keyboard paths | The documented shortcuts work |
| axe (automated accessibility rules) | No serious or critical finding |
| Print | The disclosures survive the print stylesheet |

Not measured, by anyone:

- **What it looks like.** No person has watched a page render. Geometry and
  contrast are not taste, and the captures the browser job writes are CI
  artifacts that no one has opened. `npm run states` exists so this can be done
  without a network: it renders every state to a page, and reading it is a
  person's job.
- **A screen reader.** The markup uses the conventions CI can assert; a real
  screen-reader pass has not happened.
- **A phone.** The viewport is the same width as a phone's, in a desktop engine.

Those three are the Phase 8 work, listed in
[`IMPLEMENTATION-STATUS.md`](IMPLEMENTATION-STATUS.md).

## Risks identified

- **Glass is expensive.** Layered blur with saturation on a large surface can
  cost real frames on a slow device; the fallbacks exist for that reason, and the
  performance claim is deliberately absent from the site.
- **A second palette by accident.** The token sheet is generated rather than
  written for exactly this reason, and `design system mirrors the app`
  (`scripts/website_quality.py`) fails if a hand-written colour appears.

## Test results

| Test | Result |
|---|---|
| `app-web/tests/views.test.mjs` (screens, states, escaping, no `undefined` in copy) | Pass |
| `app-web/tests/shell.test.mjs`, `actions.test.mjs`, `tokens.test.mjs`, `dom.test.mjs` | Pass |
| `browser` job (the table above) | Pass |
| `python3 scripts/website_quality.py website/dist` | 31 checks pass |
| A human looking at it | **Not tested** |

## Completion decision

- [x] Acceptance criteria met
- [x] Tests pass, with the measurements above
- [x] Documentation: this file; the design contract is in
  [`docs/ui-ux-design.md`](../ui-ux-design.md) and the site's walkthrough page
- [ ] A human has looked at the result — outstanding, and no gate can replace it

**Go / No-Go: Go, with the human-review item carried forward.**
