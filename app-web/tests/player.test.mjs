/**
 * The queue and the transport.
 *
 * The pure queue rules are tested directly; the player's integration with the
 * audio engine and the data source is tested against fakes, because the paths
 * that matter are the ones a browser makes hard to reach on demand: a stream
 * that cannot be resolved, a cached URL that expired, a demo row that has no
 * audio, and a queue edit while the last track is playing.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  enqueueTrack,
  removeAt,
  moveTo,
  shuffledOrder,
  nextIndex,
  previousIndex,
  createPlayer,
  REPEAT,
  STREAM_TTL_MS,
} from "../src/js/player.js";

const track = (id) => ({ id, videoId: id, title: `Track ${id}`, artist: "Artist", durationMs: 60_000 });

test("queue edits return new arrays and never mutate the caller's", () => {
  const queue = [track("a"), track("b")];
  const appended = enqueueTrack(queue, track("c"));
  assert.deepEqual(queue.map((entry) => entry.id), ["a", "b"], "the original array is untouched");
  assert.deepEqual(appended.map((entry) => entry.id), ["a", "b", "c"]);
  assert.deepEqual(enqueueTrack(queue, track("z"), { next: true }).map((entry) => entry.id), ["z", "a", "b"]);
  assert.deepEqual(removeAt(appended, 1).map((entry) => entry.id), ["a", "c"]);
  assert.deepEqual(moveTo(appended, 0, 2).map((entry) => entry.id), ["b", "c", "a"]);
  assert.deepEqual(removeAt(appended, 99).map((entry) => entry.id), ["a", "b", "c"], "an out-of-range removal is a no-op");
});

test("shuffle plays every track once before it repeats anything", () => {
  const order = shuffledOrder(6, 0, () => 0.5);
  assert.equal(order.length, 6);
  assert.equal(order[0], 0, "the current track stays in front");
  assert.deepEqual([...order].sort((a, b) => a - b), [0, 1, 2, 3, 4, 5], "no track is dropped or doubled");
});

test("advance follows repeat, shuffle and the end of the queue", () => {
  assert.equal(nextIndex({ length: 3, index: 0, repeat: REPEAT.OFF }), 1);
  assert.equal(nextIndex({ length: 3, index: 2, repeat: REPEAT.OFF }), -1, "the queue stops at the end by default");
  assert.equal(nextIndex({ length: 3, index: 2, repeat: REPEAT.ALL }), 0);
  assert.equal(nextIndex({ length: 3, index: 1, repeat: REPEAT.ONE }), 1, "repeat-one holds the row");
  assert.equal(nextIndex({ length: 3, index: 0, repeat: REPEAT.OFF, shuffle: true, order: [0, 2, 1] }), 2);
  assert.equal(nextIndex({ length: 3, index: 1, repeat: REPEAT.OFF, shuffle: true, order: [0, 2, 1] }), -1);
  assert.equal(nextIndex({ length: 0, index: -1, repeat: REPEAT.ALL }), -1);
});

test("previous restarts the track before it steps back", () => {
  assert.equal(previousIndex({ length: 5, index: 2, positionMs: 10_000 }), 2, "past three seconds it restarts");
  assert.equal(previousIndex({ length: 5, index: 2, positionMs: 500 }), 1);
  assert.equal(previousIndex({ length: 5, index: 0, positionMs: 100 }), 0);
  assert.equal(previousIndex({ length: 5, index: 0, positionMs: 100, repeat: REPEAT.ALL }), 4);
});

/* --------------------------------------------------------------- fakes ---- */

function fakeEngine() {
  const state = { status: "idle", positionMs: 0, durationMs: 0, bufferedMs: 0, volume: 1, muted: false, processed: false };
  let loaded = null;
  return {
    loads: [],
    get state() {
      return state;
    },
    subscribe: () => () => {},
    async load(url, { autoplay }) {
      loaded = url;
      this.loads.push(url);
      state.status = autoplay ? "playing" : "ready";
      return true;
    },
    play: async () => true,
    pause: () => {},
    seek: (ms) => {
      state.positionMs = ms;
    },
    setVolume: () => {},
    setMuted: () => {},
    setEqualizer: () => true,
    stop: () => {},
    dispose: () => {},
  };
}

function fakeSource({ fail = null, stream = null } = {}) {
  return {
    calls: [],
    async resolveStream(videoId) {
      this.calls.push(videoId);
      if (fail) return fail;
      return {
        ok: true,
        stream: stream ?? { url: `https://cdn.test/${videoId}`, mimeType: "audio/webm", itag: 251 },
        track: { videoId, title: "Resolved", durationMs: 90_000 },
      };
    },
  };
}

function fakeSettings() {
  const values = { repeat: REPEAT.OFF, shuffle: false, volume: 1 };
  return {
    get: (key) => values[key],
    set: (patch) => Object.assign(values, patch),
    subscribe: () => {},
    values,
  };
}

test("playing a track resolves its stream once and starts the engine", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  const player = createPlayer({ engine, source, settings: fakeSettings(), now: () => 1000 });
  await player.playTracks([track("a"), track("b")], 0);
  assert.equal(source.calls.length, 1);
  assert.equal(engine.loads[0], "https://cdn.test/a");
  assert.equal(player.current.id, "a");
  assert.equal(player.state.playing, true);
  assert.equal(player.cacheSize, 1);
});

