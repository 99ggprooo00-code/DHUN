# Phase 6 — DHUN interface and full-screen player

> Status: ✅ Complete — measured by CI on every route, never seen by a person
> Branch: `arena/59c43e0a-dhun`
> Last updated: 2026-10-17

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

**The app's own copy is gated too.** The document's `<title>`, its meta
description and the install manifest's description are the lines a tab, a share
sheet, a search result and an install prompt show, and they are the app's
chrome as much as any screen is. Three of them read "play YouTube Music in your
browser" — the one claim this build cannot make from its own chrome, and the
claim [PHASE-02](PHASE-02-PLAYBACK-POC.md) exists to keep open. They now describe
what the app does: search anonymously, keep the library on the device, and say
plainly when a source will not answer. `app-web/tests/actions.test.mjs` checks
them, along with every string the screens render.

Two smaller findings from the same pass, both about the chrome rather than a
failure:

| Before | After |
|---|---|
| The first-run strip read "Search and play from YouTube Music anonymously" — on screen at boot, on every route, for the whole session. | It says what the app does and what it will not pretend to do, and it is dismissed by the visitor's first interaction (a pointer or a key). It used to be permanent: nothing ever cleared an `info` banner. |

The copy lives in one table in `app-web/src/js/source.js` with three registers —
`summary` for a row or a toast, `help` for a state's body, and the raw string for
the diagnostic — and `npm run states` renders all of them to a page, so that
reading what a refused network actually says is a two-minute job instead of a
browser session on a bad network.

### The layout pass of 2026-10-16

The captures below are what a person saw in the built `/app/`, and what was
changed because of it. Every number is a measurement from that session
(headless Chromium, the built app, not a mock-up), not an estimate.

| What the captures showed | What the app says | What changed |
|---|---|---|
| The whole interface was a 400px column marooned in a 1200px window — 800px of dead glass, rows at 360px | `DhunShellPolicy.masterPaneMaxWidth` is `DhunSpacing.playerContentMaxWidth` (**720dp**), chosen because "a list of rows does not improve when it gets wider" | The 840px block now caps content with `--dhun-pane-master-max` (720px), the token that already mirrored that measure. The legal pages keep their own narrower 680px |
| The document scrolled, not the list: `#app` measured **2210px tall in a 900px window**, so the app bar, the rail and the bottom navigation slid away on every screen | `.dhun-scroll` is the scroll region; that only works if the shell is height-bounded | `#app` is bounded (`100dvh`, `100vh` fallback). Measured after: `#app` 1280x900, scroll region 1200x718 |
| Quick picks was an artwork-tile shelf that did not fit itself — a 260px cover inside a 260px tile with padding and a border, hanging 18px out of its own card (`scrollWidth 269 > clientWidth 260`) | `HomeScreen.kt` draws `QuickPicksGrid`: twelve tracks chunked two to a column, columns `dialogMinWidth` (**280dp**) wide, each row a **48dp** cover over title and artist on the content-row surface | The rows are the app's rows, and the overflow is gone |
| A phone showed a **280px** cover with 55px of dead gutter either side; the "wide" layout was a 400px strip split `1 1 45% / 1 1 45%` | `PlayerLayout`: the cover is fitted to `min(field width, field height, playerArtworkMaxSize = 640dp)`; the wide mode needs ≥ `playerWideLayoutMinWidth` (480dp) **and** width > height, and gives the controls `clamp(0.34 · width, 240dp, 400dp)` | Stacked: the hero fills its field (measured 350x350 on a 390x844 phone, transport fully on screen). Wide: 1048px composition — 640 cover, 343 controls column, cluster bottom-aligned as the app draws it |
| In the player's 343px control column, "This track could not be played" wrapped to **one word per line** beside its own buttons | The app's control cluster stacks its notice in that column | The message takes its own line with the actions beneath it, on phones and in the player alike |
| The toast covered the mini player: a tap on **expand** hit the toast, so the full player could not be opened on a phone at all (dock measured **166px**, toast offset 112px) | — | The toast's offset is made of the dock it actually has, and opening the player clears a toast it inherited |
| Pressing Queue or Lyrics threw the visitor back to the cover: the player's scroll reset **613 → 0** and the panel they asked for moved from y=327 to **y=940**, off a 844px screen | — | Where they were is kept across a re-render of the same track; a different track still starts at the cover |

