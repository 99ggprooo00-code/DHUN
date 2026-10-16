/**
 * The audio engine, against a fake element and a fake Web Audio context.
 *
 * These are the tests that could not exist before: the previous build had no
 * audio path to test. Every browser-specific failure the engine claims to handle
 * — a refused autoplay, a cross-origin stream the graph cannot touch, a media
 * error with no message, a position that arrives from `timeupdate` — is exercised
 * here by driving the fake element the way a browser would.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  AUDIO_STATUS,
  describeMediaError,
  classifyPlayRejection,
  createEqualizerGraph,
  dbToGain,
  clampDb,
  createAudioEngine,
} from "../src/js/audio.js";

/** A stand-in for HTMLAudioElement: events can be fired, play() can be refused. */
function fakeAudio({ failWith = null, duration = 240, autoPlay = true } = {}) {
  const listeners = new Map();
  const element = {
    crossOrigin: null,
    preload: null,
    src: "",
    volume: 1,
    muted: false,
    playbackRate: 1,
    currentTime: 0,
    duration,
    readyState: 1,
    buffered: { length: 1, end: () => 12 },
    error: null,
    paused: true,
    attributes: new Set(),
    setAttribute: (name) => element.attributes.add(name),
    removeAttribute: (name) => element.attributes.delete(name),
    load: () => {},
    addEventListener: (name, handler) => {
      if (!listeners.has(name)) listeners.set(name, []);
      listeners.get(name).push(handler);
    },
    fire(name) {
      for (const handler of listeners.get(name) ?? []) handler({ type: name });
    },
    async play() {
      if (failWith) throw failWith;
      element.paused = false;
      element.fire("playing");
    },
    pause() {
      element.paused = true;
      element.fire("pause");
    },
  };
  if (!autoPlay) element.play = async () => undefined;
  return element;
}

/** The smallest Web Audio context that `createEqualizerGraph` uses. */
function fakeContext() {
  const created = { filters: 0, gains: 0, compressors: 0 };
  const node = () => ({
    connect: () => node(),
    disconnect: () => {},
    gain: { value: 0 },
  });
  return {
    created,
    state: "running",
    destination: node(),
    resume: async () => {},
    close: async () => {},
    createMediaElementSource: () => ({ connect: () => {}, disconnect: () => {} }),
    createAnalyser: () => ({ fftSize: 0, frequencyBinCount: 8, getByteFrequencyData: () => {} }),
    createBiquadFilter: () => {
      created.filters += 1;
      return { type: "", frequency: { value: 0 }, Q: { value: 0 }, gain: { value: 0 }, connect: () => {}, disconnect: () => {} };
    },
    createGain: () => {
      created.gains += 1;
      return { gain: { value: 0 }, connect: () => {}, disconnect: () => {} };
    },
    createDynamicsCompressor: () => {
      created.compressors += 1;
      return {
        threshold: { value: 0 },
        ratio: { value: 1 },
        knee: { value: 0 },
        attack: { value: 0 },
        release: { value: 0 },
        connect: () => {},
        disconnect: () => {},
      };
    },
  };
}

test("every MediaError code becomes a sentence a person can act on", () => {
  for (const code of [1, 2, 3, 4, 99, undefined]) {
    const described = describeMediaError(code);
    assert.ok(described.message.length > 10, `code ${code} has no message`);
    assert.ok(described.kind);
  }
  assert.equal(describeMediaError(4).kind, "unsupported");
  assert.match(describeMediaError(2).message, /network dropped|expired/);
  assert.match(describeMediaError(4).message, /expired/i);
});

test("a refused autoplay is a state, not an error", () => {
  const blocked = classifyPlayRejection(Object.assign(new Error("x"), { name: "NotAllowedError" }));
  assert.equal(blocked.kind, "autoplay-blocked");
  assert.match(blocked.message, /interact with the page/);
  assert.equal(classifyPlayRejection(new Error("boom")).kind, "play");
});

test("dB conversion and clamping match the app's own range", () => {
  assert.equal(Math.round(dbToGain(0) * 100) / 100, 1);
  assert.equal(Math.round(dbToGain(20)), 10);
  assert.equal(clampDb(40), 20);
  assert.equal(clampDb(-40), -20);
  assert.equal(clampDb("nonsense"), 0);
});

test("the equaliser graph builds one filter per band and applies a preset", () => {
  const context = fakeContext();
  const graph = createEqualizerGraph({
    context,
    source: { connect: () => {} },
    bandsHz: [60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000],
    gainsDb: [],
  });
  assert.equal(graph.filters.length, 10);
  graph.apply({ gains: [6, 3, 0, -3, -6, 0, 3, 6, 9, 12], preamp: -2, compressor: true });
  assert.equal(graph.filters[0].gain.value, 6);
  assert.equal(graph.filters[9].gain.value, 12);
  assert.ok(Math.abs(graph.preamp.gain.value - dbToGain(-2)) < 1e-9);
  assert.equal(graph.loudness.threshold.value, -24, "the compressor engages through one stable graph");
  graph.apply({ gains: graph.filters.map(() => 0), preamp: 0, compressor: false });
  assert.equal(graph.loudness.ratio.value, 1);
});

