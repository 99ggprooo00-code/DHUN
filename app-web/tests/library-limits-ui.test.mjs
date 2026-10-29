/** Controller tests with DOM/storage doubles; not physical-browser acceptance. */
import test from "node:test";
import assert from "node:assert/strict";
import { LIBRARY_LIMITS } from "../src/js/library.js";
import { bootFixture } from "./helpers/boot-fixture.mjs";

test("library actions report capacity and preserve existing data", (t) => {
  const app = bootFixture(t);
  const library = app.state.library;
  library.likes = Array.from({ length: LIBRARY_LIMITS.likes }, (_, i) => `l${i}`);
  library.playlists = Array.from({ length: LIBRARY_LIMITS.playlists }, (_, i) => ({
    id: `p${i}`, name: `List ${i}`, trackIds: [], createdAt: 0, updatedAt: 0,
  }));
  library.playlists[0].trackIds = Array.from({ length: LIBRARY_LIMITS.playlistTracks }, (_, i) => `t${i}`);
  app.state.search.results = [{ id: "new", title: "New", artist: "Artist" }];
  const before = structuredClone(library);
  app.click("toggle-like", "", { id: "new" });
  assert.match(app.state.toast.message, /Liked songs is full/);
  assert.deepEqual(app.state.library, before);
  app.createPlaylist("New playlist", "new");
  assert.match(app.state.toast.message, /Playlist limit reached/);
  assert.deepEqual(app.state.library, before);
  app.click("add-to-playlist", "new", { id: "p0" });
  assert.match(app.state.toast.message, /playlist is full/i);
  assert.deepEqual(app.state.library, before);
  app.state.search.results = [{ id: "t0", title: "Existing" }];
  app.click("add-to-playlist", "t0", { id: "p0" });
  assert.match(app.state.toast.message, /Already in that playlist/);
  assert.deepEqual(app.state.library, before);
});
