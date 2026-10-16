#!/usr/bin/env node
/**
 * Every state the app can be in, rendered to one page you can open.
 *
 * Why this exists. The failure states of this app are not edge cases: the
 * primary source is a cross-origin request to an origin nobody here controls,
 * so "the source refused" is one of the first things a real visitor can meet. A
 * state that only appears when somebody else's server says no is a state that
 * never gets looked at, and this app shipped exactly that mistake — an error
 * screen whose headline was the browser's `TypeError: Failed to fetch`.
 *
 * The page this writes is built from **the real modules**: the same `views.js`,
 * `player-ui.js` and `source.js` the app loads, called with the view models a
 * real failure produces. Nothing here is a mock-up and nothing is a screenshot;
 * if a state reads badly on this page, it reads badly in the app.
 *
 * It is a **development tool**, and deliberately not part of the deployed app:
 * it is written to a path outside the repository (a temp directory by default),
 * it is never copied into `app-web/dist`, and `tools/build.mjs` would fail on it
 * if it were — the build ships the app's own files and nothing else.
 *
 * Usage:
 *   node tools/states.mjs [--out <dir>] [--scheme dark|light|both]
 */

import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import * as views from "../src/js/views.js";
import { miniPlayer, fullPlayer } from "../src/js/player-ui.js";
import { ENDPOINT_CONTRACT, failure, failureSummary, stateError, SOURCE_KIND } from "../src/js/source.js";
import { AUDIO_STATUS } from "../src/js/audio.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.join(here, "..", "src");

const args = process.argv.slice(2);
const outIndex = args.indexOf("--out");
const outDir = outIndex >= 0 ? args[outIndex + 1] : "/tmp/dhun-states";
const schemeIndex = args.indexOf("--scheme");
const scheme = schemeIndex >= 0 ? args[schemeIndex + 1] : "both";

const tokensCss = readFileSync(path.join(src, "css", "tokens.css"), "utf8");
const appCss = readFileSync(path.join(src, "css", "app.css"), "utf8");

/* ------------------------------------------------------------ view models -- */

const track = (id, extra = {}) => ({
  id,
  videoId: id,
  title: `A track ${id}`,
  artist: "An artist",
  album: "An album",
  durationMs: 214_000,
  ...extra,
});

const TRACKS = [track("a"), track("b"), track("c")];

const SETTINGS = Object.freeze({
  theme: "dark",
  accent: "brand",
  volume: 0.8,
  muted: false,
  autoplayNext: true,
  normalizeLoudness: false,
  crossfadeMs: 0,
  equalizerEnabled: false,
  equalizerPresets: [],
  equalizerPreampDb: 0,
  equalizerGains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  romanizeLyrics: false,
  showRomanization: false,
  lyricsEnabled: true,
  sourceId: "auto",
  endpointUrl: "",
  recentSearches: [],
  cacheSizeMb: 0,
  saveHistory: true,
  telemetry: false,
});

/* The platform shape `views.settingsScreen` reads: `platform.isAvailable`-style
   booleans plus the list of what this browser cannot do, which the screen prints
   as caveats. See `platform.unavailableCapabilities`. */
const PLATFORM = Object.freeze({
  language: "en-GB",
  network: { online: true, effectiveType: "4g", saveData: false },
  storage: true,
  indexedDb: true,
  audioContext: true,
  mediaSession: true,
  serviceWorker: true,
  broadcast: true,
  wakeLock: true,
  installable: true,
  standalone: false,
  reduceMotion: false,
  unavailable: [],
});

const CAPABILITIES = Object.freeze({ ...PLATFORM, audioContext: true });
const BUILD = Object.freeze({ version: "1.00.002", label: "pre-release" });

/** Every player state the chrome can be in. */
const playerVm = (extra = {}) => ({
  track: TRACKS[0],
  playing: false,
  positionMs: 0,
  durationMs: 214_000,
  bufferedMs: 0,
  queue: TRACKS,
  index: 0,
  repeat: "off",
  shuffle: false,
  failures: {},
  error: null,
  needsGesture: false,
  audioStatus: AUDIO_STATUS.IDLE,
  resolving: false,
  volume: 0.8,
  muted: false,
  liked: false,
  tab: "queue",
  lyrics: { status: "idle", lines: [], activeIndex: -1 },
  romanize: false,
  canProcess: true,
  eqActive: false,
  eqLabel: "Equaliser off",
  context: "",
  sourceLabel: null,
  ...extra,
});

