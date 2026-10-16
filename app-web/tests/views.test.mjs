/**
 * The screens render, escape and tell the truth.
 *
 * A view layer with no browser is testable exactly to the extent that it is a
 * pure function of its view model, which is why these screens are strings. What
 * this file proves:
 *
 *   - every screen renders without throwing, for an empty, a full and a failing
 *     view model;
 *   - user data is escaped (a track called `<img onerror=…>` is text, not markup);
 *   - nothing renders `undefined` or `[object Object]` into the page;
 *   - the honesty notices the repository requires are actually in the markup;
 *   - the removed device-only surfaces (Downloads above all) appear nowhere.
 */
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import * as views from "../src/js/views.js";
import { LEGAL_DOCUMENTS, legalDocumentById } from "../src/js/data/legal-content.js";
import { handledActions, actionsIn } from "./helpers/main-source.mjs";
import { miniPlayer, fullPlayer, statusLine } from "../src/js/player-ui.js";
import { AUDIO_STATUS } from "../src/js/audio.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const jsRoot = path.join(here, "..", "src", "js");

const track = (id = "tr-1", extra = {}) => ({
  id,
  videoId: id,
  title: `Song ${id}`,
  artist: "An artist",
  album: "An album",
  durationMs: 180_000,
  ...extra,
});

/** The bundled documents as the screen's index, the shape `main.js` builds. */
const legalIndex = (activeId) =>
  LEGAL_DOCUMENTS.map((entry) => ({
    id: entry.id,
    title: entry.title,
    status: entry.status,
    effectiveDate: entry.effectiveDate,
    href: `#/about-legal/${entry.id}`,
    active: entry.id === activeId,
  }));

const shell = {
  platform: {
    language: "en-GB",
    network: { online: true, effectiveType: "4g", saveData: false },
    storage: true,
    indexedDb: true,
    audioContext: true,
    mediaSession: true,
    serviceWorker: true,
    broadcast: true,
    wakeLock: true,
    installable: false,
    standalone: false,
    reduceMotion: false,
    unavailable: [],
  },
  pwa: { sw: "ready", installable: false, installed: false, online: true, updateReady: false, offlineReady: true, error: null },
  library: { kind: "indexeddb", counts: { playlists: 2, likes: 3, history: 4 } },
  build: { version: "2.0.0", label: "web" },
  diagnostics: {
    rows: [{ label: "Build", value: "2.0.0" }],
    contract: [{ method: "GET", path: "/health", returns: "{}" }],
    streamCacheSize: 0,
  },
  sourceState: { tone: "info", title: "YouTube Music — anonymous", body: "Requests go from this browser.", actions: [] },
  sources: [{ id: "auto", label: "Automatic", detail: "…" }],
  equalizer: {
    presets: [{ id: "flat", name: "Flat" }, { id: "custom", name: "Custom" }],
    activePresetId: "flat",
    bands: Array.from({ length: 10 }, (_, index) => ({ index, label: `${60 * (index + 1)}` })),
    status: "Off.",
  },
};

const settings = {
  theme: "dark",
  accent: "brand",
  volume: 1,
  repeat: "off",
  shuffle: false,
  resumeOnLaunch: true,
  lyricsEnabled: true,
  romanizeLyrics: true,
  normalizeVolume: false,
  equalizerEnabled: false,
  equalizerPreset: "flat",
  equalizerPreampDb: 0,
  equalizerGains: Array(10).fill(0),
  sourceId: "auto",
  endpointUrl: "",
  recentSearches: [],
  confirmClearLibrary: false,
};

const playerVm = (over = {}) => ({
  track: track(),
  playing: true,
  positionMs: 30_000,
  durationMs: 180_000,
  bufferedMs: 60_000,
  queue: [track("tr-1"), track("tr-2")],
  index: 0,
  repeat: "off",
  shuffle: false,
  failures: {},
  error: null,
  needsGesture: false,
  audioStatus: AUDIO_STATUS.PLAYING,
  resolving: false,
  volume: 1,
  muted: false,
  liked: false,
  tab: "queue",
  lyrics: { status: "idle", lines: [], activeIndex: -1, message: "" },
  romanize: true,
  canProcess: true,
  eqActive: false,
  eqLabel: "Equaliser off",
  context: "Album · Lowland Echoes",
  sourceLabel: null,
  ...over,
});

