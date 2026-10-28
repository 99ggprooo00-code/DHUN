/** Controlled fetch/AbortSignal regressions: no live provider or media calls. */
import test from "node:test";
import assert from "node:assert/strict";
import { API_SOURCES, FAILURE_KIND, SourceError, createSource } from "../src/js/api.js";

const turn = () => new Promise((resolve) => setImmediate(resolve));
const isCancellation = (error) => error instanceof SourceError && error.kind === FAILURE_KIND.timeout;
const observe = (promise) => promise.then(
  (value) => ({ state: "fulfilled", value }),
  (error) => ({ state: "rejected", error }),
);

function controlledFetch({ primary = "fail", primaryBudget = 1000, respectAbort = true } = {}) {
  const calls = [];
  let released = false;
  const response = (url) => {
    const query = new URL(url).searchParams.get("query") ?? "fixture";
    return { ok: true, status: 200, json: async () => ({ success: true, data: { results: [{ id: query, name: query, image: [], downloadUrl: [] }] } }) };
  };
  const fetchImpl = (url, { signal }) => {
    const call = { url, signal, settled: false };
    calls.push(call);
    if (url.startsWith(API_SOURCES[0].origin) && primary === "fail") {
      call.settled = true;
      return Promise.reject(new TypeError("controlled initial-host failure"));
    }
    if (released) {
      call.settled = true;
      return Promise.resolve(response(url));
    }
    return new Promise((resolve, reject) => {
      const finish = (callback, value) => {
        if (call.settled) return;
        call.settled = true;
        signal.removeEventListener("abort", abort);
        callback(value);
      };
      const abort = () => {
        if (respectAbort) finish(reject, Object.assign(new Error("controlled abort"), { name: "AbortError" }));
      };
      call.succeed = () => finish(resolve, response(url));
      signal.addEventListener("abort", abort, { once: true });
      if (signal.aborted) abort();
    });
  };
  const source = createSource({ fetchImpl, timeoutMs: primaryBudget });
  return {
    source, calls,
    probes: () => calls.filter((call) => !call.url.startsWith(API_SOURCES[0].origin)),
    release() {
      released = true;
      for (const call of calls) call.succeed?.();
    },
  };
}

function track(t, fixture, ...promises) {
  const results = promises.map(observe);
  t.after(async () => {
    fixture.release();
    await Promise.all(results);
  });
  return results;
}

test("an already-cancelled source call never starts a primary or failover request", async () => {
  const controller = new AbortController(); controller.abort();
  let calls = 0;
  const source = createSource({ fetchImpl: async () => {
    calls += 1;
    return { ok: true, json: async () => ({ data: { results: [] } }) };
  } });
  await assert.rejects(source.search("songs", "fixture", 0, { signal: controller.signal }), isCancellation);
  assert.equal(calls, 0);
});

test("cancelling the primary request rejects without contacting fallback hosts", async (t) => {
  const fixture = controlledFetch({ primary: "pending" });
  const controller = new AbortController();
  const [result] = track(t, fixture, fixture.source.search("songs", "fixture", 0, { signal: controller.signal }));
  controller.abort();
  await turn();
  assert.equal(fixture.probes().length, 0, "cancellation is not an instance outage");
  const outcome = await result;
  assert.equal(outcome.state, "rejected");
  assert.ok(isCancellation(outcome.error));
});

test("cancelling a shared-race waiter is immediate and leaves the initiator alive", async (t) => {
  const fixture = controlledFetch();
  const controller = new AbortController();
  let waiterOutcome;
  const [initiator, waiter] = track(t, fixture,
    fixture.source.search("songs", "first"),
    fixture.source.search("songs", "second", 0, { signal: controller.signal }),
  );
  waiter.then((outcome) => { waiterOutcome = outcome; });
  await turn();
  assert.equal(fixture.probes().length, 2);
  controller.abort();
  await turn();
  assert.equal(waiterOutcome?.state, "rejected", "the waiter must not wait for the shared network deadline");
  assert.ok(isCancellation(waiterOutcome.error));
  assert.ok(fixture.probes().every((call) => !call.signal.aborted));
  fixture.release();
  assert.equal((await initiator).state, "fulfilled");
});

test("cancelling the race initiator cannot abort another caller's shared probes", async (t) => {
  const fixture = controlledFetch();
  const controller = new AbortController();
  let initiatorOutcome;
  const [initiator, waiter] = track(t, fixture,
    fixture.source.search("songs", "first", 0, { signal: controller.signal }),
    fixture.source.search("songs", "second"),
  );
  initiator.then((outcome) => { initiatorOutcome = outcome; });
  await turn();
  assert.equal(fixture.probes().length, 2);
  const probes = fixture.probes().slice();
  controller.abort();
  await turn();
  assert.equal(initiatorOutcome?.state, "rejected");
  assert.ok(isCancellation(initiatorOutcome.error));
  assert.ok(probes.every((call) => !call.signal.aborted), "the other caller still owns an interest in this race");
  fixture.release();
  const outcome = await waiter;
  assert.equal(outcome.state, "fulfilled");
  assert.equal(outcome.value[0].id, "second", "a waiter must not get the initiating path's data");
});

test("when every race consumer cancels, all probes stop and no winner is adopted", async (t) => {
  const fixture = controlledFetch();
  const a = new AbortController(), b = new AbortController();
  const results = track(t, fixture,
    fixture.source.search("songs", "first", 0, { signal: a.signal }),
    fixture.source.search("songs", "second", 0, { signal: b.signal }),
  );
  await turn();
  const probes = fixture.probes().slice();
  assert.equal(probes.length, 2);
  a.abort(); b.abort();
  await turn();
  assert.ok(probes.every((call) => call.signal.aborted));
  assert.equal(fixture.probes().length, 2, "cancelled consumers must not start another race");
  fixture.release();
  for (const result of results) {
    const outcome = await result;
    assert.equal(outcome.state, "rejected");
    assert.ok(isCancellation(outcome.error));
  }
  assert.equal(fixture.source.status().origin.id, API_SOURCES[0].id);
});

