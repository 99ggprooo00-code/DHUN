/** Application-facing session for the asynchronous IndexedDB adapter. */
import { createIndexedDBStorage, pruneSnapshots } from "./library.js";

export async function openAppStorage(options) {
  const adapter = await createIndexedDBStorage(options);
  let memory;
  try { memory = await adapter.read(); }
  catch (error) { adapter.close(); throw error; }
  let pending = false, exclusive = false, guard = null, lastError = null;
  const deferred = [];
  const busy = () => pending || exclusive;
  function drain() {
    while (!busy() && deferred.length) deferred.shift()();
  }
  function rememberFailure(error) {
    lastError = error;
    guard = error?.code === "CONFLICT" ? "conflict"
      : error?.code === "COMMIT_UNCERTAIN" ? "unconfirmed"
      : error?.code === "LEGACY_DIVERGED" ? "legacy"
      : error?.code === "CORRUPT" ? "corrupt"
      : error?.code === "LEGACY_UNREADABLE" ? "unreadable" : "failed";
  }
  return {
    transactional: true,
    get persistent() { return !guard; },
    get importGuard() { return guard; },
    get lastError() { return lastError; },
    get busy() { return busy(); },
    load() { return memory; },
    defer(fn) { if (busy()) deferred.push(fn); else fn(); },
    beginExclusive() { if (busy()) return false; exclusive = true; return true; },
    endExclusive() { exclusive = false; drain(); },
    async save(library, { requirePersistence = false } = {}) {
      const candidate = pruneSnapshots(library);
      if (!requirePersistence) memory = candidate;
      if (busy() || guard) return false;
      pending = true;
      try {
        const committed = await adapter.save(candidate);
        if (requirePersistence) memory = committed;
        return true;
      } catch (error) {
        rememberFailure(error);
        return false;
      } finally {
        pending = false;
        // A destructive commit must first publish its result to the controller.
        // Running deferred playback/catalogue updates in this microtask would
        // build them from stale in-tab state and could undo that commit.
        setTimeout(drain, 0);
      }
    },
    async importAll(text) {
      if (guard) throw lastError ?? new Error("Saved data needs review; no write was attempted.");
      try {
        const imported = await adapter.importAll(text);
        memory = imported;
        return imported;
      } catch (error) {
        if (error?.code) rememberFailure(error);
        throw error;
      }
    },
    async exportAll() { return adapter.exportAll(); },
    close() { adapter.close(); deferred.length = 0; },
  };
}
