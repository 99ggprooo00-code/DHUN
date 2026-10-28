/**
 * The queue and the transport.
 *
 * The pure queue rules are tested directly; the player's integration with the
 * audio element and the catalogue is tested against fakes, because the paths
 * that matter are the ones a browser makes hard to reach on demand: a stream
 * that cannot be resolved, a saved row with no URL, a demo row, a rejected
 * autoplay, and a queue edit while the last track is playing.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  REPEAT,
  STATUS,
  attachMediaSession,
  createPlayer,
  currentTrack,
  emptyPlayerState,
  nextIndex,
  playRejection,
  previousIndex,
  shuffledOrder,
} from "../src/js/player.js";
import { FAILURE_KIND, SourceError } from "../src/js/api.js";

const track = (id, extra = {}) => ({
  id,
  title: `Track ${id}`,
  artist: "Naya Koirala",
  duration: 200,
  stream: `https://a.saavncdn.com/${id}.m4a`,
  ...extra,
});

/** A stand-in for `<audio>`: records what the player asked it to do. */
function fakeAudio() {
  const listeners = new Map();
  const element = {
    src: "",
    currentTime: 0,
    duration: 0,
    volume: 1,
    muted: false,
    paused: true,
    error: null,
    setAttribute() {},
    removeAttribute(name) {
      if (name === "src") element.src = "";
    },
    load() {},
    pause() {
      element.paused = true;
    },
    play() {
      element.paused = false;
      return Promise.resolve();
    },
    addEventListener(name, handler) {
      if (!listeners.has(name)) listeners.set(name, new Set());
      listeners.get(name).add(handler);
    },
    removeEventListener(name, handler) {
      listeners.get(name)?.delete(handler);
    },
    emit(name) {
      for (const handler of listeners.get(name) ?? []) handler();
    },
  };
  return element;
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

test("a fresh player is idle, silent and empty", () => {
  const state = emptyPlayerState();
  assert.equal(state.status, STATUS.idle);
  assert.deepEqual(state.queue, []);
  assert.equal(state.index, -1);
  assert.equal(currentTrack(state), null);
  assert.deepEqual([...REPEAT], ["off", "all", "one"]);
});

test("shuffle keeps the current track first and plays every track once", () => {
  const order = shuffledOrder(6, 0, () => 0.5);
  assert.equal(order.length, 6);
  assert.equal(order[0], 0, "the track already playing stays first");
  assert.deepEqual([...order].sort((a, b) => a - b), [0, 1, 2, 3, 4, 5], "no track dropped or doubled");
});

test("next follows repeat, shuffle and the end of the queue", () => {
  assert.equal(nextIndex({ queue: [1, 2, 3], index: 0, repeat: "off" }), 1);
  assert.equal(nextIndex({ queue: [1, 2, 3], index: 2, repeat: "off" }), -1, "the queue stops at the end");
  assert.equal(nextIndex({ queue: [1, 2, 3], index: 2, repeat: "all" }), 0);
  assert.equal(nextIndex({ queue: [1, 2, 3], index: 1, repeat: "one" }), 1, "repeat-one holds the row");
  assert.equal(nextIndex({ queue: [1, 2, 3], index: 0, repeat: "off", shuffle: true, order: [0, 2, 1] }), 2);
  assert.equal(nextIndex({ queue: [], index: -1, repeat: "all" }), -1);
});

test("previous restarts the track before it steps back", () => {
  assert.equal(previousIndex({ queue: [1, 2, 3], index: 1, position: 10 }), 1, "past three seconds it restarts");
  assert.equal(previousIndex({ queue: [1, 2, 3], index: 1, position: 1 }), 0);
  assert.equal(previousIndex({ queue: [1, 2, 3], index: 0, position: 1 }), 0);
  assert.equal(previousIndex({ queue: [1, 2, 3], index: 0, position: 1, repeat: "all" }), 2);
});

test("a rejected play is classified, not swallowed", () => {
  const blocked = playRejection(Object.assign(new Error("x"), { name: "NotAllowedError" }));
  assert.equal(blocked.status, STATUS.blocked, "an autoplay refusal is its own state, not an error");
  const unsupported = playRejection(Object.assign(new Error("x"), { name: "NotSupportedError" }));
  assert.equal(unsupported.status, STATUS.error);
  assert.ok(unsupported.title.length > 4);
  assert.equal(playRejection(Object.assign(new Error("x"), { name: "AbortError" })), null, "an interrupted load is not an error");
});

test("playing a queue loads the first track and reports playing only when the element agrees", async () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio });
  player.play([track("a"), track("b")], 0, "Album");
  await settle();
  assert.equal(player.state.queue.length, 2);
  assert.equal(player.state.index, 0);
  assert.equal(audio.src, "https://a.saavncdn.com/a.m4a");
  // `play()` resolved, so the promise path sets playing.
  assert.equal(player.state.status, STATUS.playing);
});