test("late completion after the last consumer cancels cannot select an origin", async (t) => {
  const fixture = controlledFetch();
  const controller = new AbortController();
  const [result] = track(t, fixture, fixture.source.search("songs", "first", 0, { signal: controller.signal }));
  await turn();
  controller.abort();
  fixture.release();
  const outcome = await result;
  assert.equal(outcome.state, "rejected");
  assert.ok(isCancellation(outcome.error));
  await turn();
  assert.equal(fixture.source.status().origin.id, API_SOURCES[0].id);
});

test("a fresh caller can start a new race while an abandoned race cleans up", async (t) => {
  // Deliberately delay the old fetch's abort acknowledgement so its finally
  // runs AFTER a replacement race exists, rather than accidentally before it.
  const fixture = controlledFetch({ respectAbort: false });
  const controller = new AbortController();
  const [old] = track(t, fixture, fixture.source.search("songs", "old", 0, { signal: controller.signal }));
  await turn();
  const abandoned = fixture.probes().slice();
  controller.abort();
  const [fresh] = track(t, fixture, fixture.source.search("songs", "fresh"));
  await turn();
  assert.equal(fixture.probes().length, 4);
  for (const call of abandoned) call.succeed();
  await turn();
  assert.equal(fixture.source.status().origin.id, API_SOURCES[0].id, "old responses must not adopt an origin");
  const [joined] = track(t, fixture, fixture.source.search("songs", "joined"));
  await turn();
  const live = fixture.probes().filter((call) => !call.signal.aborted);
  assert.equal(live.length, 2, "old finalizers must not clear or cancel the replacement shared race");
  fixture.release();
  assert.equal((await old).state, "rejected");
  assert.equal((await fresh).value[0].id, "fresh");
  assert.equal((await joined).value[0].id, "joined");
});

test("a network timeout may still fail over when the caller has not cancelled", async (t) => {
  let primaryAborted = false;
  const source = createSource({ timeoutMs: 10, fetchImpl: (url, { signal }) => {
    if (!url.startsWith(API_SOURCES[0].origin)) return Promise.resolve({ ok: true, json: async () => ({ data: { results: [] } }) });
    return new Promise((resolve, reject) => signal.addEventListener("abort", () => {
      primaryAborted = true;
      reject(Object.assign(new Error("controlled deadline"), { name: "AbortError" }));
    }, { once: true }));
  } });
  assert.deepEqual(await source.search("songs", "fixture"), []);
  assert.ok(primaryAborted);
  assert.equal(source.status().origin.id, API_SOURCES[1].id);
});

function watchedController() {
  const controller = new AbortController();
  const listeners = new Set();
  const add = controller.signal.addEventListener.bind(controller.signal);
  const remove = controller.signal.removeEventListener.bind(controller.signal);
  controller.signal.addEventListener = (type, listener, options) => {
    if (type === "abort") listeners.add(listener);
    return add(type, listener, options);
  };
  controller.signal.removeEventListener = (type, listener, options) => {
    if (type === "abort") listeners.delete(listener);
    return remove(type, listener, options);
  };
  return { controller, listeners };
}

test("shared consumer abort listeners are released after cancellation and success", async (t) => {
  const fixture = controlledFetch();
  const a = watchedController(), b = watchedController();
  const [first, second] = track(t, fixture,
    fixture.source.search("songs", "first", 0, { signal: a.controller.signal }),
    fixture.source.search("songs", "second", 0, { signal: b.controller.signal }),
  );
  await turn();
  assert.equal(a.listeners.size, 1);
  assert.equal(b.listeners.size, 1);
  a.controller.abort();
  assert.equal(a.listeners.size, 0);
  fixture.release();
  assert.equal((await first).state, "rejected");
  assert.equal((await second).state, "fulfilled");
  assert.equal(b.listeners.size, 0);
});

test("artist cancellation is not swallowed as missing optional songs and albums", async (t) => {
  const controller = new AbortController();
  const pending = [];
  const source = createSource({ fetchImpl: (url, { signal }) => {
    if (new URL(url).pathname === "/api/artists/fixture") {
      return Promise.resolve({ ok: true, json: async () => ({ data: { id: "fixture", name: "Fixture", image: [] } }) });
    }
    return new Promise((resolve, reject) => {
      const abort = () => reject(Object.assign(new Error("controlled abort"), { name: "AbortError" }));
      signal.addEventListener("abort", abort, { once: true });
      pending.push(() => resolve({ ok: true, json: async () => ({ data: [] }) }));
    });
  } });
  const result = observe(source.artist("fixture", { signal: controller.signal }));
  t.after(async () => { for (const finish of pending) finish(); await result; });
  await turn();
  controller.abort();
  const outcome = await result;
  assert.equal(outcome.state, "rejected", "the profile arrived first, but the cancelled aggregate is not success");
  assert.ok(isCancellation(outcome.error));
});

test("ordinary optional artist-list failures still keep a valid profile", async () => {
  const source = createSource({ fetchImpl: async (url) => {
    if (new URL(url).pathname === "/api/artists/fixture") {
      return { ok: true, json: async () => ({ data: { id: "fixture", name: "Fixture", image: [] } }) };
    }
    throw new TypeError("controlled optional endpoint failure");
  } });
  const result = await source.artist("fixture");
  assert.equal(result.artist.id, "fixture");
  assert.deepEqual(result.songs, []);
  assert.deepEqual(result.albums, []);
});
