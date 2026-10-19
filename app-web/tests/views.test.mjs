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
import { failure, stateError, HOME_MOODS } from "../src/js/source.js";

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
  homeError: () => views.homeScreen({ shelves: [], loading: false, error: refused() }),
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
  searchError: () => views.searchScreen({ query: "x", status: "error", results: stateError(failure("http", new Error("HTTP 500 from /youtubei/v1/search"))) }),
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

/** A refused cross-origin request, as a screen receives it. */
const refused = () => stateError(failure("network", new TypeError("Failed to fetch")));
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

  // Search, too. It used to carry a one-line hint under the query bar instead
  // of the strip (`Searching the demo catalogue — fictional rows, no audio.`),
  // which is the same claim in smaller type and without the way out — and
  // measured 2026-10-16 the search route carried no `data-notice` at all, so the
  // browser job's rule ("the demo answered, so this page must say so") had
  // nothing to find. Rows are rows on every screen.
  const demoSearch = text(views.searchScreen({
    query: "koshi",
    status: "ready",
    results: { tracks: [track("a", { demo: true })], albums: [], artists: [], playlists: [] },
    source: { kind: "demo" },
  }));
  assert.match(demoSearch, /no audio/);
  assert.match(demoSearch, /data-notice="demo"/);
  // And a search still in flight makes no claim about rows it does not have.
  const pendingSearch = text(views.searchScreen({ query: "koshi", status: "loading", source: { kind: "demo" } }));
  assert.ok(!/data-notice="demo"/.test(pendingSearch), "an unfinished search has no rows to label");

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
  const withFailure = text(views.trackRow(track("a"), { failed: { kind: "http", message: "HTTP 500 from /player" } }));
  assert.match(withFailure, /The source answered with an error \(HTTP 500\)\./);
  assert.match(withFailure, /HTTP 500 from \/player/, "the raw string stays beside the sentence");
  assert.match(withFailure, /role="status"/);
});

/*
 * The copy rules, at the surface where a person reads them.
 *
 * Every one of these exists because the app shipped the alternative: a failed
 * screen whose headline was the browser's `TypeError: Failed to fetch`. That
 * string is identical for a flat network, a DNS failure, an ad-blocker and a
 * genuine cross-origin refusal, it names no remedy, and it tells the visitor
 * nothing. The tests below pin the three registers — sentence, one-liner, raw
 * diagnostic — and the *order* they appear in.
 */
test("a failed screen leads with the app's sentence and keeps the raw string as a labelled detail", () => {
  const markup = text(views.homeScreen({ shelves: [], loading: false, error: refused() }));
  assert.match(markup, /The home feed could not be loaded/);
  assert.match(markup, /The browser refused the request before reading a response/);
  assert.match(markup, /Technical detail/);
  assert.match(markup, /Failed to fetch/);
  assert.ok(
    markup.indexOf("refused the request before reading a response") < markup.indexOf("Technical detail"),
    "the sentence comes first; the diagnostic follows it",
  );
  // The browser's words are never the title or the body of the state.
  assert.ok(!/dhun-state__body[^<]*Failed to fetch/.test(markup));
});

test("a failed search says the same kind of thing, with its own sentence", () => {
  const markup = text(views.searchScreen({ query: "koshi", status: "error", results: refused() }));
  assert.match(markup, /That search did not go through/);
  assert.match(markup, /The browser refused the request before reading a response/);
  assert.match(markup, /Technical detail/);
  assert.match(markup, /Failed to fetch/);
});

test("a refusal in the player offers the explanation as well as a retry", () => {
  const markup = text(fullPlayer(playerVm({ error: { kind: "network-or-cors", message: "Failed to fetch" } })));
  assert.match(markup, /DHUN could not reach the source/);
  assert.match(markup, /data-action="retry-track"/);
  assert.match(markup, /data-action="open-about"/, "a refusal has an explanation, and the strip links to it");
  // The raw string travels with the sentence here too, labelled — the strip is
  // where somebody staring at a dead player is most likely to want to quote it.
  assert.match(markup, /Technical detail/);
  assert.ok(markup.indexOf("could not reach the source") < markup.indexOf("Failed to fetch"));
});

