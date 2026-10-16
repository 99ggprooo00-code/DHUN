# Phase 8 — Reliability, accessibility, privacy and security

> Status: ⚠️ Complete with exceptions — the automated half is done and green; the human half has not happened
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-16

## Objective

Decide what will be checked automatically, what can only be checked by a person,
and make both lists visible instead of pretending the first one covers the
second.

## Acceptance criteria

- [x] Every rule that can be checked by a machine is checked on every pull
  request, on a runner that has no browser session and no network dependency.
- [x] No gate requires a service the project does not have — no account, no
  secret, no paid runner beyond the free tier.
- [x] Each gate's coverage is written down, next to what it does **not** cover —
  a gate list that overstates itself is how a project becomes confident and
  wrong.
- [x] The honesty rules are applied to the served bytes and not only the build —
  the check runs after publication, on the live URLs.
- [x] The manual matrix exists as a list of specific tests with environments, so
  "we should test that" is a numbered instruction rather than a sentiment.
- [ ] The manual matrix has been performed and its results recorded here.

## The gate list

Everything below runs on every pull request unless it says otherwise. The numbers
are the ones this session read, and they move — the point of the table is which
gate covers which claim.

| Gate | Command | Covers | Result |
|---|---|---|---|
| App suite | `cd app-web && npm test` | The state machine, the engine, storage, the router, the source seam, the screens, the shell, the tokens, the icons | 165 pass |
| Repository contract suite | `python3 -m unittest discover -s scripts -p 'test_*.py'` | The web app's source-level rules, the site's honesty and quality rules, and the repository's own hygiene (links, secrets, debris) — no Node, no network | OK |
| Site quality | `python3 scripts/website_quality.py website/dist` | Design-system mirror, contrast in both schemes, touch targets, budgets, metadata, crawlability | 31 checks |
| Site honesty | `python3 scripts/website_claims.py website/dist` | Forbidden claims, required caveats, no baked digests, no telemetry SDK | 4 pages, 9 rules |
| Web documents | `python3 scripts/web_player_docs.py docs/web-player` | These documents: required sections, evidence beside every completion, no claim the repository cannot back | OK |
| Failure copy | `app-web/tests/source.test.mjs`, `app-web/tests/views.test.mjs` | A failure never leads with a browser's or a server's own string; every kind and stage has a sentence; a help sentence stands without the summary above it; every state keeps its raw string as a labelled diagnostic | Pass |
| The app's own copy | `app-web/tests/actions.test.mjs` | No string the app renders — a screen, the chrome, the document's title and description, the install manifest — promises playback or names an audio quality. Deliberately narrower than the site's lexicon, because Python cannot render these screens | Pass |
| Browser job (CI) | `.github/workflows/website.yml` | Rendering, overflow, contrast, tab order, keyboard, motion, print, axe — every route, both schemes | Pass |
| Served-site check (CI, after a publish) | `scripts/website_smoke.py` | The bytes a visitor actually receives, re-read on the live site | Pass |
| Minification proof | `cd website && node tools/verify-minify.mjs` | The committed build matches a fresh build | Pass |
| Markup validity | `npx html-validate "dist/**/*.html"` | Valid HTML on every built page | Pass |

## Accessibility

Automated, and green:

- tab order and focus visibility on every route, including that every visibly
  focusable control takes focus;
- radio groups counted as one tab stop each, with arrow-key movement asserted;
- text contrast in both colour schemes, measured in the rendered page;
- axe's rule set, at serious and critical severity, on every route;
- names, roles and landmarks — the app's screens carry headings, the dock sits in
  its own landmark, and the source badge has a text equivalent.

**Not automated, and not done:** a screen-reader pass, a keyboard-only session
with the browser's own navigation, and zoom/reflow at 200%. Those need a person
and a browser; they are listed as open in
[`IMPLEMENTATION-STATUS.md`](IMPLEMENTATION-STATUS.md).

## Privacy

| Rule | How it is kept |
|---|---|
| No analytics, telemetry or crash reporting | `scripts/website_claims.py`'s telemetry rules read the shipped dependency graph; the app ships no third-party script at all |
| No account, no DHUN server | ADR-008 boundary 1; `scripts/test_app_web.py` fails if a DHUN-operated endpoint appears in the code or the policy |
| Everything kept locally | `storage.js`; stated on the app's legal screens and in the site's copy |
| Third-party traffic disclosed | The app's privacy page names what leaves the browser and when, with the same status tags the native legal bundle uses |

## Security

| Rule | How it is kept |
|---|---|
| Strict Content Security Policy | `app-web/src/index.html`: no inline script, no `eval`, no CDN, `base-uri`/`form-action` locked; asserted by `app-web/tests/shell.test.mjs` and `scripts/test_app_web.py` |
| Framing refused | `isFramed()` is the first thing `boot()` asks; the app refuses to run inside somebody else's frame (the `frame-ancestors` directive cannot be delivered from a static host, which is why the rule is enforced in code) |
| No secrets, no keys | The app needs none: no API key, no token, no account. `scripts/test_repo_health.py` scans the tree for secrets anyway |
| No third-party runtime asset | No CDN, no font service, no icon library, no analytics — asserted by `scripts/test_app_web.py` against the manifest and the markup |
| Untrusted input from a source is escaped | `dom.js` escapes every interpolated value; `views.test.mjs` renders a track whose title is markup and asserts it stays text |

## The manual test matrix

Not performed. When it is, record the result in this file — browser and version,
device, and what happened, including "it refused and said so".

| # | Test | Environment | Status |
|---|---|---|---|
| 1 | Search, then play a track (the Phase 2 test) | Desktop browser, home network | 🔲 Not run |
| 2 | Same, on a phone | Android browser | 🔲 Not run |
| 3 | Reload with the library kept | Desktop browser | 🔲 Not run |
| 4 | Offline: hard reload with the network off | Desktop browser | 🔲 Not run |
| 5 | Install to home screen, launch, reload | Android browser | 🔲 Not run |
| 6 | Keyboard-only session, including the shortcut sheet | Desktop browser | 🔲 Not run |
| 7 | Screen reader over Home, Search, Library and the player | Desktop browser | 🔲 Not run |
| 8 | Zoom to 200% and reflow | Desktop browser | 🔲 Not run |
| 9 | Reduced motion and forced colours | Desktop browser | 🔲 Partly covered by the browser job's preference passes |
| 10 | Background the tab mid-track, and open a second tab | Desktop browser | 🔲 Not run |
| 11 | Read every state on the review page (`npm run states`) and say whether the words are right | Desktop browser, no network needed | 🔲 Not run by a person; the copy was reviewed as rendered text on 2026-10-16, which is how the two defects in Phase 6 were found |

## Risks identified

- **A green gate list reads as "done"**. Nine green rows and one unticked table
  look like success from a distance; that is why the overall status line in the
  tracker is the first thing in this directory.
- **Automated accessibility is a floor, not a ceiling.** axe finds what a machine
  can find; it has never told anyone whether a screen reader can use the player.

## Completion decision

- [x] The automated gates exist, run on every pull request, and are green
- [x] Each gate's coverage is written down, including what it does not cover
- [x] The manual matrix is written down as a list of specific tests
- [ ] The manual matrix has been performed

**Go / No-Go: Conditional Go.** Nothing in the automated half is outstanding.
The manual half is the reason the project is not release-ready, together with
[PHASE-02](PHASE-02-PLAYBACK-POC.md).
