/** End-to-end browser storage checks against the production app and a frozen
 * complete old-main app (faa4a012). Fictional data; not a deployed-site test. */
import assert from "node:assert/strict";
import { readFile, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { chromium } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { newAppContext, installCatalogueOutage } from "./catalogue-fixture.mjs";

const oldRoot = fileURLToPath(new URL("./fixtures/old-main-app/", import.meta.url));
const seed = { likes: ["legacy-fixture"], playlists: [], history: [], snapshots: {}, recentSearches: [],
  settings: { theme: "dark", accent: "brand", volume: 0.8, repeat: "off", shuffle: false } };
const raw = page => page.evaluate(async () => {
  const db = await new Promise((resolve, reject) => { const r = indexedDB.open("dhun.web.library"); r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error); });
  return new Promise((resolve, reject) => { const tx = db.transaction("library"), store = tx.objectStore("library"), keys = store.getAllKeys(), values = store.getAll();
    tx.oncomplete = () => { db.close(); resolve({ records: Object.fromEntries(keys.result.map((k, i) => [k, values.result[i]])), legacy: localStorage.getItem("dhun.web.v3") }); };
    tx.onabort = () => reject(tx.error); });
});
const waitSettings = page => page.locator('[data-action="set-theme"][data-value="light"]').waitFor();
const waitSaved = page => page.getByText("Library saved.", { exact: true }).waitFor();
async function downloaded(page, action) {
  const pending = page.waitForEvent("download"); await action();
  const download = await pending; return JSON.parse(await readFile(await download.path(), "utf8"));
}

