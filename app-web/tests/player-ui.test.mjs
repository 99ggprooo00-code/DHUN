import test from "node:test";
import assert from "node:assert/strict";
import { fullPlayer, fullPlayerRenderKey } from "../src/js/player-ui.js";
import { miniPlayer } from "../src/js/shell.js";

const model = () => ({ track: { id: "fixture", title: "Fixture", artist: "Test" }, queue: [], position: 1, duration: 30, percent: 3, status: "playing", playing: true, tab: "queue", liked: false, repeat: "off" });

test("progress updates keep the full-player structural key stable", () => {
  const before = model();
  assert.equal(fullPlayerRenderKey(before), fullPlayerRenderKey({ ...before, position: 10, duration: 60, percent: 17 }));
});

test("track, queue, status, tab, errors and like changes still require real renders", () => {
  const before = model();
  for (const patch of [{ track: { ...before.track, id: "other" } }, { queue: [{ id: "other" }] }, { status: "paused", playing: false }, { tab: "lyrics" }, { error: { title: "refused" } }, { liked: true }]) {
    assert.notEqual(fullPlayerRenderKey(before), fullPlayerRenderKey({ ...before, ...patch }));
  }
});

test("loading controls announce their pause/cancel intent without claiming audio is playing", () => {
  const vm = { ...model(), status: "loading", playing: false };
  assert.match(String(fullPlayer(vm)), /aria-label="Pause loading"/);
  assert.match(String(miniPlayer(vm)), /aria-label="Pause loading"/);
});
