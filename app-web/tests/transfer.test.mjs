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