export async function checkIntegratedStorage(browser, { base, record }) {
  const contexts = [];
  const fresh = async () => { const c = await newAppContext(browser, { serviceWorkers: "block" }); contexts.push(c); return c; };
  try {
    let context = await fresh();
    await context.addInitScript(seed => { if (localStorage.getItem("dhun.web.v3") === null) localStorage.setItem("dhun.web.v3", JSON.stringify(seed)); }, seed);
    // Route the whole frozen build on the same origin, not a storage mock. The
    // app's original module graph and controls execute unchanged.
    await context.route(`${base}/old-main/**`, async route => {
      const name = new URL(route.request().url()).pathname.slice("/old-main/".length) || "index.html";
      if (name.includes("..")) return route.abort();
      try { const body = await readFile(`${oldRoot}${name}`); const type = name.endsWith(".js") ? "text/javascript" : name.endsWith(".css") ? "text/css" : name.endsWith(".html") ? "text/html" : "application/octet-stream";
        await route.fulfill({ status: 200, contentType: type, body }); } catch { await route.fulfill({ status: 404, body: "fixture missing" }); }
    });
    const old = await context.newPage(), newer = await context.newPage();
    await old.goto(`${base}/old-main/#/settings`); await waitSettings(old);
    await newer.goto(`${base}/app/#/settings`); await waitSettings(newer);
    let state = await raw(newer);
    assert.equal(state.records.current.revision, 1);
    assert.equal(JSON.parse(state.records.current.library).likes[0], "legacy-fixture");
    assert.equal(JSON.parse(state.legacy).settings.theme, "dark");
    await newer.locator('[data-action="set-theme"][data-value="light"]').click(); await waitSaved(newer);
    await old.locator('[data-action="set-theme"][data-value="system"]').click();
    await newer.locator('[data-action="set-accent"][data-value="cyan"]').click();
    await newer.getByText(/cannot write until saved data is reviewed/i).first().waitFor();
    state = await raw(newer);
    assert.equal(JSON.parse(state.records.current.library).settings.theme, "light");
    assert.equal(JSON.parse(state.legacy).settings.theme, "system");
    await newer.reload(); await newer.getByRole("heading", { name: "Saved data needs review" }).waitFor();
    const canonical = await downloaded(newer, () => newer.getByRole("button", { name: "Download validated canonical backup" }).click());
    const legacy = await downloaded(newer, () => newer.getByRole("button", { name: "Download validated legacy backup" }).click());
    assert.equal(canonical.settings.theme, "light"); assert.equal(legacy.settings.theme, "system");
    const legacyText = await downloaded(newer, () => newer.getByRole("button", { name: "Download legacy storage text (JSON string)" }).click());
    assert.equal(JSON.parse(legacyText).settings.theme, "system");
    // A changed legacy copy after review cannot be silently acknowledged.
    await old.locator('[data-action="set-theme"][data-value="dark"]').click();
    newer.once("dialog", d => d.accept()); await newer.getByRole("button", { name: "Keep database library and archive legacy copy" }).click();
    await newer.getByText(/Legacy data changed after review/).waitFor();
    state = await raw(newer); assert.equal(Object.keys(state.records).filter(k => k.startsWith("legacy-recovery:")).length, 0);
    await newer.reload(); await newer.getByRole("heading", { name: "Saved data needs review" }).waitFor();
    newer.once("dialog", d => d.accept()); await newer.getByRole("button", { name: "Keep database library and archive legacy copy" }).click();
    await waitSettings(newer);
    state = await raw(newer);
    assert.equal(JSON.parse(state.records.current.library).settings.theme, "light");
    assert.equal(JSON.parse(state.legacy).settings.theme, "dark");
    const archived = Object.values(state.records).find(r => r && Object.hasOwn(r, "priorBaseline"));
    assert.equal(JSON.parse(archived.legacy).settings.theme, "dark");
    record("/app/ real old/new recovery", "full frozen old-main app writes legacy after migration; both validated copies download, review races are refused, and explicit canonical choice archives legacy without changing its key");

    context = await fresh();
    await context.addInitScript(seed => { if (localStorage.getItem("dhun.web.v3") === null) localStorage.setItem("dhun.web.v3", JSON.stringify(seed)); }, seed);
    const adoptPage = await context.newPage(); await adoptPage.goto(`${base}/app/#/settings`); await waitSettings(adoptPage);
    await adoptPage.locator('[data-action="set-theme"][data-value="light"]').click(); await waitSaved(adoptPage);
    await adoptPage.evaluate(() => { const value = JSON.parse(localStorage.getItem("dhun.web.v3")); value.settings.theme = "system"; localStorage.setItem("dhun.web.v3", JSON.stringify(value)); });
    await adoptPage.reload(); await adoptPage.getByRole("heading", { name: "Saved data needs review" }).waitFor();
    adoptPage.once("dialog", d => d.accept());
    await adoptPage.getByRole("button", { name: "Use validated legacy library and archive database copies" }).click();
    await waitSettings(adoptPage); state = await raw(adoptPage);
    assert.equal(JSON.parse(state.records.current.library).settings.theme, "system");
    assert.equal(JSON.parse(state.records.previous.library).settings.theme, "light");
    const adoption = Object.values(state.records).find(r => r && Object.hasOwn(r, "priorBaseline"));
    assert.equal(JSON.parse(adoption.canonical.library).settings.theme, "light");
    assert.equal(JSON.parse(adoption.legacy).settings.theme, "system");
    assert.equal(JSON.parse(state.legacy).settings.theme, "system");
    await adoptPage.reload(); await waitSettings(adoptPage);
    record("/app/ choose legacy recovery", "explicit adoption commits validated legacy and archives canonical/previous copies atomically; reload retains the selection");

    // Export reads a fresh committed snapshot but does not promote the stale
    // tab's write revision. Its failed optimistic edit is separately exportable.
    context = await fresh(); const a = await context.newPage(), b = await context.newPage();
    await a.goto(`${base}/app/#/settings`); await waitSettings(a);
    await b.goto(`${base}/app/#/settings`); await waitSettings(b);
    await b.locator('[data-action="set-theme"][data-value="light"]').click(); await waitSaved(b);
    const exported = await downloaded(a, () => a.locator('[data-action="export-library"]').click());
    assert.equal(exported.settings.theme, "light");
    await a.locator('[data-action="set-theme"][data-value="system"]').click();
    await a.getByText(/cannot write until saved data is reviewed/i).first().waitFor();
    a.once("dialog", d => d.accept());
    const visit = await downloaded(a, () => a.locator('[data-action="export-library"]').click());
    assert.equal(visit.settings.theme, "system");
    assert.equal(JSON.parse((await raw(a)).records.current.library).settings.theme, "light");
    await b.reload(); await waitSettings(b);
    assert.equal(JSON.parse((await raw(b)).records.current.library).settings.theme, "light");
    record("/app/ concurrent export and conflict", "actual export reads committed revision without authorizing stale overwrite; failed in-tab edit is separately downloadable and newer saved data survives reload");

    context = await fresh();
    await context.addInitScript(() => {
      const put = IDBObjectStore.prototype.put;
      IDBObjectStore.prototype.put = function (...args) {
        const request = put.apply(this, args);
        if (window.__holdTransaction && args[1] === "current") {
          const store = this;
          const keepOpen = () => { if (window.__holdTransaction) { const next = store.get("current"); next.onsuccess = keepOpen; } };
          keepOpen();
        }
        return request;
      };
    });
    const gated = await context.newPage(); await gated.goto(`${base}/app/#/settings`); await waitSettings(gated);
    await gated.evaluate(() => { window.__holdTransaction = true; });
    await gated.locator('[data-action="set-theme"][data-value="light"]').click();
    await gated.getByText("Saving library…").waitFor();
    await gated.locator('[data-action="set-accent"][data-value="cyan"]').click();
    await gated.getByText(/library operation is still in progress/i).waitFor();
    await gated.evaluate(() => { window.__holdTransaction = false; }); await waitSaved(gated);
    const gatedLibrary = JSON.parse((await raw(gated)).records.current.library);
    assert.equal(gatedLibrary.settings.theme, "light"); assert.equal(gatedLibrary.settings.accent, "brand");
    record("/app/ pending mutation gate", "an intentionally held transaction shows pending state and rejects an overlapping action without reporting it saved");

    // Import the actual exported file through the app's file input in a new
    // browser context, then export/reload and compare the complete payload.
    context = await fresh(); const target = await context.newPage();
    await target.goto(`${base}/app/#/settings`); await waitSettings(target);
    await target.locator('[data-action="import-library"]').setInputFiles({ name: "backup.json", mimeType: "application/json", buffer: Buffer.from(JSON.stringify(exported)) });
    await target.getByText("Library imported.").waitFor();
    await target.reload(); await waitSettings(target);
    const again = await downloaded(target, () => target.locator('[data-action="export-library"]').click());
    const { exportedAt: _firstTime, ...first } = exported, { exportedAt: _secondTime, ...second } = again;
    assert.deepEqual(second, first);
    record("/app/ actual export/import round trip", "a file exported by the app imports in a fresh browser context, survives reload and exports the same complete library");

    // Abort the initial migration transaction; reload in the same tab retries
    // without modifying or replacing the original legacy string.
    context = await fresh();
    await context.addInitScript(seed => {
      if (localStorage.getItem("dhun.web.v3") === null) localStorage.setItem("dhun.web.v3", JSON.stringify(seed));
      if (!sessionStorage.getItem("migration-aborted")) {
        sessionStorage.setItem("migration-aborted", "yes");
        const add = IDBObjectStore.prototype.add;
        IDBObjectStore.prototype.add = function (...args) { const request = add.apply(this, args); if (args[1] === "current") this.transaction.abort(); return request; };
      }
    }, seed);
    const retry = await context.newPage(); await retry.goto(`${base}/app/#/settings`);
    await retry.getByRole("heading", { name: "Saved data needs review" }).waitFor();
    assert.equal(await retry.evaluate(() => localStorage.getItem("dhun.web.v3")), JSON.stringify(seed));
    await retry.reload(); await waitSettings(retry);
    state = await raw(retry); assert.equal(state.records.current.revision, 1);
    assert.equal(JSON.parse(state.records.current.library).likes[0], "legacy-fixture");
    record("/app/ interrupted migration", "injected initial IndexedDB abort leaves legacy bytes intact; reload retries and migrates once");

    context = await fresh();
    await context.addInitScript(seed => {
      if (localStorage.getItem("dhun.web.v3") === null) localStorage.setItem("dhun.web.v3", JSON.stringify(seed));
      IDBObjectStore.prototype.get = function () { throw new DOMException("Injected read failure", "UnknownError"); };
    }, seed);
    const unreadable = await context.newPage(); await unreadable.goto(`${base}/app/#/settings`);
    await unreadable.getByRole("heading", { name: "Saved data needs review" }).waitFor();
    await unreadable.getByRole("button", { name: "Download legacy storage text (JSON string)" }).waitFor();
    assert.equal(await unreadable.evaluate(() => localStorage.getItem("dhun.web.v3")), JSON.stringify(seed));
    record("/app/ startup read failure", "injected IndexedDB read failure disables the app and retains downloadable legacy text");

    context = await fresh(); const corrupt = await context.newPage();
    await corrupt.goto(`${base}/app/#/settings`); await waitSettings(corrupt);
    await corrupt.locator('[data-action="set-theme"][data-value="light"]').click(); await waitSaved(corrupt);
    const beforeDeletion = await raw(corrupt);
    const staleBeforeRecovery = await context.newPage(); await staleBeforeRecovery.goto(`${base}/app/#/settings`); await waitSettings(staleBeforeRecovery);
    await corrupt.evaluate(async () => {
      const db = await new Promise((resolve, reject) => { const r = indexedDB.open("dhun.web.library"); r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error); });
      await new Promise((resolve, reject) => { const tx = db.transaction("library", "readwrite"); tx.objectStore("library").delete("current"); tx.oncomplete = resolve; tx.onabort = () => reject(tx.error); }); db.close();
    });
    await corrupt.reload(); await corrupt.getByRole("heading", { name: "Saved data needs review" }).waitFor();
    await corrupt.getByRole("button", { name: "Download database previous record" }).waitFor();
    state = await raw(corrupt); assert.equal(Object.hasOwn(state.records, "current"), false); assert.ok(state.records.previous);
    const previousBackup = await downloaded(corrupt, () => corrupt.getByRole("button", { name: "Download validated previous backup" }).click());
    assert.equal(previousBackup.settings.theme, "dark");
    const changeCurrent = (page, value) => page.evaluate(async value => {
      const db = await new Promise((resolve, reject) => { const r = indexedDB.open("dhun.web.library"); r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error); });
      await new Promise((resolve, reject) => { const tx = db.transaction("library", "readwrite"), store = tx.objectStore("library"); if (value) store.put(value, "current"); else store.delete("current"); tx.oncomplete = resolve; tx.onabort = () => reject(tx.error); }); db.close();
    }, value);
    await changeCurrent(corrupt, beforeDeletion.records.current);
    corrupt.once("dialog", d => d.accept()); await corrupt.getByRole("button", { name: "Restore previous library when current is missing" }).click();
    await corrupt.getByText(/A current record now exists/).waitFor();
    assert.equal((await raw(corrupt)).records.current.library, beforeDeletion.records.current.library);
    await changeCurrent(corrupt, null); await corrupt.reload();
    await corrupt.getByRole("button", { name: "Restore previous library when current is missing" }).waitFor();
    corrupt.once("dialog", d => d.accept()); await corrupt.getByRole("button", { name: "Restore previous library when current is missing" }).click();
    await waitSettings(corrupt); state = await raw(corrupt);
    assert.equal(JSON.parse(state.records.current.library).settings.theme, "dark");
    assert.equal(state.records.previous.revision + 1, state.records.current.revision);
    assert.ok(state.records.current.epoch);
    await staleBeforeRecovery.locator('[data-action="set-theme"][data-value="system"]').click();
    await staleBeforeRecovery.getByText(/cannot write until saved data is reviewed/i).first().waitFor();
    assert.equal(JSON.parse((await raw(staleBeforeRecovery)).records.current.library).settings.theme, "dark");
    record("/app/ missing current record", "validated previous backup downloads; restore refuses a reappearing current and a new generation blocks a stale tab even when the revision is reused");

    context = await fresh(); const initialOld = await context.newPage(), initialNew = await context.newPage();
    await initialOld.goto(`${base}/app/#/settings`); await waitSettings(initialOld);
    const oldEpoch = (await raw(initialOld)).records.current.epoch;
    await changeCurrent(initialOld, null);
    await initialNew.goto(`${base}/app/#/settings`); await waitSettings(initialNew);
    state = await raw(initialNew); assert.equal(state.records.current.revision, 1); assert.notEqual(state.records.current.epoch, oldEpoch);
    await initialOld.locator('[data-action="set-theme"][data-value="light"]').click();
    await initialOld.getByText(/cannot write until saved data is reviewed/i).first().waitFor();
    assert.equal(JSON.parse((await raw(initialNew)).records.current.library).settings.theme, "dark");
    record("/app/ missing initial database record", "a reinitialized revision one receives a new generation; an older revision-one tab cannot overwrite it");

    context = await fresh(); const damagedWhileOpen = await context.newPage();
    await damagedWhileOpen.goto(`${base}/app/#/settings`); await waitSettings(damagedWhileOpen);
    await damagedWhileOpen.evaluate(async () => {
      const db = await new Promise((resolve, reject) => { const r = indexedDB.open("dhun.web.library"); r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error); });
      await new Promise((resolve, reject) => { const tx = db.transaction("library", "readwrite"), store = tx.objectStore("library"), r = store.get("current");
        r.onsuccess = () => store.put({ ...r.result, library: "{broken database text" }, "current"); tx.oncomplete = resolve; tx.onabort = () => reject(tx.error); }); db.close();
    });
    await damagedWhileOpen.locator('[data-action="set-theme"][data-value="light"]').click();
    await damagedWhileOpen.getByText(/Saved data could not be read safely/).first().waitFor();
    damagedWhileOpen.once("dialog", d => d.accept());
    const inTab = await downloaded(damagedWhileOpen, () => damagedWhileOpen.locator('[data-action="export-library"]').click());
    assert.equal(inTab.settings.theme, "light");
    assert.equal((await raw(damagedWhileOpen)).records.current.library, "{broken database text");
    record("/app/ corruption after startup", "after an injected database corruption, the guarded tab can export its labelled in-memory edit while the damaged saved bytes remain untouched");

    context = await fresh(); const blocked = await context.newPage(); await blocked.goto(`${base}/404.html`);
    await blocked.evaluate(async () => {
      const m = await import("/app/js/library.js"); const name = `dhun-blocked-${Math.random()}`;
      const hold = await new Promise((resolve, reject) => { const r = indexedDB.open(name, 1); r.onupgradeneeded = () => r.result.createObjectStore("library"); r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error); });
      hold.onversionchange = () => {}; // simulate old tab that refuses to close
      try {
        await m.createIndexedDBStorage({ databaseName: name, indexedDB: { open: n => indexedDB.open(n, 2) } });
        throw Error("blocked upgrade incorrectly succeeded");
      } catch (error) { if (error.code !== "BLOCKED") throw error; }
      finally { hold.close(); }
      const reopened = await new Promise((resolve, reject) => { const r = indexedDB.open(name, 1); r.onsuccess = () => resolve(r.result); r.onerror = () => reject(r.error); });
      if (reopened.version !== 1) throw Error("a late blocked upgrade changed the schema");
      reopened.close();
    });
    record("/app/ blocked database upgrade", "real Chromium upgrade blocked by an older connection is reported; a later unblocking cannot silently upgrade the database");

    // A browser-imposed low quota causes an actual IDB quota error, not a
    // synthetic Storage.setItem error. Import must not claim success or replace.
    context = await fresh(); const quota = await context.newPage();
    await quota.goto(`${base}/404.html`);
    const cdp = await context.newCDPSession(quota);
    await cdp.send("Storage.overrideQuotaForOrigin", { origin: new URL(base).origin, quotaSize: 2 * 1024 * 1024 });
    await quota.goto(`${base}/app/#/settings`); await waitSettings(quota);
    const beforeQuota = await raw(quota);
    const big = { ...exported, settings: { ...exported.settings, extra: "x".repeat(6 * 1024 * 1024) } };
    const importOutcome = quota.waitForFunction(() => /Import (failed|unconfirmed)|Library imported/.test(document.querySelector("#dhun-toast")?.textContent || ""), null, { timeout: 30000 });
    await quota.locator('[data-action="import-library"]').setInputFiles({ name: "quota.json", mimeType: "application/json", buffer: Buffer.from(JSON.stringify(big)) });
    await importOutcome;
    assert.match(await quota.locator("#dhun-toast").innerText(), /Import failed: Browser storage is full/);
    assert.equal((await raw(quota)).records.current.library, beforeQuota.records.current.library);
    await quota.reload(); await waitSettings(quota);
    assert.equal((await raw(quota)).records.current.library, beforeQuota.records.current.library);
    await cdp.detach();
    record("/app/ import under real browser quota", "Chromium quota override rejects an otherwise valid large import through the actual UI; the prior committed library survives reload");

    // A persistent profile exercises actual browser process close/relaunch,
    // rather than treating a page reload as a browser restart.
    const directory = await mkdtemp(join(tmpdir(), "dhun-browser-restart-"));
    const launch = async () => {
      const c = await chromium.launchPersistentContext(directory, { headless: true, serviceWorkers: "block", ...(process.env.DHUN_CHROMIUM ? { executablePath: process.env.DHUN_CHROMIUM } : {}) });
      await installCatalogueOutage(c); return c;
    };
    let persistent;
    try {
      persistent = await launch(); let page = await persistent.newPage();
      await page.goto(`${base}/app/#/settings`); await waitSettings(page);
      await page.locator('[data-action="set-theme"][data-value="light"]').click(); await waitSaved(page);
      await persistent.close(); persistent = null;
      persistent = await launch(); page = await persistent.newPage();
      await page.goto(`${base}/app/#/settings`); await waitSettings(page);
      assert.equal(JSON.parse((await raw(page)).records.current.library).settings.theme, "light");
      record("/app/ browser restart", "a real persistent Chromium profile retains the committed library after closing and relaunching the browser process");
    } finally { await persistent?.close(); await rm(directory, { recursive: true, force: true }); }
  } finally { await Promise.all(contexts.map(c => c.close())); }
}