test("loading a stream reports LOADING and then PLAYING", async () => {
  const element = fakeAudio();
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null });
  const seen = [];
  engine.subscribe((state) => seen.push(state.status));
  await engine.load("https://cdn.test/a", { autoplay: true });
  assert.equal(engine.state.status, AUDIO_STATUS.PLAYING);
  assert.ok(seen.includes(AUDIO_STATUS.LOADING));
  assert.equal(element.crossOrigin, "anonymous", "CORS is requested first so the EQ can process the bytes");
  assert.ok(element.attributes.has("playsinline"), "iOS needs the inline opt-in to start a stream");
});

test("a refused autoplay becomes the BLOCKED state and asks for a gesture", async () => {
  const element = fakeAudio({ failWith: Object.assign(new Error("no"), { name: "NotAllowedError" }) });
  let gestured = 0;
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null, onNeedsGesture: () => (gestured += 1) });
  const started = await engine.load("https://cdn.test/a", { autoplay: true });
  assert.equal(started, false);
  assert.equal(engine.state.status, AUDIO_STATUS.BLOCKED);
  assert.equal(gestured, 1);
  assert.equal(engine.state.error.kind, "autoplay-blocked");
});

test("a media error is classified from the element's own code", async () => {
  const element = fakeAudio();
  element.error = { code: 4 };
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null });
  await engine.load("https://cdn.test/a", { autoplay: false });
  element.fire("error");
  assert.equal(engine.state.status, AUDIO_STATUS.ERROR);
  assert.equal(engine.state.error.kind, "unsupported");
});

test("unprocessed loading drops the CORS request and says processing is off", async () => {
  const element = fakeAudio();
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null });
  await engine.load("https://cdn.test/a", { autoplay: false, allowUnprocessed: true });
  assert.equal(element.crossOrigin, null);
  assert.equal(engine.state.processable, false);
});

test("buffering is a state, and it clears when the stream catches up", async () => {
  const element = fakeAudio();
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null });
  await engine.load("https://cdn.test/a", { autoplay: true });
  element.fire("waiting");
  assert.equal(engine.state.status, AUDIO_STATUS.BUFFERING);
  element.fire("canplay");
  assert.equal(engine.state.status, AUDIO_STATUS.PLAYING);
});

test("progress and buffered ranges are reported separately", async () => {
  const element = fakeAudio({ duration: 100 });
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null });
  await engine.load("https://cdn.test/a", { autoplay: false });
  element.currentTime = 30;
  element.buffered = { length: 1, end: () => 55 };
  element.fire("timeupdate");
  assert.equal(engine.state.positionMs, 30_000);
  assert.equal(engine.state.bufferedMs, 55_000);
});

test("a missing or empty stream URL is a named refusal, never a silent no-op", async () => {
  const engine = createAudioEngine({ audio: fakeAudio(), AudioContextCtor: null });
  assert.equal(await engine.load("", {}), false);
  assert.equal(engine.state.error.kind, "nostream");
});

test("seeking clamps to the track and pushes the element's time", async () => {
  const element = fakeAudio({ duration: 60 });
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null });
  await engine.load("https://cdn.test/a", { autoplay: false });
  element.fire("loadedmetadata");
  engine.seek(30_000);
  assert.equal(element.currentTime, 30);
  engine.seek(-5);
  assert.equal(element.currentTime, 0);
  engine.seek(999_000);
  assert.equal(element.currentTime, 60, "a seek past the end lands at the end");
});

test("volume and mute reach the element and the state", () => {
  const element = fakeAudio();
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null });
  engine.setVolume(0.25);
  assert.equal(element.volume, 0.25);
  engine.setVolume(9);
  assert.equal(engine.state.volume, 1);
  engine.setMuted(true);
  assert.equal(element.muted, true);
  assert.equal(engine.state.muted, true);
});

test("the equaliser attaches its graph once and reports whether processing is live", async () => {
  const element = fakeAudio();
  const context = fakeContext();
  const engine = createAudioEngine({
    audio: element,
    AudioContextCtor: function AudioContext() {
      return context;
    },
    bandsHz: [60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000],
  });
  await engine.load("https://cdn.test/a", { autoplay: false });
  assert.equal(engine.setEqualizer({ enabled: true, gains: Array(10).fill(4), preamp: 0 }), true);
  assert.equal(context.created.filters, 10);
  assert.equal(engine.state.processed, true);
  assert.equal(engine.canProcess, true);
  assert.equal(engine.spectrum().length, 8, "the analyser is exposed for the visualiser");
});

test("an engine with no audio element at all reports it instead of crashing", async () => {
  const engine = createAudioEngine({ audio: null, AudioContextCtor: null });
  assert.equal(await engine.load("https://cdn.test/a", {}), false);
  assert.equal(engine.state.error.kind, "unsupported");
});

test("ending a track fires the callback exactly once", async () => {
  let ended = 0;
  const element = fakeAudio();
  const engine = createAudioEngine({ audio: element, AudioContextCtor: null, onEnded: () => (ended += 1) });
  await engine.load("https://cdn.test/a", { autoplay: true });
  element.fire("ended");
  assert.equal(ended, 1);
  assert.equal(engine.state.status, AUDIO_STATUS.ENDED);
});
