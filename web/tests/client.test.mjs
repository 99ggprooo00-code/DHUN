#!/usr/bin/env node
/* ===========================================================================
   DHUN Web — client test harness.

   Runs the real assets/dhun.js inside a jsdom document built from the real
   index.html, with a stub <audio> element, and asserts on the app's own
   state and DOM output. This is not a re-implementation of the client: it
   imports the shipped module and drives it.

   Bridge-dependent cases talk to a live bridge over HTTP. They skip (rather
   than fail) when nothing is listening, so the suite runs offline.

   Setup (dev only — jsdom is not a repo dependency):
       npm install --no-save jsdom
       python3 web/tools/make-demo-audio.py      # demo audio for the player cases
       node web/tools/mock-bridge.mjs &          # optional, for bridge cases
       node --test web/tests/
   =========================================================================== */

import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { JSDOM } from "jsdom";

const here = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.join(here, "..");
const BRIDGE = process.env.DHUN_TEST_BRIDGE ?? "http://localhost:8787";

/* ------------------------------------------------------- DOM environment -- */

const html = fs.readFileSync(path.join(webRoot, "index.html"), "utf8");
const dom = new JSDOM(html, {
  url: "http://localhost:8080/",
  pretendToBeVisual: true,
});

const consoleErrors = [];

/* The stub extends Node's EventTarget, so its events must be Node's Event —
   jsdom's Event class is a different realm and Node's dispatchEvent rejects
   it with ERR_INVALID_ARG_TYPE. */
const NodeEvent = globalThis.Event;

/** Minimal <audio> stand-in: jsdom has no HTMLMediaElement implementation. */
class AudioStub extends EventTarget {
  constructor() {
    super();
    this.src = "";
    this.volume = 1;
    this.currentTime = 0;
    this.duration = NaN;
    this.paused = true;
    this.preload = "";
    this.crossOrigin = null;
    this.playCalls = 0;
    this.pauseCalls = 0;
  }

  async play() {
    this.playCalls += 1;
    this.paused = false;
    this.dispatchEvent(new NodeEvent("play"));
  }

  pause() {
    this.pauseCalls += 1;
    this.paused = true;
    this.dispatchEvent(new NodeEvent("pause"));
  }

  removeAttribute(name) {
    if (name === "src") this.src = "";
  }
}

globalThis.window = dom.window;
globalThis.document = dom.window.document;
// Node 22 exposes `navigator` as a getter-only global, so it needs redefining
// rather than plain assignment.
Object.defineProperty(globalThis, "navigator", {
  value: dom.window.navigator,
  configurable: true,
  writable: true,
});
globalThis.localStorage = dom.window.localStorage;
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.Audio = AudioStub;
globalThis.MediaMetadata = class MediaMetadata {
  constructor(init) { Object.assign(this, init); }
};

const realConsoleError = console.error;
console.error = (...args) => { consoleErrors.push(args.join(" ")); realConsoleError(...args); };

/* Import the shipped client module. boot() runs on import. */
const client = await import(pathToFileURL(path.join(webRoot, "assets", "dhun.js")).href);
const { state, audio, formatTime, artworkFor } = client;

/* ------------------------------------------------------------- helpers ---- */

async function bridgeAvailable() {
  try {
    const response = await fetch(`${BRIDGE}/health`, { signal: AbortSignal.timeout(1500) });
    return response.ok && (await response.json()).ok === true;
  } catch {
    return false;
  }
}

function trackRows() {
  return [...dom.window.document.querySelectorAll("#results .track-row")];
}

function queueRows() {
  return [...dom.window.document.querySelectorAll("#queueList .track-row")];
}

/* --------------------------------------------------------------- tests ---- */

test("boots against the real index.html with no missing element ids", () => {
  const missing = consoleErrors.filter((line) => line.includes("missing element ids"));
  assert.deepEqual(missing, [], `dhun.js reported missing ids: ${missing.join("; ")}`);
});

