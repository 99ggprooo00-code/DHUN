/** Replacement imports: real storage adapter with isolated backing; no browser I/O. */
import test from "node:test";
import assert from "node:assert/strict";
import { addToPlaylist, createPlaylist, createStorage, emptyLibrary, LIBRARY_LIMITS, rememberPlay, rememberSearch, STORAGE_KEY, toggleLike, validateImport } from "../src/js/library.js";
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

test("a simulated quota refusal keeps the prior library and permits retry in a fresh adapter", async () => {
  const f = setup(); const candidate = f.storage.exportAll(emptyLibrary());
  f.control.deny = true;
  await assert.rejects(handleFileImport(file(candidate), f.storage), error => error.code === "IMPORT_UNCERTAIN");
  assert.equal(f.map.get(STORAGE_KEY), f.original);
  assert.deepEqual(f.storage.load(), f.previous);
  f.control.deny = false;
  await assert.rejects(handleFileImport(file(candidate), f.storage), /will not write again/i);
  const reopened = createStorage({ setItem(k, v) { f.map.set(k, String(v)); }, getItem(k) { return f.map.get(k) ?? null; }, removeItem(k) { f.map.delete(k); } });
  assert.deepEqual((await handleFileImport(file(candidate), reopened)).likes, []);
});

test("unreadable storage is rejected before a write", async () => {
  const f = setup(); const candidate = f.storage.exportAll(emptyLibrary());
  f.control.failRead = true;
  await assert.rejects(handleFileImport(file(candidate), f.storage), /could not read.*nothing was written/i);
  assert.deepEqual(f.storage.load(), f.previous);
  assert.equal(f.map.get(STORAGE_KEY), f.original);
});

