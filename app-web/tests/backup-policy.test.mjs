import test from "node:test";
import assert from "node:assert/strict";
import { BACKUP_MAX_BYTES, BACKUP_MAX_DEPTH, BACKUP_MAX_TOKENS, checkBackupComplexity, checkBackupText } from "../src/js/backup-policy.js";
import { createStorage, emptyLibrary, snapshot, STORAGE_KEY } from "../src/js/library.js";
import { backupExportWarning, handleFileImport } from "../src/js/transfer.js";
import { bootFixture } from "./helpers/boot-fixture.mjs";

const file = text => ({ size: Buffer.byteLength(text), text: async () => text });
function setup() {
  const map = new Map(), control = { failWrite: false };
  const backing = { getItem: k => map.get(k) ?? null, setItem(k, v) { if (control.failWrite && k === STORAGE_KEY) throw Error("quota"); map.set(k, String(v)); }, removeItem: k => map.delete(k) };
  const storage = createStorage(backing);
  const original = emptyLibrary(); original.likes = ["previous"];
  assert.equal(storage.save(original), true);
  return { storage, map, control, original: map.get(STORAGE_KEY), previous: storage.load() };
}

test("ordinary Unicode and legacy large exports round-trip using real storage", async () => {
  const f = setup(), source = emptyLibrary();
  for (let i = 0; i < 2000; i++) { const id = `item-${i}`; source.likes.push(id); source.snapshots[id] = snapshot({ id, title: `धुन 🎵 ${"x".repeat(4500)}` }); }
  const text = f.storage.exportAll(source);
  assert.ok(Buffer.byteLength(text) > 8 * 1024 * 1024);
  assert.equal(backupExportWarning(text), null);
  const restored = await handleFileImport(file(text), f.storage);
  assert.equal(restored.likes.length, 2000);
  assert.equal(restored.snapshots['item-1999'].title, source.snapshots['item-1999'].title);
  assert.deepEqual(f.storage.load(), restored);
});

test("byte boundary is enforced before reading and again on actual UTF-8 text", async () => {
  const f = setup();
  const base = f.storage.exportAll(emptyLibrary());
  for (const delta of [-1, 0]) {
    const text = base + " ".repeat(BACKUP_MAX_BYTES + delta - Buffer.byteLength(base));
    assert.equal(file(text).size, BACKUP_MAX_BYTES + delta);
    assert.deepEqual((await handleFileImport(file(text), f.storage)).likes, []);
  }
  let read = false;
  await assert.rejects(handleFileImport({ size: BACKUP_MAX_BYTES + 1, text: async () => { read = true; return base; } }, f.storage), /24 MiB/);
  assert.equal(read, false);
  assert.throws(() => checkBackupText('"' + '🎵'.repeat(Math.floor(BACKUP_MAX_BYTES / 4)) + '"'), /24 MiB/);
  const before = f.map.get(STORAGE_KEY);
  await assert.rejects(handleFileImport({ size: 1, text: async () => base + ' '.repeat(BACKUP_MAX_BYTES) }, f.storage), /24 MiB/);
  assert.equal(f.map.get(STORAGE_KEY), before);
});

