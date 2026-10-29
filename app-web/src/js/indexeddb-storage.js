/** Transactional library persistence used by application startup and actions.
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

function createEpoch() {
  try {
    if (typeof globalThis.crypto?.randomUUID === "function") return globalThis.crypto.randomUUID();
    if (globalThis.crypto?.getRandomValues) return Array.from(globalThis.crypto.getRandomValues(new Uint8Array(16)), x => x.toString(16).padStart(2, "0")).join("");
    throw new Error("secure random unavailable");
  } catch (error) { throw failure("UNAVAILABLE", "A safe library generation cannot be created. Keep all copies and seek recovery help.", error); }
}

// Only representations whose JSON encoding preserves the complete structured
// clone can be compared/recovered this way. Unsupported values stay guarded.
function recoveryRecordJSON(value) {
  const seen = new Set();
  function safe(item) {
    if (item === null || typeof item === "string" || typeof item === "boolean") return true;
    if (typeof item === "number") return Number.isFinite(item) && !Object.is(item, -0);
    if (!item || typeof item !== "object" || seen.has(item) || (!Array.isArray(item) && Object.getPrototypeOf(item) !== Object.prototype)) return false;
    if (Array.isArray(item) && (Object.keys(item).length !== item.length || Object.keys(item).some((key, i) => key !== String(i)))) return false;
    seen.add(item); const result = Object.values(item).every(safe); seen.delete(item); return result;
  }
  if (!safe(value)) throw failure("CORRUPT", "The damaged record cannot be compared safely. Keep browser data and seek recovery help.");
  return JSON.stringify(value);
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
      Object.keys(record).some(key => !["schema", "revision", "library", "legacyBaseline", "epoch"].includes(key)) ||
      record.schema !== SCHEMA || !Number.isSafeInteger(record.revision) || record.revision < 1 ||
      typeof record.library !== "string" || !(record.legacyBaseline === null || typeof record.legacyBaseline === "string") ||
      (Object.hasOwn(record, "epoch") && (typeof record.epoch !== "string" || !record.epoch || record.epoch.length > 128))) {
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
      // A blocked request may later resume after we have reported failure.
      // Abort that late upgrade rather than silently changing the schema.
      if (settled) { request.transaction.abort(); return; }
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
  let revision = null, epoch = null, guarded = null, closed = false;
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
        tx.onabort = () => reject(reason ?? failure("ABORTED", tx.error?.name === "QuotaExceededError"
          ? "Browser storage is full. No transaction changes were committed. Keep your backup and review saved data before freeing space or retrying."
          : "The library transaction was aborted. No transaction changes were committed. Review saved data before retrying.", tx.error));
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
            current = { schema: SCHEMA, revision: 1, epoch: createEpoch(), library: JSON.stringify(library), legacyBaseline: legacyAtOpen };
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
    epoch = initial.epoch ?? null;
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
        if (!snapshot && !refresh && (current.revision !== revision || (current.epoch ?? null) !== epoch)) {
          throw failure("CONFLICT", "The saved library changed in another tab. Reopen DHUN to review it.");
        }
        if (refresh) { revision = current.revision; epoch = current.epoch ?? null; }
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
        const expected = revision, expectedEpoch = epoch;
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
              if (current.revision !== expected || (current.epoch ?? null) !== expectedEpoch) throw failure("CONFLICT", "The saved library changed in another tab. Nothing was written; reopen DHUN to review it.");
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
        epoch = committed.epoch ?? null;
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

/** Read raw recovery records without normalizing, migrating, or writing them.
 * Missing, unreadable and unrecognized records are reported rather than
 * interpreted as an empty library. The caller labels downloads as raw recovery
 * evidence, not as validated importable backups. */