test("a stream that must be resolved is fetched before the element sees it", async () => {
  const audio = fakeAudio();
  const asked = [];
  const player = createPlayer({
    audio,
    resolve: async (song) => {
      asked.push(song.id);
      return { ...song, stream: `https://fresh/${song.id}.m4a`, streamQuality: "320kbps" };
    },
  });
  player.play([{ id: "saved", title: "Saved", artist: "A", duration: 100, stream: "" }], 0);
  await settle();
  assert.deepEqual(asked, ["saved"]);
  assert.equal(audio.src, "https://fresh/saved.m4a");
  assert.equal(player.state.queue[0].streamQuality, "320kbps", "the hydrated row replaces the stored one");
});

test("a saved row with no URL says why it cannot play", async () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio, resolve: async (song) => song });
  player.play([{ id: "s1", title: "Stored", artist: "A", duration: 100, stream: "" }], 0);
  await settle();
  assert.equal(player.state.status, STATUS.error);
  assert.match(player.state.error.title, /no audio URL/i);
  assert.equal(audio.src, "", "nothing was loaded");
});

test("a demo row says it is a sample rather than pretending to play", async () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio, resolve: async (song) => song });
  player.play([{ id: "demo-tr-1", title: "Made up", artist: "A", duration: 100, stream: "", demo: true }], 0);
  await settle();
  assert.equal(player.state.status, STATUS.error);
  assert.match(player.state.error.title, /sample/i);
  assert.equal(audio.src, "");
});

test("a resolve failure keeps the source's own taxonomy", async () => {
  const audio = fakeAudio();
  const player = createPlayer({
    audio,
    resolve: async () => {
      throw new SourceError(FAILURE_KIND.http, "gone", { status: 404, url: "https://saavn.dev/api/songs/x" });
    },
  });
  player.play([{ id: "s1", title: "T", artist: "A", duration: 1, stream: "" }], 0);
  await settle();
  assert.equal(player.state.status, STATUS.error);
  assert.match(player.state.error.title, /404/);
});

test("a slow resolve that lands after the visitor skipped is dropped", async () => {
  const audio = fakeAudio();
  const releases = new Map();
  const player = createPlayer({
    audio,
    resolve: (song) => new Promise((resolve) => {
      releases.set(song.id, () => resolve({ ...song, stream: `https://fresh/${song.id}.m4a` }));
    }),
  });
  player.play([track("a", { stream: "" }), track("b", { stream: "" })], 0);
  player.next();
  releases.get("b")(); await settle();
  releases.get("a")(); await settle(); // actually complete the stale request, too
  assert.equal(player.state.index, 1);
  assert.equal(audio.src, "https://fresh/b.m4a");
  assert.equal(player.track.id, "b");
  assert.equal(player.state.queue[0].stream, "", "stale hydration must not modify the queue");
});

test("volume, repeat and shuffle are state, and volume is clamped", () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio });
  player.setVolume(0.4);
  assert.equal(audio.volume, 0.4);
  assert.equal(player.state.volume, 0.4);
  player.setVolume(99);
  assert.equal(player.state.volume, 1);
  player.setVolume(-3);
  assert.equal(player.state.volume, 0);
  assert.equal(player.cycleRepeat(), "all");
  assert.equal(player.cycleRepeat(), "one");
  assert.equal(player.cycleRepeat(), "off");
  assert.equal(player.toggleShuffle(), true);
  assert.equal(player.state.shuffle, true);
});

test("queue edits do not interrupt the track that is playing", async () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio });
  player.play([track("a"), track("b")], 0);
  await settle();
  assert.equal(player.enqueue(track("c")), true);
  assert.equal(player.enqueue(track("c")), false, "the same track is not queued twice");
  assert.equal(player.playNext(track("d")), true);
  assert.deepEqual(player.state.queue.map((entry) => entry.id), ["a", "d", "b", "c"]);
  assert.equal(player.state.index, 0, "the playing track kept its place");
  player.removeAt(1);
  assert.deepEqual(player.state.queue.map((entry) => entry.id), ["a", "b", "c"]);
  player.removeAt(99);
  assert.equal(player.state.queue.length, 3, "an out-of-range removal is a no-op");
});

test("the end of the queue with repeat off stops instead of looping", async () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio });
  player.play([track("a")], 0);
  await settle();
  audio.emit("ended");
  await settle();
  assert.equal(player.state.status, STATUS.ended);
});