const screens = {
  home: () =>
    views.homeScreen({
      shelves: [
        { title: "Quick picks", entries: [track("a"), track("b")] },
        { title: "Recommended songs", entries: [track("c")] },
        { title: "Albums for you", entries: [{ kind: "album", id: "MPREb_1", title: "An album", artist: "A", year: "2025" }] },
      ],
      currentId: "a",
      source: { kind: "innertube" },
    }),
  homeEmpty: () => views.homeScreen({ shelves: [], loading: false, error: null, source: { kind: "innertube" } }),
  homeLoading: () => views.homeScreen({ shelves: [], loading: true, error: null }),
  homeError: () =>
    views.homeScreen({ shelves: [], loading: false, error: { message: "Failed to fetch", help: "…" } }),
  searchIdle: () => views.searchScreen({ query: "", results: null, status: "idle", source: { kind: "innertube" } }),
  searchRecent: () => views.searchScreen({ query: "", results: null, status: "idle", recentSearches: ["koshi"], source: { kind: "innertube" } }),
  searchReady: () =>
    views.searchScreen({
      query: "koshi",
      status: "ready",
      results: { tracks: [track("t1")], albums: [{ kind: "album", id: "MPREb_1", title: "A" }], artists: [], playlists: [] },
      source: { kind: "innertube" },
    }),
  searchEmpty: () => views.searchScreen({ query: "zzz", status: "ready", results: { tracks: [], albums: [], artists: [], playlists: [] } }),
  searchError: () => views.searchScreen({ query: "x", status: "error", results: { message: "HTTP 500", help: "…" } }),
  libraryPlaylists: () => views.libraryScreen({ tab: "playlists", playlists: [{ id: "pl-1", name: "Mix", trackIds: ["a"] }] }),
  libraryLiked: () => views.libraryScreen({ tab: "liked", liked: [track("a")] }),
  libraryHistory: () => views.libraryScreen({ tab: "history", history: [track("a")] }),
  libraryEmpty: () => views.libraryScreen({ tab: "playlists" }),
  libraryMemory: () => views.libraryScreen({ tab: "playlists", libraryKind: "memory" }),
  playlist: () => views.playlistScreen({ playlist: { id: "pl-1", name: "Mix" }, tracks: [track("a")], isOwn: true, missing: 2 }),
  playlistMissing: () => views.playlistScreen({ playlist: null, tracks: [] }),
  artist: () => views.artistScreen({ artist: { id: "UC1", name: "An artist" }, tracks: [track("a")], albums: [{ kind: "album", id: "x", title: "B" }] }),
  album: () => views.albumScreen({ album: { id: "MPREb_1", title: "An album", artist: "A", year: "2025" }, tracks: [track("a")] }),
  albumMissing: () => views.albumScreen({ album: null, tracks: [] }),
  settings: () => views.settingsScreen({ settings, ...shell, capabilities: shell.platform }),
  settingsNoAudio: () =>
    views.settingsScreen({
      settings,
      ...shell,
      capabilities: { ...shell.platform, audioContext: false },
    }),
  about: () =>
    views.aboutLegalScreen({
      documents: legalIndex("privacy"),
      activeId: "privacy",
      document: legalDocumentById("privacy"),
      platform: shell.platform,
      build: shell.build,
    }),
  menu: () => views.trackMenuSheet({ track: track(), inQueue: true, liked: true, playlists: [] }),
  picker: () => views.playlistPickerSheet({ track: track(), playlists: [{ id: "pl-1", name: "Mix" }] }),
  confirm: () => views.confirmSheet({ title: "Delete?", body: "…", confirmLabel: "Delete", action: "clear-library-confirm" }),
  shortcuts: () => views.shortcutsSheet({ platform: "Linux" }),
  toast: () => views.toast("Added to the queue."),
};

/**
 * `html` returns a String subclass marked as markup, which `assert.match` and
 * `strictEqual` do not accept. Rendering to a primitive is explicit here for the
 * same reason the marker exists: the difference between markup and data has to
 * be visible at the boundary, not assumed.
 */
const text = (value) => String(value).replace(/\s+/g, " ");
for (const [name, render] of Object.entries(screens)) screens[name] = () => text(render());

test("every screen renders, for an empty, a full and a failing view model", () => {
  for (const [name, render] of Object.entries(screens)) {
    const markup = render();
    assert.equal(typeof markup, "string", `${name} did not return markup`);
    assert.ok(markup.trim().length > 0, `${name} rendered nothing`);
  }
});