export async function inspectIndexedDBRecovery({ indexedDB, legacyStorage, databaseName = LIBRARY_DATABASE, legacyKey = STORAGE_KEY } = {}) {
  let legacy = { status: "unavailable", text: null };
  try {
    if (legacyStorage === undefined) legacyStorage = globalThis.localStorage;
    if (legacyStorage) {
      const text = legacyStorage.getItem(legacyKey);
      legacy = { status: text === null ? "missing" : "available", text };
    }
  } catch { /* preserve the possibility of reading the database */ }
  try {
    if (indexedDB === undefined) indexedDB = globalThis.indexedDB;
    if (!indexedDB) return { legacy, database: { status: "unavailable" } };
    const db = await new Promise((resolve, reject) => {
      const request = indexedDB.open(databaseName);
      let creating = false, settled = false;
      request.onupgradeneeded = () => { creating = true; request.transaction.abort(); };
      request.onblocked = () => { settled = true; reject(failure("BLOCKED", "Another tab is blocking the database.")); };
      request.onerror = () => { if (!settled) { settled = true; reject(failure(creating ? "MISSING" : "UNAVAILABLE", "The database could not be read.")); } };
      request.onsuccess = () => { if (settled) request.result.close(); else { settled = true; resolve(request.result); } };
    });
    try {
      if (!db.objectStoreNames.contains(STORE)) return { legacy, database: { status: "unrecognized" } };
      const records = await new Promise((resolve, reject) => {
        const tx = db.transaction(STORE, "readonly"), store = tx.objectStore(STORE);
        const current = store.get(CURRENT), previous = store.get(PREVIOUS), keys = store.getAllKeys(), all = store.getAll();
        tx.oncomplete = () => resolve({ current: current.result, previous: previous.result,
          hasCurrent: keys.result.includes(CURRENT), hasPrevious: keys.result.includes(PREVIOUS), archived: keys.result.map((key, index) => ({ key, record: all.result[index] })).filter(item => typeof item.key === "string" && item.key.startsWith("legacy-recovery:")) });
        tx.onabort = () => reject(tx.error);
      });
      return { legacy, database: { status: "available", ...records } };
    } finally { db.close(); }
  } catch (error) {
    return { legacy, database: { status: error?.code === "MISSING" ? "missing" : "unavailable" } };
  }
}

/** Explicitly choose the canonical or validated legacy library after divergence.
 * Both sides and the prior baseline are archived in the same
 * transaction as the revision-checked metadata update; no legacy key is written.
 * A concurrent legacy change can still occur across browser storage systems and
 * must be reported after commit rather than described as a confirmed resolution.
 */
export async function acknowledgeLegacyDivergence({ expectedCurrent, expectedLegacy, choice = "canonical", indexedDB, legacyStorage, databaseName = LIBRARY_DATABASE, legacyKey = STORAGE_KEY } = {}) {
  validateRecord(expectedCurrent);
  if (!["canonical", "legacy"].includes(choice)) throw failure("CORRUPT", "The recovery choice is unrecognized.");
  let legacyCandidate;
  if (choice === "legacy") {
    try { legacyCandidate = JSON.stringify(validateLibraryText(expectedLegacy)); }
    catch (error) { throw failure("LEGACY_CORRUPT", "Legacy data cannot be adopted safely. Keep the original and seek recovery help.", error); }
  }
  if (!(expectedLegacy === null || typeof expectedLegacy === "string")) throw failure("CORRUPT", "Legacy data was not reviewed safely.");
  try { if (indexedDB === undefined) indexedDB = globalThis.indexedDB; if (legacyStorage === undefined) legacyStorage = globalThis.localStorage; }
  catch (error) { throw failure("UNAVAILABLE", "Storage is unavailable; no recovery choice was saved.", error); }
  if (!legacyStorage) throw failure("LEGACY_UNREADABLE", "Legacy data cannot be read; no recovery choice was saved.");
  const readLegacy = () => { try { return legacyStorage.getItem(legacyKey); } catch (error) { throw failure("LEGACY_UNREADABLE", "Legacy data cannot be read; no recovery choice was saved.", error); } };
  const legacy = readLegacy();
  if (legacy !== expectedLegacy) throw failure("LEGACY_DIVERGED", "Legacy data changed after review. Reload and review both copies again.");
  const db = await openDatabase(indexedDB, databaseName);
  try {
    const changed = await new Promise((resolve, reject) => {
      let reason;
      const tx = db.transaction(STORE, "readwrite"), store = tx.objectStore(STORE);
      const c = store.get(CURRENT), p = store.get(PREVIOUS);
      let current, previous, left = 2, updated = false;
      const abort = error => { reason = error; try { tx.abort(); } catch { /* completion reports uncertainty */ } };
      tx.oncomplete = () => reason ? reject(failure("COMMIT_UNCERTAIN", "Review saved data before retrying.", reason)) : resolve(updated);
      tx.onabort = () => reject(reason ?? failure("ABORTED", "Recovery transaction aborted; no changes were committed.", tx.error));
      const finish = () => {
        if (--left) return;
        try {
          validateRecord(current);
          if (current.revision !== expectedCurrent.revision || current.schema !== expectedCurrent.schema ||
              current.library !== expectedCurrent.library || current.legacyBaseline !== expectedCurrent.legacyBaseline ||
              (current.epoch ?? null) !== (expectedCurrent.epoch ?? null)) {
            throw failure("CONFLICT", "The canonical library changed. Reload and review both copies again.");
          }
          if (current.revision > 1 && previous === undefined) throw failure("CORRUPT", "The previous recovery record is missing.");
          if (previous !== undefined) {
            validateRecord(previous);
            if (previous.revision >= current.revision || previous.legacyBaseline !== current.legacyBaseline) throw failure("CORRUPT", "Recovery metadata is inconsistent.");
          }
          if (readLegacy() !== legacy) throw failure("LEGACY_DIVERGED", "Legacy data changed again. Reload and review both copies again.");
          if (current.legacyBaseline === legacy) return;
          if (choice === "legacy" && current.revision === Number.MAX_SAFE_INTEGER) throw failure("CORRUPT", "The saved revision cannot be advanced safely.");
          const id = `legacy-recovery:${Date.now()}:${globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2)}`;
          store.add({ schema: SCHEMA, currentRevision: current.revision, priorBaseline: current.legacyBaseline, legacy, canonical: current, previous: previous ?? null }, id);
          if (choice === "legacy") {
            store.put({ ...current, revision: current.revision + 1, library: legacyCandidate, legacyBaseline: legacy }, CURRENT);
            store.put({ ...current, legacyBaseline: legacy }, PREVIOUS);
          } else {
            store.put({ ...current, legacyBaseline: legacy }, CURRENT);
            if (previous !== undefined) store.put({ ...previous, legacyBaseline: legacy }, PREVIOUS);
          }
          updated = true;
        } catch (error) { abort(error); }
      };
      c.onsuccess = () => { current = c.result; finish(); };
      p.onsuccess = () => { previous = p.result; finish(); };
    });
    try { if (readLegacy() !== legacy) throw new Error("legacy changed"); }
    catch (error) { throw failure("COMMIT_UNCERTAIN", "Recovery metadata may have been updated, but legacy data changed again or cannot be read. Keep every copy and review again.", error); }
    return changed;
  } finally { db.close(); }
}