test("a failure offers one action per outcome, not two buttons to the same screen", () => {
  // The demo failure used to render "Data source" *and* "Configure a source",
  // both wired to `open-settings-source` — the same destination under two
  // labels. A demo row has nothing to retry, so it gets the one action that
  // could change its outcome.
  const demo = actionsIn(fullPlayer(playerVm({ error: { kind: "demo", message: "no audio" } })));
  assert.deepEqual(demo.filter((action) => action === "open-settings-source").length, 1);
  assert.ok(!demo.includes("retry-track"), "there is nothing to retry on a demo row");

  const refused = actionsIn(fullPlayer(playerVm({ error: { kind: "network-or-cors", message: "Failed to fetch" } })));
  assert.ok(refused.includes("retry-track"));
  assert.ok(refused.includes("open-settings-source"));
  assert.ok(refused.includes("open-about"));
});

test("a list marks the row that failed, and only that row", () => {
  const markup = text(
    views.trackList([track("a"), track("b"), track("c")], {
      failures: { 1: { kind: "network-or-cors", message: "Failed to fetch" } },
    }),
  );
  const failures = markup.match(/class="dhun-row__failure"/g) ?? [];
  assert.equal(failures.length, 1, "one failed row means one failure line");
  assert.match(markup, /DHUN could not reach the source/);
  assert.match(markup, /Failed to fetch/, "the raw string travels with it");
});

test("the lyrics panel never prints the browser's words either", () => {
  // Found in the same sweep: the lyrics fetch rejected with the same
  // `TypeError`, and the panel printed `Lyrics could not be fetched: Failed to
  // fetch` — the sentence the whole change exists to remove.
  const withRaw = text(fullPlayer(playerVm({ tab: "lyrics", lyrics: { status: "error", lines: [], kind: "lyrics", message: "Failed to fetch" } })));
  assert.match(withRaw, /DHUN could not reach the lyrics service/);
  assert.match(withRaw, /Failed to fetch/, "kept as the diagnostic");
  assert.ok(!/could not be fetched: Failed to fetch/.test(withRaw));

  const withoutKind = text(fullPlayer(playerVm({ tab: "lyrics", lyrics: { status: "error", lines: [], message: "Failed to fetch" } })));
  assert.ok(!/could not be fetched: Failed to fetch/.test(withoutKind), "an unknown kind still cannot print it");
});

test("a demo row's lyrics tab explains itself rather than looking broken", () => {
  const markup = text(fullPlayer(playerVm({ tab: "lyrics", lyrics: { status: "demo", lines: [], message: "Demo row — no lyrics exist for it." } })));
  assert.match(markup, /No lyrics for this track|no lyrics/i);
});

