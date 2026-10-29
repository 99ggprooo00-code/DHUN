import { readFileSync } from "node:fs";
/** Real Chromium component tests for the production IndexedDB adapter.
 * End-to-end application tests are in integrated-storage.mjs. */
export async function checkIndexedDBStorage(browser, { base, record }) {
  const context = await browser.newContext({ serviceWorkers: "block" });
  const a = await context.newPage(); const b = await context.newPage();
  try {
    await Promise.all([a.goto(`${base}/404.html`), b.goto(`${base}/404.html`)]);
    // Frozen, unmodified library adapter from main commit faa4a012. Serving it
    // through Playwright keeps the regression independent of Git checkout depth.
    const legacySource = readFileSync(new URL("./fixtures/legacy-library-v3.js", import.meta.url), "utf8");
    await context.route("**/legacy-library-fixture.js", route => route.fulfill({ status: 200, contentType: "text/javascript", body: legacySource }));
    const prefix = `dhun-idb-test-${Date.now()}-${Math.random()}`;
    const db = `${prefix}-db`, key = `${prefix}-legacy`;
    await a.evaluate(async ({ db, key }) => {
      const m = await import("/app/js/library.js");
      const initial = { ...m.emptyLibrary(), likes: ["legacy-🎵"] };
      localStorage.setItem(key, JSON.stringify(initial));
      window.adapter = await m.createIndexedDBStorage({ databaseName: db, legacyKey: key });
      if ((await window.adapter.read()).likes[0] !== "legacy-🎵") throw Error("legacy migration lost an entry");
    }, { db, key });
    await b.evaluate(async ({ db, key }) => {
      const m = await import("/app/js/library.js");
      window.adapter = await m.createIndexedDBStorage({ databaseName: db, legacyKey: key });
      if ((await window.adapter.read()).likes[0] !== "legacy-🎵") throw Error("second tab read differs");
    }, { db, key });
    await a.evaluate(async () => {
      const v = await window.adapter.read();
      await window.adapter.save({ ...v, likes: ["newer-🎵"] });
    });
    await b.evaluate(async () => {
      const exported = JSON.parse(await window.adapter.exportAll());
      if (exported.likes[0] !== "newer-🎵") throw Error("export did not read one committed snapshot");
      const m = await import("/app/js/library.js");
      try { await window.adapter.save({ ...m.emptyLibrary(), likes: ["stale"] }); throw Error("stale write committed"); }
      catch (error) { if (error.code !== "CONFLICT") throw error; }
    });
    if ((await a.evaluate(async () => (await window.adapter.read()).likes[0])) !== "newer-🎵") throw Error("conflict replaced committed record");
    record("/app/ IndexedDB adapter revision", "separate Chromium tabs: stale write rejected even after exporting the newer snapshot; committed data survived");

    await b.evaluate(async ({ db, key }) => {
      window.adapter.close();
      const m = await import("/app/js/library.js");
      window.adapter = await m.createIndexedDBStorage({ databaseName: db, legacyKey: key,
        beforeCommit({ phase, transaction }) { if (phase === "save") transaction.abort(); } });
      const current = await window.adapter.read();
      try { await window.adapter.save({ ...current, likes: ["aborted"] }); throw Error("injected abort committed"); }
      catch (error) { if (error.code !== "ABORTED") throw error; }
      window.adapter.close();
      window.adapter = await m.createIndexedDBStorage({ databaseName: db, legacyKey: key });
      if ((await window.adapter.read()).likes[0] !== "newer-🎵") throw Error("abort lost the committed record");
      try { await window.adapter.importAll('{"app":"DHUN web","version":3}'); throw Error("invalid import succeeded"); }
      catch (error) { if (error.message === "invalid import succeeded") throw error; }
      const valid = await window.adapter.read();
      for (const bad of [NaN, Infinity, -0, undefined]) {
        try { await window.adapter.save({ ...valid, settings: { ...valid.settings, volume: bad } }); throw Error("invalid candidate succeeded"); }
        catch (error) { if (error.message === "invalid candidate succeeded") throw error; }
      }
      if ((await window.adapter.read()).likes[0] !== "newer-🎵") throw Error("invalid import or candidate replaced committed record");
    }, { db, key });
    record("/app/ IndexedDB adapter abort", "injected abort after queued writes and invalid import left the committed record intact after reopening");

    await b.evaluate(async ({ db, key }) => {
      // Represents a legacy writer; it does not use the new adapter's protocol.
      localStorage.setItem(key, '{"changed":"by older code"}');
      const m = await import("/app/js/library.js");
      try { await window.adapter.save(m.emptyLibrary()); throw Error("legacy conflict wrote a record"); }
      catch (error) { if (error.code !== "LEGACY_DIVERGED") throw error; }
      const raw = await new Promise((resolve, reject) => {
        const request = indexedDB.open(db, 1);
        request.onerror = () => reject(request.error);
        request.onsuccess = () => {
          const d = request.result, tx = d.transaction("library", "readonly"), get = tx.objectStore("library").get("current");
          get.onsuccess = () => { const value = get.result; d.close(); resolve(value); };
          get.onerror = () => reject(get.error);
        };
      });
      if (JSON.parse(raw.library).likes[0] !== "newer-🎵" || localStorage.getItem(key) !== '{"changed":"by older code"}') throw Error("divergent copies were not retained");
    }, { db, key });
    record("/app/ IndexedDB adapter divergence", "injected old-key change blocks a write and retains both legacy bytes and IndexedDB canonical data");

    await a.evaluate(async ({ prefix }) => {
      const m = await import("/app/js/library.js"); const db = `${prefix}-migration`, key = `${prefix}-migration-key`;
      const source = JSON.stringify({ ...m.emptyLibrary(), likes: ["recover-me"] }); localStorage.setItem(key, source);
      try {
        await m.createIndexedDBStorage({ databaseName: db, legacyKey: key,
          beforeCommit({ phase, transaction }) { if (phase === "migration") transaction.abort(); } });
        throw Error("migration abort was ignored");
      } catch (error) { if (error.code !== "ABORTED") throw error; }
      if (localStorage.getItem(key) !== source) throw Error("legacy bytes changed on abort");
      const reopened = await m.createIndexedDBStorage({ databaseName: db, legacyKey: key });
      if ((await reopened.read()).likes[0] !== "recover-me") throw Error("migration retry lost source");
      reopened.close();
      const badKey = `${prefix}-bad`, badDb = `${prefix}-bad-db`, bad = JSON.stringify({ ...m.emptyLibrary(), likes: ["duplicate", "duplicate"] });
      localStorage.setItem(badKey, bad);
      try { await m.createIndexedDBStorage({ databaseName: badDb, legacyKey: badKey }); throw Error("damaged source accepted"); }
      catch (error) { if (error.code !== "LEGACY_CORRUPT") throw error; }
      if (localStorage.getItem(badKey) !== bad) throw Error("damaged legacy source overwritten");
    }, { prefix });
    record("/app/ IndexedDB adapter migration", "injected migration abort retries safely; damaged legacy input is refused without overwriting its bytes");

    const oldKey = `${prefix}-old-key`, oldDb = `${prefix}-old-db`;
    await a.evaluate(async ({ oldKey }) => {
      const old = await import("/legacy-library-fixture.js");
      localStorage.setItem(oldKey, JSON.stringify(old.emptyLibrary()));
      window.oldAdapter = old.createStorage(localStorage, oldKey);
      window.oldAdapter.load();
    }, { oldKey });
    await b.evaluate(async ({ oldKey, oldDb }) => {
      const m = await import("/app/js/library.js");
      window.newAdapter = await m.createIndexedDBStorage({ databaseName: oldDb, legacyKey: oldKey });
      const current = await window.newAdapter.read();
      await window.newAdapter.save({ ...current, likes: ["new-version-save"] });
    }, { oldKey, oldDb });
    await a.evaluate(async () => {
      const old = await import("/legacy-library-fixture.js");
      const stale = window.oldAdapter.load();
      if (!window.oldAdapter.save({ ...stale, likes: ["old-version-save"] })) throw Error("old adapter did not write");
    });
    await b.evaluate(async ({ oldKey, oldDb }) => {
      const m = await import("/app/js/library.js");
      const oldBytes = localStorage.getItem(oldKey);
      try { await window.newAdapter.save(m.emptyLibrary()); throw Error("legacy divergence not rejected"); }
      catch (error) { if (error.code !== "LEGACY_DIVERGED") throw error; }
      window.newAdapter.close();
      try { await m.createIndexedDBStorage({ databaseName: oldDb, legacyKey: oldKey }); throw Error("divergence not detected after reopen"); }
      catch (error) { if (error.code !== "LEGACY_DIVERGED") throw error; }
      const durable = await new Promise((resolve, reject) => {
        const open = indexedDB.open(oldDb, 1);
        open.onerror = () => reject(open.error);
        open.onsuccess = () => { const d = open.result; const get = d.transaction("library").objectStore("library").get("current");
          get.onsuccess = () => { const value = get.result; d.close(); resolve(value); }; get.onerror = () => reject(get.error); };
      });
      if (JSON.parse(oldBytes).likes[0] !== "old-version-save" || JSON.parse(durable.library).likes[0] !== "new-version-save") throw Error("cross-version copies not retained");
    }, { oldKey, oldDb });
    await a.reload();
    if ((await a.evaluate(key => JSON.parse(localStorage.getItem(key)).likes[0], oldKey)) !== "old-version-save") throw Error("old legacy write did not survive reload");
    record("/app/ IndexedDB adapter old version", "frozen main-branch adapter wrote the legacy key after migration; new IndexedDB record and old bytes both survived and divergence blocked writes/reopen");

    await a.evaluate(async ({ prefix }) => {
      const m = await import("/app/js/library.js"); const db = `${prefix}-postcommit`, key = `${prefix}-postcommit-key`;
      const legacy = JSON.stringify(m.emptyLibrary()); localStorage.setItem(key, legacy);
      const adapter = await m.createIndexedDBStorage({ databaseName: db, legacyKey: key,
        beforeCommit({ phase }) { if (phase === "save") localStorage.setItem(key, '{"late":"legacy change"}'); } });
      const candidate = { ...(await adapter.read()), likes: ["committed-but-review"] };
      try { await adapter.save(candidate); throw Error("post-commit divergence reported success"); }
      catch (error) { if (error.code !== "COMMIT_UNCERTAIN") throw error; }
      adapter.close();
      const raw = await new Promise((resolve, reject) => { const open = indexedDB.open(db, 1);
        open.onerror = () => reject(open.error); open.onsuccess = () => { const d = open.result;
          const get = d.transaction("library").objectStore("library").get("current");
          get.onsuccess = () => { const value = get.result; d.close(); resolve(value); }; get.onerror = () => reject(get.error); }; });
      if (JSON.parse(raw.library).likes[0] !== "committed-but-review" || localStorage.getItem(key) !== '{"late":"legacy change"}') throw Error("post-commit outcome or old bytes lost");
    }, { prefix });
    record("/app/ IndexedDB adapter uncertain outcome", "injected legacy change during commit reports review rather than success and retains both copies");

    await a.evaluate(async ({ prefix }) => {
      const m = await import("/app/js/library.js"); const db = `${prefix}-recovery`, key = `${prefix}-recovery-key`;
      const adapter = await m.createIndexedDBStorage({ databaseName: db, legacyKey: key });
      await adapter.save({ ...(await adapter.read()), likes: ["good-current"] }); adapter.close();
      await new Promise((resolve, reject) => { const open = indexedDB.open(db, 1); open.onerror = () => reject(open.error);
        open.onsuccess = () => { const d = open.result, tx = d.transaction("library", "readwrite"); tx.objectStore("library").delete("previous");
          tx.oncomplete = () => { d.close(); resolve(); }; tx.onabort = () => reject(tx.error); }; });
      try { await m.createIndexedDBStorage({ databaseName: db, legacyKey: key }); throw Error("missing recovery metadata accepted"); }
      catch (error) { if (error.code !== "CORRUPT") throw error; }
      const raw = await new Promise((resolve, reject) => { const open = indexedDB.open(db, 1); open.onerror = () => reject(open.error);
        open.onsuccess = () => { const d = open.result, get = d.transaction("library").objectStore("library").get("current");
          get.onsuccess = () => { const v = get.result; d.close(); resolve(v); }; get.onerror = () => reject(get.error); }; });
      if (JSON.parse(raw.library).likes[0] !== "good-current") throw Error("missing metadata check overwrote current record");
      try { await m.createIndexedDBStorage({ indexedDB: null, legacyStorage: localStorage, legacyKey: key }); throw Error("unavailable IndexedDB accepted"); }
      catch (error) { if (error.code !== "UNAVAILABLE") throw error; }
      try { await m.createIndexedDBStorage({ legacyStorage: { getItem() { throw Error("injected denied read"); } }, databaseName: `${prefix}-denied` }); throw Error("unreadable legacy accepted"); }
      catch (error) { if (error.code !== "LEGACY_UNREADABLE") throw error; }
    }, { prefix });
    record("/app/ IndexedDB adapter recovery metadata", "injected missing previous record and unavailable database are refused without overwriting the current record");

    const raceDb = `${prefix}-concurrent`, raceKey = `${prefix}-concurrent-key`;
    for (const page of [a, b]) await page.evaluate(async ({ raceDb, raceKey }) => {
      const m = await import("/app/js/library.js");
      window.raceAdapter = await m.createIndexedDBStorage({ databaseName: raceDb, legacyKey: raceKey });
    }, { raceDb, raceKey });
    const outcomes = await Promise.all([a, b].map((page, index) => page.evaluate(async index => {
      const m = await import("/app/js/library.js");
      try { await window.raceAdapter.save({ ...m.emptyLibrary(), likes: [`writer-${index}`] }); return "committed"; }
      catch (error) { return error.code ?? error.message; }
    }, index)));
    if (outcomes.filter(x => x === "committed").length !== 1 || outcomes.filter(x => x === "CONFLICT").length !== 1) throw Error(`concurrent writes produced ${outcomes.join(", ")}`);
    const winner = await a.evaluate(async ({ raceDb, raceKey }) => {
      const m = await import("/app/js/library.js"); const reopened = await m.createIndexedDBStorage({ databaseName: raceDb, legacyKey: raceKey });
      const value = (await reopened.read()).likes[0]; reopened.close(); return value;
    }, { raceDb, raceKey });
    if (!/^writer-[01]$/.test(winner)) throw Error("concurrent winner did not survive reopening");
    record("/app/ IndexedDB adapter concurrent writes", "two Chromium tabs attempted the same revision concurrently; exactly one committed and the other conflicted");

    await a.evaluate(async ({ prefix }) => {
      const m = await import("/app/js/library.js"), databaseName = `${prefix}-unicode`, legacyKey = `${prefix}-unicode-key`;
      const adapter = await m.createIndexedDBStorage({ databaseName, legacyKey });
      const text = "धुन🎵".repeat(200000), library = { ...(await adapter.read()), settings: { ...(await adapter.read()).settings, extra: text } };
      await adapter.save(library); adapter.close();
      const reopened = await m.createIndexedDBStorage({ databaseName, legacyKey });
      if ((await reopened.read()).settings.extra !== text) throw Error("Unicode data changed across database reopen");
      const backup = await reopened.exportAll();
      if (JSON.parse(backup).settings.extra !== text) throw Error("Unicode data changed during export");
      reopened.close();
      const restored = await m.createIndexedDBStorage({ databaseName: `${databaseName}-restore`, legacyKey: `${legacyKey}-restore` });
      await restored.importAll(backup); restored.close();
      const checked = await m.createIndexedDBStorage({ databaseName: `${databaseName}-restore`, legacyKey: `${legacyKey}-restore` });
      if ((await checked.read()).settings.extra !== text) throw Error("Unicode data changed during import and reopen");
      checked.close();
    }, { prefix });
    record("/app/ IndexedDB adapter Unicode", "a multi-megabyte Unicode library survived export, import and reopen in Chromium");

    const quotaContext = await browser.newContext({ serviceWorkers: "block" });
    try {
      const page = await quotaContext.newPage(); await page.goto(`${base}/404.html`);
      const cdp = await quotaContext.newCDPSession(page);
      await cdp.send("Storage.overrideQuotaForOrigin", { origin: new URL(base).origin, quotaSize: 2 * 1024 * 1024 });
      await page.evaluate(async () => {
        const m = await import("/app/js/library.js");
        const databaseName = `dhun-quota-${Math.random()}`, legacyKey = `${databaseName}-legacy`;
        const adapter = await m.createIndexedDBStorage({ databaseName, legacyKey });
        const before = { ...(await adapter.read()), likes: ["saved-before-quota"] };
        await adapter.save(before);
        try {
          await adapter.save({ ...before, settings: { ...before.settings, extra: "x".repeat(6 * 1024 * 1024) } });
          throw Error("quota-limited transaction unexpectedly succeeded");
        } catch (error) {
          if (error.code !== "ABORTED" || error.cause?.name !== "QuotaExceededError") throw error;
        }
        adapter.close();
        const reopened = await m.createIndexedDBStorage({ databaseName, legacyKey });
        if ((await reopened.read()).likes[0] !== "saved-before-quota") throw Error("quota abort lost committed record");
        reopened.close();
      });
      await cdp.detach();
      record("/app/ IndexedDB adapter quota", "Chromium quota override caused a real IndexedDB QuotaExceededError; the prior committed record survived reopen");
    } finally { await quotaContext.close(); }
  } finally {
    await context.close();
  }
}
