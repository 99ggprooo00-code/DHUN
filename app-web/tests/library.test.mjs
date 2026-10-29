/**
 * The library: likes, playlists, history, settings and the storage handle.
 *
 * This is the only module that touches `localStorage`, so it is the only place
 * where "did that save?" can be answered. The tests drive a fake backing store
 * — including one that throws, which is what a private window or a full quota
 * does — because the failure that matters is not a lost playlist but an app
 * that says it saved one.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  ACCENTS,
  DEFAULT_SETTINGS,
  LIBRARY_LIMITS,
  STORAGE_KEY,
  THEMES,
  addToPlaylist,
  createPlaylist,
  createStorage,
  deletePlaylist,
  emptyLibrary,
  historySongs,
  likedSongs,
  normaliseLibrary,
  playlistSongs,
  rememberPlay,
  rememberSearch,
  removeFromPlaylist,
  renamePlaylist,
  snapshot,
  toggleLike,
} from "../src/js/library.js";

const song = (id, extra = {}) => ({
  id,
  title: `Track ${id}`,
  artist: "Naya Koirala",
  album: { id: "al-1", name: "Dharan Nights" },
  duration: 200,
  image: "https://a.saavncdn.com/x.jpg",
  stream: "https://a.saavncdn.com/audio.m4a?expires=1",
  ...extra,
});

function fakeBacking({ failOnWrite = false, forget = false } = {}) {
  const map = new Map();
  return {
    map,
    setItem(key, value) {
      if (failOnWrite) throw new Error("QuotaExceededError");
      if (!forget) map.set(key, String(value));
    },
    getItem(key) {
      return map.has(key) ? map.get(key) : null;
    },
    removeItem(key) {
      map.delete(key);
    },
  };
}

test("a fresh library is empty and carries the build's defaults", () => {
  const library = emptyLibrary();
  assert.deepEqual(library.likes, []);
  assert.deepEqual(library.playlists, []);
  assert.deepEqual(library.history, []);
  assert.equal(library.settings.theme, "dark");
  assert.equal(library.settings.accent, "brand");
  assert.deepEqual([...THEMES], ["dark", "light", "system"]);
  assert.deepEqual([...ACCENTS], ["brand", "azure", "jade", "amber", "rose", "cyan"]);
});

test("the equaliser is not a setting this build stores", () => {
  const library = emptyLibrary();
  assert.equal("equalizerEnabled" in library.settings, false);
  assert.equal("equalizerGains" in library.settings, false);
});

test("a snapshot keeps the row and drops the stream", () => {
  const saved = snapshot(song("s1"));
  assert.equal(saved.id, "s1");
  assert.equal(saved.title, "Track s1");
  assert.equal(saved.stream, "", "a stream URL expires; storing it would play a dead link");
  assert.equal(saved.streamQuality, "");
  assert.equal(saved.image, "https://a.saavncdn.com/x.jpg");
});

test("liking is a toggle, and a song is stored once", () => {
  let library = emptyLibrary();
  const first = toggleLike(library, song("s1"));
  assert.equal(first.liked, true);
  library = first.library;
  assert.deepEqual(library.likes, ["s1"]);
  assert.ok(library.snapshots.s1, "the row is kept so the list can draw without the network");

  const second = toggleLike(library, song("s1"));
  assert.equal(second.liked, false);
  library = second.library;
  assert.deepEqual(library.likes, []);
});

test("the newest like is first", () => {
  let library = emptyLibrary();
  library = toggleLike(library, song("a")).library;
  library = toggleLike(library, song("b")).library;
  assert.deepEqual(library.likes, ["b", "a"]);
});

test("history is newest-first, deduplicated, and bounded", () => {
  let library = emptyLibrary();
  library = rememberPlay(library, song("a"), 1);
  library = rememberPlay(library, song("b"), 2);
  library = rememberPlay(library, song("a"), 3);
  assert.deepEqual(library.history.map((entry) => entry.id), ["a", "b"]);
  for (let index = 0; index < LIBRARY_LIMITS.history + 20; index += 1) {
    library = rememberPlay(library, song(`t${index}`), 100 + index);
  }
  assert.equal(library.history.length, LIBRARY_LIMITS.history);
});

test("recent searches are unique, newest first and bounded", () => {
  let library = emptyLibrary();
  for (const term of ["a", "b", "a", "  c  "]) library = rememberSearch(library, term);
  assert.deepEqual(library.recentSearches, ["c", "a", "b"]);
  assert.deepEqual(rememberSearch(library, "").recentSearches, library.recentSearches, "an empty search is not remembered");
});

test("playlists can be made, renamed, filled and emptied", () => {
  let library = emptyLibrary();
  const created = createPlaylist(library, "Night drive", 1000);
  library = created.library;
  const playlist = created.playlist;
  assert.equal(playlist.name, "Night drive");
  assert.ok(playlist.id);

  library = addToPlaylist(library, playlist.id, song("a"), 1001).library;
  library = addToPlaylist(library, playlist.id, song("b"), 1002).library;
  const duplicate = addToPlaylist(library, playlist.id, song("a"), 1003);
  assert.equal(duplicate.added, false, "a second add is a no-op, not a duplicate row");
  library = duplicate.library;
  assert.equal(playlistSongs(library, library.playlists[0]).length, 2);

  library = removeFromPlaylist(library, playlist.id, "a", 1004);
  assert.deepEqual(playlistSongs(library, library.playlists[0]).map((entry) => entry.id), ["b"]);

  library = renamePlaylist(library, playlist.id, "Late night", 1005);
  assert.equal(library.playlists[0].name, "Late night");

  library = deletePlaylist(library, playlist.id);
  assert.equal(library.playlists.length, 0);
});

test("a playlist id is unique even when two are made in the same millisecond", () => {
  const first = createPlaylist(emptyLibrary(), "A", 5);
  const second = createPlaylist(first.library, "B", 5);
  assert.equal(new Set(second.library.playlists.map((playlist) => playlist.id)).size, 2);
});

test("liked and history lists come back as rows, not ids", () => {
  let library = emptyLibrary();
  library = toggleLike(library, song("a")).library;
  library = rememberPlay(library, song("b"), 10);
  assert.equal(likedSongs(library)[0].title, "Track a");
  assert.equal(historySongs(library)[0].title, "Track b");
});

test("normaliseLibrary trusts nothing it reads", () => {
  const library = normaliseLibrary({
    likes: ["a", "a", 3, null],
    history: [{ id: "a", at: "12" }, "b", {}, null],
    playlists: [
      { id: "p1", name: "  " },
      { name: "no id" },
      { id: "p2", name: "Kept", trackIds: ["s1", "s1"] },
      { id: "p3", name: "Also kept", trackIds: ["s1", "gone"] },
    ],
    snapshots: { s1: song("s1"), bad: { id: "other" } },
    recentSearches: ["x", "", "x"],
    settings: { theme: "neon", accent: "beige", volume: 9, repeat: "sometimes" },
  });
  assert.deepEqual(library.likes, ["a", "3"], "ids are strings and appear once");
  assert.deepEqual(library.history.map((entry) => entry.id), ["a", "b"]);
  assert.equal(library.history[0].at, 12);
  assert.equal(library.playlists.length, 2, "a playlist without an id, or without a name, is unusable and dropped");
  assert.equal(library.playlists.find((playlist) => playlist.id === "p2").name, "Kept");
  assert.deepEqual(
    library.playlists.find((playlist) => playlist.id === "p2").trackIds,
    ["s1"],
    "a duplicate track id is stored once",
  );
  assert.deepEqual(Object.keys(library.snapshots), ["s1"], "a snapshot whose id disagrees with its key is dropped");
  assert.deepEqual(library.recentSearches, ["x"]);
  assert.equal(library.settings.theme, DEFAULT_SETTINGS.theme, "an unknown theme falls back, it is not applied");
  assert.equal(library.settings.accent, DEFAULT_SETTINGS.accent);
  assert.equal(library.settings.volume, 1, "out-of-range values are clamped");
  assert.equal(library.settings.repeat, "off");
});

test("a corrupt blob is an empty library, not a boot failure", () => {
  assert.deepEqual(normaliseLibrary("this is not a library"), emptyLibrary());
  assert.deepEqual(normaliseLibrary(null), emptyLibrary());
});

test("storage round-trips a library and reports that it saved", () => {
  const backing = fakeBacking();
  const storage = createStorage(backing, STORAGE_KEY);
  assert.equal(storage.persistent, true);
  let library = emptyLibrary();
  library = createPlaylist(library, "Saved", 100).library;
  assert.equal(storage.save(library), true);
  assert.ok(backing.map.has(STORAGE_KEY));
  const loaded = createStorage(backing, STORAGE_KEY).load();
  assert.equal(loaded.playlists[0].name, "Saved");
});

test("a browser that forgets is reported, not trusted", () => {
  const storage = createStorage(fakeBacking({ forget: true }), STORAGE_KEY);
  assert.equal(storage.persistent, false, "a write that does not read back is not persistence");
  assert.equal(storage.save(emptyLibrary()), false);
  assert.deepEqual(storage.load(), emptyLibrary());
});

test("a quota failure stops the claim instead of lying", () => {
  const storage = createStorage(fakeBacking({ failOnWrite: true }), STORAGE_KEY);
  assert.equal(storage.persistent, false);
  assert.equal(storage.save(emptyLibrary()), false);
});

test("an absent localStorage is not an exception", () => {
  const storage = createStorage(undefined, STORAGE_KEY);
  assert.equal(storage.persistent, false);
  assert.deepEqual(storage.load(), emptyLibrary());
});

test("export says what wrote the file, and import reads it back", () => {
  const storage = createStorage(fakeBacking(), STORAGE_KEY);
  let library = emptyLibrary();
  library = toggleLike(library, song("a")).library;
  const text = storage.exportAll(library);
  const parsed = JSON.parse(text);
  assert.equal(parsed.app, "DHUN web", "the importer keys on this field");
  assert.ok(parsed.exportedAt);

  const other = createStorage(fakeBacking(), STORAGE_KEY);
  const imported = other.importAll(text);
  assert.deepEqual(imported.likes, ["a"]);
});

// ---- snapshot pruning (WD-1): a row is kept only while a list refers to it ----

function memoryBacking() {
  const map = new Map();
  return {
    setItem: (k, v) => map.set(k, String(v)),
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    removeItem: (k) => map.delete(k),
    raw: (k) => map.get(k),
  };
}

test("a row no list refers to is dropped when the library is saved", () => {
  let library = toggleLike(emptyLibrary(), song("s1")).library;
  library = toggleLike(library, song("s1")).library; // unliked: nothing refers to s1 now
  assert.deepEqual(library.likes, []);
  const backing = memoryBacking();
  createStorage(backing).save(library);
  assert.deepEqual(JSON.parse(backing.raw(STORAGE_KEY)).snapshots, {}, "the stored document must not keep the unliked row");
});

test("a row a playlist or the history still refers to is kept on save", () => {
  let library = createPlaylist(emptyLibrary(), "Mix", 1).library;
  library = addToPlaylist(library, library.playlists[0].id, song("p1"), 2).library;
  library = rememberPlay(library, song("h1"), 3);
  const backing = memoryBacking();
  createStorage(backing).save(library);
  const stored = JSON.parse(backing.raw(STORAGE_KEY));
  assert.deepEqual(Object.keys(stored.snapshots).sort(), ["h1", "p1"]);
});

test("a stored document that carries unreferenced rows is pruned when it is read back", () => {
  const backing = memoryBacking();
  backing.setItem(STORAGE_KEY, JSON.stringify({ ...emptyLibrary(), snapshots: { orphan: song("orphan") } }));
  const loaded = createStorage(backing).load();
  assert.deepEqual(loaded.snapshots, {});
});

// Runtime reliability follow-up: write permission is not read permission, and
// a failed destructive save must not become a successful in-memory commit.
function changingBacking(initial = null) {
  const map = new Map(initial ? [[STORAGE_KEY, JSON.stringify(initial)]] : []);
  const control = { deny: false, forget: false };
  return {
    map, control,
    getItem: (key) => map.get(key) ?? null,
    setItem(key, value) {
      if (control.deny) throw Object.assign(new Error("synthetic quota"), { name: "QuotaExceededError" });
      if (!control.forget) map.set(key, String(value));
    },
    removeItem: (key) => map.delete(key),
  };
}

test("blocked localStorage property access does not crash library startup", () => {
  const previous = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  Object.defineProperty(globalThis, "localStorage", { configurable: true, get() { throw new Error("SecurityError: denied"); } });
  try {
    const storage = createStorage();
    assert.equal(storage.persistent, false);
    assert.deepEqual(storage.load(), emptyLibrary());
    assert.equal(storage.save(emptyLibrary()), false);
  } finally {
    if (previous) Object.defineProperty(globalThis, "localStorage", previous);
    else delete globalThis.localStorage;
  }
});

test("an unsuccessful write probe must not hide an existing readable library", () => {
  const library = toggleLike(emptyLibrary(), song("kept")).library;
  const backing = changingBacking(library); backing.control.deny = true;
  const storage = createStorage(backing);
  assert.equal(storage.persistent, false);
  assert.deepEqual(storage.load().likes, ["kept"]);
  assert.deepEqual(JSON.parse(backing.map.get(STORAGE_KEY)).likes, ["kept"]);
});

test("ordinary failed writes retain useful visit-only changes without claiming persistence", () => {
  const backing = changingBacking(); const storage = createStorage(backing);
  backing.control.deny = true;
  const library = toggleLike(emptyLibrary(), song("visit-only")).library;
  assert.equal(storage.save(library), false);
  assert.deepEqual(storage.load().likes, ["visit-only"]);
  assert.equal(storage.persistent, false);
  assert.equal(backing.map.has(STORAGE_KEY), false);
});

test("saving retries after write access or quota recovers on the same handle", () => {
  const backing = changingBacking(); const storage = createStorage(backing);
  backing.control.deny = true; assert.equal(storage.save(emptyLibrary()), false);
  backing.control.deny = false;
  const library = toggleLike(emptyLibrary(), song("retry")).library;
  assert.equal(storage.save(library), true);
  assert.equal(storage.persistent, true);
  assert.deepEqual(createStorage(backing).load().likes, ["retry"]);
});

test("a failed durable-only save preserves previous memory and the saved library", () => {
  const before = toggleLike(emptyLibrary(), song("kept")).library;
  const backing = changingBacking(before); const storage = createStorage(backing);
  const previous = storage.load(); backing.control.deny = true;
  assert.equal(storage.save(emptyLibrary(), { requirePersistence: true }), false);
  assert.deepEqual(storage.load(), previous);
  assert.deepEqual(JSON.parse(backing.map.get(STORAGE_KEY)).likes, ["kept"]);
});

test("a successful durable erase keeps settings, prunes snapshots and survives a reload", () => {
  const before = toggleLike(emptyLibrary(), song("kept")).library; before.settings.theme = "light";
  const backing = changingBacking(before); const storage = createStorage(backing);
  storage.load();
  const after = { ...emptyLibrary(), settings: before.settings };
  assert.equal(storage.save(after, { requirePersistence: true }), true);
  const loaded = createStorage(backing).load();
  assert.deepEqual(loaded.likes, []); assert.deepEqual(loaded.snapshots, {});
  assert.equal(loaded.settings.theme, "light");
});

test("a backing that silently discards a write cannot acknowledge a durable erase", () => {
  const before = toggleLike(emptyLibrary(), song("kept")).library;
  const backing = changingBacking(before); const storage = createStorage(backing); storage.load();
  backing.control.forget = true;
  assert.equal(storage.save(emptyLibrary(), { requirePersistence: true }), false);
  assert.deepEqual(storage.load().likes, ["kept"]);
  assert.deepEqual(JSON.parse(backing.map.get(STORAGE_KEY)).likes, ["kept"]);
});

test("a simulated failed import keeps the previous library and can be retried in a fresh adapter", () => {
  const before = toggleLike(emptyLibrary(), song("previous")).library;
  const next = toggleLike(emptyLibrary(), song("imported")).library;
  const backing = changingBacking(before); const storage = createStorage(backing); storage.load();
  const exported = storage.exportAll(next); backing.control.deny = true;
  assert.throws(() => storage.importAll(exported), error => error.code === "IMPORT_UNCERTAIN");
  assert.deepEqual(storage.load().likes, ["previous"]);
  assert.deepEqual(JSON.parse(backing.map.get(STORAGE_KEY)).likes, ["previous"]);
  backing.control.deny = false;
  assert.throws(() => storage.importAll(exported), /will not write again/i);
  assert.deepEqual(createStorage(backing).importAll(exported).likes, ["imported"]);
});

test("saved collection limits refuse additions without evicting or mutating anything", () => {
  const library = emptyLibrary();
  library.likes = Array.from({ length: LIBRARY_LIMITS.likes }, (_, i) => `like-${i}`);
  library.playlists = Array.from({ length: LIBRARY_LIMITS.playlists }, (_, i) => ({
    id: `p-${i}`, name: `Playlist ${i}`, trackIds: [], createdAt: i, updatedAt: i,
  }));
  library.playlists[0].trackIds = Array.from({ length: LIBRARY_LIMITS.playlistTracks }, (_, i) => `track-${i}`);
  const before = structuredClone(library);
  assert.equal(toggleLike(library, song("new")).reason, "limit");
  assert.equal(createPlaylist(library, "New").reason, "limit");
  assert.equal(addToPlaylist(library, "p-0", song("new")).reason, "limit");
  assert.equal(addToPlaylist(library, "p-0", song("track-0")).reason, "duplicate");
  assert.equal(addToPlaylist(library, "missing", song("new")).reason, "missing");
  assert.deepEqual(library, before);
  assert.equal(toggleLike(library, song("like-0")).liked, false, "removal still works at capacity");
  const smaller = removeFromPlaylist(library, "p-0", "track-0");
  assert.equal(addToPlaylist(smaller, "p-0", song("new")).added, true);
  assert.ok(createPlaylist(deletePlaylist(library, "p-1"), "New").playlist);
});