test("a second visit to the same track reuses the resolved URL inside its TTL", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  let clock = 1000;
  const player = createPlayer({ engine, source, settings: fakeSettings(), now: () => clock });
  await player.playTracks([track("a")], 0);
  clock += STREAM_TTL_MS / 2;
  engine.state.status = "idle";
  await player.playTrack(track("a"));
  assert.equal(source.calls.length, 1, "a fresh URL is not requested again inside the TTL");
});

test("an expired cached URL is re-resolved rather than retried for ever", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  let clock = 1000;
  const player = createPlayer({ engine, source, settings: fakeSettings(), now: () => clock });
  await player.playTracks([track("a")], 0);
  clock += STREAM_TTL_MS + 1;
  await player.playTrack(track("a"));
  assert.equal(source.calls.length, 2);
});

test("a stream that cannot be resolved marks its own row and keeps the queue", async () => {
  const engine = fakeEngine();
  const source = fakeSource({ fail: { ok: false, kind: "network-or-cors", message: "Failed to fetch" } });
  const player = createPlayer({ engine, source, settings: fakeSettings() });
  await player.playTracks([track("a"), track("b")], 0);
  assert.equal(player.state.queue.length, 2, "the queue survives a failure");
  assert.equal(Object.keys(player.state.failures).length, 1);
  assert.equal(player.state.failures[0].kind, "network-or-cors");
  assert.equal(player.state.error.message, "Failed to fetch");
  assert.equal(player.state.playing, false);
});

test("a demo row is refused with its own explanation and never opens a stream", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  const events = [];
  const player = createPlayer({ engine, source, settings: fakeSettings(), onEvent: (event) => events.push(event) });
  await player.playTracks([{ ...track("d"), demo: true }], 0);
  assert.equal(source.calls.length, 0, "a fictional row must not cause a network request");
  assert.equal(engine.loads.length, 0);
  assert.equal(player.state.error.kind, "demo");
  assert.match(player.state.error.message, /demo row/i);
  assert.equal(events.at(-1).type, "track-failed");
});

test("a failed row can be retried explicitly, which re-resolves it", async () => {
  const engine = fakeEngine();
  const source = fakeSource({ fail: { ok: false, kind: "http", message: "HTTP 500" } });
  const player = createPlayer({ engine, source, settings: fakeSettings() });
  await player.playTracks([track("a")], 0);
  source.calls.length = 0;
  source.resolveStream = async function resolve(videoId, ...rest) {
    this.calls.push(videoId);
    return { ok: true, stream: { url: "https://cdn.test/retry", mimeType: "audio/webm" }, track: { videoId } };
  };
  await player.retry();
  assert.deepEqual(source.calls, ["a"]);
  assert.equal(engine.loads.at(-1), "https://cdn.test/retry");
});

test("removing the playing row starts the next one; removing the last stops", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  const player = createPlayer({ engine, source, settings: fakeSettings() });
  await player.playTracks([track("a"), track("b"), track("c")], 1);
  player.removeAt(1);
  assert.deepEqual(player.state.queue.map((entry) => entry.id), ["a", "c"]);
  assert.equal(player.state.index, 1, "the row that took its place is now current");
  player.removeAt(1);
  player.removeAt(0);
  assert.equal(player.state.queue.length, 0);
  assert.equal(player.state.index, -1);
  assert.equal(player.state.playing, false);
});

test("moving a row keeps the same track current", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  const player = createPlayer({ engine, source, settings: fakeSettings() });
  await player.playTracks([track("a"), track("b"), track("c")], 0);
  player.move(0, 2);
  assert.deepEqual(player.state.queue.map((entry) => entry.id), ["b", "c", "a"]);
  assert.equal(player.current.id, "a", "reordering the queue does not change what is playing");
  assert.equal(player.state.index, 2);
});

test("repeat and shuffle are written back to settings", () => {
  const settings = fakeSettings();
  const player = createPlayer({ engine: fakeEngine(), source: fakeSource(), settings });
  player.cycleRepeat();
  assert.equal(player.state.repeat, REPEAT.ALL);
  assert.equal(settings.values.repeat, REPEAT.ALL);
  player.cycleRepeat();
  assert.equal(player.state.repeat, REPEAT.ONE);
  player.cycleRepeat();
  assert.equal(player.state.repeat, REPEAT.OFF);
  player.toggleShuffle();
  assert.equal(player.state.shuffle, true);
  assert.equal(settings.values.shuffle, true);
  assert.equal(player.state.order.length, 0, "an empty queue has no shuffle order to build");
});

test("the stream cache can be inspected and cleared", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  const player = createPlayer({ engine, source, settings: fakeSettings() });
  await player.playTracks([track("a"), track("b")], 0);
  assert.equal(player.cacheSize, 1);
  player.clearStreamCache();
  assert.equal(player.cacheSize, 0);
});

test("a restored session keeps the queue and the position", async () => {
  const engine = fakeEngine();
  const source = fakeSource();
  const player = createPlayer({ engine, source, settings: fakeSettings() });
  engine.state.status = "ready";
  const restored = await player.restore({ queue: [track("a"), track("b")], index: 1, positionMs: 12_000 });
  assert.equal(restored, false, "restoring with nothing resolved yet is reported, not faked");
  assert.deepEqual(player.state.queue.map((entry) => entry.id), ["a", "b"]);
  assert.equal(player.current.id, "b");
  assert.equal(player.state.positionMs, 12_000);
});
