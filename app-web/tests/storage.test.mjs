/**
 * Persistence, including the ways it fails.
 *
 * A web app never gets to assume storage works: Safari's private mode exposes
 * `localStorage` and throws on write, a quota can fill between two writes, and a
 * user can hand-edit a key into invalid JSON. Each of those is a test here,
 * because each of them is a way a shipped player loses somebody's playlists.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  SETTINGS_KEY,
  SETTINGS_KEYS,
  defaultSettings,
  normaliseSettings,
  coerceSetting,
  createSettingsStore,
  emptyLibrary,
  normaliseLibrary,
  exportLibrary,
  importLibrary,
  EXPORT_FORMAT,
  EXPORT_VERSION,
  normaliseSession,
  readSession,
  writeSession,
  clearSession,
  SESSION_KEY,
} from "../src/js/storage.js";

/** A storage object with the real API, backed by a Map. */
function memoryStorage(initial = {}) {
  const map = new Map(Object.entries(initial));
  return {
    getItem: (key) => (map.has(key) ? map.get(key) : null),
    setItem: (key, value) => map.set(key, String(value)),
    removeItem: (key) => map.delete(key),
    get size() {
      return map.size;
    },
    raw: map,
  };
}

/** Storage that reports present and throws on write — Safari's private mode. */
function hostileStorage() {
  return {
    getItem: () => null,
    setItem: () => {
      throw new DOMExceptionLike("QuotaExceededError");
    },
    removeItem: () => {},
  };
}

class DOMExceptionLike extends Error {
  constructor(name) {
    super(name);
    this.name = name;
  }
}

test("the defaults cover every key, so nothing is ever undefined", () => {
  const defaults = defaultSettings();
  assert.deepEqual(Object.keys(defaults).sort(), [...SETTINGS_KEYS].sort());
  for (const [key, value] of Object.entries(defaults)) {
    assert.ok(value !== undefined, `${key} has no default`);
  }
  assert.equal(defaults.theme, "dark", "dark is the app's default");
  assert.equal(defaults.equalizerGains.length, 10);
});

test("a value that is not in the enum falls back instead of leaking through", () => {
  assert.equal(coerceSetting("theme", "neon"), "dark");
  assert.equal(coerceSetting("theme", "light"), "light");
  assert.equal(coerceSetting("accent", "azure"), "azure");
  assert.equal(coerceSetting("volume", 12), 1, "out of range clamps to the maximum");
  assert.equal(coerceSetting("volume", -3), 0);
  assert.equal(coerceSetting("volume", "loud"), 1);
  assert.equal(coerceSetting("resumeOnLaunch", "yes"), true, "non-boolean falls back to the default");
  assert.equal(coerceSetting("endpointUrl", "javascript:alert(1)"), "", "only http(s) endpoints are accepted");
  assert.equal(coerceSetting("endpointUrl", "https://example.org/dhun"), "https://example.org/dhun");
});

test("equaliser gains are always ten numbers inside the app's range", () => {
  assert.deepEqual(coerceSetting("equalizerGains", [1, 2]), defaultSettings().equalizerGains);
  assert.deepEqual(coerceSetting("equalizerGains", Array(10).fill(99)), Array(10).fill(20));
  const mixed = coerceSetting("equalizerGains", [0, "x", 2, 3, 4, 5, 6, 7, 8, 9]);
  assert.equal(mixed.length, 10);
  assert.equal(mixed[1], 0, "a junk value becomes a flat band rather than NaN");
});

test("hand-edited JSON in localStorage cannot take the app down", () => {
  assert.deepEqual(normaliseSettings("{not json"), defaultSettings());
  assert.deepEqual(normaliseSettings(null), defaultSettings());
  assert.deepEqual(normaliseSettings([1, 2, 3]).theme, "dark");
  const partial = normaliseSettings({ theme: "light", accent: 42 });
  assert.equal(partial.theme, "light");
  assert.equal(partial.accent, "brand");
  assert.equal(partial.volume, 1, "missing keys keep their defaults");
});

test("settings round-trip through storage and unknown keys are ignored", () => {
  const storage = memoryStorage();
  const store = createSettingsStore(storage);
  assert.equal(store.available, true);
  store.set({ theme: "light", nonsense: true, volume: 0.5 });
  assert.equal(store.get("theme"), "light");
  assert.equal(store.get("volume"), 0.5);
  assert.equal(store.get("nonsense"), undefined);

  const reopened = createSettingsStore(storage);
  assert.equal(reopened.get("theme"), "light", "a new page load reads what the last one wrote");
  assert.ok(storage.raw.has(SETTINGS_KEY));
});

test("a browser that refuses to store still runs, and says it cannot store", () => {
  const store = createSettingsStore(hostileStorage());
  assert.equal(store.available, false);
  assert.deepEqual(store.set({ theme: "light" }), null, "the write reports failure instead of throwing");
  assert.equal(store.get("theme"), "light", "the value is still applied for this session");
});

test("a library with junk in it is repaired, not trusted", () => {
  const library = normaliseLibrary({
    likes: ["tr-1", { id: "tr-2" }, { id: "tr-2" }, { id: "" }, 42],
    playlists: [
      { id: "pl-1", name: "Road trip", trackIds: ["a", "a", "b"] },
      { id: "pl-2", name: "" },
      { name: "no id" },
      "not a playlist",
    ],
    history: [{ id: "tr-1", title: "One", at: 1 }, { title: "no id" }],
    plays: { "tr-1": 4, "tr-2": -1, bad: "x" },
  });
  assert.deepEqual(library.likes.map((entry) => entry.id), ["tr-1", "tr-2"], "duplicates and junk drop out");
  assert.equal(library.playlists.length, 1);
  assert.deepEqual(library.playlists[0].trackIds, ["a", "b"]);
  assert.equal(library.history.length, 1);
  assert.deepEqual(library.plays, { "tr-1": 4 });
});

