# DHUN Web — architecture

**Product vs implementation:** [PRD §1](../PRD.md#1-product-definition-and-source-of-truth)
governs multi-source intent. This is a single JioSaavn catalogue family with
wrapper failover, not an implemented federated provider model. Before a second
source, design provider-scoped IDs/snapshot migration, capabilities/account
states, auth/security, playback ownership and source-aware routing. Keep this
JS client separate from native KMP/Compose; D5 is design-only.

The browser build is a static page that assembles itself at runtime. There is no
framework, no bundler, no transpiler and no dependency: twenty ES modules loaded
by `<script type="module">`, two stylesheets, and the browser's own platform for
everything else. That is a constraint with a reason — nothing has to be fetched
to run it, build it, or check it — and it is why the module boundary matters
more here than it would in a framework project.

## The modules

Every file in `app-web/src/js/`, and what it is for. `scripts/web_player_docs.py`
fails when a module exists that this table does not name, or names one that has
been deleted — the list is checked against the directory, not trusted.

| Module | What it owns |
|---|---|
| `js/main.js` | Boot, state, the action table (one delegated listener), the render pass, focus and titles |
| `js/router.js` | Hash routes as data: parse, format, tab, title, the detail set |
| `js/api.js` | The JioSaavn seam: endpoints, normalisers, timeouts, failure kinds |
| `js/player.js` | Queue, repeat, shuffle, stream resolution, the media element, Media Session |
| `js/library.js` | Likes, playlists, history, snapshots, settings, storage (`dhun.web.v3`) |
| `js/views.js` | One function per screen: Home, Search, Library, album, artist, playlist, collection, Settings, About, the framing refusal |
| `js/shell.js` | App bar, bottom navigation, navigation rail, docked mini player |
| `js/player-ui.js` | The full player: transport, scrubber, queue and lyrics tabs |
| `js/components.js` | Cards, rows, rails, states, sheets, notices, badges, toasts |
| `js/demo.js` | The labelled sample catalogue — fictional rows, no audio |
| `js/dom.js` | The escaping `html` template, `raw`, markup helpers, focus helpers |
| `js/format.js` | Durations, counts, pluralisation, dates |
| `js/icons.js` | The icon set, generated from `DhunIcons.kt` |
| `js/legal.js` | The web build's own legal pages |
| `js/markdown.js` | The small Markdown renderer those pages use |
| `js/transfer.js` | Export and import of the library as one JSON file |
| `js/pwa.js` | Service-worker registration, update state, install prompt |
| `js/shortcuts.js` | The keyboard map the dispatcher and the help sheet share |
| `js/version.js` | The build's version string, the one place it is written |

## The layers

```
index.html          region shells (#dhun-topbar, #main, #dhun-dock, …), CSP, noindex
  └── js/main.js    boot, state, the action table, the render pass
        ├── js/router.js       hash route ↔ state (pure)
        ├── js/api.js          the JioSaavn seam: endpoints, normalisers, failures
        ├── js/player.js       queue, repeat, shuffle, stream resolution (pure + element)
        ├── js/library.js      localStorage library, settings, import/export rules
        ├── js/views.js        one function per screen, returning markup
        ├── js/shell.js        app bar, bottom navigation, rail, docked mini player
        ├── js/player-ui.js    the full player: transport, scrubber, queue/lyrics tabs
        └── js/components.js   cards, rows, rails, states, sheets, notices, badges
```

Screen renderers and queue/normalisation helpers are pure functions. The client
is not wholly pure: `api.js` performs network I/O, `library.js` wraps storage,
`pwa.js` registers browser lifecycle hooks, `player.js` owns one audio element,
and `main.js` coordinates DOM, persistence and actions. `app-web/tests/` renders
every screen in Node and asserts the markup, which is only possible because the
screens take a view-model and return a string.

## Boot

`js/main.js` runs three steps, in this order:

1. **Refuse to run in someone else's frame.** `frame-ancestors` cannot be sent by
   a `<meta>` CSP and GitHub Pages sends no headers, so the rule is enforced in
   JavaScript: inside a frame, the app draws a refusal and starts nothing
   (`framedRefusal()` in `views.js`). `scripts/test_app_web.py` asserts the check
   is still there.
2. **Load this browser's library** (`library.js`). If `localStorage` throws or a
   private window forgets what it wrote, the library is kept in memory and
   `persistent: false` is reported on Settings instead of a save being promised.
3. **Resolve the theme** from the settings (`dark`, `light`, `system`), write it
   to `<html data-dhun-theme>`, and render. The palettes are keyed on the root,
   not on `#app`: a theme on the app element themes the chrome and leaves the
   page behind it painted the other way (that was a shipped bug, and the browser
   run still measures it).

The shell regions already exist in `index.html` before the module runs, so the
first paint is the frame rather than a blank page, and a visitor with scripting
off sees a sentence saying so (`<noscript>`).

## Routing

`js/router.js` is data: `parse(hash)` → `{name, id, query, player, playerTab}`,
`format(route)` → a hash, plus `tab(route)`, `title(route, entityTitle)` and the
detail-route set. It is unit-tested as a round trip, including the deep links the
site emits (`#/?player=open&ptab=lyrics`).

Screens: `home`, `search`, `library`, `album`, `artist`, `playlist`,
`collection` (a playlist in this browser), `settings`, `about`. The `about` route
carries a document name (`#/about/privacy|terms|licences`). The full player is
not a route of its own: it is `?player=open` (and `?ptab=queue|lyrics`) on the
route underneath, so opening a track from Search returns to Search when it
closes.

A hash route is used because the app is served from a static host with no
rewrite rules: `#/album/123` is a URL that works on GitHub Pages, and `router.js`
`format()` is the only thing that writes one.

### Screen-owned catalogue requests

`main.js` owns separate AbortControllers for Home, Search and remote entities.
Replacing/clearing an intent or leaving its screen cancels that job. Existing
request-id guards remain, and both result and global badge/fallback publication
require the current owner. Interrupted data returns to idle and can retry on a
later visit. Aborting fetch cannot recall data already received upstream.

Initialization uses the route handler once. Hash queries are compared before
state assignment; form/recent/clear actions synchronize the parsed route when
replacing history. Player-only routing retains current jobs/data; explicit
Retry/Refresh can replace them. Player stream resolution remains independent.
`app-web/tests/navigation-requests.test.mjs` tests production boot/router logic
with Node doubles, not browser layout, device or sound acceptance.

A focused Search draft and selection survive a result/error rerender only within
the same request intent. A new explicit submit owns the canonical field value;
unsubmitted text is neither sent nor persisted. This retains the continued-typing
focus policy, not a guarantee about a phone's software keyboard or IME composition.

### Component and page lifetime

`boot()` returns an idempotent `destroy()` for explicit unmounting. Non-cached
`pagehide` disposes the component too: screen requests and that component's player
lookup are aborted, owned root/document/window/media-query/audio/PWA listeners
are released, and late callbacks cannot mutate its UI or navigate via its old
`go`. Another live component/request owner is not disposed. Event helpers return
cleanup functions; none of this unregisters a persistent service worker or clears
its caches. Accepted local storage transactions are not catalogue jobs: a pending
import may finish saving, but its obsolete UI continuation is ignored.

Cached `pagehide` instead suspends only screen jobs. `pageshow` retries interrupted
current-route work once; player resolution and PWA ownership survive the cached
page lifetime. A visibility change, progress rerender or player-only route change
is not teardown. There is no search debounce; searches begin on form/recent/filter/
route intents. Toast callbacks are guarded and their timer is cleared at teardown;
blob-URL revocation is resource cleanup, not UI publication.

These lifecycle transitions are tested with Node doubles, not actual browser
back/forward-cache, focus, offline activation or audio-output acceptance.

## The catalogue seam

`js/api.js` owns the catalogue contract, normalisation, failures and failover:

- **The contract.** One `ENDPOINTS` table, with query values encoded in it — a
  search for "Arijit & Pritam" is a realistic query and an unencoded `&` is a
  truncated search, not an error message.
- **Normalisation.** The API's records become the small shapes the screens draw
  (`normaliseSong`, `normaliseAlbum`, `normaliseArtist`, `normalisePlaylist`).
  Nothing downstream sees a raw response, so a missing field is handled once.
- **Failure.** A typed `SourceError` with a kind (`offline`, `timeout`, `http`,
  `shape`, `network`), and `describeFailure()`, which writes the
  sentence a visitor reads. The interface shows that sentence first and the
  underlying detail after it, labelled "Technical detail".

The API is JioSaavn, read through three community deployments of the same
open-source wrapper (`API_SOURCES` in `api.js`: `saavn.dev`, `saavn.sumit.co`,
`saavnx.vercel.app`), none with a support contract with this project.
`createRacer()` tries the instance that answered last, alone; if it fails, the
others are raced and the winner becomes the remembered instance. A total failure
carries a per-host record (`describeAttempts`) into the sentence a visitor
reads. Every instance must also appear in the CSP, which
`scripts/test_app_web.py` asserts by reading `api.js`. That is why the failure taxonomy exists: the
service can rate-limit, change shape or disappear, and the app has to survive
each of those as a state rather than as a blank screen. The CSP names exactly
those three wrapper origins and `*.saavncdn.com`, so a stray request to anywhere else fails
closed.

`js/demo.js` is the labelled sample catalogue: fixed rows, `demo: true`, an empty
stream. It is chosen only when the network fails, it is announced by a badge and
a notice on screen, and pressing play on one of its rows explains that a sample
row has no audio. It is never mixed with live results.

## Playback

`js/player.js` holds the queue and the transport as data: `STATUS`, `REPEAT`,
`nextIndex`, `previousIndex`, `shuffledOrder`, `playRejection`. A track is played
only after `pickStream()` returns a URL from the catalogue's own `downloadUrl`
list; a row without one is rejected with a sentence (`missingStreamError`), and
`playRejection()` maps a failed load to a state the interface can draw.

`player-ui.js` draws the full player. It never keeps its own clock: position and
duration come from the media element, so a progress bar cannot advance over
silence. The overlay is re-rendered on the player's own events, which is why the
scroll position of the panel is saved and restored around the render — a queue
scrolled to the fifth row used to jump to the top several times a second.

## The library

`js/library.js` stores one JSON document under `dhun.web.v3`: likes, playlists,
history, recent searches, a snapshot table and settings. The snapshot rule is the
important one — likes and history store *the row the visitor saw*, not only an
id, so a saved track can be drawn with no network at all. Playlists store ids and
resolve them through that table, which is what makes a playlist open after a
reload with no connection. Every mutation returns the new library plus what
changed (`{library, liked, changed}`, `{library, playlist}`), so the caller
decides what to announce and the tests can pin the shape.

`js/transfer.js` is the file format for export and import: a versioned JSON
document with the same shape, validated on the way in so an import cannot put a
malformed row into the store.

## The interface

`js/components.js` is the vocabulary: `card`, `rail`, `songRow`, `songList`,
`chip`, `loadingState`, `emptyState`, `errorState`, `sheet`, `notice`,
`sourceBadge`, `toast`, `pageHeader`. `js/dom.js` supplies the `html` tagged
template — every interpolated value is escaped unless it is wrapped in `raw()`,
which is what stops a track title from becoming markup.

`js/shell.js` draws the chrome: the app bar, the three-destination navigation
(Home, Search, Library), the rail at ≥840 px, and the docked mini player.
`js/main.js` owns the interaction model: **one** delegated click listener
dispatching a table of actions (`data-action="…"`) plus one submit handler and
one input handler for the fields that need one. Adding a control means adding an
entry to that table; the Python gate fails when a `data-action` in the chrome is
not in it.

Sheets are the app's dialogs. `role="dialog" aria-modal="true"` is a promise the
shell keeps rather than a declaration it prints: focus moves into the sheet, Tab
and Shift+Tab wrap inside it, Escape closes it, and closing returns focus to the
button that opened it.

## Offline

`app-web/src/sw.js` caches **the shell** — the document, both stylesheets, every
module, the manifest and the icons — cache-first with a background revalidate on
navigation. It caches **no audio and no API response**: a stream URL is signed
and expires, and a stale home feed presented as current is worse than an honest
"you are offline".

The shell list is explicit and `tools/build.mjs` checks it against the real build
output, so a module that is not listed fails the build rather than making a
half-working offline app. A new worker installs and waits; the page shows a
notice to close and reopen DHUN, and the worker takes over when no DHUN tab is
open. There is no reload-now control (see IMPLEMENTATION-PLAN §2). The cache name
carries the app version, so an old cache is deleted rather than accumulated.

## Styling

`src/css/tokens.css` mirrors the app's design tokens
(`shared/…/design/DhunAppearance.kt`, `DhunTypography.kt`, `DhunShapes.kt`,
`DhunSpacing.kt`, `DhunAnimations.kt`, `DhunIcons.kt` for the icon paths):
the surface ladder, the glass alphas, both themes, six accent ramps, the type and
space scales. Every colour in `app.css` is a token and every length is a token or
a percentage — `scripts/test_app_web.py` fails on a raw hex, a raw pixel value
outside the token file, or a custom property the stylesheets read but nobody
defines.

## The legal pages

`js/legal.js` + `js/markdown.js` render the web build's own privacy policy, terms
and licences from `js/data/web-legal.js`. They are not the installed app's
documents: those describe downloads and a SQLite store that a browser tab cannot
honour, so the web build ships its own text, which says what this build stores
and what it sends. The generated `js/data/legal-content.js` is an excluded
native-corpus parity fixture, not browser policy. Terms/Privacy remain DRAFT;
controller, private contact, permission and legal-adoption decisions remain open.


## Runtime reliability boundaries

The existing module boundaries remain. Transport uses a stale-result generation
and playback intent; a separate ephemeral selection counter deduplicates history
writes. FullPlayer compares structural view data and patches only scrubber/time
progress, avoiding repeated animation/control replacement. This is a small render
path correction, not a virtual-DOM framework or architecture replacement.

Storage read access is independent of write probing. Ordinary failed edits can
remain in memory, while destructive changes/import require acknowledged writes
before publishing replacement state. Shell cache updates do not overwrite known-good
HTML with HTTP errors; cache refusal does not discard usable online responses.
No audio/API caching, new origin/provider/backend, schema/key or native-runtime
change is introduced. Executable regressions are in
[runtime tests](../../app-web/tests/); device/provider/audio limits remain open.
