/** Transactional library persistence, not yet wired into the application boot.
 *
 * The legacy key is never written here. IndexedDB cannot atomically coordinate
 * with localStorage or prevent an older build from writing it: divergence blocks
 * further commits and both copies remain available for explicit recovery.
 */
import { emptyLibrary, FORMAT_VERSION, parseImportJSON, STORAGE_KEY, validateImport } from "./library.js";

export const LIBRARY_DATABASE = "dhun.web.library";
const STORE = "library";
const CURRENT = "current";
const PREVIOUS = "previous";
const SCHEMA = 1;

function failure(code, message, cause) {
  const error = new Error(message, cause ? { cause } : undefined);
  error.code = code;
  return error;
}

function validateLibraryText(text) {
  const raw = parseImportJSON(text);
  if (!raw || typeof raw !== "object" || Array.isArray(raw) ||
      Object.hasOwn(raw, "app") || Object.hasOwn(raw, "version") || Object.hasOwn(raw, "exportedAt")) {
    throw failure("CORRUPT", "The saved library has unrecognized metadata. Keep browser data and seek recovery help.");
  }
  return validateImport({ ...raw, app: "DHUN web", version: FORMAT_VERSION });
}

function validateRecord(record) {
  if (!record || typeof record !== "object" || Array.isArray(record) ||
      Object.keys(record).some(key => !["schema", "revision", "library", "legacyBaseline"].includes(key)) ||
      record.schema !== SCHEMA || !Number.isSafeInteger(record.revision) || record.revision < 1 ||
      typeof record.library !== "string" || !(record.legacyBaseline === null || typeof record.legacyBaseline === "string")) {
    throw failure("CORRUPT", "The saved database record is incomplete or unrecognized. Keep browser data and seek recovery help.");
  }
  try { return validateLibraryText(record.library); }
  catch (error) { throw failure("CORRUPT", "The saved database library cannot be validated. Keep browser data and seek recovery help.", error); }
}

function openDatabase(indexedDB, name) {
  return new Promise((resolve, reject) => {
    if (!indexedDB) { reject(failure("UNAVAILABLE", "IndexedDB is unavailable. The saved library was not changed.")); return; }
    let request, settled = false;
    try { request = indexedDB.open(name, 1); }
    catch (error) { reject(failure("UNAVAILABLE", "The library database could not be opened. The saved library was not changed.", error)); return; }
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE);
    };
    request.onblocked = () => {
      settled = true;
      reject(failure("BLOCKED", "The library database upgrade is blocked by another tab. Close other DHUN tabs and reopen this one."));
    };
    request.onerror = () => {
      if (!settled) { settled = true; reject(failure("UNAVAILABLE", "The library database could not be opened. The saved library was not changed.", request.error)); }
    };
    request.onsuccess = () => {
      if (settled) { request.result.close(); return; }
      settled = true;
      resolve(request.result);
    };
  });
}

/**
 * Create a storage session. Methods throw structured errors; only transaction
 * completion acknowledges a write. Callers must block UI while initialize and
 * every operation is pending and must never fall back to a legacy-key write.
 * `beforeCommit` is an optional deterministic abort seam for adapter tests.
 */
