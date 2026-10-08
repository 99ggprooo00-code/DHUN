/**
 * Mutation proof for `tests/annotation-report.mjs`.
 *
 * The bug this protects is quiet: an annotation that is clipped or dropped
 * still leaves the run green, and the measurement it carried is gone. So every
 * case here asserts the *budget* and the *accounting*, not the prose.
 *
 * No browser, no network, no install:
 *   cd website && node --test tests/annotation-report.test.mjs
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  ANNOTATION_LIMIT,
  MESSAGE_BUDGET,
  clipMessage,
  packItems,
  packReport,
  renderAllReports,
  renderReport,
} from "./annotation-report.mjs";

const entriesOf = (count, size) =>
  new Map([["viewport 320", Array.from({ length: count }, (_, i) => `m${i} ${"x".repeat(size)}`)]]);

test("the budget sits under the observed truncation point", () => {
  assert.ok(MESSAGE_BUDGET < 4000, "GitHub clips around 4 KB; the budget must leave slack");
  assert.ok(MESSAGE_BUDGET > 1000, "the budget must be large enough to be worth annotating");
});

test("no message a caller can emit exceeds the budget", () => {
  for (const [count, size] of [
    [1, 10],
    [3, 200],
    [40, 500],
    [200, 2000],
  ]) {
    const { annotations } = packReport(entriesOf(count, size), "measurement", { messageBudget: MESSAGE_BUDGET });
    for (const { body } of annotations) {
      assert.ok(
        body.length <= MESSAGE_BUDGET,
        `a ${body.length}-char message escaped the ${MESSAGE_BUDGET} budget (${count}×${size})`,
      );
    }
  }
});

test("one item longer than the budget is clipped, not dropped", () => {
  const long = "y".repeat(MESSAGE_BUDGET * 3);
  const chunks = packItems([long]);
  assert.ok(chunks.length >= 1);
  for (const chunk of chunks) assert.ok(chunk.length <= MESSAGE_BUDGET);
  assert.match(clipMessage(long), /clipped/);
});

test("the annotation cap is respected", () => {
  const { annotations } = packReport(entriesOf(500, 100), "measurement");
  assert.ok(
    annotations.length <= ANNOTATION_LIMIT,
    `${annotations.length} annotations exceeds the ~10-per-step cap GitHub returns`,
  );
});

test("everything the cap leaves out is named and counted", () => {
  const entries = new Map([
    ["axe", ["axe: 0 violations on /"]],
    // Long enough that ten annotations cannot hold them: short items pack
    // dozens per message and never reach the cap.
    ["contrast", Array.from({ length: 60 }, (_, i) => `contrast ${i}: ${"5.4".padEnd(380, ".")}:1`)],
    ["targets", Array.from({ length: 60 }, (_, i) => `target ${i}: ${"44".padEnd(380, ".")} px`)],
  ]);
  const { annotations, dropped } = packReport(entries, "measurement");
  assert.ok(dropped.length > 0, "this harness run had to leave something out");
  const total = dropped.reduce((sum, entry) => sum + entry.count, 0);
  assert.ok(total > 0);
  const overflow = annotations[annotations.length - 1];
  assert.match(overflow.title, /not annotated/);
  assert.match(overflow.body, /job log/);
  // The overflow note must not itself be clipped into meaninglessness.
  assert.ok(!overflow.body.includes("…(clipped"));
});

test("a small run loses nothing at all", () => {
  const entries = new Map([
    ["axe", ["axe: 0 violations"]],
    ["contrast", ["contrast dark: 5.71:1", "contrast light: 4.6:1"]],
  ]);
  const { annotations, dropped } = packReport(entries, "measurement");
  assert.deepEqual(dropped, []);
  assert.equal(annotations.length, 2);
  assert.match(annotations[0].body, /axe: 0 violations/);
});

test("the full report is rendered without clipping", () => {
  const entries = new Map([
    ["axe", Array.from({ length: 50 }, (_, i) => `entry ${i} ${"z".repeat(300)}`)],
  ]);
  const markdown = renderReport(entries, "measurement");
  assert.ok(markdown.length > MESSAGE_BUDGET * 3, "the report is not the annotation");
  assert.match(markdown, /### measurement \(1\)/);
  assert.match(markdown, /entry 49/);
  assert.doesNotMatch(markdown, /clipped/);
});

test("the run report covers failures, warnings and measurements", () => {
  const markdown = renderAllReports({
    failures: [["overflow", ["/ overflows at 280 px"]]],
    warnings: [["summary", ["no summary file"]]],
    measurements: [["contrast", ["5.71:1 dark"]]],
  });
  assert.match(markdown, /### problem \(1\)/);
  assert.match(markdown, /### note \(1\)/);
  assert.match(markdown, /### measurement \(1\)/);
  assert.match(markdown, /overflows at 280 px/);
});
