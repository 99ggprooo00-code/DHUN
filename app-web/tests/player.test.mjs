/**
 * Queue and transport behaviour.
 *
 * Mirrors the rules in shared/src/commonMain/kotlin/dev/dhun/player/
 * QueueManager.kt and the DhunPlayer contract: what "next" means at the end
 * of a queue, what repeat-one does, how the clock advances. No audio element
 * is involved — this is the arithmetic, not the playback.
 */

import test from "node:test";
import assert from "node:assert/strict";

import { createPlayer, REPEAT, BACKEND } from "../src/js/player.js";

/** Every player starts a timing clock; each test tears its own down, or the
 *  test runner would wait on a timer that never ends. */
function makePlayer(t) {
  const created = createPlayer();
  if (t && typeof t.after === "function") t.after(() => created.dispose());
  return created;
}

function track(id, durationMs = 60_000) {
  return { id, title: id, artist: "Sample", album: "Sample", durationMs, streamUrl: null };
}

function queueOf(count) {
  return Array.from({ length: count }, (_, i) => track(`t${i}`));
}

test("a new player is idle and honest about having no backend", async (t) => {
  const player = makePlayer(t);
  assert.equal(player.state.queue.length, 0);
  assert.equal(player.state.index, -1);
  assert.equal(player.state.playing, false);
  assert.equal(player.state.backend, BACKEND.NONE);
  assert.equal(player.currentTrack, null);
});

test("playing a track with no stream starts the labelled clock, not audio", async (t) => {
  const player = makePlayer(t);
  const tracks = queueOf(3);
  player.play(1, { queue: tracks });
  assert.equal(player.state.playing, true);
  assert.equal(player.state.index, 1);
  assert.equal(player.currentTrack.id, "t1");
  assert.equal(player.state.backend, BACKEND.TIMING, "no stream means the clock, and it says so");
});

test("pause and resume keep the position", async (t) => {
  const player = makePlayer(t);
  player.play(0, { queue: queueOf(2) });
  player.seek(12_000);
  player.pause();
  assert.equal(player.state.playing, false);
  assert.equal(player.state.positionMs, 12_000);
  player.resume();
  assert.equal(player.state.playing, true);
  assert.equal(player.state.positionMs, 12_000);
});

test("seeking is clamped to the track", async (t) => {
  const player = makePlayer(t);
  player.play(0, { queue: [track("a", 30_000)] });
  player.seek(-5_000);
  assert.equal(player.state.positionMs, 0);
  player.seek(999_000);
  assert.equal(player.state.positionMs, 30_000);
});

test("next stops at the end of the queue unless repeat-all wraps it", async (t) => {
  const player = makePlayer(t);
  player.setQueue(queueOf(3));
  player.play(2);
  player.next();
  assert.equal(player.state.playing, false, "the last row ends playback by default");
  assert.equal(player.state.positionMs, 0);

  player.setRepeat(REPEAT.ALL);
  player.play(2);
  player.next();
  assert.equal(player.state.index, 0, "repeat-all wraps to the first row");
  assert.equal(player.state.playing, true);
});

test("previous restarts the current track before it steps back", async (t) => {
  const player = makePlayer(t);
  player.play(1, { queue: queueOf(3) });
  player.seek(9_000);
  player.previous();
  assert.equal(player.state.index, 1, "past 3 s, previous restarts the track");
  assert.equal(player.state.positionMs, 0);
  player.previous();
  assert.equal(player.state.index, 0);
});

test("shuffle never plays the same row twice in a row", async (t) => {
  const player = makePlayer(t);
  player.setQueue(queueOf(8));
  player.play(3);
  player.setShuffle(true);
  for (let i = 0; i < 20; i += 1) {
    const before = player.state.index;
    player.next();
    assert.notEqual(player.state.index, before);
  }
});

test("repeat cycles off → all → one → off", async (t) => {
  const player = makePlayer(t);
  assert.equal(player.state.repeat, REPEAT.OFF);
  assert.equal(player.cycleRepeat(), REPEAT.ALL);
  assert.equal(player.cycleRepeat(), REPEAT.ONE);
  assert.equal(player.cycleRepeat(), REPEAT.OFF);
});

test("setQueue replaces the queue without starting playback", async (t) => {
  const player = makePlayer(t);
  player.setQueue(queueOf(4), 2);
  assert.equal(player.state.queue.length, 4);
  assert.equal(player.state.index, 2);
  assert.equal(player.state.playing, false);
  player.setQueue([]);
  assert.equal(player.state.index, -1);
});

test("play clamps an out-of-range index instead of throwing", async (t) => {
  const player = makePlayer(t);
  player.setQueue(queueOf(3));
  player.play(99);
  assert.equal(player.state.index, 2);
  player.play(-5);
  assert.equal(player.state.index, 0);
});

test("subscribers see every state change, and can unsubscribe", async (t) => {
  const player = makePlayer(t);
  const seen = [];
  const off = player.subscribe((state) => seen.push(state.playing));
  player.play(0, { queue: queueOf(2) });
  player.pause();
  off();
  player.resume();
  assert.deepEqual(seen, [true, false]);
});