test("readback failure after a successful write has an uncertain durable outcome", async () => {
  const map = new Map(); let failAfterWrite = false; let wrote = false;
  const backing = { setItem(k, v) { map.set(k, String(v)); if (failAfterWrite && k === STORAGE_KEY) wrote = true; },
    getItem(k) { if (wrote && k === STORAGE_KEY) throw Error("readback failed"); return map.get(k) ?? null; },
    removeItem(k) { map.delete(k); } };
  const storage = createStorage(backing);
  const previous = toggleLike(emptyLibrary(), { id: "old" }).library;
  assert.equal(storage.save(previous), true); const original = map.get(STORAGE_KEY);
  const candidate = storage.exportAll(emptyLibrary()); failAfterWrite = true;
  await assert.rejects(handleFileImport(file(candidate), storage), /could not be confirmed/i);
  assert.deepEqual(storage.load(), previous);
  assert.notEqual(map.get(STORAGE_KEY), original);
  assert.equal(storage.importGuard, "unconfirmed");
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

test("direct validation rejects non-finite and negative-zero values at any depth", () => {
  for (const value of [Infinity, -Infinity, NaN, -0]) {
    const raw = { app: "DHUN web", version: 3, ...emptyLibrary(), settings: { extra: [{ value }] } };
    assert.throws(() => validateImport(raw), /non-finite or negative-zero/i);
  }
});

test("unsafe numeric tokens cannot replace existing data through either import path", async () => {
  const f = setup(); const base = JSON.stringify(f.valid);
  for (const token of ["1e400", "9007199254740993", "1e-400", "-0", "-0.0", "1.0000000000000001", "4e-324", `0.1${"0".repeat(100_000)}1`]) {
    const text = base.replace('"settings":{', `"settings":{"extra":[{"nested":${token}}],`);
    await assert.rejects(handleFileImport(file(text), f.storage), /number.*preserve/i);
    assert.throws(() => f.storage.importAll(text), /number.*preserve/i);
    assert.equal(f.map.get(STORAGE_KEY), f.original);
    assert.deepEqual(f.storage.load(), f.previous);
  }
});

test("equivalent decimal spellings and safe legacy numeric strings round trip", async () => {
  const f = setup(); const text = JSON.stringify(f.valid).replace('"settings":{', '"settings":{"extra":[0.10,1e0,-12.50,5e-324,9007199254740992],');
  const imported = await handleFileImport(file(text), f.storage);
  assert.deepEqual(imported.settings.extra, [0.1, 1, -12.5, 5e-324, 9007199254740992]);
  assert.deepEqual((await handleFileImport(file(f.storage.exportAll(imported)), f.storage)).settings.extra, imported.settings.extra);
  // Numeric-looking text and escaped quotes inside strings are not JSON numbers.
  const quoted = structuredClone(f.valid);
  quoted.settings.extra = ['text "1e400" and \\ -0 9007199254740993'];
  assert.deepEqual((await handleFileImport(file(JSON.stringify(quoted)), f.storage)).settings.extra, quoted.settings.extra);
  for (const value of ["+0.5", ".5", "0.500", "5e-1", "000.50", "1."]) {
    const candidate = structuredClone(f.valid); candidate.settings.volume = value;
    assert.equal((await handleFileImport(file(JSON.stringify(candidate)), f.storage)).settings.volume, Number(value));
  }
});

test("controller reports unsafe numeric input without replacing its in-tab library", async (t) => {
  const app = bootFixture(t); const previous = app.state.library;
  const candidate = JSON.stringify({ app: "DHUN web", version: 3, ...previous });
  app.importFile(file(candidate.replace('"settings":{', '"settings":{"extra":[1e-400],')));
  await settle(); await settle();
  assert.strictEqual(app.state.library, previous);
  assert.match(app.state.toast.message, /Import failed:.*number.*preserve/i);
  assert.doesNotMatch(app.state.toast.message, /Library imported/);
});

test("unconfirmed import blocks subsequent writes and a fresh adapter sees the durable result", async () => {
  const map = new Map(); let breakReadback = false, wrote = false, writes = 0;
  const backing = {
    setItem(k, v) { map.set(k, String(v)); if (k === STORAGE_KEY) { writes++; if (breakReadback) wrote = true; } },
    getItem(k) { if (wrote && k === STORAGE_KEY) throw Error("readback denied"); return map.get(k) ?? null; },
    removeItem(k) { map.delete(k); },
  };
  const a = createStorage(backing), before = toggleLike(emptyLibrary(), { id: "old" }).library;
  assert.equal(a.save(before), true);
  const next = toggleLike(emptyLibrary(), { id: "candidate" }).library;
  const text = a.exportAll(next); breakReadback = true;
  await assert.rejects(handleFileImport(file(text), a), error => error.code === "IMPORT_UNCERTAIN");
  assert.equal(a.importGuard, "unconfirmed");
  assert.deepEqual(a.load().likes, ["old"]);
  assert.deepEqual(JSON.parse(map.get(STORAGE_KEY)).likes, ["candidate"]);
  const afterCommit = writes;
  const visitEdit = toggleLike(a.load(), { id: "visit" }).library;
  assert.equal(a.save(visitEdit), false);
  assert.deepEqual(a.load().likes, ["visit", "old"]);
  assert.equal(a.save(emptyLibrary(), { requirePersistence: true }), false);
  await assert.rejects(handleFileImport(file(text), a), /will not write again/i);
  assert.equal(writes, afterCommit, "the guarded tab never overwrites the saved candidate");
  breakReadback = false; wrote = false;
  assert.deepEqual(createStorage(backing).load().likes, ["candidate"]);
});

test("mismatched readback preserves another tab's later write and blocks stale writes", async () => {
  const map = new Map(); let race = false, writes = 0;
  const other = toggleLike(emptyLibrary(), { id: "other-tab" }).library;
  const backing = { getItem: k => map.get(k) ?? null, removeItem: k => map.delete(k),
    setItem(k, v) { map.set(k, String(v)); if (k === STORAGE_KEY) { writes++; if (race) map.set(k, JSON.stringify(other)); } } };
  const a = createStorage(backing); assert.equal(a.save(toggleLike(emptyLibrary(), { id: "old" }).library), true);
  const text = a.exportAll(emptyLibrary()); race = true;
  await assert.rejects(handleFileImport(file(text), a), error => error.code === "IMPORT_UNCERTAIN");
  assert.deepEqual(JSON.parse(map.get(STORAGE_KEY)).likes, ["other-tab"]);
  const count = writes; assert.equal(a.save(a.load()), false); assert.equal(writes, count);
  assert.deepEqual(createStorage(backing).load().likes, ["other-tab"]);
});

test("a write that mutates and then throws is unconfirmed, not a safe quota failure", async () => {
  const map = new Map(); let afterMutation = false;
  const backing = { getItem: k => map.get(k) ?? null, removeItem: k => map.delete(k),
    setItem(k, v) { map.set(k, String(v)); if (afterMutation && k === STORAGE_KEY) throw Error("unexpected failure"); } };
  const a = createStorage(backing); assert.equal(a.save(toggleLike(emptyLibrary(), { id: "old" }).library), true);
  const text = a.exportAll(emptyLibrary()); afterMutation = true;
  await assert.rejects(handleFileImport(file(text), a), error => error.code === "IMPORT_UNCERTAIN");
  assert.deepEqual(a.load().likes, ["old"]); assert.deepEqual(JSON.parse(map.get(STORAGE_KEY)).likes, []);
  assert.equal(a.importGuard, "unconfirmed");
});

test("a stale tab refuses import and does not overwrite the other tab", async () => {
  const map = new Map(); const backing = { getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) };
  const a = createStorage(backing), b = createStorage(backing); a.load(); b.load();
  const other = toggleLike(emptyLibrary(), { id: "other-tab" }).library;
  assert.equal(b.save(other), true); const saved = map.get(STORAGE_KEY);
  await assert.rejects(handleFileImport(file(a.exportAll(emptyLibrary())), a), /changed since this tab loaded/i);
  assert.equal(a.importGuard, "conflict"); assert.equal(map.get(STORAGE_KEY), saved);
  assert.equal(a.save(emptyLibrary()), false); assert.equal(map.get(STORAGE_KEY), saved);
  assert.deepEqual(createStorage(backing).load().likes, ["other-tab"]);
});

test("an intervening write between the last check and setItem remains a documented race", async () => {
  const map = new Map(); let race = false;
  const other = toggleLike(emptyLibrary(), { id: "other-tab" }).library;
  const backing = { getItem: k => map.get(k) ?? null, removeItem: k => map.delete(k),
    setItem(k, v) { if (race && k === STORAGE_KEY) map.set(k, JSON.stringify(other)); map.set(k, String(v)); } };
  const a = createStorage(backing); assert.equal(a.save(toggleLike(emptyLibrary(), { id: "old" }).library), true);
  const text = a.exportAll(emptyLibrary()); race = true;
  await handleFileImport(file(text), a);
  assert.deepEqual(JSON.parse(map.get(STORAGE_KEY)).likes, [], "single-key storage has no atomic compare-and-swap");
});

test("controller distinguishes unconfirmed import and refuses later saves", async (t) => {
  const app = bootFixture(t, { hash: "#/settings" }); const before = app.state.library;
  const set = app.backing.setItem, get = app.backing.getItem; let wrote = false;
  app.backing.setItem = (k, v) => { set(k, v); if (k === STORAGE_KEY) wrote = true; };
  app.backing.getItem = k => { if (wrote && k === STORAGE_KEY) throw Error("readback failure"); return get(k); };
  const text = JSON.stringify({ app: "DHUN web", version: 3, ...emptyLibrary() });
  app.importFile(file(text)); await settle(); await settle();
  assert.strictEqual(app.state.library, before);
  assert.match(app.state.toast.message, /Import unconfirmed:.*will not write again/i);
  const durable = app.saved.get(STORAGE_KEY);
  app.click("set-theme", "light");
  assert.equal(app.saved.get(STORAGE_KEY), durable);
  assert.equal(app.state.library.settings.theme, "light");
  assert.match(String(app.doc.querySelector("#dhun-view").innerHTML), /saved library needs review.*will not write again/i);
});

test("even a non-Error readback exception activates the write guard", async () => {
  const map = new Map(); let wrote = false;
  const backing = { getItem(k) { if (wrote && k === STORAGE_KEY) throw null; return map.get(k) ?? null; },
    setItem(k, v) { map.set(k, String(v)); if (k === STORAGE_KEY) wrote = true; }, removeItem: k => map.delete(k) };
  const storage = createStorage(backing); storage.load();
  await assert.rejects(handleFileImport(file(storage.exportAll(emptyLibrary())), storage), error => error.code === "IMPORT_UNCERTAIN");
  assert.equal(storage.importGuard, "unconfirmed");
  assert.equal(storage.save(emptyLibrary()), false);
});

test("corrupt existing storage cannot be overwritten by an import", async () => {
  const map = new Map([[STORAGE_KEY, '{not-json']]);
  const backing = { getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) };
  const storage = createStorage(backing); storage.load();
  await assert.rejects(handleFileImport(file(storage.exportAll(emptyLibrary())), storage), /could not be read safely/i);
  assert.equal(storage.save(emptyLibrary()), false);
  assert.equal(map.get(STORAGE_KEY), '{not-json');
});

