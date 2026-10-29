/** Basic failure contract for the in-development IndexedDB adapter. Actual
 * transactions are exercised in Chromium by website/tests/indexeddb-check.mjs. */
import test from "node:test";
import assert from "node:assert/strict";
import { createIndexedDBStorage } from "../src/js/library.js";

test("IndexedDB adapter reports unavailable database without a legacy write", async () => {
  let writes = 0;
  const legacy = { getItem: () => null, setItem: () => { writes += 1; } };
  await assert.rejects(createIndexedDBStorage({ indexedDB: null, legacyStorage: legacy }), error => error.code === "UNAVAILABLE");
  assert.equal(writes, 0);
});

test("IndexedDB adapter fails closed when legacy storage cannot be read", async () => {
  let opened = false;
  const legacy = { getItem() { throw new Error("injected read failure"); } };
  await assert.rejects(createIndexedDBStorage({ indexedDB: { open() { opened = true; } }, legacyStorage: legacy }), error => error.code === "LEGACY_UNREADABLE");
  assert.equal(opened, false);
});