test("an empty library is a complete document, not an absent one", () => {
  const empty = emptyLibrary();
  assert.deepEqual(empty.likes, []);
  assert.deepEqual(empty.playlists, []);
  assert.deepEqual(empty.history, []);
  assert.deepEqual(empty.plays, {});
  assert.deepEqual(empty.tracks, {}, "the local track table exists from the start");
});

test("a local playlist keeps the rows it points at, and junk rows are dropped", () => {
  // The defect this pins: a playlist made in the browser opened as "That
  // playlist could not be loaded", because a playlist's rows were looked up in
  // the network and a playlist made here is not there. The rows it points at are
  // remembered locally instead, keyed by id, exactly like the app's own metadata
  // table.
  const library = normaliseLibrary({
    playlists: [{ id: "pl-1", name: "Rain tape", trackIds: ["tr-1", "tr-2"] }],
    tracks: {
      "tr-1": { id: "tr-1", title: "Seven Bridges", artist: "A", artwork: "a.jpg", durationMs: 1000 },
      "tr-2": { id: "tr-2", title: "  ", artist: "B" },
      "tr-3": "not a row",
      "": { id: "", title: "no id" },
      // Parsed from a file the visitor chose: an id that is an object key must
      // not become a prototype. An object *literal* would set the prototype, so
      // this is exactly what `JSON.parse` gives `normaliseLibrary`.
      ...JSON.parse('{"__proto__": {"title": "polluted"}}'),
    },
    plays: JSON.parse('{"__proto__": 9, "constructor": 4}'),
  });
  assert.deepEqual(Object.keys(library.tracks), ["tr-1"], "a row with no title is not a row");
  assert.equal(library.tracks.polluted, undefined, "an id of __proto__ polluted the track table");
  assert.deepEqual(Object.keys(library.plays), [], "or the play counts");
  assert.equal(library.tracks["tr-1"].title, "Seven Bridges");

  // And it travels with an export, so a restored playlist still opens.
  const document = exportLibrary(library);
  assert.equal(document.library.tracks["tr-1"].title, "Seven Bridges");
  const imported = importLibrary(JSON.stringify(document));
  assert.equal(imported.ok, true);
  assert.equal(imported.library.tracks["tr-1"].title, "Seven Bridges");
});

test("the export is versioned, self-describing and carries the library", () => {
  const library = normaliseLibrary({ likes: ["tr-1"], playlists: [{ id: "pl-1", name: "Mix" }] });
  const document = exportLibrary(library, {
    settings: defaultSettings(),
    now: Date.parse("2026-10-15T09:00:00Z"),
  });
  assert.equal(document.format, EXPORT_FORMAT);
  assert.equal(document.version, EXPORT_VERSION);
  assert.equal(document.exportedAt, "2026-10-15T09:00:00.000Z");
  assert.equal(document.library.likes.length, 1);
  assert.equal(document.settings.theme, "dark");
  // The two things that must never travel: a personal endpoint address and the
  // visitor's own search history.
  assert.equal("endpointUrl" in document.settings, false);
  assert.equal("recentSearches" in document.settings, false);
});

test("import accepts its own export and explains every rejection", () => {
  const original = normaliseLibrary({ likes: ["tr-1"], playlists: [{ id: "pl-1", name: "Mix", trackIds: ["tr-1"] }] });
  const text = JSON.stringify(exportLibrary(original));

  const accepted = importLibrary(text);
  assert.equal(accepted.ok, true);
  assert.deepEqual(accepted.counts, { likes: 1, playlists: 1, history: 0 });
  assert.equal(accepted.library.playlists[0].name, "Mix");

  assert.equal(importLibrary("{oops").reason, "not-json");
  assert.equal(importLibrary(JSON.stringify({ hello: "world" })).reason, "not-dhun");
  const future = importLibrary(JSON.stringify({ format: EXPORT_FORMAT, version: EXPORT_VERSION + 1 }));
  assert.equal(future.reason, "newer-version");
  assert.match(future.message, /newer DHUN/);
});

test("a corrupted export still imports what it can", () => {
  const result = importLibrary(
    JSON.stringify({
      format: EXPORT_FORMAT,
      version: EXPORT_VERSION,
      library: { playlists: [{ id: "pl-1", name: "Survivor", trackIds: ["x"] }, null], likes: "not a list" },
    }),
  );
  assert.equal(result.ok, true);
  assert.equal(result.library.playlists.length, 1);
  assert.deepEqual(result.library.likes, []);
});

test("the session restores a queue, and refuses a session that is not one", () => {
  assert.equal(normaliseSession(null), null);
  assert.equal(normaliseSession({ queue: [] }), null);
  assert.equal(normaliseSession({ queue: [{ id: 5 }] }), null, "a row with no id cannot be restored");

  const session = {
    queue: [{ id: "tr-1", videoId: "abc", title: "One", artist: "A", durationMs: 1000 }],
    index: 0,
    positionMs: 4000,
  };
  assert.equal(normaliseSession(session).positionMs, 4000);
  const storage = memoryStorage();
  assert.equal(writeSession(session, storage), true);
  assert.ok(storage.raw.has(SESSION_KEY));
  assert.equal(readSession(storage).queue[0].title, "One");
  assert.equal(clearSession(storage), true);
  assert.equal(readSession(storage), null);
});

test("a session with an index past the end is clamped rather than dropped", () => {
  const session = normaliseSession({
    queue: [{ id: "a" }, { id: "b" }],
    index: 9,
    positionMs: -5,
  });
  assert.equal(session.index, 0);
  assert.equal(session.positionMs, 0);
});