test("a write that mutates then throws with stale readback remains unconfirmed", async () => {
  const map = new Map(); let stale = null, failing = false;
  const backing = {
    getItem(k) { return failing && k === STORAGE_KEY ? stale : map.get(k) ?? null; },
    setItem(k, value) { if (failing && k === STORAGE_KEY) { map.set(k, String(value)); throw Error("write failed after mutation"); } map.set(k, String(value)); },
    removeItem(k) { map.delete(k); },
  };
  const storage = createStorage(backing), before = toggleLike(emptyLibrary(), { id: "old" }).library;
  assert.equal(storage.save(before), true); stale = map.get(STORAGE_KEY); failing = true;
  await assert.rejects(handleFileImport(file(storage.exportAll(emptyLibrary())), storage), error => error.code === "IMPORT_UNCERTAIN");
  assert.notEqual(map.get(STORAGE_KEY), stale);
  assert.deepEqual(storage.load().likes, ["old"]);
  assert.equal(storage.importGuard, "unconfirmed");
  assert.equal(storage.save(before), false);
});

test("a stale ordinary edit is visit-only and does not overwrite a newer tab", () => {
  const map = new Map(); let writes = 0;
  const backing = { getItem: k => map.get(k) ?? null, setItem(k, v) { map.set(k, String(v)); if (k === STORAGE_KEY) writes++; }, removeItem: k => map.delete(k) };
  const a = createStorage(backing), b = createStorage(backing);
  a.load(); b.load();
  const newer = toggleLike(emptyLibrary(), { id: "other-tab" }).library;
  assert.equal(b.save(newer), true); const previous = map.get(STORAGE_KEY); const count = writes;
  const visitOnly = toggleLike(a.load(), { id: "local-edit" }).library;
  assert.equal(a.save(visitOnly), false);
  assert.equal(a.importGuard, "conflict");
  assert.deepEqual(a.load().likes, ["local-edit"]);
  assert.equal(map.get(STORAGE_KEY), previous); assert.equal(writes, count);
  assert.deepEqual(createStorage(backing).load().likes, ["other-tab"]);
});

