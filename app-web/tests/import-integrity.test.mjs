/** Replacement imports: real storage adapter with isolated backing; no browser I/O. */
import test from "node:test";
import assert from "node:assert/strict";
import { addToPlaylist, createPlaylist, createStorage, emptyLibrary, LIBRARY_LIMITS, rememberPlay, rememberSearch, STORAGE_KEY, toggleLike } from "../src/js/library.js";
import { handleFileImport } from "../src/js/transfer.js";
import { bootFixture, settle } from "./helpers/boot-fixture.mjs";

const file = (text) => ({ size: Buffer.byteLength(text), text: async () => text });
function setup() {
  const map = new Map();
  const control = { deny: false, failRead: false };
  const backing = {
    setItem(k, v) { if (control.deny) throw Error("quota"); map.set(k, String(v)); },
    getItem(k) { if (control.failRead && k === STORAGE_KEY) throw Error("read denied"); return map.get(k) ?? null; },
    removeItem(k) { map.delete(k); },
  };
  const storage = createStorage(backing);
  const previous = toggleLike(emptyLibrary(), { id: "old", title: "Previous" }).library;
  assert.equal(storage.save(previous), true);
  const original = map.get(STORAGE_KEY);
  const valid = JSON.parse(storage.exportAll(emptyLibrary()));
  return { storage, map, control, original, valid, previous: storage.load() };
}
async function rejectsWithoutReplacement(ctx, mutate, pattern) {
  const candidate = structuredClone(ctx.valid); mutate(candidate);
  await assert.rejects(handleFileImport(file(JSON.stringify(candidate)), ctx.storage), pattern);
  assert.equal(ctx.map.get(STORAGE_KEY), ctx.original, "no durable write on invalid input");
  assert.deepEqual(ctx.storage.load(), ctx.previous, "in-tab data preserved");
}

test("missing or malformed required structures never replace existing storage", async () => {
  const f = setup();
  for (const field of ["likes", "playlists", "history", "snapshots", "recentSearches", "settings"]) {
    await rejectsWithoutReplacement(f, (v) => { delete v[field]; }, new RegExp(field));
    await rejectsWithoutReplacement(f, (v) => { v[field] = null; }, new RegExp(field));
  }
  await rejectsWithoutReplacement(f, (v) => { v.extraData = ["valuable"]; }, /unsupported/);
  await rejectsWithoutReplacement(f, (v) => { v.playlists = [{ id: "p", name: "x" }]; }, /trackIds/);
  await rejectsWithoutReplacement(f, (v) => { v.snapshots = { a: { id: "b" } }; }, /snapshot/);
});

test("over-limit collections and fields reject before replacement", async () => {
  const f = setup();
  for (const field of ["likes", "history", "playlists", "recentSearches"]) {
    await rejectsWithoutReplacement(f, (v) => { v[field] = Array(LIBRARY_LIMITS[field] + 1).fill("x"); }, /limit/);
  }
  await rejectsWithoutReplacement(f, (v) => { v.playlists = [{ id: "p", name: "x", trackIds: Array(1001).fill("x") }]; }, /limit/);
  for (const [field, value] of [["name", "n".repeat(121)], ["description", "d".repeat(401)]]) {
    await rejectsWithoutReplacement(f, (v) => { v.playlists = [{ id: "p", name: "ok", trackIds: [], [field]: value }]; }, /would change/);
  }
  await rejectsWithoutReplacement(f, (v) => { v.settings.sourceId = "x".repeat(41); }, /would change/);
});

test("duplicates, unsupported values and silently discarded snapshot data are rejected", async () => {
  const f = setup();
  await rejectsWithoutReplacement(f, (v) => { v.likes = [3, "3"]; }, /duplicate/);
  await rejectsWithoutReplacement(f, (v) => { v.recentSearches = ["term", " term "]; }, /lose entries/);
  await rejectsWithoutReplacement(f, (v) => { v.playlists = [{ id: "p", name: "p", trackIds: ["a", "a"] }]; }, /duplicate/);
  await rejectsWithoutReplacement(f, (v) => { v.playlists = [{ id: "p", name: "p", trackIds: [] }, { id: "p", name: "other", trackIds: [] }]; }, /duplicate/);
  await rejectsWithoutReplacement(f, (v) => { v.settings.volume = 2; }, /would change/);
  await rejectsWithoutReplacement(f, (v) => { v.likes = ["a"]; v.snapshots = { a: { id: "a", title: "A", stream: "https://example.test/audio" } }; }, /would change/);
  await rejectsWithoutReplacement(f, (v) => { v.snapshots = { orphan: { id: "orphan", title: "A" } }; }, /discarded/);
  await rejectsWithoutReplacement(f, (v) => { v.likes = ["a"]; v.snapshots = { a: { id: "a", artists: [null] } }; }, /nested structure/);
});