const demoSource = { kind: SOURCE_KIND.DEMO, detail: "These titles are invented and no stream exists for them." };
const liveSource = { kind: SOURCE_KIND.INNERTUBE, detail: "Requests go from this browser to music.youtube.com." };

/* The failures, built by the app's own constructors — the same ones a refused
   network, a 502 and an expired stream produce. */
const REFUSED = stateError(failure("network", new TypeError("Failed to fetch")));
const HTTP = stateError(failure("http", new Error("HTTP 502 from /youtubei/v1/player")));
const SEARCH_HTTP = stateError(failure("http", new Error("HTTP 502 from /youtubei/v1/search")));
const SHAPE = stateError(failure("shape", new Error("the search response had no list renderers"), { kind: "shape" }));
/** A tiny stand-in for `dom.js`'s tag function, for the two sections that need
 *  to compose markup from an array. Real screens import the real one. */
const html = (strings, ...values) =>
  strings.reduce((out, piece, index) => {
    const value = values[index];
    return out + piece + (Array.isArray(value) ? value.join("") : (value ?? ""));
  }, "");
const TIMEOUT = stateError(failure("network", new TypeError("The operation was aborted")));
const PLAYABILITY = stateError(failure("playability", new Error("Video unavailable"), { kind: "playability" }));
const DEMO_FAILURE = stateError(
  failure("demo", new Error('"A track a" is a fictional demo entry, so there is no audio to fetch'), { kind: "demo" }),
);

/* --------------------------------------------------------------- sections -- */

/**
 * The frame a state needs.
 *
 * Some states are full-screen overlays: the player is `position: absolute;
 * inset: 0` and the mini-player docks to the bottom edge. Undecorated in a long
 * review page, the first of them covers everything after it — which is exactly
 * what happened the first time this page was read. So each section is rendered
 * inside its own relatively-positioned frame, at the size the state is designed
 * for: a phone for the player and the sheets, a wide pane for the screens.
 */
const FRAME = Object.freeze({
  phone: { width: 390, height: 844, label: "390 × 844" },
  phoneTall: { width: 390, height: 620, label: "390 × 620" },
  wide: { width: 1024, height: 900, label: "1024 × 900" },
  flow: { width: 0, height: 0, label: "natural height" },
});

