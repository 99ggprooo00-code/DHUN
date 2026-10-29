/**
 * Importing a library from a file.
 *
 * The importer is the one place where a stranger's bytes become the app's
 * state, so every rejection is specific and happens before anything is written.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { handleFileImport } from "../src/js/transfer.js";
import { emptyLibrary, createStorage, toggleLike } from "../src/js/library.js";

function fakeFile(content, { size = null, name = "dhun-library.json" } = {}) {
  return {
    name,
    size: size ?? content.length,
    async text() {
      return content;
    },
  };
}

function fakeStorage(initial = emptyLibrary()) {
  let library = initial;
  return {
    get library() {
      return library;
    },
    importAll(text) {
      library = JSON.parse(text);
      return library;
    },
  };
}

test("a file is rejected before it is read when it is too large", async () => {
  const storage = fakeStorage();
  await assert.rejects(
    () => handleFileImport(fakeFile("{}", { size: 9 * 1024 * 1024 }), storage),
    /larger than 8 MB/,
  );
  assert.deepEqual(storage.library, emptyLibrary(), "a refused file must not touch the library");
});

test("no file at all says so", async () => {
  await assert.rejects(() => handleFileImport(null, fakeStorage()), /no file was chosen/i);
});

test("bytes that are not JSON are named as such", async () => {
  await assert.rejects(
    () => handleFileImport(fakeFile("this is not json"), fakeStorage()),
    /not JSON/i,
  );
});

test("a JSON file that this app did not write is refused", async () => {
  await assert.rejects(
    () => handleFileImport(fakeFile(JSON.stringify({ app: "something else", likes: [] })), fakeStorage()),
    /not exported by this app/i,
  );
});

test("numeric JSON tokens that would change meaning are rejected before replacing the library", async () => {
  const storage = fakeStorage(toggleLike(emptyLibrary(), { id: "preserve-me" }).library);
  const tokens = ["1e400", "9007199254740993", "1e-400", "-0", "0.8000000000000001"];
  for (const token of tokens) {
    const text = `{"app":"DHUN web","version":3,"likes":[],"playlists":[],"history":[],"snapshots":{},"recentSearches":[],"settings":{"extra":${token}}}`;
    const before = structuredClone(storage.library);
    await assert.rejects(
      () => handleFileImport(fakeFile(text), storage),
      /number JavaScript cannot preserve exactly/i,
      `token ${token} must not be silently rounded or normalised`,
    );
    assert.deepEqual(storage.library, before, `token ${token} must not replace the current library`);
  }
});

test("legacy numeric strings are rejected when conversion would round, underflow or erase negative zero", async () => {
  for (const value of ["9007199254740993", "1e-400", "-0"]) {
    const backing = new Map();
    const storage = createStorage({ setItem: (k, v) => backing.set(k, String(v)), getItem: (k) => backing.get(k) ?? null, removeItem: (k) => backing.delete(k) });
    const previous = toggleLike(emptyLibrary(), { id: "preserve-me" }).library;
    assert.equal(storage.save(previous), true);
    const before = new Map(backing);
    const text = JSON.stringify({
      app: "DHUN web", version: 3, likes: [], playlists: [], history: [], snapshots: {},
      recentSearches: [], settings: { volume: value },
    });
    await assert.rejects(() => handleFileImport(fakeFile(text), storage), /would change during import|would be discarded/i);
    assert.deepEqual(backing, before);
    assert.deepEqual(storage.load(), previous);
  }
});

test("a real export imports, and the library comes back", async () => {
  const source = createStorage(
    (() => {
      const map = new Map();
      return { setItem: (k, v) => map.set(k, String(v)), getItem: (k) => (map.has(k) ? map.get(k) : null), removeItem: (k) => map.delete(k) };
    })(),
  );
  const library = toggleLike(emptyLibrary(), { id: "s1", title: "Track", artist: "Naya", duration: 200 }).library;
  const text = source.exportAll(library);

  const storage = fakeStorage();
  const imported = await handleFileImport(fakeFile(text), storage);
  assert.deepEqual(imported.likes, ["s1"]);
  assert.equal(storage.library.likes[0], "s1", "the import reached storage, not only the return value");
});

// ---- format version (WD-3): only the version this build writes is read ----

test("a file written by a different format version is refused, and the version is named", async () => {
  const storage = fakeStorage();
  await assert.rejects(
    () => handleFileImport(fakeFile(JSON.stringify({ app: "DHUN web", version: 4, likes: [] })), storage),
    /format version 4/,
  );
  assert.deepEqual(storage.library, emptyLibrary(), "a refused file must not touch the library");
});

test("a file with no format version is refused", async () => {
  await assert.rejects(
    () => handleFileImport(fakeFile(JSON.stringify({ app: "DHUN web", likes: [] })), fakeStorage()),
    /format version/,
  );
});
