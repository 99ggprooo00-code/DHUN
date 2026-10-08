/**
 * Mutation proof for `tests/rules.mjs` — the decisions behind the browser checks.
 *
 * Every case has a "must pass" and a "must fail" half, so a rule that stops
 * firing is a red test in the build job rather than a green tick in a job
 * nobody can run locally. No browser, no network, no server:
 *   cd website && node --test tests/rules.test.mjs
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  INLINE_TARGET_FLOOR,
  TARGET_FLOOR,
  caveatsHiddenInPrint,
  duplicateLinkTargets,
  focusChanged,
  focusSignature,
  forcedColorsBoundaryMissing,
  headingOrderProblem,
  targetProblem,
} from "./rules.mjs";

test("targets: a standalone control under 44 px fails, at 44 passes", () => {
  assert.deepEqual(targetProblem({ width: 44, height: 44, standalone: true }), null);
  assert.equal(
    targetProblem({ width: 43.9, height: 44, standalone: true }).floor,
    TARGET_FLOOR,
  );
  assert.equal(
    targetProblem({ width: 44, height: 20, standalone: true }).floor,
    TARGET_FLOOR,
  );
});

test("targets: an inline target is exempt unless it is small in both axes", () => {
  // A footnote marker: one line tall, but wide enough to hit.
  assert.deepEqual(targetProblem({ width: 30, height: 14, standalone: false }), null);
  assert.equal(
    targetProblem({ width: 10, height: 10, standalone: false }).floor,
    INLINE_TARGET_FLOOR,
  );
});

test("headings: a skipped level fails, an upward jump passes", () => {
  assert.equal(headingOrderProblem([1, 2, 3, 2, 3]), null);
  assert.equal(headingOrderProblem([1, 2]), null);
  assert.match(headingOrderProblem([1, 2, 4]), /h2 is followed by h4/);
  assert.match(headingOrderProblem([1, 3]), /h1 is followed by h3/);
});

test("headings: two h1s are not this rule's business", () => {
  // `website_quality.py` owns "exactly one h1"; this rule must not duplicate it.
  assert.equal(headingOrderProblem([1, 1, 2]), null);
});

test("links: one text naming two destinations fails, one destination passes", () => {
  assert.deepEqual(
    duplicateLinkTargets([
      { text: "Features", href: "/features/" },
      { text: "Features", href: "/features/" },
      { text: "Source", href: "https://github.com/x" },
    ]),
    [],
  );
  const offenders = duplicateLinkTargets([
    { text: "Read more", href: "/features/" },
    { text: "read   more", href: "/ui/" },
  ]);
  assert.equal(offenders.length, 1);
  assert.match(offenders[0], /\/features\/ and \/ui\//);
});

test("links: an image link with no text is ignored, not reported", () => {
  assert.deepEqual(duplicateLinkTargets([{ text: "  ", href: "/a/" }, { text: "", href: "/b/" }]), []);
});

test("focus: a ring appearing is a change, an identical style is not", () => {
  const idle = {
    outlineStyle: "none",
    outlineWidth: "0px",
    outlineColor: "rgb(0, 0, 0)",
    boxShadow: "none",
    borderColor: "rgb(0, 0, 0)",
  };
  const focused = { ...idle, outlineStyle: "solid", outlineWidth: "3px" };
  assert.notEqual(focusSignature(idle), focusSignature(focused));
  assert.equal(focusChanged(idle, focused), true);
  assert.equal(focusChanged(idle, idle), false);
  // A ring drawn with box-shadow instead of outline still counts.
  assert.equal(focusChanged(idle, { ...idle, boxShadow: "0 0 0 3px #fff" }), true);
});

test("forced colours: a border or an outline keeps the control visible", () => {
  const bare = {
    borderTopStyle: "none",
    borderTopWidth: "0px",
    borderTopColor: "rgba(0, 0, 0, 0)",
    borderRightStyle: "none",
    borderRightWidth: "0px",
    borderRightColor: "rgba(0, 0, 0, 0)",
    borderBottomStyle: "none",
    borderBottomWidth: "0px",
    borderBottomColor: "rgba(0, 0, 0, 0)",
    borderLeftStyle: "none",
    borderLeftWidth: "0px",
    borderLeftColor: "rgba(0, 0, 0, 0)",
    outlineStyle: "none",
    outlineWidth: "0px",
    outlineColor: "rgba(0, 0, 0, 0)",
  };
  assert.equal(forcedColorsBoundaryMissing(bare), true);
  assert.equal(
    forcedColorsBoundaryMissing({
      ...bare,
      borderTopStyle: "solid",
      borderTopWidth: "1px",
      borderTopColor: "rgb(255, 255, 255)",
    }),
    false,
  );
  assert.equal(
    forcedColorsBoundaryMissing({
      ...bare,
      outlineStyle: "solid",
      outlineWidth: "1px",
      outlineColor: "CanvasText",
    }),
    false,
  );
  // A zero-width border is not a boundary, whatever its style says.
  assert.equal(
    forcedColorsBoundaryMissing({
      ...bare,
      borderLeftStyle: "solid",
      borderLeftWidth: "0px",
      borderLeftColor: "rgb(255, 255, 255)",
    }),
    true,
  );
  // The trap: `1px solid transparent` is exactly what the site's buttons carry,
  // and it is invisible under forced colours.
  assert.equal(
    forcedColorsBoundaryMissing({
      ...bare,
      borderTopStyle: "solid",
      borderTopWidth: "1px",
      borderTopColor: "rgba(0, 0, 0, 0)",
    }),
    true,
  );
});

test("print: a hidden caveat is named, a visible one is not", () => {
  assert.deepEqual(
    caveatsHiddenInPrint([
      { key: "borrowed-time", visible: true, height: 120 },
      { key: "rolling-unverified", visible: true, height: 40 },
    ]),
    [],
  );
  assert.deepEqual(
    caveatsHiddenInPrint([
      { key: "borrowed-time", visible: false, height: 0 },
      { key: "hardware-gates-open", visible: true, height: 0 },
    ]),
    ["borrowed-time", "hardware-gates-open"],
  );
});
