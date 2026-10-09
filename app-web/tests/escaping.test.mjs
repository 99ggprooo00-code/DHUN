/**
 * Escaped-markup regression test (incident 7, PR #146).
 *
 * The `html` tag in `src/js/dom.js` escapes every interpolated value that is
 * not wrapped in `raw()`. A nested `html` template used as a value — or an
 * array of them joined without `raw(...)` — therefore renders as visible
 * text (literally `&lt;button …&gt;` in the DOM). The first real instance
 * hid the track-row overflow button and produced the phone-viewport li
 * overflow that failed the website browser check.
 *
 * There is no browser in this repository's CI, so this renders every view
 * with representative sample data in Node and asserts the output contains
 * no escaped angle brackets — the only way `&lt;` can appear in these
 * renders is an un-`raw()`'d nested template.
 */

import test from "node:test";
import assert from "node:assert/strict";

import catalog from "../src/js/data/sample-catalog.js";
import * as views from "../src/js/views.js";

const track = catalog.tracks[6];

const cases = {
  "sectionHeader with hint": () => views.sectionHeader("Title", "hint text"),
  "errorState": () => views.errorState({ title: "Title", body: "body text" }),
  "emptyState": () => views.emptyState({ title: "Title", body: "body text", iconName: "Search" }),
  "loadingState": () => views.loadingState(4),
  "trackRow": () => views.trackRow(track, { index: 0 }),
  "trackList": () => views.trackList(catalog.tracks.slice(0, 3)),
  "previewNotice": () => views.previewNotice(),
  "dataSourceNotice": () => views.dataSourceNotice("sample"),
  "homeScreen": () =>
    views.homeScreen({
      feed: {
        ok: true,
        source: "sample",
        sections: [
          { title: "Quick picks", tracks: catalog.tracks.slice(0, 6) },
          { title: "Recommended songs", tracks: catalog.tracks.slice(6, 12) },
          { title: "Listen again", tracks: catalog.tracks.slice(2, 8) },
        ],
      },
      source: "sample",
      currentTrackId: null,
    }),
  "homeScreen loading": () => views.homeScreen({ feed: null, source: "sample", currentTrackId: null }),
  "searchScreen idle with recent": () =>
    views.searchScreen({ query: "", results: null, source: "sample", recentSearches: ["a", "b"], status: "idle" }),
  "searchScreen idle without recent": () =>
    views.searchScreen({ query: "", results: null, source: "sample", recentSearches: [], status: "idle" }),
  "searchScreen with results": () =>
    views.searchScreen({
      query: "q",
      results: { tracks: catalog.tracks.slice(0, 4), albums: catalog.albums, artists: catalog.artists, playlists: catalog.playlists },
      source: "sample",
      recentSearches: [],
      status: "ok",
    }),
  "searchScreen no results": () =>
    views.searchScreen({
      query: "q",
      results: { tracks: [], albums: [], artists: [], playlists: [] },
      source: "sample",
      recentSearches: [],
      status: "ok",
    }),
  "searchScreen error": () =>
    views.searchScreen({ query: "q", results: null, source: "sample", recentSearches: [], status: "error" }),
  "libraryScreen playlists": () =>
    views.libraryScreen({ tab: "PLAYLISTS", playlists: catalog.playlists, history: [], favourites: [], downloadsAvailable: false }),
  "libraryScreen history": () =>
    views.libraryScreen({ tab: "HISTORY", playlists: [], history: [track], favourites: [], downloadsAvailable: false }),
  "libraryScreen history empty": () =>
    views.libraryScreen({ tab: "HISTORY", playlists: [], history: [], favourites: [], downloadsAvailable: false }),
  "libraryScreen downloads": () =>
    views.libraryScreen({ tab: "DOWNLOADS", playlists: [], history: [], favourites: [], downloadsAvailable: false }),
  "artistScreen": () =>
    views.artistScreen({ artist: catalog.artists[0], tracks: catalog.tracks, albums: catalog.albums }),
  "albumScreen": () => views.albumScreen({ album: catalog.albums[0], tracks: catalog.tracks }),
  "playlistScreen": () => views.playlistScreen({ playlist: catalog.playlists[0], tracks: [] }),
  "settingsScreen": () =>
    views.settingsScreen({
      theme: "dark",
      accent: "brand",
      cacheSizeMb: 512,
      resumeOnLaunch: true,
      equalizer: { enabled: true, presetId: "flat", gainsDb: Array(10).fill(0), preamp: 0 },
    }),
  "trackOverflowSheet": () => views.trackOverflowSheet(track, { inFavourites: false }),
  "addToPlaylistSheet": () => views.addToPlaylistSheet(track, catalog.playlists),
};

test("no view renders escaped markup (nested templates must be wrapped in raw())", (t) => {
  for (const [name, render] of Object.entries(cases)) {
    t.test(name, () => {
      const out = render();
      assert.doesNotMatch(
        out,
        /&lt;/,
        `${name} contains an escaped '<' — a nested template is interpolated without raw() and renders as visible text`,
      );
      assert.doesNotMatch(
        out,
        /&quot;class=/,
        `${name} contains an escaped 'class=' attribute — markup is being rendered as text`,
      );
    });
  }
});

test("the fixed structures are real DOM fragments, not text", (t) => {
  t.test("trackRow keeps its overflow button as an element", () => {
    assert.match(views.trackRow(track, { index: 0 }), /<button\s+class="dhun-icon-button"/);
  });
  t.test("searchScreen renders result grids as elements", () => {
    const out = views.searchScreen({
      query: "q",
      results: { tracks: catalog.tracks.slice(0, 2), albums: catalog.albums.slice(0, 1), artists: catalog.artists.slice(0, 1), playlists: catalog.playlists.slice(0, 1) },
      source: "sample",
      recentSearches: [],
      status: "ok",
    });
    assert.match(out, /<ul class="dhun-grid">/);
    assert.match(out, /<div class="dhun-tracklist">/);
  });
  t.test("loadingState renders shimmer rows as elements", () => {
    assert.match(views.loadingState(3), /<div class="dhun-track" aria-hidden="true">/);
  });
});