test("malformed startup storage is protected from ordinary and destructive saves", () => {
  const map = new Map([[STORAGE_KEY, '{broken library']]);
  const backing = { getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) };
  const storage = createStorage(backing);
  assert.deepEqual(storage.load(), emptyLibrary());
  assert.equal(storage.importGuard, "corrupt");
  assert.equal(storage.persistent, false);
  assert.equal(storage.save(toggleLike(emptyLibrary(), { id: "visit" }).library), false);
  assert.equal(storage.save(emptyLibrary(), { requirePersistence: true }), false);
  assert.equal(map.get(STORAGE_KEY), '{broken library');
  assert.equal(createStorage(backing).importGuard, null, "guard is established on load");
  const reopened = createStorage(backing); reopened.load();
  assert.equal(reopened.importGuard, "corrupt");
  assert.equal(map.get(STORAGE_KEY), '{broken library');
});

test("an initial storage read exception blocks later writes, even after access returns", () => {
  const map = new Map([[STORAGE_KEY, JSON.stringify(toggleLike(emptyLibrary(), { id: "saved" }).library)]]);
  let deny = true;
  const backing = { getItem(k) { if (deny && k === STORAGE_KEY) throw Error("read denied"); return map.get(k) ?? null; }, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) };
  const storage = createStorage(backing); const previous = map.get(STORAGE_KEY);
  assert.deepEqual(storage.load(), emptyLibrary()); assert.equal(storage.importGuard, "unreadable");
  deny = false;
  assert.equal(storage.save(emptyLibrary()), false); assert.equal(map.get(STORAGE_KEY), previous);
  assert.deepEqual(createStorage(backing).load().likes, ["saved"]);
});

test("settings warns that a corrupt startup fallback is incomplete and preserves its bytes", async t => {
  const raw = '{not-json';
  const app = bootFixture(t, { hash: "#/settings", storageSeed: [[STORAGE_KEY, raw]] });
  assert.match(String(app.doc.querySelector("#dhun-view").innerHTML), /library shown may be incomplete.*do not delete browser data/i);
  app.click("set-theme", "light");
  assert.equal(app.state.library.settings.theme, "light");
  assert.equal(app.saved.get(STORAGE_KEY), raw);
  const backup = JSON.stringify({ app: "DHUN web", version: 3, ...emptyLibrary() });
  app.importFile(file(backup)); await settle(); await settle();
  assert.match(app.state.toast.message, /could not be read safely.*do not delete browser data/i);
  assert.equal(app.saved.get(STORAGE_KEY), raw);
});