test("nothing renders undefined, null or [object Object] into the page", () => {
  for (const [name, render] of Object.entries(screens)) {
    const markup = render();
    assert.ok(!markup.includes("undefined"), `${name} leaked "undefined"`);
    assert.ok(!markup.includes("[object Object]"), `${name} leaked an object`);
    assert.ok(!markup.includes("NaN"), `${name} leaked NaN`);
  }
});

test("the player's two surfaces render in every state a browser can put them in", () => {
  const states = [
    playerVm(),
    playerVm({ playing: false, audioStatus: AUDIO_STATUS.PAUSED }),
    playerVm({ audioStatus: AUDIO_STATUS.BUFFERING }),
    playerVm({ audioStatus: AUDIO_STATUS.LOADING, resolving: true }),
    playerVm({ audioStatus: AUDIO_STATUS.BLOCKED, needsGesture: true }),
    playerVm({ error: { kind: "network-or-cors", message: "Failed to fetch" } }),
    playerVm({ error: { kind: "demo", message: "no audio" } }),
    playerVm({ failures: { 1: { kind: "http", message: "HTTP 500" } } }),
    playerVm({ tab: "lyrics", lyrics: { status: "ready", lines: [{ text: "Line", timeMs: 1000 }], activeIndex: 0 } }),
    playerVm({ tab: "lyrics", lyrics: { status: "empty", lines: [], activeIndex: -1 } }),
    playerVm({ queue: [], index: -1, track: null }),
  ];
  for (const state of states) {
    const mini = text(miniPlayer(state));
    const full = text(fullPlayer(state));
    assert.equal(typeof mini, "string");
    assert.ok(full.includes("dhun-full"));
    assert.ok(!full.includes("undefined"));
  }
  assert.match(text(statusLine({ audioStatus: AUDIO_STATUS.BUFFERING })), /Buffering/);
  assert.match(text(statusLine({ error: { kind: "demo" } })), /Demo row/);
  assert.match(text(statusLine({ error: { kind: "autoplay-blocked" } })), /Press play/);
});

test("user data is escaped, and a hostile title stays text", () => {
  const hostile = track("evil", {
    title: '<img src=x onerror="alert(1)"> & "quotes"',
    artist: "<script>alert(2)</script>",
  });
  const markup = text(views.trackList([hostile]));
  assert.ok(!markup.includes("<img src=x"), "a title became markup");
  assert.ok(!markup.includes("<script>"), "an artist became a script tag");
  assert.ok(markup.includes("&lt;img src=x"), "the title is present, escaped");
  assert.ok(markup.includes("&amp;"), "ampersands are escaped");

  const entity = text(views.playlistScreen({ playlist: { id: "x", name: "<b>name</b>" }, tracks: [] }));
  assert.ok(!entity.includes("<b>name</b>"));
  const menu = text(views.trackMenuSheet({ track: hostile, playlists: [{ id: "p", name: "</div><script>x</script>" }] }));
  assert.ok(!menu.includes("<script>"));
});

test("the markup is well formed: every tag a screen opens is closed", () => {
  // A tiny checker rather than a parser: the app emits a small, known tag set,
  // and an unbalanced tag is exactly the class of bug a string view layer can
  // introduce. Void elements are skipped.
  const voids = new Set(["img", "input", "br", "hr", "meta", "link", "source", "circle", "path", "rect", "use", "canvas"]);
  for (const [name, render] of Object.entries(screens)) {
    const stack = [];
    for (const match of render().matchAll(/<\/?([a-zA-Z][a-zA-Z0-9-]*)[^>]*?(\/?)>/g)) {
      const [full, tag, selfClosing] = match;
      const lower = tag.toLowerCase();
      if (voids.has(lower) || selfClosing === "/") continue;
      if (full.startsWith("</")) {
        const open = stack.pop();
        assert.equal(open, lower, `${name}: </${lower}> closed <${open}>`);
      } else {
        stack.push(lower);
      }
    }
    assert.deepEqual(stack, [], `${name}: unclosed tags ${stack.join(", ")}`);
  }
});