test("demo mode loads the generated catalogue", () => {
  assert.equal(state.demoMode, true);
  assert.equal(state.results.length, 3, "expected 3 demo tracks");
  const rows = trackRows();
  assert.ok(rows.length >= 3, `expected demo rows rendered, got ${rows.length}`);
  assert.match(rows[0].textContent, /Signal Drift/);
});

test("formatTime formats mm:ss and guards bad input", () => {
  assert.equal(formatTime(0), "0:00");
  assert.equal(formatTime(9), "0:09");
  assert.equal(formatTime(65), "1:05");
  assert.equal(formatTime(600), "10:00");
  assert.equal(formatTime(NaN), "--:--");
  assert.equal(formatTime(-5), "--:--");
});

test("artworkFor falls back to a generated tile and passes through real URLs", () => {
  const generated = artworkFor({ id: "abc", title: "Nightbus" });
  assert.match(generated, /^data:image\/svg\+xml;utf8,/);
  assert.ok(!generated.includes('"'), "data URI must be quote-safe for CSS url()");
  const real = "https://example.com/art.jpg";
  assert.equal(artworkFor({ id: "x", thumbnailUrl: real }), real);
});

test("clicking a demo track starts playback from the local demo file", async () => {
  client.clearQueue();
  const row = trackRows()[1];
  assert.ok(row, "expected a second demo row");
  row.dispatchEvent(new dom.window.MouseEvent("click", { bubbles: true }));
  await new Promise((resolve) => setTimeout(resolve, 20));

  assert.equal(state.isPlaying, true, "player should be playing");
  assert.match(audio.src, /demo-2\.wav$/, `expected demo file src, got ${audio.src}`);
  assert.ok(audio.src.startsWith("http://localhost:8080/"), "demo src must be same-origin");
  assert.equal(state.queue.length, 3, "playing a result should queue the result set");
  assert.equal(state.currentIndex, 1);
});

test("queue add and remove keep currentIndex consistent", async () => {
  client.clearQueue();
  client.addToQueue(state.results[0]);
  client.addToQueue(state.results[1]);
  client.addToQueue(state.results[2]);
  assert.equal(state.queue.length, 3);
  assert.equal(queueRows().length, 3, "queue panel should render 3 rows");

  await client.playIndex(1, { autoplay: false });
  assert.equal(state.currentIndex, 1);

  client.removeFromQueue(0);
  assert.equal(state.currentIndex, 0, "removing an earlier track shifts the index down");
  assert.equal(state.queue.length, 2);

  client.clearQueue();
  assert.equal(state.queue.length, 0);
  assert.equal(state.currentIndex, -1);
});

test("bridge cases", { skip: (await bridgeAvailable()) ? false : "no bridge on " + BRIDGE }, async () => {
  state.bridgeUrl = BRIDGE;

  const connected = await client.checkBridge();
  assert.equal(connected, true, "health check should succeed against the mock bridge");
  assert.equal(state.bridgeStatus, "ok");

  await client.runSearch("orbit");
  assert.equal(state.searching, false);
  assert.equal(state.results.length, 1, `expected 1 match, got ${state.results.length}`);
  assert.equal(state.results[0].title, "Low Orbit");
  assert.match(trackRows()[0].textContent, /Low Orbit/);

  client.clearQueue();
  client.addToQueue(state.results[0]);
  await client.playIndex(0, { autoplay: false });
  assert.match(audio.src, /\/audio\/demo-2$/,
    `bridge tracks must play via the byte proxy, got ${audio.src}`);

  await client.runSearch("nothing-matches-this");
  assert.equal(state.searchError, "empty");
});

test("search without a bridge surfaces the explanatory state card", async () => {
  client.clearQueue();
  state.bridgeUrl = "";
  state.demoMode = false;
  await client.runSearch("anything");
  assert.equal(state.searchError, "no-bridge");
  const card = dom.window.document.querySelector("#results .state-card");
  assert.ok(card, "expected a state card");
  assert.match(card.textContent, /No bridge connected/);
  assert.match(card.textContent, /User-Agent/,
    "the card should explain the googlevideo User-Agent binding");
});