### What the interface claimed, and did not know (2026-10-16, later the same day)

The layout pass above found things that were drawn wrong. Reading the app's Home
again — instead of assuming the web's version of it was close enough — found
things the interface *said* that it had no basis for.

| What it said | What was true | What changed |
|---|---|---|
| The source badge: **"YouTube Music"**, on every screen, including `#/search` | The rows on screen were the demo catalogue's: the source was unreachable and the demo had answered. `source.js`'s own contract says *"Whichever answers is recorded in `lastUsed.kind` and shown in the UI"*, and the badge asked `describeActive()` — the source DHUN would have asked | A second record, `rowsUsed`, for the source that produced the rows; `describeRows()` feeds the badge, the demo strip, the settings banner and the dock label. Verified live in five routes × two schemes |
| Search's demo claim was a one-line hint with no `data-notice` | The browser job's rule — *"the demo answered, so this page must say those rows are fictional and have no audio"* — keys on `data-kind="demo"`. With the badge reading `innertube` on every route, **that rule could never fire on any route**, including the one it was written for | The same strip as every other screen, in every state, and Search's weaker line is gone |
| An empty mood: *"Search for something, or point DHUN at your own data source"* | That is the **feed's** empty copy, shown to a visitor who had just pressed **Chill** | The mood's states speak for the mood and name the query that came back empty |
| *(nothing — Home had no header and no moods)* | `HomeScreen.kt` opens with the `DHUN` wordmark, a `headlineMedium` greeting from `GetHomeFeedUseCase.greetingForCurrentTime`, and a `DhunHorizontalRail` of `HomeMood.kt`'s five moods | All three, above the state, so a failed feed still says hello. A mood **is a search against the source** (`FOCUS("Focus", "focus instrumental music")`), shaped into `UseCases.moodSections`' one shelf — measured: pressing Chill issues `POST music.youtube.com/youtubei/v1/search`, and returning to *For you* issues none |

The greeting is worth a note of its own. This repository has already shipped the
UTC version of it once — Android, 2026-10-10, *"Home always said 'Good night'"* —
so the mirror takes the device offset (`greetingForNow`), and Nepal's +5:45 is
asserted directly, along with both sides of every boundary.

`app-web/tests/source.test.mjs` now reads `HomeMood.kt` itself and fails if the
web's labels or queries drift from it: the same mirror rule the token sheet lives
under, applied to the one thing on Home a visitor presses.

### The detail screens, and the appearance control (2026-10-17)

A review pass over the three entity screens against the Kotlin that draws them,
and one press of the **Light** theme chip with a screenshot taken either side.