test("the demo catalogue's own sentence is not replaced by a generic one", () => {
  const markup = text(fullPlayer(playerVm({ error: { kind: "demo", message: '"A fictional song" is a fictional demo entry, so there is no audio to fetch' } })));
  assert.match(markup, /fictional demo entry/);
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

test("a mood's states speak for the mood, and never drop the provenance strip", () => {
  // Three defects this pins, all measured 2026-10-16 on a 390x844 phone with the
  // source unreachable:
  //   1. the demo strip was appended *after* the shelves, so an empty screen
  //      silently lost the one sentence that says where the answer came from;
  //   2. an empty mood reused the feed's empty copy — it told a visitor who had
  //      just pressed Chill to "search for something, or point DHUN at your own
  //      data source", which is a different screen's advice;
  //   3. the mood's query was nowhere in the copy, so the sentence could not say
  //      what had been asked.
  const moods = [
    { id: "for-you", label: "For you" },
    { id: "chill", label: "Chill" },
  ];
  const source = { kind: "demo" };

  const empty = text(views.homeScreen({
    shelves: [], moods, mood: "chill", moodLabel: "Chill", moodQuery: "chill music",
    greeting: "Good afternoon", source,
  }));
  assert.match(empty, /No Chill songs/);
  assert.match(empty, /chill music/, "the copy names the request that came back empty");
  assert.ok(!/Search for something/.test(empty), "the feed's empty copy is not the mood's");
  assert.match(empty, /data-notice="demo"/, "an empty answer still says who answered");

  const failed = text(views.homeScreen({
    shelves: [], error: stateError(failure("http", new Error("HTTP 502 from /youtubei/v1/search"))),
    moods, mood: "chill", moodLabel: "Chill", moodQuery: "chill music", greeting: "Good afternoon", source,
  }));
  assert.match(failed, /Chill could not be loaded/);
  assert.match(failed, /data-notice="demo"/, "a failed request still says who answered");

  const loading = text(views.homeScreen({
    shelves: [], loading: true, moods, mood: "chill", moodLabel: "Chill", moodQuery: "chill music", source,
  }));
  assert.match(loading, /Loading Chill/);
  assert.match(loading, /data-notice="demo"/);

  // And the feed itself keeps its own words, and the header survives every
  // state: the app draws the greeting above the state, not instead of it.
  const feedEmpty = text(views.homeScreen({ shelves: [], moods, greeting: "Good evening", source: { kind: "innertube" } }));
  assert.match(feedEmpty, /Nothing to show yet/);
  assert.match(feedEmpty, /Good evening/);
  assert.ok(!/data-notice="demo"/.test(feedEmpty), "a live source is not labelled a demo");
});

test("the header carries the app's brand and greeting, and the rail is the app's five moods", () => {
  const body = text(views.homeScreen({
    shelves: [{ title: "Quick picks", entries: [track("a")] }],
    moods: HOME_MOODS.map(({ id, label }) => ({ id, label })),
    mood: "focus",
    greeting: "Good morning",
  }));
  assert.match(body, /dhun-home__brand">DHUN/);
  assert.match(body, /Good morning/);
  // HOME_MOODS is asserted against HomeMood.kt in source.test.mjs; here it is the
  // rail's rendering that matters — five tabs, one selected, each addressable.
  assert.equal(body.match(/data-action="set-mood"/g).length, 5);
  assert.match(body, /data-mood="focus"[^>]*aria-selected="true"/);
  assert.match(body, /role="tablist"/);
});

/**
 * The entity headers are the app's headers.
 *
 * `AlbumScreen.kt` prints the cover, the title, a `artistName • year` line and
 * then a *separate* quieter line of `trackCountText • durationText` — the
 * strings the source sent, not the number of rows this build happened to get.
 * The web used to merge all of it into one line and count its own rows, which is
 * a different promise: "5 tracks" that came from a truncated response is a claim
 * about the internet, while "18 minutes" from the source is a fact about the
 * album. The actions are the app's too, and the artist hero is a 16:10 crop with
 * the name over it rather than a round avatar and a paragraph.
 */
test("the album header keeps the source's own count and run time on their own line", () => {
  const body = text(views.albumScreen({
    album: { id: "MPREb_1", title: "Dharan Nights", artist: "Naya Koirala", year: "2024", trackCountText: "5 songs", durationText: "18 minutes" },
    tracks: [track("a"), track("b")],
  }));
  assert.match(body, /dhun-entity__title">Dharan Nights/);
  assert.match(body, /dhun-entity__meta">Naya Koirala · 2024</);
  assert.match(body, /dhun-entity__submeta">5 songs · 18 minutes</);
  // The two lines are separate elements: the app separates them by type size,
  // and one merged line is what made the web header read as another component.
  assert.ok(!/Naya Koirala · 2024 · 5 songs/.test(body), "the meta lines must not be merged");
});

test("an album whose source sent no counts falls back to its rows, and says so plainly", () => {
  const body = text(views.albumScreen({
    album: { id: "MPREb_2", title: "No metadata", artist: "A", year: null },
    tracks: [track("a"), track("b"), track("c")],
  }));
  assert.match(body, /dhun-entity__submeta">3 tracks</);
  // A source that sent no artist and no year still gets a truthful first line
  // rather than an empty paragraph.
  const bare = text(views.albumScreen({ album: { id: "MPREb_3", title: "Naked" }, tracks: [] }));
  assert.match(bare, /dhun-entity__meta">Album</);
});

test("the entity actions are Play and Shuffle — the app's pair, and no fake ones", () => {
  for (const [name, body] of [
    ["album", views.albumScreen({ album: { id: "x", title: "T", artist: "A" }, tracks: [track("a")] })],
    ["artist", views.artistScreen({ artist: { id: "UC1", name: "A" }, tracks: [track("a")] })],
    ["playlist", views.playlistScreen({ playlist: { id: "pl-1", name: "P" }, tracks: [track("a")] })],
  ]) {
    const rendered = text(body);
    assert.match(rendered, /data-action="play-entity"/, `${name}: no Play`);
    assert.match(rendered, /data-action="shuffle-entity"/, `${name}: no Shuffle`);
    // Queue left the entity header with the app's own header: queueing a whole
    // album is the row menu's job, and the header button promised the same thing
    // twice.
    assert.ok(!/data-action="queue-entity"/.test(rendered), `${name}: a Queue action survived`);
  }
});

test("a local playlist is labelled local, and a remote one is not", () => {
  const mine = text(views.playlistScreen({ playlist: { id: "pl-1", name: "My mix" }, tracks: [track("a")], isOwn: true }));
  assert.match(mine, /dhun-entity__meta">Local playlist · 1 track</);
  const theirs = text(views.playlistScreen({
    playlist: { id: "VL1", name: "Someone's mix", artist: "A curator", trackCountText: "12 songs" },
    tracks: [track("a")],
  }));
  assert.match(theirs, /dhun-entity__meta">A curator · 12 songs</);
  assert.ok(!/Local playlist/.test(theirs), "a remote playlist is not local");
});

test("the artist hero is the app's: a 16:10 veil, and only the listeners the source sent", () => {
  const withListeners = text(views.artistScreen({
    artist: { id: "UC1", name: "Naya Koirala", listeners: "1.2M monthly listeners" },
    tracks: [track("a")],
  }));
  assert.match(withListeners, /dhun-entity--hero/);
  assert.match(withListeners, /dhun-entity__art--hero/);
  assert.match(withListeners, /dhun-entity__veil/);
  assert.match(withListeners, /dhun-entity__title">Naya Koirala/);
  assert.match(withListeners, /dhun-entity__meta">1\.2M monthly listeners</);

  // A listener count is only ever the source's string. When the source sent a
  // description instead, the hero shows no statistic at all — an artist hero
  // with an invented number would be the exact lie the app refuses to tell.
  const described = text(views.artistScreen({
    artist: { id: "UC2", name: "Someone", description: "A long biography that belongs on the page, not in the hero." },
    tracks: [track("a")],
  }));
  assert.ok(!/monthly listeners/.test(described), "no listener figure was invented");
  // The biography still reaches the page, in the app's own place for it: the
  // "About" card at the end (`ArtistScreen.kt` — `GlassCard`, `maxLines = 8`),
  // not over the hero artwork.
  assert.match(described, /About/);
  assert.match(described, /dhun-about__body">A long biography/);
  const hero = described.slice(0, described.indexOf("dhun-entity__actions"));
  assert.ok(!/long biography/.test(hero), "the hero must not print the biography over the artwork");
});

/**
 * A shelf of albums is not a list of songs.
 *
 * The home screen classifies a shelf's entries by `kind`: entries without one
 * are drawn as playable rows. The demo catalogue's albums had no kind, so
 * "Albums for you" rendered four `play-track` rows whose ids are `al-01` … `al-04`
 * — pressing one asked the player for an album as if it were a video, and the
 * album pages were reachable only by typing a URL. The kind is what makes a
 * shelf honest about what its entries are.
 */
test("an album shelf renders album cards with their own action, never playable rows", () => {
  const body = text(views.homeScreen({
    shelves: [
      { title: "Quick picks", entries: [track("t1")] },
      { title: "Albums for you", entries: [{ id: "al-01", title: "Dharan Nights", artist: "Naya Koirala", kind: "album", artwork: null }] },
    ],
  }));
  assert.match(body, /dhun-card"[^>]*data-action="open-entity"[^>]*data-kind="album"[^>]*data-id="al-01"/);
  assert.ok(!/data-action="play-track"[^>]*data-id="al-01"/.test(body), "an album is not a playable row");
  // And the two shelves render as the two different things they are.
  assert.match(body, /dhun-quickrow/);
  assert.match(body, /dhun-shelf--cards/);
});

test("the demo catalogue's own home feed labels its albums", async () => {
  const { createDemoSource } = await import("../src/js/source.js");
  const home = await createDemoSource().home();
  const albumShelf = home.shelves.find((shelfEntry) => /album/i.test(shelfEntry.title));
  assert.ok(albumShelf, "the demo home has no album shelf");
  for (const entry of albumShelf.entries) {
    assert.equal(entry.kind, "album", `${entry.id} would be drawn as a song`);
  }
  for (const shelfEntry of home.shelves.filter((s) => !/album/i.test(s.title))) {
    for (const entry of shelfEntry.entries) {
      assert.ok(!entry.kind || entry.kind === "track", `${entry.id} in “${shelfEntry.title}” is neither a song nor a card`);
    }
  }
});