/** Restore a validated previous record only when the current key is genuinely
 * absent. The legacy baseline and the exact reviewed previous record are checked
 * again inside the same transaction. No existing current record is overwritten. */
export async function restoreMissingCurrent({ expectedPrevious, expectedLegacy, indexedDB, legacyStorage, databaseName = LIBRARY_DATABASE, legacyKey = STORAGE_KEY } = {}) {
  validateRecord(expectedPrevious);
  if (expectedPrevious.revision === Number.MAX_SAFE_INTEGER) throw failure("CORRUPT", "The recovery revision cannot be advanced safely.");
  try { if (indexedDB === undefined) indexedDB = globalThis.indexedDB; if (legacyStorage === undefined) legacyStorage = globalThis.localStorage; }
  catch (error) { throw failure("UNAVAILABLE", "Storage is unavailable; no recovery was saved.", error); }
  const readLegacy = () => {
    try { if (!legacyStorage) throw new Error("unavailable"); return legacyStorage.getItem(legacyKey); }
    catch (error) { throw failure("LEGACY_UNREADABLE", "Legacy data cannot be checked; no recovery was saved.", error); }
  };
  if (readLegacy() !== expectedLegacy || expectedPrevious.legacyBaseline !== expectedLegacy) {
    throw failure("LEGACY_DIVERGED", "Legacy data differs from this recovery record. Keep all copies and seek recovery help.");
  }
  // A missing current record can have had any revision. A fresh random epoch
  // prevents a surviving old tab from matching a reused revision (ABA).
  const newEpoch = createEpoch();
  const db = await openDatabase(indexedDB, databaseName);
  try {
    await new Promise((resolve, reject) => {
      let reason;
      const tx = db.transaction(STORE, "readwrite"), store = tx.objectStore(STORE);
      const key = store.getKey(CURRENT), prev = store.get(PREVIOUS);
      let remaining = 2;
      const abort = error => { reason = error; try { tx.abort(); } catch { /* completion is uncertain */ } };
      tx.oncomplete = () => reason ? reject(failure("COMMIT_UNCERTAIN", "Review saved data before retrying.", reason)) : resolve();
      tx.onabort = () => reject(reason ?? failure("ABORTED", "Recovery transaction aborted; no changes were committed.", tx.error));
      const finish = () => {
        if (--remaining) return;
        try {
          if (key.result !== undefined) throw failure("CONFLICT", "A current record now exists. Reload and review it before recovery.");
          const previous = prev.result;
          validateRecord(previous);
          if (previous.schema !== expectedPrevious.schema || previous.revision !== expectedPrevious.revision ||
              previous.library !== expectedPrevious.library || previous.legacyBaseline !== expectedPrevious.legacyBaseline ||
              (previous.epoch ?? null) !== (expectedPrevious.epoch ?? null)) {
            throw failure("CONFLICT", "The previous record changed. Reload and review it before recovery.");
          }
          if (readLegacy() !== expectedLegacy) throw failure("LEGACY_DIVERGED", "Legacy data changed during review. Keep all copies and retry recovery.");
          store.add({ ...previous, revision: previous.revision + 1, epoch: newEpoch }, CURRENT);
        } catch (error) { abort(error); }
      };
      key.onsuccess = finish; prev.onsuccess = finish;
    });
    try { if (readLegacy() !== expectedLegacy) throw new Error("legacy changed"); }
    catch (error) { throw failure("COMMIT_UNCERTAIN", "The recovery may have committed, but legacy data changed or cannot be checked. Keep all copies and review again.", error); }
  } finally { db.close(); }
}