| What it said | What was true | What changed |
|---|---|---|
| An album header of `Naya Koirala · 2024 · 5 tracks` on one line, a 200px cover, Play and **Queue** | `AlbumScreen.kt`: cover → title → `artistName • year` → a *separate* `labelSmall` line of `trackCountText • durationText`, then a filled **Play** and an outlined **Shuffle**. Both strings are the **source's**, read out of the response's `secondSubtitle` by `BrowseParsers.kt` — the web counted its own rows instead, which is a claim about the network, not about the album | The app's two lines, the source's own strings, Play + Shuffle. Where a source sent no such line, the row count is the fallback and is named as a count of rows |
| The artist page: a round avatar, "Artist" in small caps, a paragraph of biography under it, Play and Queue | `ArtistScreen.kt`: a **16:10 full-bleed crop**, the name overlaid `headlineMedium` under a veil composited from the theme background at `.15 / .05 / LYRICS_VEIL_BOTTOM (.62)`, `monthlyListeners` under it when the source's own string says so, a tonal Shuffle and Radio above the top songs, and the biography in an **About** card at the end, eight lines maximum | The hero, the veil, the listeners rule and the About card. Radio has no counterpart here (`/next` stations do not exist in this build) and is not faked; Play and Shuffle act on the same top songs |
| A playlist's `meta` line carried the description; the kicker said "Playlist" | `PlaylistScreen.kt` remote: title, `subtitle • meta`, then the description on the quietest rung, three lines, plus Play / Shuffle. Local: the same with **Local playlist** as the subtitle and Rename / Delete added | Both variants, with the app's own label for each and the description in its own place |
| Full player label: **"Album ·"** — a separator with nothing after it — over a playlist, an artist or an album alike | The label was built inline as `"Album · "` and ignored the entity's title | `entityLabel(routeName, name)` in `router.js`; a missing name yields no label and the player says "Playing" |
| An album screen printed **"Dharan Nights"** twice — once in the app bar, once under the cover — and carried two `h1`s saying the same words | `AlbumScreen.kt` and `ArtistScreen.kt` draw the entity's name in the content and keep no title in the chrome | The three entity routes leave the heading to the page; `browser.mjs` counts `h1`s on a real album screen and was run against the duplicate first (2 found) before it passed |
| The site's **"Launch /app/ Player"** link landed on Home with no player and no explanation | the overlay only draws once a track is loaded, so the request needs an answer until then | the dock says *"Nothing is playing yet"* and names what would open it; the request is kept, and the browser job presses a track to prove the player the link asked for appears |
| The theme chip: **Light** left a dark page with a pale app bar, and the meta lines vanished into it | `applyTheme()` wrote `data-dhun-theme` to `#app`, but the palettes are keyed on the root and `body { background: var(--dhun-background) }` is painted *above* `#app` — so only the chrome was themed | The attributes go to `document.documentElement`. `browser.mjs` now presses the control and reads the document: the attribute, `body`'s computed background and `--dhun-background` all have to agree, and the check was run against the old behaviour first (six failures) before it passed |
| The demo Home's "Albums for you" shelf: four rows you could press to play | The shelf's entries carried no `kind`, and the home screen treats an entry without one as a **song**: the rows were `play-track` with album ids, and the album pages were reachable only by typing a URL | The bundled source labels its own entries, as every mapper does |
| Search's playlist results were drawn as **artists** | Same cause: `card()` infers *artist* from a `name`, so a playlist without a `kind` opened `#/artist/pl-…`, a page that cannot exist | Measured after the fix: the cards read `open-entity:playlist:pl-01`, and pressing one opens `#/playlist/pl-01` |

### What the interface claimed, and did not know (2026-10-16, later the same day)

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

- **What it looks like, beyond the pass below.** Geometry and contrast are not
  taste. The first look at the real captures happened on 2026-10-16 and is
  recorded above; it found seven defects, which is the argument for doing it
  again rather than an argument that it is finished. `npm run states` still
  exists so the copy and the states can be read without a network, and the
  Phase 8 rows are still open.
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
| `browser` job (the table above) | Pass — 135 measurements, 0 failures (2026-10-20) |
| `python3 scripts/website_quality.py website/dist` | 31 checks pass |
| A human looking at the built app's captures (34, four widths, both themes) | Looked at on 2026-10-16 — seven drawing defects and four claims the interface could not back, all fixed and listed above. Still not a phone and still not a screen reader |
| A second look, at the entity screens, the appearance chip and a player deep link (2026-10-17) | Seven more defects, all fixed and listed in the section above. The Light chip was pressed in a real browser, and the check is in the browser job; the phone is still an emulated viewport and no screen reader has run |

## Completion decision

- [x] Acceptance criteria met
- [x] Tests pass, with the measurements above
- [x] Documentation: this file; the design contract is in
  [`docs/ui-ux-design.md`](../ui-ux-design.md) and the site's walkthrough page
- [ ] A human has looked at the result — outstanding, and no gate can replace it

**Go / No-Go: Go, with the human-review item carried forward.**
