# 29 — The browser mirror of the app (`app-web/`)

Session `arena/967513fd-dhun`, 2026-10-09. Branch point and GitHub `main` at
boot `d82aa190b702cd0e3fe42dbff34c7a0c6e84e2cc` (PR #143).

Status vocabulary: **verified** = read out of a tool output in this session;
**not verified** = explicitly unchecked; **expected** = plausible but unchecked.

## What was built

A new isolated top-level module, `app-web/`: the DHUN interface in a browser,
mirroring the Compose UI rather than reinterpreting it. No runtime dependency —
vanilla ES modules, two stylesheets, one HTML file.

| Claim | State | Evidence |
|---|---|---|
| Module builds | **verified** | `node tools/build.mjs` → 14 files, 83,741 bytes |
| Node tests | **verified** | `cd app-web && npm test` → **60 tests, 60 pass, 0 fail** |
| Python contract tests (CI gate) | **verified** | `python3 -m unittest discover -s scripts -p 'test_*.py'` → **295 tests OK** (23 of them new, in `scripts/test_app_web.py`) |
| Dev server serves the app | **verified** | `node tools/serve.mjs 4173` → `index.html` 200; `js/main.js` 26,905 B; `css/tokens.css` 16,559 B; `css/app.css` 33,274 B; `js/icons.js` 6,178 B |
| Icon set matches the app | **verified** | `tools/gen-icons.mjs --check` → 31 icons, generated from `shared/src/commonMain/kotlin/dev/dhun/design/DhunIcons.kt` |
| Colour tokens trace to the app | **verified** | every colour literal in `css/tokens.css` is asserted to exist in `DhunAppearance.kt` (60+ literals, both themes, six accent ramps) — enforced in Node *and* in `scripts/test_app_web.py` |
| No raw hex / raw px outside `tokens.css` | **verified** | two tests, one per runner; `@media` conditions excepted because CSS cannot use a custom property in a media query, and the two breakpoint literals are cross-checked against the token file |
| No third-party runtime asset | **verified** | no external `src`/`href`, no `@import`, no `@font-face`, no `node_modules`, no `dist` committed; `package.json` declares no dependency of any kind |
| Page is `noindex` + strict CSP | **verified** | `index.html`: `noindex, nofollow`; `default-src 'none'; script-src 'self'; … base-uri 'none'` |
| Layout / paint / real input events | **not verified** — no browser exists in this sandbox and no browser binary can be fetched (egress allowlist) | — |
| Live metadata | **not verified** — the sandbox reaches github.com, npm and pypi only | the app falls back to the bundled sample catalogue and says so |

## Fidelity, screen by screen

Each row is a mirror of a Compose file, not an interpretation of one.

| Web module | Mirror of | Mirrored |
|---|---|---|
| `js/nav.js` | `ui/shell/AppNavState.kt`, `DhunShellLayout.kt` | tab order (Home, Search, Library; CATALOG excluded as in the app), `MAX_TAB_HISTORY = 8`, the rail/two-pane breakpoint at `navigationRailBreakpoint` 840, `backAction` order, Settings-as-singleton |
| `css/tokens.css` | `design/DhunAppearance.kt`, `DhunTypography.kt`, `DhunShapes.kt`, `DhunSpacing.kt`, `DhunAnimations.kt` | 5-rung dark surface stack, glass ladder, 4-step alpha text ladder, both themes, six accent ramps, M3 type scale, shape scale, spacing scale, 150/300/500 ms |
| `js/icons.js` | `design/DhunIcons.kt` | 31 vectors, generated from the Kotlin enum |
| `js/views.js` (Home) | `ui/home/HomeScreen.kt` | Quick picks shelf (`quickPickWidth` 260), Recommended songs, Listen again |
| `js/views.js` (Search) | `ui/search/SearchScreen.kt` | placeholder "Search YouTube Music for songs, albums, and artists", Recent searches, Songs/Artists/Albums/Playlists sections, "No results found" |
| `js/views.js` (Library) | `ui/library/LibraryScreen.kt` | Playlists / Downloads / History, "No downloads yet", "No history yet", "My playlist" |
| `js/views.js` (Settings) | `ui/settings/SettingsScreen.kt` | Appearance, "Playback & storage", "Audio cache size" chips (256 MB → 4 GB, Unlimited), "Resume on launch", Equalizer |
| `js/player-ui.js` | `ui/player/MiniPlayer.kt`, `FullPlayer.kt`, `TransportControls.kt`, `PlayerSeekBar.kt` | 72 dp dock, 56 dp thumb, progress hairline, transport at 52 dp, Up next / Lyrics tabs, wide layout ≥ 480 dp landscape (`PlayerLayout.kt`) |
| `js/player.js` | `player/QueueManager.kt`, `DhunPlayer` | queue, repeat off/all/one, shuffle that never repeats a row, previous-restarts-then-steps, end-of-queue stops without repeat-all |
| `js/equalizer.js` | `player/equalizer/EqualizerBands.kt`, `EqualizerPreset.kt`, `EqualizerSession.kt` | 10 libVLC bands (60 Hz → 16 kHz), ±20 dB, 18 VLC presets verbatim, preamp, "Custom" on any off-curve move, 0.05 dB match epsilon |
| `js/lyrics.js` | `lyrics/LrcParser.kt` | `[mm:ss.xx]`, multiple stamps per line, metadata tags skipped, enhanced word timings stripped, unsynced fallback, sorted output |

Omitted, with the reason (per the B3 table in the ADR-008 amendment):
offline downloads (no browser equivalent of ADR-006's device store), the
desktop/Android platform surfaces (tray, SMTC, jump lists, widgets), and live
playback (unproven, labelled, never faked).

## The honest part

**No audio plays.** No stream is reachable from this origin — the B1 spike in
`web-spike/` was blocked before a readable response (verification record 19),
and this sandbox's egress allowlist blocks everything but github.com, npm and
pypi. So the transport advances a *labelled clock*: the seek bar, queue,
lyrics sync and equaliser can all be exercised, nothing is audible, and the
page says so in a persistent notice (`data-testid="backend-notice"`, asserted
by both `tests/boot.test.mjs` and `scripts/test_app_web.py`).

The metadata shown is a **fictional sample catalogue** bundled with the module
(`js/data/sample-catalog.js`), again labelled on screen. It exists so the
interface can be seen and tested without an upstream; it is not presented as
the real catalogue, and a live InnerTube source is attempted first and reports
its own failure verbatim when it is refused.

Adding a proxy to make playback work would breach ADR-008's boundary 1 and was
not done.

## Also in this session

- `docs/runbooks/publishing-the-site.md` regained the "Pages falls back to
  legacy" recovery path it lost in `d82aa19`, which re-greens
  `scripts/test_website_workflow.py::PublishingRunbook` — the only red test on
  `main` at boot (CI run `37831998519`, step "Packaging and fixture helper
  tests").
- `website/tests/browser.mjs`: the parked measurement-loss bug is fixed. The
  carry annotation clipped at 24 KB while GitHub clips a message at roughly
  4 KB, so everything past the first screenful — both axe scans among it —
  was dropped with the run still green. Packing now lives in
  `website/tests/annotation-report.mjs` (budgeted, capped, and tested: 8 new
  node tests), and the full report also goes to the job log and to
  `$GITHUB_STEP_SUMMARY`, which are not capped. **Not verified in a live run** —
  its effect is only observable in CI.

## Fixes found after the first `app-web` commit

All four were found by re-reading the diff, not by a browser: there is no engine
here, so a visual bug is caught by reading or not at all.

| Defect | Fix | Verified |
|---|---|---|
| `.dhun-rail` named both the large-screen navigation rail and the horizontal quick-picks shelf; the later rule won and the rail rendered as a row | the shelf is now `.dhun-shelf` | rules are distinct; **not** verified in a browser |
| `.dhun-tracklist > li` had no `display:flex`, so a row button and its 48 dp overflow button stacked | the list item is a flex row; row times got `labelSmall` | same |
| `Add to playlist` rows reused the entry-point action, so choosing a playlist re-opened the same sheet | rows use `data-action="confirm-add-to-playlist"`; adding to *Liked Songs* is favouriting, matching the app's model | boot test asserts the sheet renders the confirm action and the Liked Songs row; the state change itself is untested (the DOM stub cannot click) |
| the full player's artwork was an `<img>` painted over its own gradient | it is a `<span>`, like every other piece of artwork in the app | boot test renders the full player |

Each is also recorded in `.ai/DEBUG_LOG.md`.
