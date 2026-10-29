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