test("ordinary saves still have a check-to-write race without atomic compare-and-swap", () => {
  const map = new Map(); let injectRace = false;
  const other = toggleLike(emptyLibrary(), { id: "other-tab" }).library;
  const backing = {
    getItem(k) {
      const prior = map.get(k) ?? null;
      if (injectRace && k === STORAGE_KEY) { injectRace = false; map.set(k, JSON.stringify(other)); }
      return prior;
    },
    setItem: (k, value) => map.set(k, String(value)),
    removeItem: k => map.delete(k),
  };
  const tab = createStorage(backing); tab.load();
  const local = toggleLike(emptyLibrary(), { id: "local" }).library;
  injectRace = true;
  assert.equal(tab.save(local), true);
  assert.deepEqual(JSON.parse(map.get(STORAGE_KEY)).likes, ["local"], "the intervening update is not detected atomically");
});

test("parseable damaged or unsupported saved data cannot be normalized and overwritten", () => {
  const base = emptyLibrary();
  const cases = [
    JSON.stringify({ ...base, likes: ["saved", "saved"] }),
    JSON.stringify({ ...base, likes: Array.from({ length: LIBRARY_LIMITS.likes + 1 }, (_, i) => String(i)) }),
    JSON.stringify({ ...base, playlists: [{ id: "p", name: " ", trackIds: [] }] }),
    JSON.stringify({ ...base, settings: { ...base.settings, volume: 2 } }),
    JSON.stringify({ ...base, unsupported: ["important"] }),
    JSON.stringify({ ...base, settings: { ...base.settings, equalizerEnabled: true } }),
    '{"likes":[],"likes":["saved"],"playlists":[],"history":[],"snapshots":{},"recentSearches":[],"settings":{}}',
    JSON.stringify({ ...base, likes: ["NUMBER_TOKEN"] }).replace('"NUMBER_TOKEN"', "9007199254740993"),
    JSON.stringify({ ...base, likes: null }),
  ];
  for (const raw of cases) {
    const map = new Map([[STORAGE_KEY, raw]]);
    const backing = { getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) };
    const firstImport = createStorage(backing);
    assert.throws(() => firstImport.importAll(JSON.stringify({ app: "DHUN web", version: 3, ...base })), /could not be read safely/);
    assert.equal(map.get(STORAGE_KEY), raw, "import before load must not overwrite damaged bytes");
    const storage = createStorage(backing); storage.load();
    assert.equal(storage.importGuard, "corrupt", raw.slice(0, 120));
    assert.equal(storage.save(emptyLibrary()), false);
    assert.equal(storage.save(emptyLibrary(), { requirePersistence: true }), false);
    assert.throws(() => storage.importAll(JSON.stringify({ app: "DHUN web", version: 3, ...base })), /could not be read safely/);
    assert.equal(map.get(STORAGE_KEY), raw);
    const reopened = createStorage(backing); reopened.load();
    assert.equal(reopened.importGuard, "corrupt");
    assert.equal(map.get(STORAGE_KEY), raw);
  }
});

test("recognized lossless legacy values remain readable and writable", () => {
  const raw = JSON.stringify({ ...emptyLibrary(), likes: [17], history: ["old"] });
  const map = new Map([[STORAGE_KEY, raw]]);
  const backing = { getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)), removeItem: k => map.delete(k) };
  const storage = createStorage(backing); const library = storage.load();
  assert.equal(storage.importGuard, null);
  assert.deepEqual(library.likes, ["17"]);
  assert.deepEqual(library.history, [{ id: "old", at: 0 }]);
  assert.equal(storage.save(library), true);
  assert.deepEqual(createStorage(backing).load(), library);
});

test("settings warns on parseable damaged data and leaves its original bytes intact", async t => {
  const raw = JSON.stringify({ ...emptyLibrary(), likes: ["recoverable", "recoverable"] });
  const app = bootFixture(t, { hash: "#/settings", storageSeed: [[STORAGE_KEY, raw]] });
  assert.deepEqual(app.state.library.likes, ["recoverable"]);
  assert.match(String(app.doc.querySelector("#dhun-view").innerHTML), /library shown may be incomplete.*do not delete browser data/i);
  app.click("set-theme", "light");
  assert.equal(app.saved.get(STORAGE_KEY), raw);
  app.click("export-library");
  assert.match(app.state.toast.message, /export unavailable.*may be incomplete/i);
  app.importFile(file(JSON.stringify({ app: "DHUN web", version: 3, ...emptyLibrary() })));
  await settle(); await settle();
  assert.match(app.state.toast.message, /could not be read safely.*do not delete browser data/i);
  assert.equal(app.saved.get(STORAGE_KEY), raw);
});