/** Explicit recovery of a JSON-representable damaged current record. Both the
 * reviewed damaged record and previous record are archived before replacement;
 * all checks and writes occur in one IndexedDB transaction. */
export async function restoreCorruptCurrent({ expectedCurrent, expectedPrevious, expectedLegacy, indexedDB, legacyStorage, databaseName = LIBRARY_DATABASE, legacyKey = STORAGE_KEY } = {}) {
  const reviewedCurrent = recoveryRecordJSON(expectedCurrent);
  validateRecord(expectedPrevious);
  if (expectedPrevious.revision === Number.MAX_SAFE_INTEGER) throw failure("CORRUPT", "The recovery revision cannot be advanced safely.");
  try { if (indexedDB === undefined) indexedDB = globalThis.indexedDB; if (legacyStorage === undefined) legacyStorage = globalThis.localStorage; }
  catch (error) { throw failure("UNAVAILABLE", "Storage is unavailable; no recovery was saved.", error); }
  const readLegacy = () => {
    try { if (!legacyStorage) throw new Error("unavailable"); return legacyStorage.getItem(legacyKey); }
    catch (error) { throw failure("LEGACY_UNREADABLE", "Legacy data cannot be checked; no recovery was saved.", error); }
  };
  if (readLegacy() !== expectedLegacy || expectedPrevious.legacyBaseline !== expectedLegacy) throw failure("LEGACY_DIVERGED", "Legacy data differs from this recovery record. Keep all copies and seek recovery help.");
  const newEpoch = createEpoch();
  const db = await openDatabase(indexedDB, databaseName);
  try {
    await new Promise((resolve, reject) => {
      let reason;
      const tx = db.transaction(STORE, "readwrite"), store = tx.objectStore(STORE);
      const current = store.get(CURRENT), key = store.getKey(CURRENT), prev = store.get(PREVIOUS);
      let remaining = 3;
      const abort = error => { reason = error; try { tx.abort(); } catch { /* completion is uncertain */ } };
      tx.oncomplete = () => reason ? reject(failure("COMMIT_UNCERTAIN", "Review saved data before retrying.", reason)) : resolve();
      tx.onabort = () => reject(reason ?? failure("ABORTED", "Recovery transaction aborted; no changes were committed.", tx.error));
      const finish = () => {
        if (--remaining) return;
        try {
          if (key.result === undefined || recoveryRecordJSON(current.result) !== reviewedCurrent) throw failure("CONFLICT", "The current record changed. Reload and review it before recovery.");
          const previous = prev.result;
          validateRecord(previous);
          if (previous.schema !== expectedPrevious.schema || previous.revision !== expectedPrevious.revision || previous.library !== expectedPrevious.library ||
              previous.legacyBaseline !== expectedPrevious.legacyBaseline || (previous.epoch ?? null) !== (expectedPrevious.epoch ?? null)) {
            throw failure("CONFLICT", "The previous record changed. Reload and review it before recovery.");
          }
          if (readLegacy() !== expectedLegacy) throw failure("LEGACY_DIVERGED", "Legacy data changed during review. Keep all copies and retry recovery.");
          const id = `legacy-recovery:${Date.now()}:${createEpoch()}`;
          store.add({ kind: "corrupt-current", current: current.result, previous }, id);
          store.put({ ...previous, revision: previous.revision + 1, epoch: newEpoch }, CURRENT);
        } catch (error) { abort(error); }
      };
      current.onsuccess = finish; key.onsuccess = finish; prev.onsuccess = finish;
    });
    try { if (readLegacy() !== expectedLegacy) throw new Error("legacy changed"); }
    catch (error) { throw failure("COMMIT_UNCERTAIN", "Recovery may have committed, but legacy data changed or cannot be checked. Keep all copies and review again.", error); }
  } finally { db.close(); }
}