export async function createIndexedDBStorage({
  indexedDB,
  legacyStorage,
  databaseName = LIBRARY_DATABASE,
  legacyKey = STORAGE_KEY,
  beforeCommit = null,
} = {}) {
  if (indexedDB === undefined) {
    try { indexedDB = globalThis.indexedDB; }
    catch (error) { throw failure("UNAVAILABLE", "IndexedDB is unavailable. The saved library was not changed.", error); }
  }
  if (legacyStorage === undefined) {
    try { legacyStorage = globalThis.localStorage; }
    catch (error) { throw failure("LEGACY_UNREADABLE", "Legacy storage cannot be read. Keep browser data and seek recovery help.", error); }
  }
  if (!legacyStorage) throw failure("LEGACY_UNREADABLE", "Legacy storage is unavailable. Keep browser data and seek recovery help.");
  const readLegacy = () => {
    try { return legacyStorage.getItem(legacyKey); }
    catch (error) { throw failure("LEGACY_UNREADABLE", "Legacy storage cannot be read. Keep browser data and seek recovery help.", error); }
  };
  const legacyAtOpen = readLegacy();
  const db = await openDatabase(indexedDB, databaseName);
  let revision = null, guarded = null, closed = false;
  db.onversionchange = () => { closed = true; db.close(); guarded = "BLOCKED"; };

  const checkLegacy = baseline => {
    const actual = readLegacy();
    if (actual !== baseline) {
      guarded = "LEGACY_DIVERGED";
      throw failure("LEGACY_DIVERGED", "An older version changed the legacy library. Both copies were kept; stop editing and seek recovery help.");
    }
  };

  function transact(mode, callback) {
    return new Promise((resolve, reject) => {
      if (closed) { reject(failure("UNAVAILABLE", "The library database is closed. Reopen DHUN to review saved data.")); return; }
      let tx, result, reason;
      try {
        tx = db.transaction(STORE, mode);
        const store = tx.objectStore(STORE);
        const abort = error => { reason = error; try { tx.abort(); } catch { /* onabort may already be queued */ } };
        tx.oncomplete = () => reason
          ? reject(failure("COMMIT_UNCERTAIN", "The transaction completed after an error; review saved data before retrying.", reason))
          : resolve(result);
        tx.onabort = () => reject(reason ?? failure("ABORTED", "The library transaction was aborted. No transaction changes were committed.", tx.error));
        tx.onerror = () => { /* IndexedDB aborts; wait for onabort before reporting failure. */ };
        callback({ tx, store, abort, done(value) { result = value; } });
      } catch (error) {
        if (tx) {
          reason = error;
          try { tx.abort(); } catch { reject(reason); }
        } else reject(failure("UNAVAILABLE", "The library transaction could not be started.", error));
      }
    });
  }

  try {
    const initial = await transact("readwrite", ({ store, tx, abort, done }) => {
      const currentRequest = store.get(CURRENT), previousRequest = store.get(PREVIOUS);
      let current, previous, pending = 2;
      const finish = () => {
        if (--pending) return;
        try {
          if (current === undefined) {
            if (previous !== undefined) throw failure("CORRUPT", "The current database record is missing; an older record is retained for recovery.");
            let library;
            try { library = legacyAtOpen === null ? emptyLibrary() : validateLibraryText(legacyAtOpen); }
            catch (error) { throw failure("LEGACY_CORRUPT", "Legacy data cannot be migrated safely. Keep browser data and the original backup; seek recovery help.", error); }
            current = { schema: SCHEMA, revision: 1, library: JSON.stringify(library), legacyBaseline: legacyAtOpen };
            store.add(current, CURRENT);
            beforeCommit?.({ phase: "migration", transaction: tx });
          } else {
            validateRecord(current);
            if (current.revision > 1 && previous === undefined) throw failure("CORRUPT", "The recovery record is missing. Keep browser data and seek recovery help.");
            if (previous !== undefined) {
              validateRecord(previous);
              if (previous.revision >= current.revision || previous.legacyBaseline !== current.legacyBaseline) throw failure("CORRUPT", "Recovery metadata is inconsistent. Keep browser data and seek recovery help.");
            }
          }
          done(current);
        } catch (error) { abort(error); }
      };
      currentRequest.onsuccess = () => { current = currentRequest.result; finish(); };
      previousRequest.onsuccess = () => { previous = previousRequest.result; finish(); };
    });
    revision = initial.revision;
    checkLegacy(initial.legacyBaseline);

    return {
      get revision() { return revision; },
      get guard() { return guarded; },
      close() { if (!closed) db.close(); closed = true; },
      async read({ refresh = false, snapshot = false } = {}) {
        if (guarded) throw failure(guarded, "The library needs review. Keep browser data and seek recovery help.");
        const current = await transact("readonly", ({ store, abort, done }) => {
          const request = store.get(CURRENT);
          request.onsuccess = () => {
            try { validateRecord(request.result); done(request.result); } catch (error) { abort(error); }
          };
        });
        checkLegacy(current.legacyBaseline);
        // Merely exporting a fresh snapshot must not authorize stale in-tab
        // state to overwrite that revision. Refresh is an explicit adoption.
        if (!snapshot && !refresh && current.revision !== revision) {
          throw failure("CONFLICT", "The saved library changed in another tab. Reopen DHUN to review it.");
        }
        if (refresh) revision = current.revision;
        return validateRecord(current);
      },
      async save(library) {
        if (guarded) throw failure(guarded, "The library needs review. Keep browser data and seek recovery help.");
        if (!library || typeof library !== "object" || Array.isArray(library) ||
            Object.hasOwn(library, "app") || Object.hasOwn(library, "version") || Object.hasOwn(library, "exportedAt")) {
          throw failure("INVALID", "The candidate library is not a recognized saved library.");
        }
        // Validate the original object before serialization: JSON.stringify can
        // otherwise drop undefined fields or turn NaN, Infinity and -0 into
        // different values before our lossless parser ever sees them.
        const validated = validateImport({ ...library, app: "DHUN web", version: FORMAT_VERSION });
        const serialized = JSON.stringify(validated);
        validateLibraryText(serialized); // resource budgets and lexical integrity
        const expected = revision;
        const committed = await transact("readwrite", ({ store, tx, abort, done }) => {
          const currentRequest = store.get(CURRENT), previousRequest = store.get(PREVIOUS);
          let current, previous, pending = 2;
          const finish = () => {
            if (--pending) return;
            try {
              validateRecord(current);
              if (current.revision > 1 && previous === undefined) throw failure("CORRUPT", "The recovery record is missing. Keep browser data and seek recovery help.");
              if (previous !== undefined) {
                validateRecord(previous);
                if (previous.revision >= current.revision || previous.legacyBaseline !== current.legacyBaseline) throw failure("CORRUPT", "Recovery metadata is inconsistent. Keep browser data and seek recovery help.");
              }
              checkLegacy(current.legacyBaseline);
              if (current.revision !== expected) throw failure("CONFLICT", "The saved library changed in another tab. Nothing was written; reopen DHUN to review it.");
              if (current.revision === Number.MAX_SAFE_INTEGER) throw failure("CORRUPT", "The saved revision cannot be advanced safely.");
              const next = { ...current, revision: current.revision + 1, library: serialized };
              store.put(current, PREVIOUS);
              store.put(next, CURRENT);
              beforeCommit?.({ phase: "save", transaction: tx });
              done(next);
            } catch (error) { abort(error); }
          };
          currentRequest.onsuccess = () => { current = currentRequest.result; finish(); };
          previousRequest.onsuccess = () => { previous = previousRequest.result; finish(); };
        });
        revision = committed.revision;
        try { checkLegacy(committed.legacyBaseline); }
        catch (error) {
          guarded = error.code ?? "LEGACY_UNREADABLE";
          throw failure("COMMIT_UNCERTAIN", "The database transaction committed, but legacy data changed or could not be checked. Keep both copies and review saved data before retrying.", error);
        }
        return validateRecord(committed);
      },
      async importAll(text) {
        const parsed = parseImportJSON(text);
        return this.save(validateImport(parsed));
      },
      async exportAll() {
        const library = await this.read({ snapshot: true });
        return JSON.stringify({ app: "DHUN web", version: FORMAT_VERSION, exportedAt: new Date().toISOString(), ...library }, null, 2);
      },
    };
  } catch (error) {
    db.close();
    throw error;
  }
}