/** name, what produces it in the app, and the markup itself. */
const SECTIONS = [
  {
    id: "boot",
    title: "The refusal — nothing answered",
    where: "main.js → sourceState(), the strip above every screen",
    note: "What a visitor on a network that blocks the request (or a proxy, or an ad-blocker) meets first. The sentence is the app's; the browser's own words sit under it, labelled, for a bug report.",
    render: () =>
      views.notice({
        tone: "warn",
        title: "Showing the demo catalogue",
        body: `${failureSummary(REFUSED)} These rows are the demo catalogue: invented titles with no audio.`,
        actions: [{ id: "open-settings-source", label: "Data source" }],
        hook: "demo",
      }),
  },
  {
    id: "home-error",
    title: "Home could not be loaded",
    where: "views.homeScreen({ error }) — Automatic turned off, or no source at all",
    note: "The two-register failure: what happened, in the app's words; then the raw string, named, on the quiet rung.",
    render: () =>
      views.homeScreen({
        shelves: [],
        loading: false,
        error: REFUSED,
        actions: [],
      }),
  },
  {
    id: "home-demo",
    title: "Home, answering from the demo catalogue",
    where: "views.homeScreen({ shelves, source })",
    note: "Rows that are labelled as fictional at both ends: the badge above them and the strip below.",
    render: () =>
      views.homeScreen({
        shelves: [
          { title: "Quick picks", entries: TRACKS.map((entry) => ({ ...entry, demo: true })) },
          { title: "Listen again", entries: [track("d", { demo: true })] },
        ],
        loading: false,
        error: null,
        currentId: null,
        demoTracks: TRACKS,
        source: demoSource,
      }),
  },
  {
    id: "home-loading",
    title: "Home, loading",
    where: "views.homeScreen({ loading: true })",
    note: "A skeleton that says what it is waiting for, to a screen reader as well as to the eye.",
    render: () => views.homeScreen({ shelves: [], loading: true, error: null }),
  },
  {
    id: "home-empty",
    title: "Home, answered with nothing in it",
    where: "views.homeScreen({ shelves: [] })",
    note: "An empty answer is not an error, and it is not dressed as one.",
    render: () => views.homeScreen({ shelves: [], loading: false, error: null, source: liveSource }),
  },
  {
    id: "search-error",
    title: "Search did not go through",
    where: "views.searchScreen({ status: \"error\", results })",
    note: "A server error, named with its status code in the sentence so nobody has to open developer tools to learn it.",
    render: () => views.searchScreen({ query: "a song", status: "error", results: SEARCH_HTTP, filter: "all", source: liveSource }),
  },
  {
    id: "search-empty",
    title: "Search, no results",
    where: "views.searchScreen({ results: { tracks: [] } })",
    note: "Where the visitor gets the sentence that distinguishes \"nothing matched\" from \"nothing answered\".",
    render: () =>
      views.searchScreen({
        query: "zzzz",
        status: "ready",
        results: { tracks: [], albums: [], artists: [], playlists: [] },
        filter: "all",
        recentSearches: ["koshi", "nepali jazz"],
        source: liveSource,
      }),
  },
  {
    id: "search-ready",
    title: "Search, real results",
    where: "views.searchScreen({ status: \"ready\" })",
    note: "The source badge is on every list, not only on the failures.",
    render: () =>
      views.searchScreen({
        query: "koshi",
        status: "ready",
        results: { tracks: TRACKS, albums: [], artists: [], playlists: [] },
        filter: "tracks",
        recentSearches: ["koshi"],
        currentId: "b",
        source: liveSource,
      }),
  },
  {
    frame: FRAME.phone,
    id: "row-failure",
    title: "A track that could not be resolved",
    where: "player-ui.js → the queue, with player.js's recorded failure on the row",
    note: "Three rows, one of which failed: the failing row keeps its place and says why, and the queue is not renumbered around it. This is the real path — the queue renderer passes the failure row by row.",
    render: () =>
      html`${fullPlayer(
        playerVm({
          failures: { 1: { kind: "http", message: "HTTP 502 from /youtubei/v1/player" } },
          error: { kind: "http", message: "HTTP 502 from /youtubei/v1/player" },
        }),
      )}
      <ul class="dhun-tracklist">
        ${TRACKS.map((entry, index) =>
          views.trackRow(entry, {
            index,
            failed: index === 1 ? { kind: "network-or-cors", message: "Failed to fetch" } : null,
          }),
        )}
      </ul>`,
  },
  {
    id: "playability",
    title: "The source refused the track",
    where: "views.errorState({ body: PLAYABILITY })",
    note: "\"Video unavailable\" is the source's own reason. The app does not translate it into something friendlier and less true.",
    render: () =>
      views.errorState({
        title: "That track cannot be played",
        body: PLAYABILITY.help,
        technical: PLAYABILITY.technical,
        actions: [{ id: "go-home", label: "Go home" }],
      }),
  },
  {
    id: "timeout",
    title: "The source stopped answering",
    where: "stateError(failure(\"network\", aborted))",
    note: "A timeout is its own sentence: it is not a cross-origin refusal, and the app does not pretend they are the same thing.",
    render: () =>
      views.errorState({
        title: "The request was stopped",
        body: TIMEOUT.help,
        technical: TIMEOUT.technical,
        actions: [{ id: "retry-search", label: "Try again" }],
      }),
  },
  {
    id: "shape",
    title: "The source changed what it sends",
    where: "stateError(failure(\"shape\", …))",
    note: "The one failure that must not say \"try again\": a retry cannot fix a contract change.",
    render: () =>
      views.errorState({
        title: "Home could not be loaded",
        body: SHAPE.help,
        technical: SHAPE.technical,
        actions: [{ id: "open-settings-source", label: "Change data source" }],
      }),
  },
  {
    id: "offline",
    title: "Offline",
    where: "main.js → renderBanner() when the browser reports no connection",
    note: "The interface still works and the library is still here; only tracks need the network. Said once, at the top, rather than as an error on every screen.",
    render: () =>
      views.notice({
        tone: "warn",
        title: "You are offline",
        body: "The interface still works and your library is here. Tracks need a connection: stream URLs are not stored on the device.",
      }),
  },
  {
    id: "no-storage",
    title: "A browser that will not keep the library",
    where: "views.libraryScreen({ libraryKind: \"memory\" }) — private windows, blocked storage",
    note: "Degrading honestly beats failing loudly: the library works for this tab and the screen says nothing will be kept.",
    render: () => views.libraryScreen({ tab: "playlists", playlists: [], liked: [], history: [], libraryKind: "memory" }),
  },
  {
    id: "library-empty",
    title: "An empty library",
    where: "views.libraryScreen({ tab: \"playlists\" })",
    note: "Empty states name the next action instead of ending the sentence.",
    render: () => views.libraryScreen({ tab: "playlists", playlists: [], liked: [], history: [], libraryKind: "indexeddb" }),
  },
  {
    frame: FRAME.phone,
    id: "player-gesture",
    title: "The browser is waiting for a gesture",
    where: "fullPlayer(viewModel with needsGesture) — the autoplay policy",
    note: "Not an error. A prompt with the button that resolves it.",
    render: () => fullPlayer(playerVm({ playing: false, audioStatus: AUDIO_STATUS.BLOCKED, needsGesture: true, error: null })),
  },
  {
    frame: FRAME.phone,
    id: "player-refused",
    title: "The player, refused",
    where: "fullPlayer(viewModel with error.kind === \"network-or-cors\")",
    note: "The sentence, the retry, the data-source switch, and \"Why?\" — a refusal is the one failure with a written explanation rather than a fix.",
    render: () => fullPlayer(playerVm({ error: REFUSED, audioStatus: AUDIO_STATUS.ERROR })),
  },
  {
    frame: FRAME.phone,
    id: "player-demo",
    title: "The player, on a demo row",
    where: "fullPlayer(viewModel with error.kind === \"demo\")",
    note: "The demo catalogue's own sentence, which is better than any generic line the app could write about it.",
    render: () => fullPlayer(playerVm({ error: DEMO_FAILURE })),
  },
  {
    frame: FRAME.phone,
    id: "player-playing",
    title: "The player, playing",
    where: "fullPlayer(viewModel with audioStatus PLAYING)",
    note: "The state everything else exists to reach. Nothing on this page claims it has been heard — that is Phase 2.",
    render: () =>
      fullPlayer(
        playerVm({
          playing: true,
          audioStatus: AUDIO_STATUS.PLAYING,
          positionMs: 74_000,
          bufferedMs: 96_000,
          liked: true,
        }),
      ),
  },
  {
    frame: FRAME.phoneTall,
    id: "mini-player",
    title: "The docked mini-player",
    where: "miniPlayer(viewModel)",
    note: "What sits above the bottom navigation on every route.",
    render: () => html`<div class="dhun-dock" style="position:absolute;inset:auto 0 0 0">
      ${miniPlayer(playerVm({ playing: true, audioStatus: AUDIO_STATUS.PLAYING, positionMs: 74_000 }))}
      <nav class="dhun-bottom-nav" aria-label="Primary">
        <span class="dhun-nav-item" aria-current="page"><span class="dhun-nav-item__label">Home</span></span>
        <span class="dhun-nav-item"><span class="dhun-nav-item__label">Search</span></span>
        <span class="dhun-nav-item"><span class="dhun-nav-item__label">Your library</span></span>
      </nav>
    </div>`,
  },
  {
    frame: FRAME.phoneTall,
    id: "framed",
    title: "DHUN inside somebody else's frame",
    where: "views.framedRefusal() — platform.isFramed()",
    note: "The app refuses to boot in a frame, and explains itself instead of rendering a shell inside a stranger's page.",
    render: () => views.framedRefusal(),
  },
  {
    frame: FRAME.wide,
    id: "settings",
    title: "Settings",
    where: "views.settingsScreen({ … })",
    note: "Where the data source is chosen, the equaliser lives, and the library's storage kind is stated.",
    render: () =>
      views.settingsScreen({
        settings: SETTINGS,
        platform: PLATFORM,
        capabilities: CAPABILITIES,
        sources: [
          { id: "auto", label: "Automatic", detail: "Your endpoint if set, otherwise YouTube Music directly." },
          { id: "endpoint", label: "Your endpoint only", detail: "No address configured yet." },
          { id: "innertube", label: "YouTube Music only", detail: "Never silently falls back to sample data." },
          { id: "demo", label: "Demo catalogue only", detail: "Fictional rows, no audio, always available." },
        ],
        sourceState: {
          tone: "warn",
          title: "Showing the demo catalogue",
          body: `${failureSummary(REFUSED)} These rows are the demo catalogue: invented titles with no audio.`,
          detail: demoSource.detail,
          kind: SOURCE_KIND.DEMO,
          actions: [{ id: "open-settings-source", label: "Data source" }],
        },
        equalizer: {
          enabled: false,
          presets: [{ id: "flat", name: "Flat" }, { id: "bass", name: "Bass boost" }],
          activePresetId: "flat",
          preampDb: 0,
          bands: [60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000].map((hz, index) => ({
            index,
            hz,
            label: `${hz} Hz`,
            gainDb: 0,
          })),
        },
        library: { kind: "indexeddb", counts: { playlists: 2, likes: 4, history: 12 } },
        pwa: { sw: "registered", update: null, installable: true, standalone: false },
        diagnostics: {
          rows: [
            { label: "Build", value: `${BUILD.version} · ${BUILD.label}` },
            { label: "Route", value: "#/settings" },
            { label: "Data source", value: "YouTube Music — anonymous" },
            { label: "Last source used", value: "demo", state: "warn" },
            { label: "Library storage", value: "IndexedDB", state: "ok" },
            { label: "Settings storage", value: "localStorage", state: "ok" },
            { label: "Media Session controls", value: "available", state: "ok" },
            { label: "Web Audio (equaliser)", value: "available, idle", state: "ok" },
            { label: "Equaliser on this stream", value: "no graph yet", state: "neutral" },
            { label: "Service worker", value: "registered", state: "ok" },
            { label: "Cross-tab control", value: "available", state: "ok" },
            { label: "Network", value: "online", state: "ok" },
            { label: "Standalone", value: "browser tab" },
            { label: "Stream cache", value: "0 resolved URL(s)" },
          ],
          contract: ENDPOINT_CONTRACT,
          streamCacheSize: 0,
        },
      }),
  },
];