test("complexity guards run before JSON parsing and preserve stored data", async () => {
  const f = setup(); const valid = JSON.stringify({ app: "DHUN web", version: 3, ...emptyLibrary() });
  const deep = valid.replace('"settings":{', `"settings":{"extra":${'['.repeat(BACKUP_MAX_DEPTH)}0${']'.repeat(BACKUP_MAX_DEPTH)},`);
  const wide = valid.replace('"settings":{', `"settings":{"extra":[${'0,'.repeat(BACKUP_MAX_TOKENS)}0],`);
  for (const [text, pattern] of [[deep, /nesting depth/], [wide, /JSON tokens/]]) {
    await assert.rejects(handleFileImport(file(text), f.storage), pattern);
    assert.equal(f.map.get(STORAGE_KEY), f.original);
    assert.deepEqual(f.storage.load(), f.previous);
    assert.match(backupExportWarning(text), pattern);
  }
  assert.doesNotThrow(() => checkBackupComplexity('[' + '0,'.repeat(BACKUP_MAX_TOKENS - 2) + '0]'));
  assert.throws(() => checkBackupComplexity('[' + '0,'.repeat(BACKUP_MAX_TOKENS - 1) + '0]'), /JSON tokens/);
  assert.doesNotThrow(() => checkBackupComplexity('['.repeat(BACKUP_MAX_DEPTH) + '0' + ']'.repeat(BACKUP_MAX_DEPTH)));
  assert.throws(() => checkBackupComplexity('['.repeat(BACKUP_MAX_DEPTH + 1) + '0' + ']'.repeat(BACKUP_MAX_DEPTH + 1)), /nesting/);
  assert.doesNotThrow(() => checkBackupComplexity('["brackets [ and \\\"quoted\\\"",1]'));
});

test("quota refusal preserves durable and in-tab data and reports no success", async () => {
  const f = setup(), candidate = f.storage.exportAll(emptyLibrary()); f.control.failWrite = true;
  await assert.rejects(handleFileImport(file(candidate), f.storage), error => error.code === "IMPORT_UNCERTAIN");
  assert.equal(f.map.get(STORAGE_KEY), f.original);
  assert.deepEqual(f.storage.load(), f.previous);
});

test("export warning keeps the complete text and identifies unsupported or invalid backups", () => {
  const f = setup(); const lib = emptyLibrary();
  lib.settings.extra = 'x'.repeat(BACKUP_MAX_BYTES);
  const text = f.storage.exportAll(lib);
  assert.match(backupExportWarning(text), /cannot be restored.*24 MiB/i);
  assert.equal(JSON.parse(text).settings.extra.length, BACKUP_MAX_BYTES);
  lib.settings.extra = [Infinity];
  assert.match(backupExportWarning(f.storage.exportAll(lib), lib), /non-finite/i);
  assert.match(backupExportWarning(f.storage.exportAll({ ...lib, settings: { ...lib.settings, volume: 2 } })), /cannot be restored/i);
});

test("controller warns on an unsupported export without suppressing the download", t => {
  const app = bootFixture(t); let clicked = false;
  app.win.Blob = Blob; app.win.confirm = message => { assert.match(message, /cannot be restored.*Download the complete file/i); assert.equal(clicked, false); return true; }; app.win.URL = { createObjectURL: () => 'blob:fixture', revokeObjectURL() {} };
  const create = app.doc.createElement.bind(app.doc);
  app.doc.createElement = tag => { const el = create(tag); if (tag === 'a') el.click = () => { clicked = true; }; return el; };
  app.state.library.settings.extra = 'x'.repeat(BACKUP_MAX_BYTES);
  app.click('export-library');
  assert.equal(clicked, true);
  assert.match(app.state.toast.message, /cannot be restored.*24 MiB/i);
  assert.equal(app.state.toast.tone, 'warn');
});

test("controller reports serialization failure rather than exporting or claiming success", t => {
  const app = bootFixture(t);
  const circular = {}; circular.self = circular; app.state.library.settings.extra = circular;
  app.click('export-library');
  assert.match(app.state.toast.message, /Export failed/i);
  assert.doesNotMatch(app.state.toast.message, /Library exported/);
});

test("declining an unsupported export does not start a download", t => {
  const app = bootFixture(t); let clicked = false;
  app.win.Blob = Blob; app.win.confirm = () => false;
  const create = app.doc.createElement.bind(app.doc);
  app.doc.createElement = tag => { const el = create(tag); if (tag === 'a') el.click = () => { clicked = true; }; return el; };
  app.state.library.settings.extra = 'x'.repeat(BACKUP_MAX_BYTES);
  app.click('export-library');
  assert.equal(clicked, false);
  assert.match(app.state.toast.message, /cannot be restored/i);
});