test("valid empty and populated app exports replace and survive a fresh load", async () => {
  const f = setup();
  const track = { id: "new", title: "New song", artist: "Artist", duration: 123 };
  let populated = toggleLike(emptyLibrary(), track).library;
  populated = rememberPlay(populated, track, 1234);
  populated = rememberSearch(populated, "search term");
  const created = createPlaylist(populated, "Saved mix", 1235);
  populated = addToPlaylist(created.library, created.playlist.id, track, 1236).library;
  populated.settings.theme = "light";
  const exported = f.storage.exportAll(populated);
  const result = await handleFileImport(file(exported), f.storage);
  assert.deepEqual(result, { ...populated, settings: { ...populated.settings, sourceId: "" } });
  const reopened = createStorage({ setItem(k, v) { f.map.set(k, String(v)); }, getItem(k) { return f.map.get(k) ?? null; }, removeItem(k) { f.map.delete(k); } });
  assert.deepEqual(reopened.load(), result);
  const empty = f.storage.exportAll(emptyLibrary());
  assert.deepEqual((await handleFileImport(file(empty), f.storage)).likes, []);
});

test("safe legacy normalization and exact list boundaries are accepted", async () => {
  const f = setup();
  f.valid.likes = Array.from({ length: LIBRARY_LIMITS.likes }, (_, i) => i + 1);
  f.valid.history = ["legacy"];
  f.valid.playlists = [{ id: 42, name: " Mix ", trackIds: [1, 2], createdAt: "12" }];
  const imported = await handleFileImport(file(JSON.stringify(f.valid)), f.storage);
  assert.equal(imported.likes.length, LIBRARY_LIMITS.likes);
  assert.equal(imported.likes[0], "1");
  assert.deepEqual(imported.history, [{ id: "legacy", at: 0 }]);
  assert.deepEqual(imported.playlists[0].trackIds, ["1", "2"]);
  assert.equal(imported.playlists[0].name, "Mix");
  assert.equal(imported.playlists[0].createdAt, 12);
});

test("a failed write keeps the prior library and permits retry", async () => {
  const f = setup(); const candidate = f.storage.exportAll(emptyLibrary());
  f.control.deny = true;
  await assert.rejects(handleFileImport(file(candidate), f.storage), /could not confirm/i);
  assert.equal(f.map.get(STORAGE_KEY), f.original);
  assert.deepEqual(f.storage.load(), f.previous);
  f.control.deny = false;
  assert.deepEqual((await handleFileImport(file(candidate), f.storage)).likes, []);
});

test("readback failure after a write retains in-tab data but durable outcome is uncertain", async () => {
  const f = setup(); const candidate = f.storage.exportAll(emptyLibrary());
  f.control.failRead = true;
  await assert.rejects(handleFileImport(file(candidate), f.storage), /could not confirm/i);
  assert.deepEqual(f.storage.load(), f.previous);
  assert.notEqual(f.map.get(STORAGE_KEY), f.original, "the write may already have succeeded before readback fails");
});

test("controller reports invalid import without a success message or in-tab replacement", async (t) => {
  const app = bootFixture(t);
  const previous = app.state.library;
  app.importFile(file(JSON.stringify({ app: "DHUN web", version: 3 })));
  await settle(); await settle();
  assert.strictEqual(app.state.library, previous);
  assert.match(app.state.toast.message, /Import failed:.*likes/);
  assert.doesNotMatch(app.state.toast.message, /Library imported/);
});


test("duplicate JSON keys are rejected even when escaped, without writes", async () => {
  const f = setup();
  const text = JSON.stringify(f.valid).replace('"likes":[]', '"likes":[],"li\\u006bes":["lost"]');
  await assert.rejects(handleFileImport(file(text), f.storage), /duplicate JSON object key/);
  assert.throws(() => f.storage.importAll(text), /duplicate JSON object key/);
  assert.equal(f.map.get(STORAGE_KEY), f.original);
  assert.deepEqual(f.storage.load(), f.previous);
  // Colons and braces inside string values are not interpreted as keys.
  f.valid.settings.extra = 'text: { "likes": [] }';
  await handleFileImport(file(JSON.stringify(f.valid)), f.storage);
});

test("exact playlist, track, history and search limits are accepted", async () => {
  const f = setup();
  f.valid.playlists = Array.from({ length: LIBRARY_LIMITS.playlists }, (_, i) => ({ id: `p${i}`, name: `P${i}`, trackIds: [] }));
  f.valid.playlists[0].trackIds = Array.from({ length: LIBRARY_LIMITS.playlistTracks }, (_, i) => `t${i}`);
  f.valid.history = Array.from({ length: LIBRARY_LIMITS.history }, (_, i) => ({ id: `h${i}`, at: i }));
  f.valid.recentSearches = Array.from({ length: LIBRARY_LIMITS.recentSearches }, (_, i) => `q${i}`);
  const imported = await handleFileImport(file(JSON.stringify(f.valid)), f.storage);
  assert.equal(imported.playlists.length, LIBRARY_LIMITS.playlists);
  assert.equal(imported.playlists[0].trackIds.length, LIBRARY_LIMITS.playlistTracks);
  assert.equal(imported.history.length, LIBRARY_LIMITS.history);
  assert.equal(imported.recentSearches.length, LIBRARY_LIMITS.recentSearches);
});
