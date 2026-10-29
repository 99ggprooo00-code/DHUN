/** Controlled PWA ownership/cleanup, not service-worker or install acceptance. */
import test from "node:test";
import assert from "node:assert/strict";
import { createPwa } from "../src/js/pwa.js";

const turn = () => new Promise((resolve) => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
class Target {
  listeners = new Map();
  addEventListener(type, fn) {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set());
    this.listeners.get(type).add(fn);
  }
  removeEventListener(type, fn) { this.listeners.get(type)?.delete(fn); }
  emit(type, event = {}) { for (const fn of this.listeners.get(type) ?? []) fn(event); }
  count() { return [...this.listeners.values()].reduce((sum, listeners) => sum + listeners.size, 0); }
}
function fixture(t, registrationPromise) {
  const win = new Target(); win.matchMedia = () => ({ matches: false });
  const registration = new Target(); registration.waiting = null; registration.installing = null;
  let registers = 0, unregisters = 0, messages = 0;
  registration.unregister = async () => { unregisters += 1; };
  const navigator = { onLine: true, serviceWorker: {
    register: () => { registers += 1; return registrationPromise ?? Promise.resolve(registration); },
    controller: { postMessage() { messages += 1; } },
  } };
  const states = [];
  const pwa = createPwa({ win, navigator, onState: (state) => states.push(state) });
  t.after(() => pwa.destroy?.());
  return { pwa, win, registration, states, get registers() { return registers; }, get unregisters() { return unregisters; }, get messages() { return messages; } };
}

test("destroy releases PWA/window listeners without uninstalling the shared worker", async (t) => {
  const f = fixture(t); f.pwa.start(); await turn();
  assert.ok(f.win.count() > 0); assert.equal(f.registration.count(), 1);
  assert.equal(typeof f.pwa.destroy, "function");
  f.pwa.destroy(); f.pwa.destroy();
  assert.equal(f.win.count(), 0); assert.equal(f.registration.count(), 0);
  assert.equal(f.unregisters, 0);
  const emitted = f.states.length;
  f.win.emit("offline"); f.registration.emit("updatefound");
  assert.equal(f.states.length, emitted);
});

test("a registration finishing after destroy cannot publish or add listeners", async (t) => {
  const pending = deferred(); const f = fixture(t, pending.promise);
  t.after(() => pending.resolve(f.registration));
  f.pwa.start();
  assert.equal(typeof f.pwa.destroy, "function"); f.pwa.destroy();
  const emitted = f.states.length;
  pending.resolve(f.registration); await turn();
  assert.equal(f.states.length, emitted); assert.equal(f.registration.count(), 0);
  assert.equal(f.pwa.registration, null);
});

test("destroy ignores a late registration rejection instead of reporting a new failure", async (t) => {
  const pending = deferred(); const f = fixture(t, pending.promise);
  t.after(() => pending.resolve(f.registration));
  f.pwa.start();
  assert.equal(typeof f.pwa.destroy, "function"); f.pwa.destroy();
  const emitted = f.states.length;
  pending.reject(new Error("controlled late rejection")); await turn();
  assert.equal(f.states.length, emitted);
});

test("replacement installing workers and destroy release old state listeners", async (t) => {
  const f = fixture(t); f.pwa.start(); await turn();
  const old = new Target(); old.state = "installing";
  f.registration.installing = old; f.registration.emit("updatefound");
  assert.equal(old.count(), 1);
  const fresh = new Target(); fresh.state = "installing";
  f.registration.installing = fresh; f.registration.emit("updatefound");
  assert.equal(old.count(), 0, "an obsolete installing worker must not stay subscribed");
  assert.equal(fresh.count(), 1);
  assert.equal(typeof f.pwa.destroy, "function"); f.pwa.destroy();
  assert.equal(fresh.count(), 0);
  const emitted = f.states.length; fresh.state = "installed"; fresh.emit("statechange");
  assert.equal(f.states.length, emitted);
});

test("a prompt choice after disposal cannot report installation to an obsolete caller", async (t) => {
  const f = fixture(t); const choice = deferred();
  t.after(() => choice.resolve({ outcome: "accepted" }));
  f.pwa.start(); await turn();
  f.win.emit("beforeinstallprompt", { preventDefault() {}, prompt: async () => {}, userChoice: choice.promise });
  const result = f.pwa.promptInstall(); await turn();
  assert.equal(typeof f.pwa.destroy, "function"); f.pwa.destroy();
  const emitted = f.states.length; choice.resolve({ outcome: "accepted" });
  assert.equal(await result, false); assert.equal(f.states.length, emitted);
});

test("start is idempotent and cannot revive a disposed PWA owner", async (t) => {
  const f = fixture(t); f.pwa.start(); f.pwa.start(); await turn();
  assert.equal(f.registers, 1); assert.equal(f.win.count(), 4);
  assert.equal(typeof f.pwa.destroy, "function"); f.pwa.destroy();
  f.pwa.start(); await turn();
  assert.equal(f.registers, 1); assert.equal(f.win.count(), 0);
});

test("a disposed PWA owner cannot post version messages to the persistent worker", async (t) => {
  const f = fixture(t); f.pwa.start(); await turn();
  f.pwa.announceVersion("unit-fixture"); assert.equal(f.messages, 1);
  assert.equal(typeof f.pwa.destroy, "function"); f.pwa.destroy();
  f.pwa.announceVersion("obsolete"); assert.equal(f.messages, 1);
  assert.equal(await f.pwa.promptInstall(), false); assert.equal(f.unregisters, 0);
});