test("the honesty notices are in the rendered copy, not only in a doc", () => {
  const demoHome = text(views.homeScreen({
    shelves: [{ title: "Quick picks", entries: [track("a", { demo: true })] }],
    source: { kind: "demo" },
  }));
  assert.match(demoHome, /Demo catalogue/);
  assert.match(demoHome, /no audio/);
  // The hook is what the browser job's `/app/` pass reads: it fails when the
  // demo catalogue is answering and this strip is not on screen, so the strip
  // has to carry the name the moment the screen knows the source is the demo.
  assert.match(demoHome, /data-notice="demo"/);

  const noAudioBrowser = text(views.settingsScreen({ settings, ...shell, capabilities: { ...shell.platform, audioContext: false } }));
  assert.match(noAudioBrowser, /no Web Audio API/);
  assert.match(noAudioBrowser, /nothing\./i, "the reason the control is absent is stated");

  const memoryLibrary = text(views.libraryScreen({ tab: "playlists", libraryKind: "memory" }));
  assert.match(memoryLibrary, /not saving your library/);

  const about = screens.about();
  assert.match(about, /not affiliated with/);
  assert.match(about, /no DHUN server/i, "the app says plainly that nothing is proxied");
  // Lyrics leave the browser too, so the screen that lists what leaves the
  // browser has to name the service that receives them.
  const privacy = text(views.aboutLegalScreen({
    documents: legalIndex("privacy"),
    activeId: "privacy",
    document: legalDocumentById("privacy"),
    platform: shell.platform,
    build: shell.build,
  }));
  assert.match(privacy, /LRCLIB/);
  // The sentence is the one the bundled page actually carries: turning the
  // setting off stops *new* lookups, and requests already sent cannot be
  // recalled. A stricter-sounding claim would be a claim the code cannot keep.
  assert.match(privacy, /Turning it off prevents new lyric lookups/, "the way to stop it is stated");
  assert.match(privacy, /already transmitted\s+requests cannot be recalled/);
});

test("a document body that is data stays text, and a bundled document is markup", () => {
  const asData = text(
    views.aboutLegalScreen({
      documents: [],
      activeId: null,
      document: { id: "x", title: "X", status: "ok", markdown: "<script>alert(1)</script>", sha256: "" },
      platform: shell.platform,
      build: shell.build,
    }),
  );
  assert.ok(!asData.includes("<script>"), "a document body that is a plain string must be escaped");

  const bundled = text(
    views.aboutLegalScreen({
      documents: legalIndex("terms"),
      activeId: "terms",
      document: legalDocumentById("terms"),
      platform: shell.platform,
      build: shell.build,
    }),
  );
  assert.ok(bundled.includes('<ul class="dhun-legal__list">'), "the bundled documents render as lists, not as escaped text");
  assert.match(bundled, /without any warranty/, "the licence text survives the renderer");
  assert.match(bundled, /experimental pre-release/, "the build states what it cannot promise");

  const about = text(
    views.aboutLegalScreen({
      documents: legalIndex("about"),
      activeId: "about",
      document: legalDocumentById("about"),
      platform: shell.platform,
      build: shell.build,
    }),
  );
  assert.match(about, /trademark of Google LLC/, "the About page names whose content this is");
});