test("repeat-one restarts the same row when it ends", async () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio });
  player.play([track("a"), track("b")], 0);
  await settle();
  player.cycleRepeat();
  player.cycleRepeat();
  assert.equal(player.state.repeat, "one");
  audio.emit("ended");
  await settle();
  assert.equal(player.state.index, 0);
});

test("the queue view marks the current row and its playing state", async () => {
  const audio = fakeAudio();
  const player = createPlayer({ audio });
  player.play([track("a"), track("b")], 0);
  await settle();
  const rows = player.queueView();
  assert.equal(rows[0].current, true);
  assert.equal(rows[0].playing, true);
  assert.equal(rows[1].current, false);
  assert.equal(rows[1].index, 1);
});

test("no media session is not an error", () => {
  assert.doesNotThrow(() => attachMediaSession({ track: track("a"), mediaSession: null }));
});

test("a media session receives the track and the transport handlers", () => {
  const actions = new Map();
  const session = {
    metadata: null,
    playbackState: "none",
    setActionHandler(name, handler) {
      actions.set(name, handler);
    },
    setPositionState(state) {
      actions.set("position", state);
    },
  };
  attachMediaSession({
    track: track("a"),
    status: STATUS.playing,
    position: 10,
    duration: 200,
    handlers: { play: () => {}, next: () => {} },
    mediaSession: session,
  });
  assert.equal(session.playbackState, "playing");
  assert.ok(actions.has("nexttrack"), "the media session speaks the platform's names, not ours");
  assert.ok(actions.has("play"));
  assert.deepEqual(actions.get("position"), { duration: 200, playbackRate: 1, position: 10 });
});

// Reproduced against the merged runtime, with controllable media promises.
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

test("selecting an unresolved track silences and releases the previous media first", async () => {
  const audio = fakeAudio(); const pending = deferred();
  const player = createPlayer({ audio, resolve: () => pending.promise });
  player.play([track("old")]); await settle();
  player.play([track("new", { stream: "" })]);
  assert.equal(player.state.status, STATUS.loading);
  assert.equal(audio.paused, true); assert.equal(audio.src, "");
  pending.resolve(track("new")); await settle();
  assert.equal(player.track.id, "new"); assert.equal(player.state.status, STATUS.playing);
});

test("an unplayable sample cannot leave previous real media running under its metadata", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("old")]); await settle();
  player.play([track("demo-tr-1", { stream: "", demo: true })]); await settle();
  assert.equal(player.state.status, STATUS.error); assert.match(player.state.error.title, /sample/i);
  assert.equal(audio.paused, true); assert.equal(audio.src, "");
  assert.equal(player.requested, false);
});

test("a late resume resolution cannot resurrect a stopped empty player", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a")]); await settle(); player.toggle();
  const pending = deferred(); audio.play = () => pending.promise;
  player.toggle(); player.stop(); pending.resolve(); await settle();
  assert.equal(player.state.status, STATUS.idle); assert.equal(player.state.index, -1);
  assert.deepEqual(player.state.queue, []); assert.equal(player.requested, false);
});

test("a late resume rejection cannot replace Stop with an unrelated error", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a")]); await settle(); player.toggle();
  const pending = deferred(); audio.play = () => pending.promise;
  player.toggle(); player.stop(); pending.reject(Object.assign(new Error("late"), { name: "NotAllowedError" })); await settle();
  assert.equal(player.state.status, STATUS.idle); assert.equal(player.state.error, null);
});

test("Pause during stream resolution prevents a later implicit start", async () => {
  const audio = fakeAudio(); const pending = deferred();
  const player = createPlayer({ audio, resolve: () => pending.promise });
  player.play([track("slow", { stream: "" })]); player.toggle();
  pending.resolve(track("slow")); await settle();
  assert.equal(player.state.status, STATUS.paused); assert.equal(player.requested, false);
  assert.equal(audio.paused, true); assert.equal(audio.src, "");
});

test("Pause during a pending resume wins over that resume's completion", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a")]); await settle(); player.toggle();
  const pending = deferred(); audio.play = () => { audio.paused = false; return pending.promise; };
  player.toggle(); player.toggle(); pending.resolve(); await settle();
  assert.equal(player.state.status, STATUS.paused); assert.equal(audio.paused, true);
  assert.equal(player.requested, false);
});

test("the early play event is not decoded playback evidence", async () => {
  const audio = fakeAudio(); const pending = deferred();
  audio.play = () => { audio.paused = false; audio.emit("play"); return pending.promise; };
  const player = createPlayer({ audio }); player.play([track("a")]);
  assert.equal(player.state.status, STATUS.loading);
  audio.emit("playing"); assert.equal(player.state.status, STATUS.playing);
  pending.resolve(); await settle();
});