test("late install-prompt rejection is handled without a stale state callback", async (t) => {
  const f = fixture(t); const prompt = deferred();
  f.pwa.start(); await turn();
  f.win.emit("beforeinstallprompt", { preventDefault() {}, prompt: () => prompt.promise, userChoice: Promise.resolve({ outcome: "dismissed" }) });
  const result = f.pwa.promptInstall();
  f.pwa.destroy(); const emitted = f.states.length;
  prompt.reject(new Error("controlled obsolete prompt"));
  assert.equal(await result, false);
  assert.equal(f.states.length, emitted);
});

test("both native prompt promises are observed when choice rejects before prompt completion", async (t) => {
  const f = fixture(t); const prompt = deferred(), choice = deferred();
  t.after(() => { prompt.resolve(); choice.resolve({ outcome: "dismissed" }); });
  f.pwa.start(); await turn();
  f.win.emit("beforeinstallprompt", { preventDefault() {}, prompt: () => prompt.promise, userChoice: choice.promise });
  const result = f.pwa.promptInstall();
  f.pwa.destroy();
  choice.reject(new Error("controlled cancellation before prompt completion"));
  await turn();
  prompt.resolve();
  assert.equal(await result, false);
});

test("registration resolving while the first worker installs does not report readiness", async (t) => {
  const f = fixture(t);
  f.registration.installing = new Target();
  f.registration.installing.state = "installing";
  f.pwa.start(); await turn();
  assert.equal(f.pwa.state.sw, "registering");
  assert.equal(f.pwa.state.offlineReady, false);
});

test("activation reports a worker, not unverified shell assets", async (t) => {
  const f = fixture(t); const worker = new Target(); worker.state = "installing";
  f.registration.installing = worker; f.pwa.start(); await turn();
  worker.state = "activating"; worker.emit("statechange");
  assert.equal(f.pwa.state.sw, "registering");
  f.registration.installing = null; f.registration.active = worker;
  worker.state = "activated"; worker.emit("statechange");
  assert.equal(f.pwa.state.sw, "ready");
  assert.equal(f.pwa.state.offlineReady, false, "activation does not prove all best-effort cache writes succeeded");
});

test("failed first installation and registration report failure", async (t) => {
  const f = fixture(t); const worker = new Target(); worker.state = "installing";
  f.registration.installing = worker; f.pwa.start(); await turn();
  f.registration.installing = null; worker.state = "redundant"; worker.emit("statechange");
  assert.equal(f.pwa.state.sw, "failed"); assert.equal(f.pwa.state.offlineReady, false);
  const g = fixture(t, Promise.reject(new Error("registration denied")));
  g.pwa.start(); await turn();
  assert.equal(g.pwa.state.sw, "failed"); assert.match(g.pwa.state.error, /registration denied/);
});

test("waiting update and failed replacement preserve the active worker", async (t) => {
  const f = fixture(t); const active = new Target(); active.state = "activated";
  f.registration.active = active; f.pwa.start(); await turn();
  assert.equal(f.pwa.state.sw, "ready");
  const update = new Target(); update.state = "installing";
  f.registration.installing = update; f.registration.emit("updatefound");
  assert.equal(f.pwa.state.sw, "ready");
  f.registration.installing = null; f.registration.waiting = update;
  update.state = "installed"; update.emit("statechange");
  assert.equal(f.pwa.state.sw, "update-ready"); assert.equal(f.pwa.state.updateReady, true);
  f.registration.waiting = null; update.state = "redundant"; update.emit("statechange");
  assert.equal(f.pwa.state.sw, "ready"); assert.equal(f.pwa.state.updateReady, false);
});

test("old worker events cannot replace newer lifecycle state", async (t) => {
  const f = fixture(t); const old = new Target(); old.state = "installing";
  f.registration.installing = old; f.pwa.start(); await turn();
  const fresh = new Target(); fresh.state = "installing";
  f.registration.installing = fresh; f.registration.emit("updatefound");
  old.state = "redundant"; old.emit("statechange");
  assert.equal(f.pwa.state.sw, "registering");
  f.registration.installing = null; f.registration.active = fresh;
  fresh.state = "activated"; fresh.emit("statechange");
  assert.equal(f.pwa.state.sw, "ready");
});

test("active worker activation is observed while an update is installing", async (t) => {
  const f = fixture(t); const active = new Target(); active.state = "activating";
  const update = new Target(); update.state = "installing";
  f.registration.active = active; f.registration.installing = update;
  f.pwa.start(); await turn();
  assert.equal(f.pwa.state.sw, "registering");
  active.state = "activated"; active.emit("statechange");
  assert.equal(f.pwa.state.sw, "ready");
  assert.equal(f.pwa.state.offlineReady, false);
});

test("waiting worker becomes update-ready when the active worker finishes activating", async (t) => {
  const f = fixture(t); const active = new Target(); active.state = "activating";
  const waiting = new Target(); waiting.state = "installed";
  f.registration.active = active; f.registration.waiting = waiting;
  f.pwa.start(); await turn();
  assert.equal(f.pwa.state.sw, "registering");
  active.state = "activated"; active.emit("statechange");
  assert.equal(f.pwa.state.sw, "update-ready");
  assert.equal(f.pwa.state.updateReady, true);
});

test("destroy releases listeners for both active and installing workers", async (t) => {
  const f = fixture(t); const active = new Target(); active.state = "activated";
  const update = new Target(); update.state = "installing";
  f.registration.active = active; f.registration.installing = update;
  f.pwa.start(); await turn();
  assert.equal(active.count(), 1); assert.equal(update.count(), 1);
  f.pwa.destroy();
  assert.equal(active.count(), 0); assert.equal(update.count(), 0);
  const count = f.states.length;
  active.emit("statechange"); update.emit("statechange");
  assert.equal(f.states.length, count);
});
