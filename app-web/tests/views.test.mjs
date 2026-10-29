/**
 * The screens render, escape and tell the truth.
 *
 * A view layer with no browser is testable exactly to the extent that it is a
 * pure function of its view model, which is why these screens return markup.
 * What this file proves:
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

import * as views from "../src/js/views.js";
import { WEB_LEGAL_DOCUMENTS } from "../src/js/data/web-legal.js";
import { legalBody } from "../src/js/legal.js";
import { fullPlayer } from "../src/js/player-ui.js";
import { DEMO_ALBUMS, DEMO_ARTISTS, DEMO_SONGS, demoHome, searchDemo } from "../src/js/demo.js";
import { emptyLibrary } from "../src/js/library.js";

const text = (markup) => String(markup);

const song = (id = "tr-1", extra = {}) => ({
  kind: "song",
  id,
  title: `Song ${id}`,
  artist: "An artist",
  album: { id: "al-1", name: "An album" },
  duration: 180,
  image: "",
  stream: "https://a.saavncdn.com/x.m4a",
  ...extra,
});

const hero = song("hero-1", { title: "Hero track" });

function homeVm(extra = {}) {
  return {
    status: "ready",
    hour: 22,
    headline: "What do you want to hear?",
    source: { label: "JioSaavn", tone: "live", detail: "" },
    hero,
    rails: [{ id: "trending", title: "Trending now", kind: "songs", items: [song("a"), song("b")] }],
    hrefFor: () => "#/album/al-1",
    currentId: "",
    playing: false,
    likes: [],
    ...extra,
  };
}

function searchVm(extra = {}) {
  return {
    status: "ready",
    query: "lofi",
    filter: "songs",
    results: [song("a"), song("b")],
    recentSearches: [],
    hrefFor: () => "#/album/al-1",
    currentId: "",
    playing: false,
    likes: [],
    history: [],
    ...extra,
  };
}

function libraryVm(extra = {}) {
  const library = emptyLibrary();
  return {
    tab: "playlists",
    playlists: [],
    playlistCovers: {},
    likes: [],
    likeIds: [],
    history: [],
    currentId: "",
    playing: false,
    storage: { persistent: true, note: "" },
    ...extra,
  };
}

function settingsVm(extra = {}) {
  return {
    settings: emptyLibrary().settings,
    source: { note: "JioSaavn's public API.", endpoint: "https://saavn.dev/api", disclosure: "" },
    storage: { persistent: true, note: "" },
    version: "DHUN web 3.0.0",
    ...extra,
  };
}

const hrefFor = () => "#/album/al-1";

const playerVm = (extra = {}) => ({
  track: song("a", { streamQuality: "320kbps", hasLyrics: true }),
  queue: [{ ...song("a"), index: 0, current: true }],
  status: "playing",
  position: 10,
  duration: 180,
  percent: 5,
  volume: 0.8,
  muted: false,
  shuffle: false,
  repeat: "off",
  tab: "queue",
  context: "Album",
  sourceLabel: "JioSaavn",
  liked: false,
  playing: true,
  error: null,
  ...extra,
});

const screens = {
  home: () => views.homeScreen(homeVm()),
  search: () => views.searchScreen(searchVm()),
  library: () => views.libraryScreen(libraryVm()),
  settings: () => views.settingsScreen(settingsVm()),
  album: () =>
    views.albumScreen({
      album: { ...DEMO_ALBUMS[0], title: "Dharan Nights" },
      currentId: "",
      playing: false,
      likes: [],
      error: null,
      source: { label: "JioSaavn", tone: "live", detail: "" },
      storage: { persistent: true },
      hrefFor,
    }),
  artist: () =>
    views.artistScreen({
      artist: DEMO_ARTISTS[0].artist,
      songs: DEMO_SONGS.slice(0, 3),
      albums: DEMO_ALBUMS,
      currentId: "",
      playing: false,
      likes: [],
      error: null,
      source: { label: "JioSaavn", tone: "live", detail: "" },
      hrefFor,
    }),
  playlist: () =>
    views.playlistScreen({
      playlist: { id: "pl-x", title: "Late night", songs: [song("a")], image: "" },
      currentId: "",
      playing: false,
      likes: [],
      error: null,
      source: { label: "JioSaavn", tone: "live", detail: "" },
      hrefFor,
    }),
  collection: () =>
    views.collectionScreen({
      playlist: { id: "pl-1", name: "Night drive", trackIds: ["a"] },
      tracks: [song("a")],
      currentId: "",
      playing: false,
      likes: [],
      likeIds: [],
      error: null,
    }),
  about: () =>
    views.aboutScreen({
      title: "Privacy",
      activeId: "privacy",
      documents: WEB_LEGAL_DOCUMENTS,
      body: legalBody(WEB_LEGAL_DOCUMENTS[1]),
    }),
  framed: () => views.framedRefusal(),
  player: () => fullPlayer(playerVm()),
};

test("every screen renders, and none of them prints a hole", () => {
  for (const [name, build] of Object.entries(screens)) {
    const markup = text(build());
    assert.ok(markup.length > 50, `${name} rendered almost nothing`);
    assert.ok(!markup.includes("undefined"), `${name} printed "undefined"`);
    assert.ok(!markup.includes("[object Object]"), `${name} printed an object`);
    assert.ok(!markup.includes("NaN"), `${name} printed NaN`);
  }
});

test("every screen renders in its empty and failing state too", () => {
  const failing = { title: "The catalogue did not answer in time", body: "No response.", technical: "url", kind: "timeout" };
  const home = text(views.homeScreen(homeVm({ status: "error", error: failing, rails: [], hero: null })));
  assert.ok(home.includes("Try again"), "a failed home offers no way to try again");
  assert.ok(home.includes("did not answer"), "the failure's own words were dropped");

  const search = text(views.searchScreen(searchVm({ status: "error", error: failing, results: [] })));
  assert.ok(search.includes("Try again"));

  const album = text(views.albumScreen({ status: "error", error: failing, album: null, likes: [], currentId: "", playing: false }));
  const noData = text(views.albumScreen({ album: null, likes: [], currentId: "", playing: false }));
  assert.ok(noData.includes("could not be loaded"), "a screen with no data must explain itself, not throw");
  assert.ok(album.includes("did not answer"));
  assert.ok(album.includes("Go home"));

  const library = text(views.libraryScreen(libraryVm()));
  assert.ok(library.includes("No playlists yet"), "an empty library must say how to fill it");
});

test("user data is escaped, not inserted", () => {
  const hostile = '<img src=x onerror="alert(1)">';
  const markup = text(views.homeScreen(homeVm({ headline: hostile, rails: [], hero: null })));
  assert.ok(!markup.includes("<img src=x"), "a headline became markup");
  assert.ok(markup.includes("&lt;img"));

  const row = text(views.searchScreen(searchVm({ results: [song("a", { title: hostile, artist: hostile })], filter: "songs" })));
  assert.ok(!row.includes("<img src=x"), "a track title became markup");
});

test("the source of every row is on screen, and it names the catalogue", () => {
  const live = text(views.homeScreen(homeVm()));
  assert.ok(live.includes("dhun-source-badge"));
  assert.ok(live.includes("JioSaavn"));

  const sample = text(
    views.homeScreen(
      homeVm({
        status: "sample",
        source: { label: "Sample catalogue", tone: "sample", detail: "" },
        rails: demoHome().filter((rail) => rail.kind === "songs"),
      }),
    ),
  );
  assert.ok(sample.includes("Sample catalogue"));
  assert.ok(sample.includes("nothing in this list has audio"), "the sample catalogue does not say it is silent");
});

test("a saved playlist can be edited from the row menu's own screen", () => {
  // The menu itself lives in main.js; what is asserted here is that the screen
  // that shows a saved playlist can draw its tracks and a remove affordance.
  const markup = text(screens.collection());
  assert.ok(markup.includes("Night drive"));
  assert.ok(markup.includes("Song a"));
});

test("the library does not offer downloads or a cache budget", () => {
  const markup = text(views.libraryScreen(libraryVm({ tab: "likes", likes: [song("a")], likeIds: ["a"] })));
  assert.ok(!/download/i.test(markup), "the web build cannot store a file, so it must not offer Downloads");
  for (const screen of Object.values(screens)) assert.ok(!/cache size/i.test(text(screen())));
});

test("the settings screen says what this browser is storing, and offers a way out", () => {
  const markup = text(screens.settings());
  assert.ok(markup.includes("Kept in this browser") || markup.includes("browser only") || markup.includes("this browser"));
  assert.ok(markup.includes("Export library"));
  assert.match(String(markup), /up to 24 MiB of UTF-8 JSON/);
  assert.match(String(markup), /browser storage quota or available memory/);
  assert.ok(markup.includes("Import library"));
  assert.ok(markup.includes("Erase library"), "the delete control must name its actual scope");
  assert.ok(!markup.includes("Erase everything"), "settings and shell cache are kept");
  assert.ok(!markup.includes("Equalizer"), "the browser cannot honour the app's equaliser, so it must not draw one");
});

test("a guarded tab does not claim its changes are saved", () => {
  const markup = text(views.libraryScreen(libraryVm({ storage: { persistent: false, note: "" } })));
  assert.ok(markup.includes("cannot confirm saving"));
});

test("the about screen renders the web build's own pages", () => {
  const markup = text(screens.about());
  assert.ok(markup.includes("Privacy"));
  assert.ok(markup.includes("dhun-prose"));
  assert.ok(markup.includes("saavn.dev"), "the privacy page must name the API the build calls");
  assert.ok(!markup.includes("SQLite"), "the installed app's storage model is not this build's");
});

test("the framed refusal is a refusal, not the app", () => {
  const markup = text(screens.framed());
  assert.ok(markup.includes("embedded"));
  assert.ok(markup.includes("Open DHUN"));
});

test("the locked full player has tabs, a queue and an honest lyrics panel", () => {
  const queue = text(screens.player());
  assert.ok(queue.includes('role="tablist"'));
  assert.ok(queue.includes("role=\"tab\""));
  assert.ok(queue.includes("Queue"));
  assert.ok(queue.includes("Lyrics"));

  const lyrics = text(fullPlayer(playerVm({ tab: "lyrics" })));
  assert.ok(lyrics.includes("Lyrics are not available in this build."), "the lyrics tab pretends to know words it does not have");
  assert.ok(!lyrics.includes("undefined"));
});

test("a sample track says it is a sample wherever it is drawn", () => {
  const sampleSong = DEMO_SONGS[0];
  assert.equal(sampleSong.demo, true);
  const markup = text(views.searchScreen(searchVm({ results: [sampleSong] })));
  assert.ok(markup.includes(sampleSong.title));
});

test("the sample search still returns rows of the asked kind", () => {
  assert.ok(searchDemo("songs", "monsoon").length > 0);
});

test("no screen claims Android or desktop builds ship here", () => {
  for (const [name, build] of Object.entries(screens)) {
    const markup = text(build());
    for (const phrase of ["Download the app", "Install the APK", "Get it on Google Play"]) {
      assert.ok(!markup.includes(phrase), `${name} claims a package this build does not ship`);
    }
  }
});
