import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { WEB_VERSION } from "../src/js/version.js";

function worker({ failOpen = false, failPut = false } = {}) {
  const listeners = new Map();
  const shell = new Map([["./index.html", new Response("known-good shell")]]);
  const deleted = []; const fetched = [];
  const control = { status: 200, offline: false };
  const self = { location: { origin: "https://fixture.test" }, registration: { scope: "https://fixture.test/DHUN/app/" }, clients: { claim: async () => {} }, addEventListener: (name, handler) => listeners.set(name, handler) };
  const caches = {
    async open() {
      if (failOpen) throw new Error("controlled cache denial");
      return {
        match: async key => shell.get(key)?.clone(),
        async put(key, value) { if (failPut) throw new Error("controlled cache quota"); shell.set(key, value); },
      };
    },
    keys: async () => ["other-app-cache", "dhun-web-v0.0.0", `dhun-web-v${WEB_VERSION}`],
    delete: async key => { deleted.push(key); return true; },
  };
  const fetch = async request => {
    fetched.push(request.url);
    if (control.offline) throw new Error("controlled offline");
    return new Response("network shell", { status: control.status });
  };
  runInNewContext(readFileSync(new URL("../src/sw.js", import.meta.url), "utf8"), { self, caches, fetch, URL, Response });
  return {
    shell, deleted, fetched, control,
    async request(url = "https://fixture.test/DHUN/app/", mode = "navigate") {
      let response;
      listeners.get("fetch")({ request: { url, mode, method: "GET" }, respondWith(value) { response = value; } });
      return response;
    },
    async activate() { let pending; listeners.get("activate")({ waitUntil(value) { pending = value; } }); await pending; },
  };
}

test("a navigation HTTP error cannot replace the known-good offline shell", async () => {
  const runtime = worker(); runtime.control.status = 503;
  assert.equal((await runtime.request()).status, 503, "online HTTP failure is not fabricated success");
  runtime.control.offline = true;
  assert.equal(await (await runtime.request()).text(), "known-good shell");
});

test("a successful navigation refreshes the shell, not third-party music or APIs", async () => {
  const runtime = worker();
  assert.equal(await (await runtime.request()).text(), "network shell");
  runtime.control.offline = true;
  assert.equal(await (await runtime.request()).text(), "network shell");
  assert.equal(await runtime.request("https://aac.saavncdn.com/music.m4a", "cors"), undefined);
  assert.equal(await runtime.request("https://fixture.test/DHUN/app/api/songs", "cors"), undefined);
});

test("cache write refusal does not discard an otherwise usable network navigation", async () => {
  const runtime = worker({ failPut: true });
  assert.equal(await (await runtime.request()).text(), "network shell");
  assert.equal(await runtime.shell.get("./index.html").text(), "known-good shell");
});

test("unavailable Cache Storage still permits online shell assets", async () => {
  const runtime = worker({ failOpen: true });
  const response = await runtime.request("https://fixture.test/DHUN/app/js/main.js", "cors");
  assert.equal(await response.text(), "network shell");
});

test("activation retires only older DHUN shell versions, preserving unrelated caches", async () => {
  const runtime = worker(); await runtime.activate();
  assert.deepEqual(runtime.deleted, ["dhun-web-v0.0.0"]);
});
