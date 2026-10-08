import test from "node:test";
import assert from "node:assert/strict";

import { parseLrc, activeLineIndex, isSynced } from "../src/js/lyrics.js";

test("a plain synced line parses to milliseconds", () => {
  const lines = parseLrc("[00:12.34]Hello world");
  assert.deepEqual(lines, [{ timeMs: 12_340, text: "Hello world" }]);
});

test("two and three fraction digits are both accepted", () => {
  assert.equal(parseLrc("[01:00.5]half")[0].timeMs, 60_500);
  assert.equal(parseLrc("[01:00.25]quarter")[0].timeMs, 60_250);
  assert.equal(parseLrc("[01:00.125]eighth")[0].timeMs, 60_125);
  assert.equal(parseLrc("[01:00]whole")[0].timeMs, 60_000);
});

test("minutes above two digits still work (long-form LRC)", () => {
  assert.equal(parseLrc("[100:00.00]long")[0].timeMs, 6_000_000);
});

test("several timestamps on one line produce one line per timestamp", () => {
  const lines = parseLrc("[00:12.34][00:15.10]Repeat");
  assert.deepEqual(lines, [
    { timeMs: 12_340, text: "Repeat" },
    { timeMs: 15_100, text: "Repeat" },
  ]);
});

test("metadata tags are skipped, not turned into lyrics", () => {
  const lines = parseLrc("[ti:Song]\n[ar:Artist]\n[by:Someone]\n[00:01.00]First");
  assert.deepEqual(lines, [{ timeMs: 1000, text: "First" }]);
});

test("enhanced word timings are stripped from the text", () => {
  const lines = parseLrc("[00:01.00]Hello <00:01.20>world");
  assert.equal(lines[0].text, "Hello world");
});

test("unsynced lines fall back with a null time, and can be switched off", () => {
  assert.deepEqual(parseLrc("no timestamps here"), [{ timeMs: null, text: "no timestamps here" }]);
  assert.deepEqual(parseLrc("no timestamps here", { allowUnsyncedFallback: false }), []);
  // A metadata tag is not an unsynced line even with fallback on.
  assert.deepEqual(parseLrc("[ti:Song]"), []);
});

test("output is sorted by time, unsynced last", () => {
  const lines = parseLrc("[00:05.00]second\n[00:01.00]first\nplain\n[00:03.00]third");
  assert.deepEqual(
    lines.map((line) => line.timeMs),
    [1000, 3000, 5000, null],
  );
});

test("blank lines are dropped, an empty lyric line keeps its timing", () => {
  assert.deepEqual(parseLrc("[00:01.00]a\n\n\n[00:02.00]").map((l) => l.timeMs), [1000, 2000]);
});

test("the highlighted line is the last one at or before the position", () => {
  const lines = parseLrc("[00:01.00]one\n[00:03.00]two\n[00:06.00]three");
  assert.equal(activeLineIndex(lines, 0), -1);
  assert.equal(activeLineIndex(lines, 1000), 0);
  assert.equal(activeLineIndex(lines, 2999), 0);
  assert.equal(activeLineIndex(lines, 3000), 1);
  assert.equal(activeLineIndex(lines, 999_999), 2);
  assert.equal(activeLineIndex([], 1000), -1);
});

test("a track with no timestamps is reported as unsynced", () => {
  assert.equal(isSynced(parseLrc("[00:01.00]a")), true);
  assert.equal(isSynced(parseLrc("plain text")), false);
});
