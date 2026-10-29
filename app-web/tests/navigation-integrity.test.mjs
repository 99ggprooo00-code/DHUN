/** Controller and route regressions with controlled Node doubles, not a browser. */
import test from "node:test";
import assert from "node:assert/strict";
import { parseRoute, formatRoute } from "../src/js/router.js";
import { bootFixture } from "./helpers/boot-fixture.mjs";

test("malformed encoded detail links stay usable and never start a lookup", (t) => {
  for (const name of ["album", "artist", "playlist", "collection"]) {
    assert.equal(parseRoute(`#/${name}/%E0%A4%A`).id, null);
  }
  assert.equal(parseRoute("#/about/%ZZ").doc, null);
  const app = bootFixture(t, { hash: "#/album/%" });
  assert.equal(app.state.route.name, "album");
  assert.equal(app.state.entity.status, "error");
  assert.equal(app.calls.length, 0);
  app.navigate("#/search");
  assert.equal(app.state.route.name, "search");
});

test("about document segments round trip encoded delimiters", () => {
  const route = { name: "about", doc: "a/b?%" };
  assert.equal(parseRoute(formatRoute(route)).doc, route.doc);
});

test("navigating away dismisses a track sheet and preserves the originating primary tab", (t) => {
  const app = bootFixture(t, { hash: "#/search" });
  app.state.search.results = [{ id: "s", kind: "song", title: "Track", artist: "A", album: { id: "alb", name: "Album" } }];
  app.click("row-menu", "", { id: "s" });
  assert.equal(app.state.sheet?.kind, "track");
  assert.match(String(app.doc.getElementById("dhun-sheet").innerHTML), /href="#\/album\/alb"/);
  app.navigate("#/album/alb");
  assert.equal(app.state.sheet, null);
  assert.doesNotMatch(String(app.doc.getElementById("dhun-sheet").innerHTML), /role="dialog"/);
  assert.match(String(app.doc.getElementById("dhun-nav-region").innerHTML), /dhun-nav-item is-active" data-action="nav" data-tab="SEARCH"/);
});

test("track menu does not offer a broken album link when its album id is missing", (t) => {
  const app = bootFixture(t, { hash: "#/search" });
  app.state.search.results = [{ id: "s", kind: "song", title: "Track", artist: "A", album: { id: "", name: "Unknown" } }];
  app.click("row-menu", "", { id: "s" });
  assert.doesNotMatch(String(app.doc.getElementById("dhun-sheet").innerHTML), /href="#\/album\//);
});


test("the app back button returns catalogue details to their originating Search query", (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  app.navigate("#/album/alb");
  app.click("back");
  assert.equal(app.win.location.hash, "#/search?q=rain");
  app.navigate(app.win.location.hash);
  assert.equal(app.state.route.name, "search");
  assert.equal(app.state.route.query, "rain");
});

test("the track menu omits the link to the album already on screen", (t) => {
  const app = bootFixture(t, { hash: "#/album/alb" });
  app.state.search.results = [{ id: "s", kind: "song", title: "Track", album: { id: "alb", name: "Album" } }];
  app.click("row-menu", "", { id: "s" });
  assert.doesNotMatch(String(app.doc.getElementById("dhun-sheet").innerHTML), /href="#\/album\//);
});

test("Back from a catalogue detail restores the Library subsection it came from", (t) => {
  for (const tab of ["likes", "history", "playlists"]) {
    const app = bootFixture(t, { hash: `#/library/${tab}` });
    app.navigate("#/album/alb");
    app.click("back");
    assert.equal(app.win.location.hash, `#/library/${tab}`);
  }
});

test("collection details return to playlists while a direct catalogue link defaults Home", (t) => {
  const collection = bootFixture(t, { hash: "#/library/likes" });
  collection.navigate("#/collection/mix");
  collection.navigate("#/artist/a");
  collection.click("back");
  assert.equal(collection.win.location.hash, "#/library/playlists");
  const direct = bootFixture(t, { hash: "#/album/alb" });
  direct.click("back");
  assert.equal(direct.win.location.hash, "#/home");
});


test("browser history restores the detail entry's Search origin and query", (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  app.navigate("#/album/alb");
  const albumEntry = structuredClone(app.win.history.state);
  app.navigate("#/library/likes");
  app.navigate("#/album/alb", { historyState: albumEntry });
  assert.match(String(app.doc.getElementById("dhun-nav-region").innerHTML), /dhun-nav-item is-active" data-action="nav" data-tab="SEARCH"/);
  app.click("back");
  assert.equal(app.win.location.hash, "#/search?q=rain");
});

test("the same catalogue URL opened from different tabs owns independent origins", (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  app.navigate("#/album/alb");
  const searchEntry = structuredClone(app.win.history.state);
  app.navigate("#/library/history");
  app.navigate("#/album/alb");
  const libraryEntry = structuredClone(app.win.history.state);
  app.navigate("#/album/alb", { historyState: searchEntry });
  app.click("back");
  assert.equal(app.win.location.hash, "#/search?q=rain");
  app.navigate("#/album/alb", { historyState: libraryEntry });
  app.click("back");
  assert.equal(app.win.location.hash, "#/library/history");
});

test("restored detail retains its original search even after a later query and reload", (t) => {
  const app = bootFixture(t, { hash: "#/search?q=rain" });
  app.navigate("#/album/alb");
  const entry = structuredClone(app.win.history.state);
  app.navigate("#/search?q=sun");
  app.navigate("#/album/alb", { historyState: entry });
  app.destroy();
  const reloaded = app.mount();
  assert.equal(reloaded.state.tab, "SEARCH");
  app.click("back");
  assert.equal(app.win.location.hash, "#/search?q=rain");
});

test("foreign or mismatched history state never replaces the in-memory fallback", (t) => {
  const app = bootFixture(t, { hash: "#/library/likes" });
  const foreign = { owner: "someone-else", dhunNavigation: { hash: "#/album/other", tab: "SEARCH", libraryTab: "likes", searchQuery: "wrong" } };
  app.navigate("#/album/alb", { historyState: foreign });
  assert.equal(app.win.history.state.owner, "someone-else");
  app.click("back");
  assert.equal(app.win.location.hash, "#/library/likes");
  app.navigate("#/album/alb", { historyState: "foreign-state" });
  assert.equal(app.win.history.state, "foreign-state");
  app.click("back");
  assert.equal(app.win.location.hash, "#/library/likes");
});

test("unavailable history state falls back to the in-memory detail destination", (t) => {
  const app = bootFixture(t, { hash: "#/library/likes" });
  app.win.history.replaceState = () => { throw new Error("history unavailable"); };
  app.navigate("#/album/alb");
  app.click("back");
  assert.equal(app.win.location.hash, "#/library/likes");
});