test("buffer starvation returns to loading, and a playing event resumes current intent", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a")]); await settle(); audio.emit("waiting");
  assert.equal(player.state.status, STATUS.loading);
  audio.emit("playing"); assert.equal(player.state.status, STATUS.playing);
  player.toggle(); audio.emit("playing");
  assert.equal(player.state.status, STATUS.paused); assert.equal(player.requested, false);
});

test("shuffle deletion remaps remaining indices rather than skipping into a missing slot", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a"), track("b"), track("c")]); await settle();
  player.state.shuffle = true; player.state.order = [0, 2, 1];
  player.removeAt(2);
  assert.deepEqual(player.state.order, [0, 1]);
  assert.equal(nextIndex(player.state), 1);
  player.next(); await settle(); assert.equal(player.track.id, "b");
});

test("deleting before a resolving current item hydrates its new position, not another row", async () => {
  const audio = fakeAudio(); const pending = deferred(); const persisted = [];
  const player = createPlayer({ audio, resolve: () => pending.promise, persist: value => persisted.push(value) });
  player.play([track("a"), track("b", { stream: "" }), track("c")], 1);
  player.removeAt(0); pending.resolve(track("b")); await settle();
  assert.deepEqual(player.state.queue.map(row => row.id), ["b", "c"]);
  assert.equal(player.state.index, 0); assert.equal(player.state.queue[0].stream, track("b").stream);
  assert.deepEqual(player.state.order, [0, 1]); assert.equal(persisted.at(-1).index, 0);
});

test("manual Next at the last track ends and pauses actual media without clearing the queue", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a")]); await settle(); player.next();
  assert.equal(player.state.status, STATUS.ended); assert.equal(audio.paused, true);
  assert.equal(player.requested, false); assert.equal(player.state.queue.length, 1);
});

test("manual Next at the end invalidates a still-resolving track", async () => {
  const audio = fakeAudio(); const pending = deferred();
  const player = createPlayer({ audio, resolve: () => pending.promise });
  player.play([track("slow", { stream: "" })]); player.next();
  pending.resolve(track("slow")); await settle();
  assert.equal(player.state.status, STATUS.ended); assert.equal(audio.src, "");
  assert.equal(player.requested, false);
});

test("destroy invalidates resolution and stops owned media", async () => {
  const audio = fakeAudio(); const pending = deferred();
  const player = createPlayer({ audio, resolve: () => pending.promise });
  player.play([track("slow", { stream: "" })]); player.destroy();
  pending.resolve(track("slow")); await settle();
  assert.equal(audio.src, ""); assert.equal(audio.paused, true);
  assert.equal(player.state.status, STATUS.idle);
});

test("late media events cannot revive stopped state or restore old metadata", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a")]); await settle(); player.stop();
  for (const event of ["play", "playing", "ended", "error", "timeupdate", "loadedmetadata"]) audio.emit(event);
  assert.equal(player.state.status, STATUS.idle); assert.equal(player.state.error, null);
  assert.equal(player.state.duration, 0); assert.equal(player.state.position, 0);
});

test("playback identity changes per selection, not per progress event or pause/resume", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("a"), track("b")]); await settle(); const first = player.playbackId;
  audio.emit("timeupdate"); player.toggle(); player.toggle(); await settle();
  assert.equal(player.playbackId, first);
  player.next(); await settle(); assert.notEqual(player.playbackId, first);
});

test("stopping clears media-session metadata, playback and stale handlers", () => {
  const actions = new Map();
  const session = { metadata: { title: "old" }, playbackState: "playing", setPositionState: () => {}, setActionHandler: (name, action) => actions.set(name, action) };
  attachMediaSession({ track: null, mediaSession: session });
  assert.equal(session.metadata, null); assert.equal(session.playbackState, "none");
  assert.equal(actions.size, 7); assert.ok([...actions.values()].every(action => action === null));
});

test("queue replacement never emits playing for a different unready selection", async () => {
  const audio = fakeAudio(); const player = createPlayer({ audio });
  player.play([track("old")]); await settle();
  const misleading = [];
  player.subscribe(state => { if (state.status === STATUS.playing && currentTrack(state)?.id !== "old") misleading.push(state); });
  player.play([track("demo-tr-1", { stream: "", demo: true })]); await settle();
  assert.deepEqual(misleading, []);
  assert.equal(audio.paused, true); assert.equal(player.state.status, STATUS.error);
});