/* ------------------------------------------------------------------ render -- */

function page(title, body, theme) {
  return `<!doctype html>
<html lang="en" data-dhun-theme="${theme}" data-dhun-accent="brand">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${title}</title>
    <!-- Development review page. Generated by app-web/tools/states.mjs; never
         written into app-web/dist and never deployed. The stylesheet is the
         app's own, inlined so the file works from anywhere. -->
    <style>
${tokensCss}
    </style>
    <style>
${appCss}
    </style>
    <style>
      body { margin: 0; background: var(--dhun-background); color: var(--dhun-text); }
      .review { max-width: 1080px; margin: 0 auto; padding: var(--dhun-space-xxl, 32px) var(--dhun-space-screen-padding);
        font: var(--dhun-text-body-medium); }
      .review > header h1 { font: var(--dhun-text-headline-large); margin: 0 0 var(--dhun-space-sm); }
      .review > header p { max-width: 68ch; color: var(--dhun-text-secondary); }
      .review hr { border: 0; border-top: var(--dhun-space-border) solid var(--dhun-border); margin: var(--dhun-space-xxl, 32px) 0; }
      .state { margin: 0 0 var(--dhun-space-xxl, 32px); }
      .state > h2 { font: var(--dhun-text-title-large); margin: 0 0 var(--dhun-space-xs); }
      .state > .where { margin: 0 0 var(--dhun-space-xs); font: var(--dhun-text-body-small); color: var(--dhun-text-secondary); }
      .state > .where code { font-family: var(--dhun-font-mono); }
      .state > .note { margin: 0 0 var(--dhun-space-md); max-width: 68ch; font: var(--dhun-text-body-small); color: var(--dhun-text-secondary); }
      .stage { border: var(--dhun-space-border) solid var(--dhun-border); border-radius: var(--dhun-shape-card); padding: var(--dhun-space-md); background: var(--dhun-surface); }
      .review code { font-family: var(--dhun-font-mono); }
      /* The frame each state is designed for. The relative position is what
         keeps a full-screen overlay inside its own section instead of
         covering the page; the size is the size the state is for, so what is
         on screen is what a visitor's device shows. */
      .review__frame {
        position: relative;
        overflow: hidden;
        border: var(--dhun-space-border) solid var(--dhun-border);
        border-radius: var(--dhun-shape-card-large, 16px);
        background: var(--dhun-background);
        margin-inline: auto;
        max-width: 100%;
      }
      .review__frame--flow { overflow: visible; border: 0; border-radius: 0; }
      .review__frame-label {
        display: block;
        margin: var(--dhun-space-xs) 0 0;
        font: var(--dhun-text-label-small);
        letter-spacing: var(--dhun-tracking-label-small);
        color: var(--dhun-text-secondary);
        text-align: center;
      }
      /* A sheet or an overlay that is fixed-positioned in the app is anchored
         to its frame here. Only the position is overridden; every other
         declaration — including the layout the state actually uses — is the
         app's. */
      .review__frame .dhun-overlay-mount,
      .review__frame .dhun-sheet-mount,
      .review__frame .dhun-toast-mount {
        position: absolute;
      }
    </style>
  </head>
  <body>
    <main class="review">
      <header>
        <h1>DHUN web — every state, rendered</h1>
        <p>
          Built from the app's own modules by <code>app-web/tools/states.mjs</code>. Not a screenshot and not a
          mock-up: the markup below is what <code>views.js</code>, <code>player-ui.js</code> and <code>source.js</code>
          return for each view model, styled by the app's own stylesheet. Buttons are inert here — they carry the
          action names the app wires up.
        </p>
        <p>
          <strong>This page measures nothing.</strong> It is a surface for a person to look at; the browser job in CI
          is what measures contrast, tab order and overflow. And no state below is a claim about playback:
          ${"`docs/web-player/PHASE-02-PLAYBACK-POC.md`"} is where that stands, and it is still open.
        </p>
      </header>
      ${body}
    </main>
  </body>
</html>
`;
}

mkdirSync(outDir, { recursive: true });
const wanted = scheme === "both" ? ["dark", "light"] : [scheme];
const written = [];
for (const theme of wanted) {
  const body = SECTIONS.map((section) => {
    const frame = section.frame ?? FRAME.flow;
    const style = frame.width
      ? `max-width:${frame.width}px;${frame.height ? `height:${frame.height}px;` : ""}`
      : "";
    const open = `<div class="review__frame${frame.width ? "" : " review__frame--flow"}" style="${style}">`;
    return `      <hr />
      <section class="state" id="${section.id}">
        <h2>${section.title}</h2>
        <p class="where">${section.where.replace(/`([^`]+)`/g, "<code>$1</code>")}</p>
        <p class="note">${section.note}</p>
        ${open}
${section.render()}
        </div>
        <span class="review__frame-label">${frame.label}${frame === FRAME.flow ? "" : " · the state's own frame"}</span>
      </section>`;
  }).join("\n");
  const file = path.join(outDir, `states-${theme}.html`);
  writeFileSync(file, page(`DHUN web states — ${theme}`, body, theme), "utf8");
  written.push(file);
}

console.log(`states: ${SECTIONS.length} state(s) → ${written.join(", ")}`);
