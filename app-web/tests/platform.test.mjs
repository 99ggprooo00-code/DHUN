/**
 * Capability detection, and the sentences that go with it.
 *
 * The value of this module is that it answers before something breaks, so the
 * tests are about the awkward cases: Safari's private mode (storage present,
 * writes refused), a browser with no Media Session, and the rule that a missing
 * capability is reported rather than thrown.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  storageWorks,
  indexedDbWorks,
  canProcessAudio,
  networkState,
  detectPlatform,
  unavailableCapabilities,
  isFramed,
} from "../src/js/platform.js";

test("storage is judged by writing, not by existing", () => {
  const working = {
    setItem: () => {},
    removeItem: () => {},
  };
  assert.equal(storageWorks(working), true);

  const privateMode = {
    setItem: () => {
      throw new Error("QuotaExceededError");
    },
    removeItem: () => {},
  };
  assert.equal(storageWorks(privateMode), false, "Safari's private mode exposes the object and refuses the write");
  assert.equal(storageWorks(null), false);
});

test("a missing capability is a false, never an exception", () => {
  assert.equal(indexedDbWorks(undefined), false);
  assert.equal(indexedDbWorks({}), false);
  assert.equal(indexedDbWorks({ open: () => {} }), true);
  // `canProcessAudio` reads the ambient global on purpose: the point is that it
  // answers on a runtime where AudioContext does not exist at all.
  assert.equal(typeof canProcessAudio(), "boolean");
});

test("online is optimistic and offline is trusted", () => {
  assert.deepEqual(networkState({ onLine: false }).online, false);
  assert.equal(networkState({ onLine: true }).online, true);
  assert.equal(networkState({}).online, true, "a browser that does not say is assumed online");
  assert.equal(networkState({ onLine: true, connection: { effectiveType: "3g", saveData: true } }).effectiveType, "3g");
});

test("detection reads the environment it is given, not the one it runs in", () => {
  const platform = detectPlatform({
    window: { matchMedia: () => ({ matches: false }) },
    navigator: { language: "ne", onLine: true, serviceWorker: {} },
    localStorage: { setItem: () => {}, removeItem: () => {} },
    indexedDB: { open: () => {} },
  });
  assert.equal(platform.storage, true);
  assert.equal(platform.indexedDb, true);
  assert.equal(platform.serviceWorker, true);
  assert.equal(platform.mediaSession, false, "no Media Session in this fake browser");
  assert.equal(platform.standalone, false);
  assert.equal(platform.language, "ne");
  assert.deepEqual(platform.network, { online: true, effectiveType: null, saveData: false });
});

test("an installed PWA is detected by display mode, not by a user-agent string", () => {
  const platform = detectPlatform({
    window: { matchMedia: (query) => ({ matches: query.includes("standalone") }) },
    navigator: { onLine: true },
  });
  assert.equal(platform.standalone, true);
});

test("reduce-motion is reported so the player can honour it", () => {
  const platform = detectPlatform({
    window: { matchMedia: (query) => ({ matches: query.includes("reduce") }) },
    navigator: { onLine: true },
  });
  assert.equal(platform.reduceMotion, true);
});

test("every missing capability produces a sentence, and a working browser produces none", () => {
  const capable = {
    storage: true,
    indexedDb: true,
    audioContext: true,
    mediaSession: true,
    serviceWorker: true,
  };
  assert.deepEqual(unavailableCapabilities(capable), []);

  const bare = { storage: false, indexedDb: false, audioContext: false, mediaSession: false, serviceWorker: false };
  const missing = unavailableCapabilities(bare);
  assert.deepEqual(
    missing.map((entry) => entry.id),
    ["storage", "indexeddb", "mediasession", "audio", "serviceworker"],
  );
  for (const entry of missing) {
    assert.ok(entry.title.length > 5 && entry.body.length > 40, `${entry.id} has no real explanation`);
  }
  assert.match(missing.find((entry) => entry.id === "audio").body, /hidden rather than shown/);
  assert.match(missing.find((entry) => entry.id === "storage").body, /private window|blocking/);
});

test("framing is detected by identity, and a browser that will not answer is not a frame", () => {
  const self = { name: "self" };
  const other = { name: "top" };
  assert.equal(isFramed({ self, top: other }), true);
  assert.equal(isFramed({ top: self, self }), false, "a top-level document is not framed");
  // A window that exposes no `top` (Node, a worker, a fake) is not a frame: the
  // answer must degrade to "run the app", never to "refuse for no reason".
  assert.equal(isFramed({ self }), false);
  assert.equal(isFramed({}), false);
  const hostile = {};
  Object.defineProperty(hostile, "top", {
    get() {
      throw new Error("Blocked a frame from accessing a cross-origin frame");
    },
  });
  assert.equal(isFramed(hostile), false, "an unreadable `top` is reported, not thrown");
});