test("no bundled document renders a Markdown marker as literal text", () => {
  // The renderer applies one marker kind at a time and joins a paragraph across
  // soft-wrapped lines, but a *list item* that wraps is its own block. A marker
  // that opens on one line and closes on the next therefore leaks into the page
  // as visible `**` or backticks — and it leaked in seven documents before this
  // test existed. The documents are the single source for both the app and the
  // web build, so the repair is in `legal/*.md`, not in a second renderer.
  for (const entry of LEGAL_DOCUMENTS) {
    const markup = text(
      views.aboutLegalScreen({
        documents: legalIndex(entry.id),
        activeId: entry.id,
        document: entry,
        platform: shell.platform,
        build: shell.build,
      }),
    );
    const visible = markup.replace(/<[^>]+>/g, " ").replace(/&(#39|quot|amp|lt|gt);/g, " ");
    for (const marker of ["**", "~~"]) {
      assert.ok(!visible.includes(marker), `${entry.id} renders a literal ${marker}`);
    }
    assert.ok(!visible.includes("`"), `${entry.id} renders a literal backtick`);
    assert.ok(!/undefined|\[object Object\]/.test(visible), `${entry.id} renders a hole`);
  }
});

test("the removed device-only surfaces are gone from the interface", () => {
  // `about` renders the project's own documents, and those pages describe the
  // *Android and desktop* builds — including their offline downloads. That is
  // the honest thing for them to do, so the check covers the interface.
  const allMarkup = Object.entries(screens)
    .filter(([name]) => name !== "about")
    .map(([, render]) => render())
    .join("\n");
  assert.ok(!/downloads/i.test(allMarkup.replace(/download \.json|Download " |download the/gi, "")), "Downloads must not appear as a library tab or action");
  assert.ok(!/offline playback/i.test(allMarkup));
  for (const source of ["views.js", "player-ui.js"]) {
    const body = readFileSync(path.join(jsRoot, source), "utf8");
    assert.ok(!/LIBRARY_TABS = .*DOWNLOADS/.test(body));
    assert.ok(!/"download"|"offline"/.test(body), `${source} still mentions a download/offline surface`);
  }
});

test("the keyboard help is generated from the one shortcut table", () => {
  const markup = text(views.shortcutsSheet({ platform: "Linux" }));
  assert.match(markup, /Space/);
  assert.match(markup, /Play or pause/);
  assert.match(markup, /Ctrl/);
  const mac = text(views.shortcutsSheet({ platform: "MacIntel" }));
  assert.match(mac, /⌘/);
});

test("an empty state always offers the next step rather than a dead end", () => {
  assert.match(screens.libraryEmpty(), /⋮/, "an empty library tells you how to make a playlist");
  assert.match(screens.searchIdle(), /Songs, albums, artists and playlists/);
  assert.match(screens.homeEmpty(), /Search for something/);
});

test("a notice can carry a stable name for the browser job", () => {
  const named = text(views.notice({ tone: "warn", title: "Demo catalogue", hook: "demo" }));
  assert.match(named, /data-notice="demo"/);
  assert.match(named, /role="status"/);
  // Unnamed notices are unchanged: the hook is additive, not a new requirement
  // on every call site.
  const plain = text(views.notice({ title: "Heads up" }));
  assert.ok(!plain.includes("data-notice"), "a notice with no hook carries no name");
});

test("the framing refusal explains itself without the shell", () => {
  const markup = text(views.framedRefusal());
  assert.match(markup, /data-notice="framed"/);
  assert.match(markup, /will not run inside a frame/i);
  // A refusal with no way out is a dead end; the link goes to the app itself,
  // out of the frame rather than inside it.
  assert.match(markup, /target="_top"/);
  // No action ids: nothing in the refusal is wired to a handler, because the
  // app's delegated listener is never attached.
  assert.deepEqual(actionsIn(markup), []);
});

test("the source badge names who answered, for every kind", () => {
  for (const [kind, expected] of [["innertube", "YouTube Music"], ["endpoint", "Your endpoint"], ["demo", "Demo data"]]) {
    const markup = text(views.sourceBadge({ kind, detail: "…" }));
    assert.match(markup, new RegExp(expected));
    assert.match(markup, new RegExp(`data-kind="${kind}"`));
  }
});

test("a failing row keeps its place and says why", () => {
  const markup = text(views.trackList([track("a"), track("b")], {
    failures: undefined,
  }));
  assert.ok(markup.includes("dhun-tracklist"));
  const withFailure = text(views.trackRow(track("a"), { failed: { message: "HTTP 500 from /player" } }));
  assert.match(withFailure, /HTTP 500 from \/player/);
  assert.match(withFailure, /role="status"/);
});

test("every control a screen renders is answered by a handler", () => {
  // The rendered markup is the only place the dynamic action names exist: a
  // queue row's "play-queue-row", a card's "open-entity", a settings chip's
  // "set-theme" are assembled from arguments. Reading the source would miss all
  // three; rendering the screens cannot.
  const handled = handledActions();
  const emitted = new Map();
  const collect = (where, markup) => {
    for (const action of actionsIn(markup)) {
      if (!emitted.has(action)) emitted.set(action, new Set());
      emitted.get(action).add(where);
    }
  };
  for (const [name, render] of Object.entries(screens)) collect(name, render());
  for (const state of [playerVm(), playerVm({ audioStatus: AUDIO_STATUS.BUFFERING, needsGesture: true }), playerVm({ queue: [], track: null, index: -1, tab: "lyrics" })]) {
    collect("mini player", miniPlayer(state));
    collect("full player", fullPlayer(state));
  }

  const dead = [...emitted.entries()]
    .filter(([action]) => !handled.has(action))
    .map(([action, where]) => `${action} (${[...where].sort().join(", ")})`);
  assert.deepEqual(dead, [], `these controls do nothing when pressed: ${dead.join("; ")}`);
});
